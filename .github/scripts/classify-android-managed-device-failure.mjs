import { appendFile, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const INFRASTRUCTURE_PATTERNS = [
    ["adb-unavailable", /adb[^\n]*(?:device offline|device not found|no devices|cannot connect)/i],
    ["avd-start-failed", /(?:failed|unable|could not)\s+to\s+(?:launch|start)[^\n]*(?:avd|emulator)/i],
    ["emulator-boot-failed", /emulator[^\n]*(?:boot[^\n]*(?:failed|timeout|timed out)|terminated|offline|not booted)/i],
    ["managed-device-unavailable", /managed[ -]device[^\n]*(?:failed|timeout|timed out|unavailable)/i],
];

// 저장소가 429 로 거절해 설정 단계 의존성 해석이 실패했고 :app 태스크가 하나도 돌지 않았다면
// PR 코드가 실행되기 전에 끝난 인프라 실패다(#432). 429 없는 해석 실패는 없는 좌표일 수 있어
// 재실행으로 풀리지 않으므로 넣지 않는다.
const DEPENDENCY_RESOLUTION_FAILURE = /Could not resolve all files for configuration/;
const DEPENDENCY_REPOSITORY_RATE_LIMIT = /Received status code 429 from server/;
const APP_TASK_EXECUTED = /^> Task :app:/m;

function dependencyResolutionSignals(log) {
    const rateLimitedBeforeAppTasks = DEPENDENCY_RESOLUTION_FAILURE.test(log) &&
        DEPENDENCY_REPOSITORY_RATE_LIMIT.test(log) &&
        !APP_TASK_EXECUTED.test(log);
    return rateLimitedBeforeAppTasks ? ["dependency-repository-rate-limited"] : [];
}

const BOOT_PHASE_PATTERNS = [
    ["managed-device-setup-task", /> Task [^\n]*:pixel2Api(?:30|34)(?:Setup|Check)\b/im],
    ["api34-managed-device-task", /> Task [^\n]*:pixel2Api34DebugAndroidTest\b/im],
    ["gradle-managed-device", /gradle managed device|managed[ -]device/i],
    ["emulator-start", /(?:launching|starting|started)[^\n]*(?:avd|emulator)|(?:avd|emulator)[^\n]*boot/i],
    ["system-image-provisioning", /(?:preparing|downloading|installing)[^\n]*(?:system image|system-images;android-(?:30|34))/i],
];

const TEST_EXECUTION_PATTERNS = [
    ["test-execution-started", /starting\s+\d+\s+tests?\s+on\s+pixel2Api(?:30|34)\b/i],
    ["test-execution-progress", /pixel2Api(?:30|34)\s+tests\s+\d+\/\d+\s+completed/i],
];

function matchingSignals(log, patterns) {
    return patterns.filter(([, pattern]) => pattern.test(log)).map(([name]) => name);
}

export function classifyAndroidManagedDeviceFailure({
    device,
    exitCode = "",
    log = "",
    outcome,
    testResultCount = 0,
}) {
    const normalizedDevice = String(device ?? "").trim().toLowerCase();
    const normalizedExitCode = String(exitCode ?? "").trim();
    const normalizedOutcome = String(outcome ?? "").trim().toLowerCase();
    const infrastructureSignals = [
        ...matchingSignals(log, INFRASTRUCTURE_PATTERNS),
        ...dependencyResolutionSignals(log),
    ];
    const bootPhaseSignals = matchingSignals(log, BOOT_PHASE_PATTERNS);
    const testExecutionSignals = matchingSignals(log, TEST_EXECUTION_PATTERNS);
    const timedOut = normalizedExitCode === "124" ||
        normalizedOutcome === "cancelled" ||
        normalizedOutcome === "timed_out";
    const failed = normalizedOutcome !== "success" || (
        normalizedExitCode !== "" && normalizedExitCode !== "0"
    );

    const supported = normalizedDevice === "api30" || normalizedDevice === "api34";
    const noTestExecution = testResultCount === 0 && testExecutionSignals.length === 0;
    const hasInfrastructureEvidence = infrastructureSignals.length > 0 || bootPhaseSignals.length > 0;
    const api30Retryable = normalizedDevice === "api30" &&
        normalizedExitCode === "124" &&
        noTestExecution &&
        hasInfrastructureEvidence &&
        failed;
    const api34Retryable = normalizedDevice === "api34" &&
        noTestExecution &&
        (infrastructureSignals.length > 0 || (timedOut && bootPhaseSignals.length > 0)) &&
        failed;

    let reason = "not-failed";
    if (!supported) {
        reason = "unsupported-device";
    } else if (!failed) {
        reason = "not-failed";
    } else if (testResultCount > 0) {
        reason = "test-results-exist";
    } else if (testExecutionSignals.length > 0) {
        reason = "test-execution-started";
    } else if (api30Retryable || (normalizedDevice === "api34" && timedOut && bootPhaseSignals.length > 0)) {
        reason = "managed-device-boot-timeout";
    } else if (api34Retryable) {
        reason = "explicit-infrastructure-failure";
    } else {
        reason = "not-proven-infrastructure-failure";
    }

    return {
        retryable: api30Retryable || api34Retryable,
        reason,
        exitCode: normalizedExitCode,
        infrastructureSignals,
        bootPhaseSignals,
        testExecutionSignals,
        testResultCount,
    };
}

async function countXmlFiles(directory) {
    let entries;
    try {
        entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
        if (error.code === "ENOENT") return 0;
        throw error;
    }

    let count = 0;
    for (const entry of entries) {
        const entryPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            count += await countXmlFiles(entryPath);
        } else if (entry.isFile() && entry.name.endsWith(".xml")) {
            count += 1;
        }
    }
    return count;
}

async function main() {
    const [logPath, resultsDirectory, outputPath] = process.argv.slice(2);
    if (!logPath || !resultsDirectory || !outputPath) {
        throw new Error("Usage: classify-android-managed-device-failure.mjs <log> <results-dir> <output>");
    }

    let log = "";
    try {
        log = await readFile(logPath, "utf8");
    } catch (error) {
        if (error.code !== "ENOENT") throw error;
    }
    const testResultCount = await countXmlFiles(resultsDirectory);
    const classification = classifyAndroidManagedDeviceFailure({
        device: process.env.DEVICE,
        exitCode: process.env.ANDROID_TEST_EXIT_CODE,
        log,
        outcome: process.env.ANDROID_TEST_OUTCOME,
        testResultCount,
    });
    const marker = {
        schemaVersion: 1,
        sourceRunId: Number(process.env.GITHUB_RUN_ID),
        runAttempt: Number(process.env.GITHUB_RUN_ATTEMPT),
        headSha: process.env.TARGET_SHA,
        device: process.env.DEVICE,
        ...classification,
    };

    await mkdir(path.dirname(outputPath), { recursive: true });
    await writeFile(outputPath, `${JSON.stringify(marker, null, 2)}\n`);
    if (process.env.GITHUB_OUTPUT) {
        await appendFile(process.env.GITHUB_OUTPUT, [
            `retryable=${classification.retryable}`,
            `reason=${classification.reason}`,
            "",
        ].join("\n"));
    }
    process.stdout.write(`${JSON.stringify(marker)}\n`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
    await main();
}

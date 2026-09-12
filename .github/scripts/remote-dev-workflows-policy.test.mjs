import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { copyFile, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const workflowDirectory = new URL("../workflows/", import.meta.url);
const readWorkflow = (name) => readFile(new URL(name, workflowDirectory), "utf8");

// screenshot 골든을 가진 모듈만 적는다. `:feature:profile:presentation` 은 여기 없다 — 그 모듈은
// screenshot 플러그인을 달지 않는다(#175). 목록에 남겨 두면 폴백 task 목록이 존재하지 않는 task 를 가리켜,
// repository-quality 가 실패해 폴백이 쓰이는 순간 screenshot 잡까지 「task not found」로 함께 빨개진다.
const screenshotModules = [
    ":core:ui",
    ":feature:onboarding:presentation",
    ":feature:feed:presentation",
    ":feature:editor:presentation",
    ":feature:foryou:presentation",
    ":feature:notification:presentation",
];

test("baseline generation executes PR code without write credentials", async () => {
    const source = await readWorkflow("screenshot-baseline-generate.yml");

    assert.match(source, /^\s{2}pull_request:\n\s{4}types: \[labeled\]$/m);
    assert.match(source, /^permissions:\n\s{2}contents: read\n\s{2}pull-requests: read$/m);
    assert.doesNotMatch(source, /^\s{2}contents: write$/m);
    assert.match(source, /github\.event\.label\.name == 'screenshot-baseline'/);
    assert.match(source, /github\.event\.pull_request\.head\.sha/);
    assert.match(source, /github\.event\.pull_request\.head\.repo\.full_name == github\.repository/);
    assert.match(source, /persist-credentials: false/);
    assert.match(source, /platforms: linux\/amd64/);
    assert.match(source, /docker run --rm --platform linux\/amd64/);
    assert.match(source, /--rerun --no-daemon/);
    assert.match(source, /gh api --paginate --slurp/);
    assert.match(source, /resolve-pr-impact\.mjs/);
    assert.match(source, /update_tasks\+=\("\$\{module\}:updateScreenshotTest"\)/);
    assert.match(source, /validate_tasks\+=\("\$\{module\}:validateScreenshotTest"\)/);
    assert.match(source, /SCREENSHOT_MODULES: \$\{\{ steps\.impact\.outputs\.screenshot_modules \}\}/);
});

test("privileged baseline apply is a workflow-run bridge restricted to PNG baselines", async () => {
    const source = await readWorkflow("screenshot-baseline-apply.yml");

    assert.match(source, /^\s{2}workflow_run:/m);
    assert.match(source, /workflows: \["Generate Screenshot Baselines"\]/);
    assert.match(source, /github\.event\.workflow_run\.event == 'pull_request'/);
    assert.match(source, /^\s{2}contents: write$/m);
    assert.match(source, /^\s{2}pull-requests: write$/m);
    assert.doesNotMatch(source, /actions\/checkout@/);
    assert.doesNotMatch(source, /git apply/);
    assert.doesNotMatch(source, /child_process/);
    assert.match(source, /files\.json/);
    assert.match(source, /Rejected non-baseline or duplicate path/);
    assert.match(source, /89504e470d0a1a0a/);
    assert.match(source, /pullRequest\.head\.sha !== metadata\.headSha/);
    assert.match(source, /force: false/);
    for (const module of screenshotModules) {
        const root = `${module.slice(1).replaceAll(":", "/")}/src/screenshotTestDebug/reference/`;
        assert.ok(source.includes(`'${root}'`), `${module} baseline root is not allowed`);
    }
    for (const workflow of [
        "pr-validation.yml",
        "codeql.yml",
        "merge-order-guard.yml",
    ]) {
        assert.ok(source.includes(`workflow_id: '${workflow}'`), `${workflow} is not redispatched`);
    }
    assert.match(source, /if: steps\.commit\.outputs\.changed == 'true'/);
    assert.match(source, /const pullRequestNumber = Number\(process\.env\.TARGET_PR\)/);
    assert.match(source, /Number\.isSafeInteger\(pullRequestNumber\)/);
    assert.match(source, /pullRequestNumber <= 0/);
    assert.match(source, /const pullRequestInput = String\(pullRequestNumber\)/);
    assert.equal(
        (source.match(/inputs: \{ pull_request_number: pullRequestInput \}/g) ?? []).length,
        3,
    );
});

test("screenshot workflow fallbacks cover every baseline module", async () => {
    const [reusable, validation] = await Promise.all([
        readWorkflow("screenshot.yml"),
        readWorkflow("pr-validation.yml"),
    ]);
    const moduleFallback = screenshotModules.join(" ");
    const taskFallback = screenshotModules.map((module) => `${module}:validateScreenshotTest`).join(" ");

    for (const [name, source] of [["reusable", reusable], ["PR", validation]]) {
        assert.ok(source.includes(taskFallback), `${name} task fallback is incomplete`);
        assert.ok(source.includes(moduleFallback), `${name} module fallback is incomplete`);
    }
});

test("docker fallback commands target exactly the baseline modules", async () => {
    // Actions 가 멈춰 문서대로 컨테이너에서 골든을 돌리는 순간, 목록에 없는 모듈이 명령에 남아 있으면
    // Gradle 이 태스크 선택 단계에서 "Task 'updateScreenshotTest' not found" 로 죽어 나머지 모듈 골든까지
    // 못 받는다(#351, :feature:profile:presentation 이 그렇게 남아 있었다). workflow 쪽 fallback 만
    // 맞춰 두면 이 경로는 아무도 검사하지 않으므로 같은 목록으로 여기서도 대조한다.
    const repositoryRoot = new URL("../../", import.meta.url);
    const [dockerfile, guide] = await Promise.all([
        readFile(new URL("Dockerfile.screenshot", repositoryRoot), "utf8"),
        readFile(new URL("docs/testing/screenshot.md", repositoryRoot), "utf8"),
    ]);
    const expected = [...screenshotModules].sort();

    for (const [name, source] of [
        ["Dockerfile.screenshot", dockerfile],
        ["docs/testing/screenshot.md", guide],
    ]) {
        for (const task of ["updateScreenshotTest", "validateScreenshotTest"]) {
            const mentioned = [...source.matchAll(
                new RegExp(`(:[a-z0-9]+(?::[a-z0-9]+)+):${task}\\b`, "g"),
            )].map(([, module]) => module);

            assert.deepEqual(
                [...new Set(mentioned)].sort(),
                expected,
                `${name} 의 ${task} 명령이 baseline 모듈 목록과 다르다`,
            );
        }
    }
});

test("managed device keeps required contexts but boots only CI Test Plan lanes", async () => {
    const source = await readWorkflow("android-managed-device.yml");

    assert.match(source, /^\s{2}pull_request:\n\s{4}types: \[opened, reopened, edited, synchronize\]$/m);
    assert.doesNotMatch(source, /^\s{2}workflow_call:/m);
    assert.doesNotMatch(source, /contains\(github\.event\.pull_request\.labels\.\*\.name, 'android-test'\)/);
    assert.match(source, /github\.event_name == 'pull_request'/);
    assert.match(source, /github\.event\.pull_request\.head\.repo\.full_name == github\.repository/);
    assert.match(source, /inputs\.pull_request_number > 0/);
    assert.match(source, /github\.ref_name == github\.event\.repository\.default_branch/);
    assert.match(source, /cancel-in-progress: true/);
    assert.match(source, /^\s{6}pull_request_number:\n/m);
    assert.match(source, /^\s{6}expected_head_sha:\n/m);
    assert.match(source, /^\s{6}expected_plan_digest:\n/m);
    assert.match(source, /head_repository.*!=.*GITHUB_REPOSITORY/);
    assert.match(source, /target_sha.*!=.*EXPECTED_HEAD_SHA/);
    assert.match(source, /target_branch.*!=.*DISPATCH_REF_NAME/);
    assert.match(source, /target_sha.*!=.*EXECUTION_SHA/);
    assert.match(
        source,
        /ref: \$\{\{ github\.event_name == 'pull_request' && github\.event\.pull_request\.head\.sha \|\| github\.sha \}\}/,
    );
    assert.doesNotMatch(source, /ref: \$\{\{ steps\.target\.outputs\.sha \}\}/);
    assert.match(source, /actual_sha.*!=.*EXPECTED_SHA/);
    const trustedCheckout = source.indexOf("Clone trusted target policy");
    const policyStaging = source.indexOf("Stage trusted Android test policy");
    const targetCheckout = source.indexOf("Clone tested revision");
    const targetVerification = source.indexOf("Verify checked out revision");
    const bootstrapRenderer = source.indexOf("Stage bootstrap result renderer from the tested revision");
    const bootstrapVerifier = source.indexOf("Stage bootstrap result verifier from the tested revision");
    assert.ok(trustedCheckout >= 0 && trustedCheckout < policyStaging);
    assert.ok(policyStaging < targetCheckout);
    assert.ok(targetCheckout < targetVerification && targetVerification < bootstrapRenderer);
    assert.ok(bootstrapRenderer < bootstrapVerifier);
    assert.match(source, /source=trusted/);
    assert.match(source, /source=bootstrap/);
    assert.doesNotMatch(source, /source=target/);
    assert.match(source, /renderer=trusted/);
    assert.match(source, /renderer=bootstrap/);
    assert.match(source, /classifier=trusted/);
    assert.match(source, /classifier=unavailable/);
    assert.doesNotMatch(source, /Stage bootstrap infrastructure classifier/);
    assert.match(source, /Android test policy가 부분 설치된 상태입니다/);
    assert.match(source, /bootstrap mode에서는 expected_plan_digest를 사용할 수 없습니다/);
    assert.match(source, /bootstrap mode에서는 selected selector를 신뢰된 parser 없이 실행할 수 없습니다/);
    assert.match(source, /policy bootstrap full run/);
    assert.match(source, /bootstrap result renderer가 tested revision에 없습니다/);
    assert.match(source, /bootstrap result verifier가 tested revision에 없습니다/);
    assert.match(source, /resolve-android-test-plan\.mjs/);
    assert.match(source, /Validate selected tests in the tested revision/);
    assert.match(source, /Verify selected androidTest results/);
    assert.match(source, /Summarize androidTest results/);
    assert.match(source, /verify-android-test-plan-result\.mjs/);
    assert.match(source, /if: steps\.target\.outputs\.run_lane == 'true'/);
    assert.match(source, /selectors_json='\[\]'/);
    assert.match(source, /persist-credentials: false/);
    assert.match(source, /MERGE_QUEUE_SKIP: \$\{\{ matrix\.merge_queue_skip \}\}/);
    assert.match(
        source,
        /MERGE_QUEUE_SKIP.*== "true" && "\$EVENT_NAME" == "merge_group"[\s\S]*?run_lane=false[\s\S]*?elif \[\[ "\$mode" == "full" \]\]; then[\s\S]*?run_lane=true/,
    );
    assert.doesNotMatch(source, /MERGE_QUEUE_SKIP.*target_pr/);
    assert.match(
        source,
        /elif \[\[ "\$mode" == "selected" \]\]; then[\s\S]*?select\(\.device == \$device\)[\s\S]*?run_lane=true/,
    );
    assert.match(source, /merge_queue_skip matrix 값은 true 또는 false여야 합니다/);

    const bootstrapRendererStep = source.slice(bootstrapRenderer, bootstrapVerifier);
    assert.match(bootstrapRendererStep, /steps\.target\.outputs\.run_lane == 'true'/);
    assert.doesNotMatch(bootstrapRendererStep, /selectors_json != '\[\]'/);
});

test("managed device exposes a secretless branch Baseline Profile artifact mode", async () => {
    const source = await readWorkflow("android-managed-device.yml");
    const generatorStart = source.indexOf("  baseline-profile-generation:\n");
    const managedDeviceStart = source.indexOf("  managed-device:\n", generatorStart);
    assert.ok(generatorStart >= 0 && managedDeviceStart > generatorStart);

    const generator = source.slice(generatorStart, managedDeviceStart);
    const managedDevice = source.slice(managedDeviceStart);
    assert.match(source, /^\s{6}generate_baseline_profile:\n/m);
    assert.match(source, /type: boolean/);
    assert.match(generator, /github\.event_name == 'workflow_dispatch'/);
    assert.match(generator, /inputs\.generate_baseline_profile/);
    assert.match(generator, /inputs\.pull_request_number == 0/);
    assert.match(generator, /ref: \$\{\{ github\.sha \}\}/);
    assert.match(generator, /persist-credentials: false/);
    assert.match(generator, /uses: \.\/\.github\/actions\/setup-ci-config/);
    assert.match(generator, /uses: \.\/\.github\/actions\/setup-ci-release-signing/);
    assert.match(generator, /:app:generateBaselineProfile/);
    assert.match(generator, /-x :app:uploadCrashlyticsMappingFileRelease/);
    assert.match(generator, /continue-on-error: true/);
    assert.match(generator, /baseline-profile-log/);
    assert.match(generator, /generate\.log/);
    assert.match(generator, /app\/src\/main\/generated\/baselineProfiles\//);
    assert.match(generator, /actions\/upload-artifact@[0-9a-f]{40} # v7\.0\.1/);
    assert.match(generator, /Restore Baseline Profile generation exit code/);
    assert.doesNotMatch(generator, /secrets\./);
    assert.match(managedDevice, /!inputs\.generate_baseline_profile/);
});

test("managed device summarizes XML and uploads the full Gradle log before failing", async () => {
    const source = await readWorkflow("android-managed-device.yml");

    assert.match(source, /- name: Run managed-device androidTest\n\s+id: android_test/);
    // #1378: invocation 을 반복하므로 로그는 append 로 한 파일에 모은다. 파일은 루프
    // 앞에서 한 번 비운다.
    assert.match(source, /: > "\$gradle_log"/);
    assert.match(source, /--console=plain \\\n\s+--stacktrace >> "\$gradle_log" 2>&1/);
    assert.match(source, /echo "exit_code=\$status"/);
    assert.match(
        source,
        /- name: Verify selected androidTest results\n\s+id: selected_android_test\n\s+if: >-\n\s+always\(\) &&[\s\S]*?steps\.target\.outputs\.selectors_json != '\[\]'/,
    );
    assert.match(
        source,
        /- name: Summarize androidTest results\n\s+id: android_test_results\n\s+if: always\(\) && steps\.target\.outputs\.run_lane == 'true'/,
    );
    const summaryStep = source.slice(
        source.indexOf("- name: Summarize androidTest results\n"),
        source.indexOf("- name: Upload androidTest reports and failure evidence"),
    );
    assert.doesNotMatch(summaryStep, /selectors_json != '\[\]'/);
    assert.match(
        summaryStep,
        /ANDROID_TEST_ANNOTATION_DIR: \$\{\{ runner\.temp \}\}\/android-test-annotations/,
    );
    assert.match(summaryStep, /SELECTED_VERIFIER_EXIT_CODE/);
    assert.match(summaryStep, /render-android-test-results\.mjs/);
    assert.match(source, /\$\{\{ runner\.temp \}\}\/android-test-logs\//);
    assert.match(source, /\$\{\{ runner\.temp \}\}\/android-test-annotations\//);

    const verify = source.indexOf("Verify selected androidTest results");
    const summary = source.indexOf("Summarize androidTest results\n");
    const annotationSteps = [1, 2, 3, 4, 5].map((index) =>
        source.indexOf(`- name: Publish androidTest annotations ${index}\n`),
    );
    const upload = source.indexOf("Upload androidTest reports and failure evidence");
    const restore = source.indexOf("Restore managed-device androidTest exit code");
    assert.equal([...source.matchAll(/- name: Publish androidTest annotations \d+\n/g)].length, 5);
    assert.ok(verify >= 0 && verify < summary);
    annotationSteps.forEach((stepStart, offset) => {
        const index = offset + 1;
        const next = annotationSteps[offset + 1] ?? upload;
        assert.ok(stepStart > summary && stepStart < next);
        const step = source.slice(stepStart, next);
        assert.match(step, /if: >-\n\s+always\(\) &&/);
        assert.match(step, /steps\.target\.outputs\.run_lane == 'true'/);
        assert.ok(
            step.includes(
                `fromJSON(steps.android_test_results.outputs.annotation_chunks || '0') >= ${index}`,
            ),
        );
        assert.ok(
            step.includes(`run: cat "$RUNNER_TEMP/android-test-annotations/chunk-${index}.log"`),
        );
    });
    assert.ok(annotationSteps.at(-1) < upload && upload < restore);
    assert.match(source, /ANDROID_TEST_EXIT_CODE: \$\{\{ steps\.android_test\.outputs\.exit_code \}\}/);
    assert.match(source, /exit "\$ANDROID_TEST_EXIT_CODE"/);
    assert.match(source, /SELECTED_VERIFIER_EXIT_CODE: \$\{\{ steps\.selected_android_test\.outputs\.exit_code \}\}/);
    assert.match(source, /RESULT_RENDERER_EXIT_CODE: \$\{\{ steps\.android_test_results\.outputs\.exit_code \}\}/);
});

test("managed device fails fast per lane and preserves only bounded infrastructure evidence", async () => {
    const source = await readWorkflow("android-managed-device.yml");

    assert.match(source, /timeout-minutes: \$\{\{ matrix\.job_timeout_minutes \}\}/);
    assert.match(source, /device: api30[\s\S]*?job_timeout_minutes: 35[\s\S]*?gradle_timeout_minutes: 30[\s\S]*?gradle_step_timeout_minutes: 31/);
    assert.match(source, /device: api34[\s\S]*?job_timeout_minutes: 15[\s\S]*?gradle_timeout_minutes: 12[\s\S]*?gradle_step_timeout_minutes: 13/);
    assert.match(
        source,
        /device: api26[\s\S]*?full_selector: com\.careercompass\.careercompass_fe\.ApiBoundarySmokeAndroidTest[\s\S]*?merge_queue_skip: true/,
    );
    assert.match(
        source,
        /device: api36[\s\S]*?full_selector: com\.careercompass\.careercompass_fe\.ApiBoundarySmokeAndroidTest[\s\S]*?merge_queue_skip: true/,
    );
    assert.match(source, /device: api30[\s\S]*?merge_queue_skip: false/);
    assert.match(source, /device: api34[\s\S]*?merge_queue_skip: false/);
    assert.match(
        source,
        /- name: Run managed-device androidTest[\s\S]*?timeout-minutes: \$\{\{ matrix\.gradle_step_timeout_minutes \}\}/,
    );
    // #1378: 다중 selector 목록 실행에서 누락된 선언 selector 는 신뢰 경로인 단일
    // selector invocation 으로 재실행해 합본하되, Gradle 시간 예산은 invocation
    // 수와 무관하게 lane 의 GRADLE_TIMEOUT_MINUTES 전체를 공유한다.
    assert.match(source, /deadline=\$\(\( \$\(date \+%s\) \+ GRADLE_TIMEOUT_MINUTES \* 60 \)\)/);
    assert.match(source, /timeout --signal=TERM --kill-after=30s "\$\{remaining\}s"/);
    assert.match(source, /selector_recorded/);
    assert.match(source, /selected retry \$retried\/\$\{#missing\[@\]\}/);
    assert.doesNotMatch(source, /timeout-minutes: 45/);

    const gradle = source.indexOf("Run managed-device androidTest");
    const diagnostics = source.indexOf("Collect managed-device infrastructure diagnostics");
    const classifier = source.indexOf("Classify managed-device infrastructure failure");
    const retryMarker = source.indexOf("Upload managed-device infrastructure retry marker");
    const evidence = source.indexOf("Upload androidTest reports and failure evidence");
    const restore = source.indexOf("Restore managed-device androidTest exit code");
    assert.ok(gradle < diagnostics && diagnostics < classifier);
    assert.ok(classifier < retryMarker && retryMarker < evidence && evidence < restore);

    const diagnosticsStep = source.slice(diagnostics, classifier);
    assert.match(diagnosticsStep, /adb devices -l/);
    assert.match(diagnosticsStep, /emulator" -accel-check/);
    assert.match(diagnosticsStep, /ps -eo pid,ppid,etimes,stat,%cpu,%mem,comm/);
    assert.doesNotMatch(diagnosticsStep, /\.android\/avd|dmesg|printenv|ps [^\n]*command/);
    assert.match(source, /steps\.policy\.outputs\.classifier == 'trusted'/);
    assert.match(source, /ANDROID_TEST_OUTCOME: \$\{\{ steps\.android_test\.outcome \}\}/);
    assert.match(source, /android-managed-device-retry-\$\{\{ matrix\.device \}\}-\$\{\{ github\.run_id \}\}-\$\{\{ github\.run_attempt \}\}/);
    assert.match(source, /exit 124/);
});

test("managed-device recovery reruns only one validated first-attempt infrastructure failure", async () => {
    const source = await readWorkflow("android-managed-device-retry.yml");

    assert.match(source, /^\s{2}workflow_run:\n\s{4}workflows: \["Android Managed Device Test"\]/m);
    assert.match(source, /github\.event\.workflow_run\.event == 'pull_request'/);
    assert.match(source, /github\.event\.workflow_run\.head_repository\.full_name == github\.repository/);
    assert.match(source, /github\.event\.workflow_run\.run_attempt == 1/);
    assert.match(source, /^\s{6}actions: write$/m);
    assert.doesNotMatch(source, /actions\/checkout@/);
    assert.doesNotMatch(source, /^\s+run:/m);
    assert.match(source, /marker\.sourceRunId === run\.id/);
    assert.match(source, /marker\.headSha === run\.head_sha/);
    assert.match(source, /marker\.testResultCount === 0/);
    assert.match(source, /marker\.testExecutionSignals\.length === 0/);
    assert.match(source, /marker\.exitCode === devicePolicy\.requiredExitCode/);
    assert.match(source, /android-managed-device-retry-api30-/);
    assert.match(source, /android-managed-device-retry-api34-/);
    assert.match(source, /Expected exactly one retry marker artifact/);
    assert.match(source, /Pixel 2 API 30 androidTest/);
    assert.match(source, /Pixel 2 API 34 accessibility smoke/);
    assert.match(source, /POST \/repos\/\{owner\}\/\{repo\}\/actions\/jobs\/\{job_id\}\/rerun/);
});

test("managed device stages every local dependency of its trusted Android policy", async () => {
    const source = await readWorkflow("android-managed-device.yml");
    const stageStart = source.indexOf("- name: Stage trusted Android test policy");
    const stageEnd = source.indexOf("- name: Resolve and verify tested revision");
    assert.ok(stageStart >= 0 && stageStart < stageEnd);

    const stagedSource = source.slice(stageStart, stageEnd);
    const stagedFiles = new Set(
        [...stagedSource.matchAll(/\.github\/scripts\/([a-z0-9-]+\.mjs)/g)]
            .map((match) => match[1]),
    );
    const scriptsDirectory = new URL("../scripts/", import.meta.url);
    for (const stagedFile of stagedFiles) {
        const script = await readFile(new URL(stagedFile, scriptsDirectory), "utf8");
        for (const match of script.matchAll(/from\s+["']\.\/([^"']+\.mjs)["']/g)) {
            assert.ok(
                stagedFiles.has(match[1]),
                `${stagedFile} imports ${match[1]}, but the workflow does not stage it`,
            );
        }
    }

    const stagedDirectory = await mkdtemp(path.join(tmpdir(), "android-test-policy-"));
    try {
        await Promise.all(
            [...stagedFiles].map((file) => copyFile(
                fileURLToPath(new URL(file, scriptsDirectory)),
                path.join(stagedDirectory, file),
            )),
        );
        const payloadPath = path.join(stagedDirectory, "pull-request.json");
        await writeFile(payloadPath, JSON.stringify({
            number: 1,
            body: `## CI Test Plan

\`\`\`json
{
  "androidTest": {
    "mode": "selected",
    "reason": "스테이징한 신뢰 정책을 실제 selected 계획으로 실행합니다.",
    "tests": [
      {
        "path": "app/src/androidTest/java/com/careercompass/careercompass_fe/AccessibilitySmokeAndroidTest.kt",
        "selector": "com.careercompass.careercompass_fe.AccessibilitySmokeAndroidTest#welcomeAndLogin_haveNoAutomatedAccessibilityErrors",
        "device": "api30"
      }
    ]
  }
}
\`\`\``,
        }));
        execFileSync(
            process.execPath,
            [path.join(stagedDirectory, "validate-pr-ci-test-plan.mjs"), payloadPath, process.cwd()],
            { stdio: "pipe" },
        );
    } finally {
        await rm(stagedDirectory, { recursive: true, force: true });
    }
});

test("screenshot cleanup tolerates cancellation before Gradle setup", async () => {
    const source = await readWorkflow("screenshot.yml");

    assert.match(source, /sudo chown -R .*\$GITHUB_WORKSPACE/);
    assert.match(source, /if \[\[ -e "\$HOME\/\.gradle" \]\]; then/);
    assert.match(source, /sudo chown -R .*\$HOME\/\.gradle/);
});

test("screenshot failure comment rechecks the live baseline label before writing", async () => {
    const source = await readWorkflow("screenshot.yml");
    const commentStep = source.indexOf("- name: Comment artifact URL on PR (failure)");
    const liveLabels = source.indexOf("github.rest.issues.listLabelsOnIssue", commentStep);
    const baselineGuard = source.indexOf("labels.some(({ name }) => name === baselineLabel)", liveLabels);
    const listComments = source.indexOf("github.rest.issues.listComments", baselineGuard);

    assert.ok(commentStep >= 0, "screenshot failure comment step is missing");
    assert.ok(liveLabels > commentStep, "live PR labels are not queried in the comment step");
    assert.ok(baselineGuard > liveLabels, "screenshot-baseline is not checked after the live query");
    assert.ok(listComments > baselineGuard, "comment lookup happens before the baseline label guard");
    assert.match(source.slice(liveLabels, listComments), /core\.info\([\s\S]*return;/);
});

test("required checks expose manual dispatch for token-authored commits", async () => {
    const validation = await readWorkflow("pr-validation.yml");
    const guard = await readWorkflow("merge-order-guard.yml");

    assert.match(validation, /^\s{2}workflow_dispatch:$/m);
    assert.match(guard, /^\s{2}workflow_dispatch:\n\s{4}inputs:/m);
    assert.match(guard, /github\.event_name == 'pull_request' \|\| github\.event_name == 'workflow_dispatch'/);
});

import assert from "node:assert/strict";
import test from "node:test";
import { classifyAndroidManagedDeviceFailure } from "./classify-android-managed-device-failure.mjs";

test("retries one API 34 timeout proven to be in the managed-device boot phase", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "success",
        exitCode: "124",
        log: "> Task :app:pixel2Api34DebugAndroidTest",
        testResultCount: 0,
    });

    assert.equal(result.retryable, true);
    assert.equal(result.reason, "managed-device-boot-timeout");
});

test("does not retry a test failure after XML results exist", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "success",
        exitCode: "1",
        log: "emulator terminated before boot",
        testResultCount: 1,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "test-results-exist");
});

test("does not relabel a slow compile as an emulator infrastructure failure", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "cancelled",
        log: "> Task :feature:careercompass:presentation:compileDebugKotlin",
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-proven-infrastructure-failure");
});

test("retries the API 30 pre-test provisioning timeout observed on PR 1312", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api30",
        outcome: "success",
        exitCode: "124",
        log: [
            "> Task :app:pixel2Api30Setup",
            'Preparing "Install Intel x86_64 Atom System Image API 30 (revision 11)".',
            "Installing Intel x86_64 Atom System Image in /sdk/system-images/android-30/default/x86_64",
        ].join("\n"),
        testResultCount: 0,
    });

    assert.equal(result.retryable, true);
    assert.equal(result.reason, "managed-device-boot-timeout");
    assert.equal(result.exitCode, "124");
    assert.deepEqual(result.testExecutionSignals, []);
});

test("does not retry an API 30 compile-only timeout", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api30",
        outcome: "success",
        exitCode: "124",
        log: "> Task :feature:careercompass:presentation:compileDebugKotlin",
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-proven-infrastructure-failure");
});

test("does not retry an API 30 non-timeout even with infrastructure evidence", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api30",
        outcome: "success",
        exitCode: "1",
        log: "Unable to start emulator: boot timed out",
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-proven-infrastructure-failure");
});

test("does not retry an API 30 timeout after test execution starts", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api30",
        outcome: "success",
        exitCode: "124",
        log: [
            "> Task :app:pixel2Api30Setup",
            "Starting 88 tests on pixel2Api30",
        ].join("\n"),
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "test-execution-started");
    assert.deepEqual(result.testExecutionSignals, ["test-execution-started"]);
});

test("retries an explicit emulator boot failure before tests start", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "success",
        exitCode: "1",
        log: "Unable to start emulator: boot timed out",
        testResultCount: 0,
    });

    assert.equal(result.retryable, true);
    assert.equal(result.reason, "explicit-infrastructure-failure");
});

// Afternote-FE PR #2154 run 35843330616 API 34 로그에서 뜬 줄이다. Maven Central 이 429 로 거절해
// 설정 단계에서 끝났고, :build-logic 태스크만 돌았을 뿐 :app 태스크와 에뮬레이터 기동은 없었다.
const DEPENDENCY_RATE_LIMIT_LOG = [
    "=== selected invocation: 전체 목록 3개 ===",
    "> Task :build-logic:compileKotlin",
    "> Task :build-logic:jar",
    "> Configure project :app",
    "FAILURE: Build failed with an exception.",
    "> Could not resolve all files for configuration ':app:unified-test-platform-android-test-plugin-host-additional-test-output'.",
    "   > Could not resolve com.google.protobuf:protobuf-java:4.28.3.",
    "               > Could not HEAD 'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/2.4.10/org.jetbrains.kotlin.plugin.compose.gradle.plugin-2.4.10.jar'. Received status code 429 from server: Too Many Requests",
    "BUILD FAILED in 34s",
].join("\n");

test("retries the API 34 configuration-phase dependency rate limit observed on Afternote-FE PR 2154", () => {
    // 그 run 은 Gradle 실패에서 스텝이 바로 빠져 outcome=failure·exitCode="" 였다(#432).
    // 종료 코드를 기록하게 고친 뒤에는 outcome=success·exitCode=1 로 온다.
    for (const [outcome, exitCode] of [["failure", ""], ["success", "1"]]) {
        const result = classifyAndroidManagedDeviceFailure({
            device: "api34",
            outcome,
            exitCode,
            log: DEPENDENCY_RATE_LIMIT_LOG,
            testResultCount: 0,
        });

        assert.equal(result.retryable, true);
        assert.equal(result.reason, "explicit-infrastructure-failure");
        assert.deepEqual(result.infrastructureSignals, ["dependency-repository-rate-limited"]);
    }
});

test("does not retry a dependency rate limit once an :app task has run", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "success",
        exitCode: "1",
        // 로그 첫 줄이 아닌 자리에 둬야 줄 단위 판정(/m)이 빠지는 회귀를 잡는다.
        log: DEPENDENCY_RATE_LIMIT_LOG.replace(
            "> Configure project :app",
            "> Configure project :app\n> Task :app:preBuild UP-TO-DATE",
        ),
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-proven-infrastructure-failure");
    assert.deepEqual(result.infrastructureSignals, []);
});

test("does not retry a dependency failure that lacks either resolution failure or 429", () => {
    for (const log of [
        // PR 이 없는 좌표를 넣은 경우다. 다시 돌려도 같은 실패가 난다.
        [
            "> Could not resolve all files for configuration ':app:debugRuntimeClasspath'.",
            "   > Could not find com.example:missing:1.0.",
        ].join("\n"),
        // 429 가 찍혔어도 해석 실패로 끝나지 않았으면 빌드를 멈춘 원인이 아니다.
        [
            "Could not HEAD 'https://repo.maven.apache.org/maven2/x.pom'. Received status code 429 from server: Too Many Requests",
            "> Task :feature:careercompass:presentation:compileDebugKotlin FAILED",
        ].join("\n"),
    ]) {
        const result = classifyAndroidManagedDeviceFailure({
            device: "api34",
            outcome: "success",
            exitCode: "1",
            log,
            testResultCount: 0,
        });

        assert.equal(result.retryable, false);
        assert.equal(result.reason, "not-proven-infrastructure-failure");
        assert.deepEqual(result.infrastructureSignals, []);
    }
});

test("keeps API 30 dependency rate limits on the timeout-only recovery policy", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api30",
        outcome: "success",
        exitCode: "1",
        log: DEPENDENCY_RATE_LIMIT_LOG,
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-proven-infrastructure-failure");
    assert.deepEqual(result.infrastructureSignals, ["dependency-repository-rate-limited"]);
});

test("does not retry a successful managed-device run", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api34",
        outcome: "success",
        exitCode: "0",
        log: "> Task :app:pixel2Api34DebugAndroidTest",
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "not-failed");
});

test("does not retry an unsupported managed device", () => {
    const result = classifyAndroidManagedDeviceFailure({
        device: "api35",
        outcome: "success",
        exitCode: "124",
        log: "Unable to start emulator: boot timed out",
        testResultCount: 0,
    });

    assert.equal(result.retryable, false);
    assert.equal(result.reason, "unsupported-device");
});

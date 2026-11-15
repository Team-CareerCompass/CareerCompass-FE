import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
    MEANINGFUL_INCREASE_BYTES,
    buildReport,
    compareSize,
    renderMarkdown,
} from "./render-release-aab-report.mjs";

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptsDirectory, "../..");

function currentMetrics(overrides = {}) {
    return {
        sourceSha: "current-sha",
        aabSha256: "a".repeat(64),
        aabSizeBytes: 16_000_000,
        signerSha256: "AA:BB",
        mappingSha256: "b".repeat(64),
        minimumDownloadBytes: 10_000_000,
        maximumDownloadBytes: 12_000_000,
        installableApkBytes: 13_000_000,
        bundletoolVersion: "1.18.3",
        bundletoolSha256: "c".repeat(64),
        ...overrides,
    };
}

test("reports a first-run baseline gap without fabricating a comparison", () => {
    const report = buildReport(currentMetrics(), null, "2026-08-22T00:00:00.000Z");

    assert.equal(report.baseline, null);
    assert.equal(report.comparison, null);
    assert.equal(report.policy.meaningfulIncrease, false);
    assert.match(renderMarkdown(report), /Baseline: unavailable/);
});

test("marks either a five-percent or one-MiB increase as meaningful", () => {
    assert.equal(compareSize(105, 100).meaningfulIncrease, true);
    assert.equal(
        compareSize(10_000_000 + MEANINGFUL_INCREASE_BYTES, 10_000_000)
            .meaningfulIncrease,
        true,
    );
    assert.equal(compareSize(10_100_000, 10_000_000).meaningfulIncrease, false);
});

test("compares only public numeric report fields from the previous baseline", () => {
    const baseline = buildReport(
        currentMetrics({
            sourceSha: "baseline-sha",
            aabSizeBytes: 15_000_000,
            maximumDownloadBytes: 11_000_000,
            installableApkBytes: 12_000_000,
        }),
        null,
        "2026-08-21T00:00:00.000Z",
    );
    const report = buildReport(currentMetrics(), baseline, "2026-08-22T00:00:00.000Z");

    assert.equal(report.baseline.sourceSha, "baseline-sha");
    assert.equal(report.comparison.aab.deltaBytes, 1_000_000);
    assert.equal(report.comparison.minimumDownload.deltaBytes, 0);
    assert.equal(report.comparison.maximumDownload.deltaBytes, 1_000_000);
    assert.equal(report.comparison.installableApk.deltaBytes, 1_000_000);
});

test("release workflow is secretless, non-deploying, and uploads reports only", async () => {
    const workflow = await readFile(
        join(repositoryRoot, ".github/workflows/release-aab-preflight.yml"),
        "utf8",
    );
    const appBuild = await readFile(join(repositoryRoot, "app/build.gradle.kts"), "utf8");
    const verifier = await readFile(
        join(repositoryRoot, "scripts/verify-play-release-bundle.sh"),
        "utf8",
    );
    const uploadBlock = workflow.slice(
        workflow.indexOf("- name: Upload digest and size reports only"),
    );

    assert.match(workflow, /^\s*pull_request:\s*\n\s+branches: \[main\]/m);
    assert.match(workflow, /^\s*schedule:\s*\n\s+# .*\n\s+- cron: '37 18 \* \* 1,4'/m);
    assert.match(workflow, /^\s*workflow_dispatch:\s*$/m);
    assert.match(workflow, /github\.event\.pull_request\.head\.repo\.full_name == github\.repository/);
    assert.match(workflow, /github\.event\.pull_request\.head\.ref == 'develop'/);
    assert.match(workflow, /uses: \.\/\.github\/actions\/setup-ci-config/);
    assert.match(workflow, /uses: \.\/\.github\/actions\/setup-ci-release-signing/);
    assert.match(workflow, /test-release-aab-negative-fixtures\.sh/);
    assert.match(workflow, /a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29/);
    assert.doesNotMatch(workflow, /\bsecrets\./);
    assert.doesNotMatch(
        workflow,
        /appDistributionUpload|firebaseAppDistribution|publishBundle|upload.*Play/i,
    );
    assert.doesNotMatch(workflow, /^\s*environment:/m);
    assert.doesNotMatch(uploadBlock, /\.aab|mapping\.txt|\.apk(?:s)?/);
    assert.match(workflow, /release-aab-preflight\.json/);
    assert.match(workflow, /release-aab-preflight\.md/);
    assert.match(verifier, /:app:bundleRelease/);
    assert.match(verifier, /-x :app:uploadCrashlyticsMappingFileRelease/);
    for (const entry of [
        "BundleConfig.pb",
        "base/manifest/AndroidManifest.xml",
        "base/resources.pb",
        "base/dex/classes.dex",
    ]) {
        assert.match(verifier, new RegExp(entry.replaceAll(".", "\\.")));
    }
    assert.match(appBuild, /isMinifyEnabled = true/);
    assert.match(appBuild, /isShrinkResources = true/);
});

// #1754 — 빌드·서명·크기만 보던 게이트에 «그 산출물이 실제로 켜지는가» 를 더한다. R8 keep 누락
// 부류(#1753)는 정적 검증을 전부 통과하므로, 이 단언들이 풀리면 같은 결함이 다시 배포까지 간다.
test("release preflight starts the very artifact it verified, before cleanup", async () => {
    const [workflow, preflight, smoke] = await Promise.all([
        readFile(join(repositoryRoot, ".github/workflows/release-aab-preflight.yml"), "utf8"),
        readFile(join(repositoryRoot, ".github/scripts/run-release-aab-preflight.sh"), "utf8"),
        readFile(join(repositoryRoot, ".github/scripts/run-release-startup-smoke.sh"), "utf8"),
    ]);

    // 스모크 대상은 preflight 가 이미 만든 universal APK 다 — 다시 빌드하면 검증 대상과 배포 대상이 갈라진다.
    assert.match(preflight, /RELEASE_SMOKE_APK_PATH/);
    assert.match(preflight, /cp "\$\{universal_apk_path\}" "\$\{RELEASE_SMOKE_APK_PATH\}"/);
    assert.doesNotMatch(smoke, /gradlew|bundletool|assembleRelease/);

    assert.match(workflow, /run-release-startup-smoke\.sh/);
    assert.match(workflow, /- name: Enable and verify KVM access/);
    assert.match(workflow, /test -c \/dev\/kvm/);

    // 정리 단계가 스모크 APK 를 지우고, 스모크는 그 전에 끝나야 한다.
    const smokeIndex = workflow.indexOf("- name: Verify the release artifact starts on a device");
    const cleanupIndex = workflow.indexOf("- name: Remove private release outputs");
    assert.ok(smokeIndex > 0 && cleanupIndex > smokeIndex, "smoke must run before cleanup");
    assert.match(workflow.slice(cleanupIndex), /release-startup-smoke\/universal\.apk/);

    // 두 축을 모두 본다 — 크래시 뒤 시스템이 프로세스를 되살리면 pid 만으로는 통과해 버린다.
    assert.match(smoke, /FATAL EXCEPTION/);
    assert.match(smoke, /pidof/);
    assert.match(smoke, /am start -W -n/);
    assert.doesNotMatch(smoke, /\bsecrets\b/);
});

// #1769 — 부팅 판정이 넓으면 게이트가 «없는 문제» 로 주 2회 빨간불을 낸다. 거짓 경보는 결함을
// 놓치는 것만큼 나쁘다 — 아무도 안 보게 되기 때문이다.
test("startup smoke aborts only on unmistakably fatal emulator output", async () => {
    const smoke = await readFile(
        join(repositoryRoot, ".github/scripts/run-release-startup-smoke.sh"),
        "utf8",
    );

    // 정상 부팅 중에도 나오는 ERROR 를 실패로 읽으면 안 된다.
    assert.doesNotMatch(smoke, /\^\(FATAL\|ERROR\)/);

    const guard = /grep -m1 -E '(\^\([^']+\))'/.exec(smoke);
    assert.ok(guard, "부팅 중단 판정 패턴을 찾지 못했습니다");
    const pattern = new RegExp(guard[1]);

    // 실제 관측된 로그 표본으로 양쪽을 다 고정한다.
    const healthy = [
        "INFO         | Android emulator version 37.1.11.0",
        "ERROR        | Failed to open dsp device, falling back",
        "WARNING: cannnot unmap ptr 0x7f4d46e01000 as it is in the protected range",
        "INFO         | Monitoring duration of emulator setup.",
    ];
    const fatal = [
        "PANIC: Cannot find AVD system path. Please define ANDROID_SDK_ROOT",
        "ERROR        | Unknown AVD name [careercompass-release-smoke], use -list-avds to see valid list.",
    ];
    for (const line of healthy) {
        assert.doesNotMatch(line, pattern, `정상 부팅 줄을 실패로 읽습니다: ${line}`);
    }
    for (const line of fatal) {
        assert.match(line, pattern, `치명 줄을 놓칩니다: ${line}`);
    }

    // 실패했을 때 무엇이 걸렸는지 로그에 남아야 다음 실패를 진단할 수 있다.
    assert.match(smoke, /부팅 중단 표시: \$\{fatal_line\}/);
});

// #336 — 스모크는 설치된 앱을 패키지명으로 찾는데, 기기가 아는 이름은 namespace 가 아니라
// applicationId 다. #220 이 namespace 만 옮기고 스크립트를 그대로 두자 preflight 가 런처
// 액티비티를 찾지 못해 스케줄 실행마다 빨개졌고, 그때의 단언은 존재 여부만 봐서 놓쳤다.
test("startup smoke resolves the app by applicationId, not the namespace", async () => {
    const [smoke, appBuild] = await Promise.all([
        readFile(join(repositoryRoot, ".github/scripts/run-release-startup-smoke.sh"), "utf8"),
        readFile(join(repositoryRoot, "app/build.gradle.kts"), "utf8"),
    ]);

    const applicationId = /^\s*applicationId = "([^"]+)"/m.exec(appBuild)?.[1];
    assert.ok(applicationId, "app/build.gradle.kts 에서 applicationId 를 읽지 못했습니다");

    const smokePackage = /^package_name="([^"]+)"$/m.exec(smoke)?.[1];
    assert.ok(smokePackage, "스모크 스크립트에서 package_name 을 읽지 못했습니다");
    assert.equal(
        smokePackage,
        applicationId,
        `스모크가 찾는 패키지명이 applicationId 와 다릅니다: ${smokePackage} != ${applicationId}`,
    );

    // 패키지명은 한 곳에서만 정한다. 다른 줄에 리터럴이 박히면 위 대조가 그 줄을 못 본다.
    const strayLiterals = smoke
        .split("\n")
        .filter((line) => /"com\.[A-Za-z0-9_.]+"/.test(line) && !line.startsWith("package_name="));
    assert.deepEqual(strayLiterals, [], "패키지명 리터럴은 package_name 한 줄에만 둔다");

    // 기기를 상대하는 세 지점이 모두 그 변수를 쓴다.
    assert.match(smoke, /resolve-activity --brief/);
    assert.match(smoke, /category\.LAUNCHER "\$\{package_name\}"/);
    assert.match(smoke, /== "\$\{package_name\}\/"\*/);
    assert.match(smoke, /pidof "\$\{package_name\}"/);
});

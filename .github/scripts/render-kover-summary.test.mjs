import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
    collectModuleSourceKeys,
    coverageForSourceKeys,
    changedFilesBetween,
    evaluateCoveragePolicy,
    includeBaselineModules,
    loadCoveragePolicy,
    parseKoverXml,
    renderPolicyAnnotations,
    renderSummary,
    selectChangedModules,
    validateCoveragePolicy,
} from "./render-kover-summary.mjs";

const COMMITTED_POLICY_PATH = new URL("../kover-coverage-policy.json", import.meta.url);
const REPOSITORY_ROOT = fileURLToPath(new URL("../../", import.meta.url));

const REPORT = `<?xml version="1.0" encoding="UTF-8"?>
<report name="sample">
  <package name="com/example/app">
    <sourcefile name="Home.kt">
      <line nr="1" mi="0" ci="1" mb="0" cb="0"/>
      <counter type="BRANCH" missed="1" covered="3"/>
      <counter type="LINE" missed="2" covered="8"/>
    </sourcefile>
  </package>
  <package name="com/example/core">
    <sourcefile name="Token.java">
      <counter type="BRANCH" missed="0" covered="0"/>
      <counter type="LINE" missed="5" covered="5"/>
    </sourcefile>
  </package>
  <counter type="INSTRUCTION" missed="10" covered="20"/>
  <counter type="BRANCH" missed="1" covered="3"/>
  <counter type="LINE" missed="7" covered="13"/>
</report>`;

const POLICY = {
    schemaVersion: 1,
    mode: "warn",
    source: {
        ref: "develop",
        sha: "0123456789abcdef0123456789abcdef01234567",
        runUrl: "https://github.com/example/repository/actions/runs/123",
    },
    modules: {
        app: {
            line: { missed: 2, covered: 8 },
            branch: { missed: 1, covered: 3 },
        },
        empty: {
            line: null,
            branch: null,
        },
    },
};

function moduleCoverage(name, line, branch) {
    return {
        name,
        coverage: {
            counters: { LINE: line, BRANCH: branch },
            matchedSources: 1,
            declaredSources: 1,
        },
    };
}

test("parses report-level and source-level line and branch counters", () => {
    const report = parseKoverXml(REPORT);

    assert.deepEqual(report.aggregate.LINE, { missed: 7, covered: 13 });
    assert.deepEqual(report.aggregate.BRANCH, { missed: 1, covered: 3 });
    assert.deepEqual(report.sources.get("com.example.app:Home.kt")?.LINE, {
        missed: 2,
        covered: 8,
    });
});

test("maps changed paths to the deepest app, core, or feature module", () => {
    assert.deepEqual(
        selectChangedModules(
            [
                "README.md",
                "app/src/main/Home.kt",
                "core/network/src/test/TokenTest.kt",
                "feature/timeletter/presentation/build.gradle.kts",
            ],
            ["app", "core/network", "feature/timeletter/domain", "feature/timeletter/presentation"],
        ),
        ["app", "core/network", "feature/timeletter/presentation"],
    );
});

test("keeps deleted production files in changed-module coverage selection", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "kover-deleted-module-"));
    const source = path.join(root, "feature/deleted/domain/src/main/Deleted.kt");
    await fs.mkdir(path.dirname(source), { recursive: true });
    await fs.writeFile(source, "package example\nclass Deleted\n");
    execFileSync("git", ["init", "--quiet"], { cwd: root });
    execFileSync("git", ["config", "user.name", "Kover Test"], { cwd: root });
    execFileSync("git", ["config", "user.email", "kover@example.invalid"], { cwd: root });
    execFileSync("git", ["add", "."], { cwd: root });
    execFileSync("git", ["commit", "--quiet", "-m", "base"], { cwd: root });
    const base = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();

    await fs.rm(source);
    execFileSync("git", ["add", "--all"], { cwd: root });
    execFileSync("git", ["commit", "--quiet", "-m", "delete"], { cwd: root });
    const head = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();

    const changedFiles = changedFilesBetween(base, head, root);
    assert.deepEqual(changedFiles, ["feature/deleted/domain/src/main/Deleted.kt"]);
    assert.deepEqual(
        selectChangedModules(changedFiles, ["feature/deleted/domain"]),
        ["feature/deleted/domain"],
    );
});

test("evaluates both baseline and destination modules when production code is renamed", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "kover-renamed-module-"));
    const oldSource = "feature/old/domain/src/main/Old.kt";
    const newSource = "feature/new/domain/src/main/Old.kt";
    await fs.mkdir(path.join(root, path.dirname(oldSource)), { recursive: true });
    await fs.writeFile(path.join(root, oldSource), "package example\nclass Renamed\n");
    execFileSync("git", ["init", "--quiet"], { cwd: root });
    execFileSync("git", ["config", "user.name", "Kover Test"], { cwd: root });
    execFileSync("git", ["config", "user.email", "kover@example.invalid"], { cwd: root });
    execFileSync("git", ["add", "."], { cwd: root });
    execFileSync("git", ["commit", "--quiet", "-m", "base"], { cwd: root });
    const base = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();

    await fs.mkdir(path.join(root, path.dirname(newSource)), { recursive: true });
    execFileSync("git", ["mv", oldSource, newSource], { cwd: root });
    execFileSync("git", ["commit", "--quiet", "-m", "rename"], { cwd: root });
    const head = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();

    const changedFiles = changedFilesBetween(base, head, root);
    assert.deepEqual(changedFiles, [oldSource, newSource]);

    const policy = structuredClone(POLICY);
    policy.modules["feature/old/domain"] = {
        line: { missed: 1, covered: 9 },
        branch: { missed: 1, covered: 3 },
    };
    const knownModules = includeBaselineModules(["feature/new/domain"], policy);
    const changedModules = selectChangedModules(changedFiles, knownModules);
    assert.deepEqual(changedModules, ["feature/new/domain", "feature/old/domain"]);

    const evaluation = evaluateCoveragePolicy({
        policy,
        modules: [
            moduleCoverage(
                "feature/new/domain",
                { missed: 1, covered: 1 },
                { missed: 0, covered: 0 },
            ),
            {
                name: "feature/old/domain",
                coverage: coverageForSourceKeys(parseKoverXml(REPORT), new Set()),
            },
        ],
    });
    assert.equal(
        evaluation.modules.find(({ name }) => name === "feature/new/domain")?.result,
        "untracked",
    );
    assert.deepEqual(
        evaluation.regressions.map(({ module, type, status }) => ({ module, type, status })),
        [
            { module: "feature/old/domain", type: "LINE", status: "missing" },
            { module: "feature/old/domain", type: "BRANCH", status: "missing" },
        ],
    );
});

test("collects package and file keys from main and debug source sets", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "kover-summary-"));
    await fs.mkdir(path.join(root, "app/src/main/kotlin/com/example/app"), { recursive: true });
    await fs.mkdir(path.join(root, "app/src/debug/java/com/example/debug"), { recursive: true });
    await fs.writeFile(
        path.join(root, "app/src/main/kotlin/com/example/app/Home.kt"),
        "package com.example.app\nclass Home\n",
    );
    await fs.writeFile(
        path.join(root, "app/src/debug/java/com/example/debug/DebugOnly.java"),
        "package com.example.debug; class DebugOnly {}\n",
    );

    assert.deepEqual(
        [...(await collectModuleSourceKeys(root, "app"))].sort(),
        ["com.example.app:Home.kt", "com.example.debug:DebugOnly.java"],
    );
});

test("sums only source files owned by a changed module", () => {
    const report = parseKoverXml(REPORT);
    const coverage = coverageForSourceKeys(
        report,
        new Set(["com.example.app:Home.kt", "com.example.app:Missing.kt"]),
    );

    assert.deepEqual(coverage.counters.LINE, { missed: 2, covered: 8 });
    assert.deepEqual(coverage.counters.BRANCH, { missed: 1, covered: 3 });
    assert.equal(coverage.matchedSources, 1);
    assert.equal(coverage.declaredSources, 2);
});

test("pins the committed baseline to the develop revision it was measured at", async () => {
    const policy = await loadCoveragePolicy(COMMITTED_POLICY_PATH);

    assert.equal(policy.mode, "warn");
    assert.equal(policy.source.ref, "develop");
    assert.equal(policy.source.sha, "1ed5007a526ba91abe5af4473b83d998dd00a4d3");
    assert.equal(
        policy.source.runUrl,
        "https://github.com/Team-CareerCompass/CareerCompass-FE/actions/runs/34664218494",
    );

    // 그 커밋 트리에서 모듈별 :koverXmlReportCi 가 낸 집계 counter 그대로다. PR 레인이
    // 모듈 리포트를 읽어 비교하므로 기준선도 같은 단위로 잰다. 측정 없이 손으로 숫자를
    // 고치면 여기서 걸린다.
    assert.deepEqual(policy.modules["core/common"], {
        line: { missed: 2, covered: 71 },
        branch: { missed: 10, covered: 68 },
    });
    assert.deepEqual(policy.modules["feature/onboarding/data"], {
        line: { missed: 3, covered: 34 },
        branch: { missed: 2, covered: 6 },
    });
});

test("leaves a module unmeasured only while it ships no production source", async () => {
    const policy = await loadCoveragePolicy(COMMITTED_POLICY_PATH);

    const unmeasured = [];
    const sourceless = [];
    for (const [module, baseline] of Object.entries(policy.modules)) {
        if (baseline.line === null) {
            unmeasured.push(module);
            assert.equal(baseline.branch, null, `${module} has a branch baseline without a line one`);
        }
        if ((await collectModuleSourceKeys(REPOSITORY_ROOT, module)).size === 0) {
            sourceless.push(module);
        }
    }

    // 골격만 있는 모듈은 잴 것이 없어 null 이다. 소스가 들어왔는데도 null 로 남아 있으면
    // 기준선 갱신이 밀린 것이므로 그 모듈 이름이 여기서 드러난다.
    assert.deepEqual(unmeasured.sort(), sourceless.sort());
    assert.ok(unmeasured.length < Object.keys(policy.modules).length / 2);
});

test("compares changed modules against the committed baseline instead of calling them new", async () => {
    const policy = await loadCoveragePolicy(COMMITTED_POLICY_PATH);
    const measured = Object.entries(policy.modules).filter(([, baseline]) => baseline.line !== null);
    const atBaseline = measured.map(([name, baseline]) =>
        moduleCoverage(name, { ...baseline.line }, { ...baseline.branch }),
    );

    const evaluation = evaluateCoveragePolicy({ policy, modules: atBaseline });
    assert.deepEqual(evaluation.regressions, []);
    assert.deepEqual(
        [...new Set(evaluation.modules.flatMap((module) => [
            module.metrics.LINE.status,
            module.metrics.BRANCH.status,
        ]))],
        ["pass"],
    );

    const summary = renderSummary({
        aggregate: { LINE: { missed: 0, covered: 1 }, BRANCH: { missed: 0, covered: 1 } },
        modules: atBaseline,
        artifactUrl: "https://example.invalid/artifact",
        policyEvaluation: evaluation,
    });
    assert.doesNotMatch(summary, /\(new\)/);
    assert.match(summary, /\| `core\/common` \| 97\.26% vs 97\.26% \(\+0\.00 pp\) \|/);

    const dropped = evaluateCoveragePolicy({
        policy,
        modules: [
            moduleCoverage("feature/feed/domain", { missed: 22, covered: 192 }, { missed: 13, covered: 129 }),
        ],
    });
    assert.deepEqual(
        dropped.regressions.map(({ module, type, status }) => ({ module, type, status })),
        [{ module: "feature/feed/domain", type: "LINE", status: "regression" }],
    );
});

test("rejects missing metrics and zero-total baseline counters", () => {
    const missingMetric = structuredClone(POLICY);
    delete missingMetric.modules.app.branch;
    assert.throws(
        () => validateCoveragePolicy(missingMetric),
        /baseline app\.branch is required/,
    );

    const zeroTotal = structuredClone(POLICY);
    zeroTotal.modules.app.line = { missed: 0, covered: 0 };
    assert.throws(
        () => validateCoveragePolicy(zeroTotal),
        /must be null when no coverage counter is measurable/,
    );
});

test("detects exact line regression and missing branch coverage without a base artifact", () => {
    const evaluation = evaluateCoveragePolicy({
        policy: POLICY,
        modules: [
            moduleCoverage(
                "app",
                { missed: 21, covered: 79 },
                { missed: 0, covered: 0 },
            ),
        ],
    });

    assert.equal(evaluation.mode, "warn");
    assert.deepEqual(
        evaluation.regressions.map(({ module, type, status }) => ({ module, type, status })),
        [
            { module: "app", type: "LINE", status: "regression" },
            { module: "app", type: "BRANCH", status: "missing" },
        ],
    );
    assert.match(renderPolicyAnnotations(evaluation)[0], /^::warning /);
    assert.match(renderPolicyAnnotations(evaluation)[1], /not measurable/);

    const enforced = evaluateCoveragePolicy({
        policy: POLICY,
        mode: "enforce",
        modules: [moduleCoverage("app", { missed: 21, covered: 79 }, { missed: 1, covered: 3 })],
    });
    assert.match(renderPolicyAnnotations(enforced)[0], /^::error /);
});

test("handles zero-test, newly measurable, and renamed untracked modules", () => {
    const evaluation = evaluateCoveragePolicy({
        policy: POLICY,
        modules: [
            moduleCoverage("empty", { missed: 0, covered: 0 }, { missed: 0, covered: 0 }),
            moduleCoverage("feature/renamed/data", { missed: 1, covered: 1 }, { missed: 0, covered: 0 }),
        ],
    });

    assert.equal(evaluation.modules[0].result, "not-applicable");
    assert.equal(evaluation.modules[1].result, "untracked");
    assert.equal(evaluation.regressions.length, 0);
    assert.match(renderPolicyAnnotations(evaluation)[0], /^::notice /);

    const newlyMeasurablePolicy = structuredClone(POLICY);
    newlyMeasurablePolicy.modules.app.branch = null;
    const newlyMeasurable = evaluateCoveragePolicy({
        policy: newlyMeasurablePolicy,
        modules: [moduleCoverage("app", { missed: 2, covered: 8 }, { missed: 1, covered: 1 })],
    });
    assert.equal(newlyMeasurable.modules[0].metrics.BRANCH.status, "newly-measurable");
    assert.equal(newlyMeasurable.regressions.length, 0);
});

test("does not warn when changed-module line and branch ratios hold or improve", () => {
    const evaluation = evaluateCoveragePolicy({
        policy: POLICY,
        modules: [moduleCoverage("app", { missed: 20, covered: 80 }, { missed: 2, covered: 8 })],
    });

    assert.equal(evaluation.modules[0].result, "pass");
    assert.equal(evaluation.regressions.length, 0);
    assert.deepEqual(renderPolicyAnnotations(evaluation), []);
});

test("renders artifact link, aggregate totals, changed modules, and report-only policy", () => {
    const report = parseKoverXml(REPORT);
    const summary = renderSummary({
        aggregate: report.aggregate,
        artifactUrl: "https://example.test/artifact",
        modules: [
            {
                name: "app",
                coverage: coverageForSourceKeys(report, new Set(["com.example.app:Home.kt"])),
            },
        ],
    });

    assert.match(summary, /https:\/\/example\.test\/artifact/);
    assert.match(summary, /Aggregate line: \*\*65\.00% \(13\/20\)\*\*/);
    assert.match(summary, /`app` \| 80\.00% \(8\/10\) \| 75\.00% \(3\/4\)/);
    assert.match(summary, /no coverage percentage threshold is enforced/);
});

test("preserves the coverage summary and appends warning-only ratchet evidence", () => {
    const report = parseKoverXml(REPORT);
    const modules = [
        {
            name: "app",
            coverage: coverageForSourceKeys(report, new Set(["com.example.app:Home.kt"])),
        },
    ];
    const policyEvaluation = evaluateCoveragePolicy({ policy: POLICY, modules });
    const summary = renderSummary({
        aggregate: report.aggregate,
        artifactUrl: "https://example.test/artifact",
        modules,
        policyEvaluation,
    });

    assert.match(summary, /### Changed modules/);
    assert.match(summary, /`app` \| 80\.00% \(8\/10\) \| 75\.00% \(3\/4\)/);
    assert.match(summary, /### Coverage non-regression/);
    assert.match(summary, /Policy mode: `warn`/);
    assert.match(summary, /Warning-only non-regression baseline/);
    assert.match(summary, /No absolute coverage percentage threshold is enforced/);
});

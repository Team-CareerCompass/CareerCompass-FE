import assert from "node:assert/strict";
import test from "node:test";

import {
    CLASSPATH_SCOPES,
    classifyUpdate,
    classpathScopesOf,
    compareVersions,
    consistencyFindings,
    detectCatalogUsage,
    parseBomPom,
    parseResolvedDependencies,
    parseVersionCatalog,
    renderSummary,
    firstPatchedVersion,
    stableReleaseAtOrAbove,
} from "./collect-dependency-audit.mjs";

const catalogText = `
[versions]
kotlin = "2.4.0"
composeBom = "2026.06.01"
runtime = "1.10.6"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-runtime = { group = "androidx.compose.runtime", name = "runtime", version.ref = "runtime" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
unused = { module = "example:unused", version = "1.0.0" }

[plugins]
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
`;

test("parses version catalog coordinates and refs", () => {
    const catalog = parseVersionCatalog(catalogText);
    assert.equal(catalog.versions.kotlin, "2.4.0");
    assert.equal(catalog.libraries["androidx-compose-runtime"].coordinate, "androidx.compose.runtime:runtime");
    assert.equal(catalog.plugins["kotlin-compose"].id, "org.jetbrains.kotlin.plugin.compose");
});

test("finds generated accessors and convention-plugin string aliases only", () => {
    const catalog = parseVersionCatalog(catalogText);
    const usage = detectCatalogUsage(catalog, [
        {
            path: "feature/build.gradle.kts",
            content: "implementation(libs.androidx.compose.runtime)\nalias(libs.plugins.kotlin.compose)",
        },
        {
            path: "build-logic/Compose.kt",
            content: 'val bom = libs.findLibrary("androidx-compose-bom").get()',
        },
    ]);
    assert.deepEqual(Object.keys(usage.libraries).sort(), [
        "androidx-compose-bom",
        "androidx-compose-runtime",
    ]);
    assert.deepEqual(Object.keys(usage.plugins), ["kotlin-compose"]);
    assert.equal(usage.libraries.unused, undefined);
});

test("orders prerelease channels below the matching stable release", () => {
    assert.ok(compareVersions("1.0.0", "1.0.0-rc13") > 0);
    assert.ok(compareVersions("1.0.0-rc13", "1.0.0-rc02") > 0);
    assert.equal(classifyUpdate("1.0.0-rc13", "1.0.0"), "channel");
    assert.equal(classifyUpdate("1.9.0", "2.0.0"), "major");
    assert.equal(classifyUpdate("2.4.0", "2.4.1"), "patch");
});

test("extracts BOM-managed versions", () => {
    const managed = parseBomPom(`
        <project>
          <properties><runtime.version>1.11.4</runtime.version></properties>
          <dependencyManagement><dependencies>
            <dependency>
              <groupId>androidx.compose.runtime</groupId>
              <artifactId>runtime</artifactId>
              <version>\${runtime.version}</version>
            </dependency>
          </dependencies></dependencyManagement>
        </project>
    `);
    assert.equal(managed["androidx.compose.runtime:runtime"], "1.11.4");
});

test("extracts selected Gradle versions after conflict resolution", () => {
    const dependencies = parseResolvedDependencies(
        "+--- androidx.compose.runtime:runtime:1.10.6 -> 1.11.4\n" +
            "+--- unused.constraint:only:9.9.9 (c)\n" +
            "\\--- com.squareup.okhttp3:okhttp:5.4.0",
        "app-runtime.txt",
    );
    assert.deepEqual(dependencies, [
        {
            coordinate: "androidx.compose.runtime:runtime",
            requestedVersion: "1.10.6",
            selectedVersion: "1.11.4",
            source: "app-runtime.txt",
            scope: "project",
        },
        {
            coordinate: "com.squareup.okhttp3:okhttp",
            requestedVersion: "5.4.0",
            selectedVersion: "5.4.0",
            source: "app-runtime.txt",
            scope: "project",
        },
    ]);
});

test("flags BOM and resolved-version mismatches", () => {
    const catalog = parseVersionCatalog(catalogText);
    const entries = [
        {
            alias: "androidx-compose-runtime",
            coordinate: "androidx.compose.runtime:runtime",
            currentVersion: "1.10.6",
            versionRef: "runtime",
            bom: {
                alias: "androidx-compose-bom",
                managedVersion: "1.11.4",
            },
        },
    ];
    const findings = consistencyFindings(
        entries,
        [{ coordinate: "androidx.compose.runtime:runtime", version: "1.11.4" }],
        catalog,
    );
    assert.deepEqual(
        findings.map((finding) => finding.type).sort(),
        ["bom-override-mismatch", "declared-resolved-mismatch"],
    );
});

test("tags resolved dependencies with the classpath the report describes", () => {
    const dependencies = parseResolvedDependencies(
        "+--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
        "root-build-environment.txt",
        CLASSPATH_SCOPES.build,
    );
    assert.equal(dependencies[0].scope, "build");
    assert.equal(
        parseResolvedDependencies("+--- a:b:1.0.0", "app-runtime.txt")[0].scope,
        "project",
    );
});

test("derives a catalog entry's classpath from where it is used", () => {
    assert.deepEqual(
        [...classpathScopesOf({ kind: "plugin", alias: "kotlin-jvm", references: ["build.gradle.kts:38"] })],
        ["build"],
    );
    assert.deepEqual(
        [...classpathScopesOf({ kind: "library", references: ["build-logic/build.gradle.kts:16"] })],
        ["build"],
    );
    assert.deepEqual(
        [...classpathScopesOf({ kind: "library", references: ["core/domain/build.gradle.kts:8"] })],
        ["project"],
    );
    assert.deepEqual(
        [...classpathScopesOf({
            kind: "library",
            references: ["build-logic/build.gradle.kts:21", "core/domain/build.gradle.kts:14"],
        })].sort(),
        ["build", "project"],
    );
    assert.deepEqual([...classpathScopesOf({ kind: "library" })].sort(), ["build", "project"]);
});

// #312: KGP·AGP 가 플러그인 클래스패스에 끌어온 coroutines 1.9.0 을 앱 라이브러리 선언 1.11.0 과
// 견줘 «선언과 해석이 다르다» 고 오탐했다. 같은 클래스패스끼리만 비교해야 한다.
test("does not compare a project library against the plugin classpath", () => {
    const catalog = parseVersionCatalog(catalogText);
    const coroutines = {
        alias: "coroutines-core",
        kind: "library",
        coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core",
        currentVersion: "1.11.0",
        references: ["core/domain/build.gradle.kts:8"],
    };
    const resolved = [
        { coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core", version: "1.9.0", scopes: ["build"] },
        { coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core", version: "1.11.0", scopes: ["project"] },
    ];
    assert.deepEqual(consistencyFindings([coroutines], resolved, catalog), []);

    // 같은 클래스패스에서 어긋나면 여전히 잡는다 — 프로젝트 쪽과 플러그인 쪽 각각.
    const projectDrift = [
        { coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core", version: "1.10.2", scopes: ["project"] },
    ];
    assert.deepEqual(
        consistencyFindings([coroutines], projectDrift, catalog).map((finding) => finding.selectedVersion),
        ["1.10.2"],
    );
    const kotlinGradlePlugin = {
        alias: "kotlin-gradlePlugin",
        kind: "library",
        coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
        currentVersion: "2.4.0",
        references: ["build-logic/build.gradle.kts:16"],
    };
    const buildDrift = [
        { coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin", version: "2.3.0", scopes: ["build"] },
        { coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin", version: "2.4.0", scopes: ["project"] },
    ];
    assert.deepEqual(
        consistencyFindings([kotlinGradlePlugin], buildDrift, catalog).map((finding) => finding.selectedVersion),
        ["2.3.0"],
    );

    // scope 가 없는 해석 결과(옛 호출자)는 예전처럼 모든 항목과 견준다.
    assert.equal(
        consistencyFindings(
            [coroutines],
            [{ coordinate: "org.jetbrains.kotlinx:kotlinx-coroutines-core", version: "1.9.0" }],
            catalog,
        ).length,
        1,
    );
});

test("flags Kotlin toolchain versions that drift apart", () => {
    const catalog = parseVersionCatalog(catalogText);
    const findings = consistencyFindings(
        [
            {
                alias: "kotlin-gradlePlugin",
                coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
                currentVersion: "2.4.0",
                versionRef: "kotlin",
            },
            {
                alias: "compose-compiler-gradle-plugin",
                coordinate: "org.jetbrains.kotlin:compose-compiler-gradle-plugin",
                currentVersion: "2.3.0",
                versionRef: "kotlin",
            },
        ],
        [],
        catalog,
    );
    assert.match(
        findings.find((finding) => finding.type === "toolchain-version-mismatch").message,
        /Kotlin\/Compose compiler/,
    );
});

test("renders a compact CI summary", () => {
    const summary = renderSummary({
        generatedAt: "2026-08-21T00:00:00.000Z",
        commitSha: "abc123",
        entries: [
            {
                alias: "okhttp",
                currentVersion: "5.4.0",
                latestStable: "6.0.0",
                latestInChannel: "5.5.0",
                updateKind: "major",
                metadata: { url: "https://repo1.maven.org/example" },
            },
        ],
        vulnerabilities: [],
        consistencyFindings: [],
        compatibility: { exitCode: 0 },
        coverage: {
            usedEntries: 1,
            resolvedPackages: 1,
            metadataGaps: 0,
            gaps: [],
        },
    });
    assert.match(summary, /okhttp/);
    assert.match(summary, /현재 호환성 검사: 통과/);
});

// GHSA-r937-wjx7-w2jp 의 실제 OSV 응답 모양이다 (2026-08-31 실측).
const kotlinAdvisory = {
    id: "GHSA-r937-wjx7-w2jp",
    database_specific: { severity: "MODERATE" },
    affected: [
        {
            package: { name: "org.jetbrains.kotlin:kotlin-gradle-plugin", ecosystem: "Maven" },
            ranges: [{ type: "ECOSYSTEM", events: [{ introduced: "0" }, { fixed: "2.4.20-Beta1" }] }],
        },
    ],
};

test("reads the first patched version for the coordinate the advisory actually names", () => {
    assert.equal(
        firstPatchedVersion(kotlinAdvisory, "org.jetbrains.kotlin:kotlin-gradle-plugin", "2.4.10"),
        "2.4.20-Beta1",
    );
    // 같은 권고가 다른 좌표도 담을 수 있다. 우리 좌표가 아닌 갈래를 읽으면 엉뚱한 버전으로 올린다.
    assert.equal(firstPatchedVersion(kotlinAdvisory, "org.jetbrains.kotlin:kotlin-stdlib", "2.4.10"), null);
});

test("picks the fix for the branch the project is actually on", () => {
    const advisory = {
        affected: [
            {
                package: { name: "org.example:lib", ecosystem: "Maven" },
                ranges: [
                    { events: [{ introduced: "0" }, { fixed: "1.5.0" }] },
                    { events: [{ introduced: "2.0.0" }, { fixed: "2.3.0" }] },
                ],
            },
        ],
    };
    // 2.x 를 쓰는데 1.5.0 을 권하면 다운그레이드다.
    assert.equal(firstPatchedVersion(advisory, "org.example:lib", "2.1.0"), "2.3.0");
    assert.equal(firstPatchedVersion(advisory, "org.example:lib", "1.2.0"), "1.5.0");
});

test("reports no stable fix while every patched release is still a prerelease", () => {
    // #986 의 상태 그대로다 — fixed 는 2.4.20-Beta1 인데 정식은 2.4.10 까지밖에 없다.
    const released = ["2.4.0", "2.4.10", "2.4.20-Beta1", "2.4.20-Beta2", "2.4.20-RC", "2.4.20-RC2"];
    assert.equal(stableReleaseAtOrAbove(released, "2.4.20-Beta1"), null);
    // 정식이 나오면 바로 그 버전을 집는다. 이 전환이 보류해 둔 이슈를 깨우는 신호다.
    assert.equal(stableReleaseAtOrAbove([...released, "2.4.20"], "2.4.20-Beta1"), "2.4.20");
    // 이미 지나간 권고라면 현재 정식 중 가장 낮은 해소 버전을 집는다.
    assert.equal(stableReleaseAtOrAbove([...released, "2.4.20", "2.4.21"], "2.4.20-Beta1"), "2.4.20");
});

test("summarizes whether a security finding has a stable version to move to", () => {
    const base = {
        generatedAt: "2026-08-21T00:00:00.000Z",
        commitSha: "abc123",
        entries: [],
        consistencyFindings: [],
        compatibility: { exitCode: 0 },
        coverage: { usedEntries: 1, resolvedPackages: 1, metadataGaps: 0, gaps: [] },
    };
    const held = renderSummary({
        ...base,
        vulnerabilities: [
            {
                coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
                version: "2.4.10",
                vulnerabilities: [{ id: "GHSA-r937-wjx7-w2jp", firstPatched: "2.4.20-Beta1", firstPatchedStable: null }],
                stableFixVersion: null,
            },
        ],
    });
    assert.match(held, /정식 패치판 없음 \(최초 패치 2\.4\.20-Beta1\)/);

    const fixed = renderSummary({
        ...base,
        vulnerabilities: [
            {
                coordinate: "org.jetbrains.kotlin:kotlin-gradle-plugin",
                version: "2.4.10",
                vulnerabilities: [{ id: "GHSA-r937-wjx7-w2jp", firstPatched: "2.4.20-Beta1", firstPatchedStable: "2.4.20" }],
                stableFixVersion: "2.4.20",
            },
        ],
    });
    assert.match(fixed, /정식 패치판 `2\.4\.20`/);
});

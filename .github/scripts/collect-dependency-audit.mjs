#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const REPOSITORIES = {
    central: "https://repo1.maven.org/maven2",
    google: "https://dl.google.com/dl/android/maven2",
    jitpack: "https://jitpack.io",
    kakao: "https://devrepo.kakao.com/nexus/content/groups/public",
    pluginPortal: "https://plugins.gradle.org/m2",
};

const PLUGIN_COORDINATES = {
    "com.android.application": "com.android.tools.build:gradle",
    "com.android.library": "com.android.tools.build:gradle",
    "com.android.compose.screenshot": "com.android.tools.screenshot:screenshot-gradle-plugin",
    "com.google.dagger.hilt.android": "com.google.dagger:hilt-android-gradle-plugin",
    "com.google.devtools.ksp": "com.google.devtools.ksp:symbol-processing-gradle-plugin",
    "com.google.firebase.appdistribution": "com.google.firebase:firebase-appdistribution-gradle",
    "com.google.firebase.crashlytics": "com.google.firebase:firebase-crashlytics-gradle",
    "com.google.gms.google-services": "com.google.gms:google-services",
    "org.jetbrains.kotlin.jvm": "org.jetbrains.kotlin:kotlin-gradle-plugin",
    "org.jetbrains.kotlin.plugin.compose": "org.jetbrains.kotlin:compose-compiler-gradle-plugin",
    "org.jetbrains.kotlin.plugin.serialization": "org.jetbrains.kotlin:kotlin-gradle-plugin",
    "org.jlleitschuh.gradle.ktlint": "org.jlleitschuh.gradle:ktlint-gradle",
};

const SOURCE_EXTENSIONS = [".gradle.kts", ".kt"];
const ALIGNMENT_GROUPS = [
    {
        name: "Kotlin/Compose compiler",
        aliases: new Set([
            "compose-compiler-gradle-plugin",
            "jetbrains-kotlin-jvm",
            "kotlin-compose",
            "kotlin-gradlePlugin",
            "kotlin-serialization",
        ]),
    },
    {
        name: "Android Gradle Plugin",
        aliases: new Set(["android-application", "android-gradlePlugin", "android-library"]),
    },
    {
        name: "Hilt",
        aliases: new Set(["hilt-android", "hilt-compiler", "hilt-android"]),
    },
];
const IGNORED_DIRECTORIES = new Set([
    ".git",
    ".gradle",
    ".idea",
    ".claude",
    "build",
]);

function unique(values) {
    return [...new Set(values)];
}

function stripTomlComment(line) {
    let quoted = false;
    let escaped = false;
    for (let index = 0; index < line.length; index += 1) {
        const character = line[index];
        if (escaped) {
            escaped = false;
            continue;
        }
        if (character === "\\" && quoted) {
            escaped = true;
            continue;
        }
        if (character === '"') {
            quoted = !quoted;
            continue;
        }
        if (character === "#" && !quoted) {
            return line.slice(0, index);
        }
    }
    return line;
}

function parseInlineTable(value) {
    const fields = {};
    const matcher = /([A-Za-z0-9_.-]+)\s*=\s*"((?:\\.|[^"])*)"/g;
    for (const match of value.matchAll(matcher)) {
        fields[match[1]] = match[2].replaceAll('\\"', '"').replaceAll("\\\\", "\\");
    }
    return fields;
}

export function parseVersionCatalog(content) {
    const catalog = {
        versions: {},
        libraries: {},
        plugins: {},
    };
    let section = "";

    for (const rawLine of String(content).split(/\r?\n/)) {
        const line = stripTomlComment(rawLine).trim();
        if (!line) {
            continue;
        }
        const sectionMatch = /^\[([^\]]+)]$/.exec(line);
        if (sectionMatch) {
            section = sectionMatch[1];
            continue;
        }
        const assignment = /^([A-Za-z0-9_.-]+)\s*=\s*(.+)$/.exec(line);
        if (!assignment) {
            continue;
        }
        const [, alias, rawValue] = assignment;
        if (section === "versions") {
            const version = /^"((?:\\.|[^"])*)"$/.exec(rawValue)?.[1];
            if (version !== undefined) {
                catalog.versions[alias] = version;
            }
            continue;
        }
        if (section !== "libraries" && section !== "plugins") {
            continue;
        }
        const fields = parseInlineTable(rawValue);
        if (section === "libraries") {
            const coordinate = fields.module ??
                (fields.group && fields.name ? `${fields.group}:${fields.name}` : null);
            catalog.libraries[alias] = {
                alias,
                coordinate,
                version: fields.version ?? null,
                versionRef: fields["version.ref"] ?? null,
            };
        } else {
            catalog.plugins[alias] = {
                alias,
                id: fields.id ?? null,
                version: fields.version ?? null,
                versionRef: fields["version.ref"] ?? null,
            };
        }
    }
    return catalog;
}

function accessorFor(alias) {
    return alias.replaceAll(/[-_]/g, ".");
}

function lineNumberAt(content, index) {
    return content.slice(0, index).split("\n").length;
}

function findOccurrences(content, needles) {
    const lines = new Set();
    for (const needle of needles) {
        let index = content.indexOf(needle);
        while (index >= 0) {
            lines.add(lineNumberAt(content, index));
            index = content.indexOf(needle, index + needle.length);
        }
    }
    return [...lines].sort((left, right) => left - right);
}

export function detectCatalogUsage(catalog, sources) {
    const usage = {
        libraries: {},
        plugins: {},
    };

    for (const [kind, entries] of Object.entries({
        libraries: catalog.libraries,
        plugins: catalog.plugins,
    })) {
        for (const alias of Object.keys(entries)) {
            const accessorPrefix = kind === "plugins" ? "libs.plugins." : "libs.";
            const accessor = `${accessorPrefix}${accessorFor(alias)}`;
            const stringNeedles = [
                `implementation("${alias}")`,
                `api("${alias}")`,
                `ksp("${alias}")`,
                `testImplementation("${alias}")`,
                `androidTestImplementation("${alias}")`,
                `debugImplementation("${alias}")`,
                `findLibrary("${alias}")`,
            ];
            const references = [];
            for (const source of sources) {
                const lines = findOccurrences(source.content, [accessor, ...stringNeedles]);
                references.push(...lines.map((line) => `${source.path}:${line}`));
            }
            if (references.length > 0) {
                usage[kind][alias] = references;
            }
        }
    }
    return usage;
}

async function collectSourceFiles(root) {
    const sources = [];
    async function walk(directory) {
        const entries = await fs.readdir(directory, { withFileTypes: true });
        for (const entry of entries) {
            if (IGNORED_DIRECTORIES.has(entry.name)) {
                continue;
            }
            const absolutePath = path.join(directory, entry.name);
            if (entry.isDirectory()) {
                await walk(absolutePath);
                continue;
            }
            if (!SOURCE_EXTENSIONS.some((extension) => entry.name.endsWith(extension))) {
                continue;
            }
            sources.push({
                path: path.relative(root, absolutePath),
                content: await fs.readFile(absolutePath, "utf8"),
            });
        }
    }
    await walk(root);
    return sources;
}

function channelOf(version) {
    const normalized = String(version ?? "").toLowerCase();
    if (/snapshot|dev/.test(normalized)) {
        return "snapshot";
    }
    if (/eap/.test(normalized)) {
        return "eap";
    }
    if (/(?:^|[-.])m\d+/.test(normalized)) {
        return "milestone";
    }
    if (/alpha/.test(normalized)) {
        return "alpha";
    }
    if (/beta/.test(normalized)) {
        return "beta";
    }
    if (/(?:^|[-.])rc\d*/.test(normalized)) {
        return "rc";
    }
    return "stable";
}

function numericParts(version) {
    const normalized = String(version ?? "");
    const qualifierIndex = normalized.search(
        /(?:[-.]?(?:alpha|beta|rc|snapshot|dev|eap)|[-.]m\d+)/i,
    );
    const core = qualifierIndex >= 0 ? normalized.slice(0, qualifierIndex) : normalized;
    return (core.match(/\d+/g) ?? []).map(Number);
}

function qualifierNumber(version) {
    return Number(/(?:alpha|beta|rc|eap|(?:^|[-.])m)[.-]?(\d+)/i.exec(String(version))?.[1] ?? 0);
}

export function compareVersions(left, right) {
    const leftParts = numericParts(left);
    const rightParts = numericParts(right);
    const length = Math.max(leftParts.length, rightParts.length);
    for (let index = 0; index < length; index += 1) {
        const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
        if (difference !== 0) {
            return difference;
        }
    }
    const ranks = {
        snapshot: 0,
        eap: 1,
        milestone: 2,
        alpha: 3,
        beta: 4,
        rc: 5,
        stable: 6,
    };
    const channelDifference = ranks[channelOf(left)] - ranks[channelOf(right)];
    if (channelDifference !== 0) {
        return channelDifference;
    }
    const qualifierDifference = qualifierNumber(left) - qualifierNumber(right);
    if (qualifierDifference !== 0) {
        return qualifierDifference;
    }
    return String(left).localeCompare(String(right), "en", { numeric: true });
}

export function classifyUpdate(currentVersion, targetVersion) {
    if (!currentVersion || !targetVersion || compareVersions(targetVersion, currentVersion) <= 0) {
        return "none";
    }
    const current = numericParts(currentVersion);
    const target = numericParts(targetVersion);
    if ((target[0] ?? 0) !== (current[0] ?? 0)) {
        return "major";
    }
    if ((target[1] ?? 0) !== (current[1] ?? 0)) {
        return "minor";
    }
    if ((target[2] ?? 0) !== (current[2] ?? 0)) {
        return "patch";
    }
    return "channel";
}

function latestVersion(versions, channel = null) {
    const filtered = versions.filter((version) => !channel || channelOf(version) === channel);
    return filtered.sort(compareVersions).at(-1) ?? null;
}

function coordinatePath(coordinate) {
    const [group, artifact] = coordinate.split(":");
    return `${group.replaceAll(".", "/")}/${artifact}`;
}

function repositoryOrder(coordinate, pluginId = null) {
    const group = coordinate.split(":")[0];
    const ordered = [];
    if (/^(androidx|com\.android|com\.google)/.test(group)) {
        ordered.push("google");
    }
    if (group.startsWith("com.kakao")) {
        ordered.push("kakao");
    }
    if (group.startsWith("com.github")) {
        ordered.push("jitpack");
    }
    ordered.push("central", "google", "kakao", "jitpack");
    ordered.push("pluginPortal");
    return [...new Set(ordered)];
}

async function fetchText(url, fetchImpl) {
    const response = await fetchImpl(url, {
        headers: { "User-Agent": "CareerCompass-dependency-audit" },
        signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return response.text();
}

async function fetchCoordinateFile(entry, suffix, fetchImpl) {
    const attempts = [];
    const coordinateCandidates = [entry.coordinate];
    if (entry.pluginId) {
        coordinateCandidates.push(`${entry.pluginId}:${entry.pluginId}.gradle.plugin`);
    }
    for (const coordinate of unique(coordinateCandidates)) {
        for (const repositoryName of repositoryOrder(coordinate, entry.pluginId)) {
            const url = `${REPOSITORIES[repositoryName]}/${coordinatePath(coordinate)}/${suffix}`;
            try {
                return {
                    text: await fetchText(url, fetchImpl),
                    repository: repositoryName,
                    coordinate,
                    url,
                    attempts,
                };
            } catch (error) {
                attempts.push({
                    repository: repositoryName,
                    coordinate,
                    url,
                    error: error instanceof Error ? error.message : String(error),
                });
            }
        }
    }
    return { text: null, repository: null, coordinate: null, url: null, attempts };
}

function decodeXml(value) {
    return String(value)
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&amp;", "&")
        .replaceAll("&quot;", '"')
        .replaceAll("&apos;", "'");
}

export function parseBomPom(content) {
    const properties = {};
    const propertiesBlock = /<properties>([\s\S]*?)<\/properties>/.exec(content)?.[1] ?? "";
    for (const match of propertiesBlock.matchAll(/<([A-Za-z0-9_.-]+)>([^<]+)<\/\1>/g)) {
        properties[match[1]] = decodeXml(match[2].trim());
    }
    const dependencyManagement =
        /<dependencyManagement>([\s\S]*?)<\/dependencyManagement>/.exec(content)?.[1] ?? "";
    const managed = {};
    for (const dependency of dependencyManagement.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
        const block = dependency[1];
        const group = /<groupId>([^<]+)<\/groupId>/.exec(block)?.[1]?.trim();
        const artifact = /<artifactId>([^<]+)<\/artifactId>/.exec(block)?.[1]?.trim();
        let version = /<version>([^<]+)<\/version>/.exec(block)?.[1]?.trim();
        if (!group || !artifact || !version) {
            continue;
        }
        version = version.replace(/\$\{([^}]+)}/g, (_, key) => properties[key] ?? `\${${key}}`);
        if (!version.includes("${")) {
            managed[`${group}:${artifact}`] = version;
        }
    }
    return managed;
}

// 해석 보고서가 어느 클래스패스를 설명하는지. `project` 는 모듈의 runtimeClasspath 류,
// `build` 는 루트 `buildEnvironment`(플러그인 클래스패스)다. 카탈로그 선언과 해석 결과를
// 비교할 때는 같은 클래스패스끼리만 견준다 — #312 는 KGP·AGP 가 끌어오는 coroutines 1.9.0
// (플러그인 클래스패스) 을 앱 라이브러리 선언 1.11.0 과 비교해 «선언과 해석이 다르다» 고
// 오탐했다. 앱·도메인 모듈은 전부 1.11.0 으로 해석되고 있었다.
export const CLASSPATH_SCOPES = Object.freeze({ project: "project", build: "build" });

export function parseResolvedDependencies(content, source = "", scope = CLASSPATH_SCOPES.project) {
    const dependencies = [];
    const matcher = /([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^\s()]+)(?:\s+->\s+([^\s()]+))?/g;
    for (const line of String(content).split(/\r?\n/)) {
        if (/\(c\)\s*$/.test(line.trim())) {
            continue;
        }
        for (const match of line.matchAll(matcher)) {
            const requestedVersion = match[3];
            const selectedVersion = match[4] ?? requestedVersion;
            if (requestedVersion.startsWith("{") || selectedVersion.startsWith("{")) {
                continue;
            }
            dependencies.push({
                coordinate: `${match[1]}:${match[2]}`,
                requestedVersion,
                selectedVersion: selectedVersion.replace(/[),]$/, ""),
                source,
                scope,
            });
        }
    }
    return dependencies;
}

function mergeResolvedDependencies(entries) {
    const merged = new Map();
    for (const entry of entries) {
        const key = `${entry.coordinate}@${entry.selectedVersion}`;
        const current = merged.get(key) ?? {
            coordinate: entry.coordinate,
            version: entry.selectedVersion,
            requests: [],
            sources: [],
            scopes: [],
        };
        current.requests.push(entry.requestedVersion);
        current.sources.push(entry.source);
        current.scopes.push(entry.scope ?? CLASSPATH_SCOPES.project);
        current.requests = [...new Set(current.requests)];
        current.sources = [...new Set(current.sources)];
        current.scopes = [...new Set(current.scopes)];
        merged.set(key, current);
    }
    return [...merged.values()].sort((left, right) =>
        `${left.coordinate}@${left.version}`.localeCompare(`${right.coordinate}@${right.version}`),
    );
}

async function mapWithConcurrency(values, concurrency, callback) {
    const results = new Array(values.length);
    let nextIndex = 0;
    async function worker() {
        while (nextIndex < values.length) {
            const index = nextIndex;
            nextIndex += 1;
            results[index] = await callback(values[index], index);
        }
    }
    await Promise.all(Array.from({ length: Math.min(concurrency, values.length) }, worker));
    return results;
}

async function resolveBomVersions(entries, fetchImpl, offline) {
    const bomEntries = entries.filter(
        (entry) => entry.kind === "library" && entry.coordinate?.endsWith("-bom") && entry.currentVersion,
    );
    const resolved = [];
    for (const entry of bomEntries) {
        if (offline) {
            resolved.push({ entry, managed: {}, source: null, attempts: [] });
            continue;
        }
        const result = await fetchCoordinateFile(
            entry,
            `${entry.currentVersion}/${entry.coordinate.split(":")[1]}-${entry.currentVersion}.pom`,
            fetchImpl,
        );
        resolved.push({
            entry,
            managed: result.text ? parseBomPom(result.text) : {},
            source: result.url,
            attempts: result.attempts,
        });
    }
    return resolved;
}

function applyBomVersions(entries, boms) {
    for (const entry of entries) {
        if (entry.kind !== "library" || !entry.coordinate || entry.coordinate.endsWith("-bom")) {
            continue;
        }
        for (const bom of boms) {
            const managedVersion = bom.managed[entry.coordinate];
            if (!managedVersion) {
                continue;
            }
            entry.bom = {
                alias: bom.entry.alias,
                version: bom.entry.currentVersion,
                managedVersion,
                source: bom.source,
            };
            if (!entry.currentVersion) {
                entry.currentVersion = managedVersion;
                entry.versionSource = `bom:${bom.entry.alias}`;
            }
            break;
        }
    }
}

async function collectMetadata(entries, fetchImpl, offline) {
    return mapWithConcurrency(entries, 8, async (entry) => {
        if (!entry.coordinate || !entry.currentVersion) {
            return { ...entry, metadata: null };
        }
        if (offline) {
            return { ...entry, metadata: { offline: true } };
        }
        const result = await fetchCoordinateFile(entry, "maven-metadata.xml", fetchImpl);
        const versions = result.text
            ? [...result.text.matchAll(/<version>([^<]+)<\/version>/g)].map((match) => decodeXml(match[1].trim()))
            : [];
        const currentChannel = channelOf(entry.currentVersion);
        const latestStable = latestVersion(versions, "stable");
        const latestInChannel = latestVersion(versions, currentChannel);
        return {
            ...entry,
            latestStable,
            latestInChannel,
            latestOverall: latestVersion(versions),
            updateKind: classifyUpdate(entry.currentVersion, latestStable),
            channelUpdateKind: classifyUpdate(entry.currentVersion, latestInChannel),
            metadata: {
                repository: result.repository,
                queriedCoordinate: result.coordinate,
                url: result.url,
                attempts: result.attempts,
                versionCount: versions.length,
            },
        };
    });
}

async function queryOsv(packages, fetchImpl, offline) {
    if (offline || packages.length === 0) {
        return { findings: [], errors: offline ? ["offline"] : [] };
    }
    const findings = [];
    const errors = [];
    for (let offset = 0; offset < packages.length; offset += 500) {
        const chunk = packages.slice(offset, offset + 500);
        try {
            const response = await fetchImpl("https://api.osv.dev/v1/querybatch", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "User-Agent": "CareerCompass-dependency-audit",
                },
                body: JSON.stringify({
                    queries: chunk.map((entry) => ({
                        version: entry.version,
                        package: { ecosystem: "Maven", name: entry.coordinate },
                    })),
                }),
                signal: AbortSignal.timeout(30_000),
            });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            const payload = await response.json();
            for (let index = 0; index < chunk.length; index += 1) {
                const vulnerabilities = payload.results?.[index]?.vulns ?? [];
                if (vulnerabilities.length === 0) {
                    continue;
                }
                findings.push({
                    ...chunk[index],
                    vulnerabilities: vulnerabilities.map((vulnerability) => ({
                        id: vulnerability.id,
                        modified: vulnerability.modified ?? null,
                    })),
                });
            }
        } catch (error) {
            errors.push(error instanceof Error ? error.message : String(error));
        }
    }
    return { findings, errors };
}

// querybatch 는 취약점 id 만 돌려준다. «정식 패치판이 나왔는가» 는 권고의 fixed 이벤트와 그
// 좌표가 실제로 배포한 버전 목록을 대조해야만 알 수 있어 상세 조회가 따로 필요하다.
//
// 이 조회가 없으면 감사는 «패치 버전이 프리릴리스뿐이라 대응을 보류한» 권고를 영영 깨우지
// 못한다. fingerprint 가 보는 것은 해석 버전과 취약점 목록뿐이라, 정식판 출시는 그 둘 중
// 어느 것도 바꾸지 않기 때문이다 — #986 을 닫으면서 남은 구멍으로 명시해 둔 자리다.
async function fetchAdvisory(id, fetchImpl) {
    const response = await fetchImpl(`https://api.osv.dev/v1/vulns/${encodeURIComponent(id)}`, {
        headers: { "User-Agent": "CareerCompass-dependency-audit" },
        signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
}

// 권고 하나가 여러 갈래를 동시에 고칠 수 있다(1.x 는 1.5 에서, 2.x 는 2.3 에서). 우리에게
// 의미 있는 fixed 는 «지금 쓰는 버전보다 위» 중 가장 낮은 것이다 — 그게 이 저장소가 실제로
// 올라가야 할 지점이다. 그런 갈래가 없으면(이미 최상위 갈래에 있으면) 가장 높은 fixed 로
// 떨어뜨려 판정 자체가 사라지지 않게 한다.
export function firstPatchedVersion(advisory, coordinate, currentVersion) {
    const fixes = [];
    for (const affected of advisory?.affected ?? []) {
        if (affected.package?.ecosystem !== "Maven" || affected.package?.name !== coordinate) {
            continue;
        }
        for (const range of affected.ranges ?? []) {
            for (const event of range.events ?? []) {
                if (event.fixed) {
                    fixes.push(event.fixed);
                }
            }
        }
    }
    if (fixes.length === 0) {
        return null;
    }
    const sorted = unique(fixes).sort(compareVersions);
    return sorted.find((version) => compareVersions(version, currentVersion) > 0) ?? sorted.at(-1);
}

// fixed 가 프리릴리스면(2.4.20-Beta1) 그 권고를 해소하는 «정식» 릴리스는 아직 없을 수 있다.
// 판정은 좌표가 실제로 배포한 목록에서 fixed 이상인 stable 중 가장 낮은 것을 찾는 것이다 —
// 없으면 null 이고, 그게 «프로덕션 툴체인을 베타로 올리지 않으면 못 고친다» 는 뜻이다.
export function stableReleaseAtOrAbove(versions, target) {
    if (!target) {
        return null;
    }
    return (versions ?? [])
        .filter((version) => channelOf(version) === "stable" && compareVersions(version, target) >= 0)
        .sort(compareVersions)
        .at(0) ?? null;
}

async function annotateAdvisories(findings, fetchImpl, offline) {
    const errors = [];
    if (offline || findings.length === 0) {
        return { findings, errors };
    }

    const advisories = new Map();
    for (const id of unique(findings.flatMap((finding) => finding.vulnerabilities.map((item) => item.id)))) {
        try {
            advisories.set(id, await fetchAdvisory(id, fetchImpl));
        } catch (error) {
            errors.push(`권고 상세 조회 실패: ${id} (${error instanceof Error ? error.message : String(error)})`);
        }
    }

    const releases = new Map();
    for (const coordinate of unique(findings.map((finding) => finding.coordinate))) {
        const result = await fetchCoordinateFile({ coordinate }, "maven-metadata.xml", fetchImpl);
        if (!result.text) {
            errors.push(`릴리스 목록 미확인: ${coordinate}`);
            continue;
        }
        releases.set(
            coordinate,
            [...result.text.matchAll(/<version>([^<]+)<\/version>/g)].map((match) => decodeXml(match[1].trim())),
        );
    }

    const annotated = findings.map((finding) => {
        const versions = releases.get(finding.coordinate) ?? [];
        const vulnerabilities = finding.vulnerabilities.map((item) => {
            const advisory = advisories.get(item.id);
            if (!advisory) {
                return { ...item, firstPatched: null, firstPatchedStable: null };
            }
            const firstPatched = firstPatchedVersion(advisory, finding.coordinate, finding.version);
            return {
                ...item,
                severity: advisory.database_specific?.severity ?? null,
                firstPatched,
                firstPatchedStable: stableReleaseAtOrAbove(versions, firstPatched),
            };
        });
        // 한 좌표에 권고가 여럿이면 «전부를 넘기는» 한 버전이어야 올릴 수 있다. 하나라도
        // 정식 패치판이 없으면 그 좌표는 아직 정식으로 해소되지 않는다.
        const stableFixVersion = vulnerabilities.every((item) => item.firstPatchedStable)
            ? vulnerabilities.map((item) => item.firstPatchedStable).sort(compareVersions).at(-1)
            : null;
        return {
            ...finding,
            vulnerabilities,
            latestStable: latestVersion(versions, "stable"),
            stableFixVersion,
        };
    });
    return { findings: annotated, errors };
}

function directEntries(catalog, usage) {
    const entries = [];
    for (const [alias, references] of Object.entries(usage.libraries)) {
        const library = catalog.libraries[alias];
        entries.push({
            kind: "library",
            alias,
            coordinate: library.coordinate,
            versionRef: library.versionRef,
            currentVersion: library.version ?? catalog.versions[library.versionRef] ?? null,
            versionSource: library.version ? "inline" : library.versionRef ? `versions.${library.versionRef}` : null,
            references,
        });
    }
    for (const [alias, references] of Object.entries(usage.plugins)) {
        const plugin = catalog.plugins[alias];
        const coordinate = PLUGIN_COORDINATES[plugin.id] ??
            (plugin.id ? `${plugin.id}:${plugin.id}.gradle.plugin` : null);
        entries.push({
            kind: "plugin",
            alias,
            pluginId: plugin.id,
            coordinate,
            versionRef: plugin.versionRef,
            currentVersion: plugin.version ?? catalog.versions[plugin.versionRef] ?? null,
            versionSource: plugin.version ? "inline" : plugin.versionRef ? `versions.${plugin.versionRef}` : null,
            references,
        });
    }
    return entries.sort((left, right) => `${left.kind}:${left.alias}`.localeCompare(`${right.kind}:${right.alias}`));
}

// 카탈로그 항목이 어느 클래스패스에 실리는지를 사용처로 판정한다. 플러그인 항목과
// `build-logic/`·`settings.gradle.kts` 에서만 쓰는 라이브러리(kotlin-gradlePlugin 등)는 플러그인
// 클래스패스, 모듈 빌드 스크립트에서 쓰는 라이브러리는 프로젝트 클래스패스다. 양쪽에서 쓰면
// 둘 다. 사용처를 모르는 항목은 어느 쪽 보고서와도 견준다(누락보다 오탐이 낫다).
export function classpathScopesOf(entry) {
    if (entry.kind === "plugin") {
        return new Set([CLASSPATH_SCOPES.build]);
    }
    const references = entry.references ?? [];
    if (references.length === 0) {
        return new Set([CLASSPATH_SCOPES.project, CLASSPATH_SCOPES.build]);
    }
    const scopes = new Set();
    for (const reference of references) {
        const filePath = reference.replace(/:\d+$/, "");
        const onBuildClasspath =
            filePath.startsWith("build-logic/") || filePath === "settings.gradle.kts";
        scopes.add(onBuildClasspath ? CLASSPATH_SCOPES.build : CLASSPATH_SCOPES.project);
    }
    return scopes;
}

function sharesClasspath(entryScopes, dependency) {
    const dependencyScopes = dependency.scopes ?? [dependency.scope ?? null];
    // scope 를 모르는 해석 결과(옛 호출자)는 예전처럼 모든 항목과 견준다.
    return dependencyScopes.some((scope) => scope === null || scope === undefined || entryScopes.has(scope));
}

export function consistencyFindings(entries, resolvedDependencies, catalog) {
    const findings = [];
    for (const entry of entries) {
        if (entry.versionRef && !(entry.versionRef in catalog.versions)) {
            findings.push({
                type: "missing-version-ref",
                alias: entry.alias,
                coordinate: entry.coordinate,
                message: `${entry.alias}가 없는 version ref ${entry.versionRef}를 참조합니다.`,
            });
        }
        if (!entry.currentVersion) {
            findings.push({
                type: "unresolved-version",
                alias: entry.alias,
                coordinate: entry.coordinate,
                message: `${entry.alias}의 실제 버전을 CI가 해석하지 못했습니다.`,
            });
        }
        if (entry.bom && entry.versionRef && entry.currentVersion !== entry.bom.managedVersion) {
            findings.push({
                type: "bom-override-mismatch",
                alias: entry.alias,
                coordinate: entry.coordinate,
                declaredVersion: entry.currentVersion,
                selectedVersion: entry.bom.managedVersion,
                bomAlias: entry.bom.alias,
                message: `${entry.alias} ${entry.currentVersion}이 ${entry.bom.alias}의 ${entry.bom.managedVersion}과 다릅니다.`,
            });
        }
        if (!entry.coordinate || !entry.currentVersion) {
            continue;
        }
        const entryScopes = classpathScopesOf(entry);
        const selectedVersions = resolvedDependencies
            .filter((dependency) => dependency.coordinate === entry.coordinate)
            .filter((dependency) => sharesClasspath(entryScopes, dependency))
            .map((dependency) => dependency.version)
            .filter((version) => version !== entry.currentVersion);
        for (const selectedVersion of [...new Set(selectedVersions)]) {
            findings.push({
                type: "declared-resolved-mismatch",
                alias: entry.alias,
                coordinate: entry.coordinate,
                declaredVersion: entry.currentVersion,
                selectedVersion,
                message: `${entry.alias} 선언 ${entry.currentVersion}이 Gradle에서 ${selectedVersion}으로 해석됩니다.`,
            });
        }
    }

    for (const group of ALIGNMENT_GROUPS) {
        const alignedEntries = entries.filter(
            (entry) => group.aliases.has(entry.alias) && entry.currentVersion,
        );
        const versions = [...new Set(alignedEntries.map((entry) => entry.currentVersion))];
        if (versions.length > 1) {
            findings.push({
                type: "toolchain-version-mismatch",
                alias: alignedEntries.map((entry) => entry.alias).join(","),
                coordinate: `toolchain:${group.name}`,
                message: `${group.name} 버전이 ${versions.join(", ")}로 갈립니다.`,
            });
        }
    }
    return findings.filter(
        (finding, index, all) =>
            index === all.findIndex(
                (candidate) =>
                    `${candidate.type}:${candidate.coordinate}:${candidate.selectedVersion ?? ""}` ===
                    `${finding.type}:${finding.coordinate}:${finding.selectedVersion ?? ""}`,
            ),
    );
}

function markdownCell(value) {
    return String(value ?? "-").replaceAll("|", "\\|").replaceAll("\n", " ");
}

export function renderSummary(audit) {
    const updates = audit.entries.filter(
        (entry) =>
            (entry.updateKind && entry.updateKind !== "none") ||
            (entry.channelUpdateKind && entry.channelUpdateKind !== "none"),
    );
    const lines = [
        "# Android dependency audit",
        "",
        `- 생성: ${audit.generatedAt}`,
        `- 커밋: \`${audit.commitSha}\``,
        `- 사용 중 카탈로그 항목: ${audit.coverage.usedEntries}`,
        `- 해석한 런타임/빌드 좌표: ${audit.coverage.resolvedPackages}`,
        `- 메타데이터 미확인: ${audit.coverage.metadataGaps}`,
        `- OSV 발견 좌표: ${audit.vulnerabilities.length}`,
        `- 현재 호환성 검사: ${audit.compatibility?.exitCode === 0 ? "통과" : "실패 또는 미실행"}`,
        "",
        "## 업데이트 후보",
        "",
    ];
    if (updates.length === 0) {
        lines.push("업데이트 후보 없음");
    } else {
        lines.push(
            "| alias | 현재 | 최신 안정 | 동일 채널 최신 | 안정/채널 종류 | 메타데이터 |",
            "|---|---:|---:|---:|---|---|",
        );
        for (const entry of updates) {
            lines.push(
                `| ${markdownCell(entry.alias)} | ${markdownCell(entry.currentVersion)} | ${markdownCell(entry.latestStable)} | ${markdownCell(entry.latestInChannel)} | ${markdownCell(`${entry.updateKind}/${entry.channelUpdateKind}`)} | ${markdownCell(entry.metadata?.url)} |`,
            );
        }
    }
    lines.push("", "## 보안 권고", "");
    if (audit.vulnerabilities.length === 0) {
        lines.push("OSV 발견 없음");
    } else {
        for (const finding of audit.vulnerabilities) {
            // 정식 패치판 유무를 함께 적는다. «권고는 떴는데 올릴 정식판이 없다» 와 «올릴 수
            // 있는데 안 올렸다» 는 대응이 정반대라, id 만으로는 요약을 읽고 판단할 수 없다.
            const patch = finding.stableFixVersion
                ? `정식 패치판 \`${finding.stableFixVersion}\``
                : finding.vulnerabilities?.some((item) => item.firstPatched)
                    ? `정식 패치판 없음 (최초 패치 ${finding.vulnerabilities
                          .map((item) => item.firstPatched)
                          .filter(Boolean)
                          .join(", ")})`
                    : "패치 버전 미확인";
            lines.push(
                `- \`${finding.coordinate}:${finding.version}\`: ${finding.vulnerabilities.map((item) => item.id).join(", ")} — ${patch}`,
            );
        }
    }
    lines.push("", "## 정합성 후보", "");
    if (audit.consistencyFindings.length === 0) {
        lines.push("정합성 후보 없음");
    } else {
        lines.push(...audit.consistencyFindings.map((finding) => `- ${finding.message}`));
    }
    if (audit.coverage.gaps.length > 0) {
        lines.push("", "## 수집 공백", "", ...audit.coverage.gaps.map((gap) => `- ${gap}`));
    }
    return `${lines.join("\n")}\n`;
}

function parseArguments(argv) {
    const options = {
        root: process.cwd(),
        output: "build/reports/dependency-audit/dependency-audit.json",
        summary: "build/reports/dependency-audit/dependency-audit.md",
        resolvedReports: [],
        buildEnvironmentReports: [],
        resolutionStatus: null,
        compatibilityStatus: null,
        offline: false,
    };
    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        if (argument === "--offline") {
            options.offline = true;
        } else if (argument === "--resolved-report") {
            options.resolvedReports.push(argv[++index]);
        } else if (argument === "--build-environment-report") {
            options.buildEnvironmentReports.push(argv[++index]);
        } else if (argument === "--root") {
            options.root = argv[++index];
        } else if (argument === "--output") {
            options.output = argv[++index];
        } else if (argument === "--summary") {
            options.summary = argv[++index];
        } else if (argument === "--compatibility-status") {
            options.compatibilityStatus = argv[++index];
        } else if (argument === "--resolution-status") {
            options.resolutionStatus = argv[++index];
        } else {
            throw new Error(`알 수 없는 인자: ${argument}`);
        }
    }
    return options;
}

async function readJsonIfPresent(filePath) {
    if (!filePath) {
        return null;
    }
    try {
        return JSON.parse(await fs.readFile(filePath, "utf8"));
    } catch (error) {
        if (error?.code === "ENOENT") {
            return null;
        }
        throw error;
    }
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    const root = path.resolve(options.root);
    const catalog = parseVersionCatalog(
        await fs.readFile(path.join(root, "gradle/libs.versions.toml"), "utf8"),
    );
    const usage = detectCatalogUsage(catalog, await collectSourceFiles(root));
    let entries = directEntries(catalog, usage);
    const boms = await resolveBomVersions(entries, fetch, options.offline);
    applyBomVersions(entries, boms);

    const resolvedRaw = [];
    const missingResolvedReports = [];
    const reportInputs = [
        ...options.resolvedReports.map((reportPath) => ({ reportPath, scope: CLASSPATH_SCOPES.project })),
        ...options.buildEnvironmentReports.map((reportPath) => ({ reportPath, scope: CLASSPATH_SCOPES.build })),
    ];
    for (const { reportPath, scope } of reportInputs) {
        try {
            const content = await fs.readFile(reportPath, "utf8");
            resolvedRaw.push(...parseResolvedDependencies(content, path.basename(reportPath), scope));
        } catch (error) {
            if (error?.code === "ENOENT") {
                missingResolvedReports.push(reportPath);
                continue;
            }
            throw error;
        }
    }
    const resolvedDependencies = mergeResolvedDependencies(resolvedRaw);
    entries = await collectMetadata(entries, fetch, options.offline);

    const aliasesByCoordinate = new Map();
    for (const entry of entries) {
        const aliases = aliasesByCoordinate.get(entry.coordinate) ?? [];
        aliases.push(entry.alias);
        aliasesByCoordinate.set(entry.coordinate, aliases);
    }
    const osvPackages = mergeResolvedDependencies([
        ...resolvedRaw,
        ...entries
            .filter((entry) => entry.coordinate && entry.currentVersion)
            .map((entry) => ({
                coordinate: entry.coordinate,
                requestedVersion: entry.currentVersion,
                selectedVersion: entry.currentVersion,
                source: "version-catalog",
            })),
    ]).map((entry) => ({
        coordinate: entry.coordinate,
        version: entry.version,
        aliases: aliasesByCoordinate.get(entry.coordinate) ?? [],
        sources: entry.sources,
    }));
    const osv = await queryOsv(osvPackages, fetch, options.offline);
    const advisories = await annotateAdvisories(osv.findings, fetch, options.offline);
    const compatibility = await readJsonIfPresent(options.compatibilityStatus);
    let resolutionStatus = {};
    if (options.resolutionStatus) {
        try {
            const statusText = await fs.readFile(options.resolutionStatus, "utf8");
            resolutionStatus = Object.fromEntries(
                statusText
                    .split(/\r?\n/)
                    .filter(Boolean)
                    .map((line) => {
                        const [name, exitCode] = line.split("=", 2);
                        return [name, Number(exitCode)];
                    }),
            );
        } catch (error) {
            if (error?.code !== "ENOENT") {
                throw error;
            }
        }
    }
    const metadataGaps = entries.filter(
        (entry) => entry.currentVersion && !entry.metadata?.url && !entry.metadata?.offline,
    );
    const gaps = [
        ...missingResolvedReports.map((report) => `Gradle 해석 보고서 누락: ${report}`),
        ...Object.entries(resolutionStatus)
            .filter(([, exitCode]) => exitCode !== 0)
            .map(([name, exitCode]) => `Gradle 의존성 해석 실패: ${name} (exit ${exitCode})`),
        ...metadataGaps.map((entry) => `Maven metadata 미확인: ${entry.alias} (${entry.coordinate})`),
        ...osv.errors.map((error) => `OSV 조회 실패: ${error}`),
        ...advisories.errors,
        ...boms
            .filter((bom) => Object.keys(bom.managed).length === 0)
            .map((bom) => `BOM POM 미확인: ${bom.entry.alias}`),
    ];
    const audit = {
        schemaVersion: 1,
        generatedAt: new Date().toISOString(),
        repository: process.env.GITHUB_REPOSITORY ?? "Team-CareerCompass/CareerCompass-FE",
        commitSha: process.env.GITHUB_SHA ?? "local",
        runUrl:
            process.env.GITHUB_SERVER_URL && process.env.GITHUB_REPOSITORY && process.env.GITHUB_RUN_ID
                ? `${process.env.GITHUB_SERVER_URL}/${process.env.GITHUB_REPOSITORY}/actions/runs/${process.env.GITHUB_RUN_ID}`
                : null,
        entries,
        resolvedDependencies,
        vulnerabilities: advisories.findings,
        consistencyFindings: consistencyFindings(entries, resolvedDependencies, catalog),
        compatibility,
        resolutionStatus,
        coverage: {
            catalogLibraries: Object.keys(catalog.libraries).length,
            catalogPlugins: Object.keys(catalog.plugins).length,
            usedEntries: entries.length,
            resolvedPackages: resolvedDependencies.length,
            metadataGaps: metadataGaps.length,
            gaps,
        },
    };
    const summary = renderSummary(audit);
    await fs.mkdir(path.dirname(options.output), { recursive: true });
    await fs.mkdir(path.dirname(options.summary), { recursive: true });
    await fs.writeFile(options.output, `${JSON.stringify(audit, null, 2)}\n`, "utf8");
    await fs.writeFile(options.summary, summary, "utf8");
    process.stdout.write(summary);
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.stack : error);
        process.exitCode = 1;
    });
}

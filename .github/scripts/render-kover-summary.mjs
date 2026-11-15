#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const COVERAGE_SCOPES = ["app", "core", "feature"];
const SOURCE_SETS = ["main", "debug"];
const COVERAGE_TYPES = ["LINE", "BRANCH"];
const DEFAULT_POLICY_PATH = ".github/kover-coverage-policy.json";

function decodeXml(value) {
    return String(value)
        .replaceAll("&quot;", '"')
        .replaceAll("&apos;", "'")
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&amp;", "&");
}

function attribute(attributes, name) {
    const match = new RegExp(`\\b${name}="([^"]*)"`).exec(attributes);
    return match ? decodeXml(match[1]) : null;
}

function emptyCounters() {
    return {
        LINE: { missed: 0, covered: 0 },
        BRANCH: { missed: 0, covered: 0 },
    };
}

function countersFrom(fragment) {
    const counters = emptyCounters();
    const counterPattern = /<counter\b([^>]*)\/?\s*>/g;
    let match;
    while ((match = counterPattern.exec(fragment)) !== null) {
        const type = attribute(match[1], "type");
        if (!(type in counters)) {
            continue;
        }
        counters[type] = {
            missed: Number(attribute(match[1], "missed")),
            covered: Number(attribute(match[1], "covered")),
        };
    }
    return counters;
}

export function parseKoverXml(xml) {
    const sources = new Map();
    const packagePattern = /<package\b([^>]*)>([\s\S]*?)<\/package>/g;
    let packageMatch;
    while ((packageMatch = packagePattern.exec(xml)) !== null) {
        const packageName = (attribute(packageMatch[1], "name") ?? "").replaceAll("/", ".");
        const sourcePattern = /<sourcefile\b([^>]*)>([\s\S]*?)<\/sourcefile>/g;
        let sourceMatch;
        while ((sourceMatch = sourcePattern.exec(packageMatch[2])) !== null) {
            const sourceName = attribute(sourceMatch[1], "name");
            if (!sourceName) {
                throw new Error(`Kover sourcefile without a name in package ${packageName}`);
            }
            const key = `${packageName}:${sourceName}`;
            if (sources.has(key)) {
                throw new Error(`Ambiguous Kover source key: ${key}`);
            }
            sources.set(key, countersFrom(sourceMatch[2]));
        }
    }

    const lastPackageEnd = xml.lastIndexOf("</package>");
    const reportTail = lastPackageEnd >= 0 ? xml.slice(lastPackageEnd + "</package>".length) : xml;
    return { sources, aggregate: countersFrom(reportTail) };
}

async function walk(directory, predicate, results = []) {
    let entries;
    try {
        entries = await fs.readdir(directory, { withFileTypes: true });
    } catch (error) {
        if (error?.code === "ENOENT") {
            return results;
        }
        throw error;
    }
    for (const entry of entries) {
        const absolute = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            if (!["build", ".gradle", ".git"].includes(entry.name)) {
                await walk(absolute, predicate, results);
            }
        } else if (predicate(absolute)) {
            results.push(absolute);
        }
    }
    return results;
}

export async function discoverCoverageModules(root) {
    const modules = [];
    for (const scope of COVERAGE_SCOPES) {
        const scopeRoot = path.join(root, scope);
        const buildFiles = await walk(scopeRoot, (file) => path.basename(file) === "build.gradle.kts");
        for (const buildFile of buildFiles) {
            modules.push(path.relative(root, path.dirname(buildFile)).split(path.sep).join("/"));
        }
    }
    return [...new Set(modules)].sort();
}

export function selectChangedModules(changedFiles, modules) {
    const longestFirst = [...modules].sort((left, right) => right.length - left.length);
    const selected = new Set();
    for (const file of changedFiles) {
        const normalized = file.split(path.sep).join("/");
        const module = longestFirst.find(
            (candidate) => normalized === candidate || normalized.startsWith(`${candidate}/`),
        );
        if (module) {
            selected.add(module);
        }
    }
    return [...selected].sort();
}

export function includeBaselineModules(discoveredModules, policy) {
    return [...new Set([...discoveredModules, ...Object.keys(policy.modules)])].sort();
}

export async function collectModuleSourceKeys(root, module) {
    const keys = new Set();
    for (const sourceSet of SOURCE_SETS) {
        const sourceRoot = path.join(root, module, "src", sourceSet);
        const sources = await walk(sourceRoot, (file) => /\.(?:kt|java)$/.test(file));
        for (const source of sources) {
            const content = await fs.readFile(source, "utf8");
            const packageName = /^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)/m.exec(content)?.[1] ?? "";
            keys.add(`${packageName}:${path.basename(source)}`);
        }
    }
    return keys;
}

function addCounters(target, source) {
    for (const type of COVERAGE_TYPES) {
        target[type].missed += source[type]?.missed ?? 0;
        target[type].covered += source[type]?.covered ?? 0;
    }
}

export function coverageForSourceKeys(report, sourceKeys) {
    const counters = emptyCounters();
    let matchedSources = 0;
    for (const sourceKey of sourceKeys) {
        const sourceCounters = report.sources.get(sourceKey);
        if (sourceCounters) {
            matchedSources += 1;
            addCounters(counters, sourceCounters);
        }
    }
    return { counters, matchedSources, declaredSources: sourceKeys.size };
}

function formatCounter(counter) {
    const total = counter.covered + counter.missed;
    if (total === 0) {
        return "N/A (0/0)";
    }
    return `${((counter.covered / total) * 100).toFixed(2)}% (${counter.covered}/${total})`;
}

function counterPercentage(counter) {
    const total = counter.covered + counter.missed;
    return total === 0 ? null : (counter.covered / total) * 100;
}

function assertCounter(counter, field) {
    if (!counter || typeof counter !== "object" || Array.isArray(counter)) {
        throw new Error(`${field} must be an object or null`);
    }
    for (const key of ["missed", "covered"]) {
        if (!Number.isSafeInteger(counter[key]) || counter[key] < 0) {
            throw new Error(`${field}.${key} must be a non-negative safe integer`);
        }
    }
    if (counter.missed + counter.covered === 0) {
        throw new Error(`${field} must be null when no coverage counter is measurable`);
    }
}

function validateBaselineSource(source, field) {
    if (!source || typeof source !== "object" || Array.isArray(source)) {
        throw new Error(`${field} metadata is required`);
    }
    if (!/^[0-9a-f]{40}$/.test(source.sha ?? "")) {
        throw new Error(`${field}.sha must be a full commit SHA`);
    }
    if (typeof source.ref !== "string" || source.ref.length === 0) {
        throw new Error(`${field}.ref is required`);
    }
    if (typeof source.runUrl !== "string" || !source.runUrl.startsWith("https://")) {
        throw new Error(`${field}.runUrl must be an HTTPS URL`);
    }
}

export function validateCoveragePolicy(policy) {
    if (!policy || typeof policy !== "object" || Array.isArray(policy)) {
        throw new Error("Kover coverage policy must be a JSON object");
    }
    if (policy.schemaVersion !== 1) {
        throw new Error(`Unsupported Kover coverage policy schemaVersion: ${policy.schemaVersion}`);
    }
    if (!["warn", "enforce"].includes(policy.mode)) {
        throw new Error('Kover coverage policy mode must be "warn" or "enforce"');
    }
    validateBaselineSource(policy.source, "Kover coverage policy source");
    if (!policy.modules || typeof policy.modules !== "object" || Array.isArray(policy.modules)) {
        throw new Error("Kover coverage policy modules must be an object");
    }

    for (const [module, baseline] of Object.entries(policy.modules)) {
        if (!module || !baseline || typeof baseline !== "object" || Array.isArray(baseline)) {
            throw new Error(`Invalid Kover coverage baseline for module ${module || "<empty>"}`);
        }
        if ("source" in baseline) {
            validateBaselineSource(baseline.source, `Kover coverage baseline ${module}.source`);
        }
        for (const type of COVERAGE_TYPES) {
            const field = type.toLowerCase();
            if (!(field in baseline)) {
                throw new Error(`Kover coverage baseline ${module}.${field} is required`);
            }
            if (baseline[field] !== null) {
                assertCounter(baseline[field], `Kover coverage baseline ${module}.${field}`);
            }
        }
    }
    return policy;
}

export async function loadCoveragePolicy(policyPath) {
    let parsed;
    try {
        parsed = JSON.parse(await fs.readFile(policyPath, "utf8"));
    } catch (error) {
        if (error instanceof SyntaxError) {
            throw new Error(`Invalid JSON in Kover coverage policy ${policyPath}: ${error.message}`);
        }
        throw error;
    }
    return validateCoveragePolicy(parsed);
}

function evaluateMetric(current, baseline, tracked) {
    const currentTotal = current.covered + current.missed;
    if (!tracked) {
        return { status: "untracked", current, baseline: null };
    }
    if (baseline === null) {
        return {
            status: currentTotal === 0 ? "not-applicable" : "newly-measurable",
            current,
            baseline,
        };
    }
    if (currentTotal === 0) {
        return { status: "missing", current, baseline };
    }

    const baselineTotal = baseline.covered + baseline.missed;
    const regressed =
        BigInt(current.covered) * BigInt(baselineTotal) <
        BigInt(baseline.covered) * BigInt(currentTotal);
    return { status: regressed ? "regression" : "pass", current, baseline };
}

export function evaluateCoveragePolicy({ modules, policy, mode = policy.mode }) {
    validateCoveragePolicy(policy);
    if (!["warn", "enforce"].includes(mode)) {
        throw new Error('Kover coverage evaluation mode must be "warn" or "enforce"');
    }

    const evaluatedModules = modules.map((module) => {
        const baseline = policy.modules[module.name];
        const tracked = baseline !== undefined;
        const metrics = Object.fromEntries(
            COVERAGE_TYPES.map((type) => [
                type,
                evaluateMetric(
                    module.coverage.counters[type] ?? { missed: 0, covered: 0 },
                    tracked ? baseline[type.toLowerCase()] : null,
                    tracked,
                ),
            ]),
        );
        const statuses = Object.values(metrics).map((metric) => metric.status);
        const result = statuses.some((status) => ["regression", "missing"].includes(status))
            ? "regression"
            : statuses.includes("untracked")
              ? "untracked"
              : statuses.every((status) => status === "not-applicable")
                ? "not-applicable"
                : "pass";
        return { name: module.name, metrics, result, source: baseline?.source ?? policy.source };
    });
    const regressions = evaluatedModules.flatMap((module) =>
        COVERAGE_TYPES.filter((type) =>
            ["regression", "missing"].includes(module.metrics[type].status),
        ).map((type) => ({ module: module.name, type, ...module.metrics[type] })),
    );

    return {
        mode,
        source: policy.source,
        modules: evaluatedModules,
        regressions,
    };
}

function formatPolicyMetric(metric) {
    if (metric.status === "untracked") {
        return "Not tracked";
    }
    if (metric.status === "not-applicable") {
        return "N/A";
    }
    if (metric.status === "newly-measurable") {
        return `${formatCounter(metric.current)} (new)`;
    }
    if (metric.status === "missing") {
        return `N/A vs ${formatCounter(metric.baseline)} baseline`;
    }
    const current = counterPercentage(metric.current);
    const baseline = counterPercentage(metric.baseline);
    const delta = current - baseline;
    const sign = delta >= 0 ? "+" : "";
    return `${current.toFixed(2)}% vs ${baseline.toFixed(2)}% (${sign}${delta.toFixed(2)} pp)`;
}

function formatPolicyResult(result, mode) {
    if (result === "regression") {
        return mode === "enforce" ? "Failure: regression" : "Warning: regression";
    }
    if (result === "untracked") return "Baseline missing";
    if (result === "not-applicable") return "N/A";
    return "No regression";
}

function escapeWorkflowCommandData(value) {
    return String(value).replaceAll("%", "%25").replaceAll("\r", "%0D").replaceAll("\n", "%0A");
}

function escapeWorkflowCommandProperty(value) {
    return escapeWorkflowCommandData(value).replaceAll(":", "%3A").replaceAll(",", "%2C");
}

export function renderPolicyAnnotations(evaluation) {
    const level = evaluation.mode === "enforce" ? "error" : "warning";
    const annotations = evaluation.regressions.map((regression) => {
        const metric = regression.type.toLowerCase();
        const message = regression.status === "missing"
            ? `${regression.module} ${metric} coverage is not measurable; baseline was ${formatCounter(regression.baseline)}.`
            : `${regression.module} ${metric} coverage regressed from ${formatCounter(regression.baseline)} to ${formatCounter(regression.current)}.`;
        return `::${level} title=${escapeWorkflowCommandProperty("Kover coverage regression")}::${escapeWorkflowCommandData(message)}`;
    });
    for (const module of evaluation.modules.filter((entry) => entry.result === "untracked")) {
        annotations.push(
            `::notice title=${escapeWorkflowCommandProperty("Kover coverage baseline missing")}::${escapeWorkflowCommandData(`${module.name} is not tracked by the committed Kover baseline yet.`)}`,
        );
    }
    return annotations;
}

export function renderSummary({ aggregate, modules, artifactUrl, policyEvaluation = null }) {
    const lines = [
        "## Kover JVM unit-test coverage",
        "",
        `[Download the full aggregate HTML/XML report](${artifactUrl})`,
        "",
        `Aggregate line: **${formatCounter(aggregate.LINE)}** · branch: **${formatCounter(aggregate.BRANCH)}**`,
        "",
        "### Changed modules",
        "",
    ];

    if (modules.length === 0) {
        lines.push("No app/core/feature Gradle module changed in this pull request.");
    } else {
        lines.push("| Module | Line | Branch | Mapped source files |");
        lines.push("| --- | ---: | ---: | ---: |");
        for (const module of modules) {
            lines.push(
                `| \`${module.name}\` | ${formatCounter(module.coverage.counters.LINE)} | ${formatCounter(module.coverage.counters.BRANCH)} | ${module.coverage.matchedSources}/${module.coverage.declaredSources} |`,
            );
        }
    }

    if (policyEvaluation) {
        const shortSha = policyEvaluation.source.sha.slice(0, 7);
        lines.push(
            "",
            "### Coverage non-regression",
            "",
            `Policy mode: \`${policyEvaluation.mode}\` · baseline: [\`${policyEvaluation.source.ref}@${shortSha}\`](${policyEvaluation.source.runUrl})`,
            "",
        );
        if (policyEvaluation.modules.length === 0) {
            lines.push("No changed module requires a coverage comparison.");
        } else {
            lines.push("| Module | Line vs baseline | Branch vs baseline | Result |");
            lines.push("| --- | ---: | ---: | --- |");
            for (const module of policyEvaluation.modules) {
                lines.push(
                    `| \`${module.name}\` | ${formatPolicyMetric(module.metrics.LINE)} | ${formatPolicyMetric(module.metrics.BRANCH)} | ${formatPolicyResult(module.result, policyEvaluation.mode)} |`,
                );
            }
            for (const module of policyEvaluation.modules.filter((module) => module.source !== policyEvaluation.source)) {
                lines.push(
                    "",
                    `- \`${module.name}\` baseline: [\`${module.source.ref}@${module.source.sha.slice(0, 7)}\`](${module.source.runUrl})`,
                );
            }
        }
        const policyDescription = policyEvaluation.mode === "enforce"
            ? "Enforced non-regression baseline: a changed module regression fails this job."
            : "Warning-only non-regression baseline: regressions emit annotations but do not fail this job.";
        lines.push("", `> ${policyDescription} No absolute coverage percentage threshold is enforced.`, "");
    } else {
        lines.push("", "> Report-only baseline: no coverage percentage threshold is enforced.", "");
    }
    return lines.join("\n");
}

export function changedFilesBetween(baseSha, headSha, root = process.cwd()) {
    const output = execFileSync(
        "git",
        [
            "diff",
            "--name-status",
            "-z",
            "--find-renames",
            "--find-copies-harder",
            "--diff-filter=ACDMRT",
            `${baseSha}...${headSha}`,
        ],
        { cwd: root, encoding: "utf8" },
    );
    const fields = output.split("\0");
    if (fields.at(-1) === "") {
        fields.pop();
    }

    const changedFiles = new Set();
    for (let index = 0; index < fields.length;) {
        const status = fields[index++];
        const pathCount = status.startsWith("R") || status.startsWith("C") ? 2 : 1;
        if (!/^[ACDMRT]/.test(status) || index + pathCount > fields.length) {
            throw new Error(`Malformed git name-status entry near ${JSON.stringify(status)}`);
        }
        for (let pathIndex = 0; pathIndex < pathCount; pathIndex += 1) {
            changedFiles.add(fields[index++]);
        }
    }
    return [...changedFiles];
}

async function main() {
    const root = process.cwd();
    const baseSha = process.env.KOVER_BASE_SHA;
    const headSha = process.env.KOVER_HEAD_SHA;
    const artifactUrl = process.env.KOVER_ARTIFACT_URL;
    const outputPath = process.env.GITHUB_STEP_SUMMARY;
    const configuredModules = process.env.KOVER_COVERAGE_MODULES ?? "all";
    const policyPath = path.resolve(root, process.env.KOVER_POLICY_PATH ?? DEFAULT_POLICY_PATH);
    if (!baseSha || !headSha || !artifactUrl || !outputPath) {
        throw new Error(
            "KOVER_BASE_SHA, KOVER_HEAD_SHA, KOVER_ARTIFACT_URL, and GITHUB_STEP_SUMMARY are required",
        );
    }

    const policy = await loadCoveragePolicy(policyPath);

    let report;
    let modules;
    if (configuredModules === "all") {
        const reportXml = await fs.readFile(
            path.join(root, "build", "reports", "kover", "reportCi.xml"),
            "utf8",
        );
        report = parseKoverXml(reportXml);
        const allModules = includeBaselineModules(await discoverCoverageModules(root), policy);
        const changedModules = selectChangedModules(
            changedFilesBetween(baseSha, headSha, root),
            allModules,
        );
        modules = [];
        for (const module of changedModules) {
            const sourceKeys = await collectModuleSourceKeys(root, module);
            modules.push({ name: module, coverage: coverageForSourceKeys(report, sourceKeys) });
        }
    } else {
        report = { aggregate: emptyCounters() };
        modules = [];
        for (const projectPath of configuredModules.split(/\s+/).filter(Boolean)) {
            const module = projectPath.replace(/^:/, "").replaceAll(":", "/");
            const reportXml = await fs.readFile(
                path.join(root, module, "build", "reports", "kover", "reportCi.xml"),
                "utf8",
            );
            const moduleReport = parseKoverXml(reportXml);
            addCounters(report.aggregate, moduleReport.aggregate);
            modules.push({
                name: module,
                coverage: {
                    counters: moduleReport.aggregate,
                    matchedSources: moduleReport.sources.size,
                    declaredSources: moduleReport.sources.size,
                },
            });
        }
    }

    const policyEvaluation = evaluateCoveragePolicy({ modules, policy });

    await fs.appendFile(
        outputPath,
        renderSummary({ aggregate: report.aggregate, modules, artifactUrl, policyEvaluation }),
        "utf8",
    );
    for (const annotation of renderPolicyAnnotations(policyEvaluation)) {
        console.log(annotation);
    }
    if (policyEvaluation.mode === "enforce" && policyEvaluation.regressions.length > 0) {
        process.exitCode = 1;
    }
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.stack : error);
        process.exitCode = 1;
    });
}

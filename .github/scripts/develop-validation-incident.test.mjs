import assert from "node:assert/strict";
import test from "node:test";
import {
    DEVELOP_VALIDATION_LABEL,
    DEVELOP_VALIDATION_MARKER,
    reconcileDevelopValidationIncident,
} from "./develop-validation-incident.mjs";
import { inspectIssue, readFormSection } from "./reconcile-issue-metadata.mjs";

const SHA_A = "a".repeat(40);
const SHA_B = "b".repeat(40);

function incident({ number = 10, sha = SHA_A, state = "open", user = "github-actions[bot]", labels } = {}) {
    return {
        number,
        state,
        user: { login: user },
        labels: labels ?? [{ name: DEVELOP_VALIDATION_LABEL }],
        body: `${DEVELOP_VALIDATION_MARKER}\n<!-- develop-validation-sha:${sha} -->`,
    };
}

function harness(issues = [], { labelExists = true, currentSha = SHA_A } = {}) {
    const calls = [];
    const record = (method) => async (parameters) => {
        calls.push({ method, parameters });
        if (method === "getLabel" && !labelExists) {
            throw Object.assign(new Error("Not Found"), { status: 404 });
        }
        if (method === "create") {
            return { data: { number: 99 } };
        }
        return { data: {} };
    };
    const github = {
        paginate: async () => issues,
        rest: {
            repos: {
                getBranch: async (parameters) => {
                    calls.push({ method: "getBranch", parameters });
                    return { data: { commit: { sha: currentSha } } };
                },
            },
            issues: {
                listForRepo: () => {},
                getLabel: record("getLabel"),
                createLabel: record("createLabel"),
                create: record("create"),
                update: record("update"),
                createComment: record("createComment"),
            },
        },
    };
    const context = {
        repo: { owner: "CareerCompass", repo: "CareerCompass-FE" },
        serverUrl: "https://github.com",
        runId: 123,
    };
    const core = { info: () => {} };
    return { calls, context, core, github };
}

function failingResults() {
    return {
        "unit-test": { result: "failure" },
        screenshot: { result: "success" },
    };
}

function successfulResults() {
    return {
        "unit-test": { result: "success" },
        screenshot: { result: "success" },
    };
}

async function reconcile(testHarness, { results = failingResults(), sha = SHA_A } = {}) {
    return reconcileDevelopValidationIncident({
        github: testHarness.github,
        context: testHarness.context,
        core: testHarness.core,
        validationResults: results,
        sha,
    });
}

test("creates a labelled incident for the first failing develop SHA", async () => {
    const testHarness = harness([]);

    const result = await reconcile(testHarness);

    assert.deepEqual(result, { action: "created", issueNumber: 99 });
    const create = testHarness.calls.find((call) => call.method === "create");
    assert.deepEqual(create.parameters.labels, [DEVELOP_VALIDATION_LABEL]);
    assert.match(create.parameters.body, new RegExp(`develop-validation-sha:${SHA_A}`));
});

test("incident 본문이 reconcile-issue-metadata 의 판정을 통과한다", async () => {
    const testHarness = harness([]);

    await reconcile(testHarness);
    const body = testHarness.calls.find((call) => call.method === "create").parameters.body;

    assert.equal(readFormSection(body, "작업 유형"), "bug — 버그·오동작");
    assert.equal(readFormSection(body, "주 담당 모듈"), "platform — Android 앱·CI·빌드·릴리스·저장소 운영");
    assert.notEqual(readFormSection(body, "개요"), null);

    const inspection = inspectIssue({
        number: 99,
        state: "open",
        user: { login: "github-actions[bot]" },
        labels: [{ name: DEVELOP_VALIDATION_LABEL }],
        body,
    });

    assert.equal(inspection.status, "valid", "판정 불가면 일일 스윕이 incident 를 not_planned 로 닫는다");
    assert.equal(inspection.expectedLabel, "bug");
    assert.equal(inspection.expectedAreaLabel, "area:platform");
    assert.equal(inspection.expectedAssignee, "1hyok");
    assert.deepEqual(inspection.labels, [DEVELOP_VALIDATION_LABEL, "bug", "area:platform"]);
});

test("양식 절을 넣은 본문도 incident 소유 판정을 유지한다", async () => {
    const created = harness([]);
    await reconcile(created);
    const body = created.calls.find((call) => call.method === "create").parameters.body;

    const testHarness = harness([{
        number: 31,
        state: "open",
        user: { login: "github-actions[bot]" },
        labels: [{ name: DEVELOP_VALIDATION_LABEL }],
        body,
    }]);
    const result = await reconcile(testHarness);

    assert.deepEqual(result, { action: "reused", issueNumber: 31 });
});

test("reuses only an incident that belongs to the same failing SHA", async () => {
    const testHarness = harness([incident({ number: 11, sha: SHA_A })]);

    const result = await reconcile(testHarness);

    assert.deepEqual(result, { action: "reused", issueNumber: 11 });
    const updates = testHarness.calls.filter((call) => call.method === "update");
    assert.equal(updates.length, 1);
    assert.equal("labels" in updates[0].parameters, false, "same-SHA updates must preserve human-added labels");
    assert.equal(testHarness.calls.some((call) => call.method === "create"), false);
});

test("reopens the same SHA incident when that exact run is retried after recovery", async () => {
    const testHarness = harness([incident({ number: 12, sha: SHA_A, state: "closed" })]);

    await reconcile(testHarness);

    const update = testHarness.calls.find((call) => call.method === "update");
    assert.equal(update.parameters.state, "open");
    assert.equal(update.parameters.state_reason, "reopened");
});

test("creates a new incident for a different SHA after an older incident was closed", async () => {
    const testHarness = harness([incident({ number: 13, sha: SHA_A, state: "closed" })], { currentSha: SHA_B });

    const result = await reconcile(testHarness, { sha: SHA_B });

    assert.deepEqual(result, { action: "created", issueNumber: 99 });
    assert.equal(testHarness.calls.some((call) => call.method === "update"), false);
});

test("closes every open bot-owned labelled incident when develop recovers", async () => {
    const forgedByUser = incident({ number: 21, user: "outside-contributor" });
    const missingLabel = incident({ number: 22, labels: [] });
    const tracked = incident({ number: 23 });
    const testHarness = harness([forgedByUser, missingLabel, tracked], { currentSha: SHA_B });

    const result = await reconcile(testHarness, { results: successfulResults(), sha: SHA_B });

    assert.deepEqual(result, { action: "recovered", closedIssueNumbers: [23] });
    const updates = testHarness.calls.filter((call) => call.method === "update");
    assert.deepEqual(updates.map((call) => call.parameters.issue_number), [23]);
    assert.equal(updates[0].parameters.state_reason, "completed");
    assert.deepEqual(
        testHarness.calls.filter((call) => call.method === "createComment").map((call) => call.parameters.issue_number),
        [23],
    );
});

test("creates the dedicated label when it does not exist", async () => {
    const testHarness = harness([], { labelExists: false });

    await reconcile(testHarness);

    const createLabel = testHarness.calls.find((call) => call.method === "createLabel");
    assert.equal(createLabel.parameters.name, DEVELOP_VALIDATION_LABEL);
});

test("a stale failed rerun cannot create or reopen an incident", async () => {
    const testHarness = harness([incident({ state: "closed" })], { currentSha: SHA_B });

    const result = await reconcile(testHarness, { sha: SHA_A });

    assert.deepEqual(result, { action: "stale", currentSha: SHA_B });
    assert.deepEqual(testHarness.calls.map((call) => call.method), ["getBranch"]);
});

test("a stale successful rerun cannot close the current failure incident", async () => {
    const testHarness = harness([incident({ sha: SHA_B })], { currentSha: SHA_B });

    const result = await reconcile(testHarness, { results: successfulResults(), sha: SHA_A });

    assert.deepEqual(result, { action: "stale", currentSha: SHA_B });
    assert.deepEqual(testHarness.calls.map((call) => call.method), ["getBranch"]);
});

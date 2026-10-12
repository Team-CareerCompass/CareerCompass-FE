import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../workflows/author-auto-merge.yml", import.meta.url), "utf8");
const script = source.split("          script: |\n")[1].replace(/^ {12}/gm, "");
const execute = new (Object.getPrototypeOf(async function () {}).constructor)("github", "context", "core", script);

function fixture(overrides = {}) {
    const pr = {
        number: 1, node_id: "PR_1", user: { login: "1hyok" }, state: "open", draft: false,
        head: { sha: "current-head", repo: { full_name: "Team-CareerCompass/CareerCompass-FE" } },
        base: { ref: "develop", repo: { full_name: "Team-CareerCompass/CareerCompass-FE" } },
        ...overrides,
    };
    const calls = [];
    const rules = [
        { type: "pull_request" },
        { type: "required_status_checks", parameters: {
            strict_required_status_checks_policy: true,
            required_status_checks: [{ context: "guard", integration_id: 15368 }],
        } },
    ];
    const live = { headRefOid: pr.head.sha, baseRefName: pr.base.ref, mergeStateStatus: "BLOCKED", autoMergeRequest: null };
    const github = {
        rest: { pulls: {
            get: async () => ({ data: pr }),
            list: async () => ({ data: [pr] }),
            merge: async args => { calls.push({ type: "merge", args }); return { data: { merged: true } }; },
        } },
        paginate: async () => [pr],
        request: async () => ({ data: rules }),
        graphql: async (query, args) => {
            if (query.startsWith("query")) return { repository: { pullRequest: live } };
            calls.push({ type: query.includes("disablePullRequestAutoMerge") ? "disable" : "enable", args });
        },
    };
    const context = { repo: { owner: "Team-CareerCompass", repo: "CareerCompass-FE" }, payload: { pull_request: { number: 1 } } };
    return { pr, calls, rules, live, github, context, run: () => execute(github, context, { info() {} }) };
}

test("only the current API author can receive automatic merge", async () => {
    const f = fixture({ user: { login: "another-author" } });
    f.context.payload.pull_request.user = { login: "1hyok" };
    await f.run();
    assert.deepEqual(f.calls, []);
});

test("drafts, forks, stacked bases, and closed PRs are excluded", async () => {
    for (const overrides of [
        { draft: true }, { state: "closed" },
        { head: { sha: "current-head", repo: { full_name: "fork/repo" } } },
        { base: { ref: "feat/parent", repo: { full_name: "Team-CareerCompass/CareerCompass-FE" } } },
    ]) {
        const f = fixture(overrides);
        await f.run();
        assert.deepEqual(f.calls, []);
    }
});

test("an ineligible own PR loses its existing automatic merge request", async () => {
    const f = fixture({ draft: true, auto_merge: {} });
    await f.run();
    assert.equal(f.calls[0].type, "disable");
});

test("missing trusted guard, strict base, or PR protection fails closed", async () => {
    for (const mutate of [
        f => f.rules.splice(0, 1),
        f => { f.rules[1].parameters.strict_required_status_checks_policy = false; },
        f => { f.rules[1].parameters.required_status_checks[0].integration_id = 1; },
    ]) {
        const f = fixture(); mutate(f);
        await assert.rejects(f.run(), /protection is missing/);
        assert.deepEqual(f.calls, []);
    }
});

test("pending checks use native auto merge and keep protection enforced", async () => {
    const f = fixture();
    await f.run();
    assert.deepEqual(f.calls, [{ type: "enable", args: { id: "PR_1" } }]);
});

test("already clean PRs merge only their observed head SHA", async () => {
    const f = fixture(); f.live.mergeStateStatus = "CLEAN";
    await f.run();
    assert.equal(f.calls[0].type, "merge");
    assert.equal(f.calls[0].args.sha, "current-head");
    assert.equal(f.calls[0].args.merge_method, "merge");
});

test("head or base changes during inspection prevent mutation", async () => {
    for (const overrides of [{ headRefOid: "new-head" }, { baseRefName: "feat/parent" }]) {
        const f = fixture(); Object.assign(f.live, overrides);
        await f.run(); assert.deepEqual(f.calls, []);
    }
});

test("a completed workflow for an older head cannot enable or merge a PR", async () => {
    const f = fixture(); f.context.payload.workflow_run = { head_sha: "old-head" };
    await f.run(); assert.deepEqual(f.calls, []);
});

test("dispatch scans open PRs and existing native requests are left enabled", async () => {
    const f = fixture(); f.context.payload = {}; f.live.autoMergeRequest = { enabledAt: "now" };
    await f.run(); assert.deepEqual(f.calls, []);
});

test("the privileged workflow never checks out or executes PR files", () => {
    assert.match(source, /pull_request_target:/);
    assert.match(source, /workflow_run:/);
    assert.doesNotMatch(source, /actions\/checkout|\n\s+run:|\$\{\{.*pull_request.*(?:title|body)/);
});

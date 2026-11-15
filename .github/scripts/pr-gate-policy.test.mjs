import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import test from "node:test";

const workflowDirectory = new URL("../workflows/", import.meta.url);
const ENTRY_WORKFLOW = "pr-validation.yml";
const VALIDATION_WORKFLOWS = ["lint.yml", "unit-test.yml", "screenshot.yml", "repository-quality.yml"];
const HEAVY_VALIDATION_WORKFLOWS = ["lint.yml", "unit-test.yml", "screenshot.yml"];
// Repository ruleset 20911039 의 required context 와 함께 바꿔야 하는 외부 계약이다.
const REQUIRED_VALIDATION_CONTEXTS = [
    "Repository Quality / Repository Quality",
    "Screenshot / Validate Compose Preview Screenshots",
    "Static Analysis / Check Code Quality (Ktlint)",
    "Static Analysis / Check Project Issues (Android Lint)",
    "Unit Test / Run Unit Tests",
];
const REQUIRED_MANAGED_DEVICE_CONTEXTS = [
    "Pixel 2 API 30 androidTest",
    "Pixel 2 API 34 accessibility smoke",
];

async function workflows() {
    const names = (await readdir(workflowDirectory)).filter((name) => name.endsWith(".yml"));
    return Promise.all(names.map(async (name) => [name, await readFile(new URL(name, workflowDirectory), "utf8")]));
}

function readWorkflow(name) {
    return readFile(new URL(name, workflowDirectory), "utf8");
}

function jobNames(source) {
    const jobsSection = source.slice(source.indexOf("\njobs:\n"));
    return [...jobsSection.matchAll(/^ {2}([A-Za-z][\w-]*):$/gm)].map((match) => match[1]);
}

function displayNameOf(source, job) {
    const pattern = new RegExp(`^ {2}${job}:$[\\s\\S]*?^ {4}name:\\s*(.+)$`, "m");
    return pattern.exec(source)?.[1]?.trim();
}

function calledWorkflowOf(source, job) {
    const pattern = new RegExp(`^ {2}${job}:$[\\s\\S]*?^ {4}uses:\\s*\\./\\.github/workflows/(.+)$`, "m");
    return pattern.exec(source)?.[1]?.trim();
}

test("pull request validation has exactly one entry point", async () => {
    // 검증 워크플로가 스스로 pull_request 를 듣고 있으면 같은 이름의 check 가 두 벌 돈다.
    const entryPoints = (await workflows())
        .filter(([name, source]) => /^on:\n(?:[^\n]*\n)*?\s{2}pull_request:/m.test(source) && VALIDATION_WORKFLOWS.includes(name))
        .map(([name]) => name);

    assert.deepEqual(entryPoints, []);
    assert.match(
        await readWorkflow(ENTRY_WORKFLOW),
        /^\s{2}pull_request:\n\s{4}types: \[opened, synchronize, reopened, edited\]$/m,
    );
});

test("every validation workflow is reachable only as a reusable call", async () => {
    for (const name of VALIDATION_WORKFLOWS) {
        assert.match(await readWorkflow(name), /^\s{2}workflow_call:$/m, `${name} must be callable`);
    }
});

test("the entry workflow calls every validation workflow without an aggregate runner job", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const jobs = jobNames(entry);

    assert.equal(jobs.length, VALIDATION_WORKFLOWS.length);
    assert.doesNotMatch(entry, /^ {2}ci-gate:$/m);
    for (const workflow of VALIDATION_WORKFLOWS) {
        assert.ok(entry.includes(`uses: ./.github/workflows/${workflow}`), `${workflow} is not called`);
    }
});

test("required validation context names stay aligned with the repository ruleset", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const contexts = [];

    for (const job of jobNames(entry)) {
        const callerName = displayNameOf(entry, job);
        const workflow = calledWorkflowOf(entry, job);
        assert.ok(callerName, `${job} has no display name`);
        assert.ok(workflow, `${job} is not a reusable workflow call`);

        const reusable = await readWorkflow(workflow);
        for (const reusableJob of jobNames(reusable)) {
            const reusableName = displayNameOf(reusable, reusableJob);
            assert.ok(reusableName, `${workflow}:${reusableJob} has no display name`);
            contexts.push(`${callerName} / ${reusableName}`);
        }
    }

    assert.deepEqual(contexts.sort(), [...REQUIRED_VALIDATION_CONTEXTS].sort());
});

test("both PR managed device lanes stay aligned with the repository ruleset", async () => {
    const managedDevice = await readWorkflow("android-managed-device.yml");
    const contexts = [
        ...managedDevice.matchAll(
            /^ {10}- name: (Pixel 2 API .+)\n([\s\S]*?)(?=^ {10}- name: Pixel 2 API |^ {4}steps:)/gm,
        ),
    ]
        .filter((match) => /^ {12}merge_queue_skip: false$/m.test(match[2]))
        .map((match) => match[1]);

    assert.deepEqual(contexts.sort(), [...REQUIRED_MANAGED_DEVICE_CONTEXTS].sort());
});

test("stale runs are cancelled per pull request", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.match(
        entry,
        /^concurrency:\n\s{2}group: pr-validation-\$\{\{ github\.event\.pull_request\.number \|\| inputs\.pull_request_number \|\| github\.ref \}\}\n\s{2}cancel-in-progress: true$/m,
    );
});

test("token-authored commits preserve the pull request context on manual dispatch", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const reusableWorkflows = await Promise.all(VALIDATION_WORKFLOWS.map(readWorkflow));
    const normalizedPullRequestNumber =
        /pull_request_number: \$\{\{ fromJSON\(format\('\{0\}', inputs\.pull_request_number \|\| github\.event\.pull_request\.number \|\| 0\)\) \}\}/g;

    assert.match(entry, /^\s{2}workflow_dispatch:\n\s{4}inputs:\n\s{6}pull_request_number:/m);
    assert.equal(
        (entry.match(normalizedPullRequestNumber) ?? []).length,
        VALIDATION_WORKFLOWS.length,
    );
    for (const reusable of reusableWorkflows) {
        assert.match(
            reusable,
            /pull_request_number:\n\s+required: false\n\s+default: 0\n\s+type: number/,
        );
    }
});

test("merge queue groups revalidate every required context", async () => {
    // 큐 항목이 required context 를 만들지 못하면 영구 pending 후 큐에서 방출된다.
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const managedDevice = await readWorkflow("android-managed-device.yml");

    for (const source of [entry, managedDevice]) {
        assert.match(source, /^\s{2}merge_group:\n\s{4}types: \[checks_requested\]$/m);
    }
    assert.match(managedDevice, /^\s+github\.event_name == 'merge_group'$/m);
});

test("merge group validation falls back to the full suite without a pull request", async () => {
    // merge group 에는 PR 이 없다. 번호가 빈 문자열이면 number 입력 자체가 깨지고,
    // 0 으로 닫으면 develop push 와 같은 전량 검증 경로를 그대로 탄다.
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const repositoryQuality = await readWorkflow("repository-quality.yml");

    assert.equal(
        (entry.match(/\|\| github\.event\.pull_request\.number \|\| 0\)\) \}\}/g) ?? []).length,
        VALIDATION_WORKFLOWS.length,
    );
    for (const gate of ["Require linked Issue", "Validate CI Test Plan", "Reject test-only production declarations"]) {
        assert.match(
            repositoryQuality,
            new RegExp(`- name: ${gate}\\n\\s+if: inputs\\.pull_request_number > 0`),
            `${gate} must stay inert without a pull request`,
        );
    }
});

test("optional boundary device lanes stay out of the merge queue", async () => {
    // 경계 lane 까지 큐에서 돌면 required 가 아닌 job 이 처리량만 깎는다.
    const managedDevice = await readWorkflow("android-managed-device.yml");

    assert.match(
        managedDevice,
        /\[\[ "\$MERGE_QUEUE_SKIP" == "true" && "\$EVENT_NAME" == "merge_group" \]\]/,
    );
});

test("the entry point keeps no pull_request branch or path filter", async () => {
    // #683: base 변경(feat/* → develop)은 기본 activity type 에 없어 재트리거되지
    // 않는다. 필터가 있으면 스택 PR 이 검증을 한 번도 거치지 않고 머지된다.
    // required workflow 자체의 paths 필터는 제외된 PR 에 check 를 만들지 않아 ruleset 을
    // 영구 pending 으로 남길 수 있으므로 변경 파일 분류는 job 안에서 한다.
    const entry = await readWorkflow(ENTRY_WORKFLOW);
    const trigger = /^on:\n\s{2}pull_request:\n([\s\S]*?)^\S/m.exec(entry)?.[1] ?? "";

    assert.doesNotMatch(trigger, /branches/);
    assert.doesNotMatch(trigger, /^\s+paths(?:-ignore)?:/m);
});

/**
 * 면제 라벨은 게이트가 빨개진 **뒤에** 붙는다 — 이벤트 페이로드의 라벨은 트리거 시점 값이라 그 라벨이 없다.
 * 그대로 두면 라벨을 붙이고 재실행해도 같은 페이로드를 다시 읽어 면제가 영영 듣지 않는다.
 */
test("policy payload refreshes labels from the live API when it reuses the event payload", async () => {
    const repositoryQuality = await readWorkflow("repository-quality.yml");

    assert.match(repositoryQuality, /gh api "repos\/\$GITHUB_REPOSITORY\/issues\/\$PR_NUMBER\/labels"/);
    assert.match(repositoryQuality, /\.labels = \$labels\[0\]/);
});

test("impact outputs scope each heavy lane and classification failure runs full validation", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.equal((entry.match(/^ {4}needs: repository-quality$/gm) ?? []).length, HEAVY_VALIDATION_WORKFLOWS.length);
    assert.equal(
        (entry.match(/^ {4}if: \$\{\{ !cancelled\(\) \}\}$/gm) ?? []).length,
        HEAVY_VALIDATION_WORKFLOWS.length,
        "quality failures must fan out to full validation without reviving a cancelled stale run",
    );
    for (const output of ["ktlint_required", "android_lint_required", "unit_test_required", "screenshot_required"]) {
        assert.match(
            entry,
            new RegExp(`needs\\.repository-quality\\.result != 'success' \\|\\| needs\\.repository-quality\\.outputs\\.${output} != 'false'`),
        );
    }
    assert.match(entry, /outputs\.ktlint_tasks \|\| 'ktlintCheck :build-logic:ktlintCheck'/);
    assert.match(entry, /outputs\.screenshot_tasks \|\| ':core:ui:validateScreenshotTest/);
});

test("heavy reusable workflows default to full validation and preserve every required job context", async () => {
    for (const name of ["unit-test.yml", "screenshot.yml"]) {
        const source = await readWorkflow(name);
        const jobs = jobNames(source);

        assert.match(
            source,
            /^ {6}run_validation:\n(?: {8}.+\n)*? {8}default: true\n {8}type: boolean$/m,
            `${name} must default callers such as develop validation to the full suite`,
        );
        assert.equal(
            (source.match(/^ {4}if: inputs\.run_validation$/gm) ?? []).length,
            jobs.length,
            `${name} must skip work at the existing required job boundary`,
        );
    }
    const lint = await readWorkflow("lint.yml");
    for (const input of ["run_ktlint", "run_android_lint"]) {
        assert.match(
            lint,
            new RegExp(`^ {6}${input}:\\n(?: {8}.+\\n)*? {8}default: true\\n {8}type: boolean$`, "m"),
        );
    }
    assert.match(lint, /^ {4}if: inputs\.run_ktlint$/m);
    assert.match(lint, /^ {4}if: inputs\.run_android_lint$/m);
});

test("repository quality owns fail-closed paginated impact classification and PR gates", async () => {
    const repositoryQuality = await readWorkflow("repository-quality.yml");
    const unitTest = await readWorkflow("unit-test.yml");

    assert.match(repositoryQuality, /^ {4}outputs:\n {6}docs_only:\n(?: {8}.+\n)*? {8}value: \$\{\{ jobs\.repository-quality\.outputs\.docs_only \}\}$/m);
    assert.match(repositoryQuality, /^ {4}outputs:\n {6}docs_only: \$\{\{ steps\.classify-documentation-changes\.outputs\.docs_only \}\}$/m);
    assert.match(repositoryQuality, /gh api --paginate --slurp/);
    assert.match(repositoryQuality, /classify-documentation-changes\.mjs \\\n\s+"\$CHANGED_FILES"/);
    assert.match(repositoryQuality, /resolve-pr-impact\.mjs "\$files_json"/);
    assert.match(repositoryQuality, /if \[ "\$GITHUB_EVENT_NAME" = "workflow_dispatch" \]; then/);
    assert.match(repositoryQuality, /if \[ "\$GITHUB_SHA" != "\$head_sha" \]; then/);
    assert.match(repositoryQuality, /persist-credentials: false/);
    assert.match(repositoryQuality, /env -u GH_TOKEN -u GITHUB_TOKEN/);
    assert.match(
        repositoryQuality,
        /- name: Validate CI Test Plan\n\s+if: inputs\.pull_request_number > 0/,
    );
    assert.match(repositoryQuality, /pull_request_json=%s\\n' "\$policy_payload_file"/);
    assert.match(repositoryQuality, /trusted_head_commit: \$headCommit\[0\]/);
    assert.doesNotMatch(unitTest, /Validate CI Test Plan/);
    // #1895 — 새 프로덕션 함수의 main 참조 게이트는 PR files API 의 patch 로 판정하므로 files_json 을 받는다.
    assert.match(
        repositoryQuality,
        /validate-test-only-production-declarations\.mjs\n\s+"\$\{\{ steps\.changed-files\.outputs\.pull_request_json \}\}"\n\s+"\$\{\{ steps\.classify-documentation-changes\.outputs\.files_json \}\}"/,
    );
});

test("editing CI Test Plan retriggers every required validation context", async () => {
    const entry = await readWorkflow(ENTRY_WORKFLOW);

    assert.match(entry, /types: \[opened, synchronize, reopened, edited\]/);
});

test("repository quality still runs on develop and main pushes", async () => {
    const source = await readWorkflow("repository-quality.yml");

    assert.match(source, /^\s{2}push:\n\s{4}branches:\s*\[develop, main\]$/m);
});

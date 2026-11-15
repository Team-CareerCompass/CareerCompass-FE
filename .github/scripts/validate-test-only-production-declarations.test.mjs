import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
    EXEMPT_LABEL,
    addedFunctionNames,
    candidateDeclarations,
    filesFromGitDiff,
    findViolations,
    hasExemptLabel,
    referencePattern,
} from "./validate-test-only-production-declarations.mjs";

const scriptPath = fileURLToPath(new URL("./validate-test-only-production-declarations.mjs", import.meta.url));
const gitEnvironment = {
    ...process.env,
    GIT_AUTHOR_NAME: "t", GIT_AUTHOR_EMAIL: "t@t", GIT_COMMITTER_NAME: "t", GIT_COMMITTER_EMAIL: "t@t",
};

function git(repo, ...args) {
    const result = spawnSync("git", ["-C", repo, ...args], { encoding: "utf8", env: gitEnvironment });
    assert.equal(result.status, 0, result.stderr);
    return result.stdout;
}

function write(repo, relative, text) {
    mkdirSync(join(repo, relative, ".."), { recursive: true });
    writeFileSync(join(repo, relative), text);
}

// develop 에 Base.kt, feat 브랜치에 새 함수 6종 — 테스트만/아무도/main 호출/private/override/파일 안 호출.
function fixture() {
    const repo = mkdtempSync(join(tmpdir(), "test-only-production-"));
    git(repo, "init", "-q", "-b", "develop");
    write(repo, "app/src/main/kotlin/a/Base.kt", "package a\n\nclass Base {\n    fun stay() = 1\n}\n");
    git(repo, "add", ".");
    git(repo, "commit", "-q", "-m", "base");
    git(repo, "checkout", "-q", "-b", "feat/x");
    write(repo, "app/src/main/kotlin/a/Vm.kt", `package a

class Vm : Parent() {
    fun onlyTestCalls() = 1
    fun nobodyCalls() = 2
    fun usedInMain() = 3
    private fun hidden() = 4
    override fun onCleared() = Unit
    fun helperUsedHere() = 5
    fun caller() = helperUsedHere()
}
`);
    write(repo, "app/src/main/kotlin/a/Route.kt", "package a\n\nprivate fun route(vm: Vm) { vm.usedInMain(); vm.caller() }\n");
    write(repo, "app/src/test/kotlin/a/VmTest.kt", "package a\n\nclass VmTest { fun t() { Vm().onlyTestCalls() } }\n");
    git(repo, "add", ".");
    git(repo, "commit", "-q", "-m", "feat");
    return repo;
}

const VM_PATCH = `@@ -0,0 +1,10 @@
+package a
+
+class Vm : Parent() {
+    fun onlyTestCalls() = 1
+    fun nobodyCalls() = 2
+    fun usedInMain() = 3
+    private fun hidden() = 4
+    override fun onCleared() = Unit
+    fun helperUsedHere() = 5
+    fun caller() = helperUsedHere()
+}`;

test("추가된 줄의 fun 선언만 모으고 private·override·operator·abstract 는 뺀다", () => {
    assert.deepEqual(addedFunctionNames(VM_PATCH), ["onlyTestCalls", "nobodyCalls", "usedInMain", "helperUsedHere", "caller"]);
    assert.deepEqual(addedFunctionNames("@@ -1 +1 @@\n-fun old()\n+    internal suspend fun <T> List<T>.pick(i: Int)"), ["pick"]);
    assert.deepEqual(addedFunctionNames("+    @Composable\n+    fun Screen(state: S) {\n+    operator fun plus(o: A) = o\n+    abstract fun load()"), ["Screen"]);
    assert.deepEqual(addedFunctionNames("+++ b/x.kt\n fun context() = 1\n-fun removed() = 2"), []);
});

test("src/main Kotlin 만 보고, testing 모듈·konsist·삭제·순수 rename 은 건너뛴다", () => {
    const files = [
        { filename: "app/src/main/kotlin/a/Vm.kt", status: "added", patch: VM_PATCH },
        { filename: "app/src/test/kotlin/a/VmTest.kt", status: "added", patch: "+fun onlyInTest() = 1" },
        { filename: "core/domain/testing/src/main/kotlin/Fake.kt", status: "added", patch: "+fun fake() = 1" },
        { filename: "konsist/src/main/kotlin/K.kt", status: "added", patch: "+fun k() = 1" },
        { filename: "app/src/main/kotlin/a/Gone.kt", status: "removed", patch: "-fun gone() = 1" },
        { filename: "app/src/main/kotlin/a/Moved.kt", status: "renamed", changes: 0 },
        { filename: "app/src/main/res/values/strings.xml", status: "modified", patch: "+<string name=\"fun\">fun x(</string>" },
    ];
    assert.deepEqual(
        candidateDeclarations(files).map((d) => d.name),
        ["onlyTestCalls", "nobodyCalls", "usedInMain", "helperUsedHere", "caller"],
    );
});

test("main Kotlin 파일에 patch 가 없으면 통과시키지 않고 실패한다", () => {
    assert.throws(
        () => candidateDeclarations([{ filename: "app/src/main/kotlin/a/Big.kt", status: "modified", changes: 3 }]),
        /Big\.kt: .*patch 가 없어/,
    );
});

test("참조 패턴은 POSIX ERE 다 — \\b·\\s 를 쓰지 않는다", () => {
    const pattern = referencePattern("toggle$x");
    assert.doesNotMatch(pattern, /\\[bs]/);
    assert.match(pattern, /\[\[:space:\]\]/);
    assert.match(pattern, /::toggle\\\$x/);
});

test("테스트만 부르는 함수와 아무도 안 부르는 함수만 위반이다", async () => {
    const repo = fixture();
    const files = [{ filename: "app/src/main/kotlin/a/Vm.kt", status: "added", patch: VM_PATCH }];

    const violations = await findViolations(files, { root: repo });

    assert.deepEqual(violations, [
        { filename: "app/src/main/kotlin/a/Vm.kt", name: "onlyTestCalls", kind: "test-only" },
        { filename: "app/src/main/kotlin/a/Vm.kt", name: "nobodyCalls", kind: "unreferenced" },
    ]);
});

test("main 호출자를 붙이면 위반이 사라진다", async () => {
    const repo = fixture();
    write(repo, "app/src/main/kotlin/a/Route.kt",
        "package a\n\nprivate fun route(vm: Vm) { vm.usedInMain(); vm.caller(); vm.onlyTestCalls(); vm.nobodyCalls() }\n");
    git(repo, "commit", "-q", "-am", "wire");

    assert.deepEqual(await findViolations([{ filename: "app/src/main/kotlin/a/Vm.kt", status: "added", patch: VM_PATCH }], { root: repo }), []);
});

test("--local 은 git diff 로 같은 files 배열을 만들고 리비전에 대고 검사한다", async () => {
    const repo = fixture();
    const files = filesFromGitDiff(repo, "develop", "HEAD");

    assert.deepEqual(files.map((f) => f.filename), ["app/src/main/kotlin/a/Route.kt", "app/src/main/kotlin/a/Vm.kt"]);
    // Actions 러너처럼 GITHUB_WORKSPACE 가 다른 저장소를 가리켜도 --local 은 cwd 를 본다.
    const result = spawnSync("node", [scriptPath, "--local", "develop", "HEAD"], {
        cwd: repo, encoding: "utf8", env: { ...process.env, GITHUB_WORKSPACE: "/nonexistent-workspace" },
    });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stdout, /::error file=app\/src\/main\/kotlin\/a\/Vm\.kt::.*onlyTestCalls — main 참조 0, 테스트만 부른다/);
    assert.match(result.stdout, /nobodyCalls — main 참조 0, 아무도 안 부른다/);
    assert.doesNotMatch(result.stdout, /usedInMain|hidden|onCleared|helperUsedHere|caller|route/);
});

test("PR 모드는 files API 페이지를 읽고, 면제 라벨이 있으면 경고로 낮춰 통과시킨다", async () => {
    const repo = fixture();
    const filesPath = join(repo, "files.json");
    writeFileSync(filesPath, JSON.stringify([[{ filename: "app/src/main/kotlin/a/Vm.kt", status: "added", patch: VM_PATCH }]]));
    const prPath = join(repo, "pr.json");

    writeFileSync(prPath, JSON.stringify({ labels: [] }));
    const blocked = spawnSync("node", [scriptPath, prPath, filesPath], { cwd: repo, encoding: "utf8", env: { ...process.env, GITHUB_WORKSPACE: repo } });
    assert.equal(blocked.status, 1, blocked.stdout + blocked.stderr);
    assert.match(blocked.stdout, /::error file=/);
    assert.match(blocked.stdout, new RegExp(EXEMPT_LABEL));

    writeFileSync(prPath, JSON.stringify({ labels: [{ name: EXEMPT_LABEL }] }));
    const exempted = spawnSync("node", [scriptPath, prPath, filesPath], { cwd: repo, encoding: "utf8", env: { ...process.env, GITHUB_WORKSPACE: repo } });
    assert.equal(exempted.status, 0, exempted.stdout + exempted.stderr);
    assert.match(exempted.stdout, /::warning file=/);
    // repository-quality 가 실제로 넘기는 모양은 `{pull_request: {...}}` 봉투다. 봉투를 벗기지 않으면
    // 라벨을 못 찾아 면제가 영영 듣지 않는다 — 게이트 문서가 가리키는 탈출구가 조용히 막힌다.
    writeFileSync(prPath, JSON.stringify({ pull_request: { labels: [{ name: EXEMPT_LABEL }] } }));
    const wrapped = spawnSync("node", [scriptPath, prPath, filesPath], { cwd: repo, encoding: "utf8", env: { ...process.env, GITHUB_WORKSPACE: repo } });
    assert.equal(wrapped.status, 0, wrapped.stdout + wrapped.stderr);
    assert.match(wrapped.stdout, /::warning file=/);

    assert.equal(hasExemptLabel({ labels: [{ name: EXEMPT_LABEL }] }), true);
    assert.equal(hasExemptLabel({ labels: ["issue-assignee-exempt"] }), false);
});

#!/usr/bin/env node

// PR 이 src/main 에 새로 넣은 Kotlin 함수가 main 어디서도 참조되지 않으면 실패시킨다 (#1895).
//
// 0904 PR #1583 에 ViewModel 멤버 `onReceiverRegisterStart()` 가 main 호출자 없이 테스트만 참조하는
// 채로 올라갔다. `ProductionVisibilityKonsistTest`(#1678) 는 최상위 선언만 보므로 멤버 함수는 어떤
// 게이트에도 걸리지 않았다. 이 스크립트는 PR files API 의 `patch` 에서 «추가된» 선언만 뽑아
// 체크아웃된 PR 트리에서 이름 참조를 센다 — 기존 코드의 미참조 멤버는 대상이 아니라 baseline 이 없다.
//
// 판정은 이름 기반이라 거짓 음성 쪽으로 기운다(같은 이름이 main 어디든 있으면 통과). 참조는 있지만
// 죽은 코드(항상 첫 줄에서 return · 소비자가 항상 null 을 넘기는 파라미터)는 못 본다 — 리뷰 몫이다.

import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { flattenPaginatedFiles } from "./classify-documentation-changes.mjs";

export const EXEMPT_LABEL = "test-only-production-exempt";

const MAIN_KOTLIN_RE = /(^|\/)src\/main\/.*\.kt$/;
// testing 모듈의 main 은 테스트 픽스처다. konsist·build-logic 은 앱 코드가 아니다.
const SKIP_PATH_RE = /\/testing\/|^konsist\/|^build-logic\//;
const FUN_DECL_RE =
    /^\+\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|protected)\s+)?(?:(?:suspend|inline|infix|tailrec|open|actual|expect)\s+)*fun\s+(?:<[^>]+>\s+)?(?:[\w.<>?*, ]+\.)?(\w+)\s*\(/;
// private 은 파일 안, override 는 인터페이스, operator 는 연산자 문법, abstract 는 구현체를 거쳐 불린다.
const SKIP_DECL_RE = /^\+[^\n]*\b(?:private|override|operator|abstract)\b[^\n]*\bfun\b/;

export function addedFunctionNames(patch) {
    const names = [];
    for (const line of String(patch ?? "").split("\n")) {
        if (!line.startsWith("+") || line.startsWith("+++") || SKIP_DECL_RE.test(line)) continue;
        const match = FUN_DECL_RE.exec(line);
        if (match) names.push(match[1]);
    }
    return names;
}

function isCandidateFile(file, index) {
    if (file === null || typeof file !== "object" || typeof file.filename !== "string") {
        throw new Error(`pull request file at index ${index} has no valid filename`);
    }
    if (!MAIN_KOTLIN_RE.test(file.filename) || SKIP_PATH_RE.test(file.filename)) return false;
    if (file.status === "removed") return false;
    // 순수 rename 은 patch 가 없고 새 선언도 없다.
    if (file.status === "renamed" && Number(file.changes ?? 0) === 0) return false;
    return true;
}

export function candidateDeclarations(files) {
    const declarations = [];
    files.forEach((file, index) => {
        if (!isCandidateFile(file, index)) return;
        if (typeof file.patch !== "string") {
            // fail-closed — patch 없이 «새 선언 없음» 으로 통과시키면 큰 파일이 게이트를 우회한다.
            throw new Error(`${file.filename}: pull request files API 응답에 patch 가 없어 새 선언을 판정할 수 없습니다`);
        }
        for (const name of addedFunctionNames(file.patch)) {
            declarations.push({ filename: file.filename, name });
        }
    });
    return declarations;
}

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

// git grep 은 POSIX ERE 다 — \b·\s 는 GNU 확장이라 플랫폼에 따라 글자로 읽힌다.
export function referencePattern(name) {
    const escaped = escapeRegExp(name);
    return `(^|[^[:alnum:]_])${escaped}[[:space:]]*\\(|::${escaped}([^[:alnum:]_]|$)`;
}

function gitGrepCount(root, revision, pattern, pathspecs) {
    const args = ["-C", root, "grep", "-c", "-E", pattern];
    if (revision) args.push(revision);
    args.push("--", ...pathspecs);
    const result = spawnSync("git", args, { encoding: "utf8" });
    if (result.status !== 0 && result.status !== 1) {
        throw new Error(`git grep failed (${result.status}): ${result.stderr.trim()}`);
    }
    return result.stdout
        .split("\n")
        .filter((line) => line.includes(":"))
        .reduce((total, line) => total + Number(line.slice(line.lastIndexOf(":") + 1)), 0);
}

async function ownFileText(root, revision, filename) {
    if (revision) {
        const result = spawnSync("git", ["-C", root, "show", `${revision}:${filename}`], { encoding: "utf8" });
        return result.status === 0 ? result.stdout : "";
    }
    try {
        return await readFile(resolve(root, filename), "utf8");
    } catch {
        return "";
    }
}

async function ownFileReferences(root, revision, filename, name) {
    const escaped = escapeRegExp(name);
    const use = new RegExp(`(^|[^\\w])${escaped}\\s*\\(|::${escaped}\\b`);
    const declaration = new RegExp(`\\bfun\\s+(?:<[^>]+>\\s+)?(?:[\\w.<>?*, ]+\\.)?${escaped}\\s*\\(`);
    return (await ownFileText(root, revision, filename))
        .split("\n")
        .filter((line) => use.test(line) && !declaration.test(line)).length;
}

export async function findViolations(files, { root, revision = "" } = {}) {
    const violations = [];
    for (const { filename, name } of candidateDeclarations(files)) {
        const pattern = referencePattern(name);
        const mainReferences =
            gitGrepCount(root, revision, pattern, ["*/src/main/*", `:!${filename}`]) +
            (await ownFileReferences(root, revision, filename, name));
        if (mainReferences > 0) continue;
        const testReferences = gitGrepCount(root, revision, pattern, ["*/src/test/*", "*/src/androidTest/*"]);
        violations.push({ filename, name, kind: testReferences > 0 ? "test-only" : "unreferenced" });
    }
    return violations;
}

const KIND_LABEL = { "test-only": "테스트만 부른다", unreferenced: "아무도 안 부른다" };

export function describe(violation) {
    return `${violation.filename}::${violation.name} — main 참조 0, ${KIND_LABEL[violation.kind]}`;
}

export function hasExemptLabel(pullRequest) {
    return (pullRequest?.labels ?? []).some((label) => (typeof label === "string" ? label : label?.name) === EXEMPT_LABEL);
}

// 로컬 점검용 — `--local <base> [head]` 는 API 없이 git diff 로 같은 files 배열을 만든다.
export function filesFromGitDiff(root, base, head = "HEAD") {
    const result = spawnSync(
        "git",
        ["-C", root, "diff", `${base}...${head}`, "--diff-filter=AMR", "--", "*/src/main/*.kt"],
        { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
    );
    if (result.status !== 0) throw new Error(`git diff failed: ${result.stderr.trim()}`);
    const files = [];
    let current = null;
    for (const line of result.stdout.split("\n")) {
        const header = /^diff --git a\/(.+?) b\/(.+)$/.exec(line);
        if (header) {
            current = { filename: header[2], status: "modified", changes: 1, patch: "" };
            files.push(current);
            continue;
        }
        if (current && (line.startsWith("+") || line.startsWith("-") || line.startsWith("@@") || line.startsWith(" "))) {
            current.patch += `${line}\n`;
        }
    }
    return files;
}

async function main(argv) {
    // --local 은 개발자가 저장소 루트에서 부른다. Actions 러너에서 돌리는 테스트에도 GITHUB_WORKSPACE 가
    // 실제 저장소로 잡혀 있으므로, 그 값은 PR 모드에서만 믿는다 (0904 CI 실측 — fixture 대신 레포를 읽었다).
    const root = argv[0] === "--local" ? process.cwd() : process.env.GITHUB_WORKSPACE || process.cwd();
    let files;
    let pullRequest = { labels: [] };
    let revision = "";
    if (argv[0] === "--local") {
        const [, base, head = "HEAD"] = argv;
        if (!base) throw new Error("--local <base> [head]");
        revision = head;
        files = filesFromGitDiff(root, base, head);
    } else {
        const [pullRequestPath, filesPath] = argv;
        if (!pullRequestPath || !filesPath) {
            throw new Error("usage: validate-test-only-production-declarations.mjs <pull-request.json> <files.json> | --local <base> [head]");
        }
        // 워크플로가 넘기는 파일은 `{pull_request: {...}}` 봉투다(repository-quality 의 policy payload).
        // 봉투를 벗기지 않으면 `labels` 를 못 찾아 **면제 라벨이 영영 듣지 않는다** — 게이트 문서가
        // 가리키는 탈출구가 조용히 막힌 채로 남는다. 봉투 없이 PR 객체를 그대로 주는 호출도 받는다.
        const payload = JSON.parse(await readFile(pullRequestPath, "utf8"));
        pullRequest = payload?.pull_request ?? payload;
        files = flattenPaginatedFiles(JSON.parse(await readFile(filesPath, "utf8")));
    }

    const violations = await findViolations(files, { root, revision });
    if (violations.length === 0) {
        console.log("새로 넣은 프로덕션 함수는 전부 main 에서 참조된다.");
        return 0;
    }
    const exempt = hasExemptLabel(pullRequest);
    for (const violation of violations) {
        console.log(`::${exempt ? "warning" : "error"} file=${violation.filename}::${describe(violation)}`);
    }
    console.log(
        `${violations.length}건 — 프로덕션 호출자가 없는 함수는 지금 필요 없는 코드다. 함수와 그 테스트를 빼거나 main 호출자를 붙여라.` +
            (exempt
                ? ` (${EXEMPT_LABEL} 라벨로 면제 — PR 본문에 소비처를 적어 두었는지 확인)`
                : ` 후속 PR 이 곧 소비하는 공개 계약이면 PR 에 \`${EXEMPT_LABEL}\` 라벨을 붙이고 본문에 소비처를 적는다.`),
    );
    return exempt ? 0 : 1;
}

const isDirectExecution = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirectExecution) {
    main(process.argv.slice(2))
        .then((code) => {
            process.exitCode = code;
        })
        .catch((error) => {
            console.error(`::error::${error.message}`);
            process.exitCode = 1;
        });
}

import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { inspectModules } from './resolve-pr-impact.mjs';

const workflowDirectory = new URL('../workflows/', import.meta.url);
const repoRoot = path.resolve(fileURLToPath(new URL('../..', import.meta.url)));

function escapeForRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const dependencyPathFilters = [
  '**/*.gradle',
  '**/*.gradle.kts',
  '**/*.lockfile',
  '**/build-logic/**',
  '**/buildSrc/**',
  '**/gradle.properties',
  '**/gradlew',
  '**/gradlew.bat',
  'gradle/**',
  '.github/workflows/dependency-review.yml',
  '.github/workflows/dependency-submission.yml',
  '.github/workflows/dependency-submission-trusted.yml',
  '.github/workflows/dependency-submission-upload.yml',
];

// Settings → Actions → General → Actions permissions 의 실제 저장소 설정을 코드 쪽에
// 미러링한다 (#1538). 이 목록 밖의 액션은 CI 가 아니라 run 시작 시점에 startup_failure 로
// 죽고, 그 사유(«... is not allowed in Team-CareerCompass/CareerCompass-FE»)는 job 이 만들어지지 않아
// 로그에도 남지 않는다 — 관리자만 설정 화면에서 확인할 수 있다. 그 침묵을 막으려고
// 아래 테스트가 diff 옆에서 먼저 red 를 낸다.
//
// 실측값 (GET /repos/Team-CareerCompass/CareerCompass-FE/actions/permissions[/selected-actions]):
//   allowed_actions: "selected", sha_pinning_required: true
//   github_owned_allowed: true, verified_allowed: true
const repositoryActionsPolicy = {
    // github_owned_allowed 가 커버하는 소유자. 2026-08-30 실측으로 `github` 조직도
    // GitHub 소유로 취급된다 — github/codeql-action 은 patterns 없이 통과한다.
    githubOwnedOwners: ['actions', 'github'],
    // verified_allowed 가 이 셋을 이미 덮지만 그것에 기대지 않는다. Marketplace 검증은
    // 언제든 취소될 수 있고 그때 정지하는 것은 릴리스·스크린샷 파이프라인이다.
    patternsAllowed: [
        'docker/build-push-action@*',
        'docker/setup-buildx-action@*',
        'google-github-actions/auth@*',
        'gradle/actions/dependency-submission@*',
        'gradle/actions/setup-gradle@*',
    ],
};

async function workflows() {
  const names = (await readdir(workflowDirectory)).filter((name) => name.endsWith('.yml'));
  return Promise.all(
    names.map(async (name) => [name, await readFile(new URL(name, workflowDirectory), 'utf8')]),
  );
}

function runnerJobBlocks(source) {
  const lines = source.split('\n');
  const blocks = [];
  let inJobs = false;
  let current = null;

  const flush = () => {
    if (current && /^    runs-on:/m.test(current.source)) {
      blocks.push(current);
    }
    current = null;
  };

  for (const line of lines) {
    if (line === 'jobs:') {
      inJobs = true;
      continue;
    }
    if (!inJobs) {
      continue;
    }
    if (/^\S/.test(line)) {
      flush();
      inJobs = false;
      continue;
    }
    const job = /^  ([A-Za-z0-9_-]+):\s*$/.exec(line);
    if (job) {
      flush();
      current = { name: job[1], source: `${line}\n` };
    } else if (current) {
      current.source += `${line}\n`;
    }
  }
  flush();
  return blocks;
}

function checkoutStepBlocks(source) {
  const lines = source.split('\n');
  const blocks = [];
  for (let index = 0; index < lines.length; index += 1) {
    if (!/^\s+uses:\s*actions\/checkout@/.test(lines[index])) {
      continue;
    }
    const usesIndent = /^\s*/.exec(lines[index])[0].length;
    const stepIndent = usesIndent - 2;
    const block = [lines[index]];
    for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
      const line = lines[cursor];
      const indent = /^\s*/.exec(line)[0].length;
      if (line.trim().length > 0 && indent <= stepIndent) {
        break;
      }
      block.push(line);
    }
    blocks.push(block.join('\n'));
  }
  return blocks;
}

function eventPathFilters(source, eventName) {
  const match = new RegExp(`^  ${eventName}:\\n    paths:\\n((?:      - '[^']+'\\n)+)`, 'm').exec(
    source,
  );
  assert.ok(match, `${eventName} must declare a paths filter`);
  return match[1].trim().split('\n').map((line) => {
    const path = /^- '([^']+)'$/.exec(line.trim());
    assert.ok(path, `invalid dependency path filter: ${line}`);
    return path[1];
  });
}

function workflowName(source) {
  const match = /^name:\s*(.+)$/m.exec(source);
  assert.ok(match, 'workflow must declare a top-level name');
  return match[1].trim();
}

// 같은 저장소의 로컬 액션·reusable workflow 는 uses 대상이 이 저장소의 리비전이라
// 고정 대상이 아니다. 그 밖은 전부 40자리 commit SHA 여야 한다.
function isLocalReference(reference) {
    return reference.startsWith("./");
}

function isPinnedToCommit(reference) {
    return /@[0-9a-f]{40}$/.test(reference);
}

function actionReferences(source) {
    return [...source.matchAll(/^\s*(?:-\s+)?uses:\s*(\S+)/gm)].map((match) => match[1]);
}

// GitHub 의 patterns_allowed 매칭을 그대로 흉내 낸다 — `*` 는 경로 구분자를 넘지 않는다.
// 그래서 `gradle/actions/*@*` 같은 넓은 표현 대신 액션마다 한 줄씩 적는다.
function matchesAllowedPattern(reference, pattern) {
    const expression = pattern.split('*').map(escapeForRegex).join('[^/]*');
    return new RegExp(`^${expression}$`).test(reference);
}

function isGithubOwnedAction(reference) {
    return repositoryActionsPolicy.githubOwnedOwners.includes(reference.split('/')[0]);
}

function isAllowedByRepositoryPolicy(reference) {
    return (
        isLocalReference(reference) ||
        isGithubOwnedAction(reference) ||
        repositoryActionsPolicy.patternsAllowed.some((pattern) =>
            matchesAllowedPattern(reference, pattern),
        )
    );
}

async function compositeActions() {
    const directory = new URL('../actions/', import.meta.url);
    const names = await readdir(directory);
    const sources = [];
    for (const name of names) {
        try {
            sources.push([
                `.github/actions/${name}/action.yml`,
                await readFile(new URL(`${name}/action.yml`, directory), 'utf8'),
            ]);
        } catch {
            continue;
        }
    }
    assert.ok(sources.length > 0, 'composite action 을 하나도 못 읽었다 — 판정이 망가졌다');
    return sources;
}

function requiresGradleSetup(source) {
  return source.includes('./gradlew') || /uses:\s*gradle\/actions\/setup-gradle@/.test(source);
}

test('recognizes direct and script-delegated Gradle workflows', () => {
  assert.equal(requiresGradleSetup('run: ./gradlew test'), true);
  assert.equal(
    requiresGradleSetup('uses: gradle/actions/setup-gradle@v4\nrun: bash scripts/build.sh'),
    true,
  );
  assert.equal(
    requiresGradleSetup('uses: gradle/actions/dependency-submission@0123456789abcdef'),
    false,
  );
});

test('recognizes floating action references that the pinning policy must reject', () => {
    // 정책이 실제로 무엇을 막는지 fixture 로 고정한다 — 태그·브랜치·짧은 SHA 는 전부
    // 사후에 다른 코드로 바뀔 수 있다.
    assert.equal(isPinnedToCommit('actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1'), true);
    assert.equal(isPinnedToCommit('actions/checkout@v7'), false);
    assert.equal(isPinnedToCommit('ScaCap/action-ktlint@master'), false);
    assert.equal(isPinnedToCommit('actions/checkout@3d3c42e'), false);
    assert.equal(isLocalReference('./.github/actions/setup-ci-config'), true);
    assert.equal(isLocalReference('actions/checkout@v7'), false);
});

test('every third-party action is pinned to an immutable commit SHA', async () => {
    const unpinned = [];
    for (const [name, source] of await workflows()) {
        for (const reference of actionReferences(source)) {
            if (isLocalReference(reference) || isPinnedToCommit(reference)) {
                continue;
            }
            unpinned.push(`${name}: ${reference}`);
        }
    }

    assert.deepEqual(unpinned, []);
});

test('recognizes the references the repository allow list must reject', () => {
    // 2026-08-30 실측 fixture — 아래 두 종류만 통과해야 한다: GitHub 소유 액션과
    // patterns_allowed 에 한 줄로 적힌 액션.
    assert.equal(
        isAllowedByRepositoryPolicy('actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1'),
        true,
    );
    assert.equal(
        isAllowedByRepositoryPolicy(
            'github/codeql-action/init@db488ddef3bf6cb639b32c2e9a7c0a7ea8271d28',
        ),
        true,
    );
    assert.equal(
        isAllowedByRepositoryPolicy(
            'gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb',
        ),
        true,
    );
    assert.equal(isAllowedByRepositoryPolicy('./.github/actions/setup-ci-config'), true);
    // 목록 밖 서드파티 — startup_failure 로 확인한 차단 대상이다.
    assert.equal(
        isAllowedByRepositoryPolicy('hmarr/debug-action@cd1afbd7852b7ad7b1b7a9a1b03efebd3b0a1820'),
        false,
    );
    // 같은 저장소의 다른 액션이라도 목록에 없으면 막힌다 — `*` 는 `/` 를 넘지 않는다.
    assert.equal(
        isAllowedByRepositoryPolicy(
            'gradle/actions/wrapper-validation@9c971963bec38e04b3d30dcc455b5382be2fdbfb',
        ),
        false,
    );
    assert.equal(
        isAllowedByRepositoryPolicy('docker/login-action@0000000000000000000000000000000000000000'),
        false,
    );
});

test('every action reference is covered by the repository allow list', async () => {
    const rejected = [];
    for (const [name, source] of [...(await workflows()), ...(await compositeActions())]) {
        for (const reference of actionReferences(source)) {
            if (isAllowedByRepositoryPolicy(reference)) {
                continue;
            }
            rejected.push(`${name}: ${reference}`);
        }
    }

    assert.deepEqual(
        rejected,
        [],
        '저장소 Actions 허용 목록에 없는 액션이다 — 설정을 먼저 넓히지 않으면 run 이 startup_failure 로 죽는다',
    );
});

test('the repository allow list carries no pattern the workflows stopped using', async () => {
    // 안 쓰는 패턴을 남겨 두면 허용 범위만 넓어지고 아무도 그것을 눈치채지 못한다.
    const references = [...(await workflows()), ...(await compositeActions())].flatMap(
        ([, source]) => actionReferences(source),
    );
    const unused = repositoryActionsPolicy.patternsAllowed.filter(
        (pattern) => !references.some((reference) => matchesAllowedPattern(reference, pattern)),
    );

    assert.deepEqual(unused, []);
});

test('every pinned action records the release it was pinned from', async () => {
    // SHA 만 있으면 무엇을 쓰고 있는지 사람이 읽을 수 없고 Dependabot 갱신도 대조가 안 된다.
    const undocumented = [];
    for (const [name, source] of await workflows()) {
        for (const line of source.split('\n')) {
            const match = /^\s*(?:-\s+)?uses:\s*(\S+@[0-9a-f]{40})(.*)$/.exec(line);
            if (match && !/^\s*#\s*v?\d/.test(match[2])) {
                undocumented.push(`${name}: ${match[1]}`);
            }
        }
    }

    assert.deepEqual(undocumented, []);
});

test('composite actions in this repository pin their own dependencies', async () => {
    const directory = new URL('../actions/', import.meta.url);
    const names = await readdir(directory);
    for (const name of names) {
        let source;
        try {
            source = await readFile(new URL(`${name}/action.yml`, directory), 'utf8');
        } catch {
            continue;
        }
        for (const reference of actionReferences(source)) {
            assert.ok(
                isLocalReference(reference) || isPinnedToCommit(reference),
                `${name} uses an unpinned action: ${reference}`,
            );
        }
    }
});

test('every workflow that runs the Gradle wrapper uses the pinned setup-gradle action', async () => {
  const gradleWorkflows = (await workflows()).filter(([, source]) => requiresGradleSetup(source));
  assert.ok(gradleWorkflows.length > 0);

  for (const [name, source] of gradleWorkflows) {
    assert.match(
      source,
      /gradle\/actions\/setup-gradle@[0-9a-f]{40} # v\d+\.\d+\.\d+/,
      `${name} must configure Gradle from an immutable action revision`,
    );
    assert.match(source, /cache-provider:\s*basic/, `${name} must use the open-source cache provider`);
    assert.match(source, /validate-wrappers:\s*true/, `${name} must fail closed on wrapper tampering`);
  }
});

test('Gradle caching has a single owner in every workflow', async () => {
  for (const [name, source] of await workflows()) {
    assert.doesNotMatch(source, /^\s+cache:\s*['"]?gradle['"]?\s*$/m, `${name} uses setup-java caching`);
    if (requiresGradleSetup(source)) {
      assert.doesNotMatch(source, /uses:\s*actions\/cache@/, `${name} uses a competing Gradle cache`);
    }
  }
});

test('every runner job has an explicit positive timeout', async () => {
  const missing = [];
  for (const [workflow, source] of await workflows()) {
    for (const job of runnerJobBlocks(source)) {
      const timeout = /^    timeout-minutes:\s*(.+)\s*$/m.exec(job.source)?.[1];
      if (!timeout || (!/^[1-9][0-9]*$/.test(timeout) && !/^\$\{\{.+\}\}$/.test(timeout))) {
        missing.push(`${workflow}: ${job.name}`);
      }
    }
  }
  assert.deepEqual(missing, []);
});

test('checkout never persists the workflow token in the worktree', async () => {
  const unsafe = [];
  for (const [workflow, source] of await workflows()) {
    checkoutStepBlocks(source).forEach((block, index) => {
      if (!/^\s+persist-credentials:\s*false\s*$/m.test(block)) {
        unsafe.push(`${workflow}: checkout ${index + 1}`);
      }
    });
  }
  assert.deepEqual(unsafe, []);
});

test('reusable lint jobs keep a read-only token', async () => {
  const lint = await readFile(new URL('../workflows/lint.yml', import.meta.url), 'utf8');
  assert.match(lint, /^permissions:\n  contents: read$/m);
  assert.doesNotMatch(lint, /pull-requests:\s*write/);
});

test('dependency audit resolves and tests every domain module on its current platform', async () => {
  const source = await readFile(
    new URL('../workflows/dependency-audit.yml', import.meta.url),
    'utf8',
  );

  // 모듈 목록을 여기 손으로 적으면 규약 마이그레이션 때 워크플로와 갈린다. #1151 이 domain
  // 3모듈을 JVM 으로 옮겼을 때 이 목록은 낡은 태스크를 그대로 고정하고 있었고, 그래서
  // 「없는 configuration 을 묻는 워크플로」가 초록으로 통과했다(#1306). 주간 스케줄이라
  // 다음 실행 전까지 아무도 몰랐을 결함이다. 빌드 스크립트에서 판정해 규약이 바뀌면
  // 워크플로도 따라오도록 강제한다.
  const modules = await inspectModules(repoRoot);
  const domainModules = modules.filter(
    ({ projectPath }) => projectPath.endsWith(':domain') || projectPath === ':core:model',
  );
  assert.ok(domainModules.length > 0, 'domain 모듈을 하나도 못 찾았다 — 판정이 망가졌다');

  assert.match(source, /\.\/gradlew assembleDebug testDebugUnitTest/);

  domainModules.forEach(({ projectPath, android }) => {
    const configuration = android ? 'debugRuntimeClasspath' : 'runtimeClasspath';
    const command = `${projectPath}:dependencies --configuration ${configuration}`;
    assert.ok(source.includes(command), `${command} is missing`);

    const testTask = android ? `${projectPath}:testDebugUnitTest` : `${projectPath}:test`;
    // `:test` 로만 찾으면 `:testDebugUnitTest` 가 그대로 통과한다 — 경계를 본다.
    assert.ok(
      new RegExp(`${escapeForRegex(testTask)}(?![A-Za-z])`).test(source),
      `${testTask} is missing`,
    );
  });

  // run_report 로 뽑아 놓고 --resolved-report 에 안 적으면 그 모듈은 감사에서 조용히
  // 빠진다. 실패가 아니라 «취약점 없음» 으로 보이는 누락이라 눈에 띄지 않는다.
  const produced = [...source.matchAll(/^\s*run_report\s+(\S+)\s/gm)].map((match) => match[1]);
  assert.ok(produced.length > 0, 'run_report 선언을 하나도 못 찾았다 — 판정이 망가졌다');

  // 플러그인 클래스패스 보고서는 --build-environment-report 로 넘긴다(#312). 어느 플래그든
  // 수집 단계에 들어가야 한다는 판정은 같다.
  const collected = new Set(
    [
      ...source.matchAll(
        /--(?:resolved|build-environment)-report "\$REPORT_DIR\/resolved\/([^"]+)\.txt"/g,
      ),
    ].map((match) => match[1]),
  );
  const missing = produced.filter((name) => !collected.has(name));
  assert.deepEqual(missing, [], `수집 단계에 넘어가지 않는 리포트가 있다: ${missing.join(', ')}`);
});

test('dependency graph generation is immutable, fail closed, and wrapper validated', async () => {
  const prSource = await readFile(
    new URL('../workflows/dependency-submission.yml', import.meta.url),
    'utf8',
  );
  const trustedSource = await readFile(
    new URL('../workflows/dependency-submission-trusted.yml', import.meta.url),
    'utf8',
  );
  const source = `${prSource}\n${trustedSource}`;
  const actionReferences = source.match(/gradle\/actions\/dependency-submission@[0-9a-f]{40} # v\d+\.\d+\.\d+/g);

  assert.equal(actionReferences?.length, 3);
  assert.equal((source.match(/dependency-graph-continue-on-failure:\s*false/g) ?? []).length, 3);
  assert.equal((source.match(/validate-wrappers:\s*true/g) ?? []).length, 3);
  assert.match(prSource, /dependency-graph:\s*generate-and-upload/);
  assert.equal(workflowName(prSource), 'Generate PR Dependency Graph');
  assert.doesNotMatch(prSource, /^  push:/m);
  assert.match(trustedSource, /dependency-graph:\s*generate-and-submit/);
  assert.equal(workflowName(trustedSource), 'Submit Trusted Branch Dependency Graph');
  assert.match(trustedSource, /^  push:/m);
  assert.doesNotMatch(trustedSource, /^  pull_request:/m);
  assert.doesNotMatch(trustedSource, /^    paths:/m);
});

test('manual dependency baseline is hard-wired to the checked-out main SHA', async () => {
  const source = await readFile(
    new URL('../workflows/dependency-submission-trusted.yml', import.meta.url),
    'utf8',
  );
  const manualJob = /^  submit-main-baseline:\n[\s\S]+$/m.exec(source)?.[0];

  assert.match(source, /^  workflow_dispatch:\s*$/m);
  assert.doesNotMatch(source, /^  workflow_dispatch:\n    inputs:/m);
  assert.match(
    source,
    /github\.event_name == 'workflow_dispatch' && 'refs\/heads\/main' \|\| github\.ref/,
  );
  assert.ok(manualJob);
  assert.match(
    manualJob,
    /if:\s*github\.event_name == 'workflow_dispatch' && github\.ref == 'refs\/heads\/develop'/,
  );
  assert.match(manualJob, /GITHUB_DEPENDENCY_GRAPH_REF:\s*refs\/heads\/main/);
  assert.match(manualJob, /^          ref:\s*refs\/heads\/main$/m);
  assert.match(manualJob, /snapshot_sha="\$\(git rev-parse HEAD\)"/);
  assert.match(manualJob, /GITHUB_DEPENDENCY_GRAPH_SHA=\$snapshot_sha/);
  assert.match(manualJob, /dependency-graph:\s*generate-and-submit/);
  assert.doesNotMatch(manualJob, /inputs\./);
});

test('dependency PR workflows use the same complete server-side path filter', async () => {
  const submission = await readFile(
    new URL('../workflows/dependency-submission.yml', import.meta.url),
    'utf8',
  );
  const review = await readFile(
    new URL('../workflows/dependency-review.yml', import.meta.url),
    'utf8',
  );

  assert.deepEqual(eventPathFilters(submission, 'pull_request'), dependencyPathFilters);
  assert.deepEqual(eventPathFilters(review, 'pull_request'), dependencyPathFilters);
  assert.doesNotMatch(submission, /Detect dependency graph input changes/);
  assert.doesNotMatch(review, /Detect dependency graph input changes/);
});

test('the privileged PR graph bridge never checks out or executes pull request code', async () => {
  const source = await readFile(
    new URL('../workflows/dependency-submission-upload.yml', import.meta.url),
    'utf8',
  );
  const prSource = await readFile(
    new URL('../workflows/dependency-submission.yml', import.meta.url),
    'utf8',
  );
  const trustedSource = await readFile(
    new URL('../workflows/dependency-submission-trusted.yml', import.meta.url),
    'utf8',
  );
  const subscribedWorkflow = /workflows:\s*\[([^\]]+)\]/.exec(source)?.[1]?.trim();

  assert.match(source, /workflow_run:/);
  assert.equal(subscribedWorkflow, workflowName(prSource));
  assert.notEqual(subscribedWorkflow, workflowName(trustedSource));
  assert.match(source, /github\.event\.workflow_run\.event == 'pull_request'/);
  assert.match(source, /github\.event\.workflow_run\.conclusion == 'success'/);
  assert.match(source, /actions:\s*read/);
  assert.match(source, /contents:\s*write/);
  assert.match(source, /actions\/github-script@[0-9a-f]{40} # v\d+\.\d+\.\d+/);
  assert.match(source, /listWorkflowRunArtifacts/);
  assert.match(source, /core\.setFailed\('Expected dependency graph artifact/);
  assert.match(source, /if:\s*steps\.dependency-graph-artifact\.outputs\.available == 'true'/);
  assert.match(source, /gradle\/actions\/dependency-submission@[0-9a-f]{40} # v\d+\.\d+\.\d+/);
  assert.match(source, /cache-disabled:\s*true/);
  assert.match(source, /dependency-graph:\s*download-and-submit/);
  assert.doesNotMatch(source, /actions\/checkout@/);
  assert.doesNotMatch(source, /^\s+-?\s*run:/m);
});

test('pull requests beyond the conservative path-filter boundary fail closed', async () => {
  const source = await readFile(
    new URL('../workflows/repository-quality.yml', import.meta.url),
    'utf8',
  );

  assert.match(source, /github\.event\.pull_request\.changed_files/);
  assert.match(source, /MAX_PATH_FILTER_FILES:\s*300/);
  assert.match(source, /CHANGED_FILES > MAX_PATH_FILTER_FILES/);
  // 경계를 넘은 PR 은 여전히 exit 1 로 닫힌다 — 면제는 아래 테스트가 잠그는 릴리스 PR 하나뿐이다.
  assert.match(source, /paths 안전 경계 \$\{MAX_PATH_FILTER_FILES\}개를 초과했습니다[^]*?exit 1/);
});

test('only the release pull request is exempt from the path-filter boundary', async () => {
  const source = await readFile(
    new URL('../workflows/repository-quality.yml', import.meta.url),
    'utf8',
  );

  // 면제 조건은 «릴리스 PR 인가» 다 — base main + head develop 둘 다 요구한다.
  // 크기를 기준으로 열면(예: 파일 수 상한 상향) 가드 자체가 무의미해진다.
  assert.match(source, /base_ref="\$\(jq -r '\.base\.ref' "\$pull_request_file"\)"/);
  assert.match(source, /head_ref="\$\(jq -r '\.head\.ref' "\$pull_request_file"\)"/);
  assert.match(
    source,
    /if \[ "\$base_ref" = "main" \] && \[ "\$head_ref" = "develop" \]; then/,
  );

  // 면제될 때는 실패시키지 않되 근거를 로그에 남긴다.
  assert.match(source, /::notice::릴리스 PR\(develop → main\)이라 paths 경계/);

  // 면제 분기 안에는 exit 가 없어야 하고, 그 밖의 초과는 여전히 실패한다.
  const exemption = source.match(
    /if \[ "\$base_ref" = "main" \] && \[ "\$head_ref" = "develop" \]; then([^]*?)else/,
  );
  assert.ok(exemption, 'release exemption branch not found');
  assert.doesNotMatch(exemption[1], /exit\s+1/);
});

test('dependency review blocks high severity changes without enforcing a license allowlist', async () => {
  const source = await readFile(new URL('../workflows/dependency-review.yml', import.meta.url), 'utf8');

  assert.match(source, /actions\/dependency-review-action@[0-9a-f]{40} # v\d+\.\d+\.\d+/);
  assert.match(source, /fail-on-severity:\s*high/);
  assert.match(source, /license-check:\s*true/);
  assert.doesNotMatch(source, /(allow|deny)-licenses:/);
  assert.doesNotMatch(source, /pull_request_target:/);
});

test('Dependabot updates Actions and the screenshot image but not Gradle versions', async () => {
  const source = await readFile(new URL('../dependabot.yml', import.meta.url), 'utf8');

  assert.match(source, /package-ecosystem:\s*github-actions/);
  assert.match(source, /package-ecosystem:\s*docker/);
  assert.doesNotMatch(source, /package-ecosystem:\s*gradle/);
  assert.doesNotMatch(source, /auto-merge|automerge/);
  assert.doesNotMatch(source, /^\s+- internal$/m);
  assert.equal(source.match(/^\s+- maintenance$/gm)?.length, 2);
  assert.equal(source.match(/^\s+- area:platform$/gm)?.length, 2);
});

test('the Gradle wrapper JAR and distribution stay on the reviewed official checksums', async () => {
  const properties = await readFile(
    new URL('../../gradle/wrapper/gradle-wrapper.properties', import.meta.url),
    'utf8',
  );
  const wrapperJar = await readFile(
    new URL('../../gradle/wrapper/gradle-wrapper.jar', import.meta.url),
  );

  assert.match(
    properties,
    /^distributionUrl=https\\:\/\/services\.gradle\.org\/distributions\/gradle-9\.6\.1-bin\.zip$/m,
  );
  assert.match(
    properties,
    /^distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14$/m,
  );
  assert.match(properties, /^validateDistributionUrl=true$/m);
  assert.equal(
    createHash('sha256').update(wrapperJar).digest('hex'),
    '76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3',
  );
});

export const DEVELOP_VALIDATION_LABEL = "develop-validation";
export const DEVELOP_VALIDATION_MARKER = "<!-- develop-validation-incident -->";

function shaMarker(sha) {
    return `<!-- develop-validation-sha:${sha} -->`;
}

function labelName(label) {
    return typeof label === "string" ? label : label.name;
}

function isOwnedIncident(issue) {
    return (
        !issue.pull_request &&
        issue.user?.login === "github-actions[bot]" &&
        issue.labels?.some((label) => labelName(label) === DEVELOP_VALIDATION_LABEL) &&
        issue.body?.includes(DEVELOP_VALIDATION_MARKER)
    );
}

async function ensureTrackingLabel(github, context) {
    const parameters = {
        owner: context.repo.owner,
        repo: context.repo.repo,
        name: DEVELOP_VALIDATION_LABEL,
    };
    try {
        await github.rest.issues.getLabel(parameters);
    } catch (error) {
        if (error.status !== 404) {
            throw error;
        }
        await github.rest.issues.createLabel({
            ...parameters,
            color: "B60205",
            description: "develop push 조합 검증 실패 incident",
        });
    }
}

// 본문은 이슈 등록 양식(.github/ISSUE_TEMPLATE/issue.yml)의 절 구조를 그대로 따른다.
// Issue Metadata Guard 는 `### 작업 유형`·`### 주 담당 모듈` 을 읽어 라벨과 담당자를 정하고,
// 읽지 못하면 판정 불가로 보고 이슈를 not_planned 로 닫는다. 양식 절이 없으면 develop 이 빨간
// 동안 incident 가 일일 스윕에 사라진다.
const INCIDENT_TYPE_SECTION = "bug — 버그·오동작";
const INCIDENT_MODULE_SECTION = "platform — Android 앱·CI·빌드·릴리스·저장소 운영";

function failureBody({ failures, runUrl, sha }) {
    return [
        DEVELOP_VALIDATION_MARKER,
        shaMarker(sha),
        "### 작업 유형",
        "",
        INCIDENT_TYPE_SECTION,
        "",
        "### 주 담당 모듈",
        "",
        INCIDENT_MODULE_SECTION,
        "",
        "### 개요",
        "",
        "develop 조합 검증이 실패했습니다.",
        "",
        `- commit: \`${sha}\``,
        `- run: ${runUrl}`,
        "",
        "실패한 job:",
        "",
        ...failures.map(({ name, result }) => `- \`${name}\`: **${result}**`),
        "",
        "동일 commit 재실행은 이 이슈를 갱신합니다. 이후 develop push가 통과하면 자동으로 닫힙니다.",
        "",
        "### 자식 이슈",
        "",
        "_No response_",
        "",
        "### 참고",
        "",
        "develop 조합 검증 워크플로가 자동 생성한 이슈입니다.",
    ].join("\n");
}

export async function reconcileDevelopValidationIncident({
    github,
    context,
    core,
    validationResults,
    sha,
}) {
    const { data: developBranch } = await github.rest.repos.getBranch({
        owner: context.repo.owner,
        repo: context.repo.repo,
        branch: "develop",
    });
    const currentSha = developBranch.commit.sha;
    if (currentSha !== sha) {
        core.info(`Skipped stale develop validation run ${sha}; current develop is ${currentSha}.`);
        return { action: "stale", currentSha };
    }

    const failures = Object.entries(validationResults)
        .filter(([, value]) => value.result !== "success")
        .map(([name, value]) => ({ name, result: value.result }));
    const runUrl = `${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`;
    const issues = await github.paginate(github.rest.issues.listForRepo, {
        owner: context.repo.owner,
        repo: context.repo.repo,
        state: "all",
        sort: "updated",
        direction: "desc",
        per_page: 100,
    });
    const trackedIssues = issues.filter(isOwnedIncident);

    if (failures.length > 0) {
        await ensureTrackingLabel(github, context);
        const body = failureBody({ failures, runUrl, sha });
        const title = `🚨 develop 검증 실패 (${sha.slice(0, 7)})`;
        const existing = trackedIssues.find((issue) => issue.body.includes(shaMarker(sha)));

        if (existing) {
            await github.rest.issues.update({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: existing.number,
                title,
                body,
                ...(existing.state === "closed" ? { state: "open", state_reason: "reopened" } : {}),
            });
            core.info(`Reused develop validation issue #${existing.number} for ${sha}.`);
            return { action: "reused", issueNumber: existing.number };
        }

        const { data: created } = await github.rest.issues.create({
            owner: context.repo.owner,
            repo: context.repo.repo,
            title,
            body,
            labels: [DEVELOP_VALIDATION_LABEL],
        });
        core.info(`Created develop validation issue #${created.number} for ${sha}.`);
        return { action: "created", issueNumber: created.number };
    }

    const openIssues = trackedIssues.filter((issue) => issue.state === "open");
    for (const issue of openIssues) {
        await github.rest.issues.createComment({
            owner: context.repo.owner,
            repo: context.repo.repo,
            issue_number: issue.number,
            body: `✅ develop 검증이 \`${sha}\` 에서 복구됐습니다. ${runUrl}`,
        });
        await github.rest.issues.update({
            owner: context.repo.owner,
            repo: context.repo.repo,
            issue_number: issue.number,
            state: "closed",
            state_reason: "completed",
        });
        core.info(`Closed recovered develop validation issue #${issue.number}.`);
    }
    return { action: "recovered", closedIssueNumbers: openIssues.map((issue) => issue.number) };
}

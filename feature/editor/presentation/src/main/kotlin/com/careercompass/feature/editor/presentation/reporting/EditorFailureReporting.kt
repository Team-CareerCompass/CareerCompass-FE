package com.careercompass.feature.editor.presentation.reporting

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.reporting.recordStagedFailure

/** 리포팅 속성 키 — 지원서 기능 안에서 어느 단계가 실패했는지. */
public const val EDITOR_REPORT_KEY_STAGE: String = "editor_stage"

/** 지원서 기능의 실패 단계. 값은 리포팅 콘솔 필터용 안정 식별자다. */
public enum class EditorFailureStage(
    public val key: String,
) {
    /** `GET /postings/{id}` — 문항 확인 화면의 첫 조회. 실패하면 확인할 대상 자체가 없다. */
    SetupLoad("setup_load"),

    /** `POST /applications` — 초안 생성 시작. LLM 장애(503)·호출 한도(429)가 여기로 모인다. */
    DraftCreate("draft_create"),
}

/**
 * [ErrorReporter.recordFailure] 에 지원서 단계 속성을 붙여 기록한다. 취소 필터링은 인터페이스가 한다.
 *
 * 무엇을 접고 무엇을 남길지는 [recordStagedFailure] 한 곳이 정한다 — 온보딩·피드·프로필과 같은 규칙이다.
 */
public fun ErrorReporter.recordEditorFailure(
    stage: EditorFailureStage,
    throwable: Throwable,
) {
    recordStagedFailure(
        stageKey = EDITOR_REPORT_KEY_STAGE,
        stage = stage.key,
        throwable = throwable,
    )
}

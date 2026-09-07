package com.careercompass.feature.editor.domain.model

/**
 * `GET /applications` 한 페이지 — 지원 이력(F4-4).
 *
 * [nextCursor] 가 null 이면 마지막 페이지다(API_SPEC v0.1 「페이징」).
 */
public data class ApplicationHistoryPage(
    val applications: List<ApplicationDraft>,
    val nextCursor: String? = null,
)

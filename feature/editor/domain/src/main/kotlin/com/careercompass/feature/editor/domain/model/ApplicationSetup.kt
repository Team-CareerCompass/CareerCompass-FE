package com.careercompass.feature.editor.domain.model

/**
 * 초안 작성 첫 화면이 그리는 값 — 기능 스펙 F4-1.
 *
 * [recognizedItems] 가 비었다는 것은 「공고에서 문항을 못 찾았다」는 뜻이고, 그 자체가 실패는 아니다.
 * 명세는 그때 사용자가 직접 쓰게 정했다 — 「항목을 못 찾았습니다」로 끝내면 그 공고는 초안을 못 만든다.
 */
public data class ApplicationSetup(
    val postingId: Long,
    val postingTitle: String,
    val recognizedItems: List<ApplicationItemDraft>,
)

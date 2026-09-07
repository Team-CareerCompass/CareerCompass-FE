package com.careercompass.feature.profile.presentation.pastapplication

/**
 * 스낵바 한 줄로 끝나는 알림.
 *
 * 분류 변경은 성공을 알리지 않는다 — 고른 값이 그 자리 칩에 바로 보이므로 문장을 덧붙이면 소음이다.
 * 실패만 말한다.
 */
public enum class PastApplicationListMessage {
    /** 상한(10개)에 닿아 추가 진입점을 막았다. */
    LimitReached,

    /** 분류를 바꾸지 못했다 — 칩은 원래 값으로 남는다. */
    CategoryUpdateFailed,

    Deleted,

    DeleteFailed,
}

/** 화면이 [PastApplicationListViewModel] 에 올려 보내는 사용자 조작. */
public sealed interface PastApplicationListEvent {
    /** 지원서를 펼치거나 접는다 — 분류된 항목은 펼쳤을 때만 보인다. */
    public data class ApplicationToggled(
        val id: Long,
    ) : PastApplicationListEvent

    /** 항목의 분류를 고치러 시트를 연다. */
    public data class ItemCategoryClicked(
        val applicationId: Long,
        val itemId: Long,
    ) : PastApplicationListEvent

    public data class DeleteClicked(
        val id: Long,
    ) : PastApplicationListEvent

    public data object AddClicked : PastApplicationListEvent

    public data object RetryClicked : PastApplicationListEvent

    public data object BackClicked : PastApplicationListEvent
}

/** 삭제 확인 다이얼로그가 가리키는 지원서. null 이면 닫힘이다. */
public data class PastApplicationDeleteTarget(
    val id: Long,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

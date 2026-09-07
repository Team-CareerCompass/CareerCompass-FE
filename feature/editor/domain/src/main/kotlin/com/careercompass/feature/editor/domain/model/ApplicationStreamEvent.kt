package com.careercompass.feature.editor.domain.model

/**
 * `GET /applications/{id}/stream` 이 흘려보내는 한 덩어리 — API_SPEC v0.1 §6.
 *
 * 스펙이 이름까지 적어 둔 것은 [ItemDone] (`event: item_done`) 하나뿐이다. 나머지 둘은 **화면이 성립하려면
 * 있어야 하는 것**이라 여기 두고, 서버에 요구할 목록으로 `docs/spec/canon.md` 에 적었다.
 * 모르는 이름의 덩어리는 사건으로 만들지 않고 버린다 — 서버가 이벤트를 늘려도 스트림이 죽지 않아야 한다.
 */
public sealed interface ApplicationStreamEvent {
    /** `event: item_done` — 항목 하나가 답을 받았다. */
    public data class ItemDone(
        val itemId: Long,
        val answer: String,
    ) : ApplicationStreamEvent

    /**
     * 항목 하나가 실패했다. [code] 는 API_SPEC §9 의 에러 코드(`LLM_UNAVAILABLE` 등), 없으면 null.
     *
     * 이것이 없으면 화면은 실패한 항목을 「아직 쓰는 중」과 구분할 수 없어 영원히 스피너를 돌린다.
     */
    public data class ItemFailed(
        val itemId: Long,
        val code: String?,
    ) : ApplicationStreamEvent

    /** 지원서 전체의 상태가 바뀌었다. 스트림 도중에 `partial_failed` 로 갈리는 자리다. */
    public data class StatusChanged(
        val status: ApplicationStatus,
    ) : ApplicationStreamEvent
}

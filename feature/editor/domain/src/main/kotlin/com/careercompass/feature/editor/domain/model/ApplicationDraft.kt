package com.careercompass.feature.editor.domain.model

/**
 * 지원서 한 건 — API_SPEC v0.1 §6 의 `POST /applications` 응답 그대로.
 *
 * 항목은 서버가 준 `order` 순서로 들고 있는다.
 */
public data class ApplicationDraft(
    val id: Long,
    val status: ApplicationStatus,
    val items: List<ApplicationItem>,
    val postingId: Long? = null,
    val result: ApplicationResult? = null,
) {
    /** 답을 받은 항목 수 / 전체 항목 수 — 진행 표시(#184)가 읽는 값이다. */
    public val completedItemCount: Int
        get() = items.count { it.status == ApplicationItemStatus.Done }

    /** 아직 쓰는 중인 항목이 하나라도 있는가. */
    public val hasLoadingItem: Boolean
        get() = items.any { it.status == ApplicationItemStatus.Loading }
}

/**
 * 스트림 한 덩어리를 반영한 새 초안 — **순수 함수**다.
 *
 * 이 자리를 ViewModel 에 두면 「어느 항목을 어떻게 갈아 끼우는가」가 화면마다 다시 쓰이고, 그때부터 초안
 * 편집 화면과 진행 화면이 서로 다르게 반영한다. 모르는 항목 id 를 가리키는 덩어리는 아무것도 바꾸지 않는다 —
 * 서버가 우리가 모르는 항목을 말하면 그것은 우리 초안이 낡은 것이고, 여기서 항목을 만들어 내면 문항 없는
 * 답이 화면에 생긴다.
 */
public fun ApplicationDraft.applying(event: ApplicationStreamEvent): ApplicationDraft =
    when (event) {
        is ApplicationStreamEvent.ItemDone -> {
            replacingItem(event.itemId) { it.copy(status = ApplicationItemStatus.Done, answer = event.answer) }
        }

        is ApplicationStreamEvent.ItemFailed -> {
            replacingItem(event.itemId) { it.copy(status = ApplicationItemStatus.Failed) }
        }

        is ApplicationStreamEvent.StatusChanged -> {
            copy(status = event.status)
        }
    }

/**
 * 스트림이 닫힌 뒤의 상태.
 *
 * 서버가 마지막 상태를 말해 주지 않고 연결만 닫는 경우가 있다 — 그때 [ApplicationStatus.Generating] 으로
 * 남으면 화면은 오지 않을 항목을 계속 기다린다. 아직 `loading` 인 항목은 답을 못 받은 것이므로 실패로 굳히고,
 * 전체는 실패한 항목이 있으면 [ApplicationStatus.PartialFailed], 없으면 [ApplicationStatus.Ready] 다.
 *
 * 이미 `generating` 이 아니면(저장됐거나 서버가 상태를 말해 줬으면) 손대지 않는다.
 */
public fun ApplicationDraft.settled(): ApplicationDraft {
    if (status != ApplicationStatus.Generating) return this
    val settledItems =
        items.map { item ->
            if (item.status == ApplicationItemStatus.Loading) item.copy(status = ApplicationItemStatus.Failed) else item
        }
    val hasFailure = settledItems.any { it.status == ApplicationItemStatus.Failed }
    return copy(
        status = if (hasFailure) ApplicationStatus.PartialFailed else ApplicationStatus.Ready,
        items = settledItems,
    )
}

private fun ApplicationDraft.replacingItem(
    itemId: Long,
    transform: (ApplicationItem) -> ApplicationItem,
): ApplicationDraft {
    if (items.none { it.id == itemId }) return this
    return copy(items = items.map { if (it.id == itemId) transform(it) else it })
}

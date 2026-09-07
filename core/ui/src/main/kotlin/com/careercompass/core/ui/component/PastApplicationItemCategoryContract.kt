package com.careercompass.core.ui.component

import androidx.compose.runtime.Immutable
import com.careercompass.core.model.application.PastApplicationCategory

/**
 * Step 4 항목의 분류를 고르는 시트 상태 — 기능 스펙 F1-4 「분류가 불확실하면 사용자가 수동 조정」.
 *
 * 어떤 항목을 고치는지 시트가 알아야 저장 결과를 그 항목에만 반영할 수 있어 [documentId]·[itemId] 를 함께 든다.
 *
 * @property documentId 목록 안에서만 유효한 문서 식별자(`remote-<id>`).
 * @property contentPreview 어떤 항목을 고치는지 시트에서 다시 확인할 수 있게 보여 주는 본문 앞부분.
 */
@Immutable
public data class PastApplicationItemCategoryState(
    public val documentId: String,
    public val itemId: Long,
    public val contentPreview: String,
    public val selected: PastApplicationCategory,
) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        require(contentPreview.isNotBlank()) { "contentPreview must not be blank" }
    }
}

/** User intentions emitted by [PastApplicationItemCategorySheet]. */
public sealed interface PastApplicationItemCategoryEvent {
    public data class CategorySelected(
        public val category: PastApplicationCategory,
    ) : PastApplicationItemCategoryEvent

    public data object Dismissed : PastApplicationItemCategoryEvent
}

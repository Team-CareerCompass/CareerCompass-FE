package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 항목 하나를 다시 쓴다 — `POST /applications/{id}/items/{itemId}/regenerate` (§6, F4-3).
 *
 * [tone] 이 null 이면 초안을 만들 때 고른 어조를 그대로 쓴다(서버 기본값). [emphasizeCardIds] 가 비면
 * 필드를 보내지 않는다 — 빈 배열은 「강조할 카드가 없다」이고 미전송은 「고르지 않았다」라, 서버가 둘을
 * 다르게 읽을 여지를 두지 않는다.
 */
public class RegenerateApplicationItemUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            applicationId: Long,
            itemId: Long,
            tone: ApplicationTone? = null,
            emphasizeCardIds: List<Long> = emptyList(),
        ): Result<ApplicationItem> = applicationRepository.regenerateItem(applicationId, itemId, tone, emphasizeCardIds)
    }

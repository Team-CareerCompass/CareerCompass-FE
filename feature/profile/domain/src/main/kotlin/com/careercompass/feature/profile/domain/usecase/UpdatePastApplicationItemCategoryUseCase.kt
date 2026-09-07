package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import javax.inject.Inject

/**
 * 항목 분류 수동 조정 — `PATCH /past-applications/{appId}/items/{itemId}` (API_SPEC v0.1 §4).
 *
 * AI 분류가 불확실한 항목([PastApplicationItem.confident] 가 false)을 사람이 고치는 자리다(F1-4).
 * 서버는 고친 항목 하나를 돌려주므로 화면은 그 항목만 갈아 끼운다 — 목록 전체를 다시 읽지 않는다.
 */
public class UpdatePastApplicationItemCategoryUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(
            applicationId: Long,
            itemId: Long,
            category: PastApplicationCategory,
        ): Result<PastApplicationItem> = pastApplicationRepository.updateItemCategory(applicationId, itemId, category)
    }

package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 최종 저장 — `POST /applications/{id}/save` (§6). 이력이 여기서 생긴다.
 *
 * 저장본을 다시 편집하면 상태가 `ready` 로 돌아가고, 다시 이 호출을 해야 이력에 반영된다.
 */
public class SaveApplicationUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(applicationId: Long): Result<ApplicationDraft> = applicationRepository.save(applicationId)
    }

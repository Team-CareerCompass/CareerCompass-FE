package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/** 지원 결과 입력 — `PATCH /applications/{id}/result` (§6, F4-4). */
public class UpdateApplicationResultUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            applicationId: Long,
            result: ApplicationResult,
        ): Result<ApplicationDraft> = applicationRepository.updateResult(applicationId, result)
    }

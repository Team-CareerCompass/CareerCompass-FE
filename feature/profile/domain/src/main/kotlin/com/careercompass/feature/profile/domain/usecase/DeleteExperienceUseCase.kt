package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.ExperienceRepository
import javax.inject.Inject

/** 경험 카드 삭제 — `DELETE /experiences/{id}` (API_SPEC v0.1 §3). 확인은 화면이 받는다. */
public class DeleteExperienceUseCase
    @Inject
    constructor(
        private val experienceRepository: ExperienceRepository,
    ) {
        public suspend operator fun invoke(id: Long): Result<Unit> = experienceRepository.deleteExperience(id)
    }

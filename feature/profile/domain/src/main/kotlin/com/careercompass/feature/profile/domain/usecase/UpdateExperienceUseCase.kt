package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.ExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDraft
import javax.inject.Inject

/**
 * 경험 카드 수정 — `PATCH /experiences/{id}` (API_SPEC v0.1 §3).
 *
 * 초안은 카드 전체를 담는다. 시점의 정밀도를 잃지 않는 일은 초안을 만드는 쪽이 이미 했다
 * (`ExperienceEditorRules.resolvePoint`, #166 · #171) — 여기서 다시 손대면 그 판정이 두 벌이 된다.
 */
public class UpdateExperienceUseCase
    @Inject
    constructor(
        private val experienceRepository: ExperienceRepository,
    ) {
        public suspend operator fun invoke(
            id: Long,
            draft: ExperienceDraft,
        ): Result<Experience> = experienceRepository.updateExperience(id, draft)
    }

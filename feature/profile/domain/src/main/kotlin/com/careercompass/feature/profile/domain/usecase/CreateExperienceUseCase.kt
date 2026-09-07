package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.ExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDraft
import javax.inject.Inject

/**
 * 경험 카드 등록 — `POST /experiences` (API_SPEC v0.1 §3).
 *
 * 30장 상한은 화면이 먼저 막고([com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS]), 그래도
 * 넘치면 서버가 422 `LIMIT_EXCEEDED` 를 준다 — 두 판정이 겹치는 것은 의도다. 화면은 사용자가 폼을 채우기
 * 전에 막고, 서버는 다른 기기에서 동시에 담은 경우까지 막는다.
 */
public class CreateExperienceUseCase
    @Inject
    constructor(
        private val experienceRepository: ExperienceRepository,
    ) {
        public suspend operator fun invoke(draft: ExperienceDraft): Result<Experience> = experienceRepository.createExperience(draft)
    }

package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import javax.inject.Inject

/**
 * 관심 분야 태그 저장 — `PUT /users/me/tags` (API_SPEC v0.1 §2).
 *
 * 희망 직무와 같이 **전체 교체**다. 상한(최소 1 · 최대 5)과 표기 다듬기는 화면이 이미 지킨다
 * ([com.careercompass.core.model.user.InterestTagRules]).
 */
public class SaveProfileTagsUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
    ) {
        public suspend operator fun invoke(tags: List<String>): Result<Unit> = userProfileRepository.replaceTags(tags)
    }

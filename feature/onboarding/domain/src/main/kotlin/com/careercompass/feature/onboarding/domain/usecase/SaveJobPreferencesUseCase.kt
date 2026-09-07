package com.careercompass.feature.onboarding.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.JobOptionCatalog
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
import com.careercompass.feature.onboarding.domain.repository.OnboardingProgressRepository
import javax.inject.Inject

/**
 * Step 2 희망 직무·관심 분야를 저장하고 진행 상태를 Step 3 로 옮긴다 — 기능 스펙 F1-2 Step 2.
 *
 * [jobCodes] 순서가 곧 우선순위다(첫 번째가 `priority = 1`). 두 요청 중 앞이 실패하면 뒤는 보내지 않는다.
 */
public class SaveJobPreferencesUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
        private val progressRepository: OnboardingProgressRepository,
    ) {
        public suspend operator fun invoke(
            jobCodes: List<String>,
            tags: List<String>,
        ): Result<Unit> {
            require(jobCodes.size in 1..MAX_JOB_INTERESTS) { "jobCodes must be 1..$MAX_JOB_INTERESTS" }
            require(jobCodes.distinct().size == jobCodes.size) { "jobCodes must be unique" }
            require(jobCodes.all(JobOptionCatalog::contains)) { "jobCodes must come from JobOptionCatalog" }
            require(tags.size in 1..MAX_PROFILE_TAGS) { "tags must be 1..$MAX_PROFILE_TAGS" }
            require(tags.all(String::isNotBlank) && tags.distinct().size == tags.size) { "tags must be non-blank and unique" }

            val interests = jobCodes.mapIndexed { index, code -> JobInterest(code = code, priority = index + 1) }
            userProfileRepository.replaceJobInterests(interests).getOrElse { return Result.failure(it) }
            userProfileRepository.replaceTags(tags).getOrElse { return Result.failure(it) }
            return progressRepository.save(OnboardingStep.Experience)
        }
    }

package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.JobInterest
import javax.inject.Inject

/**
 * 희망 직무 저장 — `PUT /users/me/job-interests` (API_SPEC v0.1 §2).
 *
 * **전체 교체다.** 부분 수정이 아니라 목록 통째로 덮으므로 화면은 지금 고른 전부를 보낸다. 우선순위
 * ([JobInterest.priority])는 고른 순서로 1부터 매긴다 — 스펙이 순위를 받는다고만 정하고 그 뜻을 정하지
 * 않아, 사용자가 먼저 고른 것이 더 중요하다는 가장 흔한 해석을 따른다.
 *
 * 상한(최소 1 · 최대 3)은 화면이 이미 지킨다([com.careercompass.core.model.user.MAX_JOB_INTERESTS]).
 * 여기서 다시 세지 않는 이유는 규칙이 두 자리에 있으면 어긋나기 때문이다.
 */
public class SaveJobInterestsUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
    ) {
        public suspend operator fun invoke(codes: List<String>): Result<Unit> =
            userProfileRepository.replaceJobInterests(
                codes.mapIndexed { index, code -> JobInterest(code = code, priority = index + 1) },
            )
    }

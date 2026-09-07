package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.UserProfile
import javax.inject.Inject

/**
 * 서버 프로필을 다시 읽는다 — `GET /users/me`. 성공하면 캐시도 함께 갱신되므로
 * [ObserveProfileUseCase] 를 보는 다른 화면도 새 값을 받는다.
 *
 * 마이 홈은 개수까지 함께 받아야 해서 [LoadProfileHomeUseCase] 를 쓴다. 프로필만 필요한 화면
 * (프로필 편집)이 그 use case 를 부르면 쓰지도 않을 목록 조회 두 번이 딸려 나간다.
 */
public class RefreshProfileUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
    ) {
        public suspend operator fun invoke(): Result<UserProfile> = userProfileRepository.refreshProfile()
    }

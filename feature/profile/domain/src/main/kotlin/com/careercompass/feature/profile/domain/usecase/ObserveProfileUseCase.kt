package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.UserProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 마지막으로 받아 둔 프로필 — 서버를 기다리지 않고 마이 홈의 첫 프레임을 채운다.
 *
 * 조회([LoadProfileHomeUseCase])는 이것과 별개로 돈다. 둘을 하나로 묶지 않는 이유는 **캐시가 있는데도
 * 빈 화면을 보이는 일**을 없애기 위해서다 — 온보딩을 마친 사용자는 이미 프로필을 한 번 받았고, 마이 탭에
 * 들어올 때마다 스피너를 보여 줄 이유가 없다. 앱 시작이 캐시로 목적지를 정하는 것과 같은 규칙이다.
 */
public class ObserveProfileUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
    ) {
        public operator fun invoke(): Flow<UserProfile?> = userProfileRepository.profile
    }

package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.feature.profile.domain.model.ProfileHome
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * 마이 홈 한 장을 채운다 — `GET /users/me` 하나와 배지 개수 둘.
 *
 * ### 실패를 두 층으로 가른다
 * **프로필이 실패하면 이 호출이 실패한다.** 이름도 완성도도 없는 마이 홈은 그릴 것이 없다.
 * **개수가 실패하면 null 로 남기고 성공으로 돌려준다.** 배지 하나가 비는 것과 화면 전체가 실패 화면으로
 * 바뀌는 것은 사용자에게 전혀 다른 일이고, 후자를 고르면 부수적인 요청 하나가 화면 전체를 인질로 잡는다.
 * 세 요청이 각각 401 을 만날 수 있지만 세션 판정의 근거는 프로필 조회 하나로 족하다 — 401 이면 토큰이
 * 이미 죽었으므로 나머지 둘도 같은 실패를 만나고, 화면은 그중 하나만 보면 된다.
 *
 * ### 왜 셋을 동시에 부르는가
 * 서로를 기다릴 이유가 없다. 순서대로 부르면 마이 탭을 열 때마다 왕복 3회가 직렬로 쌓인다. 실패해도
 * 나머지를 취소하지 않는다 — 개수 하나가 죽었다고 프로필을 버릴 이유가 없어서다. 그래서 개수 쪽은
 * `Result` 로 접혀 예외가 스코프를 무너뜨리지 않는다(리포지토리 계약이 이미 `Result` 다).
 */
public class LoadProfileHomeUseCase
    @Inject
    constructor(
        private val userProfileRepository: UserProfileRepository,
        private val countExperienceCards: CountExperienceCardsUseCase,
        private val countPastApplications: CountPastApplicationsUseCase,
    ) {
        public suspend operator fun invoke(): Result<ProfileHome> =
            coroutineScope {
                val experienceCount = async { countExperienceCards() }
                val pastApplicationCount = async { countPastApplications() }
                userProfileRepository.refreshProfile().map { profile ->
                    ProfileHome(
                        profile = profile,
                        experienceCardCount = experienceCount.await().getOrNull(),
                        pastApplicationCount = pastApplicationCount.await().getOrNull(),
                    )
                }
            }
    }

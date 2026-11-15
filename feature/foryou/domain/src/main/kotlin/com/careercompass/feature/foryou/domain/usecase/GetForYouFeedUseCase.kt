package com.careercompass.feature.foryou.domain.usecase

import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import javax.inject.Inject

/**
 * For You 한 화면치 — `GET /feed/for-you` (API_SPEC v0.1 §7).
 *
 * 프로필·경험 카드가 모자라면 서버가 422 `PROFILE_INCOMPLETE` 로 거절한다. 그 실패를 여기서 빈 목록으로
 * 바꾸지 않는다 — 「추천할 것이 없다」와 「추천할 재료가 없다」는 사용자가 할 일이 다르다(전자는 기다리기,
 * 후자는 프로필 채우기).
 */
public class GetForYouFeedUseCase
    @Inject
    constructor(
        private val forYouRepository: ForYouRepository,
    ) {
        public suspend operator fun invoke(): Result<ForYouFeed> = forYouRepository.getForYouFeed()
    }

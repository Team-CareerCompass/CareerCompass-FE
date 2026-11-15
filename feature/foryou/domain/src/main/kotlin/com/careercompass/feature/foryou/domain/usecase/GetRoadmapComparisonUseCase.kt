package com.careercompass.feature.foryou.domain.usecase

import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import javax.inject.Inject

/**
 * 커리어 로드맵 비교 — `GET /roadmap/compare` (API_SPEC v0.1 §7).
 *
 * 기본값을 [RoadmapCohort.Peer] 로 둔 것은 계약의 예시가 그것이고, 셋 중 「내가 지금 어디쯤인가」를 가장
 * 먼저 답하는 축이기 때문이다.
 */
public class GetRoadmapComparisonUseCase
    @Inject
    constructor(
        private val forYouRepository: ForYouRepository,
    ) {
        public suspend operator fun invoke(cohort: RoadmapCohort = RoadmapCohort.Peer): Result<RoadmapComparison> =
            forYouRepository.getRoadmapComparison(cohort)
    }

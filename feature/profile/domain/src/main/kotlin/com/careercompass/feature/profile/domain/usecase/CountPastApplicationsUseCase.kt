package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import javax.inject.Inject

/**
 * 등록한 과거 지원서 수 — `GET /past-applications` 는 페이징이 없어 목록 길이가 곧 개수다(API_SPEC §4).
 *
 * 목록 화면(#180)이 같은 요청을 다시 부를 것이므로 여기서 결과를 캐시하지 않는다. 지금 아는 것은
 * 「몇 개인가」뿐이고, 무엇이 있는지는 그 화면이 그때 다시 읽는 것이 맞다.
 */
public class CountPastApplicationsUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(): Result<Int> = pastApplicationRepository.getPastApplications().map { it.size }
    }

package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import com.careercompass.core.model.application.PastApplication
import javax.inject.Inject

/**
 * 과거 지원서 목록 — `GET /past-applications` (API_SPEC v0.1 §4).
 *
 * 페이징이 없다. 상한이 10개(F1-4)라 한 번에 다 받는 것이 계약이고, 목록 길이가 곧 전체 개수다.
 */
public class GetPastApplicationsUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(): Result<List<PastApplication>> = pastApplicationRepository.getPastApplications()
    }

package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationHistoryPage
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 지원 이력 한 페이지 — `GET /applications` (§6, F4-4).
 *
 * [status] 로 「저장한 것만」처럼 좁힌다. 값을 문자열로 받지 않는 것이 이 자리의 값어치다 — 화면이 `"saved"`
 * 를 손으로 적으면 오타가 조용히 빈 목록이 된다.
 */
public class GetApplicationHistoryUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            status: ApplicationStatus? = null,
            cursor: String? = null,
            limit: Int? = null,
        ): Result<ApplicationHistoryPage> = applicationRepository.getApplications(status, cursor, limit)
    }

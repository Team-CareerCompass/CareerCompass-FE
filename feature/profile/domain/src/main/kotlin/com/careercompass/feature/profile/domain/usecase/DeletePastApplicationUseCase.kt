package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import javax.inject.Inject

/**
 * 과거 지원서 삭제 — `DELETE /past-applications/{id}` (API_SPEC v0.1 §4).
 *
 * 서버가 저장소(S3)의 원본 파일까지 지운다. 되돌릴 수 없으므로 확인은 화면이 받고, 그 사실도 화면이 말한다.
 */
public class DeletePastApplicationUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(id: Long): Result<Unit> = pastApplicationRepository.delete(id)
    }

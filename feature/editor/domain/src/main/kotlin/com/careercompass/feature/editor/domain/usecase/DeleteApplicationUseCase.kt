package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 이력 삭제 — `DELETE /applications/{id}` (§6).
 *
 * 연결된 지원서까지 함께 지워지므로 되돌릴 수 없다. 확인 절차는 화면이 갖는다(#189).
 */
public class DeleteApplicationUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(applicationId: Long): Result<Unit> = applicationRepository.delete(applicationId)
    }

package com.careercompass.core.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.UploadFile
import javax.inject.Inject

/**
 * Step 4 지원서 업로드 — 업로드·텍스트 추출·AI 분류가 끝난 결과를 돌려받는다(API_SPEC §4).
 *
 * 재시도는 같은 [UploadFile] 로 다시 호출한다 — [UploadFile.openStream] 이 호출마다 새 스트림을 연다.
 */
public class UploadPastApplicationUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(
            file: UploadFile,
            label: String,
        ): Result<PastApplication> {
            require(label.isNotBlank()) { "label must not be blank" }
            return pastApplicationRepository.upload(file = file, label = label.trim())
        }
    }

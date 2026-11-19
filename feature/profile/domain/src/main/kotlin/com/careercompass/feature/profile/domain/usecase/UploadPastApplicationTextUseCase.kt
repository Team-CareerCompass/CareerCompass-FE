package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.PastApplicationRepository
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.pastApplicationTextUpload
import javax.inject.Inject

/**
 * 앱 안에서 직접 쓴 지원서를 등록한다 — `POST /past-applications/upload` (API_SPEC v0.1 §4).
 *
 * 텍스트를 TXT 파일로 만들어 **파일 업로드와 같은 엔드포인트**로 보낸다. 그 판단의 근거는
 * [pastApplicationTextUpload] 의 KDoc 에 있다(#181) — 서버가 업로드 뒤에 하는 일이 두 경로에서 같으므로
 * 입구를 둘로 만들지 않는다.
 *
 * 여러 항목이 한 본문에 섞여 있어도 그대로 보낸다. 항목을 나누는 것은 서버의 일이고(F1-4 「하나의 지원서
 * 파일에 여러 항목이 혼재할 수 있으므로 항목을 분리하여 저장」), 파일 업로드도 같은 길을 지난다.
 */
public class UploadPastApplicationTextUseCase
    @Inject
    constructor(
        private val pastApplicationRepository: PastApplicationRepository,
    ) {
        public suspend operator fun invoke(
            label: String,
            content: String,
        ): Result<PastApplication> =
            pastApplicationTextUpload(label = label, content = content).mapCatching { (file, normalizedLabel) ->
                pastApplicationRepository.upload(file, normalizedLabel).getOrThrow()
            }
    }

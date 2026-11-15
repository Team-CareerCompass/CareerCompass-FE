package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 답을 고쳐 보낸다 — `PATCH /applications/{id}/items/{itemId}` (§6).
 *
 * **임시 저장과 최종 저장이 갈리는 자리가 아니다.** 둘의 차이는 `status` 뿐이라, 30초 임시 저장은 이 호출을
 * 변경된 항목에만 부르고 최종 저장만 `POST …/save` 를 부른다(`docs/spec/canon.md` 「지원서 규칙」). 임시 저장
 * 전용 엔드포인트를 만들지 않은 판정이 그것이다.
 *
 * 기본 호출은 상한을 검사한다. 임시 초안 보관이나 F4-3의 초과 확인 뒤 최종 저장에서는
 * [allowOverLimit]으로 원문을 그대로 보낼 수 있다. 서버 실패는 호출자가 표시한다.
 */
public class UpdateApplicationItemAnswerUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            applicationId: Long,
            item: ApplicationItem,
            answer: String,
            allowOverLimit: Boolean = false,
        ): Result<ApplicationItem> {
            if (!allowOverLimit && answer.length > item.maxChars) {
                return Result.failure(EditorFailure.AnswerTooLong(maxChars = item.maxChars, actualChars = answer.length))
            }
            return applicationRepository.updateItemAnswer(applicationId, item.id, answer)
        }
    }

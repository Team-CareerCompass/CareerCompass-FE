package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 답을 고쳐 보낸다 — `PATCH /applications/{id}/items/{itemId}` (§6).
 *
 * **임시 저장과 최종 저장이 갈리는 자리가 아니다.** 둘의 차이는 `status` 뿐이라, 30초 임시 저장은 이 호출을
 * debounce 로 부르고 최종 저장만 `POST …/save` 를 부른다(`docs/spec/canon.md` 「지원서 규칙」). 임시 저장
 * 전용 엔드포인트를 만들지 않은 판정이 그것이다.
 *
 * 글자 상한은 **보내기 전에** 확인한다. 서버도 거절하지만, 왕복을 기다려 「저장 실패」만 보여 주면 사용자는
 * 무엇이 문제인지 모르고, 그 사이 debounce 가 같은 요청을 또 보낸다.
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
        ): Result<ApplicationItem> {
            if (answer.length > item.maxChars) {
                return Result.failure(EditorFailure.AnswerTooLong(maxChars = item.maxChars, actualChars = answer.length))
            }
            return applicationRepository.updateItemAnswer(applicationId, item.id, answer)
        }
    }

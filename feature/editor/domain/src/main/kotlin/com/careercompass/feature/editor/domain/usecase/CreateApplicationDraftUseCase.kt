package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 공고에서 초안을 연다 — `POST /applications` (API_SPEC v0.1 §6).
 *
 * 「만들기」가 아니라 「연다」인 것은 서버 규칙 때문이다. 같은 공고에 진행 중인 초안이 있으면 서버가 새로
 * 만들지 않고 그 id 를 돌려준다(`docs/spec/canon.md` 「지원서 규칙」) — 미저장 초안이 여러 개 쌓이면 어느
 * 것을 이어 쓰는지 사용자가 알 수 없기 때문이다. 그래서 스트림이 끊긴 뒤 다시 부르는 자리도 여기다.
 *
 * ### 문항을 언제 실어 보내는가
 * [items] 가 [recognizedItems] 와 같으면 **보내지 않는다.** 계약에 없는 필드라(「지원서 문항 확정의 계약」),
 * 보낼 이유가 없을 때 보내면 그 필드를 모르는 서버가 요청을 거절할 여지만 만든다. 사용자가 실제로 고쳤을
 * 때만 — 그때는 보내지 않으면 화면이 받은 입력이 사라진다 — 실어 보낸다.
 *
 * 공고가 문항을 하나도 못 찾은 경우([recognizedItems] 가 빔)에도 사용자가 쓴 것이 있으면 보낸다. 그것이
 * 「인식된 항목이 없으면 직접 입력」(F4-1)이 성립하는 유일한 길이다.
 */
public class CreateApplicationDraftUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            postingId: Long,
            tone: ApplicationTone = ApplicationTone.Formal,
            items: List<ApplicationItemDraft> = emptyList(),
            recognizedItems: List<ApplicationItemDraft> = items,
        ): Result<ApplicationDraft> =
            applicationRepository.createDraft(
                postingId = postingId,
                tone = tone,
                items = items.takeIf { it.isNotEmpty() && it != recognizedItems },
            )
    }

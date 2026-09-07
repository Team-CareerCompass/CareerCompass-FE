package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import javax.inject.Inject

/**
 * 공고에서 초안을 연다 — `POST /applications` (API_SPEC v0.1 §6).
 *
 * 「만들기」가 아니라 「연다」인 것은 서버 규칙 때문이다. 같은 공고에 진행 중인 초안이 있으면 서버가 새로
 * 만들지 않고 그 id 를 돌려준다(`docs/spec/canon.md` 「지원서 규칙」) — 미저장 초안이 여러 개 쌓이면 어느
 * 것을 이어 쓰는지 사용자가 알 수 없기 때문이다. 그래서 스트림이 끊긴 뒤 다시 부르는 자리도 여기다.
 */
public class CreateApplicationDraftUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public suspend operator fun invoke(
            postingId: Long,
            tone: ApplicationTone = ApplicationTone.Formal,
        ): Result<ApplicationDraft> = applicationRepository.createDraft(postingId, tone)
    }

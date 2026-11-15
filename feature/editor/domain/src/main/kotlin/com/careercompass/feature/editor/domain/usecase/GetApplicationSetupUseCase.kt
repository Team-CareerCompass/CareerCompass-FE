package com.careercompass.feature.editor.domain.usecase

import com.careercompass.core.domain.repository.PostingRepository
import com.careercompass.feature.editor.domain.model.ApplicationSetup
import com.careercompass.feature.editor.domain.model.toItemDrafts
import javax.inject.Inject

/**
 * 초안을 만들기 전에 보여 줄 것을 한 번에 읽는다 — `GET /postings/{id}` (API_SPEC v0.1 §5).
 *
 * 문항은 공고 파싱 결과에 들어 있다(`parsed.formQuestions`). 그래서 §6 을 부르기 **전에** 사용자에게
 * 보여 줄 수 있고, F4-1 이 요구하는 「확인받고 시작한다」가 성립한다.
 *
 * 파싱이 아직 안 끝났거나 실패한 공고는 `parsed` 가 null 이다. 그때는 인식된 문항이 하나도 없는 것과 같이
 * 다룬다 — 사용자가 직접 쓰면 초안은 만들어진다. 「파싱을 기다려 주세요」로 막으면 그 공고는 영영 못 쓴다.
 */
public class GetApplicationSetupUseCase
    @Inject
    constructor(
        private val postingRepository: PostingRepository,
    ) {
        public suspend operator fun invoke(postingId: Long): Result<ApplicationSetup> =
            postingRepository.getPostingDetail(postingId).map { detail ->
                ApplicationSetup(
                    postingId = detail.id,
                    postingTitle = detail.title,
                    recognizedItems =
                        detail.parsed
                            ?.formQuestions
                            .orEmpty()
                            .toItemDrafts(),
                )
            }
    }

package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.applying
import com.careercompass.feature.editor.domain.model.settled
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * 스트림을 초안에 접어 넣어 **화면이 그릴 값 그대로** 흘려보낸다 — `GET /applications/{id}/stream` (§6).
 *
 * 화면에 사건을 넘기지 않는 이유는 반영 규칙이 한 벌이어야 하기 때문이다. 진행 화면과 편집 화면이 각자
 * 사건을 접으면 두 화면이 같은 스트림에서 다른 초안을 만든다.
 *
 * 첫 값은 인자로 받은 [draft] 그대로다 — 구독하자마자 화면이 문항 목록을 그릴 수 있어야 하고, 그래야
 * 「아직 아무 사건도 안 왔다」와 「빈 초안」이 구분된다. 스트림이 닫히면 마지막으로 굳힌 값을 한 번 더 낸다
 * ([settled]) — 서버가 마지막 상태를 말해 주지 않고 연결만 닫아도 화면이 오지 않을 항목을 기다리지 않도록.
 */
public class ObserveApplicationDraftUseCase
    @Inject
    constructor(
        private val applicationRepository: ApplicationRepository,
    ) {
        public operator fun invoke(draft: ApplicationDraft): Flow<ApplicationDraft> =
            flow {
                var current = draft
                emit(current)
                applicationRepository.streamEvents(draft.id).collect { event ->
                    current = current.applying(event)
                    emit(current)
                }
                val settled = current.settled()
                if (settled != current) emit(settled)
            }
    }

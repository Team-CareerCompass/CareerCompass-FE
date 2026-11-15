package com.careercompass.feature.editor.presentation.setup

import androidx.lifecycle.SavedStateHandle
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationTone

/**
 * entry 에 귀속된 문항 확인 입력. 확정한 편집 문항과 선택한 문체만 프로세스 경계를 넘긴다.
 *
 * 인식 원본은 매번 서버에서 읽는다. 원본과 같은 목록은 저장하지 않아, 손대지 않은 공고가 예전 인식 결과로
 * 덮이지 않게 한다. 편집 시트의 미확정 입력·요청 진행·오류·단발 신호는 복원하지 않는다.
 *
 * Bundle 에는 문자열 목록과 정수 배열만 넣는다. 인식 문항이 생성 상한 10개를 넘더라도 사용자가 줄이는 도중의
 * 편집을 보존한다. 비정상적으로 큰 원본은 Binder 한도를 지키기 위해 저장하지 않으며 일부만 잘라 남기지 않는다.
 */
internal class ApplicationSetupInputDraft(
    private val handle: SavedStateHandle,
) {
    fun restoredState(): ApplicationSetupUiState =
        ApplicationSetupUiState(
            tone = ApplicationTone.entries.firstOrNull { it.name == handle.get<String>(KEY_TONE) } ?: ApplicationTone.Formal,
        )

    fun restoredItems(): List<ApplicationItemDraft>? {
        val questions = handle.get<ArrayList<String>>(KEY_QUESTIONS) ?: return null
        val limits = handle.get<IntArray>(KEY_LIMITS) ?: return null
        if (questions.size != limits.size) return null
        return questions.mapIndexed { index, question ->
            ApplicationItemDraft(order = index + 1, question = question, maxChars = limits[index].takeIf { it != NO_LIMIT })
        }
    }

    fun save(state: ApplicationSetupUiState) {
        val items = state.items
        if (items != state.recognizedItems && items.size <= MAX_SAVED_ITEMS &&
            items.sumOf { it.question.length.toLong() } <= MAX_SAVED_CHARS
        ) {
            handle[KEY_QUESTIONS] = ArrayList(items.map { it.question })
            handle[KEY_LIMITS] = items.map { it.maxChars ?: NO_LIMIT }.toIntArray()
        } else {
            clearItems()
        }
        if (state.tone == ApplicationTone.Formal) handle.remove<String>(KEY_TONE) else handle[KEY_TONE] = state.tone.name
    }

    fun clear() {
        clearItems()
        handle.remove<String>(KEY_TONE)
    }

    private fun clearItems() {
        handle.remove<ArrayList<String>>(KEY_QUESTIONS)
        handle.remove<IntArray>(KEY_LIMITS)
    }

    private companion object {
        const val KEY_QUESTIONS = "editor.setup.draft.questions"
        const val KEY_LIMITS = "editor.setup.draft.limits"
        const val KEY_TONE = "editor.setup.draft.tone"
        const val NO_LIMIT = -1
        const val MAX_SAVED_ITEMS = 1_000
        const val MAX_SAVED_CHARS = 100_000
    }
}

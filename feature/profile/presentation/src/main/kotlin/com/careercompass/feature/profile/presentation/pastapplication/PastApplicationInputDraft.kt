package com.careercompass.feature.profile.presentation.pastapplication

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.ui.component.DirectInputState

/**
 * 온보딩과 같은 복원 규칙: 시트를 다시 열 때 입력만 되살리고 요청·오류·일회성 신호는 복원하지 않는다.
 * 취소·업로드 성공·세션 종료 시 지운다. 본문은 온보딩과 같은 10만 자까지 Bundle 에 넣는다.
 * 한도를 넘으면 옛 본문을 지워, 오래되거나 잘린 글을 완전한 초안처럼 되살리지 않는다.
 */
internal class PastApplicationInputDraft(
    private val handle: SavedStateHandle,
) {
    fun restore(): DirectInputState =
        DirectInputState(
            label = handle.get<String>(KEY_LABEL).orEmpty(),
            content = handle.get<String>(KEY_CONTENT).orEmpty(),
        )

    fun save(input: DirectInputState) {
        handle[KEY_LABEL] = input.label
        if (input.content.length <= MAX_CONTENT_CHARS) {
            handle[KEY_CONTENT] = input.content
        } else {
            handle.remove<String>(KEY_CONTENT)
        }
    }

    fun clear() {
        handle.remove<String>(KEY_LABEL)
        handle.remove<String>(KEY_CONTENT)
    }

    private companion object {
        const val KEY_LABEL = "past_application_input.label"
        const val KEY_CONTENT = "past_application_input.content"
        const val MAX_CONTENT_CHARS = 100_000
    }
}

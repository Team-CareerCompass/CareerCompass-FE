package com.careercompass.feature.editor.presentation.editor

import androidx.lifecycle.SavedStateHandle

/**
 * Editor host가 소유하는 수동 입력만 보관한다. 서버 답·로딩·오류는 다시 조회한다.
 *
 * #133·#137처럼 Bundle/Parcel 경계를 쓰되 모든 문항을 합쳐 10만 UTF-16자로 제한한다.
 * 문항별 최대 글자 수는 경고 기준이며 이 저장 안전 한도와 다르다. 초과 붙여넣기는 기존 입력을
 * 유지하고 안내한다. 탭 이동은 API 저장이 아니며, 빈 문자열도 의도적인 삭제로 보존한다.
 */
internal class ApplicationEditorInput(
    private val handle: SavedStateHandle,
) {
    val selectedItemId: Long? get() = handle[SELECTED]

    fun answers(): Map<Long, String> {
        val ids = handle.get<LongArray>(IDS) ?: return emptyMap()
        val values = handle.get<ArrayList<String>>(ANSWERS) ?: return emptyMap()
        if (ids.size != values.size || values.sumOf { it.length.toLong() } > MAX_CHARS) return emptyMap()
        return ids.zip(values).toMap()
    }

    fun save(
        answers: Map<Long, String>,
        selectedItemId: Long?,
    ) {
        handle[IDS] = answers.keys.toLongArray()
        handle[ANSWERS] = ArrayList(answers.values)
        handle[SELECTED] = selectedItemId
    }

    fun clear() {
        handle.remove<LongArray>(IDS)
        handle.remove<ArrayList<String>>(ANSWERS)
        handle.remove<Long>(SELECTED)
    }

    companion object {
        const val MAX_CHARS = 100_000
        private const val IDS = "editor.input.ids"
        private const val ANSWERS = "editor.input.answers"
        private const val SELECTED = "editor.input.selected"
    }
}

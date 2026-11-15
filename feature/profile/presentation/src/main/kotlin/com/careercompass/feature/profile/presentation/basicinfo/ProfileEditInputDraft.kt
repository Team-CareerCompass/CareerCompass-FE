package com.careercompass.feature.profile.presentation.basicinfo

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.model.user.InterestTagRules
import com.careercompass.core.model.user.MAX_JOB_INTERESTS

/** null 은 손대지 않은 칸, 빈 문자열/목록은 사용자가 비운 칸이다. 서버 프리필 뒤 입력만 덮는다. */
public data class ProfileEditInputOverrides(
    val name: String? = null,
    val school: String? = null,
    val department: String? = null,
    val gradePointAverage: String? = null,
    val graduationDate: String? = null,
    val selectedJobCodes: List<String>? = null,
    val interestTags: List<String>? = null,
    val interestInput: String? = null,
) {
    internal fun applyTo(state: ProfileEditUiState): ProfileEditUiState =
        state.copy(
            name = name ?: state.name,
            school = school ?: state.school,
            department = department ?: state.department,
            gradePointAverage = gradePointAverage ?: state.gradePointAverage,
            graduationDate = graduationDate ?: state.graduationDate,
            selectedJobCodes = selectedJobCodes ?: state.selectedJobCodes,
            interestTags = interestTags ?: state.interestTags,
            interestInput = interestInput ?: state.interestInput,
        )
}

/**
 * 프로필 편집 entry 의 미저장 입력. 서버 원본과 다른 칸만 문자열/문자열 목록으로 남긴다.
 *
 * 새 ViewModel 이 첫 프로필을 읽으면 최신 서버 값을 프리필하고 저장한 입력만 덮는다. 따라서 빈 값으로 고친
 * 칸도 보존하면서 손대지 않은 칸은 서버 갱신을 따라간다. 원본·오류·저장 진행·피커·단발 신호는 저장하지 않는다.
 * 기존 경험 카드 수정은 전체 레코드를 보내므로 초안 복원 대상이 아니다. 신규 경험 시트도 이 폼의 범위 밖이다.
 *
 * 저장 완료/세션 만료 뒤에는 초안을 지우고 이 entry 에서 다시 쓰지 않는다. 소비된 성공 신호가 입력 초안을
 * 되살리는 것을 막는다. 긴 붙여넣기는 Binder 한도를 위해 칸당 1,000자까지만 저장하며 초과분을 자르지 않는다.
 */
internal class ProfileEditInputDraft(
    private val handle: SavedStateHandle,
) {
    private var finished = false

    fun restoredInputs(): ProfileEditInputOverrides =
        ProfileEditInputOverrides(
            name = handle[NAME],
            school = handle[SCHOOL],
            department = handle[DEPARTMENT],
            gradePointAverage = handle[GPA],
            graduationDate = handle[GRADUATION],
            selectedJobCodes =
                handle
                    .get<ArrayList<String>>(JOBS)
                    ?.filter(String::isNotBlank)
                    ?.distinct()
                    ?.take(MAX_JOB_INTERESTS),
            interestTags = handle.get<ArrayList<String>>(TAGS)?.let(InterestTagRules::sanitize),
            interestInput = handle[INTEREST_INPUT],
        )

    fun save(state: ProfileEditUiState) {
        if (state.isSaved || state.sessionEnd != null) {
            finish()
        }
        if (finished) return
        // 서버를 못 읽은 실패 화면에서는 기존 초안을 그대로 기다리게 한다.
        val original = state.original ?: return
        putChanged(NAME, state.name, original.name.orEmpty())
        putChanged(SCHOOL, state.school, original.school.orEmpty())
        putChanged(DEPARTMENT, state.department, original.department.orEmpty())
        putChanged(GPA, state.gradePointAverage, original.gpa?.toString().orEmpty())
        putChanged(GRADUATION, state.graduationDate, original.gradYear?.toString().orEmpty())
        putChanged(INTEREST_INPUT, state.interestInput, "")
        putChanged(JOBS, state.selectedJobCodes, original.jobInterests.sortedBy { it.priority }.map { it.code })
        putChanged(TAGS, state.interestTags, InterestTagRules.sanitize(original.tags))
    }

    fun finish() {
        finished = true
        KEYS.forEach { handle.remove<Any>(it) }
    }

    private fun putChanged(
        key: String,
        value: String,
        original: String,
    ) {
        if (value != original && value.length <= MAX_FIELD_CHARS) handle[key] = value else handle.remove<String>(key)
    }

    private fun putChanged(
        key: String,
        value: List<String>,
        original: List<String>,
    ) {
        if (value != original) handle[key] = ArrayList(value) else handle.remove<ArrayList<String>>(key)
    }

    private companion object {
        const val NAME = "profile.edit.draft.name"
        const val SCHOOL = "profile.edit.draft.school"
        const val DEPARTMENT = "profile.edit.draft.department"
        const val GPA = "profile.edit.draft.gpa"
        const val GRADUATION = "profile.edit.draft.graduation"
        const val JOBS = "profile.edit.draft.jobs"
        const val TAGS = "profile.edit.draft.tags"
        const val INTEREST_INPUT = "profile.edit.draft.interestInput"
        const val MAX_FIELD_CHARS = 1_000
        val KEYS = listOf(NAME, SCHOOL, DEPARTMENT, GPA, GRADUATION, JOBS, TAGS, INTEREST_INPUT)
    }
}

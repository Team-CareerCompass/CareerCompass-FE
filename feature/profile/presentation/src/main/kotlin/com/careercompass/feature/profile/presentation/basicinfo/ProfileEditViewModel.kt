package com.careercompass.feature.profile.presentation.basicinfo

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.user.GraduationDateRules
import com.careercompass.core.model.user.InterestTagRules
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.core.model.user.ProfileBasicInfoRules
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.model.user.SchoolCatalog
import com.careercompass.core.model.user.SchoolNameRules
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.model.user.UserProfileUpdate
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.GraduationPickerState
import com.careercompass.core.ui.component.SchoolDirectInputState
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.core.ui.component.SchoolPickerState
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.profile.domain.usecase.ObserveProfileUseCase
import com.careercompass.feature.profile.domain.usecase.RefreshProfileUseCase
import com.careercompass.feature.profile.domain.usecase.SaveJobInterestsUseCase
import com.careercompass.feature.profile.domain.usecase.SaveProfileTagsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdateProfileBasicInfoUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import com.careercompass.feature.profile.presentation.reporting.ProfileFailureStage
import com.careercompass.feature.profile.presentation.reporting.recordProfileFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Year
import javax.inject.Inject

/**
 * 프로필 편집이 그리는 값.
 *
 * 다섯 칸은 전부 **문자열**이다. 학점과 졸업 연월은 입력 도중에 숫자가 되지 못하는 순간을 지나므로
 * (`3.` · `2027.`) 파싱한 값을 상태에 두면 그 순간을 표현할 수 없다. 숫자로 옮기는 것은 저장 직전 한 번이다.
 *
 * @property original 프리필의 바탕이 된 서버 값. 「무엇이 바뀌었는가」의 기준이라, 저장은 이것과 다른 필드만 보낸다.
 * @property serverRejectedField 서버가 짚어 준 칸(400 `INVALID_INPUT` 의 `error.field`). 사용자가 그 칸을
 *   고치면 사라진다 — 우리 검증은 통과한 값이라 로컬 규칙으로는 다시 만들 수 없다.
 */
public data class ProfileEditUiState(
    val original: UserProfile? = null,
    val name: String = "",
    val school: String = "",
    val department: String = "",
    val gradePointAverage: String = "",
    val graduationDate: String = "",
    val selectedJobCodes: List<String> = emptyList(),
    val interestTags: List<String> = emptyList(),
    val interestInput: String = "",
    val nameError: ProfileFieldViolation? = null,
    val schoolError: ProfileFieldViolation? = null,
    val departmentError: ProfileFieldViolation? = null,
    val gradePointAverageError: ProfileFieldViolation? = null,
    val graduationDateError: ProfileFieldViolation? = null,
    val serverRejectedField: ProfileEditField? = null,
    val schoolPicker: SchoolPickerState? = null,
    val graduationPicker: GraduationPickerState? = null,
    val isSaving: Boolean = false,
    val loadFailure: FailureKind? = null,
    val message: ProfileEditMessage? = null,
    val isSaved: Boolean = false,
    val sessionEnd: ProfileSessionEnd? = null,
) : UiState {
    /** 프로필을 아직 못 받아 칸을 채울 수 없다. */
    val isLoading: Boolean get() = original == null && loadFailure == null

    /** 조회가 실패했고 프리필할 값도 없다 — 편집할 대상 자체가 없으므로 화면을 덮는다. */
    val isFailureVisible: Boolean get() = original == null && loadFailure != null

    /** 지금 화면 값과 서버 값의 차이. 빈 수정이면 저장이 요청 없이 끝난다. */
    val update: UserProfileUpdate
        get() =
            UserProfileUpdate(
                name = name.trim().takeIf { it.isNotEmpty() && it != original?.name },
                school = SchoolNameRules.normalize(school).takeIf { it.isNotEmpty() && it != original?.school },
                department = department.trim().takeIf { it.isNotEmpty() && it != original?.department },
                gpa = ProfileBasicInfoRules.parseGradePointAverage(gradePointAverage)?.takeIf { it != original?.gpa },
                gradYear = ProfileBasicInfoRules.parseGraduationYear(graduationDate)?.takeIf { it != original?.gradYear },
            )

    /** 희망 직무를 더 고를 수 있는가 — 상한(3)에 닿으면 화면이 이유를 말한다. 조용히 무시하지 않는다. */
    val isJobLimitReached: Boolean get() = selectedJobCodes.size >= MAX_JOB_INTERESTS

    /** 관심 태그를 더 담을 수 있는가 — 상한(5). */
    val isTagLimitReached: Boolean get() = interestTags.size >= MAX_PROFILE_TAGS

    /** 최소 1개씩은 있어야 한다(API_SPEC §2). 서버가 빈 목록을 준 계정도 저장 전에 채우게 된다. */
    val isInterestsFilled: Boolean get() = selectedJobCodes.isNotEmpty() && interestTags.isNotEmpty()

    /** 지금 담은 태그를 저장할 수 있는가 — 다듬은 결과가 비지 않고, 중복이 아니며, 상한 안이다. */
    val isTagAddEnabled: Boolean
        get() =
            InterestTagRules.normalize(interestInput).let { tag ->
                tag.isNotEmpty() && tag !in interestTags && !isTagLimitReached
            }

    /** 전체 교체 API 라 「바뀐 것만」이 없다 — 목록이 원본과 다르면 통째로 보낸다. */
    val hasJobInterestChanges: Boolean get() = selectedJobCodes != original?.jobInterests?.map { it.code }.orEmpty()

    val hasTagChanges: Boolean get() = interestTags != original?.tags.orEmpty()

    val hasFieldError: Boolean
        get() =
            listOfNotNull(nameError, schoolError, departmentError, gradePointAverageError, graduationDateError).isNotEmpty()

    /**
     * 저장을 지금 누를 수 있는가.
     *
     * 바뀐 것이 없어도 열어 둔다 — 「저장」이 회색이면 사용자는 자기 입력이 반영되지 않았다고 읽는다.
     * 빈 수정은 리포지토리가 요청 없이 끝내므로 눌러도 헛되지 않다.
     */
    val isSaveEnabled: Boolean get() = original != null && !isSaving && !hasFieldError && isInterestsFilled
}

public sealed interface ProfileEditIntent : MviIntent {
    public data class Screen(
        val event: ProfileEditEvent,
    ) : ProfileEditIntent

    public data class SchoolPicker(
        val event: SchoolPickerEvent,
    ) : ProfileEditIntent

    public data class GraduationPicker(
        val event: GraduationDatePickerEvent,
    ) : ProfileEditIntent

    public data object ConsumeMessage : ProfileEditIntent

    public data object ConsumeSaved : ProfileEditIntent

    public data object ConsumeSessionEnded : ProfileEditIntent
}

public sealed interface ProfileEditReducerEvent : ReducerEvent {
    /** 서버 값이 도착했다 — 사용자가 이미 고친 칸은 덮지 않는다. */
    public data class Prefilled(
        val profile: UserProfile,
    ) : ProfileEditReducerEvent

    public data class LoadFailed(
        val kind: FailureKind,
    ) : ProfileEditReducerEvent

    /**
     * 희망 직무·관심 태그를 서버 값으로 되돌렸다 — 전체 교체 저장이 실패했을 때다(#177).
     *
     * 기본 정보와 달리 사용자가 친 글자를 버린다. 전체 교체는 부분 성공이 없어 화면 값이 서버에 없다는 뜻이고,
     * 그대로 두면 사용자는 저장된 줄 안다.
     */
    public data class InterestsReverted(
        val profile: UserProfile,
    ) : ProfileEditReducerEvent

    public data class FormChanged(
        val form: ProfileEditUiState,
    ) : ProfileEditReducerEvent

    public data class SchoolPickerChanged(
        val picker: SchoolPickerState?,
    ) : ProfileEditReducerEvent

    public data class GraduationPickerChanged(
        val picker: GraduationPickerState?,
    ) : ProfileEditReducerEvent

    public data object SaveStarted : ProfileEditReducerEvent

    public data class Saved(
        val profile: UserProfile,
    ) : ProfileEditReducerEvent

    public data class SaveRejected(
        val field: ProfileEditField?,
    ) : ProfileEditReducerEvent

    public data object SaveFailed : ProfileEditReducerEvent

    public data class SessionEnded(
        val cause: ProfileSessionEnd,
    ) : ProfileEditReducerEvent

    public data object MessageConsumed : ProfileEditReducerEvent

    public data object SavedConsumed : ProfileEditReducerEvent

    public data object SessionEndedConsumed : ProfileEditReducerEvent
}

/**
 * 프로필 편집(#176) — 온보딩 Step 1 이 받은 다섯 칸을 나중에 고친다.
 *
 * ### 규칙은 온보딩과 한 벌이다
 * 검증은 [ProfileBasicInfoRules] 가, 학교 표기는 [SchoolNameRules] 가, 졸업 연월 표기는
 * [GraduationDateRules] 가 갖는다. 전부 `core:model` 이고 온보딩도 같은 것을 부른다 — 규칙이 두 벌이면
 * 온보딩을 통과한 값이 이 화면에서 거부되거나 그 반대가 된다(#176 의 완료 조건).
 *
 * ### 프리필은 사용자가 고친 칸을 덮지 않는다
 * 캐시가 먼저 칸을 채우고 서버 응답이 뒤따라온다. 그 사이 사용자가 이름을 고쳤는데 응답이 도착해 덮으면
 * 방금 친 글자가 사라진다. 그래서 [ProfileEditReducerEvent.Prefilled] 는 **원본과 같은 값이 들어 있는
 * 칸만** 갈아 끼운다.
 *
 * ### 서버가 거부한 칸
 * 400 `INVALID_INPUT` 은 어느 필드가 문제인지 함께 준다([CoreDataFailure.InvalidInput.field]). 우리 검증은
 * 통과한 값이므로 로컬 규칙으로는 그 오류를 다시 만들 수 없다 — 그래서 별도 필드로 들고 있다가 사용자가
 * 그 칸을 고치면 지운다. 필드를 알 수 없으면 칸을 짚지 않고 스낵바로만 알린다.
 */
@HiltViewModel
public class ProfileEditViewModel
    @Inject
    constructor(
        observeProfile: ObserveProfileUseCase,
        private val refreshProfile: RefreshProfileUseCase,
        private val updateProfileBasicInfo: UpdateProfileBasicInfoUseCase,
        private val saveJobInterests: SaveJobInterestsUseCase,
        private val saveProfileTags: SaveProfileTagsUseCase,
        private val errorReporter: ErrorReporter,
        private val clock: Clock,
    ) : MviViewModel<ProfileEditIntent, ProfileEditUiState, ProfileEditReducerEvent>(ProfileEditUiState()) {
        private var saveJob: Job? = null

        init {
            viewModelScope.launch {
                observeProfile().collect { profile ->
                    profile?.let { dispatch(ProfileEditReducerEvent.Prefilled(it)) }
                }
            }
            viewModelScope.launch {
                refreshProfile()
                    .onSuccess { dispatch(ProfileEditReducerEvent.Prefilled(it)) }
                    .onFailure(::onLoadFailure)
            }
        }

        override fun onIntent(intent: ProfileEditIntent) {
            when (intent) {
                is ProfileEditIntent.Screen -> onEvent(intent.event)
                is ProfileEditIntent.SchoolPicker -> onSchoolPickerEvent(intent.event)
                is ProfileEditIntent.GraduationPicker -> onGraduationPickerEvent(intent.event)
                ProfileEditIntent.ConsumeMessage -> dispatch(ProfileEditReducerEvent.MessageConsumed)
                ProfileEditIntent.ConsumeSaved -> dispatch(ProfileEditReducerEvent.SavedConsumed)
                ProfileEditIntent.ConsumeSessionEnded -> dispatch(ProfileEditReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: ProfileEditUiState,
            event: ProfileEditReducerEvent,
        ): ProfileEditUiState =
            when (event) {
                is ProfileEditReducerEvent.Prefilled -> {
                    state.prefilledWith(event.profile)
                }

                is ProfileEditReducerEvent.LoadFailed -> {
                    state.copy(loadFailure = event.kind)
                }

                is ProfileEditReducerEvent.InterestsReverted -> {
                    state.copy(
                        original = event.profile,
                        isSaving = false,
                        selectedJobCodes =
                            event.profile.jobInterests
                                .sortedBy { it.priority }
                                .map { it.code },
                        interestTags = event.profile.tags,
                        interestInput = "",
                        message = ProfileEditMessage.InterestsReverted,
                    )
                }

                is ProfileEditReducerEvent.FormChanged -> {
                    event.form
                }

                is ProfileEditReducerEvent.SchoolPickerChanged -> {
                    state.copy(schoolPicker = event.picker)
                }

                is ProfileEditReducerEvent.GraduationPickerChanged -> {
                    state.copy(graduationPicker = event.picker)
                }

                ProfileEditReducerEvent.SaveStarted -> {
                    state.copy(isSaving = true, serverRejectedField = null)
                }

                is ProfileEditReducerEvent.Saved -> {
                    state.copy(isSaving = false, original = event.profile, isSaved = true)
                }

                is ProfileEditReducerEvent.SaveRejected -> {
                    state.copy(
                        isSaving = false,
                        serverRejectedField = event.field,
                        message = if (event.field == null) ProfileEditMessage.SaveRejected else null,
                    )
                }

                ProfileEditReducerEvent.SaveFailed -> {
                    state.copy(isSaving = false, message = ProfileEditMessage.SaveFailed)
                }

                is ProfileEditReducerEvent.SessionEnded -> {
                    state.copy(isSaving = false, sessionEnd = event.cause)
                }

                ProfileEditReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                ProfileEditReducerEvent.SavedConsumed -> {
                    state.copy(isSaved = false)
                }

                ProfileEditReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnd = null)
                }
            }

        private fun onEvent(event: ProfileEditEvent) {
            when (event) {
                is ProfileEditEvent.NameChanged -> {
                    updateForm(ProfileEditField.Name) {
                        copy(name = event.value, nameError = ProfileBasicInfoRules.validateName(event.value, requireValue = false))
                    }
                }

                is ProfileEditEvent.DepartmentChanged -> {
                    updateForm(ProfileEditField.Department) {
                        copy(
                            department = event.value,
                            departmentError = ProfileBasicInfoRules.validateDepartment(event.value, requireValue = false),
                        )
                    }
                }

                is ProfileEditEvent.GradePointAverageChanged -> {
                    updateForm(ProfileEditField.GradePointAverage) {
                        copy(
                            gradePointAverage = event.value,
                            gradePointAverageError = ProfileBasicInfoRules.validateGradePointAverage(event.value),
                        )
                    }
                }

                is ProfileEditEvent.JobToggled -> {
                    toggleJob(event.code)
                }

                is ProfileEditEvent.InterestInputChanged -> {
                    dispatch(ProfileEditReducerEvent.FormChanged(currentState.copy(interestInput = event.value)))
                }

                ProfileEditEvent.InterestTagAdded -> {
                    addInterestTag()
                }

                is ProfileEditEvent.InterestTagRemoved -> {
                    dispatch(
                        ProfileEditReducerEvent.FormChanged(currentState.copy(interestTags = currentState.interestTags - event.tag)),
                    )
                }

                ProfileEditEvent.SchoolPickerClicked -> {
                    openSchoolPicker()
                }

                ProfileEditEvent.GraduationPickerClicked -> {
                    openGraduationPicker()
                }

                ProfileEditEvent.SaveClicked -> {
                    save()
                }

                ProfileEditEvent.RetryClicked -> {
                    retryLoad()
                }

                // 뒤로 가기는 목적지 결정이라 Screen 의 콜백이 갖는다 — 상태가 겪는 것이 없다.
                ProfileEditEvent.BackClicked -> {
                    Unit
                }
            }
        }

        /** 프리필 — 사용자가 이미 고친 칸은 그대로 둔다. 판정 기준은 「원본과 같은 값인가」다. */
        private fun ProfileEditUiState.prefilledWith(profile: UserProfile): ProfileEditUiState =
            copy(
                original = profile,
                loadFailure = null,
                name = if (name == original?.name.orEmpty()) profile.name.orEmpty() else name,
                school = if (school == original?.school.orEmpty()) profile.school.orEmpty() else school,
                department = if (department == original?.department.orEmpty()) profile.department.orEmpty() else department,
                gradePointAverage =
                    if (gradePointAverage == original?.gpa.gpaText()) profile.gpa.gpaText() else gradePointAverage,
                graduationDate =
                    if (graduationDate == original?.gradYear.yearText()) profile.gradYear.yearText() else graduationDate,
                selectedJobCodes =
                    if (selectedJobCodes == original?.jobInterests?.map { it.code }.orEmpty()) {
                        profile.jobInterests.sortedBy { it.priority }.map { it.code }
                    } else {
                        selectedJobCodes
                    },
                interestTags =
                    if (interestTags == original?.tags.orEmpty()) InterestTagRules.sanitize(profile.tags) else interestTags,
            )

        /** 사용자가 그 칸을 고쳤으면 서버가 짚어 준 표시를 지운다 — 이미 다른 값이라 그 판정은 낡았다. */
        private inline fun updateForm(
            field: ProfileEditField,
            transform: ProfileEditUiState.() -> ProfileEditUiState,
        ) {
            val next = currentState.transform()
            dispatch(
                ProfileEditReducerEvent.FormChanged(
                    if (next.serverRejectedField == field) next.copy(serverRejectedField = null) else next,
                ),
            )
        }

        /**
         * 희망 직무 고르기 — 이미 고른 것은 해제하고, 상한에 닿았으면 **이유를 말한다.**
         *
         * 고른 순서가 우선순위가 되므로 목록 끝에 붙인다([SaveJobInterestsUseCase]).
         */
        private fun toggleJob(code: String) {
            val selected = currentState.selectedJobCodes
            when {
                code in selected -> {
                    dispatch(ProfileEditReducerEvent.FormChanged(currentState.copy(selectedJobCodes = selected - code)))
                }

                currentState.isJobLimitReached -> {
                    dispatch(ProfileEditReducerEvent.FormChanged(currentState.copy(message = ProfileEditMessage.JobLimitReached)))
                }

                else -> {
                    dispatch(ProfileEditReducerEvent.FormChanged(currentState.copy(selectedJobCodes = selected + code)))
                }
            }
        }

        /** 태그 담기 — 다듬은 값이 비면 아무 일도 없고, 중복이면 입력만 비우며, 상한에 닿았으면 이유를 말한다. */
        private fun addInterestTag() {
            val state = currentState
            val tag = InterestTagRules.normalize(state.interestInput)
            if (tag.isEmpty()) return
            // 이미 담은 태그면 입력만 비운다 — 사용자의 뜻은 이미 이뤄져 있고, 글자를 남겨 두면 안 담긴 줄 안다.
            if (tag in state.interestTags) {
                dispatch(ProfileEditReducerEvent.FormChanged(state.copy(interestInput = "")))
                return
            }
            if (state.isTagLimitReached) {
                dispatch(ProfileEditReducerEvent.FormChanged(state.copy(message = ProfileEditMessage.TagLimitReached)))
                return
            }
            dispatch(
                ProfileEditReducerEvent.FormChanged(state.copy(interestInput = "", interestTags = state.interestTags + tag)),
            )
        }

        private fun retryLoad() {
            viewModelScope.launch {
                refreshProfile()
                    .onSuccess { dispatch(ProfileEditReducerEvent.Prefilled(it)) }
                    .onFailure(::onLoadFailure)
            }
        }

        private fun onLoadFailure(cause: Throwable) {
            if (cause is CoreDataFailure.Unauthorized) {
                errorReporter.recordProfileFailure(ProfileFailureStage.BasicInfoLoad, cause)
                dispatch(ProfileEditReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                return
            }
            errorReporter.recordProfileFailure(ProfileFailureStage.BasicInfoLoad, cause)
            dispatch(ProfileEditReducerEvent.LoadFailed(cause.toFailureKind()))
        }

        /**
         * 저장 — 필수 칸을 한 번 더 보고, 통과하면 바뀐 필드만 보낸다.
         *
         * 진행 중이면 무시한다. 저장이 끝나기 전에 화면을 떠나도 이 job 은 `viewModelScope` 에 있어
         * entry 가 살아 있는 동안 이어진다(#146 과 같은 판정) — 요청이 조용히 취소되는 길을 만들지 않는다.
         */
        private fun save() {
            if (saveJob?.isActive == true) return
            val validated = currentState.validatedForSubmit()
            // 전체 교체 API 는 빈 목록을 받으면 계약을 깬다(최소 1개씩) — 화면이 이미 저장을 잠그지만,
            // 진입점이 하나뿐이라는 것에 기대지 않고 여기서도 막는다.
            if (!validated.isInterestsFilled) return
            if (validated.hasFieldError) {
                dispatch(ProfileEditReducerEvent.FormChanged(validated))
                return
            }
            dispatch(ProfileEditReducerEvent.SaveStarted)
            saveJob = viewModelScope.launch { runSave(validated) }
        }

        /**
         * 기본 정보 → 희망 직무 → 관심 태그 순으로 보낸다. 셋 다 바뀐 것이 있을 때만 왕복한다.
         *
         * 전체 교체 두 개(`PUT`)가 실패하면 **화면 값을 서버 값으로 되돌린다** — 부분 성공이 없으므로 화면 값이
         * 서버에 없다는 뜻이고, 그대로 두면 사용자는 저장된 줄 안다(#177). 기본 정보(`PATCH`)의 실패는 반대로
         * 입력을 그대로 둔다(#176) — 부분 수정이라 다시 누르면 되고, 친 글자를 버릴 이유가 없다.
         */
        private suspend fun runSave(form: ProfileEditUiState) {
            val profile =
                if (form.update.isEmpty) {
                    form.original
                } else {
                    updateProfileBasicInfo(form.update).getOrElse { cause ->
                        onSaveFailure(cause)
                        return
                    }
                }
            if (form.hasJobInterestChanges) {
                saveJobInterests(form.selectedJobCodes).onFailure { cause ->
                    onInterestsSaveFailure(cause)
                    return
                }
            }
            if (form.hasTagChanges) {
                saveProfileTags(form.interestTags).onFailure { cause ->
                    onInterestsSaveFailure(cause)
                    return
                }
            }
            // 두 PUT 은 프로필을 돌려주지 않는다 — 서버가 무엇을 갖게 됐는지는 다시 읽어야 안다.
            val saved = if (form.hasJobInterestChanges || form.hasTagChanges) refreshProfile().getOrNull() else null
            dispatch(ProfileEditReducerEvent.Saved(saved ?: profile ?: return))
        }

        /** 전체 교체가 실패했다 — 서버 값을 다시 읽어 화면을 되돌린다. 못 읽으면 일반 실패로 접는다. */
        private suspend fun onInterestsSaveFailure(cause: Throwable) {
            errorReporter.recordProfileFailure(ProfileFailureStage.InterestsSave, cause)
            if (cause is CoreDataFailure.Unauthorized) {
                dispatch(ProfileEditReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                return
            }
            val profile = refreshProfile().getOrNull()
            if (profile == null) {
                dispatch(ProfileEditReducerEvent.SaveFailed)
            } else {
                dispatch(ProfileEditReducerEvent.InterestsReverted(profile))
            }
        }

        /** 제출 시점에만 필수 여부를 따진다 — 입력 도중에 「필수 입력이에요」 를 띄우면 첫 글자부터 빨간 칸이 된다. */
        private fun ProfileEditUiState.validatedForSubmit(): ProfileEditUiState =
            copy(
                nameError = ProfileBasicInfoRules.validateName(name, requireValue = true),
                schoolError = ProfileBasicInfoRules.validateSchool(school, requireValue = true),
                departmentError = ProfileBasicInfoRules.validateDepartment(department, requireValue = true),
                gradePointAverageError = ProfileBasicInfoRules.validateGradePointAverage(gradePointAverage),
                graduationDateError = ProfileBasicInfoRules.validateGraduationDate(graduationDate),
            )

        private fun onSaveFailure(cause: Throwable) {
            errorReporter.recordProfileFailure(ProfileFailureStage.BasicInfoSave, cause)
            when (cause) {
                is CoreDataFailure.Unauthorized -> {
                    dispatch(ProfileEditReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                }

                is CoreDataFailure.InvalidInput -> {
                    dispatch(ProfileEditReducerEvent.SaveRejected(ProfileEditField.fromWireName(cause.field)))
                }

                else -> {
                    dispatch(ProfileEditReducerEvent.SaveFailed)
                }
            }
        }

        private fun openSchoolPicker() {
            if (currentState.isSaving) return
            dispatch(
                ProfileEditReducerEvent.SchoolPickerChanged(
                    SchoolPickerState(query = "", results = SchoolCatalog.search("")),
                ),
            )
        }

        private fun onSchoolPickerEvent(event: SchoolPickerEvent) {
            val picker = currentState.schoolPicker ?: return
            when (event) {
                is SchoolPickerEvent.QueryChanged -> {
                    dispatch(
                        ProfileEditReducerEvent.SchoolPickerChanged(
                            SchoolPickerState(query = event.value, results = SchoolCatalog.search(event.value)),
                        ),
                    )
                }

                is SchoolPickerEvent.SchoolSelected -> {
                    chooseSchool(event.school)
                }

                SchoolPickerEvent.DirectInputRequested -> {
                    dispatch(
                        ProfileEditReducerEvent.SchoolPickerChanged(
                            picker.copy(directInput = SchoolDirectInputState(value = SchoolNameRules.normalize(picker.query))),
                        ),
                    )
                }

                is SchoolPickerEvent.DirectInputChanged -> {
                    dispatch(
                        ProfileEditReducerEvent.SchoolPickerChanged(
                            picker.copy(
                                directInput =
                                    SchoolDirectInputState(
                                        value = event.value,
                                        error = ProfileBasicInfoRules.validateSchool(event.value, requireValue = false),
                                    ),
                            ),
                        ),
                    )
                }

                SchoolPickerEvent.DirectInputConfirmed -> {
                    val input = picker.directInput ?: return
                    val error = ProfileBasicInfoRules.validateSchool(input.value, requireValue = true)
                    if (error != null) {
                        dispatch(
                            ProfileEditReducerEvent.SchoolPickerChanged(picker.copy(directInput = input.copy(error = error))),
                        )
                    } else {
                        chooseSchool(input.value)
                    }
                }

                SchoolPickerEvent.DirectInputCancelled -> {
                    dispatch(ProfileEditReducerEvent.SchoolPickerChanged(picker.copy(directInput = null)))
                }

                SchoolPickerEvent.Dismissed -> {
                    dispatch(ProfileEditReducerEvent.SchoolPickerChanged(null))
                }
            }
        }

        /** 목록 값·직접 입력값 모두 같은 규칙으로 다듬어 담는다. */
        private fun chooseSchool(school: String) {
            updateForm(ProfileEditField.School) {
                copy(school = SchoolNameRules.normalize(school), schoolError = null, schoolPicker = null)
            }
        }

        private fun openGraduationPicker() {
            if (currentState.isSaving) return
            val currentYear = Year.now(clock).value
            val years = GraduationDateRules.yearsFrom(currentYear)
            val typedYear = ProfileBasicInfoRules.parseGraduationYear(currentState.graduationDate)
            dispatch(
                ProfileEditReducerEvent.GraduationPickerChanged(
                    GraduationPickerState(
                        years = years,
                        selectedYear = typedYear?.takeIf { it in years } ?: currentYear,
                        selectedMonth = GraduationDateRules.parseMonth(currentState.graduationDate) ?: GraduationDateRules.DEFAULT_MONTH,
                    ),
                ),
            )
        }

        private fun onGraduationPickerEvent(event: GraduationDatePickerEvent) {
            val picker = currentState.graduationPicker ?: return
            when (event) {
                is GraduationDatePickerEvent.YearSelected -> {
                    dispatch(ProfileEditReducerEvent.GraduationPickerChanged(picker.copy(selectedYear = event.year)))
                }

                is GraduationDatePickerEvent.MonthSelected -> {
                    dispatch(ProfileEditReducerEvent.GraduationPickerChanged(picker.copy(selectedMonth = event.month)))
                }

                GraduationDatePickerEvent.Confirmed -> {
                    val value = GraduationDateRules.format(picker.selectedYear, picker.selectedMonth)
                    updateForm(ProfileEditField.GraduationYear) {
                        copy(
                            graduationDate = value,
                            graduationDateError = ProfileBasicInfoRules.validateGraduationDate(value),
                            graduationPicker = null,
                        )
                    }
                }

                GraduationDatePickerEvent.Dismissed -> {
                    dispatch(ProfileEditReducerEvent.GraduationPickerChanged(null))
                }
            }
        }
    }

/** 학점 칸의 표기 — 서버 값이 없으면 빈 칸이다. */
private fun Double?.gpaText(): String = this?.toString().orEmpty()

/** 졸업 칸의 표기 — 서버는 연도만 주므로 프리필은 `YYYY` 다. 달은 사용자가 피커에서 고를 때 붙는다. */
private fun Int?.yearText(): String = this?.toString().orEmpty()

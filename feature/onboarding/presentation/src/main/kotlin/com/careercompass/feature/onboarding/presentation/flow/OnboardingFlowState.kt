package com.careercompass.feature.onboarding.presentation.flow

import androidx.compose.runtime.Immutable
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.user.JobOptionCatalog
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.core.ui.component.ExperienceDeleteState
import com.careercompass.core.ui.component.ExperienceEditorState
import com.careercompass.core.ui.component.GraduationPickerState
import com.careercompass.core.ui.component.PastApplicationItemCategoryState
import com.careercompass.core.ui.component.SchoolPickerState
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputState
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelState
import com.careercompass.feature.onboarding.presentation.shared.model.OnboardingFieldError

/**
 * 온보딩 흐름의 실패 사유. 문구는 Screen 이 만든다 — §9 의 코드에서 온 사유는 실패 표(`FailureDisplay`, #204)를
 * 읽고, 화면 고유 사유(파일 형식·크기)만 온보딩 문자열을 쓴다(#236).
 *
 * **세션 만료(401)는 여기 없다** — 화면에 그릴 사유가 아니라 화면을 떠날 신호이기 때문이다(#211).
 * 401 은 [OnboardingFlowState.sessionEnded] 로 올라가고 앱 셸이 로그인 화면으로 보낸다. 사유 목록에 없는 것이
 * 곧 답이다: 이 열거형이 만료를 담을 수 없으니 온보딩 화면에는 만료를 그리는 자리도 없다.
 */
public sealed interface OnboardingFailureReason {
    /** 연결 없음 — 실패 표의 `NoConnection` 행(#204). */
    public data object Network : OnboardingFailureReason

    /** 우리가 먼저 끊은 요청 — 표의 `Timeout` 행. 연결 없음과 처방이 다르다(기다렸다 다시). */
    public data object Timeout : OnboardingFailureReason

    /**
     * 상한 — 표의 `LimitExceeded` 행. 무엇이 몇 개까지인지는 [surface] 가 말한다(경험 카드 30 · 지원서 10).
     * 표에 문맥이 없는 상한(관심 태그)은 [FailureSurface.Unspecified] 로 개수를 말하지 않는다 — 틀린 숫자를
     * 말하느니 안 말한다.
     */
    public data class LimitExceeded(
        val surface: FailureSurface,
    ) : OnboardingFailureReason

    /** 400 — 표의 `InvalidInput` 행. */
    public data object InvalidInput : OnboardingFailureReason

    /** 503 — 표의 `ServiceUnavailable` 행. 서버가 쉬는 것을 「서버에 문제가 있다」로 말하지 않는다(#236). */
    public data object Maintenance : OnboardingFailureReason

    /** 500 — 표의 `Unexpected` 행. */
    public data object Server : OnboardingFailureReason

    /** 화면 고유 — §9 의 코드가 아니라 온보딩 문자열로 남는다(파일 형식 검증). */
    public data object UnsupportedFile : OnboardingFailureReason

    /** 화면 고유 — 파일 크기 검증. */
    public data object FileTooLarge : OnboardingFailureReason

    /** 사유를 확인하지 못한 실패 — 표의 `Unexpected` 행. */
    public data object Unknown : OnboardingFailureReason
}

/** [OnboardingViewModel] 이 요청하는 화면 이동. 뒤로 가기는 상태 변화가 없어 Screen 이 직접 처리한다. */
public sealed interface OnboardingDestination {
    /** [step] 화면으로 전진한다(저장 성공 뒤, 또는 재개 지점으로의 이동). */
    public data class Step(
        val step: OnboardingStep,
    ) : OnboardingDestination

    public data object Complete : OnboardingDestination

    public data object Feed : OnboardingDestination

    public data object BoardRegister : OnboardingDestination
}

/** Step 1 입력값과 필드별 검증 사유. 문구 변환은 `OnboardingStep1Screen` 가 한다. */
@Immutable
public data class OnboardingStep1FormState(
    val name: String = "",
    val school: String = "",
    val major: String = "",
    val gradePointAverage: String = "",
    val graduationDate: String = "",
    val nameError: OnboardingFieldError? = null,
    val schoolError: OnboardingFieldError? = null,
    val majorError: OnboardingFieldError? = null,
    val gradePointAverageError: OnboardingFieldError? = null,
    val graduationDateError: OnboardingFieldError? = null,
) {
    val hasErrors: Boolean
        get() = listOfNotNull(nameError, schoolError, majorError, gradePointAverageError, graduationDateError).isNotEmpty()
}

/** Step 2 입력값. [selectedJobCodes] 의 순서가 우선순위다. */
@Immutable
public data class OnboardingStep2FormState(
    val selectedJobCodes: List<String> = emptyList(),
    val interestInput: String = "",
    val interestTags: List<String> = emptyList(),
) {
    init {
        require(selectedJobCodes.distinct().size == selectedJobCodes.size) { "selected job codes must be unique" }
        require(selectedJobCodes.size <= MAX_JOB_INTERESTS) { "selected job codes must not exceed $MAX_JOB_INTERESTS" }
        require(selectedJobCodes.all(JobOptionCatalog::contains)) { "selected job codes must come from JobOptionCatalog" }
        require(interestTags.all(String::isNotBlank)) { "interest tags must not be blank" }
        require(interestTags.distinct().size == interestTags.size) { "interest tags must be unique" }
        require(interestTags.size <= MAX_PROFILE_TAGS) { "interest tags must not exceed $MAX_PROFILE_TAGS" }
    }
}

/** Step 3 목록 상태. [isLoaded] 가 false 면 아직 서버 목록을 받지 않았다. */
@Immutable
public data class OnboardingStep3FormState(
    val selectedType: ExperienceType = ExperienceType.Project,
    val experiences: List<Experience> = emptyList(),
    val isLoaded: Boolean = false,
) {
    init {
        require(experiences.map(Experience::id).distinct().size == experiences.size) { "experience ids must be unique" }
    }
}

/** 업로드 문서의 처리 상태. */
@Immutable
public sealed interface OnboardingUploadStatus {
    public data object Processing : OnboardingUploadStatus

    /** 분류가 끝났다. [items] 는 사용자가 분류를 조정할 수 있는 항목 목록(F1-4)이다. */
    @Immutable
    public data class Completed(
        val items: List<PastApplicationItem>,
    ) : OnboardingUploadStatus {
        init {
            require(items.map(PastApplicationItem::id).distinct().size == items.size) { "item ids must be unique" }
        }

        val classifiedItemCount: Int
            get() = items.size
    }

    @Immutable
    public data class Failed(
        val reason: OnboardingFailureReason,
    ) : OnboardingUploadStatus
}

/**
 * Step 4 목록의 문서 하나.
 *
 * @property id 목록 안에서만 유효한 식별자. 서버 문서는 `remote-<id>`, 이번 세션 업로드는 `local-<n>`.
 * @property remoteId 서버 id. 업로드가 끝나기 전이나 실패한 문서는 null.
 * @property label 사용자가 정한 업로드 라벨(F1-4). 파일 업로드도 올리기 전에 라벨 시트에서 받는다.
 * @property sizeBytes 서버 목록에는 크기가 없어 null 일 수 있다.
 * @property file 재시도에 쓸 원본. 서버에서 읽어 온 문서는 null.
 */
@Immutable
public data class OnboardingUploadDocument(
    val id: String,
    val remoteId: Long?,
    val label: String,
    val sizeBytes: Long?,
    val status: OnboardingUploadStatus,
    val file: UploadFile?,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        require(sizeBytes == null || sizeBytes >= 1) { "sizeBytes must be null or positive" }
    }
}

/**
 * Step 4 목록 상태.
 *
 * @property expandedDocumentId 항목 목록을 펼친 문서. 한 번에 하나만 펼친다 — 목록이 길어지면 아래 액션이 밀린다.
 */
@Immutable
public data class OnboardingStep4FormState(
    val documents: List<OnboardingUploadDocument> = emptyList(),
    val isLoaded: Boolean = false,
    val expandedDocumentId: String? = null,
) {
    init {
        require(documents.map(OnboardingUploadDocument::id).distinct().size == documents.size) { "document ids must be unique" }
        require(documents.size <= MAX_PAST_APPLICATIONS) { "documents must not exceed $MAX_PAST_APPLICATIONS" }
        require(expandedDocumentId == null || documents.any { it.id == expandedDocumentId }) {
            "expandedDocumentId must refer to a listed document"
        }
    }
}

/**
 * 그래프 스코프 [OnboardingViewModel] 의 전체 상태 — Step 1~4 와 완료 화면이 함께 본다.
 *
 * [failure]·[pendingNavigation]·[sessionEnded] 는 단발 신호다. 시트·피커 상태는 null 이면 닫힌 것이다.
 *
 * @property sessionEnded 401 로 세션이 끝났다 — 피드·게시판의 `sessionEnded` 와 같은 신호다(#211). Screen 이
 *   소비해 앱 셸의 `onSessionEnded(SessionEndCause.Expired)` 를 부르고, 셸이 시작 목적지를 다시 계산해
 *   로그인 화면으로 보낸다. 온보딩이 판정하는 것은 「끝났다」까지이고, **왜** 끝났는지와 무엇을 알릴지는
 *   셸이 갖는다(#128).
 */
@Immutable
public data class OnboardingFlowState(
    val isResolvingEntry: Boolean = true,
    val step1: OnboardingStep1FormState = OnboardingStep1FormState(),
    val step2: OnboardingStep2FormState = OnboardingStep2FormState(),
    val step3: OnboardingStep3FormState = OnboardingStep3FormState(),
    val step4: OnboardingStep4FormState = OnboardingStep4FormState(),
    val userName: String? = null,
    val isSubmitting: Boolean = false,
    val failure: OnboardingFailureReason? = null,
    val sessionEnded: Boolean = false,
    val pendingNavigation: OnboardingDestination? = null,
    val schoolPicker: SchoolPickerState? = null,
    val graduationPicker: GraduationPickerState? = null,
    val experienceEditor: ExperienceEditorState? = null,
    val experienceDelete: ExperienceDeleteState? = null,
    val directInput: DirectInputState? = null,
    val uploadLabel: UploadLabelState? = null,
    val itemCategoryPicker: PastApplicationItemCategoryState? = null,
) : UiState {
    init {
        require(userName == null || userName.isNotBlank()) { "userName must be null or non-blank" }
    }

    /** 진입 판정이 끝나고 저장 요청이 없을 때만 입력을 받는다. */
    val isInputEnabled: Boolean
        get() = !isResolvingEntry && !isSubmitting
}

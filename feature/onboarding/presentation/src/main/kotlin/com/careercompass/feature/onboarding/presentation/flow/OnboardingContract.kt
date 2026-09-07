package com.careercompass.feature.onboarding.presentation.flow

import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceDeleteState
import com.careercompass.core.ui.component.ExperienceEditorState
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.GraduationPickerState
import com.careercompass.core.ui.component.PastApplicationItemCategoryEvent
import com.careercompass.core.ui.component.PastApplicationItemCategoryState
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.core.ui.component.SchoolPickerState
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.feature.onboarding.presentation.OnboardingStep1Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep2Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep3Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Event
import com.careercompass.feature.onboarding.presentation.complete.OnboardingCompleteEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputState
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelState

/**
 * Step 1~4 와 완료 화면이 그래프 스코프 [OnboardingViewModel] 에 보내는 것 — 진입점은 `onIntent` 하나다(#245).
 *
 * 화면·시트마다 이미 sealed 이벤트 계약이 있으므로(`OnboardingStep1Event` 등), Intent 는 그 이벤트를 어느
 * 화면이 보냈는지로 감싼다. 이벤트 계약을 하나로 합치지 않는 이유는 stateless 화면과 골든이 그 계약을 그대로
 * 쓰기 때문이다. 뒤로 가기·파일 선택기 열기처럼 상태를 바꾸지 않는 갈래는 Screen 이 걸러 여기까지 오지 않는다.
 */
public sealed interface OnboardingIntent : MviIntent {
    public data class Step1(
        val event: OnboardingStep1Event,
    ) : OnboardingIntent

    public data class SchoolPicker(
        val event: SchoolPickerEvent,
    ) : OnboardingIntent

    public data class GraduationPicker(
        val event: GraduationDatePickerEvent,
    ) : OnboardingIntent

    public data class Step2(
        val event: OnboardingStep2Event,
    ) : OnboardingIntent

    public data class Step3(
        val event: OnboardingStep3Event,
    ) : OnboardingIntent

    public data class ExperienceDelete(
        val event: ExperienceDeleteEvent,
    ) : OnboardingIntent

    public data class ExperienceEditor(
        val event: ExperienceQuickAddEvent,
    ) : OnboardingIntent

    public data class Step4(
        val event: OnboardingStep4Event,
    ) : OnboardingIntent

    /** Screen 이 파일 선택기에서 읽어 만든 [UploadFile]. 바로 올리지 않고 라벨 시트를 먼저 연다(F1-4). */
    public data class FileSelected(
        val file: UploadFile,
    ) : OnboardingIntent

    /** 파일을 [UploadFile] 로 만들지 못했다(지원하지 않는 형식·크기 초과·읽기 실패). */
    public data class FileSelectionFailed(
        val reason: OnboardingFailureReason,
        val cause: Throwable,
    ) : OnboardingIntent

    public data class UploadLabel(
        val event: UploadLabelEvent,
    ) : OnboardingIntent

    public data class ItemCategoryPicker(
        val event: PastApplicationItemCategoryEvent,
    ) : OnboardingIntent

    public data class DirectInput(
        val event: DirectInputEvent,
    ) : OnboardingIntent

    public data class Complete(
        val event: OnboardingCompleteEvent,
    ) : OnboardingIntent

    public data object ConsumeNavigation : OnboardingIntent

    public data object ConsumeFailure : OnboardingIntent

    /**
     * Screen 이 세션 종료를 앱 셸에 넘겼다.
     *
     * 넘기기 **전에** 비운다 — 그래프 스코프 상태를 Step 1~4 가 함께 보므로, 전환 중 두 화면이 같은 신호를
     * 읽고 각자 셸을 부를 수 있다. 셸은 그 겹침을 견디지만(재계산 합류), 신호는 한 번만 살아 있는 편이 옳다.
     */
    public data object ConsumeSessionEnded : OnboardingIntent
}

/**
 * 상태가 겪은 것. [OnboardingViewModel] 만 만든다.
 *
 * 온보딩 상태는 조각(Step 1~4 폼·시트·피커)이 많아, 이벤트는 「어느 조각이 어떤 값이 됐다」 를 나른다. 다음 조각
 * 값을 계산하는 일(검증·정규화·목록 갱신)은 `onIntent` 쪽 처리기가 하고, `reduce` 는 그 값을 자리에 놓는 순수
 * 함수로 남는다. 조각 둘이 함께 바뀌어야 하는 전이(학교 확정 = 폼 갱신 + 피커 닫기)는 이벤트 하나로 묶는다 —
 * 둘로 나누면 그 사이 프레임에 반쯤 바뀐 상태가 그려진다.
 */
public sealed interface OnboardingReducerEvent : ReducerEvent {
    /** 진입 판정이 끝났다 — 프로필 프리필이 초안 위에 덮인다(서버 > 초안 > 빈 값). */
    public data class EntryResolved(
        val userName: String?,
        val step1: OnboardingStep1FormState,
        val step2: OnboardingStep2FormState,
    ) : OnboardingReducerEvent

    public data class Step1Updated(
        val form: OnboardingStep1FormState,
    ) : OnboardingReducerEvent

    public data class Step2Updated(
        val form: OnboardingStep2FormState,
    ) : OnboardingReducerEvent

    public data class Step3Updated(
        val form: OnboardingStep3FormState,
    ) : OnboardingReducerEvent

    public data class Step4Updated(
        val form: OnboardingStep4FormState,
    ) : OnboardingReducerEvent

    /** null 이면 닫힌 것이다. */
    public data class SchoolPickerUpdated(
        val picker: SchoolPickerState?,
    ) : OnboardingReducerEvent

    /** 학교를 정했다 — Step 1 폼을 갱신하고 피커를 닫는다. */
    public data class SchoolChosen(
        val form: OnboardingStep1FormState,
    ) : OnboardingReducerEvent

    public data class GraduationPickerUpdated(
        val picker: GraduationPickerState?,
    ) : OnboardingReducerEvent

    /** 졸업 시점을 정했다 — Step 1 폼을 갱신하고 피커를 닫는다. */
    public data class GraduationChosen(
        val form: OnboardingStep1FormState,
    ) : OnboardingReducerEvent

    public data class ExperienceEditorUpdated(
        val editor: ExperienceEditorState?,
    ) : OnboardingReducerEvent

    /** 카드 저장을 시작했다 — 시트를 잠그고 앞선 실패 표시를 지운다. */
    public data class ExperienceSubmissionStarted(
        val editor: ExperienceEditorState,
    ) : OnboardingReducerEvent

    /** 카드가 저장됐다 — 목록을 갱신하고 시트를 닫는다. */
    public data class ExperienceSaved(
        val form: OnboardingStep3FormState,
    ) : OnboardingReducerEvent

    /** 카드 저장이 실패했다 — 시트 잠금을 풀고 사유를 알린다. */
    public data class ExperienceSubmissionFailed(
        val reason: OnboardingFailureReason?,
    ) : OnboardingReducerEvent

    public data class ExperienceDeleteUpdated(
        val dialog: ExperienceDeleteState?,
    ) : OnboardingReducerEvent

    public data class UploadLabelUpdated(
        val sheet: UploadLabelState?,
    ) : OnboardingReducerEvent

    public data class DirectInputUpdated(
        val input: DirectInputState?,
    ) : OnboardingReducerEvent

    public data class ItemCategoryPickerUpdated(
        val picker: PastApplicationItemCategoryState?,
    ) : OnboardingReducerEvent

    /** 단계 저장을 시작했다 — 입력을 잠그고 앞선 실패 표시를 지운다. [step1] 은 검증 표시를 함께 실을 때만 있다. */
    public data class SubmissionStarted(
        val step1: OnboardingStep1FormState? = null,
    ) : OnboardingReducerEvent

    /** 단계 저장이 끝났다. [userName] 은 Step 1 저장이 이름을 확정했을 때만 있다. */
    public data class SubmissionSucceeded(
        val userName: String? = null,
    ) : OnboardingReducerEvent

    /** 단계 저장이 실패했다 — 입력 잠금을 풀고 사유를 알린다. 세션 만료면 사유가 없다(null). */
    public data class SubmissionFailed(
        val reason: OnboardingFailureReason?,
    ) : OnboardingReducerEvent

    /** 저장 요청 밖의 실패 — 사유만 알린다(상한·낙관적 갱신 되돌림·파일 검증). */
    public data class Failed(
        val reason: OnboardingFailureReason?,
    ) : OnboardingReducerEvent

    /** 401 — 배너 대신 셸이 로그인 화면으로 보내게 한다(#211). */
    public data object SessionEnded : OnboardingReducerEvent

    public data class NavigationRequested(
        val destination: OnboardingDestination,
    ) : OnboardingReducerEvent

    public data object NavigationConsumed : OnboardingReducerEvent

    public data object FailureConsumed : OnboardingReducerEvent

    public data object SessionEndedConsumed : OnboardingReducerEvent
}

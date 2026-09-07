package com.careercompass.feature.onboarding.presentation.flow

import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.ui.component.DirectInputEvent
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.PastApplicationItemCategoryEvent
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.feature.onboarding.presentation.OnboardingStep1Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep2Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep3Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Event
import com.careercompass.feature.onboarding.presentation.complete.OnboardingCompleteEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelEvent

/*
 * 테스트가 읽기 쉽도록 화면별 이벤트를 [OnboardingIntent] 로 감싸는 짧은 손잡이.
 *
 * 프로덕션의 진입점은 [OnboardingViewModel.onIntent] 하나뿐이다(#245). 이 확장은 테스트 소스에만 있어
 * 「어느 화면이 보낸 이벤트인가」 를 시나리오 이름으로 읽게 해 주고, 프로덕션에 `onStep1Event` 같은 두 번째
 * 진입점을 되살리지 않는다.
 */

internal fun OnboardingViewModel.onStep1Event(event: OnboardingStep1Event) = onIntent(OnboardingIntent.Step1(event))

internal fun OnboardingViewModel.onSchoolPickerEvent(event: SchoolPickerEvent) = onIntent(OnboardingIntent.SchoolPicker(event))

internal fun OnboardingViewModel.onGraduationPickerEvent(event: GraduationDatePickerEvent) =
    onIntent(OnboardingIntent.GraduationPicker(event))

internal fun OnboardingViewModel.onStep2Event(event: OnboardingStep2Event) = onIntent(OnboardingIntent.Step2(event))

internal fun OnboardingViewModel.onStep3Event(event: OnboardingStep3Event) = onIntent(OnboardingIntent.Step3(event))

internal fun OnboardingViewModel.onExperienceDeleteEvent(event: ExperienceDeleteEvent) = onIntent(OnboardingIntent.ExperienceDelete(event))

internal fun OnboardingViewModel.onExperienceEditorEvent(event: ExperienceQuickAddEvent) =
    onIntent(OnboardingIntent.ExperienceEditor(event))

internal fun OnboardingViewModel.onStep4Event(event: OnboardingStep4Event) = onIntent(OnboardingIntent.Step4(event))

internal fun OnboardingViewModel.onFileSelected(file: UploadFile) = onIntent(OnboardingIntent.FileSelected(file))

internal fun OnboardingViewModel.onFileSelectionFailed(
    reason: OnboardingFailureReason,
    cause: Throwable,
) = onIntent(OnboardingIntent.FileSelectionFailed(reason, cause))

internal fun OnboardingViewModel.onUploadLabelEvent(event: UploadLabelEvent) = onIntent(OnboardingIntent.UploadLabel(event))

internal fun OnboardingViewModel.onItemCategoryPickerEvent(event: PastApplicationItemCategoryEvent) =
    onIntent(OnboardingIntent.ItemCategoryPicker(event))

internal fun OnboardingViewModel.onDirectInputEvent(event: DirectInputEvent) = onIntent(OnboardingIntent.DirectInput(event))

internal fun OnboardingViewModel.onCompleteEvent(event: OnboardingCompleteEvent) = onIntent(OnboardingIntent.Complete(event))

internal fun OnboardingViewModel.onNavigationConsumed() = onIntent(OnboardingIntent.ConsumeNavigation)

internal fun OnboardingViewModel.onFailureConsumed() = onIntent(OnboardingIntent.ConsumeFailure)

internal fun OnboardingViewModel.onSessionEndedConsumed() = onIntent(OnboardingIntent.ConsumeSessionEnded)

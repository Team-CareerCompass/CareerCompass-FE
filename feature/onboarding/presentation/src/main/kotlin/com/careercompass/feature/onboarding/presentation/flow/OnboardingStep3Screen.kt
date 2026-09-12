package com.careercompass.feature.onboarding.presentation.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.ui.component.ExperienceDeleteDialog
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.component.ExperienceQuickAddSheet
import com.careercompass.core.ui.component.labelResId
import com.careercompass.feature.onboarding.presentation.OnboardingExperience
import com.careercompass.feature.onboarding.presentation.OnboardingExperienceType
import com.careercompass.feature.onboarding.presentation.OnboardingStep3Content
import com.careercompass.feature.onboarding.presentation.OnboardingStep3Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep3UiState
import com.careercompass.feature.onboarding.presentation.R
import com.careercompass.feature.onboarding.presentation.flow.component.OnboardingFlowFailureHost
import com.careercompass.feature.onboarding.presentation.shared.component.OnboardingSheetHost

/**
 * Step 3(경험) 화면의 상태 배선. [viewModel] 은 그래프 스코프 [OnboardingViewModel] 이어야 한다.
 *
 * @param onSessionEnded 401 로 세션이 끝났다 — 앱 셸이 사유를 만료로 갈라 로그인 화면으로 보낸다(#211).
 */
@Composable
public fun OnboardingStep3Screen(
    viewModel: OnboardingViewModel,
    onNavigate: (OnboardingDestination) -> Unit,
    onBack: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConsumePendingNavigation(
        destination = state.pendingNavigation,
        onNavigate = onNavigate,
        onConsumed = { viewModel.onIntent(OnboardingIntent.ConsumeNavigation) },
    )
    ConsumeSessionEnd(
        sessionEnded = state.sessionEnded,
        onSessionEnded = onSessionEnded,
        onConsumed = { viewModel.onIntent(OnboardingIntent.ConsumeSessionEnded) },
    )

    OnboardingFlowFailureHost(
        failure = state.failure,
        onDismiss = { viewModel.onIntent(OnboardingIntent.ConsumeFailure) },
        modifier = modifier,
    ) {
        OnboardingStep3Content(
            state = state.step3.toUiState(isInputEnabled = state.isInputEnabled),
            onEvent = { event ->
                if (event == OnboardingStep3Event.BackClicked) onBack() else viewModel.onIntent(OnboardingIntent.Step3(event))
            },
        )
    }

    state.experienceEditor?.let { editor ->
        OnboardingSheetHost(
            onDismissRequest = { viewModel.onIntent(OnboardingIntent.ExperienceEditor(ExperienceQuickAddEvent.Dismissed)) },
            // 제출 중에는 스크림·스와이프·뒤로가기로 시트가 숨겨지지 않게 한다(#340). ViewModel 도 같은 조건으로
            // 닫기를 무시하므로, 숨은 시트만 남거나 늦은 응답이 다음 시트에 떨어지는 일이 없다.
            isDismissEnabled = !editor.isSubmitting,
        ) {
            ExperienceQuickAddSheet(state = editor, onEvent = { viewModel.onIntent(OnboardingIntent.ExperienceEditor(it)) })
        }
    }

    state.experienceDelete?.let { pending ->
        ExperienceDeleteDialog(state = pending, onEvent = { viewModel.onIntent(OnboardingIntent.ExperienceDelete(it)) })
    }
}

@Composable
private fun OnboardingStep3FormState.toUiState(isInputEnabled: Boolean): OnboardingStep3UiState =
    OnboardingStep3UiState(
        experienceTypes =
            ExperienceType.entries.map { type ->
                OnboardingExperienceType(id = type.wireValue, label = stringResource(type.labelResId()))
            },
        selectedExperienceTypeId = selectedType.wireValue,
        experiences = experiences.map { it.toUiModel() },
        isInputEnabled = isInputEnabled,
    )

@Composable
private fun Experience.toUiModel(): OnboardingExperience =
    OnboardingExperience(
        id = id.toString(),
        typeId = type.wireValue,
        title = title,
        period = periodText(),
        role = roleText(),
        tags = (details as? ExperienceDetails.Project)?.techs.orEmpty(),
    )

/**
 * 카드 목록에 그릴 시점 글.
 *
 * 시점은 그 카드가 **아는 정밀도 그대로** 그린다 — 연도만 아는 수상은 「2025」, 연월 이상은 「2025.06」이다.
 * 유형별로 어느 필드를 먼저 볼지 따지던 규칙은 모델의 [ExperiencePoint] 로 옮겨 갔다(#207).
 */
@Composable
private fun Experience.periodText(): String {
    val unknown = stringResource(R.string.onboarding_experience_period_unknown)
    val start = startPoint?.toPeriodText() ?: return unknown
    if (!type.hasPeriod) return start
    val end = endPoint?.toPeriodText() ?: stringResource(R.string.onboarding_experience_period_ongoing)
    return stringResource(R.string.onboarding_experience_period_range, start, end)
}

@Composable
private fun Experience.roleText(): String =
    when (val details = details) {
        is ExperienceDetails.Project -> details.role ?: stringResource(R.string.onboarding_experience_role_fallback_project)
        is ExperienceDetails.Award -> details.rank
        is ExperienceDetails.Intern -> stringResource(R.string.onboarding_experience_role_intern, details.company, details.role)
        is ExperienceDetails.Activity -> details.role ?: details.organization
        is ExperienceDetails.Certificate -> details.issuer ?: stringResource(R.string.onboarding_experience_role_fallback_certificate)
    }

private fun ExperiencePoint.toPeriodText(): String =
    when (this) {
        is ExperiencePoint.Year -> "%04d".format(year)
        is ExperiencePoint.WithMonth -> "%04d.%02d".format(year, month)
    }

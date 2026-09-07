package com.careercompass.feature.onboarding.presentation.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.user.JobOptionCatalog
import com.careercompass.feature.onboarding.presentation.OnboardingJobOption
import com.careercompass.feature.onboarding.presentation.OnboardingStep2Content
import com.careercompass.feature.onboarding.presentation.OnboardingStep2Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep2UiState
import com.careercompass.feature.onboarding.presentation.flow.component.OnboardingFlowFailureHost

/**
 * Step 2(희망 직무·관심 분야) 화면의 상태 배선. [viewModel] 은 그래프 스코프 [OnboardingViewModel] 이어야 한다.
 *
 * @param onSessionEnded 401 로 세션이 끝났다 — 앱 셸이 사유를 만료로 갈라 로그인 화면으로 보낸다(#211).
 */
@Composable
public fun OnboardingStep2Screen(
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
        OnboardingStep2Content(
            state = state.step2.toUiState(isInputEnabled = state.isInputEnabled),
            onEvent = { event ->
                if (event == OnboardingStep2Event.BackClicked) onBack() else viewModel.onIntent(OnboardingIntent.Step2(event))
            },
        )
    }
}

internal fun OnboardingStep2FormState.toUiState(isInputEnabled: Boolean): OnboardingStep2UiState =
    OnboardingStep2UiState(
        jobOptions = JobOptionCatalog.options.map { OnboardingJobOption(id = it.code, label = it.label) },
        selectedJobIds = selectedJobCodes.toSet(),
        interestInput = interestInput,
        interestTags = interestTags,
        isInputEnabled = isInputEnabled,
    )

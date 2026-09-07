package com.careercompass.feature.profile.presentation.home

import kotlinx.coroutines.flow.StateFlow

/*
 * 테스트가 읽기 쉽도록 [ProfileHomeIntent] 를 짧은 손잡이로 감싼다. 프로덕션의 진입점은
 * [ProfileHomeViewModel.onIntent] 하나다.
 */

internal val ProfileHomeViewModel.state: StateFlow<ProfileHomeUiState> get() = uiState

internal fun ProfileHomeViewModel.onEvent(event: ProfileHomeEvent) = onIntent(ProfileHomeIntent.Screen(event))

internal fun ProfileHomeViewModel.onRefresh() = onIntent(ProfileHomeIntent.Refresh)

internal fun ProfileHomeViewModel.onBiometricAvailabilityChanged(canEnroll: Boolean) =
    onIntent(ProfileHomeIntent.BiometricAvailabilityChanged(canEnroll))

internal fun ProfileHomeViewModel.onEnrollPromptRequestConsumed() = onIntent(ProfileHomeIntent.ConsumeEnrollPromptRequest)

internal fun ProfileHomeViewModel.onBiometricEnrollSucceeded() = onIntent(ProfileHomeIntent.BiometricEnrollSucceeded)

internal fun ProfileHomeViewModel.onBiometricEnrollCancelled() = onIntent(ProfileHomeIntent.BiometricEnrollCancelled)

internal fun ProfileHomeViewModel.onBiometricEnrollFailed(cause: Throwable) = onIntent(ProfileHomeIntent.BiometricEnrollFailed(cause))

internal fun ProfileHomeViewModel.onMenuNavigationConsumed() = onIntent(ProfileHomeIntent.ConsumeMenuNavigation)

internal fun ProfileHomeViewModel.onMessageConsumed() = onIntent(ProfileHomeIntent.ConsumeMessage)

internal fun ProfileHomeViewModel.onSessionEndedConsumed() = onIntent(ProfileHomeIntent.ConsumeSessionEnded)

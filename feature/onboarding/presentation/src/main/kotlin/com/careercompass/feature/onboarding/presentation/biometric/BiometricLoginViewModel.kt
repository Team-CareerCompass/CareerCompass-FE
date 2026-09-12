package com.careercompass.feature.onboarding.presentation.biometric

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.domain.usecase.auth.ResolveSessionEntryUseCase
import com.careercompass.core.domain.usecase.auth.SessionEntryDestination
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.feature.onboarding.presentation.reporting.OnboardingFailureStage
import com.careercompass.feature.onboarding.presentation.reporting.recordOnboardingFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 지문 빠른 로그인 화면 상태 — F1-1 「로그인 시 지문 인식」. 진입점은 [onIntent] 하나, 전이는 [reduce] 한 곳이다(#245).
 *
 * 인증 성공은 새 세션 발급이 아니라 **이미 저장된 세션을 그대로 쓴다는 확인**이다. 다만 그 세션을 서버가 아직
 * 받아 주는지는 여기서 모르므로, 성공 뒤 [ResolveSessionEntryUseCase] 로 검증해 목적지를 정한다 — 곧장 피드로
 * 보내면 죽은 세션이 피드 → 401 → 로그인으로 두 번 이동하며 피드 셸이 잠깐 드러났다(#81). 생체 인증 자체는
 * [BiometricLoginScreen] 이 `BiometricPrompt` 로 수행하고 결과만 Intent 로 온다.
 */
@HiltViewModel
public class BiometricLoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        userProfileRepository: UserProfileRepository,
        private val resolveSessionEntry: ResolveSessionEntryUseCase,
        private val errorReporter: ErrorReporter,
    ) : MviViewModel<BiometricLoginIntent, BiometricLoginUiState, BiometricLoginReducerEvent>(BiometricLoginUiState()) {
        private var verifyJob: Job? = null

        init {
            viewModelScope.launch {
                combine(authRepository.isBiometricEnabled, userProfileRepository.profile) { enabled, profile ->
                    BiometricLoginReducerEvent.AccountChanged(isBiometricEnabled = enabled, userName = profile?.name)
                }.collect(::dispatch)
            }
        }

        override fun onIntent(intent: BiometricLoginIntent) {
            when (intent) {
                BiometricLoginIntent.AuthenticationStarted -> {
                    dispatch(BiometricLoginReducerEvent.AuthenticationStarted)
                }

                BiometricLoginIntent.AuthenticationSucceeded -> {
                    verifySession()
                }

                BiometricLoginIntent.AuthenticationCancelled -> {
                    dispatch(BiometricLoginReducerEvent.AuthenticationEnded)
                }

                is BiometricLoginIntent.AuthenticationFailed -> {
                    errorReporter.recordOnboardingFailure(OnboardingFailureStage.BiometricAuth, intent.cause)
                    dispatch(BiometricLoginReducerEvent.AuthenticationFailed(intent.reason))
                }

                BiometricLoginIntent.ChooseOtherMethod -> {
                    chooseOtherMethod()
                }

                BiometricLoginIntent.ConsumeNavigation -> {
                    dispatch(BiometricLoginReducerEvent.NavigationConsumed)
                }

                BiometricLoginIntent.ConsumeFailure -> {
                    dispatch(BiometricLoginReducerEvent.FailureConsumed)
                }
            }
        }

        override fun reduce(
            state: BiometricLoginUiState,
            event: BiometricLoginReducerEvent,
        ): BiometricLoginUiState =
            when (event) {
                is BiometricLoginReducerEvent.AccountChanged -> {
                    state.copy(isBiometricEnabled = event.isBiometricEnabled, userName = event.userName)
                }

                BiometricLoginReducerEvent.AuthenticationStarted -> {
                    state.copy(isAuthenticating = true, failure = null)
                }

                is BiometricLoginReducerEvent.SessionResolved -> {
                    state.copy(isAuthenticating = false, pendingNavigation = event.destination)
                }

                BiometricLoginReducerEvent.AuthenticationEnded -> {
                    state.copy(isAuthenticating = false)
                }

                is BiometricLoginReducerEvent.AuthenticationFailed -> {
                    state.copy(isAuthenticating = false, failure = event.reason)
                }

                BiometricLoginReducerEvent.OtherMethodChosen -> {
                    state.copy(pendingNavigation = BiometricDestination.Login)
                }

                BiometricLoginReducerEvent.NavigationConsumed -> {
                    state.copy(pendingNavigation = null)
                }

                BiometricLoginReducerEvent.FailureConsumed -> {
                    state.copy(failure = null)
                }
            }

        /**
         * 다른 방법으로 로그인 — 이 기기에 남은 세션을 먼저 정리하고 로그인 화면으로 보낸다.
         *
         * 여기서 로그인하는 사람은 다음 계정일 수 있다. 앞 계정의 SESSION 스코프 저장소(프로필·온보딩 진행·공고
         * 스냅샷)를 지우지 않고 로그인 화면만 띄우면 그 계정이 앞 계정의 상태를 그대로 물려받는다(#335).
         * 정리에 실패해도 화면은 보낸다 — 사용자를 지문 화면에 가둘 이유가 되지 못하고, 새 세션 저장이 한 번 더
         * 비운다.
         */
        private fun chooseOtherMethod() {
            viewModelScope.launch {
                authRepository.clearSession()
                dispatch(BiometricLoginReducerEvent.OtherMethodChosen)
            }
        }

        /**
         * 지문이 맞았다 — 인증 중 표시를 유지한 채 세션을 검증하고 목적지를 정한다. 검증이 이미 진행 중이면 합류한다.
         * 서버 확인에 실패해 마지막으로 알려진 온보딩 상태로 판단했으면 그 실패를 기록만 하고 진행한다.
         */
        private fun verifySession() {
            if (verifyJob?.isActive == true) return
            dispatch(BiometricLoginReducerEvent.AuthenticationStarted)
            verifyJob =
                viewModelScope.launch {
                    val entry = resolveSessionEntry()
                    entry.fallbackCause?.let { errorReporter.recordOnboardingFailure(OnboardingFailureStage.BiometricSessionVerify, it) }
                    val destination =
                        when (entry.destination) {
                            SessionEntryDestination.Feed -> BiometricDestination.Feed
                            SessionEntryDestination.Onboarding -> BiometricDestination.Onboarding
                            SessionEntryDestination.Login -> BiometricDestination.SessionExpired
                        }
                    dispatch(BiometricLoginReducerEvent.SessionResolved(destination))
                }
        }
    }

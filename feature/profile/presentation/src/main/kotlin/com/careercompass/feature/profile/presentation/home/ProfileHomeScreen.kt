package com.careercompass.feature.profile.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.mvi.ObserveFlag
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.feature.profile.presentation.R
import kotlinx.coroutines.launch

/**
 * 마이 홈 진입점 — 하단 탭 「마이」가 그리는 화면(Figma 05 · 01).
 *
 * 화면에 (다시) 들어올 때마다 서버 값을 맞춘다. 프로필 편집·경험 카드에서 돌아오면 개수와 완성도가 바뀌어
 * 있을 수 있어서다 — 진행 중인 조회가 있으면 ViewModel 이 겹치지 않게 막는다.
 *
 * @param onNavigate 메뉴를 눌렀다. 목적지는 앱 셸이 정한다 — 네 화면이 각각 다른 모듈 몫이라 이 화면이
 *   알 수 있는 것이 아니다(#176 · #178 · #180 · #196).
 * @param onSessionEnded 로그아웃했거나 401 을 만났다 — 셸이 시작 목적지를 다시 계산해 로그인으로 되돌린다.
 *   어느 쪽인지는 [ProfileSessionEnd] 가 말한다: 만료 안내를 띄울지는 그 값으로 갈린다(#128).
 * @param biometricEnrollPrompt 지문 등록 프롬프트 어댑터. 플랫폼을 아는 셸이 채운다
 *   ([ProfileBiometricEnrollPrompt] 의 KDoc).
 */
@Composable
public fun ProfileHomeScreen(
    onNavigate: (ProfileHomeMenu) -> Unit,
    onSessionEnded: (ProfileSessionEnd) -> Unit,
    biometricEnrollPrompt: ProfileBiometricEnrollPrompt,
    modifier: Modifier = Modifier,
    viewModel: ProfileHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(ProfileHomeIntent.Refresh)
        onPauseOrDispose { }
    }

    ObserveSignal(
        signal = state.pendingMenu,
        consumed = ProfileHomeIntent.ConsumeMenuNavigation,
        onIntent = viewModel::onIntent,
        onSignal = onNavigate,
    )
    ObserveSignal(
        signal = state.sessionEnd,
        consumed = ProfileHomeIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
        onSignal = onSessionEnded,
    )
    ObserveSignal(
        signal = state.message,
        consumed = ProfileHomeIntent.ConsumeMessage,
        onIntent = viewModel::onIntent,
    ) { message ->
        // 스낵바는 표출에 시간이 걸린다 — effect 수명에 매달면 소비 직후의 상태 변화가 이것을 취소한다.
        snackbarScope.launch { snackbarHostState.showSnackbar(resources.getString(message.messageRes())) }
    }

    // 켜는 방향은 #98 의 등록 프롬프트를 그대로 쓴다. null 이면 이 기기·호스트에서 지문을 등록할 수 없다는 뜻이다.
    val launchEnrollPrompt =
        biometricEnrollPrompt.rememberLauncher { result ->
            when (result) {
                ProfileBiometricEnrollResult.Succeeded -> viewModel.onIntent(ProfileHomeIntent.BiometricEnrollSucceeded)
                ProfileBiometricEnrollResult.Cancelled -> viewModel.onIntent(ProfileHomeIntent.BiometricEnrollCancelled)
                is ProfileBiometricEnrollResult.Failed -> viewModel.onIntent(ProfileHomeIntent.BiometricEnrollFailed(result.cause))
            }
        }
    val canEnrollBiometric = launchEnrollPrompt != null
    LaunchedEffect(canEnrollBiometric) {
        viewModel.onIntent(ProfileHomeIntent.BiometricAvailabilityChanged(canEnrollBiometric))
    }

    ObserveFlag(
        raised = state.isEnrollPromptRequested,
        consumed = ProfileHomeIntent.ConsumeEnrollPromptRequest,
        onIntent = viewModel::onIntent,
    ) {
        // 등록할 수 없는 기기에서는 스위치가 잠겨 있어 요청이 오지 않는다. 그래도 오면 잠금이 풀리지 않으므로
        // 취소와 같게 되돌린다.
        val launch = launchEnrollPrompt
        if (launch != null) launch() else viewModel.onIntent(ProfileHomeIntent.BiometricEnrollCancelled)
    }

    Box(modifier = modifier.fillMaxSize()) {
        ProfileHomeContent(
            state = state,
            onEvent = { viewModel.onIntent(ProfileHomeIntent.Screen(it)) },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun ProfileHomeMessage.messageRes(): Int =
    when (this) {
        ProfileHomeMessage.RefreshFailed -> R.string.profile_home_refresh_failed
    }

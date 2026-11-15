package com.careercompass.careercompass_fe.navigation

import androidx.compose.runtime.Composable
import com.careercompass.careercompass_fe.session.SessionEndCause
import com.careercompass.core.ui.navigation.pushSingleTop
import com.careercompass.feature.onboarding.presentation.biometric.BiometricEnrollPromptResult
import com.careercompass.feature.onboarding.presentation.biometric.rememberBiometricEnrollPrompt
import com.careercompass.feature.profile.presentation.home.ProfileBiometricEnrollPrompt
import com.careercompass.feature.profile.presentation.home.ProfileBiometricEnrollResult
import com.careercompass.feature.profile.presentation.home.ProfileHomeMenu
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd

/**
 * 마이 홈의 지문 등록 프롬프트 — **셸이 두 피처를 잇는 자리다.**
 *
 * 프롬프트 배선(`FragmentActivity` · `BiometricPrompt`)은 온보딩 모듈이 갖고 있고(#98 · #113), profile 이
 * 그것을 직접 의존하면 feature 가 feature 를 보게 된다. 셸은 이미 둘 다 의존하므로 여기서 어댑터 하나로
 * 잇는다 — 옮겨진 것은 결과 타입 셋뿐이고 판정은 온보딩 쪽 구현이 그대로 갖는다.
 */
internal object AppBiometricEnrollPrompt : ProfileBiometricEnrollPrompt {
    @Composable
    override fun rememberLauncher(onResult: (ProfileBiometricEnrollResult) -> Unit): (() -> Unit)? =
        rememberBiometricEnrollPrompt { result ->
            onResult(
                when (result) {
                    BiometricEnrollPromptResult.Succeeded -> ProfileBiometricEnrollResult.Succeeded
                    BiometricEnrollPromptResult.Cancelled -> ProfileBiometricEnrollResult.Cancelled
                    is BiometricEnrollPromptResult.Failed -> ProfileBiometricEnrollResult.Failed(result.cause)
                },
            )
        }
}

/**
 * 마이 홈 메뉴 ↔ 루트 키. 넷 다 마이 탭 위 한 칸으로 쌓인다.
 *
 * 목적지를 profile 모듈이 아니라 셸이 갖는 이유는 네 화면의 주인이 서로 다르기 때문이다 — 프로필 편집·경험
 * 카드·과거 지원서는 profile, 알림 설정은 notification 모듈이다. 앞의 셋은 실제 화면이 붙었고(#176 · #178 · #180),
 * 알림 설정만 아직 자리표시자라 그 화면이 붙으면 이 표의 오른쪽 한 줄만 바뀐다.
 */
internal fun AppState.navigateToProfileMenu(menu: ProfileHomeMenu) {
    val key =
        when (menu) {
            ProfileHomeMenu.ProfileEdit -> Route.ProfileEdit
            ProfileHomeMenu.ExperienceCards -> Route.ExperienceCards
            ProfileHomeMenu.PastApplications -> Route.PastApplications
            ProfileHomeMenu.NotificationSettings -> Route.NotificationSettingsPlaceholder
        }
    backStack.pushSingleTop(key)
}

/**
 * profile 화면이 올린 세션 종료 사유를 셸의 사유로 옮긴다.
 *
 * 로그아웃은 사용자가 한 일이라 안내하지 않고, 401 만 로그인 화면에 만료를 알린다(#128). 화면이 둘 다
 * 낼 수 있으므로 갈래를 아는 쪽이 말해 주고 셸은 옮기기만 한다.
 */
internal fun ProfileSessionEnd.toSessionEndCause(): SessionEndCause =
    when (this) {
        ProfileSessionEnd.LoggedOut -> SessionEndCause.LoggedOut
        ProfileSessionEnd.Expired -> SessionEndCause.Expired
    }

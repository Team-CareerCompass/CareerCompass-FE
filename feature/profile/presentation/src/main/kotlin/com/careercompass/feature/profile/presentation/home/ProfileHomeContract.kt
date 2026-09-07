package com.careercompass.feature.profile.presentation.home

import androidx.compose.runtime.Composable
import com.careercompass.core.model.settings.ThemeMode

/**
 * 마이 홈에서 갈 수 있는 곳 — Figma 05 · 01 「마이」의 메뉴 네 줄이다.
 *
 * 목적지 결정은 화면이 아니라 앱 셸이 갖는다(`AppNavigation`). 여기 있는 것은 「사용자가 무엇을 눌렀는가」
 * 뿐이라, 실제 화면이 붙는 순서(#176 · #178 · #180 · #196)와 무관하게 이 계약은 그대로다.
 *
 * ### 「등록 게시판」이 없는 이유
 * 시안에는 다섯째 줄로 등록 게시판이 있지만 그 화면은 feed 모듈 소유이고(`BoardListScreen`), 거기로
 * 보내려면 피드 로컬 스택에 진입 요청을 하나 더 여는 일이 먼저다. 이 화면의 완료 조건(#175)이 그 줄을
 * 세지 않으므로 남의 모듈을 함께 고치지 않고 비워 둔다.
 */
public enum class ProfileHomeMenu {
    ProfileEdit,
    ExperienceCards,
    PastApplications,
    NotificationSettings,
}

/**
 * 이 화면에서 세션이 끝난 두 갈래 — 셸은 이 값으로 로그인 화면에 만료 안내를 띄울지 정한다(#128).
 *
 * 자리표시자 시절에는 마이 탭에서 세션이 끝나는 길이 로그아웃 하나뿐이라 셸이 그것을 알고 있었다. 이제는
 * 프로필 조회가 401 을 만나는 길도 같은 화면에 있으므로, **어느 쪽인지 아는 화면이 말해 준다.**
 */
public enum class ProfileSessionEnd {
    /** 사용자가 로그아웃했다. 자기가 한 일이라 안내하지 않는다. */
    LoggedOut,

    /** 401 로 세션이 만료됐다. 로그인 화면이 그 사실을 알려야 한다. */
    Expired,
}

/** 스낵바 한 줄로 끝나는 알림 — 화면을 덮지 않는 실패다. */
public enum class ProfileHomeMessage {
    /** 캐시로 그린 채 새로고침만 실패했다. 보이는 값이 오래됐을 수 있다는 것만 알린다. */
    RefreshFailed,
}

/** 화면이 [ProfileHomeViewModel] 에 올려 보내는 사용자 조작. */
public sealed interface ProfileHomeEvent {
    public data class MenuClicked(
        val menu: ProfileHomeMenu,
    ) : ProfileHomeEvent

    public data object RetryClicked : ProfileHomeEvent

    public data class BiometricToggled(
        val enabled: Boolean,
    ) : ProfileHomeEvent

    public data object ThemeClicked : ProfileHomeEvent

    public data class ThemeSelected(
        val mode: ThemeMode,
    ) : ProfileHomeEvent

    public data object ThemeDismissed : ProfileHomeEvent

    public data object LogoutClicked : ProfileHomeEvent

    public data object LogoutConfirmed : ProfileHomeEvent

    public data object LogoutDismissed : ProfileHomeEvent
}

/** 지문 등록 프롬프트가 끝난 방식. 플랫폼(BiometricPrompt)의 결과를 이 모듈의 말로 옮긴 것이다. */
public sealed interface ProfileBiometricEnrollResult {
    public data object Succeeded : ProfileBiometricEnrollResult

    public data object Cancelled : ProfileBiometricEnrollResult

    public data class Failed(
        val cause: Throwable,
    ) : ProfileBiometricEnrollResult
}

/**
 * 지문 등록 프롬프트를 이 화면에 붙이는 어댑터 — **구현은 앱 셸이 준다.**
 *
 * 프롬프트는 `FragmentActivity` 와 `BiometricPrompt` 를 알아야 하고, 그 배선은 온보딩 모듈이 이미
 * 갖고 있다(`rememberBiometricEnrollPrompt`, #98 · #113). profile 이 그것을 직접 쓰려면 feature 가
 * feature 를 의존해야 하므로, 대신 **셸이 두 모듈을 잇게** 한다 — 셸은 이미 둘 다 의존한다.
 *
 * MVI 규약이 「플랫폼에 매인 콜백은 stateful 층의 파라미터로 남긴다」고 정한 자리와 같다
 * (`docs/convention/mvi.md`, 소셜 SDK 토큰 요청).
 */
public fun interface ProfileBiometricEnrollPrompt {
    /**
     * 등록 프롬프트를 띄우는 함수. **이 기기·호스트에서 지문을 등록할 수 없으면 null** 이고, 화면은
     * 그것으로 스위치를 켤 수 있는지 판정한다.
     */
    @Composable
    public fun rememberLauncher(onResult: (ProfileBiometricEnrollResult) -> Unit): (() -> Unit)?
}

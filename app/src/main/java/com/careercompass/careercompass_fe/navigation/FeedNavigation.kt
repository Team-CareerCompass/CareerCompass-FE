package com.careercompass.careercompass_fe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.careercompass.core.ui.component.CareerCompassBottomTab
import com.careercompass.feature.feed.presentation.navigation.FeedExternalActions

/**
 * 피드 로컬 스택이 앱 셸에 남긴 이동의 구현. 상세·원문·게시판은 `FeedNavHost` 가 제 백스택으로 처리한다.
 *
 * @param onSessionEnded 401 로 세션이 끝났다 — 시작 목적지를 다시 계산해 로그인으로 보낸다.
 */
@Composable
internal fun rememberFeedExternalActions(
    appState: AppState,
    onSessionEnded: () -> Unit,
): FeedExternalActions {
    val onSessionEndedState by rememberUpdatedState(onSessionEnded)
    return remember(appState) {
        object : FeedExternalActions {
            override fun navigateToNotifications() = appState.navigateToNotifications()

            /** 프로필 입력 안내 — 마이 탭(profile 모듈 진입점이 생기기 전까지 자리표시자). */
            override fun navigateToProfileTab() = appState.navigateToTab(CareerCompassBottomTab.My)

            /** 지원서 초안 작성 — editor 모듈의 문항 확인 화면(#183). */
            override fun navigateToApplicationSetup(postingId: Long) = appState.navigateToApplicationSetup(postingId)

            override fun onSessionEnded() = onSessionEndedState()
        }
    }
}

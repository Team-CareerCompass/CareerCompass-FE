package com.careercompass.feature.feed.presentation.navigation

/**
 * 피드 로컬 스택이 **스스로 갈 수 없는 곳**과 셸만 내릴 수 있는 판정을 앱 셸에 남긴 것.
 *
 * 그래프 안의 push/pop 은 [FeedNavHost] 가 로컬 백스택으로 직접 처리한다. 스택 바닥에서의 back 은 이동이 아니라 경계라
 * [com.careercompass.core.ui.navigation.FeatureStackBoundary] 가 갖는다.
 */
public interface FeedExternalActions {
    /** 피드 헤더의 알림 — notification 모듈이 진입점을 제공할 때까지 셸의 자리표시자. */
    public fun navigateToNotifications()

    /** 프로필 입력 안내 — 마이 탭. */
    public fun navigateToProfileTab()

    /** 지원서 초안 작성 — editor 모듈의 문항 확인 화면(#183). 피드 그래프 밖이라 셸이 쌓는다. */
    public fun navigateToApplicationSetup(postingId: Long)

    /** 401 로 세션이 끝났다 — 셸이 시작 목적지를 다시 계산해 로그인 화면으로 보낸다. */
    public fun onSessionEnded()
}

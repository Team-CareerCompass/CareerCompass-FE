package com.careercompass.feature.feed.presentation.navigation

/**
 * 피드 화면 콜백이 요청하는 이동. 그래프 안의 push/pop 은 [FeedLocalNavActions] 가 로컬 백스택으로 처리하고,
 * 그래프 밖 목적지(알림·마이 탭)와 세션 판정은 [FeedExternalActions] 로 앱 셸에 넘긴다.
 */
public interface FeedNavActions {
    public fun navigateToPostingDetail(postingId: Long)

    public fun navigateToPostingRaw(postingId: Long)

    public fun navigateToBoardRegister()

    public fun navigateToBoardList()

    public fun navigateToNotifications()

    /** 프로필 입력 안내 — 앱 셸이 마이 탭으로 보낸다. */
    public fun navigateToProfileTab()

    /** 지원서 초안 작성 — 앱 셸이 editor 모듈의 문항 확인 화면으로 보낸다(#183). */
    public fun navigateToApplicationSetup(postingId: Long)

    /** 뒤로 가기. 피드 홈에서 부르면 로컬 스택이 바닥이므로 셸이 결정한다. */
    public fun popBack()

    /** 401 로 세션이 끝났다. 앱 셸이 로그인 화면으로 보낸다. */
    public fun onSessionEnded()
}

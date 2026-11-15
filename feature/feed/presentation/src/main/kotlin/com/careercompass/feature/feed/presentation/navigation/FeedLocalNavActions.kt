package com.careercompass.feature.feed.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.core.ui.navigation.popOrExit
import com.careercompass.core.ui.navigation.pushSingleTop

/**
 * 피드 화면 콜백을 로컬 백스택 조작으로 잇는다.
 *
 * 컴포저블이 아니라 평범한 클래스다 — 백스택 모양을 컴포지션 없이 재려는 것이다(`FeedLocalNavActionsTest`).
 */
internal class FeedLocalNavActions(
    private val backStack: NavBackStack<NavKey>,
    private val boundary: FeatureStackBoundary,
    private val externalActions: FeedExternalActions,
) : FeedNavActions {
    /** 유사 공고 이동처럼 상세 위에 다른 상세가 쌓이는 것이 정상이라 single top 이 아니다. */
    override fun navigateToPostingDetail(postingId: Long) {
        backStack.add(FeedRoute.PostingDetail(postingId))
    }

    override fun navigateToPostingRaw(postingId: Long) {
        backStack.add(FeedRoute.PostingRaw(postingId))
    }

    override fun navigateToBoardRegister(): Unit = backStack.pushSingleTop(FeedRoute.BoardRegister)

    override fun navigateToBoardList(): Unit = backStack.pushSingleTop(FeedRoute.BoardList)

    override fun navigateToNotifications(): Unit = externalActions.navigateToNotifications()

    override fun navigateToProfileTab(): Unit = externalActions.navigateToProfileTab()

    override fun navigateToApplicationSetup(postingId: Long): Unit = externalActions.navigateToApplicationSetup(postingId)

    override fun popBack(): Unit = backStack.popOrExit(boundary)

    override fun onSessionEnded(): Unit = externalActions.onSessionEnded()

    /**
     * 셸의 진입 요청을 스택에 반영한다.
     *
     * 딥링크 상세는 같은 상세가 이미 최상단이면 다시 쌓지 않는다 — 소비 전에 효과가 다시 돌아도 안전하다. 다른 상세면
     * 유사 공고 이동처럼 위에 쌓는다. 게시판 등록은 같은 화면을 연달아 쌓지 않는다.
     */
    fun applyEntryRequest(request: FeedEntryRequest) {
        when (request) {
            is FeedEntryRequest.PostingDetail -> {
                val top = backStack.lastOrNull() as? FeedRoute.PostingDetail
                if (top?.postingId != request.postingId) backStack.add(FeedRoute.PostingDetail(request.postingId))
            }

            FeedEntryRequest.BoardRegister -> {
                backStack.pushSingleTop(FeedRoute.BoardRegister)
            }
        }
    }
}

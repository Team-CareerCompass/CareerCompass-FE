package com.careercompass.feature.feed.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 피드 로컬 백스택의 모양 회귀 기준(#259) — Nav2 시절 루트 `NavController` 가 만들던 스택을 그대로 못박는다.
 *
 * 컴포지션이 필요 없어 Robolectric 없이 돈다. 스택을 화면 수명으로 옮기는 기전은 `core:ui` 의 `FeatureNavDisplayTest` 가 본다.
 */
public class FeedLocalNavActionsTest {
    private var exits = 0
    private val external = RecordingExternalActions()
    private val exitCountingBoundary =
        object : FeatureStackBoundary {
            override fun exit() {
                exits += 1
            }
        }
    private val backStack = NavBackStack<NavKey>(FeedRoute.Home)
    private val actions =
        FeedLocalNavActions(
            backStack = backStack,
            boundary = exitCountingBoundary,
            externalActions = external,
        )

    private fun shape(): List<String> =
        backStack.map { key ->
            when (key) {
                is FeedRoute.PostingDetail -> "PostingDetail(${key.postingId})"
                is FeedRoute.PostingRaw -> "PostingRaw(${key.postingId})"
                else -> key::class.simpleName!!
            }
        }

    @Test
    public fun `상세 위에 다른 상세와 원문이 쌓이고 뒤로가기로 한 칸씩 내려온다`() {
        actions.navigateToPostingDetail(1)
        actions.navigateToPostingDetail(2)
        actions.navigateToPostingRaw(2)
        assertEquals(listOf("Home", "PostingDetail(1)", "PostingDetail(2)", "PostingRaw(2)"), shape())

        actions.popBack()
        actions.popBack()
        assertEquals(listOf("Home", "PostingDetail(1)"), shape())
        assertEquals(0, exits)
    }

    @Test
    public fun `게시판 등록과 목록은 같은 화면을 연달아 쌓지 않는다`() {
        actions.navigateToBoardList()
        actions.navigateToBoardList()
        actions.navigateToBoardRegister()
        actions.navigateToBoardRegister()

        assertEquals(listOf("Home", "BoardList", "BoardRegister"), shape())
    }

    @Test
    public fun `홈에서의 뒤로가기는 스택을 비우지 않고 셸에 넘긴다`() {
        actions.popBack()

        assertEquals(listOf("Home"), shape())
        assertEquals(1, exits)
    }

    @Test
    public fun `그래프 밖 이동과 세션 판정은 스택을 건드리지 않고 셸로 나간다`() {
        actions.navigateToNotifications()
        actions.navigateToProfileTab()
        actions.onSessionEnded()

        assertEquals(listOf("notifications", "profile", "session-ended"), external.calls)
        assertEquals(listOf("Home"), shape())
    }

    @Test
    public fun `딥링크 상세는 같은 상세가 최상단이면 다시 쌓지 않고 다른 상세면 위에 쌓는다`() {
        actions.applyEntryRequest(FeedEntryRequest.PostingDetail(7))
        actions.applyEntryRequest(FeedEntryRequest.PostingDetail(7))
        assertEquals(listOf("Home", "PostingDetail(7)"), shape())

        actions.applyEntryRequest(FeedEntryRequest.PostingDetail(8))
        assertEquals(listOf("Home", "PostingDetail(7)", "PostingDetail(8)"), shape())
    }

    @Test
    public fun `게시판 먼저 등록하기는 홈 위에 등록 화면 하나를 올린다`() {
        actions.applyEntryRequest(FeedEntryRequest.BoardRegister)
        actions.applyEntryRequest(FeedEntryRequest.BoardRegister)

        assertEquals(listOf("Home", "BoardRegister"), shape())
        // 등록 화면에서 뒤로가기는 홈이다 — 온보딩 완료 화면으로 돌아가지 않는다.
        actions.popBack()
        assertEquals(listOf("Home"), shape())
        assertEquals(0, exits)
    }

    /** 초안 작성은 피드 그래프 밖이다 — 로컬 스택을 건드리지 않고 셸에 넘긴다. */
    @Test
    public fun `초안 작성은 로컬 스택을 쌓지 않고 셸로 넘긴다`() {
        actions.navigateToApplicationSetup(101L)

        assertEquals(listOf("Home"), shape())
        assertEquals(listOf("application-setup:101"), external.calls)
    }

    private class RecordingExternalActions : FeedExternalActions {
        val calls = mutableListOf<String>()

        override fun navigateToNotifications() {
            calls += "notifications"
        }

        override fun navigateToProfileTab() {
            calls += "profile"
        }

        override fun navigateToApplicationSetup(postingId: Long) {
            calls += "application-setup:$postingId"
        }

        override fun onSessionEnded() {
            calls += "session-ended"
        }
    }
}

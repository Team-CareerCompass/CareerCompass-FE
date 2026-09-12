package com.careercompass.feature.feed.presentation.board

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardListFailureContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun networkUnavailable_showsConnectionNotice() {
        composeRule.setFailureContent(reason = FeedFailureReason.NetworkUnavailable)

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("점검 진행 중").assertCountEquals(0)
    }

    @Test
    fun maintenance_showsMaintenanceNoticeAndEmitsRetry() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Maintenance,
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("서비스가 잠시 점검 중이에요").assertIsDisplayed()
        composeRule.onNodeWithText("점검 진행 중").assertIsDisplayed()
        // 게시판 목록은 스냅샷을 저장하지 않아 어느 사유에서도 오프라인 경로가 없다.
        composeRule.onAllNodesWithText("오프라인 모드로 보기").assertCountEquals(0)
        composeRule.onNode(hasText("새로고침") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun generic_showsBoardRetryNotice() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.Unexpected),
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("게시판을 불러오지 못했어요").assertIsDisplayed()
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    /**
     * 404 는 지워진 게시판이다. 다시 물어도 같은 답이 온다(#342).
     *
     * 전에는 사유가 [FailureKind.Unexpected] 로 뭉개져 「게시판을 불러오지 못했어요 + 다시 시도」가 나갔다.
     */
    @Test
    fun notFound_namesTheBoardAndDropsRetry() {
        composeRule.setFailureContent(reason = FeedFailureReason.Generic(FailureKind.NotFound))

        composeRule.onNodeWithText("게시판을 찾을 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
    }

    /**
     * 표가 이 화면에 없는 길을 가리키면 버튼을 그리지 않는다(#342).
     *
     * 목록이 넘길 수 있는 콜백은 재조회 하나뿐이라, 「정리하러 가기」라고 적힌 버튼이 재조회를 하게 된다.
     */
    @Test
    fun limitExceeded_dropsActionThisScreenCannotPerform() {
        composeRule.setFailureContent(reason = FeedFailureReason.Generic(FailureKind.LimitExceeded))

        composeRule.onNodeWithText("게시판을 더 등록할 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("정리하러 가기").assertCountEquals(0)
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
    }
}

private fun ComposeContentTestRule.setFailureContent(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit = {},
) {
    setContent {
        CareerCompassTheme {
            BoardListFailureContent(reason = reason, onRetryClick = onRetryClick)
        }
    }
}

package com.careercompass.feature.feed.presentation.postingraw

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

/**
 * 원문 보기의 실패 화면이 **사유마다** 갈리는지 본다 — 문구도 행동 버튼도.
 *
 * 셋을 한 화면으로 접으면 점검 중에 상세에서 「원문 보기」를 누른 사용자가 바로 앞 화면과 다른 말을 듣는다
 * (#212). 게시판 목록의 같은 시험(`BoardListFailureContentTest`)과 짝이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostingRawFailureContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun networkUnavailable_showsConnectionNotice() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.NetworkUnavailable,
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("점검 진행 중").assertCountEquals(0)
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun maintenance_showsSameMaintenanceCopyAsThePrecedingScreen() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Maintenance,
            onRetryClick = { retryCount += 1 },
        )

        // 공고 상세·게시판 목록·피드 홈과 같은 문장이다. 여기서 갈리면 같은 503 을 화면마다 다르게 말하게 된다.
        composeRule.onNodeWithText("서비스가 잠시 점검 중이에요").assertIsDisplayed()
        composeRule.onNodeWithText("점검 진행 중").assertIsDisplayed()
        // 일반 실패 문구가 함께 뜨면 안 된다 — 그게 접혀 있던 자리다.
        composeRule.onAllNodesWithText("원문을 불러오지 못했어요").assertCountEquals(0)
        // 원문은 스냅샷을 저장하지 않아 오프라인 경로가 없다.
        composeRule.onAllNodesWithText("오프라인 모드로 보기").assertCountEquals(0)
        // 「다시 시도」가 아니라 「새로고침」이다 — 재시도를 권하는 실패가 아니다.
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
        composeRule.onNode(hasText("새로고침") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun generic_showsRawRetryNotice() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.Unexpected),
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("원문을 불러오지 못했어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("점검 진행 중").assertCountEquals(0)
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }
}

private fun ComposeContentTestRule.setFailureContent(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit = {},
) {
    setContent {
        CareerCompassTheme {
            PostingRawFailureContent(reason = reason, onRetryClick = onRetryClick)
        }
    }
}

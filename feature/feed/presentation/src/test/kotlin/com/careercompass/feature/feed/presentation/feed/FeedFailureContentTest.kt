package com.careercompass.feature.feed.presentation.feed

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
class FeedFailureContentTest {
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
        composeRule.onNode(hasText("새로고침") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun maintenanceWithSnapshot_keepsOfflineRouteOpen() {
        var offlineCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Maintenance,
            onOfflineClick = { offlineCount += 1 },
        )

        composeRule.onNode(hasText("오프라인 모드로 보기") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, offlineCount) }
    }

    @Test
    fun maintenanceWithoutSnapshot_hidesOfflineRoute() {
        composeRule.setFailureContent(reason = FeedFailureReason.Maintenance, onOfflineClick = null)

        composeRule.onAllNodesWithText("오프라인 모드로 보기").assertCountEquals(0)
    }

    @Test
    fun generic_showsRetryNoticeWithoutMaintenanceCopy() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.Unexpected),
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("공고를 불러오지 못했어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("점검 진행 중").assertCountEquals(0)
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    /**
     * 조건 때문에 실패한 자리의 탈출구(#144). 실패 화면이 헤더를 통째로 대신하므로, 이 버튼이 없으면
     * 조건을 되돌릴 조작이 화면에 하나도 남지 않는다.
     */
    @Test
    fun queryReset_emitsResetAndKeepsRetryRoute() {
        var resetCount = 0
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Maintenance,
            onRetryClick = { retryCount += 1 },
            onOfflineClick = {},
            onResetQueryClick = { resetCount += 1 },
        )

        composeRule.onNode(hasText("조건 지우고 다시 보기") and hasClickAction()).performClick()

        // 조건을 지우는 길이 열려도 「지금 조건 그대로」 하는 길이 닫히지는 않는다 — 서버가 곧 살아날
        // 수도 있고, 그때는 조건을 버릴 이유가 없다.
        composeRule.onNode(hasText("새로고침") and hasClickAction()).performClick()
        composeRule.onNode(hasText("오프라인 모드로 보기") and hasClickAction()).assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(1, resetCount)
            assertEquals(1, retryCount)
        }
    }

    @Test
    fun withoutQueryReset_hidesResetAction() {
        // null 은 「되돌릴 조건이 없거나 조건 탓이 아니다」는 뜻이다 — 눌러도 같은 실패로 돌아올
        // 버튼을 그리지 않는다. 판정은 FeedViewState.canResetFailedQuery 가 한다.
        composeRule.setFailureContent(reason = FeedFailureReason.Maintenance, onResetQueryClick = null)

        composeRule.onAllNodesWithText("조건 지우고 다시 보기").assertCountEquals(0)
    }

    @Test
    fun genericWithQueryReset_keepsReasonNoticeIntact() {
        // 사유 화면은 탈출구가 붙어도 바뀌지 않는다 — 아래에 한 겹 덧붙일 뿐이다.
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.Unexpected),
            onResetQueryClick = {},
        )

        composeRule.onNodeWithText("공고를 불러오지 못했어요").assertIsDisplayed()
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("조건 지우고 다시 보기").assertIsDisplayed()
    }

    /** 429 는 「잠깐 쉬었다 다시」다. 사유를 말해야 사용자가 왜 기다리는지 안다(#342). */
    @Test
    fun rateLimited_saysWhyAndKeepsRetry() {
        var retryCount = 0
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.RateLimited),
            onRetryClick = { retryCount += 1 },
        )

        composeRule.onNodeWithText("요청이 너무 많아요").assertIsDisplayed()
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    /**
     * 403 은 다시 보내도 같은 답이 온다. 표가 「할 수 있는 일 없음」이라고 하면 버튼을 그리지 않는다(#342).
     *
     * 조건 초기화는 별개다. 그 판정은 사유(`FeedFailureReason.isQueryAttributable`)가 하지 갈래가 하지 않는다.
     */
    @Test
    fun permissionDenied_dropsRetryButKeepsQueryReset() {
        composeRule.setFailureContent(
            reason = FeedFailureReason.Generic(FailureKind.PermissionDenied),
            onResetQueryClick = {},
        )

        composeRule.onNodeWithText("접근할 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
        composeRule.onNodeWithText("조건 지우고 다시 보기").assertIsDisplayed()
    }
}

private fun ComposeContentTestRule.setFailureContent(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit = {},
    onOfflineClick: (() -> Unit)? = null,
    onResetQueryClick: (() -> Unit)? = null,
) {
    setContent {
        CareerCompassTheme {
            FeedFailureContent(
                reason = reason,
                onRetryClick = onRetryClick,
                onOfflineClick = onOfflineClick,
                onResetQueryClick = onResetQueryClick,
            )
        }
    }
}

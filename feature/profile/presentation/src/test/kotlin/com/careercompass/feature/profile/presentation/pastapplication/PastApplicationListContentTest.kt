package com.careercompass.feature.profile.presentation.pastapplication

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PastApplicationListContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `라벨과 항목 개수와 상한을 그린다`() {
        composeRule.setContent(PastApplicationListUiState(applications = listOf(application(1L))))

        composeRule.onNodeWithText("지원서 1").assertIsDisplayed()
        composeRule.onNodeWithText("항목 2개").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 10개").assertIsDisplayed()
    }

    /** 펼쳐 봐야만 알 수 있으면 열 장을 하나씩 열어 봐야 한다. */
    @Test
    fun `확인이 필요한 항목 수를 접힌 채로도 보인다`() {
        composeRule.setContent(PastApplicationListUiState(applications = listOf(application(1L))))

        composeRule.onNodeWithText("확인 필요 1개").assertIsDisplayed()
    }

    @Test
    fun `펼치면 항목과 분류 칩이 보인다`() {
        composeRule.setContent(
            PastApplicationListUiState(applications = listOf(application(1L)), expandedId = 1L),
        )

        composeRule.onNodeWithText("분류가 애매한 문단").assertIsDisplayed()
        composeRule.onNodeWithText("지원 동기").assertIsDisplayed()
        composeRule.onNodeWithText("기타").assertIsDisplayed()
    }

    @Test
    fun `접혀 있으면 항목을 그리지 않는다`() {
        composeRule.setContent(PastApplicationListUiState(applications = listOf(application(1L))))

        composeRule.onAllNodesWithText("분류가 애매한 문단").assertCountEquals(0)
    }

    @Test
    fun `분류 칩을 누르면 그 항목을 올려 보낸다`() {
        val events = mutableListOf<PastApplicationListEvent>()
        composeRule.setContent(
            PastApplicationListUiState(applications = listOf(application(1L)), expandedId = 1L),
            onEvent = { events += it },
        )

        composeRule.onNode(hasText("기타") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(PastApplicationListEvent.ItemCategoryClicked(1L, 11L)), events)
        }
    }

    @Test
    fun `삭제 버튼을 누르면 그 지원서를 올려 보낸다`() {
        val events = mutableListOf<PastApplicationListEvent>()
        composeRule.setContent(
            PastApplicationListUiState(applications = listOf(application(7L))),
            onEvent = { events += it },
        )

        composeRule.onNode(hasText("삭제") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(listOf(PastApplicationListEvent.DeleteClicked(7L)), events) }
    }

    @Test
    fun `아직 올린 것이 없으면 올리러 가는 길을 연다`() {
        val events = mutableListOf<PastApplicationListEvent>()
        composeRule.setContent(PastApplicationListUiState(), onEvent = { events += it })

        composeRule.onNodeWithText("아직 올린 지원서가 없어요").assertIsDisplayed()
        composeRule.onNode(hasText("첫 지원서 올리기") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(listOf(PastApplicationListEvent.AddClicked), events) }
    }

    @Test
    fun `읽은 지원서가 없는 실패는 목록 자리를 덮는다`() {
        composeRule.setContent(PastApplicationListUiState(loadFailure = FailureKind.NoConnection))

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
    }

    private fun application(id: Long) =
        PastApplication(
            id = id,
            label = "지원서 $id",
            items =
                listOf(
                    PastApplicationItem(id = 11L, category = PastApplicationCategory.Other, content = "분류가 애매한 문단", confident = false),
                    PastApplicationItem(id = 12L, category = PastApplicationCategory.Motivation, content = "지원 동기 문단", confident = true),
                ),
            createdAt = null,
        )

    private fun ComposeContentTestRule.setContent(
        state: PastApplicationListUiState,
        onEvent: (PastApplicationListEvent) -> Unit = {},
    ) {
        setContent {
            CareerCompassTheme {
                PastApplicationListContent(state = state, onEvent = onEvent)
            }
        }
    }
}

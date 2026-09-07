package com.careercompass.feature.profile.presentation.experience

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
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
class ExperienceListContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `카드의 유형 배지와 제목과 기간을 그린다`() {
        composeRule.setContent(ExperienceListUiState(cards = listOf(project(1L)), totalCount = 1))

        composeRule.onNodeWithText("카드 1").assertIsDisplayed()
        composeRule.onNodeWithText("2025.03 ~ 진행 중").assertIsDisplayed()
        // 「프로젝트」는 필터 칩과 카드 배지 둘.
        composeRule.onAllNodesWithText("프로젝트").assertCountEquals(2)
    }

    /** 연도만 아는 수상 카드에 없는 달을 지어내지 않는다(#166). */
    @Test
    fun `시점은 아는 정밀도까지만 적는다`() {
        composeRule.setContent(ExperienceListUiState(cards = listOf(award(1L)), totalCount = 1))

        composeRule.onNodeWithText("2025").assertIsDisplayed()
    }

    @Test
    fun `상한을 개수와 함께 늘 보인다`() {
        composeRule.setContent(ExperienceListUiState(cards = listOf(project(1L)), totalCount = 12))

        composeRule.onNodeWithText("12 / 30장").assertIsDisplayed()
    }

    /** 필터가 걸리면 전체를 모르므로 개수 표시를 접는다. */
    @Test
    fun `필터가 걸려 전체를 모르면 개수를 적지 않는다`() {
        composeRule.setContent(
            ExperienceListUiState(
                cards = listOf(project(1L)),
                filter = ExperienceTypeFilter(ExperienceType.Project),
                totalCount = null,
            ),
        )

        composeRule.onAllNodesWithText("/ 30장", substring = true).assertCountEquals(0)
    }

    @Test
    fun `카드가 없으면 만들러 가는 길을 연다`() {
        val events = mutableListOf<ExperienceListEvent>()
        composeRule.setContent(ExperienceListUiState(totalCount = 0), onEvent = { events += it })

        composeRule.onNodeWithText("아직 등록한 경험이 없어요").assertIsDisplayed()
        composeRule.onNode(hasText("첫 카드 만들기") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(listOf(ExperienceListEvent.AddClicked), events) }
    }

    /** 필터 때문에 빈 것과 정말 없는 것은 사용자가 할 일이 다르다 — 되돌릴 조작을 준다. */
    @Test
    fun `필터 때문에 비면 전체로 돌아가는 길을 연다`() {
        val events = mutableListOf<ExperienceListEvent>()
        composeRule.setContent(
            ExperienceListUiState(filter = ExperienceTypeFilter(ExperienceType.Award)),
            onEvent = { events += it },
        )

        composeRule.onNodeWithText("이 유형의 카드가 없어요").assertIsDisplayed()
        composeRule.onNode(hasText("전체 보기") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(null))), events)
        }
    }

    @Test
    fun `읽은 카드가 없는 실패는 목록 자리를 덮는다`() {
        composeRule.setContent(ExperienceListUiState(loadFailure = FailureKind.NoConnection))

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
    }

    @Test
    fun `유형 필터를 누르면 올려 보낸다`() {
        val events = mutableListOf<ExperienceListEvent>()
        composeRule.setContent(ExperienceListUiState(cards = listOf(project(1L))), onEvent = { events += it })

        composeRule.onNode(hasText("자격증") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Certificate))), events)
        }
    }

    private fun project(id: Long) =
        Experience(
            id = id,
            title = "카드 $id",
            startPoint = ExperiencePoint.YearMonth(2025, 3),
            endPoint = null,
            details = ExperienceDetails.Project(role = "Android", techs = emptyList(), summary = null, link = null),
            createdAt = null,
        )

    private fun award(id: Long) =
        Experience(
            id = id,
            title = "수상 $id",
            startPoint = ExperiencePoint.Year(2025),
            endPoint = null,
            details = ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = "건국대학교"),
            createdAt = null,
        )

    private fun ComposeContentTestRule.setContent(
        state: ExperienceListUiState,
        onEvent: (ExperienceListEvent) -> Unit = {},
    ) {
        setContent {
            CareerCompassTheme {
                ExperienceListContent(state = state, onEvent = onEvent)
            }
        }
    }
}

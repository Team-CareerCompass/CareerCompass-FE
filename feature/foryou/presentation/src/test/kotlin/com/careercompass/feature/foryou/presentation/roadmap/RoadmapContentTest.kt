package com.careercompass.feature.foryou.presentation.roadmap

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.RoadmapMetric
import com.careercompass.feature.foryou.domain.model.RoadmapSuggestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoadmapContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `표본 수와 내 값과 평균과 학기별 제안을 보여 준다`() {
        show(RoadmapUiState(comparison = comparison()))
        composeRule.onNodeWithText("86명의 기록과 비교했어요").assertIsDisplayed()
        composeRule.onNodeWithText("동기 평균").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            ).performScrollToNode(hasText("3-2 학기"))
        composeRule.onNodeWithText("SQLD + 토익 800+").assertIsDisplayed()
    }

    @Test fun `내 성장은 현재 수치만 보여 주고 평균과 표본을 꾸미지 않는다`() {
        show(RoadmapUiState(cohort = RoadmapCohort.MeOnly, comparison = comparison().copy(cohort = RoadmapCohort.MeOnly, sampleSize = 0)))
        composeRule.onNodeWithText("현재 4").assertIsDisplayed()
        composeRule.onAllNodesWithText("동기 평균").assertCountEquals(0)
        composeRule.onAllNodesWithText("0명의 기록과 비교했어요").assertCountEquals(0)
    }

    @Test fun `표본 없는 비교는 지표 대신 빈 상태를 보여 준다`() {
        show(RoadmapUiState(comparison = comparison().copy(sampleSize = 0)))
        composeRule.onNodeWithText("아직 비교할 정보가 부족해요").assertIsDisplayed()
        composeRule.onAllNodesWithText("프로젝트 수").assertCountEquals(0)
    }

    @Test fun `코호트와 새로고침은 해당 의도를 보낸다`() {
        val intents = mutableListOf<RoadmapIntent>()
        show(RoadmapUiState(comparison = comparison().copy(sampleSize = 0)), onIntent = { intents += it })
        composeRule.onNodeWithText("합격 선배 비교").performClick()
        composeRule.onNodeWithText("새로고침").performClick()
        assertEquals(listOf(RoadmapIntent.SelectCohort(RoadmapCohort.Senior), RoadmapIntent.Refresh), intents)
    }

    @Test fun `프로필 부족은 프로필을 채울 경로를 연다`() {
        var completed = 0
        show(RoadmapUiState(failure = FailureKind.ProfileIncomplete), onCompleteProfile = { completed++ })
        composeRule.onNodeWithText("프로필 입력하기").performClick()
        assertEquals(1, completed)
    }

    private fun show(
        state: RoadmapUiState,
        onIntent: (RoadmapIntent) -> Unit = {},
        onCompleteProfile: () -> Unit = {},
    ) {
        composeRule.setContent { CareerCompassTheme { RoadmapContent(state, onIntent, {}, onCompleteProfile) } }
    }

    private fun comparison() =
        RoadmapComparison(
            RoadmapCohort.Peer,
            86,
            listOf(RoadmapMetric("프로젝트 수", 4.0, 2.0)),
            listOf(RoadmapSuggestion("3-2", "SQLD + 토익 800+", 12)),
        )
}

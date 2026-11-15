package com.careercompass.feature.editor.presentation.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationProgressContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `생성 중에도 받은 답을 읽을 수 있다`() {
        val state =
            ApplicationProgressUiState(
                draft = draft().copy(items = listOf(item(1, ApplicationItemStatus.Done, "이미 받은 답"), item(2))),
                isStreaming = true,
            )
        composeRule.setContent { CareerCompassTheme { ApplicationProgressContent(state, {}, {}, {}) } }
        composeRule.onNodeWithText("1 / 2개 항목 완료").assertIsDisplayed()
        composeRule.onNodeWithText("이미 받은 답").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("생성 중"))
        composeRule.onNodeWithText("생성 중").assertIsDisplayed()
    }

    @Test fun `큰 글꼴에서도 실패 항목을 재시도할 수 있다`() {
        val intents = mutableListOf<ApplicationProgressIntent>()
        val state = ApplicationProgressUiState(draft = draft().copy(items = listOf(item(2, ApplicationItemStatus.Failed))))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CareerCompassTheme { ApplicationProgressContent(state, { intents += it }, {}, {}) }
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("이 항목 다시 시도"))
        composeRule.onNodeWithText("이 항목 다시 시도").assertIsDisplayed().performClick()
        assertEquals(listOf(ApplicationProgressIntent.RetryItem(2)), intents)
    }

    @Test fun `재시도 중인 항목의 버튼만 잠근다`() {
        val state =
            ApplicationProgressUiState(
                draft = draft().copy(items = listOf(item(1, ApplicationItemStatus.Failed))),
                retryingIds = setOf(1),
            )
        composeRule.setContent { CareerCompassTheme { ApplicationProgressContent(state, {}, {}, {}) } }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("이 항목 다시 시도"))
        composeRule.onNodeWithText("이 항목 다시 시도").assertIsNotEnabled()
    }
}

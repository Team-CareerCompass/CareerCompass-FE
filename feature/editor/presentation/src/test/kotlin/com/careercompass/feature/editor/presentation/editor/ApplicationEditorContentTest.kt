package com.careercompass.feature.editor.presentation.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationEditorContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `줄바꿈 붙여넣기와 글자 초과를 그대로 보여 준다`() {
        var state by mutableStateOf(state())
        composeRule.setContent {
            CareerCompassTheme {
                ApplicationEditorContent(state, {
                    if (it is ApplicationProgressIntent.EditAnswer) state = state.copy(editedAnswers = mapOf(it.itemId to it.answer))
                }, {})
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("첫째 줄\n둘째 줄")
        composeRule.onNode(hasSetTextAction()).assertTextContains("첫째 줄\n둘째 줄")
        composeRule.onNodeWithText("9 / 5자").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("최대 글자 수를 넘었어요. 입력은 그대로 유지돼요.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `두 배 글꼴에서 열 번째 탭과 카운터가 스크롤로 보인다`() {
        val intents = mutableListOf<ApplicationProgressIntent>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CareerCompassTheme {
                    ApplicationEditorContent(state(), { intents += it }, {})
                }
            }
        }
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).performScrollToIndex(9)
        composeRule
            .onNodeWithText("10번 문항")
            .assertHeightIsAtLeast(48.dp)
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf(ApplicationProgressIntent.SelectItem(10)), intents)
        composeRule.onNodeWithText("4 / 5자").performScrollTo().assertIsDisplayed()
    }

    @Test fun `수동 삭제는 서버 답으로 되돌아가지 않고 다음 문항은 선택 의도만 보낸다`() {
        val intents = mutableListOf<ApplicationProgressIntent>()
        composeRule.setContent {
            CareerCompassTheme {
                ApplicationEditorContent(state().copy(editedAnswers = mapOf(1L to "")), { intents += it }, {})
            }
        }
        composeRule.onNodeWithText("0 / 5자").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("다음 문항").performClick()
        assertEquals(listOf(ApplicationProgressIntent.SelectItem(2)), intents)
    }

    private fun state() =
        ApplicationProgressUiState(
            draft =
                ApplicationDraft(
                    42,
                    ApplicationStatus.Ready,
                    (1..10).map { index ->
                        ApplicationItem(index.toLong(), index, "문항 $index 의 경험을 작성해 주세요", 5, ApplicationItemStatus.Done, "서버 답")
                    },
                ),
            selectedItemId = 1,
        )
}

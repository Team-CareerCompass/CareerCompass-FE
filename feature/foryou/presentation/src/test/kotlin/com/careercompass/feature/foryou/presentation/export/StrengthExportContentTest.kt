package com.careercompass.feature.foryou.presentation.export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.StrengthExport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrengthExportContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `체크박스와 네 형식은 선택 의도를 보낸다`() {
        val intents = mutableListOf<StrengthExportIntent>()
        show(StrengthExportUiState(), onIntent = { intents += it })
        composeRule.onNodeWithText("기본 정보").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("HTML"))
        composeRule.onNodeWithText("HTML").performClick()
        assertEquals(
            listOf(StrengthExportIntent.ToggleSection(ExportSection.Basic), StrengthExportIntent.SelectFormat(ExportFormat.Html)),
            intents,
        )
    }

    @Test fun `빈 선택은 생성 버튼을 잠근다`() {
        show(StrengthExportUiState(sections = emptyList()))
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("미리보기 만들기"))
        composeRule.onNodeWithText("미리보기 만들기").assertIsNotEnabled()
    }

    @Test fun `HTML은 문자열 미리보기로 보이고 빈 결과는 복사를 막는다`() {
        show(StrengthExportUiState(preview = StrengthExport(ExportFormat.Html, "")))
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("클립보드에 복사"))
        composeRule.onNodeWithText("클립보드에 복사").assertIsNotEnabled()
        composeRule.onNodeWithText("HTML 미리보기").assertIsDisplayed()
    }

    @Test fun `경험 없는 안내는 경험 카드 추가로 이어진다`() {
        var adds = 0
        show(StrengthExportUiState(hasExperiences = false), onAdd = { adds++ })
        composeRule.onNodeWithText("등록한 경험 카드가 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("경험 카드 추가하기").performClick()
        assertEquals(1, adds)
    }

    private fun show(
        state: StrengthExportUiState,
        onIntent: (StrengthExportIntent) -> Unit = {},
        onAdd: () -> Unit = {},
    ) {
        composeRule.setContent { CareerCompassTheme { StrengthExportContent(state, onIntent, {}, onAdd, {}) } }
    }
}

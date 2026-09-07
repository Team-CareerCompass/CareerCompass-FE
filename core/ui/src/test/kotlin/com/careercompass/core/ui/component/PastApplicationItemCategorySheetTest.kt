package com.careercompass.core.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
public class PastApplicationItemCategorySheetTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun blankIdentityOrPreview_isRejectedByContract() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleState.copy(documentId = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            sampleState.copy(contentPreview = "   ")
        }
    }

    @Test
    public fun sixCategories_areListedWithCurrentOneSelected() {
        setSheet(sampleState)

        composeRule.onNodeWithText("분류 바꾸기").assertIsDisplayed()
        composeRule.onNodeWithText(sampleState.contentPreview).assertIsDisplayed()
        listOf("지원 동기", "성장 배경", "경험 기술", "직무 역량", "입사 후 포부", "기타").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule
            .onNodeWithText("기타")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "선택됨"))
        composeRule
            .onNodeWithText("지원 동기")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "선택 안 됨"))
    }

    @Test
    public fun selectionAndCancel_emitDistinctEvents() {
        val events = mutableListOf<PastApplicationItemCategoryEvent>()
        setSheet(sampleState, onEvent = events::add)

        composeRule.onNodeWithText("직무 역량").performClick()
        composeRule.onNode(hasText("취소") and hasClickAction()).performClick()

        assertEquals(
            listOf(
                PastApplicationItemCategoryEvent.CategorySelected(PastApplicationCategory.Competency),
                PastApplicationItemCategoryEvent.Dismissed,
            ),
            events,
        )
    }

    private fun setSheet(
        state: PastApplicationItemCategoryState,
        onEvent: (PastApplicationItemCategoryEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            CareerCompassTheme {
                PastApplicationItemCategorySheet(state = state, onEvent = onEvent)
            }
        }
    }

    private companion object {
        val sampleState =
            PastApplicationItemCategoryState(
                documentId = "remote-9",
                itemId = 3L,
                contentPreview = "동아리에서 팀장을 맡아 협업하는 법을 배웠습니다.",
                selected = PastApplicationCategory.Other,
            )
    }
}

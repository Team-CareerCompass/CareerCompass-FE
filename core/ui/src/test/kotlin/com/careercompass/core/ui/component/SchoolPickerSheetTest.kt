package com.careercompass.core.ui.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
public class SchoolPickerSheetTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun results_renderAsButtonRowsWithAccessibleNames() {
        setSheet(SchoolPickerState(results = listOf("건국대학교", "고려대학교")))

        composeRule.onNodeWithText("학교 선택").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("건국대학교 선택")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("고려대학교 선택").assertIsDisplayed()
    }

    @Test
    public fun selectingRow_emitsSchoolSelected() {
        val events = mutableListOf<SchoolPickerEvent>()
        setSheet(SchoolPickerState(results = listOf("건국대학교")), onEvent = events::add)

        composeRule.onNodeWithContentDescription("건국대학교 선택").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SchoolPickerEvent.SchoolSelected("건국대학교")), events)
        }
    }

    @Test
    public fun typingQuery_emitsQueryChanged() {
        val events = mutableListOf<SchoolPickerEvent>()
        setSheet(SchoolPickerState(results = listOf("건국대학교")), onEvent = events::add)

        composeRule.onNodeWithContentDescription("학교 검색").performTextInput("건국")

        composeRule.runOnIdle {
            assertEquals(listOf(SchoolPickerEvent.QueryChanged("건국")), events)
        }
    }

    /** 0건이 막다른 길이면 앱 전체가 막힌다 — 안내 문구 옆에 반드시 빠져나갈 버튼이 있어야 한다(#138). */
    @Test
    public fun emptyResults_showEmptyMessageAndDirectInputEscape() {
        setSheet(SchoolPickerState(query = "없는대", results = emptyList()))

        composeRule.onNodeWithText("검색 결과가 없어요").assertIsDisplayed()
        directInputAction().assertIsDisplayed()
    }

    @Test
    public fun beforeSearching_directInputIsNotOffered() {
        setSheet(SchoolPickerState(results = listOf("건국대학교")))

        directInputAction().assertDoesNotExist()
    }

    @Test
    public fun directInputAction_emitsDirectInputRequested() {
        val events = mutableListOf<SchoolPickerEvent>()
        setSheet(SchoolPickerState(query = "없는대", results = emptyList()), onEvent = events::add)

        directInputAction().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SchoolPickerEvent.DirectInputRequested), events)
        }
    }

    @Test
    public fun directInputMode_showsFieldAndConfirmsTypedName() {
        val events = mutableListOf<SchoolPickerEvent>()
        setSheet(
            SchoolPickerState(
                query = "서울예술",
                results = emptyList(),
                directInput = SchoolDirectInputState(value = "서울예술대학교"),
            ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("학교 직접 입력").assertIsDisplayed()
        composeRule.onNodeWithText("서울예술대학교").assertIsDisplayed()
        confirmButton().assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SchoolPickerEvent.DirectInputConfirmed), events)
        }
    }

    @Test
    public fun directInputMode_returnsToList() {
        val events = mutableListOf<SchoolPickerEvent>()
        setSheet(
            SchoolPickerState(query = "서울예술", results = emptyList(), directInput = SchoolDirectInputState()),
            onEvent = events::add,
        )

        composeRule.onNode(hasText("목록에서 고르기") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SchoolPickerEvent.DirectInputCancelled), events)
        }
    }

    @Test
    public fun directInputMode_blocksConfirmAndShowsError() {
        setSheet(
            SchoolPickerState(
                query = "서울예술",
                results = emptyList(),
                directInput = SchoolDirectInputState(value = "가".repeat(51), error = ProfileFieldViolation.TooLong(50)),
            ),
        )

        composeRule.onNodeWithText("50자 이내로 입력해 주세요").assertIsDisplayed()
        confirmButton().assertIsNotEnabled()
    }

    private fun directInputAction() = composeRule.onNode(hasText("목록에 없어요. 직접 입력할게요") and hasClickAction())

    private fun confirmButton() = composeRule.onNode(hasText("이 학교로 정하기") and hasClickAction())

    private fun setSheet(
        state: SchoolPickerState,
        onEvent: (SchoolPickerEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            CareerCompassTheme {
                SchoolPickerSheet(state = state, onEvent = onEvent)
            }
        }
    }
}

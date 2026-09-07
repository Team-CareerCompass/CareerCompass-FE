package com.careercompass.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
public class GraduationDatePickerSheetTest {
    @get:Rule
    public val composeRule = createComposeRule()

    private val state =
        GraduationPickerState(
            years = listOf(2025, 2026, 2027),
            selectedYear = 2027,
            selectedMonth = 2,
        )

    @Test
    public fun selectedYearAndMonth_areMarkedSelected() {
        setSheet(state)

        composeRule.onNodeWithText("졸업 예정").assertIsDisplayed()
        composeRule.onNodeWithText("2027년").assertIsOn()
        composeRule.onNodeWithText("2월").assertIsOn()
    }

    @Test
    public fun chipsAndConfirm_emitDistinctEvents() {
        val events = mutableListOf<GraduationDatePickerEvent>()
        setSheet(state, onEvent = events::add)

        composeRule.onNodeWithText("2026년").performClick()
        composeRule.onNodeWithText("8월").performClick()
        composeRule.onNode(hasText("선택 완료") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    GraduationDatePickerEvent.YearSelected(2026),
                    GraduationDatePickerEvent.MonthSelected(8),
                    GraduationDatePickerEvent.Confirmed,
                ),
                events,
            )
        }
    }

    private fun setSheet(
        state: GraduationPickerState,
        onEvent: (GraduationDatePickerEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            CareerCompassTheme {
                GraduationDatePickerSheet(state = state, onEvent = onEvent)
            }
        }
    }
}

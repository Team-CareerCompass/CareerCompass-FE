package com.careercompass.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
public class ExperienceDeleteDialogTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun blankTitle_isRejectedByContract() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleState.copy(title = "   ")
        }
    }

    @Test
    public fun dialog_namesTheCardBeingDeleted() {
        setDialog()

        composeRule.onNodeWithText("경험을 삭제할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("「카카오 인턴」을(를) 삭제하면 되돌릴 수 없어요.").assertIsDisplayed()
    }

    @Test
    public fun confirmAndCancel_emitDistinctEvents() {
        val events = mutableListOf<ExperienceDeleteEvent>()
        setDialog(onEvent = events::add)

        composeRule.onNode(hasText("삭제") and hasClickAction()).performClick()
        composeRule.onNode(hasText("취소") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ExperienceDeleteEvent.Confirmed, ExperienceDeleteEvent.Dismissed), events)
        }
    }

    private fun setDialog(onEvent: (ExperienceDeleteEvent) -> Unit = {}) {
        composeRule.setContent {
            CareerCompassTheme {
                ExperienceDeleteDialog(state = sampleState, onEvent = onEvent)
            }
        }
    }

    private companion object {
        val sampleState = ExperienceDeleteState(experienceId = 3L, title = "카카오 인턴")
    }
}

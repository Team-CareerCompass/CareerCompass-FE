package com.careercompass.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
public class DirectInputSheetTest {
    @get:Rule
    public val composeRule = createComposeRule()

    @Test
    public fun emptyState_disablesSubmitUntilBothFieldsFilled() {
        setSheet(DirectInputState())

        composeRule.onNodeWithText("직접 입력하기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("내용 *").assertIsDisplayed()
        submitButton().assertIsNotEnabled()
    }

    @Test
    public fun filledState_enablesSubmit() {
        setSheet(DirectInputState(label = "2024 카카오 인턴 자소서", content = "지원 동기"))

        submitButton().assertIsEnabled()
    }

    @Test
    public fun fieldErrors_areRendered() {
        setSheet(DirectInputState(labelError = ProfileFieldViolation.TooLong(50), contentError = ProfileFieldViolation.Required))

        composeRule.onNodeWithText("50자 이내로 입력해 주세요").assertIsDisplayed()
        composeRule.onNodeWithText("필수 입력이에요").assertIsDisplayed()
    }

    @Test
    public fun controls_emitDistinctEvents() {
        val events = mutableListOf<DirectInputEvent>()
        setSheet(DirectInputState(label = "라벨", content = "본문"), onEvent = events::add)

        composeRule.onNodeWithContentDescription("지원서 이름 *").performTextReplacement("라벨!")
        composeRule.onNodeWithContentDescription("내용 *").performTextReplacement("본문!")
        submitButton().performClick()
        composeRule.onNode(hasText("취소") and hasClickAction()).performClick()

        // stateless 필드는 호스트가 값을 되돌리지 않으므로 입력 이벤트는 첫 발생만 본다.
        composeRule.runOnIdle {
            assertEquals(DirectInputEvent.LabelChanged("라벨!"), events.filterIsInstance<DirectInputEvent.LabelChanged>().first())
            assertEquals(DirectInputEvent.ContentChanged("본문!"), events.filterIsInstance<DirectInputEvent.ContentChanged>().first())
            assertEquals(
                listOf(DirectInputEvent.Submitted, DirectInputEvent.Dismissed),
                events.filterNot { it is DirectInputEvent.LabelChanged || it is DirectInputEvent.ContentChanged },
            )
        }
    }

    private fun submitButton() = composeRule.onNode(hasText("업로드") and hasClickAction())

    private fun setSheet(
        state: DirectInputState,
        onEvent: (DirectInputEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            CareerCompassTheme {
                DirectInputSheet(state = state, onEvent = onEvent)
            }
        }
    }
}

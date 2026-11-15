package com.careercompass.feature.profile.presentation.experience

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceDeleteState
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExperienceListDeleteDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `삭제를 기다리는 동안 확인과 취소를 잠그고 응답 뒤 다시 연다`() {
        val isDeleting = mutableStateOf(false)
        val events = mutableListOf<ExperienceDeleteEvent>()
        composeRule.setContent {
            CareerCompassTheme {
                ExperienceListDeleteDialog(
                    state = ExperienceDeleteState(experienceId = 1L, title = "프로젝트 카드"),
                    isDeleting = isDeleting.value,
                    onEvent = {
                        events += it
                        if (it == ExperienceDeleteEvent.Confirmed) isDeleting.value = true
                    },
                )
            }
        }
        composeRule.onNodeWithText("삭제").assertIsEnabled().performClick()

        composeRule.onNodeWithText("삭제").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("취소").assertIsNotEnabled().performClick()
        assertEquals(listOf(ExperienceDeleteEvent.Confirmed), events)

        composeRule.runOnIdle { isDeleting.value = false }
        composeRule.onNodeWithText("삭제").assertIsEnabled()
        composeRule.onNodeWithText("취소").assertIsEnabled().performClick()
        assertEquals(listOf(ExperienceDeleteEvent.Confirmed, ExperienceDeleteEvent.Dismissed), events)
    }
}

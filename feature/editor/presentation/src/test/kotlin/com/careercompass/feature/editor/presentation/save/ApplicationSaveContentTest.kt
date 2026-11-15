package com.careercompass.feature.editor.presentation.save

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.presentation.editor.ApplicationEditorContent
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
class ApplicationSaveContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `큰 글꼴 초과 확인 창에서도 그대로 저장과 계속 편집을 선택한다`() {
        val intents = mutableListOf<ApplicationProgressIntent>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CareerCompassTheme { ApplicationSaveOverflowDialog(state(), { intents += it }) }
            }
        }
        composeRule.onNodeWithText("그대로 저장").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("계속 편집").assertIsDisplayed().performClick()
        assertEquals(listOf(ApplicationProgressIntent.ConfirmOverLimitSave, ApplicationProgressIntent.DismissOverLimitSave), intents)
    }

    @Test fun `최종 저장 중에는 본문과 저장 버튼을 잠근다`() {
        composeRule.setContent {
            CareerCompassTheme {
                ApplicationEditorContent(state().copy(save = ApplicationSaveState(isSaving = true, isFinalSaving = true)), {}, {})
            }
        }
        composeRule.onNodeWithText("답변").assertIsNotEnabled()
        composeRule.onNodeWithText("최종 저장").assertIsNotEnabled()
        composeRule.onNodeWithText("진행 화면으로").assertIsNotEnabled()
    }

    @Test fun `연속 저장 실패는 중단 이유와 재시도 동작을 보여 준다`() {
        val intents = mutableListOf<ApplicationProgressIntent>()
        composeRule.setContent {
            CareerCompassTheme {
                ApplicationSaveStatus(ApplicationSaveState(failure = FailureKind.NoConnection, autoSaveStopped = true), { intents += it })
            }
        }
        composeRule.onNodeWithText("저장하지 못했어요. 입력한 내용은 유지했어요.").assertIsDisplayed()
        composeRule.onNodeWithText("저장이 3회 연속 실패해 자동 저장을 멈췄어요. 문제를 해결한 뒤 다시 저장해 주세요.").assertIsDisplayed()
        composeRule.onNodeWithText("다시 저장").performClick()
        assertEquals(listOf(ApplicationProgressIntent.RetrySave), intents)
    }

    private fun state() =
        ApplicationProgressUiState(
            draft =
                ApplicationDraft(
                    42,
                    ApplicationStatus.Ready,
                    listOf(ApplicationItem(1, 1, "지원 동기", 5, ApplicationItemStatus.Done, "여섯 글자다")),
                ),
        )
}

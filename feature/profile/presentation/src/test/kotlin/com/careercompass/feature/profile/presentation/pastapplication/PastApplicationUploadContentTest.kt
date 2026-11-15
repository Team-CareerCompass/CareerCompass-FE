package com.careercompass.feature.profile.presentation.pastapplication

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PastApplicationUploadContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `상단과 빈 목록의 추가 버튼은 같은 두 선택지를 제공한다`() {
        val state = mutableStateOf(PastApplicationListUiState())
        val events = mutableListOf<PastApplicationListEvent>()
        composeRule.setContent {
            CareerCompassTheme {
                PastApplicationListContent(state.value, onEvent = {
                    events += it
                    state.value = state.value.copy(showAddOptions = it == PastApplicationListEvent.AddClicked)
                })
            }
        }
        composeRule.onNode(hasText("지원서 추가") and hasClickAction()).performClick()
        composeRule.onNodeWithText("파일 올리기").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("첫 지원서 올리기").performClick()
        composeRule.onNodeWithText("직접 쓰기").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PastApplicationListEvent.AddClicked,
                    PastApplicationListEvent.FileUploadClicked,
                    PastApplicationListEvent.AddClicked,
                    PastApplicationListEvent.DirectInputClicked,
                ),
                events,
            )
        }
    }

    @Test fun `두 배 글꼴에서도 파일 선택과 직접 쓰기에 접근할 수 있다`() {
        val events = mutableListOf<PastApplicationListEvent>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                CareerCompassTheme { PastApplicationAddOptionsContent(onEvent = { events += it }) }
            }
        }
        composeRule
            .onNodeWithText("파일 올리기")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithText("직접 쓰기")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(2, events.size) }
    }

    @Test fun `연결 실패 이유는 스크롤 시트 안에서도 보이고 재시도를 제공한다`() {
        composeRule.setContent {
            CareerCompassTheme {
                PastApplicationFileUploadContent(
                    PastApplicationFileUploadState(label = "지원서", failure = FailureKind.NoConnection),
                    onEvent = {},
                )
            }
        }
        composeRule.onNodeWithText("연결할 수 없어요", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("인터넷 연결을 확인하고 다시 시도해 주세요", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performScrollTo().assertIsDisplayed()
    }

    @Test fun `업로드 중에는 입력과 제출과 취소를 잠근다`() {
        val events = mutableListOf<PastApplicationFileUploadIntent>()
        composeRule.setContent {
            CareerCompassTheme {
                PastApplicationFileUploadContent(
                    PastApplicationFileUploadState(
                        file = UploadFile("지원서.pdf", 1) { byteArrayOf(1).inputStream() },
                        label = "지원서",
                        isSubmitting = true,
                    ),
                    onEvent = { events += it },
                )
            }
        }
        composeRule.onNodeWithText("지원서").assertIsNotEnabled()
        composeRule
            .onNodeWithText("업로드 중…")
            .performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        composeRule
            .onNodeWithText("취소")
            .performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(events.isEmpty()) }
    }
}

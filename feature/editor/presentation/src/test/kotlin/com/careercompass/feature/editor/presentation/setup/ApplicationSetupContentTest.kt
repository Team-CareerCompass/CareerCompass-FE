package com.careercompass.feature.editor.presentation.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationTone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationSetupContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `문항 번호와 질문과 글자 수 제한을 그린다`() {
        composeRule.setContent(state(items = listOf(itemDraft(1, "지원 동기를 작성해 주세요", 500))))

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onNodeWithText("지원 동기를 작성해 주세요").assertIsDisplayed()
        composeRule.onNodeWithText("500자").assertIsDisplayed()
    }

    /** 400 이나 600 을 대신 보여 주면 공고가 정한 값과 구분되지 않는다. */
    @Test
    fun `제한이 없는 문항은 제한 없음이라고 쓴다`() {
        composeRule.setContent(state(items = listOf(itemDraft(1, "문항", null))))

        composeRule.onNodeWithText("제한 없음").assertIsDisplayed()
    }

    @Test
    fun `제한 없는 문항이 있으면 기본 길이를 미리 알린다`() {
        composeRule.setContent(state(items = listOf(itemDraft(1, "문항", null))))

        composeRule.scrollTo(hasText(DEFAULT_LENGTH_NOTICE))
        composeRule.onNodeWithText(DEFAULT_LENGTH_NOTICE).assertIsDisplayed()
    }

    /** 없을 때 띄우면 사실이 아닌 안내가 된다. */
    @Test
    fun `모든 문항에 제한이 있으면 기본 길이 안내를 띄우지 않는다`() {
        composeRule.setContent(state(items = listOf(itemDraft(1, "문항", 500))))

        composeRule.onAllNodesWithText(DEFAULT_LENGTH_NOTICE).assertCountEquals(0)
    }

    @Test
    fun `인식된 문항이 없으면 직접 쓰라고 안내한다`() {
        composeRule.setContent(state(items = emptyList(), recognized = emptyList()))

        composeRule.onNodeWithText("공고에서 항목을 찾지 못했어요").assertIsDisplayed()
        composeRule.onNodeWithText("초안 작성 시작").assertIsNotEnabled()
    }

    @Test
    fun `문체를 고르면 그 값을 올려 보낸다`() {
        val events = mutableListOf<ApplicationSetupEvent>()
        composeRule.setContent(state(), onEvent = { events += it })

        composeRule.scrollTo(hasText("친근체"))
        composeRule.onNodeWithText("친근체").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ApplicationSetupEvent.ToneSelected(ApplicationTone.Casual)), events)
        }
    }

    @Test
    fun `고치기와 삭제는 그 문항 번호를 올려 보낸다`() {
        val events = mutableListOf<ApplicationSetupEvent>()
        composeRule.setContent(state(items = listOf(itemDraft(1, "문항", 500))), onEvent = { events += it })

        composeRule.onNode(hasText("고치기") and hasClickAction()).performClick()
        composeRule.onNode(hasText("삭제") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(ApplicationSetupEvent.ItemEditClicked(1), ApplicationSetupEvent.ItemDeleteClicked(1)),
                events,
            )
        }
    }

    @Test
    fun `공고를 못 읽으면 목록 자리를 덮는다`() {
        composeRule.setContent(state(items = emptyList(), postingTitle = "", loadFailure = FailureKind.NoConnection))

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
    }

    @Test
    fun `생성 중에는 시작 버튼이 잠기고 문구가 바뀐다`() {
        composeRule.setContent(state(isCreating = true))

        composeRule.onNodeWithText("초안을 준비하고 있어요…").assertIsNotEnabled()
    }

    private fun state(
        items: List<com.careercompass.feature.editor.domain.model.ApplicationItemDraft> =
            listOf(itemDraft(1, "지원 동기를 작성해 주세요", 500), itemDraft(2, "강점과 약점", 400)),
        recognized: List<com.careercompass.feature.editor.domain.model.ApplicationItemDraft> = items,
        postingTitle: String = "카카오 SW 인턴십",
        loadFailure: FailureKind? = null,
        isCreating: Boolean = false,
    ) = ApplicationSetupUiState(
        postingTitle = postingTitle,
        items = items,
        recognizedItems = recognized,
        loadFailure = loadFailure,
        isCreating = isCreating,
    )

    private fun ComposeContentTestRule.scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private companion object {
        const val DEFAULT_LENGTH_NOTICE = "글자 수 제한이 없는 항목은 400~600자로 작성해 드려요."
    }

    private fun ComposeContentTestRule.setContent(
        state: ApplicationSetupUiState,
        onEvent: (ApplicationSetupEvent) -> Unit = {},
    ) {
        setContent {
            CareerCompassTheme {
                ApplicationSetupContent(state = state, onEvent = onEvent)
            }
        }
    }
}

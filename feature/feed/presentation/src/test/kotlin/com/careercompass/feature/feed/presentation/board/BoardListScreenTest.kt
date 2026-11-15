package com.careercompass.feature.feed.presentation.board

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeBoardRepository
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.domain.usecase.DeleteBoardUseCase
import com.careercompass.feature.feed.domain.usecase.GetBoardsUseCase
import com.careercompass.feature.feed.domain.usecase.RetryBoardUseCase
import com.careercompass.feature.feed.domain.usecase.ToggleBoardActiveUseCase
import com.careercompass.feature.feed.domain.usecase.UpdateBoardUseCase
import com.careercompass.feature.feed.presentation.FIXED_CLOCK
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.board
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.careercompass.core.model.board.BoardStatus as DomainBoardStatus

/**
 * 목록에서 시킨 일이 실패했을 때 **어떤 문장이 뜨는가**를 본다.
 *
 * ViewModel 은 실패 표의 행까지만 말할 수 있고(`BoardListMessage.Failed`), 그 값이 문장이 되는 자리는
 * Screen 의 `toLabel` 이다. 사유가 다시 「재시도를 요청하지 못했어요」 하나로 접히는 회귀는 여기서만 잡힌다(#360).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardListScreenTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository =
        FakeBoardRepository(
            initial = listOf(board(id = 2, isActive = false, status = DomainBoardStatus.Failed, failCount = 3)),
        )

    @Test
    fun `수집을 막아 둔 사이트의 재시도 실패는 그 사유를 말한다`() {
        repository.onRetry = { Result.failure(CoreDataFailure.BoardBlocked("BOARD_BLOCKED", RuntimeException())) }
        setListContent()

        composeRule.onNodeWithContentDescription("게시판 2 재시도").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("자동 수집이 허용되지 않는 사이트예요", substring = true).assertIsDisplayed()
    }

    /**
     * 사유를 확인하지 못한 실패는 표의 게시판 행이 아니라 무엇을 하다 실패했는지를 말한다.
     *
     * 표의 `Unexpected`×게시판 행은 「게시판을 불러오지 못했어요」라 조회를 가리키는 문장이다. 목록에서 시킨
     * 일에 그대로 붙이면 사용자가 무엇이 실패했는지 잘못 읽는다.
     */
    @Test
    fun `사유 없는 토글 실패는 수집 설정 문구로 알린다`() {
        repository.onUpdate = { _, _ -> Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
        setListContent()

        composeRule.onNodeWithContentDescription("게시판 2 수집 활성화").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("수집 설정을 바꾸지 못했어요").assertIsDisplayed()
    }

    private fun setListContent() {
        val viewModel = viewModel()
        composeRule.setContent {
            CareerCompassTheme {
                BoardListScreen(
                    onBackClick = {},
                    onAddBoardClick = {},
                    onSessionEnded = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun viewModel(): BoardListViewModel =
        BoardListViewModel(
            getBoards = GetBoardsUseCase(repository),
            toggleBoardActive = ToggleBoardActiveUseCase(repository),
            retryBoard = RetryBoardUseCase(repository),
            deleteBoard = DeleteBoardUseCase(repository),
            updateBoard = UpdateBoardUseCase(repository),
            errorReporter = RecordingErrorReporter(),
            clock = FIXED_CLOCK,
            savedStateHandle = SavedStateHandle(),
        )
}

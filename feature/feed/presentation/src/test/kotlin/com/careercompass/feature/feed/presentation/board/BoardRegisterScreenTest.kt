package com.careercompass.feature.feed.presentation.board

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeBoardRepository
import com.careercompass.core.model.board.Board
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.domain.usecase.DetectBoardUseCase
import com.careercompass.feature.feed.domain.usecase.RegisterBoardUseCase
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.board
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 등록 제출 중 이탈 차단 — **시스템 뒤로가기**까지 막혔는지 본다.
 *
 * 상단 화살표만 막으면 반쪽이다: 사용자는 늘 쓰던 손짓(뒤로가기·가장자리 제스처)으로 나가고 진행 중이던
 * 요청은 그대로 끊긴다(#146). ViewModel 단위 테스트는 「BackClicked 를 어떻게 판정하는가」까지만 볼 수 있어,
 * 그 판정이 시스템 뒤로가기에도 닿았는지는 여기서만 드러난다.
 *
 * 호스트를 실제 [ComponentActivity] 로 두고 그 액티비티의 디스패처로 누른다. 테스트가 만든 디스패처를
 * 컴포지션 로컬로 꽂는 방법은 쓰지 않는다 — `BackHandler` 는 호스트가 제공하는 내비게이션 이벤트
 * 디스패처를 먼저 보므로, 꽂아 준 디스패처는 아무도 안 쓴 채 테스트만 통과한다(실측으로 확인).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardRegisterScreenTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val gate = CompletableDeferred<Result<Board>>()
    private val repository = FakeBoardRepository().apply { onRegister = { gate.await() } }

    /** 화면이 먹지 않은 시스템 뒤로가기 — 실제 앱에서는 NavHost 가 받아 이 화면을 닫는 자리다. */
    private var fallbackBackCount = 0

    /** 화면이 스스로 요청한 이탈([BoardRegisterScreen] 의 `onBackClick`). */
    private var navigatedBackCount = 0

    @Test
    fun `제출 중에는 시스템 뒤로가기가 화면을 벗어나지 못한다`() {
        val viewModel = submittingScreen()

        pressSystemBack()

        assertEquals(0, fallbackBackCount)
        assertEquals(0, navigatedBackCount)
        // 화면이 그대로이므로 요청도 그대로다 — 조용히 끊기던 자리가 여기였다.
        assertTrue(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `제출 중에는 상단 화살표도 화면을 벗어나지 못한다`() {
        submittingScreen()

        composeRule.onNodeWithContentDescription("뒤로가기").performClick()

        assertEquals(0, navigatedBackCount)
    }

    @Test
    fun `제출 중이 아니면 시스템 뒤로가기는 평소대로 지나간다`() {
        setEntryContent(viewModel().readyToRegister())

        pressSystemBack()

        // BackHandler 가 꺼져 있어야 뒤로가기가 내비게이션까지 내려간다.
        assertEquals(1, fallbackBackCount)
    }

    @Test
    fun `제출이 끝나면 갇히지 않고 목록으로 돌아간다`() {
        submittingScreen()

        gate.complete(Result.success(board(id = 1L)))
        composeRule.waitForIdle()

        assertEquals(1, navigatedBackCount)
    }

    /**
     * 점검 안내가 **스낵바 문구로도** 갈리는지 본다.
     *
     * ViewModel 은 `BoardRegisterMessage.Maintenance` 까지만 말할 수 있다. 그 값이 어떤 문장이 되는지는
     * Screen 의 `toLabel` 이 정하므로, 「게시판을 등록하지 못했어요」로 되돌아가는 회귀는 여기서만 잡힌다.
     */
    @Test
    fun `등록 중 서버 점검은 점검 문구로 알린다`() {
        repository.onRegister = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException())) }
        val viewModel = viewModel().readyToRegister()
        setEntryContent(viewModel)

        viewModel.onEvent(BoardRegisterEvent.RegisterClicked)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("서비스가 잠시 점검 중이에요").assertIsDisplayed()
    }

    /**
     * 사유가 분명한 등록 실패는 그 사유를 말한다(#360).
     *
     * 전에는 `else` 로 떨어져 「게시판을 등록하지 못했어요. 잠시 후 다시 시도해 주세요」가 됐다.
     * 수집을 막아 둔 사이트를 등록하려던 사용자에게 잠시 후 같은 주소로 다시 해 보라고 말하는 문장이다.
     */
    @Test
    fun `등록 중 수집 차단은 차단 문구로 알린다`() {
        repository.onRegister = { Result.failure(CoreDataFailure.BoardBlocked("BOARD_BLOCKED", RuntimeException())) }
        val viewModel = viewModel().readyToRegister()
        setEntryContent(viewModel)

        viewModel.onEvent(BoardRegisterEvent.RegisterClicked)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("자동 수집이 허용되지 않는 사이트예요", substring = true).assertIsDisplayed()
    }

    /** 등록 요청이 응답을 기다리는 중인 화면. */
    private fun submittingScreen(): BoardRegisterViewModel {
        val viewModel = viewModel().readyToRegister()
        setEntryContent(viewModel)
        viewModel.onEvent(BoardRegisterEvent.RegisterClicked)
        composeRule.waitForIdle()
        return viewModel
    }

    private fun setEntryContent(viewModel: BoardRegisterViewModel) {
        // 화면보다 **먼저** 등록해야 화면의 BackHandler 가 뒤로가기를 먼저 받는다(나중에 등록된 쪽이 우선).
        composeRule.activity.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    fallbackBackCount++
                }
            },
        )
        composeRule.setContent {
            CareerCompassTheme {
                BoardRegisterScreen(
                    onBackClick = { navigatedBackCount++ },
                    onSessionEnded = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun viewModel(): BoardRegisterViewModel =
        BoardRegisterViewModel(
            detectBoard = DetectBoardUseCase(repository),
            registerBoard = RegisterBoardUseCase(repository),
            errorReporter = RecordingErrorReporter(),
            savedStateHandle = SavedStateHandle(),
        )

    private fun BoardRegisterViewModel.readyToRegister(): BoardRegisterViewModel =
        apply {
            onEvent(BoardRegisterEvent.UrlChanged("konkuk.ac.kr/board/notice"))
            onEvent(BoardRegisterEvent.DetectClicked)
            onEvent(BoardRegisterEvent.NameChanged("건국대 공지"))
            onEvent(BoardRegisterEvent.TypeSelected(BoardType.Employment))
            check(state.value.isRegisterEnabled)
        }
}

package com.careercompass.feature.feed.presentation.board

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeBoardRepository
import com.careercompass.core.model.board.BoardUpdate
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.feed.domain.usecase.DeleteBoardUseCase
import com.careercompass.feature.feed.domain.usecase.GetBoardsUseCase
import com.careercompass.feature.feed.domain.usecase.RetryBoardUseCase
import com.careercompass.feature.feed.domain.usecase.ToggleBoardActiveUseCase
import com.careercompass.feature.feed.domain.usecase.UpdateBoardUseCase
import com.careercompass.feature.feed.presentation.FIXED_CLOCK
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.board
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.util.toUiBoardStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException
import com.careercompass.core.model.board.BoardStatus as DomainBoardStatus
import com.careercompass.core.model.board.BoardType as DomainBoardType

@OptIn(ExperimentalCoroutinesApi::class)
class BoardListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reporter = RecordingErrorReporter()

    private fun viewModel(repository: FakeBoardRepository): BoardListViewModel =
        BoardListViewModel(
            getBoards = GetBoardsUseCase(repository),
            toggleBoardActive = ToggleBoardActiveUseCase(repository),
            retryBoard = RetryBoardUseCase(repository),
            deleteBoard = DeleteBoardUseCase(repository),
            updateBoard = UpdateBoardUseCase(repository),
            errorReporter = reporter,
            clock = FIXED_CLOCK,
            // 수정 시트 초안의 복원 계약은 BoardEditInputRestoreTest 가 본다 — 여기서는 빈 저장소로 둔다.
            savedStateHandle = SavedStateHandle(),
        )

    private fun repository() =
        FakeBoardRepository(
            initial =
                listOf(
                    board(id = 1),
                    board(id = 2, isActive = false, status = DomainBoardStatus.Failed, failCount = 3),
                ),
        )

    @Test
    fun `목록을 읽어 온다`() {
        val state = viewModel(repository()).state.value

        assertEquals(listOf(1L, 2L), state.boards.map { it.id })
    }

    @Test
    fun `수집 토글은 isActive 만 담아 보내고 서버 값으로 확정한다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardToggled("1"))

        assertFalse(
            viewModel.state.value.boards
                .first { it.id == 1L }
                .isActive,
        )
        assertEquals(listOf(1L to BoardUpdate(isActive = false)), repository.updates.toList())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `토글은 먼저 뒤집고 실패하면 되돌린다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                repository().apply {
                    onUpdate = { _, _ ->
                        gate.await()
                        Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException()))
                    }
                }
            val viewModel = viewModel(repository)

            viewModel.onEvent(BoardListEvent.BoardToggled("1"))
            val optimistic =
                viewModel.state.value.boards
                    .first { it.id == 1L }
            assertFalse(optimistic.isActive)
            assertEquals(DomainBoardStatus.Paused, optimistic.status)

            gate.complete(Unit)

            val reverted =
                viewModel.state.value.boards
                    .first { it.id == 1L }
            assertTrue(reverted.isActive)
            assertEquals(DomainBoardStatus.Active, reverted.status)
            assertEquals(BoardListMessage.ToggleFailed, viewModel.state.value.message)
            // 일시적 전송 실패는 (원인, 단계) 조합의 세션 첫 건만 표본으로 남는다.
            assertEquals(listOf("board_toggle"), reporter.stages)
        }

    /** 토글 실패의 되돌리기는 그 사이 수정 시트가 저장한 이름을 지우지 않는다(#235) — 되돌리는 것은 토글이 건드린 두 필드뿐이다. */
    @Test
    fun `토글 실패의 되돌리기는 그 사이 저장된 수정을 지우지 않는다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = repository()
            repository.onUpdate = { id, update ->
                if (update.isActive != null) {
                    gate.await()
                    Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException()))
                } else {
                    Result.success(repository.boards.first { it.id == id }.copy(name = checkNotNull(update.name)))
                }
            }
            val viewModel = viewModel(repository)

            viewModel.onEvent(BoardListEvent.BoardToggled("1"))
            viewModel.onEvent(BoardListEvent.BoardSelected("1"))
            viewModel.onEditEvent(BoardEditEvent.NameChanged("새 이름"))
            viewModel.onEditEvent(BoardEditEvent.SaveClicked)
            assertEquals(
                "새 이름",
                viewModel.state.value.boards
                    .first { it.id == 1L }
                    .name,
            )

            gate.complete(Unit)

            val board =
                viewModel.state.value.boards
                    .first { it.id == 1L }
            assertEquals("새 이름", board.name)
            assertTrue(board.isActive)
            assertEquals(DomainBoardStatus.Active, board.status)
            assertEquals(BoardListMessage.ToggleFailed, viewModel.state.value.message)
        }

    @Test
    fun `재시도는 위임하고 성공하면 실패 상태를 지운다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.RetryClicked("2"))

        val retried =
            viewModel.state.value.boards
                .first { it.id == 2L }
        assertEquals(listOf(2L), repository.retries.toList())
        assertEquals(DomainBoardStatus.Active, retried.status)
        assertEquals(0, retried.failCount)
        assertEquals(BoardListMessage.RetryRequested, viewModel.state.value.message)
        // 서버가 끈 게시판을 되살리는 길 — 낙관적 갱신이 다시 켜 주므로 중단 안내가 사라진다.
        assertTrue(retried.isActive)
        assertEquals(BoardStatus.Active, retried.toUiBoardStatus())
    }

    @Test
    fun `서버가 끈 게시판을 토글로 켜면 중단이 아니라 실패 중으로 돌아간다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardToggled("2"))

        val toggled =
            viewModel.state.value.boards
                .first { it.id == 2L }
        assertEquals(listOf(2L to BoardUpdate(isActive = true)), repository.updates.toList())
        assertTrue(toggled.isActive)
        // 토글은 수집 실패를 지우지 않는다 — 재시도와 달리 다음 수집 주기를 기다린다.
        assertEquals(DomainBoardStatus.Failed, toggled.status)
        assertEquals(3, toggled.failCount)
        assertEquals(BoardStatus.Failing, toggled.toUiBoardStatus())
    }

    @Test
    fun `재시도 실패는 스낵바로 알린다`() {
        val repository =
            repository().apply {
                onRetry =
                    { Result.failure(CoreDataFailure.BoardBlocked("BOARD_BLOCKED", RuntimeException())) }
            }
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.RetryClicked("2"))

        assertEquals(BoardListMessage.RetryFailed, viewModel.state.value.message)
        assertEquals(
            DomainBoardStatus.Failed,
            viewModel.state.value.boards
                .first { it.id == 2L }
                .status,
        )
    }

    @Test
    fun `삭제는 확인 다이얼로그를 거쳐야 보낸다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.DeleteClicked("1"))
        assertEquals(
            1L,
            viewModel.state.value.pendingDeletion
                ?.id,
        )
        viewModel.dismissDelete()
        assertNull(viewModel.state.value.pendingDeletion)
        assertEquals(2, repository.boards.size)

        viewModel.onEvent(BoardListEvent.DeleteClicked("1"))
        viewModel.confirmDelete()

        assertNull(viewModel.state.value.pendingDeletion)
        assertEquals(
            listOf(2L),
            viewModel.state.value.boards
                .map { it.id },
        )
        assertEquals(listOf(2L), repository.boards.map { it.id })
    }

    @Test
    fun `삭제 실패는 목록을 유지하고 스낵바로 알린다`() {
        val repository =
            repository().apply {
                onDelete =
                    { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
            }
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.DeleteClicked("1"))
        viewModel.confirmDelete()

        assertEquals(
            listOf(1L, 2L),
            viewModel.state.value.boards
                .map { it.id },
        )
        assertEquals(BoardListMessage.DeleteFailed, viewModel.state.value.message)
    }

    @Test
    fun `추가·뒤로가기는 단발 신호로 올라간다`() {
        val viewModel = viewModel(repository())

        viewModel.onEvent(BoardListEvent.AddBoardClicked)
        assertEquals(BoardListDestination.Register, viewModel.state.value.pendingNavigation)
        viewModel.onNavigationConsumed()
        viewModel.onEvent(BoardListEvent.BackClicked)
        assertEquals(BoardListDestination.Back, viewModel.state.value.pendingNavigation)
    }

    @Test
    fun `목록 조회 실패는 사유를 구분하고 401 은 세션을 끝낸다`() {
        val network =
            FakeBoardRepository.strict().apply {
                onGetBoards =
                    { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
            }
        assertEquals(BoardListLoadState.Failed(FeedFailureReason.NetworkUnavailable), viewModel(network).state.value.loadState)

        val maintenance =
            FakeBoardRepository.strict().apply {
                onGetBoards = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException())) }
            }
        assertEquals(BoardListLoadState.Failed(FeedFailureReason.Maintenance), viewModel(maintenance).state.value.loadState)

        val unauthorized =
            FakeBoardRepository.strict().apply {
                onGetBoards = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())) }
            }
        val state = viewModel(unauthorized).state.value
        assertEquals(BoardListLoadState.Failed(FeedFailureReason.Generic(FailureKind.AuthExpired)), state.loadState)
        assertTrue(state.sessionEnded)
    }

    @Test
    fun `재진입 새로고침은 새 목록을 반영하고 로딩 상태로 되돌리지 않는다`() {
        val repository = repository()
        val viewModel = viewModel(repository)
        repository.boards += board(id = 3)

        viewModel.refresh()

        assertEquals(
            listOf(1L, 2L, 3L),
            viewModel.state.value.boards
                .map { it.id },
        )
    }

    @Test
    fun `카드를 누르면 원본 값으로 수정 시트가 열린다`() {
        val viewModel = viewModel(repository())

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))

        val draft = checkNotNull(viewModel.state.value.editDraft)
        assertEquals(1L, draft.board.id)
        assertEquals("게시판 1", draft.name)
        assertEquals(BoardType.Scholarship, draft.type)
        assertEquals(BoardCollectCycle.Daily, draft.cycle)
        assertFalse(draft.isSaving)

        viewModel.onEvent(BoardListEvent.BoardSelected("없는 id"))
        assertEquals(draft, viewModel.state.value.editDraft)
    }

    @Test
    fun `변경 없이 저장하면 요청 없이 닫힌다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.NameChanged("  게시판 1  "))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        assertNull(viewModel.state.value.editDraft)
        assertTrue(repository.updates.isEmpty())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `이름만 바꾸면 이름만 담아 보내고 목록을 응답으로 교체한다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.NameChanged(" 새 이름 "))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        assertEquals(listOf(1L to BoardUpdate(name = "새 이름")), repository.updates.toList())
        assertEquals(
            "새 이름",
            viewModel.state.value.boards
                .first { it.id == 1L }
                .name,
        )
        assertNull(viewModel.state.value.editDraft)
        assertEquals(BoardListMessage.Updated, viewModel.state.value.message)
    }

    @Test
    fun `유형과 주기를 바꾸면 그 필드만 담아 보낸다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.TypeSelected(BoardType.Employment))
        viewModel.onEditEvent(BoardEditEvent.CycleSelected(BoardCollectCycle.Weekly))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        assertEquals(
            listOf(1L to BoardUpdate(type = DomainBoardType.Recruit, cycleHours = 168)),
            repository.updates.toList(),
        )
        val updated =
            viewModel.state.value.boards
                .first { it.id == 1L }
        assertEquals(DomainBoardType.Recruit, updated.type)
        assertEquals(168, updated.cycleHours)
    }

    @Test
    fun `빈 이름은 저장하지 않는다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.NameChanged("   "))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        assertTrue(repository.updates.isEmpty())
        assertEquals("   ", checkNotNull(viewModel.state.value.editDraft).name)
    }

    @Test
    fun `저장 실패는 시트를 유지하고 스낵바로 알린다`() {
        val repository =
            repository().apply {
                onUpdate = { _, _ -> Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
            }
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.NameChanged("새 이름"))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        val draft = checkNotNull(viewModel.state.value.editDraft)
        assertEquals("새 이름", draft.name)
        assertFalse(draft.isSaving)
        assertEquals(BoardListMessage.UpdateFailed, viewModel.state.value.message)
        assertEquals(listOf("board_update"), reporter.stages)
        assertEquals(
            "게시판 1",
            viewModel.state.value.boards
                .first { it.id == 1L }
                .name,
        )
        assertFalse(viewModel.state.value.sessionEnded)
    }

    @Test
    fun `저장 중 401 은 세션을 끝낸다`() {
        val repository =
            repository().apply {
                onUpdate = { _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())) }
            }
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.CycleSelected(BoardCollectCycle.Weekly))
        viewModel.onEditEvent(BoardEditEvent.SaveClicked)

        assertTrue(viewModel.state.value.sessionEnded)
        assertEquals(BoardListMessage.UpdateFailed, viewModel.state.value.message)
    }

    @Test
    fun `저장 중에는 닫기를 무시하고 응답이 오면 닫힌다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                repository().apply {
                    onUpdate = { id, update ->
                        gate.await()
                        Result.success(board(id = id).copy(name = checkNotNull(update.name)))
                    }
                }
            val viewModel = viewModel(repository)

            viewModel.onEvent(BoardListEvent.BoardSelected("1"))
            viewModel.onEditEvent(BoardEditEvent.NameChanged("새 이름"))
            viewModel.onEditEvent(BoardEditEvent.SaveClicked)
            assertTrue(checkNotNull(viewModel.state.value.editDraft).isSaving)

            viewModel.onEditEvent(BoardEditEvent.DismissClicked)
            viewModel.onEditEvent(BoardEditEvent.SaveClicked)
            assertTrue(checkNotNull(viewModel.state.value.editDraft).isSaving)

            gate.complete(Unit)

            assertNull(viewModel.state.value.editDraft)
            assertEquals(1, repository.updates.size)
            assertEquals(BoardListMessage.Updated, viewModel.state.value.message)
        }

    @Test
    fun `닫기는 편집 내용을 버리고 요청을 보내지 않는다`() {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onEvent(BoardListEvent.BoardSelected("1"))
        viewModel.onEditEvent(BoardEditEvent.NameChanged("새 이름"))
        viewModel.onEditEvent(BoardEditEvent.DismissClicked)

        assertNull(viewModel.state.value.editDraft)
        assertTrue(repository.updates.isEmpty())
        assertEquals(
            "게시판 1",
            viewModel.state.value.boards
                .first { it.id == 1L }
                .name,
        )
    }
}

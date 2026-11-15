package com.careercompass.feature.feed.presentation.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.board.Board
import com.careercompass.core.model.board.BoardUpdate
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.feed.domain.usecase.DeleteBoardUseCase
import com.careercompass.feature.feed.domain.usecase.GetBoardsUseCase
import com.careercompass.feature.feed.domain.usecase.RetryBoardUseCase
import com.careercompass.feature.feed.domain.usecase.ToggleBoardActiveUseCase
import com.careercompass.feature.feed.domain.usecase.UpdateBoardUseCase
import com.careercompass.feature.feed.presentation.reporting.FeedFailureStage
import com.careercompass.feature.feed.presentation.reporting.recordFeedFailure
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.toFeedFailureReason
import com.careercompass.feature.feed.presentation.shared.util.toCollectCycle
import com.careercompass.feature.feed.presentation.shared.util.toDomainBoardType
import com.careercompass.feature.feed.presentation.shared.util.toUiBoardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import com.careercompass.core.model.board.BoardStatus as DomainBoardStatus

public sealed interface BoardListLoadState {
    public data object Loading : BoardListLoadState

    public data class Loaded(
        val boards: List<Board>,
    ) : BoardListLoadState

    public data class Failed(
        val reason: FeedFailureReason,
    ) : BoardListLoadState
}

public sealed interface BoardListDestination {
    public data object Back : BoardListDestination

    public data object Register : BoardListDestination
}

/** 목록에서 사용자가 시킨 일. 실패 안내가 무엇을 하다 실패했는지를 말할 때 쓴다. */
public enum class BoardListAction {
    Toggle,
    Retry,
    Delete,
    Update,
}

public sealed interface BoardListMessage {
    /** 재시도 요청이 서버에 닿았다. */
    public data object RetryRequested : BoardListMessage

    public data object Updated : BoardListMessage

    /**
     * 시킨 일이 실패했다. 실패 표(#204)의 어느 행이었는지([kind])를 그대로 들고 간다.
     *
     * 전에는 네 갈래가 각자 고정 문구 하나였다. `BOARD_BLOCKED`·`RATE_LIMITED`·`INVALID_INPUT` 이 모두
     * 「재시도를 요청하지 못했어요」로 접혔고, 사용자는 사이트가 수집을 막아 둔 것을 앱이 잠깐 실패한 것으로
     * 읽고 같은 버튼을 되풀이해 눌렀다(#360). 문구를 여기서 짓지 않고 갈래만 실어 보내면, 표에 행이 있는
     * 실패는 표의 제 행으로 나간다. 무엇을 하다 실패했는지는 [action] 이 말한다.
     */
    public data class Failed(
        val action: BoardListAction,
        val kind: FailureKind,
    ) : BoardListMessage
}

/**
 * 수정 시트가 편집 중인 값 — 「저장」 전까지 [board] 에 반영되지 않는다.
 *
 * URL 은 서버가 감지 결과와 묶어 두므로 바꿀 수 없다. [toUpdate] 는 원본과 다른 필드만 담아
 * `PATCH /boards/{id}` 가 바뀐 것만 실어 나가게 한다.
 */
public data class BoardEditDraft(
    val board: Board,
    val name: String,
    val type: BoardType,
    val cycle: BoardCollectCycle,
    val isSaving: Boolean = false,
) {
    /** 원본과 다른 필드만 채운 부분 수정. 이름은 trim 해 비교하고, 비어 있으면 바뀐 것으로 보지 않는다. */
    public fun toUpdate(): BoardUpdate {
        val trimmedName = name.trim()
        return BoardUpdate(
            name = trimmedName.takeIf { it.isNotEmpty() && it != board.name },
            type = type.toDomainBoardType().takeIf { it != board.type },
            cycleHours = cycle.hours.takeIf { it != board.cycleHours },
        )
    }

    public companion object {
        public fun from(board: Board): BoardEditDraft =
            BoardEditDraft(
                board = board,
                name = board.name,
                type = board.type.toUiBoardType(),
                cycle = board.cycleHours.toCollectCycle(),
            )
    }
}

public data class BoardListViewState(
    val loadState: BoardListLoadState = BoardListLoadState.Loading,
    /** 삭제 확인 다이얼로그가 가리키는 게시판. null 이면 닫힘. */
    val pendingDeletion: Board? = null,
    /** 수정 시트가 편집 중인 게시판. null 이면 닫힘. */
    val editDraft: BoardEditDraft? = null,
    val pendingNavigation: BoardListDestination? = null,
    /** 재시도 요청이 오가는 중인 게시판 id. 그동안 그 카드의 재시도 버튼은 잠긴다. */
    val retryingBoardIds: Set<Long> = emptySet(),
    val message: BoardListMessage? = null,
    val sessionEnded: Boolean = false,
) : UiState {
    public val boards: List<Board> get() = (loadState as? BoardListLoadState.Loaded)?.boards.orEmpty()
}

/** 화면·수정 시트·삭제 다이얼로그가 [BoardListViewModel] 에 보내는 것. */
public sealed interface BoardListIntent : MviIntent {
    public data class Screen(
        val event: BoardListEvent,
    ) : BoardListIntent

    /** 수정 시트 이벤트. 저장 중에는 닫기를 무시해 응답이 시트 없는 화면에 떨어지지 않게 한다. */
    public data class Edit(
        val event: BoardEditEvent,
    ) : BoardListIntent

    /** 화면 재진입 시 목록을 다시 읽는다. 첫 로드가 진행 중이면 겹치지 않는다. */
    public data object Refresh : BoardListIntent

    public data object RetryLoad : BoardListIntent

    public data object ConfirmDelete : BoardListIntent

    public data object DismissDelete : BoardListIntent

    public data object ConsumeNavigation : BoardListIntent

    public data object ConsumeMessage : BoardListIntent

    public data object ConsumeSessionEnded : BoardListIntent
}

/** 상태가 겪은 것. [BoardListViewModel] 만 만든다. */
public sealed interface BoardListReducerEvent : ReducerEvent {
    public data class NavigationRequested(
        val destination: BoardListDestination,
    ) : BoardListReducerEvent

    /** null 이면 다이얼로그를 닫는다. */
    public data class DeletionRequested(
        val board: Board?,
    ) : BoardListReducerEvent

    /** null 이면 시트를 닫는다. */
    public data class EditDraftChanged(
        val draft: BoardEditDraft?,
    ) : BoardListReducerEvent

    public data class LoadStateChanged(
        val loadState: BoardListLoadState,
    ) : BoardListReducerEvent

    /** 읽은 목록이 있을 때만 갈아 끼운다 — 로딩·실패 중이면 아무것도 바꾸지 않는다. */
    public data class BoardsReplaced(
        val boards: List<Board>,
    ) : BoardListReducerEvent

    public data class MessageRaised(
        val message: BoardListMessage,
    ) : BoardListReducerEvent

    /** 재시도 요청을 보냈다. 응답이 올 때까지 그 게시판의 버튼을 잠근다. */
    public data class RetryStarted(
        val boardId: Long,
    ) : BoardListReducerEvent

    /** 재시도 요청이 끝났다. 성공이든 실패든 잠금은 풀린다. */
    public data class RetryFinished(
        val boardId: Long,
    ) : BoardListReducerEvent

    public data object SessionEnded : BoardListReducerEvent

    public data object NavigationConsumed : BoardListReducerEvent

    public data object MessageConsumed : BoardListReducerEvent

    public data object SessionEndedConsumed : BoardListReducerEvent
}

/**
 * 내 게시판 목록 — 수집 ON/OFF 는 먼저 뒤집고 실패하면 되돌리며, 삭제는 확인 뒤에만 보낸다.
 * 카드를 누르면 수정 시트가 열리고, 저장은 바뀐 필드만 `PATCH` 로 보낸다. 진입점은 [onIntent] 하나, 전이는
 * [reduce] 한 곳이다(#246).
 *
 * 등록 화면에서 돌아오면 Screen 이 [BoardListIntent.Refresh] 를 보낸다.
 *
 * 수정 시트에서 고치던 값은 [BoardEditInputDraft] 가 [SavedStateHandle] 에 남긴다(#156). 서버에 이미 있는
 * 게시판을 고치는 자리라 **필드 단위로 서버가 이긴다** — 남기는 것은 사용자가 실제로 바꾼 필드뿐이고, 시트를
 * 다시 열 때 바탕은 그 순간 목록에 있는 서버 값이다. 왜 그렇게 정했는지(그리고 왜 「충돌을 알아채 물어보기」가
 * 불가능한지)는 그 클래스의 KDoc 에 있다.
 */
@HiltViewModel
public class BoardListViewModel
    @Inject
    constructor(
        private val getBoards: GetBoardsUseCase,
        private val toggleBoardActive: ToggleBoardActiveUseCase,
        private val retryBoard: RetryBoardUseCase,
        private val deleteBoard: DeleteBoardUseCase,
        private val updateBoard: UpdateBoardUseCase,
        private val errorReporter: ErrorReporter,
        /** Screen 이 마지막 수집 상대 시각에 같은 시계를 쓴다. */
        public val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : MviViewModel<BoardListIntent, BoardListViewState, BoardListReducerEvent>(BoardListViewState()) {
        private val editInputDraft = BoardEditInputDraft(savedStateHandle)

        private var loadJob: Job? = null

        init {
            load(showLoading = true)
            // 시트가 값을 바꾸는 자리마다 저장하지 않고 상태 흐름 한 곳에서 남긴다 — 각자 저장하게 두면 언젠가
            // 한 곳이 빠지고, 빠진 자리는 프로세스가 죽어야 드러난다. 갱신은 시트가 **열려 있는 동안만** 한다:
            // 닫힘(null)까지 여기서 받으면 살아난 직후 시트가 닫힌 상태라는 이유로 방금 복원한 초안을 지운다.
            viewModelScope.launch {
                uiState.map { it.editDraft }.distinctUntilChanged().collect { draft -> draft?.let(editInputDraft::save) }
            }
        }

        override fun onIntent(intent: BoardListIntent) {
            when (intent) {
                is BoardListIntent.Screen -> onEvent(intent.event)
                is BoardListIntent.Edit -> onEditEvent(intent.event)
                BoardListIntent.Refresh -> refresh()
                BoardListIntent.RetryLoad -> load(showLoading = true)
                BoardListIntent.ConfirmDelete -> confirmDelete()
                BoardListIntent.DismissDelete -> dispatch(BoardListReducerEvent.DeletionRequested(null))
                BoardListIntent.ConsumeNavigation -> dispatch(BoardListReducerEvent.NavigationConsumed)
                BoardListIntent.ConsumeMessage -> dispatch(BoardListReducerEvent.MessageConsumed)
                BoardListIntent.ConsumeSessionEnded -> dispatch(BoardListReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: BoardListViewState,
            event: BoardListReducerEvent,
        ): BoardListViewState =
            when (event) {
                is BoardListReducerEvent.NavigationRequested -> {
                    state.copy(pendingNavigation = event.destination)
                }

                is BoardListReducerEvent.DeletionRequested -> {
                    state.copy(pendingDeletion = event.board)
                }

                is BoardListReducerEvent.EditDraftChanged -> {
                    state.copy(editDraft = event.draft)
                }

                is BoardListReducerEvent.LoadStateChanged -> {
                    state.copy(loadState = event.loadState)
                }

                is BoardListReducerEvent.BoardsReplaced -> {
                    if (state.loadState is BoardListLoadState.Loaded) {
                        state.copy(loadState = BoardListLoadState.Loaded(event.boards))
                    } else {
                        state
                    }
                }

                is BoardListReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                is BoardListReducerEvent.RetryStarted -> {
                    state.copy(retryingBoardIds = state.retryingBoardIds + event.boardId)
                }

                is BoardListReducerEvent.RetryFinished -> {
                    state.copy(retryingBoardIds = state.retryingBoardIds - event.boardId)
                }

                BoardListReducerEvent.SessionEnded -> {
                    state.copy(sessionEnded = true)
                }

                BoardListReducerEvent.NavigationConsumed -> {
                    state.copy(pendingNavigation = null)
                }

                BoardListReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                BoardListReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }

        private fun onEvent(event: BoardListEvent) {
            when (event) {
                BoardListEvent.AddBoardClicked -> {
                    dispatch(BoardListReducerEvent.NavigationRequested(BoardListDestination.Register))
                }

                is BoardListEvent.BoardToggled -> {
                    toggle(event.boardId.toLongOrNull() ?: return)
                }

                is BoardListEvent.RetryClicked -> {
                    retry(event.boardId.toLongOrNull() ?: return)
                }

                is BoardListEvent.DeleteClicked -> {
                    val boardId = event.boardId.toLongOrNull() ?: return
                    val board = currentState.boards.firstOrNull { it.id == boardId } ?: return
                    dispatch(BoardListReducerEvent.DeletionRequested(board))
                }

                is BoardListEvent.BoardSelected -> {
                    val boardId = event.boardId.toLongOrNull() ?: return
                    val board = currentState.boards.firstOrNull { it.id == boardId } ?: return
                    // 바탕은 언제나 방금 읽어 온 서버 값이고, 살아남은 초안은 사용자가 바꾼 필드만 그 위에 덮는다.
                    val base = BoardEditDraft.from(board)
                    val restored = editInputDraft.restoredEdit()?.applyTo(base) ?: base
                    dispatch(BoardListReducerEvent.EditDraftChanged(restored))
                }

                BoardListEvent.BackClicked -> {
                    dispatch(BoardListReducerEvent.NavigationRequested(BoardListDestination.Back))
                }
            }
        }

        private fun onEditEvent(event: BoardEditEvent) {
            when (event) {
                is BoardEditEvent.NameChanged -> {
                    updateDraft { it.copy(name = event.value) }
                }

                is BoardEditEvent.TypeSelected -> {
                    updateDraft { it.copy(type = event.type) }
                }

                is BoardEditEvent.CycleSelected -> {
                    updateDraft { it.copy(cycle = event.cycle) }
                }

                BoardEditEvent.SaveClicked -> {
                    save()
                }

                BoardEditEvent.DismissClicked -> {
                    if (currentState.editDraft?.isSaving == true) return
                    closeEditSheet()
                }
            }
        }

        private fun refresh() {
            if (loadJob?.isActive == true) return
            load(showLoading = currentState.loadState !is BoardListLoadState.Loaded)
        }

        private fun confirmDelete() {
            val board = currentState.pendingDeletion ?: return
            dispatch(BoardListReducerEvent.DeletionRequested(null))
            viewModelScope.launch {
                deleteBoard(board.id)
                    .onSuccess { updateBoards { boards -> boards.filterNot { it.id == board.id } } }
                    .onFailure { throwable ->
                        recordFailure(FeedFailureStage.BoardDelete, throwable)
                        raiseFailure(BoardListAction.Delete, throwable)
                    }
            }
        }

        private fun load(showLoading: Boolean) {
            loadJob?.cancel()
            if (showLoading) dispatch(BoardListReducerEvent.LoadStateChanged(BoardListLoadState.Loading))
            loadJob =
                viewModelScope.launch {
                    getBoards()
                        .onSuccess { boards -> dispatch(BoardListReducerEvent.LoadStateChanged(BoardListLoadState.Loaded(boards))) }
                        .onFailure { throwable ->
                            recordFailure(FeedFailureStage.BoardList, throwable)
                            // 이미 읽은 목록이 있으면 그대로 둔다 — 재조회 실패로 화면을 비우지 않는다.
                            if (currentState.loadState !is BoardListLoadState.Loaded) {
                                dispatch(
                                    BoardListReducerEvent.LoadStateChanged(BoardListLoadState.Failed(throwable.toFeedFailureReason())),
                                )
                            }
                        }
                }
        }

        private fun toggle(boardId: Long) {
            val before = currentState.boards.firstOrNull { it.id == boardId } ?: return
            val optimistic =
                before.copy(
                    isActive = !before.isActive,
                    status =
                        when {
                            before.isActive -> DomainBoardStatus.Paused
                            before.status == DomainBoardStatus.Paused -> DomainBoardStatus.Active
                            else -> before.status
                        },
                )
            replaceBoard(optimistic)
            viewModelScope.launch {
                toggleBoardActive(boardId, isActive = optimistic.isActive)
                    .onSuccess { updated -> replaceBoard(updated) }
                    .onFailure { throwable ->
                        recordFailure(FeedFailureStage.BoardToggle, throwable)
                        // 되돌리는 것은 토글이 건드린 두 필드뿐이다 — 요청이 오가는 사이 수정 시트가 저장한
                        // 이름·주기를 옛 스냅샷으로 덮지 않는다(#235).
                        updateBoard(boardId) { it.copy(isActive = before.isActive, status = before.status) }
                        raiseFailure(BoardListAction.Toggle, throwable)
                    }
            }
        }

        /**
         * 지금 바로 다시 수집시킨다. 응답이 올 때까지 그 게시판의 버튼은 잠가 둔다.
         *
         * 잠그지 않으면 연타한 횟수만큼 `POST /boards/{id}/retry` 가 나가고 스낵바도 그만큼 뜬다. 버튼이
         * 살아 있는 동안 사용자는 눌리지 않았다고 읽으므로 한 번 더 누르는 쪽이 자연스럽다(#341).
         */
        private fun retry(boardId: Long) {
            if (boardId in currentState.retryingBoardIds) return
            if (currentState.boards.none { it.id == boardId }) return
            dispatch(BoardListReducerEvent.RetryStarted(boardId))
            viewModelScope.launch {
                val result = retryBoard(boardId)
                dispatch(BoardListReducerEvent.RetryFinished(boardId))
                result
                    .onSuccess {
                        // 손대는 것은 재시도가 되살린 세 필드뿐이다. 요청이 오가는 사이 수정 시트가 저장한
                        // 이름이나 재조회가 실어 온 값을 요청 전 스냅샷으로 덮지 않는다(#235 의 토글과 같은 꼴).
                        updateBoard(boardId) { it.copy(status = DomainBoardStatus.Active, failCount = 0, isActive = true) }
                        dispatch(BoardListReducerEvent.MessageRaised(BoardListMessage.RetryRequested))
                    }.onFailure { throwable ->
                        recordFailure(FeedFailureStage.BoardRetry, throwable)
                        raiseFailure(BoardListAction.Retry, throwable)
                    }
            }
        }

        /** 바뀐 필드만 보낸다. 바뀐 게 없으면 요청 없이 닫고, 실패하면 시트를 유지한 채 알린다. */
        private fun save() {
            val draft = currentState.editDraft ?: return
            if (draft.isSaving || draft.name.isBlank()) return
            val update = draft.toUpdate()
            if (update.isEmpty) {
                closeEditSheet()
                return
            }
            updateDraft { it.copy(isSaving = true) }
            viewModelScope.launch {
                updateBoard(draft.board.id, update)
                    .onSuccess { updated ->
                        replaceBoard(updated)
                        editInputDraft.clear()
                        dispatch(BoardListReducerEvent.EditDraftChanged(null))
                        dispatch(BoardListReducerEvent.MessageRaised(BoardListMessage.Updated))
                    }.onFailure { throwable ->
                        recordFailure(FeedFailureStage.BoardUpdate, throwable)
                        updateDraft { it.copy(isSaving = false) }
                        raiseFailure(BoardListAction.Update, throwable)
                    }
            }
        }

        /** 시트를 닫으면서 초안도 버린다 — 닫히는 이유(취소·보낼 것 없음)가 모두 편집을 버리는 쪽이다. */
        private fun closeEditSheet() {
            editInputDraft.clear()
            dispatch(BoardListReducerEvent.EditDraftChanged(null))
        }

        private fun updateDraft(transform: (BoardEditDraft) -> BoardEditDraft) {
            val draft = currentState.editDraft ?: return
            dispatch(BoardListReducerEvent.EditDraftChanged(transform(draft)))
        }

        private fun replaceBoard(board: Board) {
            updateBoards { boards -> boards.map { if (it.id == board.id) board else it } }
        }

        private fun updateBoard(
            boardId: Long,
            transform: (Board) -> Board,
        ) {
            updateBoards { boards -> boards.map { if (it.id == boardId) transform(it) else it } }
        }

        private fun updateBoards(transform: (List<Board>) -> List<Board>) {
            val loaded = currentState.loadState as? BoardListLoadState.Loaded ?: return
            dispatch(BoardListReducerEvent.BoardsReplaced(transform(loaded.boards)))
        }

        /** 실패를 안내로 옮긴다. 문구는 짓지 않고 표의 행만 실어 보낸다(#360). */
        private fun raiseFailure(
            action: BoardListAction,
            throwable: Throwable,
        ) {
            dispatch(BoardListReducerEvent.MessageRaised(BoardListMessage.Failed(action, throwable.toFailureKind())))
        }

        private fun recordFailure(
            stage: FeedFailureStage,
            throwable: Throwable,
        ) {
            errorReporter.recordFeedFailure(stage, throwable)
            if (throwable is CoreDataFailure.Unauthorized) {
                dispatch(BoardListReducerEvent.SessionEnded)
            }
        }
    }

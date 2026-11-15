package com.careercompass.feature.feed.presentation.board

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.board.MAX_BOARDS
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.sentence
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.presentation.R
import com.careercompass.feature.feed.presentation.shared.component.FeedTopBar
import com.careercompass.feature.feed.presentation.shared.util.toBoardUiModel
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * 내 게시판 진입점 — 삭제는 확인 다이얼로그를, 수정은 바텀시트를 거치고, 화면에 돌아올 때마다 목록을 다시 읽는다.
 *
 * 수정 실패 스낵바는 시트 안에 띄운다 — 시트가 별도 창이라 화면 스낵바는 시트 뒤에 가려진다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun BoardListScreen(
    onBackClick: () -> Unit,
    onAddBoardClick: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoardListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetSnackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(BoardListIntent.Refresh)
        onPauseOrDispose { }
    }
    val pendingNavigation = state.pendingNavigation
    LaunchedEffect(pendingNavigation) {
        if (pendingNavigation == null) return@LaunchedEffect
        viewModel.onIntent(BoardListIntent.ConsumeNavigation)
        when (pendingNavigation) {
            BoardListDestination.Back -> onBackClick()
            BoardListDestination.Register -> onAddBoardClick()
        }
    }
    val sessionEnded = state.sessionEnded
    LaunchedEffect(sessionEnded) {
        if (sessionEnded) {
            viewModel.onIntent(BoardListIntent.ConsumeSessionEnded)
            onSessionEnded()
        }
    }
    val message = state.message
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        viewModel.onIntent(BoardListIntent.ConsumeMessage)
        val host = if (state.editDraft != null) sheetSnackbarHostState else snackbarHostState
        snackbarScope.launch { host.showSnackbar(message.toLabel(resources)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val loadState = state.loadState) {
            is BoardListLoadState.Failed -> {
                BoardListErrorChrome(onBackClick = { viewModel.onIntent(BoardListIntent.Screen(BoardListEvent.BackClicked)) }) {
                    BoardListFailureContent(
                        reason = loadState.reason,
                        onRetryClick = { viewModel.onIntent(BoardListIntent.RetryLoad) },
                    )
                }
            }

            BoardListLoadState.Loading,
            is BoardListLoadState.Loaded,
            -> {
                val retryingBoardIds = state.retryingBoardIds
                val uiState =
                    remember(loadState, retryingBoardIds, resources) {
                        loadState.toUiState(resources, viewModel.clock, retryingBoardIds)
                    }
                BoardListContent(
                    state = uiState,
                    onEvent = { viewModel.onIntent(BoardListIntent.Screen(it)) },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val pendingDeletion = state.pendingDeletion
    if (pendingDeletion != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(BoardListIntent.DismissDelete) },
            confirmButton = {
                CareerCompassButton(
                    text = stringResource(R.string.feed_board_delete_dialog_confirm),
                    onClick = { viewModel.onIntent(BoardListIntent.ConfirmDelete) },
                    variant = CareerCompassButtonVariant.Danger,
                    size = CareerCompassButtonSize.Small,
                )
            },
            dismissButton = {
                CareerCompassButton(
                    text = stringResource(R.string.feed_board_delete_dialog_cancel),
                    onClick = { viewModel.onIntent(BoardListIntent.DismissDelete) },
                    variant = CareerCompassButtonVariant.Ghost,
                    size = CareerCompassButtonSize.Small,
                )
            },
            title = { Text(text = stringResource(R.string.feed_board_delete_dialog_title)) },
            text = { Text(text = stringResource(R.string.feed_board_delete_dialog_message, pendingDeletion.name)) },
            containerColor = CareerCompassTheme.colors.surface,
            titleContentColor = CareerCompassTheme.colors.onSurface,
            textContentColor = CareerCompassTheme.colors.onSurfaceVariant,
        )
    }

    val editDraft = state.editDraft
    if (editDraft != null) {
        // 저장 중에는 스와이프·스크림·뒤로가기로 시트가 숨겨지지 않게 한다 — 숨긴 뒤 닫기를 무시하면 빈 창만 남는다.
        val isSaving by rememberUpdatedState(editDraft.isSaving)
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(BoardListIntent.Edit(BoardEditEvent.DismissClicked)) },
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { value -> value != SheetValue.Hidden || !isSaving },
                ),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            Box {
                BoardEditSheetContent(
                    state = remember(editDraft, resources) { editDraft.toUiState(resources) },
                    onEvent = { viewModel.onIntent(BoardListIntent.Edit(it)) },
                )
                SnackbarHost(
                    hostState = sheetSnackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun BoardListErrorChrome(
    onBackClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CareerCompassTheme.colors.subtleSurface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        FeedTopBar(
            title = stringResource(R.string.feed_board_list_title),
            onBackClick = onBackClick,
            actions = null,
        )
        content()
    }
}

internal fun BoardListLoadState.toUiState(
    resources: Resources,
    clock: Clock,
    retryingBoardIds: Set<Long>,
): BoardListUiState =
    BoardListUiState(
        content =
            when (this) {
                is BoardListLoadState.Loaded -> {
                    if (boards.isEmpty()) {
                        BoardListContentState.Empty
                    } else {
                        BoardListContentState.Loaded(
                            boards.distinctBy { it.id }.map { board ->
                                board.toBoardUiModel(resources, clock).copy(isRetrying = board.id in retryingBoardIds)
                            },
                        )
                    }
                }

                BoardListLoadState.Loading,
                is BoardListLoadState.Failed,
                -> {
                    BoardListContentState.Loading
                }
            },
        maxBoardCount = MAX_BOARDS,
    )

/** 이름 오류는 비어 있을 때만이다 — 원본 이름은 비어 있지 않으므로 사용자가 지운 뒤에만 나타난다. */
internal fun BoardEditDraft.toUiState(resources: Resources): BoardEditUiState =
    BoardEditUiState(
        boardName = board.name,
        url = board.url,
        name = name,
        nameError = if (name.isBlank()) resources.getString(R.string.feed_board_edit_name_error_blank) else null,
        type = type,
        cycle = cycle,
        isSaving = isSaving,
        hasChanges = !toUpdate().isEmpty,
    )

/**
 * 안내 문구. 실패는 실패 표에서 읽고(#204·#360), 「다시 수집을 시작했어요」처럼 이 화면만 하는 말은 여기 남는다.
 *
 * 사유를 확인하지 못한 실패([FailureKind.Unexpected])만 화면 고유 문구를 쓴다. 표의 그 행은 게시판 문맥에서
 * 「게시판을 불러오지 못했어요」라 조회 실패를 가리키는 문장이고, 목록에서 시킨 삭제·수정·토글·재시도에 그대로
 * 붙이면 사용자가 무엇이 실패했는지 잘못 읽는다. 사유가 좁혀진 실패는 표의 제 행이 이긴다.
 */
private fun BoardListMessage.toLabel(resources: Resources): String =
    when (this) {
        BoardListMessage.RetryRequested -> {
            resources.getString(R.string.feed_board_retry_requested)
        }

        BoardListMessage.Updated -> {
            resources.getString(R.string.feed_board_updated)
        }

        is BoardListMessage.Failed -> {
            if (kind == FailureKind.Unexpected) {
                resources.getString(action.unexpectedRes())
            } else {
                kind.display(FailureSurface.Board).sentence(resources)
            }
        }
    }

private fun BoardListAction.unexpectedRes(): Int =
    when (this) {
        BoardListAction.Toggle -> R.string.feed_board_toggle_failed
        BoardListAction.Retry -> R.string.feed_board_retry_failed
        BoardListAction.Delete -> R.string.feed_board_delete_failed
        BoardListAction.Update -> R.string.feed_board_update_failed
    }

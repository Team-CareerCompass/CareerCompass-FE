package com.careercompass.feature.feed.presentation.board

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.sentence
import com.careercompass.core.ui.failure.title
import com.careercompass.feature.feed.presentation.R
import kotlinx.coroutines.launch

/** 게시판 등록 진입점. 등록이 끝나면 [onBackClick] 으로 목록에 돌아간다(목록은 재진입 시 다시 읽는다). */
@Composable
public fun BoardRegisterScreen(
    onBackClick: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoardRegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    // 제출 중 이탈 차단은 상단 화살표만 막아서는 반쪽이다 — 시스템 뒤로가기와 가장자리 제스처가 그대로
    // 남으면 사용자는 늘 쓰던 손짓으로 나가고 요청은 그대로 끊긴다(#146). 두 길을 같은 이벤트로 모아
    // ViewModel 이 한자리에서 판정하게 한다. 제출 중이 아니면 꺼져 있어 평소 뒤로가기는 그대로 동작한다.
    BackHandler(enabled = state.isSubmitting) { viewModel.onIntent(BoardRegisterIntent.Screen(BoardRegisterEvent.BackClicked)) }

    val isBackRequested = state.isBackRequested
    LaunchedEffect(isBackRequested) {
        if (isBackRequested) {
            viewModel.onIntent(BoardRegisterIntent.ConsumeBack)
            onBackClick()
        }
    }
    val sessionEnded = state.sessionEnded
    LaunchedEffect(sessionEnded) {
        if (sessionEnded) {
            viewModel.onIntent(BoardRegisterIntent.ConsumeSessionEnded)
            onSessionEnded()
        }
    }
    val message = state.message
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        viewModel.onIntent(BoardRegisterIntent.ConsumeMessage)
        snackbarScope.launch {
            // 제출 중 뒤로가기는 조급한 사용자가 연달아 누른다. 그대로 두면 안내가 줄을 서서 등록이 끝난
            // 뒤에도 계속 뜨므로, 새 안내가 앞의 안내를 대신하게 한다.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message.toLabel(resources))
        }
    }

    val uiState = remember(state, resources) { state.toUiState(resources) }

    Box(modifier = modifier.fillMaxSize()) {
        BoardRegisterContent(
            state = uiState,
            onEvent = { viewModel.onIntent(BoardRegisterIntent.Screen(it)) },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal fun BoardRegisterViewState.toUiState(resources: Resources): BoardRegisterUiState =
    BoardRegisterUiState(
        url = url,
        urlError =
            when (urlError) {
                null -> null
                BoardUrlError.Invalid -> resources.getString(R.string.feed_board_register_url_invalid)
                BoardUrlError.Duplicate -> resources.getString(R.string.feed_board_register_url_duplicate)
            },
        detection = detection,
        name = name,
        type = type,
        cycle = cycle,
        isSubmitting = isSubmitting,
    )

/**
 * 안내 문구 — 서버 코드에서 온 것은 실패 표에서 읽고([FailureKind], #204), 이 화면에서만 나는 것은
 * 여기 남는다.
 *
 * 「구조를 분석하지 못했다」·「등록하지 못했다」·「나가지 못한다」는 §9 의 어느 코드도 아니다. 게시판
 * 등록의 단계(감지 → 등록)와 이탈 차단(#146)이 만들어 내는 상태라, 표에 넣으면 다른 기능이 쓸 수 없는
 * 행이 하나 늘 뿐이다.
 *
 * 스낵바는 한 줄만 허용하므로 표의 제목과 본문을 이어 붙인다.
 */
internal fun BoardRegisterMessage.toLabel(resources: Resources): String =
    when (this) {
        BoardRegisterMessage.NetworkUnavailable -> {
            FailureKind.NoConnection.display().sentence(resources)
        }

        BoardRegisterMessage.DetectFailed -> {
            resources.getString(R.string.feed_board_register_detect_failed)
        }

        // 사유를 확인하지 못한 실패만 이 화면의 문구를 쓴다. 표에 행이 있는 실패는 표가 이긴다(#360).
        is BoardRegisterMessage.RegisterFailed -> {
            if (kind == FailureKind.Unexpected) {
                resources.getString(R.string.feed_board_register_failed)
            } else {
                kind.display(FailureSurface.Board).sentence(resources)
            }
        }

        // 점검 문구도 표에서 읽는다 — 화면 한 장을 쓰는 자리(FeedMaintenanceState)와 같은 행이다. 본문은
        // 화면 한 장용으로 줄바꿈을 품고 있어 한 줄짜리 스낵바에는 제목만 싣는다.
        BoardRegisterMessage.Maintenance -> {
            FailureKind.ServiceUnavailable.display().title(resources)
        }

        BoardRegisterMessage.SubmitInProgress -> {
            resources.getString(R.string.feed_board_register_submit_in_progress)
        }

        BoardRegisterMessage.AlreadyRegistered -> {
            FailureKind.DuplicateBoard.display(FailureSurface.Board).sentence(resources)
        }

        // 상한은 도메인이 들고 온 값을 그대로 쓴다 — 표의 기본값(MAX_BOARDS)과 어긋나는 순간이 있다면
        // 사용자에게는 서버가 말한 쪽이 참이다.
        is BoardRegisterMessage.LimitReached -> {
            FailureKind.LimitExceeded.display(FailureSurface.Board, itemLimit = limit).sentence(resources)
        }
    }

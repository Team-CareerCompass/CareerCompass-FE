package com.careercompass.feature.editor.presentation.setup

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemRules
import com.careercompass.feature.editor.presentation.R
import com.careercompass.feature.editor.presentation.setup.component.ApplicationItemEditSheet
import kotlinx.coroutines.launch

/**
 * 지원서 작성 첫 화면 진입점 — 공고 상세의 「지원서 초안 작성하기」가 연다(#183).
 *
 * 초안 생성이 시작되면 [onDraftStarted] 로 진행 화면(#184)에 넘긴다. 이 화면은 백스택에서 사라져야 한다 —
 * 남겨 두면 뒤로 가기가 이미 만든 초안을 다시 만드는 자리로 돌아온다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ApplicationSetupScreen(
    onBackClick: () -> Unit,
    onDraftStarted: (ApplicationDraftStarted) -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: ApplicationSetupViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    ObserveSignal(
        signal = state.sessionEnd,
        consumed = ApplicationSetupIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
    ) { onSessionEnded() }
    ObserveSignal(
        signal = state.draftStarted,
        consumed = ApplicationSetupIntent.ConsumeDraftStarted,
        onIntent = viewModel::onIntent,
        onSignal = onDraftStarted,
    )
    ObserveSignal(
        signal = state.message,
        consumed = ApplicationSetupIntent.ConsumeMessage,
        onIntent = viewModel::onIntent,
    ) { message ->
        snackbarScope.launch { snackbarHostState.showSnackbar(message.text(resources)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ApplicationSetupContent(
            state = state,
            onEvent = { event ->
                if (event == ApplicationSetupEvent.BackClicked) {
                    onBackClick()
                } else {
                    viewModel.onIntent(ApplicationSetupIntent.Screen(event))
                }
            },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.itemEditor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(ApplicationSetupIntent.ItemEditor(ApplicationItemEditorEvent.Dismissed)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            ApplicationItemEditSheet(
                state = editor,
                onEvent = { viewModel.onIntent(ApplicationSetupIntent.ItemEditor(it)) },
            )
        }
    }
}

private fun ApplicationSetupMessage.text(resources: Resources): String =
    when (this) {
        ApplicationSetupMessage.LimitReached -> {
            resources.getString(R.string.editor_setup_limit_reached, ApplicationItemRules.MAX_ITEMS)
        }

        ApplicationSetupMessage.LastItemKept -> {
            resources.getString(R.string.editor_setup_last_item_kept)
        }

        ApplicationSetupMessage.CreateFailed -> {
            resources.getString(R.string.editor_setup_create_failed)
        }
    }

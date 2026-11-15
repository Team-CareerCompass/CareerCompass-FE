package com.careercompass.feature.editor.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.mvi.ObserveSignal

@Composable
public fun ApplicationProgressScreen(
    viewModel: ApplicationProgressViewModel,
    onBackClick: () -> Unit,
    onCompleteProfile: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(ApplicationProgressIntent.Resumed)
        onPauseOrDispose { viewModel.onIntent(ApplicationProgressIntent.Paused) }
    }
    ObserveSignal(
        signal =
            state.sessionEnded.takeIf {
                it
            },
        consumed = ApplicationProgressIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
    ) {
        onSessionEnded()
    }
    ApplicationProgressContent(state, viewModel::onIntent, onBackClick, onCompleteProfile, modifier)
}

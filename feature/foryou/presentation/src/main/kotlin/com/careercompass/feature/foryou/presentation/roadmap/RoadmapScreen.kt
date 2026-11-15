package com.careercompass.feature.foryou.presentation.roadmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.mvi.ObserveSignal

@Composable
public fun RoadmapScreen(
    onBackClick: () -> Unit,
    onCompleteProfile: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoadmapViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(RoadmapIntent.Resumed)
        onPauseOrDispose { }
    }
    ObserveSignal(signal = state.sessionEnded.takeIf { it }, consumed = RoadmapIntent.ConsumeSessionEnded, onIntent = viewModel::onIntent) {
        onSessionEnded()
    }
    RoadmapContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onCompleteProfile = onCompleteProfile,
        modifier = modifier,
    )
}

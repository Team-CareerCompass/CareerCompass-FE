package com.careercompass.feature.editor.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
public fun ApplicationProgressScreen(
    viewModel: ApplicationProgressViewModel,
    onBackClick: () -> Unit,
    onCompleteProfile: () -> Unit,
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApplicationProgressContent(state, viewModel::onIntent, onBackClick, onCompleteProfile, modifier, onEditItem)
}

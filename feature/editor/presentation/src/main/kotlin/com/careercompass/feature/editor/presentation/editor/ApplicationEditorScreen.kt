package com.careercompass.feature.editor.presentation.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressViewModel

@Composable
public fun ApplicationEditorScreen(
    viewModel: ApplicationProgressViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApplicationEditorContent(state, viewModel::onIntent, onBackClick, modifier)
}

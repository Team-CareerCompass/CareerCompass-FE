package com.careercompass.feature.editor.presentation.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressViewModel
import com.careercompass.feature.editor.presentation.save.ApplicationSaveOverflowDialog

@Composable
public fun ApplicationEditorScreen(
    viewModel: ApplicationProgressViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(ApplicationProgressIntent.EditorResumed)
        onPauseOrDispose { viewModel.onIntent(ApplicationProgressIntent.EditorPaused) }
    }
    ApplicationEditorContent(state, viewModel::onIntent, onBackClick, modifier)
    if (state.save.confirmOverflow) ApplicationSaveOverflowDialog(state, viewModel::onIntent)
}

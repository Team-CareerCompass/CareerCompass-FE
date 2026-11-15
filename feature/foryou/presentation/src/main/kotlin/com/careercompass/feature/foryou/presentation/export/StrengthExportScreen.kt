package com.careercompass.feature.foryou.presentation.export

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.feature.foryou.presentation.R
import kotlinx.coroutines.launch

@Composable
public fun StrengthExportScreen(
    onBackClick: () -> Unit,
    onAddExperience: () -> Unit,
    onCompleteProfile: () -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: StrengthExportViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(StrengthExportIntent.RefreshAvailability)
        onPauseOrDispose { }
    }
    ObserveSignal(
        signal =
            state.sessionEnded.takeIf {
                it
            },
        consumed = StrengthExportIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
    ) {
        onSessionEnded()
    }
    ObserveSignal(signal = state.copyRequest, consumed = StrengthExportIntent.ConsumeCopy, onIntent = viewModel::onIntent) { content ->
        val copied =
            runCatching {
                val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
                val clip = ClipData.newPlainText(resources.getString(R.string.foryou_export_title), content)
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                clipboard.setPrimaryClip(clip)
            }.isSuccess
        scope.launch {
            snackbar.showSnackbar(resources.getString(if (copied) R.string.foryou_export_copied else R.string.foryou_export_copy_failed))
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        StrengthExportContent(state, viewModel::onIntent, onBackClick, onAddExperience, onCompleteProfile)
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

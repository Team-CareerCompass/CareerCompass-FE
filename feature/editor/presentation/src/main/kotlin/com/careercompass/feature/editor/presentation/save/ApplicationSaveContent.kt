package com.careercompass.feature.editor.presentation.save

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.description
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.title
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.presentation.R
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ApplicationSaveStatus(
    state: ApplicationSaveState,
    onIntent: (ApplicationProgressIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.small)) {
        if (state.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(if (state.isFinalSaving) R.string.editor_save_final_working else R.string.editor_save_temporary_working))
        } else {
            val last = state.lastSavedAt
            Text(
                text =
                    if (last == null) {
                        stringResource(R.string.editor_save_temporary_notice)
                    } else {
                        stringResource(
                            R.string.editor_save_last_time,
                            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(last)),
                        )
                    },
                style = CareerCompassTheme.typography.caption,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
        state.failure?.let { failure ->
            Text(stringResource(R.string.editor_save_failed), color = CareerCompassTheme.colors.error)
            Text(failure.display().title(), style = CareerCompassTheme.typography.labelMedium)
            Text(failure.display().description(), style = CareerCompassTheme.typography.bodyMedium)
            if (state.autoSaveStopped) Text(stringResource(R.string.editor_save_stopped), color = CareerCompassTheme.colors.error)
            CareerCompassButton(
                text = stringResource(R.string.editor_save_retry),
                onClick = { onIntent(ApplicationProgressIntent.RetrySave) },
                enabled = !state.isSaving,
                variant = CareerCompassButtonVariant.Secondary,
            )
        }
    }
}

@Composable
internal fun ApplicationSaveOverflowDialog(
    state: ApplicationProgressUiState,
    onIntent: (ApplicationProgressIntent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    AlertDialog(
        onDismissRequest = { onIntent(ApplicationProgressIntent.DismissOverLimitSave) },
        icon = {
            Text(
                "⚠",
                color = colors.onWarningContainer,
                modifier = Modifier.background(colors.warningContainer, CareerCompassTheme.shapes.pill).padding(12.dp),
            )
        },
        title = { Text(stringResource(R.string.editor_save_overflow_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
            ) {
                state.overflowItems.forEach { item ->
                    Text(stringResource(R.string.editor_progress_item_question, item.order, item.question))
                    Text(stringResource(R.string.editor_edit_count, item.answer.orEmpty().length, item.maxChars), color = colors.error)
                }
                Text(stringResource(R.string.editor_save_overflow_description))
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(ApplicationProgressIntent.ConfirmOverLimitSave) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.warning, contentColor = colors.onWarning),
                shape = CareerCompassTheme.shapes.control,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.editor_save_overflow_confirm)) }
        },
        dismissButton = {
            CareerCompassButton(
                text = stringResource(R.string.editor_save_keep_editing),
                onClick = { onIntent(ApplicationProgressIntent.DismissOverLimitSave) },
                variant = CareerCompassButtonVariant.Ghost,
            )
        },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

@Composable
public fun ApplicationSavedScreen(
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(CareerCompassTheme.colors.subtleSurface)) {
        CareerCompassTopAppBar(title = stringResource(R.string.editor_save_complete_title), onBackClick = onCloseClick)
        Column(
            Modifier.padding(CareerCompassTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.large),
        ) {
            Text(stringResource(R.string.editor_save_complete_title), style = CareerCompassTheme.typography.headline2)
            Text(stringResource(R.string.editor_save_complete_description), style = CareerCompassTheme.typography.bodyMedium)
            CareerCompassButton(
                text = stringResource(R.string.editor_save_close),
                onClick = onCloseClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

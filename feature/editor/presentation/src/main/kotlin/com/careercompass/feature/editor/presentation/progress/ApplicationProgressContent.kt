package com.careercompass.feature.editor.presentation.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureAction
import com.careercompass.core.ui.failure.actionLabel
import com.careercompass.core.ui.failure.description
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.title
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.presentation.R

@Composable
internal fun ApplicationProgressContent(
    state: ApplicationProgressUiState,
    onIntent: (ApplicationProgressIntent) -> Unit,
    onBackClick: () -> Unit,
    onCompleteProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onEditItem: (Long) -> Unit = {},
) {
    val spacing = CareerCompassTheme.spacing
    Column(modifier.fillMaxSize().background(CareerCompassTheme.colors.subtleSurface)) {
        CareerCompassTopAppBar(title = stringResource(R.string.editor_progress_title), onBackClick = onBackClick)
        LazyColumn(contentPadding = PaddingValues(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            item {
                Text(
                    stringResource(
                        when {
                            state.draft == null -> R.string.editor_progress_recovering

                            state.draft?.hasLoadingItem == true -> R.string.editor_progress_generating_title

                            state.draft?.items?.any {
                                it.status == ApplicationItemStatus.Failed
                            } == true -> R.string.editor_progress_failed_title

                            else -> R.string.editor_progress_ready_title
                        },
                    ),
                    style = CareerCompassTheme.typography.headline2,
                )
                Text(stringResource(R.string.editor_progress_description), style = CareerCompassTheme.typography.bodyMedium)
                state.draft?.let { draft ->
                    Text(
                        stringResource(R.string.editor_progress_count, draft.completedItemCount, draft.items.size),
                        style = CareerCompassTheme.typography.labelMedium,
                    )
                }
                if (state.isRecovering || state.isStreaming) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.isRecovering) Text(stringResource(R.string.editor_progress_recovering))
            }
            if (state.identityMismatch) {
                item {
                    Text(stringResource(R.string.editor_progress_mismatch), color = CareerCompassTheme.colors.error)
                }
            }
            state.failure?.let { kind ->
                item {
                    val display = kind.display()
                    CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(display.title(), style = CareerCompassTheme.typography.labelMedium)
                        Text(display.description())
                        if (state.interrupted) Text(stringResource(R.string.editor_progress_interrupted))
                        val action =
                            when (display.action) {
                                FailureAction.Retry -> ({ onIntent(ApplicationProgressIntent.Recover) })
                                FailureAction.CompleteProfile -> onCompleteProfile
                                else -> null
                            }
                        val label = display.actionLabel()
                        if (label != null &&
                            action != null
                        ) {
                            CareerCompassButton(text = label, onClick = action, enabled = !state.isRecovering)
                        }
                    }
                }
            }
            items(state.draft?.items.orEmpty(), key = { it.id }) { item ->
                val statusColor =
                    when (item.status) {
                        ApplicationItemStatus.Done -> CareerCompassTheme.colors.primaryEmphasis
                        ApplicationItemStatus.Failed -> CareerCompassTheme.colors.error
                        ApplicationItemStatus.Loading -> CareerCompassTheme.colors.onSurfaceVariant
                    }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CareerCompassTheme.shapes.card,
                    color = CareerCompassTheme.colors.surface,
                    contentColor = CareerCompassTheme.colors.onSurface,
                    border = BorderStroke(1.dp, statusColor),
                ) {
                    Column(Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        Text(
                            stringResource(R.string.editor_progress_item_question, item.order, item.question),
                            style = CareerCompassTheme.typography.labelMedium,
                        )
                        val retrying = item.id in state.retryingIds
                        val status =
                            when {
                                item.id in state.editedAnswers -> R.string.editor_edit_manual
                                retrying -> R.string.editor_progress_retrying
                                item.status == ApplicationItemStatus.Done -> R.string.editor_progress_done
                                item.status == ApplicationItemStatus.Failed -> R.string.editor_progress_failed
                                state.isStreaming -> R.string.editor_progress_generating
                                else -> R.string.editor_progress_waiting
                            }
                        Text(stringResource(status), style = CareerCompassTheme.typography.caption, color = statusColor)
                        (state.editedAnswers[item.id] ?: item.answer)?.takeIf { it.isNotEmpty() }?.let { answer ->
                            SelectionContainer { Text(answer, style = CareerCompassTheme.typography.bodyMedium) }
                        }
                        CareerCompassButton(
                            text = stringResource(R.string.editor_edit_item),
                            onClick = { onEditItem(item.id) },
                            enabled = !state.identityMismatch,
                            variant = com.careercompass.core.ui.component.CareerCompassButtonVariant.Ghost,
                        )
                        if (item.status == ApplicationItemStatus.Failed) {
                            CareerCompassButton(
                                text = stringResource(R.string.editor_progress_retry_item),
                                onClick = { onIntent(ApplicationProgressIntent.RetryItem(item.id)) },
                                enabled = !retrying && !state.isRecovering && !state.identityMismatch,
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.careercompass.feature.editor.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.presentation.R
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState

@Composable
internal fun ApplicationEditorContent(
    state: ApplicationProgressUiState,
    onIntent: (ApplicationProgressIntent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val items = state.draft?.items.orEmpty()
    val selected = items.firstOrNull { it.id == state.selectedItemId } ?: items.firstOrNull()
    val tabs = rememberLazyListState()
    val selectedIndex = items.indexOf(selected)
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) tabs.animateScrollToItem(selectedIndex)
    }
    Column(modifier.fillMaxSize().background(colors.subtleSurface).imePadding()) {
        CareerCompassTopAppBar(title = stringResource(R.string.editor_edit_title), onBackClick = onBackClick)
        LazyRow(state = tabs, contentPadding = PaddingValues(horizontal = spacing.large)) {
            items(items, key = { it.id }) { item ->
                val isSelected = item.id == selected?.id
                Column(
                    Modifier
                        .widthIn(min = 88.dp)
                        .heightIn(min = 48.dp)
                        .selectable(isSelected, role = Role.Tab, onClick = { onIntent(ApplicationProgressIntent.SelectItem(item.id)) })
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    Text(
                        stringResource(R.string.editor_edit_tab, item.order),
                        style = CareerCompassTheme.typography.labelMedium,
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    )
                    if (isSelected) HorizontalDivider(color = colors.onSurface, thickness = 2.dp)
                }
            }
        }
        HorizontalDivider(color = colors.subtleOutline)
        if (selected == null) {
            Text(stringResource(R.string.editor_progress_recovering), Modifier.padding(spacing.large))
            if (state.failure != null || state.identityMismatch) {
                CareerCompassButton(text = stringResource(R.string.editor_edit_progress), onClick = onBackClick)
            }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(selected.question, style = CareerCompassTheme.typography.headline4)
            Text(
                stringResource(R.string.editor_edit_maximum, selected.maxChars),
                style = CareerCompassTheme.typography.caption,
                color = colors.onSurfaceVariant,
            )
            val answer = state.editedAnswers[selected.id] ?: selected.answer.orEmpty()
            val overflow = answer.length > selected.maxChars
            OutlinedTextField(
                value = answer,
                onValueChange = { onIntent(ApplicationProgressIntent.EditAnswer(selected.id, it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).background(colors.surface, CareerCompassTheme.shapes.card),
                label = { Text(stringResource(R.string.editor_edit_answer)) },
                shape = CareerCompassTheme.shapes.card,
                textStyle = CareerCompassTheme.typography.bodyMedium,
                minLines = 10,
                maxLines = 16,
                isError = overflow,
                enabled = !state.identityMismatch && !state.sessionEnded,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text(
                stringResource(R.string.editor_edit_count, answer.length, selected.maxChars),
                style = CareerCompassTheme.typography.labelMedium,
                color = if (overflow) colors.error else colors.onSurface,
            )
            if (overflow) Text(stringResource(R.string.editor_edit_overflow), color = colors.error)
            if (state.inputLimitReached) Text(stringResource(R.string.editor_edit_input_limit), color = colors.error)
            Text(
                stringResource(R.string.editor_edit_local_notice),
                style = CareerCompassTheme.typography.caption,
                color = colors.onSurfaceVariant,
            )
            if (state.failure != null || state.identityMismatch) {
                Text(stringResource(R.string.editor_edit_progress_notice), color = colors.error)
            }
        }
        CareerCompassButton(
            text = stringResource(if (selectedIndex < items.lastIndex) R.string.editor_edit_next else R.string.editor_edit_progress),
            onClick = {
                if (selectedIndex < items.lastIndex) {
                    onIntent(ApplicationProgressIntent.SelectItem(items[selectedIndex + 1].id))
                } else {
                    onBackClick()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(spacing.large),
        )
    }
}

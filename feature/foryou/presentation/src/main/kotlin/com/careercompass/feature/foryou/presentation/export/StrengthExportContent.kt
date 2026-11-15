package com.careercompass.feature.foryou.presentation.export

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureAction
import com.careercompass.core.ui.failure.actionLabel
import com.careercompass.core.ui.failure.description
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.title
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.presentation.R

@Composable
internal fun StrengthExportContent(
    state: StrengthExportUiState,
    onIntent: (StrengthExportIntent) -> Unit,
    onBackClick: () -> Unit,
    onAddExperience: () -> Unit,
    onCompleteProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CareerCompassTheme.spacing
    Column(modifier = modifier.fillMaxSize().background(CareerCompassTheme.colors.subtleSurface)) {
        CareerCompassTopAppBar(title = stringResource(R.string.foryou_export_title), onBackClick = onBackClick)
        LazyColumn(contentPadding = PaddingValues(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            item { Text(stringResource(R.string.foryou_export_description), style = CareerCompassTheme.typography.bodyMedium) }
            if (state.hasExperiences == false) {
                item {
                    CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.foryou_export_no_experiences), style = CareerCompassTheme.typography.labelMedium)
                        Text(
                            stringResource(R.string.foryou_export_no_experiences_description),
                            style = CareerCompassTheme.typography.bodyMedium,
                        )
                        CareerCompassButton(
                            text = stringResource(R.string.foryou_export_add_experience),
                            onClick = onAddExperience,
                            variant = CareerCompassButtonVariant.Ghost,
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.foryou_export_sections), style = CareerCompassTheme.typography.headline2)
                ExportSection.entries.forEach { section ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                                value = section in state.sections,
                                enabled = !state.isGenerating,
                                role = Role.Checkbox,
                                onValueChange = { onIntent(StrengthExportIntent.ToggleSection(section)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = section in state.sections, onCheckedChange = null, enabled = !state.isGenerating)
                        Text(stringResource(section.labelRes()), style = CareerCompassTheme.typography.bodyMedium)
                    }
                }
                if (state.sections.isEmpty()) {
                    Text(stringResource(R.string.foryou_export_select_section), color = CareerCompassTheme.colors.error)
                }
            }
            item {
                Text(stringResource(R.string.foryou_export_format), style = CareerCompassTheme.typography.headline2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    ExportFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.format == format,
                            onClick = { onIntent(StrengthExportIntent.SelectFormat(format)) },
                            enabled = !state.isGenerating,
                            label = { Text(stringResource(format.labelRes())) },
                        )
                    }
                }
            }
            item {
                CareerCompassButton(
                    text = stringResource(if (state.isGenerating) R.string.foryou_export_generating else R.string.foryou_export_generate),
                    onClick = { onIntent(StrengthExportIntent.GeneratePreview) },
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isGenerating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = spacing.small))
            }
            state.failure?.let { failure ->
                item {
                    val display = failure.display()
                    CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(display.title(), style = CareerCompassTheme.typography.labelMedium)
                        Text(display.description(), style = CareerCompassTheme.typography.bodyMedium)
                        val action =
                            when (display.action) {
                                FailureAction.Retry -> ({ onIntent(StrengthExportIntent.GeneratePreview) })
                                FailureAction.CompleteProfile -> onCompleteProfile
                                else -> null
                            }
                        val label = display.actionLabel()
                        if (action != null && label != null) CareerCompassButton(text = label, onClick = action)
                    }
                }
            }
            state.preview?.let { preview ->
                item {
                    Text(
                        stringResource(R.string.foryou_export_preview, stringResource(preview.format.labelRes())),
                        style = CareerCompassTheme.typography.headline2,
                    )
                    if (preview.content.isBlank()) {
                        Text(stringResource(R.string.foryou_export_empty), style = CareerCompassTheme.typography.bodyMedium)
                        CareerCompassButton(text = stringResource(R.string.foryou_export_add_experience), onClick = onAddExperience)
                    } else {
                        CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
                            // HTML도 문자열 그대로 미리 보고 복사한다. 서버 본문을 WebView에서 실행하지 않는다.
                            SelectionContainer { Text(preview.content, style = CareerCompassTheme.typography.bodyMedium) }
                        }
                    }
                    CareerCompassButton(
                        text = stringResource(R.string.foryou_export_copy),
                        onClick = { onIntent(StrengthExportIntent.CopyPreview) },
                        enabled = state.canCopy,
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.medium),
                    )
                }
            }
        }
    }
}

@StringRes
private fun ExportSection.labelRes(): Int =
    when (this) {
        ExportSection.Basic -> R.string.foryou_export_section_basic
        ExportSection.Skills -> R.string.foryou_export_section_skills
        ExportSection.Projects -> R.string.foryou_export_section_projects
        ExportSection.Awards -> R.string.foryou_export_section_awards
        ExportSection.Summary -> R.string.foryou_export_section_summary
    }

@StringRes
private fun ExportFormat.labelRes(): Int =
    when (this) {
        ExportFormat.Markdown -> R.string.foryou_export_format_markdown
        ExportFormat.Notion -> R.string.foryou_export_format_notion
        ExportFormat.Html -> R.string.foryou_export_format_html
        ExportFormat.Plain -> R.string.foryou_export_format_plain
    }

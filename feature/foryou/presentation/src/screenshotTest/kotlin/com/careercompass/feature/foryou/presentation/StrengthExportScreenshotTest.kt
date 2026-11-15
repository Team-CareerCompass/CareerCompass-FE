package com.careercompass.feature.foryou.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.presentation.export.StrengthExportContent
import com.careercompass.feature.foryou.presentation.export.StrengthExportUiState

@PreviewTest
@Preview(name = "Strength export selections", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun StrengthExportSelectionsPreview() {
    ExportPreview(StrengthExportUiState(hasExperiences = true))
}

@PreviewTest
@Preview(name = "Strength export no experiences", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun StrengthExportNoExperiencesPreview() {
    ExportPreview(StrengthExportUiState(hasExperiences = false))
}

@PreviewTest
@Preview(name = "Strength export result", widthDp = 360, heightDp = 1120, locale = "ko")
@Composable
public fun StrengthExportResultPreview() {
    ExportPreview(StrengthExportUiState(preview = StrengthExport(ExportFormat.Markdown, "# 내 강점\n모바일 앱을 만들고 사용자 경험을 개선했습니다.")))
}

@Composable
private fun ExportPreview(state: StrengthExportUiState) {
    CareerCompassTheme { StrengthExportContent(state, {}, {}, {}, {}) }
}

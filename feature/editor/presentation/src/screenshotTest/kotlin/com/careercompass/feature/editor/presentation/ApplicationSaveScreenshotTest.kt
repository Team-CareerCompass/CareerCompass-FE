package com.careercompass.feature.editor.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState
import com.careercompass.feature.editor.presentation.save.ApplicationSaveOverflowDialog
import com.careercompass.feature.editor.presentation.save.ApplicationSaveState
import com.careercompass.feature.editor.presentation.save.ApplicationSaveStatus
import com.careercompass.feature.editor.presentation.save.ApplicationSavedScreen

@PreviewTest
@Preview(name = "Save overflow", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ApplicationSaveOverflowPreview() {
    CareerCompassTheme {
        ApplicationSaveOverflowDialog(
            ApplicationProgressUiState(
                draft =
                    ApplicationDraft(
                        42,
                        ApplicationStatus.Ready,
                        listOf(
                            ApplicationItem(1, 1, "강점과 약점", 400, ApplicationItemStatus.Done, "가".repeat(428)),
                        ),
                    ),
            ),
            {},
        )
    }
}

@PreviewTest
@Preview(name = "Save stopped", widthDp = 360, heightDp = 400, locale = "ko")
@Composable
public fun ApplicationSaveStoppedPreview() {
    CareerCompassTheme { ApplicationSaveStatus(ApplicationSaveState(failure = FailureKind.NoConnection, autoSaveStopped = true), {}) }
}

@PreviewTest
@Preview(name = "Save complete", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ApplicationSavedPreview() {
    CareerCompassTheme { ApplicationSavedScreen({}) }
}

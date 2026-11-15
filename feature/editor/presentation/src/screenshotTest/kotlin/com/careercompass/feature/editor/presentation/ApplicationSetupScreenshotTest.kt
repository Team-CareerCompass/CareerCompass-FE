package com.careercompass.feature.editor.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.presentation.setup.ApplicationItemEditorState
import com.careercompass.feature.editor.presentation.setup.ApplicationSetupContent
import com.careercompass.feature.editor.presentation.setup.ApplicationSetupUiState
import com.careercompass.feature.editor.presentation.setup.component.ApplicationItemEditSheet

@PreviewTest
@Preview(name = "Application setup recognized", widthDp = 360, heightDp = 800)
@Composable
public fun ApplicationSetupRecognizedPreview() {
    ApplicationSetupPreviewSurface(state = setupPreviewState())
}

/** 인식된 문항이 없는 자리 — 실패가 아니라 직접 쓰는 화면이다(F4-1). */
@PreviewTest
@Preview(name = "Application setup empty recognition", widthDp = 360, heightDp = 800)
@Composable
public fun ApplicationSetupEmptyRecognitionPreview() {
    ApplicationSetupPreviewSurface(
        state = setupPreviewState().copy(items = emptyList(), recognizedItems = emptyList()),
    )
}

@PreviewTest
@Preview(name = "Application setup creating", widthDp = 360, heightDp = 800)
@Composable
public fun ApplicationSetupCreatingPreview() {
    ApplicationSetupPreviewSurface(state = setupPreviewState().copy(isCreating = true))
}

@PreviewTest
@Preview(name = "Application item editor", widthDp = 360, heightDp = 420)
@Composable
public fun ApplicationItemEditorPreview() {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            ApplicationItemEditSheet(
                state =
                    ApplicationItemEditorState(
                        order = 1,
                        question = "지원 동기를 작성해 주세요",
                        maxChars = "500",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "Application item editor errors", widthDp = 360, heightDp = 420)
@Composable
public fun ApplicationItemEditorErrorPreview() {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            ApplicationItemEditSheet(
                state =
                    ApplicationItemEditorState(
                        order = null,
                        maxChars = "20",
                        questionError = ProfileFieldViolation.Required,
                        maxCharsError = ProfileFieldViolation.OutOfRange,
                    ),
                onEvent = {},
            )
        }
    }
}

@Composable
private fun ApplicationSetupPreviewSurface(state: ApplicationSetupUiState) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.subtleSurface) {
            ApplicationSetupContent(state = state, onEvent = {})
        }
    }
}

private fun setupPreviewState(): ApplicationSetupUiState {
    val items =
        listOf(
            ApplicationItemDraft(order = 1, question = "지원 동기를 작성해 주세요", maxChars = 500),
            ApplicationItemDraft(order = 2, question = "본인의 강점과 약점을 서술해 주세요", maxChars = 400),
            ApplicationItemDraft(order = 3, question = "도전했던 경험을 기술해 주세요", maxChars = null),
        )
    return ApplicationSetupUiState(
        postingTitle = "카카오 SW 인턴십",
        items = items,
        recognizedItems = items,
        tone = ApplicationTone.Formal,
    )
}

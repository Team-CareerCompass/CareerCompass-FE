package com.careercompass.feature.editor.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.presentation.editor.ApplicationEditorContent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState

@PreviewTest
@Preview(name = "Editor answer", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ApplicationEditorPreview() {
    CareerCompassTheme { ApplicationEditorContent(editorState(), {}, {}) }
}

@PreviewTest
@Preview(name = "Editor overflow", widthDp = 360, heightDp = 1000, locale = "ko")
@Composable
public fun ApplicationEditorOverflowPreview() {
    CareerCompassTheme {
        ApplicationEditorContent(editorState().copy(editedAnswers = mapOf(1L to "경험을 글자 수 제한보다 길게 직접 작성하고 있어요.\n입력한 글은 잘리지 않아요.")), {}, {})
    }
}

@PreviewTest
@Preview(name = "Editor large font", widthDp = 360, heightDp = 1000, locale = "ko", fontScale = 2f)
@Composable
public fun ApplicationEditorLargeFontPreview() {
    CareerCompassTheme { ApplicationEditorContent(editorState(), {}, {}) }
}

private fun editorState() =
    ApplicationProgressUiState(
        draft =
            ApplicationDraft(
                42,
                ApplicationStatus.Ready,
                (1..10).map { index ->
                    ApplicationItem(
                        index.toLong(),
                        index,
                        "본인의 강점과 약점을 서술해 주세요",
                        40,
                        ApplicationItemStatus.Done,
                        "사용자의 불편을 발견하고 해결하는 실행력이 제 강점입니다.",
                    )
                },
            ),
        selectedItemId = 1,
    )

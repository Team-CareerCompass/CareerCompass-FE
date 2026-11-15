package com.careercompass.feature.editor.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressContent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState

@PreviewTest
@Preview(name = "Progress partial", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ApplicationProgressPartialPreview() {
    CareerCompassTheme {
        ApplicationProgressContent(
            ApplicationProgressUiState(draft = previewDraft(ApplicationItemStatus.Loading), isStreaming = true),
            {},
            {},
            {},
        )
    }
}

@PreviewTest
@Preview(name = "Progress failed", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ApplicationProgressFailedPreview() {
    CareerCompassTheme {
        ApplicationProgressContent(ApplicationProgressUiState(draft = previewDraft(ApplicationItemStatus.Failed)), {}, {}, {})
    }
}

private fun previewDraft(second: ApplicationItemStatus) =
    ApplicationDraft(
        id = 42,
        status = ApplicationStatus.Generating,
        items =
            listOf(
                ApplicationItem(1, 1, "지원 동기를 작성해 주세요", 500, ApplicationItemStatus.Done, "모바일 프로젝트를 통해 사용자의 불편을 해결하는 경험을 쌓았습니다."),
                ApplicationItem(2, 2, "협업 경험을 작성해 주세요", 400, second, null),
            ),
    )

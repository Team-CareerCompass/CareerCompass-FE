package com.careercompass.feature.profile.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.pastapplication.PastApplicationAddOptionsContent
import com.careercompass.feature.profile.presentation.pastapplication.PastApplicationFileUploadContent
import com.careercompass.feature.profile.presentation.pastapplication.PastApplicationFileUploadState

@PreviewTest
@Preview(name = "Upload options", widthDp = 360, heightDp = 360, locale = "ko")
@Preview(name = "Upload options large font", widthDp = 360, heightDp = 640, fontScale = 2f, locale = "ko")
@Composable
public fun PastApplicationUploadOptionsPreview() {
    CareerCompassTheme { Surface { PastApplicationAddOptionsContent(onEvent = {}) } }
}

@PreviewTest
@Preview(name = "Upload name", widthDp = 360, heightDp = 640, locale = "ko")
@Preview(name = "Upload name large font", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "ko")
@Composable
public fun PastApplicationUploadNamePreview() {
    CareerCompassTheme { Surface { PastApplicationFileUploadContent(state = uploadPreviewState(), onEvent = {}) } }
}

@PreviewTest
@Preview(name = "Upload retry", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun PastApplicationUploadRetryPreview() {
    CareerCompassTheme {
        Surface { PastApplicationFileUploadContent(state = uploadPreviewState().copy(failure = FailureKind.NoConnection), onEvent = {}) }
    }
}

private fun uploadPreviewState() =
    PastApplicationFileUploadState(
        file = UploadFile("2026 카카오 인턴 지원서.pdf", 1) { byteArrayOf(1).inputStream() },
        label = "2026 카카오 인턴 지원서",
    )

package com.careercompass.feature.onboarding.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.component.DirectInputSheet
import com.careercompass.core.ui.component.DirectInputState
import com.careercompass.core.ui.theme.CareerCompassTheme

@PreviewTest
@Preview(name = "Direct input empty", widthDp = 360, heightDp = 800)
@Composable
public fun DirectInputEmptyPreview() {
    DirectInputPreviewHost(state = DirectInputState())
}

@PreviewTest
@Preview(name = "Direct input filled", widthDp = 360, heightDp = 800)
@Composable
public fun DirectInputFilledPreview() {
    DirectInputPreviewHost(
        state =
            DirectInputState(
                label = "2024 카카오 인턴 자소서",
                content = "지원 동기: 사용자에게 닿는 제품을 만들고 싶어 지원했습니다.\n\n성장 배경: ...",
            ),
    )
}

@PreviewTest
@Preview(name = "Direct input errors", widthDp = 360, heightDp = 800)
@Composable
public fun DirectInputErrorPreview() {
    DirectInputPreviewHost(
        state =
            DirectInputState(
                labelError = ProfileFieldViolation.Required,
                contentError = ProfileFieldViolation.Required,
            ),
    )
}

@Composable
private fun DirectInputPreviewHost(state: DirectInputState) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            DirectInputSheet(state = state, onEvent = {})
        }
    }
}

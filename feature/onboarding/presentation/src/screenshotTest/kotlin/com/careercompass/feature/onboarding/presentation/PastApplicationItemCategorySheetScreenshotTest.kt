package com.careercompass.feature.onboarding.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.ui.component.PastApplicationItemCategorySheet
import com.careercompass.core.ui.component.PastApplicationItemCategoryState
import com.careercompass.core.ui.theme.CareerCompassTheme

@PreviewTest
@Preview(name = "Past application item category unsure", widthDp = 360, heightDp = 800)
@Composable
public fun PastApplicationItemCategoryUnsurePreview() {
    PastApplicationItemCategoryPreviewHost(
        state =
            PastApplicationItemCategoryState(
                documentId = "remote-9",
                itemId = 2L,
                contentPreview = "동아리에서 팀장을 맡아 6명과 함께 서비스를 만들었습니다. 일정과 역할을 나누며 협업하는 법을 배웠습니다.",
                selected = PastApplicationCategory.Other,
            ),
    )
}

@PreviewTest
@Preview(name = "Past application item category motivation", widthDp = 360, heightDp = 800)
@Composable
public fun PastApplicationItemCategoryMotivationPreview() {
    PastApplicationItemCategoryPreviewHost(
        state =
            PastApplicationItemCategoryState(
                documentId = "remote-9",
                itemId = 1L,
                contentPreview = "사용자에게 닿는 제품을 만들고 싶어 지원했습니다.",
                selected = PastApplicationCategory.Motivation,
            ),
    )
}

@Composable
private fun PastApplicationItemCategoryPreviewHost(state: PastApplicationItemCategoryState) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            PastApplicationItemCategorySheet(state = state, onEvent = {})
        }
    }
}

package com.careercompass.feature.onboarding.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.user.SchoolCatalog
import com.careercompass.core.ui.component.SchoolDirectInputState
import com.careercompass.core.ui.component.SchoolPickerSheet
import com.careercompass.core.ui.component.SchoolPickerState
import com.careercompass.core.ui.theme.CareerCompassTheme

@PreviewTest
@Preview(name = "School picker default", widthDp = 360, heightDp = 800)
@Composable
public fun SchoolPickerDefaultPreview() {
    SchoolPickerPreviewHost(state = SchoolPickerState(results = SchoolCatalog.search("")))
}

@PreviewTest
@Preview(name = "School picker filtered", widthDp = 360, heightDp = 800)
@Composable
public fun SchoolPickerFilteredPreview() {
    SchoolPickerPreviewHost(state = SchoolPickerState(query = "건국", results = SchoolCatalog.search("건국")))
}

/** 0건이어도 「직접 입력」 탈출구가 함께 보여야 한다 — 문구만 남으면 앱 전체가 막힌다(#138). */
@PreviewTest
@Preview(name = "School picker empty", widthDp = 360, heightDp = 800)
@Composable
public fun SchoolPickerEmptyPreview() {
    SchoolPickerPreviewHost(state = SchoolPickerState(query = "없는 학교", results = emptyList()))
}

@PreviewTest
@Preview(name = "School picker direct input", widthDp = 360, heightDp = 800)
@Composable
public fun SchoolPickerDirectInputPreview() {
    SchoolPickerPreviewHost(
        state =
            SchoolPickerState(
                query = "서울예술",
                results = emptyList(),
                directInput = SchoolDirectInputState(value = "서울예술대학교"),
            ),
    )
}

@Composable
private fun SchoolPickerPreviewHost(state: SchoolPickerState) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            SchoolPickerSheet(state = state, onEvent = {})
        }
    }
}

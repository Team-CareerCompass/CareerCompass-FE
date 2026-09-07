package com.careercompass.feature.onboarding.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.component.GraduationDatePickerSheet
import com.careercompass.core.ui.component.GraduationPickerState
import com.careercompass.core.ui.theme.CareerCompassTheme

@PreviewTest
@Preview(name = "Graduation picker default", widthDp = 360, heightDp = 800)
@Composable
public fun GraduationDatePickerDefaultPreview() {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            GraduationDatePickerSheet(
                state =
                    GraduationPickerState(
                        years = (2000..2032).toList(),
                        selectedYear = 2027,
                        selectedMonth = 2,
                    ),
                onEvent = {},
            )
        }
    }
}

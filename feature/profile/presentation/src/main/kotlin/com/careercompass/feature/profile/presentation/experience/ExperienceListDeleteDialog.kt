package com.careercompass.feature.profile.presentation.experience

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceDeleteState
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.core.ui.R as CoreUiR

/** 온보딩의 즉시 닫기와 달리, profile 은 삭제 응답을 기다리므로 그동안 확인·닫기를 잠근다. */
@Composable
internal fun ExperienceListDeleteDialog(
    state: ExperienceDeleteState,
    isDeleting: Boolean,
    onEvent: (ExperienceDeleteEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onEvent(ExperienceDeleteEvent.Dismissed) },
        confirmButton = {
            CareerCompassButton(
                text = stringResource(CoreUiR.string.core_ui_experience_delete_confirm),
                onClick = { onEvent(ExperienceDeleteEvent.Confirmed) },
                variant = CareerCompassButtonVariant.Danger,
                size = CareerCompassButtonSize.Small,
                enabled = !isDeleting,
            )
        },
        dismissButton = {
            CareerCompassButton(
                text = stringResource(CoreUiR.string.core_ui_sheet_cancel),
                onClick = { onEvent(ExperienceDeleteEvent.Dismissed) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
                enabled = !isDeleting,
            )
        },
        modifier = modifier,
        title = { Text(stringResource(CoreUiR.string.core_ui_experience_delete_title)) },
        text = { Text(stringResource(CoreUiR.string.core_ui_experience_delete_message, state.title)) },
        containerColor = CareerCompassTheme.colors.surface,
        titleContentColor = CareerCompassTheme.colors.onSurface,
        textContentColor = CareerCompassTheme.colors.onSurfaceVariant,
    )
}

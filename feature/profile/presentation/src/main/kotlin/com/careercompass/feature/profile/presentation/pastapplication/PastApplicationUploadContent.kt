package com.careercompass.feature.profile.presentation.pastapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassTextField
import com.careercompass.core.ui.component.CareerCompassTextFieldSize
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.sentence
import com.careercompass.core.ui.failure.toMessage
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R

@Composable
internal fun PastApplicationAddOptionsContent(
    onEvent: (PastApplicationListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(CareerCompassTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
    ) {
        Text(
            stringResource(R.string.profile_upload_choose_title),
            modifier = Modifier.semantics { heading() },
            style = CareerCompassTheme.typography.headline4,
            color = CareerCompassTheme.colors.onSurface,
        )
        Text(
            stringResource(R.string.profile_upload_formats),
            style = CareerCompassTheme.typography.bodyMedium,
            color = CareerCompassTheme.colors.onSurfaceVariant,
        )
        CareerCompassButton(
            text = stringResource(R.string.profile_upload_file_action),
            onClick = { onEvent(PastApplicationListEvent.FileUploadClicked) },
            modifier = Modifier.fillMaxWidth(),
            size = CareerCompassButtonSize.Large,
        )
        CareerCompassButton(
            text = stringResource(R.string.profile_upload_direct_action),
            onClick = { onEvent(PastApplicationListEvent.DirectInputClicked) },
            modifier = Modifier.fillMaxWidth(),
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Large,
        )
    }
}

@Composable
internal fun PastApplicationFileUploadContent(
    state: PastApplicationFileUploadState,
    onEvent: (PastApplicationFileUploadIntent) -> Unit,
    modifier: Modifier = Modifier,
    canSubmit: Boolean = true,
) {
    val display = state.failure?.display(FailureSurface.Application)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(CareerCompassTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
    ) {
        Text(
            stringResource(R.string.profile_upload_title),
            modifier = Modifier.semantics { heading() },
            style = CareerCompassTheme.typography.headline4,
            color = CareerCompassTheme.colors.onSurface,
        )
        Text(
            stringResource(R.string.profile_upload_selected_file, state.file?.fileName.orEmpty()),
            style = CareerCompassTheme.typography.caption,
            color = CareerCompassTheme.colors.onSurfaceVariant,
        )
        CareerCompassTextField(
            value = state.label,
            onValueChange = { onEvent(PastApplicationFileUploadIntent.LabelChanged(it)) },
            label = stringResource(R.string.profile_upload_label),
            supportingText = stringResource(R.string.profile_upload_hint),
            errorMessage = state.labelError?.toMessage(),
            isError = state.labelError != null,
            enabled = !state.isSubmitting,
            size = CareerCompassTextFieldSize.Large,
        )
        if (display != null) {
            Text(
                text = display.sentence(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.actionDanger,
            )
        }
        CareerCompassButton(
            text =
                stringResource(
                    when {
                        state.isSubmitting -> R.string.profile_upload_progress
                        display?.isRetryable == true -> com.careercompass.core.ui.R.string.core_ui_state_retry
                        else -> R.string.profile_upload_submit
                    },
                ),
            onClick = { onEvent(PastApplicationFileUploadIntent.Submit) },
            modifier = Modifier.fillMaxWidth(),
            size = CareerCompassButtonSize.Large,
            enabled = canSubmit && !state.isSubmitting && (display == null || display.isRetryable),
        )
        CareerCompassButton(
            text = stringResource(com.careercompass.core.ui.R.string.core_ui_sheet_cancel),
            onClick = { onEvent(PastApplicationFileUploadIntent.Dismiss) },
            modifier = Modifier.fillMaxWidth(),
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Large,
            enabled = !state.isSubmitting,
        )
    }
}

package com.careercompass.feature.onboarding.presentation.pastapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassTextField
import com.careercompass.core.ui.component.CareerCompassTextFieldSize
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.onboarding.presentation.R
import com.careercompass.feature.onboarding.presentation.shared.util.toMessage

/**
 * Step 4 「직접 입력하기」 시트의 본문. 시트 컨테이너는 호스트가 감싼다.
 *
 * 본문은 여러 줄이라 디자인 시스템 단일 행 입력 대신 같은 테두리 규칙을 따르는 다중 행 입력을 쓴다.
 */
@Composable
public fun DirectInputSheet(
    state: DirectInputState,
    onEvent: (DirectInputEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.onboarding_direct_input_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = CareerCompassTheme.typography.headline4,
        )
        CareerCompassTextField(
            value = state.label,
            onValueChange = { onEvent(DirectInputEvent.LabelChanged(it)) },
            label = stringResource(R.string.onboarding_direct_input_label_label),
            placeholder = stringResource(R.string.onboarding_direct_input_label_placeholder),
            errorMessage = state.labelError?.let { it.toMessage() },
            isError = state.labelError != null,
            enabled = state.isInputEnabled,
            size = CareerCompassTextFieldSize.Large,
        )
        ContentField(
            value = state.content,
            onValueChange = { onEvent(DirectInputEvent.ContentChanged(it)) },
            errorMessage = state.contentError?.let { it.toMessage() },
            enabled = state.isInputEnabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            CareerCompassButton(
                text = stringResource(com.careercompass.core.ui.R.string.core_ui_sheet_cancel),
                onClick = { onEvent(DirectInputEvent.Dismissed) },
                modifier = Modifier.weight(1f),
                variant = CareerCompassButtonVariant.Secondary,
                size = CareerCompassButtonSize.Large,
                enabled = state.isInputEnabled,
            )
            CareerCompassButton(
                text = stringResource(R.string.onboarding_direct_input_submit),
                onClick = { onEvent(DirectInputEvent.Submitted) },
                modifier = Modifier.weight(1f),
                size = CareerCompassButtonSize.Large,
                enabled = state.isSubmitEnabled,
            )
        }
    }
}

@Composable
private fun ContentField(
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    enabled: Boolean,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val label = stringResource(R.string.onboarding_direct_input_content_label)
    val placeholder = stringResource(R.string.onboarding_direct_input_content_placeholder)
    val borderColor =
        when {
            !enabled -> colors.subtleOutline
            errorMessage != null -> colors.actionDanger
            else -> colors.interactiveOutline
        }
    val textStyle =
        CareerCompassTheme.typography.bodyMedium.copy(
            color = if (enabled) colors.onSurface else colors.disabledContent,
        )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = label,
            color = if (enabled) colors.onSurfaceVariant else colors.disabledContent,
            style = CareerCompassTheme.typography.labelMedium,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = CONTENT_MIN_HEIGHT)
                    .background(
                        color = if (enabled) colors.surface else colors.disabledContainer,
                        shape = CareerCompassTheme.shapes.largeControl,
                    ).border(
                        width = 1.dp,
                        color = borderColor,
                        shape = CareerCompassTheme.shapes.largeControl,
                    ).semantics {
                        contentDescription = label
                        if (errorMessage != null) error(errorMessage)
                    },
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primaryEmphasis),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(spacing.large)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.mutedContent,
                            style = CareerCompassTheme.typography.bodyMedium,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = colors.actionDanger,
                style = CareerCompassTheme.typography.caption,
            )
        }
    }
}

private val CONTENT_MIN_HEIGHT = 160.dp

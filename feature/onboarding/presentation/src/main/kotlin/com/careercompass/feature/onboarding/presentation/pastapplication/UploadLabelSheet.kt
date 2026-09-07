package com.careercompass.feature.onboarding.presentation.pastapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassTextField
import com.careercompass.core.ui.component.CareerCompassTextFieldSize
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.onboarding.presentation.R
import com.careercompass.feature.onboarding.presentation.shared.util.toMessage

/**
 * 파일 업로드 라벨 확인 시트의 본문. 시트 컨테이너는 호스트가 감싼다.
 *
 * 고른 파일 이름을 함께 보여 준다 — 여러 개를 올리는 도중에는 지금 무엇에 이름을 붙이는지가 라벨보다 먼저
 * 헷갈린다. 업로드 뒤에는 이름을 못 바꾼다는 사실도 여기서만 알릴 수 있다(서버에 수정 엔드포인트가 없다).
 */
@Composable
public fun UploadLabelSheet(
    state: UploadLabelState,
    onEvent: (UploadLabelEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.large, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.onboarding_upload_label_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = CareerCompassTheme.typography.headline4,
        )
        Text(
            text = stringResource(R.string.onboarding_upload_label_selected_file, state.fileName),
            color = colors.mutedContent,
            maxLines = FILE_NAME_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            style = CareerCompassTheme.typography.caption,
        )
        CareerCompassTextField(
            value = state.label,
            onValueChange = { onEvent(UploadLabelEvent.LabelChanged(it)) },
            label = stringResource(R.string.onboarding_upload_label_field_label),
            placeholder = stringResource(R.string.onboarding_upload_label_field_placeholder),
            supportingText = stringResource(R.string.onboarding_upload_label_hint),
            errorMessage = state.labelError?.let { it.toMessage() },
            isError = state.labelError != null,
            size = CareerCompassTextFieldSize.Large,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            CareerCompassButton(
                text = stringResource(com.careercompass.core.ui.R.string.core_ui_sheet_cancel),
                onClick = { onEvent(UploadLabelEvent.Dismissed) },
                modifier = Modifier.weight(1f),
                variant = CareerCompassButtonVariant.Secondary,
                size = CareerCompassButtonSize.Large,
            )
            CareerCompassButton(
                text = stringResource(R.string.onboarding_upload_label_submit),
                onClick = { onEvent(UploadLabelEvent.Submitted) },
                modifier = Modifier.weight(1f),
                size = CareerCompassButtonSize.Large,
                enabled = state.isSubmitEnabled,
            )
        }
    }
}

private const val FILE_NAME_MAX_LINES = 2

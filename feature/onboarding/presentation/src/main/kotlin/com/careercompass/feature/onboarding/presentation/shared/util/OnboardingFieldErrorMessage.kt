package com.careercompass.feature.onboarding.presentation.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.careercompass.core.ui.R
import com.careercompass.feature.onboarding.presentation.shared.model.OnboardingFieldError

/**
 * 입력 오류 문구 — 문구 자체는 `core:ui` 가 갖는다(`core_ui_field_*`).
 *
 * 같은 다섯 필드를 마이 탭의 프로필 편집도 받으므로(#176) 문구를 모듈마다 두면 두 벌이 된다. 이 함수는
 * 온보딩의 오류 타입을 그 문구에 잇기만 한다.
 */
@Composable
@ReadOnlyComposable
internal fun OnboardingFieldError.toMessage(): String =
    when (this) {
        OnboardingFieldError.Required -> stringResource(R.string.core_ui_field_required)
        is OnboardingFieldError.TooLong -> stringResource(R.string.core_ui_field_too_long, maxLength)
        OnboardingFieldError.InvalidFormat -> stringResource(R.string.core_ui_field_invalid_format)
        OnboardingFieldError.OutOfRange -> stringResource(R.string.core_ui_field_out_of_range)
    }

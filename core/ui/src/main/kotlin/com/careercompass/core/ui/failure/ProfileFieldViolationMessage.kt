package com.careercompass.core.ui.failure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.R

/**
 * 입력 필드 판정을 사용자 문구로 옮긴다 — **문구의 정본은 여기 하나다.**
 *
 * 같은 다섯 필드를 온보딩 Step 1 과 마이 탭의 프로필 편집이 받는다(#176). 판정을 `core:model` 로 올리고도
 * 문구를 화면 모듈마다 두면 「필수 입력이에요」가 두 벌이 되고, 한쪽만 고쳐지는 날이 온다.
 */
@Composable
@ReadOnlyComposable
public fun ProfileFieldViolation.toMessage(): String =
    when (this) {
        ProfileFieldViolation.Required -> stringResource(R.string.core_ui_field_required)
        is ProfileFieldViolation.TooLong -> stringResource(R.string.core_ui_field_too_long, maxLength)
        ProfileFieldViolation.InvalidFormat -> stringResource(R.string.core_ui_field_invalid_format)
        ProfileFieldViolation.OutOfRange -> stringResource(R.string.core_ui_field_out_of_range)
    }

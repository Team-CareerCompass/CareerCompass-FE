package com.careercompass.feature.profile.presentation.home.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.settings.ThemeMode
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.ProfileHomeEvent
import com.careercompass.feature.profile.presentation.home.ProfileHomeUiState

/**
 * 설정 줄의 최소 높이 — 손가락이 닿는 자리는 48dp 아래로 내려가지 않는다(WCAG 2.5.5 · ATF `TouchTargetSizeCheck`).
 *
 * 줄 자체가 눌리는 화면 테마 줄은 글자 높이(약 21dp)로 줄어들어 그대로 두면 검사에 걸린다. 지문 줄은 스위치가
 * 눌리는 자리라 스위치 쪽에 [minimumInteractiveComponentSize] 를 따로 건다 — 줄을 키우는 것만으로는 스위치
 * 노드의 크기가 그대로다.
 */
private val SETTING_ROW_MIN_HEIGHT = 48.dp

/** 계측 테스트가 지문 스위치를 찾는 시맨틱 태그 — 스위치 자체에는 읽을 문구가 없다. */
public const val PROFILE_BIOMETRIC_SWITCH_TAG: String = "profile_biometric_switch"

/** 화면 테마 줄을 찾는 시맨틱 태그 — 라벨과 현재 값이 한 줄에 병합돼 문구만으로는 집기 어렵다. */
public const val PROFILE_THEME_ROW_TAG: String = "profile_theme_row"

/**
 * 계정 설정 — 지문 로그인 · 화면 테마 · 로그아웃.
 *
 * ### 시안에 없는 섹션인 이유
 * Figma 05 · 01 은 이 셋을 상단 톱니(⚙) 뒤의 설정 화면으로 미뤄 두었고, 그 화면은 아직 없다. 자리표시자가
 * 갖고 있던 이 셋을 **어느 것 하나도 잃지 않는 것**이 이 화면의 완료 조건이라(#175) 마이 홈에 그대로 얹었다.
 * 설정 화면이 생기면 이 컴포저블을 통째로 그쪽으로 옮긴다 — 화면 안 위치만 바뀌고 계약은 그대로다.
 */
@Composable
internal fun ProfileAccountSection(
    state: ProfileHomeUiState,
    onEvent: (ProfileHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text = stringResource(R.string.profile_home_section_account),
            style = CareerCompassTheme.typography.caption,
            color = colors.mutedContent,
        )
        CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
            BiometricLoginSetting(state = state, onEvent = onEvent)
            Spacer(modifier = Modifier.height(spacing.small))
            ThemeModeSetting(state = state, onEvent = onEvent)
        }
        CareerCompassButton(
            text = stringResource(R.string.profile_home_logout),
            onClick = { onEvent(ProfileHomeEvent.LogoutClicked) },
            modifier = Modifier.fillMaxWidth(),
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Large,
            enabled = !state.isLoggingOut,
        )
    }
}

/**
 * 지문 빠른 로그인 스위치.
 *
 * 스위치가 그리는 값은 저장소의 등록 상태 하나뿐이라, 프롬프트를 취소했거나 등록이 실패하면 아무것도 되돌리지
 * 않아도 원래 자리에 남는다. 안내 문구는 등록할 수 없는 기기에서만 한 줄 붙는다 — 켜져 있는데 지문을 지운 기기는
 * 스위치를 끌 수 있어야 하므로 그때는 잠그지도 안내하지도 않는다(#113).
 */
@Composable
private fun BiometricLoginSetting(
    state: ProfileHomeUiState,
    onEvent: (ProfileHomeEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = SETTING_ROW_MIN_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_home_biometric_label),
            style = CareerCompassTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Switch(
            checked = state.isBiometricEnabled,
            onCheckedChange = { enabled -> onEvent(ProfileHomeEvent.BiometricToggled(enabled)) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag(PROFILE_BIOMETRIC_SWITCH_TAG),
            enabled = state.isBiometricSwitchEnabled,
        )
    }
    if (state.isBiometricUnavailableNoticeVisible) {
        Text(
            text = stringResource(R.string.profile_home_biometric_unavailable),
            style = CareerCompassTheme.typography.caption,
            color = colors.mutedContent,
        )
    }
}

/**
 * 화면 테마 — 지금 값을 줄에 적고, 누르면 셋 중 하나를 고르는 다이얼로그를 연다.
 *
 * 스위치가 아닌 이유는 값이 셋이기 때문이다. 「시스템 따름」과 「밝게」는 지금 기기가 밝을 때 **같은 화면을
 * 그리지만 뜻이 다르다** — 앞은 기기가 어두워지면 따라 어두워지고 뒤는 그대로 밝다. Boolean 하나로 접으면 그
 * 차이를 되돌릴 자리가 없어진다.
 */
@Composable
private fun ThemeModeSetting(
    state: ProfileHomeUiState,
    onEvent: (ProfileHomeEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SETTING_ROW_MIN_HEIGHT)
                .clickable { onEvent(ProfileHomeEvent.ThemeClicked) }
                .testTag(PROFILE_THEME_ROW_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_home_theme_label),
            style = CareerCompassTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(state.themeMode.labelRes()),
            style = CareerCompassTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 세 값을 라디오로 고른다. 고르는 즉시 닫히므로 확인 버튼을 두지 않는다 — 되돌리기가 같은 자리에서 한 번이다. */
@Composable
internal fun ProfileThemeModeDialog(
    selected: ThemeMode,
    onEvent: (ProfileHomeEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    AlertDialog(
        onDismissRequest = { onEvent(ProfileHomeEvent.ThemeDismissed) },
        confirmButton = {
            CareerCompassButton(
                text = stringResource(R.string.profile_home_theme_dialog_close),
                onClick = { onEvent(ProfileHomeEvent.ThemeDismissed) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
        },
        title = { Text(text = stringResource(R.string.profile_home_theme_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxSmall)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == selected,
                                    role = Role.RadioButton,
                                    onClick = { onEvent(ProfileHomeEvent.ThemeSelected(mode)) },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = null)
                        Spacer(modifier = Modifier.width(spacing.xSmall))
                        Text(
                            text = stringResource(mode.labelRes()),
                            style = CareerCompassTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

/** 로그아웃 확인 — 되돌릴 수 없는 조작이라 한 번 묻는다. */
@Composable
internal fun ProfileLogoutDialog(onEvent: (ProfileHomeEvent) -> Unit) {
    val colors = CareerCompassTheme.colors

    AlertDialog(
        onDismissRequest = { onEvent(ProfileHomeEvent.LogoutDismissed) },
        confirmButton = {
            CareerCompassButton(
                text = stringResource(R.string.profile_home_logout_dialog_confirm),
                onClick = { onEvent(ProfileHomeEvent.LogoutConfirmed) },
                variant = CareerCompassButtonVariant.Danger,
                size = CareerCompassButtonSize.Small,
            )
        },
        dismissButton = {
            CareerCompassButton(
                text = stringResource(R.string.profile_home_logout_dialog_cancel),
                onClick = { onEvent(ProfileHomeEvent.LogoutDismissed) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
        },
        title = { Text(text = stringResource(R.string.profile_home_logout_dialog_title)) },
        text = { Text(text = stringResource(R.string.profile_home_logout_dialog_message)) },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

@StringRes
private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.System -> R.string.profile_home_theme_system
        ThemeMode.Light -> R.string.profile_home_theme_light
        ThemeMode.Dark -> R.string.profile_home_theme_dark
    }

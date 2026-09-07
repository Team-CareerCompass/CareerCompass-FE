package com.careercompass.feature.profile.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.component.ProfileAccountSection
import com.careercompass.feature.profile.presentation.home.component.ProfileCompletionCard
import com.careercompass.feature.profile.presentation.home.component.ProfileLogoutDialog
import com.careercompass.feature.profile.presentation.home.component.ProfileMenuRow
import com.careercompass.feature.profile.presentation.home.component.ProfileSummaryCard
import com.careercompass.feature.profile.presentation.home.component.ProfileThemeModeDialog

/**
 * 마이 홈(Figma 05 · 01) — 상태 없는 본문. 프리뷰 · Robolectric 의 진입점이다.
 *
 * ### 이 화면의 엣지 상태 (`docs/spec/edge-states.md` §6 의 일곱 칸)
 * 1. **오프라인·네트워크 실패** — 캐시가 있으면 목록을 그대로 두고 스낵바 한 줄(Screen 이 띄운다). 캐시가
 *    없으면 화면 한 장을 [CareerCompassFailureState] 로 덮고 문구는 실패 표(#204)가 정한다. 스냅샷은
 *    저장하지 않으므로 「오프라인 모드로 보기」 경로가 없다.
 * 2. **로딩** — 첫 조회에만 화면 가운데 진행 표시. 캐시가 있으면 그리지 않는다(값이 이미 보인다).
 * 3. **빈 결과** — 없다. 프로필은 로그인한 사용자에게 언제나 하나 있고, 개수 0 은 배지를 떼는 것으로 끝난다.
 * 4. **권한 거부** — 없다. 이 화면은 어떤 런타임 권한도 요구하지 않는다.
 * 5. **서버 점검(503)** — 1번과 같은 길로 접힌다. 실패 표가 점검 전용 문구와 「다시 시도」를 준다.
 * 6. **세션 만료(401)** — 그리지 않는다. `sessionEnded` 를 올려 셸이 로그인으로 보낸다.
 * 7. **되돌릴 길** — 실패 화면이 전체를 덮는 것은 캐시가 없을 때뿐이고, 그때도 재시도 버튼과 하단 탭이
 *    남아 다른 탭으로 나갈 수 있다.
 */
@Composable
public fun ProfileHomeContent(
    state: ProfileHomeUiState,
    onEvent: (ProfileHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.subtleSurface),
    ) {
        CareerCompassTopAppBar(
            title = stringResource(R.string.profile_home_title),
            onBackClick = null,
        )
        val profile = state.profile
        val failure = state.loadFailure
        when {
            profile == null && failure != null -> ProfileHomeFailure(kind = failure, onEvent = onEvent)
            profile == null -> ProfileHomeLoading()
            else -> ProfileHomeBody(profile = profile, state = state, onEvent = onEvent)
        }
    }

    if (state.isThemeDialogVisible) {
        ProfileThemeModeDialog(selected = state.themeMode, onEvent = onEvent)
    }
    if (state.isLogoutDialogVisible) {
        ProfileLogoutDialog(onEvent = onEvent)
    }
}

@Composable
private fun ProfileHomeBody(
    profile: UserProfile,
    state: ProfileHomeUiState,
    onEvent: (ProfileHomeEvent) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        item(key = "summary") {
            ProfileSummaryCard(
                profile = profile,
                experienceCardCount = state.experienceCardCount,
                pastApplicationCount = state.pastApplicationCount,
            )
        }
        item(key = "completion") {
            ProfileCompletionCard(completion = profile.completion, gaps = state.completionGaps)
        }
        items(items = ProfileHomeMenu.entries, key = { it.name }) { menu ->
            ProfileMenuRow(
                title = stringResource(menu.titleRes()),
                description = stringResource(menu.descriptionRes()),
                count = state.menuCount(menu),
                onClick = { onEvent(ProfileHomeEvent.MenuClicked(menu)) },
            )
        }
        item(key = "account") {
            ProfileAccountSection(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun ProfileHomeLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.profile_home_loading),
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * 실패 표의 행을 그대로 그린다(#204). 재시도 버튼은 **표가 재시도를 처방했을 때만** 붙는다 — 같은 요청을
 * 다시 보내도 답이 갈리지 않는 실패에 버튼을 주면 사용자는 같은 실패를 한 번 더 만난다.
 */
@Composable
private fun ProfileHomeFailure(
    kind: FailureKind,
    onEvent: (ProfileHomeEvent) -> Unit,
) {
    val display = kind.display(surface = FailureSurface.Unspecified)
    CareerCompassFailureState(
        display = display,
        onActionClick = if (display.isRetryable) ({ onEvent(ProfileHomeEvent.RetryClicked) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun ProfileHomeUiState.menuCount(menu: ProfileHomeMenu): Int? =
    when (menu) {
        ProfileHomeMenu.ExperienceCards -> experienceCardCount

        ProfileHomeMenu.PastApplications -> pastApplicationCount

        // 프로필 편집·알림 설정은 셀 것이 없다 — 배지 자리를 비워 둔다.
        ProfileHomeMenu.ProfileEdit, ProfileHomeMenu.NotificationSettings -> null
    }

private fun ProfileHomeMenu.titleRes(): Int =
    when (this) {
        ProfileHomeMenu.ProfileEdit -> R.string.profile_home_menu_profile_edit
        ProfileHomeMenu.ExperienceCards -> R.string.profile_home_menu_experience_cards
        ProfileHomeMenu.PastApplications -> R.string.profile_home_menu_past_applications
        ProfileHomeMenu.NotificationSettings -> R.string.profile_home_menu_notification_settings
    }

private fun ProfileHomeMenu.descriptionRes(): Int =
    when (this) {
        ProfileHomeMenu.ProfileEdit -> R.string.profile_home_menu_profile_edit_description
        ProfileHomeMenu.ExperienceCards -> R.string.profile_home_menu_experience_cards_description
        ProfileHomeMenu.PastApplications -> R.string.profile_home_menu_past_applications_description
        ProfileHomeMenu.NotificationSettings -> R.string.profile_home_menu_notification_settings_description
    }

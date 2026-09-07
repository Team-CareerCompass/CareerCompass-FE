package com.careercompass.feature.profile.presentation.pastapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.ui.component.CareerCompassBadge
import com.careercompass.core.ui.component.CareerCompassBadgeTone
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassEmptyState
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTag
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.component.labelResId
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R

/** 눌리는 칩의 최소 높이 — WCAG 2.5.5 · ATF `TouchTargetSizeCheck` 가 48dp 를 요구한다. */
private val TAG_MIN_TOUCH_HEIGHT = 48.dp

/**
 * 과거 지원서 목록(Figma 05 · 05) — 상태 없는 본문.
 *
 * ### 이 화면의 엣지 상태 (`docs/spec/edge-states.md` §6)
 * 1. **오프라인·네트워크 실패** — 읽어 온 지원서가 없을 때만 화면을 덮는다(실패 표 #204 ·
 *    `FailureSurface.Application`). 분류 변경·삭제 실패는 목록을 그대로 두고 스낵바 한 줄이다.
 * 2. **로딩** — 첫 조회에만 진행 표시.
 * 3. **빈 결과** — 사유가 하나다(아직 안 올렸다). 올리러 가는 길을 연다.
 * 4. **권한 거부** — 없다.
 * 5. **서버 점검(503)** — 1번과 같은 길.
 * 6. **세션 만료(401)** — 그리지 않는다. 셸이 로그인으로 보낸다.
 * 7. **되돌릴 길** — 실패 화면에도 상단 바의 뒤로 가기가 남는다.
 */
@Composable
public fun PastApplicationListContent(
    state: PastApplicationListUiState,
    onEvent: (PastApplicationListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.subtleSurface),
    ) {
        CareerCompassTopAppBar(
            title = stringResource(R.string.profile_past_application_title),
            onBackClick = { onEvent(PastApplicationListEvent.BackClicked) },
        )
        PastApplicationHeader(state = state, onEvent = onEvent)
        when {
            state.isFailureVisible -> {
                val display = requireNotNull(state.loadFailure).display(surface = FailureSurface.Application)
                CareerCompassFailureState(
                    display = display,
                    onActionClick = if (display.isRetryable) ({ onEvent(PastApplicationListEvent.RetryClicked) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.isInitialLoading -> {
                PastApplicationLoading()
            }

            state.isEmpty -> {
                CareerCompassEmptyState(
                    title = stringResource(R.string.profile_past_application_empty_title),
                    description = stringResource(R.string.profile_past_application_empty_description),
                    actionText = stringResource(R.string.profile_past_application_empty_action),
                    onActionClick = { onEvent(PastApplicationListEvent.AddClicked) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    items(items = state.applications, key = { it.id }) { application ->
                        PastApplicationCard(
                            application = application,
                            isExpanded = application.id == state.expandedId,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PastApplicationHeader(
    state: PastApplicationListUiState,
    onEvent: (PastApplicationListEvent) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_past_application_count, state.count, MAX_PAST_APPLICATIONS),
            style = CareerCompassTheme.typography.caption,
            color = CareerCompassTheme.colors.onSurfaceVariant,
        )
        CareerCompassButton(
            text = stringResource(R.string.profile_past_application_add),
            onClick = { onEvent(PastApplicationListEvent.AddClicked) },
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Small,
        )
    }
}

/**
 * 지원서 한 장 — 라벨·항목 개수와, 펼쳤을 때의 분류된 항목들.
 *
 * **확인이 필요한 항목 수를 접힌 채로도 보인다.** 그것이 이 화면에 온 이유이므로, 펼쳐 봐야만 알 수 있으면
 * 열 장을 하나씩 열어 봐야 한다.
 */
@Composable
private fun PastApplicationCard(
    application: PastApplication,
    isExpanded: Boolean,
    onEvent: (PastApplicationListEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val uncertainCount = application.items.count { !it.confident }

    CareerCompassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEvent(PastApplicationListEvent.ApplicationToggled(application.id)) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = application.label,
                    style = CareerCompassTheme.typography.labelMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_past_application_item_count, application.items.size),
                    style = CareerCompassTheme.typography.caption,
                    color = colors.onSurfaceVariant,
                )
            }
            CareerCompassButton(
                text = stringResource(R.string.profile_past_application_delete),
                onClick = { onEvent(PastApplicationListEvent.DeleteClicked(application.id)) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
        }
        if (uncertainCount > 0) {
            Spacer(modifier = Modifier.height(spacing.xxSmall))
            CareerCompassBadge(
                label = stringResource(R.string.profile_past_application_uncertain_count, uncertainCount),
                tone = CareerCompassBadgeTone.Warning,
            )
        }
        if (isExpanded) {
            Spacer(modifier = Modifier.height(spacing.small))
            HorizontalDivider(color = colors.subtleOutline)
            application.items.forEach { item ->
                PastApplicationItemRow(
                    applicationId = application.id,
                    item = item,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/**
 * 항목 한 줄 — 분류 칩과 본문 앞부분.
 *
 * **분류가 불확실한 항목**([PastApplicationItem.confident] 가 false)에 경고 배지를 붙인다. 그것이 손으로
 * 고쳐야 할 것들이고(F1-4), 확신한 항목과 같은 모양이면 무엇을 봐야 할지 알 수 없다.
 */
@Composable
private fun PastApplicationItemRow(
    applicationId: Long,
    item: PastApplicationItem,
    onEvent: (PastApplicationListEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Spacer(modifier = Modifier.height(spacing.small))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CareerCompassTag(
            label = stringResource(item.category.labelResId()),
            selected = !item.confident,
            onClick = { onEvent(PastApplicationListEvent.ItemCategoryClicked(applicationId, item.id)) },
            modifier = Modifier.heightIn(min = TAG_MIN_TOUCH_HEIGHT),
        )
        if (!item.confident) {
            CareerCompassBadge(
                label = stringResource(R.string.profile_past_application_uncertain_badge),
                tone = CareerCompassBadgeTone.Warning,
            )
        }
    }
    Text(
        text = item.content,
        style = CareerCompassTheme.typography.caption,
        color = colors.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PastApplicationLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.profile_past_application_loading),
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
    }
}

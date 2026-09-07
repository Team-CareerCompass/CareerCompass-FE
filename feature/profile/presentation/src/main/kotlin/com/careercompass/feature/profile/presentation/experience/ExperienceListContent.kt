package com.careercompass.feature.profile.presentation.experience

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassEmptyState
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTag
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.experience.component.ExperienceCardRow
import com.careercompass.feature.profile.presentation.experience.component.labelRes

/**
 * 경험 카드 목록(Figma 05 · 03) — 상태 없는 본문.
 *
 * ### 이 화면의 엣지 상태 (`docs/spec/edge-states.md` §6)
 * 1. **오프라인·네트워크 실패** — 읽어 온 카드가 하나도 없을 때만 화면을 덮는다(실패 표 #204).
 *    이어 읽기 실패는 보이는 목록을 그대로 두고 스낵바 한 줄이다.
 * 2. **로딩** — 첫 조회에만 진행 표시. 이어 읽기는 목록 끝의 버튼 자리에서 돈다.
 * 3. **빈 결과** — 사유가 둘이다. 필터 때문이면 되돌릴 조작(「전체 보기」)을, 정말 없으면 만들러 가는
 *    길(「첫 카드 만들기」)을 연다. 같은 문장을 쓰면 필터를 걸어 둔 사용자는 카드가 사라진 줄 안다.
 * 4. **권한 거부** — 없다.
 * 5. **서버 점검(503)** — 1번과 같은 길.
 * 6. **세션 만료(401)** — 그리지 않는다. 셸이 로그인으로 보낸다.
 * 7. **되돌릴 길** — 실패 화면에도 상단 바의 뒤로 가기와 유형 필터가 남는다.
 */
@Composable
public fun ExperienceListContent(
    state: ExperienceListUiState,
    onEvent: (ExperienceListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    val emptyReason = state.emptyReason

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.subtleSurface),
    ) {
        CareerCompassTopAppBar(
            title = stringResource(R.string.profile_experience_title),
            onBackClick = { onEvent(ExperienceListEvent.BackClicked) },
        )
        ExperienceListHeader(state = state, onEvent = onEvent)
        when {
            state.isFailureVisible -> {
                val display = requireNotNull(state.loadFailure).display(surface = FailureSurface.ExperienceCard)
                CareerCompassFailureState(
                    display = display,
                    onActionClick = if (display.isRetryable) ({ onEvent(ExperienceListEvent.RetryClicked) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.isInitialLoading -> {
                ExperienceListLoading()
            }

            emptyReason != null -> {
                ExperienceListEmpty(reason = emptyReason, onEvent = onEvent)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    items(items = state.cards, key = { it.id }) { card ->
                        ExperienceCardRow(
                            card = card,
                            onClick = { onEvent(ExperienceListEvent.CardClicked(card.id)) },
                            onDeleteClick = { onEvent(ExperienceListEvent.DeleteClicked(card.id)) },
                        )
                    }
                    if (state.nextCursor != null) {
                        item(key = "load_more") {
                            CareerCompassButton(
                                text = stringResource(R.string.profile_experience_load_more),
                                onClick = { onEvent(ExperienceListEvent.LoadMore) },
                                modifier = Modifier.fillMaxWidth(),
                                variant = CareerCompassButtonVariant.Secondary,
                                size = CareerCompassButtonSize.Large,
                                enabled = state.canLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 개수·추가 버튼과 유형 필터.
 *
 * **상한을 늘 보인다**(`12 / 30장`). 서버가 422 를 줄 때까지 기다렸다 알리면 사용자는 폼을 다 채운 뒤에야
 * 막힌다(#178). 필터가 걸려 있으면 전체를 모르므로 개수 표시를 접는다 — 필터된 목록의 길이를 전체인 양
 * 적으면 상한 안내가 거짓이 된다.
 */
@Composable
private fun ExperienceListHeader(
    state: ExperienceListUiState,
    onEvent: (ExperienceListEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
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
            text =
                state.countLabelValue
                    ?.let { stringResource(R.string.profile_experience_count, it, MAX_EXPERIENCE_CARDS) }
                    .orEmpty(),
            style = CareerCompassTheme.typography.caption,
            color = colors.onSurfaceVariant,
        )
        CareerCompassButton(
            text = stringResource(R.string.profile_experience_add),
            onClick = { onEvent(ExperienceListEvent.AddClicked) },
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Small,
        )
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.large),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        ExperienceTypeFilter.all.forEach { filter ->
            CareerCompassTag(
                label = filter.type?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.profile_experience_filter_all),
                selected = filter == state.filter,
                onClick = { onEvent(ExperienceListEvent.FilterSelected(filter)) },
                modifier = Modifier.padding(vertical = spacing.xxSmall),
            )
        }
    }
}

@Composable
private fun ExperienceListEmpty(
    reason: ExperienceEmptyReason,
    onEvent: (ExperienceListEvent) -> Unit,
) {
    when (reason) {
        ExperienceEmptyReason.NoCards -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.profile_experience_empty_title),
                description = stringResource(R.string.profile_experience_empty_description),
                actionText = stringResource(R.string.profile_experience_empty_action),
                onActionClick = { onEvent(ExperienceListEvent.AddClicked) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        ExperienceEmptyReason.FilteredOut -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.profile_experience_filtered_empty_title),
                description = stringResource(R.string.profile_experience_filtered_empty_description),
                actionText = stringResource(R.string.profile_experience_filtered_empty_action),
                onActionClick = { onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(null))) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ExperienceListLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.profile_experience_loading),
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
    }
}

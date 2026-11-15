package com.careercompass.feature.editor.presentation.setup

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTag
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemRules
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.presentation.R

/** 눌리는 칩의 최소 높이 — WCAG 2.5.5 · ATF `TouchTargetSizeCheck` 가 48dp 를 요구한다. */
private val TONE_MIN_TOUCH_HEIGHT = 48.dp

/**
 * 지원서 작성 첫 화면(F4-1) — 상태 없는 본문.
 *
 * **Figma 04 에 이 화면의 시안이 없다.** 페이지는 「01 AI 초안 생성」(진행)부터 시작한다. 정본 순서가
 * API_SPEC > 기능 스펙 > Figma 이므로 F4-1 의 요구를 그대로 옮기고, 어휘와 부품은 04 의 다른 화면
 * (문항 번호 + 「(500자)」 표기, 문체 칩)에 맞췄다.
 *
 * ### 이 화면의 엣지 상태 (`docs/spec/edge-states.md` §6)
 * 1. **오프라인·네트워크 실패** — 공고를 못 읽으면 화면을 덮는다(실패 표 #204 · `FailureSurface.Posting`).
 *    초안 생성 실패는 문항을 그대로 두고 스낵바 한 줄이다 — 쓰던 것을 잃지 않는다.
 * 2. **로딩** — 첫 조회에만 진행 표시. 생성 중에는 버튼이 잠기고 문구가 바뀐다.
 * 3. **빈 결과** — 인식된 문항이 없는 것은 실패가 아니다. 직접 쓰는 자리를 연다(F4-1).
 * 4. **권한 거부** — 없다.
 * 5. **서버 점검(503)** — 1번과 같은 길. 생성 중 503(LLM 장애)은 스낵바다.
 * 6. **세션 만료(401)** — 그리지 않는다. 셸이 로그인으로 보낸다.
 * 7. **되돌릴 길** — 실패 화면에도 상단 바의 뒤로 가기가 남는다.
 */
@Composable
public fun ApplicationSetupContent(
    state: ApplicationSetupUiState,
    onEvent: (ApplicationSetupEvent) -> Unit,
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
            title = stringResource(R.string.editor_setup_title),
            onBackClick = { onEvent(ApplicationSetupEvent.BackClicked) },
            subtitle = state.postingTitle.takeIf { it.isNotBlank() },
        )
        when {
            state.isFailureVisible -> {
                val display = requireNotNull(state.loadFailure).display(surface = FailureSurface.Posting)
                CareerCompassFailureState(
                    display = display,
                    onActionClick = if (display.isRetryable) ({ onEvent(ApplicationSetupEvent.RetryClicked) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.isInitialLoading -> {
                SetupLoading()
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    item(key = "heading") { SetupHeading(isEmptyRecognition = state.isEmptyRecognition) }
                    items(items = state.items, key = { it.order }) { item ->
                        ApplicationItemRow(item = item, onEvent = onEvent)
                    }
                    item(key = "add") { AddItemRow(state = state, onEvent = onEvent) }
                    if (state.hasUnboundedItem) {
                        item(key = "default-length") { DefaultLengthNotice() }
                    }
                    item(key = "tone") { TonePicker(selected = state.tone, onEvent = onEvent) }
                }
                StartBar(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SetupHeading(isEmptyRecognition: Boolean) {
    val spacing = CareerCompassTheme.spacing
    Column {
        Text(
            text =
                stringResource(
                    if (isEmptyRecognition) R.string.editor_setup_empty_heading else R.string.editor_setup_heading,
                ),
            style = CareerCompassTheme.typography.headline4,
            color = CareerCompassTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xxSmall))
        Text(
            text =
                stringResource(
                    if (isEmptyRecognition) R.string.editor_setup_empty_description else R.string.editor_setup_description,
                ),
            style = CareerCompassTheme.typography.bodyMedium,
            color = CareerCompassTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * 문항 한 줄 — 번호·질문 원문·글자 수 제한.
 *
 * 제한을 못 찾은 문항은 「제한 없음」이라고 **그대로** 쓴다. 400 이나 600 을 대신 보여 주면 공고가 정한 값과
 * 구분되지 않고, 사용자는 공고에 없는 제한을 공고의 것으로 읽는다. 무엇으로 쓰이는지는 목록 아래 안내가 말한다.
 */
@Composable
private fun ApplicationItemRow(
    item: ApplicationItemDraft,
    onEvent: (ApplicationSetupEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                text = stringResource(R.string.editor_setup_item_order, item.order),
                style = CareerCompassTheme.typography.labelMedium,
                color = colors.primaryEmphasis,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.question,
                    style = CareerCompassTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        item.maxChars?.let { stringResource(R.string.editor_setup_item_max_chars, it) }
                            ?: stringResource(R.string.editor_setup_item_no_max_chars),
                    style = CareerCompassTheme.typography.caption,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.xxSmall))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            CareerCompassButton(
                text = stringResource(R.string.editor_setup_item_edit),
                onClick = { onEvent(ApplicationSetupEvent.ItemEditClicked(item.order)) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
            CareerCompassButton(
                text = stringResource(R.string.editor_setup_item_delete),
                onClick = { onEvent(ApplicationSetupEvent.ItemDeleteClicked(item.order)) },
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
        }
    }
}

@Composable
private fun AddItemRow(
    state: ApplicationSetupUiState,
    onEvent: (ApplicationSetupEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editor_setup_item_count, state.items.size, ApplicationItemRules.MAX_ITEMS),
            style = CareerCompassTheme.typography.caption,
            color = CareerCompassTheme.colors.onSurfaceVariant,
        )
        CareerCompassButton(
            text = stringResource(R.string.editor_setup_item_add),
            onClick = { onEvent(ApplicationSetupEvent.ItemAddClicked) },
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Small,
        )
    }
}

/** 제한 없는 문항이 하나라도 있을 때만 보인다 — 없을 때 띄우면 사실이 아닌 안내가 된다(F4-2). */
@Composable
private fun DefaultLengthNotice() {
    Text(
        text =
            stringResource(
                R.string.editor_setup_default_length_notice,
                ApplicationItemRules.DEFAULT_MIN_CHARS,
                ApplicationItemRules.DEFAULT_MAX_CHARS,
            ),
        style = CareerCompassTheme.typography.caption,
        color = CareerCompassTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun TonePicker(
    selected: ApplicationTone,
    onEvent: (ApplicationSetupEvent) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing
    Column {
        Text(
            text = stringResource(R.string.editor_setup_tone_label),
            style = CareerCompassTheme.typography.labelMedium,
            color = CareerCompassTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xxSmall))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            ApplicationTone.entries.forEach { tone ->
                CareerCompassTag(
                    label = stringResource(tone.labelResId()),
                    selected = tone == selected,
                    onClick = { onEvent(ApplicationSetupEvent.ToneSelected(tone)) },
                    modifier = Modifier.heightIn(min = TONE_MIN_TOUCH_HEIGHT),
                )
            }
        }
    }
}

@Composable
private fun StartBar(
    state: ApplicationSetupUiState,
    onEvent: (ApplicationSetupEvent) -> Unit,
) {
    CareerCompassButton(
        text =
            stringResource(
                if (state.isCreating) R.string.editor_setup_starting else R.string.editor_setup_start,
            ),
        onClick = { onEvent(ApplicationSetupEvent.StartClicked) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(CareerCompassTheme.spacing.large),
        enabled = state.canStart,
    )
}

@Composable
private fun SetupLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.editor_setup_loading),
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** 어조의 화면 문구 — Figma 04 「03 · 톤 & 재생성」이 쓰는 말 그대로다. */
internal fun ApplicationTone.labelResId(): Int =
    when (this) {
        ApplicationTone.Formal -> R.string.editor_setup_tone_formal
        ApplicationTone.Casual -> R.string.editor_setup_tone_casual
    }

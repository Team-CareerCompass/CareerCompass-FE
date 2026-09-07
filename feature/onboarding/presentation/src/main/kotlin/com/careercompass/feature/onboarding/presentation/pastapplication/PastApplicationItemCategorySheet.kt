package com.careercompass.feature.onboarding.presentation.pastapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.onboarding.presentation.R

/**
 * Step 4 항목 분류 조정 시트의 본문. 시트 컨테이너는 호스트가 감싼다.
 *
 * 분류는 6종 고정이라 검색·페이징이 없다. 고르는 순간 저장을 시작하고 시트를 닫는다 — 저장은 낙관적으로
 * 반영하고 실패하면 목록이 되돌아간다(호스트 [com.careercompass.feature.onboarding.presentation.flow.OnboardingViewModel] 몫).
 */
@Composable
public fun PastApplicationItemCategorySheet(
    state: PastApplicationItemCategoryState,
    onEvent: (PastApplicationItemCategoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val selectedState = stringResource(R.string.onboarding_item_category_selected_state)
    val unselectedState = stringResource(R.string.onboarding_item_category_unselected_state)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.large, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.onboarding_item_category_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = CareerCompassTheme.typography.headline4,
        )
        Text(
            text = state.contentPreview,
            color = colors.mutedContent,
            maxLines = PREVIEW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            style = CareerCompassTheme.typography.caption,
        )
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            PastApplicationCategory.entries.forEach { category ->
                val selected = category == state.selected
                CategoryRow(
                    label = stringResource(category.labelResId()),
                    selected = selected,
                    stateDescription = if (selected) selectedState else unselectedState,
                    onClick = { onEvent(PastApplicationItemCategoryEvent.CategorySelected(category)) },
                )
                HorizontalDivider(color = colors.subtleOutline)
            }
        }
        CareerCompassButton(
            text = stringResource(com.careercompass.core.ui.R.string.core_ui_sheet_cancel),
            onClick = { onEvent(PastApplicationItemCategoryEvent.Dismissed) },
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Large,
        )
    }
}

@Composable
private fun CategoryRow(
    label: String,
    selected: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CATEGORY_ROW_MIN_HEIGHT)
                .semantics { this.stateDescription = stateDescription }
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(horizontal = spacing.xSmall),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) colors.primaryEmphasis else colors.onSurface,
            style =
                CareerCompassTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}

/** 분류의 화면 라벨 — 기능 스펙 F1-4 의 6종 문구를 Step 4 목록과 시트가 함께 쓴다. */
public fun PastApplicationCategory.labelResId(): Int =
    when (this) {
        PastApplicationCategory.Motivation -> R.string.onboarding_item_category_motivation
        PastApplicationCategory.Growth -> R.string.onboarding_item_category_growth
        PastApplicationCategory.Experience -> R.string.onboarding_item_category_experience
        PastApplicationCategory.Competency -> R.string.onboarding_item_category_competency
        PastApplicationCategory.Aspiration -> R.string.onboarding_item_category_aspiration
        PastApplicationCategory.Other -> R.string.onboarding_item_category_other
    }

private const val PREVIEW_MAX_LINES = 3

private val CATEGORY_ROW_MIN_HEIGHT = 48.dp

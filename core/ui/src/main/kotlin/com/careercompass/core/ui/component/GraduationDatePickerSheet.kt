package com.careercompass.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.careercompass.core.ui.R
import com.careercompass.core.ui.theme.CareerCompassTheme

/**
 * 졸업 예정 연월 선택 시트의 본문 — 연도는 가로 목록, 월은 12개 칩으로 고른다.
 *
 * 시트 컨테이너는 호스트가 감싼다. 선택 중인 연도는 처음 열릴 때 보이는 위치로 스크롤한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun GraduationDatePickerSheet(
    state: GraduationPickerState,
    onEvent: (GraduationDatePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val selectedState = stringResource(R.string.core_ui_graduation_picker_selected_state)
    val unselectedState = stringResource(R.string.core_ui_graduation_picker_unselected_state)
    val yearListState = rememberLazyListState()

    LaunchedEffect(state.selectedYear) {
        val index = state.years.indexOf(state.selectedYear)
        if (index >= 0) yearListState.animateScrollToItem((index - YEAR_SCROLL_LEAD).coerceAtLeast(0))
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.large, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.core_ui_graduation_picker_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = CareerCompassTheme.typography.headline4,
        )
        SectionLabel(text = stringResource(R.string.core_ui_graduation_picker_year_label))
        LazyRow(
            state = yearListState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            items(items = state.years, key = { it }) { year ->
                val selected = year == state.selectedYear
                CareerCompassTag(
                    label = stringResource(R.string.core_ui_graduation_picker_year, year),
                    selected = selected,
                    onClick = { onEvent(GraduationDatePickerEvent.YearSelected(year)) },
                    stateDescription = if (selected) selectedState else unselectedState,
                    role = Role.RadioButton,
                )
            }
        }
        SectionLabel(text = stringResource(R.string.core_ui_graduation_picker_month_label))
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            state.months.forEach { month ->
                val selected = month == state.selectedMonth
                CareerCompassTag(
                    label = stringResource(R.string.core_ui_graduation_picker_month, month),
                    selected = selected,
                    onClick = { onEvent(GraduationDatePickerEvent.MonthSelected(month)) },
                    stateDescription = if (selected) selectedState else unselectedState,
                    role = Role.RadioButton,
                )
            }
        }
        CareerCompassButton(
            text = stringResource(R.string.core_ui_graduation_picker_confirm),
            onClick = { onEvent(GraduationDatePickerEvent.Confirmed) },
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            size = CareerCompassButtonSize.Large,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = CareerCompassTheme.colors.onSurfaceVariant,
        style = CareerCompassTheme.typography.labelMedium,
    )
}

/** 선택 연도가 목록 왼쪽 끝에 붙지 않도록 앞에 남겨 두는 항목 수. */
private const val YEAR_SCROLL_LEAD = 2

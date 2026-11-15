package com.careercompass.feature.foryou.presentation.roadmap

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassEmptyState
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureAction
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.RoadmapMetric
import com.careercompass.feature.foryou.presentation.R
import java.text.NumberFormat

@Composable
internal fun RoadmapContent(
    state: RoadmapUiState,
    onIntent: (RoadmapIntent) -> Unit,
    onBackClick: () -> Unit,
    onCompleteProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CareerCompassTheme.spacing
    Column(modifier = modifier.fillMaxSize().background(CareerCompassTheme.colors.subtleSurface)) {
        CareerCompassTopAppBar(title = stringResource(R.string.foryou_roadmap_title), onBackClick = onBackClick)
        LazyRow(contentPadding = PaddingValues(horizontal = spacing.large), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            items(RoadmapCohort.entries) { cohort ->
                FilterChip(
                    selected = state.cohort == cohort,
                    onClick = { onIntent(RoadmapIntent.SelectCohort(cohort)) },
                    label = { Text(stringResource(cohort.labelRes())) },
                )
            }
        }
        val failure = state.failure
        val comparison = state.comparison
        when {
            state.isLoading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large))
                Text(stringResource(R.string.foryou_roadmap_loading), modifier = Modifier.padding(spacing.large))
            }

            failure != null -> {
                val display = failure.display()
                CareerCompassFailureState(
                    display = display,
                    onActionClick =
                        when (display.action) {
                            FailureAction.Retry -> ({ onIntent(RoadmapIntent.Refresh) })
                            FailureAction.CompleteProfile -> onCompleteProfile
                            else -> null
                        },
                    modifier = Modifier.weight(1f),
                )
            }

            comparison != null -> {
                RoadmapLoaded(comparison = comparison, onRefresh = { onIntent(RoadmapIntent.Refresh) })
            }
        }
    }
}

@Composable
private fun RoadmapLoaded(
    comparison: RoadmapComparison,
    onRefresh: () -> Unit,
) {
    val spacing = CareerCompassTheme.spacing
    val isSelf = comparison.cohort == RoadmapCohort.MeOnly
    // 표본이 없는 비교 수치는 그리지 않는다. 내 성장에는 코호트 표본이 필요하지 않다.
    if (comparison.metrics.isEmpty() || (!isSelf && comparison.sampleSize <= 0)) {
        CareerCompassEmptyState(
            title = stringResource(R.string.foryou_roadmap_empty_title),
            description = stringResource(if (isSelf) R.string.foryou_roadmap_empty_self else R.string.foryou_roadmap_empty_cohort),
            actionText = stringResource(R.string.foryou_refresh),
            onActionClick = onRefresh,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        item {
            Text(
                text =
                    if (isSelf) {
                        stringResource(
                            R.string.foryou_roadmap_self_notice,
                        )
                    } else {
                        stringResource(R.string.foryou_roadmap_sample, comparison.sampleSize)
                    },
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
        items(comparison.metrics) { metric -> RoadmapMetricCard(metric = metric, cohort = comparison.cohort) }
        if (comparison.suggestions.isNotEmpty()) {
            item { Text(stringResource(R.string.foryou_roadmap_suggestions), style = CareerCompassTheme.typography.headline2) }
            items(comparison.suggestions) { suggestion ->
                CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.foryou_roadmap_semester, suggestion.semester),
                        style = CareerCompassTheme.typography.labelMedium,
                    )
                    Text(suggestion.action, style = CareerCompassTheme.typography.bodyMedium)
                }
            }
        }
        item {
            CareerCompassButton(
                text = stringResource(R.string.foryou_refresh),
                onClick = onRefresh,
                variant = CareerCompassButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RoadmapMetricCard(
    metric: RoadmapMetric,
    cohort: RoadmapCohort,
) {
    val colors = CareerCompassTheme.colors
    CareerCompassCard(modifier = Modifier.fillMaxWidth()) {
        Text(metric.name, style = CareerCompassTheme.typography.labelMedium)
        if (cohort == RoadmapCohort.MeOnly) {
            // §7에는 과거 시점·기간이 없다. peerAvg를 과거의 내 값으로 지어내지 않는다.
            Text(stringResource(R.string.foryou_roadmap_current_value, metric.me.label()), style = CareerCompassTheme.typography.headline2)
        } else {
            val maximum = maxOf(metric.me.validValue(), metric.cohortAverage.validValue(), 1.0)
            MetricBar(
                label = stringResource(R.string.foryou_roadmap_me),
                value = metric.me,
                maximum = maximum,
                color = colors.primaryEmphasis,
            )
            MetricBar(
                label =
                    stringResource(
                        if (cohort ==
                            RoadmapCohort.Peer
                        ) {
                            R.string.foryou_roadmap_peer_average
                        } else {
                            R.string.foryou_roadmap_senior_average
                        },
                    ),
                value = metric.cohortAverage,
                maximum = maximum,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricBar(
    label: String,
    value: Double,
    maximum: Double,
    color: Color,
) {
    Column(modifier = Modifier.padding(top = CareerCompassTheme.spacing.small), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = CareerCompassTheme.typography.caption)
            Text(value.label(), style = CareerCompassTheme.typography.caption)
        }
        LinearProgressIndicator(
            progress = { (value.validValue() / maximum).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            color = color,
            trackColor = CareerCompassTheme.colors.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private fun Double.validValue(): Double = if (isFinite() && this >= 0) this else 0.0

private fun Double.label(): String =
    if (isFinite() &&
        this >= 0
    ) {
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(this)
    } else {
        "—"
    }

@StringRes
private fun RoadmapCohort.labelRes(): Int =
    when (this) {
        RoadmapCohort.Peer -> R.string.foryou_roadmap_peer
        RoadmapCohort.Senior -> R.string.foryou_roadmap_senior
        RoadmapCohort.MeOnly -> R.string.foryou_roadmap_self
    }

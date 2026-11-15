package com.careercompass.feature.foryou.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.navigation.FeatureNavDisplay
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.presentation.R
import com.careercompass.feature.foryou.presentation.export.StrengthExportScreen
import com.careercompass.feature.foryou.presentation.roadmap.RoadmapScreen

@Composable
public fun ForYouNavHost(
    boundary: FeatureStackBoundary,
    externalActions: ForYouExternalActions,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(ForYouRoute.Home)
    val actions = remember(backStack, boundary, externalActions) { ForYouLocalNavActions(backStack, boundary, externalActions) }
    FeatureNavDisplay(
        backStack = backStack,
        boundary = boundary,
        modifier = modifier,
        entryProvider =
            entryProvider {
                entry<ForYouRoute.Home> {
                    AnalysisHomeContent(
                        onRoadmapClick = actions::navigateToRoadmap,
                        onExportClick = actions::navigateToExport,
                    )
                }
                entry<ForYouRoute.StrengthExport> {
                    StrengthExportScreen(
                        onBackClick = actions::popBack,
                        onAddExperience = actions::navigateToExperiences,
                        onCompleteProfile = actions::navigateToProfile,
                        onSessionEnded = actions::onSessionEnded,
                        viewModel = hiltViewModel(),
                    )
                }
                entry<ForYouRoute.Roadmap> {
                    RoadmapScreen(
                        onBackClick = actions::popBack,
                        onCompleteProfile = actions::navigateToProfile,
                        onSessionEnded = actions::onSessionEnded,
                        viewModel = hiltViewModel(),
                    )
                }
            },
    )
}

/** 분석 탭의 사용 가능한 기능 진입점. 추천·Export는 각 담당 이슈에서 더한다. */
@Composable
internal fun AnalysisHomeContent(
    onRoadmapClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CareerCompassTheme.spacing
    Column(modifier = modifier.fillMaxSize().background(CareerCompassTheme.colors.subtleSurface)) {
        CareerCompassTopAppBar(title = stringResource(R.string.foryou_analysis_title), onBackClick = null)
        Column(modifier = Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            Text(stringResource(R.string.foryou_analysis_description), style = CareerCompassTheme.typography.bodyMedium)
            CareerCompassCard(modifier = Modifier.fillMaxWidth(), onClick = onRoadmapClick) {
                Text(stringResource(R.string.foryou_roadmap_title), style = CareerCompassTheme.typography.headline2)
                Text(stringResource(R.string.foryou_roadmap_entry_description), style = CareerCompassTheme.typography.bodyMedium)
            }
            CareerCompassCard(modifier = Modifier.fillMaxWidth(), onClick = onExportClick) {
                Text(stringResource(R.string.foryou_export_title), style = CareerCompassTheme.typography.headline2)
                Text(stringResource(R.string.foryou_export_entry_description), style = CareerCompassTheme.typography.bodyMedium)
            }
        }
    }
}

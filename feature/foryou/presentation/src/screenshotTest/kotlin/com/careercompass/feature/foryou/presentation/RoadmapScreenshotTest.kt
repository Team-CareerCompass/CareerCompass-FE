package com.careercompass.feature.foryou.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.RoadmapMetric
import com.careercompass.feature.foryou.domain.model.RoadmapSuggestion
import com.careercompass.feature.foryou.presentation.roadmap.RoadmapContent
import com.careercompass.feature.foryou.presentation.roadmap.RoadmapUiState

@PreviewTest
@Preview(name = "Roadmap peer", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun RoadmapPeerPreview() {
    RoadmapPreview(RoadmapCohort.Peer)
}

@PreviewTest
@Preview(name = "Roadmap senior", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun RoadmapSeniorPreview() {
    RoadmapPreview(RoadmapCohort.Senior)
}

@PreviewTest
@Preview(name = "Roadmap self", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun RoadmapSelfPreview() {
    RoadmapPreview(RoadmapCohort.MeOnly)
}

@PreviewTest
@Preview(name = "Roadmap insufficient sample", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun RoadmapEmptyPreview() {
    RoadmapPreview(RoadmapCohort.Peer, sampleSize = 0)
}

@Composable
private fun RoadmapPreview(
    cohort: RoadmapCohort,
    sampleSize: Int = 86,
) {
    CareerCompassTheme {
        RoadmapContent(
            state =
                RoadmapUiState(
                    cohort = cohort,
                    comparison =
                        RoadmapComparison(
                            cohort = cohort,
                            sampleSize = sampleSize,
                            metrics =
                                listOf(
                                    RoadmapMetric("프로젝트 수", 4.0, 2.0),
                                    RoadmapMetric("인턴 경험", 0.0, 0.8),
                                    RoadmapMetric("수상 이력", 1.0, 1.2),
                                    RoadmapMetric("자격증", 2.0, 1.5),
                                    RoadmapMetric("학점", 3.9, 3.6),
                                ),
                            suggestions = listOf(RoadmapSuggestion("3-2", "SQLD + 토익 800+", 12)),
                        ),
                ),
            onIntent = {},
            onBackClick = {},
            onCompleteProfile = {},
        )
    }
}

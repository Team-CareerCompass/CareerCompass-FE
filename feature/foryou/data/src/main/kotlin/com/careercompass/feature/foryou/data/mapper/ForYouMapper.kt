package com.careercompass.feature.foryou.data.mapper

import com.careercompass.core.network.dto.ExportResultDto
import com.careercompass.core.network.dto.ForYouFeedDto
import com.careercompass.core.network.dto.ForYouRecommendationDto
import com.careercompass.core.network.dto.ForYouTopPickDto
import com.careercompass.core.network.dto.RoadmapCompareDto
import com.careercompass.core.network.dto.RoadmapMetricDto
import com.careercompass.core.network.dto.RoadmapSuggestionDto
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.model.ForYouRecommendation
import com.careercompass.feature.foryou.domain.model.ForYouSection
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.RoadmapMetric
import com.careercompass.feature.foryou.domain.model.RoadmapSuggestion
import com.careercompass.feature.foryou.domain.model.StrengthExport

/** API_SPEC v0.1 §7 응답 → 도메인. */
internal object ForYouMapper {
    /**
     * 톱 픽의 배열 `reason` 과 나머지의 문자열 `reason` 을 **여기서** 한 벌로 모은다.
     *
     * 이유가 하나도 없는 추천은 버린다 — 「왜 이 공고인가」가 이 화면의 전부라, 이유 없는 카드는 공고 목록과
     * 다를 바 없으면서 추천이라고 주장한다. 빈 문자열도 없는 것으로 본다.
     */
    fun toFeed(dto: ForYouFeedDto): ForYouFeed =
        ForYouFeed(
            topPick = dto.topPick?.let(::toTopPick),
            byStrength = dto.byStrength.mapNotNull { toRecommendation(it, ForYouSection.ByStrength) },
            byGap = dto.byGap.mapNotNull { toRecommendation(it, ForYouSection.ByGap) },
        )

    private fun toTopPick(dto: ForYouTopPickDto): ForYouRecommendation? {
        val reasons = dto.reason.map(String::trim).filter { it.isNotEmpty() }
        if (reasons.isEmpty()) return null
        return ForYouRecommendation(postingId = dto.postingId, section = ForYouSection.TopPick, reasons = reasons)
    }

    private fun toRecommendation(
        dto: ForYouRecommendationDto,
        section: ForYouSection,
    ): ForYouRecommendation? {
        val reason = dto.reason.trim()
        if (reason.isEmpty()) return null
        return ForYouRecommendation(postingId = dto.postingId, section = section, reasons = listOf(reason))
    }

    /**
     * 모르는 `cohort` 값은 요청에 실어 보낸 값으로 되돌린다.
     *
     * 서버가 무엇을 돌려주든 **우리가 물어본 축**이 화면이 그릴 축이다. 여기서 null 을 내면 응답 전체를 버리게
     * 되는데, 지표와 제안은 멀쩡히 왔다.
     */
    fun toComparison(
        dto: RoadmapCompareDto,
        requested: RoadmapCohort,
    ): RoadmapComparison =
        RoadmapComparison(
            cohort = RoadmapCohort.fromWireValue(dto.cohort) ?: requested,
            sampleSize = dto.sampleSize,
            metrics = dto.metrics.map(::toMetric),
            suggestions = dto.suggestions.map(::toSuggestion),
        )

    private fun toMetric(dto: RoadmapMetricDto): RoadmapMetric = RoadmapMetric(name = dto.name, me = dto.me, cohortAverage = dto.peerAvg)

    private fun toSuggestion(dto: RoadmapSuggestionDto): RoadmapSuggestion =
        RoadmapSuggestion(semester = dto.semester, action = dto.action, expectedLift = dto.expectedLift)

    /** 모르는 형식은 요청한 형식으로 되돌린다 — 본문은 이미 그 형식으로 왔다. */
    fun toExport(
        dto: ExportResultDto,
        requested: ExportFormat,
    ): StrengthExport =
        StrengthExport(
            format = ExportFormat.fromWireValue(dto.format) ?: requested,
            content = dto.content,
        )
}

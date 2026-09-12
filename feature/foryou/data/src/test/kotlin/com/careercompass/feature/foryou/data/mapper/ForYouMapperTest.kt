package com.careercompass.feature.foryou.data.mapper

import com.careercompass.core.network.dto.ExportResultDto
import com.careercompass.core.network.dto.ForYouFeedDto
import com.careercompass.core.network.dto.ForYouRecommendationDto
import com.careercompass.core.network.dto.ForYouTopPickDto
import com.careercompass.core.network.dto.RoadmapCompareDto
import com.careercompass.core.network.dto.RoadmapMetricDto
import com.careercompass.core.network.dto.RoadmapSuggestionDto
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ForYouSection
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForYouMapperTest {
    /**
     * 계약이 같은 이름의 필드를 톱 픽에서는 배열로, 나머지에서는 문자열로 준다. 화면이 그 차이를 알면 카드
     * 한 장을 그리는 코드가 자리마다 갈라진다.
     */
    @Test
    fun `배열 이유와 문자열 이유를 한 벌 목록으로 모은다`() {
        val feed =
            ForYouMapper.toFeed(
                ForYouFeedDto(
                    topPick = ForYouTopPickDto(postingId = 101L, reason = listOf("전공 일치", "마감까지 여유")),
                    byStrength = listOf(ForYouRecommendationDto(postingId = 102L, reason = "프로젝트 경험이 맞아요")),
                    byGap = listOf(ForYouRecommendationDto(postingId = 103L, reason = "어학·인턴 보완용")),
                ),
            )

        assertEquals(listOf("전공 일치", "마감까지 여유"), feed.topPick?.reasons)
        assertEquals(listOf("프로젝트 경험이 맞아요"), feed.byStrength.single().reasons)
        assertEquals(ForYouSection.ByGap, feed.byGap.single().section)
    }

    /** 이유 없는 카드는 공고 목록과 다를 바 없으면서 추천이라고 주장한다. */
    @Test
    fun `이유가 비면 그 추천을 버린다`() {
        val feed =
            ForYouMapper.toFeed(
                ForYouFeedDto(
                    topPick = ForYouTopPickDto(postingId = 101L, reason = listOf("   ")),
                    byStrength = listOf(ForYouRecommendationDto(postingId = 102L, reason = "")),
                    byGap = listOf(ForYouRecommendationDto(postingId = 103L, reason = " 보완용 ")),
                ),
            )

        assertNull(feed.topPick)
        assertEquals(emptyList<Long>(), feed.byStrength.map { it.postingId })
        assertEquals(listOf("보완용"), feed.byGap.single().reasons)
    }

    /** 여기서 null 을 내면 멀쩡히 온 지표와 제안까지 버리게 된다. */
    @Test
    fun `모르는 cohort 는 물어본 축으로 되돌린다`() {
        val comparison =
            ForYouMapper.toComparison(
                dto = compareDto(cohort = "classmates"),
                requested = RoadmapCohort.Senior,
            )

        assertEquals(RoadmapCohort.Senior, comparison.cohort)
        assertEquals(86, comparison.sampleSize)
    }

    /** 계약의 필드 이름은 `peerAvg` 지만 `senior`·`me_only` 에서는 동기 평균이 아니다. */
    @Test
    fun `peerAvg 를 축 평균으로 옮기고 소수도 받는다`() {
        val comparison = ForYouMapper.toComparison(compareDto(), RoadmapCohort.Peer)

        assertEquals(listOf(4.0, 0.0), comparison.metrics.map { it.me })
        assertEquals(listOf(2.0, 0.8), comparison.metrics.map { it.cohortAverage })
        assertEquals("3-2", comparison.suggestions.single().semester)
        assertEquals(12, comparison.suggestions.single().expectedLift)
    }

    @Test
    fun `모르는 형식은 요청한 형식으로 되돌린다`() {
        val export = ForYouMapper.toExport(ExportResultDto(format = "pdf", content = "# 문서"), ExportFormat.Markdown)

        assertEquals(ExportFormat.Markdown, export.format)
        assertEquals("# 문서", export.content)
    }

    private fun compareDto(cohort: String = "peer") =
        RoadmapCompareDto(
            cohort = cohort,
            sampleSize = 86,
            metrics =
                listOf(
                    RoadmapMetricDto(name = "프로젝트 수", me = 4.0, peerAvg = 2.0),
                    RoadmapMetricDto(name = "인턴 경험", me = 0.0, peerAvg = 0.8),
                ),
            suggestions = listOf(RoadmapSuggestionDto(semester = "3-2", action = "SQLD + 토익 800+", expectedLift = 12)),
        )
}

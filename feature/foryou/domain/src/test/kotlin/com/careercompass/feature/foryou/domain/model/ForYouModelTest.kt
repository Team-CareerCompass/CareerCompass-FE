package com.careercompass.feature.foryou.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouModelTest {
    /** 「왜 이 공고인가」가 이 화면의 전부다. 이유 없는 추천은 만들 수 없어야 한다. */
    @Test
    fun `이유 없는 추천은 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) {
            ForYouRecommendation(postingId = 1L, section = ForYouSection.TopPick, reasons = emptyList())
        }
    }

    @Test
    fun `톱 픽을 앞세운 순서로 전체를 낸다`() {
        val feed =
            ForYouFeed(
                topPick = recommendation(1L, ForYouSection.TopPick),
                byStrength = listOf(recommendation(2L, ForYouSection.ByStrength)),
                byGap = listOf(recommendation(3L, ForYouSection.ByGap)),
            )

        assertEquals(listOf(1L, 2L, 3L), feed.all.map { it.postingId })
        assertFalse(feed.isEmpty)
    }

    @Test
    fun `톱 픽이 없어도 나머지가 있으면 빈 화면이 아니다`() {
        val feed = ForYouFeed(topPick = null, byStrength = listOf(recommendation(2L, ForYouSection.ByStrength)), byGap = emptyList())

        assertFalse(feed.isEmpty)
        assertEquals(listOf(2L), feed.all.map { it.postingId })
    }

    @Test
    fun `세 묶음이 모두 비면 빈 화면이다`() {
        assertTrue(ForYouFeed(topPick = null, byStrength = emptyList(), byGap = emptyList()).isEmpty)
    }

    /** 같은 값을 「앞선다」로 보여 주면 사용자가 없는 우위를 읽는다. */
    @Test
    fun `지표가 같으면 앞선 것이 아니다`() {
        assertTrue(RoadmapMetric(name = "프로젝트 수", me = 4.0, cohortAverage = 2.0).isAhead)
        assertFalse(RoadmapMetric(name = "프로젝트 수", me = 2.0, cohortAverage = 2.0).isAhead)
        assertFalse(RoadmapMetric(name = "인턴 경험", me = 0.0, cohortAverage = 0.8).isAhead)
    }

    @Test
    fun `계약 문자열을 그대로 고정한다`() {
        assertEquals(listOf("peer", "senior", "me_only"), RoadmapCohort.entries.map { it.wireValue })
        assertEquals(listOf("markdown", "notion", "html", "plain"), ExportFormat.entries.map { it.wireValue })
        assertEquals(
            listOf("basic", "skills", "projects", "awards", "summary"),
            ExportSection.entries.map { it.wireValue },
        )
        assertEquals(RoadmapCohort.MeOnly, RoadmapCohort.fromWireValue("me_only"))
        assertNull(ExportFormat.fromWireValue("pdf"))
    }

    @Test
    fun `카테고리별 상한은 계약대로 다섯이다`() {
        assertEquals(5, MAX_FOR_YOU_ITEMS_PER_SECTION)
    }

    private fun recommendation(
        postingId: Long,
        section: ForYouSection,
    ) = ForYouRecommendation(postingId = postingId, section = section, reasons = listOf("이유"))
}

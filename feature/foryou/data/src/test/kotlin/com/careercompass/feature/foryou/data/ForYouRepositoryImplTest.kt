package com.careercompass.feature.foryou.data

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.network.dto.ExportResultDto
import com.careercompass.core.network.dto.ForYouFeedDto
import com.careercompass.core.network.dto.RoadmapCompareDto
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.feature.foryou.data.support.FakeForYouApiService
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouRepositoryImplTest {
    private val apiService = FakeForYouApiService()
    private val repository = ForYouRepositoryImpl(apiService)

    /**
     * 추천도 로드맵도 서버가 프로필·경험 카드를 읽어 계산한다. 재료가 모자라면 422 `PROFILE_INCOMPLETE` 로
     * 거절하고, 화면은 그것을 「프로필을 채워 주세요」로 안내해야 한다 — 일반 오류로 내려앉으면 사용자가
     * 무엇을 하면 되는지 알 수 없다.
     */
    @Test
    fun `프로필 부족 422 는 화면이 읽을 수 있는 사유가 된다`() =
        runTest {
            apiService.onGetForYouFeed = { throw apiException("PROFILE_INCOMPLETE", 422) }
            apiService.onGetRoadmapComparison = { throw apiException("PROFILE_INCOMPLETE", 422) }

            val feedFailure = repository.getForYouFeed().exceptionOrNull()
            val roadmapFailure = repository.getRoadmapComparison(RoadmapCohort.Peer).exceptionOrNull()

            assertTrue("expected ProfileIncomplete but was $feedFailure", feedFailure is CoreDataFailure.ProfileIncomplete)
            assertTrue("expected ProfileIncomplete but was $roadmapFailure", roadmapFailure is CoreDataFailure.ProfileIncomplete)
            assertEquals("PROFILE_INCOMPLETE", (feedFailure as CoreDataFailure).code)
        }

    @Test
    fun `로드맵은 고른 축을 계약 문자열로 실어 보낸다`() =
        runTest {
            apiService.onGetRoadmapComparison = { cohort ->
                BaseResponse(ok = true, data = RoadmapCompareDto(cohort, 3, emptyList(), emptyList()))
            }

            val comparison = repository.getRoadmapComparison(RoadmapCohort.MeOnly).getOrThrow()

            assertEquals(listOf("me_only"), apiService.cohortQueries.toList())
            assertEquals(RoadmapCohort.MeOnly, comparison.cohort)
        }

    @Test
    fun `내보내기는 형식과 묶음을 계약 문자열로 실어 보낸다`() =
        runTest {
            apiService.onExport = { body -> BaseResponse(ok = true, data = ExportResultDto(body.format, "# 문서")) }

            val export =
                repository
                    .export(ExportFormat.Html, listOf(ExportSection.Basic, ExportSection.Awards))
                    .getOrThrow()

            assertEquals("html", apiService.exportRequests.single().format)
            assertEquals(listOf("basic", "awards"), apiService.exportRequests.single().sections)
            assertEquals(ExportFormat.Html, export.format)
        }

    /** 빈 선택은 use case 가 이미 막는다 — 여기까지 온 것은 배선이 어긋난 것이라 조용히 보내지 않는다. */
    @Test
    fun `묶음이 비면 요청을 만들지 않는다`() =
        runTest {
            runCatching { repository.export(ExportFormat.Markdown, emptyList()) }
                .onSuccess { error("expected IllegalArgumentException") }
                .onFailure { assertTrue(it is IllegalArgumentException) }

            assertTrue(apiService.exportRequests.isEmpty())
        }

    @Test
    fun `봉투가 실패면 데이터가 없다는 사유로 접힌다`() =
        runTest {
            apiService.onGetForYouFeed = { BaseResponse(ok = true, data = null) }

            val failure = repository.getForYouFeed().exceptionOrNull()

            assertTrue("expected ApiException but was $failure", failure is ApiException)
            assertEquals("EMPTY_DATA", (failure as ApiException).code)
        }

    @Test
    fun `추천 응답을 도메인으로 옮긴다`() =
        runTest {
            apiService.onGetForYouFeed = {
                BaseResponse(ok = true, data = ForYouFeedDto(topPick = null, byStrength = emptyList(), byGap = emptyList()))
            }

            assertTrue(repository.getForYouFeed().getOrThrow().isEmpty)
        }

    private fun apiException(
        code: String,
        status: Int,
    ) = ApiException(code = code, serverMessage = null, fallbackMessage = "실패", status = status)
}

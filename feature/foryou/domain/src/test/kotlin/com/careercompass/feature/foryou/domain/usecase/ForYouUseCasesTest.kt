package com.careercompass.feature.foryou.domain.usecase

import com.careercompass.feature.foryou.domain.error.ForYouFailure
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.testing.FakeForYouRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouUseCasesTest {
    private val repository = FakeForYouRepository()

    /**
     * 「추천할 것이 없다」와 「추천할 재료가 없다」는 사용자가 할 일이 다르다 — 전자는 기다리기, 후자는
     * 프로필 채우기다. 실패를 빈 목록으로 접으면 그 둘이 같은 화면이 된다.
     *
     * 서버 사유가 어느 값으로 오는지(422 `PROFILE_INCOMPLETE` → `CoreDataFailure.ProfileIncomplete`)는
     * data 계층의 몫이라 `ForYouRepositoryImplTest` 가 고정한다. 여기서 볼 것은 **그 실패가 그대로 지난다**는
     * 것뿐이다.
     */
    @Test
    fun `실패를 빈 목록으로 바꾸지 않는다`() =
        runTest {
            val failure = RuntimeException("profile incomplete")
            repository.onGetForYouFeed = { Result.failure(failure) }

            val result = GetForYouFeedUseCase(repository)()

            assertSame(failure, result.exceptionOrNull())
        }

    @Test
    fun `추천 조회를 그대로 위임한다`() =
        runTest {
            val feed = ForYouFeed(topPick = null, byStrength = emptyList(), byGap = emptyList())
            repository.onGetForYouFeed = { Result.success(feed) }

            assertEquals(feed, GetForYouFeedUseCase(repository)().getOrNull())
            assertEquals(1, repository.feedCallCount)
        }

    @Test
    fun `로드맵은 고르지 않으면 동기 비교다`() =
        runTest {
            repository.onGetRoadmapComparison = { _ -> Result.failure(RuntimeException()) }

            GetRoadmapComparisonUseCase(repository)()
            GetRoadmapComparisonUseCase(repository)(RoadmapCohort.Senior)

            assertEquals(listOf(RoadmapCohort.Peer, RoadmapCohort.Senior), repository.roadmapCalls.toList())
        }

    /** 서버는 빈 선택을 성공으로 받아 빈 문서를 돌려준다 — 사용자는 무엇이 잘못됐는지 모른다. */
    @Test
    fun `묶음을 하나도 고르지 않으면 보내지 않는다`() =
        runTest {
            val result = ExportStrengthsUseCase(repository)(ExportFormat.Markdown, emptyList())

            assertSame(ForYouFailure.NoExportSection, result.exceptionOrNull())
            assertTrue(repository.exportCalls.isEmpty())
        }

    /** 같은 선택이 늘 같은 문서를 만들어야 한다 — 체크한 순서가 문서의 절 순서가 되면 그렇지 않다. */
    @Test
    fun `고른 순서가 아니라 문서 순서로 보내고 중복을 걷는다`() =
        runTest {
            repository.onExport = { format, _ -> Result.success(StrengthExport(format, "# 문서")) }

            ExportStrengthsUseCase(repository)(
                format = ExportFormat.Notion,
                sections = listOf(ExportSection.Summary, ExportSection.Basic, ExportSection.Summary),
            )

            assertEquals(
                ExportFormat.Notion to listOf(ExportSection.Basic, ExportSection.Summary),
                repository.exportCalls.single(),
            )
        }
}

package com.careercompass.feature.feed.domain.usecase

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingQuery
import com.careercompass.core.model.posting.PostingSort
import com.careercompass.core.model.posting.PostingType
import com.careercompass.feature.feed.domain.FIXED_CLOCK
import com.careercompass.feature.feed.domain.TODAY
import com.careercompass.feature.feed.domain.model.FeedDeadlineFilter
import com.careercompass.feature.feed.domain.model.FeedPage
import com.careercompass.feature.feed.domain.model.FeedQuery
import com.careercompass.feature.feed.domain.posting
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class GetFeedPageUseCaseTest {
    private val expired = posting(id = 1, title = "어제 마감", dueDate = TODAY.minusDays(1))
    private val dueToday = posting(id = 2, title = "오늘 마감", dueDate = TODAY)
    private val dueInWeek = posting(id = 3, title = "7일 뒤 마감", dueDate = TODAY.plusDays(7))
    private val dueAfterWeek = posting(id = 4, title = "8일 뒤 마감", dueDate = TODAY.plusDays(8))
    private val dueInMonth = posting(id = 5, title = "30일 뒤 마감", dueDate = TODAY.plusDays(30))
    private val dueAfterMonth = posting(id = 6, title = "31일 뒤 마감", dueDate = TODAY.plusDays(31))
    private val noDue = posting(id = 7, title = "마감일 없음", dueDate = null)
    private val all = listOf(expired, dueToday, dueInWeek, dueAfterWeek, dueInMonth, dueAfterMonth, noDue)

    private fun useCase(repository: FakePostingRepository) = GetFeedPageUseCase(repository, FIXED_CLOCK)

    private suspend fun ids(
        query: FeedQuery,
        postings: List<Posting> = all,
    ): List<Long> = useCase(FakePostingRepository(initial = postings))(query).getOrThrow().postings.map(Posting::id)

    @Test
    fun `기본 필터는 마감 지난 공고만 숨기고 당일 마감·마감일 없음은 보인다`() =
        runTest {
            assertEquals(listOf(2L, 3L, 4L, 5L, 6L, 7L), ids(FeedQuery()))
        }

    @Test
    fun `IncludeExpired 는 마감 지난 공고까지 보인다`() =
        runTest {
            assertEquals(all.map(Posting::id), ids(FeedQuery(deadline = FeedDeadlineFilter.IncludeExpired)))
        }

    @Test
    fun `WithinWeek 는 오늘부터 7일째까지 포함하고 마감일 없는 공고·지난 공고는 뺀다`() =
        runTest {
            assertEquals(listOf(2L, 3L), ids(FeedQuery(deadline = FeedDeadlineFilter.WithinWeek)))
        }

    @Test
    fun `WithinMonth 는 오늘부터 30일째까지 포함한다`() =
        runTest {
            assertEquals(listOf(2L, 3L, 4L, 5L), ids(FeedQuery(deadline = FeedDeadlineFilter.WithinMonth)))
        }

    @Test
    fun `Range 는 양 끝을 포함한 범위 안에 마감하는 공고만 남기고 마감일 없는 공고는 뺀다`() =
        runTest {
            val range = FeedDeadlineFilter.Range(start = TODAY.plusDays(7), end = TODAY.plusDays(8))

            assertEquals(listOf(3L, 4L), ids(FeedQuery(deadline = range)))
        }

    @Test
    fun `Range 는 오늘과 무관하다 — 지난 날짜를 고르면 마감 지난 공고가 나온다`() =
        runTest {
            val range = FeedDeadlineFilter.Range(start = TODAY.minusDays(3), end = TODAY.minusDays(1))

            assertEquals(listOf(1L), ids(FeedQuery(deadline = range)))
        }

    @Test
    fun `Range 는 한쪽만 고르면 그 방향으로만 거른다`() =
        runTest {
            assertEquals(
                listOf(5L, 6L),
                ids(FeedQuery(deadline = FeedDeadlineFilter.Range(start = TODAY.plusDays(30), end = null))),
            )
            assertEquals(
                listOf(1L, 2L),
                ids(FeedQuery(deadline = FeedDeadlineFilter.Range(start = null, end = TODAY))),
            )
        }

    @Test
    fun `검색어는 제목에 대소문자 무시로 포함되는 공고만 남긴다`() =
        runTest {
            val postings =
                listOf(
                    posting(id = 10, title = "2026 KAKAO SW 인턴십"),
                    posting(id = 11, title = "kakao 스타일 공모전"),
                    posting(id = 12, title = "네이버 신입 채용"),
                )

            assertEquals(listOf(10L, 11L), ids(FeedQuery(searchQuery = " Kakao "), postings))
            assertEquals(listOf(10L), ids(FeedQuery(searchQuery = "sw 인턴"), postings))
            assertEquals(emptyList<Long>(), ids(FeedQuery(searchQuery = "라인"), postings))
        }

    @Test
    fun `마감일 필터와 검색어는 함께 적용된다`() =
        runTest {
            val postings =
                listOf(
                    posting(id = 20, title = "카카오 인턴", dueDate = TODAY.plusDays(3)),
                    posting(id = 21, title = "카카오 공채", dueDate = TODAY.plusDays(20)),
                    posting(id = 22, title = "네이버 인턴", dueDate = TODAY.plusDays(3)),
                )

            assertEquals(
                listOf(20L),
                ids(FeedQuery(deadline = FeedDeadlineFilter.WithinWeek, searchQuery = "카카오"), postings),
            )
        }

    @Test
    fun `서버 쿼리는 커서와 함께 그대로 옮기고 nextCursor 는 필터로 비어도 남긴다`() =
        runTest {
            val repository =
                FakePostingRepository(
                    onGetPostings = { Result.success(CursorPage(items = listOf(expired), nextCursor = "next")) },
                )
            val query =
                FeedQuery(
                    types = setOf(PostingType.Scholarship),
                    boardIds = setOf(3L),
                    minScore = 80,
                    unreadOnly = true,
                    sort = PostingSort.DueAsc,
                )

            val page = useCase(repository)(query, cursor = "abc").getOrThrow()

            assertEquals(FeedPage(postings = emptyList(), nextCursor = "next"), page)
            assertTrue(page.hasNext)
            assertEquals(
                PostingQuery(
                    boardIds = listOf(3L),
                    types = listOf(PostingType.Scholarship),
                    minScore = 80,
                    unreadOnly = true,
                    dueFrom = TODAY,
                    includeNoDueDate = true,
                    sort = PostingSort.DueAsc,
                    cursor = "abc",
                ),
                repository.queries.single(),
            )
        }

    @Test
    fun `조회가 실패하면 실패를 그대로 돌려준다`() =
        runTest {
            val repository =
                FakePostingRepository.strict().apply {
                    onGetPostings = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
                }

            val outcome = useCase(repository)(FeedQuery())

            assertTrue(outcome.exceptionOrNull() is CoreDataFailure.NetworkUnavailable)
        }
}

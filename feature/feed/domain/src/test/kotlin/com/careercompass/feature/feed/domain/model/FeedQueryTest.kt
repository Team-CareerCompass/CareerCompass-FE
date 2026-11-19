package com.careercompass.feature.feed.domain.model

import com.careercompass.core.model.posting.PostingQuery
import com.careercompass.core.model.posting.PostingSort
import com.careercompass.core.model.posting.PostingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedQueryTest {
    /**
     * 선택지는 F3-2 레이블 경계 둘뿐이다(이슈 #200). 값을 [FeedQuery.ALLOWED_MIN_SCORES] 에서 읽어 오지 않고
     * **여기 그대로 적는다** — 두 곳이 같은 상수를 보면 상수가 틀려도 초록이라, 이 테스트가 무는 것이 없다.
     */
    @Test
    fun `minScore 는 null 또는 60·80 만 허용한다`() {
        assertEquals(setOf(60, 80), FeedQuery.ALLOWED_MIN_SCORES)
        listOf(null, 60, 80).forEach { FeedQuery(minScore = it) }
        listOf(0, 59, 65, 81, 100).forEach { score ->
            assertThrows(IllegalArgumentException::class.java) { FeedQuery(minScore = score) }
        }
    }

    /** 스펙 F2-3 이 적었던 70 은 이제 선택지가 아니다 — 만들려 하면 그 자리에서 거절된다. */
    @Test
    fun `없어진 선택지 70 은 조회 조건으로 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { FeedQuery(minScore = 70) }
    }

    @Test
    fun `검색어는 앞뒤 공백을 지워 저장하고 같은 값으로 취급한다`() {
        val query = FeedQuery(searchQuery = "  카카오 인턴 ")

        assertEquals("카카오 인턴", query.searchQuery)
        assertTrue(query.hasSearchQuery)
        assertEquals(FeedQuery(searchQuery = "카카오 인턴"), query)
        assertEquals(FeedQuery(searchQuery = "카카오 인턴").hashCode(), query.hashCode())
        assertEquals("", query.copy(searchQuery = "   ").searchQuery)
        assertFalse(FeedQuery().hasSearchQuery)
    }

    @Test
    fun `빈 필터는 「전체」 마감 프리셋만 실은 서버 쿼리로 옮긴다`() {
        val postingQuery = FeedQuery().toPostingQuery(cursor = null, today = TODAY)

        assertEquals(PostingQuery(dueFrom = TODAY, includeNoDueDate = true), postingQuery)
        assertEquals(emptyList<Long>(), postingQuery.boardIds)
        assertEquals(emptyList<PostingType>(), postingQuery.types)
        assertEquals(null, postingQuery.minScore)
        assertFalse(postingQuery.unreadOnly)
        assertEquals(PostingSort.CollectedDesc, postingQuery.sort)
        assertEquals(PostingQuery.DEFAULT_LIMIT, postingQuery.limit)
    }

    @Test
    fun `조건은 정렬된 목록으로 옮기고 검색어·마감일도 싣는다`() {
        val query =
            FeedQuery(
                types = setOf(PostingType.Contest, PostingType.Recruit),
                boardIds = setOf(7L, 3L),
                deadline = FeedDeadlineFilter.WithinWeek,
                minScore = 80,
                unreadOnly = true,
                sort = PostingSort.ScoreDesc,
                searchQuery = "카카오",
            )

        val postingQuery = query.toPostingQuery(cursor = "abc", today = TODAY, limit = 50)

        assertEquals(
            PostingQuery(
                boardIds = listOf(3L, 7L),
                types = listOf(PostingType.Recruit, PostingType.Contest),
                minScore = 80,
                unreadOnly = true,
                searchQuery = "카카오",
                dueFrom = TODAY,
                dueTo = TODAY.plusDays(7),
                includeNoDueDate = false,
                sort = PostingSort.ScoreDesc,
                cursor = "abc",
                limit = 50,
            ),
            postingQuery,
        )
    }

    @Test
    fun `isDefault 는 아무 조건도 걸리지 않은 조회만 참이다`() {
        assertTrue(FeedQuery().isDefault)
        assertTrue(FeedQuery(searchQuery = "   ").isDefault)

        assertFalse(FeedQuery(types = setOf(PostingType.Recruit)).isDefault)
        assertFalse(FeedQuery(boardIds = setOf(1L)).isDefault)
        assertFalse(FeedQuery(deadline = FeedDeadlineFilter.WithinWeek).isDefault)
        assertFalse(FeedQuery(deadline = FeedDeadlineFilter.Range(start = NOVEMBER_FIRST, end = null)).isDefault)
        assertFalse(FeedQuery(minScore = 60).isDefault)
        assertFalse(FeedQuery(unreadOnly = true).isDefault)
        assertFalse(FeedQuery(sort = PostingSort.DueAsc).isDefault)
        assertFalse(FeedQuery(searchQuery = "카카오").isDefault)
        assertTrue(FeedQuery(searchQuery = "카카오").copy(searchQuery = "").isDefault)
    }

    @Test
    fun `범위는 한쪽만 골라도 되지만 둘 다 비거나 뒤집히면 만들 수 없다`() {
        FeedDeadlineFilter.Range(start = NOVEMBER_FIRST, end = null)
        FeedDeadlineFilter.Range(start = null, end = NOVEMBER_LAST)
        FeedDeadlineFilter.Range(start = NOVEMBER_FIRST, end = NOVEMBER_FIRST)

        assertThrows(IllegalArgumentException::class.java) {
            FeedDeadlineFilter.Range(start = null, end = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedDeadlineFilter.Range(start = NOVEMBER_LAST, end = NOVEMBER_FIRST)
        }
    }

    @Test
    fun `범위는 양 끝을 포함하고 열린 쪽은 언제나 통과한다`() {
        val november = FeedDeadlineFilter.Range(start = NOVEMBER_FIRST, end = NOVEMBER_LAST)

        assertTrue(november.contains(NOVEMBER_FIRST))
        assertTrue(november.contains(NOVEMBER_LAST))
        assertFalse(november.contains(NOVEMBER_FIRST.minusDays(1)))
        assertFalse(november.contains(NOVEMBER_LAST.plusDays(1)))

        val fromNovember = FeedDeadlineFilter.Range(start = NOVEMBER_FIRST, end = null)
        assertTrue(fromNovember.contains(NOVEMBER_LAST.plusYears(1)))
        assertFalse(fromNovember.contains(NOVEMBER_FIRST.minusDays(1)))

        val untilNovember = FeedDeadlineFilter.Range(start = null, end = NOVEMBER_LAST)
        assertTrue(untilNovember.contains(NOVEMBER_FIRST.minusYears(1)))
        assertFalse(untilNovember.contains(NOVEMBER_LAST.plusDays(1)))
    }

    @Test
    fun `copy 는 바꾼 필드만 반영한다`() {
        val base = FeedQuery(types = setOf(PostingType.Scholarship), minScore = 60)

        val copied = base.copy(unreadOnly = true)

        assertEquals(setOf(PostingType.Scholarship), copied.types)
        assertEquals(60, copied.minScore)
        assertTrue(copied.unreadOnly)
        assertFalse(copied == base)
    }

    @Test
    fun `마감일 프리셋은 API_SPEC v0_3 매핑대로 절대 날짜로 싣는다`() {
        fun mapped(deadline: FeedDeadlineFilter) = FeedQuery(deadline = deadline).toPostingQuery(cursor = null, today = TODAY)

        assertEquals(
            PostingQuery(dueFrom = TODAY, dueTo = TODAY.plusDays(30), includeNoDueDate = false),
            mapped(FeedDeadlineFilter.WithinMonth),
        )
        assertEquals(PostingQuery(includeExpired = true, includeNoDueDate = true), mapped(FeedDeadlineFilter.IncludeExpired))
        assertEquals(
            PostingQuery(dueTo = TODAY.minusDays(1), includeNoDueDate = false),
            mapped(FeedDeadlineFilter.Range(start = null, end = TODAY.minusDays(1))),
        )
    }

    @Test
    fun `빈 검색어는 q 를 싣지 않는다`() {
        assertEquals(null, FeedQuery(searchQuery = "   ").toPostingQuery(cursor = null, today = TODAY).searchQuery)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 19)
    }
}

private val NOVEMBER_FIRST: LocalDate = LocalDate.of(2026, 11, 1)
private val NOVEMBER_LAST: LocalDate = LocalDate.of(2026, 11, 30)

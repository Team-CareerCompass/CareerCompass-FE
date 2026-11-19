package com.careercompass.core.data.repoimpl.posting

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.posting.PostingQuery
import com.careercompass.core.model.posting.PostingSort
import com.careercompass.core.model.posting.PostingType
import com.careercompass.core.network.dto.PostingBoardDto
import com.careercompass.core.network.dto.PostingDetailDto
import com.careercompass.core.network.dto.PostingDto
import com.careercompass.core.network.dto.PostingListDto
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.core.network.service.PostingApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostingRepositoryImplTest {
    private data class ListCall(
        val boardIds: List<Long>?,
        val types: List<String>?,
        val minScore: Int?,
        val unreadOnly: Boolean?,
        val sort: String,
        val cursor: String?,
        val limit: Int,
        val search: SearchParams = SearchParams(),
    )

    private data class SearchParams(
        val q: String? = null,
        val dueFrom: String? = null,
        val dueTo: String? = null,
        val includeNoDueDate: Boolean? = null,
        val includeExpired: Boolean? = null,
    )

    private class FakePostingApi : PostingApiService {
        val listCalls = mutableListOf<ListCall>()
        val bookmarkCalls = mutableListOf<Pair<Long, Boolean>>()
        var detailThrows: Throwable? = null

        override suspend fun getPostings(
            boardIds: List<Long>?,
            types: List<String>?,
            minScore: Int?,
            unreadOnly: Boolean?,
            q: String?,
            dueFrom: String?,
            dueTo: String?,
            includeNoDueDate: Boolean?,
            includeExpired: Boolean?,
            sort: String,
            cursor: String?,
            limit: Int,
        ): BaseResponse<PostingListDto> {
            listCalls +=
                ListCall(
                    boardIds,
                    types,
                    minScore,
                    unreadOnly,
                    sort,
                    cursor,
                    limit,
                    SearchParams(q, dueFrom, dueTo, includeNoDueDate, includeExpired),
                )
            return BaseResponse(ok = true, data = PostingListDto(postings = listOf(posting()), nextCursor = ""))
        }

        override suspend fun getPostingDetail(id: Long): BaseResponse<PostingDetailDto> {
            detailThrows?.let { throw it }
            return BaseResponse(
                ok = true,
                data =
                    PostingDetailDto(
                        id = id,
                        title = "t",
                        type = "contest",
                        board = PostingBoardDto(3, "b"),
                        rawContent = "본문",
                        url = "https://x",
                        collectedAt = "2026-05-18T07:00:00+09:00",
                        isRead = false,
                        isBookmarked = false,
                        similar = emptyList(),
                    ),
            )
        }

        override suspend fun addBookmark(id: Long): BaseResponse<Unit> {
            bookmarkCalls += id to true
            return BaseResponse(ok = true)
        }

        override suspend fun removeBookmark(id: Long): BaseResponse<Unit> {
            bookmarkCalls += id to false
            return BaseResponse(ok = true)
        }

        override suspend fun markRead(id: Long): BaseResponse<Unit> = BaseResponse(ok = true)

        private fun posting() =
            PostingDto(
                101,
                "t",
                "recruit",
                PostingBoardDto(3, "b"),
                "2026-05-25",
                "2026-05-18T07:00:00+09:00",
                88,
                "very_suitable",
                false,
                false,
            )
    }

    private val api = FakePostingApi()
    private val repository = PostingRepositoryImpl(api)

    @Test
    fun `조회 조건을 wire 파라미터로 옮기고 빈 필터는 생략한다`() =
        runTest {
            val page =
                repository
                    .getPostings(
                        PostingQuery(
                            boardIds = listOf(3),
                            types = listOf(PostingType.Recruit, PostingType.Scholarship),
                            minScore = 60,
                            unreadOnly = true,
                            sort = PostingSort.ScoreDesc,
                            cursor = "abc",
                            limit = 10,
                        ),
                    ).getOrThrow()

            assertEquals(ListCall(listOf(3), listOf("recruit", "scholarship"), 60, true, "score_desc", "abc", 10), api.listCalls.single())
            assertEquals(1, page.items.size)
            assertNull(page.nextCursor)

            repository.getPostings(PostingQuery()).getOrThrow()
            assertEquals(ListCall(null, null, null, null, "collected_desc", null, 20), api.listCalls.last())
        }

    @Test
    fun `검색어와 마감일 조건을 v0_3 wire 파라미터로 싣는다`() =
        runTest {
            repository
                .getPostings(
                    PostingQuery(
                        searchQuery = "인턴",
                        dueFrom = java.time.LocalDate.of(2026, 11, 19),
                        dueTo = java.time.LocalDate.of(2026, 11, 26),
                        includeNoDueDate = false,
                    ),
                ).getOrThrow()

            assertEquals(SearchParams("인턴", "2026-11-19", "2026-11-26", false, null), api.listCalls.single().search)
        }

    @Test
    fun `북마크 설정은 값에 따라 POST 와 DELETE 를 가른다`() =
        runTest {
            repository.setBookmarked(101, bookmarked = true).getOrThrow()
            repository.setBookmarked(101, bookmarked = false).getOrThrow()

            assertEquals(listOf(101L to true, 101L to false), api.bookmarkCalls)
        }

    @Test
    fun `상세 조회 실패는 도메인 사유로 옮긴다`() =
        runTest {
            api.detailThrows = ApiException("POSTING_NOT_FOUND", "공고를 찾을 수 없습니다", "실패", status = 404)

            assertTrue(repository.getPostingDetail(999).exceptionOrNull() is CoreDataFailure.NotFound)
            assertEquals(
                PostingType.Contest,
                repository
                    .getPostingDetail(1)
                    .let {
                        api.detailThrows = null
                        repository.getPostingDetail(1)
                    }.getOrThrow()
                    .type,
            )
        }
}

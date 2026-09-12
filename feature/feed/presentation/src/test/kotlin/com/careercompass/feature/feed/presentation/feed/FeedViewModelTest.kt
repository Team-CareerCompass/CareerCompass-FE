package com.careercompass.feature.feed.presentation.feed

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ERROR_REPORT_KEY_TRANSPORT
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeBoardRepository
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingQuery
import com.careercompass.core.model.posting.PostingSort
import com.careercompass.core.model.posting.PostingType
import com.careercompass.feature.feed.domain.model.FeedDeadlineFilter
import com.careercompass.feature.feed.domain.model.FeedSnapshot
import com.careercompass.feature.feed.domain.testing.FakeFeedSnapshotRepository
import com.careercompass.feature.feed.domain.usecase.CountTodayNewPostingsUseCase
import com.careercompass.feature.feed.domain.usecase.GetBoardsUseCase
import com.careercompass.feature.feed.domain.usecase.GetFeedPageUseCase
import com.careercompass.feature.feed.domain.usecase.TogglePostingBookmarkUseCase
import com.careercompass.feature.feed.presentation.FIXED_CLOCK
import com.careercompass.feature.feed.presentation.FeedListingCategory
import com.careercompass.feature.feed.presentation.FeedLoadMoreState
import com.careercompass.feature.feed.presentation.FeedUiEvent
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.TODAY
import com.careercompass.feature.feed.presentation.board
import com.careercompass.feature.feed.presentation.feedfilter.FeedDeadlineRange
import com.careercompass.feature.feed.presentation.feedfilter.FeedDeadlineRangeEndpoint
import com.careercompass.feature.feed.presentation.feedfilter.FeedDeadlineRangeError
import com.careercompass.feature.feed.presentation.feedfilter.FeedFilterEvent
import com.careercompass.feature.feed.presentation.feedfilter.FeedMinScoreFilter
import com.careercompass.feature.feed.presentation.feedfilter.FeedSortMenuEvent
import com.careercompass.feature.feed.presentation.feedfilter.FeedSortOption
import com.careercompass.feature.feed.presentation.posting
import com.careercompass.feature.feed.presentation.profile
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException
import com.careercompass.feature.feed.presentation.feedfilter.FeedDeadlineFilter as UiDeadlineFilter

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reporter = RecordingErrorReporter()

    private fun viewModel(
        postingRepository: FakePostingRepository = FakePostingRepository(initial = List(3) { posting(id = it + 1L) }),
        boardRepository: FakeBoardRepository = FakeBoardRepository(initial = listOf(board(id = 1), board(id = 2))),
        profileRepository: FakeUserProfileRepository = FakeUserProfileRepository(initialProfile = profile()),
        snapshotRepository: FakeFeedSnapshotRepository = FakeFeedSnapshotRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): FeedViewModel =
        FeedViewModel(
            getFeedPage = GetFeedPageUseCase(postingRepository, FIXED_CLOCK),
            countTodayNewPostings = CountTodayNewPostingsUseCase(postingRepository, FIXED_CLOCK),
            togglePostingBookmark = TogglePostingBookmarkUseCase(postingRepository),
            getBoards = GetBoardsUseCase(boardRepository),
            userProfileRepository = profileRepository,
            feedSnapshotRepository = snapshotRepository,
            errorReporter = reporter,
            clock = FIXED_CLOCK,
            savedStateHandle = savedStateHandle,
        )

    /** 희망 직무·관심 태그가 비어 적합도를 낼 수 없는 프로필. */
    private fun emptyProfileRepository() =
        FakeUserProfileRepository(initialProfile = profile(jobInterests = emptyList(), tags = emptyList()))

    /** 네트워크 단절로 첫 조회가 실패하는 리포지토리. */
    private fun offlinePostings() =
        FakePostingRepository().apply {
            onGetPostings = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
        }

    private fun maintenancePostings(initial: List<Posting> = emptyList()) =
        FakePostingRepository(initial = initial).apply {
            onGetPostings = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException())) }
        }

    private fun snapshot(vararg ids: Long) = FeedSnapshot(postings = ids.map { posting(id = it) }, savedAt = FIXED_CLOCK.instant())

    /**
     * **조건이 걸린 조회에서만** 실패하는 서버 — 이슈 #144 의 재현이다.
     *
     * 적합도는 LLM 산출값이라, 정렬을 「적합도순」으로 바꾸거나 최소 적합도를 걸면 서버가 그 조건에서만
     * 503 `LLM_UNAVAILABLE` 을 낸다. 기본 조회는 그대로 성공하므로 「조건을 지우면 살아난다」가 참이다.
     */
    private fun scoreConditionFails(
        initial: List<Posting> = List(3) { posting(id = it + 1L) },
        failure: () -> Throwable = { CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException()) },
    ) = FakePostingRepository(initial = initial).apply {
        onGetPostings = { query ->
            if (query.sort == PostingSort.ScoreDesc || query.minScore != null) {
                Result.failure(failure())
            } else {
                Result.success(CursorPage(items = initial, nextCursor = null))
            }
        }
    }

    /**
     * 마감이 지나 클라이언트 필터가 통째로 걸러 내는 페이지 — 커서만 `c1`·`c2`… 로 한 칸씩 나아간다.
     *
     * 「받은 것이 없다」와 「서버에 없다」가 갈리는 자리를 그대로 재현한다: 항목은 0건인데 커서는 남는다.
     */
    private fun expiredPage(cursor: String?): Result<CursorPage<Posting>> {
        val followed = cursor?.removePrefix("c")?.toInt() ?: 0
        return Result.success(
            CursorPage(
                items = listOf(posting(id = followed + 1L, dueDate = TODAY.minusDays(1))),
                nextCursor = "c${followed + 1}",
            ),
        )
    }

    @Test
    fun `초기 로드는 첫 페이지·오늘 신규 개수·이름·게시판을 함께 채운다`() {
        val state = viewModel().state.value

        assertEquals(FeedLoadState.Loaded, state.loadState)
        assertEquals(listOf(1L, 2L, 3L), state.postings.map(Posting::id))
        assertNull(state.nextCursor)
        assertEquals(3, state.todayNewCount)
        assertEquals("일혁", state.userName)
        assertEquals(listOf(1L, 2L), state.boards.map { it.id })
        assertEquals(FeedListingCategory.All, state.selectedCategory)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun `검색어는 즉시 상태에 반영되고 300ms 뒤에 재조회한다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakePostingRepository(
                    initial = listOf(posting(id = 1, title = "카카오 인턴"), posting(id = 2, title = "네이버 공채")),
                )
            val viewModel = viewModel(postingRepository = repository)
            val queriesBefore = repository.queries.size

            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("카카오"))

            assertEquals("카카오", viewModel.state.value.searchInput)
            assertEquals("", viewModel.state.value.query.searchQuery)
            advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS - 1)
            assertEquals(queriesBefore, repository.queries.size)

            advanceTimeBy(2)

            assertEquals("카카오", viewModel.state.value.query.searchQuery)
            assertEquals(
                listOf(1L),
                viewModel.state.value.postings
                    .map(Posting::id),
            )
        }

    @Test
    fun `연속 입력은 마지막 검색어로 한 번만 재조회한다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakePostingRepository(initial = listOf(posting(id = 1, title = "카카오 인턴")))
            val viewModel = viewModel(postingRepository = repository)
            val queriesBefore = repository.queries.size

            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("카"))
            advanceTimeBy(100)
            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("카카"))
            advanceTimeBy(100)
            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("카카오"))
            advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS + 1)

            assertEquals(queriesBefore + 1, repository.queries.size)
            assertEquals("카카오", viewModel.state.value.query.searchQuery)
        }

    @Test
    fun `카테고리 칩은 types 하나로 재조회한다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1), posting(id = 2, type = PostingType.Scholarship)))
        val viewModel = viewModel(postingRepository = repository)

        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Scholarship))

        assertEquals(listOf(PostingType.Scholarship), repository.queries.last().types)
        assertEquals(FeedListingCategory.Scholarship, viewModel.state.value.selectedCategory)
        assertEquals(
            listOf(2L),
            viewModel.state.value.postings
                .map(Posting::id),
        )
    }

    @Test
    fun `같은 칩을 다시 누르면 재조회하지 않는다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        val viewModel = viewModel(postingRepository = repository)
        val queriesBefore = repository.queries.size

        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.All))

        assertEquals(queriesBefore, repository.queries.size)
    }

    @Test
    fun `필터 시트는 적용할 때만 조회 조건에 반영된다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1, boardId = 1, score = 80, dueDate = TODAY.plusDays(2))))
        val viewModel = viewModel(postingRepository = repository)
        val queriesBefore = repository.queries.size

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.BoardToggled("1"))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.WithinWeek))
        viewModel.onFilterEvent(FeedFilterEvent.MinScoreSelected(FeedMinScoreFilter.AtLeast80))
        viewModel.onFilterEvent(FeedFilterEvent.UnreadOnlyToggled)

        val draft = requireNotNull(viewModel.state.value.filterDraft)
        assertEquals(setOf(1L), draft.boardIds)
        assertEquals(queriesBefore, repository.queries.size)

        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)

        val state = viewModel.state.value
        assertNull(state.filterDraft)
        assertEquals(setOf(1L), state.query.boardIds)
        assertEquals(FeedDeadlineFilter.WithinWeek, state.query.deadline)
        assertEquals(80, state.query.minScore)
        assertTrue(state.query.unreadOnly)
        assertEquals(4, state.activeFilterCount)
        assertEquals(
            PostingQuery(boardIds = listOf(1L), minScore = 80, unreadOnly = true),
            repository.queries.last(),
        )
        assertEquals(listOf(1L), state.postings.map(Posting::id))
    }

    /**
     * 이슈 #155 — 목록에 없는 게시판 조건을 끄는 유일한 손짓. 「사라진 게시판」 태그를 누르면 **목록에
     * 없는 id 만** 빠지고, 고른 게시판은 남는다. 시트의 다른 조건과 마찬가지로 적용해야 조회가 바뀐다.
     */
    @Test
    fun `사라진 게시판을 끄면 목록에 없는 조건만 빠지고 적용해야 조회에 반영된다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1, boardId = 1)))
        // 게시판 9번은 그 사이 지워졌지만, 조건은 프로세스 사망을 건너 그대로 살아 왔다(#137).
        val viewModel =
            viewModel(
                postingRepository = repository,
                savedStateHandle = SavedStateHandle(mapOf("feed.draft.query.boardIds" to longArrayOf(1L, 9L))),
            )
        assertEquals(setOf(1L, 9L), viewModel.state.value.query.boardIds)
        val queriesBefore = repository.queries.size

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.MissingBoardsCleared)

        assertEquals(setOf(1L), requireNotNull(viewModel.state.value.filterDraft).boardIds)
        assertEquals(queriesBefore, repository.queries.size)

        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)

        val state = viewModel.state.value
        assertEquals(setOf(1L), state.query.boardIds)
        assertEquals(1, state.activeFilterCount)
        assertEquals(listOf(1L), repository.queries.last().boardIds)
    }

    /**
     * 게시판 목록 조회가 실패해도 걸어 둔 조건은 저절로 사라지지 않는다(이슈 #155).
     *
     * 못 받은 목록을 「게시판이 없다」로 읽어 조건을 버리면, 지하철에서 앱을 켠 사용자의 필터가 저 혼자
     * 풀린다. 앱은 조건을 그대로 두고, 시트가 「확인 못 한 게시판」으로 보여 사용자가 정하게 한다.
     */
    @Test
    fun `게시판 목록 조회가 실패해도 걸어 둔 게시판 조건은 남는다`() {
        val boardRepository = FakeBoardRepository.strict().apply { onGetBoards = { Result.failure(RuntimeException("boom")) } }

        val state =
            viewModel(
                boardRepository = boardRepository,
                savedStateHandle = SavedStateHandle(mapOf("feed.draft.query.boardIds" to longArrayOf(9L))),
            ).state.value

        assertFalse(state.boardsLoaded)
        assertEquals(setOf(9L), state.query.boardIds)
        assertEquals(1, state.activeFilterCount)
    }

    /**
     * 이슈 #206 — 빈 목록의 「사라진 게시판 조건 빼기」는 시트를 거치지 않고 조건을 빼고 다시 읽는다.
     *
     * 시트 경로(`FeedFilterEvent.MissingBoardsCleared` + 「적용」)와 **같은 규칙**을 쓰되(`missingFrom`)
     * 초안이 아니라 조회 조건을 바로 고친다. 시트를 열어 주는 것으로 끝나면 「열어 보기 전에는 원인을
     * 모른다」는, 이 사유가 생긴 문제가 그대로 남는다.
     */
    @Test
    fun `빈 목록에서 사라진 게시판 조건을 빼면 그 id 만 빠지고 곧바로 다시 조회한다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1, boardId = 1)))
        val viewModel =
            viewModel(
                postingRepository = repository,
                savedStateHandle = SavedStateHandle(mapOf("feed.draft.query.boardIds" to longArrayOf(1L, 9L))),
            )
        assertEquals(setOf(1L, 9L), viewModel.state.value.query.boardIds)
        val queriesBefore = repository.queries.size

        viewModel.onEvent(FeedUiEvent.MissingBoardsCleared)

        val state = viewModel.state.value
        // 고른 게시판은 남고 지워진 것만 빠진다 — 「필터 초기화」처럼 멀쩡한 조건까지 풀지 않는다.
        assertEquals(setOf(1L), state.query.boardIds)
        assertEquals(1, state.activeFilterCount)
        // 시트를 열지 않는다. 조회는 「적용」을 기다리지 않고 바로 나간다.
        assertNull(state.filterDraft)
        assertTrue(repository.queries.size > queriesBefore)
        assertEquals(listOf(1L), repository.queries.last().boardIds)
    }

    /**
     * 게시판 목록을 못 받았으면 빈 목록 쪽 손잡이는 아무것도 하지 않는다(이슈 #206).
     *
     * 그 상태에서는 화면이 「지워졌어요」라고 말하지 않으므로 버튼도 없다. 그래도 계약을 여기서 한 번 더
     * 지켜, 조회 실패를 근거로 사용자의 조건이 지워지는 길을 남기지 않는다. 시트의 태그는 다르다 —
     * 그것은 「확인 못 한 게시판」이라고 밝힌 채 사용자가 직접 누르는 것이다(#155).
     */
    @Test
    fun `게시판 목록을 못 받았으면 빈 목록의 조건 빼기는 아무 일도 하지 않는다`() {
        val boardRepository = FakeBoardRepository.strict().apply { onGetBoards = { Result.failure(RuntimeException("boom")) } }
        val repository = FakePostingRepository(initial = emptyList())
        val viewModel =
            viewModel(
                postingRepository = repository,
                boardRepository = boardRepository,
                savedStateHandle = SavedStateHandle(mapOf("feed.draft.query.boardIds" to longArrayOf(9L))),
            )
        val queriesBefore = repository.queries.size

        viewModel.onEvent(FeedUiEvent.MissingBoardsCleared)

        assertFalse(viewModel.state.value.boardsLoaded)
        assertEquals(setOf(9L), viewModel.state.value.query.boardIds)
        assertEquals(queriesBefore, repository.queries.size)
    }

    @Test
    fun `직접 지정 범위는 날짜를 고른 뒤에야 적용되고 다시 열면 그대로 남는다`() {
        val repository =
            FakePostingRepository(
                initial =
                    listOf(
                        posting(id = 1, dueDate = TODAY.plusDays(10)),
                        posting(id = 2, dueDate = TODAY.plusDays(40)),
                        posting(id = 3, dueDate = null),
                    ),
            )
        val viewModel = viewModel(postingRepository = repository)

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.Range))

        // 날짜가 하나도 없으면 거를 것이 없다 — 「적용」은 시트를 닫지 않는다.
        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)
        assertNotNull(viewModel.state.value.filterDraft)

        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.Start))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(5)))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.End))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(20)))
        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)

        val state = viewModel.state.value
        assertNull(state.filterDraft)
        assertEquals(
            FeedDeadlineFilter.Range(start = TODAY.plusDays(5), end = TODAY.plusDays(20)),
            state.query.deadline,
        )
        assertEquals(listOf(1L), state.postings.map(Posting::id))
        assertEquals(1, state.activeFilterCount)

        viewModel.onEvent(FeedUiEvent.FilterRequested)

        val reopened = requireNotNull(viewModel.state.value.filterDraft)
        assertEquals(UiDeadlineFilter.Range, reopened.deadline)
        assertEquals(
            FeedDeadlineRange(start = TODAY.plusDays(5), end = TODAY.plusDays(20)),
            reopened.deadlineRange,
        )
    }

    @Test
    fun `뒤집힌 범위는 적용되지 않고 초기화로 지워진다`() {
        val viewModel = viewModel()

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.Range))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.Start))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(20)))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.End))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(5)))

        val draft = requireNotNull(viewModel.state.value.filterDraft)
        assertFalse(draft.isApplicable)
        assertEquals(FeedDeadlineRangeError.StartAfterEnd, draft.deadlineRange.error)

        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)

        assertNotNull(viewModel.state.value.filterDraft)
        assertEquals(FeedDeadlineFilter.All, viewModel.state.value.query.deadline)

        viewModel.onFilterEvent(FeedFilterEvent.ResetClicked)

        assertEquals(FeedFilterDraft.Default, viewModel.state.value.filterDraft)
        assertEquals(
            FeedDeadlineRange(),
            viewModel.state.value.filterDraft
                ?.deadlineRange,
        )
    }

    @Test
    fun `날짜 선택기를 그냥 닫으면 고른 범위는 그대로다`() {
        val viewModel = viewModel()

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.Range))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.Start))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.End))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangePickerDismissed)

        assertEquals(
            FeedDeadlineRange(start = TODAY, end = null, editing = null),
            viewModel.state.value.filterDraft
                ?.deadlineRange,
        )
    }

    @Test
    fun `범위가 걸린 조회는 스냅샷으로 저장하지 않는다`() {
        // 범위도 조건이다 — 부분집합을 「전체」로 저장하면 오프라인 목록이 거짓말을 한다.
        val snapshots = FakeFeedSnapshotRepository()
        val viewModel = viewModel(snapshotRepository = snapshots)
        snapshots.saved.clear()

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.Range))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.Start))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY))
        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)

        assertFalse(viewModel.state.value.query.isDefault)
        assertTrue(snapshots.saved.isEmpty())
    }

    @Test
    fun `필터 시트 초기화는 카테고리까지 되돌리고 닫기는 초안을 버린다`() {
        val viewModel = viewModel()
        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        assertEquals(
            FeedListingCategory.Employment,
            viewModel.state.value.filterDraft
                ?.category,
        )
        viewModel.onFilterEvent(FeedFilterEvent.ResetClicked)
        assertEquals(FeedFilterDraft.Default, viewModel.state.value.filterDraft)
        viewModel.onFilterEvent(FeedFilterEvent.DismissClicked)

        assertNull(viewModel.state.value.filterDraft)
        assertEquals(FeedListingCategory.Employment, viewModel.state.value.selectedCategory)
    }

    @Test
    fun `정렬 선택은 메뉴를 닫고 재조회한다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        val viewModel = viewModel(postingRepository = repository)

        viewModel.onEvent(FeedUiEvent.SortMenuRequested)
        assertTrue(viewModel.state.value.isSortMenuVisible)
        viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.DueAsc))

        assertFalse(viewModel.state.value.isSortMenuVisible)
        assertEquals(PostingSort.DueAsc, viewModel.state.value.query.sort)
        assertEquals(PostingSort.DueAsc, repository.queries.last().sort)
    }

    @Test
    fun `무한 스크롤은 커서를 따라 누적하고 끝에서는 요청하지 않는다`() {
        val repository = FakePostingRepository(initial = List(45) { posting(id = it + 1L) })
        val viewModel = viewModel(postingRepository = repository)
        assertEquals(20, viewModel.state.value.postings.size)
        assertEquals("20", viewModel.state.value.nextCursor)

        viewModel.onLoadMore()
        assertEquals(40, viewModel.state.value.postings.size)
        viewModel.onLoadMore()
        assertEquals(45, viewModel.state.value.postings.size)
        assertNull(viewModel.state.value.nextCursor)
        assertFalse(viewModel.state.value.isLoadingMore)

        val queriesBefore = repository.queries.size
        viewModel.onLoadMore()

        assertEquals(queriesBefore, repository.queries.size)
        assertEquals(
            45,
            viewModel.state.value.postings
                .map(Posting::id)
                .distinct()
                .size,
        )
    }

    @Test
    fun `클라이언트 필터로 빈 페이지가 와도 커서가 있으면 이어 읽는다`() {
        val expired = posting(id = 1, dueDate = TODAY.minusDays(1))
        val valid = posting(id = 2, dueDate = TODAY.plusDays(1))
        val repository =
            FakePostingRepository(
                onGetPostings = { query ->
                    Result.success(
                        when (query.cursor) {
                            null -> CursorPage(items = listOf(expired), nextCursor = "1")
                            else -> CursorPage(items = listOf(valid), nextCursor = null)
                        },
                    )
                },
            )

        val state = viewModel(postingRepository = repository).state.value

        assertEquals(FeedLoadState.Loaded, state.loadState)
        assertEquals(listOf(2L), state.postings.map(Posting::id))
        assertNull(state.nextCursor)
    }

    @Test
    fun `상한까지 따라가고도 빈손이면 커서를 남기고 더 찾아보기로 선다`() {
        // 100건을 훑고도 조건에 맞는 것이 없었다 — 「없음」이 아니라 「아직 못 찾음」이다.
        val repository = FakePostingRepository(onGetPostings = { expiredPage(it.cursor) })

        val state = viewModel(postingRepository = repository).state.value

        assertEquals(FeedLoadState.Loaded, state.loadState)
        assertTrue(state.postings.isEmpty())
        assertEquals(FeedLoadMoreState.Paused, state.loadMore)
        // 커서가 상한만큼 나아간 채 남았다 — 「끝」이 아니라 「여기까지」다.
        assertEquals("c${FeedViewModel.MAX_EMPTY_PAGE_FOLLOW_UPS}", state.nextCursor)
        assertTrue(state.hasNext)
    }

    @Test
    fun `더 찾아보기는 멈춘 자리에서 다음 상한만큼 이어 읽는다`() {
        // 상한은 총량이 아니라 한 걸음의 크기다 — 눌러 준 만큼 계속 간다.
        val valid = posting(id = 99, dueDate = TODAY.plusDays(3))
        val repository =
            FakePostingRepository(
                onGetPostings = { query ->
                    if (query.cursor == "c5") {
                        Result.success(CursorPage(items = listOf(valid), nextCursor = null))
                    } else {
                        expiredPage(query.cursor)
                    }
                },
            )
        val viewModel = viewModel(postingRepository = repository)
        assertEquals(FeedLoadMoreState.Paused, viewModel.state.value.loadMore)

        viewModel.onEvent(FeedUiEvent.LoadMoreSelected)

        val state = viewModel.state.value
        assertEquals(listOf(99L), state.postings.map(Posting::id))
        assertFalse(state.hasNext)
        assertEquals(FeedLoadMoreState.Ready, state.loadMore)
    }

    @Test
    fun `이어 읽어도 한 건도 늘지 않으면 다시 선다`() {
        // 「늘었는가」로 재기 때문에 중복만 실려 와도 자동 추적이 여기서 끝난다 — 무한 되풀이가 없다.
        val same = posting(id = 1, dueDate = TODAY.plusDays(3))
        val repository =
            FakePostingRepository(
                onGetPostings = { Result.success(CursorPage(items = listOf(same), nextCursor = "next")) },
            )
        val viewModel = viewModel(postingRepository = repository)
        assertEquals(FeedLoadMoreState.Ready, viewModel.state.value.loadMore)

        viewModel.onLoadMore()

        assertEquals(
            listOf(1L),
            viewModel.state.value.postings
                .map(Posting::id),
        )
        assertEquals(FeedLoadMoreState.Paused, viewModel.state.value.loadMore)
    }

    @Test
    fun `페이지 실패는 커서를 남긴 채 다시 시도할 자리를 만든다`() {
        // 스낵바는 지나가 버린다 — 되살릴 근거(커서·상태)가 상태에 남아 있어야 다시 시도가 가능하다.
        val repository = FakePostingRepository(initial = List(45) { posting(id = it + 1L) })
        val viewModel = viewModel(postingRepository = repository)
        repository.onGetPostings = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }

        viewModel.onLoadMore()

        assertEquals(FeedLoadMoreState.Failed, viewModel.state.value.loadMore)
        assertEquals(FeedMessage.LoadMoreFailed, viewModel.state.value.message)
        assertEquals("20", viewModel.state.value.nextCursor)

        repository.onGetPostings = null
        viewModel.onEvent(FeedUiEvent.LoadMoreSelected)

        assertEquals(40, viewModel.state.value.postings.size)
        assertEquals(FeedLoadMoreState.Ready, viewModel.state.value.loadMore)
    }

    @Test
    fun `오프라인 스냅샷은 커서를 비워 더 찾아보기를 걸지 않는다`() {
        // 스냅샷에는 이어 읽을 다음 페이지가 없다 — 「더 찾아보기」를 권하면 눌러도 갈 곳이 없다.
        val viewModel =
            viewModel(postingRepository = offlinePostings(), snapshotRepository = FakeFeedSnapshotRepository(initial = snapshot(7L)))

        viewModel.showOfflineSnapshot()

        assertFalse(viewModel.state.value.hasNext)
        assertEquals(FeedLoadMoreState.Ready, viewModel.state.value.loadMore)
    }

    @Test
    fun `북마크는 먼저 뒤집고 실패하면 되돌린 뒤 스낵바를 띄운다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePostingRepository(
                    initial = listOf(posting(id = 1, isBookmarked = false)),
                    onSetBookmarked = { _, _ ->
                        gate.await()
                        Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException()))
                    },
                )
            val viewModel = viewModel(postingRepository = repository)

            viewModel.onEvent(FeedUiEvent.BookmarkToggled("1"))
            assertTrue(
                viewModel.state.value.postings
                    .single()
                    .isBookmarked,
            )

            gate.complete(Unit)

            assertFalse(
                viewModel.state.value.postings
                    .single()
                    .isBookmarked,
            )
            assertEquals(FeedMessage.BookmarkFailed, viewModel.state.value.message)
            // 일시적 전송 실패는 (원인, 단계) 조합의 세션 첫 건만 표본으로 남는다.
            assertEquals(listOf("bookmark"), reporter.stages)
            viewModel.onMessageConsumed()
            assertNull(viewModel.state.value.message)
        }

    /** 북마크 응답이 오가는 사이 새로고침이 목록을 갈아 끼워도, 응답이 그 값을 옛 스냅샷으로 덮지 않는다(#235). */
    @Test
    fun `북마크 응답이 늦어도 그 사이 새로고침으로 바뀐 값을 지우지 않는다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePostingRepository(
                    initial = listOf(posting(id = 1, isBookmarked = false, isRead = false)),
                    onSetBookmarked = { _, _ ->
                        gate.await()
                        Result.success(Unit)
                    },
                )
            val viewModel = viewModel(postingRepository = repository)

            viewModel.onEvent(FeedUiEvent.BookmarkToggled("1"))
            repository.postings[0] = posting(id = 1, isBookmarked = false, isRead = true)
            viewModel.refresh()
            assertTrue(
                viewModel.state.value.postings
                    .single()
                    .isRead,
            )

            gate.complete(Unit)

            val refreshed =
                viewModel.state.value.postings
                    .single()
            assertTrue(refreshed.isRead)
            assertTrue(refreshed.isBookmarked)
        }

    @Test
    fun `북마크 성공은 서버가 돌려준 값으로 확정한다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1, isBookmarked = false)))
        val viewModel = viewModel(postingRepository = repository)

        viewModel.onEvent(FeedUiEvent.BookmarkToggled("1"))

        assertTrue(
            viewModel.state.value.postings
                .single()
                .isBookmarked,
        )
        assertEquals(listOf(1L to true), repository.bookmarkCalls.toList())
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `네트워크 단절은 네트워크 오류 상태가 되고 다시 시도로 복구한다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        repository.onGetPostings = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
        val viewModel = viewModel(postingRepository = repository)

        assertEquals(FeedLoadState.Failed(FeedFailureReason.NetworkUnavailable), viewModel.state.value.loadState)

        repository.onGetPostings = null
        viewModel.retry()

        assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
        assertEquals(
            listOf(1L),
            viewModel.state.value.postings
                .map(Posting::id),
        )
    }

    @Test
    fun `서버 오류는 일반 실패 상태다`() {
        val repository = FakePostingRepository()
        repository.onGetPostings = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }

        assertEquals(FeedLoadState.Failed(FeedFailureReason.Generic), viewModel(postingRepository = repository).state.value.loadState)
        assertTrue(reporter.stages.contains("feed_load"))
    }

    @Test
    fun `서버 점검 503 은 점검 상태가 되고 다시 시도로 복구한다`() {
        val repository = maintenancePostings(initial = listOf(posting(id = 1)))
        val viewModel = viewModel(postingRepository = repository)

        assertEquals(FeedLoadState.Failed(FeedFailureReason.Maintenance), viewModel.state.value.loadState)

        repository.onGetPostings = null
        viewModel.retry()

        assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
    }

    @Test
    fun `서버 점검은 빼고 네트워크 단절은 세션 표본 한 건만 남긴다`() {
        viewModel(postingRepository = maintenancePostings())

        // 503 은 서버가 스스로 알린 상태라 통째로 뺀다.
        assertTrue(reporter.records.isEmpty())

        repeat(3) { viewModel(postingRepository = offlinePostings()) }

        // 재시도 폭주가 표본을 독점하지 못하게 (원인, 단계) 조합당 세션 첫 건만 남는다 —
        // 단계가 다르면 따로 세므로 오늘 신규 개수와 피드 로드가 각각 한 건씩 남는다.
        assertEquals(listOf("today_count", "feed_load"), reporter.stages)
        assertTrue(reporter.records.all { it.second[ERROR_REPORT_KEY_TRANSPORT] == "transient" })
    }

    @Test
    fun `서버 점검으로 실패해도 저장된 스냅샷을 오프라인 제안으로 싣는다`() {
        val snapshots = FakeFeedSnapshotRepository(initial = snapshot(7L, 8L))

        val state = viewModel(postingRepository = maintenancePostings(), snapshotRepository = snapshots).state.value

        assertEquals(FeedLoadState.Failed(FeedFailureReason.Maintenance), state.loadState)
        assertEquals(listOf(7L, 8L), state.offlineSnapshot?.postings?.map(Posting::id))
    }

    @Test
    fun `401 은 세션 종료 신호를 올린다`() {
        val repository = FakePostingRepository()
        repository.onGetPostings = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())) }
        val viewModel = viewModel(postingRepository = repository)

        assertTrue(viewModel.state.value.sessionEnded)

        viewModel.onSessionEndedConsumed()

        assertFalse(viewModel.state.value.sessionEnded)
    }

    @Test
    fun `당겨서 새로고침은 목록을 유지한 채 다시 받는다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        val viewModel = viewModel(postingRepository = repository)
        repository.postings += posting(id = 2)

        viewModel.refresh()

        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(
            listOf(1L, 2L),
            viewModel.state.value.postings
                .map(Posting::id)
                .sorted(),
        )
    }

    @Test
    fun `새로고침 실패는 기존 목록을 지우지 않고 스낵바만 띄운다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        val viewModel = viewModel(postingRepository = repository)
        repository.onGetPostings = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }

        viewModel.refresh()

        assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
        assertEquals(
            listOf(1L),
            viewModel.state.value.postings
                .map(Posting::id),
        )
        assertEquals(FeedMessage.RefreshFailed, viewModel.state.value.message)
    }

    @Test
    fun `카드·알림·게시판 이동은 단발 신호로 올라가고 소비되면 비워진다`() {
        val viewModel = viewModel()

        viewModel.onEvent(FeedUiEvent.ListingSelected("2"))
        assertEquals(FeedDestination.PostingDetail(2L), viewModel.state.value.pendingNavigation)
        viewModel.onNavigationConsumed()
        assertNull(viewModel.state.value.pendingNavigation)

        viewModel.onEvent(FeedUiEvent.NotificationsSelected)
        assertEquals(FeedDestination.Notifications, viewModel.state.value.pendingNavigation)
        viewModel.onBoardListRequested()
        assertEquals(FeedDestination.BoardList, viewModel.state.value.pendingNavigation)
        viewModel.onBoardRegisterRequested()
        assertEquals(FeedDestination.BoardRegister, viewModel.state.value.pendingNavigation)

        viewModel.onEvent(FeedUiEvent.CompleteProfileSelected)
        assertEquals(FeedDestination.Profile, viewModel.state.value.pendingNavigation)
    }

    @Test
    fun `빈 목록의 게시판 등록 안내는 등록 화면으로 보낸다`() {
        val viewModel = viewModel(postingRepository = FakePostingRepository())

        viewModel.onEvent(FeedUiEvent.BoardRegisterSelected)

        assertEquals(FeedDestination.BoardRegister, viewModel.state.value.pendingNavigation)
    }

    @Test
    fun `빈 목록의 필터 초기화는 칩까지 풀고 정렬은 남긴다`() {
        val viewModel = viewModel()
        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))
        viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.DueAsc))
        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.MinScoreSelected(FeedMinScoreFilter.AtLeast80))
        viewModel.onFilterEvent(FeedFilterEvent.UnreadOnlyToggled)
        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)
        assertTrue(viewModel.state.value.hasActiveFilter)

        viewModel.onEvent(FeedUiEvent.FilterResetSelected)

        val query = viewModel.state.value.query
        assertFalse(viewModel.state.value.hasActiveFilter)
        assertTrue(query.types.isEmpty())
        assertNull(query.minScore)
        assertFalse(query.unreadOnly)
        assertEquals(FeedDeadlineFilter.All, query.deadline)
        assertEquals(PostingSort.DueAsc, query.sort)
    }

    @Test
    fun `필터 초기화는 검색어를 지우지 않는다`() =
        runTest {
            // 검색어와 필터는 각자의 사유가 각자의 행동을 준다 — 하나를 풀 때 다른 하나까지 지우면
            // 사용자가 되돌린 적 없는 조건이 사라진다.
            val viewModel = viewModel()
            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("백엔드"))
            advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS + 1)

            viewModel.onEvent(FeedUiEvent.FilterResetSelected)

            assertEquals("백엔드", viewModel.state.value.query.searchQuery)
            assertEquals("백엔드", viewModel.state.value.searchInput)
        }

    @Test
    fun `게시판 조회에 성공해야 목록을 받아 봤다고 표시한다`() {
        assertTrue(viewModel().state.value.boardsLoaded)

        val failing = FakeBoardRepository.strict().apply { onGetBoards = { Result.failure(RuntimeException("boom")) } }

        assertFalse(viewModel(boardRepository = failing).state.value.boardsLoaded)
    }

    @Test
    fun `화면에 돌아오면 게시판만 다시 읽는다`() {
        // 「등록한 게시판이 없어요」를 보고 등록하러 갔다 온 사용자에게 같은 안내가 남지 않게 한다.
        val boards = FakeBoardRepository(initial = emptyList())
        val postings = FakePostingRepository()
        val viewModel = viewModel(postingRepository = postings, boardRepository = boards)
        assertTrue(
            viewModel.state.value.boards
                .isEmpty(),
        )
        boards.boards += board(id = 9)
        val queriesBefore = postings.queries.size

        viewModel.refreshBoards()

        assertEquals(
            listOf(9L),
            viewModel.state.value.boards
                .map { it.id },
        )
        assertEquals(queriesBefore, postings.queries.size)
    }

    @Test
    fun `프로필이 비어 있고 점수 없는 항목이 있으면 안내를 켠다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1), posting(id = 2, score = 80)))

        val state = viewModel(postingRepository = repository, profileRepository = emptyProfileRepository()).state.value

        assertTrue(state.isProfileNoticeVisible)
    }

    @Test
    fun `서버가 점수를 다 줬으면 프로필이 비어도 안내하지 않는다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1, score = 88), posting(id = 2, score = 80)))

        val state = viewModel(postingRepository = repository, profileRepository = emptyProfileRepository()).state.value

        assertFalse(state.isProfileNoticeVisible)
    }

    @Test
    fun `프로필을 아직 못 받았으면 미입력이라고 단정하지 않는다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))

        val state = viewModel(postingRepository = repository, profileRepository = FakeUserProfileRepository()).state.value

        assertNull(state.profile)
        assertFalse(state.isProfileNoticeVisible)
    }

    @Test
    fun `프로필이 채워져 있으면 점수 없는 항목이 있어도 안내하지 않는다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))

        val state = viewModel(postingRepository = repository).state.value

        assertFalse(state.isProfileNoticeVisible)
    }

    @Test
    fun `프로필이 늦게 도착하면 안내도 그때 켜진다`() {
        val repository = FakePostingRepository(initial = listOf(posting(id = 1)))
        val profileRepository = FakeUserProfileRepository()
        val viewModel = viewModel(postingRepository = repository, profileRepository = profileRepository)

        assertFalse(viewModel.state.value.isProfileNoticeVisible)

        profileRepository.profileState.value = profile(jobInterests = emptyList(), tags = emptyList())

        assertTrue(viewModel.state.value.isProfileNoticeVisible)
    }

    @Test
    fun `오늘 개수·게시판 조회 실패는 피드를 막지 않고 기록만 남긴다`() {
        val boardRepository = FakeBoardRepository.strict().apply { onGetBoards = { Result.failure(RuntimeException("boom")) } }

        val state = viewModel(boardRepository = boardRepository).state.value

        assertEquals(FeedLoadState.Loaded, state.loadState)
        assertTrue(state.boards.isEmpty())
        assertTrue(reporter.stages.contains("filter_boards"))
    }

    // ── 오프라인 스냅샷 (#86) ──────────────────────────────────────────────────

    @Test
    fun `기본 조건의 첫 페이지 성공은 스냅샷으로 저장한다`() {
        val snapshots = FakeFeedSnapshotRepository()

        viewModel(snapshotRepository = snapshots)

        assertEquals(1, snapshots.saved.size)
        assertEquals(
            listOf(1L, 2L, 3L),
            snapshots.saved
                .single()
                .postings
                .map(Posting::id),
        )
        assertEquals(FIXED_CLOCK.instant(), snapshots.saved.single().savedAt)
    }

    @Test
    fun `조건이 걸린 조회는 스냅샷으로 저장하지 않는다`() {
        // 필터된 부분집합을 「전체」로 저장하면 오프라인에서 빠진 공고를 모른 채 읽게 된다.
        val snapshots = FakeFeedSnapshotRepository()
        val viewModel = viewModel(snapshotRepository = snapshots)
        snapshots.saved.clear()

        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))

        assertTrue(snapshots.saved.isEmpty())
    }

    @Test
    fun `빈 결과는 스냅샷으로 저장하지 않는다`() {
        val snapshots = FakeFeedSnapshotRepository()

        viewModel(postingRepository = FakePostingRepository(), snapshotRepository = snapshots)

        assertTrue(snapshots.saved.isEmpty())
    }

    @Test
    fun `네트워크 단절로 실패하면 저장된 스냅샷을 오프라인 제안으로 싣는다`() {
        val snapshots = FakeFeedSnapshotRepository(initial = snapshot(7L, 8L))

        val state = viewModel(postingRepository = offlinePostings(), snapshotRepository = snapshots).state.value

        assertEquals(FeedLoadState.Failed(FeedFailureReason.NetworkUnavailable), state.loadState)
        assertEquals(listOf(7L, 8L), state.offlineSnapshot?.postings?.map(Posting::id))
        assertFalse(state.isOffline)
    }

    @Test
    fun `스냅샷이 없으면 오프라인 제안도 없다`() {
        val state = viewModel(postingRepository = offlinePostings()).state.value

        assertNull(state.offlineSnapshot)
    }

    @Test
    fun `오프라인 모드로 보기는 스냅샷을 목록으로 걸고 저장 시각을 남긴다`() {
        val viewModel =
            viewModel(postingRepository = offlinePostings(), snapshotRepository = FakeFeedSnapshotRepository(initial = snapshot(7L, 8L)))

        viewModel.showOfflineSnapshot()

        val state = viewModel.state.value
        assertTrue(state.isOffline)
        assertEquals(FeedLoadState.Loaded, state.loadState)
        assertEquals(listOf(7L, 8L), state.postings.map(Posting::id))
        assertEquals(FIXED_CLOCK.instant(), state.offlineSavedAt)
        assertNull(state.nextCursor)
    }

    @Test
    fun `오프라인 목록에도 마감일 범위가 그대로 적용된다`() {
        // 스냅샷은 기본 조회의 사본이라 범위가 반영돼 있지 않다 — 걸어 둔 조건 밖 공고가 새어 나오면 안 된다.
        val snapshots =
            FakeFeedSnapshotRepository(
                initial =
                    FeedSnapshot(
                        postings =
                            listOf(
                                posting(id = 7, dueDate = TODAY.plusDays(10)),
                                posting(id = 8, dueDate = TODAY.plusDays(40)),
                                posting(id = 9, dueDate = null),
                            ),
                        savedAt = FIXED_CLOCK.instant(),
                    ),
            )
        val viewModel = viewModel(postingRepository = offlinePostings(), snapshotRepository = snapshots)

        viewModel.onEvent(FeedUiEvent.FilterRequested)
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineSelected(UiDeadlineFilter.Range))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.Start))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(5)))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeEndpointClicked(FeedDeadlineRangeEndpoint.End))
        viewModel.onFilterEvent(FeedFilterEvent.DeadlineRangeDateSelected(TODAY.plusDays(20)))
        viewModel.onFilterEvent(FeedFilterEvent.ApplyClicked)
        viewModel.showOfflineSnapshot()

        val state = viewModel.state.value
        assertTrue(state.isOffline)
        assertEquals(listOf(7L), state.postings.map(Posting::id))
    }

    @Test
    fun `오프라인 모드에서는 더 불러오기가 돌지 않고 북마크는 안내만 한다`() {
        val postings = offlinePostings()
        val viewModel = viewModel(postingRepository = postings, snapshotRepository = FakeFeedSnapshotRepository(initial = snapshot(7L)))
        viewModel.showOfflineSnapshot()
        val queriesBefore = postings.queries.size

        viewModel.onLoadMore()
        viewModel.onEvent(FeedUiEvent.BookmarkToggled("7"))

        assertEquals(queriesBefore, postings.queries.size)
        assertEquals(FeedMessage.OfflineReadOnly, viewModel.state.value.message)
        assertTrue(postings.bookmarkCalls.isEmpty())
    }

    @Test
    fun `오프라인에서 조건 변경 시 로딩 중 배너 없음`() =
        runTest(mainDispatcherRule.dispatcher) {
            val postings = offlinePostings()
            val viewModel =
                viewModel(
                    postingRepository = postings,
                    snapshotRepository = FakeFeedSnapshotRepository(initial = snapshot(7L, 8L)),
                )
            viewModel.showOfflineSnapshot()
            assertTrue(viewModel.state.value.isOffline)

            // 조건을 바꾸면 재조회가 돈다. 그 조회가 끝나기 전 자리를 열어 둔다.
            val gate = CompletableDeferred<Unit>()
            postings.onGetPostings = {
                gate.await()
                Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException()))
            }
            viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))

            // 스냅샷 목록은 이미 비워졌다. 배너 문구의 유일한 근거인 offlineSavedAt 이 남아 있으면
            // 로딩 화면 위에 저장본 배너가 그대로 뜬다(FeedStateMapping.toFeedUiState).
            val loading = viewModel.state.value
            assertEquals(FeedLoadState.Loading, loading.loadState)
            assertTrue(loading.postings.isEmpty())
            assertFalse(loading.isOffline)
            assertNull(loading.offlineSavedAt)
            // 다시 열어 줄 스냅샷은 버리지 않는다.
            assertNotNull(loading.offlineSnapshot)

            gate.complete(Unit)

            // 재조회가 실패해도 배너는 돌아오지 않는다. 「다시 시도」를 눌러도 마찬가지다.
            val failed = viewModel.state.value
            assertEquals(FeedLoadState.Failed(FeedFailureReason.NetworkUnavailable), failed.loadState)
            assertFalse(failed.isOffline)
            assertNull(failed.offlineSavedAt)
            assertNotNull(failed.offlineSnapshot)
        }

    @Test
    fun `새로고침이 성공하면 온라인 목록으로 돌아온다`() {
        val postings = offlinePostings()
        val viewModel = viewModel(postingRepository = postings, snapshotRepository = FakeFeedSnapshotRepository(initial = snapshot(7L)))
        viewModel.showOfflineSnapshot()
        assertTrue(viewModel.state.value.isOffline)

        postings.onGetPostings = null
        postings.postings.addAll(List(2) { posting(id = it + 1L) })
        viewModel.refresh()

        val state = viewModel.state.value
        assertFalse(state.isOffline)
        assertNull(state.offlineSavedAt)
        assertNull(state.offlineSnapshot)
        assertEquals(listOf(1L, 2L), state.postings.map(Posting::id))
    }

    @Test
    fun `스냅샷 저장이 실패해도 조회 결과는 그대로 두고 기록만 남긴다`() {
        val snapshots =
            FakeFeedSnapshotRepository().apply {
                onSave = { Result.failure(IllegalStateException("disk full")) }
            }

        val viewModel = viewModel(snapshotRepository = snapshots)

        assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
        assertEquals(
            listOf(1L, 2L, 3L),
            viewModel.state.value.postings
                .map(Posting::id),
        )
        assertTrue(reporter.stages.contains("feed_snapshot_save"))
    }

    @Test
    fun `조건 때문에 실패하면 그 조건을 지우고 다시 보는 길을 연다`() {
        val viewModel = viewModel(postingRepository = scoreConditionFails())

        viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.ScoreDesc))

        val failed = viewModel.state.value
        assertEquals(FeedLoadState.Failed(FeedFailureReason.Maintenance), failed.loadState)
        assertTrue(failed.canResetFailedQuery)
    }

    @Test
    fun `조건 초기화는 검색어·필터·정렬을 함께 지우고 그 자리에서 다시 조회한다`() =
        runTest {
            // 되돌리기만 하고 멈추면 반쪽이다 — 실패 화면에는 목록이 없어 조건이 바뀐 것을 확인할 길이
            // 없고, 남은 버튼(다시 시도)은 지금 조건을 그대로 다시 보낸다.
            val viewModel = viewModel(postingRepository = scoreConditionFails())
            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("백엔드"))
            advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS + 1)
            viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))
            viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.ScoreDesc))
            assertEquals(FeedLoadState.Failed(FeedFailureReason.Maintenance), viewModel.state.value.loadState)

            viewModel.resetQueryAndRetry()

            val state = viewModel.state.value
            assertTrue(state.query.isDefault)
            assertEquals(PostingSort.CollectedDesc, state.query.sort)
            assertEquals("", state.searchInput)
            assertFalse(state.canResetFailedQuery)
            // 사용자가 한 번 더 누르지 않아도 목록이 돌아와 있다.
            assertEquals(FeedLoadState.Loaded, state.loadState)
            assertEquals(listOf(1L, 2L, 3L), state.postings.map(Posting::id))
        }

    @Test
    fun `조건 초기화는 아직 반영 전인 검색어까지 되살리지 않는다`() =
        runTest {
            // 디바운스를 끊지 않으면 300ms 뒤 applyQuery 가 방금 지운 검색어를 다시 실어, 초기화한
            // 조회가 조용히 옛 조건으로 되돌아간다.
            val viewModel = viewModel(postingRepository = scoreConditionFails())
            viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.ScoreDesc))
            viewModel.onEvent(FeedUiEvent.SearchQueryChanged("백엔드"))

            viewModel.resetQueryAndRetry()
            advanceTimeBy(FeedViewModel.SEARCH_DEBOUNCE_MS + 1)

            assertEquals("", viewModel.state.value.searchInput)
            assertEquals("", viewModel.state.value.query.searchQuery)
            assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
        }

    @Test
    fun `원인을 모르는 실패도 조건이 걸려 있으면 초기화 길을 연다`() {
        // Generic 은 「조건 탓이 아니다」가 아니라 「모른다」다 — 되돌릴 조건이 실제로 있으면,
        // 헛다리를 짚어도 잃는 것이 없고 닫아 두면 빠져나갈 길 없는 화면이 남는다.
        val viewModel = viewModel(postingRepository = scoreConditionFails(failure = { IllegalStateException("boom") }))

        viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.ScoreDesc))

        val state = viewModel.state.value
        assertEquals(FeedLoadState.Failed(FeedFailureReason.Generic), state.loadState)
        assertTrue(state.canResetFailedQuery)
    }

    @Test
    fun `네트워크 단절에는 조건이 걸려 있어도 초기화를 내밀지 않는다`() {
        // 요청이 서버에 닿지도 못했으니 조건이 답을 바꿀 여지가 없다 — 지워도 같은 실패로 돌아온다.
        val viewModel = viewModel(postingRepository = offlinePostings())

        viewModel.onEvent(FeedUiEvent.FilterSelected(FeedListingCategory.Employment))

        val state = viewModel.state.value
        assertEquals(FeedLoadState.Failed(FeedFailureReason.NetworkUnavailable), state.loadState)
        assertFalse(state.query.isDefault)
        assertFalse(state.canResetFailedQuery)
    }

    @Test
    fun `기본 조회가 실패하면 되돌릴 조건이 없어 초기화를 내밀지 않는다`() {
        val state = viewModel(postingRepository = maintenancePostings()).state.value

        assertEquals(FeedLoadState.Failed(FeedFailureReason.Maintenance), state.loadState)
        assertFalse(state.canResetFailedQuery)
    }

    @Test
    fun `조회에 성공한 화면에는 조건이 걸려 있어도 초기화를 내밀지 않는다`() {
        // 실패 화면에서만 여는 길이다 — 성공한 목록 위에서는 헤더의 검색칸·필터·정렬이 그대로 살아 있다.
        val viewModel = viewModel()

        viewModel.onSortEvent(FeedSortMenuEvent.SortSelected(FeedSortOption.ScoreDesc))

        assertEquals(FeedLoadState.Loaded, viewModel.state.value.loadState)
        assertFalse(viewModel.state.value.canResetFailedQuery)
    }
}

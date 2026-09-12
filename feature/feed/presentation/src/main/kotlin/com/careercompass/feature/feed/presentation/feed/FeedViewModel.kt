package com.careercompass.feature.feed.presentation.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.board.Board
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.feature.feed.domain.model.FeedPage
import com.careercompass.feature.feed.domain.model.FeedQuery
import com.careercompass.feature.feed.domain.model.FeedSnapshot
import com.careercompass.feature.feed.domain.repository.FeedSnapshotRepository
import com.careercompass.feature.feed.domain.usecase.CountTodayNewPostingsUseCase
import com.careercompass.feature.feed.domain.usecase.GetBoardsUseCase
import com.careercompass.feature.feed.domain.usecase.GetFeedPageUseCase
import com.careercompass.feature.feed.domain.usecase.TogglePostingBookmarkUseCase
import com.careercompass.feature.feed.presentation.FeedLoadMoreState
import com.careercompass.feature.feed.presentation.FeedUiEvent
import com.careercompass.feature.feed.presentation.feedfilter.FeedFilterEvent
import com.careercompass.feature.feed.presentation.feedfilter.FeedSortMenuEvent
import com.careercompass.feature.feed.presentation.reporting.FeedFailureStage
import com.careercompass.feature.feed.presentation.reporting.recordFeedFailure
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.toFeedFailureReason
import com.careercompass.feature.feed.presentation.shared.util.toMinScore
import com.careercompass.feature.feed.presentation.shared.util.toPostingSort
import com.careercompass.feature.feed.presentation.shared.util.toPostingTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** 화면·필터 시트·정렬 메뉴가 [FeedViewModel] 에 보내는 것. */
public sealed interface FeedIntent : MviIntent {
    public data class Screen(
        val event: FeedUiEvent,
    ) : FeedIntent

    public data class Filter(
        val event: FeedFilterEvent,
    ) : FeedIntent

    public data class Sort(
        val event: FeedSortMenuEvent,
    ) : FeedIntent

    /** 이어 읽기 — 목록 끝에 닿았거나(자동) 「더 찾아보기」·「다시 시도」를 눌렀다(수동). */
    public data object LoadMore : FeedIntent

    /** 당겨서 새로고침 — 목록은 유지한 채 첫 페이지와 오늘 신규 개수를 다시 받는다. */
    public data object Refresh : FeedIntent

    /** 오류 화면의 「다시 시도」 — **지금 조건 그대로** 처음부터 다시 읽는다. */
    public data object Retry : FeedIntent

    /** 오류 화면의 「조건 지우고 다시 보기」. */
    public data object ResetQueryAndRetry : FeedIntent

    /** 화면에 돌아올 때마다 게시판 목록만 다시 읽는다. */
    public data object RefreshBoards : FeedIntent

    /** 네트워크 오류 화면의 「오프라인 모드로 보기」. */
    public data object ShowOfflineSnapshot : FeedIntent

    public data object RequestBoardList : FeedIntent

    public data object RequestBoardRegister : FeedIntent

    public data object ConsumeNavigation : FeedIntent

    public data object ConsumeMessage : FeedIntent

    public data object ConsumeSessionEnded : FeedIntent
}

/** 상태가 겪은 것. [FeedViewModel] 만 만든다. */
public sealed interface FeedReducerEvent : ReducerEvent {
    public data class ProfileChanged(
        val profile: UserProfile?,
    ) : FeedReducerEvent

    public data class BoardsLoaded(
        val boards: List<Board>,
    ) : FeedReducerEvent

    public data class TodayCountLoaded(
        val count: Int,
    ) : FeedReducerEvent

    public data class SearchInputChanged(
        val input: String,
    ) : FeedReducerEvent

    public data class QueryChanged(
        val query: FeedQuery,
    ) : FeedReducerEvent

    /** 검색어·조건·정렬을 기본으로 되돌린다. */
    public data object QueryReset : FeedReducerEvent

    /** null 이면 시트를 닫는다. */
    public data class FilterDraftChanged(
        val draft: FeedFilterDraft?,
    ) : FeedReducerEvent

    public data class SortMenuVisibilityChanged(
        val isVisible: Boolean,
    ) : FeedReducerEvent

    public data class NavigationRequested(
        val destination: FeedDestination,
    ) : FeedReducerEvent

    public data class MessageRaised(
        val message: FeedMessage,
    ) : FeedReducerEvent

    /** 처음부터 다시 읽는다 — 목록을 비우고 로딩으로 간다. */
    public data object LoadStarted : FeedReducerEvent

    public data object RefreshStarted : FeedReducerEvent

    public data object LoadMoreStarted : FeedReducerEvent

    /** 첫 페이지(또는 새로고침)가 왔다 — 목록을 갈아 끼우고 온라인으로 돌아온다. */
    public data class PageLoaded(
        val postings: List<Posting>,
        val nextCursor: String?,
        val loadMore: FeedLoadMoreState,
    ) : FeedReducerEvent

    /** 이어 읽은 페이지가 왔다 — 목록에 덧붙인다. */
    public data class MoreLoaded(
        val postings: List<Posting>,
        val nextCursor: String?,
        val loadMore: FeedLoadMoreState,
    ) : FeedReducerEvent

    public data class LoadFailed(
        val reason: FeedFailureReason,
    ) : FeedReducerEvent

    /** 새로고침이 실패했지만 보여 줄 목록이 있다 — 목록은 두고 안내만 낸다. */
    public data object RefreshFailed : FeedReducerEvent

    public data object LoadMoreFailed : FeedReducerEvent

    public data class PostingsReplaced(
        val postings: List<Posting>,
    ) : FeedReducerEvent

    /** null 이면 저장된 스냅샷이 없다 — 「오프라인 모드로 보기」가 열리지 않는다. */
    public data class OfflineSnapshotLoaded(
        val snapshot: FeedSnapshot?,
    ) : FeedReducerEvent

    /** 스냅샷을 목록으로 건다 — 더 불러오기는 잠기고 배너에 저장 시각이 뜬다. */
    public data class OfflineSnapshotShown(
        val postings: List<Posting>,
        val savedAt: Instant,
    ) : FeedReducerEvent

    public data object SessionEnded : FeedReducerEvent

    public data object NavigationConsumed : FeedReducerEvent

    public data object MessageConsumed : FeedReducerEvent

    public data object SessionEndedConsumed : FeedReducerEvent
}

/**
 * 피드 홈 — 조회 조건·페이징·북마크를 다루고, 표시 문구는 만들지 않는다. 진입점은 [onIntent] 하나, 전이는
 * [reduce] 한 곳이다(#246).
 *
 * - 검색어는 입력 즉시 상태에 반영하고 [SEARCH_DEBOUNCE_MS] 뒤에 재조회한다.
 * - 페이지가 비어도 `nextCursor` 가 남아 있으면 끝이 아니다([FeedPage]) — 항목이 나올 때까지 몇 페이지를 이어 읽는다.
 *   한 번에 [MAX_EMPTY_PAGE_FOLLOW_UPS] 페이지까지만 따라가고, 그러고도 목록이 늘지 않았으면
 *   [FeedLoadMoreState.Paused] 로 서서 「더 찾아보기」를 사용자에게 넘긴다([loadMoreStateAfter]).
 * - 북마크는 먼저 뒤집고 실패하면 되돌린다.
 * - 프로필 캐시는 인사말뿐 아니라 적합도 표시 판정에도 쓴다([FeedViewState.isProfileNoticeVisible]).
 * - `401` 은 [FeedViewState.sessionEnded] 로 올리고, 네트워크 단절·서버 점검은 [FeedFailureReason] 으로 구분한다.
 * - 검색어·필터·정렬은 [FeedInputDraft] 가 [SavedStateHandle] 에 남긴다 — 프로세스가 죽어도 조회 조건이 남고,
 *   그 조건으로 목록을 **다시 조회한다**(#137). 목록·페이징을 되살리지 않는 이유는 그 클래스의 KDoc 에 있다.
 */
@HiltViewModel
public class FeedViewModel
    @Inject
    constructor(
        private val getFeedPage: GetFeedPageUseCase,
        private val countTodayNewPostings: CountTodayNewPostingsUseCase,
        private val togglePostingBookmark: TogglePostingBookmarkUseCase,
        private val getBoards: GetBoardsUseCase,
        private val userProfileRepository: UserProfileRepository,
        private val feedSnapshotRepository: FeedSnapshotRepository,
        private val errorReporter: ErrorReporter,
        /** Screen 이 D-day·신규 판정에 같은 시계를 쓴다. */
        public val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : MviViewModel<FeedIntent, FeedViewState, FeedReducerEvent>(
            // 복원한 검색어·조건이 시작값이다. 목록은 비어 있고, 아래 init 의 load() 가 그 조건으로 첫 페이지를 읽는다.
            FeedInputDraft(savedStateHandle).restoredState(),
        ) {
        private val draft = FeedInputDraft(savedStateHandle)

        /**
         * 죽기 전 열려 있던 필터 시트의 편집값 — 시트를 **다시 열 때** 한 번 쓰고 버린다.
         *
         * 시작할 때 읽어 두는 이유: 시트가 열려 있었는지는 복원하지 않으므로, 저장소에 남은 초안은 아래 구독이
         * 「시트가 닫혀 있다」를 보는 순간 지워진다. 조건이 다시 적용되면([applyQuery]) 이 값도 버린다 —
         * 칩·정렬로 바꾼 조건과 어긋난 초안으로 시트가 열리지 않게.
         */
        private var restoredFilterDraft: FeedFilterDraft? = draft.restoredFilterDraft()

        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null
        private var searchJob: Job? = null
        private var boardsJob: Job? = null

        init {
            // 조건을 바꾸는 자리가 여럿(검색·칩·시트·정렬)이라 상태 흐름 한 곳에서 남긴다. 목록이 흔들릴
            // 때마다 다시 쓰지 않도록 저장 대상만 뽑아 비교한다.
            viewModelScope.launch {
                uiState
                    .map { FeedInputDraft.Input(searchInput = it.searchInput, query = it.query, filterDraft = it.filterDraft) }
                    .distinctUntilChanged()
                    .collect(draft::save)
            }
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile -> dispatch(FeedReducerEvent.ProfileChanged(profile)) }
            }
            loadBoards()
            loadTodayCount()
            load()
        }

        override fun onIntent(intent: FeedIntent) {
            when (intent) {
                is FeedIntent.Screen -> onEvent(intent.event)
                is FeedIntent.Filter -> onFilterEvent(intent.event)
                is FeedIntent.Sort -> onSortEvent(intent.event)
                FeedIntent.LoadMore -> loadMore()
                FeedIntent.Refresh -> refresh()
                FeedIntent.Retry -> load()
                FeedIntent.ResetQueryAndRetry -> resetQueryAndRetry()
                FeedIntent.RefreshBoards -> refreshBoards()
                FeedIntent.ShowOfflineSnapshot -> showOfflineSnapshot()
                FeedIntent.RequestBoardList -> dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.BoardList))
                FeedIntent.RequestBoardRegister -> dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.BoardRegister))
                FeedIntent.ConsumeNavigation -> dispatch(FeedReducerEvent.NavigationConsumed)
                FeedIntent.ConsumeMessage -> dispatch(FeedReducerEvent.MessageConsumed)
                FeedIntent.ConsumeSessionEnded -> dispatch(FeedReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: FeedViewState,
            event: FeedReducerEvent,
        ): FeedViewState =
            when (event) {
                is FeedReducerEvent.ProfileChanged -> {
                    state.copy(profile = event.profile)
                }

                is FeedReducerEvent.BoardsLoaded -> {
                    state.copy(boards = event.boards, boardsLoaded = true)
                }

                is FeedReducerEvent.TodayCountLoaded -> {
                    state.copy(todayNewCount = event.count)
                }

                is FeedReducerEvent.SearchInputChanged -> {
                    state.copy(searchInput = event.input)
                }

                is FeedReducerEvent.QueryChanged -> {
                    state.copy(query = event.query)
                }

                FeedReducerEvent.QueryReset -> {
                    state.copy(searchInput = "", query = FeedQuery(), filterDraft = null, isSortMenuVisible = false)
                }

                is FeedReducerEvent.FilterDraftChanged -> {
                    state.copy(filterDraft = event.draft)
                }

                is FeedReducerEvent.SortMenuVisibilityChanged -> {
                    state.copy(isSortMenuVisible = event.isVisible)
                }

                is FeedReducerEvent.NavigationRequested -> {
                    state.copy(pendingNavigation = event.destination)
                }

                is FeedReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                FeedReducerEvent.LoadStarted -> {
                    state.copy(
                        loadState = FeedLoadState.Loading,
                        postings = emptyList(),
                        nextCursor = null,
                        isRefreshing = false,
                        loadMore = FeedLoadMoreState.Ready,
                    )
                }

                FeedReducerEvent.RefreshStarted -> {
                    state.copy(isRefreshing = true, loadMore = FeedLoadMoreState.Ready)
                }

                FeedReducerEvent.LoadMoreStarted -> {
                    state.copy(loadMore = FeedLoadMoreState.Loading)
                }

                is FeedReducerEvent.PageLoaded -> {
                    state
                        .copy(
                            postings = event.postings,
                            nextCursor = event.nextCursor,
                            loadState = FeedLoadState.Loaded,
                            isRefreshing = false,
                            loadMore = event.loadMore,
                        ).online()
                }

                is FeedReducerEvent.MoreLoaded -> {
                    state.copy(postings = event.postings, nextCursor = event.nextCursor, loadMore = event.loadMore)
                }

                is FeedReducerEvent.LoadFailed -> {
                    state.copy(loadState = FeedLoadState.Failed(event.reason), isRefreshing = false)
                }

                FeedReducerEvent.RefreshFailed -> {
                    state.copy(isRefreshing = false, message = FeedMessage.RefreshFailed)
                }

                FeedReducerEvent.LoadMoreFailed -> {
                    state.copy(loadMore = FeedLoadMoreState.Failed, message = FeedMessage.LoadMoreFailed)
                }

                is FeedReducerEvent.PostingsReplaced -> {
                    state.copy(postings = event.postings)
                }

                is FeedReducerEvent.OfflineSnapshotLoaded -> {
                    state.copy(offlineSnapshot = event.snapshot)
                }

                is FeedReducerEvent.OfflineSnapshotShown -> {
                    state.copy(
                        postings = event.postings,
                        nextCursor = null,
                        loadState = FeedLoadState.Loaded,
                        isRefreshing = false,
                        loadMore = FeedLoadMoreState.Ready,
                        isOffline = true,
                        offlineSavedAt = event.savedAt,
                    )
                }

                FeedReducerEvent.SessionEnded -> {
                    state.copy(sessionEnded = true)
                }

                FeedReducerEvent.NavigationConsumed -> {
                    state.copy(pendingNavigation = null)
                }

                FeedReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                FeedReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }

        private fun onEvent(event: FeedUiEvent) {
            when (event) {
                is FeedUiEvent.SearchQueryChanged -> {
                    onSearchInput(event.query)
                }

                is FeedUiEvent.FilterSelected -> {
                    applyQuery(currentState.query.copy(types = event.category.toPostingTypes()))
                }

                FeedUiEvent.FilterRequested -> {
                    // 프로세스가 죽어 닫힌 시트는 저절로 열리지 않는다 — 대신 다시 열면 고르던 값이 그대로 있다(#137).
                    val restored = restoredFilterDraft
                    restoredFilterDraft = null
                    dispatch(FeedReducerEvent.FilterDraftChanged(restored ?: FeedFilterDraft.from(currentState.query)))
                }

                FeedUiEvent.FilterResetSelected -> {
                    // 시트의 「초기화」와 같은 기본값을 쓴다 — 빈 목록에서 푸는 조건과 시트에서 푸는
                    // 조건이 갈리면, 초기화했는데도 목록이 그대로인 경우가 생긴다.
                    applyQuery(FeedFilterDraft.Default.applyTo(currentState.query) ?: return)
                }

                FeedUiEvent.BoardRegisterSelected -> {
                    dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.BoardRegister))
                }

                FeedUiEvent.MissingBoardsCleared -> {
                    // 시트의 같은 손짓(FeedFilterEvent.MissingBoardsCleared)과 **한 규칙**을 쓴다
                    // (`missingFrom`) — 목록에 없는 id 만 턴다. 다른 것은 시트가 열려 있지 않아 초안이
                    // 아니라 조회 조건을 바로 고치고 다시 읽는다는 것뿐이다.
                    //
                    // 시트와 달리 **확인된 것만** 뺀다([FeedViewState.hasDeletedBoardFilter]) — 시트의 태그는
                    // 「확인 못 한 게시판」도 사용자가 보고 누르는 것이지만, 여기 버튼은 「지워졌어요」라고
                    // 말한 화면에만 있다. 목록을 못 받은 채로 빼면 화면이 하지 않은 말을 근거로 조건을 지운다.
                    val current = currentState
                    if (!current.hasDeletedBoardFilter) return
                    applyQuery(current.query.copy(boardIds = current.query.boardIds - current.missingBoardIds))
                }

                FeedUiEvent.LoadMoreSelected -> {
                    loadMore()
                }

                FeedUiEvent.SortMenuRequested -> {
                    dispatch(FeedReducerEvent.SortMenuVisibilityChanged(true))
                }

                is FeedUiEvent.ListingSelected -> {
                    val postingId = event.listingId.toLongOrNull() ?: return
                    dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.PostingDetail(postingId)))
                }

                is FeedUiEvent.BookmarkToggled -> {
                    toggleBookmark(event.listingId.toLongOrNull() ?: return)
                }

                FeedUiEvent.NotificationsSelected -> {
                    dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.Notifications))
                }

                FeedUiEvent.CompleteProfileSelected -> {
                    dispatch(FeedReducerEvent.NavigationRequested(FeedDestination.Profile))
                }
            }
        }

        private fun onFilterEvent(event: FeedFilterEvent) {
            when (event) {
                is FeedFilterEvent.CategorySelected -> {
                    updateDraft { it.copy(category = event.category) }
                }

                is FeedFilterEvent.BoardToggled -> {
                    val boardId = event.boardId.toLongOrNull() ?: return
                    updateDraft { draft ->
                        draft.copy(boardIds = if (boardId in draft.boardIds) draft.boardIds - boardId else draft.boardIds + boardId)
                    }
                }

                FeedFilterEvent.MissingBoardsCleared -> {
                    // 목록에 없는 id 만 턴다. 사용자의 손짓으로만 지우는 이유 — 목록을 못 받았을 수도 있어
                    // (#155) 앱이 알아서 버리면 잠깐 끊긴 사이 사용자의 조건이 사라진다.
                    val boards = currentState.boards
                    updateDraft { draft -> draft.copy(boardIds = draft.boardIds - draft.boardIds.missingFrom(boards)) }
                }

                is FeedFilterEvent.DeadlineSelected -> {
                    updateDraft { it.copy(deadline = event.deadline) }
                }

                is FeedFilterEvent.DeadlineRangeEndpointClicked -> {
                    updateDraft { it.copy(deadlineRange = it.deadlineRange.copy(editing = event.endpoint)) }
                }

                is FeedFilterEvent.DeadlineRangeDateSelected -> {
                    updateDraft { draft ->
                        val endpoint = draft.deadlineRange.editing ?: return@updateDraft draft
                        draft.copy(deadlineRange = draft.deadlineRange.withDate(endpoint, event.date))
                    }
                }

                FeedFilterEvent.DeadlineRangePickerDismissed -> {
                    updateDraft { it.copy(deadlineRange = it.deadlineRange.copy(editing = null)) }
                }

                is FeedFilterEvent.MinScoreSelected -> {
                    updateDraft { it.copy(minScore = event.minScore.toMinScore()) }
                }

                FeedFilterEvent.UnreadOnlyToggled -> {
                    updateDraft { it.copy(unreadOnly = !it.unreadOnly) }
                }

                FeedFilterEvent.ResetClicked -> {
                    updateDraft { FeedFilterDraft.Default }
                }

                FeedFilterEvent.ApplyClicked -> {
                    // 잘못된 범위는 시트를 닫지 않는다 — 버튼이 이미 잠겨 있지만, 계약을 여기서도 지켜
                    // 도메인이 만들 수 없는 값을 조회 조건에 넣지 않는다.
                    val applied = currentState.filterDraft?.applyTo(currentState.query) ?: return
                    dispatch(FeedReducerEvent.FilterDraftChanged(null))
                    applyQuery(applied)
                }

                FeedFilterEvent.DismissClicked -> {
                    dispatch(FeedReducerEvent.FilterDraftChanged(null))
                }
            }
        }

        private fun onSortEvent(event: FeedSortMenuEvent) {
            when (event) {
                is FeedSortMenuEvent.SortSelected -> {
                    dispatch(FeedReducerEvent.SortMenuVisibilityChanged(false))
                    applyQuery(currentState.query.copy(sort = event.option.toPostingSort()))
                }

                FeedSortMenuEvent.DismissClicked -> {
                    dispatch(FeedReducerEvent.SortMenuVisibilityChanged(false))
                }
            }
        }

        /**
         * 이어 읽기 — 목록 끝에 닿았거나(자동) 「더 찾아보기」·「다시 시도」를 눌렀다(수동, [FeedUiEvent.LoadMoreSelected]).
         *
         * 다음 커서가 없거나 이미 읽는 중이면 무시한다. [FeedLoadMoreState.Paused]·[FeedLoadMoreState.Failed]
         * 는 **막지 않는다** — 그 둘은 자동 페이징만 서 있는 자리이고, 여기까지 오는 길은 사용자가 누른
         * 버튼뿐이기 때문이다(자동 트리거는 화면에서 [FeedLoadMoreState.Ready] 일 때만 무장한다).
         */
        private fun loadMore() {
            val current = currentState
            if (current.isOffline) return
            val cursor = current.nextCursor ?: return
            if (current.isLoadingMore || current.isRefreshing || current.loadState != FeedLoadState.Loaded) return
            val query = current.query
            dispatch(FeedReducerEvent.LoadMoreStarted)
            loadMoreJob =
                viewModelScope.launch {
                    fetchUntilNonEmpty(query, cursor)
                        .onSuccess { page ->
                            val before = currentState.postings
                            val merged = (before + page.postings).distinctBy(Posting::id)
                            dispatch(
                                FeedReducerEvent.MoreLoaded(
                                    postings = merged,
                                    nextCursor = page.nextCursor,
                                    loadMore = page.loadMoreStateAfter(gained = merged.size > before.size),
                                ),
                            )
                        }.onFailure { throwable ->
                            recordFailure(FeedFailureStage.FeedLoadMore, throwable)
                            dispatch(FeedReducerEvent.LoadMoreFailed)
                        }
                }
        }

        private fun refresh() {
            if (currentState.loadState == FeedLoadState.Loading) return
            loadJob?.cancel()
            loadMoreJob?.cancel()
            val query = currentState.query
            dispatch(FeedReducerEvent.RefreshStarted)
            loadTodayCount()
            loadJob =
                viewModelScope.launch {
                    fetchUntilNonEmpty(query, cursor = null)
                        .onSuccess { page ->
                            dispatch(
                                FeedReducerEvent.PageLoaded(
                                    postings = page.postings,
                                    nextCursor = page.nextCursor,
                                    loadMore = page.loadMoreStateAfter(gained = page.postings.isNotEmpty()),
                                ),
                            )
                            saveSnapshotIfDefault(query, page.postings)
                        }.onFailure { throwable ->
                            recordFailure(FeedFailureStage.FeedRefresh, throwable)
                            if (currentState.postings.isEmpty()) {
                                dispatch(FeedReducerEvent.LoadFailed(throwable.toFeedFailureReason()))
                            } else {
                                dispatch(FeedReducerEvent.RefreshFailed)
                            }
                        }
                }
        }

        /**
         * 오류 화면의 「조건 지우고 다시 보기」 — 조회 조건을 기본값으로 되돌리고 **그 자리에서 다시 읽는다**.
         *
         * 되돌리기와 재조회를 한 번에 하는 이유: 실패 화면에는 목록이 없어 조건이 바뀐 것을 확인할 방법이
         * 없고, 남은 버튼은 「다시 시도」뿐인데 그것은 지금 조건을 그대로 다시 보낸다. 조건만 지우고 멈추면
         * 화면은 여전히 옛 조건의 실패를 말한 채 사용자가 한 번 더 누르기를 기다리게 된다 — 반쪽이다.
         *
         * 빈 목록의 [FeedUiEvent.FilterResetSelected] 와 달리 **검색어·정렬까지** 지운다. 저기는 조회
         * 자체는 성공한 자리라 조건을 한 겹씩 벗겨 주면 되지만, 여기서는 무엇이 실패를 불렀는지 화면이
         * 모른다. 게다가 #144 의 재현은 정렬(「적합도순」)과 최소 적합도였으므로 정렬을 남겨 두면 같은
         * 실패로 돌아온다. 되돌아갈 곳은 한 곳뿐이다 — 성공한 적이 있는 기본 조회다.
         */
        private fun resetQueryAndRetry() {
            // 아직 반영 전인 검색어 디바운스를 먼저 끊는다. 살려 두면 [SEARCH_DEBOUNCE_MS] 뒤에
            // applyQuery 가 방금 지운 검색어를 다시 실어 조건이 되살아난다.
            searchJob?.cancel()
            restoredFilterDraft = null
            dispatch(FeedReducerEvent.QueryReset)
            load()
        }

        /**
         * 빈 피드가 「등록한 게시판이 없어요」라고 말한 뒤 사용자가 등록하고 돌아오는 길이 생겼다. 그때
         * 게시판을 다시 읽지 않으면 방금 등록한 사람에게 같은 안내가 그대로 남는다. 공고는 다시 읽지
         * 않는다 — 첫 수집이 끝나기 전이라 결과가 같고, 목록·스크롤을 흔들 이유가 없다.
         */
        private fun refreshBoards() {
            if (boardsJob?.isActive == true) return
            loadBoards()
        }

        /**
         * 스냅샷에는 다음 커서가 없으므로 이어 읽기는 잠기고, 북마크는 [FeedMessage.OfflineReadOnly] 로 막는다.
         * 검색·필터·정렬은 그대로 재조회를 부르고, 성공하면 [online] 으로 온라인 목록으로 돌아온다.
         *
         * 스냅샷은 기본 조회의 사본이라 지금 걸린 마감일·검색어가 반영돼 있지 않다 — 조회와 같은 규칙
         * ([FeedQuery.filterClientSide])을 여기서 한 번 더 적용해, 「마감일 범위」를 걸어 둔 채 오프라인으로
         * 넘어온 사람이 범위 밖 공고를 보지 않게 한다.
         */
        private fun showOfflineSnapshot() {
            val snapshot = currentState.offlineSnapshot ?: return
            loadJob?.cancel()
            loadMoreJob?.cancel()
            dispatch(
                FeedReducerEvent.OfflineSnapshotShown(
                    postings = currentState.query.filterClientSide(snapshot.postings, LocalDate.now(clock)),
                    savedAt = snapshot.savedAt,
                ),
            )
        }

        /**
         * 기본 조건의 첫 페이지만 스냅샷으로 남긴다 — 조건이 걸린 결과를 저장하면 오프라인에서 「전체」로 보이는
         * 목록이 사실은 부분집합이 된다. 빈 목록은 저장하지 않는다(스냅샷 없음과 구분할 이유가 없다).
         * 저장 실패는 기록만 남긴다 — 이번 조회는 이미 성공했고 사용자가 할 일이 없다.
         */
        private fun saveSnapshotIfDefault(
            query: FeedQuery,
            postings: List<Posting>,
        ) {
            if (!query.isDefault || postings.isEmpty()) return
            viewModelScope.launch {
                feedSnapshotRepository
                    .save(FeedSnapshot(postings = postings, savedAt = Instant.now(clock)))
                    .onFailure { recordFailure(FeedFailureStage.FeedSnapshotSave, it) }
            }
        }

        /** 서버에서 목록을 못 받았다 — 스냅샷이 있으면 「오프라인 모드로 보기」를 열어 준다. */
        private fun loadSnapshotForOfflineOffer() {
            viewModelScope.launch {
                feedSnapshotRepository
                    .load()
                    .onSuccess { snapshot -> dispatch(FeedReducerEvent.OfflineSnapshotLoaded(snapshot)) }
                    .onFailure { recordFailure(FeedFailureStage.FeedSnapshotLoad, it) }
            }
        }

        private fun onSearchInput(input: String) {
            dispatch(FeedReducerEvent.SearchInputChanged(input))
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    applyQuery(currentState.query.copy(searchQuery = input))
                }
        }

        private fun updateDraft(transform: (FeedFilterDraft) -> FeedFilterDraft) {
            val draft = currentState.filterDraft ?: return
            dispatch(FeedReducerEvent.FilterDraftChanged(transform(draft)))
        }

        private fun applyQuery(query: FeedQuery) {
            if (query == currentState.query) return
            restoredFilterDraft = null
            dispatch(FeedReducerEvent.QueryChanged(query))
            load()
        }

        private fun load() {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            val query = currentState.query
            dispatch(FeedReducerEvent.LoadStarted)
            loadJob =
                viewModelScope.launch {
                    fetchUntilNonEmpty(query, cursor = null)
                        .onSuccess { page ->
                            dispatch(
                                FeedReducerEvent.PageLoaded(
                                    postings = page.postings,
                                    nextCursor = page.nextCursor,
                                    loadMore = page.loadMoreStateAfter(gained = page.postings.isNotEmpty()),
                                ),
                            )
                            saveSnapshotIfDefault(query, page.postings)
                        }.onFailure { throwable ->
                            recordFailure(FeedFailureStage.FeedLoad, throwable)
                            val reason = throwable.toFeedFailureReason()
                            dispatch(FeedReducerEvent.LoadFailed(reason))
                            // 점검 중에도 스냅샷은 유효하다 — 서버가 살아나기를 기다리는 동안 마지막 목록을 열어 둔다.
                            if (reason !is FeedFailureReason.Generic) loadSnapshotForOfflineOffer()
                        }
                }
        }

        /**
         * 이번 조회 뒤 이어 읽기를 자동으로 굴릴지, 사용자에게 넘길지 정한다.
         *
         * 기준은 「이번에 목록이 늘었는가」 하나다. 커서가 남았는데 한 건도 늘지 않았다면
         * [fetchUntilNonEmpty] 가 상한([MAX_EMPTY_PAGE_FOLLOW_UPS])까지 따라가고도 빈손으로 온 것이므로
         * [FeedLoadMoreState.Paused] 로 선다. 여기서 자동으로 계속 따라가면 걸러질 페이지만 끝없이 받게
         * 되고(데이터·배터리), 반대로 조용히 멈추면 화면이 「끝」이라고 거짓말을 한다 — 그래서 멈추되
         * 멈췄다고 말하고 이어 갈 버튼을 준다.
         *
         * 「늘었는가」로 재는 이유는 **되풀이를 끝내기 위해서**다. 페이지가 겹쳐 와 중복만 실려 와도
         * (`distinctBy`) 목록은 늘지 않으므로 여기서 선다 — 자동 이어 읽기는 반드시 목록을 늘리거나 멈춘다.
         */
        private fun FeedPage.loadMoreStateAfter(gained: Boolean): FeedLoadMoreState =
            if (hasNext && !gained) FeedLoadMoreState.Paused else FeedLoadMoreState.Ready

        /**
         * 클라이언트 필터로 페이지가 통째로 비면 다음 커서를 따라 최대 [MAX_EMPTY_PAGE_FOLLOW_UPS]페이지까지 이어 읽는다.
         * 그래도 비면 받은 커서를 그대로 돌려줘 호출자가 끝으로 오판하지 않게 한다.
         */
        private suspend fun fetchUntilNonEmpty(
            query: FeedQuery,
            cursor: String?,
        ): Result<FeedPage> {
            val collected = mutableListOf<Posting>()
            var next = cursor
            repeat(MAX_EMPTY_PAGE_FOLLOW_UPS) {
                val page = getFeedPage(query, next).getOrElse { return Result.failure(it) }
                collected += page.postings
                next = page.nextCursor
                if (collected.isNotEmpty() || !page.hasNext) {
                    return Result.success(FeedPage(postings = collected.toList(), nextCursor = next))
                }
            }
            return Result.success(FeedPage(postings = collected.toList(), nextCursor = next))
        }

        private fun toggleBookmark(postingId: Long) {
            if (currentState.isOffline) {
                dispatch(FeedReducerEvent.MessageRaised(FeedMessage.OfflineReadOnly))
                return
            }
            // 확정·되돌리기는 북마크 필드만 지금 카드 위에 얹는다 — 요청이 오가는 사이 새로고침이 목록을
            // 갈아 끼웠을 수 있고, 옛 스냅샷으로 덮으면 그 변화(읽음 표시 등)가 지워진다(#235).
            val wasBookmarked =
                currentState.postings
                    .firstOrNull { it.id == postingId }
                    ?.isBookmarked ?: return
            updatePosting(postingId) { it.copy(isBookmarked = !wasBookmarked) }
            viewModelScope.launch {
                togglePostingBookmark(postingId, currentlyBookmarked = wasBookmarked)
                    .onSuccess { bookmarked -> updatePosting(postingId) { it.copy(isBookmarked = bookmarked) } }
                    .onFailure { throwable ->
                        recordFailure(FeedFailureStage.Bookmark, throwable)
                        updatePosting(postingId) { it.copy(isBookmarked = wasBookmarked) }
                        dispatch(FeedReducerEvent.MessageRaised(FeedMessage.BookmarkFailed))
                    }
            }
        }

        /** 목록에 그 공고가 남아 있을 때만 그 카드 위에 [transform] 을 적용한다. */
        private fun updatePosting(
            postingId: Long,
            transform: (Posting) -> Posting,
        ) {
            dispatch(FeedReducerEvent.PostingsReplaced(currentState.postings.map { if (it.id == postingId) transform(it) else it }))
        }

        /**
         * 게시판 목록 — 필터 시트의 선택지이자 빈 피드 사유 판정의 근거다.
         *
         * 실패해도 피드는 막지 않되 [FeedViewState.boardsLoaded] 를 켜지 않는다 — 못 받은 것을 0개로
         * 읽으면 게시판이 있는 사용자에게 「등록한 게시판이 없어요」라고 하게 된다.
         */
        private fun loadBoards() {
            boardsJob =
                viewModelScope.launch {
                    getBoards()
                        .onSuccess { boards -> dispatch(FeedReducerEvent.BoardsLoaded(boards)) }
                        .onFailure { recordFailure(FeedFailureStage.FilterBoards, it) }
                }
        }

        private fun loadTodayCount() {
            viewModelScope.launch {
                countTodayNewPostings()
                    .onSuccess { count -> dispatch(FeedReducerEvent.TodayCountLoaded(count)) }
                    .onFailure { recordFailure(FeedFailureStage.TodayCount, it) }
            }
        }

        private fun recordFailure(
            stage: FeedFailureStage,
            throwable: Throwable,
        ) {
            errorReporter.recordFeedFailure(stage, throwable)
            if (throwable is CoreDataFailure.Unauthorized) {
                dispatch(FeedReducerEvent.SessionEnded)
            }
        }

        public companion object {
            /** 검색어 입력 뒤 재조회까지 기다리는 시간. */
            public const val SEARCH_DEBOUNCE_MS: Long = 300L

            /**
             * 한 번의 이어 읽기가 빈 페이지를 따라가는 상한 — 기본 페이지 크기 기준 100건.
             *
             * **한 번에**가 핵심이다. 이 상한은 「거기까지가 전부」라는 뜻이 아니라 「사용자를 한 번 이상
             * 기다리게 하지 않는다」는 뜻이다. 5회면 왕복 다섯 번(체감 1~2초)이라 진행 표시 없이도 버틸
             * 만하고, 그 이상 늘리면 아무 반응 없이 멈춘 화면이 된다. 상한에 닿으면 멈추되
             * [FeedLoadMoreState.Paused] 로 그렇다고 말하고, 사용자가 「더 찾아보기」를 누르면 거기서부터
             * 다시 5회를 따라간다 — 상한이 총량이 아니라 한 걸음의 크기라, 100건에 갇히지 않는다.
             */
            public const val MAX_EMPTY_PAGE_FOLLOW_UPS: Int = 5
        }
    }

/** 조회가 성공했다 — 오프라인 표시와 그 근거를 모두 버린다. [FeedViewModel.reduce] 만 부른다. */
private fun FeedViewState.online(): FeedViewState =
    if (!isOffline && offlineSnapshot == null && offlineSavedAt == null) {
        this
    } else {
        copy(isOffline = false, offlineSavedAt = null, offlineSnapshot = null)
    }

package com.careercompass.feature.feed.presentation.postingdetail

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.feed.domain.usecase.OpenPostingDetailUseCase
import com.careercompass.feature.feed.domain.usecase.TogglePostingBookmarkUseCase
import com.careercompass.feature.feed.presentation.navigation.FeedRoute
import com.careercompass.feature.feed.presentation.reporting.FeedFailureStage
import com.careercompass.feature.feed.presentation.reporting.recordFeedFailure
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.SuitabilityJudgement
import com.careercompass.feature.feed.presentation.shared.model.judgeSuitability
import com.careercompass.feature.feed.presentation.shared.model.toFeedFailureReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 적합도 자동 재조회의 간격과 횟수 — 5초 × 4회 = 20초, 그 뒤 멈춘다(#221).
 *
 * 서버가 진행률을 주지 않으므로(엣지 상태 §2.2) 끝을 화면이 정해야 한다. 무한 폴링은 영구 실패 공고에서
 * 영원히 돌며 배터리와 서버를 함께 태운다. 20초는 「금방 끝나는 분석은 기다려 주되, 안 끝나는 분석에 매달리지
 * 않는다」의 경계로 잡은 값이다 — 서버가 진행 상태를 주는 날 이 상수는 사라진다.
 */
public val SUITABILITY_AUTO_RECHECK_INTERVAL: Duration = 5.seconds

/** [SUITABILITY_AUTO_RECHECK_INTERVAL] 참고. */
public const val SUITABILITY_AUTO_RECHECK_LIMIT: Int = 4

public sealed interface PostingDetailLoadState {
    public data object Loading : PostingDetailLoadState

    public data class Loaded(
        val detail: PostingDetail,
    ) : PostingDetailLoadState

    public data class Failed(
        val reason: FeedFailureReason,
    ) : PostingDetailLoadState
}

public sealed interface PostingDetailDestination {
    public data object Back : PostingDetailDestination

    public data class Raw(
        val postingId: Long,
    ) : PostingDetailDestination

    public data class Posting(
        val postingId: Long,
    ) : PostingDetailDestination

    /** 앱 셸이 마이 탭(프로필 입력)으로 보낸다. */
    public data object Profile : PostingDetailDestination

    /**
     * 지원서 초안 작성 — 앱 셸이 editor 모듈의 문항 확인 화면으로 보낸다(#183).
     *
     * 피드 그래프 안이 아니라 셸을 지나는 이유는 목적지가 다른 모듈이기 때문이다. 초안 화면에서 뒤로 오면
     * 이 상세가 그대로 남아 있어야 하므로 상세를 걷어내지 않는다.
     */
    public data class ApplicationSetup(
        val postingId: Long,
    ) : PostingDetailDestination
}

public enum class PostingDetailMessage {
    BookmarkFailed,
}

/** 공유 시트에 실을 내용. Screen 이 `Intent.ACTION_SEND` 로 바꾼다. */
public data class PostingShareRequest(
    val title: String,
    val url: String,
)

/**
 * @property isSuitabilityRecheckExhausted 적합도 자동 재조회를 다 썼다(#221). 「분석 중」일 때만 뜻이 있다 —
 *   판정이 바뀌면(점수 도착·프로필 미입력·다시 읽기) 함께 지워진다.
 */
public data class PostingDetailViewState(
    val postingId: Long,
    val loadState: PostingDetailLoadState = PostingDetailLoadState.Loading,
    val profile: UserProfile? = null,
    val isSuitabilityRecheckExhausted: Boolean = false,
    val pendingNavigation: PostingDetailDestination? = null,
    val shareRequest: PostingShareRequest? = null,
    val message: PostingDetailMessage? = null,
    val sessionEnded: Boolean = false,
) : UiState {
    public val detail: PostingDetail? get() = (loadState as? PostingDetailLoadState.Loaded)?.detail

    public val suitabilityJudgement: SuitabilityJudgement?
        get() = detail?.let { judgeSuitability(hasScore = it.suitability != null, profile = profile) }
}

/** 화면이 [PostingDetailViewModel] 에 보내는 것. */
public sealed interface PostingDetailIntent : MviIntent {
    public data class Screen(
        val event: PostingDetailEvent,
    ) : PostingDetailIntent

    public data object ConsumeNavigation : PostingDetailIntent

    public data object ConsumeShare : PostingDetailIntent

    public data object ConsumeMessage : PostingDetailIntent

    public data object ConsumeSessionEnded : PostingDetailIntent
}

/** 상태가 겪은 것. [PostingDetailViewModel] 만 만든다. */
public sealed interface PostingDetailReducerEvent : ReducerEvent {
    public data class ProfileChanged(
        val profile: UserProfile?,
    ) : PostingDetailReducerEvent

    public data class NavigationRequested(
        val destination: PostingDetailDestination,
    ) : PostingDetailReducerEvent

    public data class ShareRequested(
        val request: PostingShareRequest,
    ) : PostingDetailReducerEvent

    public data class MessageRaised(
        val message: PostingDetailMessage,
    ) : PostingDetailReducerEvent

    public data class LoadStateChanged(
        val loadState: PostingDetailLoadState,
    ) : PostingDetailReducerEvent

    /** 읽은 상세가 있을 때만 그 위를 갈아 끼운다 — 로딩·실패 중이면 아무것도 바꾸지 않는다. */
    public data class DetailReplaced(
        val detail: PostingDetail,
    ) : PostingDetailReducerEvent

    public data class RecheckExhaustedChanged(
        val isExhausted: Boolean,
    ) : PostingDetailReducerEvent

    public data object SessionEnded : PostingDetailReducerEvent

    public data object NavigationConsumed : PostingDetailReducerEvent

    public data object ShareConsumed : PostingDetailReducerEvent

    public data object MessageConsumed : PostingDetailReducerEvent

    public data object SessionEndedConsumed : PostingDetailReducerEvent
}

/**
 * 공고 상세 — 열면서 읽음 처리하고([OpenPostingDetailUseCase]), 북마크는 먼저 뒤집고 실패하면 되돌린다.
 * 진입점은 [onIntent] 하나, 전이는 [reduce] 한 곳이다(#246).
 *
 * ### 적합도 자동 재조회 (#221)
 * 적합도는 서버가 LLM 으로 계산하므로 공고를 열었을 때 아직 없는 것이 정상 경로다. 판정이 「분석 중」인 동안
 * [SUITABILITY_AUTO_RECHECK_INTERVAL] 마다 [SUITABILITY_AUTO_RECHECK_LIMIT] 번까지 **조용히** 다시 읽는다 —
 * `loadState` 를 `Loading` 으로 되돌리지 않아 화면이 깜빡이지 않는다. 다 쓰면
 * [PostingDetailViewState.isSuitabilityRecheckExhausted] 를 올려 카드가 「다시 확인」을 열고, 그 버튼은 한 번에
 * 한 번만 더 묻는다.
 *
 * 재조회의 시작·정지는 **판정을 따라간다**([SuitabilityJudgement]): 「분석 중」이 되면 시작하고, 그 밖의
 * 판정이 되면 멈추고 소진도 지운다. 그래서 프로필이 늦게 도착해 「프로필 미입력」으로 갈리면 그 자리에서
 * 멈춘다 — 둘을 섞지 않는다는 요구(#100)가 폴링 게이트에서도 지켜진다. 마이 탭에서 프로필을 채우고 돌아와
 * 「분석 중」이 되면 그때 시작한다.
 */
@HiltViewModel(assistedFactory = PostingDetailViewModel.Factory::class)
public class PostingDetailViewModel
    @AssistedInject
    constructor(
        @Assisted route: FeedRoute.PostingDetail,
        private val openPostingDetail: OpenPostingDetailUseCase,
        private val togglePostingBookmark: TogglePostingBookmarkUseCase,
        private val userProfileRepository: UserProfileRepository,
        private val errorReporter: ErrorReporter,
        /** Screen 이 상대 시각·마감 표기에 같은 시계를 쓴다. */
        public val clock: Clock,
    ) : MviViewModel<PostingDetailIntent, PostingDetailViewState, PostingDetailReducerEvent>(
            PostingDetailViewState(postingId = route.postingId),
        ) {
        /** 공고 id 는 Nav3 키가 나른다 — entry 가 이 팩토리로 키를 넘긴다(#259). */
        @AssistedFactory
        public interface Factory {
            public fun create(route: FeedRoute.PostingDetail): PostingDetailViewModel
        }

        private val postingId: Long get() = currentState.postingId

        private var loadJob: Job? = null
        private var recheckJob: Job? = null

        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile -> dispatch(PostingDetailReducerEvent.ProfileChanged(profile)) }
            }
            viewModelScope.launch {
                uiState
                    .map { it.suitabilityJudgement }
                    .distinctUntilChanged()
                    .collect { judgement ->
                        if (judgement == SuitabilityJudgement.Analyzing) startAutoRecheck() else stopRecheck()
                    }
            }
            load()
        }

        override fun onIntent(intent: PostingDetailIntent) {
            when (intent) {
                is PostingDetailIntent.Screen -> onEvent(intent.event)
                PostingDetailIntent.ConsumeNavigation -> dispatch(PostingDetailReducerEvent.NavigationConsumed)
                PostingDetailIntent.ConsumeShare -> dispatch(PostingDetailReducerEvent.ShareConsumed)
                PostingDetailIntent.ConsumeMessage -> dispatch(PostingDetailReducerEvent.MessageConsumed)
                PostingDetailIntent.ConsumeSessionEnded -> dispatch(PostingDetailReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: PostingDetailViewState,
            event: PostingDetailReducerEvent,
        ): PostingDetailViewState =
            when (event) {
                is PostingDetailReducerEvent.ProfileChanged -> {
                    state.copy(profile = event.profile)
                }

                is PostingDetailReducerEvent.NavigationRequested -> {
                    state.copy(pendingNavigation = event.destination)
                }

                is PostingDetailReducerEvent.ShareRequested -> {
                    state.copy(shareRequest = event.request)
                }

                is PostingDetailReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                is PostingDetailReducerEvent.LoadStateChanged -> {
                    state.copy(loadState = event.loadState)
                }

                is PostingDetailReducerEvent.DetailReplaced -> {
                    if (state.loadState is PostingDetailLoadState.Loaded) {
                        state.copy(loadState = PostingDetailLoadState.Loaded(event.detail))
                    } else {
                        state
                    }
                }

                is PostingDetailReducerEvent.RecheckExhaustedChanged -> {
                    state.copy(isSuitabilityRecheckExhausted = event.isExhausted)
                }

                PostingDetailReducerEvent.SessionEnded -> {
                    state.copy(sessionEnded = true)
                }

                PostingDetailReducerEvent.NavigationConsumed -> {
                    state.copy(pendingNavigation = null)
                }

                PostingDetailReducerEvent.ShareConsumed -> {
                    state.copy(shareRequest = null)
                }

                PostingDetailReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                PostingDetailReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }

        private fun onEvent(event: PostingDetailEvent) {
            when (event) {
                PostingDetailEvent.BackClicked -> {
                    navigate(PostingDetailDestination.Back)
                }

                PostingDetailEvent.BookmarkToggled -> {
                    toggleBookmark()
                }

                PostingDetailEvent.ShareClicked -> {
                    val detail = currentState.detail ?: return
                    dispatch(PostingDetailReducerEvent.ShareRequested(PostingShareRequest(title = detail.title, url = detail.url)))
                }

                PostingDetailEvent.ViewOriginalClicked -> {
                    navigate(PostingDetailDestination.Raw(postingId))
                }

                PostingDetailEvent.CreateDraftClicked -> {
                    navigate(PostingDetailDestination.ApplicationSetup(postingId))
                }

                PostingDetailEvent.CompleteProfileClicked -> {
                    navigate(PostingDetailDestination.Profile)
                }

                is PostingDetailEvent.SimilarPostingSelected -> {
                    val similarId = event.listingId.toLongOrNull() ?: return
                    navigate(PostingDetailDestination.Posting(similarId))
                }

                PostingDetailEvent.RetryClicked -> {
                    load()
                }

                PostingDetailEvent.SuitabilityRecheckClicked -> {
                    recheckOnDemand()
                }
            }
        }

        private fun navigate(destination: PostingDetailDestination) {
            dispatch(PostingDetailReducerEvent.NavigationRequested(destination))
        }

        private fun load() {
            loadJob?.cancel()
            dispatch(PostingDetailReducerEvent.LoadStateChanged(PostingDetailLoadState.Loading))
            loadJob =
                viewModelScope.launch {
                    openPostingDetail(postingId)
                        .onSuccess { detail -> dispatch(PostingDetailReducerEvent.LoadStateChanged(PostingDetailLoadState.Loaded(detail))) }
                        .onFailure { throwable ->
                            recordFailure(FeedFailureStage.PostingDetail, throwable)
                            dispatch(
                                PostingDetailReducerEvent.LoadStateChanged(
                                    PostingDetailLoadState.Failed(throwable.toFeedFailureReason()),
                                ),
                            )
                        }
                }
        }

        private fun startAutoRecheck() {
            if (recheckJob?.isActive == true) return
            recheckJob =
                viewModelScope.launch {
                    repeat(SUITABILITY_AUTO_RECHECK_LIMIT) {
                        delay(SUITABILITY_AUTO_RECHECK_INTERVAL)
                        // 기다리는 사이 판정이 갈렸으면(프로필 도착 등) 헛요청을 보내지 않는다.
                        if (!isStillAnalyzing()) return@launch
                        recheckSuitability()
                    }
                    if (isStillAnalyzing()) dispatch(PostingDetailReducerEvent.RecheckExhaustedChanged(true))
                }
        }

        private fun stopRecheck() {
            recheckJob?.cancel()
            recheckJob = null
            if (currentState.isSuitabilityRecheckExhausted) dispatch(PostingDetailReducerEvent.RecheckExhaustedChanged(false))
        }

        /** 「다시 확인」 — 한 번만 더 묻고, 여전히 분석 중이면 다시 소진 상태로 돌아간다. */
        private fun recheckOnDemand() {
            if (!isStillAnalyzing()) return
            recheckJob?.cancel()
            recheckJob =
                viewModelScope.launch {
                    dispatch(PostingDetailReducerEvent.RecheckExhaustedChanged(false))
                    recheckSuitability()
                    if (isStillAnalyzing()) dispatch(PostingDetailReducerEvent.RecheckExhaustedChanged(true))
                }
        }

        /**
         * 조용한 재조회 — 성공하면 상세를 갈아 끼우되 `Loaded` 를 유지하고, 실패해도 화면을 흔들지 않는다.
         *
         * 북마크 여부만은 지금 값을 지킨다 — 사용자가 방금 뒤집어 서버 응답을 기다리는 중일 수 있고, 그 결과는
         * [toggleBookmark] 가 확정한다. [OpenPostingDetailUseCase] 는 이미 읽은 공고에 읽음 요청을 다시 보내지
         * 않으므로 재조회가 읽음 요청을 되풀이하지도 않는다.
         */
        private suspend fun recheckSuitability() {
            openPostingDetail(postingId)
                .onSuccess { fresh -> updateDetail { current -> fresh.copy(isBookmarked = current.isBookmarked) } }
                .onFailure { throwable -> recordFailure(FeedFailureStage.SuitabilityRecheck, throwable) }
        }

        private fun isStillAnalyzing(): Boolean = currentState.suitabilityJudgement == SuitabilityJudgement.Analyzing

        /**
         * 북마크는 먼저 뒤집고, 응답이 오면 **그 필드만** 지금 상세 위에 확정한다.
         *
         * 요청을 보내던 순간의 상세를 통째로 되돌려 놓으면 안 된다(#235) — 응답이 오가는 사이 상세는 바뀔 수
         * 있다. 「분석 중」이면 [recheckSuitability] 가 적합도를 실어 오는데, 옛 스냅샷으로 덮으면 방금 나타난
         * 점수가 사라진다. 그래서 성공은 서버가 준 북마크 값을, 실패는 누르기 전 북마크 값을 **지금 상세**에
         * 얹는다.
         */
        private fun toggleBookmark() {
            val wasBookmarked = currentState.detail?.isBookmarked ?: return
            updateDetail { it.copy(isBookmarked = !wasBookmarked) }
            viewModelScope.launch {
                togglePostingBookmark(postingId, currentlyBookmarked = wasBookmarked)
                    .onSuccess { bookmarked -> updateDetail { it.copy(isBookmarked = bookmarked) } }
                    .onFailure { throwable ->
                        recordFailure(FeedFailureStage.Bookmark, throwable)
                        updateDetail { it.copy(isBookmarked = wasBookmarked) }
                        dispatch(PostingDetailReducerEvent.MessageRaised(PostingDetailMessage.BookmarkFailed))
                    }
            }
        }

        /** 읽은 상세가 있을 때만 그 위에 [transform] 을 적용한다 — 로딩·실패 중에는 아무것도 하지 않는다. */
        private fun updateDetail(transform: (PostingDetail) -> PostingDetail) {
            val detail = currentState.detail ?: return
            dispatch(PostingDetailReducerEvent.DetailReplaced(transform(detail)))
        }

        private fun recordFailure(
            stage: FeedFailureStage,
            throwable: Throwable,
        ) {
            errorReporter.recordFeedFailure(stage, throwable)
            if (throwable is CoreDataFailure.Unauthorized) {
                dispatch(PostingDetailReducerEvent.SessionEnded)
            }
        }
    }

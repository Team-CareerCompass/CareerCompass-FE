package com.careercompass.feature.feed.presentation.postingraw

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.url.ExternalUrl
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.feed.domain.usecase.OpenPostingDetailUseCase
import com.careercompass.feature.feed.presentation.navigation.FeedRoute
import com.careercompass.feature.feed.presentation.reporting.FeedFailureStage
import com.careercompass.feature.feed.presentation.reporting.UnsupportedExternalUrlException
import com.careercompass.feature.feed.presentation.reporting.recordFeedFailure
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.toFeedFailureReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock

public sealed interface PostingRawLoadState {
    public data object Loading : PostingRawLoadState

    public data class Loaded(
        val detail: PostingDetail,
    ) : PostingRawLoadState

    /**
     * 실패를 **사유**로 들고 있는다 — 피드 홈·게시판 목록·공고 상세가 이미 쓰는 계약([FeedFailureReason])이다.
     *
     * 예전에는 `isNetworkUnavailable` 불리언 한 칸이었고, 그래서 503(서버 점검)이 「네트워크 아님」 쪽으로
     * 떨어져 일반 실패로 접혔다. 점검 중에 상세에서 「원문 보기」를 누르면 바로 앞 화면은 「서비스가 잠시
     * 점검 중이에요」라고 해 놓고 이 화면만 「원문을 불러오지 못했어요」라고 말하던 원인이다(#212).
     */
    public data class Failed(
        val reason: FeedFailureReason,
    ) : PostingRawLoadState
}

public data class PostingRawViewState(
    val postingId: Long,
    val loadState: PostingRawLoadState = PostingRawLoadState.Loading,
    val isBackRequested: Boolean = false,
    /** 외부 브라우저로 열 원본 링크. Screen 이 `Intent.ACTION_VIEW` 로 바꾸고 [PostingRawIntent.ConsumeOpenUrl] 로 비운다. */
    val openUrl: String? = null,
    /** 원본 링크가 웹 주소가 아니어서 열지 않았다. Screen 이 열기 실패와 같은 안내를 띄운다. */
    val openUrlRejected: Boolean = false,
    val sessionEnded: Boolean = false,
) : UiState

/** 화면이 [PostingRawViewModel] 에 보내는 것. */
public sealed interface PostingRawIntent : MviIntent {
    public data class Screen(
        val event: PostingRawEvent,
    ) : PostingRawIntent

    public data object Retry : PostingRawIntent

    public data object ConsumeBack : PostingRawIntent

    public data object ConsumeOpenUrl : PostingRawIntent

    public data object ConsumeOpenUrlRejected : PostingRawIntent

    public data object ConsumeSessionEnded : PostingRawIntent
}

/** 상태가 겪은 것. [PostingRawViewModel] 만 만든다. */
public sealed interface PostingRawReducerEvent : ReducerEvent {
    public data object BackRequested : PostingRawReducerEvent

    public data class OpenUrlRequested(
        val url: String,
    ) : PostingRawReducerEvent

    public data object OpenUrlRejected : PostingRawReducerEvent

    public data class LoadStateChanged(
        val loadState: PostingRawLoadState,
    ) : PostingRawReducerEvent

    public data object SessionEnded : PostingRawReducerEvent

    public data object BackConsumed : PostingRawReducerEvent

    public data object OpenUrlConsumed : PostingRawReducerEvent

    public data object OpenUrlRejectedConsumed : PostingRawReducerEvent

    public data object SessionEndedConsumed : PostingRawReducerEvent
}

/** 원문 보기 — 상세와 같은 use case 로 본문·원본 링크를 다시 받는다(이미 읽음 처리된 공고라 추가 요청은 없다). */
@HiltViewModel(assistedFactory = PostingRawViewModel.Factory::class)
public class PostingRawViewModel
    @AssistedInject
    constructor(
        @Assisted route: FeedRoute.PostingRaw,
        private val openPostingDetail: OpenPostingDetailUseCase,
        private val errorReporter: ErrorReporter,
        /** Screen 이 수집 시각 표기에 같은 시계를 쓴다. */
        public val clock: Clock,
    ) : MviViewModel<PostingRawIntent, PostingRawViewState, PostingRawReducerEvent>(
            PostingRawViewState(postingId = route.postingId),
        ) {
        /** 공고 id 는 Nav3 키가 나른다 — entry 가 이 팩토리로 키를 넘긴다(#259). */
        @AssistedFactory
        public interface Factory {
            public fun create(route: FeedRoute.PostingRaw): PostingRawViewModel
        }

        private val postingId: Long get() = currentState.postingId

        private var loadJob: Job? = null

        init {
            load()
        }

        override fun onIntent(intent: PostingRawIntent) {
            when (intent) {
                is PostingRawIntent.Screen -> onEvent(intent.event)
                PostingRawIntent.Retry -> load()
                PostingRawIntent.ConsumeBack -> dispatch(PostingRawReducerEvent.BackConsumed)
                PostingRawIntent.ConsumeOpenUrl -> dispatch(PostingRawReducerEvent.OpenUrlConsumed)
                PostingRawIntent.ConsumeOpenUrlRejected -> dispatch(PostingRawReducerEvent.OpenUrlRejectedConsumed)
                PostingRawIntent.ConsumeSessionEnded -> dispatch(PostingRawReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: PostingRawViewState,
            event: PostingRawReducerEvent,
        ): PostingRawViewState =
            when (event) {
                PostingRawReducerEvent.BackRequested -> state.copy(isBackRequested = true)
                is PostingRawReducerEvent.OpenUrlRequested -> state.copy(openUrl = event.url)
                PostingRawReducerEvent.OpenUrlRejected -> state.copy(openUrlRejected = true)
                is PostingRawReducerEvent.LoadStateChanged -> state.copy(loadState = event.loadState)
                PostingRawReducerEvent.SessionEnded -> state.copy(sessionEnded = true)
                PostingRawReducerEvent.BackConsumed -> state.copy(isBackRequested = false)
                PostingRawReducerEvent.OpenUrlConsumed -> state.copy(openUrl = null)
                PostingRawReducerEvent.OpenUrlRejectedConsumed -> state.copy(openUrlRejected = false)
                PostingRawReducerEvent.SessionEndedConsumed -> state.copy(sessionEnded = false)
            }

        private fun onEvent(event: PostingRawEvent) {
            when (event) {
                PostingRawEvent.BackClicked -> {
                    dispatch(PostingRawReducerEvent.BackRequested)
                }

                PostingRawEvent.OpenOriginalClicked -> {
                    val detail = (currentState.loadState as? PostingRawLoadState.Loaded)?.detail ?: return
                    val openable = ExternalUrl.openableOrNull(detail.url)
                    if (openable == null) {
                        // 서버가 준 값이라고 그대로 넘기지 않는다 — 이 주소는 사용자가 등록한 게시판에서 긁혀 온다.
                        val failure = UnsupportedExternalUrlException(ExternalUrl.schemeOrNull(detail.url))
                        errorReporter.recordFeedFailure(
                            stage = FeedFailureStage.PostingRaw,
                            throwable = failure,
                            attributes = failure.reportAttributes,
                        )
                        dispatch(PostingRawReducerEvent.OpenUrlRejected)
                        return
                    }
                    dispatch(PostingRawReducerEvent.OpenUrlRequested(openable))
                }
            }
        }

        private fun load() {
            loadJob?.cancel()
            dispatch(PostingRawReducerEvent.LoadStateChanged(PostingRawLoadState.Loading))
            loadJob =
                viewModelScope.launch {
                    openPostingDetail(postingId)
                        .onSuccess { detail -> dispatch(PostingRawReducerEvent.LoadStateChanged(PostingRawLoadState.Loaded(detail))) }
                        .onFailure { throwable ->
                            errorReporter.recordFeedFailure(FeedFailureStage.PostingRaw, throwable)
                            dispatch(PostingRawReducerEvent.LoadStateChanged(PostingRawLoadState.Failed(throwable.toFeedFailureReason())))
                            if (throwable is CoreDataFailure.Unauthorized) dispatch(PostingRawReducerEvent.SessionEnded)
                        }
                }
        }
    }

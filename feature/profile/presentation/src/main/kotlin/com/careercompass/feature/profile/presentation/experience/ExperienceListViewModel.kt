package com.careercompass.feature.profile.presentation.experience

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.profile.domain.usecase.GetExperiencesUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import com.careercompass.feature.profile.presentation.reporting.ProfileFailureStage
import com.careercompass.feature.profile.presentation.reporting.recordProfileFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 경험 카드 목록이 그리는 값.
 *
 * @property cards 지금까지 읽어 온 페이지를 이어 붙인 목록. 정렬은 서버가 준 순서 그대로다.
 * @property nextCursor null 이면 마지막 페이지다.
 * @property totalCount 전체 개수. **필터가 걸려 있으면 모른다**(null) — 서버가 필터별 총계를 주지 않고,
 *   필터된 목록의 길이를 전체인 양 적으면 상한 안내가 거짓이 된다.
 */
public data class ExperienceListUiState(
    val cards: List<Experience> = emptyList(),
    val filter: ExperienceTypeFilter = ExperienceTypeFilter(null),
    val nextCursor: String? = null,
    val totalCount: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadFailure: FailureKind? = null,
    val message: ExperienceListMessage? = null,
    val pendingCardId: Long? = null,
    val isAddRequested: Boolean = false,
    val sessionEnd: ProfileSessionEnd? = null,
) : UiState {
    /** 첫 조회 중이라 그릴 것이 아무것도 없다. */
    val isInitialLoading: Boolean get() = cards.isEmpty() && loadFailure == null && isLoading

    /** 실패 화면이 목록 자리를 덮는가 — 읽어 온 카드가 하나도 없을 때뿐이다. */
    val isFailureVisible: Boolean get() = cards.isEmpty() && loadFailure != null

    /** 카드가 0개인 이유. 카드가 있으면 null 이다. */
    val emptyReason: ExperienceEmptyReason?
        get() =
            when {
                cards.isNotEmpty() || isLoading || loadFailure != null -> null
                filter.type != null -> ExperienceEmptyReason.FilteredOut
                else -> ExperienceEmptyReason.NoCards
            }

    /**
     * 상한(30개)에 닿았는가 — **전체 개수를 알 때만 판정한다.**
     *
     * 필터가 걸려 있으면 전체를 모르므로 잠그지 않는다. 모르면서 잠그면 카드가 다섯 장인 사용자가
     * 「자격증」 필터에서 추가를 못 하게 된다.
     */
    val isLimitReached: Boolean get() = (totalCount ?: 0) >= MAX_EXPERIENCE_CARDS

    /** 상한 표시에 쓰는 값. 필터가 걸려 있으면 전체를 모르므로 개수 표시를 접는다. */
    val countLabelValue: Int? get() = totalCount

    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore && !isLoading
}

public sealed interface ExperienceListIntent : MviIntent {
    public data class Screen(
        val event: ExperienceListEvent,
    ) : ExperienceListIntent

    /** 화면에 (다시) 들어왔다 — 편집·삭제하고 돌아오면 목록이 바뀌어 있다. */
    public data object Refresh : ExperienceListIntent

    public data object ConsumeMessage : ExperienceListIntent

    public data object ConsumeCardNavigation : ExperienceListIntent

    public data object ConsumeAddRequest : ExperienceListIntent

    public data object ConsumeSessionEnded : ExperienceListIntent
}

public sealed interface ExperienceListReducerEvent : ReducerEvent {
    public data class LoadStarted(
        val filter: ExperienceTypeFilter,
    ) : ExperienceListReducerEvent

    public data class Loaded(
        val cards: List<Experience>,
        val nextCursor: String?,
        val totalCount: Int?,
    ) : ExperienceListReducerEvent

    public data class LoadFailed(
        val kind: FailureKind,
    ) : ExperienceListReducerEvent

    public data object LoadMoreStarted : ExperienceListReducerEvent

    public data class MoreLoaded(
        val cards: List<Experience>,
        val nextCursor: String?,
        val totalCount: Int?,
    ) : ExperienceListReducerEvent

    public data class MessageRaised(
        val message: ExperienceListMessage,
    ) : ExperienceListReducerEvent

    public data class CardRequested(
        val id: Long,
    ) : ExperienceListReducerEvent

    public data object AddRequested : ExperienceListReducerEvent

    public data class SessionEnded(
        val cause: ProfileSessionEnd,
    ) : ExperienceListReducerEvent

    public data object MessageConsumed : ExperienceListReducerEvent

    public data object CardNavigationConsumed : ExperienceListReducerEvent

    public data object AddRequestConsumed : ExperienceListReducerEvent

    public data object SessionEndedConsumed : ExperienceListReducerEvent
}

/**
 * 경험 카드 목록(#178) — `GET /experiences` 를 유형 필터·커서 페이징으로 읽는다.
 *
 * ### 전체 개수를 어떻게 아는가
 * §3 은 총계를 주지 않는다. 그래서 **필터가 없을 때** 읽어 온 페이지의 누계를 전체로 본다 — 커서를 끝까지
 * 따라가지 않은 동안에는 실제보다 작을 수 있지만, 상한(30)이 기본 페이지(20)의 두 배도 안 되므로 두 번째
 * 페이지를 읽으면 확정된다. 필터가 걸려 있으면 전체를 모른다고 두고 상한 판정을 하지 않는다 —
 * 모르면서 잠그면 카드가 몇 장 없는 사용자가 추가를 못 하게 된다.
 *
 * ### 상한을 서버가 알려 주기 전에 화면이 먼저 말한다
 * 422 `LIMIT_EXCEEDED` 를 기다렸다 알리면 사용자는 폼을 다 채운 뒤에야 막힌다(#178 의 완료 조건).
 */
@HiltViewModel
public class ExperienceListViewModel
    @Inject
    constructor(
        private val getExperiences: GetExperiencesUseCase,
        private val errorReporter: ErrorReporter,
    ) : MviViewModel<ExperienceListIntent, ExperienceListUiState, ExperienceListReducerEvent>(ExperienceListUiState()) {
        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null

        init {
            load(ExperienceTypeFilter(null))
        }

        override fun onIntent(intent: ExperienceListIntent) {
            when (intent) {
                is ExperienceListIntent.Screen -> onEvent(intent.event)
                ExperienceListIntent.Refresh -> load(currentState.filter)
                ExperienceListIntent.ConsumeMessage -> dispatch(ExperienceListReducerEvent.MessageConsumed)
                ExperienceListIntent.ConsumeCardNavigation -> dispatch(ExperienceListReducerEvent.CardNavigationConsumed)
                ExperienceListIntent.ConsumeAddRequest -> dispatch(ExperienceListReducerEvent.AddRequestConsumed)
                ExperienceListIntent.ConsumeSessionEnded -> dispatch(ExperienceListReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: ExperienceListUiState,
            event: ExperienceListReducerEvent,
        ): ExperienceListUiState =
            when (event) {
                is ExperienceListReducerEvent.LoadStarted -> {
                    // 필터를 바꾸면 커서도 처음으로 돌아간다 — 이전 필터의 페이지를 이어 붙이면 목록이 섞인다.
                    state.copy(filter = event.filter, cards = emptyList(), nextCursor = null, isLoading = true, loadFailure = null)
                }

                is ExperienceListReducerEvent.Loaded -> {
                    state.copy(
                        cards = event.cards,
                        nextCursor = event.nextCursor,
                        totalCount = event.totalCount ?: state.totalCount,
                        isLoading = false,
                        loadFailure = null,
                    )
                }

                is ExperienceListReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, loadFailure = event.kind)
                }

                ExperienceListReducerEvent.LoadMoreStarted -> {
                    state.copy(isLoadingMore = true)
                }

                is ExperienceListReducerEvent.MoreLoaded -> {
                    state.copy(
                        cards = state.cards + event.cards,
                        nextCursor = event.nextCursor,
                        totalCount = event.totalCount ?: state.totalCount,
                        isLoadingMore = false,
                    )
                }

                is ExperienceListReducerEvent.MessageRaised -> {
                    state.copy(isLoadingMore = false, message = event.message)
                }

                is ExperienceListReducerEvent.CardRequested -> {
                    state.copy(pendingCardId = event.id)
                }

                ExperienceListReducerEvent.AddRequested -> {
                    state.copy(isAddRequested = true)
                }

                is ExperienceListReducerEvent.SessionEnded -> {
                    state.copy(isLoading = false, isLoadingMore = false, sessionEnd = event.cause)
                }

                ExperienceListReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                ExperienceListReducerEvent.CardNavigationConsumed -> {
                    state.copy(pendingCardId = null)
                }

                ExperienceListReducerEvent.AddRequestConsumed -> {
                    state.copy(isAddRequested = false)
                }

                ExperienceListReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnd = null)
                }
            }

        private fun onEvent(event: ExperienceListEvent) {
            when (event) {
                is ExperienceListEvent.FilterSelected -> {
                    if (event.filter != currentState.filter) load(event.filter)
                }

                is ExperienceListEvent.CardClicked -> {
                    dispatch(ExperienceListReducerEvent.CardRequested(event.id))
                }

                ExperienceListEvent.AddClicked -> {
                    if (currentState.isLimitReached) {
                        dispatch(ExperienceListReducerEvent.MessageRaised(ExperienceListMessage.LimitReached))
                    } else {
                        dispatch(ExperienceListReducerEvent.AddRequested)
                    }
                }

                ExperienceListEvent.LoadMore -> {
                    loadMore()
                }

                ExperienceListEvent.RetryClicked -> {
                    load(currentState.filter)
                }

                // 뒤로 가기는 목적지 결정이라 Screen 의 콜백이 갖는다.
                ExperienceListEvent.BackClicked -> {
                    Unit
                }
            }
        }

        private fun load(filter: ExperienceTypeFilter) {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            dispatch(ExperienceListReducerEvent.LoadStarted(filter))
            loadJob =
                viewModelScope.launch {
                    getExperiences(type = filter.type)
                        .onSuccess { page ->
                            dispatch(
                                ExperienceListReducerEvent.Loaded(
                                    cards = page.items,
                                    nextCursor = page.nextCursor,
                                    totalCount = page.items.size.takeIf { filter.type == null },
                                ),
                            )
                        }.onFailure { cause -> onFailure(ProfileFailureStage.ExperienceList, cause) }
                }
        }

        /** 이어 읽기 실패는 이미 보이는 목록을 덮지 않는다 — 스낵바 한 줄로 끝난다. */
        private fun loadMore() {
            val state = currentState
            val cursor = state.nextCursor ?: return
            if (!state.canLoadMore || loadMoreJob?.isActive == true) return
            dispatch(ExperienceListReducerEvent.LoadMoreStarted)
            loadMoreJob =
                viewModelScope.launch {
                    getExperiences(type = state.filter.type, cursor = cursor)
                        .onSuccess { page ->
                            dispatch(
                                ExperienceListReducerEvent.MoreLoaded(
                                    cards = page.items,
                                    nextCursor = page.nextCursor,
                                    totalCount =
                                        (currentState.cards.size + page.items.size).takeIf { state.filter.type == null },
                                ),
                            )
                        }.onFailure { cause ->
                            errorReporter.recordProfileFailure(ProfileFailureStage.ExperienceList, cause)
                            if (cause is CoreDataFailure.Unauthorized) {
                                dispatch(ExperienceListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                            } else {
                                dispatch(ExperienceListReducerEvent.MessageRaised(ExperienceListMessage.LoadMoreFailed))
                            }
                        }
                }
        }

        private fun onFailure(
            stage: ProfileFailureStage,
            cause: Throwable,
        ) {
            errorReporter.recordProfileFailure(stage, cause)
            if (cause is CoreDataFailure.Unauthorized) {
                dispatch(ExperienceListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
            } else {
                dispatch(ExperienceListReducerEvent.LoadFailed(cause.toFailureKind()))
            }
        }
    }

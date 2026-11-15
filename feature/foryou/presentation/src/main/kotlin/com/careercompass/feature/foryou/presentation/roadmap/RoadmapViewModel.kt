package com.careercompass.feature.foryou.presentation.roadmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.reporting.recordStagedFailure
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.usecase.GetRoadmapComparisonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

public data class RoadmapUiState(
    val cohort: RoadmapCohort = RoadmapCohort.Peer,
    val comparison: RoadmapComparison? = null,
    val isLoading: Boolean = false,
    val failure: FailureKind? = null,
    val sessionEnded: Boolean = false,
) : UiState

public sealed interface RoadmapIntent : MviIntent {
    public data class SelectCohort(
        val cohort: RoadmapCohort,
    ) : RoadmapIntent

    public data object Refresh : RoadmapIntent

    public data object Resumed : RoadmapIntent

    public data object ConsumeSessionEnded : RoadmapIntent
}

public sealed interface RoadmapReducerEvent : ReducerEvent {
    public data class Loading(
        val cohort: RoadmapCohort,
    ) : RoadmapReducerEvent

    public data class Loaded(
        val comparison: RoadmapComparison,
    ) : RoadmapReducerEvent

    public data class Failed(
        val kind: FailureKind,
    ) : RoadmapReducerEvent

    public data object SessionEnded : RoadmapReducerEvent

    public data object SessionEndConsumed : RoadmapReducerEvent
}

/** 코호트 전환 때 이전 요청을 취소하고, 늦은 응답도 세대 번호로 버린다. */
@HiltViewModel
public class RoadmapViewModel
    @Inject
    constructor(
        private val getComparison: GetRoadmapComparisonUseCase,
        private val errorReporter: ErrorReporter,
        private val savedStateHandle: SavedStateHandle,
    ) : MviViewModel<RoadmapIntent, RoadmapUiState, RoadmapReducerEvent>(
            RoadmapUiState(cohort = RoadmapCohort.fromWireValue(savedStateHandle.get<String>(COHORT_KEY).orEmpty()) ?: RoadmapCohort.Peer),
        ) {
        private var loadJob: Job? = null
        private var generation = 0L
        private var wasResumed = false

        init {
            load(currentState.cohort)
        }

        override fun onIntent(intent: RoadmapIntent) {
            when (intent) {
                is RoadmapIntent.SelectCohort -> {
                    if (intent.cohort != currentState.cohort) load(intent.cohort)
                }

                RoadmapIntent.Refresh -> {
                    if (loadJob?.isActive != true) load(currentState.cohort)
                }

                RoadmapIntent.Resumed -> {
                    if (wasResumed && loadJob?.isActive != true) load(currentState.cohort)
                    wasResumed = true
                }

                RoadmapIntent.ConsumeSessionEnded -> {
                    dispatch(RoadmapReducerEvent.SessionEndConsumed)
                }
            }
        }

        private fun load(cohort: RoadmapCohort) {
            loadJob?.cancel()
            val requestGeneration = ++generation
            savedStateHandle[COHORT_KEY] = cohort.wireValue
            dispatch(RoadmapReducerEvent.Loading(cohort))
            loadJob =
                viewModelScope.launch {
                    val result = getComparison(cohort)
                    if (requestGeneration != generation) return@launch
                    result.fold(
                        onSuccess = { dispatch(RoadmapReducerEvent.Loaded(it)) },
                        onFailure = { failure ->
                            errorReporter.recordStagedFailure(stageKey = "foryou_stage", stage = "roadmap_compare", throwable = failure)
                            if (failure is CoreDataFailure.Unauthorized) {
                                savedStateHandle.remove<String>(COHORT_KEY)
                                dispatch(RoadmapReducerEvent.SessionEnded)
                            } else {
                                dispatch(RoadmapReducerEvent.Failed(failure.toFailureKind()))
                            }
                        },
                    )
                }
        }

        override fun reduce(
            state: RoadmapUiState,
            event: RoadmapReducerEvent,
        ): RoadmapUiState =
            when (event) {
                is RoadmapReducerEvent.Loading -> state.copy(cohort = event.cohort, comparison = null, isLoading = true, failure = null)
                is RoadmapReducerEvent.Loaded -> state.copy(comparison = event.comparison, isLoading = false, failure = null)
                is RoadmapReducerEvent.Failed -> state.copy(isLoading = false, failure = event.kind)
                RoadmapReducerEvent.SessionEnded -> state.copy(comparison = null, isLoading = false, failure = null, sessionEnded = true)
                RoadmapReducerEvent.SessionEndConsumed -> state.copy(sessionEnded = false)
            }
    }

private const val COHORT_KEY = "roadmap.cohort"

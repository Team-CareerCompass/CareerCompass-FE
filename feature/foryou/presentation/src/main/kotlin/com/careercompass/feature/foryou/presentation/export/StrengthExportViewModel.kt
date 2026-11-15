package com.careercompass.feature.foryou.presentation.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.reporting.recordStagedFailure
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.repository.ExperienceRepository
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.usecase.ExportStrengthsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

public data class StrengthExportUiState(
    val format: ExportFormat = ExportFormat.Markdown,
    val sections: List<ExportSection> = ExportSection.entries,
    val preview: StrengthExport? = null,
    val isGenerating: Boolean = false,
    val failure: FailureKind? = null,
    val hasExperiences: Boolean? = null,
    val copyRequest: String? = null,
    val sessionEnded: Boolean = false,
) : UiState {
    val canGenerate: Boolean get() = sections.isNotEmpty() && !isGenerating
    val canCopy: Boolean get() = !isGenerating && !preview?.content.isNullOrBlank()
}

public sealed interface StrengthExportIntent : MviIntent {
    public data class SelectFormat(
        val format: ExportFormat,
    ) : StrengthExportIntent

    public data class ToggleSection(
        val section: ExportSection,
    ) : StrengthExportIntent

    public data object GeneratePreview : StrengthExportIntent

    public data object CopyPreview : StrengthExportIntent

    public data object ConsumeCopy : StrengthExportIntent

    public data object RefreshAvailability : StrengthExportIntent

    public data object ConsumeSessionEnded : StrengthExportIntent
}

public sealed interface StrengthExportReducerEvent : ReducerEvent {
    public data class FormatSelected(
        val format: ExportFormat,
    ) : StrengthExportReducerEvent

    public data class SectionsSelected(
        val sections: List<ExportSection>,
    ) : StrengthExportReducerEvent

    public data object Generating : StrengthExportReducerEvent

    public data class Generated(
        val export: StrengthExport,
    ) : StrengthExportReducerEvent

    public data class Failed(
        val kind: FailureKind,
    ) : StrengthExportReducerEvent

    public data class AvailabilityLoaded(
        val hasExperiences: Boolean?,
    ) : StrengthExportReducerEvent

    public data object CopyRequested : StrengthExportReducerEvent

    public data object CopyConsumed : StrengthExportReducerEvent

    public data object SessionEnded : StrengthExportReducerEvent

    public data object SessionEndConsumed : StrengthExportReducerEvent
}

@HiltViewModel
public class StrengthExportViewModel
    @Inject
    constructor(
        private val exportStrengths: ExportStrengthsUseCase,
        private val experiences: ExperienceRepository,
        private val errorReporter: ErrorReporter,
        private val savedStateHandle: SavedStateHandle,
    ) : MviViewModel<StrengthExportIntent, StrengthExportUiState, StrengthExportReducerEvent>(restoredState(savedStateHandle)) {
        private var exportJob: Job? = null
        private var availabilityJob: Job? = null
        private var generation = 0L
        private var availabilityGeneration = 0L
        private var ended = false

        override fun onIntent(intent: StrengthExportIntent) {
            if (ended && intent != StrengthExportIntent.ConsumeSessionEnded) return
            when (intent) {
                is StrengthExportIntent.SelectFormat -> {
                    if (!currentState.isGenerating && currentState.format != intent.format) {
                        savedStateHandle[FORMAT_KEY] = intent.format.wireValue
                        dispatch(StrengthExportReducerEvent.FormatSelected(intent.format))
                    }
                }

                is StrengthExportIntent.ToggleSection -> {
                    if (!currentState.isGenerating) {
                        val selected = currentState.sections.toMutableSet().apply { if (!add(intent.section)) remove(intent.section) }
                        val ordered = ExportSection.entries.filter { it in selected }
                        savedStateHandle[SECTIONS_KEY] = ArrayList(ordered.map { it.wireValue })
                        dispatch(StrengthExportReducerEvent.SectionsSelected(ordered))
                    }
                }

                StrengthExportIntent.GeneratePreview -> {
                    generate()
                }

                StrengthExportIntent.CopyPreview -> {
                    if (currentState.canCopy) dispatch(StrengthExportReducerEvent.CopyRequested)
                }

                StrengthExportIntent.ConsumeCopy -> {
                    dispatch(StrengthExportReducerEvent.CopyConsumed)
                }

                StrengthExportIntent.RefreshAvailability -> {
                    refreshAvailability()
                }

                StrengthExportIntent.ConsumeSessionEnded -> {
                    dispatch(StrengthExportReducerEvent.SessionEndConsumed)
                }
            }
        }

        private fun generate() {
            if (!currentState.canGenerate) return
            val format = currentState.format
            val sections = currentState.sections
            val requestGeneration = ++generation
            dispatch(StrengthExportReducerEvent.Generating)
            exportJob =
                viewModelScope.launch {
                    val result = exportStrengths(format, sections)
                    if (ended || requestGeneration != generation) return@launch
                    result.fold(
                        onSuccess = { dispatch(StrengthExportReducerEvent.Generated(it)) },
                        onFailure = { failure ->
                            report("export", failure)
                            if (failure is CoreDataFailure.Unauthorized) {
                                endSession()
                            } else {
                                dispatch(
                                    StrengthExportReducerEvent.Failed(failure.toFailureKind()),
                                )
                            }
                        },
                    )
                }
        }

        private fun refreshAvailability() {
            availabilityJob?.cancel()
            val requestGeneration = ++availabilityGeneration
            availabilityJob =
                viewModelScope.launch {
                    val result = experiences.getExperiences(limit = 1)
                    if (ended || requestGeneration != availabilityGeneration) return@launch
                    result.fold(
                        onSuccess = { if (!ended) dispatch(StrengthExportReducerEvent.AvailabilityLoaded(it.items.isNotEmpty())) },
                        onFailure = { failure ->
                            report("export_availability", failure)
                            if (failure is CoreDataFailure.Unauthorized) {
                                endSession()
                            } else if (!ended) {
                                dispatch(StrengthExportReducerEvent.AvailabilityLoaded(null))
                            }
                        },
                    )
                }
        }

        private fun endSession() {
            ended = true
            generation++
            exportJob?.cancel()
            availabilityJob?.cancel()
            savedStateHandle.remove<String>(FORMAT_KEY)
            savedStateHandle.remove<ArrayList<String>>(SECTIONS_KEY)
            dispatch(StrengthExportReducerEvent.SessionEnded)
        }

        private fun report(
            stage: String,
            failure: Throwable,
        ) {
            errorReporter.recordStagedFailure(stageKey = "foryou_stage", stage = stage, throwable = failure)
        }

        override fun reduce(
            state: StrengthExportUiState,
            event: StrengthExportReducerEvent,
        ): StrengthExportUiState =
            when (event) {
                is StrengthExportReducerEvent.FormatSelected -> {
                    state.copy(
                        format = event.format,
                        preview = null,
                        failure = null,
                        copyRequest = null,
                    )
                }

                is StrengthExportReducerEvent.SectionsSelected -> {
                    state.copy(
                        sections = event.sections,
                        preview = null,
                        failure = null,
                        copyRequest = null,
                    )
                }

                StrengthExportReducerEvent.Generating -> {
                    state.copy(isGenerating = true, preview = null, failure = null, copyRequest = null)
                }

                is StrengthExportReducerEvent.Generated -> {
                    state.copy(isGenerating = false, preview = event.export, failure = null)
                }

                is StrengthExportReducerEvent.Failed -> {
                    state.copy(isGenerating = false, failure = event.kind)
                }

                is StrengthExportReducerEvent.AvailabilityLoaded -> {
                    state.copy(hasExperiences = event.hasExperiences)
                }

                StrengthExportReducerEvent.CopyRequested -> {
                    state.copy(copyRequest = state.preview?.content)
                }

                StrengthExportReducerEvent.CopyConsumed -> {
                    state.copy(copyRequest = null)
                }

                StrengthExportReducerEvent.SessionEnded -> {
                    StrengthExportUiState(sessionEnded = true)
                }

                StrengthExportReducerEvent.SessionEndConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }
    }

private fun restoredState(handle: SavedStateHandle): StrengthExportUiState =
    StrengthExportUiState(
        format = ExportFormat.fromWireValue(handle.get<String>(FORMAT_KEY).orEmpty()) ?: ExportFormat.Markdown,
        sections =
            handle.get<ArrayList<String>>(SECTIONS_KEY)?.let { saved -> ExportSection.entries.filter { it.wireValue in saved } }
                ?: ExportSection.entries,
    )

private const val FORMAT_KEY = "export.format"
private const val SECTIONS_KEY = "export.sections"

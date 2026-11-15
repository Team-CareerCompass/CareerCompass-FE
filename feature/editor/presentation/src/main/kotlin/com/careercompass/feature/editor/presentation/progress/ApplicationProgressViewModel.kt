package com.careercompass.feature.editor.presentation.progress

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
import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.model.settled
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import com.careercompass.feature.editor.domain.usecase.ObserveApplicationDraftUseCase
import com.careercompass.feature.editor.presentation.setup.ApplicationDraftStarted
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

public data class ApplicationProgressUiState(
    val draft: ApplicationDraft? = null,
    val isRecovering: Boolean = false,
    val isStreaming: Boolean = false,
    val retryingIds: Set<Long> = emptySet(),
    val failure: FailureKind? = null,
    val interrupted: Boolean = false,
    val identityMismatch: Boolean = false,
    val sessionEnded: Boolean = false,
) : UiState

public sealed interface ApplicationProgressIntent : MviIntent {
    public data class Started(
        val value: ApplicationDraftStarted,
    ) : ApplicationProgressIntent

    public data object Resumed : ApplicationProgressIntent

    public data object Paused : ApplicationProgressIntent

    public data object Recover : ApplicationProgressIntent

    public data class RetryItem(
        val itemId: Long,
    ) : ApplicationProgressIntent

    public data object ConsumeSessionEnded : ApplicationProgressIntent
}

public sealed interface ApplicationProgressReducerEvent : ReducerEvent {
    public data class DraftChanged(
        val draft: ApplicationDraft,
    ) : ApplicationProgressReducerEvent

    public data class Loading(
        val recovering: Boolean,
        val streaming: Boolean,
    ) : ApplicationProgressReducerEvent

    public data class RetryChanged(
        val id: Long,
        val retrying: Boolean,
    ) : ApplicationProgressReducerEvent

    public data class Failed(
        val kind: FailureKind,
        val interrupted: Boolean = false,
    ) : ApplicationProgressReducerEvent

    public data object IdentityMismatch : ApplicationProgressReducerEvent

    public data object SessionEnded : ApplicationProgressReducerEvent

    public data object SessionEndConsumed : ApplicationProgressReducerEvent
}

/** Host 수명으로 받은 항목을 지킨다. 프로세스 복원은 canon의 동일 초안 POST 계약을 따른다. */
@HiltViewModel(assistedFactory = ApplicationProgressViewModel.Factory::class)
public class ApplicationProgressViewModel
    @AssistedInject
    constructor(
        @Assisted private val postingId: Long,
        private val repository: ApplicationRepository,
        private val observeDraft: ObserveApplicationDraftUseCase,
        private val reporter: ErrorReporter,
        private val handle: SavedStateHandle,
    ) : MviViewModel<ApplicationProgressIntent, ApplicationProgressUiState, ApplicationProgressReducerEvent>(ApplicationProgressUiState()) {
        @AssistedFactory public interface Factory {
            public fun create(postingId: Long): ApplicationProgressViewModel
        }

        private val identity = ProgressIdentity(handle)
        private var streamJob: Job? = null
        private var recoverJob: Job? = null
        private val retryJobs = mutableMapOf<Long, Job>()
        private val retryOverrides = mutableMapOf<Long, ApplicationItem>()
        private var generation = 0L
        private var resumed = false
        private var needsRecovery = identity.applicationId != null
        private var ended = false

        override fun onIntent(intent: ApplicationProgressIntent) {
            if (ended && intent != ApplicationProgressIntent.ConsumeSessionEnded) return
            when (intent) {
                is ApplicationProgressIntent.Started -> {
                    val started = intent.value
                    if (identity.applicationId != null || started.postingId != postingId) return
                    identity.save(started)
                    val draft = started.draft
                    dispatch(ApplicationProgressReducerEvent.DraftChanged(if (draft.hasLoadingItem) draft else draft.settled()))
                    if (resumed) startStream()
                }

                ApplicationProgressIntent.Resumed -> {
                    if (resumed) return
                    resumed = true
                    if (needsRecovery || currentState.draft == null) recover() else startStream()
                }

                ApplicationProgressIntent.Paused -> {
                    resumed = false
                    if (streamJob?.isActive == true || recoverJob?.isActive == true) {
                        needsRecovery = true
                        generation++
                        streamJob?.cancel()
                        recoverJob?.cancel()
                        dispatch(ApplicationProgressReducerEvent.Loading(recovering = false, streaming = false))
                    }
                }

                ApplicationProgressIntent.Recover -> {
                    recover()
                }

                is ApplicationProgressIntent.RetryItem -> {
                    retryItem(intent.itemId)
                }

                ApplicationProgressIntent.ConsumeSessionEnded -> {
                    dispatch(ApplicationProgressReducerEvent.SessionEndConsumed)
                }
            }
        }

        private fun recover() {
            val expectedId = identity.applicationId ?: return
            if (recoverJob?.isActive == true || retryJobs.values.any { it.isActive } || currentState.identityMismatch) return
            streamJob?.cancel()
            val request = ++generation
            dispatch(ApplicationProgressReducerEvent.Loading(recovering = true, streaming = false))
            recoverJob =
                viewModelScope.launch {
                    // GET /applications/{id} 미확정. 진행 중인 동일 id를 반환하는 canon 계약으로 복구한다.
                    val result = repository.createDraft(postingId, identity.tone, identity.items())
                    if (ended || request != generation) return@launch
                    result.fold(
                        onSuccess = { fresh ->
                            if (fresh.id != expectedId) {
                                // 서버가 새 초안을 만들었다면 그것을 기존 작업으로 위장하지 않는다.
                                dispatch(ApplicationProgressReducerEvent.IdentityMismatch)
                                return@fold
                            }
                            val received =
                                currentState.draft
                                    ?.items
                                    .orEmpty()
                                    .associateBy { it.id }
                            val merged =
                                fresh.copy(
                                    items =
                                        fresh.items.map { item ->
                                            received[item.id]?.takeIf {
                                                it.status == ApplicationItemStatus.Done &&
                                                    item.status != ApplicationItemStatus.Done
                                            }
                                                ?: item
                                        },
                                )
                            retryOverrides.clear()
                            needsRecovery = false
                            dispatch(ApplicationProgressReducerEvent.DraftChanged(if (merged.hasLoadingItem) merged else merged.settled()))
                            dispatch(ApplicationProgressReducerEvent.Loading(recovering = false, streaming = false))
                            if (resumed) startStream()
                        },
                        onFailure = { fail("progress_restore", it) },
                    )
                }
        }

        private fun startStream() {
            val draft = currentState.draft ?: return
            if (!draft.hasLoadingItem || streamJob?.isActive == true || ended) return
            val request = ++generation
            dispatch(ApplicationProgressReducerEvent.Loading(recovering = false, streaming = true))
            streamJob =
                viewModelScope.launch {
                    try {
                        observeDraft(draft)
                            .distinctUntilChanged()
                            .onEach { incoming ->
                                if (!ended && request == generation) {
                                    val previous =
                                        currentState.draft
                                            ?.items
                                            .orEmpty()
                                            .associateBy { it.id }
                                    val merged =
                                        incoming.copy(
                                            items =
                                                incoming.items.map { item ->
                                                    retryOverrides[item.id]
                                                        ?: previous[item.id]?.takeIf { item.id in currentState.retryingIds }
                                                        ?: item
                                                },
                                        )
                                    dispatch(ApplicationProgressReducerEvent.DraftChanged(merged))
                                }
                            }.takeWhile { it.hasLoadingItem && request == generation && !ended }
                            .collect { }
                        if (!ended && request == generation) {
                            currentState.draft?.let { dispatch(ApplicationProgressReducerEvent.DraftChanged(it.settled())) }
                            dispatch(ApplicationProgressReducerEvent.Loading(recovering = false, streaming = false))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        if (!ended && request == generation) {
                            needsRecovery = true
                            fail("progress_stream", failure, interrupted = true)
                        }
                    }
                }
        }

        private fun retryItem(id: Long) {
            val draft = currentState.draft ?: return
            val item = draft.items.firstOrNull { it.id == id } ?: return
            if (item.status != ApplicationItemStatus.Failed || currentState.isRecovering || id in currentState.retryingIds) return
            dispatch(ApplicationProgressReducerEvent.RetryChanged(id, true))
            retryJobs[id] =
                viewModelScope.launch {
                    val result = repository.regenerateItem(draft.id, id, identity.tone, emptyList())
                    if (ended) return@launch
                    dispatch(ApplicationProgressReducerEvent.RetryChanged(id, false))
                    result.fold(
                        onSuccess = { updated ->
                            if (updated.id != id) {
                                fail("progress_retry", IllegalStateException("Mismatched application item"))
                            } else {
                                retryOverrides[id] = updated
                                currentState.draft?.let { current ->
                                    val items = current.items.map { if (it.id == id) updated else it }
                                    val changed =
                                        current.copy(
                                            items = items,
                                            status =
                                                if (items.all {
                                                        it.status == ApplicationItemStatus.Done
                                                    }
                                                ) {
                                                    ApplicationStatus.Ready
                                                } else {
                                                    current.status
                                                },
                                        )
                                    dispatch(
                                        ApplicationProgressReducerEvent.DraftChanged(
                                            if (changed.hasLoadingItem) changed else changed.settled(),
                                        ),
                                    )
                                }
                                if (updated.status == ApplicationItemStatus.Loading || needsRecovery) {
                                    needsRecovery = true
                                    if (resumed) {
                                        // 비동기 재생성 응답이면 현재 상태를 다시 읽어 그 항목의 스트림을 연다.
                                        retryJobs.remove(id)
                                        recover()
                                    }
                                }
                            }
                        },
                        onFailure = { fail("progress_retry", it) },
                    )
                }
        }

        private fun fail(
            stage: String,
            failure: Throwable,
            interrupted: Boolean = false,
        ) {
            reporter.recordStagedFailure(stageKey = "editor_stage", stage = stage, throwable = failure)
            if (failure is CoreDataFailure.Unauthorized) {
                ended = true
                generation++
                streamJob?.cancel()
                recoverJob?.cancel()
                retryJobs.values.forEach { it.cancel() }
                identity.clear()
                dispatch(ApplicationProgressReducerEvent.SessionEnded)
            } else {
                val kind = if (failure is EditorFailure.StreamInterrupted) FailureKind.NoConnection else failure.toFailureKind()
                dispatch(ApplicationProgressReducerEvent.Failed(kind, interrupted))
            }
        }

        override fun reduce(
            state: ApplicationProgressUiState,
            event: ApplicationProgressReducerEvent,
        ): ApplicationProgressUiState =
            when (event) {
                is ApplicationProgressReducerEvent.DraftChanged -> {
                    state.copy(draft = event.draft)
                }

                is ApplicationProgressReducerEvent.Loading -> {
                    state.copy(isRecovering = event.recovering, isStreaming = event.streaming, failure = null, interrupted = false)
                }

                is ApplicationProgressReducerEvent.RetryChanged -> {
                    state.copy(
                        retryingIds =
                            if (event.retrying) {
                                state.retryingIds + event.id
                            } else {
                                state.retryingIds -
                                    event.id
                            },
                        failure = if (event.retrying) null else state.failure,
                    )
                }

                is ApplicationProgressReducerEvent.Failed -> {
                    state.copy(
                        isRecovering = false,
                        isStreaming = if (event.interrupted) false else state.isStreaming,
                        failure = event.kind,
                        interrupted = event.interrupted,
                    )
                }

                ApplicationProgressReducerEvent.IdentityMismatch -> {
                    state.copy(isRecovering = false, isStreaming = false, identityMismatch = true)
                }

                ApplicationProgressReducerEvent.SessionEnded -> {
                    ApplicationProgressUiState(sessionEnded = true)
                }

                ApplicationProgressReducerEvent.SessionEndConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }
    }

/** 서버 결과는 저장하지 않는다. 같은 생성 요청을 복구할 식별자·사용자가 확정한 입력만 Bundle에 둔다. */
internal class ProgressIdentity(
    private val handle: SavedStateHandle,
) {
    val applicationId: Long? get() = handle[ID]
    val tone: ApplicationTone get() = ApplicationTone.fromWireValue(handle.get<String>(TONE).orEmpty()) ?: ApplicationTone.Formal

    fun save(started: ApplicationDraftStarted) {
        handle[ID] = started.draft.id
        handle[TONE] = started.tone.wireValue
        started.requestItems?.let { items ->
            handle[QUESTIONS] = ArrayList(items.map { it.question })
            handle[LIMITS] = items.map { it.maxChars ?: -1 }.toIntArray()
        }
    }

    fun items(): List<ApplicationItemDraft>? {
        val questions = handle.get<ArrayList<String>>(QUESTIONS) ?: return null
        val limits = handle.get<IntArray>(LIMITS) ?: return null
        if (questions.size != limits.size) return null
        return questions.mapIndexed { index, question -> ApplicationItemDraft(index + 1, question, limits[index].takeIf { it > 0 }) }
    }

    fun clear() {
        handle.remove<Long>(ID)
        handle.remove<String>(TONE)
        handle.remove<ArrayList<String>>(QUESTIONS)
        handle.remove<IntArray>(LIMITS)
    }

    private companion object {
        const val ID = "editor.progress.id"
        const val TONE = "editor.progress.tone"
        const val QUESTIONS = "editor.progress.questions"
        const val LIMITS = "editor.progress.limits"
    }
}

package com.careercompass.feature.editor.presentation.setup

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemRules
import com.careercompass.feature.editor.domain.model.ApplicationSetup
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.model.renumbered
import com.careercompass.feature.editor.domain.usecase.CreateApplicationDraftUseCase
import com.careercompass.feature.editor.domain.usecase.GetApplicationSetupUseCase
import com.careercompass.feature.editor.presentation.reporting.EditorFailureStage
import com.careercompass.feature.editor.presentation.reporting.recordEditorFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 문항 확인 화면이 그리는 값.
 *
 * @property recognizedItems 공고가 인식한 원본. [items] 와 견줘 「손봤는가」를 판정하므로 화면이 고쳐도
 *   이 값은 그대로 둔다 — 그 판정이 요청에 문항을 실을지 정한다.
 */
public data class ApplicationSetupUiState(
    val postingTitle: String = "",
    val items: List<ApplicationItemDraft> = emptyList(),
    val recognizedItems: List<ApplicationItemDraft> = emptyList(),
    val tone: ApplicationTone = ApplicationTone.Formal,
    val isLoading: Boolean = false,
    val loadFailure: FailureKind? = null,
    val isCreating: Boolean = false,
    val itemEditor: ApplicationItemEditorState? = null,
    val message: ApplicationSetupMessage? = null,
    val draftStarted: ApplicationDraftStarted? = null,
    val sessionEnd: ApplicationSetupSessionEnd? = null,
) : UiState {
    val isInitialLoading: Boolean get() = postingTitle.isEmpty() && loadFailure == null && isLoading

    val isFailureVisible: Boolean get() = postingTitle.isEmpty() && loadFailure != null

    /** 공고에서 문항을 하나도 못 찾았다 — 실패가 아니라 직접 쓰는 자리다(F4-1). */
    val isEmptyRecognition: Boolean get() = !isInitialLoading && loadFailure == null && recognizedItems.isEmpty()

    /** 문항이 하나도 없으면 초안을 만들 수 없다. */
    val canStart: Boolean get() = items.isNotEmpty() && !isCreating

    val isLimitReached: Boolean get() = items.size >= ApplicationItemRules.MAX_ITEMS

    /** 글자 수 제한을 못 찾은 문항이 있는가 — 있으면 기본 길이 안내를 미리 보인다(F4-2). */
    val hasUnboundedItem: Boolean get() = items.any { it.maxChars == null }
}

public sealed interface ApplicationSetupIntent : MviIntent {
    public data class Screen(
        val event: ApplicationSetupEvent,
    ) : ApplicationSetupIntent

    public data class ItemEditor(
        val event: ApplicationItemEditorEvent,
    ) : ApplicationSetupIntent

    public data object ConsumeMessage : ApplicationSetupIntent

    public data object ConsumeDraftStarted : ApplicationSetupIntent

    public data object ConsumeSessionEnded : ApplicationSetupIntent
}

public sealed interface ApplicationSetupReducerEvent : ReducerEvent {
    public data object LoadStarted : ApplicationSetupReducerEvent

    public data class Loaded(
        val setup: ApplicationSetup,
    ) : ApplicationSetupReducerEvent

    public data class LoadFailed(
        val kind: FailureKind,
    ) : ApplicationSetupReducerEvent

    public data class ToneChanged(
        val tone: ApplicationTone,
    ) : ApplicationSetupReducerEvent

    public data class ItemsChanged(
        val items: List<ApplicationItemDraft>,
    ) : ApplicationSetupReducerEvent

    /** null 이면 시트를 닫는다. */
    public data class ItemEditorChanged(
        val editor: ApplicationItemEditorState?,
    ) : ApplicationSetupReducerEvent

    public data object CreateStarted : ApplicationSetupReducerEvent

    public data object CreateFinished : ApplicationSetupReducerEvent

    public data class DraftStarted(
        val started: ApplicationDraftStarted,
    ) : ApplicationSetupReducerEvent

    public data class MessageRaised(
        val message: ApplicationSetupMessage,
    ) : ApplicationSetupReducerEvent

    public data object SessionEnded : ApplicationSetupReducerEvent

    public data object MessageConsumed : ApplicationSetupReducerEvent

    public data object DraftStartedConsumed : ApplicationSetupReducerEvent

    public data object SessionEndedConsumed : ApplicationSetupReducerEvent
}

/**
 * 지원서 작성 첫 화면(#183) — 공고에서 인식한 문항을 확인받고 초안 생성을 시작한다(F4-1).
 *
 * ### 인식 결과가 없는 것은 실패가 아니다
 * 공고에서 문항을 하나도 못 찾으면 「항목을 못 찾았습니다」로 끝내지 않는다. 그러면 그 공고는 영영 초안을
 * 못 만든다. 명세가 정한 처리대로 직접 쓰는 자리를 열어 준다. 파싱이 아직 안 끝난 공고(`parsed` 가 null)도
 * 같은 길이다 — 기다리라고 막으면 사용자가 할 수 있는 일이 없다.
 *
 * ### 고친 문항만 서버로 간다
 * §6 의 `POST /applications` 에는 문항을 받는 자리가 없다(`docs/spec/canon.md` 「지원서 문항 확정의 계약」).
 * 그래서 인식 결과를 그대로 쓴 요청은 지금 계약과 한 글자도 다르지 않게 보내고, 사용자가 손봤을 때만 문항을
 * 실어 보낸다. 그 판정은 [CreateApplicationDraftUseCase] 가 갖는다 — 화면이 정하면 다른 진입점이 생겼을 때
 * 규칙이 두 벌이 된다.
 *
 * ### 마지막 문항은 지울 수 없다
 * 문항이 0개면 초안을 만들 수 없어 화면이 막다른 길이 된다. 지우기 대신 고치기를 하라고 알린다.
 */
@HiltViewModel(assistedFactory = ApplicationSetupViewModel.Factory::class)
public class ApplicationSetupViewModel
    @AssistedInject
    constructor(
        @Assisted private val postingId: Long,
        private val getApplicationSetup: GetApplicationSetupUseCase,
        private val createApplicationDraft: CreateApplicationDraftUseCase,
        private val errorReporter: ErrorReporter,
    ) : MviViewModel<ApplicationSetupIntent, ApplicationSetupUiState, ApplicationSetupReducerEvent>(
            ApplicationSetupUiState(),
        ) {
        /** 공고 id 는 앱 셸의 Nav3 키가 나른다 — entry 가 이 팩토리로 넘긴다. */
        @AssistedFactory
        public interface Factory {
            public fun create(postingId: Long): ApplicationSetupViewModel
        }

        private var loadJob: Job? = null
        private var createJob: Job? = null

        init {
            load()
        }

        override fun onIntent(intent: ApplicationSetupIntent) {
            when (intent) {
                is ApplicationSetupIntent.Screen -> {
                    onEvent(intent.event)
                }

                is ApplicationSetupIntent.ItemEditor -> {
                    onEditorEvent(intent.event)
                }

                ApplicationSetupIntent.ConsumeMessage -> {
                    dispatch(ApplicationSetupReducerEvent.MessageConsumed)
                }

                ApplicationSetupIntent.ConsumeDraftStarted -> {
                    dispatch(ApplicationSetupReducerEvent.DraftStartedConsumed)
                }

                ApplicationSetupIntent.ConsumeSessionEnded -> {
                    dispatch(ApplicationSetupReducerEvent.SessionEndedConsumed)
                }
            }
        }

        override fun reduce(
            state: ApplicationSetupUiState,
            event: ApplicationSetupReducerEvent,
        ): ApplicationSetupUiState =
            when (event) {
                ApplicationSetupReducerEvent.LoadStarted -> {
                    state.copy(isLoading = true, loadFailure = null)
                }

                is ApplicationSetupReducerEvent.Loaded -> {
                    state.copy(
                        postingTitle = event.setup.postingTitle,
                        items = event.setup.recognizedItems,
                        recognizedItems = event.setup.recognizedItems,
                        isLoading = false,
                        loadFailure = null,
                    )
                }

                is ApplicationSetupReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, loadFailure = event.kind)
                }

                is ApplicationSetupReducerEvent.ToneChanged -> {
                    state.copy(tone = event.tone)
                }

                is ApplicationSetupReducerEvent.ItemsChanged -> {
                    state.copy(items = event.items)
                }

                is ApplicationSetupReducerEvent.ItemEditorChanged -> {
                    state.copy(itemEditor = event.editor)
                }

                ApplicationSetupReducerEvent.CreateStarted -> {
                    state.copy(isCreating = true)
                }

                ApplicationSetupReducerEvent.CreateFinished -> {
                    state.copy(isCreating = false)
                }

                is ApplicationSetupReducerEvent.DraftStarted -> {
                    state.copy(isCreating = false, draftStarted = event.started)
                }

                is ApplicationSetupReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                ApplicationSetupReducerEvent.SessionEnded -> {
                    state.copy(isCreating = false, sessionEnd = ApplicationSetupSessionEnd)
                }

                ApplicationSetupReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                ApplicationSetupReducerEvent.DraftStartedConsumed -> {
                    state.copy(draftStarted = null)
                }

                ApplicationSetupReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnd = null)
                }
            }

        private fun onEvent(event: ApplicationSetupEvent) {
            when (event) {
                is ApplicationSetupEvent.ToneSelected -> dispatch(ApplicationSetupReducerEvent.ToneChanged(event.tone))
                is ApplicationSetupEvent.ItemEditClicked -> openEditor(event.order)
                is ApplicationSetupEvent.ItemDeleteClicked -> deleteItem(event.order)
                ApplicationSetupEvent.ItemAddClicked -> openEditor(null)
                ApplicationSetupEvent.StartClicked -> create()
                ApplicationSetupEvent.RetryClicked -> load()
                ApplicationSetupEvent.BackClicked -> Unit
            }
        }

        private fun onEditorEvent(event: ApplicationItemEditorEvent) {
            val editor = currentState.itemEditor ?: return
            when (event) {
                is ApplicationItemEditorEvent.QuestionChanged -> {
                    dispatch(
                        ApplicationSetupReducerEvent.ItemEditorChanged(
                            editor.copy(question = event.value, questionError = null),
                        ),
                    )
                }

                is ApplicationItemEditorEvent.MaxCharsChanged -> {
                    dispatch(
                        ApplicationSetupReducerEvent.ItemEditorChanged(
                            editor.copy(maxChars = event.value, maxCharsError = null),
                        ),
                    )
                }

                ApplicationItemEditorEvent.Submitted -> {
                    submitEditor(editor)
                }

                ApplicationItemEditorEvent.Dismissed -> {
                    dispatch(ApplicationSetupReducerEvent.ItemEditorChanged(null))
                }
            }
        }

        private fun openEditor(order: Int?) {
            if (order == null && currentState.isLimitReached) {
                dispatch(ApplicationSetupReducerEvent.MessageRaised(ApplicationSetupMessage.LimitReached))
                return
            }
            val target = order?.let { value -> currentState.items.firstOrNull { it.order == value } }
            dispatch(
                ApplicationSetupReducerEvent.ItemEditorChanged(
                    ApplicationItemEditorState(
                        order = target?.order,
                        question = target?.question.orEmpty(),
                        maxChars = target?.maxChars?.toString().orEmpty(),
                    ),
                ),
            )
        }

        private fun submitEditor(editor: ApplicationItemEditorState) {
            val validated =
                editor.copy(
                    questionError = ApplicationItemRules.validateQuestion(editor.question),
                    maxCharsError = ApplicationItemRules.validateMaxChars(editor.maxChars),
                )
            if (validated.hasErrors) {
                dispatch(ApplicationSetupReducerEvent.ItemEditorChanged(validated))
                return
            }

            val question = editor.question.trim()
            val maxChars = ApplicationItemRules.parseMaxChars(editor.maxChars)
            val items = currentState.items
            val next =
                if (editor.isNew) {
                    items + ApplicationItemDraft(order = items.size + 1, question = question, maxChars = maxChars)
                } else {
                    items.map { if (it.order == editor.order) it.copy(question = question, maxChars = maxChars) else it }
                }
            dispatch(ApplicationSetupReducerEvent.ItemsChanged(next.renumbered()))
            dispatch(ApplicationSetupReducerEvent.ItemEditorChanged(null))
        }

        private fun deleteItem(order: Int) {
            val items = currentState.items
            if (items.size <= 1) {
                dispatch(ApplicationSetupReducerEvent.MessageRaised(ApplicationSetupMessage.LastItemKept))
                return
            }
            dispatch(ApplicationSetupReducerEvent.ItemsChanged(items.filterNot { it.order == order }.renumbered()))
        }

        private fun load() {
            loadJob?.cancel()
            dispatch(ApplicationSetupReducerEvent.LoadStarted)
            loadJob =
                viewModelScope.launch {
                    getApplicationSetup(postingId)
                        .onSuccess { dispatch(ApplicationSetupReducerEvent.Loaded(it)) }
                        .onFailure { throwable ->
                            errorReporter.recordEditorFailure(EditorFailureStage.SetupLoad, throwable)
                            if (throwable is CoreDataFailure.Unauthorized) {
                                dispatch(ApplicationSetupReducerEvent.SessionEnded)
                            } else {
                                dispatch(ApplicationSetupReducerEvent.LoadFailed(throwable.toFailureKind()))
                            }
                        }
                }
        }

        private fun create() {
            if (!currentState.canStart) return
            createJob?.cancel()
            val state = currentState
            dispatch(ApplicationSetupReducerEvent.CreateStarted)
            createJob =
                viewModelScope.launch {
                    createApplicationDraft(
                        postingId = postingId,
                        tone = state.tone,
                        items = state.items,
                        recognizedItems = state.recognizedItems,
                    ).onSuccess { draft ->
                        dispatch(
                            ApplicationSetupReducerEvent.DraftStarted(
                                ApplicationDraftStarted(applicationId = draft.id, postingId = postingId),
                            ),
                        )
                    }.onFailure { throwable ->
                        errorReporter.recordEditorFailure(EditorFailureStage.DraftCreate, throwable)
                        dispatch(ApplicationSetupReducerEvent.CreateFinished)
                        if (throwable is CoreDataFailure.Unauthorized) {
                            dispatch(ApplicationSetupReducerEvent.SessionEnded)
                        } else {
                            dispatch(ApplicationSetupReducerEvent.MessageRaised(ApplicationSetupMessage.CreateFailed))
                        }
                    }
                }
        }
    }

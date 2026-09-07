package com.careercompass.feature.profile.presentation.pastapplication

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.PastApplicationLabelRules
import com.careercompass.core.model.application.PastApplicationTextUploadException
import com.careercompass.core.model.application.PastApplicationTextUploadFailure
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.component.DirectInputEvent
import com.careercompass.core.ui.component.DirectInputState
import com.careercompass.core.ui.component.PastApplicationItemCategoryState
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.profile.domain.usecase.DeletePastApplicationUseCase
import com.careercompass.feature.profile.domain.usecase.GetPastApplicationsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdatePastApplicationItemCategoryUseCase
import com.careercompass.feature.profile.domain.usecase.UploadPastApplicationTextUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import com.careercompass.feature.profile.presentation.reporting.ProfileFailureStage
import com.careercompass.feature.profile.presentation.reporting.recordProfileFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 본문 미리보기 길이 — 어떤 항목을 고치는지 알아볼 만큼만 보인다. */
private const val CONTENT_PREVIEW_LENGTH = 60

/**
 * 과거 지원서 목록이 그리는 값.
 *
 * @property expandedId 펼친 지원서. 항목은 펼쳤을 때만 그린다 — 열 장이 각각 항목 대여섯을 펴면
 *   목록이 스크롤 벽이 되고, 정작 고쳐야 할 항목을 찾기 어렵다.
 */
public data class PastApplicationListUiState(
    val applications: List<PastApplication> = emptyList(),
    val expandedId: Long? = null,
    val isLoading: Boolean = false,
    val loadFailure: FailureKind? = null,
    val categoryEditor: PastApplicationItemCategoryState? = null,
    val pendingDeletion: PastApplicationDeleteTarget? = null,
    val isDeleting: Boolean = false,
    val message: PastApplicationListMessage? = null,
    val directInput: DirectInputState? = null,
    val sessionEnd: ProfileSessionEnd? = null,
) : UiState {
    val isInitialLoading: Boolean get() = applications.isEmpty() && loadFailure == null && isLoading

    val isFailureVisible: Boolean get() = applications.isEmpty() && loadFailure != null

    val isEmpty: Boolean get() = applications.isEmpty() && !isLoading && loadFailure == null

    /** 페이징이 없으므로 목록 길이가 곧 전체다 — 상한 판정에 모르는 구석이 없다. */
    val isLimitReached: Boolean get() = applications.size >= MAX_PAST_APPLICATIONS

    val count: Int get() = applications.size
}

public sealed interface PastApplicationListIntent : MviIntent {
    public data class Screen(
        val event: PastApplicationListEvent,
    ) : PastApplicationListIntent

    /** 분류 시트가 고른 값. */
    public data class CategorySelected(
        val category: PastApplicationCategory,
    ) : PastApplicationListIntent

    public data object DismissCategoryEditor : PastApplicationListIntent

    public data object ConfirmDelete : PastApplicationListIntent

    public data object DismissDelete : PastApplicationListIntent

    public data object Refresh : PastApplicationListIntent

    public data object ConsumeMessage : PastApplicationListIntent

    /** 직접 작성 시트가 보내는 것. */
    public data class DirectInput(
        val event: DirectInputEvent,
    ) : PastApplicationListIntent

    public data object ConsumeSessionEnded : PastApplicationListIntent
}

public sealed interface PastApplicationListReducerEvent : ReducerEvent {
    public data object LoadStarted : PastApplicationListReducerEvent

    public data class Loaded(
        val applications: List<PastApplication>,
    ) : PastApplicationListReducerEvent

    public data class LoadFailed(
        val kind: FailureKind,
    ) : PastApplicationListReducerEvent

    public data class ExpansionChanged(
        val id: Long?,
    ) : PastApplicationListReducerEvent

    /** null 이면 시트를 닫는다. */
    public data class CategoryEditorChanged(
        val editor: PastApplicationItemCategoryState?,
    ) : PastApplicationListReducerEvent

    /** 고친 항목 하나만 갈아 끼운다 — 목록 전체를 다시 읽지 않는다. */
    public data class ItemCategoryUpdated(
        val applicationId: Long,
        val item: PastApplicationItem,
    ) : PastApplicationListReducerEvent

    public data class DeletionRequested(
        val target: PastApplicationDeleteTarget?,
    ) : PastApplicationListReducerEvent

    public data object DeleteStarted : PastApplicationListReducerEvent

    public data object DeleteFinished : PastApplicationListReducerEvent

    public data class MessageRaised(
        val message: PastApplicationListMessage,
    ) : PastApplicationListReducerEvent

    /** null 이면 시트를 닫는다. */
    public data class DirectInputChanged(
        val input: DirectInputState?,
    ) : PastApplicationListReducerEvent

    public data class SessionEnded(
        val cause: ProfileSessionEnd,
    ) : PastApplicationListReducerEvent

    public data object MessageConsumed : PastApplicationListReducerEvent

    public data object SessionEndedConsumed : PastApplicationListReducerEvent
}

/**
 * 과거 지원서 목록(#180) — 올린 지원서를 다시 보고, 분류를 고치고, 지운다.
 *
 * ### 서버 계약에 없어서 못 하는 두 가지
 * 이슈는 「파일 형식 배지」와 「라벨 수정」도 적었지만 둘 다 계약에 없다.
 * - **형식 배지** — `GET /past-applications` 의 응답에 파일 이름도 형식도 없다(`PastApplication` 은
 *   id · label · items · createdAt 뿐이다). 라벨에서 확장자를 읽어 배지를 만드는 길이 있지만, 라벨은
 *   사용자가 붙이는 아무 문장이라 「2024 카카오.pdf 지원서」 같은 값에서 엉뚱한 형식을 읽는다 —
 *   그 함정은 #121 이 이미 밟았고, 여기서 다시 밟지 않는다.
 * - **라벨 수정** — §4 에 라벨을 고치는 엔드포인트가 없다. 업로드 요청 필드로만 받는다.
 *
 * 둘 다 서버가 필드·엔드포인트를 주면 그때 붙인다. 판정은 `docs/spec/canon.md` 의 「서버 계약에 없어서
 * 화면이 못 하는 것」 표에 적었다.
 *
 * ### 분류 변경은 항목 하나만 갈아 낀다
 * 서버가 고친 항목을 돌려주므로 목록을 다시 읽지 않는다. 다시 읽으면 펼침 상태와 스크롤이 함께 흔들린다.
 */
@HiltViewModel
public class PastApplicationListViewModel
    @Inject
    constructor(
        private val getPastApplications: GetPastApplicationsUseCase,
        private val updateItemCategory: UpdatePastApplicationItemCategoryUseCase,
        private val deletePastApplication: DeletePastApplicationUseCase,
        private val uploadPastApplicationText: UploadPastApplicationTextUseCase,
        private val errorReporter: ErrorReporter,
    ) : MviViewModel<PastApplicationListIntent, PastApplicationListUiState, PastApplicationListReducerEvent>(
            PastApplicationListUiState(),
        ) {
        private var loadJob: Job? = null
        private var categoryJob: Job? = null
        private var deleteJob: Job? = null
        private var uploadJob: Job? = null

        init {
            load()
        }

        override fun onIntent(intent: PastApplicationListIntent) {
            when (intent) {
                is PastApplicationListIntent.Screen -> {
                    onEvent(intent.event)
                }

                is PastApplicationListIntent.CategorySelected -> {
                    selectCategory(intent.category)
                }

                PastApplicationListIntent.DismissCategoryEditor -> {
                    dispatch(PastApplicationListReducerEvent.CategoryEditorChanged(null))
                }

                PastApplicationListIntent.ConfirmDelete -> {
                    confirmDelete()
                }

                PastApplicationListIntent.DismissDelete -> {
                    dispatch(PastApplicationListReducerEvent.DeletionRequested(null))
                }

                PastApplicationListIntent.Refresh -> {
                    load()
                }

                PastApplicationListIntent.ConsumeMessage -> {
                    dispatch(PastApplicationListReducerEvent.MessageConsumed)
                }

                is PastApplicationListIntent.DirectInput -> {
                    onDirectInputEvent(intent.event)
                }

                PastApplicationListIntent.ConsumeSessionEnded -> {
                    dispatch(PastApplicationListReducerEvent.SessionEndedConsumed)
                }
            }
        }

        override fun reduce(
            state: PastApplicationListUiState,
            event: PastApplicationListReducerEvent,
        ): PastApplicationListUiState =
            when (event) {
                PastApplicationListReducerEvent.LoadStarted -> {
                    state.copy(isLoading = true, loadFailure = null)
                }

                is PastApplicationListReducerEvent.Loaded -> {
                    state.copy(
                        applications = event.applications,
                        isLoading = false,
                        loadFailure = null,
                        // 사라진 지원서를 펼친 채로 두지 않는다.
                        expandedId = state.expandedId?.takeIf { id -> event.applications.any { it.id == id } },
                    )
                }

                is PastApplicationListReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, loadFailure = event.kind)
                }

                is PastApplicationListReducerEvent.ExpansionChanged -> {
                    state.copy(expandedId = event.id)
                }

                is PastApplicationListReducerEvent.CategoryEditorChanged -> {
                    state.copy(categoryEditor = event.editor)
                }

                is PastApplicationListReducerEvent.ItemCategoryUpdated -> {
                    state.copy(
                        categoryEditor = null,
                        applications =
                            state.applications.map { application ->
                                if (application.id != event.applicationId) {
                                    application
                                } else {
                                    application.copy(
                                        items = application.items.map { if (it.id == event.item.id) event.item else it },
                                    )
                                }
                            },
                    )
                }

                is PastApplicationListReducerEvent.DeletionRequested -> {
                    state.copy(pendingDeletion = event.target)
                }

                PastApplicationListReducerEvent.DeleteStarted -> {
                    state.copy(isDeleting = true)
                }

                PastApplicationListReducerEvent.DeleteFinished -> {
                    state.copy(isDeleting = false, pendingDeletion = null)
                }

                is PastApplicationListReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                is PastApplicationListReducerEvent.DirectInputChanged -> {
                    state.copy(directInput = event.input)
                }

                is PastApplicationListReducerEvent.SessionEnded -> {
                    state.copy(isLoading = false, isDeleting = false, sessionEnd = event.cause)
                }

                PastApplicationListReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                PastApplicationListReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnd = null)
                }
            }

        private fun onEvent(event: PastApplicationListEvent) {
            when (event) {
                is PastApplicationListEvent.ApplicationToggled -> {
                    // 같은 것을 다시 누르면 접는다 — 한 번에 하나만 펼친다.
                    dispatch(
                        PastApplicationListReducerEvent.ExpansionChanged(
                            event.id.takeIf { it != currentState.expandedId },
                        ),
                    )
                }

                is PastApplicationListEvent.ItemCategoryClicked -> {
                    openCategoryEditor(event.applicationId, event.itemId)
                }

                is PastApplicationListEvent.DeleteClicked -> {
                    val target = currentState.applications.firstOrNull { it.id == event.id } ?: return
                    dispatch(
                        PastApplicationListReducerEvent.DeletionRequested(
                            PastApplicationDeleteTarget(id = target.id, label = target.label),
                        ),
                    )
                }

                PastApplicationListEvent.AddClicked -> {
                    if (currentState.isLimitReached) {
                        dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.LimitReached))
                    } else {
                        dispatch(PastApplicationListReducerEvent.DirectInputChanged(DirectInputState()))
                    }
                }

                PastApplicationListEvent.RetryClicked -> {
                    load()
                }

                // 뒤로 가기는 목적지 결정이라 Screen 의 콜백이 갖는다.
                PastApplicationListEvent.BackClicked -> {
                    Unit
                }
            }
        }

        /**
         * 직접 작성 — 라벨과 본문을 받아 TXT 지원서로 올린다(#181).
         *
         * 판단과 변환은 `core:model` 이 갖는다(`pastApplicationTextUpload`) — 온보딩 Step 4 의 같은 입력이
         * 같은 함수를 지난다. 업로드가 실패하면 **시트를 닫지 않는다**: 쓰던 글을 버리면 다시 써야 한다.
         */
        private fun onDirectInputEvent(event: DirectInputEvent) {
            val input = currentState.directInput ?: return
            when (event) {
                is DirectInputEvent.LabelChanged -> {
                    dispatch(PastApplicationListReducerEvent.DirectInputChanged(input.copy(label = event.value, labelError = null)))
                }

                is DirectInputEvent.ContentChanged -> {
                    dispatch(PastApplicationListReducerEvent.DirectInputChanged(input.copy(content = event.value, contentError = null)))
                }

                DirectInputEvent.Submitted -> {
                    submitDirectInput(input)
                }

                DirectInputEvent.Dismissed -> {
                    if (!input.isSubmitting) dispatch(PastApplicationListReducerEvent.DirectInputChanged(null))
                }
            }
        }

        private fun submitDirectInput(input: DirectInputState) {
            if (uploadJob?.isActive == true) return
            dispatch(PastApplicationListReducerEvent.DirectInputChanged(input.copy(isSubmitting = true)))
            uploadJob =
                viewModelScope.launch {
                    uploadPastApplicationText(label = input.label, content = input.content)
                        .onSuccess {
                            dispatch(PastApplicationListReducerEvent.DirectInputChanged(null))
                            dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.Uploaded))
                            load()
                        }.onFailure { cause -> onUploadFailure(input, cause) }
                }
        }

        private fun onUploadFailure(
            input: DirectInputState,
            cause: Throwable,
        ) {
            val validationFailure = (cause as? PastApplicationTextUploadException)?.failure
            if (validationFailure != null) {
                // 라벨과 본문의 오류를 한 번에 보인다 — 하나씩 알리면 제출을 두 번 눌러야 두 칸이 다 빨개진다.
                val next =
                    when (validationFailure) {
                        is PastApplicationTextUploadFailure.InvalidLabel,
                        PastApplicationTextUploadFailure.EmptyContent,
                        -> {
                            input.copy(
                                isSubmitting = false,
                                labelError = PastApplicationLabelRules.validate(input.label),
                                contentError = if (input.content.isBlank()) ProfileFieldViolation.Required else null,
                            )
                        }

                        PastApplicationTextUploadFailure.TooLarge -> {
                            dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.UploadTooLarge))
                            input.copy(isSubmitting = false)
                        }
                    }
                dispatch(PastApplicationListReducerEvent.DirectInputChanged(next))
                return
            }
            errorReporter.recordProfileFailure(ProfileFailureStage.PastApplicationUpload, cause)
            dispatch(PastApplicationListReducerEvent.DirectInputChanged(input.copy(isSubmitting = false)))
            if (cause is CoreDataFailure.Unauthorized) {
                dispatch(PastApplicationListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
            } else if (cause is CoreDataFailure.LimitExceeded) {
                dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.LimitReached))
            } else {
                dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.UploadFailed))
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            dispatch(PastApplicationListReducerEvent.LoadStarted)
            loadJob =
                viewModelScope.launch {
                    getPastApplications()
                        .onSuccess { dispatch(PastApplicationListReducerEvent.Loaded(it)) }
                        .onFailure(::onLoadFailure)
                }
        }

        private fun openCategoryEditor(
            applicationId: Long,
            itemId: Long,
        ) {
            val application = currentState.applications.firstOrNull { it.id == applicationId } ?: return
            val item = application.items.firstOrNull { it.id == itemId } ?: return
            dispatch(
                PastApplicationListReducerEvent.CategoryEditorChanged(
                    PastApplicationItemCategoryState(
                        documentId = applicationId.toString(),
                        itemId = itemId,
                        contentPreview = item.content.take(CONTENT_PREVIEW_LENGTH),
                        selected = item.category,
                    ),
                ),
            )
        }

        /** 고른 값이 원래 값과 같으면 왕복하지 않고 시트만 닫는다. */
        private fun selectCategory(category: PastApplicationCategory) {
            val editor = currentState.categoryEditor ?: return
            if (categoryJob?.isActive == true) return
            if (editor.selected == category) {
                dispatch(PastApplicationListReducerEvent.CategoryEditorChanged(null))
                return
            }
            val applicationId = editor.documentId.toLongOrNull() ?: return
            categoryJob =
                viewModelScope.launch {
                    updateItemCategory(applicationId, editor.itemId, category)
                        .onSuccess { item ->
                            dispatch(PastApplicationListReducerEvent.ItemCategoryUpdated(applicationId, item))
                        }.onFailure { cause ->
                            errorReporter.recordProfileFailure(ProfileFailureStage.PastApplicationCategory, cause)
                            dispatch(PastApplicationListReducerEvent.CategoryEditorChanged(null))
                            if (cause is CoreDataFailure.Unauthorized) {
                                dispatch(PastApplicationListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                            } else {
                                dispatch(
                                    PastApplicationListReducerEvent.MessageRaised(
                                        PastApplicationListMessage.CategoryUpdateFailed,
                                    ),
                                )
                            }
                        }
                }
        }

        private fun confirmDelete() {
            if (deleteJob?.isActive == true) return
            val id = currentState.pendingDeletion?.id ?: return
            dispatch(PastApplicationListReducerEvent.DeleteStarted)
            deleteJob =
                viewModelScope.launch {
                    deletePastApplication(id)
                        .onSuccess {
                            dispatch(PastApplicationListReducerEvent.DeleteFinished)
                            dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.Deleted))
                            load()
                        }.onFailure { cause ->
                            dispatch(PastApplicationListReducerEvent.DeleteFinished)
                            errorReporter.recordProfileFailure(ProfileFailureStage.PastApplicationDelete, cause)
                            if (cause is CoreDataFailure.Unauthorized) {
                                dispatch(PastApplicationListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                            } else {
                                dispatch(PastApplicationListReducerEvent.MessageRaised(PastApplicationListMessage.DeleteFailed))
                            }
                        }
                }
        }

        private fun onLoadFailure(cause: Throwable) {
            errorReporter.recordProfileFailure(ProfileFailureStage.PastApplicationList, cause)
            if (cause is CoreDataFailure.Unauthorized) {
                dispatch(PastApplicationListReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
            } else {
                dispatch(PastApplicationListReducerEvent.LoadFailed(cause.toFailureKind()))
            }
        }
    }

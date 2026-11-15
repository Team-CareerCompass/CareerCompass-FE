package com.careercompass.feature.profile.presentation.pastapplication

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.usecase.UploadPastApplicationUseCase
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationLabelRules
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.application.UploadFileSelectionReason
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 파일 바이트·스트림·실패·요청은 복원하지 않는다. 이름만 SavedStateHandle에 보관한다. */
public data class PastApplicationFileUploadState(
    val file: UploadFile? = null,
    val label: String = "",
    val labelError: ProfileFieldViolation? = null,
    val isSubmitting: Boolean = false,
    val failure: FailureKind? = null,
    val selectionFailure: UploadFileSelectionReason? = null,
    val clearUriRequested: Boolean = false,
)

public sealed interface PastApplicationFileUploadIntent {
    public data class Selected(
        val file: UploadFile,
        val restored: Boolean = false,
    ) : PastApplicationFileUploadIntent

    public data class SelectionFailed(
        val reason: UploadFileSelectionReason,
    ) : PastApplicationFileUploadIntent

    public data class LabelChanged(
        val value: String,
    ) : PastApplicationFileUploadIntent

    public data object Submit : PastApplicationFileUploadIntent

    public data object Dismiss : PastApplicationFileUploadIntent

    public data object ConsumeSelectionFailure : PastApplicationFileUploadIntent

    public data object ConsumeClearUri : PastApplicationFileUploadIntent
}

internal class PastApplicationFileUploadController(
    private val scope: CoroutineScope,
    private val handle: SavedStateHandle,
    private val state: () -> PastApplicationFileUploadState,
    private val canStart: () -> Boolean,
    private val isLimitReached: () -> Boolean,
    private val limitReached: () -> Unit,
    private val upload: UploadPastApplicationUseCase,
    private val changed: (PastApplicationFileUploadState) -> Unit,
    private val prepare: () -> Unit,
    private val finished: () -> Unit,
    private val added: (PastApplication) -> Unit,
    private val failed: (Throwable) -> Unit,
) {
    private var job: Job? = null
    private var epoch = 0L

    fun accept(intent: PastApplicationFileUploadIntent) {
        if (!canStart()) return
        when (intent) {
            is PastApplicationFileUploadIntent.Selected -> {
                if (state().isSubmitting) return
                if (isLimitReached()) {
                    reset()
                    limitReached()
                    return
                }
                val label =
                    if (intent.restored && handle.get<String>(KEY_FILE) == intent.file.fileName) {
                        handle.get<String>(KEY_LABEL) ?: PastApplicationLabelRules.defaultLabelFor(intent.file.fileName)
                    } else {
                        PastApplicationLabelRules.defaultLabelFor(intent.file.fileName)
                    }
                handle[KEY_FILE] = intent.file.fileName
                handle[KEY_LABEL] = label
                changed(PastApplicationFileUploadState(file = intent.file, label = label))
            }

            is PastApplicationFileUploadIntent.SelectionFailed -> {
                if (!state().isSubmitting) {
                    reset()
                    changed(state().copy(selectionFailure = intent.reason))
                }
            }

            is PastApplicationFileUploadIntent.LabelChanged -> {
                if (state().isSubmitting || state().file == null) return
                // 라ベルの上限を超えた入力も検証時まで保持する。Bundleには有限の文字だけを残す。
                if (intent.value.length <= 1_000) handle[KEY_LABEL] = intent.value else handle.remove<String>(KEY_LABEL)
                changed(state().copy(label = intent.value, labelError = null, failure = null))
            }

            PastApplicationFileUploadIntent.Submit -> {
                submit()
            }

            PastApplicationFileUploadIntent.Dismiss -> {
                if (!state().isSubmitting) reset()
            }

            PastApplicationFileUploadIntent.ConsumeSelectionFailure -> {
                changed(state().copy(selectionFailure = null))
            }

            PastApplicationFileUploadIntent.ConsumeClearUri -> {
                changed(state().copy(clearUriRequested = false))
            }
        }
    }

    fun cancelForSession() {
        epoch++
        job?.cancel()
        reset()
    }

    private fun reset() {
        handle.remove<String>(KEY_FILE)
        handle.remove<String>(KEY_LABEL)
        changed(PastApplicationFileUploadState(clearUriRequested = true))
    }

    private fun submit() {
        val input = state()
        val file = input.file ?: return
        if (input.isSubmitting || job?.isActive == true || !canStart()) return
        if (isLimitReached()) {
            limitReached()
            return
        }
        val violation = PastApplicationLabelRules.validate(input.label)
        if (violation != null) {
            changed(input.copy(labelError = violation))
            return
        }
        val generation = epoch
        prepare()
        changed(input.copy(isSubmitting = true, failure = null))
        job =
            scope.launch {
                try {
                    val application = upload(file, input.label).getOrThrow()
                    if (generation != epoch) return@launch
                    reset()
                    added(application)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (generation == epoch) {
                        changed(input.copy(isSubmitting = false, failure = failure.toFailureKind()))
                        failed(failure)
                    }
                } finally {
                    if (generation == epoch) finished()
                }
            }
    }

    private companion object {
        const val KEY_FILE = "past_application_upload.file_name"
        const val KEY_LABEL = "past_application_upload.label"
    }
}

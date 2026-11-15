package com.careercompass.feature.editor.presentation.save

import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.usecase.SaveApplicationUseCase
import com.careercompass.feature.editor.domain.usecase.UpdateApplicationItemAnswerUseCase
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

public data class ApplicationSaveState(
    val isSaving: Boolean = false,
    val isFinalSaving: Boolean = false,
    val lastSavedAt: Long? = null,
    val failure: FailureKind? = null,
    val finalFailure: Boolean = false,
    val consecutiveFailures: Int = 0,
    val autoSaveStopped: Boolean = false,
    val confirmOverflow: Boolean = false,
    val completed: Boolean = false,
)

/**
 * 편집 화면이 foreground일 때만 30초 주기로 변경 항목을 보낸다. 입력이 이어져도 저장이 밀리지 않는다.
 * 전송 중 편집은 다음 주기에 남겨 두고, 최종 저장은 항목 PATCH가 모두 성공한 뒤 한 번만 보낸다.
 * 한 배치에서 성공한 항목은 기억해 부분 실패 재시도 때 다시 보내지 않는다.
 */
internal class ApplicationSaveController(
    private val scope: CoroutineScope,
    private val state: () -> ApplicationProgressUiState,
    private val updateAnswer: UpdateApplicationItemAnswerUseCase,
    private val saveApplication: SaveApplicationUseCase,
    private val changed: (ApplicationSaveState) -> Unit,
    private val patched: (ApplicationItem) -> Unit,
    private val saved: (ApplicationDraft) -> Unit,
    private val failed: (Throwable) -> Unit,
) {
    private var timer: Job? = null
    private var request: Job? = null
    private var epoch = 0L
    private val acknowledged = mutableMapOf<Long, String>()
    private val finalAcknowledged = mutableMapOf<Long, String>()

    fun setActive(active: Boolean) {
        if (!active) {
            timer?.cancel()
            timer = null
        } else if (timer?.isActive != true && !state().save.completed) {
            timer =
                scope.launch {
                    while (true) {
                        delay(30_000)
                        if (!state().save.autoSaveStopped) start(final = false)
                    }
                }
        }
    }

    fun requestFinal() {
        if (!state().canSaveFinal) return
        // 생성 중 받은 임시 저장 ACK에 기대지 않고, 최종 저장 직전 수동 입력을 한 번 더 확정한다.
        if (!state().save.finalFailure) finalAcknowledged.clear()
        if (state().overflowItems.isNotEmpty()) {
            changed(state().save.copy(confirmOverflow = true))
        } else {
            start(final = true)
        }
    }

    fun confirmFinal() {
        if (!state().save.confirmOverflow || !state().canSaveFinal) return
        start(final = true)
    }

    fun dismissConfirmation() {
        changed(state().save.copy(confirmOverflow = false))
    }

    fun retry() {
        if (state().save.isSaving) return
        val final = state().save.finalFailure
        changed(state().save.copy(autoSaveStopped = false, consecutiveFailures = 0))
        if (final) requestFinal() else start(final = false)
    }

    fun cancel() {
        epoch++
        timer?.cancel()
        timer = null
        request?.cancel()
        acknowledged.clear()
        finalAcknowledged.clear()
    }

    private fun start(final: Boolean) {
        val current = state()
        val draft = current.draft ?: return
        if (current.save.isSaving || current.save.completed || current.identityMismatch || current.sessionEnded ||
            current.isRecovering
        ) {
            return
        }
        if (current.retryingIds.isNotEmpty() || (final && !current.canSaveFinal)) return
        val pending =
            draft.items.mapNotNull { item ->
                val answer = current.editedAnswers[item.id] ?: return@mapNotNull null
                val previous = if (final) finalAcknowledged[item.id] else acknowledged[item.id] ?: item.answer
                if (answer == previous) null else item to answer
            }
        if (!final && pending.isEmpty()) return
        val generation = epoch
        changed(current.save.copy(isSaving = true, isFinalSaving = final, failure = null, confirmOverflow = false))
        request =
            scope.launch {
                try {
                    for ((item, answer) in pending) {
                        // 임시 저장은 제출이 아니다. 초과 입력도 보호하고, 최종 저장에서 별도로 확인한다.
                        val result = updateAnswer(draft.id, item, answer, allowOverLimit = true)
                        if (generation != epoch) return@launch
                        val updated = result.getOrThrow()
                        check(updated.id == item.id && updated.answer == answer) { "Unconfirmed application item content" }
                        acknowledged[item.id] = answer
                        if (final) finalAcknowledged[item.id] = answer
                        patched(updated)
                    }
                    if (final) {
                        val result = saveApplication(draft.id)
                        if (generation != epoch) return@launch
                        val stored = result.getOrThrow()
                        check(stored.id == draft.id && stored.status == ApplicationStatus.Saved) { "Unconfirmed application save" }
                        setActive(false)
                        saved(stored)
                    }
                    if (generation == epoch) {
                        changed(ApplicationSaveState(lastSavedAt = System.currentTimeMillis(), completed = final))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (generation == epoch) {
                        val failures = state().save.consecutiveFailures + 1
                        changed(
                            state().save.copy(
                                isSaving = false,
                                isFinalSaving = false,
                                failure = failure.toFailureKind(),
                                finalFailure = final,
                                consecutiveFailures = failures,
                                autoSaveStopped = failures >= 3,
                            ),
                        )
                        failed(failure)
                    }
                }
            }
    }
}

internal val ApplicationProgressUiState.canSaveFinal: Boolean
    get() =
        draft?.items?.takeIf { it.isNotEmpty() }?.all {
            it.status != ApplicationItemStatus.Loading && (it.status == ApplicationItemStatus.Done || it.id in editedAnswers)
        } == true && !save.isSaving && !save.completed && !isRecovering && retryingIds.isEmpty() && !identityMismatch && !sessionEnded

internal val ApplicationProgressUiState.overflowItems: List<ApplicationItem>
    get() =
        draft
            ?.items
            .orEmpty()
            .map { it.copy(answer = editedAnswers[it.id] ?: it.answer) }
            .filter { it.remainingChars < 0 }

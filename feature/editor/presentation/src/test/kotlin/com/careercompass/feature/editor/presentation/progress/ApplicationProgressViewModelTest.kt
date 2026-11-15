package com.careercompass.feature.editor.presentation.progress

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import com.careercompass.feature.editor.domain.usecase.ObserveApplicationDraftUseCase
import com.careercompass.feature.editor.presentation.setup.ApplicationDraftStarted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeApplicationRepository()
    private val models = mutableListOf<ApplicationProgressViewModel>()
    private val reports = mutableListOf<Map<String, String>>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun `완료 항목을 즉시 채우고 중복 이벤트는 답과 순서를 바꾸지 않는다`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<ApplicationStreamEvent>(extraBufferCapacity = 8)
            repository.onStreamEvents = { events }
            val vm = start()
            runCurrent()
            assertTrue(repository.createDraftCalls.isEmpty())
            events.emit(ApplicationStreamEvent.ItemDone(1, "첫 답"))
            runCurrent()
            assertEquals(
                "첫 답",
                vm.uiState.value.draft!!
                    .items[0]
                    .answer,
            )
            assertEquals(
                ApplicationItemStatus.Loading,
                vm.uiState.value.draft!!
                    .items[1]
                    .status,
            )
            events.emit(ApplicationStreamEvent.ItemDone(1, "첫 답"))
            events.emit(ApplicationStreamEvent.ItemDone(2, "둘째 답"))
            runCurrent()
            assertEquals(
                listOf(1L, 2L),
                vm.uiState.value.draft!!
                    .items
                    .map { it.id },
            )
            assertEquals(
                ApplicationStatus.Ready,
                vm.uiState.value.draft!!
                    .status,
            )
            assertFalse(vm.uiState.value.isStreaming)
        }

    @Test fun `중간 실패를 재시도해도 다른 항목 스트림이 재시도 답을 되돌리지 않는다`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<ApplicationStreamEvent>(extraBufferCapacity = 8)
            repository.onStreamEvents = { events }
            val retry = CompletableDeferred<Result<ApplicationItem>>()
            repository.onRegenerateItem = { _, _, _, _ -> retry.await() }
            val vm = start()
            runCurrent()
            events.emit(ApplicationStreamEvent.ItemFailed(1, "LLM_UNAVAILABLE"))
            runCurrent()
            vm.onIntent(ApplicationProgressIntent.RetryItem(1))
            vm.onIntent(ApplicationProgressIntent.RetryItem(1))
            runCurrent()
            assertEquals(1, repository.regenerateCalls.size)
            retry.complete(Result.success(item(1, ApplicationItemStatus.Done, "재시도 답")))
            runCurrent()
            events.emit(ApplicationStreamEvent.ItemDone(2, "다른 답"))
            runCurrent()
            assertEquals(
                listOf("재시도 답", "다른 답"),
                vm.uiState.value.draft!!
                    .items
                    .map { it.answer },
            )
            assertTrue(repository.createDraftCalls.isEmpty())
        }

    @Test fun `끊겨도 받은 답을 지키고 재조회가 옛 loading을 돌려줘도 지우지 않는다`() =
        runTest(dispatcher) {
            repository.onStreamEvents = {
                flow {
                    emit(ApplicationStreamEvent.ItemDone(1, "받은 답"))
                    throw EditorFailure.StreamInterrupted(IOException("offline"))
                }
            }
            val vm = start()
            runCurrent()
            assertTrue(vm.uiState.value.interrupted)
            assertEquals(
                "받은 답",
                vm.uiState.value.draft!!
                    .items[0]
                    .answer,
            )
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }
            repository.onStreamEvents = { flow { emit(ApplicationStreamEvent.ItemDone(2, "나머지 답")) } }
            vm.onIntent(ApplicationProgressIntent.Recover)
            runCurrent()
            assertEquals(1, repository.createDraftCalls.size)
            assertEquals(
                listOf("받은 답", "나머지 답"),
                vm.uiState.value.draft!!
                    .items
                    .map { it.answer },
            )
            assertFalse(vm.uiState.value.interrupted)
        }

    @Test fun `백그라운드는 스트림을 닫고 복귀 때 한 번만 복구한다`() =
        runTest(dispatcher) {
            var closed = 0
            repository.onStreamEvents = {
                flow {
                    try {
                        emit(ApplicationStreamEvent.ItemDone(1, "유지할 답"))
                        awaitCancellation()
                    } finally {
                        closed++
                    }
                }
            }
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }
            val vm = start()
            runCurrent()
            vm.onIntent(ApplicationProgressIntent.Paused)
            runCurrent()
            assertEquals(1, closed)
            assertFalse(vm.uiState.value.isStreaming)
            assertEquals(
                "유지할 답",
                vm.uiState.value.draft!!
                    .items[0]
                    .answer,
            )
            vm.onIntent(ApplicationProgressIntent.Resumed)
            vm.onIntent(ApplicationProgressIntent.Resumed)
            runCurrent()
            assertEquals(1, repository.createDraftCalls.size)
            assertEquals(2, repository.streamCalls.size)
        }

    @Test fun `프로세스 복원은 요청 식별자와 확정 입력만 살리고 서버 상태를 읽는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = vm(handle)
            val questions = listOf(ApplicationItemDraft(1, "직접 수정한 질문", null))
            first.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(draft(), 101, ApplicationTone.Casual, questions)))
            val restored = vm(handle.parcelled())
            assertNull(restored.uiState.value.draft)
            repository.onCreateDraft = { _, _, _ ->
                Result.success(draft().copy(items = listOf(item(1, ApplicationItemStatus.Done, "서버의 최신 답"))))
            }
            restored.onIntent(ApplicationProgressIntent.Resumed)
            runCurrent()
            val call = repository.createDraftCalls.single()
            assertEquals(101L, call.postingId)
            assertEquals(ApplicationTone.Casual, call.tone)
            assertEquals(questions, call.items)
            assertEquals(
                "서버의 최신 답",
                restored.uiState.value.draft!!
                    .items
                    .single()
                    .answer,
            )
            assertTrue(repository.streamCalls.isEmpty())
        }

    @Test fun `복구 응답이 다른 지원서면 기존 답을 보존하고 자동 재시도를 막는다`() =
        runTest(dispatcher) {
            val vm = vm()
            vm.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(draft(), 101, ApplicationTone.Formal, null)))
            repository.onCreateDraft = { _, _, _ -> Result.success(draft().copy(id = 99)) }
            vm.onIntent(ApplicationProgressIntent.Recover)
            runCurrent()
            assertTrue(vm.uiState.value.identityMismatch)
            assertEquals(
                42L,
                vm.uiState.value.draft!!
                    .id,
            )
            vm.onIntent(ApplicationProgressIntent.Recover)
            runCurrent()
            assertEquals(1, repository.createDraftCalls.size)
            assertTrue(repository.streamCalls.isEmpty())
        }

    @Test fun `재시도 LLM 장애는 서버 점검과 구분하고 기존 답을 남긴다`() =
        runTest(dispatcher) {
            val failed = draft().copy(items = listOf(item(1, ApplicationItemStatus.Failed, "이전 답")))
            val vm = start(failed)
            repository.onRegenerateItem = { _, _, _, _ ->
                Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", IOException()))
            }
            vm.onIntent(ApplicationProgressIntent.RetryItem(1))
            runCurrent()
            assertEquals(FailureKind.ServiceUnavailable, vm.uiState.value.failure)
            assertEquals(
                "이전 답",
                vm.uiState.value.draft!!
                    .items
                    .single()
                    .answer,
            )
            assertTrue(reports.isEmpty())
        }

    @Test fun `세션 종료 뒤 취소를 무시한 재시도 응답도 화면에 돌아오지 않는다`() =
        runTest(dispatcher) {
            val later = CompletableDeferred<Result<ApplicationItem>>()
            repository.onRegenerateItem = { _, id, _, _ ->
                if (id == 1L) {
                    withContext(NonCancellable) { later.await() }
                } else {
                    Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException()))
                }
            }
            val handle = SavedStateHandle()
            val vm = vm(handle)
            vm.onIntent(
                ApplicationProgressIntent.Started(
                    ApplicationDraftStarted(
                        draft().copy(items = listOf(item(1, ApplicationItemStatus.Failed), item(2, ApplicationItemStatus.Failed))),
                        101,
                        ApplicationTone.Casual,
                        null,
                    ),
                ),
            )
            vm.onIntent(ApplicationProgressIntent.RetryItem(1))
            vm.onIntent(ApplicationProgressIntent.RetryItem(2))
            runCurrent()
            assertTrue(vm.uiState.value.sessionEnded)
            later.complete(Result.success(item(1, ApplicationItemStatus.Done, "노출하면 안 되는 답")))
            runCurrent()
            assertNull(vm.uiState.value.draft)
            assertNull(ProgressIdentity(handle).applicationId)
            vm.onIntent(ApplicationProgressIntent.ConsumeSessionEnded)
            vm.onIntent(ApplicationProgressIntent.Recover)
            assertTrue(repository.createDraftCalls.isEmpty())
        }

    @Test fun `문항 전환은 수동 입력과 빈 삭제를 보관하고 서버에 저장하지 않는다`() =
        runTest(dispatcher) {
            val vm = start()
            vm.onIntent(ApplicationProgressIntent.EditAnswer(1, "붙여넣은 첫 줄\n둘째 줄"))
            vm.onIntent(ApplicationProgressIntent.SelectItem(2))
            vm.onIntent(ApplicationProgressIntent.EditAnswer(2, ""))
            vm.onIntent(ApplicationProgressIntent.SelectItem(1))
            assertEquals(mapOf(1L to "붙여넣은 첫 줄\n둘째 줄", 2L to ""), vm.uiState.value.editedAnswers)
            assertEquals(1L, vm.uiState.value.selectedItemId)
            assertTrue(repository.updateAnswerCalls.isEmpty())
            assertTrue(repository.saveCalls.isEmpty())
        }

    @Test fun `늦은 스트림 답은 사용자가 고친 글을 대체하지 않는다`() =
        runTest(dispatcher) {
            val events = MutableSharedFlow<ApplicationStreamEvent>(extraBufferCapacity = 4)
            repository.onStreamEvents = { events }
            val vm = start()
            runCurrent()
            vm.onIntent(ApplicationProgressIntent.EditAnswer(1, "직접 쓴 답"))
            events.emit(ApplicationStreamEvent.ItemDone(1, "늦은 AI 답"))
            runCurrent()
            assertEquals("직접 쓴 답", vm.uiState.value.editedAnswers[1L])
            assertEquals(
                "늦은 AI 답",
                vm.uiState.value.draft!!
                    .items
                    .first()
                    .answer,
            )
        }

    @Test fun `실제 Parcel 복원은 수동 입력과 선택만 살리고 서버 답은 다시 읽는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val original = vm(handle)
            original.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(draft(), 101, ApplicationTone.Formal, null)))
            original.onIntent(ApplicationProgressIntent.EditAnswer(1, "내 첫 답\n줄바꿈"))
            original.onIntent(ApplicationProgressIntent.EditAnswer(2, ""))
            original.onIntent(ApplicationProgressIntent.SelectItem(2))
            original.viewModelScope.cancel()
            val restored = vm(handle.parcelled())
            assertNull(restored.uiState.value.draft)
            assertNull(restored.uiState.value.failure)
            assertFalse(restored.uiState.value.isStreaming)
            assertEquals(2L, restored.uiState.value.selectedItemId)
            repository.onCreateDraft =
                {
                    _,
                    _,
                    _,
                    ->
                    Result.success(
                        draft().copy(
                            items = listOf(item(1, ApplicationItemStatus.Done, "서버 답 1"), item(2, ApplicationItemStatus.Done, "서버 답 2")),
                        ),
                    )
                }
            restored.onIntent(ApplicationProgressIntent.Resumed)
            runCurrent()
            assertEquals(mapOf(1L to "내 첫 답\n줄바꿈", 2L to ""), restored.uiState.value.editedAnswers)
            assertEquals(
                "서버 답 2",
                restored.uiState.value.draft!!
                    .items[1]
                    .answer,
            )
            assertEquals(1, repository.createDraftCalls.size)
        }

    @Test fun `최대 글자 초과는 허용하지만 전체 임시 보관 한도 초과는 기존 글을 지킨다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = vm(handle)
            vm.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(draft(), 101, ApplicationTone.Formal, null)))
            vm.onIntent(ApplicationProgressIntent.EditAnswer(1, "가".repeat(60_000)))
            vm.onIntent(ApplicationProgressIntent.EditAnswer(2, "나".repeat(40_000)))
            assertFalse(vm.uiState.value.inputLimitReached)
            vm.onIntent(ApplicationProgressIntent.EditAnswer(2, "다".repeat(40_001)))
            assertTrue(vm.uiState.value.inputLimitReached)
            assertEquals("나".repeat(40_000), vm.uiState.value.editedAnswers[2L])
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(handle.savedStateProvider().saveState())
                assertTrue("입력 저장 상태가 Binder 예산을 넘는다", parcel.dataSize() < 250_000)
            } finally {
                parcel.recycle()
            }
            val restored = vm(handle.parcelled())
            assertEquals(
                100_000,
                restored.uiState.value.editedAnswers.values
                    .sumOf { it.length },
            )
            assertFalse(restored.uiState.value.inputLimitReached)
            vm.onIntent(ApplicationProgressIntent.EditAnswer(2, "줄인 답"))
            assertFalse(vm.uiState.value.inputLimitReached)
        }

    @Test fun `401은 수동 입력까지 지우고 뒤늦은 편집 의도도 무시한다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val vm = vm(handle)
            vm.onIntent(
                ApplicationProgressIntent.Started(
                    ApplicationDraftStarted(
                        draft().copy(items = listOf(item(1, ApplicationItemStatus.Failed))),
                        101,
                        ApplicationTone.Formal,
                        null,
                    ),
                ),
            )
            vm.onIntent(ApplicationProgressIntent.EditAnswer(1, "개인 지원서"))
            repository.onRegenerateItem = { _, _, _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException())) }
            vm.onIntent(ApplicationProgressIntent.RetryItem(1))
            runCurrent()
            vm.onIntent(ApplicationProgressIntent.EditAnswer(1, "뒤늦은 개인 지원서"))
            assertTrue(
                vm.uiState.value.editedAnswers
                    .isEmpty(),
            )
            assertTrue(
                vm(handle.parcelled())
                    .uiState.value.editedAnswers
                    .isEmpty(),
            )
        }

    @Test fun `모르는 문항 편집과 선택은 입력에 들어가지 않는다`() =
        runTest(dispatcher) {
            val vm = start()
            vm.onIntent(ApplicationProgressIntent.EditAnswer(99, "잘못된 문항"))
            vm.onIntent(ApplicationProgressIntent.SelectItem(99))
            assertTrue(
                vm.uiState.value.editedAnswers
                    .isEmpty(),
            )
            assertNull(vm.uiState.value.selectedItemId)
        }

    @Test fun `최종 저장 중 새 편집을 막고 성공 뒤 입력과 복구 식별자를 지운다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val model = vm(handle)
            val ready = draft().copy(status = ApplicationStatus.Ready, items = listOf(item(1, ApplicationItemStatus.Done, "서버 답")))
            model.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(ready, 101, ApplicationTone.Formal, null)))
            model.onIntent(ApplicationProgressIntent.EditAnswer(1, "최종 답"))
            repository.onUpdateItemAnswer = { _, id, answer -> Result.success(item(id, ApplicationItemStatus.Done, answer)) }
            val later = CompletableDeferred<Result<ApplicationDraft>>()
            repository.onSave = { later.await() }
            model.onIntent(ApplicationProgressIntent.SaveFinal)
            model.onIntent(ApplicationProgressIntent.EditAnswer(1, "저장 중 무시할 편집"))
            runCurrent()
            assertTrue(model.uiState.value.save.isFinalSaving)
            assertEquals("최종 답", model.uiState.value.editedAnswers[1L])
            later.complete(Result.success(ready.copy(status = ApplicationStatus.Saved)))
            runCurrent()
            assertTrue(model.uiState.value.save.completed)
            assertTrue(model.uiState.value.savedNavigationPending)
            assertTrue(
                model.uiState.value.editedAnswers
                    .isEmpty(),
            )
            assertNull(ProgressIdentity(handle).applicationId)
            model.onIntent(ApplicationProgressIntent.ConsumeSaved)
            model.onIntent(ApplicationProgressIntent.SaveFinal)
            assertFalse(model.uiState.value.savedNavigationPending)
            assertEquals(1, repository.saveCalls.size)
        }

    @Test fun `자동 저장 401은 타이머와 개인 입력을 함께 정리한다`() =
        runTest(dispatcher) {
            val model = start(draft().copy(items = listOf(item(1, ApplicationItemStatus.Done, "서버 답"))))
            model.onIntent(ApplicationProgressIntent.EditAnswer(1, "개인 초안"))
            repository.onUpdateItemAnswer = { _, _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException())) }
            model.onIntent(ApplicationProgressIntent.EditorResumed)
            advanceTimeBy(30_000)
            runCurrent()
            assertTrue(model.uiState.value.sessionEnded)
            assertTrue(
                model.uiState.value.editedAnswers
                    .isEmpty(),
            )
            assertNull(model.uiState.value.draft)
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(1, repository.updateAnswerCalls.size)
        }

    private fun start(draft: ApplicationDraft = draft()): ApplicationProgressViewModel =
        vm().also {
            it.onIntent(ApplicationProgressIntent.Started(ApplicationDraftStarted(draft, 101, ApplicationTone.Formal, null)))
            it.onIntent(ApplicationProgressIntent.Resumed)
        }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        ApplicationProgressViewModel(
            101,
            repository,
            ObserveApplicationDraftUseCase(repository),
            com.careercompass.feature.editor.domain.usecase
                .UpdateApplicationItemAnswerUseCase(repository),
            com.careercompass.feature.editor.domain.usecase
                .SaveApplicationUseCase(repository),
            object : ErrorReporter {
                override fun writeFailure(
                    throwable: Throwable,
                    attributes: Map<String, String>,
                ) {
                    reports += attributes
                }
            },
            handle,
        ).also { models += it }

    private fun SavedStateHandle.parcelled(): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(savedStateProvider().saveState())
            parcel.setDataPosition(0)
            SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }
}

internal fun item(
    id: Long,
    status: ApplicationItemStatus = ApplicationItemStatus.Loading,
    answer: String? = null,
) = ApplicationItem(id, id.toInt(), "문항 $id", 500, status, answer)

internal fun draft() = ApplicationDraft(42, ApplicationStatus.Generating, listOf(item(1), item(2)))

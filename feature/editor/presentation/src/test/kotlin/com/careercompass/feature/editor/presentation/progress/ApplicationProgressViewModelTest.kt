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

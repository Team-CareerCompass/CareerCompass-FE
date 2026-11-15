package com.careercompass.feature.editor.presentation.save

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import com.careercompass.feature.editor.domain.usecase.SaveApplicationUseCase
import com.careercompass.feature.editor.domain.usecase.UpdateApplicationItemAnswerUseCase
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationSaveControllerTest {
    private val repository = FakeApplicationRepository()
    private var state = ApplicationProgressUiState(draft = draft(), editedAnswers = mapOf(1L to "내 첫 답", 2L to "내 둘째 답"))
    private val saved = mutableListOf<ApplicationDraft>()
    private val failures = mutableListOf<Throwable>()

    @Test fun `입력이 계속되어도 30초에 저장하고 이중 resume과 변경 없는 주기는 중복하지 않는다`() =
        runTest {
            val controller = controller()
            controller.setActive(true)
            controller.setActive(true)
            repeat(29) { index ->
                state = state.copy(editedAnswers = state.editedAnswers + (1L to "계속 입력 $index"))
                advanceTimeBy(1_000)
                runCurrent()
            }
            assertTrue(repository.updateAnswerCalls.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, repository.updateAnswerCalls.size)
            assertEquals("계속 입력 28", repository.updateAnswerCalls[0].answer)
            assertNotNull(state.save.lastSavedAt)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(2, repository.updateAnswerCalls.size)
        }

    @Test fun `백그라운드에서 타이머가 멈추고 재진입하면 한 번만 시작한다`() =
        runTest {
            val controller = controller()
            controller.setActive(true)
            advanceTimeBy(29_000)
            controller.setActive(false)
            advanceTimeBy(90_000)
            runCurrent()
            assertTrue(repository.updateAnswerCalls.isEmpty())
            controller.setActive(true)
            controller.setActive(true)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, repository.updateAnswerCalls.size)
        }

    @Test fun `임시 저장 중 새로 친 글은 다음 저장 대상으로 남는다`() =
        runTest {
            state = state.copy(editedAnswers = mapOf(1L to "전송한 답"))
            val first = CompletableDeferred<Result<ApplicationItem>>()
            val controller = controller()
            repository.onUpdateItemAnswer = { _, id, answer ->
                if (answer == "전송한 답") first.await() else Result.success(item(id).copy(answer = answer))
            }
            controller.setActive(true)
            advanceTimeBy(30_000)
            runCurrent()
            assertTrue(state.save.isSaving)
            state = state.copy(editedAnswers = mapOf(1L to "저장 중 더 쓴 답"))
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, repository.updateAnswerCalls.size)
            first.complete(Result.success(item(1).copy(answer = "전송한 답")))
            runCurrent()
            assertEquals("저장 중 더 쓴 답", state.editedAnswers[1L])
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(listOf("전송한 답", "저장 중 더 쓴 답"), repository.updateAnswerCalls.map { it.answer })
        }

    @Test fun `항목 전송 일부 실패 때 최종 저장은 하지 않고 실패한 항목부터 재시도한다`() =
        runTest {
            val controller = controller()
            var secondFails = true
            repository.onUpdateItemAnswer = { _, id, answer ->
                if (id == 2L && secondFails) Result.failure(IOException()) else Result.success(item(id).copy(answer = answer))
            }
            controller.requestFinal()
            controller.requestFinal()
            runCurrent()
            assertEquals(listOf(1L, 2L), repository.updateAnswerCalls.map { it.itemId })
            assertTrue(repository.saveCalls.isEmpty())
            assertTrue(state.save.finalFailure)
            secondFails = false
            controller.retry()
            runCurrent()
            assertEquals(listOf(1L, 2L, 2L), repository.updateAnswerCalls.map { it.itemId })
            assertEquals(listOf(42L), repository.saveCalls)
            assertTrue(state.save.completed)
            assertEquals(1, saved.size)
            controller.requestFinal()
            runCurrent()
            assertEquals(1, repository.saveCalls.size)
        }

    @Test fun `초과한 원문은 임시 저장하고 최종 저장은 확인 후에만 실행한다`() =
        runTest {
            state = state.copy(editedAnswers = mapOf(1L to "가".repeat(501)))
            val controller = controller()
            controller.requestFinal()
            runCurrent()
            assertTrue(state.save.confirmOverflow)
            assertTrue(repository.saveCalls.isEmpty())
            controller.dismissConfirmation()
            controller.setActive(true)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals("가".repeat(501), repository.updateAnswerCalls.single().answer)
            assertTrue(repository.saveCalls.isEmpty())
            controller.requestFinal()
            assertTrue(state.save.confirmOverflow)
            controller.confirmFinal()
            runCurrent()
            assertTrue(state.save.completed)
            assertEquals(listOf(42L), repository.saveCalls)
        }

    @Test fun `실패를 표시하고 세 번 연속 실패하면 자동 저장을 멈추며 수동 재시도로 재개한다`() =
        runTest {
            val controller = controller()
            repository.onUpdateItemAnswer = { _, _, _ -> Result.failure(CoreDataFailure.NetworkUnavailable(IOException())) }
            controller.setActive(true)
            repeat(3) {
                advanceTimeBy(30_000)
                runCurrent()
            }
            assertEquals(3, repository.updateAnswerCalls.size)
            assertEquals(FailureKind.NoConnection, state.save.failure)
            assertTrue(state.save.autoSaveStopped)
            advanceTimeBy(90_000)
            runCurrent()
            assertEquals(3, repository.updateAnswerCalls.size)
            assertEquals("내 첫 답", state.editedAnswers[1L])
            repository.onUpdateItemAnswer = { _, id, answer -> Result.success(item(id).copy(answer = answer)) }
            controller.retry()
            runCurrent()
            assertFalse(state.save.autoSaveStopped)
            assertEquals(0, state.save.consecutiveFailures)
            assertEquals(5, repository.updateAnswerCalls.size)
        }

    @Test fun `세션 취소 뒤 늦게 온 저장 응답은 반영하지 않는다`() =
        runTest {
            val later = CompletableDeferred<Result<ApplicationItem>>()
            val controller = controller()
            repository.onUpdateItemAnswer = { _, _, _ -> withContext(NonCancellable) { later.await() } }
            controller.requestFinal()
            runCurrent()
            controller.cancel()
            state = ApplicationProgressUiState(sessionEnded = true)
            later.complete(Result.success(item(1)))
            runCurrent()
            assertTrue(repository.saveCalls.isEmpty())
            assertEquals(ApplicationProgressUiState(sessionEnded = true), state)
            assertTrue(saved.isEmpty())
        }

    @Test fun `서버가 saved를 확인하지 않으면 완료 화면을 열지 않는다`() =
        runTest {
            val controller = controller()
            repository.onSave = { Result.success(draft()) }
            controller.requestFinal()
            runCurrent()
            assertFalse(state.save.completed)
            assertNotNull(state.save.failure)
            assertTrue(saved.isEmpty())
        }

    @Test fun `임시 저장 뒤에도 최종 저장 직전 수동 입력을 다시 확정한다`() =
        runTest {
            state = state.copy(editedAnswers = mapOf(1L to "내 답"))
            val controller = controller()
            controller.setActive(true)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, repository.updateAnswerCalls.size)
            controller.requestFinal()
            runCurrent()
            assertEquals(listOf("내 답", "내 답"), repository.updateAnswerCalls.map { it.answer })
            assertTrue(state.save.completed)
        }

    @Test fun `서버가 본문을 잘라 반환하면 저장됐다고 표시하지 않는다`() =
        runTest {
            val controller = controller()
            repository.onUpdateItemAnswer = { _, id, _ -> Result.success(item(id).copy(answer = "잘린 답")) }
            controller.requestFinal()
            runCurrent()
            assertTrue(repository.saveCalls.isEmpty())
            assertNotNull(state.save.failure)
            assertEquals("내 첫 답", state.editedAnswers[1L])
        }

    private fun TestScope.controller(): ApplicationSaveController {
        repository.onUpdateItemAnswer = { _, id, answer -> Result.success(item(id).copy(answer = answer)) }
        repository.onSave = { Result.success(draft().copy(status = ApplicationStatus.Saved)) }
        return ApplicationSaveController(
            backgroundScope,
            { state },
            UpdateApplicationItemAnswerUseCase(repository),
            SaveApplicationUseCase(repository),
            changed = { state = state.copy(save = it) },
            patched = { updated ->
                state =
                    state.copy(draft = state.draft!!.copy(items = state.draft!!.items.map { if (it.id == updated.id) updated else it }))
            },
            saved = { saved += it },
            failed = { failures += it },
        )
    }

    private fun item(id: Long) = ApplicationItem(id, id.toInt(), "문항 $id", 500, ApplicationItemStatus.Done, "서버 답 $id")

    private fun draft() = ApplicationDraft(42, ApplicationStatus.Ready, listOf(item(1), item(2)))
}

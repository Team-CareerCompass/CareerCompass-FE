package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.draft
import com.careercompass.feature.editor.domain.item
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ObserveApplicationDraftUseCaseTest {
    private val repository = FakeApplicationRepository()
    private val observeApplicationDraft = ObserveApplicationDraftUseCase(repository)

    /** 구독하자마자 문항 목록이 있어야 한다 — 없으면 화면은 「빈 초안」과 「아직 아무 사건도 안 왔다」를 못 가른다. */
    @Test
    fun `첫 값은 받은 초안 그대로다`() =
        runTest {
            repository.onStreamEvents = { flowOf() }

            val emissions = observeApplicationDraft(draft()).toList()

            assertEquals(draft(), emissions.first())
        }

    @Test
    fun `사건마다 반영한 초안을 낸다`() =
        runTest {
            repository.onStreamEvents = {
                flowOf(
                    ApplicationStreamEvent.ItemDone(itemId = 1L, answer = "첫 답"),
                    ApplicationStreamEvent.ItemDone(itemId = 2L, answer = "둘째 답"),
                )
            }

            val emissions = observeApplicationDraft(draft()).toList()

            assertEquals(listOf(0, 1, 2, 2), emissions.map { it.completedItemCount })
            assertEquals(listOf("첫 답", "둘째 답"), emissions.last().items.map { it.answer })
        }

    /** 서버가 마지막 상태를 말하지 않고 연결만 닫는 경우가 있다. 그때 남은 항목을 굳히지 않으면 화면이 갇힌다. */
    @Test
    fun `스트림이 닫히면 굳힌 값을 한 번 더 낸다`() =
        runTest {
            repository.onStreamEvents = { flowOf(ApplicationStreamEvent.ItemDone(itemId = 1L, answer = "첫 답")) }

            val emissions = observeApplicationDraft(draft()).toList()

            assertEquals(ApplicationStatus.PartialFailed, emissions.last().status)
            assertEquals(
                listOf(ApplicationItemStatus.Done, ApplicationItemStatus.Failed),
                emissions.last().items.map { it.status },
            )
        }

    /** 굳혀도 달라진 것이 없으면 같은 값을 두 번 내지 않는다 — 화면이 같은 상태로 두 번 다시 그린다. */
    @Test
    fun `굳힌 값이 그대로면 다시 내지 않는다`() =
        runTest {
            repository.onStreamEvents = { flowOf(ApplicationStreamEvent.StatusChanged(ApplicationStatus.Ready)) }
            val ready = draft(items = listOf(item(1L, status = ApplicationItemStatus.Done, answer = "답")))

            val emissions = observeApplicationDraft(ready).toList()

            assertEquals(2, emissions.size)
            assertEquals(ApplicationStatus.Ready, emissions.last().status)
        }

    /** 끊김은 흐름을 예외로 끝낸다. 그전까지 낸 초안은 이미 화면에 반영돼 있고, 그 값은 유효하다. */
    @Test
    fun `끊기면 이미 낸 초안 뒤에 예외가 나온다`() =
        runTest {
            repository.onStreamEvents = {
                flow {
                    emit(ApplicationStreamEvent.ItemDone(itemId = 1L, answer = "첫 답"))
                    throw IOException("stream interrupted")
                }
            }
            val received = mutableListOf<Int>()

            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking {
                    observeApplicationDraft(draft()).collect { received += it.completedItemCount }
                }
            }

            assertEquals(listOf(0, 1), received)
        }

    @Test
    fun `구독할 때마다 그 초안의 스트림을 새로 연다`() =
        runTest {
            repository.onStreamEvents = { flowOf() }
            val flow = observeApplicationDraft(draft(id = 7L))

            flow.toList()
            flow.toList()

            assertEquals(listOf(7L, 7L), repository.streamCalls.toList())
        }
}

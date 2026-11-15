package com.careercompass.feature.editor.domain.usecase

import com.careercompass.feature.editor.domain.draft
import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.item
import com.careercompass.feature.editor.domain.model.ApplicationHistoryPage
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationUseCasesTest {
    private val repository = FakeApplicationRepository()

    private fun itemDraft(
        order: Int,
        question: String,
        maxChars: Int?,
    ) = ApplicationItemDraft(order = order, question = question, maxChars = maxChars)

    /** 어조를 안 고르면 격식체다 — 자소서의 기본값이고, 서버가 모르는 값을 받으면 사용자가 못 고치는 실패가 된다. */
    @Test
    fun `초안 생성은 기본 어조로 공고 id 를 올려 보낸다`() =
        runTest {
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }

            CreateApplicationDraftUseCase(repository)(postingId = 101L)

            val call = repository.createDraftCalls.single()
            assertEquals(101L, call.postingId)
            assertEquals(ApplicationTone.Formal, call.tone)
        }

    /**
     * 문항은 계약에 없는 필드다. 보낼 이유가 없을 때 보내면 그 필드를 모르는 서버가 요청을 거절할 여지만
     * 만든다 — 인식 결과를 그대로 쓴 요청은 지금 계약과 한 글자도 다르지 않아야 한다.
     */
    @Test
    fun `인식 결과를 그대로 쓰면 문항을 보내지 않는다`() =
        runTest {
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }
            val recognized = listOf(itemDraft(1, "지원 동기", 500))

            CreateApplicationDraftUseCase(repository)(
                postingId = 101L,
                items = recognized,
                recognizedItems = recognized,
            )

            assertEquals(null, repository.createDraftCalls.single().items)
        }

    @Test
    fun `문항을 손봤으면 손본 목록을 실어 보낸다`() =
        runTest {
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }
            val edited = listOf(itemDraft(1, "고친 문항", 300))

            CreateApplicationDraftUseCase(repository)(
                postingId = 101L,
                items = edited,
                recognizedItems = listOf(itemDraft(1, "지원 동기", 500)),
            )

            assertEquals(edited, repository.createDraftCalls.single().items)
        }

    /** 공고가 문항을 못 찾은 경우에도 사용자가 쓴 것은 반드시 가야 한다 — 그것이 F4-1 의 「직접 입력」이다. */
    @Test
    fun `인식 결과가 없어도 직접 쓴 문항은 실어 보낸다`() =
        runTest {
            repository.onCreateDraft = { _, _, _ -> Result.success(draft()) }
            val written = listOf(itemDraft(1, "직접 쓴 문항", null))

            CreateApplicationDraftUseCase(repository)(
                postingId = 101L,
                items = written,
                recognizedItems = emptyList(),
            )

            assertEquals(written, repository.createDraftCalls.single().items)
        }

    @Test
    fun `재생성은 고른 어조와 강조 카드를 그대로 넘긴다`() =
        runTest {
            repository.onRegenerateItem = { _, _, _, _ -> Result.success(item(2L)) }

            RegenerateApplicationItemUseCase(repository)(
                applicationId = 42L,
                itemId = 2L,
                tone = ApplicationTone.Casual,
                emphasizeCardIds = listOf(5L, 12L),
            )

            val call = repository.regenerateCalls.single()
            assertEquals(ApplicationTone.Casual, call.tone)
            assertEquals(listOf(5L, 12L), call.emphasizeCardIds)
        }

    @Test
    fun `저장과 결과 입력과 삭제는 그대로 위임한다`() =
        runTest {
            repository.onSave = { Result.success(draft(status = ApplicationStatus.Saved)) }
            repository.onUpdateResult = { _, _ -> Result.success(draft(status = ApplicationStatus.Saved)) }

            SaveApplicationUseCase(repository)(42L)
            UpdateApplicationResultUseCase(repository)(42L, ApplicationResult.Pass)
            DeleteApplicationUseCase(repository)(42L)

            assertEquals(listOf(42L), repository.saveCalls.toList())
            assertEquals(listOf(42L to ApplicationResult.Pass), repository.updateResultCalls.toList())
            assertEquals(listOf(42L), repository.deleteCalls.toList())
        }

    @Test
    fun `이력 조회는 상태 필터와 커서를 그대로 넘긴다`() =
        runTest {
            repository.onGetApplications = { _, _, _ -> Result.success(ApplicationHistoryPage(applications = emptyList())) }

            GetApplicationHistoryUseCase(repository)(status = ApplicationStatus.Saved, cursor = "eyJ", limit = 20)

            val call = repository.historyCalls.single()
            assertEquals(ApplicationStatus.Saved, call.status)
            assertEquals("eyJ", call.cursor)
            assertEquals(20, call.limit)
        }

    @Test
    fun `답이 상한 안이면 그대로 보낸다`() =
        runTest {
            repository.onUpdateItemAnswer = { _, _, answer ->
                Result.success(item(2L, status = ApplicationItemStatus.Done, answer = answer))
            }

            val result =
                UpdateApplicationItemAnswerUseCase(repository)(
                    applicationId = 42L,
                    item = item(2L, maxChars = 5),
                    answer = "다섯글자다",
                )

            assertEquals("다섯글자다", result.getOrNull()?.answer)
            assertEquals("다섯글자다", repository.updateAnswerCalls.single().answer)
        }

    /**
     * 왕복을 기다려 「저장 실패」만 보여 주면 사용자는 무엇이 문제인지 모르고, 그 사이 30초 임시 저장이 같은
     * 요청을 또 보낸다. 그래서 보내기 전에 끊는다.
     */
    @Test
    fun `상한을 넘은 답은 보내지 않고 넘은 사실을 돌려준다`() =
        runTest {
            val result =
                UpdateApplicationItemAnswerUseCase(repository)(
                    applicationId = 42L,
                    item = item(2L, maxChars = 5),
                    answer = "여섯 글자다",
                )

            val failure = result.exceptionOrNull()
            assertTrue("expected AnswerTooLong but was $failure", failure is EditorFailure.AnswerTooLong)
            assertEquals(5, (failure as EditorFailure.AnswerTooLong).maxChars)
            assertEquals(6, failure.actualChars)
            assertTrue(repository.updateAnswerCalls.isEmpty())
        }
}

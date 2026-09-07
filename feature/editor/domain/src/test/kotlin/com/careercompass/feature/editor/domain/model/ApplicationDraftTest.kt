package com.careercompass.feature.editor.domain.model

import com.careercompass.feature.editor.domain.draft
import com.careercompass.feature.editor.domain.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationDraftTest {
    @Test
    fun `항목이 답을 받으면 그 항목만 갈아 끼운다`() {
        val updated = draft().applying(ApplicationStreamEvent.ItemDone(itemId = 2L, answer = "받은 답"))

        assertEquals(listOf(null, "받은 답"), updated.items.map { it.answer })
        assertEquals(
            listOf(ApplicationItemStatus.Loading, ApplicationItemStatus.Done),
            updated.items.map { it.status },
        )
    }

    @Test
    fun `실패한 항목은 상태만 바뀌고 이미 받은 답은 남는다`() {
        val received = draft(items = listOf(item(1L, status = ApplicationItemStatus.Done, answer = "먼저 온 답")))

        val updated = received.applying(ApplicationStreamEvent.ItemFailed(itemId = 1L, code = "LLM_UNAVAILABLE"))

        assertEquals(ApplicationItemStatus.Failed, updated.items.single().status)
        assertEquals("먼저 온 답", updated.items.single().answer)
    }

    /** 우리가 모르는 항목을 서버가 말하면 우리 초안이 낡은 것이다. 만들어 내면 문항 없는 답이 화면에 생긴다. */
    @Test
    fun `모르는 항목을 가리키는 사건은 아무것도 바꾸지 않는다`() {
        val original = draft()

        val updated = original.applying(ApplicationStreamEvent.ItemDone(itemId = 99L, answer = "누구 것도 아닌 답"))

        assertEquals(original, updated)
    }

    @Test
    fun `상태 사건은 지원서 상태를 바꾼다`() {
        val updated = draft().applying(ApplicationStreamEvent.StatusChanged(ApplicationStatus.PartialFailed))

        assertEquals(ApplicationStatus.PartialFailed, updated.status)
    }

    @Test
    fun `스트림이 닫히면 남은 loading 항목을 실패로 굳히고 전체를 partial_failed 로 본다`() {
        val settled =
            draft(
                items = listOf(item(1L, status = ApplicationItemStatus.Done, answer = "답"), item(2L)),
            ).settled()

        assertEquals(ApplicationStatus.PartialFailed, settled.status)
        assertEquals(
            listOf(ApplicationItemStatus.Done, ApplicationItemStatus.Failed),
            settled.items.map { it.status },
        )
    }

    @Test
    fun `모든 항목이 답을 받았으면 ready 로 굳는다`() {
        val settled =
            draft(
                items =
                    listOf(
                        item(1L, status = ApplicationItemStatus.Done, answer = "하나"),
                        item(2L, status = ApplicationItemStatus.Done, answer = "둘"),
                    ),
            ).settled()

        assertEquals(ApplicationStatus.Ready, settled.status)
    }

    /** 서버가 상태를 말해 줬거나 이미 저장된 초안은 스트림이 닫혔다고 손댈 것이 없다. */
    @Test
    fun `generating 이 아니면 굳히지 않는다`() {
        val saved = draft(status = ApplicationStatus.Saved, items = listOf(item(1L)))

        assertSame(saved, saved.settled())
    }

    @Test
    fun `완료 항목 수와 진행 여부를 센다`() {
        val partial = draft(items = listOf(item(1L, status = ApplicationItemStatus.Done, answer = "답"), item(2L)))

        assertEquals(1, partial.completedItemCount)
        assertEquals(true, partial.hasLoadingItem)
    }

    @Test
    fun `남은 글자 수는 상한을 넘으면 음수다`() {
        assertEquals(3, item(1L, maxChars = 5, answer = "가나").remainingChars)
        assertEquals(-1, item(1L, maxChars = 5, answer = "가나다라마바").remainingChars)
        assertEquals(5, item(1L, maxChars = 5).remainingChars)
    }

    /** 상한이 0 이하인 문항은 서버가 만들 수 없는 값이다 — 조용히 받으면 화면이 「-3자 남음」을 그린다. */
    @Test
    fun `글자 상한이 0 이하면 항목을 만들 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { item(1L, maxChars = 0) }
    }
}

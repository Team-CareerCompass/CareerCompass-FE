package com.careercompass.feature.editor.domain.model

import com.careercompass.core.model.posting.PostingFormQuestion
import com.careercompass.core.model.user.ProfileFieldViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationItemDraftTest {
    /** 공고가 준 `order` 가 1,3,7 처럼 띄엄띄엄 와도 화면 번호는 1부터 이어져야 한다. */
    @Test
    fun `인식된 문항을 order 순으로 세워 1부터 다시 매긴다`() {
        val drafts =
            listOf(
                PostingFormQuestion(order = 7, question = "셋째", maxChars = null),
                PostingFormQuestion(order = 1, question = "첫째", maxChars = 500),
                PostingFormQuestion(order = 3, question = "둘째", maxChars = 400),
            ).toItemDrafts()

        assertEquals(listOf(1, 2, 3), drafts.map { it.order })
        assertEquals(listOf("첫째", "둘째", "셋째"), drafts.map { it.question })
        assertEquals(listOf(500, 400, null), drafts.map { it.maxChars })
    }

    /** 지우고 남은 구멍을 그대로 보내면 화면에 「1, 3, 4」가 보이고, 사용자는 그것을 공고의 번호로 읽는다. */
    @Test
    fun `삭제 뒤 번호를 다시 매긴다`() {
        val remaining = listOf(draft(1, "첫째"), draft(3, "셋째"), draft(4, "넷째")).renumbered()

        assertEquals(listOf(1, 2, 3), remaining.map { it.order })
        assertEquals(listOf("첫째", "셋째", "넷째"), remaining.map { it.question })
    }

    /** 바꿀 것이 없으면 같은 객체를 그대로 둔다 — 목록이 통째로 새 값이 되면 화면이 전부 다시 그린다. */
    @Test
    fun `번호가 이미 맞으면 그대로 둔다`() {
        val items = listOf(draft(1, "첫째"), draft(2, "둘째"))

        val renumbered = items.renumbered()

        assertSame(items[0], renumbered[0])
        assertSame(items[1], renumbered[1])
    }

    /** 글자 수 제한을 못 찾은 문항에 400 이나 600 을 채워 넣지 않는다 — 공고가 정한 값과 구분되지 않는다. */
    @Test
    fun `글자 수 제한이 없으면 null 로 남는다`() {
        val drafts = listOf(PostingFormQuestion(order = 1, question = "문항", maxChars = null)).toItemDrafts()

        assertNull(drafts.single().maxChars)
    }

    @Test
    fun `빈 질문은 문항이 될 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { ApplicationItemDraft(order = 1, question = "  ", maxChars = null) }
        assertThrows(IllegalArgumentException::class.java) { ApplicationItemDraft(order = 0, question = "문항", maxChars = null) }
    }

    @Test
    fun `질문 검증은 빈 값과 길이만 본다`() {
        assertEquals(ProfileFieldViolation.Required, ApplicationItemRules.validateQuestion("   "))
        assertEquals(
            ProfileFieldViolation.TooLong(ApplicationItemRules.MAX_QUESTION_LENGTH),
            ApplicationItemRules.validateQuestion("가".repeat(ApplicationItemRules.MAX_QUESTION_LENGTH + 1)),
        )
        assertNull(ApplicationItemRules.validateQuestion(" 지원 동기를 작성해 주세요 "))
    }

    /** 공고가 글자 수를 안 적는 일은 흔하다 — 빈 값을 위반으로 만들면 그런 공고는 초안을 못 만든다. */
    @Test
    fun `글자 수 제한은 빈 값이 위반이 아니고 범위 밖만 막는다`() {
        assertNull(ApplicationItemRules.validateMaxChars(""))
        assertNull(ApplicationItemRules.validateMaxChars(" 500 "))
        assertEquals(ProfileFieldViolation.InvalidFormat, ApplicationItemRules.validateMaxChars("오백"))
        assertEquals(ProfileFieldViolation.OutOfRange, ApplicationItemRules.validateMaxChars("10"))
        assertEquals(ProfileFieldViolation.OutOfRange, ApplicationItemRules.validateMaxChars("99999"))
    }

    @Test
    fun `범위 밖 입력은 제한 없음으로 읽는다`() {
        assertEquals(500, ApplicationItemRules.parseMaxChars(" 500 "))
        assertNull(ApplicationItemRules.parseMaxChars(""))
        assertNull(ApplicationItemRules.parseMaxChars("99999"))
    }

    @Test
    fun `기본 생성 길이는 명세가 정한 400에서 600이다`() {
        assertEquals(400, ApplicationItemRules.DEFAULT_MIN_CHARS)
        assertEquals(600, ApplicationItemRules.DEFAULT_MAX_CHARS)
    }

    private fun draft(
        order: Int,
        question: String,
    ) = ApplicationItemDraft(order = order, question = question, maxChars = null)
}

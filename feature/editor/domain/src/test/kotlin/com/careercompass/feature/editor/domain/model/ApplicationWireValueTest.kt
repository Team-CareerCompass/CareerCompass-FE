package com.careercompass.feature.editor.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 계약 문자열이 한 자만 어긋나도 서버가 값을 못 읽는다 — API_SPEC v0.1 §6 의 값을 그대로 고정한다. */
class ApplicationWireValueTest {
    @Test
    fun `지원서 상태의 계약 문자열`() {
        assertEquals(
            listOf("generating", "ready", "partial_failed", "saved"),
            ApplicationStatus.entries.map { it.wireValue },
        )
        assertEquals(ApplicationStatus.PartialFailed, ApplicationStatus.fromWireValue("partial_failed"))
        assertNull(ApplicationStatus.fromWireValue("done"))
    }

    @Test
    fun `어조와 결과의 계약 문자열`() {
        assertEquals(listOf("formal", "casual"), ApplicationTone.entries.map { it.wireValue })
        assertEquals(listOf("pending", "pass", "fail", "none"), ApplicationResult.entries.map { it.wireValue })
        assertEquals(ApplicationResult.Pass, ApplicationResult.fromWireValue("pass"))
        assertNull(ApplicationTone.fromWireValue("polite"))
    }

    /**
     * 항목 상태만 null 을 돌려주지 않는다. 모르는 값을 「아직 쓰는 중」이나 「완료」로 읽으면 화면이 오지 않을
     * 답을 기다리거나 빈 답을 완성으로 보여 준다. 실패로 읽으면 재생성 버튼이 뜨고 사용자가 고칠 수 있다.
     */
    @Test
    fun `모르는 항목 상태는 실패로 읽는다`() {
        assertEquals(ApplicationItemStatus.Loading, ApplicationItemStatus.fromWireValue("loading"))
        assertEquals(ApplicationItemStatus.Done, ApplicationItemStatus.fromWireValue("done"))
        assertEquals(ApplicationItemStatus.Failed, ApplicationItemStatus.fromWireValue("queued"))
        assertEquals(ApplicationItemStatus.Failed, ApplicationItemStatus.fromWireValue(""))
    }
}

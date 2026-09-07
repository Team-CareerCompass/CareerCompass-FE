package com.careercompass.feature.editor.data.mapper

import com.careercompass.core.network.sse.ServerSentEvent
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationStreamEventMapperTest {
    private val mapper = ApplicationStreamEventMapper(Json { ignoreUnknownKeys = true })

    @Test
    fun `item_done 은 항목 id 와 답을 읽는다`() {
        val event = mapper.toEvent(ServerSentEvent(event = "item_done", data = """{"itemId": 2, "answer": "받은 답"}"""))

        assertEquals(ApplicationStreamEvent.ItemDone(itemId = 2L, answer = "받은 답"), event)
    }

    @Test
    fun `item_failed 는 에러 코드를 함께 읽고 없으면 null 이다`() {
        assertEquals(
            ApplicationStreamEvent.ItemFailed(itemId = 3L, code = "LLM_UNAVAILABLE"),
            mapper.toEvent(ServerSentEvent(event = "item_failed", data = """{"itemId":3,"code":"LLM_UNAVAILABLE"}""")),
        )
        assertEquals(
            ApplicationStreamEvent.ItemFailed(itemId = 3L, code = null),
            mapper.toEvent(ServerSentEvent(event = "item_failed", data = """{"itemId":3}""")),
        )
    }

    @Test
    fun `status 는 계약의 네 값만 사건이 된다`() {
        assertEquals(
            ApplicationStreamEvent.StatusChanged(ApplicationStatus.Ready),
            mapper.toEvent(ServerSentEvent(event = "status", data = """{"status":"ready"}""")),
        )
        assertNull(mapper.toEvent(ServerSentEvent(event = "status", data = """{"status":"queued"}""")))
    }

    /** 서버가 이벤트를 늘려도 옛 앱의 스트림이 죽지 않아야 한다. */
    @Test
    fun `모르는 이벤트 이름은 버린다`() {
        assertNull(mapper.toEvent(ServerSentEvent(event = "heartbeat", data = """{"at":1}""")))
        assertNull(mapper.toEvent(ServerSentEvent(event = "message", data = "hello")))
    }

    /**
     * 덩어리 하나가 이상하다고 예외를 던지면 이미 받은 항목까지 버려진다 — 사용자에게는 다 쓴 자소서가 통째로
     * 사라지는 일이다. 버려서 잃는 답 하나는 스트림이 닫힐 때 실패로 굳어 재생성 버튼이 뜬다.
     */
    @Test
    fun `읽을 수 없는 본문은 버리고 흐름을 끊지 않는다`() {
        assertNull(mapper.toEvent(ServerSentEvent(event = "item_done", data = "not json")))
        assertNull(mapper.toEvent(ServerSentEvent(event = "item_done", data = """{"itemId":"둘"}""")))
        assertNull(mapper.toEvent(ServerSentEvent(event = "item_done", data = """{"answer":"항목 id 가 없다"}""")))
    }
}

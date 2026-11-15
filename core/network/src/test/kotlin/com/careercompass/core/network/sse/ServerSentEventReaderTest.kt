package com.careercompass.core.network.sse

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ServerSentEventReaderTest {
    @Test
    fun `빈 줄이 덩어리를 끊고 event 와 data 를 함께 낸다`() =
        runTest {
            val events =
                body(
                    """
                    event: item_done
                    data: {"itemId": 2, "answer": "..."}

                    """.trimIndent() + "\n",
                ).asServerSentEvents().toList()

            assertEquals(listOf(ServerSentEvent(event = "item_done", data = """{"itemId": 2, "answer": "..."}""")), events)
        }

    /** 스펙이 정한 기본값이다. 이름이 없다고 덩어리를 버리면 서버가 `event:` 를 생략한 순간 스트림이 침묵한다. */
    @Test
    fun `event 필드가 없으면 message 로 읽는다`() =
        runTest {
            val events = body("data: hello\n\n").asServerSentEvents().toList()

            assertEquals(listOf(ServerSentEvent(event = DEFAULT_SERVER_SENT_EVENT, data = "hello")), events)
        }

    @Test
    fun `data 가 여러 줄이면 개행으로 이어 하나로 낸다`() =
        runTest {
            val events = body("data: first\ndata: second\n\n").asServerSentEvents().toList()

            assertEquals("first\nsecond", events.single().data)
        }

    /** 하트비트가 그 모양이다. 빈 덩어리를 흘려보내면 소비자가 사건 없는 사건을 세게 된다. */
    @Test
    fun `주석만 온 덩어리는 내보내지 않는다`() =
        runTest {
            val events = body(": keep-alive\n\ndata: real\n\n").asServerSentEvents().toList()

            assertEquals(listOf("real"), events.map { it.data })
        }

    @Test
    fun `모르는 필드는 버리고 나머지를 그대로 낸다`() =
        runTest {
            val events = body("retry: 3000\nid: 7\ndata: ok\n\n").asServerSentEvents().toList()

            assertEquals(ServerSentEvent(event = DEFAULT_SERVER_SENT_EVENT, data = "ok", id = "7"), events.single())
        }

    /** 콜론 뒤 공백 **하나**만 구분자다. 두 칸째부터는 값이라 지우면 안 된다. */
    @Test
    fun `콜론 뒤 공백은 하나만 뗀다`() =
        runTest {
            val events = body("data:  두 칸\n\n").asServerSentEvents().toList()

            assertEquals(" 두 칸", events.single().data)
        }

    @Test
    fun `빈 줄 없이 끝난 마지막 덩어리는 버린다`() =
        runTest {
            val events = body("data: complete\n\ndata: truncated\n").asServerSentEvents().toList()

            assertEquals(listOf("complete"), events.map { it.data })
        }

    /**
     * 끊긴 스트림은 예외로 끝나되, **이미 읽은 덩어리는 그 예외보다 먼저 닿아야 한다.**
     *
     * 이것이 이 파일에서 가장 지킬 값이 큰 성질이다. 사이에 채널을 끼우면(`flowOn`) 생산자가 실패하는 순간
     * `coroutineScope` 가 소비자를 취소해 채널에 남은 덩어리가 사라진다 — 게다가 경합이라 어떤 실행에서는
     * 살고 어떤 실행에서는 죽는다. 사용자에게는 다 만들어진 자소서 항목이 통째로 없어지는 일이다.
     */
    @Test
    fun `중간에 끊기면 이미 낸 덩어리 뒤에 IOException 이 나온다`() =
        runTest {
            val received = mutableListOf<ServerSentEvent>()

            val thrown =
                runCatching {
                    failingBody("event: item_done\ndata: {\"itemId\":1}\n\n")
                        .asServerSentEvents()
                        .collect { received += it }
                }.exceptionOrNull()

            assertEquals(listOf("item_done"), received.map { it.event })
            assertTrue("expected IOException but was $thrown", thrown is IOException)
            assertTrue(thrown?.message.orEmpty().contains("reset"))
        }

    /** 닫지 않으면 OkHttp 연결이 풀에 돌아가지 못하고 샌다 — 스트림은 오래 열려 있어 더 아프다. */
    @Test
    fun `흐름이 끝나면 응답 본문을 닫는다`() =
        runTest {
            var closed = false
            val body = closeRecordingBody("data: one\n\n") { closed = true }

            body.asServerSentEvents().toList()

            assertTrue(closed)
        }

    private fun body(text: String): ResponseBody = text.toResponseBody(EVENT_STREAM)

    /** [prefix] 를 흘려보낸 뒤 [IOException] 으로 끊기는 본문 — 서버가 중간에 사라진 모양. */
    private fun failingBody(prefix: String): ResponseBody {
        val source =
            object : Source {
                private val buffer = Buffer().writeUtf8(prefix)

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    if (buffer.size > 0L) return buffer.read(sink, byteCount)
                    throw IOException("connection reset")
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() = Unit
            }
        return source.buffer().asResponseBody(EVENT_STREAM, -1L)
    }

    /** 닫힘을 [onClose] 로 알리는 본문. */
    private fun closeRecordingBody(
        text: String,
        onClose: () -> Unit,
    ): ResponseBody {
        val source =
            object : Source {
                private val buffer = Buffer().writeUtf8(text)

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long = if (buffer.size > 0L) buffer.read(sink, byteCount) else -1L

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() = onClose()
            }
        return source.buffer().asResponseBody(EVENT_STREAM, -1L)
    }

    private companion object {
        val EVENT_STREAM = "text/event-stream".toMediaType()
    }
}

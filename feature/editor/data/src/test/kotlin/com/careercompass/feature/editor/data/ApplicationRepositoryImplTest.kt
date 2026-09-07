package com.careercompass.feature.editor.data

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.network.dto.ApplicationDto
import com.careercompass.core.network.dto.ApplicationItemDto
import com.careercompass.core.network.dto.ApplicationListDto
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.feature.editor.data.mapper.ApplicationStreamEventMapper
import com.careercompass.feature.editor.data.support.FakeApplicationApiService
import com.careercompass.feature.editor.data.support.FakeApplicationStreamApiService
import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.model.ApplicationTone
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class ApplicationRepositoryImplTest {
    private val apiService = FakeApplicationApiService()
    private val streamService = FakeApplicationStreamApiService()
    private val repository =
        ApplicationRepositoryImpl(
            applicationApiService = apiService,
            applicationStreamApiService = streamService,
            streamEventMapper = ApplicationStreamEventMapper(Json { ignoreUnknownKeys = true }),
        )

    @Test
    fun `초안 생성은 어조를 계약 문자열로 실어 보내고 응답을 도메인으로 옮긴다`() =
        runTest {
            apiService.onCreate = { BaseResponse(ok = true, data = applicationDto()) }

            val draft = repository.createDraft(postingId = 101L, tone = ApplicationTone.Casual).getOrThrow()

            assertEquals("casual", apiService.createRequests.single().tone)
            assertEquals(101L, apiService.createRequests.single().postingId)
            assertEquals(42L, draft.id)
            assertEquals(ApplicationStatus.Generating, draft.status)
        }

    /** §9 표를 두 벌 쓰지 않는다는 것을 여기서 고정한다 — 옮긴 `mapDataFailure` 를 그대로 지난다. */
    @Test
    fun `LLM 장애 503 은 서비스 불가 사유가 된다`() =
        runTest {
            apiService.onCreate = { throw apiException(code = "LLM_UNAVAILABLE", status = 503) }

            val failure = repository.createDraft(101L, ApplicationTone.Formal).exceptionOrNull()

            assertTrue("expected ServiceUnavailable but was $failure", failure is CoreDataFailure.ServiceUnavailable)
            assertEquals("LLM_UNAVAILABLE", (failure as CoreDataFailure).code)
        }

    @Test
    fun `호출 한도 429 는 rate limited 사유가 된다`() =
        runTest {
            apiService.onRegenerate = { _, _, _ -> throw apiException(code = "RATE_LIMITED", status = 429) }

            val failure = repository.regenerateItem(42L, 2L, tone = null, emphasizeCardIds = emptyList()).exceptionOrNull()

            assertTrue("expected RateLimited but was $failure", failure is CoreDataFailure.RateLimited)
        }

    /** 빈 배열은 「강조할 카드가 없다」, 미전송은 「고르지 않았다」 — 서버가 둘을 다르게 읽을 여지를 두지 않는다. */
    @Test
    fun `강조 카드가 없으면 필드를 아예 보내지 않는다`() =
        runTest {
            apiService.onRegenerate = { _, _, _ -> BaseResponse(ok = true, data = itemDto()) }

            repository.regenerateItem(42L, 2L, tone = null, emphasizeCardIds = emptyList())

            assertNull(apiService.regenerateRequests.single().emphasizeCardIds)
            assertNull(apiService.regenerateRequests.single().tone)
        }

    @Test
    fun `이력 조회는 상태를 계약 문자열로 좁힌다`() =
        runTest {
            apiService.onGetApplications = { _, _, _ ->
                BaseResponse(ok = true, data = ApplicationListDto(applications = listOf(applicationDto()), nextCursor = "eyJ"))
            }

            val page = repository.getApplications(ApplicationStatus.Saved, cursor = "prev", limit = 20).getOrThrow()

            assertEquals(Triple("saved", "prev", 20), apiService.historyQueries.single())
            assertEquals("eyJ", page.nextCursor)
            assertEquals(1, page.applications.size)
        }

    @Test
    fun `결과 입력은 계약 문자열을 보낸다`() =
        runTest {
            apiService.onUpdateResult = { _, body ->
                assertEquals("pass", body.result)
                BaseResponse(ok = true, data = applicationDto(status = "saved", result = "pass"))
            }

            val draft = repository.updateResult(42L, ApplicationResult.Pass).getOrThrow()

            assertEquals(ApplicationResult.Pass, draft.result)
        }

    @Test
    fun `스트림은 SSE 덩어리를 도메인 사건으로 흘려보낸다`() =
        runTest {
            streamService.onStream = {
                body(
                    ": keep-alive\n\n" +
                        "event: item_done\ndata: {\"itemId\": 2, \"answer\": \"받은 답\"}\n\n" +
                        "event: status\ndata: {\"status\": \"ready\"}\n\n",
                )
            }

            val events = repository.streamEvents(42L).toList()

            assertEquals(
                listOf(
                    ApplicationStreamEvent.ItemDone(itemId = 2L, answer = "받은 답"),
                    ApplicationStreamEvent.StatusChanged(ApplicationStatus.Ready),
                ),
                events,
            )
        }

    /**
     * 하나라도 받은 뒤 끊긴 것은 「연결이 안 됐다」와 다르다 — 서버는 초안을 만들고 있고 받은 항목은
     * 유효하므로, 화면이 받은 데까지 보여 주고 이어 받는 길을 내야 한다.
     */
    @Test
    fun `덩어리를 받은 뒤 끊기면 스트림 중단 사유가 된다`() =
        runTest {
            streamService.onStream = { failingBody("event: item_done\ndata: {\"itemId\":1,\"answer\":\"첫 답\"}\n\n") }
            val received = mutableListOf<ApplicationStreamEvent>()

            val failure = runCatching { repository.streamEvents(42L).collect { received += it } }.exceptionOrNull()

            // 이미 온 덩어리가 예외보다 먼저 닿는다 — 채널을 끼우면 여기서 사라진다(`asServerSentEvents` 주석).
            assertEquals(1, received.size)
            assertTrue("expected StreamInterrupted but was $failure", failure is EditorFailure.StreamInterrupted)
        }

    /** 하나도 못 받고 끊긴 것은 연결 실패라 다른 조회와 같은 사유여야 한다 — 화면이 같은 안내를 내야 하기 때문이다. */
    @Test
    fun `덩어리를 하나도 못 받고 끊기면 네트워크 실패 사유가 된다`() =
        runTest {
            val cause = UnknownHostException("offline")
            streamService.onStream = { throw cause }

            val failure = runCatching { repository.streamEvents(42L).toList() }.exceptionOrNull()

            assertTrue("expected NetworkUnavailable but was $failure", failure is CoreDataFailure.NetworkUnavailable)
            assertSame(cause, (failure as CoreDataFailure.NetworkUnavailable).transportCause)
        }

    @Test
    fun `스트림을 여는 순간의 서버 실패도 도메인 사유가 된다`() =
        runTest {
            streamService.onStream = { throw apiException(code = "LLM_UNAVAILABLE", status = 503) }

            val failure = runCatching { repository.streamEvents(42L).toList() }.exceptionOrNull()

            assertTrue("expected ServiceUnavailable but was $failure", failure is CoreDataFailure.ServiceUnavailable)
        }

    /** 흐름을 두 번 구독하면 두 번째가 첫 구독의 기록을 물려받아 실패 사유가 뒤바뀔 수 있다. */
    @Test
    fun `두 번째 구독은 첫 구독의 수신 기록을 물려받지 않는다`() =
        runTest {
            val flow = repository.streamEvents(42L)
            streamService.onStream = { failingBody("event: item_done\ndata: {\"itemId\":1,\"answer\":\"첫 답\"}\n\n") }
            runCatching { flow.toList() }

            streamService.onStream = { throw UnknownHostException("offline") }
            val failure = runCatching { flow.toList() }.exceptionOrNull()

            assertTrue("expected NetworkUnavailable but was $failure", failure is CoreDataFailure.NetworkUnavailable)
        }

    private fun apiException(
        code: String,
        status: Int,
    ) = ApiException(code = code, serverMessage = null, fallbackMessage = "실패", status = status)

    private fun itemDto(
        id: Long = 2L,
        order: Int = 2,
        status: String = "done",
        answer: String? = "답",
    ) = ApplicationItemDto(id = id, order = order, question = "문항", maxChars = 500, status = status, answer = answer)

    private fun applicationDto(
        status: String = "generating",
        result: String? = null,
    ) = ApplicationDto(id = 42L, status = status, items = listOf(itemDto()), postingId = 101L, result = result)

    private fun body(text: String): ResponseBody = text.toResponseBody(EVENT_STREAM)

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

    private companion object {
        val EVENT_STREAM = "text/event-stream".toMediaType()
    }
}

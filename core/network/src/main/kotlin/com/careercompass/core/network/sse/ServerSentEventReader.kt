package com.careercompass.core.network.sse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.BufferedSource

/** `text/event-stream` 한 덩어리. [event] 는 `event:` 필드, 없으면 SSE 기본값 `message` 다. */
public data class ServerSentEvent(
    val event: String,
    val data: String,
    val id: String? = null,
)

/** `event:` 필드가 없는 덩어리의 기본 이름 — SSE 규격이 정한 값이다. */
public const val DEFAULT_SERVER_SENT_EVENT: String = "message"

/**
 * `@Streaming` 응답 본문을 SSE 덩어리로 자른다.
 *
 * **왜 라이브러리를 쓰지 않는가** — `okhttp-sse` 는 재연결(`retry:`·`Last-Event-ID`)까지 해 주지만, 우리
 * 스트림은 재연결이 옳은 복구가 아니다. 지원서 생성은 서버가 이미 만들어 둔 초안 하나에 붙는 것이라, 끊기면
 * 처음부터 다시 듣는 것이 아니라 `POST /applications` 로 지금 상태를 다시 받아야 한다
 * (`docs/spec/canon.md` 「지원서 규칙」 — 진행 중인 것이 있으면 서버가 그 id 를 돌려준다). 라이브러리를
 * 얹으면 그 판단이 라이브러리의 재연결 정책과 두 벌이 된다. 잘라 읽는 일 자체는 규격이 짧다.
 *
 * **왜 `flowOn` 이 아니라 [withContext] 인가** — `flowOn` 은 채널을 하나 끼우고, 그 채널을 만드는
 * `coroutineScope` 는 생산자가 실패하면 **소비자를 곧바로 취소한다.** 그래서 이미 채널에 들어간 덩어리가
 * 소비자에게 닿기 전에 사라진다 — 끊긴 스트림에서 「받은 데까지는 살린다」가 성립하지 않고, 게다가 경합이라
 * 어떤 실행에서는 살고 어떤 실행에서는 죽는다. 여기서는 **읽기만** IO 로 보내고 `emit` 은 수집자의 컨텍스트에
 * 그대로 둔다. 채널이 없으니 덩어리는 예외보다 반드시 먼저 도착한다. 대신 덩어리마다 디스패치가 한 번
 * 붙는데, SSE 는 덩어리가 항목 수만큼(보통 3~5개)이라 값이 없다.
 *
 * ### 규격에서 지키는 것
 * - 빈 줄이 덩어리의 끝이다. 그때까지 모인 `data:` 줄을 개행으로 이어 하나로 낸다.
 * - `data` 가 하나도 없는 덩어리는 내보내지 않는다 — 주석(`:` 로 시작하는 줄)만 온 하트비트가 그 모양이다.
 * - 콜론 뒤 공백 하나는 구분자라 값에서 뺀다. 마지막 `data:` 줄이 남긴 개행도 값이 아니라 이음쇠라 뺀다.
 * - 모르는 필드 이름은 버린다. 서버가 필드를 늘려도 스트림이 죽지 않아야 한다.
 * - 빈 줄 없이 끝난 마지막 덩어리는 버린다. 끊긴 자리의 반쪽짜리 값이다.
 *
 * 본문은 [ResponseBody.use] 로 감싸 흐름이 끝나면 닫힌다 — 닫지 않으면 OkHttp 연결이 풀에 돌아가지 못하고
 * 샌다. 다만 **읽는 도중의 취소는 즉시 닿지 않는다**: 블로킹 읽기는 소켓이 응답하거나 OkHttp 의 call timeout
 * (`LongRunningOperation.ApplicationStream`)이 끊을 때까지 그 스레드에 머문다. 상한이 그래서 필요하다.
 */
public fun ResponseBody.asServerSentEvents(): Flow<ServerSentEvent> =
    flow {
        use { body ->
            val source = body.source()
            while (true) {
                currentCoroutineContext().ensureActive()
                val event = withContext(Dispatchers.IO) { source.readServerSentEvent() } ?: break
                emit(event)
            }
        }
    }

/** 빈 줄이 나올 때까지 읽어 덩어리 하나를 만든다. 덩어리를 못 채우고 스트림이 끝나면 null. */
private fun BufferedSource.readServerSentEvent(): ServerSentEvent? {
    var event: String? = null
    var id: String? = null
    val data = StringBuilder()

    while (true) {
        val line = readUtf8Line() ?: return null

        if (line.isEmpty()) {
            if (data.isEmpty()) {
                event = null
                id = null
                continue
            }
            return ServerSentEvent(
                event = event ?: DEFAULT_SERVER_SENT_EVENT,
                data = data.toString().removeSuffix("\n"),
                id = id,
            )
        }
        if (line.startsWith(COMMENT_PREFIX)) continue

        val separator = line.indexOf(FIELD_SEPARATOR)
        val field = if (separator == -1) line else line.substring(0, separator)
        val value = if (separator == -1) "" else line.substring(separator + 1).removePrefix(" ")

        when (field) {
            FIELD_EVENT -> event = value
            FIELD_DATA -> data.append(value).append('\n')
            FIELD_ID -> id = value
            else -> Unit
        }
    }
}

private const val COMMENT_PREFIX = ":"
private const val FIELD_SEPARATOR = ':'
private const val FIELD_EVENT = "event"
private const val FIELD_DATA = "data"
private const val FIELD_ID = "id"

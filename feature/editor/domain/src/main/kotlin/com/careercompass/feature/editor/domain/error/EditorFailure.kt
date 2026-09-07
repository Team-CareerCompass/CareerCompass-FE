package com.careercompass.feature.editor.domain.error

/**
 * 지원서 도메인이 **서버 사유로 환원되지 않는** 실패로 쓰는 값.
 *
 * 서버가 돌려준 실패는 `CoreDataFailure`(API_SPEC §9) 로 그대로 흐른다 — 503 `LLM_UNAVAILABLE` 은
 * `ServiceUnavailable`, 429 `RATE_LIMITED` 는 `RateLimited` 다. 여기 있는 것은 그 표에 자리가 없는 둘뿐이다.
 */
public sealed class EditorFailure(
    message: String,
    cause: Throwable?,
) : Exception(message, cause) {
    /**
     * 열려 있던 SSE 스트림이 도중에 끊겼다.
     *
     * 요청이 실패한 것과 다르다 — **이미 받은 항목은 유효하다.** 그래서 전송 실패(`NetworkUnavailable`)로
     * 접지 않는다. 화면은 받은 데까지 보여 주고 이어 받는 길을 내야 하고, 그 길은 재연결이 아니라
     * `POST /applications` 다(`docs/spec/canon.md` 「지원서 규칙」).
     */
    public class StreamInterrupted(
        cause: Throwable,
    ) : EditorFailure("application stream interrupted", cause)

    /**
     * 답이 그 문항의 글자 상한을 넘었다 — 요청을 보내기 전에 확정한다(F4-2).
     *
     * 서버도 거절하겠지만, 왕복을 기다려 「저장 실패」를 보여 주면 사용자는 무엇이 문제인지 모른다.
     */
    public class AnswerTooLong(
        public val maxChars: Int,
        public val actualChars: Int,
    ) : EditorFailure("answer too long ($actualChars > $maxChars)", cause = null)
}

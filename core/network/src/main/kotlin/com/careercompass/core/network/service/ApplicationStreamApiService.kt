package com.careercompass.core.network.service

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * API_SPEC v0.1 §6 — `GET /applications/{id}/stream` (SSE).
 *
 * 나머지 §6 호출과 **서비스를 나눈 이유는 타임아웃 하나**다. 스트림은 항목마다 LLM 을 부르는 동안 열려
 * 있으므로 분 단위로 이어지고, 일반 API 의 call timeout 30초를 그대로 쓰면 서버가 아직 쓰는 중에 우리가
 * 먼저 끊는다 — 게시판 구조 감지가 같은 이유로 실패한 적이 있다(#134). 값과 판단은
 * `LongRunningOperation.ApplicationStream` 에 있다.
 *
 * `@Streaming` 이라 응답 본문을 메모리에 모으지 않는다. 이것이 없으면 Retrofit 이 스트림이 **닫힐 때까지**
 * 기다렸다가 통째로 넘겨, 실시간 진행이라는 목적 자체가 사라진다. 본문을 이벤트로 자르는 것은
 * `ServerSentEventReader` 가 한다.
 */
public interface ApplicationStreamApiService {
    @Streaming
    @GET("applications/{id}/stream")
    public suspend fun stream(
        @Path("id") applicationId: Long,
    ): ResponseBody
}

package com.careercompass.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API_SPEC v0.1 §6 지원서.
 *
 * `status` 는 `generating` / `ready` / `partial_failed` / `saved`. 문자열 그대로 두고 도메인 쪽에서 가른다 —
 * DTO 가 enum 을 선언하면 서버가 값을 하나 늘렸을 때 **응답 전체가** 파싱 실패로 떨어진다.
 *
 * [postingId] · [result] · [createdAt] · [savedAt] 은 §6 의 `POST /applications` 응답 예시에 없다. 생성
 * 요청이 `postingId` 를 싣고 `PATCH /applications/{id}/result` 가 결과를 받으므로 서버가 값을 가진 것은
 * 확실하지만, 어느 응답이 그것을 돌려주는지는 스펙이 말하지 않는다. **nullable 로 두어 「안 왔다」와 「없다」를
 * 같은 값으로 받는다** — non-null 로 선언하고 기본값을 두면 계약 누락이 정상값으로 위장한다.
 */
@Serializable
data class ApplicationDto(
    @SerialName("id")
    val id: Long,
    @SerialName("status")
    val status: String,
    @SerialName("items")
    val items: List<ApplicationItemDto>,
    @SerialName("postingId")
    val postingId: Long? = null,
    @SerialName("result")
    val result: String? = null,
    @SerialName("createdAt")
    val createdAt: String? = null,
    @SerialName("savedAt")
    val savedAt: String? = null,
)

/** §6 지원서 항목. `status` 는 응답 예시에 `done` · `loading` 만 나온다. */
@Serializable
data class ApplicationItemDto(
    @SerialName("id")
    val id: Long,
    @SerialName("order")
    val order: Int,
    @SerialName("question")
    val question: String,
    @SerialName("maxChars")
    val maxChars: Int,
    @SerialName("status")
    val status: String,
    @SerialName("answer")
    val answer: String? = null,
)

/** `GET /applications` — 목록 키는 §4 와 같은 이름 규칙(`applications`)으로 가정한다. */
@Serializable
data class ApplicationListDto(
    @SerialName("applications")
    val applications: List<ApplicationDto>,
    @SerialName("nextCursor")
    val nextCursor: String? = null,
)

/**
 * `POST /applications`.
 *
 * [items] 는 **API_SPEC v0.1 §6 에 없는 필드**다. 계약은 `postingId` 와 `tone` 만 받는데, F4-1 은 인식된
 * 문항을 사용자가 고치고 더할 수 있게 정했고 인식 결과가 없으면 직접 쓰게 정했다 — 그 결과를 보낼 자리가
 * 없으면 화면이 받은 입력이 서버에 닿지 못한다. 판정과 서버에 요구할 것은 `docs/spec/canon.md` 의
 * 「지원서 문항 확정의 계약」에 적었다.
 *
 * 기본값이 null 이라 **고치지 않은 요청에서는 직렬화되지 않는다**(`encodeDefaults = false`). 즉 이 필드가
 * 서버에 도착하는 것은 사용자가 실제로 문항을 손봤을 때뿐이고, 그전까지의 요청은 지금 계약과 한 글자도
 * 다르지 않다.
 */
@Serializable
data class CreateApplicationRequestDto(
    @SerialName("postingId")
    val postingId: Long,
    @SerialName("tone")
    val tone: String,
    @SerialName("items")
    val items: List<ApplicationItemRequestDto>? = null,
)

/** 사용자가 확정한 문항 하나. `maxChars` 가 없으면 서버가 400~600자로 정한다(F4-2). */
@Serializable
data class ApplicationItemRequestDto(
    @SerialName("order")
    val order: Int,
    @SerialName("question")
    val question: String,
    @SerialName("maxChars")
    val maxChars: Int? = null,
)

/** `POST /applications/{id}/items/{itemId}/regenerate` — 둘 다 옵션이라 없으면 직렬화되지 않는다. */
@Serializable
data class RegenerateItemRequestDto(
    @SerialName("tone")
    val tone: String? = null,
    @SerialName("emphasizeCardIds")
    val emphasizeCardIds: List<Long>? = null,
)

/** `PATCH /applications/{id}/items/{itemId}`. */
@Serializable
data class UpdateItemAnswerRequestDto(
    @SerialName("answer")
    val answer: String,
)

/** `PATCH /applications/{id}/result` — 값은 `pending` / `pass` / `fail` / `none`. */
@Serializable
data class UpdateApplicationResultRequestDto(
    @SerialName("result")
    val result: String,
)

/**
 * `event: item_done` 의 `data` — API_SPEC v0.1 §6 이 유일하게 이름까지 적어 둔 스트림 덩어리.
 *
 * 나머지 두 덩어리([ApplicationStreamItemFailedDto] · [ApplicationStreamStatusDto])는 스펙에 없다.
 * 판정과 서버에 요구할 목록은 `docs/spec/canon.md` 「지원서 스트림의 계약」에 있다.
 */
@Serializable
data class ApplicationStreamItemDoneDto(
    @SerialName("itemId")
    val itemId: Long,
    @SerialName("answer")
    val answer: String,
)

/** `event: item_failed` 의 `data` — [code] 는 §9 에러 코드. */
@Serializable
data class ApplicationStreamItemFailedDto(
    @SerialName("itemId")
    val itemId: Long,
    @SerialName("code")
    val code: String? = null,
)

/** `event: status` 의 `data` — 지원서 전체 상태가 스트림 도중에 바뀐 것을 알린다. */
@Serializable
data class ApplicationStreamStatusDto(
    @SerialName("status")
    val status: String,
)

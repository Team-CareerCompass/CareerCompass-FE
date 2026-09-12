package com.careercompass.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /feed/for-you` — API_SPEC v0.1 §7.
 *
 * [topPick] 은 nullable 이다. 추천이 하나도 없을 때 서버가 무엇을 보낼지 스펙이 말하지 않으므로, 「안 왔다」를
 * 빈 객체로 만들어 내지 않고 그대로 없음으로 받는다.
 */
@Serializable
data class ForYouFeedDto(
    @SerialName("topPick")
    val topPick: ForYouTopPickDto? = null,
    @SerialName("byStrength")
    val byStrength: List<ForYouRecommendationDto>,
    @SerialName("byGap")
    val byGap: List<ForYouRecommendationDto>,
)

/**
 * 톱 픽 — **`reason` 이 배열이다.**
 *
 * 같은 절의 [ForYouRecommendationDto] 는 같은 이름의 필드를 문자열로 준다. 계약이 그렇게 적혀 있으므로
 * DTO 는 스키마 그대로 받고, 하나로 모으는 일은 도메인 매퍼가 한다 — 여기서 억지로 같은 타입으로 만들면
 * 둘 중 한쪽의 응답이 파싱 실패로 떨어진다. 불일치 자체는 `docs/spec/canon.md` 에 판정으로 남겼다.
 */
@Serializable
data class ForYouTopPickDto(
    @SerialName("postingId")
    val postingId: Long,
    @SerialName("reason")
    val reason: List<String>,
)

/** 강점 기반·취약점 보완 추천 — `reason` 이 문자열이다. */
@Serializable
data class ForYouRecommendationDto(
    @SerialName("postingId")
    val postingId: Long,
    @SerialName("reason")
    val reason: String,
)

/** `GET /roadmap/compare?cohort=...` — API_SPEC v0.1 §7. */
@Serializable
data class RoadmapCompareDto(
    @SerialName("cohort")
    val cohort: String,
    @SerialName("sampleSize")
    val sampleSize: Int,
    @SerialName("metrics")
    val metrics: List<RoadmapMetricDto>,
    @SerialName("suggestions")
    val suggestions: List<RoadmapSuggestionDto>,
)

/**
 * 비교 지표 한 줄.
 *
 * 두 값 모두 `Double` 이다 — 스펙 예시가 `"me": 4` 와 `"peerAvg": 0.8` 을 같은 표에 섞어 놓았다. `Int` 로
 * 받으면 평균값이 오는 순간 파싱이 깨진다.
 */
@Serializable
data class RoadmapMetricDto(
    @SerialName("name")
    val name: String,
    @SerialName("me")
    val me: Double,
    @SerialName("peerAvg")
    val peerAvg: Double,
)

/** 다음 학기 제안. [expectedLift] 는 적합도 상승 폭(점). */
@Serializable
data class RoadmapSuggestionDto(
    @SerialName("semester")
    val semester: String,
    @SerialName("action")
    val action: String,
    @SerialName("expectedLift")
    val expectedLift: Int,
)

/** `POST /export` — API_SPEC v0.1 §7. */
@Serializable
data class ExportRequestDto(
    @SerialName("format")
    val format: String,
    @SerialName("sections")
    val sections: List<String>,
)

@Serializable
data class ExportResultDto(
    @SerialName("format")
    val format: String,
    @SerialName("content")
    val content: String,
)

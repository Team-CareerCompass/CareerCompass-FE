package com.careercompass.feature.foryou.domain.model

/** 무엇과 견주는가 — API_SPEC v0.1 §7 의 `cohort`. */
public enum class RoadmapCohort(
    public val wireValue: String,
) {
    /** 같은 학과·학년 동기. */
    Peer("peer"),

    /** 합격 선배. */
    Senior("senior"),

    /** 내 성장 — 비교 대상이 과거의 나다. */
    MeOnly("me_only"),
    ;

    public companion object {
        public fun fromWireValue(value: String): RoadmapCohort? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 비교 지표 한 줄.
 *
 * 계약의 필드 이름은 `peerAvg` 지만 [cohortAverage] 로 받는다 — `cohort=senior` 나 `me_only` 일 때 그 값은
 * 동기 평균이 아니다. 화면이 이름을 그대로 읽으면 「선배 평균」 자리에 「동기 평균」이라 쓰게 된다.
 */
public data class RoadmapMetric(
    val name: String,
    val me: Double,
    val cohortAverage: Double,
) {
    /** 내가 앞서는가. 같으면 false — 「같다」를 「앞선다」로 보여 주지 않는다. */
    public val isAhead: Boolean
        get() = me > cohortAverage
}

/** 다음 학기 제안. [expectedLift] 는 적합도 상승 폭(점). */
public data class RoadmapSuggestion(
    val semester: String,
    val action: String,
    val expectedLift: Int,
)

/**
 * 로드맵 비교 한 화면치 — `GET /roadmap/compare`.
 *
 * [sampleSize] 는 비교 모집단의 크기다. **화면이 이 값을 보여야 한다** — 표본이 3명인 평균과 300명인 평균은
 * 같은 숫자라도 다른 말이고, 크기를 감추면 사용자가 우연을 추세로 읽는다.
 */
public data class RoadmapComparison(
    val cohort: RoadmapCohort,
    val sampleSize: Int,
    val metrics: List<RoadmapMetric>,
    val suggestions: List<RoadmapSuggestion>,
)

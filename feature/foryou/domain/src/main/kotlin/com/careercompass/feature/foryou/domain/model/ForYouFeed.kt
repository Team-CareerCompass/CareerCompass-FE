package com.careercompass.feature.foryou.domain.model

/** 추천이 놓이는 자리 — Figma 06 의 세 묶음. */
public enum class ForYouSection {
    /** 가장 잘 맞는 공고 한 건. 이유가 여러 줄이라 카드가 크다. */
    TopPick,

    /** 강점 기반 — 지금 가진 것으로 유리한 공고. */
    ByStrength,

    /** 취약점 보완 — 부족한 것을 채우기 좋은 공고. */
    ByGap,
}

/**
 * 추천 한 건 — API_SPEC v0.1 §7 `GET /feed/for-you`.
 *
 * **[reasons] 가 목록인 것이 이 타입의 이유다.** 계약은 같은 이름의 필드를 톱 픽에서는 배열로, 나머지에서는
 * 문자열로 준다(`topPick.reason` ↔ `byStrength[].reason`). 화면이 그 차이를 알면 카드 한 장을 그리는 코드가
 * 자리마다 갈라지므로, 여기서 한 벌로 모은다. 문자열 하나는 원소 하나짜리 목록이다.
 *
 * **[postingId] 뿐이라 카드에 쓸 제목·기관이 없다.** §7 이 공고 요약을 주지 않는다 — 그 빈 곳과 그로 인해
 * 화면이 못 하는 것은 `docs/spec/canon.md` 에 적었다.
 */
public data class ForYouRecommendation(
    val postingId: Long,
    val section: ForYouSection,
    val reasons: List<String>,
) {
    init {
        require(reasons.isNotEmpty()) { "reasons must not be empty" }
    }
}

/**
 * For You 한 화면치 — 세 묶음을 순서대로 든다.
 *
 * 톱 픽이 없을 수 있다(추천할 것이 없을 때). 그 경우를 [ForYouRecommendation] 하나로 만들어 내지 않고
 * null 로 둔다 — 없는 추천을 그리면 사용자는 이유 없는 카드를 본다.
 */
public data class ForYouFeed(
    val topPick: ForYouRecommendation?,
    val byStrength: List<ForYouRecommendation>,
    val byGap: List<ForYouRecommendation>,
) {
    /** 세 묶음이 모두 비었는가 — 화면의 빈 결과 판정이다. */
    public val isEmpty: Boolean
        get() = topPick == null && byStrength.isEmpty() && byGap.isEmpty()

    /** 톱 픽을 앞세운 전체 추천. 순서가 화면 순서다. */
    public val all: List<ForYouRecommendation>
        get() = listOfNotNull(topPick) + byStrength + byGap
}

/** 카테고리별 상한 — API_SPEC v0.1 §7 「카테고리별 최대 5건」. */
public const val MAX_FOR_YOU_ITEMS_PER_SECTION: Int = 5

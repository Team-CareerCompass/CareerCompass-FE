package com.careercompass.core.model.user

/**
 * 졸업 예정 연월 입력의 표기·범위 규칙 — 온보딩 Step 1 과 마이 탭의 프로필 편집이 같은 값을 쓴다(#176).
 *
 * 서버에 나가는 것은 **연도 하나**(`gradYear`)뿐이지만 화면은 `YYYY.MM` 을 받는다. 그 어긋남을 화면마다
 * 각자 다루면 「2027」과 「2027.02」가 같은 뜻인지 매번 다시 정하게 되므로, 표기와 범위를 한 곳에 모은다.
 * 문자열을 연도로 읽는 것은 [ProfileBasicInfoRules.parseGraduationYear] 가 갖는다 — 검증과 같은 자리다.
 */
public object GraduationDateRules {
    /**
     * 오늘로부터 몇 해 뒤까지 고를 수 있는가.
     *
     * 학부 신입생이 정상 졸업까지 4년, 휴학·복수전공으로 늘어나는 몫을 더해 6년으로 둔다. 상한을 없애면
     * 스크롤이 끝없이 길어지고, 4년으로 조이면 실제 사용자를 막는다.
     */
    public const val YEARS_AHEAD: Int = 6

    /** 고르지 않았을 때의 달 — 국내 학위수여식이 몰린 2월이다. */
    public const val DEFAULT_MONTH: Int = 2

    /** [currentYear] 기준으로 고를 수 있는 연도 목록(오름차순). */
    public fun yearsFrom(currentYear: Int): List<Int> = (MIN_GRADUATION_YEAR..currentYear + YEARS_AHEAD).toList()

    /** `YYYY.MM` 의 달. 달이 없거나(`YYYY`) 범위를 벗어나면 null 이다. */
    public fun parseMonth(value: String): Int? =
        value
            .trim()
            .substringAfter('.', missingDelimiterValue = "")
            .toIntOrNull()
            ?.takeIf { it in 1..12 }

    /** 화면이 보여 주는 표기. 서버에는 연도만 나가므로 이 문자열은 입력칸 안에서만 산다. */
    public fun format(
        year: Int,
        month: Int,
    ): String = "%04d.%02d".format(year, month)
}

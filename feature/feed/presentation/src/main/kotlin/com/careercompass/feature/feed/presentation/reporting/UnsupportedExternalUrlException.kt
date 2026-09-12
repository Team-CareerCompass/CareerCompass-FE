package com.careercompass.feature.feed.presentation.reporting

/**
 * 공고 원문 주소가 웹 주소가 아니어서 열지 않았다.
 *
 * 서버 응답 계약 위반이라 일시 오류가 아니라 결함으로 남긴다. 리포팅에 올라가는 것은 [reportAttributes] 가
 * 담은 스킴뿐이다. 주소 전체를 실으면 쿼리에 붙어 온 것까지 리포팅 콘솔에 남는다.
 */
internal class UnsupportedExternalUrlException(
    scheme: String?,
) : IllegalStateException("unsupported external url scheme: ${scheme ?: NO_SCHEME}") {
    /**
     * 리포팅에 실을 속성.
     *
     * 스킴을 예외 문구로만 들고 있으면 콘솔에는 스킴이 남지 않는다.
     * [com.careercompass.core.common.reporting.ErrorReporter.recordFailure] 가 문구를 버린 사본을 올리고
     * 속성으로는 예외 타입만 붙이기 때문이다(#368). 문구는 로컬 스택트레이스용으로만 남는다.
     */
    val reportAttributes: Map<String, String> = mapOf(FEED_REPORT_KEY_URL_SCHEME to (scheme ?: NO_SCHEME))
}

/** 스킴이 없는 값도 한 칸을 채운다. 속성이 빠지면 콘솔에서 스킴 없음과 기록 누락이 같아 보인다. */
private const val NO_SCHEME = "none"

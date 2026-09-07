package com.careercompass.feature.profile.presentation.reporting

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.reporting.recordStagedFailure

/** 리포팅 속성 키 — 프로필 기능 안에서 어느 단계가 실패했는지. */
public const val PROFILE_REPORT_KEY_STAGE: String = "profile_stage"

/** 프로필 기능의 실패 단계. 값은 리포팅 콘솔 필터용 안정 식별자다. */
public enum class ProfileFailureStage(
    public val key: String,
) {
    /** 마이 홈 첫 조회 — 캐시가 없어 화면이 실패로 덮인 경우다. */
    HomeLoad("home_load"),

    /** 캐시로 그린 채 새로고침만 실패한 경우. 화면을 흔들지 않는 조용한 실패라 [HomeLoad] 와 갈라 센다. */
    HomeRefresh("home_refresh"),

    BiometricToggle("biometric_toggle"),
    ThemeMode("theme_mode"),
    Logout("logout"),
}

/**
 * [ErrorReporter.recordFailure] 에 프로필 단계 속성을 붙여 기록한다. 취소 필터링은 인터페이스가 한다.
 *
 * 무엇을 접고 무엇을 남길지는 [recordStagedFailure] 한 곳이 정한다 — 온보딩·피드와 같은 규칙을 쓴다.
 */
public fun ErrorReporter.recordProfileFailure(
    stage: ProfileFailureStage,
    throwable: Throwable,
) {
    recordStagedFailure(
        stageKey = PROFILE_REPORT_KEY_STAGE,
        stage = stage.key,
        throwable = throwable,
    )
}

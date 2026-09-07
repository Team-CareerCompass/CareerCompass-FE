package com.careercompass.careercompass_fe.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 앱 셸이 소유하는 루트 Navigation 3 백스택의 키.
 *
 * 피처 그래프는 각자의 로컬 스택을 갖고([Onboarding] · [Feed] 가 그 host 다), 여기에는 그 host 와 다른 담당 모듈이
 * 진입점을 제공하기 전까지 셸이 대신 그리는 자리표시자만 둔다. `@Serializable` 은 프로세스 재생성 뒤 루트 스택을
 * 복원하는 데 쓰인다(#260).
 */
@Serializable
public sealed interface Route : NavKey {
    /** 온보딩 host — 로그인·지문·Step 1~4·완료를 담은 로컬 스택. 시작 화면은 셸이 고른다. */
    @Serializable
    public data object Onboarding : Route

    /** 피드 host — 하단 탭 「피드」이자 메인 루트. 홈·상세·원문·게시판을 담은 로컬 스택. */
    @Serializable
    public data object Feed : Route

    /** 하단 탭 「분석」(For You·커리어 로드맵·강점 Export) — foryou 모듈 몫. */
    @Serializable
    public data object AnalysisTab : Route

    /** 하단 탭 「지원서」(AI 초안·에디터·이력) — editor 모듈 몫. */
    @Serializable
    public data object ApplicationsTab : Route

    /**
     * 하단 탭 「마이」 — profile 모듈의 마이 홈([com.careercompass.feature.profile.presentation.home.ProfileHomeScreen]).
     *
     * 이 키만 탭이고, 마이 홈의 메뉴가 가리키는 네 화면은 아래 자리표시자로 그 위에 쌓인다.
     */
    @Serializable
    public data object MyTab : Route

    /** 피드 헤더의 알림 — notification 모듈 몫. */
    @Serializable
    public data object NotificationsPlaceholder : Route

    /**
     * 마이 홈 → 프로필 편집 — profile 모듈의 화면(#176).
     *
     * 마이 홈 메뉴가 가리키는 네 화면은 전부 피드 위 한 칸으로 쌓이므로 바텀바가 저절로 숨는다
     * ([AppState.shouldShowBottomBar] 의 `else`). 알림 화면([NotificationsPlaceholder])이 이미 쓰던 자리와
     * 같은 모양이라 판정을 새로 만들지 않는다. 아직 화면이 없는 셋은 자리표시자로 남는다.
     */
    @Serializable
    public data object ProfileEdit : Route

    /** 경험 카드 목록 — profile 모듈(#178). */
    @Serializable
    public data object ExperienceCards : Route

    /** 경험 카드 등록·수정 — profile 모듈(#179). 그 화면이 붙기 전까지 자리표시자다. */
    @Serializable
    public data object ExperienceCardEditorPlaceholder : Route

    /** 과거 지원서 관리 — profile 모듈(#180 · #181). */
    @Serializable
    public data object PastApplicationsPlaceholder : Route

    /** 알림 설정 — notification 모듈(#196). */
    @Serializable
    public data object NotificationSettingsPlaceholder : Route
}

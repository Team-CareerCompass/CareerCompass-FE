package com.careercompass.careercompass_fe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.careercompass.core.ui.component.CareerCompassBottomTab
import com.careercompass.core.ui.navigation.popUpTo
import com.careercompass.core.ui.navigation.pushSingleTop
import com.careercompass.core.ui.navigation.replaceAllWith

/**
 * 앱 셸의 루트 백스택과 그 조작 — 하단 탭 전환, 메인 진입, 피드 밖 화면 push, 한 칸 내리기, 그리고 바텀바 판정.
 *
 * 컴포저블이 아니라 평범한 클래스다 — 루트 스택의 모양을 컴포지션 없이 JVM 테스트(`AppStateTest`)로 못박는다(#260).
 *
 * 루트 스택의 모양은 넷이다.
 * - 인증 전: `[Onboarding]`. 온보딩 안의 화면은 온보딩 로컬 스택이 갖는다.
 * - 메인: `[Feed]` 또는 `[Feed, 다른 탭]`. 피드가 바닥이고 다른 탭은 그 위 한 칸이라, 다른 탭에서의 back 은 피드로 돌아간다.
 *   Nav2 의 `popUpTo(피드) { saveState }` + `restoreState` 가 만들던 모양과 같다. 다만 Nav3 는 스택에서 빠진 entry 의
 *   상태를 버리므로, 다른 탭은 다시 들어오면 새로 그려진다 — 피드 탭은 바닥에 남아 로컬 스택과 ViewModel 을 지킨다.
 * - 피드 위 한 칸: `[Feed, NotificationsPlaceholder]` · `[Feed, ApplicationSetup]` 처럼 탭이 아닌 화면.
 *   바텀바가 저절로 숨고([shouldShowBottomBar] 의 `else`) back 은 피드로 돌아간다.
 * - 마이 탭 위 한 칸: `[Feed, MyTab, ProfileEdit]` 처럼 마이 홈 메뉴가 가리키는 화면(`navigateToProfileMenu`).
 *   과거 지원서 목록에서 등록으로 들어가면 그 위가 한 칸 더 쌓여 네 칸이 된다.
 */
@Stable
public class AppState(
    public val backStack: NavBackStack<NavKey>,
) {
    /** 지금 보이는 루트 키. */
    public val topKey: NavKey? get() = backStack.lastOrNull()

    /**
     * 하단 탭을 그릴지.
     *
     * @param isFeedStackAtRoot 피드 로컬 스택이 바닥(피드 홈)인지. 피드의 루트 키는 [Route.Feed] 하나뿐이라 키만으로는
     *   상세·게시판이 쌓였는지 알 수 없다 — 깊이를 아는 host 가 올려 준다(#259).
     */
    public fun shouldShowBottomBar(
        topKey: NavKey?,
        isFeedStackAtRoot: Boolean,
    ): Boolean =
        when (topKey) {
            Route.Feed -> isFeedStackAtRoot
            Route.AnalysisTab, Route.ApplicationsTab, Route.MyTab -> true
            else -> false
        }

    public fun currentTab(topKey: NavKey?): CareerCompassBottomTab =
        TAB_KEYS.entries.firstOrNull { (_, key) -> key == topKey }?.key ?: CareerCompassBottomTab.Feed

    /** 탭 전환 — 피드를 바닥에 남기고 다른 탭은 그 위 한 칸으로 교체한다. 피드 위에 쌓인 자리표시자도 걷어낸다. */
    public fun navigateToTab(tab: CareerCompassBottomTab) {
        val key = TAB_KEYS.getValue(tab)
        backStack.popUpTo(Route.Feed)
        if (key != Route.Feed) backStack.add(key)
    }

    /** 인증·온보딩을 끝냈다 — 루트를 피드 하나로 수렴한다. 뒤로가기로 인증 화면에 돌아가지 않는다(앱 종료). */
    public fun navigateToMain(): Unit = backStack.replaceAllWith(Route.Feed)

    /**
     * 딥링크를 적용하려면 먼저 피드로 내려야 하는가(#337).
     *
     * 딥링크는 피드 host 안에서만 반영되는데 루트 `NavDisplay` 는 최상단 키만 그린다. `[Feed, MyTab]` ·
     * `[Feed, ApplicationSetup]` 처럼 다른 화면이 위에 있으면 host 가 컴포지션에 없어 알림을 눌러도 상세가
     * 열리지 않고 보관만 됐다. 나중에 피드 탭을 누르는 순간에야 떴다.
     *
     * 인증 흐름(`[Onboarding]`)에서는 내리지 않는다. 내리면 인증 게이트를 건너뛰므로, 인증을 마치고
     * [navigateToMain] 이 피드를 그릴 때까지 보관한다.
     */
    public fun shouldDescendToFeedForDeepLink(topKey: NavKey?): Boolean = backStack.firstOrNull() == Route.Feed && topKey != Route.Feed

    /** 피드 헤더의 알림 — notification 모듈이 진입점을 제공할 때까지 셸의 자리표시자. */
    public fun navigateToNotifications(): Unit = backStack.pushSingleTop(Route.NotificationsPlaceholder)

    /** 지원서 작성 첫 화면(#183) — 같은 공고로 두 번 눌러도 한 장만 쌓는다. */
    public fun navigateToApplicationSetup(postingId: Long): Unit = backStack.pushSingleTop(Route.ApplicationSetup(postingId))

    /**
     * 초안 생성이 시작됐다 — 진행 화면으로 **갈아 끼운다**(#184 이 붙기 전까지 자리표시자).
     *
     * 문항 확인 화면을 백스택에 남기지 않는다. 남기면 뒤로 가기가 이미 만든 초안을 다시 만드는 자리로
     * 돌아오고, 사용자는 같은 공고에 초안이 둘 생겼다고 읽는다.
     */
    public fun navigateToApplicationProgress(applicationId: Long) {
        backStack.removeAll { it is Route.ApplicationSetup }
        backStack.pushSingleTop(Route.ApplicationProgressPlaceholder(applicationId))
    }

    /** 루트를 한 칸 내린다. 바닥이면 내리지 않고 `false` — Nav3 는 빈 백스택을 그릴 수 없다. */
    public fun popBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

/** 하단 탭 ↔ 루트 키. 이 표 하나가 정본이다. */
internal val TAB_KEYS: Map<CareerCompassBottomTab, Route> =
    mapOf(
        CareerCompassBottomTab.Feed to Route.Feed,
        CareerCompassBottomTab.Analysis to Route.AnalysisTab,
        CareerCompassBottomTab.Applications to Route.ApplicationsTab,
        CareerCompassBottomTab.My to Route.MyTab,
    )

/**
 * 루트 백스택을 세운다. [startKey] 는 첫 컴포지션에만 쓰이고, 프로세스 재생성에서는 저장된 스택이 돌아온다 —
 * 세션 종료마다 셸이 `revision` 으로 이 컴포지션을 새로 만들어 이전 스택을 버리는 규칙은 `MainActivity` 가 갖는다.
 */
@Composable
public fun rememberAppState(startKey: Route): AppState {
    val backStack = rememberNavBackStack(startKey)
    return remember(backStack) { AppState(backStack) }
}

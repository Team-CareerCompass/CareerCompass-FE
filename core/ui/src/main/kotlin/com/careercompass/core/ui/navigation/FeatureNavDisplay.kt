package com.careercompass.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * 피처 하나가 소유하는 로컬 Navigation 3 스택의 표준 표시부.
 *
 * 이 저장소의 로컬 스택은 전부 이 함수를 거친다 — 데코레이터 목록과 바닥 back 처리를 한 곳에
 * 모아 두면, 아래 두 함정을 피처마다 다시 밟지 않는다.
 *
 * 1. `NavDisplay.onBack` 은 `() -> Unit` 이다. 문서·블로그에 도는 `{ count -> … }` (Int 인자)
 *    형태는 이 버전에서 컴파일되지 않는다 (애프터노트 #959 실측 함정 1).
 * 2. `entryDecorators` 를 넘기면 **기본 목록을 통째로 대체한다**. 기본값은
 *    [rememberSaveableStateHolderNavEntryDecorator] 하나뿐이라, entry 범위 ViewModel 을 쓰려고
 *    [rememberViewModelStoreNavEntryDecorator] 만 넣으면 `rememberSaveable` 상태 보존이 조용히
 *    깨진다 (애프터노트 #959 실측 함정 2). 둘 다 넣는다.
 *
 * 바닥에서 스택을 비우지 않는 것은 Nav3 가 빈 백스택을 그릴 수 없기 때문이다. 다만 **그 자리를
 * 지키는 것이 아래 `onBack` 의 `else` 갈래는 아니다.**
 *
 * `NavDisplay` 는 `isBackEnabled = scene.previousEntries.isNotEmpty()` 로 back 핸들러를 켜는데
 * (`NavDisplay.kt:558`), `SinglePaneScene.previousEntries` 는 `entries.dropLast(1)` 이라
 * (`SinglePaneScene.kt:65`) **스택 크기가 1 이면 핸들러 자체가 꺼진다.** 그래서 제스처·시스템 back 은
 * 이 표시부를 그냥 지나쳐 상위(루트 `NavDisplay`·액티비티)로 흘러간다.
 *
 * `else -> boundary.exit()` 에 실제로 도달하는 것은 **화면 안 back 버튼**(`popOrExit`)뿐이다.
 * `FeatureNavDisplayTest` 의 「back 은 스택만 줄이고 바닥에서는 이 표시부를 지나쳐 위로 흐른다」가
 * 이 갈림을 잠근다.
 *
 * 깊이 신호는 **컴포지션에서 빠질 때 되돌리지 않는다**(#354). 되돌리면 로컬 스택이 부모 entry 에 그대로
 * 남아 있는데도 셸은 바닥이라고 알게 되고, 위에 쌓였던 루트 화면에서 돌아오는 첫 프레임이 그 값으로 그려진다.
 * 셸의 바텀바 판정은 그 프레임이 시작되기 전에 이미 값을 읽으므로, 컴포지션 안에서든 `LaunchedEffect` 에서든
 * 그 프레임에 올리는 값으로는 늦는다. 되돌리기를 없애도 오염은 남지 않는다. 셸은 이 피처가 화면에 있을 때만
 * 이 값을 읽고, host 는 컴포지션에 들어올 때마다 제 깊이를 다시 올리기 때문이다. 스택이 새로 세워진 경우도
 * 첫 컴포지션의 `LaunchedEffect` 가 바닥임을 알려 스스로 바로잡는다.
 */
@Composable
public fun FeatureNavDisplay(
    backStack: NavBackStack<NavKey>,
    boundary: FeatureStackBoundary,
    modifier: Modifier = Modifier,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val currentBoundary by rememberUpdatedState(boundary)
    val isAtRoot = backStack.size <= 1

    LaunchedEffect(isAtRoot) { currentBoundary.onAtRootChanged(isAtRoot) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                currentBoundary.exit()
            }
        },
        entryDecorators = rememberStandardNavEntryDecorators(),
        entryProvider = entryProvider,
    )
}

/**
 * 로컬 스택의 표준 entry decorator 목록.
 *
 * [FeatureNavDisplay] 를 쓸 수 없어 `NavDisplay` 를 직접 부르는 경우(다른 scene 전략이 필요한
 * 경우 등)에도 같은 목록을 쓰도록 공개해 둔다.
 */
@Composable
public fun rememberStandardNavEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

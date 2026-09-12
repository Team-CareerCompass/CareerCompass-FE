package com.careercompass.core.ui.navigation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.OnBackCompletedFallback
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 로컬 Navigation 3 스택 표시부의 **기전** 회귀 기준 (#259).
 *
 * 피처별 백스택 «모양» 은 각 `*LocalNavActionsTest` 가 본다. 여기서 잠그는 것은 그 모양을
 * 실제 화면 수명으로 옮기는 [FeatureNavDisplay] 자신의 계약이다 — 이관 전 Nav2 의 백스택
 * 엔트리가 지켜 주던 것들이라, 깨져도 컴파일은 통과하고 조용히 상태만 사라진다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class FeatureNavDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var backStack: NavBackStack<NavKey>
    private val atRootSignals = mutableListOf<Boolean>()
    private var exits = 0
    private var clearedEntryViewModels = 0
    private var clearedFlowViewModels = 0
    private val flowViewModelInstances = mutableListOf<ClearRecordingViewModel>()
    private lateinit var parentStack: NavBackStack<NavKey>
    private lateinit var flowStack: NavBackStack<NavKey>

    private val boundary =
        object : FeatureStackBoundary {
            override fun exit() {
                exits += 1
            }

            override fun onAtRootChanged(isAtRoot: Boolean) {
                atRootSignals += isAtRoot
            }
        }

    @Composable
    private fun TestHost() {
        val stack = rememberNavBackStack(RootKey)
        SideEffect { backStack = stack }

        FeatureNavDisplay(
            backStack = stack,
            boundary = boundary,
            entryProvider =
                entryProvider {
                    entry<RootKey> { BasicText("root") }
                    entry<DetailKey> { key ->
                        // entry 범위 스토어에 올라간(없으면 새로 만드는) 기록용 ViewModel.
                        val owner = checkNotNull(LocalViewModelStoreOwner.current) { "entry 스코프 owner 가 없다" }
                        ViewModelProvider(
                            owner,
                            viewModelFactory { initializer { ClearRecordingViewModel { clearedEntryViewModels += 1 } } },
                        )[ClearRecordingViewModel::class.java]

                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        BasicText(
                            text = "detail${key.id}#$taps",
                            modifier = Modifier.clickable { taps++ },
                        )
                    }
                },
        )
    }

    private fun start() {
        composeRule.setContent { TestHost() }
        composeRule.waitForIdle()
    }

    /**
     * 시스템·제스처 back 을 실제로 태우기 위한 최소 owner.
     *
     * Nav3 1.1.6 의 back 핸들러는 `androidx.activity` 가 아니라 **`androidx.navigationevent`** 를 탄다
     * (`NavDisplay` 가 `NavigationBackHandler` 를 쓴다). 그래서 `LocalOnBackPressedDispatcherOwner` 를
     * 갈아 끼워도 콜백이 하나도 안 붙는다 — 실측으로 확인했다.
     *
     * [fallback] 은 «아무 핸들러도 처리하지 않았을 때» 불린다. 이 표시부가 back 을 먹었는지를
     * 그 호출 수로 가른다.
     */
    private class TestNavEventOwner : NavigationEventDispatcherOwner {
        var fallbacks = 0
            private set

        override val navigationEventDispatcher =
            NavigationEventDispatcher(OnBackCompletedFallback { fallbacks += 1 })

        val input = DirectNavigationEventInput().also { navigationEventDispatcher.addInput(it) }
    }

    private fun startWithBackDispatcher(): TestNavEventOwner {
        val owner = TestNavEventOwner()
        composeRule.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { TestHost() }
        }
        composeRule.waitForIdle()
        return owner
    }

    /**
     * **바닥에서의 back 은 `boundary.exit()` 로 가지 않는다.**
     *
     * `NavDisplay` 는 `isBackEnabled = scene.previousEntries.isNotEmpty()` 로 핸들러를 켜고
     * (`NavDisplay.kt:558`), `SinglePaneScene.previousEntries` 는 `entries.dropLast(1)` 이다
     * (`SinglePaneScene.kt:65`). 스택 크기 1 이면 그 목록이 비어 **핸들러 자체가 꺼지고** back 은
     * 상위로 흘러간다. [FeatureNavDisplay] 의 `onBack` 에 있는 `else -> boundary.exit()` 갈래는
     * 화면 안 back 버튼(`popOrExit`)으로만 도달한다.
     */
    @Test
    fun `back 은 스택만 줄이고 바닥에서는 이 표시부를 지나쳐 위로 흐른다`() {
        val owner = startWithBackDispatcher()
        push(DetailKey(id = 1))
        composeRule.onNodeWithText("detail1#0").assertIsDisplayed()

        composeRule.runOnIdle { owner.input.backCompleted() }
        composeRule.waitForIdle()
        assertEquals("깊이 2 의 back 은 이 표시부가 먹어 스택을 줄인다", 1, backStack.size)
        assertEquals("먹었으니 위로 흐르지 않는다", 0, owner.fallbacks)
        assertEquals(0, exits)

        composeRule.runOnIdle { owner.input.backCompleted() }
        composeRule.waitForIdle()
        assertEquals("바닥에서는 스택을 비우지 않는다", 1, backStack.size)
        assertEquals("핸들러가 꺼져 있어 위로 흘러간다", 1, owner.fallbacks)
        assertEquals("boundary.exit() 은 back 경로로 도달하지 않는다", 0, exits)
    }

    private fun push(key: NavKey) = composeRule.runOnIdle { backStack.add(key) }

    private fun pop() = composeRule.runOnIdle { backStack.removeAt(backStack.lastIndex) }

    /**
     * 부모 스택의 한 entry 안에서 자식 흐름 host 를 그리는 실제 구조.
     *
     * 흐름 VM 은 «부모 entry 의 스토어» 위에 얹힌다 — 실코드의 온보딩 host 가
     * `hiltViewModel()` 을 host 몸통에서 부를 때와 같은 자리다.
     */
    @Composable
    private fun NestedFlowHost() {
        val outer = rememberNavBackStack(DetailKey(id = 0))
        SideEffect { parentStack = outer }

        FeatureNavDisplay(
            backStack = outer,
            boundary = boundary,
            entryProvider =
                entryProvider {
                    entry<RootKey> {
                        val owner = checkNotNull(LocalViewModelStoreOwner.current) { "entry 스코프 owner 가 없다" }
                        val flowViewModel =
                            ViewModelProvider(
                                owner,
                                viewModelFactory {
                                    initializer { ClearRecordingViewModel { clearedFlowViewModels += 1 } }
                                },
                            )[ClearRecordingViewModel::class.java]
                        SideEffect { flowViewModelInstances += flowViewModel }

                        val inner = rememberNavBackStack(FlowStepKey(step = 1))
                        SideEffect { flowStack = inner }
                        FeatureNavDisplay(
                            backStack = inner,
                            boundary = boundary,
                            entryProvider =
                                entryProvider {
                                    entry<FlowStepKey> { key -> BasicText("step${key.step}") }
                                },
                        )
                    }
                    entry<DetailKey> { BasicText("outside") }
                },
        )
    }

    /**
     * 흐름 VM 이 **자식 화면 사이에서 같은 인스턴스**로 남는다 (애프터노트 #259 이관 전 `FlowScopedViewModelLifetimeTest`).
     *
     * 흐름 안의 단계 이동은 자식 스택만 바꾸므로 부모 entry 의 스토어를 건드리지 않아야 한다.
     */
    @Test
    fun `흐름 VM 은 자식 화면 사이에서 같은 인스턴스로 유지된다`() {
        composeRule.setContent { NestedFlowHost() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { parentStack.add(RootKey) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("step1").assertIsDisplayed()

        composeRule.runOnIdle { flowStack.add(FlowStepKey(step = 2)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("step2").assertIsDisplayed()

        composeRule.runOnIdle { flowStack.removeAt(flowStack.lastIndex) }
        composeRule.waitForIdle()

        assertEquals(0, clearedFlowViewModels)
        assertEquals(1, flowViewModelInstances.distinct().size)
    }

    /**
     * **부모 백스택에 남아 있는 동안은 host 가 컴포지션에서 빠져도 정리되지 않는다.**
     *
     * Nav2 의 `NavBackStackEntry` 수명이 지키던 자리를 Nav3 에서는
     * [rememberViewModelStoreNavEntryDecorator] 의 `onPop` 이 지킨다 — 「pop 됐고 + 컴포지션에서
     * 빠졌을 때만」 발화한다. 이관이 가장 조용히 깨뜨릴 축이라 여기서 잠근다.
     */
    @Test
    fun `부모 백스택에 남아 있으면 host 가 컴포지션에서 빠져도 흐름 VM 이 살아 있다`() {
        composeRule.setContent { NestedFlowHost() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { parentStack.add(RootKey) }
        composeRule.waitForIdle()
        val first = flowViewModelInstances.single()

        // 부모가 다른 화면을 쌓으면 흐름 host 는 컴포지션에서 빠지지만 entry 는 스택에 남는다.
        composeRule.runOnIdle { parentStack.add(DetailKey(id = 9)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("outside").assertIsDisplayed()
        assertEquals("백스택에 남아 있으면 정리되지 않는다", 0, clearedFlowViewModels)

        composeRule.runOnIdle { parentStack.removeAt(parentStack.lastIndex) }
        composeRule.waitForIdle()
        assertEquals("돌아오면 같은 인스턴스여야 한다", first, flowViewModelInstances.last())
        assertEquals(0, clearedFlowViewModels)

        // 실제로 pop 되면 그때 정리된다 — 위 단언이 «영영 안 정리됨» 을 못 박지 않게 대조군을 둔다.
        composeRule.runOnIdle { parentStack.removeAt(parentStack.lastIndex) }
        composeRule.waitForIdle()
        assertEquals(1, clearedFlowViewModels)
    }

    @Test
    fun `위에 화면이 쌓였다 사라져도 아래 화면의 rememberSaveable 이 남는다`() {
        start()
        push(DetailKey(id = 1))
        composeRule.onNodeWithText("detail1#0").performClick()
        composeRule.onNodeWithText("detail1#1").assertIsDisplayed()

        push(DetailKey(id = 2))
        composeRule.onNodeWithText("detail2#0").assertIsDisplayed()
        pop()

        // entryDecorators 를 넘기면 기본 목록을 «대체» 하므로, 저장 홀더 데코레이터를 빠뜨리면
        // 여기서 detail1#0 으로 되돌아간다 — 컴파일은 통과하고 상태만 조용히 사라지는 함정이다.
        composeRule.onNodeWithText("detail1#1").assertIsDisplayed()
    }

    @Test
    fun `entry 범위 ViewModel 은 그 화면이 스택에서 빠질 때 정리된다`() {
        start()
        push(DetailKey(id = 1))
        composeRule.waitForIdle()
        assertEquals(0, clearedEntryViewModels)

        // 위에 다른 화면이 쌓이는 것만으로는 정리되지 않는다 — 아직 백스택에 살아 있다.
        push(DetailKey(id = 2))
        composeRule.waitForIdle()
        assertEquals(0, clearedEntryViewModels)

        pop()
        composeRule.waitForIdle()
        assertEquals(1, clearedEntryViewModels)

        pop()
        composeRule.waitForIdle()
        assertEquals(2, clearedEntryViewModels)
    }

    @Test
    fun `스택 깊이 변화가 셸로 올라간다`() {
        start()
        assertEquals(listOf(true), atRootSignals)

        push(DetailKey(id = 1))
        composeRule.waitForIdle()
        assertEquals(listOf(true, false), atRootSignals)

        pop()
        composeRule.waitForIdle()
        assertEquals(listOf(true, false, true), atRootSignals)
    }

    /**
     * **host 가 컴포지션에서 빠져도 깊이를 되돌리지 않는다** (#354).
     *
     * 로컬 스택은 부모 entry 에 남아 있어 돌아오면 같은 깊이로 다시 그려진다. 여기서 `true` 로 되돌리면
     * 셸은 바닥이라고 알게 되고, 돌아오는 첫 프레임이 그 값으로 그려진다(아래 첫 프레임 테스트).
     */
    @Test
    fun `host 가 컴포지션에서 빠져도 마지막 깊이가 그대로 남는다`() {
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) TestHost() }
        composeRule.waitForIdle()
        push(DetailKey(id = 1))
        composeRule.waitForIdle()
        assertEquals(listOf(true, false), atRootSignals)

        // 탭 이탈 — 스택은 부모 entry 에 남아 있으므로 셸이 아는 깊이도 그대로다.
        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertEquals("빠지는 것만으로는 아무것도 알리지 않는다", listOf(true, false), atRootSignals)
    }

    /**
     * **스택이 새로 세워진 host 는 들어오면서 바닥임을 스스로 알린다.**
     *
     * 컴포지션 이탈에서 되돌리지 않아도 셸이 옛 깊이에 묶이지 않는 이유다. 되돌리기가 막던 오염(로그아웃 뒤
     * 새 스택으로 재진입)을 이 재통지가 대신 막는다.
     */
    @Test
    fun `스택이 새로 세워진 host 는 들어오면서 바닥임을 알린다`() {
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) TestHost() }
        composeRule.waitForIdle()
        push(DetailKey(id = 1))
        composeRule.waitForIdle()
        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        composeRule.runOnIdle { shown = true }
        composeRule.waitForIdle()

        assertEquals(listOf(true, false, true), atRootSignals)
    }

    /**
     * **위에 쌓였던 루트 화면에서 돌아오는 첫 프레임이 지금 깊이로 그려진다** (#354).
     *
     * 셸의 바텀바 판정은 그 프레임의 컴포지션에서 깊이를 읽고, host 는 그 뒤에 그려진다. 그래서 이 표시부가
     * 그 프레임에 올리는 값은 이미 늦다. 첫 프레임을 맞히는 것은 떠날 때 남겨 둔 값 하나뿐이라, 시계를 멈추고
     * 프레임 하나만 돌려 그 값을 잰다.
     *
     * 스택을 host 밖에 두는 것은 실제 구조를 따른 것이다. 피드 로컬 스택은 부모 entry 의 저장 상태에 남아
     * 있어, 위에 쌓인 루트 화면에서 돌아오면 같은 깊이로 복원된다.
     */
    @Test
    fun `위에 쌓인 화면에서 돌아온 첫 프레임에 바텀바가 없다`() {
        var hostShown by mutableStateOf(true)
        var shellAtRoot by mutableStateOf(true)
        val shellBoundary =
            object : FeatureStackBoundary {
                override fun exit() = Unit

                override fun onAtRootChanged(isAtRoot: Boolean) {
                    shellAtRoot = isAtRoot
                }
            }

        composeRule.setContent {
            val stack = rememberNavBackStack(RootKey)
            SideEffect { backStack = stack }
            Column {
                if (shellAtRoot) BasicText(BOTTOM_BAR)
                if (hostShown) {
                    FeatureNavDisplay(
                        backStack = stack,
                        boundary = shellBoundary,
                        entryProvider =
                            entryProvider {
                                entry<RootKey> { BasicText("root") }
                                entry<DetailKey> { BasicText("detail") }
                            },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        push(DetailKey(id = 1))
        composeRule.waitForIdle()
        composeRule.onNodeWithText(BOTTOM_BAR).assertDoesNotExist()

        // 상세 위로 루트 화면(문항 확인)이 쌓여 host 가 컴포지션에서 빠진다. 그 화면에서는 루트 키만으로
        // 바텀바가 숨으므로 깊이는 읽히지 않는다.
        composeRule.runOnIdle { hostShown = false }
        composeRule.waitForIdle()

        // back 으로 돌아온다. 시계를 멈추고 프레임 하나만 돌린다.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { hostShown = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(BOTTOM_BAR).assertDoesNotExist()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("detail").assertIsDisplayed()
        composeRule.onNodeWithText(BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    fun `프로세스 재생성 뒤 스택과 화면 상태가 함께 복원된다`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { TestHost() }
        composeRule.waitForIdle()

        push(DetailKey(id = 1))
        composeRule.onNodeWithText("detail1#0").performClick()
        composeRule.onNodeWithText("detail1#1").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        // rememberNavBackStack 이 키를 직렬화해 되살린다 — NavKey 가 @Serializable 이어야 하는 이유.
        assertEquals(listOf<NavKey>(RootKey, DetailKey(id = 1)), composeRule.runOnIdle { backStack.toList() })
        composeRule.onNodeWithText("detail1#1").assertIsDisplayed()
    }

    private companion object {
        /** 셸의 바텀바 자리를 대신하는 노드. 이 표시부가 올린 깊이로만 그려진다. */
        const val BOTTOM_BAR = "bottombar"
    }

    @Serializable
    private data class FlowStepKey(
        val step: Int,
    ) : NavKey

    private class ClearRecordingViewModel(
        private val onCleared: () -> Unit,
    ) : ViewModel() {
        override fun onCleared() = onCleared.invoke()
    }
}

/**
 * 테스트용 키.
 *
 * `private` 로 두면 안 된다 — `rememberNavBackStack` 이 키를 리플렉션으로 직렬화하는데,
 * kotlinx.serialization 이 private 선언의 `INSTANCE` 에 접근하지 못해 저장 시점에
 * `IllegalAccessException` 이 난다. 프로덕션 라우트는 모두 공개라 걸리지 않는 함정이다.
 */
@Serializable
internal data object RootKey : NavKey

@Serializable
internal data class DetailKey(
    val id: Int,
) : NavKey

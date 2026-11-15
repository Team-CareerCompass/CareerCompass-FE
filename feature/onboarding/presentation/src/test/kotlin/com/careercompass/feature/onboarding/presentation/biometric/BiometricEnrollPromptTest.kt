package com.careercompass.feature.onboarding.presentation.biometric

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 마이 탭 스위치(#113)가 켜는 길을 여는 기준. `canEnrollBiometric()` 의 두 답이 모두 여기서 확인된다.
 *
 * null 쪽만 보면 판정이 영구 false 로 굳어도(하드웨어를 못 보든, 인증 수단 기준이 어긋나든) 아무도 모른다. 그래서
 * 프롬프트를 실제로 띄울 수 있는 [FragmentActivity] 호스트를 주입해 launcher 가 돌아오는 쪽까지 같이 본다
 * ([BiometricPromptLauncherRecreateTest] 와 같은 방식의 호스트다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class BiometricEnrollPromptTest {
    @get:Rule
    public val composeRule = createComposeRule()

    /** 지문 프롬프트를 받아 주는 호스트. 컴포즈 규칙의 호스트는 [FragmentActivity] 가 아니라 따로 세운다. */
    private val host: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    /**
     * `BiometricPrompt` 는 [FragmentActivity] 를 요구한다 — 아닌 호스트(테스트·프리뷰)에서는
     * 켜는 길을 아예 열지 않는다. 호출부는 이 null 로 스위치를 잠근다.
     */
    @Test
    public fun withoutFragmentActivityHost_hasNoLauncher() {
        var launch: (() -> Unit)? = {}

        composeRule.setContent {
            launch = rememberBiometricEnrollPrompt(onResult = {})
        }
        composeRule.waitForIdle()

        assertNull(launch)
    }

    /** 반대쪽. 강한 생체를 쓸 수 있는 [FragmentActivity] 호스트에서는 켜는 길이 열린다. */
    @Test
    public fun withFragmentActivityHost_hasLauncher() {
        var launch: (() -> Unit)? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalActivity provides host) {
                launch = rememberBiometricEnrollPrompt(onResult = {})
            }
        }
        composeRule.waitForIdle()

        assertNotNull(launch)
    }

    /**
     * 열린 길이 실제로 프롬프트로 이어진다. 부르자마자 결과가 돌아오면 그것은 뜨지 못하고 끝난 것이다.
     *
     * 켤 수 없는 기기·호스트에서 launcher 를 부르면 [BiometricEnrollPromptResult.Failed] 가 즉시 온다. 여기서는
     * 아무 결과도 오지 않아야 사용자가 지문을 댈 프롬프트가 떠 있는 것이다.
     */
    @Test
    public fun launcherOnFragmentActivityHost_opensPromptWithoutImmediateResult() {
        val results = mutableListOf<BiometricEnrollPromptResult>()
        var launch: (() -> Unit)? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalActivity provides host) {
                launch = rememberBiometricEnrollPrompt(onResult = results::add)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { requireNotNull(launch).invoke() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(emptyList<BiometricEnrollPromptResult>(), results) }
    }
}

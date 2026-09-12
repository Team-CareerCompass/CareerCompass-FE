package com.careercompass.feature.onboarding.presentation.biometric

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.onboarding.presentation.reporting.RecordingErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 관문이 플랫폼 판정을 실제로 읽는지 본다. 조건 판정은 [BiometricEnrollViewModel] 테스트가 이미 각 답을 직접
 * 주입해 덮고 있지만, 그 답을 만드는 `canEnrollBiometric()` 을 부르는 곳은 여기뿐이다.
 *
 * 그래서 두 호스트를 모두 합성한다. 지문을 등록할 수 있는 [FragmentActivity] 호스트에서는 시트가 떠야 하고,
 * 그렇지 않은 호스트에서는 묻지 않고 통과해야 한다. 판정이 한쪽으로 굳으면 둘 중 하나가 반드시 깨진다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class BiometricEnrollGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val authRepository = FakeAuthRepository(loggedIn = true, accessToken = "access", refreshToken = "refresh")
    private val userProfileRepository = FakeUserProfileRepository(initialProfile = profile())
    private val reporter = RecordingErrorReporter()

    /** 지문 프롬프트를 받아 주는 호스트. 컴포즈 규칙의 호스트는 [FragmentActivity] 가 아니다. */
    private val host: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `지문을 등록할 수 있는 기기에서는 제안 시트를 띄우고 이동을 붙든다`() {
        val viewModel = createViewModel()
        var proceeded = 0

        setGate(viewModel, useFragmentActivityHost = true) { proceeded++ }

        offerTitle().assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(viewModel.uiState.value.isOffered)
            assertEquals(0, proceeded)
            assertTrue(reporter.failures.isEmpty())
        }
    }

    @Test
    fun `프롬프트를 띄울 수 없는 호스트에서는 묻지 않고 통과시킨다`() {
        val viewModel = createViewModel()
        var proceeded = 0

        setGate(viewModel, useFragmentActivityHost = false) { proceeded++ }

        composeRule.onAllNodesWithText(OFFER_TITLE).assertCountEquals(0)
        composeRule.runOnIdle {
            assertFalse(viewModel.uiState.value.isOffered)
            assertEquals(1, proceeded)
        }
    }

    private fun setGate(
        viewModel: BiometricEnrollViewModel,
        useFragmentActivityHost: Boolean,
        onProceed: () -> Unit,
    ) {
        composeRule.setContent {
            WithHost(useFragmentActivityHost) {
                CareerCompassTheme {
                    BiometricEnrollGate(isRequested = true, onProceed = onProceed, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun WithHost(
        useFragmentActivityHost: Boolean,
        content: @Composable () -> Unit,
    ) {
        if (useFragmentActivityHost) {
            CompositionLocalProvider(LocalActivity provides host, content = content)
        } else {
            content()
        }
    }

    private fun offerTitle() = composeRule.onNodeWithText(OFFER_TITLE)

    private fun createViewModel() = BiometricEnrollViewModel(authRepository, userProfileRepository, reporter)

    private fun profile() =
        UserProfile(
            id = 1L,
            name = "일혁",
            school = null,
            department = null,
            gpa = null,
            gradYear = null,
            jobInterests = emptyList(),
            tags = emptyList(),
            onboardingDone = true,
            completion = 10,
        )

    private companion object {
        const val OFFER_TITLE = "지문으로 더 빠르게 로그인할까요?"
    }
}

package com.careercompass.feature.onboarding.presentation.biometric

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.domain.usecase.auth.ResolveSessionEntryUseCase
import com.careercompass.core.model.user.UserProfile
import com.careercompass.feature.onboarding.presentation.reporting.RecordingErrorReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricLoginViewModelTest {
    private val authRepository =
        FakeAuthRepository(loggedIn = true, biometricEnabled = true, accessToken = "access", refreshToken = "refresh")
    private val userProfileRepository = FakeUserProfileRepository(initialProfile = profile(name = "정일혁", onboardingDone = true))
    private val reporter = RecordingErrorReporter()
    private var refreshCalls = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        BiometricLoginViewModel(
            authRepository,
            userProfileRepository,
            ResolveSessionEntryUseCase(authRepository, userProfileRepository),
            reporter,
        )

    /** 서버 응답을 [result] 로 고정하고 호출 횟수를 센다. */
    private fun stubRefresh(result: () -> Result<UserProfile>) {
        userProfileRepository.onRefreshProfile = {
            refreshCalls += 1
            result()
        }
    }

    @Test
    fun `지문 활성 여부와 사용자 이름을 상태로 흘린다`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isBiometricEnabled)
        assertEquals("정일혁", viewModel.uiState.value.userName)

        authRepository.biometricEnabledState.value = false
        userProfileRepository.profileState.value = profile(name = null, onboardingDone = true)

        assertFalse(viewModel.uiState.value.isBiometricEnabled)
        assertNull(viewModel.uiState.value.userName)
    }

    @Test
    fun `인증 성공은 프로필을 한 번 갱신해 세션을 검증하고 완료 사용자를 피드로 보낸다`() {
        stubRefresh { Result.success(profile(name = "정일혁", onboardingDone = true)) }
        val viewModel = createViewModel()
        viewModel.onIntent(BiometricLoginIntent.AuthenticationStarted)

        viewModel.onIntent(BiometricLoginIntent.AuthenticationSucceeded)

        val state = viewModel.uiState.value
        assertEquals(BiometricDestination.Feed, state.pendingNavigation)
        assertFalse(state.isAuthenticating)
        assertEquals(1, refreshCalls)
        assertEquals(0, authRepository.rotateTokenCalls)
        assertTrue(authRepository.socialLoginCalls.isEmpty())
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `인증 성공 뒤 온보딩 미완료로 확인되면 온보딩으로 보낸다`() {
        stubRefresh { Result.success(profile(name = "정일혁", onboardingDone = false)) }
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.AuthenticationSucceeded)

        assertEquals(BiometricDestination.Onboarding, viewModel.uiState.value.pendingNavigation)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `세션 검증 중에는 인증 중 표시를 유지한다`() {
        val gate = CompletableDeferred<Result<UserProfile>>()
        userProfileRepository.onRefreshProfile = {
            refreshCalls += 1
            gate.await()
        }
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.AuthenticationSucceeded)
        assertTrue(viewModel.uiState.value.isAuthenticating)
        assertNull(viewModel.uiState.value.pendingNavigation)

        // 검증이 끝나기 전에 다시 성공이 와도 합류한다 — 갱신은 한 번.
        viewModel.onIntent(BiometricLoginIntent.AuthenticationSucceeded)
        gate.complete(Result.success(profile(name = "정일혁", onboardingDone = true)))

        assertFalse(viewModel.uiState.value.isAuthenticating)
        assertEquals(BiometricDestination.Feed, viewModel.uiState.value.pendingNavigation)
        assertEquals(1, refreshCalls)
    }

    /** 가는 화면은 「다른 방법으로 로그인」과 같지만 목적지를 나눠 둔다 — 셸이 이유를 알아야 로그인 화면이 설명한다(#128). */
    @Test
    fun `세션이 만료됐으면(401) 로컬 세션을 정리하고 만료로 표시해 로그인으로 보낸다`() {
        stubRefresh { Result.failure(CoreDataFailure.Unauthorized("AUTH_INVALID", IllegalStateException("만료"))) }
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.AuthenticationSucceeded)

        val state = viewModel.uiState.value
        assertEquals(BiometricDestination.SessionExpired, state.pendingNavigation)
        assertFalse(state.isAuthenticating)
        assertEquals(1, authRepository.clearSessionCalls)
        assertFalse(authRepository.loggedIn)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `네트워크 실패는 세션 표본만 남기고 마지막으로 알려진 완료 여부로 판단한다`() {
        stubRefresh { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException("offline"))) }
        val done = createViewModel()

        done.onIntent(BiometricLoginIntent.AuthenticationSucceeded)

        assertEquals(BiometricDestination.Feed, done.uiState.value.pendingNavigation)
        assertEquals(listOf("biometric_session_verify"), reporter.stages())
        assertEquals(0, authRepository.clearSessionCalls)

        userProfileRepository.profileState.value = null
        userProfileRepository.onboardingDoneHint = false
        val notDone = createViewModel()

        notDone.onIntent(BiometricLoginIntent.AuthenticationSucceeded)

        assertEquals(BiometricDestination.Onboarding, notDone.uiState.value.pendingNavigation)
        // 같은 (원인, 단계) 조합의 두 번째 실패는 접힌다 — 오프라인 재시도가 표본을 독점하지 않는다.
        assertEquals(1, reporter.failures.size)
    }

    @Test
    fun `사용자 취소는 표시도 기록도 하지 않는다`() {
        val viewModel = createViewModel()
        viewModel.onIntent(BiometricLoginIntent.AuthenticationStarted)

        viewModel.onIntent(BiometricLoginIntent.AuthenticationCancelled)

        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticating)
        assertNull(state.failure)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `인증 실패는 사유를 표시하고 기록한다`() {
        val viewModel = createViewModel()
        viewModel.onIntent(BiometricLoginIntent.AuthenticationStarted)

        viewModel.onIntent(BiometricLoginIntent.AuthenticationFailed(BiometricFailureReason.Lockout, IllegalStateException("lockout")))

        val state = viewModel.uiState.value
        assertEquals(BiometricFailureReason.Lockout, state.failure)
        assertFalse(state.isAuthenticating)
        assertEquals(listOf("biometric_auth"), reporter.stages())

        viewModel.onIntent(BiometricLoginIntent.ConsumeFailure)
        assertNull(viewModel.uiState.value.failure)
    }

    /** 사용자가 고른 길이라 만료와 갈린다 — 이쪽에는 안내가 붙지 않는다. */
    @Test
    fun `다른 방법으로 로그인은 만료 표시 없이 로그인 화면으로 보낸다`() {
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.ChooseOtherMethod)
        assertEquals(BiometricDestination.Login, viewModel.uiState.value.pendingNavigation)

        viewModel.onIntent(BiometricLoginIntent.ConsumeNavigation)
        assertNull(viewModel.uiState.value.pendingNavigation)
    }

    /**
     * #335 — 여기서 로그인하는 사람은 다음 계정일 수 있다. 앞 계정의 SESSION 스코프 저장소(프로필·온보딩 진행·
     * 공고 스냅샷)를 지우지 않고 로그인 화면만 띄우면 그 계정이 앞 계정의 상태를 그대로 물려받는다.
     */
    @Test
    fun `다른 방법으로 로그인은 로컬 세션을 정리한다`() {
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.ChooseOtherMethod)

        assertEquals(1, authRepository.clearSessionCalls)
        assertFalse(authRepository.loggedIn)
        assertEquals(BiometricDestination.Login, viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `정리가 끝나기 전에는 로그인 화면으로 보내지 않는다`() {
        val cleared = CompletableDeferred<Result<Unit>>()
        authRepository.onClearSession = { cleared.await() }
        val viewModel = createViewModel()

        viewModel.onIntent(BiometricLoginIntent.ChooseOtherMethod)
        assertNull(viewModel.uiState.value.pendingNavigation)

        cleared.complete(Result.success(Unit))

        assertEquals(BiometricDestination.Login, viewModel.uiState.value.pendingNavigation)
    }

    private fun profile(
        name: String?,
        onboardingDone: Boolean,
    ) = UserProfile(
        id = 1L,
        name = name,
        school = null,
        department = null,
        gpa = null,
        gradYear = null,
        jobInterests = emptyList(),
        tags = emptyList(),
        onboardingDone = onboardingDone,
        completion = 10,
    )
}

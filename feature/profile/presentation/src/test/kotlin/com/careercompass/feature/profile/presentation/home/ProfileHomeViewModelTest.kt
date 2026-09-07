package com.careercompass.feature.profile.presentation.home

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.settings.AppSettingsRepository
import com.careercompass.core.domain.testing.FakeAppSettingsRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.domain.usecase.auth.LogoutUseCase
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.settings.ThemeMode
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.profile.domain.usecase.CountExperienceCardsUseCase
import com.careercompass.feature.profile.domain.usecase.CountPastApplicationsUseCase
import com.careercompass.feature.profile.domain.usecase.LoadProfileHomeUseCase
import com.careercompass.feature.profile.domain.usecase.ObserveProfileUseCase
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
import java.io.IOException

/**
 * 자리표시자(`MyTabPlaceholderViewModelTest`)가 지키던 것을 그대로 옮겨 왔다 — 로그아웃 뒤 세션 종료,
 * 지문 스위치의 끄는 방향은 기기 지원과 무관하게 열림(#113), 테마 저장 실패 시 화면이 저장소 값을 따름.
 * 그 위에 마이 홈이 새로 지는 것(조회·캐시·실패 처분·메뉴 이동)을 더한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileHomeViewModelTest {
    private class RecordingReporter : ErrorReporter {
        val recorded = mutableListOf<Map<String, String>>()

        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) {
            recorded += attributes
        }
    }

    private val reporter = RecordingReporter()
    private val authRepository = FakeAuthRepository(loggedIn = true)
    private val userProfileRepository = FakeUserProfileRepository(initialProfile = profile())
    private val experienceRepository = FakeExperienceRepository()
    private val pastApplicationRepository = FakePastApplicationRepository()
    private val appSettingsRepository = FakeAppSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(appSettings: AppSettingsRepository = appSettingsRepository) =
        ProfileHomeViewModel(
            observeProfile = ObserveProfileUseCase(userProfileRepository),
            loadProfileHome =
                LoadProfileHomeUseCase(
                    userProfileRepository = userProfileRepository,
                    countExperienceCards = CountExperienceCardsUseCase(experienceRepository),
                    countPastApplications = CountPastApplicationsUseCase(pastApplicationRepository),
                ),
            authRepository = authRepository,
            logout = LogoutUseCase(authRepository),
            appSettingsRepository = appSettings,
            errorReporter = reporter,
        )

    /** 기기가 지문을 등록할 수 있다고 화면이 알려 준 상태 — 실제 배선에서 스위치를 켤 수 있는 유일한 조건이다. */
    private fun viewModelOnEnrollableDevice() = viewModel().apply { onBiometricAvailabilityChanged(canEnroll = true) }

    // ── 조회와 캐시 ────────────────────────────────────────────────────────────

    @Test
    fun `프로필과 개수 둘을 받아 그린다`() {
        experienceRepository.experiences += List(3) { experienceCard(it + 1L) }
        pastApplicationRepository.applications += pastApplication(1L)

        val state = viewModel().state.value

        assertEquals("정일혁", state.profile?.name)
        assertEquals(3, state.experienceCardCount)
        assertEquals(1, state.pastApplicationCount)
        assertFalse(state.isFailureVisible)
    }

    @Test
    fun `개수를 못 세면 배지 자리만 비고 화면은 그대로다`() {
        experienceRepository.onGetExperiences = { _, _, _ -> Result.failure(IOException("offline")) }

        val state = viewModel().state.value

        assertNull(state.experienceCardCount)
        assertEquals("정일혁", state.profile?.name)
        assertFalse(state.isFailureVisible)
    }

    /** 캐시가 없으면 화면 전체가 실패로 덮인다 — 그릴 것이 아무것도 없다. */
    @Test
    fun `캐시가 없을 때 조회가 실패하면 실패 화면을 그린다`() {
        userProfileRepository.profileState.value = null
        userProfileRepository.onRefreshProfile = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }

        val state = viewModel().state.value

        assertTrue(state.isFailureVisible)
        assertEquals(FailureKind.NoConnection, state.loadFailure)
        assertNull(state.message)
        assertEquals("home_load", reporter.recorded.single()["profile_stage"])
    }

    /** 캐시가 있으면 보이던 값을 그대로 두고 스낵바 한 줄로만 알린다. */
    @Test
    fun `캐시가 있으면 조회 실패가 화면을 덮지 않는다`() {
        userProfileRepository.onRefreshProfile = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", IllegalStateException())) }

        val state = viewModel().state.value

        assertFalse(state.isFailureVisible)
        assertEquals("정일혁", state.profile?.name)
        assertEquals(ProfileHomeMessage.RefreshFailed, state.message)
        assertEquals(FailureKind.Unexpected, state.loadFailure)
        // 조용한 실패와 사용자가 실제로 막힌 실패는 콘솔에서 갈려야 한다.
        assertEquals("home_refresh", reporter.recorded.single()["profile_stage"])
    }

    /** 503 은 서버가 스스로 알린 계획된 상태라 리포팅이 접는다 — 화면 처분은 다른 실패와 같다. */
    @Test
    fun `점검 중 실패도 스낵바로 끝나고 기록은 남기지 않는다`() {
        userProfileRepository.onRefreshProfile =
            { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", IllegalStateException())) }

        val state = viewModel().state.value

        assertFalse(state.isFailureVisible)
        assertEquals(ProfileHomeMessage.RefreshFailed, state.message)
        assertEquals(FailureKind.ServiceUnavailable, state.loadFailure)
        assertTrue(reporter.recorded.isEmpty())
    }

    @Test
    fun `401 은 실패 화면 대신 세션 종료를 알린다`() {
        userProfileRepository.onRefreshProfile = { Result.failure(CoreDataFailure.Unauthorized("AUTH_INVALID", IllegalStateException())) }

        val state = viewModel().state.value

        assertTrue(state.sessionEnded)
        assertFalse(state.isFailureVisible)
        assertNull(state.message)
    }

    @Test
    fun `재시도하면 실패 표시를 지우고 다시 읽는다`() {
        userProfileRepository.profileState.value = null
        var attempts = 0
        userProfileRepository.onRefreshProfile = {
            attempts++
            if (attempts == 1) Result.failure(IOException("offline")) else Result.success(profile())
        }
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isFailureVisible)

        viewModel.onEvent(ProfileHomeEvent.RetryClicked)

        assertEquals(2, attempts)
        assertFalse(viewModel.state.value.isFailureVisible)
        assertEquals(
            "정일혁",
            viewModel.state.value.profile
                ?.name,
        )
    }

    @Test
    fun `조회 중 다시 들어와도 요청은 한 번이다`() {
        val serverProfile = CompletableDeferred<Unit>()
        var calls = 0
        userProfileRepository.onRefreshProfile = {
            calls++
            serverProfile.await()
            Result.success(profile())
        }
        val viewModel = viewModel()

        viewModel.onRefresh()
        serverProfile.complete(Unit)

        assertEquals(1, calls)
    }

    @Test
    fun `메뉴를 누르면 이동 신호를 올리고 소비하면 내려간다`() {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.MenuClicked(ProfileHomeMenu.ExperienceCards))
        assertEquals(ProfileHomeMenu.ExperienceCards, viewModel.state.value.pendingMenu)

        viewModel.onMenuNavigationConsumed()
        assertNull(viewModel.state.value.pendingMenu)
    }

    @Test
    fun `스낵바를 소비하면 같은 문구가 되풀이되지 않는다`() {
        userProfileRepository.onRefreshProfile = { Result.failure(IOException("offline")) }
        val viewModel = viewModel()

        viewModel.onMessageConsumed()

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `빈 칸이 없는 프로필은 완성 안내로 끝난다`() {
        experienceRepository.experiences += experienceCard(1L)

        assertEquals(emptyList<Any>(), viewModel().state.value.completionGaps)
    }

    // ── 로그아웃 (자리표시자에서 옮겨 옴) ───────────────────────────────────────

    @Test
    fun `확인하면 로그아웃하고 세션 종료를 알린다`() {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.LogoutClicked)
        assertTrue(viewModel.state.value.isLogoutDialogVisible)

        viewModel.onEvent(ProfileHomeEvent.LogoutConfirmed)

        assertEquals(1, authRepository.logoutCalls)
        assertFalse(authRepository.loggedIn)
        assertFalse(viewModel.state.value.isLogoutDialogVisible)
        assertFalse(viewModel.state.value.isLoggingOut)
        assertTrue(viewModel.state.value.sessionEnded)
    }

    @Test
    fun `취소하면 다이얼로그만 닫고 로그아웃하지 않는다`() {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.LogoutClicked)
        viewModel.onEvent(ProfileHomeEvent.LogoutDismissed)

        assertFalse(viewModel.state.value.isLogoutDialogVisible)
        assertEquals(0, authRepository.logoutCalls)
        assertFalse(viewModel.state.value.sessionEnded)
    }

    /** 서버 로그아웃이 실패해도 로컬 세션은 정리된 뒤다 — 사용자를 로그인된 화면에 붙잡아 두지 않는다. */
    @Test
    fun `로그아웃이 실패해도 세션 종료를 알리고 실패를 기록한다`() {
        authRepository.onLogout = { Result.failure(IllegalStateException("세션 정리 실패")) }
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.LogoutConfirmed)

        assertTrue(viewModel.state.value.sessionEnded)
        assertEquals("logout", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `로그아웃 중 다시 확인해도 요청은 한 번이다`() {
        val serverLogout = CompletableDeferred<Unit>()
        authRepository.onLogout = {
            serverLogout.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.LogoutConfirmed)
        assertTrue(viewModel.state.value.isLoggingOut)
        viewModel.onEvent(ProfileHomeEvent.LogoutConfirmed)
        serverLogout.complete(Unit)

        assertEquals(1, authRepository.logoutCalls)
        assertTrue(viewModel.state.value.sessionEnded)
    }

    @Test
    fun `세션 종료를 소비하면 알림이 되풀이되지 않는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileHomeEvent.LogoutConfirmed)

        viewModel.onSessionEndedConsumed()

        assertFalse(viewModel.state.value.sessionEnded)
    }

    // ── 지문 로그인 스위치 (#113 · 자리표시자에서 옮겨 옴) ───────────────────────

    @Test
    fun `스위치는 저장소의 등록 상태를 그대로 따른다`() {
        authRepository.biometricEnabledState.value = true

        assertTrue(viewModel().state.value.isBiometricEnabled)
    }

    /** 이 스위치가 있는 이유 — 끄는 경로가 여기 말고는 없다. */
    @Test
    fun `끄면 기기의 등록 기록을 지운다`() {
        authRepository.biometricEnabledState.value = true
        val viewModel = viewModelOnEnrollableDevice()

        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = false))

        assertEquals(1, authRepository.setBiometricEnabledCalls)
        assertFalse(authRepository.biometricEnabledState.value)
        assertFalse(viewModel.state.value.isBiometricEnabled)
        assertFalse(viewModel.state.value.isBiometricBusy)
    }

    @Test
    fun `켜면 등록 프롬프트를 요청하고 결과가 올 때까지 스위치를 잠근다`() {
        val viewModel = viewModelOnEnrollableDevice()

        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))

        assertTrue(viewModel.state.value.isEnrollPromptRequested)
        assertFalse(viewModel.state.value.isBiometricSwitchEnabled)
        // 지문을 확인하기 전에는 서버를 부르지 않는다 — 등록 흐름은 #98 과 같다.
        assertEquals(0, authRepository.registerBiometricCalls)
    }

    @Test
    fun `프롬프트를 띄우면 요청 신호가 내려간다`() {
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))

        viewModel.onEnrollPromptRequestConsumed()

        assertFalse(viewModel.state.value.isEnrollPromptRequested)
    }

    @Test
    fun `지문을 확인하면 서버에 등록해 켠다`() {
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))
        viewModel.onEnrollPromptRequestConsumed()

        viewModel.onBiometricEnrollSucceeded()

        assertEquals(1, authRepository.registerBiometricCalls)
        assertTrue(viewModel.state.value.isBiometricEnabled)
        assertTrue(viewModel.state.value.isBiometricSwitchEnabled)
    }

    @Test
    fun `프롬프트를 취소하면 등록하지 않고 스위치가 꺼진 자리로 돌아온다`() {
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))

        viewModel.onBiometricEnrollCancelled()

        assertEquals(0, authRepository.registerBiometricCalls)
        assertFalse(viewModel.state.value.isBiometricEnabled)
        assertTrue(viewModel.state.value.isBiometricSwitchEnabled)
        assertTrue(reporter.recorded.isEmpty())
    }

    @Test
    fun `지문 확인이 실패하면 스위치는 꺼진 자리에 남고 사유는 기록한다`() {
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))

        viewModel.onBiometricEnrollFailed(IllegalStateException("지문 확인 실패"))

        assertEquals(0, authRepository.registerBiometricCalls)
        assertFalse(viewModel.state.value.isBiometricEnabled)
        assertTrue(viewModel.state.value.isBiometricSwitchEnabled)
        assertEquals("biometric_toggle", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `서버 등록이 실패하면 스위치는 꺼진 자리에 남고 사유는 기록한다`() {
        authRepository.onRegisterBiometric = { Result.failure(IllegalStateException("등록 실패")) }
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))

        viewModel.onBiometricEnrollSucceeded()

        assertFalse(viewModel.state.value.isBiometricEnabled)
        assertTrue(viewModel.state.value.isBiometricSwitchEnabled)
        assertEquals("biometric_toggle", reporter.recorded.single()["profile_stage"])
    }

    /** 「나중에」는 제안을 다시 하지 말라는 답일 뿐이다 — 직접 찾아온 이 경로까지 막지 않는다. */
    @Test
    fun `나중에로 넘긴 기록이 있어도 스위치로 켤 수 있다`() {
        authRepository.biometricEnrollDeclinedState.value = true
        val viewModel = viewModelOnEnrollableDevice()

        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))
        viewModel.onBiometricEnrollSucceeded()

        assertEquals(1, authRepository.registerBiometricCalls)
        assertTrue(viewModel.state.value.isBiometricEnabled)
        assertTrue(authRepository.biometricEnrollDeclinedState.value)
    }

    @Test
    fun `기기가 지문을 등록할 수 없으면 스위치는 꺼진 채 잠기고 이유를 안내한다`() {
        val viewModel = viewModel()

        viewModel.onBiometricAvailabilityChanged(canEnroll = false)

        assertFalse(viewModel.state.value.isBiometricEnabled)
        assertFalse(viewModel.state.value.isBiometricSwitchEnabled)
        assertTrue(viewModel.state.value.isBiometricUnavailableNoticeVisible)
    }

    /** 판정 전 한 프레임에 「쓸 수 없다」가 스쳤다 사라지지 않게 — 화면이 알려 주기 전에는 안내도 없다. */
    @Test
    fun `기기 판정 전에는 스위치가 잠기고 안내도 없다`() {
        val state = viewModel().state.value

        assertFalse(state.isBiometricSwitchEnabled)
        assertFalse(state.isBiometricUnavailableNoticeVisible)
    }

    /** 켜 둔 뒤 지문을 지운 기기 — 여기서도 잠그면 시작 목적지가 지문 화면인 채로 사용자가 갇힌다. */
    @Test
    fun `켜져 있으면 등록할 수 없는 기기에서도 끌 수 있다`() {
        authRepository.biometricEnabledState.value = true
        val viewModel = viewModel()

        viewModel.onBiometricAvailabilityChanged(canEnroll = false)

        assertTrue(viewModel.state.value.isBiometricSwitchEnabled)
        assertFalse(viewModel.state.value.isBiometricUnavailableNoticeVisible)

        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = false))

        assertFalse(authRepository.biometricEnabledState.value)
    }

    @Test
    fun `등록을 기다리는 동안 눌린 해제는 무시한다`() {
        val serverRegister = CompletableDeferred<Unit>()
        authRepository.onRegisterBiometric = {
            serverRegister.await()
            Result.success(Unit)
        }
        val viewModel = viewModelOnEnrollableDevice()
        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = true))
        viewModel.onBiometricEnrollSucceeded()

        viewModel.onEvent(ProfileHomeEvent.BiometricToggled(enabled = false))
        serverRegister.complete(Unit)

        assertEquals(0, authRepository.setBiometricEnabledCalls)
        assertFalse(viewModel.state.value.isBiometricBusy)
    }

    // ── 화면 테마 (자리표시자에서 옮겨 옴) ──────────────────────────────────────

    @Test
    fun `저장된 테마를 그대로 되비춘다`() {
        appSettingsRepository.themeModeState.value = ThemeMode.Dark

        assertEquals(ThemeMode.Dark, viewModel().state.value.themeMode)
    }

    @Test
    fun `테마를 고르면 저장하고 다이얼로그를 닫는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileHomeEvent.ThemeClicked)
        assertTrue(viewModel.state.value.isThemeDialogVisible)

        viewModel.onEvent(ProfileHomeEvent.ThemeSelected(ThemeMode.Light))

        assertFalse(viewModel.state.value.isThemeDialogVisible)
        assertEquals(ThemeMode.Light, appSettingsRepository.themeModeState.value)
        assertEquals(ThemeMode.Light, viewModel.state.value.themeMode)
    }

    @Test
    fun `테마 다이얼로그를 닫기만 하면 값이 그대로다`() {
        appSettingsRepository.themeModeState.value = ThemeMode.Dark
        val viewModel = viewModel()

        viewModel.onEvent(ProfileHomeEvent.ThemeClicked)
        viewModel.onEvent(ProfileHomeEvent.ThemeDismissed)

        assertFalse(viewModel.state.value.isThemeDialogVisible)
        assertEquals(ThemeMode.Dark, appSettingsRepository.themeModeState.value)
    }

    @Test
    fun `저장이 실패해도 화면은 저장소 값을 따른다`() {
        // 화면이 낙관적으로 갱신하지 않으므로, 실패하면 아무것도 되돌리지 않아도 원래 값이 남는다.
        val failing =
            object : AppSettingsRepository {
                override val themeMode = appSettingsRepository.themeMode

                override suspend fun setThemeMode(mode: ThemeMode) = throw IllegalStateException("저장 실패")
            }
        val viewModel = viewModel(appSettings = failing)

        viewModel.onEvent(ProfileHomeEvent.ThemeSelected(ThemeMode.Dark))

        assertEquals(ThemeMode.System, viewModel.state.value.themeMode)
        assertTrue(reporter.recorded.any { it.values.contains("theme_mode") })
    }

    private companion object {
        fun profile(
            school: String? = "건국대학교",
            department: String? = "컴퓨터공학부",
        ) = UserProfile(
            id = 1,
            name = "정일혁",
            school = school,
            department = department,
            gpa = 3.9,
            gradYear = 2027,
            jobInterests = listOf(JobInterest(code = "android", priority = 1)),
            tags = listOf("모바일"),
            onboardingDone = true,
            completion = 78,
        )

        fun experienceCard(id: Long) =
            Experience(
                id = id,
                title = "카드 $id",
                startPoint = ExperiencePoint.YearMonth(2025, 3),
                endPoint = null,
                details = ExperienceDetails.Project(role = null, techs = emptyList(), summary = null, link = null),
                createdAt = null,
            )

        fun pastApplication(id: Long) = PastApplication(id = id, label = "지원서 $id", items = emptyList(), createdAt = null)
    }
}

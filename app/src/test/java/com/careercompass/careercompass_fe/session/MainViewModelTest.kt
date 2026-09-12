package com.careercompass.careercompass_fe.session

import androidx.lifecycle.SavedStateHandle
import com.careercompass.careercompass_fe.navigation.AppDeepLink
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeAppSettingsRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.domain.usecase.auth.ResolveSessionEntryUseCase
import com.careercompass.core.model.settings.ThemeMode
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.network.model.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(onboardingDone: Boolean) =
        UserProfile(
            id = 1,
            name = "정일혁",
            school = null,
            department = null,
            gpa = null,
            gradYear = null,
            jobInterests = emptyList(),
            tags = emptyList(),
            onboardingDone = onboardingDone,
            completion = 10,
        )

    private fun networkFailure() = Result.failure<UserProfile>(CoreDataFailure.NetworkUnavailable(UnknownHostException()))

    private fun unauthorized() =
        Result.failure<UserProfile>(
            CoreDataFailure.Unauthorized("AUTH_INVALID", ApiException("AUTH_INVALID", null, "만료", status = 401)),
        )

    private val MainViewModel.destination: AppStartDestination? get() = launch?.destination

    private val MainViewModel.expiryNotice: Boolean get() = launch?.sessionExpiryNotice == true

    /** 실제 배선과 같게 — 세션 진입 판정은 두 리포지토리를 받는 use case 가 한다. */
    private fun mainViewModel(
        authRepository: FakeAuthRepository,
        userProfileRepository: FakeUserProfileRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        appSettingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(),
    ) = MainViewModel(
        authRepository = authRepository,
        userProfileRepository = userProfileRepository,
        resolveSessionEntry = ResolveSessionEntryUseCase(authRepository, userProfileRepository),
        appSettingsRepository = appSettingsRepository,
        errorReporter = reporter,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `세션이 없으면 로그인으로 시작한다`() {
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict())

        assertEquals(AppStartDestination.Login, viewModel.destination)
        // 한 번도 로그인하지 않은 첫 시작 — 끝난 세션이 없으니 설명할 것도 없다.
        assertFalse(viewModel.expiryNotice)
    }

    @Test
    fun `세션이 있고 지문 로그인을 켰으면 지문 화면으로 시작한다`() {
        val viewModel =
            mainViewModel(FakeAuthRepository(loggedIn = true, biometricEnabled = true), FakeUserProfileRepository.strict())

        assertEquals(AppStartDestination.BiometricLogin, viewModel.destination)
    }

    /** 지문 등록 제안(#98)이 실제로 시작 목적지를 바꾸는지 — 등록 호출부터 다음 콜드 스타트까지를 잇는다. */
    @Test
    fun `지문을 등록한 뒤 앱을 다시 켜면 지문 화면으로 시작한다`() {
        val authRepository = FakeAuthRepository(loggedIn = true)
        val profiles = FakeUserProfileRepository(profile(onboardingDone = true))
        assertEquals(AppStartDestination.Main, mainViewModel(authRepository, profiles).destination)

        runBlocking { authRepository.registerBiometric().getOrThrow() }

        assertEquals(AppStartDestination.BiometricLogin, mainViewModel(authRepository, profiles).destination)
    }

    /** 마이 탭 스위치로 끈 뒤(#113) — 등록 기록이 지워져 다음 콜드 스타트는 지문 화면을 건너뛴다. */
    @Test
    fun `지문 로그인을 끈 뒤 앱을 다시 켜면 피드로 시작한다`() {
        val authRepository = FakeAuthRepository(loggedIn = true, biometricEnabled = true)
        val profiles = FakeUserProfileRepository(profile(onboardingDone = true))
        assertEquals(AppStartDestination.BiometricLogin, mainViewModel(authRepository, profiles).destination)

        runBlocking { authRepository.setBiometricEnabled(false).getOrThrow() }

        assertEquals(AppStartDestination.Main, mainViewModel(authRepository, profiles).destination)
    }

    @Test
    fun `온보딩을 마치지 않은 세션은 온보딩으로, 마친 세션은 메인으로 간다`() {
        val notDone = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(false)))
        val done = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(true)))

        assertEquals(AppStartDestination.Onboarding, notDone.destination)
        assertEquals(AppStartDestination.Main, done.destination)
    }

    // ── 시작 경로에서 네트워크를 빼는 부분 (#74) ────────────────────────────────

    @Test
    fun `캐시가 완료면 프로필 조회를 기다리지 않고 메인으로 확정한다`() {
        // 스플래시가 이 값까지만 붙잡히므로, 서버 응답을 기다리면 그만큼 콜드 스타트가 늘어난다.
        val gate = CompletableDeferred<Result<UserProfile>>()
        val profiles = FakeUserProfileRepository(profile(true)).apply { onRefreshProfile = { gate.await() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        assertEquals(AppStartDestination.Main, viewModel.destination)
        gate.complete(Result.success(profile(true)))
    }

    @Test
    fun `캐시가 미완료면 프로필 조회 없이 온보딩으로 보낸다`() {
        // 온보딩 진입 판정이 서버를 다시 확인하므로 그 사이 완료된 사용자는 거기서 피드로 간다.
        var refreshCalls = 0
        val profiles =
            FakeUserProfileRepository(profile(false)).apply {
                onRefreshProfile = {
                    refreshCalls += 1
                    Result.success(profile(false))
                }
            }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        assertEquals(AppStartDestination.Onboarding, viewModel.destination)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `캐시로 메인에 들어간 뒤 서버가 미완료라고 하면 온보딩으로 다시 계산한다`() {
        // 캐시(profile(true))는 그대로 두고 서버만 미완료를 돌려준다 — 실제로 캐시 쓰기가 실패한 상황과 같다.
        // 확인이 한 번으로 끝나지 않으면 재계산이 또 메인으로 가서 또 확인을 걸어 영원히 돈다.
        var refreshCalls = 0
        val profiles =
            FakeUserProfileRepository(profile(true)).apply {
                onRefreshProfile = {
                    refreshCalls += 1
                    Result.success(profile(false))
                }
            }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        // 백그라운드 확인이 캐시를 뒤집었다 — 서버가 확정한 답이라 화면을 옮긴다.
        assertEquals(AppStartDestination.Onboarding, viewModel.destination)
        // 회귀 가드: 확인은 정확히 한 번이다. 0903 에 여기서 무한 재귀가 나 테스트 워커가 코루틴
        // 1억 4천만 개를 만들며 CPU 를 태웠고, 빌드가 끝나지 않았다.
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `캐시로 메인에 들어간 뒤 세션이 만료됐으면 세션을 정리하고 안내와 함께 로그인으로 간다`() {
        val auth = FakeAuthRepository(loggedIn = true)
        val profiles = FakeUserProfileRepository(profile(true)).apply { onRefreshProfile = { unauthorized() } }

        val viewModel = mainViewModel(auth, profiles)

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertTrue(auth.clearSessionCalls > 0)
        // 피드가 잠깐 보였다가 로그인으로 바뀐 경우다 — 화면이 아니라 셸이 만료를 확인했어도 설명은 필요하다.
        assertTrue(viewModel.expiryNotice)
    }

    @Test
    fun `캐시로 메인에 들어간 뒤 서버 확인이 실패하면 목적지를 유지하고 기록만 남긴다`() {
        // 오프라인 시작 — 이미 캐시로 들어와 있으므로 화면을 흔들 이유가 없다.
        val profiles = FakeUserProfileRepository(profile(true)).apply { onRefreshProfile = { networkFailure() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        assertEquals(AppStartDestination.Main, viewModel.destination)
        assertEquals(1, reporter.recorded.size)
        assertEquals("start_profile", reporter.recorded.single()["app_stage"])
    }

    // ── 프로필도 힌트도 없을 때만 서버를 기다린다 ──────────────────────────────

    @Test
    fun `아무것도 모르면 프로필 조회를 기다린다`() {
        val gate = CompletableDeferred<Result<UserProfile>>()
        val profiles = FakeUserProfileRepository().apply { onRefreshProfile = { gate.await() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        assertNull(viewModel.launch)
        gate.complete(Result.success(profile(true)))
        assertEquals(AppStartDestination.Main, viewModel.destination)
    }

    @Test
    fun `프로필 조회가 401 이면 안내와 함께 로그인으로 보낸다`() {
        val profiles = FakeUserProfileRepository().apply { onRefreshProfile = { unauthorized() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertTrue(viewModel.expiryNotice)
    }

    @Test
    fun `서버 확인이 네트워크로 실패해 로그인이 아닌 곳으로 가면 안내하지 않는다`() {
        // 오프라인 콜드 스타트 — 세션은 끝나지 않았으므로 만료라고 말하면 거짓말이다.
        val unknown = FakeUserProfileRepository().apply { onRefreshProfile = { networkFailure() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), unknown)

        assertEquals(AppStartDestination.Onboarding, viewModel.destination)
        assertFalse(viewModel.expiryNotice)
    }

    @Test
    fun `완료 여부를 전혀 모르는데 조회도 실패하면 메인이 아니라 온보딩으로 보낸다`() {
        // 신규 사용자의 첫 프로필 조회가 네트워크로 실패한 경우 — 메인으로 추정하면 온보딩을 영영 건너뛴다.
        val unknown = FakeUserProfileRepository().apply { onRefreshProfile = { networkFailure() } }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), unknown)

        assertEquals(AppStartDestination.Onboarding, viewModel.destination)
        assertEquals("start_profile", reporter.recorded.single()["app_stage"])
    }

    @Test
    fun `로그인 힌트만 있어도 기다리지 않고 메인으로 간다`() {
        val hintDone =
            FakeUserProfileRepository().apply {
                onboardingDoneHint = true
                onRefreshProfile = { networkFailure() }
            }

        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), hintDone)

        assertEquals(AppStartDestination.Main, viewModel.destination)
    }

    // ── 재계산 ────────────────────────────────────────────────────────────────

    @Test
    fun `다시 계산하면 목적지가 같아도 revision 이 올라 NavHost 가 새로 만들어진다`() {
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict())
        val first = requireNotNull(viewModel.launch)

        viewModel.onSessionEnded(SessionEndCause.LoggedOut)

        val second = requireNotNull(viewModel.launch)
        assertEquals(AppStartDestination.Login, second.destination)
        assertNotEquals(first.revision, second.revision)
        assertTrue(second.revision > first.revision)
    }

    // ── 프로세스 재생성 (#133) ────────────────────────────────────────────────

    /**
     * 세션이 살아 있는 재생성에서는 NavHost 세대가 이어져야 한다 — 세대가 바뀌면 `rememberNavController` 가
     * 이전 저장 상태를 못 찾고, 백스택과 함께 거기 매달린 온보딩 입력 초안까지 버려진다.
     */
    @Test
    fun `세션이 그대로인 프로세스 재생성은 NavHost 세대를 잇는다`() {
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(false)), handle)
        assertEquals(AppStartDestination.Onboarding, before.destination)

        val after = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(false)), handle)

        assertEquals(AppStartDestination.Onboarding, after.destination)
        assertEquals(requireNotNull(before.launch).revision, requireNotNull(after.launch).revision)
    }

    @Test
    fun `인증이 다시 필요해진 재생성은 세대를 올려 이전 백스택을 버린다`() {
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(false)), handle)

        // 그 사이 세션이 사라졌다 — 되살아난 백스택이 로그인 게이트를 건너뛰면 안 된다.
        val after = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict(), handle)

        assertEquals(AppStartDestination.Login, after.destination)
        assertNotEquals(requireNotNull(before.launch).revision, requireNotNull(after.launch).revision)
    }

    @Test
    fun `지문 확인이 남은 재생성도 세대를 올린다`() {
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(true)), handle)

        val after =
            mainViewModel(
                FakeAuthRepository(loggedIn = true, biometricEnabled = true),
                FakeUserProfileRepository(profile(true)),
                handle,
            )

        assertEquals(AppStartDestination.BiometricLogin, after.destination)
        assertNotEquals(requireNotNull(before.launch).revision, requireNotNull(after.launch).revision)
    }

    @Test
    fun `계산이 진행 중이면 다시 계산 요청은 합류해 한 번만 계산한다`() {
        val gate = CompletableDeferred<Result<UserProfile>>()
        var refreshCalls = 0
        val profiles =
            FakeUserProfileRepository().apply {
                onRefreshProfile = {
                    refreshCalls += 1
                    gate.await()
                }
            }
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)
        assertNull(viewModel.launch)

        viewModel.onSessionEnded(SessionEndCause.Expired)
        viewModel.onSessionEnded(SessionEndCause.Expired)
        gate.complete(Result.success(profile(true)))

        assertEquals(1, refreshCalls)
        assertEquals(AppStartDestination.Main, viewModel.destination)
    }

    @Test
    fun `딥링크는 소비할 때까지 보관하고, 계약에 맞지 않아 null 인 것은 무시한다`() {
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict())
        assertNull(viewModel.pendingDeepLink)

        viewModel.onDeepLink(AppDeepLink.PostingDetail(101))
        viewModel.onDeepLink(null)

        assertEquals(AppDeepLink.PostingDetail(101), viewModel.pendingDeepLink)
    }

    @Test
    fun `딥링크를 소비하면 비운다`() {
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict())
        viewModel.onDeepLink(AppDeepLink.PostingDetail(101))

        viewModel.consumeDeepLink()

        assertNull(viewModel.pendingDeepLink)
    }

    @Test
    fun `첫 시작 계산이 끝나기 전에 받은 딥링크는 계산이 끝나도 남는다`() {
        // 알림으로 콜드 스타트 — intent 의 딥링크는 세션·프로필 확인보다 먼저 실린다.
        val gate = CompletableDeferred<Result<UserProfile>>()
        val profiles = FakeUserProfileRepository().apply { onRefreshProfile = { gate.await() } }
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)
        viewModel.onDeepLink(AppDeepLink.PostingDetail(101))

        gate.complete(Result.success(profile(true)))

        assertEquals(AppStartDestination.Main, viewModel.destination)
        assertEquals(AppDeepLink.PostingDetail(101), viewModel.pendingDeepLink)
    }

    @Test
    fun `세션 종료로 다시 계산하면 소비되지 않은 딥링크를 버린다`() {
        // 로그아웃 뒤 다른 계정으로 로그인해도 남의 알림 공고가 열리지 않는다.
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict())
        viewModel.onDeepLink(AppDeepLink.PostingDetail(101))

        viewModel.onSessionEnded(SessionEndCause.LoggedOut)

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertNull(viewModel.pendingDeepLink)
    }

    /**
     * 로그아웃 상태에서 알림으로 로그인 화면에 온 뒤 카카오 앱에 다녀오는 사이 프로세스가 죽는 경로(#352).
     *
     * `MainActivity` 는 재생성에서 intent 를 다시 읽지 않으므로(`savedInstanceState != null`) 저장 상태가
     * 유일한 출처다. 여기 남지 않으면 로그인을 마쳐도 공고 상세 대신 피드 홈이 뜬다.
     */
    @Test
    fun `보관 중인 딥링크는 프로세스 재생성을 건넌다`() {
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict(), handle)
        before.onDeepLink(AppDeepLink.PostingDetail(101))

        val after = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict(), handle)

        assertEquals(AppDeepLink.PostingDetail(101), after.pendingDeepLink)
    }

    @Test
    fun `소비한 딥링크는 재생성 뒤에 되살아나지 않는다`() {
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(true)), handle)
        before.onDeepLink(AppDeepLink.PostingDetail(101))
        before.consumeDeepLink()

        val after = mainViewModel(FakeAuthRepository(loggedIn = true), FakeUserProfileRepository(profile(true)), handle)

        assertNull(after.pendingDeepLink)
    }

    @Test
    fun `세션 종료로 버린 딥링크는 재생성 뒤에도 돌아오지 않는다`() {
        // 인메모리에서만 버리면 다른 계정으로 로그인한 뒤 프로세스가 한 번 죽는 것으로 남의 공고가 되살아난다.
        val handle = SavedStateHandle()
        val before = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict(), handle)
        before.onDeepLink(AppDeepLink.PostingDetail(101))
        before.onSessionEnded(SessionEndCause.LoggedOut)

        val after = mainViewModel(FakeAuthRepository(loggedIn = false), FakeUserProfileRepository.strict(), handle)

        assertNull(after.pendingDeepLink)
    }

    // ── 세션 만료 안내 (#128) ─────────────────────────────────────────────────

    @Test
    fun `만료로 로그인 화면에 돌아오면 이유를 알린다`() {
        // 피드가 401 을 만나면 network 계층이 세션을 정리한 뒤 화면이 종료를 알린다.
        val auth = FakeAuthRepository(loggedIn = true)
        val viewModel = mainViewModel(auth, FakeUserProfileRepository(profile(true)))
        auth.loggedIn = false

        viewModel.onSessionEnded(SessionEndCause.Expired)

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertTrue(viewModel.expiryNotice)
    }

    @Test
    fun `사용자가 로그아웃하면 이유를 알리지 않는다`() {
        val auth = FakeAuthRepository(loggedIn = true)
        val viewModel = mainViewModel(auth, FakeUserProfileRepository(profile(true)))
        auth.loggedIn = false

        viewModel.onSessionEnded(SessionEndCause.LoggedOut)

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertFalse(viewModel.expiryNotice)
    }

    @Test
    fun `로그아웃 뒤 남은 요청이 401 로 돌아와도 만료로 바뀌지 않는다`() {
        // 로그아웃 요청이 나간 뒤 진행 중이던 조회가 401 을 물고 돌아오는 실제 순서다. 셸이 스스로 확인한
        // 401(계산 결과가 로그인)까지 겹쳐도 사용자가 끝낸 세션에 만료 안내를 붙이지 않는다.
        val gate = CompletableDeferred<Result<UserProfile>>()
        val profiles = FakeUserProfileRepository().apply { onRefreshProfile = { gate.await() } }
        val viewModel = mainViewModel(FakeAuthRepository(loggedIn = true), profiles)
        assertNull(viewModel.launch)

        viewModel.onSessionEnded(SessionEndCause.LoggedOut)
        viewModel.onSessionEnded(SessionEndCause.Expired)
        gate.complete(unauthorized())

        assertEquals(AppStartDestination.Login, viewModel.destination)
        assertFalse(viewModel.expiryNotice)
    }

    @Test
    fun `안내는 닫으면 꺼지고 NavHost 는 다시 만들지 않는다`() {
        val auth = FakeAuthRepository(loggedIn = true)
        val viewModel = mainViewModel(auth, FakeUserProfileRepository(profile(true)))
        auth.loggedIn = false
        viewModel.onSessionEnded(SessionEndCause.Expired)
        val shown = requireNotNull(viewModel.launch)

        viewModel.consumeSessionExpiryNotice()

        val dismissed = requireNotNull(viewModel.launch)
        assertFalse(dismissed.sessionExpiryNotice)
        // revision 이 그대로여야 백스택이 살아 있는 채로 안내만 사라진다.
        assertEquals(shown.revision, dismissed.revision)
    }

    @Test
    fun `다음 세션 종료가 로그아웃이면 남아 있던 안내가 꺼진다`() {
        val auth = FakeAuthRepository(loggedIn = true)
        val viewModel = mainViewModel(auth, FakeUserProfileRepository(profile(true)))
        auth.loggedIn = false
        viewModel.onSessionEnded(SessionEndCause.Expired)
        assertTrue(viewModel.expiryNotice)

        viewModel.onSessionEnded(SessionEndCause.LoggedOut)

        assertFalse(viewModel.expiryNotice)
    }

    @Test
    fun `지문 뒤 만료는 다시 계산하지 않고 안내만 켠다`() {
        // 지문 화면은 온보딩 그래프 안에서 스스로 로그인 화면으로 옮긴다 — 여기서 재계산하면 세션 정리가
        // 실패한 기기가 다시 지문 화면으로 돌아가 로그인 화면에 닿지 못한다.
        val viewModel =
            mainViewModel(FakeAuthRepository(loggedIn = true, biometricEnabled = true), FakeUserProfileRepository.strict())
        val started = requireNotNull(viewModel.launch)
        assertEquals(AppStartDestination.BiometricLogin, started.destination)

        viewModel.raiseSessionExpiryNotice()

        val notified = requireNotNull(viewModel.launch)
        assertTrue(notified.sessionExpiryNotice)
        assertEquals(started.revision, notified.revision)
        assertEquals(AppStartDestination.BiometricLogin, notified.destination)
    }

    @Test
    fun `저장된 테마를 시작부터 들고 있는다`() {
        // 첫 컴포지션에서 이미 값이 있어야 반대 테마가 한 프레임 스쳤다 바뀌지 않는다.
        val settings = FakeAppSettingsRepository(initialThemeMode = ThemeMode.Dark)

        val viewModel =
            mainViewModel(
                FakeAuthRepository(loggedIn = false),
                FakeUserProfileRepository.strict(),
                appSettingsRepository = settings,
            )

        assertEquals(ThemeMode.Dark, viewModel.themeMode)
    }

    @Test
    fun `테마가 바뀌면 셸이 따라간다`() {
        val settings = FakeAppSettingsRepository()
        val viewModel =
            mainViewModel(
                FakeAuthRepository(loggedIn = false),
                FakeUserProfileRepository.strict(),
                appSettingsRepository = settings,
            )

        settings.themeModeState.value = ThemeMode.Light

        assertEquals(ThemeMode.Light, viewModel.themeMode)
    }
}

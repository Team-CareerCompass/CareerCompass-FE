package com.careercompass.feature.profile.presentation.home

import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.domain.settings.AppSettingsRepository
import com.careercompass.core.domain.usecase.auth.LogoutUseCase
import com.careercompass.core.model.settings.ThemeMode
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.profile.domain.model.ProfileCompletionGap
import com.careercompass.feature.profile.domain.model.ProfileHome
import com.careercompass.feature.profile.domain.model.completionGaps
import com.careercompass.feature.profile.domain.usecase.LoadProfileHomeUseCase
import com.careercompass.feature.profile.domain.usecase.ObserveProfileUseCase
import com.careercompass.feature.profile.presentation.reporting.ProfileFailureStage
import com.careercompass.feature.profile.presentation.reporting.recordProfileFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 마이 홈이 그리는 값.
 *
 * @property profile 마지막으로 아는 프로필. 캐시가 먼저 채우고 조회가 성공하면 갈아 끼운다. null 이면
 *   아직 한 번도 받은 적이 없다는 뜻이라, 그때만 실패가 화면을 덮는다.
 * @property loadFailure 조회가 실패한 사유. 캐시가 있으면 화면을 덮지 않고 스낵바([message])로만 알린다.
 * @property sessionEnd 세션이 끝난 사유. 셸이 로그인 화면의 만료 안내 여부를 이 값으로 정한다.
 * @property canEnrollBiometric 이 기기에서 지문을 **등록**할 수 있는가. 플랫폼을 아는 화면이 알려 주기 전까지는
 *   null(판정 전)이다 — false 로 시작하면 첫 프레임에 「쓸 수 없다」 안내가 한 번 스쳤다 사라진다.
 * @property isBiometricBusy 프롬프트가 떠 있는 동안과 서버 등록·해제를 기다리는 동안 true.
 * @property isEnrollPromptRequested 켜기를 눌러 등록 프롬프트를 띄워야 한다. 화면이 띄우고 소비하는 단발 신호다.
 */
public data class ProfileHomeUiState(
    val profile: UserProfile? = null,
    val experienceCardCount: Int? = null,
    val pastApplicationCount: Int? = null,
    val isLoading: Boolean = false,
    val loadFailure: FailureKind? = null,
    val message: ProfileHomeMessage? = null,
    val pendingMenu: ProfileHomeMenu? = null,
    val isBiometricEnabled: Boolean = false,
    val canEnrollBiometric: Boolean? = null,
    val isBiometricBusy: Boolean = false,
    val isEnrollPromptRequested: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val isThemeDialogVisible: Boolean = false,
    val isLogoutDialogVisible: Boolean = false,
    val isLoggingOut: Boolean = false,
    val sessionEnd: ProfileSessionEnd? = null,
) : UiState {
    /** 세션이 끝났는가 — 왜 끝났는지는 [sessionEnd] 가 갖는다. */
    val sessionEnded: Boolean get() = sessionEnd != null

    /**
     * 실패 화면이 이 화면을 통째로 덮는가 — **아는 프로필이 하나도 없을 때뿐이다.**
     *
     * 캐시가 있으면 이름·소속·완성도는 여전히 참이다(마지막으로 서버가 준 값이다). 그것을 덮고 실패 화면을
     * 띄우면 오프라인 사용자는 자기 프로필조차 못 본다 — 앱 시작이 캐시로 목적지를 정하는 규칙과 같은 방향이다
     * (`docs/spec/edge-states.md` §5 「오프라인 시작」).
     */
    val isFailureVisible: Boolean get() = profile == null && loadFailure != null

    /** 첫 조회 중이라 그릴 것이 아무것도 없는 상태. 캐시가 있으면 로딩을 화면으로 그리지 않는다. */
    val isInitialLoading: Boolean get() = profile == null && loadFailure == null && isLoading

    /** 완성도 카드가 「무엇을 채우면 되는지」 고를 근거. 프로필을 모르면 빈 목록이다. */
    val completionGaps: List<ProfileCompletionGap>
        get() =
            profile
                ?.let { ProfileHome(it, experienceCardCount, pastApplicationCount).completionGaps() }
                .orEmpty()

    /**
     * 지문 스위치를 지금 움직일 수 있는가.
     *
     * **끄는 방향은 기기 지원과 무관하게 늘 열어 둔다.** 켜 둔 채 지문을 지운 기기는 시작 목적지가 지문 화면이라
     * 되돌릴 자리가 여기뿐인데, 기기 판정으로 함께 잠그면 그 사용자가 갇힌다(#113). 켜는 방향만
     * [canEnrollBiometric] 이 확정되고 참일 때 연다.
     */
    val isBiometricSwitchEnabled: Boolean
        get() = !isBiometricBusy && (isBiometricEnabled || canEnrollBiometric == true)

    /** 「이 기기에서는 켤 수 없다」 안내를 보일 때 — 판정이 끝났고, 못 쓰는데 켜져 있지도 않은 경우뿐이다. */
    val isBiometricUnavailableNoticeVisible: Boolean
        get() = canEnrollBiometric == false && !isBiometricEnabled
}

/** 화면이 [ProfileHomeViewModel] 에 보내는 것. 생체 프롬프트의 결과도 여기로 들어온다. */
public sealed interface ProfileHomeIntent : MviIntent {
    public data class Screen(
        val event: ProfileHomeEvent,
    ) : ProfileHomeIntent

    /** 화면에 (다시) 들어왔다 — 서버 값을 맞춘다. 진행 중인 조회가 있으면 겹치지 않는다. */
    public data object Refresh : ProfileHomeIntent

    /** 기기가 강한 생체 인증을 지금 쓸 수 있는지 — 플랫폼을 아는 화면이 알려 준다. */
    public data class BiometricAvailabilityChanged(
        val canEnroll: Boolean,
    ) : ProfileHomeIntent

    /** 등록 프롬프트를 띄운 뒤 보낸다 — 재구성 때마다 같은 프롬프트가 다시 뜨지 않게. */
    public data object ConsumeEnrollPromptRequest : ProfileHomeIntent

    /** 지문이 맞았다 — 이제서야 서버에 기기를 등록한다. */
    public data object BiometricEnrollSucceeded : ProfileHomeIntent

    /** 사용자가 프롬프트를 닫았다 — 켜지 않기로 한 것이라 스위치를 원래 자리에 두고 잠금만 푼다. */
    public data object BiometricEnrollCancelled : ProfileHomeIntent

    /** 지문 확인이 오류로 끝났다. 스위치는 원래 자리에 남고 사유는 리포팅에만 남는다. */
    public data class BiometricEnrollFailed(
        val cause: Throwable,
    ) : ProfileHomeIntent

    public data object ConsumeMenuNavigation : ProfileHomeIntent

    public data object ConsumeMessage : ProfileHomeIntent

    public data object ConsumeSessionEnded : ProfileHomeIntent
}

/** 상태가 겪은 것. [ProfileHomeViewModel] 만 만든다. */
public sealed interface ProfileHomeReducerEvent : ReducerEvent {
    public data object LoadStarted : ProfileHomeReducerEvent

    public data class Loaded(
        val home: ProfileHome,
    ) : ProfileHomeReducerEvent

    public data class LoadFailed(
        val kind: FailureKind,
    ) : ProfileHomeReducerEvent

    /** 캐시가 흘러 들어왔다. 조회 결과가 아니므로 로딩·실패 표시를 건드리지 않는다. */
    public data class CachedProfileChanged(
        val profile: UserProfile?,
    ) : ProfileHomeReducerEvent

    public data class MenuRequested(
        val menu: ProfileHomeMenu,
    ) : ProfileHomeReducerEvent

    public data class MessageRaised(
        val message: ProfileHomeMessage,
    ) : ProfileHomeReducerEvent

    public data class BiometricEnabledChanged(
        val isEnabled: Boolean,
    ) : ProfileHomeReducerEvent

    public data class BiometricAvailabilityChanged(
        val canEnroll: Boolean,
    ) : ProfileHomeReducerEvent

    /** 켜기를 눌렀다 — 스위치를 잠그고 프롬프트를 요청한다. */
    public data object EnrollPromptRequested : ProfileHomeReducerEvent

    public data object EnrollPromptRequestConsumed : ProfileHomeReducerEvent

    public data class BiometricBusyChanged(
        val isBusy: Boolean,
    ) : ProfileHomeReducerEvent

    public data class ThemeModeChanged(
        val mode: ThemeMode,
    ) : ProfileHomeReducerEvent

    public data class ThemeDialogVisibilityChanged(
        val isVisible: Boolean,
    ) : ProfileHomeReducerEvent

    public data class LogoutDialogVisibilityChanged(
        val isVisible: Boolean,
    ) : ProfileHomeReducerEvent

    /** 로그아웃 요청이 나갔다 — 다이얼로그를 닫고 진행 표시를 켠다. */
    public data object LogoutStarted : ProfileHomeReducerEvent

    /** 세션이 끝났다 — 로그아웃이 끝났거나(성공·실패 무관) 401 을 만났다. */
    public data class SessionEnded(
        val cause: ProfileSessionEnd,
    ) : ProfileHomeReducerEvent

    public data object MenuNavigationConsumed : ProfileHomeReducerEvent

    public data object MessageConsumed : ProfileHomeReducerEvent

    public data object SessionEndedConsumed : ProfileHomeReducerEvent
}

/**
 * 마이 홈(Figma 05 · 01)의 상태 — 프로필 요약·완성도·메뉴와, 자리표시자에서 넘겨받은 계정 설정 셋
 * (지문 로그인·화면 테마·로그아웃). 진입점은 [onIntent] 하나, 전이는 [reduce] 한 곳이다.
 *
 * ### 캐시 먼저, 서버는 그다음
 * [ObserveProfileUseCase] 가 마지막으로 받아 둔 프로필을 흘려 첫 프레임을 채우고, [LoadProfileHomeUseCase]
 * 가 서버 값과 개수 둘을 맞춘다. **캐시가 있는 동안에는 실패가 화면을 덮지 않는다** — 스낵바 한 줄로만
 * 알리고 보이던 값을 그대로 둔다([ProfileHomeUiState.isFailureVisible] 의 KDoc).
 *
 * ### 401 은 화면에 그리지 않는다
 * 세션이 끝난 실패는 실패 화면 대신 [ProfileHomeUiState.sessionEnded] 를 올려 셸이 로그인으로 보낸다
 * (`docs/spec/edge-states.md` §6-6). 캐시가 있어도 마찬가지다 — 토큰이 죽은 채로 남겨 두면 이 화면의
 * 어떤 조작도 되지 않는다.
 *
 * ### 계정 설정 셋이 use case 를 거치지 않는 이유
 * 지문 등록·테마·로그아웃은 프로필(§2·§3·§4)이 아니라 **세션과 기기 설정**이고, 그 계약은 이미 `core:domain`
 * 에 있다([AuthRepository] · [AppSettingsRepository] · [LogoutUseCase]). 그 위에 profile 전용 use case 를
 * 한 겹 더 씌우면 이 화면 말고는 부를 곳이 없는 껍데기가 모듈마다 늘어난다. 프로필 API 로 나가는 길은
 * 전부 use case 를 지난다.
 *
 * 로그아웃은 성공·실패와 무관하게 [ProfileHomeUiState.sessionEnded] 로 끝난다 — [LogoutUseCase] 는 서버
 * 호출이 실패해도 로컬 세션을 정리하므로, 실패했다고 사용자를 로그인된 화면에 붙잡아 두면 나갈 방법이 없다.
 */
@HiltViewModel
public class ProfileHomeViewModel
    @Inject
    constructor(
        observeProfile: ObserveProfileUseCase,
        private val loadProfileHome: LoadProfileHomeUseCase,
        private val authRepository: AuthRepository,
        private val logout: LogoutUseCase,
        private val appSettingsRepository: AppSettingsRepository,
        private val errorReporter: ErrorReporter,
    ) : MviViewModel<ProfileHomeIntent, ProfileHomeUiState, ProfileHomeReducerEvent>(ProfileHomeUiState()) {
        private var loadJob: Job? = null
        private var logoutJob: Job? = null
        private var biometricJob: Job? = null

        init {
            viewModelScope.launch {
                observeProfile().collect { profile -> dispatch(ProfileHomeReducerEvent.CachedProfileChanged(profile)) }
            }
            viewModelScope.launch {
                authRepository.isBiometricEnabled.collect { enabled ->
                    dispatch(ProfileHomeReducerEvent.BiometricEnabledChanged(enabled))
                }
            }
            viewModelScope.launch {
                appSettingsRepository.themeMode.collect { mode -> dispatch(ProfileHomeReducerEvent.ThemeModeChanged(mode)) }
            }
            load()
        }

        override fun onIntent(intent: ProfileHomeIntent) {
            when (intent) {
                is ProfileHomeIntent.Screen -> {
                    onEvent(intent.event)
                }

                ProfileHomeIntent.Refresh -> {
                    load()
                }

                is ProfileHomeIntent.BiometricAvailabilityChanged -> {
                    dispatch(ProfileHomeReducerEvent.BiometricAvailabilityChanged(intent.canEnroll))
                }

                ProfileHomeIntent.ConsumeEnrollPromptRequest -> {
                    dispatch(ProfileHomeReducerEvent.EnrollPromptRequestConsumed)
                }

                ProfileHomeIntent.BiometricEnrollSucceeded -> {
                    registerBiometric()
                }

                ProfileHomeIntent.BiometricEnrollCancelled -> {
                    dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(false))
                }

                is ProfileHomeIntent.BiometricEnrollFailed -> {
                    recordBiometricFailure(intent.cause)
                    dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(false))
                }

                ProfileHomeIntent.ConsumeMenuNavigation -> {
                    dispatch(ProfileHomeReducerEvent.MenuNavigationConsumed)
                }

                ProfileHomeIntent.ConsumeMessage -> {
                    dispatch(ProfileHomeReducerEvent.MessageConsumed)
                }

                ProfileHomeIntent.ConsumeSessionEnded -> {
                    dispatch(ProfileHomeReducerEvent.SessionEndedConsumed)
                }
            }
        }

        override fun reduce(
            state: ProfileHomeUiState,
            event: ProfileHomeReducerEvent,
        ): ProfileHomeUiState =
            when (event) {
                // 지난 실패를 함께 지운다 — 안 지우면 재시도하는 동안 실패 화면이 그대로 남아 진행 표시가 묻힌다.
                ProfileHomeReducerEvent.LoadStarted -> {
                    state.copy(isLoading = true, loadFailure = null)
                }

                is ProfileHomeReducerEvent.Loaded -> {
                    state.copy(
                        profile = event.home.profile,
                        experienceCardCount = event.home.experienceCardCount,
                        pastApplicationCount = event.home.pastApplicationCount,
                        isLoading = false,
                        loadFailure = null,
                    )
                }

                is ProfileHomeReducerEvent.LoadFailed -> {
                    state.copy(isLoading = false, loadFailure = event.kind)
                }

                // null 은 무시한다 — 「아직 캐시가 없다」와 로그아웃이 캐시를 비우는 순간이 여기로 오는데,
                // 그것으로 보이던 프로필을 지우면 화면이 나가는 길에 한 번 빈 채로 깜빡인다.
                is ProfileHomeReducerEvent.CachedProfileChanged -> {
                    event.profile?.let { state.copy(profile = it) } ?: state
                }

                is ProfileHomeReducerEvent.MenuRequested -> {
                    state.copy(pendingMenu = event.menu)
                }

                is ProfileHomeReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                is ProfileHomeReducerEvent.BiometricEnabledChanged -> {
                    state.copy(isBiometricEnabled = event.isEnabled)
                }

                is ProfileHomeReducerEvent.BiometricAvailabilityChanged -> {
                    state.copy(canEnrollBiometric = event.canEnroll)
                }

                ProfileHomeReducerEvent.EnrollPromptRequested -> {
                    state.copy(isBiometricBusy = true, isEnrollPromptRequested = true)
                }

                ProfileHomeReducerEvent.EnrollPromptRequestConsumed -> {
                    state.copy(isEnrollPromptRequested = false)
                }

                is ProfileHomeReducerEvent.BiometricBusyChanged -> {
                    state.copy(isBiometricBusy = event.isBusy)
                }

                is ProfileHomeReducerEvent.ThemeModeChanged -> {
                    state.copy(themeMode = event.mode)
                }

                is ProfileHomeReducerEvent.ThemeDialogVisibilityChanged -> {
                    state.copy(isThemeDialogVisible = event.isVisible)
                }

                is ProfileHomeReducerEvent.LogoutDialogVisibilityChanged -> {
                    state.copy(isLogoutDialogVisible = event.isVisible)
                }

                ProfileHomeReducerEvent.LogoutStarted -> {
                    state.copy(isLogoutDialogVisible = false, isLoggingOut = true)
                }

                is ProfileHomeReducerEvent.SessionEnded -> {
                    state.copy(isLoggingOut = false, sessionEnd = event.cause)
                }

                ProfileHomeReducerEvent.MenuNavigationConsumed -> {
                    state.copy(pendingMenu = null)
                }

                ProfileHomeReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                ProfileHomeReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnd = null)
                }
            }

        private fun onEvent(event: ProfileHomeEvent) {
            when (event) {
                is ProfileHomeEvent.MenuClicked -> dispatch(ProfileHomeReducerEvent.MenuRequested(event.menu))
                ProfileHomeEvent.RetryClicked -> load()
                is ProfileHomeEvent.BiometricToggled -> toggleBiometric(event.enabled)
                ProfileHomeEvent.ThemeClicked -> dispatch(ProfileHomeReducerEvent.ThemeDialogVisibilityChanged(true))
                ProfileHomeEvent.ThemeDismissed -> dispatch(ProfileHomeReducerEvent.ThemeDialogVisibilityChanged(false))
                is ProfileHomeEvent.ThemeSelected -> selectThemeMode(event.mode)
                ProfileHomeEvent.LogoutClicked -> dispatch(ProfileHomeReducerEvent.LogoutDialogVisibilityChanged(true))
                ProfileHomeEvent.LogoutDismissed -> dispatch(ProfileHomeReducerEvent.LogoutDialogVisibilityChanged(false))
                ProfileHomeEvent.LogoutConfirmed -> endSession()
            }
        }

        /** 진행 중인 조회가 있으면 무시한다 — 탭을 오가며 같은 요청을 겹쳐 보내지 않는다. */
        private fun load() {
            if (loadJob?.isActive == true) return
            dispatch(ProfileHomeReducerEvent.LoadStarted)
            loadJob =
                viewModelScope.launch {
                    loadProfileHome()
                        .onSuccess { home -> dispatch(ProfileHomeReducerEvent.Loaded(home)) }
                        .onFailure(::onLoadFailure)
                }
        }

        /**
         * 실패의 처분은 「지금 그릴 것이 남아 있는가」로 갈린다.
         *
         * 401 은 어느 쪽이든 세션 종료다. 나머지는 캐시가 있으면 스낵바, 없으면 실패 화면이고 리포팅 단계도
         * 그 둘을 갈라 센다 — 조용한 실패와 사용자가 실제로 막힌 실패는 콘솔에서 구분돼야 한다.
         */
        private fun onLoadFailure(cause: Throwable) {
            if (cause is CoreDataFailure.Unauthorized) {
                errorReporter.recordProfileFailure(ProfileFailureStage.HomeLoad, cause)
                dispatch(ProfileHomeReducerEvent.SessionEnded(ProfileSessionEnd.Expired))
                return
            }
            val hasCache = currentState.profile != null
            errorReporter.recordProfileFailure(
                stage = if (hasCache) ProfileFailureStage.HomeRefresh else ProfileFailureStage.HomeLoad,
                throwable = cause,
            )
            dispatch(ProfileHomeReducerEvent.LoadFailed(cause.toFailureKind()))
            if (hasCache) dispatch(ProfileHomeReducerEvent.MessageRaised(ProfileHomeMessage.RefreshFailed))
        }

        /**
         * 지문이 맞았다 — 이제서야 서버에 기기를 등록한다. 등록까지 성공해야 켜진 것이다(#98 과 같은 규칙).
         *
         * 등록이 진행 중이면 무시한다: 프롬프트가 성공을 두 번 전달해도 서버 호출은 한 번이다.
         */
        private fun registerBiometric() {
            if (biometricJob?.isActive == true) return
            dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(true))
            biometricJob =
                viewModelScope.launch {
                    authRepository.registerBiometric().onFailure(::recordBiometricFailure)
                    dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(false))
                }
        }

        /**
         * 켜는 쪽은 프롬프트를 **요청**만 한다 — 생체 프롬프트는 Activity 를 아는 화면이 띄우고 결과만 돌아온다.
         * 끄는 쪽은 확인할 것이 없어 곧바로 등록 기록을 지운다.
         */
        private fun toggleBiometric(enabled: Boolean) {
            if (currentState.isBiometricBusy) return
            if (enabled) {
                dispatch(ProfileHomeReducerEvent.EnrollPromptRequested)
            } else {
                disableBiometric()
            }
        }

        private fun disableBiometric() {
            dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(true))
            biometricJob =
                viewModelScope.launch {
                    authRepository.setBiometricEnabled(false).onFailure(::recordBiometricFailure)
                    dispatch(ProfileHomeReducerEvent.BiometricBusyChanged(false))
                }
        }

        /**
         * 고른 테마를 저장한다. 다이얼로그는 **저장을 기다리지 않고** 닫는다 — 화면은 저장소 흐름을 되비추므로
         * 저장이 실패하면 값이 저절로 원래대로 남는다(지문 스위치와 같은 규칙).
         */
        private fun selectThemeMode(mode: ThemeMode) {
            dispatch(ProfileHomeReducerEvent.ThemeDialogVisibilityChanged(false))
            if (mode == currentState.themeMode) return
            viewModelScope.launch {
                runCatching { appSettingsRepository.setThemeMode(mode) }
                    .onFailure { cause -> errorReporter.recordProfileFailure(ProfileFailureStage.ThemeMode, cause) }
            }
        }

        /** 진행 중인 로그아웃이 있으면 무시한다 — 다이얼로그를 닫아도 버튼 연타가 요청을 겹치게 하지 않는다. */
        private fun endSession() {
            if (logoutJob?.isActive == true) return
            dispatch(ProfileHomeReducerEvent.LogoutStarted)
            logoutJob =
                viewModelScope.launch {
                    logout().onFailure { cause -> errorReporter.recordProfileFailure(ProfileFailureStage.Logout, cause) }
                    dispatch(ProfileHomeReducerEvent.SessionEnded(ProfileSessionEnd.LoggedOut))
                }
        }

        private fun recordBiometricFailure(cause: Throwable) {
            errorReporter.recordProfileFailure(ProfileFailureStage.BiometricToggle, cause)
        }
    }

package com.careercompass.core.domain.testing

import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.model.auth.Session
import com.careercompass.core.model.auth.SocialProvider
import com.careercompass.core.model.auth.TokenBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * [AuthRepository] fake 정본.
 *
 * 기본은 토큰과 로그인 상태를 메모리에 저장하며 모든 호출을 기록한다. 특정 실패·서버 응답은 `onX` 로
 * 갈아끼운다. 호출 금지 경계가 필요한 테스트는 [strict] 로 시작해 실제로 쓰는 경로만 연다.
 */
public class FakeAuthRepository(
    loggedIn: Boolean = false,
    biometricEnabled: Boolean = false,
    biometricEnrollDeclined: Boolean = false,
    @Volatile public var accessToken: String? = null,
    @Volatile public var refreshToken: String? = null,
    public var session: Session = Session(DEFAULT_ACCESS_TOKEN, DEFAULT_REFRESH_TOKEN, isNewUser = false, expiresInSeconds = 3600),
    public var rotatedTokens: TokenBundle = TokenBundle(DEFAULT_ACCESS_TOKEN, DEFAULT_REFRESH_TOKEN, expiresInSeconds = 3600),
    public var onSocialLogin: (suspend (SocialProvider, String, String?) -> Result<Session>)? = null,
    public var onSaveSession: (suspend (Session) -> Result<Unit>)? = null,
    public var onGetAccessToken: (suspend () -> Result<String?>)? = null,
    public var onGetRefreshToken: (suspend () -> Result<String?>)? = null,
    public var onRotateToken: (suspend FakeAuthRepository.() -> Result<TokenBundle>)? = null,
    public var onLogout: (suspend () -> Result<Unit>)? = null,
    public var onClearSession: (suspend () -> Result<Unit>)? = null,
    public var onRegisterBiometric: (suspend () -> Result<Unit>)? = null,
    public var onDeclineBiometricEnroll: (suspend () -> Result<Unit>)? = null,
    public var onSetBiometricEnabled: (suspend (Boolean) -> Result<Unit>)? = null,
) : AuthRepository {
    public val loggedInState: MutableStateFlow<Boolean> = MutableStateFlow(loggedIn)

    /** 세션 경계 — 기본 동작의 [saveSession]·[logout]·[clearSession] 이 올린다. 직접 올려 경계만 흉내 낼 수도 있다. */
    public val sessionGenerationState: MutableStateFlow<Long> = MutableStateFlow(0L)
    public val biometricEnabledState: MutableStateFlow<Boolean> = MutableStateFlow(biometricEnabled)
    public val biometricEnrollDeclinedState: MutableStateFlow<Boolean> = MutableStateFlow(biometricEnrollDeclined)

    public var loggedIn: Boolean
        get() = loggedInState.value
        set(value) {
            loggedInState.value = value
        }

    override val isLoggedIn: Flow<Boolean> get() = loggedInState
    override val sessionGeneration: Flow<Long> get() = sessionGenerationState
    override val isBiometricEnabled: Flow<Boolean> get() = biometricEnabledState
    override val isBiometricEnrollDeclined: Flow<Boolean> get() = biometricEnrollDeclinedState

    public data class SocialLoginCall(
        val provider: SocialProvider,
        val providerToken: String,
        val fcmToken: String?,
    )

    public val socialLoginCalls: CopyOnWriteArrayList<SocialLoginCall> = CopyOnWriteArrayList()
    public val savedSessions: CopyOnWriteArrayList<Session> = CopyOnWriteArrayList()
    private val rotateCounter = AtomicInteger()
    private val logoutCounter = AtomicInteger()
    private val clearCounter = AtomicInteger()
    private val registerBiometricCounter = AtomicInteger()
    private val declineBiometricEnrollCounter = AtomicInteger()
    private val setBiometricEnabledCounter = AtomicInteger()

    public val rotateTokenCalls: Int get() = rotateCounter.get()
    public val logoutCalls: Int get() = logoutCounter.get()
    public val clearSessionCalls: Int get() = clearCounter.get()
    public val registerBiometricCalls: Int get() = registerBiometricCounter.get()
    public val declineBiometricEnrollCalls: Int get() = declineBiometricEnrollCounter.get()
    public val setBiometricEnabledCalls: Int get() = setBiometricEnabledCounter.get()

    override suspend fun socialLogin(
        provider: SocialProvider,
        providerToken: String,
        fcmToken: String?,
    ): Result<Session> {
        socialLoginCalls += SocialLoginCall(provider, providerToken, fcmToken)
        onSocialLogin?.let { return it(provider, providerToken, fcmToken) }
        return Result.success(session)
    }

    override suspend fun saveSession(session: Session): Result<Unit> {
        savedSessions += session
        onSaveSession?.let { return it(session) }
        sessionGenerationState.value += 1
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        loggedIn = true
        return Result.success(Unit)
    }

    override suspend fun getAccessToken(): Result<String?> {
        onGetAccessToken?.let { return it() }
        return Result.success(accessToken)
    }

    override suspend fun getRefreshToken(): Result<String?> {
        onGetRefreshToken?.let { return it() }
        return Result.success(refreshToken)
    }

    override suspend fun rotateToken(): Result<TokenBundle> {
        rotateCounter.incrementAndGet()
        onRotateToken?.let { return it(this) }
        if (refreshToken == null) return Result.failure(IllegalStateException("리프레시 토큰이 존재하지 않습니다."))
        accessToken = rotatedTokens.accessToken
        refreshToken = rotatedTokens.refreshToken
        loggedIn = true
        return Result.success(rotatedTokens)
    }

    override suspend fun logout(): Result<Unit> {
        logoutCounter.incrementAndGet()
        onLogout?.let { return it() }
        sessionGenerationState.value += 1
        accessToken = null
        refreshToken = null
        loggedIn = false
        return Result.success(Unit)
    }

    override suspend fun clearSession(): Result<Unit> {
        clearCounter.incrementAndGet()
        onClearSession?.let { return it() }
        sessionGenerationState.value += 1
        accessToken = null
        refreshToken = null
        loggedIn = false
        return Result.success(Unit)
    }

    override suspend fun registerBiometric(): Result<Unit> {
        registerBiometricCounter.incrementAndGet()
        onRegisterBiometric?.let { return it() }
        biometricEnabledState.value = true
        return Result.success(Unit)
    }

    override suspend fun declineBiometricEnroll(): Result<Unit> {
        declineBiometricEnrollCounter.incrementAndGet()
        onDeclineBiometricEnroll?.let { return it() }
        biometricEnrollDeclinedState.value = true
        return Result.success(Unit)
    }

    override suspend fun setBiometricEnabled(enabled: Boolean): Result<Unit> {
        setBiometricEnabledCounter.incrementAndGet()
        onSetBiometricEnabled?.let { return it(enabled) }
        biometricEnabledState.value = enabled
        return Result.success(Unit)
    }

    public companion object {
        public const val DEFAULT_ACCESS_TOKEN: String = "access"
        public const val DEFAULT_REFRESH_TOKEN: String = "refresh"

        /** 모든 경로를 닫고, 테스트가 쓰는 `onX` 만 명시적으로 연다. */
        public fun strict(
            loggedIn: Boolean = false,
            accessToken: String? = null,
            refreshToken: String? = null,
        ): FakeAuthRepository =
            FakeAuthRepository(
                loggedIn = loggedIn,
                accessToken = accessToken,
                refreshToken = refreshToken,
                onSocialLogin = { _, _, _ -> unexpectedCall("AuthRepository.socialLogin") },
                onSaveSession = { unexpectedCall("AuthRepository.saveSession") },
                onGetAccessToken = { unexpectedCall("AuthRepository.getAccessToken") },
                onGetRefreshToken = { unexpectedCall("AuthRepository.getRefreshToken") },
                onRotateToken = { unexpectedCall("AuthRepository.rotateToken") },
                onLogout = { unexpectedCall("AuthRepository.logout") },
                onClearSession = { unexpectedCall("AuthRepository.clearSession") },
                onRegisterBiometric = { unexpectedCall("AuthRepository.registerBiometric") },
                onDeclineBiometricEnroll = { unexpectedCall("AuthRepository.declineBiometricEnroll") },
                onSetBiometricEnabled = { unexpectedCall("AuthRepository.setBiometricEnabled") },
            )
    }
}

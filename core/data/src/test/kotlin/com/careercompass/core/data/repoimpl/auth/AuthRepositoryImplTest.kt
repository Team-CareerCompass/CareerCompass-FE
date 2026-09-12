package com.careercompass.core.data.repoimpl.auth

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.careercompass.core.data.support.FakeLocalStoreRegistry
import com.careercompass.core.data.support.InMemoryPreferencesDataStore
import com.careercompass.core.datastore.DeviceDataSource
import com.careercompass.core.datastore.ProfileDataSource
import com.careercompass.core.datastore.StoreScope
import com.careercompass.core.datastore.TokenDataSource
import com.careercompass.core.domain.error.CoreAuthFailure
import com.careercompass.core.domain.error.SessionEndedException
import com.careercompass.core.model.auth.Session
import com.careercompass.core.model.auth.SocialProvider
import com.careercompass.core.network.dto.BiometricRegisterRequestDto
import com.careercompass.core.network.dto.LogoutRequestDto
import com.careercompass.core.network.dto.RefreshDto
import com.careercompass.core.network.dto.RefreshRequestDto
import com.careercompass.core.network.dto.SocialLoginDto
import com.careercompass.core.network.dto.SocialLoginRequestDto
import com.careercompass.core.network.model.ApiErrorDto
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.core.network.service.AuthApiService
import com.careercompass.core.network.service.SocialLoginProvider
import com.careercompass.core.network.service.TokenApiService
import com.careercompass.core.network.token.AccessTokenExpiryTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class AuthRepositoryImplTest {
    private class FakeAuthApi : AuthApiService {
        var loginResponse: () -> BaseResponse<SocialLoginDto> = {
            BaseResponse(ok = true, data = SocialLoginDto("access", "refresh", isNewUser = true, expiresIn = 3600))
        }
        val loginRequests = mutableListOf<Pair<SocialLoginProvider, SocialLoginRequestDto>>()
        var logoutThrows: Throwable? = null
        var onLogout: suspend () -> Unit = {}
        val logoutRequests = mutableListOf<LogoutRequestDto>()
        val biometricRequests = mutableListOf<BiometricRegisterRequestDto>()

        override suspend fun socialLogin(
            provider: SocialLoginProvider,
            body: SocialLoginRequestDto,
        ): BaseResponse<SocialLoginDto> {
            loginRequests += provider to body
            return loginResponse()
        }

        override suspend fun logout(body: LogoutRequestDto): BaseResponse<Unit> {
            logoutRequests += body
            onLogout()
            logoutThrows?.let { throw it }
            return BaseResponse(ok = true)
        }

        override suspend fun registerBiometric(body: BiometricRegisterRequestDto): BaseResponse<Unit> {
            biometricRequests += body
            return BaseResponse(ok = true)
        }
    }

    private class FakeTokenApi : TokenApiService {
        var response: suspend () -> BaseResponse<RefreshDto> = { BaseResponse(ok = true, data = RefreshDto("access-2", "refresh-2", 1800)) }

        override suspend fun refresh(body: RefreshRequestDto): BaseResponse<RefreshDto> = response()
    }

    private val authApi = FakeAuthApi()
    private val tokenApi = FakeTokenApi()
    private val registry = FakeLocalStoreRegistry()
    private val tokenDataSource = TokenDataSource(registry.store("Token", StoreScope.SESSION))
    private val deviceDataSource = DeviceDataSource(InMemoryPreferencesDataStore())
    private val profileDataSource = ProfileDataSource(registry.store("Profile", StoreScope.SESSION), registry)
    private var now = 0L
    private val tracker = AccessTokenExpiryTracker { now }
    private val repository = repositoryWith(tokenDataSource)

    private fun repositoryWith(tokens: TokenDataSource) =
        AuthRepositoryImpl(
            tokenDataSource = tokens,
            deviceDataSource = deviceDataSource,
            authApiService = authApi,
            tokenApiService = tokenApi,
            expiryTracker = tracker,
            localStoreRegistry = registry,
            profileDataSource = profileDataSource,
        )

    @Test
    fun `소셜 로그인은 기기 식별자를 실어 보내고 세션을 돌려준다`() =
        runTest {
            val session = repository.socialLogin(SocialProvider.Kakao, "kakao-token", fcmToken = null).getOrThrow()

            val (provider, request) = authApi.loginRequests.single()
            assertEquals(SocialLoginProvider.Kakao, provider)
            assertEquals(deviceDataSource.getOrCreateDeviceId(), request.deviceId)
            assertNull(request.fcmToken)
            assertEquals(Session("access", "refresh", isNewUser = true, expiresInSeconds = 3600), session)
            assertFalse(repository.isLoggedIn.first())
        }

    @Test
    fun `세션 저장은 토큰과 만료 기록을 남긴다`() =
        runTest {
            repository.saveSession(Session("access", "refresh", isNewUser = false, expiresInSeconds = 30)).getOrThrow()

            assertTrue(repository.isLoggedIn.first())
            assertEquals("access", repository.getAccessToken().getOrThrow())
            assertTrue(tracker.isExpiringSoon())
        }

    /**
     * #335 — 로그아웃을 거치지 않고 다음 계정이 들어오는 길이 있다(지문 화면의 「다른 방법으로 로그인」).
     * 저장이 세션 경계 노릇을 하지 않으면 그 계정이 앞 계정의 프로필·온보딩 진행을 그대로 물려받는다.
     */
    @Test
    fun `세션 저장은 앞 세션의 Profile·OnboardingProgress 를 비운다`() =
        runTest {
            val progressStore = registry.store("OnboardingProgress", StoreScope.SESSION)
            val completed = booleanPreferencesKey("completed")
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            progressStore.edit { it[completed] = true }

            repository.saveSession(Session("access-2", "refresh-2", isNewUser = true, expiresInSeconds = 3600)).getOrThrow()

            assertEquals(listOf(StoreScope.SESSION), registry.clearedScopes)
            assertNull(profileDataSource.profileJson.first())
            assertNull(profileDataSource.userId.first())
            assertNull(progressStore.data.first()[completed])
            // 비우기는 새 토큰보다 앞이다 — 순서가 뒤집히면 로그인하자마자 로그아웃된 상태가 된다.
            assertEquals("access-2", tokenDataSource.getAccessToken())
            assertEquals("refresh-2", tokenDataSource.getRefreshToken())
            assertEquals(false, profileDataSource.onboardingDoneHint.first())
        }

    /**
     * #362 — 토큰 쓰기가 실패했는데 성공을 돌려주면 화면은 피드로 넘어가고 다음 요청은 토큰 없이 나간다.
     * 레지스트리에 등록되지 않은 저장소를 쓰는 것은 세션 정리는 성공시키고 토큰 쓰기만 실패시키기 위함이다.
     */
    @Test
    fun `토큰 저장이 실패하면 세션 저장은 실패로 끝난다`() =
        runTest {
            val failingTokens = InMemoryPreferencesDataStore().apply { failOnWrite = true }
            val repository = repositoryWith(TokenDataSource(failingTokens))

            val result = repository.saveSession(Session("access", "refresh", isNewUser = false, expiresInSeconds = 3600))

            assertTrue(result.exceptionOrNull() is IOException)
            assertFalse(repository.isLoggedIn.first())
        }

    /** #362 — 정리 실패를 성공으로 돌려주면 화면은 로그아웃됐다고 믿고 남은 세션 데이터 위에 다음 계정이 올라탄다. */
    @Test
    fun `로그아웃 중 세션 정리가 실패하면 실패로 끝난다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            registry.failWrites("Profile")

            val result = repository.logout()

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(listOf(StoreScope.SESSION), registry.clearedScopes)
            assertNull(tokenDataSource.getAccessToken())
        }

    /**
     * #367 — 정리가 실패해도 토큰과 만료 기록은 비워져야 한다. 토큰이 남으면 시작 판정이 남은 토큰으로
     * 세션을 다시 세우고, 만료 기록이 남으면 다음 세션이 앞 세션 기준 deadline 으로 선제 재발급을 판단한다.
     */
    @Test
    fun `로그아웃 중 한 저장소가 실패해도 토큰과 만료 기록은 비워진다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            tracker.record(30)
            registry.failWrites("Profile")

            val result = repository.logout()

            assertTrue(result.exceptionOrNull() is IOException)
            assertNull(tokenDataSource.getAccessToken())
            assertFalse(repository.isLoggedIn.first())
            assertFalse(tracker.isExpiringSoon())
        }

    @Test
    fun `세션 저장은 신규 여부를 온보딩 완료 힌트로 남긴다`() =
        runTest {
            repository.saveSession(Session("access", "refresh", isNewUser = true, expiresInSeconds = 3600)).getOrThrow()
            assertEquals(false, profileDataSource.onboardingDoneHint.first())

            repository.saveSession(Session("access-2", "refresh-2", isNewUser = false, expiresInSeconds = 3600)).getOrThrow()
            assertEquals(true, profileDataSource.onboardingDoneHint.first())

            repository.logout().getOrThrow()
            assertNull(profileDataSource.onboardingDoneHint.first())
        }

    @Test
    fun `로그인 거절과 전송 실패를 인증 사유로 옮긴다`() =
        runTest {
            authApi.loginResponse = { throw ApiException("AUTH_INVALID", null, "거절", status = 401) }
            assertTrue(repository.socialLogin(SocialProvider.Google, "t", null).exceptionOrNull() is CoreAuthFailure.SocialLoginRejected)

            authApi.loginResponse = { throw UnknownHostException("dns") }
            assertTrue(repository.socialLogin(SocialProvider.Google, "t", null).exceptionOrNull() is CoreAuthFailure.NetworkUnavailable)

            authApi.loginResponse = { BaseResponse(ok = false, error = ApiErrorDto("AUTH_INVALID", "만료")) }
            assertTrue(repository.socialLogin(SocialProvider.Google, "t", null).exceptionOrNull() is CoreAuthFailure.SocialLoginRejected)
        }

    @Test
    fun `토큰 회전은 새 토큰을 저장하고 돌려준다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")

            val bundle = repository.rotateToken().getOrThrow()

            assertEquals("access-2", bundle.accessToken)
            assertEquals("access-2", tokenDataSource.getAccessToken())
            assertEquals("refresh-2", tokenDataSource.getRefreshToken())
        }

    @Test
    fun `리프레시 토큰이 없으면 회전은 실패한다`() =
        runTest {
            assertTrue(repository.rotateToken().isFailure)
        }

    @Test
    fun `로그아웃은 서버 실패와 무관하게 SESSION 저장소를 비운다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            tracker.record(3600)
            authApi.logoutThrows = UnknownHostException("offline")

            repository.logout().getOrThrow()

            assertEquals("refresh", authApi.logoutRequests.single().refreshToken)
            assertEquals(listOf(StoreScope.SESSION), registry.clearedScopes)
            assertFalse(repository.isLoggedIn.first())
            assertFalse(tracker.isExpiringSoon())
        }

    @Test
    fun `회전 도중 로그아웃이 끝나면 회전 결과를 버리고 세션 종료로 실패한다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            val gate = CompletableDeferred<Unit>()
            tokenApi.response = {
                gate.await()
                BaseResponse(ok = true, data = RefreshDto("access-2", "refresh-2", 1800))
            }
            val rotation = async { repository.rotateToken() }
            runCurrent()

            repository.logout().getOrThrow()
            gate.complete(Unit)
            val result = rotation.await()

            assertTrue(result.exceptionOrNull() is SessionEndedException)
            assertNull(tokenDataSource.getAccessToken())
            assertNull(tokenDataSource.getRefreshToken())
            assertFalse(repository.isLoggedIn.first())
        }

    @Test
    fun `회전 도중 세션 정리가 끝나도 회전 결과를 버린다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            val gate = CompletableDeferred<Unit>()
            tokenApi.response = {
                gate.await()
                BaseResponse(ok = true, data = RefreshDto("access-2", "refresh-2", 1800))
            }
            val rotation = async { repository.rotateToken() }
            runCurrent()

            repository.clearSession().getOrThrow()
            gate.complete(Unit)

            assertTrue(rotation.await().exceptionOrNull() is SessionEndedException)
            assertNull(tokenDataSource.getAccessToken())
        }

    @Test
    fun `로그아웃 서버 응답을 기다리는 동안 저장된 새 세션은 지우지 않는다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            val gate = CompletableDeferred<Unit>()
            authApi.onLogout = { gate.await() }
            val logout = async { repository.logout() }
            runCurrent()

            repository.saveSession(Session("access-2", "refresh-2", isNewUser = false, expiresInSeconds = 3600)).getOrThrow()
            gate.complete(Unit)
            logout.await().getOrThrow()

            assertEquals("refresh", authApi.logoutRequests.single().refreshToken)
            // 비우기는 새 세션 저장이 한 번 한 것뿐이다 — 늦게 끝난 로그아웃은 그 위에 손대지 않는다.
            assertEquals(listOf(StoreScope.SESSION), registry.clearedScopes)
            assertEquals("access-2", tokenDataSource.getAccessToken())
            assertEquals("refresh-2", tokenDataSource.getRefreshToken())
            assertTrue(repository.isLoggedIn.first())
        }

    @Test
    fun `회전 도중 새 세션이 저장되면 회전 결과를 버린다`() =
        runTest {
            tokenDataSource.saveTokens("access", "refresh")
            val gate = CompletableDeferred<Unit>()
            tokenApi.response = {
                gate.await()
                BaseResponse(ok = true, data = RefreshDto("access-old-rotated", "refresh-old-rotated", 1800))
            }
            val rotation = async { repository.rotateToken() }
            runCurrent()

            repository.saveSession(Session("access-2", "refresh-2", isNewUser = false, expiresInSeconds = 3600)).getOrThrow()
            gate.complete(Unit)

            assertTrue(rotation.await().exceptionOrNull() is SessionEndedException)
            assertEquals("access-2", tokenDataSource.getAccessToken())
            assertEquals("refresh-2", tokenDataSource.getRefreshToken())
        }

    @Test
    fun `지문 등록은 기기 식별자를 보내고 현재 사용자에게 귀속한다`() =
        runTest {
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)

            repository.registerBiometric().getOrThrow()

            assertEquals(deviceDataSource.getOrCreateDeviceId(), authApi.biometricRequests.single().deviceId)
            assertEquals(1L, deviceDataSource.biometricUserId.first())
            assertTrue(repository.isBiometricEnabled.first())
        }

    @Test
    fun `프로필을 받기 전에는 지문 등록이 서버 호출 없이 실패한다`() =
        runTest {
            val result = repository.registerBiometric()

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(authApi.biometricRequests.isEmpty())
            assertNull(deviceDataSource.biometricUserId.first())
            assertFalse(repository.isBiometricEnabled.first())
        }

    @Test
    fun `계정 A 가 켠 지문 로그인은 로그아웃 뒤 계정 B 세션에서 꺼져 있다`() =
        runTest {
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            repository.registerBiometric().getOrThrow()
            assertTrue(repository.isBiometricEnabled.first())

            repository.logout().getOrThrow()
            assertFalse(repository.isBiometricEnabled.first())

            profileDataSource.saveProfile(profileJson(userId = 2L), userId = 2L)
            assertFalse(repository.isBiometricEnabled.first())
            assertEquals(1L, deviceDataSource.biometricUserId.first())
        }

    @Test
    fun `같은 계정이 다시 로그인해 프로필을 받으면 지문 로그인이 다시 켜진다`() =
        runTest {
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            repository.registerBiometric().getOrThrow()

            repository.logout().getOrThrow()
            assertFalse(repository.isBiometricEnabled.first())

            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            assertTrue(repository.isBiometricEnabled.first())
        }

    @Test
    fun `지문 로그인을 끄면 등록 사용자까지 지우고 켜면 현재 사용자에게 귀속한다`() =
        runTest {
            assertTrue(repository.setBiometricEnabled(true).exceptionOrNull() is IllegalStateException)

            profileDataSource.saveProfile(profileJson(userId = 3L), userId = 3L)
            repository.setBiometricEnabled(true).getOrThrow()
            assertEquals(3L, deviceDataSource.biometricUserId.first())
            assertTrue(repository.isBiometricEnabled.first())

            repository.setBiometricEnabled(false).getOrThrow()
            assertNull(deviceDataSource.biometricUserId.first())
            assertFalse(repository.isBiometricEnabled.first())
        }

    @Test
    fun `등록 제안 거절은 현재 사용자에게 귀속하고 로그아웃 뒤에도 남는다`() =
        runTest {
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)

            repository.declineBiometricEnroll().getOrThrow()
            assertTrue(repository.isBiometricEnrollDeclined.first())

            repository.logout().getOrThrow()
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            assertTrue(repository.isBiometricEnrollDeclined.first())
        }

    @Test
    fun `계정 A 의 거절은 계정 B 의 제안을 막지 않는다`() =
        runTest {
            profileDataSource.saveProfile(profileJson(userId = 1L), userId = 1L)
            repository.declineBiometricEnroll().getOrThrow()

            profileDataSource.saveProfile(profileJson(userId = 2L), userId = 2L)

            assertFalse(repository.isBiometricEnrollDeclined.first())
        }

    @Test
    fun `프로필을 받기 전에는 거절을 기록하지 않는다`() =
        runTest {
            val result = repository.declineBiometricEnroll()

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertFalse(repository.isBiometricEnrollDeclined.first())
        }

    private fun profileJson(userId: Long) = """{"id":$userId,"jobInterests":[],"tags":[],"onboardingDone":true,"completion":10}"""
}

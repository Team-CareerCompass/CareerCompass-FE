package com.careercompass.core.network.interceptor

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.SessionEndedException
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.model.auth.TokenBundle
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.token.AccessTokenExpiryTracker
import com.careercompass.core.network.token.TokenReissuer
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TokenAuthenticatorTest {
    private object NoopReporter : ErrorReporter {
        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) = Unit
    }

    private fun authenticator(repository: FakeAuthRepository): TokenAuthenticator {
        val lazy = dagger.Lazy<com.careercompass.core.domain.repository.AuthRepository> { repository }
        return TokenAuthenticator(
            authRepository = lazy,
            tokenReissuer = TokenReissuer(lazy, AccessTokenExpiryTracker { 0L }, NoopReporter),
            errorReporter = NoopReporter,
        )
    }

    private fun unauthorized(
        token: String?,
        prior: Response? = null,
    ): Response {
        val request =
            Request
                .Builder()
                .url("https://api.careercompass.invalid/api/v1/users/me")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()
    }

    @Test
    fun `401 이면 토큰을 회전해 새 토큰으로 재시도한다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh", rotatedTokens = TokenBundle("new", "refresh-2", 3600))

        val retry = authenticator(repository).authenticate(null, unauthorized("old"))

        assertEquals("Bearer new", retry?.header("Authorization"))
    }

    @Test
    fun `refresh 거절이면 재시도하지 않는다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(ApiException("AUTH_INVALID", null, "거절", status = 401)) }
            }

        assertNull(authenticator(repository).authenticate(null, unauthorized("old")))
        assertEquals(1, repository.clearSessionCalls)
    }

    @Test
    fun `인증 헤더가 없던 요청은 재시도하지 않는다`() {
        val repository = FakeAuthRepository(accessToken = "old", refreshToken = "refresh")

        assertNull(authenticator(repository).authenticate(null, unauthorized(token = null)))
        assertEquals(0, repository.rotateTokenCalls)
    }

    @Test
    fun `세 번째 401 이면 세션을 정리하고 포기한다`() {
        val repository = FakeAuthRepository(accessToken = "old", refreshToken = "refresh")
        val third = unauthorized("old", prior = unauthorized("old", prior = unauthorized("old")))

        assertNull(authenticator(repository).authenticate(null, third))
        assertEquals(1, repository.clearSessionCalls)
    }

    @Test
    fun `일시 실패는 현재 요청만 IOException 으로 실패시킨다`() {
        val transportFailure = java.net.SocketTimeoutException("timeout")
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(transportFailure) }
            }

        val thrown =
            assertThrows(TokenReissueFailureException::class.java) {
                authenticator(repository).authenticate(null, unauthorized("old"))
            }

        // 타입은 IOException 으로 고정이라 사유는 여기에 실려야 소비처가 문구를 가를 수 있다.
        assertEquals(TokenReissueFailureException.Reason.Transport, thrown.reason)
        assertEquals(transportFailure, thrown.cause)
        assertEquals(0, repository.clearSessionCalls)
    }

    @Test
    fun `재발급이 5xx 로 끝나면 서버 응답을 사유로 실어 보낸다`() {
        val serverFailure = ApiException("LLM_UNAVAILABLE", null, "점검 중", status = 503)
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(serverFailure) }
            }

        val thrown =
            assertThrows(TokenReissueFailureException::class.java) {
                authenticator(repository).authenticate(null, unauthorized("old"))
            }

        assertEquals(TokenReissueFailureException.Reason.Server, thrown.reason)
        assertEquals(serverFailure, thrown.cause)
    }

    @Test
    fun `세션이 교체된 요청은 재시도하지 않고 세션도 지우지 않는다`() {
        // 로그아웃 뒤 다른 계정으로 로그인한 사이 이전 세션의 요청이 401 을 받았다.
        val repository = FakeAuthRepository(accessToken = "other-account", refreshToken = "refresh")

        assertNull(authenticator(repository).authenticate(null, unauthorized("old")))
        assertEquals(0, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
    }

    @Test
    fun `회전 도중 세션이 끝나면 재시도하지 않고 세션도 다시 지우지 않는다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(SessionEndedException("로그아웃")) }
            }

        assertNull(authenticator(repository).authenticate(null, unauthorized("old")))
        assertEquals(0, repository.clearSessionCalls)
    }
}

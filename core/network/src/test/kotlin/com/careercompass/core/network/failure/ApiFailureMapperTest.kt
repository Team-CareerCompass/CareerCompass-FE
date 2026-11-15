package com.careercompass.core.network.failure

import com.careercompass.core.domain.error.CoreAuthFailure
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.network.interceptor.TokenReissueFailureException
import com.careercompass.core.network.model.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiFailureMapperTest {
    private fun api(
        code: String,
        status: Int? = null,
        field: String? = null,
    ) = ApiException(code = code, serverMessage = null, fallbackMessage = "실패", status = status, field = field)

    @Test
    fun `명세 에러 코드를 도메인 사유로 옮긴다`() {
        assertTrue(failure(api("POSTING_NOT_FOUND", 404)) is CoreDataFailure.NotFound)
        assertTrue(failure(api("DUPLICATE_BOARD", 409)) is CoreDataFailure.DuplicateBoard)
        assertTrue(failure(api("LIMIT_EXCEEDED", 422)) is CoreDataFailure.LimitExceeded)
        assertTrue(failure(api("PROFILE_INCOMPLETE", 422)) is CoreDataFailure.ProfileIncomplete)
        assertTrue(failure(api("PARSING_FAILED", 422)) is CoreDataFailure.ParsingFailed)
        assertTrue(failure(api("BOARD_BLOCKED", 422)) is CoreDataFailure.BoardBlocked)
        assertTrue(failure(api("RATE_LIMITED", 429)) is CoreDataFailure.RateLimited)
        assertTrue(failure(api("LLM_UNAVAILABLE", 503)) is CoreDataFailure.ServiceUnavailable)
        assertTrue(failure(api("INTERNAL_ERROR", 500)) is CoreDataFailure.ServerError)
        assertTrue(failure(api("AUTH_INVALID", 401)) is CoreDataFailure.Unauthorized)
        assertTrue(failure(api("PERMISSION_DENIED", 403)) is CoreDataFailure.Forbidden)
    }

    @Test
    fun `검증 실패는 서버가 지목한 필드를 보존한다`() {
        val failure = failure(api("INVALID_INPUT", 400, field = "gpa")) as CoreDataFailure.InvalidInput

        assertEquals("gpa", failure.field)
        assertEquals("INVALID_INPUT", failure.code)
    }

    @Test
    fun `알 수 없는 코드는 HTTP 상태로 갈고 그마저 없으면 원본을 남긴다`() {
        assertTrue(failure(api("HTTP_502", 502)) is CoreDataFailure.ServerError)
        assertTrue(failure(api("HTTP_404", 404)) is CoreDataFailure.NotFound)
        val original = api("SOMETHING_NEW", 418)
        assertSame(original, failure(original))
    }

    @Test
    fun `전송 실패는 NetworkUnavailable 로 옮긴다`() {
        assertTrue(Result.failure<Unit>(UnknownHostException()).mapDataFailure().exceptionOrNull() is CoreDataFailure.NetworkUnavailable)
        assertTrue(Result.failure<Unit>(UnknownHostException()).mapAuthFailure().exceptionOrNull() is CoreAuthFailure.NetworkUnavailable)
    }

    @Test
    fun `타임아웃만 NetworkUnavailable 안에서 갈라 보인다`() {
        val timedOut = Result.failure<Unit>(SocketTimeoutException()).mapDataFailure().exceptionOrNull()
        val callTimedOut = Result.failure<Unit>(InterruptedIOException("timeout")).mapDataFailure().exceptionOrNull()
        val offline = Result.failure<Unit>(UnknownHostException()).mapDataFailure().exceptionOrNull()

        // 사유는 셋 다 같다 — 갈라 보는 것은 오래 걸리는 작업을 기다린 화면뿐이다.
        assertTrue((timedOut as CoreDataFailure.NetworkUnavailable).isTimeout)
        assertTrue((callTimedOut as CoreDataFailure.NetworkUnavailable).isTimeout)
        assertFalse((offline as CoreDataFailure.NetworkUnavailable).isTimeout)
    }

    @Test
    fun `인증 경로에서 토큰 거절은 소셜 로그인 거절이 된다`() {
        assertTrue(Result.failure<Unit>(api("AUTH_INVALID", 401)).mapAuthFailure().exceptionOrNull() is CoreAuthFailure.SocialLoginRejected)
        assertTrue(Result.failure<Unit>(api("INTERNAL_ERROR", 500)).mapAuthFailure().exceptionOrNull() is CoreDataFailure.ServerError)
    }

    @Test
    fun `재발급이 503 으로 끝난 요청은 서버 점검이 된다`() {
        val reissueFailed =
            TokenReissueFailureException(
                reason = TokenReissueFailureException.Reason.Server,
                cause = api("LLM_UNAVAILABLE", 503),
            )

        val failure = Result.failure<Unit>(reissueFailed).mapDataFailure().exceptionOrNull()

        assertTrue(failure is CoreDataFailure.ServiceUnavailable)
        assertEquals("LLM_UNAVAILABLE", (failure as CoreDataFailure).code)
    }

    @Test
    fun `재발급이 서버 응답으로 끝났으면 그 응답의 사유를 쓴다`() {
        assertTrue(reissueFailure(TokenReissueFailureException.Reason.Server, api("HTTP_500", 500)) is CoreDataFailure.ServerError)
        assertTrue(reissueFailure(TokenReissueFailureException.Reason.Server, api("HTTP_503", 503)) is CoreDataFailure.ServiceUnavailable)
        // 429 는 재발급 쪽에서 5xx 로도 거절로도 갈리지 않아 Unexpected 로 실려 온다.
        assertTrue(reissueFailure(TokenReissueFailureException.Reason.Unexpected, api("RATE_LIMITED", 429)) is CoreDataFailure.RateLimited)
    }

    @Test
    fun `재발급이 전송 실패로 끝나면 원본 전송 예외로 타임아웃까지 갈린다`() {
        val timedOut = reissueFailure(TokenReissueFailureException.Reason.Transport, SocketTimeoutException())
        val offline = reissueFailure(TokenReissueFailureException.Reason.Transport, UnknownHostException())

        assertTrue((timedOut as CoreDataFailure.NetworkUnavailable).isTimeout)
        assertFalse((offline as CoreDataFailure.NetworkUnavailable).isTimeout)
    }

    @Test
    fun `사유를 확인하지 못한 재발급 실패는 원본을 남긴다`() {
        // 응답을 읽지 못한 실패다 — 네트워크 문구로 접으면 연결이 멀쩡한 사용자가 연결을 확인하러 간다.
        val parseFailed =
            TokenReissueFailureException(
                reason = TokenReissueFailureException.Reason.Unexpected,
                cause = IllegalStateException("응답을 읽지 못했습니다."),
            )

        assertSame(parseFailed, Result.failure<Unit>(parseFailed).mapDataFailure().exceptionOrNull())
    }

    private fun reissueFailure(
        reason: TokenReissueFailureException.Reason,
        cause: Throwable,
    ): Throwable = Result.failure<Unit>(TokenReissueFailureException(reason = reason, cause = cause)).mapDataFailure().exceptionOrNull()!!

    private fun failure(exception: ApiException): Throwable = Result.failure<Unit>(exception).mapDataFailure().exceptionOrNull()!!
}

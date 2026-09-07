package com.careercompass.core.network.failure

import com.careercompass.core.domain.error.CoreAuthFailure
import com.careercompass.core.domain.error.CoreDataFailure
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

    private fun failure(exception: ApiException): Throwable = Result.failure<Unit>(exception).mapDataFailure().exceptionOrNull()!!
}

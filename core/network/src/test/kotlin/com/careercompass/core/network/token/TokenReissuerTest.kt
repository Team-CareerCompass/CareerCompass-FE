package com.careercompass.core.network.token

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.SessionEndedException
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.model.auth.TokenBundle
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.token.TokenReissuer.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TokenReissuerTest {
    private class RecordingReporter : ErrorReporter {
        val attributes = mutableListOf<Map<String, String>>()

        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) {
            this.attributes += attributes
        }
    }

    private val reporter = RecordingReporter()
    private val tracker = AccessTokenExpiryTracker { 0L }

    private fun reissuer(repository: AuthRepository) =
        TokenReissuer(
            authRepository = dagger.Lazy { repository },
            expiryTracker = tracker,
            errorReporter = reporter,
        )

    private fun rotatingRepository() =
        FakeAuthRepository(
            accessToken = "old",
            refreshToken = "refresh",
            rotatedTokens = TokenBundle("new", "refresh-2", expiresInSeconds = 3600),
        )

    @Test
    fun `회전 성공 시 새 토큰과 만료 기록을 남긴다`() {
        val repository = rotatingRepository()

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertEquals(TokenReissuer.Outcome.Rotated("new"), outcome)
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals("new", repository.accessToken)
        assertTrue(!tracker.isExpiringSoon())
    }

    @Test
    fun `직전 회전 쌍과 맞는 옛 토큰의 재요청은 재발급 없이 새 토큰을 돌려준다`() {
        val repository = rotatingRepository()
        val reissuer = reissuer(repository)
        reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        val outcome = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertEquals(TokenReissuer.Outcome.TokenAlreadyChanged("new"), outcome)
        assertEquals(1, repository.rotateTokenCalls)
    }

    @Test
    fun `회전 밖에서 교체된 토큰은 세션 교체로 돌리고 세션을 건드리지 않는다`() {
        // 로그아웃 뒤 다른 계정으로 로그인한 상태에서 이전 세션의 요청이 401 을 받은 경우.
        val repository = FakeAuthRepository(accessToken = "other-account", refreshToken = "refresh")

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(outcome is TokenReissuer.Outcome.SessionChanged)
        assertEquals(0, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
        assertEquals("other-account", repository.accessToken)
    }

    @Test
    fun `세션이 비어 있으면 재발급하지 않고 세션 교체로 돌린다`() {
        val repository = FakeAuthRepository(accessToken = null, refreshToken = null)

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(outcome is TokenReissuer.Outcome.SessionChanged)
        assertEquals(0, repository.rotateTokenCalls)
        assertTrue(reporter.attributes.isEmpty())
    }

    @Test
    fun `선제 경로는 락 안에서 만료 임박이 아니면 회전하지 않는다`() {
        // 호출자가 새 토큰과 옛 만료 시각을 따로 읽은 경우 — 직전 회전이 만료 시각까지 갱신해 둔 상태.
        tracker.record(expiresInSeconds = 3600)
        val repository = rotatingRepository()

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Preemptive)

        assertEquals(TokenReissuer.Outcome.TokenAlreadyChanged("old"), outcome)
        assertEquals(0, repository.rotateTokenCalls)
    }

    @Test
    fun `401 경로는 만료 임박이 아니어도 회전한다`() {
        tracker.record(expiresInSeconds = 3600)
        val repository = rotatingRepository()

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertEquals(TokenReissuer.Outcome.Rotated("new"), outcome)
        assertEquals(1, repository.rotateTokenCalls)
    }

    @Test
    fun `회전 도중 세션이 끝나면 세션 교체로 돌리고 세션을 다시 지우지 않는다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(SessionEndedException("로그아웃이 끼어듦")) }
            }

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(outcome is TokenReissuer.Outcome.SessionChanged)
        assertEquals(0, repository.clearSessionCalls)
        assertTrue(reporter.attributes.isEmpty())
    }

    @Test
    fun `refresh 거절이면 세션을 정리하고 같은 토큰의 재요청에 같은 결과를 준다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = {
                    Result.failure(ApiException(code = "AUTH_INVALID", serverMessage = null, fallbackMessage = "거절", status = 401))
                }
            }
        var now = 0L
        val reissuer = reissuer(repository).apply { monotonicNanos = { now } }

        val first = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)
        now += 3_000_000_000L
        val second = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Preemptive)

        assertTrue(first is TokenReissuer.Outcome.AuthenticationRejected)
        assertEquals(first, second)
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(1, repository.clearSessionCalls)
        assertTrue(reporter.attributes.isEmpty())
    }

    @Test
    fun `세션 정리가 실패해도 거절은 캐시하고 실패를 계측한다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(ApiException("AUTH_INVALID", null, "거절", status = 401)) }
                onClearSession = { Result.failure(IOException("disk")) }
            }
        val reissuer = reissuer(repository)

        val first = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)
        val second = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(first is TokenReissuer.Outcome.AuthenticationRejected)
        assertEquals(first, second)
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals("session_clear", reporter.attributes.single()["auth_stage"])
    }

    @Test
    fun `전송 실패는 세션을 유지하고 계측한다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(UnknownHostException("dns")) }
            }

        val outcome = reissuer(repository).reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(outcome is TokenReissuer.Outcome.TransportFailure)
        assertEquals(0, repository.clearSessionCalls)
        assertEquals("transport", reporter.attributes.single()["failure_kind"])
    }

    /** 일시 실패는 영구 캐시하지 않는다 — 공유 창이 지난 뒤 새로 들어온 호출은 회전을 다시 시도해 복구할 수 있다(#79). */
    @Test
    fun `일시 실패의 공유 창이 지난 뒤 들어온 호출은 회전을 다시 시도한다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(ApiException("INTERNAL_ERROR", null, "장애", status = 503)) }
            }
        var now = 0L
        val reissuer = reissuer(repository).apply { monotonicNanos = { now } }

        val first = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)
        now += 3_000_000_000L
        val second = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(first is TokenReissuer.Outcome.ServerFailure)
        assertTrue(second is TokenReissuer.Outcome.ServerFailure)
        assertEquals(2, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
        assertEquals(listOf("server", "server"), reporter.attributes.map { it["failure_kind"] })
    }

    @Test
    fun `일시 실패 직후 같은 토큰으로 들어온 호출은 같은 묶음으로 보고 실패를 공유한다`() {
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(IOException("reset")) }
            }
        var now = 0L
        val reissuer = reissuer(repository).apply { monotonicNanos = { now } }

        reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)
        now += 500_000_000L
        val late = reissuer.reissue(expectedAccessToken = "old", trigger = Trigger.Unauthorized)

        assertTrue(late is TokenReissuer.Outcome.TransportFailure)
        assertEquals(1, repository.rotateTokenCalls)
    }

    @Test
    fun `5xx 는 서버 장애로 분류하고 400 은 거절로 확정한다`() {
        val serverFailure =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(ApiException("INTERNAL_ERROR", null, "장애", status = 503)) }
            }
        val rejected =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = { Result.failure(ApiException("HTTP_400", null, "거절", status = 400)) }
            }

        assertTrue(reissuer(serverFailure).reissue("old", Trigger.Unauthorized) is TokenReissuer.Outcome.ServerFailure)
        assertTrue(reissuer(rejected).reissue("old", Trigger.Unauthorized) is TokenReissuer.Outcome.AuthenticationRejected)
    }

    /**
     * N 개 호출이 같은 액세스 토큰으로 동시에 들어온 경우. 첫 호출이 회전하는 동안 나머지는 락을 기다리고,
     * 회전 결과(성공·거절·일시 실패)를 그대로 받는다 — 같은 refresh 로 재발급을 다시 치지 않는다(#79).
     */
    private fun concurrentOutcomes(
        result: () -> Result<TokenBundle>,
        callers: Int = 4,
    ): Pair<FakeAuthRepository, List<TokenReissuer.Outcome>> {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository =
            FakeAuthRepository(accessToken = "old", refreshToken = "refresh").apply {
                onRotateToken = {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    result().onSuccess { bundle ->
                        accessToken = bundle.accessToken
                        refreshToken = bundle.refreshToken
                    }
                }
            }
        val reissuer = reissuer(repository)
        val outcomes = arrayOfNulls<TokenReissuer.Outcome>(callers)
        val leader = Thread { outcomes[0] = reissuer.reissue("old", Trigger.Unauthorized) }.apply { start() }
        check(entered.await(5, TimeUnit.SECONDS))
        val waiters =
            (1 until callers).map { index ->
                Thread { outcomes[index] = reissuer.reissue("old", Trigger.Unauthorized) }.apply { start() }
            }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (waiters.any { it.state != Thread.State.BLOCKED }) {
            check(System.nanoTime() < deadline) { "대기자가 락에 막히지 않았다" }
            Thread.sleep(1)
        }
        release.countDown()
        (waiters + leader).forEach { it.join(5_000) }
        return repository to outcomes.map { requireNotNull(it) }
    }

    @Test
    fun `동시 진입 - 회전 성공은 대기자에게 새 토큰으로 공유된다`() {
        val (repository, outcomes) = concurrentOutcomes({ Result.success(TokenBundle("new", "refresh-2", 3600)) })

        assertEquals(TokenReissuer.Outcome.Rotated("new"), outcomes.first())
        assertTrue(outcomes.drop(1).all { it == TokenReissuer.Outcome.TokenAlreadyChanged("new") })
        assertEquals(1, repository.rotateTokenCalls)
    }

    @Test
    fun `동시 진입 - 401 거절은 한 번만 정리하고 모두 거절을 받는다`() {
        val (repository, outcomes) =
            concurrentOutcomes({ Result.failure(ApiException("AUTH_INVALID", null, "거절", status = 401)) })

        assertTrue(outcomes.all { it is TokenReissuer.Outcome.AuthenticationRejected })
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(1, repository.clearSessionCalls)
    }

    @Test
    fun `동시 진입 - 전송 실패는 대기자와 공유하고 재발급을 반복하지 않는다`() {
        val (repository, outcomes) = concurrentOutcomes({ Result.failure(IOException("reset")) })

        assertTrue(outcomes.all { it is TokenReissuer.Outcome.TransportFailure })
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
        assertEquals(listOf("transport"), reporter.attributes.map { it["failure_kind"] })
    }

    @Test
    fun `동시 진입 - 5xx 는 대기자와 공유하고 재발급을 반복하지 않는다`() {
        val (repository, outcomes) =
            concurrentOutcomes({ Result.failure(ApiException("INTERNAL_ERROR", null, "장애", status = 500)) })

        assertTrue(outcomes.all { it is TokenReissuer.Outcome.ServerFailure })
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
    }

    /**
     * 서버는 회전을 끝냈는데 응답이 유실된 경우. 대기자가 이미 소비된 refresh 로 다시 치면 서버의 재사용 탐지가
     * 계보를 폐기해 강제 로그아웃이 된다 — 공유 덕분에 그 두 번째 호출 자체가 나가지 않는다.
     */
    @Test
    fun `동시 진입 - 응답 유실은 소비된 refresh 를 다시 보내지 않는다`() {
        val (repository, outcomes) = concurrentOutcomes({ Result.failure(java.net.SocketTimeoutException("lost")) })

        assertTrue(outcomes.all { it is TokenReissuer.Outcome.TransportFailure })
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(0, repository.clearSessionCalls)
        assertEquals("old", repository.accessToken)
    }
}

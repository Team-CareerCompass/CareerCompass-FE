package com.careercompass.core.network.di

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.network.interceptor.AuthInterceptor
import com.careercompass.core.network.interceptor.TokenAuthenticator
import com.careercompass.core.network.token.AccessTokenExpiryTracker
import com.careercompass.core.network.token.TokenReissuer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    private object NoopReporter : ErrorReporter {
        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) = Unit
    }

    private val base = NetworkModule.provideBaseOkHttpClient()
    private val logging = NetworkModule.provideLoggingInterceptor()

    @Test
    fun `재발급 클라이언트는 Dispatcher 를 분리하고 ConnectionPool 은 공유한다`() {
        val refresh = NetworkModule.provideRefreshOkHttpClient(base, logging)

        assertNotSame(base.dispatcher, refresh.dispatcher)
        assertSame(base.connectionPool, refresh.connectionPool)
    }

    @Test
    fun `메인 클라이언트는 뿌리의 Dispatcher 를 그대로 쓴다`() {
        assertSame(base.dispatcher, mainClient().dispatcher)
    }

    @Test
    fun `일반 API 클라이언트는 read 10초 call 30초로 끊는다`() {
        val main = mainClient()

        assertEquals(SECONDS_10, main.readTimeoutMillis)
        assertEquals(SECONDS_10, main.writeTimeoutMillis)
        assertEquals(SECONDS_30, main.callTimeoutMillis)
    }

    @Test
    fun `오래 걸리는 작업 클라이언트는 타임아웃만 늘리고 인증 배선은 메인과 공유한다`() {
        val main = mainClient()

        LongRunningOperation.entries.forEach { operation ->
            val derived = main.longRunningClientFor(operation)

            assertEquals(operation.name, operation.ioTimeoutSeconds * MILLIS, derived.readTimeoutMillis.toLong())
            assertEquals(operation.name, operation.ioTimeoutSeconds * MILLIS, derived.writeTimeoutMillis.toLong())
            assertEquals(operation.name, operation.callTimeoutSeconds * MILLIS, derived.callTimeoutMillis.toLong())
            // 연결이 안 잡히는 것은 서버가 오래 일하는 것과 다른 실패라 connect 는 늘리지 않는다.
            assertEquals(operation.name, main.connectTimeoutMillis, derived.connectTimeoutMillis)
            assertEquals(operation.name, main.interceptors, derived.interceptors)
            assertSame(operation.name, main.authenticator, derived.authenticator)
            assertSame(operation.name, main.connectionPool, derived.connectionPool)
            assertSame(operation.name, main.dispatcher, derived.dispatcher)
        }
    }

    /**
     * 값 자체를 고정한다 — 「오래 걸린다」는 판단은 사람이 하지만, 상한이 사라지거나 사용자가 기다릴 수 없는
     * 길이로 늘어나는 것은 기계가 막는다. 무제한(0)은 사용자가 화면에 갇히므로 어느 항목에도 허용하지 않는다.
     */
    @Test
    fun `오래 걸리는 작업의 타임아웃은 상한이 있고 일반 API 보다 길다`() {
        LongRunningOperation.entries.forEach { operation ->
            assertTrue(operation.name, operation.ioTimeoutSeconds > IO_TIMEOUT_SECONDS)
            assertTrue(operation.name, operation.callTimeoutSeconds > CALL_TIMEOUT_SECONDS)
            assertTrue(operation.name, operation.callTimeoutSeconds <= MAX_CALL_TIMEOUT_SECONDS)
        }
        assertEquals(2 * 60L, LongRunningOperation.BoardDetect.callTimeoutSeconds)
    }

    private fun mainClient(): OkHttpClient {
        val lazy = dagger.Lazy<AuthRepository> { FakeAuthRepository() }
        val tracker = AccessTokenExpiryTracker { 0L }
        val reissuer = TokenReissuer(lazy, tracker, NoopReporter)
        return NetworkModule.provideMainOkHttpClient(
            baseClient = base,
            loggingInterceptor = logging,
            authInterceptor = AuthInterceptor(lazy, tracker, reissuer),
            tokenAuthenticator = TokenAuthenticator(lazy, reissuer, NoopReporter),
        )
    }

    private fun OkHttpClient.longRunningClientFor(operation: LongRunningOperation): OkHttpClient =
        when (operation) {
            LongRunningOperation.Upload -> NetworkModule.provideUploadOkHttpClient(this)
            LongRunningOperation.BoardDetect -> NetworkModule.provideBoardDetectOkHttpClient(this)
            LongRunningOperation.ApplicationStream -> NetworkModule.provideApplicationStreamOkHttpClient(this)
        }

    private companion object {
        const val MILLIS = 1_000L
        const val IO_TIMEOUT_SECONDS = 10L
        const val CALL_TIMEOUT_SECONDS = 30L
        const val SECONDS_10 = 10_000
        const val SECONDS_30 = 30_000

        /** 사용자가 진행 표시 앞에서 기다릴 수 있는 최대치. 넘어서면 앱이 멈춘 것과 구분되지 않는다. */
        const val MAX_CALL_TIMEOUT_SECONDS = 5 * 60L
    }
}

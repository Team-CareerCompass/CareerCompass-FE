package com.careercompass.core.network.token

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.model.auth.TokenBundle
import com.careercompass.core.network.calladapter.ApiErrorCallAdapterFactory
import com.careercompass.core.network.di.NetworkModule
import com.careercompass.core.network.dto.RefreshRequestDto
import com.careercompass.core.network.interceptor.AuthInterceptor
import com.careercompass.core.network.interceptor.TokenAuthenticator
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.service.TokenApiService
import com.careercompass.core.network.support.LocalHttpServer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 재발급이 실패했을 때 대기자들이 실제로 몇 번 서버를 치는지 소켓 경계에서 재는 실측이다.
 *
 * 여기 적힌 두 숫자는 성격이 다르다.
 *
 * 거절(401)의 「1회」는 확정된 설계다. [TokenReissuer] 가 거절을 액세스 토큰에 묶어 공유하므로 대기자가 몇이든
 * refresh 도 세션 정리도 한 번이고, 그 계약이 깨지는 것을 막는 회귀 가드다.
 *
 * 일시 실패(500)도 「1회」다(#79). 회전이 진행되는 동안 락을 기다린 대기자는 그 일시 실패를 그대로 받고 같은
 * refresh 를 다시 보내지 않는다 — 응답이 유실된 회전 뒤 소비된 refresh 를 재전송해 서버의 재사용 탐지가 계보를
 * 폐기하는 길을 막는다. 공유는 그 시도에 한하고 영구 캐시가 아니다.
 *
 * 동시 진입은 인증 인터셉터 뒤에 둔 게이트로 만든다. 그러지 않으면 첫 스레드가 나머지가 도착하기 전에 재발급을
 * 끝내 경합이 우연히 비켜 간다. 8건은 비동기(`enqueue`)가 아니라 스레드별 동기 호출로 쏜다 — 비동기 경로는
 * OkHttp Dispatcher 의 호스트 동시 한도(5)에 걸려 8건이 한꺼번에 서버에 닿지 못한다.
 */
class RefreshFailureConcurrencyProbeTest {
    private object NoopReporter : ErrorReporter {
        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) = Unit
    }

    /** 각 테스트가 재발급 엔드포인트의 실패 종류를 정한다. */
    @Volatile
    private var refreshReply: LocalHttpServer.Reply = LocalHttpServer.Reply(500, SERVER_ERROR_BODY)

    private val server =
        LocalHttpServer { recorded ->
            if (recorded.path == REFRESH_PATH) refreshReply else LocalHttpServer.Reply(401, UNAUTHORIZED_BODY)
        }

    private val json = NetworkModule.provideJson()
    private val logging = NetworkModule.provideLoggingInterceptor()
    private val base = NetworkModule.provideBaseOkHttpClient()
    private val refreshClient = NetworkModule.provideRefreshOkHttpClient(base, logging)

    /** 재발급은 프로덕션과 같은 Retrofit 조립을 탄다 — HTTP 상태에서 `ApiException` 으로 가는 변환도 실물이다. */
    private val tokenApi: TokenApiService =
        Retrofit
            .Builder()
            .baseUrl("${server.baseUrl}$API_PREFIX")
            .client(refreshClient)
            .addCallAdapterFactory(ApiErrorCallAdapterFactory(json))
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(TokenApiService::class.java)

    private val tracker = AccessTokenExpiryTracker { 0L }
    private val repository =
        FakeAuthRepository(accessToken = OLD_TOKEN, refreshToken = REFRESH_TOKEN).apply {
            onRotateToken = {
                runCatchingCancellable {
                    val reissued = tokenApi.refresh(RefreshRequestDto(refreshToken = REFRESH_TOKEN)).requireData()
                    accessToken = reissued.accessToken
                    refreshToken = reissued.refreshToken
                    TokenBundle(reissued.accessToken, reissued.refreshToken, reissued.expiresIn)
                }
            }
        }

    /** 인증 인터셉터 뒤에 서서 8건이 같은 액세스 토큰을 달고 서버에 동시에 닿게 모은다. */
    private val gate = CountDownLatch(CONCURRENT_CALLS)

    private val mainClient: OkHttpClient =
        run {
            val lazyRepository = dagger.Lazy<AuthRepository> { repository }
            val reissuer = TokenReissuer(lazyRepository, tracker, NoopReporter)
            NetworkModule
                .provideMainOkHttpClient(
                    baseClient = base,
                    loggingInterceptor = logging,
                    authInterceptor = AuthInterceptor(lazyRepository, tracker, reissuer),
                    tokenAuthenticator = TokenAuthenticator(lazyRepository, reissuer, NoopReporter),
                ).newBuilder()
                .addInterceptor(
                    Interceptor { chain ->
                        gate.countDown()
                        gate.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        chain.proceed(chain.request())
                    },
                ).build()
        }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `회귀 가드 - 거절이 동시에 들어와도 refresh 는 한 번이고 세션 정리도 한 번이다`() {
        refreshReply = LocalHttpServer.Reply(401, REJECTED_BODY)

        val outcomes = fireConcurrently(CONCURRENT_CALLS)

        assertEquals(
            List(CONCURRENT_CALLS) { "Bearer $OLD_TOKEN" },
            server.requests.filter { it.path == API_PATH }.map { it.authorization },
        )
        assertEquals(List(CONCURRENT_CALLS) { 401 }, outcomes.map { it.getOrNull() })
        assertEquals(1, server.requests.count { it.path == REFRESH_PATH })
        assertEquals(1, repository.rotateTokenCalls)
        assertEquals(1, repository.clearSessionCalls)
    }

    @Test
    fun `회귀 가드 - 일시 실패가 동시에 들어와도 refresh 는 한 번이고 세션은 남는다`() {
        refreshReply = LocalHttpServer.Reply(500, SERVER_ERROR_BODY)

        val outcomes = fireConcurrently(CONCURRENT_CALLS)

        assertEquals(1, server.requests.count { it.path == REFRESH_PATH })
        assertEquals(1, repository.rotateTokenCalls)
        // 일시 실패는 현재 요청만 실패시킨다. 세션은 그대로 남아 다음 요청이 다시 시도할 수 있다.
        assertTrue(outcomes.all { it.exceptionOrNull() is IOException })
        assertEquals(0, repository.clearSessionCalls)
        assertEquals(OLD_TOKEN, repository.accessToken)
    }

    private fun fireConcurrently(count: Int): List<Result<Int>> {
        val executor = Executors.newFixedThreadPool(count)
        try {
            val futures =
                List(count) {
                    executor.submit(
                        Callable {
                            runCatching {
                                mainClient
                                    .newCall(Request.Builder().url("${server.baseUrl}$API_PATH").build())
                                    .execute()
                                    .use { response -> response.code }
                            }
                        },
                    )
                }
            return futures.map { future ->
                try {
                    future.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    throw AssertionError("호출 ${count}건이 ${COMPLETION_TIMEOUT_SECONDS}초 안에 끝나지 않았다 - 재발급 교착")
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val OLD_TOKEN = "old"
        const val REFRESH_TOKEN = "refresh"
        const val API_PREFIX = "/api/v1/"
        const val API_PATH = "/api/v1/users/me"
        const val REFRESH_PATH = "/api/v1/auth/refresh"
        const val JSON_MEDIA_TYPE = "application/json"

        const val UNAUTHORIZED_BODY = """{"ok":false,"error":{"code":"AUTH_INVALID","message":"만료된 액세스 토큰"}}"""
        const val REJECTED_BODY = """{"ok":false,"error":{"code":"AUTH_INVALID","message":"만료된 리프레시 토큰"}}"""
        const val SERVER_ERROR_BODY = """{"ok":false,"error":{"code":"INTERNAL_ERROR","message":"일시 장애"}}"""

        /** 이슈가 정한 대기자 수. 동기 호출은 Dispatcher 의 호스트 한도에 걸리지 않아 8건이 그대로 나란히 선다. */
        const val CONCURRENT_CALLS = 8
        const val COMPLETION_TIMEOUT_SECONDS = 20L
    }
}

package com.careercompass.core.network.interceptor

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.network.token.TokenReissuer
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject

/**
 * 401 응답을 받았을 때 토큰을 회전하고 같은 요청을 새 토큰으로 한 번 더 보낸다.
 *
 * 회전은 선제 갱신 경로와 공유하는 [TokenReissuer] 락 경유 — 앞선 다른 경로가 이미 회전했으면 새 토큰만 받아
 * 재시도한다. 인증 거절의 세션 정리는 락 안에서 끝난다. 요청의 세션이 이미 끝났거나 교체됐으면 재시도하지
 * 않고 401 을 그대로 흘린다 — 다른 계정의 토큰으로 재전송하면 안 된다.
 */
public class TokenAuthenticator
    @Inject
    constructor(
        private val authRepository: dagger.Lazy<AuthRepository>,
        private val tokenReissuer: TokenReissuer,
        private val errorReporter: ErrorReporter,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            if (response.responseCount >= MAX_ATTEMPTS) {
                errorReporter.recordAuthContractViolation(AUTH_STAGE_RETRY_LIMIT)
                runBlocking { authRepository.get().clearSession() }
                return null
            }

            val originalRequest = response.request
            val oldAccessToken = originalRequest.bearerToken()
            if (oldAccessToken == null) {
                errorReporter.recordAuthContractViolation(AUTH_STAGE_MISSING_AUTH_HEADER)
                return null
            }

            val outcome =
                tokenReissuer.reissue(
                    expectedAccessToken = oldAccessToken,
                    trigger = TokenReissuer.Trigger.Unauthorized,
                )
            return when (outcome) {
                is TokenReissuer.Outcome.TokenAlreadyChanged -> {
                    originalRequest.withBearer(outcome.accessToken)
                }

                is TokenReissuer.Outcome.SessionChanged -> {
                    null
                }

                is TokenReissuer.Outcome.Rotated -> {
                    if (outcome.accessToken == oldAccessToken) {
                        errorReporter.recordAuthContractViolation(AUTH_STAGE_SAME_TOKEN)
                        runBlocking { authRepository.get().clearSession() }
                        null
                    } else {
                        originalRequest.withBearer(outcome.accessToken)
                    }
                }

                is TokenReissuer.Outcome.AuthenticationRejected -> {
                    null
                }

                is TokenReissuer.Outcome.Failure -> {
                    throw outcome.toRequestFailure()
                }
            }
        }
    }

private fun ErrorReporter.recordAuthContractViolation(authStage: String) {
    recordFailure(
        throwable = IllegalStateException("Token authenticator contract violation"),
        attributes = mapOf(KEY_AUTH_STAGE to authStage),
    )
}

/**
 * 재발급의 기술 원문을 UI 에 노출하지 않고 현재 요청만 실패시키는 예외.
 *
 * OkHttp 의 `Authenticator`·`Interceptor` 는 `IOException` 만 던질 수 있어 이 실패는 전송 실패와 타입이 같아진다.
 * 그래서 [TokenReissuer] 가 이미 가른 사유를 [reason] 으로, 서버가 준 코드·상태를 cause 로 함께 싣는다 — 둘이
 * 없으면 소비처는 재발급 5xx 도 429 도 「인터넷 연결을 확인해 주세요」로 말하게 된다(#361). 사유를 화면용
 * 값으로 옮기는 자리는 `mapDataFailure` 한 곳이다.
 */
internal class TokenReissueFailureException(
    val reason: Reason,
    cause: Throwable,
) : IOException(null, cause) {
    /** 재발급이 어떻게 끝났는지 — [TokenReissuer.Outcome] 의 갈래를 요청 실패까지 들고 오는 값이다. */
    enum class Reason {
        /** 이 세션으로는 더 갈 수 없다 — refresh 가 거절됐거나 세션이 교체됐다. */
        SessionEnded,

        /** 재발급 요청이 서버 응답 없이 전송 계층에서 끝났다. */
        Transport,

        /** 서버가 5xx 로 답했다. */
        Server,

        /** 위 어느 것도 아닌 실패 — 응답 파싱 실패나 분류되지 않은 상태 코드다. */
        Unexpected,
    }
}

/** 세션이 끝났거나 교체됐을 때도 같은 예외로 요청만 끝낸다 — 재로그인 말고는 답이 없는 갈래다. */
internal fun TokenReissuer.Outcome.SessionChanged.toRequestFailure(): TokenReissueFailureException =
    TokenReissueFailureException(reason = TokenReissueFailureException.Reason.SessionEnded, cause = exception)

/** 재발급 실패를 현재 요청의 `IOException` 으로 옮긴다 — [TokenReissuer] 가 가른 사유는 그대로 들고 간다. */
internal fun TokenReissuer.Outcome.Failure.toRequestFailure(): TokenReissueFailureException =
    TokenReissueFailureException(
        reason =
            when (this) {
                is TokenReissuer.Outcome.AuthenticationRejected -> TokenReissueFailureException.Reason.SessionEnded
                is TokenReissuer.Outcome.TransportFailure -> TokenReissueFailureException.Reason.Transport
                is TokenReissuer.Outcome.ServerFailure -> TokenReissueFailureException.Reason.Server
                is TokenReissuer.Outcome.UnexpectedFailure -> TokenReissueFailureException.Reason.Unexpected
            },
        cause = exception,
    )

/** 액세스 토큰만 갈아 끼운 재시도용 요청 사본. */
internal fun Request.withBearer(accessToken: String): Request =
    newBuilder().header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken").build()

internal fun Request.bearerToken(): String? =
    header(AUTHORIZATION_HEADER)?.let { value ->
        if (value.startsWith(BEARER_PREFIX, ignoreCase = true)) value.substring(BEARER_PREFIX.length) else value
    }

/** 이 응답까지의 시도 횟수 — OkHttp 는 재시도마다 직전 시도의 응답을 [Response.priorResponse] 로 매단다. */
private val Response.responseCount: Int
    get() = generateSequence(this) { it.priorResponse }.count()

private const val MAX_ATTEMPTS = 3
private const val AUTHORIZATION_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "
private const val KEY_AUTH_STAGE = "auth_stage"
private const val AUTH_STAGE_RETRY_LIMIT = "retry_limit"
private const val AUTH_STAGE_MISSING_AUTH_HEADER = "missing_auth_header"
private const val AUTH_STAGE_SAME_TOKEN = "same_token"

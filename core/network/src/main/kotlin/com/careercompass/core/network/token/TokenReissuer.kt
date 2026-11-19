package com.careercompass.core.network.token

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.SessionEndedException
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.network.model.ApiException
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 선제 갱신(`AuthInterceptor`)과 401 대응(`TokenAuthenticator`)이 공유하는 토큰 재발급 single-flight.
 *
 * 락 안에서 호출자가 본 토큰과 현재 저장 토큰을 다시 비교해 다른 경로가 이미 갱신했다면 중복 재발급을
 * 건너뛴다. "이미 갱신됨" 은 이 객체가 직전에 수행한 회전 쌍(옛 토큰 → 새 토큰)과 맞을 때만 인정한다 — 회전이
 * 아닌 경로(로그아웃 뒤 새 로그인)로 바뀐 토큰에 이전 세션의 요청을 다시 붙이면 다른 계정으로 전송된다
 * ([Outcome.SessionChanged]).
 *
 * 확정 거절([Outcome.AuthenticationRejected])은 락 안에서 세션 정리까지 끝내고 그 결과를 액세스 토큰에 묶어
 * 공유한다 — 같은 토큰으로 들어온 대기자는 재발급을 치지 않고 같은 결과를 받는다.
 *
 * 일시 실패(전송·5xx·예상 밖)는 **그 회전이 진행되는 동안 락을 기다린 호출과, 실패 직후 [TRANSIENT_SHARE_WINDOW_NANOS]
 * 안에 같은 토큰으로 들어온 호출에만** 공유하고 영구 캐시하지 않는다(#79). 뒤쪽 창이 필요한 이유: 같은 순간 401 을
 * 받은 요청들도 응답이 도착한 순서대로 하나씩 들어오므로, 첫 시도가 빨리 끝나면 같은 묶음의 대기자가 「끝난 뒤」에
 * 도착한다. 대기자가 같은 refresh 로 재발급을 다시 치면, 응답만 유실되고 서버는 회전을 끝낸 경우 소비된 refresh 를
 * 다시 보내게 되고 서버의 재사용 탐지가 계보를 폐기해 강제 로그아웃이 된다. 서버의 30초 유예가 이 경합을 일부
 * 흡수하지만(API_SPEC §1 v0.2) 클라이언트가 두 번째 호출을 만들지 않는 편이 확실하다. 실패가 끝난 뒤 새로 들어온
 * 호출은 회전을 다시 시도하므로 복구는 막히지 않는다.
 */
@Singleton
public class TokenReissuer
    @Inject
    constructor(
        private val authRepository: dagger.Lazy<AuthRepository>,
        private val expiryTracker: AccessTokenExpiryTracker,
        private val errorReporter: ErrorReporter,
    ) {
        /** 재발급을 요청한 경로. 선제 경로는 락 안에서 만료 임박을 다시 확인해 방금 끝난 회전 뒤의 이중 회전을 막는다. */
        public enum class Trigger {
            /** 만료 임박 판단에 따른 선제 갱신 — 만료 시각이 이미 갱신돼 있으면 회전하지 않는다. */
            Preemptive,

            /** 서버가 401 을 돌려줌 — 저장 토큰이 호출자의 것과 같다면 반드시 회전한다. */
            Unauthorized,
        }

        public sealed interface Outcome {
            /**
             * 다른 경로가 먼저 토큰을 갱신해 재발급을 생략함. 선제 경로에서는 직전 회전이 만료 시각까지 갱신해 둔
             * 경우도 포함한다 — 호출자가 새 토큰과 옛 만료 시각을 따로 읽은 것이라 회전할 이유가 없다.
             */
            public data class TokenAlreadyChanged(
                val accessToken: String,
            ) : Outcome

            /** 현재 호출이 새 토큰 발급을 완료함. */
            public data class Rotated(
                val accessToken: String,
            ) : Outcome

            /**
             * 호출자의 토큰이 속한 세션이 더 이상 현재 세션이 아님 — 로그아웃으로 끝났거나 새 로그인으로 교체됐다.
             * 이 요청은 포기해야 하고, 세션 정리는 여기서 하지 않는다(새 세션을 지우게 된다).
             */
            public data class SessionChanged(
                val exception: Throwable,
            ) : Outcome

            public sealed interface Failure : Outcome {
                public val exception: Throwable
            }

            /** refresh 가 거절됨 — 세션은 이미 정리됐다. */
            public data class AuthenticationRejected(
                override val exception: Throwable,
            ) : Failure

            public data class TransportFailure(
                override val exception: IOException,
            ) : Failure

            public data class ServerFailure(
                override val exception: Throwable,
            ) : Failure

            public data class UnexpectedFailure(
                override val exception: Throwable,
            ) : Failure
        }

        /** 끝난 회전 시도의 일련번호. 호출자는 락 밖에서 이 값을 읽어 「내가 기다리는 동안 끝난 시도」를 가른다. */
        @Volatile private var finishedAttempts: Long = 0

        private class TransientFailure(
            val accessToken: String,
            val attempt: Long,
            val finishedAtNanos: Long,
            val failure: Outcome.Failure,
        )

        private var transientFailure: TransientFailure? = null

        /** 단조 시계. 테스트가 공유 창을 넘기려고 바꾼다. */
        internal var monotonicNanos: () -> Long = System::nanoTime

        private var rejectedAccessToken: String? = null
        private var rejection: Outcome.AuthenticationRejected? = null

        /** 직전 회전의 (옛 토큰, 새 토큰). 세션이 끝나거나 거절되면 비운다. */
        private var lastRotation: Pair<String, String>? = null

        /**
         * @param expectedAccessToken 호출자가 교체하려는 기존 액세스 토큰.
         * @param trigger 호출 경로 — 선제 경로만 락 안에서 만료 임박을 다시 확인한다.
         */
        public fun reissue(
            expectedAccessToken: String,
            trigger: Trigger,
        ): Outcome {
            val attemptsSeenOnEntry = finishedAttempts
            synchronized(this) {
                transientFailure?.let { last ->
                    val sameBatch =
                        last.attempt > attemptsSeenOnEntry ||
                            monotonicNanos() - last.finishedAtNanos < TRANSIENT_SHARE_WINDOW_NANOS
                    if (last.accessToken == expectedAccessToken && sameBatch) return last.failure
                }
                // 저장 토큰 비교보다 먼저다 — 세션 정리 뒤엔 저장 토큰이 비어 있어 아래 가드를 그냥 통과한다.
                rejection?.let { if (expectedAccessToken == rejectedAccessToken) return it }

                val currentToken = runBlocking { authRepository.get().getAccessToken() }.getOrNull()
                if (currentToken.isNullOrEmpty()) {
                    lastRotation = null
                    return Outcome.SessionChanged(SessionEndedException("재발급할 세션이 없습니다."))
                }
                if (currentToken != expectedAccessToken) {
                    return if (lastRotation == expectedAccessToken to currentToken) {
                        Outcome.TokenAlreadyChanged(currentToken)
                    } else {
                        Outcome.SessionChanged(SessionEndedException("액세스 토큰이 회전 밖에서 교체됐습니다."))
                    }
                }
                if (trigger == Trigger.Preemptive && !expiryTracker.isExpiringSoon()) {
                    return Outcome.TokenAlreadyChanged(currentToken)
                }

                val rotationResult = runBlocking { authRepository.get().rotateToken() }
                val attempt = ++finishedAttempts
                transientFailure = null
                val rotationException = rotationResult.exceptionOrNull()
                if (rotationException is SessionEndedException) {
                    lastRotation = null
                    return Outcome.SessionChanged(rotationException)
                }
                if (rotationException != null) {
                    // 실패한 토큰의 deadline 을 지워 선제 재시도 반복을 막는다.
                    expiryTracker.clear()
                    val failure = classifyFailure(rotationException)
                    rememberIfRejected(expectedAccessToken, failure)
                    if (failure !is Outcome.AuthenticationRejected) {
                        transientFailure = TransientFailure(expectedAccessToken, attempt, monotonicNanos(), failure)
                    }
                    reportObservableFailure(failure)
                    return failure
                }

                val bundle = rotationResult.getOrThrow()
                bundle.expiresInSeconds?.let(expiryTracker::record) ?: expiryTracker.clear()
                rejectedAccessToken = null
                rejection = null
                lastRotation = expectedAccessToken to bundle.accessToken
                return Outcome.Rotated(bundle.accessToken)
            }
        }

        private fun classifyFailure(exception: Throwable): Outcome.Failure =
            when (exception) {
                is ApiException -> classifyApiFailure(exception)
                is HttpException -> classifyHttpFailure(exception, exception.code())
                is IOException -> Outcome.TransportFailure(exception)
                else -> Outcome.UnexpectedFailure(exception)
            }

        private fun classifyApiFailure(exception: ApiException): Outcome.Failure =
            when {
                exception.code in REJECTION_CODES -> Outcome.AuthenticationRejected(exception)
                exception.status != null -> classifyHttpFailure(exception, exception.status)
                else -> Outcome.UnexpectedFailure(exception)
            }

        /**
         * 재발급 엔드포인트 전용 분류다 — 일반 API 응답에 쓰면 안 된다.
         *
         * 이 엔드포인트에 한해 400·401·403 은 "이 refresh 로는 더 진행할 수 없다" 이므로 본문과 무관하게 거절로
         * 확정한다. 무효 refresh 를 세션에 남기면 이후 요청이 401 → 재발급 실패를 반복해 사용자가 로그인
         * 화면으로도 못 가고 데이터도 못 받는다.
         */
        private fun classifyHttpFailure(
            exception: Throwable,
            status: Int,
        ): Outcome.Failure =
            when (status) {
                400, 401, 403 -> Outcome.AuthenticationRejected(exception)
                in 500..599 -> Outcome.ServerFailure(exception)
                else -> Outcome.UnexpectedFailure(exception)
            }

        private fun rememberIfRejected(
            expectedAccessToken: String,
            failure: Outcome.Failure,
        ) {
            if (failure !is Outcome.AuthenticationRejected) return
            // 정리가 실패해도 거절은 캐시한다 — 이 refresh 로는 더 못 간다. 다만 토큰이 남은 상태는 계측에 남긴다.
            runBlocking { authRepository.get().clearSession() }.onFailure { clearFailure ->
                errorReporter.recordFailure(
                    throwable = clearFailure,
                    attributes = mapOf("auth_stage" to "session_clear", "failure_kind" to "unexpected"),
                )
            }
            rejectedAccessToken = expectedAccessToken
            rejection = failure
            lastRotation = null
        }

        private fun reportObservableFailure(failure: Outcome.Failure) {
            val failureKind =
                when (failure) {
                    is Outcome.AuthenticationRejected -> return
                    is Outcome.TransportFailure -> "transport"
                    is Outcome.ServerFailure -> "server"
                    is Outcome.UnexpectedFailure -> "unexpected"
                }
            errorReporter.recordFailure(
                throwable = failure.exception,
                attributes = mapOf("auth_stage" to "token_reissue", "failure_kind" to failureKind),
            )
        }

        private companion object {
            /** API_SPEC v0.1 §9 — 토큰 없음/만료·무효는 재로그인만이 해법이다. */
            val REJECTION_CODES = setOf("AUTH_REQUIRED", "AUTH_INVALID")

            /** 같은 순간 실패한 요청 묶음으로 보는 시간. 사용자가 다시 시도하는 간격보다 짧아야 복구를 막지 않는다. */
            const val TRANSIENT_SHARE_WINDOW_NANOS: Long = 2_000_000_000L
        }
    }

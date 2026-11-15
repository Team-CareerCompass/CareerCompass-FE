package com.careercompass.core.network.interceptor

import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.network.token.AccessTokenExpiryTracker
import com.careercompass.core.network.token.TokenReissuer
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 액세스 토큰을 요청 헤더에 부착하고, 만료 임박이면 401 을 기다리지 않고 선제 재발급한다.
 *
 * 회전은 [TokenReissuer] 단일 락 경유라 401 경로(`TokenAuthenticator`)와 이중 실행되지 않는다. 선제 재발급이
 * 일시 실패하면 기존 토큰으로 진행한다 — 401 사후 대응이 안전망이고, 일시 오류로 세션을 날리면 안 된다. 반면
 * 확정 거절·세션 교체는 즉시 실패시킨다 — 세션 정리가 끝난 뒤 죽은 토큰을 서버에 한 번 더 보낼 이유가 없다.
 * 재발급 요청 자체는 토큰 미부착 `RefreshClient` 의 별도 Retrofit 을 타므로 재귀가 없다.
 *
 * `intercept` 는 동기 콜백이라 suspend 결과를 [runBlocking] 으로 기다린다 — 공식 문서가 명시한 용도다.
 */
public class AuthInterceptor
    @Inject
    constructor(
        private val authRepository: dagger.Lazy<AuthRepository>,
        private val expiryTracker: AccessTokenExpiryTracker,
        private val tokenReissuer: TokenReissuer,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val storedToken = runBlocking { authRepository.get().getAccessToken() }.getOrNull()

            if (storedToken.isNullOrEmpty()) {
                // 토큰이 없는데 deadline 이 남아 있다면 이전 토큰 기준 stale — 재로그인 후 첫 요청이 죽은 토큰의
                // deadline 로 임박 오판하지 않게 씻는다. 로그인 요청은 토큰 없이 이 분기를 지난다.
                expiryTracker.clear()
                return chain.proceed(originalRequest)
            }

            val accessToken =
                if (expiryTracker.isExpiringSoon()) {
                    val outcome =
                        tokenReissuer.reissue(
                            expectedAccessToken = storedToken,
                            trigger = TokenReissuer.Trigger.Preemptive,
                        )
                    when (outcome) {
                        is TokenReissuer.Outcome.TokenAlreadyChanged -> outcome.accessToken
                        is TokenReissuer.Outcome.Rotated -> outcome.accessToken
                        is TokenReissuer.Outcome.SessionChanged -> throw outcome.toRequestFailure()
                        is TokenReissuer.Outcome.AuthenticationRejected -> throw outcome.toRequestFailure()
                        is TokenReissuer.Outcome.Failure -> storedToken
                    }
                } else {
                    storedToken
                }

            return chain.proceed(originalRequest.withBearer(accessToken))
        }
    }

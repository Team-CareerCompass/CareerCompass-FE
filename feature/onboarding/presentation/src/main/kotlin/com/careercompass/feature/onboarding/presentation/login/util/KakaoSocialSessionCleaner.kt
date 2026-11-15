package com.careercompass.feature.onboarding.presentation.login.util

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.domain.auth.SocialSessionCleaner
import com.kakao.sdk.user.UserApiClient
import javax.inject.Inject

/**
 * 카카오 SDK 가 기기에 둔 OAuth 토큰을 세션이 끝날 때 함께 지운다(#370).
 *
 * `UserApiClient.logout` 은 서버에 토큰 만료를 알리고, 그 응답이 성공이든 실패든 SDK 저장소의 토큰을 비운다.
 * 콜백은 기다리지 않는다. 정리 결과로 달라질 화면이 없고, 이 정리를 부르는 자리 중에는 OkHttp 스레드에서
 * `runBlocking` 으로 도는 세션 종료 경로가 있어 SDK 응답을 기다릴수록 그 스레드만 오래 잡는다.
 *
 * SDK 가 초기화되지 않았으면(`KAKAO_NATIVE_APP_KEY` 미기재 — `KakaoInitializer` 가 초기화를 건너뛴다) 진입점이
 * 동기 예외를 던진다. 로그아웃이 그 예외로 실패하면 안 되므로 여기서 삼킨다.
 */
internal class KakaoSocialSessionCleaner
    @Inject
    constructor() : SocialSessionCleaner {
        override suspend fun clearSocialSession() {
            runCatchingCancellable { UserApiClient.instance.logout {} }
        }
    }

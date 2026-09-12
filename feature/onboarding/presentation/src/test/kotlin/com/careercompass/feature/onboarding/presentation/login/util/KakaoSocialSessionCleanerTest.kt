package com.careercompass.feature.onboarding.presentation.login.util

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KakaoSocialSessionCleanerTest {
    /**
     * 정리는 로그아웃 경로 안에서 불린다. 초기화되지 않은 SDK 진입점이 던지는 동기 예외가 여기서 새면
     * 로그아웃 자체가 실패로 끝난다 — 예외가 나오지 않는 것이 이 테스트가 지키는 전부다.
     */
    @Test
    fun `SDK 가 초기화되지 않았어도 정리는 예외를 던지지 않는다`() =
        runTest {
            KakaoSocialSessionCleaner().clearSocialSession()
        }
}

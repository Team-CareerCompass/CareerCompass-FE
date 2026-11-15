package com.careercompass.feature.onboarding.presentation.login.util

import android.app.Activity
import android.net.Uri
import com.careercompass.core.domain.error.CoreAuthFailure
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.AuthError
import com.kakao.sdk.common.model.AuthErrorCause
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KakaoLoginHelperTest {
    @Test
    fun `SDK 가 초기화되지 않았으면 예외 대신 실패 Result 를 돌려준다`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

            val result = KakaoLoginHelper.requestKakaoAccessToken(activity)

            assertTrue("초기화되지 않은 SDK 호출은 실패로 끝나야 한다", result.isFailure)
        }

    @Test
    fun `redirect 로 돌아온 동의 화면 취소는 사용자 취소로 번역한다`() {
        val denied = redirectError("error=access_denied&error_description=User%20denied%20access")
        // SDK 의 redirect 파서가 취소를 이 모양으로 만든다는 전제다. SDK 가 파싱을 바꾸면 여기서 먼저 드러난다.
        assertEquals(AuthErrorCause.AccessDenied, denied.reason)
        assertEquals("User denied access", denied.response.errorDescription)

        val failure = KakaoLoginHelper.toResult(token = null, error = denied).exceptionOrNull()

        assertTrue("취소가 아니라 $failure 로 남았다", failure is CoreAuthFailure.UserCancelledAuth)
    }

    @Test
    fun `SDK 가 직접 감지한 취소는 사용자 취소로 번역한다`() {
        val cancelled = ClientError(ClientErrorCause.Cancelled, "user cancelled.")

        val failure = KakaoLoginHelper.toResult(token = null, error = cancelled).exceptionOrNull()

        assertTrue("취소가 아니라 $failure 로 남았다", failure is CoreAuthFailure.UserCancelledAuth)
    }

    @Test
    fun `만 14세 미만 정책 거부는 같은 access_denied 여도 원래 오류로 남긴다`() {
        val underAge = redirectError("error=access_denied&error_description=Not%20allowed%20under%20age%2014")
        assertEquals(AuthErrorCause.AccessDenied, underAge.reason)

        assertPreserved(underAge)
    }

    @Test
    fun `설명이 없거나 취소 문구가 아닌 access_denied 는 원래 오류로 남긴다`() {
        assertPreserved(redirectError("error=access_denied"))
        assertPreserved(redirectError("error=access_denied&error_description="))
        assertPreserved(redirectError("error=access_denied&error_description=User%20denied%20access%20later"))
        assertPreserved(redirectError("error=access_denied&error_description=user%20denied%20access"))
    }

    @Test
    fun `access_denied 가 아닌 인증 오류는 원래 오류로 남긴다`() {
        assertPreserved(redirectError("error=server_error&error_description=Internal%20server%20error"))
        // 취소 문구가 붙어 있어도 코드가 access_denied 가 아니면 취소가 아니다.
        assertPreserved(redirectError("error=invalid_request&error_description=User%20denied%20access"))
    }

    @Test
    fun `취소가 아닌 SDK 오류와 일반 오류는 원래 오류로 남긴다`() {
        assertPreserved(ClientError(ClientErrorCause.NotSupported, "KakaoTalk not installed"))
        assertPreserved(IOException("network down"))
    }

    @Test
    fun `토큰이 오면 액세스 토큰을 돌려준다`() {
        val token =
            OAuthToken(
                accessToken = "access-token",
                accessTokenExpiresAt = Date(0),
                refreshToken = "refresh-token",
                refreshTokenExpiresAt = Date(0),
            )

        val result = KakaoLoginHelper.toResult(token = token, error = null)

        assertEquals("access-token", result.getOrNull())
    }

    @Test
    fun `토큰도 오류도 없으면 실패로 끝낸다`() {
        val failure = KakaoLoginHelper.toResult(token = null, error = null).exceptionOrNull()

        assertTrue("실패가 아니라 $failure 로 끝났다", failure is IllegalStateException)
    }

    /** SDK 가 로그인 redirect 의 오류를 읽을 때 쓰는 실제 파서로 오류를 만든다. */
    private fun redirectError(query: String): AuthError = AuthError.create(Uri.parse("kakaotestkey://oauth?$query"))

    private fun assertPreserved(error: Throwable) {
        assertSame(error, KakaoLoginHelper.toResult(token = null, error = error).exceptionOrNull())
    }
}

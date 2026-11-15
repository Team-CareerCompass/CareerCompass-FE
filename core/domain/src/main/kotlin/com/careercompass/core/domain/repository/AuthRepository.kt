package com.careercompass.core.domain.repository

import com.careercompass.core.model.auth.Session
import com.careercompass.core.model.auth.SocialProvider
import com.careercompass.core.model.auth.TokenBundle
import kotlinx.coroutines.flow.Flow

/**
 * 인증 세션 계약 — API_SPEC v0.1 §1.
 *
 * 실패는 [Result] 로 돌려주고, 사유가 확인된 것은 [com.careercompass.core.domain.error.CoreAuthFailure] 로 번역돼 있다.
 */
public interface AuthRepository {
    /** 액세스 토큰 보유 여부. 세션 정리 시 false 로 바뀐다. */
    public val isLoggedIn: Flow<Boolean>

    /**
     * 세션 경계를 세는 값 — 세션이 끝나거나([logout]·[clearSession]) 새로 시작할 때마다([saveSession]) 오른다.
     *
     * 구독하면 지금 세대를 먼저 한 번 내고, 이후 경계마다 낸다. 앞 세션을 보고 만든 화면 상태를 다음 계정이
     * 물려받으면 안 되는 자리에서, 버릴 시점을 이 값의 변화로 안다(#335).
     */
    public val sessionGeneration: Flow<Long>

    /**
     * 이 기기에 지문 로그인을 등록한 사용자가 **현재 세션 사용자와 같은지**.
     *
     * 등록 사용자 id 는 DEVICE 스코프, 현재 사용자 id 는 SESSION 스코프 프로필 캐시에서 읽어 대조한다. 그래서
     * 로그아웃·다른 계정 로그인 뒤에는 false 이고, 같은 계정이 다시 로그인해 프로필을 받으면 true 로 돌아온다.
     * 프로필을 아직 받지 못한 세션에서는 false 다.
     */
    public val isBiometricEnabled: Flow<Boolean>

    /**
     * 현재 세션 사용자가 이 기기에서 지문 등록 제안을 「나중에」로 넘긴 적이 있는지.
     *
     * [isBiometricEnabled] 와 같은 대조 규칙이다 — 거절 기록은 DEVICE 스코프라 로그아웃해도 남고, 누가 넘겼는지를
     * 현재 세션 사용자와 맞춰 본다. 그래서 다른 계정은 이 기기에서도 한 번 제안을 받는다.
     * 프로필을 아직 받지 못한 세션에서는 false 다.
     */
    public val isBiometricEnrollDeclined: Flow<Boolean>

    /** `POST /auth/social/{provider}` — 성공해도 세션은 저장하지 않는다. 저장은 use case 가 [saveSession] 으로 한다. */
    public suspend fun socialLogin(
        provider: SocialProvider,
        providerToken: String,
        fcmToken: String?,
    ): Result<Session>

    public suspend fun saveSession(session: Session): Result<Unit>

    public suspend fun getAccessToken(): Result<String?>

    public suspend fun getRefreshToken(): Result<String?>

    /** `POST /auth/refresh` — 저장된 refresh 토큰으로 회전하고 새 토큰을 저장한다. */
    public suspend fun rotateToken(): Result<TokenBundle>

    /** `POST /auth/logout` best-effort 후 SESSION 스코프 저장소와 소셜 SDK 세션을 비운다. */
    public suspend fun logout(): Result<Unit>

    /** 서버 호출 없이 로컬 세션만 정리한다 — refresh 거절 등 되돌릴 수 없는 실패용. 소셜 SDK 세션도 함께 비운다. */
    public suspend fun clearSession(): Result<Unit>

    /**
     * `POST /auth/biometric/register` 후 지문 로그인을 현재 세션 사용자에게 귀속해 켠다.
     *
     * 현재 사용자 id 를 모르면(프로필을 받기 전) 서버를 부르지 않고 [IllegalStateException] 으로 실패한다.
     */
    public suspend fun registerBiometric(): Result<Unit>

    /**
     * 지문 등록 제안을 「나중에」로 넘긴 사실을 현재 세션 사용자에게 귀속해 기기에 남긴다.
     *
     * 현재 사용자 id 를 모르면(프로필을 받기 전) [IllegalStateException] 으로 실패한다 — 누구의 거절인지 모르는
     * 기록은 다음 계정에게 제안을 건너뛰게 만든다.
     */
    public suspend fun declineBiometricEnroll(): Result<Unit>

    /** 켜면 현재 세션 사용자에게 귀속하고(프로필 전이면 [IllegalStateException]), 끄면 등록 사용자까지 지운다. */
    public suspend fun setBiometricEnabled(enabled: Boolean): Result<Unit>
}

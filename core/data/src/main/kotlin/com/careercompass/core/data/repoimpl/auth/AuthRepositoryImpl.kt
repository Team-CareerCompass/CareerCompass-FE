package com.careercompass.core.data.repoimpl.auth

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.data.mapper.AuthMapper
import com.careercompass.core.datastore.DeviceDataSource
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.ProfileDataSource
import com.careercompass.core.datastore.StoreScope
import com.careercompass.core.datastore.TokenDataSource
import com.careercompass.core.domain.error.SessionEndedException
import com.careercompass.core.domain.repository.AuthRepository
import com.careercompass.core.model.auth.Session
import com.careercompass.core.model.auth.SocialProvider
import com.careercompass.core.model.auth.TokenBundle
import com.careercompass.core.network.dto.BiometricRegisterRequestDto
import com.careercompass.core.network.dto.LogoutRequestDto
import com.careercompass.core.network.dto.RefreshRequestDto
import com.careercompass.core.network.dto.SocialLoginRequestDto
import com.careercompass.core.network.failure.mapAuthFailure
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.AuthApiService
import com.careercompass.core.network.service.TokenApiService
import com.careercompass.core.network.token.AccessTokenExpiryTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

internal class AuthRepositoryImpl
    @Inject
    constructor(
        private val tokenDataSource: TokenDataSource,
        private val deviceDataSource: DeviceDataSource,
        private val authApiService: AuthApiService,
        private val tokenApiService: TokenApiService,
        // 발급 응답의 expiresIn 으로 선제 reissue deadline 을 기록하고, 세션 종료 시 함께 정리한다.
        private val expiryTracker: AccessTokenExpiryTracker,
        // 세션 경계 — SESSION 스코프 로컬 저장소 일괄 정리와 그 경계를 세는 세대를 함께 맡는다.
        private val localStoreRegistry: LocalStoreRegistry,
        // 로그인 응답의 isNewUser 를 온보딩 완료 힌트로 남기고, 현재 세션 사용자 id 로 지문 등록 사용자와 대조한다.
        private val profileDataSource: ProfileDataSource,
    ) : AuthRepository {
        /**
         * 세션 경계를 한 줄로 세우는 자물쇠 — 토큰 쓰기와 세션 정리가 서로를 가로지르지 않게 한다.
         *
         * 세대를 세는 일은 [localStoreRegistry] 몫이다([LocalStoreRegistry.sessionGeneration]). SESSION 스코프를 비우는
         * 것이 곧 세대 교체이고, 이 저장소가 비우는 자리는 [clearLocalSession](정리)과 [saveSession](새 로그인) 둘이다.
         * 세대를 읽는 규칙도 둘이다.
         * - 회전은 시작 시 세대를 기억하고 저장 직전에 다시 비교해, 로그아웃이나 새 로그인이 끼어든 회전 결과를 버린다
         *   (로그아웃이 끝난 뒤 토큰이 되살아나거나 새 세션 위에 옛 세션의 토큰이 덮이는 경합 차단).
         * - 로그아웃은 시작 시 세대를 기억하고 서버 응답 뒤 세대가 같을 때만 정리한다 — 응답을 기다리는 동안
         *   저장된 새 로그인을 지우지 않는다(#81).
         * 서버 호출 동안에는 잡지 않는다 — 로그아웃 요청도 AuthInterceptor 를 지나 회전을 부를 수 있다.
         */
        private val sessionMutex = Mutex()

        private val currentGeneration: Long get() = localStoreRegistry.sessionGeneration.value

        override val isLoggedIn: Flow<Boolean> get() = tokenDataSource.isLoggedIn

        override val sessionGeneration: Flow<Long> get() = localStoreRegistry.sessionGeneration

        /**
         * DEVICE 스코프의 등록 사용자와 SESSION 스코프의 현재 사용자가 같을 때만 켜짐. 로그아웃하면 SESSION 이 비어
         * false, 다른 계정이 들어오면 id 가 달라 false — 기기 Boolean 하나로 두었을 때 다른 계정의 세션이 지문 화면을
         * 거쳐 열리던 결함(#81)을 막는다.
         */
        override val isBiometricEnabled: Flow<Boolean>
            get() =
                combine(deviceDataSource.biometricUserId, profileDataSource.userId) { registered, current ->
                    registered != null && registered == current
                }

        override val isBiometricEnrollDeclined: Flow<Boolean>
            get() =
                combine(deviceDataSource.biometricEnrollDeclinedUserIds, profileDataSource.userId) { declined, current ->
                    current != null && current in declined
                }

        override suspend fun socialLogin(
            provider: SocialProvider,
            providerToken: String,
            fcmToken: String?,
        ): Result<Session> =
            runCatchingCancellable {
                val data =
                    authApiService
                        .socialLogin(
                            provider = AuthMapper.toWireProvider(provider),
                            body =
                                SocialLoginRequestDto(
                                    accessToken = providerToken,
                                    deviceId = deviceDataSource.getOrCreateDeviceId(),
                                    fcmToken = fcmToken,
                                ),
                        ).requireData()
                AuthMapper.toSession(data)
            }.mapAuthFailure()

        /**
         * 새 세션은 새 세대다 — 진행 중이던 회전·로그아웃이 이 세션을 건드리지 못하게 세대를 올린 채 저장한다.
         *
         * 저장은 세션 경계이기도 하다. 새 토큰을 쓰기 전에 SESSION 스코프를 먼저 비운다 — 로그아웃을 거치지 않고
         * 다음 계정이 들어오는 길(지문 화면의 「다른 방법으로 로그인」)이 있어서, 비우지 않으면 다음 계정이 앞
         * 계정의 프로필·온보딩 진행·공고 스냅샷을 그대로 물려받는다(#335).
         */
        override suspend fun saveSession(session: Session): Result<Unit> =
            runCatchingCancellable {
                sessionMutex.withLock {
                    localStoreRegistry.clearScope(StoreScope.SESSION)
                    tokenDataSource.saveTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
                    // 로그인 흐름도 같은 값으로 온보딩/피드를 가른다 — 기존 사용자는 온보딩 완료로 본다.
                    profileDataSource.setOnboardingDoneHint(done = !session.isNewUser)
                    recordIssuedExpiresIn(session.expiresInSeconds)
                }
            }

        override suspend fun getAccessToken(): Result<String?> = runCatchingCancellable { tokenDataSource.getAccessToken() }

        override suspend fun getRefreshToken(): Result<String?> = runCatchingCancellable { tokenDataSource.getRefreshToken() }

        override suspend fun rotateToken(): Result<TokenBundle> =
            runCatchingCancellable {
                val (refreshToken, generation) =
                    sessionMutex.withLock {
                        val stored = tokenDataSource.getRefreshToken() ?: error("리프레시 토큰이 존재하지 않습니다.")
                        stored to currentGeneration
                    }
                val bundle = AuthMapper.toTokenBundle(tokenApiService.refresh(RefreshRequestDto(refreshToken)).requireData())
                sessionMutex.withLock {
                    if (currentGeneration != generation) {
                        throw SessionEndedException("세션이 회전 도중 끝나 재발급 결과를 폐기했습니다.")
                    }
                    tokenDataSource.saveTokens(accessToken = bundle.accessToken, refreshToken = bundle.refreshToken)
                }
                bundle
            }

        /**
         * 서버 로그아웃은 best-effort — 네트워크가 실패해도 사용자는 로그아웃 상태로 가야 한다.
         * 로컬 정리는 서버 호출 뒤여야 한다: 로그아웃 요청도 AuthInterceptor 를 지나므로 그 시점엔 토큰이 살아 있어야 한다.
         * 그 사이 세대가 바뀌었으면(새 로그인·다른 경로의 정리) 이 로그아웃이 정리할 세션은 이미 없다 — 손대지 않고 끝낸다.
         */
        override suspend fun logout(): Result<Unit> =
            runCatchingCancellable {
                val (refreshToken, generation) = sessionMutex.withLock { tokenDataSource.getRefreshToken() to currentGeneration }
                refreshToken?.let { runCatchingCancellable { authApiService.logout(LogoutRequestDto(it)) } }
                clearLocalSession(expectedGeneration = generation)
            }

        override suspend fun clearSession(): Result<Unit> = runCatchingCancellable { clearLocalSession() }

        /** 현재 사용자 id 를 먼저 확인한다 — 서버에 기기를 등록해 놓고 로컬 귀속만 실패하는 반쪽 상태를 만들지 않는다. */
        override suspend fun registerBiometric(): Result<Unit> =
            runCatchingCancellable {
                val userId = requireCurrentUserId()
                authApiService.registerBiometric(BiometricRegisterRequestDto(deviceDataSource.getOrCreateDeviceId())).requireOk()
                deviceDataSource.enableBiometric(userId)
            }.mapDataFailure()

        override suspend fun declineBiometricEnroll(): Result<Unit> =
            runCatchingCancellable { deviceDataSource.declineBiometricEnroll(requireCurrentUserId()) }

        override suspend fun setBiometricEnabled(enabled: Boolean): Result<Unit> =
            runCatchingCancellable {
                if (enabled) deviceDataSource.enableBiometric(requireCurrentUserId()) else deviceDataSource.disableBiometric()
            }

        /** 지문 로그인 기록은 전부 계정에 귀속된다 — 주인을 모르는 채로 남기면 다음 계정이 그 기록을 물려받는다. */
        private suspend fun requireCurrentUserId(): Long = profileDataSource.userId.first() ?: error("프로필을 받기 전에는 지문 로그인 기록을 남길 수 없습니다.")

        /** [expectedGeneration] 을 주면 그 세대일 때만 정리한다 — 그 사이 열린 새 세션은 건드리지 않는다. */
        private suspend fun clearLocalSession(expectedGeneration: Long? = null) {
            sessionMutex.withLock {
                if (expectedGeneration != null && currentGeneration != expectedGeneration) return
                localStoreRegistry.clearScope(StoreScope.SESSION)
                // tracker 는 network 계층 in-memory 상태라 레지스트리 관할 밖. 남기면 재로그인 후 이전 토큰 기준 deadline 으로 오판한다.
                expiryTracker.clear()
            }
        }

        private fun recordIssuedExpiresIn(expiresInSeconds: Long?) {
            expiresInSeconds?.let(expiryTracker::record) ?: expiryTracker.clear()
        }
    }

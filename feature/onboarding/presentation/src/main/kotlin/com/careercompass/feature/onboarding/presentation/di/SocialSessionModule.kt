package com.careercompass.feature.onboarding.presentation.di

import com.careercompass.core.domain.auth.SocialSessionCleaner
import com.careercompass.feature.onboarding.presentation.login.util.KakaoSocialSessionCleaner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 소셜 SDK 세션 정리 바인딩.
 *
 * 계약은 `core:domain` 에 있고 부르는 쪽은 `core:data` 의 세션 정리지만, 구현은 카카오 SDK 를 의존하는 이 모듈
 * 몫이다. core 가 SDK 를 직접 알면 로그인 수단이 늘 때마다 core 가 따라 바뀐다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SocialSessionModule {
    @Binds
    @Singleton
    abstract fun bindSocialSessionCleaner(impl: KakaoSocialSessionCleaner): SocialSessionCleaner
}

package com.careercompass.core.network.di

import com.careercompass.core.network.service.ApplicationApiService
import com.careercompass.core.network.service.ApplicationStreamApiService
import com.careercompass.core.network.service.AuthApiService
import com.careercompass.core.network.service.BoardApiService
import com.careercompass.core.network.service.BoardDetectApiService
import com.careercompass.core.network.service.ExperienceApiService
import com.careercompass.core.network.service.ForYouApiService
import com.careercompass.core.network.service.PastApplicationApiService
import com.careercompass.core.network.service.PostingApiService
import com.careercompass.core.network.service.TokenApiService
import com.careercompass.core.network.service.UserApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object ServiceModule {
    @Provides
    @Singleton
    public fun provideAuthApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): AuthApiService = retrofit.create()

    @Provides
    @Singleton
    public fun provideTokenApiService(
        @Named(NetworkQualifiers.REFRESH_RETROFIT) retrofit: Retrofit,
    ): TokenApiService = retrofit.create()

    @Provides
    @Singleton
    public fun provideUserApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): UserApiService = retrofit.create()

    @Provides
    @Singleton
    public fun provideExperienceApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): ExperienceApiService = retrofit.create()

    /** 업로드가 [LongRunningOperation.Upload] 로 도는 서비스 — 조회·삭제까지 같은 Retrofit 을 쓴다. */
    @Provides
    @Singleton
    public fun providePastApplicationApiService(
        @Named(NetworkQualifiers.UPLOAD_RETROFIT) retrofit: Retrofit,
    ): PastApplicationApiService = retrofit.create()

    /**
     * §7 은 서버가 프로필·경험 카드를 읽어 계산하지만 응답은 요약본이라, 오래 걸리는 작업으로 보지 않는다.
     * 실측 뒤 분 단위로 드러나면 `LongRunningOperation` 에 항목을 더한다.
     */
    @Provides
    @Singleton
    public fun provideForYouApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): ForYouApiService = retrofit.create()

    @Provides
    @Singleton
    public fun providePostingApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): PostingApiService = retrofit.create()

    @Provides
    @Singleton
    public fun provideBoardApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): BoardApiService = retrofit.create()

    @Provides
    @Singleton
    public fun provideApplicationApiService(
        @Named(NetworkQualifiers.MAIN_RETROFIT) retrofit: Retrofit,
    ): ApplicationApiService = retrofit.create()

    /** 스트림만 [LongRunningOperation.ApplicationStream] 로 도는 Retrofit 에서 만든다. */
    @Provides
    @Singleton
    public fun provideApplicationStreamApiService(
        @Named(NetworkQualifiers.APPLICATION_STREAM_RETROFIT) retrofit: Retrofit,
    ): ApplicationStreamApiService = retrofit.create()

    /** 구조 감지만 [LongRunningOperation.BoardDetect] 로 도는 Retrofit 에서 만든다. */
    @Provides
    @Singleton
    public fun provideBoardDetectApiService(
        @Named(NetworkQualifiers.BOARD_DETECT_RETROFIT) retrofit: Retrofit,
    ): BoardDetectApiService = retrofit.create()
}

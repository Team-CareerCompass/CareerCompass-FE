package com.careercompass.core.network.di

import com.careercompass.core.network.BuildConfig
import com.careercompass.core.network.calladapter.ApiErrorCallAdapterFactory
import com.careercompass.core.network.interceptor.AuthInterceptor
import com.careercompass.core.network.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val IO_TIMEOUT_SECONDS = 10L

/** 한 호출의 전체 상한. OkHttp 기본값 0(무제한)에서는 read timeout 이 바이트 사이 간격에만 걸린다. */
private const val CALL_TIMEOUT_SECONDS = 30L

/** Hilt 한정자 이름 — 클라이언트·Retrofit 파생 구분. */
public object NetworkQualifiers {
    public const val BASE_CLIENT: String = "BaseClient"
    public const val MAIN_CLIENT: String = "MainClient"
    public const val REFRESH_CLIENT: String = "RefreshClient"
    public const val UPLOAD_CLIENT: String = "UploadClient"
    public const val BOARD_DETECT_CLIENT: String = "BoardDetectClient"
    public const val APPLICATION_STREAM_CLIENT: String = "ApplicationStreamClient"
    public const val MAIN_RETROFIT: String = "MainRetrofit"
    public const val REFRESH_RETROFIT: String = "RefreshRetrofit"
    public const val UPLOAD_RETROFIT: String = "UploadRetrofit"
    public const val BOARD_DETECT_RETROFIT: String = "BoardDetectRetrofit"
    public const val APPLICATION_STREAM_RETROFIT: String = "ApplicationStreamRetrofit"
}

/** 일반 API 의 타임아웃 — 우리 서버가 자기 DB 를 읽어 돌려주는 시간이 기준이다. */
private fun OkHttpClient.Builder.withApiTimeouts(): OkHttpClient.Builder =
    connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)

/**
 * [operation] 의 타임아웃만 늘린 파생 클라이언트.
 *
 * 인증 인터셉터·재발급 authenticator·로깅·ConnectionPool·Dispatcher 는 뿌리와 그대로 공유한다 — 늘려야 하는
 * 것은 기다리는 시간뿐이라, 전용 클라이언트를 처음부터 다시 조립하면 인증 배선이 한쪽만 낡는다.
 * connect timeout 은 늘리지 않는다: TCP 연결이 안 잡히는 것은 서버가 오래 일하는 것과 다른 실패다.
 */
private fun OkHttpClient.newLongRunningClient(operation: LongRunningOperation): OkHttpClient =
    newBuilder()
        .readTimeout(operation.ioTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(operation.ioTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(operation.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

@Module
@InstallIn(SingletonComponent::class)
public object NetworkModule {
    @Provides
    @Singleton
    public fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            // coerceInputValues 는 두지 않는다 — non-null 프로퍼티에 null 이 와도 기본값이 있으면 조용히 치환해
            // 계약 위반을 삼킨다. 응답 DTO 에 보정형 기본값을 새로 두는 것은 ResponseDtoContractKonsistTest 가 막는다.
            // encodeDefaults 는 기본 false — 요청 DTO 의 null 기본값 필드는 직렬화되지 않아 PATCH 부분 수정이 성립한다.
        }

    @Provides
    @Singleton
    public fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

    /**
     * 모든 클라이언트의 공통 뿌리. 파생은 [OkHttpClient.newBuilder] 로 — 설정값만이 아니라 ConnectionPool ·
     * Dispatcher · 스레드풀을 실제로 공유한다(재발급 클라이언트만 Dispatcher 를 분리한다). 인터셉터는 여기 두지
     * 않는다: 로깅은 각 클라이언트가 마지막에 달아야 최종 요청·응답을 관찰한다.
     */
    @Provides
    @Singleton
    @Named(NetworkQualifiers.BASE_CLIENT)
    public fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient.Builder().withApiTimeouts().build()

    /**
     * 재발급 전용 — 액세스 토큰을 붙이지 않고, [Dispatcher] 도 따로 쓴다.
     *
     * 일반 클라이언트로 재발급하면 만료 토큰이 헤더에 실려 401 이 반복된다. Dispatcher 를 메인과 공유하면 같은
     * 호스트의 비동기 호출이 호스트 동시 한도(기본 5)만큼 재발급을 기다리는 동안 재발급 호출 자체가 그 한도에
     * 걸려 대기열에 갇힌다 — 원 요청은 `runBlocking` 으로 재발급을 기다리므로 영구 교착이다. ConnectionPool 은
     * 계속 공유한다.
     */
    @Provides
    @Singleton
    @Named(NetworkQualifiers.REFRESH_CLIENT)
    public fun provideRefreshOkHttpClient(
        @Named(NetworkQualifiers.BASE_CLIENT) baseClient: OkHttpClient,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        baseClient
            .newBuilder()
            .dispatcher(Dispatcher())
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    @Named(NetworkQualifiers.MAIN_CLIENT)
    public fun provideMainOkHttpClient(
        @Named(NetworkQualifiers.BASE_CLIENT) baseClient: OkHttpClient,
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        baseClient
            .newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

    /** 지원서 업로드 전용 — 인증은 같고 타임아웃만 [LongRunningOperation.Upload] 로 넉넉하다. */
    @Provides
    @Singleton
    @Named(NetworkQualifiers.UPLOAD_CLIENT)
    public fun provideUploadOkHttpClient(
        @Named(NetworkQualifiers.MAIN_CLIENT) mainClient: OkHttpClient,
    ): OkHttpClient = mainClient.newLongRunningClient(LongRunningOperation.Upload)

    /**
     * 게시판 구조 감지 전용 — 업로드와 같은 처방을 [LongRunningOperation.BoardDetect] 값으로 받는다.
     *
     * 감지만 떼어 클라이언트를 따로 두는 이유는 `/boards` 의 나머지 호출(목록·등록·삭제)이 평범한 API 이기
     * 때문이다. 서비스 전체를 이 클라이언트에 태우면 응답이 멈춘 목록 조회까지 2분을 기다린다.
     */
    @Provides
    @Singleton
    @Named(NetworkQualifiers.BOARD_DETECT_CLIENT)
    public fun provideBoardDetectOkHttpClient(
        @Named(NetworkQualifiers.MAIN_CLIENT) mainClient: OkHttpClient,
    ): OkHttpClient = mainClient.newLongRunningClient(LongRunningOperation.BoardDetect)

    /**
     * 지원서 초안 SSE 전용 — [LongRunningOperation.ApplicationStream] 값으로 기다리는 시간만 늘린다.
     *
     * §6 의 나머지 호출(생성·재생성·저장·이력)까지 이 클라이언트에 태우지 않는다. 그것들은 평범한 API 라,
     * 같이 태우면 응답이 멈춘 이력 조회까지 10분을 기다린다.
     */
    @Provides
    @Singleton
    @Named(NetworkQualifiers.APPLICATION_STREAM_CLIENT)
    public fun provideApplicationStreamOkHttpClient(
        @Named(NetworkQualifiers.MAIN_CLIENT) mainClient: OkHttpClient,
    ): OkHttpClient = mainClient.newLongRunningClient(LongRunningOperation.ApplicationStream)

    @Provides
    @Singleton
    @Named(NetworkQualifiers.MAIN_RETROFIT)
    public fun provideMainRetrofit(
        @Named(NetworkQualifiers.MAIN_CLIENT) client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit = retrofit(client, json, apiErrorCallAdapterFactory)

    @Provides
    @Singleton
    @Named(NetworkQualifiers.REFRESH_RETROFIT)
    public fun provideRefreshRetrofit(
        @Named(NetworkQualifiers.REFRESH_CLIENT) client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit = retrofit(client, json, apiErrorCallAdapterFactory)

    @Provides
    @Singleton
    @Named(NetworkQualifiers.UPLOAD_RETROFIT)
    public fun provideUploadRetrofit(
        @Named(NetworkQualifiers.UPLOAD_CLIENT) client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit = retrofit(client, json, apiErrorCallAdapterFactory)

    @Provides
    @Singleton
    @Named(NetworkQualifiers.BOARD_DETECT_RETROFIT)
    public fun provideBoardDetectRetrofit(
        @Named(NetworkQualifiers.BOARD_DETECT_CLIENT) client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit = retrofit(client, json, apiErrorCallAdapterFactory)

    @Provides
    @Singleton
    @Named(NetworkQualifiers.APPLICATION_STREAM_RETROFIT)
    public fun provideApplicationStreamRetrofit(
        @Named(NetworkQualifiers.APPLICATION_STREAM_CLIENT) client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit = retrofit(client, json, apiErrorCallAdapterFactory)

    private fun retrofit(
        client: OkHttpClient,
        json: Json,
        apiErrorCallAdapterFactory: ApiErrorCallAdapterFactory,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            // HTTP 응답을 받은 뒤 400..599 본문을 ApiException 으로 변환한다.
            .addCallAdapterFactory(apiErrorCallAdapterFactory)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}

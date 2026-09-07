package com.careercompass.core.network.service

import com.careercompass.core.network.dto.ApplicationDto
import com.careercompass.core.network.dto.ApplicationItemDto
import com.careercompass.core.network.dto.ApplicationListDto
import com.careercompass.core.network.dto.CreateApplicationRequestDto
import com.careercompass.core.network.dto.RegenerateItemRequestDto
import com.careercompass.core.network.dto.UpdateApplicationResultRequestDto
import com.careercompass.core.network.dto.UpdateItemAnswerRequestDto
import com.careercompass.core.network.model.BaseResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API_SPEC v0.1 §6 — `/applications`.
 *
 * 스트림(`GET /applications/{id}/stream`)만 [ApplicationStreamApiService] 로 뺀다. 타임아웃이 다르기
 * 때문이고, 그 판단은 `LongRunningOperation.ApplicationStream` 에 적혀 있다.
 */
public interface ApplicationApiService {
    /**
     * `POST /applications` — 공고 기반 초안 생성(AI 트리거).
     *
     * 진행 중인 초안이 있으면 서버가 새로 만들지 않고 그 id 를 돌려준다(`docs/spec/canon.md` 「지원서 규칙」).
     * 그래서 이 호출은 「만들기」이자 「이어 쓰기」의 진입점이고, 스트림이 끊긴 뒤의 복구 경로이기도 하다.
     */
    @POST("applications")
    public suspend fun createApplication(
        @Body body: CreateApplicationRequestDto,
    ): BaseResponse<ApplicationDto>

    @POST("applications/{id}/items/{itemId}/regenerate")
    public suspend fun regenerateItem(
        @Path("id") applicationId: Long,
        @Path("itemId") itemId: Long,
        @Body body: RegenerateItemRequestDto,
    ): BaseResponse<ApplicationItemDto>

    @PATCH("applications/{id}/items/{itemId}")
    public suspend fun updateItemAnswer(
        @Path("id") applicationId: Long,
        @Path("itemId") itemId: Long,
        @Body body: UpdateItemAnswerRequestDto,
    ): BaseResponse<ApplicationItemDto>

    @POST("applications/{id}/save")
    public suspend fun save(
        @Path("id") applicationId: Long,
    ): BaseResponse<ApplicationDto>

    @PATCH("applications/{id}/result")
    public suspend fun updateResult(
        @Path("id") applicationId: Long,
        @Body body: UpdateApplicationResultRequestDto,
    ): BaseResponse<ApplicationDto>

    /** `GET /applications` — 지원 이력. `status` 필터와 커서 페이징을 지원한다. */
    @GET("applications")
    public suspend fun getApplications(
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): BaseResponse<ApplicationListDto>

    @DELETE("applications/{id}")
    public suspend fun delete(
        @Path("id") applicationId: Long,
    ): BaseResponse<Unit>
}

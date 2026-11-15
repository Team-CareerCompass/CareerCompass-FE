package com.careercompass.core.network.service

import com.careercompass.core.network.dto.ExportRequestDto
import com.careercompass.core.network.dto.ExportResultDto
import com.careercompass.core.network.dto.ForYouFeedDto
import com.careercompass.core.network.dto.RoadmapCompareDto
import com.careercompass.core.network.model.BaseResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API_SPEC v0.1 §7 — 교수님 피드백으로 들어온 신규 기능 셋.
 *
 * 셋 다 서버가 프로필·경험 카드를 읽어 계산하므로, 입력이 모자라면 422 `PROFILE_INCOMPLETE` 로 거절한다.
 * 그 번역은 `ApiFailureMapper` 가 이미 갖고 있다.
 */
public interface ForYouApiService {
    /** `GET /feed/for-you` — 톱 픽 + 강점 기반 + 취약점 보완, 카테고리별 최대 5건. */
    @GET("feed/for-you")
    public suspend fun getForYouFeed(): BaseResponse<ForYouFeedDto>

    /** `GET /roadmap/compare?cohort=peer` — `peer` · `senior` · `me_only`. */
    @GET("roadmap/compare")
    public suspend fun getRoadmapComparison(
        @Query("cohort") cohort: String,
    ): BaseResponse<RoadmapCompareDto>

    /** `POST /export` — 강점 데이터를 고른 형식으로 내보낸다. */
    @POST("export")
    public suspend fun export(
        @Body body: ExportRequestDto,
    ): BaseResponse<ExportResultDto>
}

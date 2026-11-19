package com.careercompass.core.network.service

import com.careercompass.core.network.dto.PostingDetailDto
import com.careercompass.core.network.dto.PostingListDto
import com.careercompass.core.network.model.BaseResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** API_SPEC v0.1 §5 — `/postings`. */
public interface PostingApiService {
    /**
     * `GET /postings` — 배열 파라미터는 명세 표기(`boardIds[]`·`types[]`)를 그대로 쓴다.
     * null 인 쿼리는 Retrofit 이 생략한다.
     */
    @GET("postings")
    public suspend fun getPostings(
        @Query("boardIds[]") boardIds: List<Long>?,
        @Query("types[]") types: List<String>?,
        @Query("minScore") minScore: Int?,
        @Query("unreadOnly") unreadOnly: Boolean?,
        @Query("q") q: String?,
        @Query("dueFrom") dueFrom: String?,
        @Query("dueTo") dueTo: String?,
        @Query("includeNoDueDate") includeNoDueDate: Boolean?,
        @Query("includeExpired") includeExpired: Boolean?,
        @Query("sort") sort: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): BaseResponse<PostingListDto>

    @GET("postings/{id}")
    public suspend fun getPostingDetail(
        @Path("id") id: Long,
    ): BaseResponse<PostingDetailDto>

    @POST("postings/{id}/bookmark")
    public suspend fun addBookmark(
        @Path("id") id: Long,
    ): BaseResponse<Unit>

    @DELETE("postings/{id}/bookmark")
    public suspend fun removeBookmark(
        @Path("id") id: Long,
    ): BaseResponse<Unit>

    @POST("postings/{id}/read")
    public suspend fun markRead(
        @Path("id") id: Long,
    ): BaseResponse<Unit>
}

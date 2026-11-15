package com.careercompass.feature.foryou.data.support

import com.careercompass.core.network.dto.ExportRequestDto
import com.careercompass.core.network.dto.ExportResultDto
import com.careercompass.core.network.dto.ForYouFeedDto
import com.careercompass.core.network.dto.RoadmapCompareDto
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.core.network.service.ForYouApiService

internal class FakeForYouApiService(
    var onGetForYouFeed: (suspend () -> BaseResponse<ForYouFeedDto>)? = null,
    var onGetRoadmapComparison: (suspend (String) -> BaseResponse<RoadmapCompareDto>)? = null,
    var onExport: (suspend (ExportRequestDto) -> BaseResponse<ExportResultDto>)? = null,
) : ForYouApiService {
    val cohortQueries: MutableList<String> = mutableListOf()
    val exportRequests: MutableList<ExportRequestDto> = mutableListOf()

    override suspend fun getForYouFeed(): BaseResponse<ForYouFeedDto> =
        requireNotNull(onGetForYouFeed) { "onGetForYouFeed 훅이 필요합니다" }.invoke()

    override suspend fun getRoadmapComparison(cohort: String): BaseResponse<RoadmapCompareDto> {
        cohortQueries += cohort
        return requireNotNull(onGetRoadmapComparison) { "onGetRoadmapComparison 훅이 필요합니다" }.invoke(cohort)
    }

    override suspend fun export(body: ExportRequestDto): BaseResponse<ExportResultDto> {
        exportRequests += body
        return requireNotNull(onExport) { "onExport 훅이 필요합니다" }.invoke(body)
    }
}

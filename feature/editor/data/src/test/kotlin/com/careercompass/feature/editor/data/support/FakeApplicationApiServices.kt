package com.careercompass.feature.editor.data.support

import com.careercompass.core.network.dto.ApplicationDto
import com.careercompass.core.network.dto.ApplicationItemDto
import com.careercompass.core.network.dto.ApplicationListDto
import com.careercompass.core.network.dto.CreateApplicationRequestDto
import com.careercompass.core.network.dto.RegenerateItemRequestDto
import com.careercompass.core.network.dto.UpdateApplicationResultRequestDto
import com.careercompass.core.network.dto.UpdateItemAnswerRequestDto
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.core.network.service.ApplicationApiService
import com.careercompass.core.network.service.ApplicationStreamApiService
import okhttp3.ResponseBody

/** §6 서비스 fake — 훅이 없으면 「불릴 리 없는 호출」이라 실패시킨다. */
internal class FakeApplicationApiService(
    var onCreate: (suspend (CreateApplicationRequestDto) -> BaseResponse<ApplicationDto>)? = null,
    var onRegenerate: (suspend (Long, Long, RegenerateItemRequestDto) -> BaseResponse<ApplicationItemDto>)? = null,
    var onUpdateAnswer: (suspend (Long, Long, UpdateItemAnswerRequestDto) -> BaseResponse<ApplicationItemDto>)? = null,
    var onSave: (suspend (Long) -> BaseResponse<ApplicationDto>)? = null,
    var onUpdateResult: (suspend (Long, UpdateApplicationResultRequestDto) -> BaseResponse<ApplicationDto>)? = null,
    var onGetApplications: (suspend (String?, String?, Int?) -> BaseResponse<ApplicationListDto>)? = null,
    var onDelete: (suspend (Long) -> BaseResponse<Unit>)? = null,
) : ApplicationApiService {
    val createRequests: MutableList<CreateApplicationRequestDto> = mutableListOf()
    val regenerateRequests: MutableList<RegenerateItemRequestDto> = mutableListOf()
    val historyQueries: MutableList<Triple<String?, String?, Int?>> = mutableListOf()

    override suspend fun createApplication(body: CreateApplicationRequestDto): BaseResponse<ApplicationDto> {
        createRequests += body
        return requireNotNull(onCreate) { "onCreate 훅이 필요합니다" }.invoke(body)
    }

    override suspend fun regenerateItem(
        applicationId: Long,
        itemId: Long,
        body: RegenerateItemRequestDto,
    ): BaseResponse<ApplicationItemDto> {
        regenerateRequests += body
        return requireNotNull(onRegenerate) { "onRegenerate 훅이 필요합니다" }.invoke(applicationId, itemId, body)
    }

    override suspend fun updateItemAnswer(
        applicationId: Long,
        itemId: Long,
        body: UpdateItemAnswerRequestDto,
    ): BaseResponse<ApplicationItemDto> = requireNotNull(onUpdateAnswer) { "onUpdateAnswer 훅이 필요합니다" }.invoke(applicationId, itemId, body)

    override suspend fun save(applicationId: Long): BaseResponse<ApplicationDto> =
        requireNotNull(onSave) { "onSave 훅이 필요합니다" }.invoke(applicationId)

    override suspend fun updateResult(
        applicationId: Long,
        body: UpdateApplicationResultRequestDto,
    ): BaseResponse<ApplicationDto> = requireNotNull(onUpdateResult) { "onUpdateResult 훅이 필요합니다" }.invoke(applicationId, body)

    override suspend fun getApplications(
        status: String?,
        cursor: String?,
        limit: Int?,
    ): BaseResponse<ApplicationListDto> {
        historyQueries += Triple(status, cursor, limit)
        return requireNotNull(onGetApplications) { "onGetApplications 훅이 필요합니다" }.invoke(status, cursor, limit)
    }

    override suspend fun delete(applicationId: Long): BaseResponse<Unit> =
        requireNotNull(onDelete) { "onDelete 훅이 필요합니다" }.invoke(applicationId)
}

internal class FakeApplicationStreamApiService(
    var onStream: (suspend (Long) -> ResponseBody)? = null,
) : ApplicationStreamApiService {
    override suspend fun stream(applicationId: Long): ResponseBody = requireNotNull(onStream) { "onStream 훅이 필요합니다" }.invoke(applicationId)
}

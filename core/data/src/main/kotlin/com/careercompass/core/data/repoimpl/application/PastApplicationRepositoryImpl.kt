package com.careercompass.core.data.repoimpl.application

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.data.mapper.PastApplicationMapper
import com.careercompass.core.domain.repository.PastApplicationRepository
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.network.dto.UpdateItemCategoryRequestDto
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.PastApplicationApiService
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import javax.inject.Inject

internal class PastApplicationRepositoryImpl
    @Inject
    constructor(
        private val pastApplicationApiService: PastApplicationApiService,
    ) : PastApplicationRepository {
        override suspend fun upload(
            file: UploadFile,
            label: String,
        ): Result<PastApplication> {
            require(label.isNotBlank()) { "label must not be blank" }
            return runCatchingCancellable {
                val part = MultipartBody.Part.createFormData("file", file.fileName, StreamRequestBody(file))
                val dto = pastApplicationApiService.upload(file = part, label = label.toRequestBody(TEXT_PLAIN)).requireData()
                PastApplicationMapper.toApplication(dto)
            }.mapDataFailure()
        }

        override suspend fun getPastApplications(): Result<List<PastApplication>> =
            runCatchingCancellable {
                pastApplicationApiService
                    .getPastApplications()
                    .requireData()
                    .applications
                    .map(PastApplicationMapper::toApplication)
            }.mapDataFailure()

        override suspend fun updateItemCategory(
            applicationId: Long,
            itemId: Long,
            category: PastApplicationCategory,
        ): Result<PastApplicationItem> =
            runCatchingCancellable {
                PastApplicationMapper.toItem(
                    pastApplicationApiService
                        .updateItemCategory(applicationId, itemId, UpdateItemCategoryRequestDto(category.wireValue))
                        .requireData(),
                )
            }.mapDataFailure()

        override suspend fun delete(id: Long): Result<Unit> =
            runCatchingCancellable { pastApplicationApiService.delete(id).requireOk() }.mapDataFailure()

        /** 파일을 메모리에 통째로 올리지 않고 스트림으로 흘린다. 재시도마다 [UploadFile.openStream] 으로 새로 연다. */
        private class StreamRequestBody(
            private val file: UploadFile,
        ) : RequestBody() {
            override fun contentType(): MediaType = file.format.mimeType.toMediaType()

            override fun contentLength(): Long = file.sizeBytes

            override fun writeTo(sink: BufferedSink) {
                file.openStream().use { input -> sink.writeAll(input.source()) }
            }
        }

        private companion object {
            val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
        }
    }

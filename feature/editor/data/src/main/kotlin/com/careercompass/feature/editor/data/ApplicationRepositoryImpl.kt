package com.careercompass.feature.editor.data

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.network.dto.CreateApplicationRequestDto
import com.careercompass.core.network.dto.RegenerateItemRequestDto
import com.careercompass.core.network.dto.UpdateApplicationResultRequestDto
import com.careercompass.core.network.dto.UpdateItemAnswerRequestDto
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.failure.toDataFailure
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.ApplicationApiService
import com.careercompass.core.network.service.ApplicationStreamApiService
import com.careercompass.core.network.sse.asServerSentEvents
import com.careercompass.feature.editor.data.mapper.ApplicationMapper
import com.careercompass.feature.editor.data.mapper.ApplicationStreamEventMapper
import com.careercompass.feature.editor.domain.error.EditorFailure
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationHistoryPage
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.io.IOException
import javax.inject.Inject

internal class ApplicationRepositoryImpl
    @Inject
    constructor(
        private val applicationApiService: ApplicationApiService,
        private val applicationStreamApiService: ApplicationStreamApiService,
        private val streamEventMapper: ApplicationStreamEventMapper,
    ) : ApplicationRepository {
        override suspend fun createDraft(
            postingId: Long,
            tone: ApplicationTone,
        ): Result<ApplicationDraft> =
            runCatchingCancellable {
                ApplicationMapper.toDraft(
                    applicationApiService
                        .createApplication(CreateApplicationRequestDto(postingId = postingId, tone = tone.wireValue))
                        .requireData(),
                )
            }.mapDataFailure()

        /**
         * 스트림의 실패는 **언제 끊겼는가**로 갈린다.
         *
         * 덩어리를 하나도 못 받고 끊긴 것은 연결이 안 된 것이라 다른 조회와 같은 `NetworkUnavailable` 이다.
         * 하나라도 받은 뒤 끊긴 것은 다르다 — 서버는 초안을 만들고 있고 받은 항목은 유효하므로, 화면이 받은
         * 데까지 보여 주고 이어 받는 길을 내야 한다. 그 구분이 [EditorFailure.StreamInterrupted] 다.
         *
         * 흐름을 만드는 자리마다 `delivered` 를 새로 잡는다 — 같은 흐름을 두 번 구독하면 두 번째 구독이
         * 첫 구독의 기록을 물려받아 「이미 받았다」로 잘못 갈린다.
         */
        override fun streamEvents(applicationId: Long): Flow<ApplicationStreamEvent> =
            flow {
                var delivered = false
                val events =
                    flow { emitAll(applicationStreamApiService.stream(applicationId).asServerSentEvents()) }
                        .mapNotNull(streamEventMapper::toEvent)
                        .onEach { delivered = true }
                        .catch { throwable -> throw throwable.toStreamFailure(delivered) }
                emitAll(events)
            }

        override suspend fun regenerateItem(
            applicationId: Long,
            itemId: Long,
            tone: ApplicationTone?,
            emphasizeCardIds: List<Long>,
        ): Result<ApplicationItem> =
            runCatchingCancellable {
                ApplicationMapper.toItem(
                    applicationApiService
                        .regenerateItem(
                            applicationId = applicationId,
                            itemId = itemId,
                            body =
                                RegenerateItemRequestDto(
                                    tone = tone?.wireValue,
                                    emphasizeCardIds = emphasizeCardIds.takeIf { it.isNotEmpty() },
                                ),
                        ).requireData(),
                )
            }.mapDataFailure()

        override suspend fun updateItemAnswer(
            applicationId: Long,
            itemId: Long,
            answer: String,
        ): Result<ApplicationItem> =
            runCatchingCancellable {
                ApplicationMapper.toItem(
                    applicationApiService
                        .updateItemAnswer(applicationId, itemId, UpdateItemAnswerRequestDto(answer))
                        .requireData(),
                )
            }.mapDataFailure()

        override suspend fun save(applicationId: Long): Result<ApplicationDraft> =
            runCatchingCancellable {
                ApplicationMapper.toDraft(applicationApiService.save(applicationId).requireData())
            }.mapDataFailure()

        override suspend fun updateResult(
            applicationId: Long,
            result: ApplicationResult,
        ): Result<ApplicationDraft> =
            runCatchingCancellable {
                ApplicationMapper.toDraft(
                    applicationApiService
                        .updateResult(applicationId, UpdateApplicationResultRequestDto(result.wireValue))
                        .requireData(),
                )
            }.mapDataFailure()

        override suspend fun getApplications(
            status: ApplicationStatus?,
            cursor: String?,
            limit: Int?,
        ): Result<ApplicationHistoryPage> =
            runCatchingCancellable {
                val dto =
                    applicationApiService
                        .getApplications(status = status?.wireValue, cursor = cursor, limit = limit)
                        .requireData()
                ApplicationHistoryPage(
                    applications = dto.applications.map(ApplicationMapper::toDraft),
                    nextCursor = dto.nextCursor,
                )
            }.mapDataFailure()

        override suspend fun delete(applicationId: Long): Result<Unit> =
            runCatchingCancellable { applicationApiService.delete(applicationId).requireOk() }.mapDataFailure()
    }

private fun Throwable.toStreamFailure(delivered: Boolean): Throwable =
    when (this) {
        is ApiException -> toDataFailure()
        is IOException -> if (delivered) EditorFailure.StreamInterrupted(this) else CoreDataFailure.NetworkUnavailable(this)
        else -> this
    }

package com.careercompass.feature.editor.domain.testing

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationHistoryPage
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [ApplicationRepository] fake 정본.
 *
 * 모든 갈래를 `onX` 훅으로 갈아 끼운다 — 성공·실패·지연을 테스트가 그 자리에서 정한다. 훅이 없으면 기록만
 * 남기고 [Result.success] 로 돌려준다. 호출 기록은 `xxxCalls` 로 검증한다.
 */
public class FakeApplicationRepository(
    public var onCreateDraft: (suspend (Long, ApplicationTone) -> Result<ApplicationDraft>)? = null,
    public var onStreamEvents: ((Long) -> Flow<ApplicationStreamEvent>)? = null,
    public var onRegenerateItem: (suspend (Long, Long, ApplicationTone?, List<Long>) -> Result<ApplicationItem>)? = null,
    public var onUpdateItemAnswer: (suspend (Long, Long, String) -> Result<ApplicationItem>)? = null,
    public var onSave: (suspend (Long) -> Result<ApplicationDraft>)? = null,
    public var onUpdateResult: (suspend (Long, ApplicationResult) -> Result<ApplicationDraft>)? = null,
    public var onGetApplications: (suspend (ApplicationStatus?, String?, Int?) -> Result<ApplicationHistoryPage>)? = null,
    public var onDelete: (suspend (Long) -> Result<Unit>)? = null,
) : ApplicationRepository {
    public val createDraftCalls: CopyOnWriteArrayList<Pair<Long, ApplicationTone>> = CopyOnWriteArrayList()
    public val streamCalls: CopyOnWriteArrayList<Long> = CopyOnWriteArrayList()
    public val regenerateCalls: CopyOnWriteArrayList<RegenerateCall> = CopyOnWriteArrayList()
    public val updateAnswerCalls: CopyOnWriteArrayList<UpdateAnswerCall> = CopyOnWriteArrayList()
    public val saveCalls: CopyOnWriteArrayList<Long> = CopyOnWriteArrayList()
    public val updateResultCalls: CopyOnWriteArrayList<Pair<Long, ApplicationResult>> = CopyOnWriteArrayList()
    public val historyCalls: CopyOnWriteArrayList<HistoryCall> = CopyOnWriteArrayList()
    public val deleteCalls: CopyOnWriteArrayList<Long> = CopyOnWriteArrayList()

    public data class RegenerateCall(
        val applicationId: Long,
        val itemId: Long,
        val tone: ApplicationTone?,
        val emphasizeCardIds: List<Long>,
    )

    public data class UpdateAnswerCall(
        val applicationId: Long,
        val itemId: Long,
        val answer: String,
    )

    public data class HistoryCall(
        val status: ApplicationStatus?,
        val cursor: String?,
        val limit: Int?,
    )

    override suspend fun createDraft(
        postingId: Long,
        tone: ApplicationTone,
    ): Result<ApplicationDraft> {
        createDraftCalls += postingId to tone
        return onCreateDraft?.invoke(postingId, tone) ?: error("onCreateDraft 훅이 필요합니다")
    }

    override fun streamEvents(applicationId: Long): Flow<ApplicationStreamEvent> {
        streamCalls += applicationId
        return onStreamEvents?.invoke(applicationId) ?: emptyFlow()
    }

    override suspend fun regenerateItem(
        applicationId: Long,
        itemId: Long,
        tone: ApplicationTone?,
        emphasizeCardIds: List<Long>,
    ): Result<ApplicationItem> {
        regenerateCalls += RegenerateCall(applicationId, itemId, tone, emphasizeCardIds)
        return onRegenerateItem?.invoke(applicationId, itemId, tone, emphasizeCardIds)
            ?: error("onRegenerateItem 훅이 필요합니다")
    }

    override suspend fun updateItemAnswer(
        applicationId: Long,
        itemId: Long,
        answer: String,
    ): Result<ApplicationItem> {
        updateAnswerCalls += UpdateAnswerCall(applicationId, itemId, answer)
        return onUpdateItemAnswer?.invoke(applicationId, itemId, answer) ?: error("onUpdateItemAnswer 훅이 필요합니다")
    }

    override suspend fun save(applicationId: Long): Result<ApplicationDraft> {
        saveCalls += applicationId
        return onSave?.invoke(applicationId) ?: error("onSave 훅이 필요합니다")
    }

    override suspend fun updateResult(
        applicationId: Long,
        result: ApplicationResult,
    ): Result<ApplicationDraft> {
        updateResultCalls += applicationId to result
        return onUpdateResult?.invoke(applicationId, result) ?: error("onUpdateResult 훅이 필요합니다")
    }

    override suspend fun getApplications(
        status: ApplicationStatus?,
        cursor: String?,
        limit: Int?,
    ): Result<ApplicationHistoryPage> {
        historyCalls += HistoryCall(status, cursor, limit)
        return onGetApplications?.invoke(status, cursor, limit) ?: error("onGetApplications 훅이 필요합니다")
    }

    override suspend fun delete(applicationId: Long): Result<Unit> {
        deleteCalls += applicationId
        return onDelete?.invoke(applicationId) ?: Result.success(Unit)
    }
}

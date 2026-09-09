package com.careercompass.feature.editor.domain.repository

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationHistoryPage
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import com.careercompass.feature.editor.domain.model.ApplicationTone
import kotlinx.coroutines.flow.Flow

/**
 * API_SPEC v0.1 §6 `/applications` 의 도메인 계약.
 *
 * 한 자리만 [Result] 가 아니다 — [streamEvents] 는 여러 번 값을 내므로 흐름이다. 실패는 흐름을 끝내는
 * 예외로 나오고, 그 자리에 이미 받은 덩어리들은 소비자가 이미 반영한 뒤다.
 */
public interface ApplicationRepository {
    /**
     * `POST /applications` — 초안 생성. 진행 중인 초안이 있으면 서버가 그것을 돌려준다.
     *
     * 그래서 이 호출은 끊긴 스트림의 복구 경로이기도 하다(`docs/spec/canon.md` 「지원서 규칙」).
     *
     * [items] 가 null 이면 문항을 보내지 않는다 — 사용자가 인식 결과를 그대로 쓴 경우이고, 그때 요청은
     * 지금 계약과 한 글자도 다르지 않다. 손본 경우에만 실어 보낸다(「지원서 문항 확정의 계약」).
     */
    public suspend fun createDraft(
        postingId: Long,
        tone: ApplicationTone,
        items: List<ApplicationItemDraft>?,
    ): Result<ApplicationDraft>

    /** `GET /applications/{id}/stream` (SSE) — 항목이 완성될 때마다 한 덩어리씩. */
    public fun streamEvents(applicationId: Long): Flow<ApplicationStreamEvent>

    /** `POST /applications/{id}/items/{itemId}/regenerate`. */
    public suspend fun regenerateItem(
        applicationId: Long,
        itemId: Long,
        tone: ApplicationTone?,
        emphasizeCardIds: List<Long>,
    ): Result<ApplicationItem>

    /** `PATCH /applications/{id}/items/{itemId}` — 수동 편집이자 30초 임시 저장의 통로다. */
    public suspend fun updateItemAnswer(
        applicationId: Long,
        itemId: Long,
        answer: String,
    ): Result<ApplicationItem>

    /** `POST /applications/{id}/save` — 최종 저장. 이력이 생긴다. */
    public suspend fun save(applicationId: Long): Result<ApplicationDraft>

    /** `PATCH /applications/{id}/result`. */
    public suspend fun updateResult(
        applicationId: Long,
        result: ApplicationResult,
    ): Result<ApplicationDraft>

    /** `GET /applications` — 지원 이력. [status] 가 null 이면 전부. */
    public suspend fun getApplications(
        status: ApplicationStatus?,
        cursor: String?,
        limit: Int?,
    ): Result<ApplicationHistoryPage>

    /** `DELETE /applications/{id}` — 이력과 지원서를 함께 지운다. */
    public suspend fun delete(applicationId: Long): Result<Unit>
}

package com.careercompass.feature.editor.data.mapper

import com.careercompass.core.network.dto.ApplicationDto
import com.careercompass.core.network.dto.ApplicationItemDto
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus

/** API_SPEC v0.1 §6 응답 → 도메인. */
internal object ApplicationMapper {
    /**
     * 항목은 서버가 준 `order` 로 정렬한다.
     *
     * 배열 순서에 기대지 않는 이유는, 스트림이 항목을 완성 순서대로 흘려보내기 때문이다 — 다시 읽은 목록이
     * 배열 순서를 그때 순서로 주면 문항이 화면에서 뒤바뀐다. `order` 는 그래서 있는 필드다.
     *
     * 모르는 `status` 는 [ApplicationStatus.Generating] 으로 읽는다. 넷 중 어느 것도 아닌 값은 우리가 모르는
     * 진행 상태이고, 「쓰는 중」으로 두면 화면이 스트림을 붙여 사실을 다시 확인한다 — `ready` 로 읽어
     * 미완성 초안을 완성으로 보여 주는 것보다 낫다.
     */
    fun toDraft(dto: ApplicationDto): ApplicationDraft =
        ApplicationDraft(
            id = dto.id,
            status = ApplicationStatus.fromWireValue(dto.status) ?: ApplicationStatus.Generating,
            items = dto.items.map(::toItem).sortedBy { it.order },
            postingId = dto.postingId,
            result = dto.result?.let(ApplicationResult::fromWireValue),
        )

    fun toItem(dto: ApplicationItemDto): ApplicationItem =
        ApplicationItem(
            id = dto.id,
            order = dto.order,
            question = dto.question,
            maxChars = dto.maxChars,
            status = ApplicationItemStatus.fromWireValue(dto.status),
            answer = dto.answer,
        )
}

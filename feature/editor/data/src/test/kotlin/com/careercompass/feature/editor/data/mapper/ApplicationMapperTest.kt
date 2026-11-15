package com.careercompass.feature.editor.data.mapper

import com.careercompass.core.network.dto.ApplicationDto
import com.careercompass.core.network.dto.ApplicationItemDto
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationResult
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationMapperTest {
    @Test
    fun `계약의 네 상태와 항목 상태를 도메인으로 옮긴다`() {
        val draft = ApplicationMapper.toDraft(dto(status = "partial_failed", result = "pass"))

        assertEquals(ApplicationStatus.PartialFailed, draft.status)
        assertEquals(ApplicationResult.Pass, draft.result)
        assertEquals(listOf(ApplicationItemStatus.Done, ApplicationItemStatus.Loading), draft.items.map { it.status })
        assertEquals(101L, draft.postingId)
    }

    /**
     * 스트림이 항목을 완성 순서대로 흘려보내므로 배열 순서는 문항 순서가 아니다. `order` 로 세우지 않으면
     * 다시 읽은 초안에서 문항이 뒤바뀐다.
     */
    @Test
    fun `항목은 배열 순서가 아니라 order 로 세운다`() {
        val draft =
            ApplicationMapper.toDraft(
                dto(
                    items =
                        listOf(
                            itemDto(id = 9L, order = 3),
                            itemDto(id = 7L, order = 1),
                            itemDto(id = 8L, order = 2),
                        ),
                ),
            )

        assertEquals(listOf(7L, 8L, 9L), draft.items.map { it.id })
    }

    /** 모르는 값을 `ready` 로 읽으면 미완성 초안이 완성으로 보인다. 「쓰는 중」이면 화면이 스트림으로 확인한다. */
    @Test
    fun `모르는 지원서 상태는 generating 으로 읽는다`() {
        assertEquals(ApplicationStatus.Generating, ApplicationMapper.toDraft(dto(status = "queued")).status)
    }

    @Test
    fun `모르는 항목 상태는 실패로 읽는다`() {
        val draft = ApplicationMapper.toDraft(dto(items = listOf(itemDto(status = "cancelled"))))

        assertEquals(ApplicationItemStatus.Failed, draft.items.single().status)
    }

    /** 결과를 안 준 것과 「없음」을 정한 것은 다르다 — 없는 값을 [ApplicationResult.None] 으로 만들지 않는다. */
    @Test
    fun `결과가 없으면 null 이고 모르는 값도 null 이다`() {
        assertNull(ApplicationMapper.toDraft(dto(result = null)).result)
        assertNull(ApplicationMapper.toDraft(dto(result = "withdrawn")).result)
    }

    private fun itemDto(
        id: Long = 1L,
        order: Int = 1,
        status: String = "done",
        answer: String? = "답",
    ) = ApplicationItemDto(id = id, order = order, question = "문항", maxChars = 500, status = status, answer = answer)

    private fun dto(
        status: String = "generating",
        result: String? = null,
        items: List<ApplicationItemDto> =
            listOf(
                itemDto(id = 1L, order = 1, status = "done"),
                itemDto(id = 2L, order = 2, status = "loading", answer = null),
            ),
    ) = ApplicationDto(id = 42L, status = status, items = items, postingId = 101L, result = result)
}

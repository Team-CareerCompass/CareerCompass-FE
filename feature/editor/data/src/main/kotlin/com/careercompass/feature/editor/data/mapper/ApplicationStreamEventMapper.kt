package com.careercompass.feature.editor.data.mapper

import com.careercompass.core.network.dto.ApplicationStreamItemDoneDto
import com.careercompass.core.network.dto.ApplicationStreamItemFailedDto
import com.careercompass.core.network.dto.ApplicationStreamStatusDto
import com.careercompass.core.network.sse.ServerSentEvent
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import com.careercompass.feature.editor.domain.model.ApplicationStreamEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * SSE 덩어리 → 도메인 사건. 읽을 수 없는 덩어리는 **null** 이고, 흐름에서 빠진다.
 *
 * 끊지 않고 버리는 이유는 스트림의 성격 때문이다. 덩어리 하나가 이상하다고 예외를 던지면 **이미 받은
 * 항목들까지 버려진다** — 사용자에게는 다 쓴 자소서가 통째로 사라지는 일이다. 버려서 잃는 것은 그 항목
 * 하나의 답이고, 그 항목은 스트림이 닫힐 때 `settled()` 가 실패로 굳혀 재생성 버튼을 띄운다. 즉 손실이
 * 화면에 드러나고 사용자가 고칠 수 있다.
 *
 * 모르는 `event` 이름도 같은 이유로 버린다 — 서버가 이벤트를 늘려도 옛 앱의 스트림이 죽지 않아야 한다.
 */
internal class ApplicationStreamEventMapper
    @Inject
    constructor(
        private val json: Json,
    ) {
        fun toEvent(event: ServerSentEvent): ApplicationStreamEvent? =
            try {
                when (event.event) {
                    EVENT_ITEM_DONE -> {
                        val dto = json.decodeFromString(ApplicationStreamItemDoneDto.serializer(), event.data)
                        ApplicationStreamEvent.ItemDone(itemId = dto.itemId, answer = dto.answer)
                    }

                    EVENT_ITEM_FAILED -> {
                        val dto = json.decodeFromString(ApplicationStreamItemFailedDto.serializer(), event.data)
                        ApplicationStreamEvent.ItemFailed(itemId = dto.itemId, code = dto.code)
                    }

                    EVENT_STATUS -> {
                        val dto = json.decodeFromString(ApplicationStreamStatusDto.serializer(), event.data)
                        ApplicationStatus.fromWireValue(dto.status)?.let(ApplicationStreamEvent::StatusChanged)
                    }

                    else -> {
                        null
                    }
                }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        private companion object {
            const val EVENT_ITEM_DONE = "item_done"
            const val EVENT_ITEM_FAILED = "item_failed"
            const val EVENT_STATUS = "status"
        }
    }

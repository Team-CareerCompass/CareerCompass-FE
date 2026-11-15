package com.careercompass.feature.editor.domain

import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus

internal fun item(
    id: Long,
    order: Int = id.toInt(),
    status: ApplicationItemStatus = ApplicationItemStatus.Loading,
    answer: String? = null,
    maxChars: Int = 500,
): ApplicationItem =
    ApplicationItem(
        id = id,
        order = order,
        question = "문항 $id",
        maxChars = maxChars,
        status = status,
        answer = answer,
    )

internal fun draft(
    id: Long = 42L,
    status: ApplicationStatus = ApplicationStatus.Generating,
    items: List<ApplicationItem> = listOf(item(1L), item(2L)),
): ApplicationDraft = ApplicationDraft(id = id, status = status, items = items, postingId = 101L)

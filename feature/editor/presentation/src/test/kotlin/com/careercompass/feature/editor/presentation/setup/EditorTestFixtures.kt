package com.careercompass.feature.editor.presentation.setup

import com.careercompass.core.model.posting.PostingBoardRef
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.model.posting.PostingFormQuestion
import com.careercompass.core.model.posting.PostingParsed
import com.careercompass.core.model.posting.PostingQualifications
import com.careercompass.core.model.posting.PostingType
import com.careercompass.feature.editor.domain.model.ApplicationDraft
import com.careercompass.feature.editor.domain.model.ApplicationItem
import com.careercompass.feature.editor.domain.model.ApplicationItemDraft
import com.careercompass.feature.editor.domain.model.ApplicationItemStatus
import com.careercompass.feature.editor.domain.model.ApplicationStatus
import java.time.Instant

internal const val TEST_POSTING_ID = 101L

internal fun postingDetail(
    questions: List<PostingFormQuestion> =
        listOf(
            PostingFormQuestion(order = 1, question = "지원 동기를 작성해 주세요", maxChars = 500),
            PostingFormQuestion(order = 2, question = "본인의 강점과 약점을 서술해 주세요", maxChars = null),
        ),
    parsed: Boolean = true,
): PostingDetail =
    PostingDetail(
        id = TEST_POSTING_ID,
        title = "카카오 SW 인턴십",
        type = PostingType.Recruit,
        board = PostingBoardRef(id = 1L, name = "카카오 채용"),
        url = "https://example.com/posting/101",
        rawContent = "원문",
        dueDate = null,
        collectedAt = Instant.parse("2026-09-01T00:00:00Z"),
        isRead = false,
        isBookmarked = false,
        parsed =
            if (parsed) {
                PostingParsed(
                    keywords = listOf("Kotlin"),
                    qualifications = PostingQualifications(year = null, gpa = null),
                    preferences = emptyList(),
                    formQuestions = questions,
                )
            } else {
                null
            },
        suitability = null,
        similar = emptyList(),
    )

internal fun itemDraft(
    order: Int,
    question: String = "문항 $order",
    maxChars: Int? = 500,
) = ApplicationItemDraft(order = order, question = question, maxChars = maxChars)

internal fun createdDraft(id: Long = 42L) =
    ApplicationDraft(
        id = id,
        status = ApplicationStatus.Generating,
        items =
            listOf(
                ApplicationItem(
                    id = 1L,
                    order = 1,
                    question = "지원 동기를 작성해 주세요",
                    maxChars = 500,
                    status = ApplicationItemStatus.Loading,
                    answer = null,
                ),
            ),
        postingId = TEST_POSTING_ID,
    )

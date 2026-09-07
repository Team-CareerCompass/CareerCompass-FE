package com.careercompass.feature.editor.domain.model

import com.careercompass.core.model.posting.PostingFormQuestion
import com.careercompass.core.model.user.ProfileFieldViolation

/**
 * 초안을 만들기 **전에** 확정하는 문항 하나 — 기능 스펙 F4-1.
 *
 * [ApplicationItem] 과 다르다. 저쪽은 서버가 답까지 실어 돌려주는 값이고, 이쪽은 「무엇을 쓸 것인가」만
 * 담은, 아직 서버에 없는 값이다. id 가 없는 것이 그 차이다.
 *
 * [maxChars] 가 null 이면 **글자 수 제한을 못 찾았다**는 뜻이다. 그때 서버는 400~600자로 만든다(F4-2) —
 * 값을 여기서 400 이나 600 으로 채워 넣지 않는다. 채우면 「공고가 정한 500자」와 「우리가 정한 500자」가
 * 화면에서 구분되지 않고, 사용자는 공고에 없는 제한을 공고의 것으로 읽는다.
 */
public data class ApplicationItemDraft(
    val order: Int,
    val question: String,
    val maxChars: Int?,
) {
    init {
        require(order >= 1) { "order must be at least 1" }
        require(question.isNotBlank()) { "question must not be blank" }
        require(maxChars == null || maxChars > 0) { "maxChars must be null or positive" }
    }
}

/**
 * 문항 입력 규칙 — 화면과 도메인이 같은 값을 본다.
 *
 * 상한을 두는 이유는 서버가 아니라 사람이다. 문항 30개짜리 자소서는 없고, 20000자짜리 문항도 없다.
 * 오타로 들어온 값을 그대로 보내면 서버가 항목마다 LLM 을 부르는 동안 사용자가 몇 분을 기다린 뒤 실패한다.
 */
public object ApplicationItemRules {
    /** 문항 수 상한. 자소서 한 장의 문항은 보통 3~5개다. */
    public const val MAX_ITEMS: Int = 10

    /** 질문 원문 길이 상한. */
    public const val MAX_QUESTION_LENGTH: Int = 200

    /** 글자 수 제한으로 받아들이는 범위. */
    public const val MIN_MAX_CHARS: Int = 100
    public const val MAX_MAX_CHARS: Int = 5000

    /** 제한을 못 찾은 문항을 서버가 만드는 길이(F4-2) — 화면이 미리 알린다. */
    public const val DEFAULT_MIN_CHARS: Int = 400
    public const val DEFAULT_MAX_CHARS: Int = 600

    public fun validateQuestion(raw: String): ProfileFieldViolation? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> ProfileFieldViolation.Required
            value.length > MAX_QUESTION_LENGTH -> ProfileFieldViolation.TooLong(MAX_QUESTION_LENGTH)
            else -> null
        }
    }

    /** 빈 값은 「제한 없음」이라 위반이 아니다 — 공고가 글자 수를 안 적는 일은 흔하다. */
    public fun validateMaxChars(raw: String): ProfileFieldViolation? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val parsed = value.toIntOrNull() ?: return ProfileFieldViolation.InvalidFormat
        return if (parsed in MIN_MAX_CHARS..MAX_MAX_CHARS) null else ProfileFieldViolation.OutOfRange
    }

    /** 입력값을 [ApplicationItemDraft.maxChars] 로 옮긴다. 빈 값·범위 밖은 null(= 제한 없음)이다. */
    public fun parseMaxChars(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in MIN_MAX_CHARS..MAX_MAX_CHARS }
}

/** 공고가 인식한 문항을 초안 문항으로 옮긴다. 순서는 공고가 준 `order` 를 따른다. */
public fun List<PostingFormQuestion>.toItemDrafts(): List<ApplicationItemDraft> =
    sortedBy(PostingFormQuestion::order)
        .mapIndexed { index, question ->
            ApplicationItemDraft(order = index + 1, question = question.question, maxChars = question.maxChars)
        }

/**
 * 순서를 1부터 다시 매긴다 — 추가·삭제·이동 뒤에 부른다.
 *
 * 서버는 `order` 로 문항을 세우므로(§6 응답의 `items[].order`), 지우고 남은 구멍을 그대로 보내면 화면에
 * 「1, 3, 4」가 보인다. 사용자가 그 번호를 공고의 번호로 읽는다.
 */
public fun List<ApplicationItemDraft>.renumbered(): List<ApplicationItemDraft> =
    mapIndexed { index, item -> if (item.order == index + 1) item else item.copy(order = index + 1) }

package com.careercompass.core.model.posting

import java.time.Instant
import java.time.LocalDate

/** 공고 유형 — API_SPEC v0.1 §5 `type`. 기능 스펙의 「장학금 / 채용 / 공모전 / 대외활동 / 기타」. */
public enum class PostingType(
    public val wireValue: String,
) {
    Recruit("recruit"),
    Scholarship("scholarship"),
    Contest("contest"),
    Activity("activity"),
    Other("other"),
    ;

    /** 지원서 초안 생성 버튼은 장학금·채용에만 노출된다 — 기능 스펙 F3-3. */
    public val supportsApplicationDraft: Boolean
        get() = this == Recruit || this == Scholarship

    public companion object {
        public fun fromWireValue(value: String): PostingType? = entries.firstOrNull { it.wireValue == value }
    }
}

/** 적합도 해석 레이블 — 기능 스펙 F3-2 「점수 해석 레이블」. */
public enum class SuitabilityLabel(
    public val wireValue: String,
    public val minScore: Int,
) {
    VerySuitable("very_suitable", 80),
    Suitable("suitable", 60),
    Neutral("neutral", 40),
    Low("low", 0),
    ;

    public companion object {
        public fun fromWireValue(value: String): SuitabilityLabel? = entries.firstOrNull { it.wireValue == value }

        public fun fromScore(score: Int): SuitabilityLabel {
            require(score in 0..100) { "score must be within 0..100" }
            return entries.first { score >= it.minScore }
        }
    }
}

/** 적합도 분석 축 — 기능 스펙 F3-2 「점수 산출 기준」 4축. */
public enum class SuitabilityAxisKind(
    public val wireValue: String,
) {
    FieldSimilarity("field_similarity"),
    Qualification("qualification"),
    Preference("preference"),
    Competition("competition"),
    ;

    public companion object {
        public fun fromWireValue(value: String): SuitabilityAxisKind? = entries.firstOrNull { it.wireValue == value }
    }
}

/** 축별 세부 점수와 가중치(%). */
public data class SuitabilityAxis(
    val kind: SuitabilityAxisKind,
    val score: Int,
    val weight: Int,
) {
    init {
        require(score in 0..100) { "score must be within 0..100" }
        require(weight in 0..100) { "weight must be within 0..100" }
    }
}

/** 공고 상세의 적합도 분석 결과 — 기능 스펙 F3-3. */
public data class Suitability(
    val score: Int,
    val label: SuitabilityLabel,
    val breakdown: List<SuitabilityAxis>,
    val strengthComment: String?,
    val weaknessComment: String?,
) {
    init {
        require(score in 0..100) { "score must be within 0..100" }
        require(breakdown.map(SuitabilityAxis::kind).distinct().size == breakdown.size) { "breakdown axes must be unique" }
        require(strengthComment == null || strengthComment.isNotBlank()) { "strengthComment must be null or non-blank" }
        require(weaknessComment == null || weaknessComment.isNotBlank()) { "weaknessComment must be null or non-blank" }
    }
}

/** 공고가 수집된 게시판 참조. */
public data class PostingBoardRef(
    val id: Long,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}

/**
 * 공고 목록 항목 — `GET /postings`.
 *
 * @property score 적합도. 파싱 전이거나 프로필이 부족하면 null (점수 미노출 — 기능 스펙 F2-3·F3-1).
 */
public data class Posting(
    val id: Long,
    val title: String,
    val type: PostingType,
    val board: PostingBoardRef,
    val dueDate: LocalDate?,
    val collectedAt: Instant,
    val score: Int?,
    val scoreLabel: SuitabilityLabel?,
    val isRead: Boolean,
    val isBookmarked: Boolean,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(score == null || score in 0..100) { "score must be null or within 0..100" }
        require((score == null) == (scoreLabel == null)) { "score and scoreLabel must be present together" }
    }

    /** 마감일이 지난 공고 — 목록에서 기본 숨김(기능 스펙 F2-3 「공고 상태 관리」). */
    public fun isExpired(today: LocalDate): Boolean = dueDate?.isBefore(today) == true

    /** 마감까지 남은 일수. 마감일이 없으면 null, 지났으면 음수. */
    public fun daysUntilDue(today: LocalDate): Long? =
        dueDate?.let {
            java.time.temporal.ChronoUnit.DAYS
                .between(today, it)
        }
}

/** 모집 대상 조건 — 학년·학점 텍스트(파싱 실패 시 null). */
public data class PostingQualifications(
    val year: String?,
    val gpa: String?,
) {
    init {
        require(year == null || year.isNotBlank()) { "year must be null or non-blank" }
        require(gpa == null || gpa.isNotBlank()) { "gpa must be null or non-blank" }
    }
}

/** 공고에서 자동 인식한 지원서 질문 항목 — 기능 스펙 F4-1. */
public data class PostingFormQuestion(
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

/** AI 구조화 파싱 결과 — 기능 스펙 F3-1. */
public data class PostingParsed(
    val keywords: List<String>,
    val qualifications: PostingQualifications,
    val preferences: List<String>,
    val formQuestions: List<PostingFormQuestion>,
) {
    init {
        require(keywords.all(String::isNotBlank)) { "keywords must not be blank" }
        require(preferences.all(String::isNotBlank)) { "preferences must not be blank" }
        require(formQuestions.map(PostingFormQuestion::order).distinct().size == formQuestions.size) {
            "form question orders must be unique"
        }
    }
}

/** 공고 상세 — `GET /postings/{id}`. 파싱·적합도는 완료 전이면 null 이다. */
public data class PostingDetail(
    val id: Long,
    val title: String,
    val type: PostingType,
    val board: PostingBoardRef,
    val url: String,
    val rawContent: String,
    val dueDate: LocalDate?,
    val collectedAt: Instant,
    val isRead: Boolean,
    val isBookmarked: Boolean,
    val parsed: PostingParsed?,
    val suitability: Suitability?,
    val similar: List<Posting>,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
        require(similar.map(Posting::id).distinct().size == similar.size) { "similar posting ids must be unique" }
    }
}

/** 정렬 조건 — API_SPEC v0.1 §5 `sort`. */
public enum class PostingSort(
    public val wireValue: String,
) {
    CollectedDesc("collected_desc"),
    DueAsc("due_asc"),
    ScoreDesc("score_desc"),
}

/**
 * 공고 목록 조회 조건 — API_SPEC §5 `GET /postings` 쿼리.
 *
 * 검색어·마감일 파라미터(`q`·`dueFrom`·`dueTo`·`includeNoDueDate`·`includeExpired`)는 v0.3(BE #56) 계약이다.
 * null 이면 보내지 않고 서버 기본값(마감일 없음 포함, 지난 마감 숨김)을 따른다.
 *
 * @property searchQuery 검색어 — 서버가 제목과 파싱 키워드를 대소문자 무시 부분 문자열로 찾는다.
 * @property dueFrom 마감일 하한(그날 포함).
 * @property dueTo 마감일 상한(그날 포함).
 */
public data class PostingQuery(
    val boardIds: List<Long> = emptyList(),
    val types: List<PostingType> = emptyList(),
    val minScore: Int? = null,
    val unreadOnly: Boolean = false,
    val searchQuery: String? = null,
    val dueFrom: LocalDate? = null,
    val dueTo: LocalDate? = null,
    val includeNoDueDate: Boolean? = null,
    val includeExpired: Boolean? = null,
    val sort: PostingSort = PostingSort.CollectedDesc,
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(boardIds.distinct().size == boardIds.size) { "boardIds must be unique" }
        require(types.distinct().size == types.size) { "types must be unique" }
        require(minScore == null || minScore in 0..100) { "minScore must be null or within 0..100" }
        require(searchQuery == null || searchQuery.isNotBlank()) { "searchQuery must be null or non-blank" }
        require(dueFrom == null || dueTo == null || !dueTo.isBefore(dueFrom)) { "dueTo must not be before dueFrom" }
        require(cursor == null || cursor.isNotBlank()) { "cursor must be null or non-blank" }
        require(limit in 1..MAX_LIMIT) { "limit must be within 1..$MAX_LIMIT" }
    }

    public companion object {
        public const val DEFAULT_LIMIT: Int = 20
        public const val MAX_LIMIT: Int = 100
    }
}

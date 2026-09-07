package com.careercompass.feature.foryou.domain.model

/** 내보내기 형식 — API_SPEC v0.1 §7 의 `format`. */
public enum class ExportFormat(
    public val wireValue: String,
) {
    Markdown("markdown"),
    Notion("notion"),
    Html("html"),
    Plain("plain"),
    ;

    public companion object {
        public fun fromWireValue(value: String): ExportFormat? = entries.firstOrNull { it.wireValue == value }
    }
}

/** 내보낼 묶음 — §7 의 `sections`. 순서가 문서에 실리는 순서다. */
public enum class ExportSection(
    public val wireValue: String,
) {
    Basic("basic"),
    Skills("skills"),
    Projects("projects"),
    Awards("awards"),
    Summary("summary"),
    ;

    public companion object {
        public fun fromWireValue(value: String): ExportSection? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 내보내기 결과 — 서버가 만든 문서 본문.
 *
 * 파일이 아니라 문자열이다. 저장·공유는 화면의 일이고(#193), 계약은 본문까지만 준다.
 */
public data class StrengthExport(
    val format: ExportFormat,
    val content: String,
)

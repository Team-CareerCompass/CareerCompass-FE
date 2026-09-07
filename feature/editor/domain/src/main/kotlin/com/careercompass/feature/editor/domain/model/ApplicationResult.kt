package com.careercompass.feature.editor.domain.model

/** 지원 결과 — API_SPEC v0.1 §6 의 `PATCH /applications/{id}/result`. */
public enum class ApplicationResult(
    public val wireValue: String,
) {
    /** 결과를 기다리는 중. */
    Pending("pending"),
    Pass("pass"),
    Fail("fail"),

    /** 결과를 남기지 않기로 했다. 「아직 안 정함」인 [Pending] 과 다르다. */
    None("none"),
    ;

    public companion object {
        public fun fromWireValue(value: String): ApplicationResult? = entries.firstOrNull { it.wireValue == value }
    }
}

package com.careercompass.feature.editor.domain.model

/**
 * 지원서 한 건의 상태 — API_SPEC v0.1 §6 의 `status`.
 *
 * 화면이 문자열을 비교하지 않도록 계약의 네 값을 그대로 타입으로 세운다. [wireValue] 는 `GET /applications`
 * 의 `status` 필터로 되돌려 보낼 때 쓴다.
 */
public enum class ApplicationStatus(
    public val wireValue: String,
) {
    /** AI 가 항목을 쓰는 중. 스트림이 열려 있는 상태다. */
    Generating("generating"),

    /** 모든 항목이 답을 받았다. 편집·저장할 수 있다. */
    Ready("ready"),

    /** 일부 항목이 실패했다. 나머지는 쓸 수 있고, 실패한 항목만 재생성한다. */
    PartialFailed("partial_failed"),

    /** 최종 저장돼 이력에 올랐다. 다시 편집하면 [Ready] 로 돌아간다(`docs/spec/canon.md` 「지원서 규칙」). */
    Saved("saved"),
    ;

    public companion object {
        public fun fromWireValue(value: String): ApplicationStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

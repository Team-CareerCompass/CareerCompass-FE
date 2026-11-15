package com.careercompass.feature.editor.domain.model

/**
 * 초안의 어조 — API_SPEC v0.1 §6 의 `tone`.
 *
 * 스펙이 예시로 보인 값은 `formal` 과 `casual` 둘이다. 셋째 값을 상상해 넣지 않는다 — 서버가 모르는 값을
 * 받으면 `INVALID_INPUT` 으로 거절하고, 그 실패는 사용자가 고칠 수 없다.
 */
public enum class ApplicationTone(
    public val wireValue: String,
) {
    Formal("formal"),
    Casual("casual"),
    ;

    public companion object {
        public fun fromWireValue(value: String): ApplicationTone? = entries.firstOrNull { it.wireValue == value }
    }
}

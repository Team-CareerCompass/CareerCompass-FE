package com.careercompass.feature.editor.domain.model

/**
 * 지원서의 문항 하나 — API_SPEC v0.1 §6 의 `items[]`.
 *
 * [maxChars] 는 서버가 문항마다 주는 글자 상한이다. 앱이 상수로 들고 있지 않는다 — 문항마다 다르고, 그
 * 값을 정하는 것은 공고를 읽은 서버다.
 */
public data class ApplicationItem(
    val id: Long,
    val order: Int,
    val question: String,
    val maxChars: Int,
    val status: ApplicationItemStatus,
    val answer: String?,
) {
    init {
        require(maxChars > 0) { "maxChars must be positive" }
    }

    /** 남은 글자 수. 상한을 넘었으면 음수다 — 화면이 그 부호로 경고를 가른다(F4-2). */
    public val remainingChars: Int
        get() = maxChars - (answer?.length ?: 0)
}

/**
 * 문항 하나의 상태.
 *
 * §6 의 응답 예시에는 `loading` 과 `done` 만 나온다. **그런데 지원서 상태에는 `partial_failed` 가 있다** —
 * 「일부가 실패했다」가 성립하려면 어느 항목이 실패했는지 항목 쪽에서 알 수 있어야 한다. 그래서 [Failed] 를
 * 둔다. 서버가 어떤 값으로 그것을 말할지는 정해지지 않았으므로, 매핑은 「`loading` 도 `done` 도 아니면
 * 실패」로 읽는다 — 판정과 그 근거는 `docs/spec/canon.md` 에 남겼다.
 */
public enum class ApplicationItemStatus(
    public val wireValue: String,
) {
    Loading("loading"),
    Done("done"),
    Failed("failed"),
    ;

    public companion object {
        /** `loading` · `done` 외의 모든 값은 [Failed] 다. 모르는 값을 성공으로 읽으면 빈 답이 정상으로 보인다. */
        public fun fromWireValue(value: String): ApplicationItemStatus = entries.firstOrNull { it.wireValue == value } ?: Failed
    }
}

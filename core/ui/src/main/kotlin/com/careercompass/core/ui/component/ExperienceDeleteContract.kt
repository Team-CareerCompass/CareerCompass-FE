package com.careercompass.core.ui.component

import androidx.compose.runtime.Immutable

/**
 * Step 3 카드 삭제 확인 상태 — 삭제는 되돌릴 수 없어 한 번 묻는다(F1-3).
 *
 * @property title 무엇을 지우는지 다이얼로그에서 다시 확인할 수 있게 든다.
 */
@Immutable
public data class ExperienceDeleteState(
    public val experienceId: Long,
    public val title: String,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }
}

/** User intentions emitted by [ExperienceDeleteDialog]. */
public sealed interface ExperienceDeleteEvent {
    public data object Confirmed : ExperienceDeleteEvent

    public data object Dismissed : ExperienceDeleteEvent
}

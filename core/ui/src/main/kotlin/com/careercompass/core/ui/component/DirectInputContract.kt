package com.careercompass.core.ui.component

import androidx.compose.runtime.Immutable
import com.careercompass.core.model.user.ProfileFieldViolation

/**
 * Step 4 「직접 입력하기」 시트 상태 — 라벨과 본문을 받아 TXT 지원서로 업로드한다(F1-4 「직접 입력」).
 *
 * 라벨의 공백·길이 규칙은 파일 업로드 라벨과 공유한다(`PastApplicationLabelRules`).
 */
@Immutable
public data class DirectInputState(
    public val label: String = "",
    public val content: String = "",
    public val labelError: ProfileFieldViolation? = null,
    public val contentError: ProfileFieldViolation? = null,
    public val isSubmitting: Boolean = false,
) {
    public val isInputEnabled: Boolean
        get() = !isSubmitting

    public val isSubmitEnabled: Boolean
        get() = !isSubmitting && label.isNotBlank() && content.isNotBlank()
}

/** User intentions emitted by [DirectInputSheet]. */
public sealed interface DirectInputEvent {
    public data class LabelChanged(
        public val value: String,
    ) : DirectInputEvent

    public data class ContentChanged(
        public val value: String,
    ) : DirectInputEvent

    public data object Submitted : DirectInputEvent

    public data object Dismissed : DirectInputEvent
}

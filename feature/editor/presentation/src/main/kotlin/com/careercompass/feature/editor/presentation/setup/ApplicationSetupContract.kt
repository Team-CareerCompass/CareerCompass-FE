package com.careercompass.feature.editor.presentation.setup

import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.feature.editor.domain.model.ApplicationTone

/** 스낵바 한 줄로 끝나는 알림. */
public enum class ApplicationSetupMessage {
    /** 문항 상한(10개)에 닿아 추가를 막았다. */
    LimitReached,

    /** 마지막 문항은 지울 수 없다 — 문항이 없으면 초안을 만들 수 없다. */
    LastItemKept,

    /** `POST /applications` 가 실패했다. 쓰던 문항은 화면에 그대로 남는다. */
    CreateFailed,
}

/** 문항 편집 시트가 가리키는 대상. [order] 가 null 이면 새 문항이다. */
public data class ApplicationItemEditorState(
    val order: Int?,
    val question: String = "",
    val maxChars: String = "",
    val questionError: ProfileFieldViolation? = null,
    val maxCharsError: ProfileFieldViolation? = null,
) {
    val isNew: Boolean get() = order == null

    val hasErrors: Boolean get() = questionError != null || maxCharsError != null
}

/** 문항 편집 시트가 올려 보내는 조작. */
public sealed interface ApplicationItemEditorEvent {
    public data class QuestionChanged(
        val value: String,
    ) : ApplicationItemEditorEvent

    public data class MaxCharsChanged(
        val value: String,
    ) : ApplicationItemEditorEvent

    public data object Submitted : ApplicationItemEditorEvent

    public data object Dismissed : ApplicationItemEditorEvent
}

/** 화면이 [ApplicationSetupViewModel] 에 올려 보내는 사용자 조작. */
public sealed interface ApplicationSetupEvent {
    public data class ToneSelected(
        val tone: ApplicationTone,
    ) : ApplicationSetupEvent

    public data class ItemEditClicked(
        val order: Int,
    ) : ApplicationSetupEvent

    public data class ItemDeleteClicked(
        val order: Int,
    ) : ApplicationSetupEvent

    public data object ItemAddClicked : ApplicationSetupEvent

    public data object StartClicked : ApplicationSetupEvent

    public data object RetryClicked : ApplicationSetupEvent

    public data object BackClicked : ApplicationSetupEvent
}

/** 초안 생성이 시작됐다 — 앱 셸이 진행 화면으로 보낸다(#184). */
public data class ApplicationDraftStarted(
    val applicationId: Long,
    val postingId: Long,
)

/** 세션이 끝났다(401). 앱 셸이 로그인으로 보낸다. */
public data object ApplicationSetupSessionEnd

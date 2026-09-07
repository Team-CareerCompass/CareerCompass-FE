package com.careercompass.feature.profile.presentation.basicinfo

/**
 * 프로필 편집이 다루는 다섯 필드 — 기능 스펙 F1-2 Step 1 과 같은 목록이다.
 *
 * 서버가 값을 거부할 때 어느 칸이 문제인지 말해 주므로(400 `INVALID_INPUT` 의 `error.field`), 그 문자열을
 * 이 열거형으로 좁혀 화면이 칸을 짚는다. 모르는 필드 이름이 오면 어느 칸도 짚지 않고 스낵바로만 알린다 —
 * 엉뚱한 칸에 빨간 줄을 그으면 사용자가 멀쩡한 값을 고치게 된다.
 */
public enum class ProfileEditField(
    /** API_SPEC v0.1 §2 `PATCH /users/me` 의 요청 필드 이름. 서버가 `error.field` 로 돌려주는 값이다. */
    public val wireName: String,
) {
    Name("name"),
    School("school"),
    Department("department"),
    GradePointAverage("gpa"),
    GraduationYear("gradYear"),
    ;

    public companion object {
        public fun fromWireName(value: String?): ProfileEditField? = entries.firstOrNull { it.wireName == value }
    }
}

/** 스낵바 한 줄로 끝나는 알림. */
public enum class ProfileEditMessage {
    /** 저장이 실패했다. 입력은 그대로 남는다 — 다시 누르면 된다. */
    SaveFailed,

    /** 서버가 값을 거부했는데 어느 칸인지 말해 주지 않았다. 칸을 짚을 수 없으니 사실만 알린다. */
    SaveRejected,
}

/** 화면이 [ProfileEditViewModel] 에 올려 보내는 사용자 조작. */
public sealed interface ProfileEditEvent {
    public data class NameChanged(
        val value: String,
    ) : ProfileEditEvent

    public data class DepartmentChanged(
        val value: String,
    ) : ProfileEditEvent

    public data class GradePointAverageChanged(
        val value: String,
    ) : ProfileEditEvent

    public data object SchoolPickerClicked : ProfileEditEvent

    public data object GraduationPickerClicked : ProfileEditEvent

    public data object SaveClicked : ProfileEditEvent

    public data object RetryClicked : ProfileEditEvent

    public data object BackClicked : ProfileEditEvent
}

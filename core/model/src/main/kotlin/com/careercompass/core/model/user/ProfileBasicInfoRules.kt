package com.careercompass.core.model.user

/** 이름 길이 상한 — 기능 스펙 F1-2 Step 1. */
public const val MAX_PROFILE_NAME_LENGTH: Int = 20

/** 학과 길이 상한 — 기능 스펙 F1-2 Step 1. */
public const val MAX_PROFILE_DEPARTMENT_LENGTH: Int = 30

/**
 * 기본 정보 필드가 거부되는 사유. 문구는 화면이 리소스로 만들고, 여기는 **판정만** 담는다.
 *
 * 화면 계층의 오류 타입(온보딩의 `OnboardingFieldError` 등)과 모양이 같은 것은 우연이 아니다 — 그쪽은
 * 이 판정을 그 모듈의 말로 옮긴 것이고, 판정의 정본은 여기 하나다.
 */
public sealed interface ProfileFieldViolation {
    /** 필수 값이 비어 있다. */
    public data object Required : ProfileFieldViolation

    /** [maxLength] 자를 넘었다. */
    public data class TooLong(
        val maxLength: Int,
    ) : ProfileFieldViolation {
        init {
            require(maxLength > 0) { "maxLength must be positive" }
        }
    }

    /** 형식이 맞지 않는다(숫자·`YYYY.MM` 등). */
    public data object InvalidFormat : ProfileFieldViolation

    /** 형식은 맞지만 허용 범위를 벗어났다. */
    public data object OutOfRange : ProfileFieldViolation
}

/**
 * 프로필 기본 정보(이름·학교·학과·학점·졸업 연도)의 검증 규칙 — 기능 스펙 F1-2 Step 1.
 *
 * ### 왜 core 에 있는가 (#176)
 * 같은 다섯 필드를 **두 화면이 받는다** — 온보딩 Step 1 과 마이 탭의 프로필 편집이다. 규칙이 두 벌이 되면
 * 온보딩을 통과한 값이 편집 화면에서 거부되거나 그 반대가 되고, 어느 쪽이 맞는지 판정할 근거가 사라진다.
 * 그래서 규칙을 화면 모듈이 아니라 **필드의 주인인 모델 옆**에 둔다. [MAX_GRADE_POINT_AVERAGE]·
 * [MIN_GRADUATION_YEAR]·[SchoolNameRules] 가 이미 이 자리에 있다.
 *
 * 여기 있는 것은 「이 문자열이 필드에 들어갈 수 있는가」 뿐이다. 「무엇을 서버에 보낼 것인가」(빈 값은 보내지
 * 않는다 등)는 [UserProfileUpdate] 가, 문구는 화면이 갖는다.
 */
public object ProfileBasicInfoRules {
    /**
     * 이름·학과처럼 길이만 보는 텍스트 필드.
     *
     * 입력 중에는 빈 값을 탓하지 않는다([requireValue] false) — 제출을 눌렀을 때만 필수 오류를 낸다.
     * 그러지 않으면 한 글자 지웠다 다시 쓰는 사이 빨간 문구가 깜빡인다.
     */
    public fun validateText(
        value: String,
        maxLength: Int,
        requireValue: Boolean,
    ): ProfileFieldViolation? =
        when {
            value.isBlank() -> if (requireValue) ProfileFieldViolation.Required else null
            value.trim().length > maxLength -> ProfileFieldViolation.TooLong(maxLength)
            else -> null
        }

    public fun validateName(
        value: String,
        requireValue: Boolean,
    ): ProfileFieldViolation? = validateText(value, MAX_PROFILE_NAME_LENGTH, requireValue)

    public fun validateDepartment(
        value: String,
        requireValue: Boolean,
    ): ProfileFieldViolation? = validateText(value, MAX_PROFILE_DEPARTMENT_LENGTH, requireValue)

    /**
     * 학교 검증 — 목록에서 고른 값과 직접 입력한 값이 같은 규칙을 지난다(#138).
     *
     * 길이는 [SchoolNameRules.normalize] 로 다듬은 뒤 잰다. 사용자가 지우지 않은 잉여 공백 때문에
     * 「너무 깁니다」 가 뜨면 어디를 줄여야 하는지 알 수 없다.
     */
    public fun validateSchool(
        value: String,
        requireValue: Boolean,
    ): ProfileFieldViolation? {
        val name = SchoolNameRules.normalize(value)
        return when {
            name.isEmpty() -> if (requireValue) ProfileFieldViolation.Required else null
            name.length > SchoolNameRules.MAX_LENGTH -> ProfileFieldViolation.TooLong(SchoolNameRules.MAX_LENGTH)
            else -> null
        }
    }

    /** 학점은 선택 입력이라 빈 값은 오류가 아니다. 적혀 있으면 형식과 범위를 본다. */
    public fun validateGradePointAverage(value: String): ProfileFieldViolation? {
        if (value.isBlank()) return null
        val parsed = parseGradePointAverage(value) ?: return ProfileFieldViolation.InvalidFormat
        return if (parsed in 0.0..MAX_GRADE_POINT_AVERAGE) null else ProfileFieldViolation.OutOfRange
    }

    public fun parseGradePointAverage(value: String): Double? = value.trim().takeIf(GPA_PATTERN::matches)?.toDoubleOrNull()

    /** `YYYY` 또는 `YYYY.MM`. 서버에는 연도만 보내므로 월 없는 값도 받는다(프로필 프리필이 연도만 안다). */
    public fun validateGraduationDate(value: String): ProfileFieldViolation? {
        if (value.isBlank()) return null
        val year = parseGraduationYear(value) ?: return ProfileFieldViolation.InvalidFormat
        return if (year >= MIN_GRADUATION_YEAR) null else ProfileFieldViolation.OutOfRange
    }

    public fun parseGraduationYear(value: String): Int? =
        GRADUATION_PATTERN
            .matchEntire(value.trim())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private val GPA_PATTERN = Regex("""^\d(\.\d{1,2})?$""")
    private val GRADUATION_PATTERN = Regex("""^(\d{4})(?:\.(0[1-9]|1[0-2]))?$""")
}

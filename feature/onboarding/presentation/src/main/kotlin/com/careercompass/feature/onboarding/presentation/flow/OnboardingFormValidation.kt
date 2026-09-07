package com.careercompass.feature.onboarding.presentation.flow

import com.careercompass.core.model.user.InterestTagRules
import com.careercompass.core.model.user.MAX_PROFILE_DEPARTMENT_LENGTH
import com.careercompass.core.model.user.MAX_PROFILE_NAME_LENGTH
import com.careercompass.core.model.user.ProfileBasicInfoRules
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.feature.onboarding.presentation.shared.model.OnboardingFieldError

/**
 * Step 1 검증 — 판정은 [ProfileBasicInfoRules] 가 하고 여기서는 온보딩의 오류 타입으로 옮기기만 한다.
 *
 * 규칙 자체를 이 모듈에 두면 마이 탭의 프로필 편집(#176)이 같은 규칙을 한 벌 더 갖게 되고, 두 벌은 반드시
 * 어긋난다 — 온보딩을 통과한 값이 편집 화면에서 거부되는 식이다. 그래서 규칙은 필드의 주인인 `core:model`
 * 로 올렸고, 이 객체는 그 판정을 화면이 읽을 말로 바꾸는 얇은 층만 남았다.
 */
internal object OnboardingStep1Rules {
    const val MAX_NAME_LENGTH = MAX_PROFILE_NAME_LENGTH
    const val MAX_MAJOR_LENGTH = MAX_PROFILE_DEPARTMENT_LENGTH

    /** 입력 중에는 빈 값을 탓하지 않는다([requireValue] false) — 「다음」 을 눌렀을 때만 필수 오류를 낸다. */
    fun validateText(
        value: String,
        maxLength: Int,
        requireValue: Boolean,
    ): OnboardingFieldError? = ProfileBasicInfoRules.validateText(value, maxLength, requireValue).toFieldError()

    /** 학교 검증 — 목록에서 고른 값과 직접 입력한 값이 같은 규칙을 지난다(#138). */
    fun validateSchool(
        value: String,
        requireValue: Boolean,
    ): OnboardingFieldError? = ProfileBasicInfoRules.validateSchool(value, requireValue).toFieldError()

    fun validateGradePointAverage(value: String): OnboardingFieldError? =
        ProfileBasicInfoRules.validateGradePointAverage(value).toFieldError()

    fun parseGradePointAverage(value: String): Double? = ProfileBasicInfoRules.parseGradePointAverage(value)

    /** `YYYY` 또는 `YYYY.MM`. 서버에는 연도만 보내므로 월 없는 값도 받는다(프로필 프리필이 연도만 안다). */
    fun validateGraduationDate(value: String): OnboardingFieldError? = ProfileBasicInfoRules.validateGraduationDate(value).toFieldError()

    fun parseGraduationYear(value: String): Int? = ProfileBasicInfoRules.parseGraduationYear(value)
}

/** 도메인 판정을 온보딩 화면의 오류 타입으로 옮긴다. 갈래가 늘면 `when` 이 빠진 분기를 알린다. */
internal fun ProfileFieldViolation?.toFieldError(): OnboardingFieldError? =
    when (this) {
        null -> null
        ProfileFieldViolation.Required -> OnboardingFieldError.Required
        is ProfileFieldViolation.TooLong -> OnboardingFieldError.TooLong(maxLength)
        ProfileFieldViolation.InvalidFormat -> OnboardingFieldError.InvalidFormat
        ProfileFieldViolation.OutOfRange -> OnboardingFieldError.OutOfRange
    }

/** Step 2 태그 정규화 — 규칙은 `core:model` 이 갖는다(마이 탭의 관심 편집이 같은 것을 쓴다, #177). */
internal fun normalizeInterestTag(raw: String): String = InterestTagRules.normalize(raw)

package com.careercompass.feature.onboarding.presentation.flow

import com.careercompass.core.model.user.GraduationDateRules
import com.careercompass.feature.onboarding.presentation.shared.model.OnboardingFieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFormValidationTest {
    @Test
    fun `이름과 학과는 길이 상한을 검사하고 입력 중에는 빈 값을 탓하지 않는다`() {
        assertNull(OnboardingStep1Rules.validateText("", OnboardingStep1Rules.MAX_NAME_LENGTH, requireValue = false))
        assertEquals(
            OnboardingFieldError.Required,
            OnboardingStep1Rules.validateText(" ", OnboardingStep1Rules.MAX_NAME_LENGTH, requireValue = true),
        )
        assertEquals(
            OnboardingFieldError.TooLong(20),
            OnboardingStep1Rules.validateText("가".repeat(21), OnboardingStep1Rules.MAX_NAME_LENGTH, requireValue = true),
        )
        assertNull(OnboardingStep1Rules.validateText("가".repeat(30), OnboardingStep1Rules.MAX_MAJOR_LENGTH, requireValue = true))
        assertEquals(
            OnboardingFieldError.TooLong(30),
            OnboardingStep1Rules.validateText("가".repeat(31), OnboardingStep1Rules.MAX_MAJOR_LENGTH, requireValue = true),
        )
    }

    @Test
    fun `학점은 0점0에서 4점5 사이 소수만 받는다`() {
        assertNull(OnboardingStep1Rules.validateGradePointAverage(""))
        assertNull(OnboardingStep1Rules.validateGradePointAverage("3.87"))
        assertNull(OnboardingStep1Rules.validateGradePointAverage("4.5"))
        assertNull(OnboardingStep1Rules.validateGradePointAverage("0"))
        assertEquals(OnboardingFieldError.OutOfRange, OnboardingStep1Rules.validateGradePointAverage("4.51"))
        assertEquals(OnboardingFieldError.InvalidFormat, OnboardingStep1Rules.validateGradePointAverage("abc"))
        assertEquals(OnboardingFieldError.InvalidFormat, OnboardingStep1Rules.validateGradePointAverage("3.875"))
        assertEquals(OnboardingFieldError.InvalidFormat, OnboardingStep1Rules.validateGradePointAverage("-1"))
        assertEquals(3.87, OnboardingStep1Rules.parseGradePointAverage(" 3.87 ")!!, 0.0001)
    }

    @Test
    fun `졸업 예정은 YYYY 또는 YYYY점MM 이고 2000년 이후여야 한다`() {
        assertNull(OnboardingStep1Rules.validateGraduationDate(""))
        assertNull(OnboardingStep1Rules.validateGraduationDate("2027.02"))
        assertNull(OnboardingStep1Rules.validateGraduationDate("2027"))
        assertEquals(OnboardingFieldError.InvalidFormat, OnboardingStep1Rules.validateGraduationDate("2027.13"))
        assertEquals(OnboardingFieldError.InvalidFormat, OnboardingStep1Rules.validateGraduationDate("27.02"))
        assertEquals(OnboardingFieldError.OutOfRange, OnboardingStep1Rules.validateGraduationDate("1999.02"))
        assertEquals(2027, OnboardingStep1Rules.parseGraduationYear("2027.02"))
        assertEquals("2027.02", GraduationDateRules.format(2027, 2))
    }

    @Test
    fun `태그는 앞의 샵과 공백을 걷어낸다`() {
        assertEquals("AI", normalizeInterestTag(" ##AI "))
        assertEquals("", normalizeInterestTag("# "))
    }

    @Test
    fun `지원서 라벨은 공백만 있으면 거부하고 길이 상한을 검사한다`() {
        assertEquals(OnboardingFieldError.Required, PastApplicationLabelRules.validate("   "))
        assertNull(PastApplicationLabelRules.validate(" 2024 카카오 인턴 자소서 "))
        assertNull(PastApplicationLabelRules.validate("가".repeat(PastApplicationLabelRules.MAX_LENGTH)))
        assertEquals(
            OnboardingFieldError.TooLong(PastApplicationLabelRules.MAX_LENGTH),
            PastApplicationLabelRules.validate("가".repeat(PastApplicationLabelRules.MAX_LENGTH + 1)),
        )
        assertEquals("2024 카카오 인턴 자소서", PastApplicationLabelRules.normalize(" 2024 카카오 인턴 자소서 "))
    }

    @Test
    fun `업로드 기본 라벨은 확장자를 뺀 파일명이고 규칙을 만족한다`() {
        assertEquals("이력서_최종_v3(2)", PastApplicationLabelRules.defaultLabelFor("이력서_최종_v3(2).pdf"))
        assertEquals("resume", PastApplicationLabelRules.defaultLabelFor("resume"))
        assertEquals(".pdf", PastApplicationLabelRules.defaultLabelFor(".pdf"))
        val long = PastApplicationLabelRules.defaultLabelFor("가".repeat(80) + ".docx")
        assertEquals(PastApplicationLabelRules.MAX_LENGTH, long.length)
        assertNull(PastApplicationLabelRules.validate(long))
    }
}

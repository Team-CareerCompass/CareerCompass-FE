package com.careercompass.core.ui.component

import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class ExperienceQuickAddContractTest {
    @Test
    public fun submitEnabled_requiresTitleAndIdle() {
        assertFalse(ExperienceEditorState().isSubmitEnabled)
        assertTrue(ExperienceEditorState(title = "제목").isSubmitEnabled)
        assertFalse(ExperienceEditorState(title = "제목", isSubmitting = true).isSubmitEnabled)
        assertFalse(ExperienceEditorState(isSubmitting = true).isInputEnabled)
    }

    @Test
    public fun rules_followSpecTable() {
        assertTrue(ExperienceEditorRules.isStartDateRequired(ExperienceType.Project))
        assertTrue(ExperienceEditorRules.isStartDateRequired(ExperienceType.Intern))
        assertFalse(ExperienceEditorRules.isStartDateRequired(ExperienceType.Award))
        // 수상·자격증은 기간이 아니라 시점 하나를 갖는다 — 종료 칸도 없고 `startDate` 도 쓰지 않는다(#166).
        assertFalse(ExperienceEditorRules.hasPeriod(ExperienceType.Award))
        assertFalse(ExperienceEditorRules.hasPeriod(ExperienceType.Certificate))
        assertTrue(ExperienceEditorRules.hasPeriod(ExperienceType.Activity))
        assertTrue(ExperienceEditorRules.isPrimaryRequired(ExperienceType.Award))
        assertTrue(ExperienceEditorRules.isPrimaryRequired(ExperienceType.Intern))
        assertTrue(ExperienceEditorRules.isPrimaryRequired(ExperienceType.Activity))
        assertFalse(ExperienceEditorRules.isPrimaryRequired(ExperienceType.Project))
        assertFalse(ExperienceEditorRules.hasSecondary(ExperienceType.Certificate))
        assertTrue(ExperienceEditorRules.isSecondaryRequired(ExperienceType.Intern))
        assertFalse(ExperienceEditorRules.isSecondaryRequired(ExperienceType.Project))
    }

    @Test
    public fun detailRules_matchSpecTable() {
        // F1-3 「유형별 입력 필드」 — 사용 기술·링크는 프로젝트에만, 자유 서술 상세는 인턴·대외활동에만.
        assertTrue(ExperienceEditorRules.hasTechTags(ExperienceType.Project))
        assertFalse(ExperienceEditorRules.hasTechTags(ExperienceType.Intern))
        assertTrue(ExperienceEditorRules.hasLink(ExperienceType.Project))
        assertFalse(ExperienceEditorRules.hasLink(ExperienceType.Activity))
        assertTrue(ExperienceEditorRules.hasDetail(ExperienceType.Intern))
        assertTrue(ExperienceEditorRules.hasDetail(ExperienceType.Activity))
        assertFalse(ExperienceEditorRules.hasDetail(ExperienceType.Project))
        // 수상·자격증은 공통 필드만으로 전 필드가 채워져 「자세히」 영역 자체가 없다.
        assertFalse(ExperienceEditorRules.hasDetailSection(ExperienceType.Award))
        assertFalse(ExperienceEditorRules.hasDetailSection(ExperienceType.Certificate))
        assertTrue(ExperienceEditorRules.hasDetailSection(ExperienceType.Project))
        assertTrue(ExperienceEditorRules.hasDetailSection(ExperienceType.Intern))
        assertTrue(ExperienceEditorRules.hasDetailSection(ExperienceType.Activity))
    }

    @Test
    public fun normalizeTechTag_dropsHashAndSpaces() {
        assertEquals("Kotlin", ExperienceEditorRules.normalizeTechTag("  #Kotlin  "))
        assertEquals("Jetpack Compose", ExperienceEditorRules.normalizeTechTag("# Jetpack Compose"))
        assertEquals("", ExperienceEditorRules.normalizeTechTag("   #  "))
    }

    @Test
    public fun hasDetailValues_seesEachDetailField() {
        assertFalse(ExperienceEditorState().hasDetailValues)
        assertTrue(ExperienceEditorState(techs = listOf("Kotlin")).hasDetailValues)
        assertTrue(ExperienceEditorState(link = "https://example.com").hasDetailValues)
        assertTrue(ExperienceEditorState(detail = "주요 업무").hasDetailValues)
        // 입력칸에만 남은 글자는 아직 태그가 아니다 — 「입력됨」 표시가 붙지 않는다.
        assertFalse(ExperienceEditorState(techInput = "Kotlin").hasDetailValues)
    }

    @Test
    public fun parseYearMonthPoint_acceptsOnlyYearDotMonth() {
        assertEquals(ExperiencePoint.YearMonth(2025, 9), ExperienceEditorRules.parseYearMonthPoint(" 2025.09 "))
        assertNull(ExperienceEditorRules.parseYearMonthPoint("2025.13"))
        assertNull(ExperienceEditorRules.parseYearMonthPoint("2025-09"))
        assertNull(ExperienceEditorRules.parseYearMonthPoint(""))
        // 연도만 친 글은 「연월」이 아니다 — 여기서 월을 채워 주면 없던 달이 생긴다(#166).
        assertNull(ExperienceEditorRules.parseYearMonthPoint("2025"))
    }

    @Test
    public fun parseYearPoint_readsYearAndNarrowsLegacyYearMonth() {
        assertEquals(ExperiencePoint.Year(2025), ExperienceEditorRules.parseYearPoint(" 2025 "))
        // 예전 카드가 남긴 `YYYY.MM` 은 연도로 좁혀 읽는다 — 좁히기는 새 정보를 만들지 않는다.
        assertEquals(ExperiencePoint.Year(2025), ExperienceEditorRules.parseYearPoint("2025.09"))
        assertNull(ExperienceEditorRules.parseYearPoint("25"))
        assertNull(ExperienceEditorRules.parseYearPoint("2025.13"))
        assertNull(ExperienceEditorRules.parseYearPoint(""))
    }

    @Test
    public fun resolvePoint_keepsOriginPrecisionWhenMonthUnchanged() {
        val origin = ExperiencePoint.Date(LocalDate.of(2025, 6, 15))
        // 칸이 `YYYY.MM` 이라 같은 달을 그대로 둔 글은 일(day)에 대해 아무 말도 하지 않는다 —
        // 아는 일은 원본의 것뿐이므로 그것을 돌려준다(#171).
        assertEquals(origin, ExperienceEditorRules.resolvePoint("2025.06", origin))
        assertEquals(origin, ExperienceEditorRules.resolvePoint(" 2025.06 ", origin))
        // 달을 고쳤으면 원본은 더 이상 같은 시점이 아니다 — 사용자가 준 정밀도인 연월 그대로 읽는다.
        assertEquals(ExperiencePoint.YearMonth(2025, 7), ExperienceEditorRules.resolvePoint("2025.07", origin))
        assertEquals(ExperiencePoint.YearMonth(2024, 6), ExperienceEditorRules.resolvePoint("2024.06", origin))
        // 신규 등록에는 지킬 원본이 없다 — 칸이 담는 만큼인 연월이 남는다.
        assertEquals(ExperiencePoint.YearMonth(2025, 6), ExperienceEditorRules.resolvePoint("2025.06", null))
        // 읽을 수 없는 글은 원본이 있어도 시점이 아니다 — 되살리기가 검증을 우회하지 않는다.
        assertNull(ExperienceEditorRules.resolvePoint("2025", origin))
        assertNull(ExperienceEditorRules.resolvePoint("", origin))
    }

    @Test
    public fun isValidDateInput_acceptsYearOnlyForAward() {
        assertTrue(ExperienceEditorRules.isValidDateInput(ExperienceType.Award, "2025"))
        assertTrue(ExperienceEditorRules.isValidDateInput(ExperienceType.Award, "2025.09"))
        assertFalse(ExperienceEditorRules.isValidDateInput(ExperienceType.Project, "2025"))
        assertTrue(ExperienceEditorRules.isValidDateInput(ExperienceType.Certificate, "2025.09"))
        assertFalse(ExperienceEditorRules.isValidDateInput(ExperienceType.Certificate, "2025"))
    }
}

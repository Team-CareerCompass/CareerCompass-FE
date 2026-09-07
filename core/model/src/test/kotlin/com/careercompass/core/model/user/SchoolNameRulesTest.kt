package com.careercompass.core.model.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolNameRulesTest {
    @Test
    fun `앞뒤 공백을 걷어내고 연속 공백을 한 칸으로 줄인다`() {
        assertEquals("건국대학교", SchoolNameRules.normalize("  건국대학교 "))
        assertEquals("한양대학교 ERICA", SchoolNameRules.normalize("한양대학교   ERICA"))
        assertEquals("서울대학교 대학원", SchoolNameRules.normalize("\t서울대학교\n대학원  "))
    }

    @Test
    fun `공백만 입력한 값은 다듬으면 비고 유효하지 않다`() {
        assertEquals("", SchoolNameRules.normalize("   "))
        assertFalse(SchoolNameRules.isValid(""))
        assertFalse(SchoolNameRules.isValid("   "))
        assertFalse(SchoolNameRules.isValid("\t\n"))
    }

    @Test
    fun `길이는 다듬은 뒤 재고 상한을 넘으면 유효하지 않다`() {
        val exact = "가".repeat(SchoolNameRules.MAX_LENGTH)
        assertTrue(SchoolNameRules.isValid("  $exact  "))
        assertFalse(SchoolNameRules.isValid(exact + "가"))
    }

    @Test
    fun `해외 대학 이름도 상한 안에 들어온다`() {
        assertTrue(SchoolNameRules.isValid("University of California, Berkeley"))
    }

    /**
     * 목록에서 고른 값과 직접 입력해 다듬은 값이 같은 모양이어야 저장된 학교 표기가 갈라지지 않는다.
     * 목록에 잉여 공백이 든 항목이 들어오면 이 테스트가 먼저 깨진다.
     */
    @Test
    fun `목록의 모든 학교는 이미 다듬어진 모양이다`() {
        SchoolCatalog.schools.forEach { school ->
            assertEquals(school, SchoolNameRules.normalize(school))
            assertTrue("$school 이 규칙을 벗어난다", SchoolNameRules.isValid(school))
        }
    }
}

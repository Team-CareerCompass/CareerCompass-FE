package com.careercompass.core.model.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolCatalogTest {
    @Test
    fun `빈 검색어는 전체 목록을 돌려준다`() {
        assertEquals(SchoolCatalog.schools, SchoolCatalog.search(""))
        assertEquals(SchoolCatalog.schools, SchoolCatalog.search("   "))
    }

    @Test
    fun `공백을 무시한 부분 일치로 찾는다`() {
        assertEquals(listOf("건국대학교"), SchoolCatalog.search("건국 대"))
        assertTrue(SchoolCatalog.search("여자").containsAll(listOf("성신여자대학교", "숙명여자대학교", "이화여자대학교")))
        assertEquals(listOf("한양대학교", "한양대학교 ERICA"), SchoolCatalog.search("한양"))
        assertEquals(listOf("한양대학교 ERICA"), SchoolCatalog.search("erica"))
    }

    @Test
    fun `없는 학교는 빈 목록이다`() {
        assertTrue(SchoolCatalog.search("없는대학교").isEmpty())
    }

    @Test
    fun `목록에는 중복이 없고 건국대학교가 들어 있다`() {
        assertEquals(SchoolCatalog.schools.size, SchoolCatalog.schools.distinct().size)
        assertTrue(SchoolCatalog.contains("건국대학교"))
    }
}

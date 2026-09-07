package com.careercompass.core.model.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JobOptionCatalogTest {
    @Test
    fun `코드와 라벨은 중복이 없다`() {
        val options = JobOptionCatalog.options
        assertEquals(options.size, options.map(JobOption::code).distinct().size)
        assertEquals(options.size, options.map(JobOption::label).distinct().size)
        assertTrue(options.size >= 15)
    }

    @Test
    fun `API 예시 코드가 목록에 있다`() {
        assertEquals("백엔드 개발", JobOptionCatalog.find("backend")?.label)
        assertEquals("프론트엔드 개발", JobOptionCatalog.find("frontend")?.label)
        assertNull(JobOptionCatalog.find("unknown"))
    }

    @Test
    fun `코드는 영문 snake_case 만 허용한다`() {
        assertThrows(IllegalArgumentException::class.java) { JobOption(code = "Back End", label = "백엔드") }
        assertThrows(IllegalArgumentException::class.java) { JobOption(code = "backend", label = " ") }
    }
}

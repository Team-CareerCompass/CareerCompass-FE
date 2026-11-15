package com.careercompass.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 다른 모듈의 계측 테스트가 합성하는 Content 만 public 이라는 문서 계약을 지킨다(#410). */
class ContentVisibilityKonsistTest {
    @Test
    fun `public Content 집합은 mvi 문서의 예외 목록과 일치한다`() {
        val actual =
            Konsist
                .scopeFromProject()
                .files
                .filter { "/src/main/" in it.projectPath.replace('\\', '/') }
                .flatMap { it.functions() }
                .filter { it.name.endsWith("Content") && it.annotations.any { annotation -> annotation.name == "Composable" } }
                .filterNot { it.hasPrivateModifier || it.hasInternalModifier || it.hasProtectedModifier }
                .map { it.name }
                .toSet()
        val root =
            generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                .first { File(it, DOCUMENT).isFile }
        val document = File(root, DOCUMENT).readText()
        val start = document.indexOf(START)
        val end = document.indexOf(END)
        assertTrue("public Content 예외 목록 경계가 문서에 있어야 한다", start >= 0 && end > start)
        val expected =
            Regex("`([A-Za-z0-9]+Content)`")
                .findAll(document.substring(start + START.length, end))
                .map { it.groupValues[1] }
                .toSet()
        assertTrue("예외 목록이 비어 있으면 가드의 정본을 잃는다", expected.isNotEmpty())
        assertEquals("public Content 선언과 $DOCUMENT 예외 목록이 다르다", expected, actual)
    }

    private companion object {
        const val DOCUMENT = "docs/convention/mvi.md"
        const val START = "<!-- public-content-exceptions:start -->"
        const val END = "<!-- public-content-exceptions:end -->"
    }
}

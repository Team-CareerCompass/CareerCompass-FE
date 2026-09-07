package com.careercompass.core.ui.component

import com.careercompass.core.model.user.ProfileFieldViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

public class SchoolPickerContractTest {
    @Test
    public fun blankOrDuplicateResults_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SchoolPickerState(results = listOf("건국대학교", " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SchoolPickerState(results = listOf("건국대학교", "건국대학교"))
        }
    }

    @Test
    public fun emptyResults_areAllowed() {
        assertEquals(emptyList<String>(), SchoolPickerState(query = "없음", results = emptyList()).results)
    }

    /** 시트를 연 첫 화면(검색어 없음)에서는 목록 선택이 유일한 길이다 — 직접 입력은 동등한 선택지가 아니다. */
    @Test
    public fun directInput_isNotOfferedBeforeSearching() {
        assertFalse(SchoolPickerState(results = listOf("건국대학교")).isDirectInputOffered)
        assertFalse(SchoolPickerState(query = "  ", results = listOf("건국대학교")).isDirectInputOffered)
    }

    @Test
    public fun directInput_isOfferedAfterSearching_regardlessOfResultCount() {
        assertTrue(SchoolPickerState(query = "없는대", results = emptyList()).isDirectInputOffered)
        assertTrue(SchoolPickerState(query = "서울대", results = listOf("서울대학교")).isDirectInputOffered)
    }

    /** 직접 입력 모드에서는 안내를 다시 띄우지 않는다 — 이미 그 길에 들어와 있다. */
    @Test
    public fun directInput_isNotOfferedWhileEditing() {
        val state = SchoolPickerState(query = "없는대", results = emptyList(), directInput = SchoolDirectInputState())
        assertFalse(state.isDirectInputOffered)
    }

    @Test
    public fun confirm_requiresNonBlankValueWithoutError() {
        assertFalse(SchoolDirectInputState().isConfirmEnabled)
        assertFalse(SchoolDirectInputState(value = "   ").isConfirmEnabled)
        assertFalse(SchoolDirectInputState(value = "서울예술대학교", error = ProfileFieldViolation.TooLong(50)).isConfirmEnabled)
        assertTrue(SchoolDirectInputState(value = "서울예술대학교").isConfirmEnabled)
    }
}

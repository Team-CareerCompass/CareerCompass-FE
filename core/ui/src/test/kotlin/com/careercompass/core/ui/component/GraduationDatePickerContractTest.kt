package com.careercompass.core.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

public class GraduationDatePickerContractTest {
    @Test
    public fun selectedYear_mustBeInAscendingUniqueYears() {
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = listOf(2026, 2027), selectedYear = 2025, selectedMonth = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = listOf(2027, 2026), selectedYear = 2026, selectedMonth = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = listOf(2026, 2026), selectedYear = 2026, selectedMonth = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = emptyList(), selectedYear = 2026, selectedMonth = 2)
        }
    }

    @Test
    public fun selectedMonth_mustBeWithinOneToTwelve() {
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = listOf(2026), selectedYear = 2026, selectedMonth = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraduationPickerState(years = listOf(2026), selectedYear = 2026, selectedMonth = 13)
        }
        assertEquals((1..12).toList(), GraduationPickerState(years = listOf(2026), selectedYear = 2026, selectedMonth = 12).months)
    }
}

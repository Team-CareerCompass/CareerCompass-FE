package com.careercompass.core.ui.component

import androidx.compose.runtime.Immutable

/** 졸업 예정 연월 선택 시트 상태. [years] 는 오름차순 목록이고 [selectedYear] 는 그 안에 있어야 한다. */
@Immutable
public data class GraduationPickerState(
    public val years: List<Int>,
    public val selectedYear: Int,
    public val selectedMonth: Int,
) {
    init {
        require(years.isNotEmpty()) { "years must not be empty" }
        require(years.distinct().size == years.size) { "years must be unique" }
        require(years == years.sorted()) { "years must be ascending" }
        require(selectedYear in years) { "selectedYear must be one of years" }
        require(selectedMonth in 1..12) { "selectedMonth must be within 1..12" }
    }

    public val months: List<Int>
        get() = MONTHS

    private companion object {
        val MONTHS = (1..12).toList()
    }
}

/** User intentions emitted by [GraduationDatePickerSheet]. */
public sealed interface GraduationDatePickerEvent {
    public data class YearSelected(
        public val year: Int,
    ) : GraduationDatePickerEvent

    public data class MonthSelected(
        public val month: Int,
    ) : GraduationDatePickerEvent

    public data object Confirmed : GraduationDatePickerEvent

    public data object Dismissed : GraduationDatePickerEvent
}

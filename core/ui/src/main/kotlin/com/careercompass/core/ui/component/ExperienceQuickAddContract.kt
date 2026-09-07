package com.careercompass.core.ui.component

import androidx.compose.runtime.Immutable
import com.careercompass.core.model.experience.EXPERIENCE_YEAR_RANGE
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.user.ProfileFieldViolation

/**
 * Step 3 「경험 추가·수정」 시트 상태 — 공통 5개 필드 + 유형별 상세(F1-3) 입력.
 *
 * [primary]·[secondary]·[detail] 의 뜻은 [type] 에 따라 다르다 — [ExperienceEditorRules] 참고.
 * 필드 오류는 사유만 두고 문구는 시트가 리소스로 만든다.
 *
 * ### 상세 입력을 왜 접어 두는가 (#139)
 * Step 3 는 기능 스펙 F1-2 가 「1개 이상 입력 시 정확도 상승」으로 정의한 **선택 단계**다. 상세 필드를 항상
 * 펼쳐 두면 시트가 길어져 「나중에 하자」가 늘고, 그러면 카드 하나조차 안 남아 F3-2 우대 조건 매칭이 오히려
 * 더 굶는다. 그래서 필수 규칙이 걸린 공통 5필드만 항상 보이고, 상세는 [isDetailExpanded] 로 접는다.
 * **이미 값이 있는 카드를 고칠 때는 펼친 채로 연다** — 접혀 있으면 사용자는 그 값이 지워졌다고 읽는다.
 *
 * @property experienceId 수정 중인 카드의 id. null 이면 신규 등록이다. 수정 중에는 [type] 을 바꾸지 않는다 —
 * 유형마다 필드 의미가 달라 바꾸려면 지우고 다시 만들어야 한다.
 * @property techs 확정된 기술 태그. 입력칸([techInput])의 글자는 아직 태그가 아니다.
 * @property isDetailExpanded 상세 입력 영역을 펼쳤는가. 접혀 있어도 값은 상태에 그대로 살아 저장에 실린다 —
 * 접기는 보이기만 줄이는 것이지 값을 버리는 것이 아니다.
 * @property startDateOrigin 이 카드가 원래 갖고 있던 시작 시점 — 신규 등록이면 null 이다. 시점 칸([startDate])은
 * `YYYY.MM` 이라 일(day)을 담지 못하므로, **사용자가 손대지 않은 정밀도를 되돌리는 데에만** 쓴다. 화면에는
 * 그리지 않는다. 근거는 [ExperienceEditorRules.resolvePoint].
 * @property endDateOrigin 종료 시점의 같은 것. 기간이 없는 유형([ExperienceEditorRules.hasPeriod])은 둘 다 null 이다.
 */
@Immutable
public data class ExperienceEditorState(
    public val experienceId: Long? = null,
    public val type: ExperienceType = ExperienceType.Project,
    public val title: String = "",
    public val startDate: String = "",
    public val endDate: String = "",
    public val startDateOrigin: ExperiencePoint? = null,
    public val endDateOrigin: ExperiencePoint? = null,
    public val primary: String = "",
    public val secondary: String = "",
    public val techs: List<String> = emptyList(),
    public val techInput: String = "",
    public val link: String = "",
    public val detail: String = "",
    public val isDetailExpanded: Boolean = false,
    public val titleError: ProfileFieldViolation? = null,
    public val startDateError: ProfileFieldViolation? = null,
    public val endDateError: ProfileFieldViolation? = null,
    public val primaryError: ProfileFieldViolation? = null,
    public val secondaryError: ProfileFieldViolation? = null,
    public val techInputError: ProfileFieldViolation? = null,
    public val linkError: ProfileFieldViolation? = null,
    public val detailError: ProfileFieldViolation? = null,
    public val isSubmitting: Boolean = false,
) {
    public val isInputEnabled: Boolean
        get() = !isSubmitting

    /** 제목만 있으면 제출을 시도할 수 있다 — 나머지 검증은 제출 시점에 필드 오류로 돌려준다. */
    public val isSubmitEnabled: Boolean
        get() = !isSubmitting && title.isNotBlank()

    /** 기존 카드를 고치는 중인가. 시트 제목·제출 문구와 유형 잠금이 이 값으로 갈린다. */
    public val isEditing: Boolean
        get() = experienceId != null

    /** 접힌 상세 영역에 이미 채워진 값이 있는가 — 접힌 채로도 「입력됨」을 알리려고 시트가 쓴다. */
    public val hasDetailValues: Boolean
        get() = techs.isNotEmpty() || link.isNotBlank() || detail.isNotBlank()
}

/** User intentions emitted by [ExperienceQuickAddSheet]. */
public sealed interface ExperienceQuickAddEvent {
    public data class TypeSelected(
        public val type: ExperienceType,
    ) : ExperienceQuickAddEvent

    public data class TitleChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    public data class StartDateChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    public data class EndDateChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    public data class PrimaryChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    public data class SecondaryChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    /** 기술 태그 입력칸의 글자가 바뀌었다. 아직 태그가 된 것은 아니다. */
    public data class TechInputChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    /** 입력칸의 글자를 태그로 확정한다(키보드 완료·칩 추가). */
    public data object TechTagSubmitted : ExperienceQuickAddEvent

    public data class TechTagRemoved(
        public val tag: String,
    ) : ExperienceQuickAddEvent

    public data class LinkChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    public data class DetailChanged(
        public val value: String,
    ) : ExperienceQuickAddEvent

    /** 상세 입력 영역을 펼치거나 접는다. */
    public data object DetailSectionToggled : ExperienceQuickAddEvent

    public data object Submitted : ExperienceQuickAddEvent

    public data object Dismissed : ExperienceQuickAddEvent
}

/**
 * 유형별 필드 의미와 필수 규칙 — 기능 스펙 F1-3 「유형별 입력 필드」를 시트 필드에 대응시킨 표.
 *
 * | 유형 | primary | secondary | 시점 칸 | 상세(접힘) |
 * |---|---|---|---|---|
 * | 프로젝트 | 역할 | 요약 | 시작 (필수) | 사용 기술 태그 + 성과·결과물 링크 |
 * | 수상 | 수상 등급 (필수) | 주관 기관 | 수상 연도 `YYYY` | (없음) |
 * | 인턴 | 회사명 (필수) | 직무 (필수) | 시작 (필수) | 주요 업무 요약 |
 * | 대외활동 | 기관명 (필수) | 성과 요약 | 시작 (선택) | 역할 |
 * | 자격증 | 발급 기관 | (없음) | 취득 연월 | (없음) |
 *
 * 이 표가 `ExperienceDetails` 의 전 필드를 덮는다 — 시트가 모르는 필드는 이제 없다. 그래서 수정 저장은
 * 「원본에서 물려받기」 대신 **시트 왕복이 무손실**이라는 계약으로 지킨다(`Experience.toEditorState()` 참고).
 *
 * ### 태그·링크 상한은 여기 없다 (#208)
 * 태그 개수·길이와 링크 형식(스킴)은 「이 카드가 성립하는 조건」이라 모델이 든다 —
 * `MAX_EXPERIENCE_TECH_TAGS` · `MAX_EXPERIENCE_TECH_TAG_LENGTH` · `MAX_EXPERIENCE_LINK_LENGTH` ·
 * `isAllowedExperienceLink`. 시트는 그것을 **참조만** 해서 오류 문구를 만든다. 값이 두 벌이면 입력 경로가
 * 늘 때마다 어긋나고, 어긋난 규칙은 어느 쪽이 사실인지 알 수 없다.
 *
 * ### 유형을 바꾸면 이전 유형에만 있던 값은
 * **지우지 않고 시트에 그대로 두되, 저장할 때 새 유형이 쓰지 않는 값은 버린다.** 이미 [primary]·[secondary]
 * 가 그렇게 동작하고 있어 규칙을 두 벌로 만들지 않는다. 칩을 잘못 눌렀다가 되돌아온 사용자가 친 글을 잃지
 * 않는 쪽이기도 하다. 「저장 때 버린다」는 `ExperienceEditorState.toDraft()` 가 이 표만 보고 값을 읽어
 * 구조적으로 보장된다.
 *
 * ### 시점 칸 하나가 유형마다 다른 정밀도로 간다 (#166 · #207)
 * 수상은 연, 자격증은 연월, 나머지는 날짜까지 담는다. **정밀도가 다른 같은 사실**이라 방향이 중요하다 —
 * 이제 그 정밀도를 모델(`ExperiencePoint`)이 값으로 들고, 시트는 칸이 담는 만큼만 읽는다. 자세한 근거는
 * [hasPeriod] 와 `toDraft()` KDoc.
 *
 * ### 칸이 담지 못하는 정밀도는 지킨다 (#171)
 * 시점 칸이 `YYYY.MM` 이라, 서버나 다른 클라이언트가 만든 `2025-06-15` 짜리 카드를 열었다 제목만 고쳐 저장하면
 * 시작일이 `2025-06-01` 로 **깎였다.** 사용자가 손대지 않은 값이 조용히 달라지는 것이다. 그래서 시트는 원본
 * 시점을 함께 들고 다니다가([ExperienceEditorState.startDateOrigin]) 사용자가 그 칸의 달을 바꾸지 않았으면
 * 원본을 그대로 돌려준다 — [resolvePoint].
 *
 * **칸의 정밀도를 일 단위로 올리는 길은 고르지 않았다.** 그러면 이 문제는 사라지지만, F1-3 의 시점은 대부분
 * 「언제쯤」이라 대다수 사용자가 모르는 일자를 지어내 채우게 된다 — [hasPeriod] 가 금지한 넓히기를 이번에는
 * **사용자에게 시키는** 셈이다. 정밀도가 부족해서 생긴 문제가 아니라 **손대지 않은 값을 다시 쓴** 것이 문제이므로,
 * 고치는 자리도 입력 칸이 아니라 저장 경로다.
 */
public object ExperienceEditorRules {
    /**
     * 시트 제목 칸의 길이 상한.
     *
     * 태그·링크 상한과 달리 아직 여기 있다 — 모델이 [com.careercompass.core.model.experience.Experience.title] 에
     * 길이를 걸지 않기 때문이다. 모델이 걸게 되면 그때 같이 올린다.
     */
    public const val MAX_TITLE_LENGTH: Int = 50

    /** 자유 서술 칸(역할·요약)의 길이 상한. 위와 같은 이유로 화면 규칙이다. */
    public const val MAX_TEXT_LENGTH: Int = 100

    /** 시작 시점이 필수인 유형인가 — 판정의 정본은 모델(`ExperienceType.requiresStartPoint`)이다. */
    public fun isStartDateRequired(type: ExperienceType): Boolean = type.requiresStartPoint

    /**
     * 기간(시작~종료)을 갖는 유형인가 — 수상·자격증은 기간이 아니라 **시점 하나**를 갖는다(F1-3).
     *
     * ### 같은 사실의 다른 정밀도 (#166 · #207)
     * 와이어(API_SPEC v0.1 §3)에서 `startDate` 는 카드 공통 컬럼이고 `year`(수상)·`acquiredYearMonth`
     * (자격증)는 유형별 `data` 안에 있지만, 셋 다 「그 경험이 언제인가」 하나를 가리킨다. 다른 것은 **정밀도**뿐이다.
     *
     * 그래서 두 방향의 값이 다르다. **좁히기(일자 → 연도·연월)는 도출**이라 새 정보를 만들지 않지만,
     * **넓히기(연도 → 일자)는 날조**다. 「2025」에서 「2025-01-01」을 만들면 사용자가 준 적 없는 월과 일이
     * 사실로 굳어 정렬·표시가 그걸 근거로 삼는다.
     *
     * 그 규칙은 이제 모델이 든다 — `ExperiencePoint` 는 넓히는 길이 없고, 유형별로 담을 수 있는 정밀도의
     * 상·하한(`ExperienceType.maxPointPrecision`)이 불변식이다. 이 함수는 그 판정을 시트 어휘로 부르는
     * 별칭이고, 정본은 `ExperienceType.hasPeriod` 다.
     */
    public fun hasPeriod(type: ExperienceType): Boolean = type.hasPeriod

    public fun isPrimaryRequired(type: ExperienceType): Boolean =
        type == ExperienceType.Award || type == ExperienceType.Intern || type == ExperienceType.Activity

    public fun hasSecondary(type: ExperienceType): Boolean = type != ExperienceType.Certificate

    public fun isSecondaryRequired(type: ExperienceType): Boolean = type == ExperienceType.Intern

    /** 사용 기술 태그를 받는 유형인가 — F1-3 표에서 프로젝트에만 있다. */
    public fun hasTechTags(type: ExperienceType): Boolean = type == ExperienceType.Project

    /** 성과·결과물 링크를 받는 유형인가 — F1-3 표에서 프로젝트에만 있다. */
    public fun hasLink(type: ExperienceType): Boolean = type == ExperienceType.Project

    /** 자유 서술 상세를 받는 유형인가 — 인턴은 주요 업무 요약, 대외활동은 역할. */
    public fun hasDetail(type: ExperienceType): Boolean = type == ExperienceType.Intern || type == ExperienceType.Activity

    /** 「자세히」 영역 자체를 그리는가. 수상·자격증은 상세 필드가 없어 접을 것도 없다. */
    public fun hasDetailSection(type: ExperienceType): Boolean = hasTechTags(type) || hasLink(type) || hasDetail(type)

    /** 관심 분야 태그와 같은 규칙으로 `#` 과 앞뒤 공백을 떼어 낸다. */
    public fun normalizeTechTag(raw: String): String = raw.trim().trimStart('#').trim()

    /**
     * 시점 칸의 글을 시점으로 읽되, **원본과 같은 달이면 원본을 그대로 돌려준다** (#171).
     *
     * ### 왜 「이 칸을 고쳤는가」를 표시하지 않고 값으로 판정하는가
     * 이 칸은 `YYYY.MM` 이라 **일을 표현할 수단이 아예 없다.** 그래서 사용자가 친 글이 원본과 같은 달이면 그
     * 글에는 일에 대한 정보가 하나도 담기지 않았고, 우리가 아는 유일한 일은 원본의 것뿐이다. 「고쳤음」 표시를
     * 따로 두는 길도 있었지만(#156 이 게시판 수정 시트에서 쓴 diff 가 그 계열이다), 그 표시는 값을 바꾸는 모든
     * 경로가 빠짐없이 세워 줘야 유지되고 한 군데만 빠뜨리면 조용히 되돌아간다. 값으로 판정하면 들고 다닐 상태가
     * 없다 — 「2025.07 로 고쳤다가 2025.06 으로 되돌린」 사용자가 일을 잃지 않는 것도 덤이다.
     *
     * 달이 다르면 원본은 더 이상 같은 시점이 아니므로 **연월 정밀도 그대로** 돌려준다. 없는 값을 지어내는 것이
     * 아니라 사용자가 방금 준 정밀도 그대로다 — 그 「그대로」를 모델이 값으로 들 수 있게 된 것이 #207 이다.
     */
    public fun resolvePoint(
        value: String,
        origin: ExperiencePoint?,
    ): ExperiencePoint? {
        val parsed = parseYearMonthPoint(value) ?: return null
        return origin?.takeIf { it is ExperiencePoint.WithMonth && it.year == parsed.year && it.month == parsed.month } ?: parsed
    }

    /** `YYYY.MM` 을 연월 시점으로 읽는다. 형식이 다르면 null. */
    public fun parseYearMonthPoint(value: String): ExperiencePoint.YearMonth? {
        val match = YEAR_MONTH.matchEntire(value.trim()) ?: return null
        val (year, month) = match.destructured
        return year.toIntOrNull()?.takeIf { it in EXPERIENCE_YEAR_RANGE }?.let { ExperiencePoint.YearMonth(it, month.toInt()) }
    }

    /**
     * 수상 연도 칸을 읽는다 — `YYYY`.
     *
     * 다른 클라이언트가 만든 카드나 이 시트의 예전 버전이 남긴 `YYYY.MM` 도 받아 **연도만** 남긴다.
     * 좁히기는 도출이라 안전하다([hasPeriod]) — 반대로 연도에서 월을 채우는 함수는 아예 없다.
     */
    public fun parseYearPoint(value: String): ExperiencePoint.Year? {
        val trimmed = value.trim()
        val year =
            YEAR
                .matchEntire(trimmed)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in EXPERIENCE_YEAR_RANGE }
        return year?.let(ExperiencePoint::Year) ?: parseYearMonthPoint(trimmed)?.toYear()
    }

    /** 시점 칸에 친 글이 그 유형이 받는 형식인가. 수상만 연도(`YYYY`)를 함께 받는다. */
    public fun isValidDateInput(
        type: ExperienceType,
        value: String,
    ): Boolean = if (type == ExperienceType.Award) parseYearPoint(value) != null else parseYearMonthPoint(value) != null

    private val YEAR_MONTH = Regex("""^(\d{4})\.(0[1-9]|1[0-2])$""")

    private val YEAR = Regex("""^(\d{4})$""")
}

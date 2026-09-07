package com.careercompass.core.ui.component

import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperienceDraft
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.experience.MAX_EXPERIENCE_LINK_LENGTH
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAGS
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAG_LENGTH
import com.careercompass.core.model.experience.isAllowedExperienceLink
import com.careercompass.core.model.user.ProfileFieldViolation

/**
 * 경험 카드 편집 시트의 **순수 전이** — 카드를 시트 상태로 펴고, 검증하고, 저장할 초안으로 접는다.
 *
 * ### 왜 화면 모듈이 아니라 여기 있는가 (#179)
 * 같은 시트를 온보딩 Step 3 와 마이 탭의 경험 카드 목록이 함께 쓴다. 시트만 공유하고 이 전이를 각 ViewModel
 * 이 따로 들면, 유형별 필수 규칙과 **날짜 정밀도 규칙**(#166 · #171)이 두 벌이 된다 — 한쪽에서 저장한 카드가
 * 다른 쪽에서 열리지 않거나, 열었다 저장만 해도 없던 월·일이 생기는 그 함정이다.
 *
 * 전부 순수 함수라 코루틴 하네스 없이 단언할 수 있고, 저장소를 부르는 일은 각 ViewModel 이 한다.
 */

public fun Experience.toEditorState(): ExperienceEditorState {
    val details = details
    val start = startPoint?.toEditorText().orEmpty()
    val primary =
        when (details) {
            is ExperienceDetails.Project -> details.role
            is ExperienceDetails.Award -> details.rank
            is ExperienceDetails.Intern -> details.company
            is ExperienceDetails.Activity -> details.organization
            is ExperienceDetails.Certificate -> details.issuer
        }
    val secondary =
        when (details) {
            is ExperienceDetails.Project -> details.summary
            is ExperienceDetails.Award -> details.organizer
            is ExperienceDetails.Intern -> details.role
            is ExperienceDetails.Activity -> details.summary
            is ExperienceDetails.Certificate -> null
        }
    val detail =
        when (details) {
            is ExperienceDetails.Intern -> details.summary
            is ExperienceDetails.Activity -> details.role
            else -> null
        }
    // 서버에 상한을 넘는 태그가 있어도(다른 클라이언트가 만들었을 수 있다) 여기서 자르지 않는다 — 자르면
    // 제목만 고치려던 사용자가 자기도 모르게 태그를 잃는다. 상한은 「새로 더할 때」만 건다.
    val techs = (details as? ExperienceDetails.Project)?.techs.orEmpty()
    val link = (details as? ExperienceDetails.Project)?.link.orEmpty()
    return ExperienceEditorState(
        experienceId = id,
        type = type,
        title = title,
        startDate = start,
        endDate = endPoint?.toEditorText().orEmpty(),
        startDateOrigin = startPoint,
        endDateOrigin = endPoint,
        primary = primary.orEmpty(),
        secondary = secondary.orEmpty(),
        techs = techs,
        link = link,
        detail = detail.orEmpty(),
        isDetailExpanded = techs.isNotEmpty() || link.isNotEmpty() || !detail.isNullOrEmpty(),
    )
}

/**
 * 시점을 그 정밀도가 담기는 칸 글로 옮긴다 — 연 정밀도는 `2025`, 그보다 자세하면 `2025.06`.
 *
 * 연도만 아는 카드를 「2025.01」로 열면 사용자가 준 적 없는 1월이 화면에 뜨고 그대로 저장에 실린다(#166).
 * 반대로 `2025-06-15` 를 「2025.06」으로 여는 것은 칸이 일을 담지 못해서일 뿐이고, 잃은 일은
 * [ExperienceEditorState.startDateOrigin] 이 들고 있다가 되돌린다(#171).
 */
private fun ExperiencePoint.toEditorText(): String =
    when (this) {
        is ExperiencePoint.Year -> "%04d".format(year)
        is ExperiencePoint.WithMonth -> "%04d.%02d".format(year, month)
    }

/** 문서를 목록에서 빼면서 펼침 상태도 함께 정리한다 — 목록에 없는 문서를 펼친 채로 두면 상태 불변식이 깨진다. */

public val ExperienceEditorState.hasErrors: Boolean
    get() =
        listOfNotNull(
            titleError,
            startDateError,
            endDateError,
            primaryError,
            secondaryError,
            techInputError,
            linkError,
            detailError,
        ).isNotEmpty()

/**
 * 입력칸에 남은 글자를 기술 태그로 확정한다. 규칙에 걸리면 태그 대신 필드 오류를 남긴다.
 *
 * 중복은 **대소문자를 무시하고** 거른다 — `kotlin` 과 `Kotlin` 은 사람에게 같은 기술이고, 카드에 둘 다 뜨면
 * 오히려 잘못 입력한 것처럼 보인다. 먼저 친 표기를 남긴다. 상한을 넘으면 오류만 남기고 입력칸은 비우지
 * 않는다 — 태그 하나를 지우고 다시 완료를 누르면 그대로 들어간다.
 */
public fun ExperienceEditorState.withTechTagCommitted(): ExperienceEditorState {
    if (!ExperienceEditorRules.hasTechTags(type)) return this
    val tag = ExperienceEditorRules.normalizeTechTag(techInput)
    return when {
        tag.isEmpty() -> {
            copy(techInput = "", techInputError = null)
        }

        tag.length > MAX_EXPERIENCE_TECH_TAG_LENGTH -> {
            copy(techInputError = ProfileFieldViolation.TooLong(MAX_EXPERIENCE_TECH_TAG_LENGTH))
        }

        techs.any { it.equals(tag, ignoreCase = true) } -> {
            copy(techInput = "", techInputError = null)
        }

        techs.size >= MAX_EXPERIENCE_TECH_TAGS -> {
            copy(techInputError = ProfileFieldViolation.OutOfRange)
        }

        else -> {
            copy(techInput = "", techs = techs + tag, techInputError = null)
        }
    }
}

/** 시트 입력을 [ExperienceEditorRules] 로 검증해 필드 오류를 채운 사본을 돌려준다. */
public fun validateExperienceEditor(editor: ExperienceEditorState): ExperienceEditorState {
    val type = editor.type
    val start = ExperienceEditorRules.parseYearMonthPoint(editor.startDate)
    val end = ExperienceEditorRules.parseYearMonthPoint(editor.endDate)
    val titleError =
        when {
            editor.title.isBlank() -> {
                ProfileFieldViolation.Required
            }

            editor.title.trim().length > ExperienceEditorRules.MAX_TITLE_LENGTH -> {
                ProfileFieldViolation.TooLong(
                    ExperienceEditorRules.MAX_TITLE_LENGTH,
                )
            }

            else -> {
                null
            }
        }
    val startDateError =
        when {
            editor.startDate.isBlank() -> if (ExperienceEditorRules.isStartDateRequired(type)) ProfileFieldViolation.Required else null

            // 받는 형식은 유형마다 다르다 — 수상은 연도(`YYYY`)다.
            !ExperienceEditorRules.isValidDateInput(type, editor.startDate) -> ProfileFieldViolation.InvalidFormat

            else -> null
        }
    val endDateError =
        when {
            !ExperienceEditorRules.hasPeriod(type) || editor.endDate.isBlank() -> null
            end == null -> ProfileFieldViolation.InvalidFormat
            start != null && end.isBefore(start) -> ProfileFieldViolation.OutOfRange
            else -> null
        }
    val primaryError = validateOptionalText(editor.primary, required = ExperienceEditorRules.isPrimaryRequired(type))
    val secondaryError =
        if (ExperienceEditorRules.hasSecondary(type)) {
            validateOptionalText(editor.secondary, required = ExperienceEditorRules.isSecondaryRequired(type))
        } else {
            null
        }
    // 상세는 전부 선택 입력이다 — 비어 있는 것은 오류가 아니고, 그 유형이 안 쓰는 값은 아예 보지 않는다.
    val linkError =
        when {
            !ExperienceEditorRules.hasLink(type) || editor.link.isBlank() -> {
                null
            }

            editor.link.trim().length > MAX_EXPERIENCE_LINK_LENGTH -> {
                ProfileFieldViolation.TooLong(MAX_EXPERIENCE_LINK_LENGTH)
            }

            !isAllowedExperienceLink(editor.link) -> {
                ProfileFieldViolation.InvalidFormat
            }

            else -> {
                null
            }
        }
    val detailError =
        if (ExperienceEditorRules.hasDetail(type)) validateOptionalText(editor.detail, required = false) else null
    return editor.copy(
        titleError = titleError,
        startDateError = startDateError,
        endDateError = endDateError,
        primaryError = primaryError,
        secondaryError = secondaryError,
        linkError = linkError,
        detailError = detailError,
        // 접힌 영역의 오류로 제출이 막히면 사용자에게는 「버튼이 안 먹는다」로만 보인다 — 오류가 나면 펼친다.
        isDetailExpanded =
            editor.isDetailExpanded || editor.techInputError != null || linkError != null || detailError != null,
    )
}

private fun validateOptionalText(
    value: String,
    required: Boolean,
): ProfileFieldViolation? =
    when {
        value.isBlank() -> if (required) ProfileFieldViolation.Required else null
        value.trim().length > ExperienceEditorRules.MAX_TEXT_LENGTH -> ProfileFieldViolation.TooLong(ExperienceEditorRules.MAX_TEXT_LENGTH)
        else -> null
    }

/**
 * 검증을 통과한 시트 입력을 [ExperienceDraft] 로 옮긴다 — 유형별 필드 의미는 [ExperienceEditorRules] 표를 따른다.
 *
 * 표에 없는 값은 **읽지 않는다**. 유형을 바꿔도 시트는 이전 유형에 친 글을 지우지 않으므로(칩을 잘못 눌렀다
 * 돌아온 사용자를 위해), 새 유형이 쓰지 않는 값이 서버로 새지 않는 것은 여기서 보장한다.
 *
 * ### 없는 값을 만들지 않는다 (#166 · #207)
 * 시트를 열었다 저장만 해도 없던 값이 생기면 안 된다. 걸리는 곳은 **시점 한 칸이 유형마다 다른 정밀도로 가는**
 * 수상·자격증이었다 — 연도 `2025` 를 `2025-01-01` 로, 취득 연월 `2025-06` 을 `2025-06-01` 로 넓혀 실었다.
 * 이제 넓히는 길이 아예 없다 — 수상 칸은 [ExperienceEditorRules.parseYearPoint] 로 연 정밀도 시점이 되고,
 * 모델이 그보다 자세한 시점을 수상 카드에 담지 않는다(`ExperienceType.maxPointPrecision`).
 *
 * 반대 방향인 좁히기(`YYYY.MM` → 연도)는 그대로 한다 — 예전 카드가 남긴 일자에서 연도를 읽는 것은 새 정보를
 * 만들지 않는다.
 *
 * ### 있던 값도 바꾸지 않는다 (#171)
 * #166 이 막은 것은 「없던 값이 생긴다」였고, 남아 있던 것은 그 반대편인 **「있던 값이 바뀐다」**였다 — 시점 칸이
 * 월 정밀도라 `2025-06-15` 짜리 카드를 열었다 저장만 해도 시작일이 `2025-06-01` 로 깎였다. 그래서 사용자가 그
 * 칸의 달을 바꾸지 않았으면 원본 시점을 되돌린다([ExperienceEditorRules.resolvePoint]). 이 칸은 일을 표현할
 * 수단이 없으므로, 달이 같다는 것은 「일에 대해 아무 말도 하지 않았다」는 뜻이다.
 *
 * ### 지켜 낸 일과 새로 친 달이 어긋나는 경우
 * 6월 20일 시작을 그대로 두고 종료만 6월로 당기면 「6월 20일 ~ 6월」이 된다. 예전에는 이것을 거꾸로 된 기간으로
 * 보고 지켜 낸 일을 버렸지만, 지금은 모델이 **두 시점을 더 굵은 쪽 정밀도로 견주므로**(`ExperiencePoint.isBefore`)
 * 그대로 성립한다 — 종료가 말한 것은 달까지뿐이라 20일보다 앞선다고 단정할 근거가 없다.
 */
public fun ExperienceEditorState.toDraft(): ExperienceDraft {
    val trimmedTitle = title.trim()
    val start = resolveStartPoint()
    val end = if (ExperienceEditorRules.hasPeriod(type)) ExperienceEditorRules.resolvePoint(endDate, endDateOrigin) else null
    val primaryText = primary.trim().ifEmpty { null }
    val secondaryText = secondary.trim().ifEmpty { null }
    val linkText = if (ExperienceEditorRules.hasLink(type)) link.trim().ifEmpty { null } else null
    val detailText = if (ExperienceEditorRules.hasDetail(type)) detail.trim().ifEmpty { null } else null
    // 모델이 「공백 없음·중복 없음」을 require 로 지킨다 — 여기서 한 번 더 거른다.
    val techTags =
        if (ExperienceEditorRules.hasTechTags(type)) {
            techs.map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase)
        } else {
            emptyList()
        }
    val details =
        when (type) {
            ExperienceType.Project -> {
                ExperienceDetails.Project(role = primaryText, techs = techTags, summary = secondaryText, link = linkText)
            }

            ExperienceType.Award -> {
                ExperienceDetails.Award(
                    contestName = trimmedTitle,
                    rank = requireNotNull(primaryText) { "award rank is required" },
                    organizer = secondaryText,
                )
            }

            ExperienceType.Intern -> {
                ExperienceDetails.Intern(
                    company = requireNotNull(primaryText) { "intern company is required" },
                    role = requireNotNull(secondaryText) { "intern role is required" },
                    summary = detailText,
                )
            }

            ExperienceType.Activity -> {
                ExperienceDetails.Activity(
                    organization = requireNotNull(primaryText) { "activity organization is required" },
                    role = detailText,
                    summary = secondaryText,
                )
            }

            ExperienceType.Certificate -> {
                ExperienceDetails.Certificate(issuer = primaryText)
            }
        }
    return ExperienceDraft(
        title = trimmedTitle,
        startPoint = start,
        endPoint = end,
        details = details,
    )
}

/**
 * 시점 칸의 글을 그 유형이 담을 수 있는 정밀도의 시점으로 읽는다.
 *
 * 수상만 칸 형식이 `YYYY` 라 따로 읽는다 — 나머지는 `YYYY.MM` 이고, 칸이 담지 못하는 정밀도는 원본에서
 * 되돌린다([ExperienceEditorRules.resolvePoint]).
 */
private fun ExperienceEditorState.resolveStartPoint(): ExperiencePoint? =
    if (type == ExperienceType.Award) {
        ExperienceEditorRules.parseYearPoint(startDate)
    } else {
        ExperienceEditorRules.resolvePoint(startDate, startDateOrigin)
    }

/**
 * 시트 입력 하나를 상태에 반영한다 — **순수 전이**라 저장소를 부르지 않는다.
 *
 * [ExperienceQuickAddEvent.Submitted]·[ExperienceQuickAddEvent.Dismissed] 는 여기서 다루지 않는다. 그 둘은
 * 상태 전이가 아니라 「저장한다」·「닫는다」는 부수효과라, 무엇을 부를지는 화면을 가진 ViewModel 이 정한다.
 *
 * 유형을 바꾸는 규칙이 여기 있는 이유는 그것이 **값의 뜻**을 바꾸기 때문이다 — 수정 중에는 바꾸지 않고,
 * 신규 등록에서 바꿔도 이미 친 값은 지우지 않는다(새 유형이 쓰지 않는 값은 [toDraft] 가 읽지 않아 저장에서
 * 빠지므로, 잘못 누른 칩을 되돌린 사용자만 이득을 본다). 오류 표시만 새 유형 기준으로 다시 계산하도록 비운다.
 */
public fun ExperienceEditorState.applying(event: ExperienceQuickAddEvent): ExperienceEditorState =
    when (event) {
        is ExperienceQuickAddEvent.TypeSelected -> {
            if (isEditing) {
                this
            } else {
                copy(
                    type = event.type,
                    startDateError = null,
                    endDateError = null,
                    primaryError = null,
                    secondaryError = null,
                    techInputError = null,
                    linkError = null,
                    detailError = null,
                )
            }
        }

        is ExperienceQuickAddEvent.TitleChanged -> {
            copy(title = event.value, titleError = null)
        }

        is ExperienceQuickAddEvent.StartDateChanged -> {
            copy(startDate = event.value, startDateError = null)
        }

        is ExperienceQuickAddEvent.EndDateChanged -> {
            copy(endDate = event.value, endDateError = null)
        }

        is ExperienceQuickAddEvent.PrimaryChanged -> {
            copy(primary = event.value, primaryError = null)
        }

        is ExperienceQuickAddEvent.SecondaryChanged -> {
            copy(secondary = event.value, secondaryError = null)
        }

        is ExperienceQuickAddEvent.TechInputChanged -> {
            copy(techInput = event.value, techInputError = null)
        }

        ExperienceQuickAddEvent.TechTagSubmitted -> {
            withTechTagCommitted()
        }

        is ExperienceQuickAddEvent.TechTagRemoved -> {
            copy(techs = techs.filterNot { it == event.tag }, techInputError = null)
        }

        is ExperienceQuickAddEvent.LinkChanged -> {
            copy(link = event.value, linkError = null)
        }

        is ExperienceQuickAddEvent.DetailChanged -> {
            copy(detail = event.value, detailError = null)
        }

        ExperienceQuickAddEvent.DetailSectionToggled -> {
            copy(isDetailExpanded = !isDetailExpanded)
        }

        // 부수효과라 여기서 다루지 않는다 — 호출부가 저장·닫기를 결정한다.
        ExperienceQuickAddEvent.Submitted, ExperienceQuickAddEvent.Dismissed -> {
            this
        }
    }

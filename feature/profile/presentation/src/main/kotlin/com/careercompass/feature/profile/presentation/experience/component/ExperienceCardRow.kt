package com.careercompass.feature.profile.presentation.experience.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.ui.component.CareerCompassBadge
import com.careercompass.core.ui.component.CareerCompassBadgeTone
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R

/**
 * 목록의 카드 한 장 — 유형 배지 · 제목 · 기간, 그리고 **유형마다 다른 부제**.
 *
 * 부제를 유형별로 가르는 이유는 그 유형에서 제목 다음으로 사람이 찾는 것이 다르기 때문이다. 프로젝트는
 * 「내가 무엇을 했는가」(역할), 인턴은 「어디서」(회사), 수상은 「누가 주었는가」(주관 기관)다. 한 필드로
 * 통일하면 어느 유형에서는 늘 비어 있는 줄이 된다.
 *
 * 부제가 없는 카드(역할을 안 적은 프로젝트 등)는 줄을 그리지 않는다 — 빈 줄로 높이를 맞추지 않는다.
 */
@Composable
internal fun ExperienceCardRow(
    card: Experience,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    CareerCompassCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CareerCompassBadge(label = stringResource(card.type.labelRes()), tone = CareerCompassBadgeTone.Brand)
            // 삭제는 카드 열기와 다른 일이라 카드 안에서도 제 버튼을 갖는다 — 열어야만 지울 수 있으면
            // 잘못 담은 카드를 치우는 데 화면 하나를 더 지난다.
            CareerCompassButton(
                text = stringResource(R.string.profile_experience_delete),
                onClick = onDeleteClick,
                variant = CareerCompassButtonVariant.Ghost,
                size = CareerCompassButtonSize.Small,
            )
        }
        Spacer(modifier = Modifier.height(spacing.xxSmall))
        Text(
            text = card.title,
            style = CareerCompassTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle()?.let { subtitle ->
            Text(
                text = subtitle,
                style = CareerCompassTheme.typography.caption,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = card.periodText(),
            style = CareerCompassTheme.typography.caption,
            color = colors.mutedContent,
        )
    }
}

/** 유형별로 제목 다음에 볼 것. 없으면 null 이고 줄을 그리지 않는다. */
private fun Experience.subtitle(): String? =
    when (val details = details) {
        is ExperienceDetails.Project -> details.role
        is ExperienceDetails.Award -> details.organizer ?: details.rank
        is ExperienceDetails.Intern -> details.company
        is ExperienceDetails.Activity -> details.organization
        is ExperienceDetails.Certificate -> details.issuer
    }

/**
 * 기간 표기 — 시점이 아는 만큼만 적는다.
 *
 * 연도만 아는 수상 카드에 `2025.01` 을 적으면 사용자가 준 적 없는 달이 사실처럼 보인다(#166). 그래서
 * [ExperiencePoint] 의 정밀도를 그대로 문자열로 옮긴다. 기간이 있는 유형인데 종료가 없으면 「진행 중」이다.
 */
@Composable
private fun Experience.periodText(): String {
    val start = startPoint ?: return stringResource(R.string.profile_experience_period_unknown)
    val end = endPoint
    return when {
        end != null -> stringResource(R.string.profile_experience_period_range, start.text(), end.text())
        type.hasPeriod -> stringResource(R.string.profile_experience_period_open, start.text())
        else -> start.text()
    }
}

private fun ExperiencePoint.text(): String =
    when (this) {
        is ExperiencePoint.Year -> "%04d".format(year)
        is ExperiencePoint.YearMonth -> "%04d.%02d".format(year, month)
        is ExperiencePoint.Date -> "%04d.%02d.%02d".format(year, month, day)
    }

@StringRes
internal fun ExperienceType.labelRes(): Int =
    when (this) {
        ExperienceType.Project -> R.string.profile_experience_filter_project
        ExperienceType.Award -> R.string.profile_experience_filter_award
        ExperienceType.Intern -> R.string.profile_experience_filter_intern
        ExperienceType.Activity -> R.string.profile_experience_filter_activity
        ExperienceType.Certificate -> R.string.profile_experience_filter_cert
    }

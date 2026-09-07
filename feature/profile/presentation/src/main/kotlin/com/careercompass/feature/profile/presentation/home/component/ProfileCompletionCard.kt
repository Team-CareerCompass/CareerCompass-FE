package com.careercompass.feature.profile.presentation.home.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.domain.model.ProfileCompletionGap
import com.careercompass.feature.profile.presentation.R

/** 안내 한 줄에 이름 대는 빈 칸의 개수. 셋을 넘기면 문장이 길어져 무엇부터 할지 되레 흐려진다. */
private const val MAX_NAMED_GAPS = 2

/** 게이지 높이 — 시안(Figma 05 · 01)의 6dp 막대. */
private val GAUGE_HEIGHT = 6.dp

/**
 * 프로필 완성도 카드 — 퍼센트 게이지와 「무엇을 채우면 되는지」 한 줄.
 *
 * ### 안내 문구를 우리가 짓는 이유
 * 시안의 문장(「어학 점수와 인턴 경험을 추가하면…」)은 API_SPEC v0.1 §2 에 없는 항목을 말한다. 서버가 주는
 * 것은 완성도 숫자 하나(`completion`)뿐이라, 사용자가 **다음에 무엇을 할지**는 우리가 아는 빈 칸으로만
 * 말할 수 있다([ProfileCompletionGap]). 빈 칸이 없으면 숫자 대신 「다 채웠다」를 적는다 — 100% 가 아니어도
 * 우리가 더 시킬 일이 없으면 그렇게 말하는 것이 맞다(완성도 산식은 서버 것이다).
 */
@Composable
internal fun ProfileCompletionCard(
    completion: Int,
    gaps: List<ProfileCompletionGap>,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val percentLabel = stringResource(R.string.profile_home_completion_percent, completion)

    CareerCompassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.profile_home_completion_title),
                style = CareerCompassTheme.typography.labelMedium,
                color = colors.onSurface,
            )
            Text(
                text = percentLabel,
                style = CareerCompassTheme.typography.labelMedium,
                color = colors.primaryEmphasis,
            )
        }
        Spacer(modifier = Modifier.height(spacing.small))
        // 값은 바로 위 줄이 퍼센트로 읽어 준다 — 게이지가 같은 값을 한 번 더 읽으면 두 번 들린다.
        LinearProgressIndicator(
            progress = { completion / 100f },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GAUGE_HEIGHT)
                    .clearAndSetSemantics { },
            color = colors.primaryEmphasis,
            trackColor = colors.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = gaps.hintText(),
            style = CareerCompassTheme.typography.caption,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun List<ProfileCompletionGap>.hintText(): String {
    if (isEmpty()) return stringResource(R.string.profile_home_completion_done)
    val separator = stringResource(R.string.profile_home_completion_hint_separator)
    // joinToString 은 inline 이 아니라 그 람다에서 stringResource 를 부를 수 없다 — 먼저 map 으로 푼다.
    val named = take(MAX_NAMED_GAPS).map { stringResource(it.labelRes()) }.joinToString(separator)
    return stringResource(R.string.profile_home_completion_hint, named)
}

@StringRes
private fun ProfileCompletionGap.labelRes(): Int =
    when (this) {
        ProfileCompletionGap.Name -> R.string.profile_home_gap_name
        ProfileCompletionGap.School -> R.string.profile_home_gap_school
        ProfileCompletionGap.Department -> R.string.profile_home_gap_department
        ProfileCompletionGap.Gpa -> R.string.profile_home_gap_gpa
        ProfileCompletionGap.GradYear -> R.string.profile_home_gap_grad_year
        ProfileCompletionGap.JobInterests -> R.string.profile_home_gap_job_interests
        ProfileCompletionGap.Tags -> R.string.profile_home_gap_tags
        ProfileCompletionGap.ExperienceCards -> R.string.profile_home_gap_experience_cards
    }

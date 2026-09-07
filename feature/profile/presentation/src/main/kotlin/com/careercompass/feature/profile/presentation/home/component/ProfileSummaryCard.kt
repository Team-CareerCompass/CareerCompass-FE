package com.careercompass.feature.profile.presentation.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R

/**
 * 마이 홈 맨 위의 프로필 요약 — 이름·소속·졸업 연도와 등록 개수 둘(Figma 05 · 01 의 어두운 카드).
 *
 * ### 시안의 「최고 적합도」를 빼 둔 이유
 * 시안은 개수 셋(경험 카드 · 과거 자소서 · 최고 적합도)을 나란히 두지만, 적합도는 공고에 붙는 값이고
 * API_SPEC v0.1 §2 의 프로필에도 §3·§4 의 목록에도 「내 최고 점수」를 주는 자리가 없다. 화면이 만들어 낼
 * 수 없는 값이라 칸을 비워 두는 대신 아예 그리지 않는다 — 정본 우선순위는 API_SPEC 이 시안보다 위다
 * (`docs/spec/canon.md`).
 *
 * 개수를 **모르는** 경우([experienceCardCount] 가 null)에는 자리는 두고 값만 「—」로 그린다. 0 으로 적으면
 * 「하나도 없다」는 거짓이 되고, 줄째 빼면 개수를 못 받은 사이 카드 높이가 흔들린다.
 */
@Composable
internal fun ProfileSummaryCard(
    profile: UserProfile,
    experienceCardCount: Int?,
    pastApplicationCount: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CareerCompassTheme.shapes.card,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
    ) {
        Column(
            modifier = Modifier.padding(spacing.xLarge),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(name = profile.name)
                Spacer(modifier = Modifier.width(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name ?: stringResource(R.string.profile_home_name_unknown),
                        style = CareerCompassTheme.typography.headline2,
                        color = colors.inverseOnSurface,
                    )
                    Text(
                        text = profile.affiliationLabel(),
                        style = CareerCompassTheme.typography.caption,
                        color = colors.inverseOnSurface,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.large)) {
                ProfileSummaryStat(
                    value = experienceCardCount,
                    label = stringResource(R.string.profile_home_stat_experience_cards),
                    modifier = Modifier.weight(1f),
                )
                ProfileSummaryStat(
                    value = pastApplicationCount,
                    label = stringResource(R.string.profile_home_stat_past_applications),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 이름 첫 글자를 딴 원형 표식. 이름을 모르면 글자 없이 원만 남긴다.
 *
 * 접근성 트리에서는 지운다 — 바로 옆 줄이 같은 이름을 온전히 읽어 주므로, 첫 글자만 한 번 더 읽으면
 * 「정, 정일혁」처럼 들린다.
 */
@Composable
private fun ProfileAvatar(name: String?) {
    val colors = CareerCompassTheme.colors
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .background(color = colors.primary, shape = CircleShape)
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val initial = name?.trim()?.take(1).orEmpty()
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = CareerCompassTheme.typography.headline2,
                color = colors.onPrimary,
            )
        }
    }
}

@Composable
private fun ProfileSummaryStat(
    value: Int?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value?.toString() ?: stringResource(R.string.profile_home_stat_unknown),
            style = CareerCompassTheme.typography.headline2,
            color = colors.inverseOnSurface,
            textAlign = TextAlign.Start,
        )
        Text(
            text = label,
            style = CareerCompassTheme.typography.caption,
            color = colors.inverseOnSurface,
        )
    }
}

/** 학교·학과·졸업 연도 중 아는 것만 잇는다. 하나도 모르면 대체 문구를 쓴다. */
@Composable
private fun UserProfile.affiliationLabel(): String {
    val gradYearLabel = gradYear?.let { stringResource(R.string.profile_home_grad_year, it) }
    val parts = listOfNotNull(school, department, gradYearLabel)
    return if (parts.isEmpty()) stringResource(R.string.profile_home_affiliation_unknown) else parts.joinToString(AFFILIATION_SEPARATOR)
}

private const val AFFILIATION_SEPARATOR = " · "

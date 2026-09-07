package com.careercompass.feature.profile.presentation.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.careercompass.core.ui.component.CareerCompassBadge
import com.careercompass.core.ui.component.CareerCompassBadgeTone
import com.careercompass.core.ui.component.CareerCompassCard
import com.careercompass.core.ui.theme.CareerCompassTheme

/**
 * 마이 홈 메뉴 한 줄 — 제목·부제와, 개수를 아는 항목에만 붙는 배지.
 *
 * 카드 전체가 버튼이라 손가락이 어디에 닿아도 열린다([CareerCompassCard] 의 `onClick`). 시안의 「›」 글리프는
 * 그리지 않는다 — 화살표는 장식이고 카드가 이미 버튼 시맨틱을 갖는데, 텍스트로 넣으면 스크린 리더가
 * 「오른쪽 홑화살괄호」를 읽는다.
 *
 * @param count 배지에 적을 개수. **null 이면 배지를 그리지 않는다** — 못 세었다는 뜻이라 0 으로 적으면 거짓이다.
 */
@Composable
internal fun ProfileMenuRow(
    title: String,
    description: String,
    count: Int?,
    onClick: () -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CareerCompassTheme.typography.labelMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = description,
                    style = CareerCompassTheme.typography.caption,
                    color = colors.onSurfaceVariant,
                )
            }
            if (count != null) {
                Spacer(modifier = Modifier.width(spacing.small))
                CareerCompassBadge(label = count.toString(), tone = CareerCompassBadgeTone.Neutral)
            }
        }
    }
}

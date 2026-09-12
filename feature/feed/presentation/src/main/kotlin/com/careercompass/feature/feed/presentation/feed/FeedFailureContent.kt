package com.careercompass.feature.feed.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassNetworkErrorState
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.presentation.R
import com.careercompass.feature.feed.presentation.shared.component.FeedMaintenanceState
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.failureKind

/**
 * 목록을 못 받았을 때 사유별로 갈리는 화면 — [FeedScreen] 가 상태 없이 그리는 부분만 떼어 냈다.
 *
 * 이 화면은 [FeedContent][com.careercompass.feature.feed.presentation.FeedContent] 을 **통째로 대신한다** —
 * 헤더의 검색칸·필터·정렬·칩이 함께 사라진다. 그래서 조건 때문에 실패하면 그 조건을 되돌릴 조작이 화면에
 * 하나도 남지 않았고, 「새로고침」은 같은 조건을 그대로 다시 보내 같은 실패를 되풀이했다(#144).
 * [onResetQueryClick] 이 그 막다른 골목의 출구다.
 *
 * @param onOfflineClick 저장해 둔 스냅샷이 있을 때만 넘긴다. `null` 이면 눌러도 보여 줄 것이 없으므로
 *  「오프라인 모드로 보기」를 아예 그리지 않는다. 점검 중에도 스냅샷은 유효하니 같은 길을 열어 둔다.
 * @param onResetQueryClick 조건을 지우고 다시 읽는 길. **되돌릴 조건이 실제로 걸려 있고 그 실패가 조건
 *  탓일 여지가 있을 때만** 넘긴다(`FeedViewState.canResetFailedQuery`); `null` 이면 그리지 않는다 —
 *  연결이 끊긴 사람에게 조건을 지우라고 하는 것은 엉뚱한 처방이고, 기본 조회가 실패한 자리에서는 눌러도
 *  같은 요청이 나간다.
 */
@Composable
internal fun FeedFailureContent(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit,
    onOfflineClick: (() -> Unit)?,
    onResetQueryClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // 조건 초기화를 안 여는 경우에는 사유 화면을 감싸지 않고 그대로 그린다 — 사유 화면 자체가
    // fillMaxSize 로 자리를 잡으므로, 쓰지 않는 Column 을 한 겹 두르면 골든만 흔들린다.
    if (onResetQueryClick == null) {
        FeedFailureNotice(
            reason = reason,
            onRetryClick = onRetryClick,
            onOfflineClick = onOfflineClick,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        FeedFailureNotice(
            reason = reason,
            onRetryClick = onRetryClick,
            onOfflineClick = onOfflineClick,
            modifier = Modifier.weight(1f),
        )
        FeedQueryResetAction(onClick = onResetQueryClick)
    }
}

/** 사유별 안내 본문 — 세 화면 모두 코어의 상태 화면을 그대로 쓴다(Figma 09 Edge Cases). */
@Composable
private fun FeedFailureNotice(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit,
    onOfflineClick: (() -> Unit)?,
    modifier: Modifier,
) {
    when (reason) {
        FeedFailureReason.NetworkUnavailable -> {
            CareerCompassNetworkErrorState(
                onRetryClick = onRetryClick,
                onOfflineClick = onOfflineClick,
                modifier = modifier,
            )
        }

        FeedFailureReason.Maintenance -> {
            FeedMaintenanceState(
                onRetryClick = onRetryClick,
                onOfflineClick = onOfflineClick,
                modifier = modifier,
            )
        }

        is FeedFailureReason.Generic -> {
            // 문구는 화면이 짓지 않고 실패 표에서 읽는다(#204). 「공고」라는 명사는 문맥이 채우고, 버튼은 표의
            // 행동이 있을 때만 붙는다. 실패 전용 부품이라 「검색 결과 없음」과 삽화부터 갈린다(#222).
            // 이 화면이 열 수 있는 길은 재조회뿐이라, 표가 다른 행동(프로필 입력·정리하기)을 가리키면
            // 콜백을 넘기지 않는다. 넘기면 「프로필 입력하기」라고 적힌 버튼이 재조회를 하게 된다(#342).
            val display = reason.failureKind.display(FailureSurface.Posting)
            CareerCompassFailureState(
                display = display,
                onActionClick = onRetryClick.takeIf { display.isRetryable },
                modifier = modifier,
            )
        }
    }
}

/**
 * 사유 화면 아래에 붙는 탈출구 — 한 줄로 「왜 이걸 권하는가」를 밝히고 버튼 하나를 준다.
 *
 * 사유 화면의 행동(새로고침·오프라인 모드)과 섞지 않고 아래에 따로 세운다. 저 둘은 「지금 조건 그대로」
 * 하는 일이고 이것만 조건을 바꾸는 일이라, 한 줄에 나란히 두면 무엇이 무엇을 바꾸는지가 흐려진다.
 * 안내 문구를 함께 두는 이유도 같다 — 버튼만 있으면 사용자는 무엇이 지워지는지 모른 채 누른다.
 */
@Composable
private fun FeedQueryResetAction(onClick: () -> Unit) {
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CareerCompassTheme.colors.subtleSurface)
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = stringResource(R.string.feed_failure_query_reset_notice),
            color = CareerCompassTheme.colors.mutedContent,
            textAlign = TextAlign.Center,
            style = CareerCompassTheme.typography.caption,
        )
        CareerCompassButton(
            text = stringResource(R.string.feed_failure_query_reset_action),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            size = CareerCompassButtonSize.Large,
        )
    }
}

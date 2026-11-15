package com.careercompass.feature.feed.presentation.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careercompass.core.ui.component.CareerCompassBadge
import com.careercompass.core.ui.component.CareerCompassBadgeTone
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.presentation.R
import com.careercompass.feature.feed.presentation.shared.component.FeedCard
import com.careercompass.feature.feed.presentation.shared.component.FeedLoadingContent
import com.careercompass.feature.feed.presentation.shared.component.FeedTopBar

/** Stateless list of registered boards with per-board activation, retry, and delete actions (spec F2-1/F2-2). */
@Composable
internal fun BoardListContent(
    state: BoardListUiState,
    onEvent: (BoardListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(CareerCompassTheme.colors.subtleSurface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        FeedTopBar(
            title = stringResource(R.string.feed_board_list_title),
            onBackClick = { onEvent(BoardListEvent.BackClicked) },
            actions = {
                CareerCompassButton(
                    text = stringResource(R.string.feed_board_list_add),
                    onClick = { onEvent(BoardListEvent.AddBoardClicked) },
                    variant = CareerCompassButtonVariant.Ghost,
                    size = CareerCompassButtonSize.Small,
                    contentDescription = stringResource(R.string.feed_board_list_add_content_description),
                )
            },
        )
        when (val content = state.content) {
            BoardListContentState.Loading -> {
                FeedLoadingContent(
                    message = stringResource(R.string.feed_board_list_loading),
                    modifier = Modifier.weight(1f),
                )
            }

            BoardListContentState.Empty -> {
                BoardListEmpty(
                    onAddBoardClick = { onEvent(BoardListEvent.AddBoardClicked) },
                    modifier = Modifier.weight(1f),
                )
            }

            is BoardListContentState.Loaded -> {
                BoardList(
                    boards = content.boards,
                    maxBoardCount = state.maxBoardCount,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BoardList(
    boards: List<BoardUiModel>,
    maxBoardCount: Int,
    onEvent: (BoardListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CareerCompassTheme.spacing

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = spacing.large,
                end = spacing.large,
                bottom = spacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "count") {
            Text(
                text = stringResource(R.string.feed_board_list_count, boards.size, maxBoardCount),
                modifier = Modifier.padding(vertical = spacing.xSmall),
                color = CareerCompassTheme.colors.onSurfaceVariant,
                style =
                    CareerCompassTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }
        items(items = boards, key = BoardUiModel::id) { board ->
            BoardCard(board = board, onEvent = onEvent)
        }
    }
}

@Composable
private fun BoardCard(
    board: BoardUiModel,
    onEvent: (BoardListEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val toggleDescription = stringResource(R.string.feed_board_toggle_content_description, board.name)

    FeedCard(onClick = { onEvent(BoardListEvent.BoardSelected(board.id)) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = board.name,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = CareerCompassTheme.typography.headline4,
                )
                Text(
                    text = board.url,
                    color = colors.mutedContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        CareerCompassTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Switch(
                checked = board.isActive,
                onCheckedChange = { onEvent(BoardListEvent.BoardToggled(board.id)) },
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            CareerCompassBadge(
                label = board.typeLabel,
                tone = board.type.badgeTone(),
            )
            BoardStatusBadge(status = board.status, failCount = board.failCount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                text =
                    board.lastCollectedLabel?.let { label ->
                        stringResource(R.string.feed_board_last_collected, label)
                    } ?: stringResource(R.string.feed_board_never_collected),
                color = colors.onSurfaceVariant,
                style = CareerCompassTheme.typography.caption,
            )
            board.postingCount?.let { postingCount ->
                Text(
                    text = stringResource(R.string.feed_board_posting_count, postingCount),
                    color = colors.onSurfaceVariant,
                    style = CareerCompassTheme.typography.caption,
                )
            }
        }
        if (board.status == BoardStatus.Deactivated) {
            BoardDeactivatedNotice(failCount = board.failCount)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            if (board.status == BoardStatus.Failing || board.status == BoardStatus.Deactivated) {
                CareerCompassButton(
                    text = stringResource(R.string.feed_board_retry),
                    onClick = { onEvent(BoardListEvent.RetryClicked(board.id)) },
                    // 서버가 끈 게시판에서는 재시도가 권하는 행동이라 강조 버튼으로 낸다.
                    variant =
                        if (board.status == BoardStatus.Deactivated) {
                            CareerCompassButtonVariant.Primary
                        } else {
                            CareerCompassButtonVariant.Secondary
                        },
                    size = CareerCompassButtonSize.Small,
                    // 요청이 오가는 동안 잠근다. 살려 두면 연타한 횟수만큼 재시도가 나간다(#341).
                    enabled = !board.isRetrying,
                    contentDescription = stringResource(R.string.feed_board_retry_content_description, board.name),
                )
            }
            CareerCompassButton(
                text = stringResource(R.string.feed_board_delete),
                onClick = { onEvent(BoardListEvent.DeleteClicked(board.id)) },
                variant = CareerCompassButtonVariant.Danger,
                size = CareerCompassButtonSize.Small,
                contentDescription = stringResource(R.string.feed_board_delete_content_description, board.name),
            )
        }
    }
}

@Composable
private fun BoardStatusBadge(
    status: BoardStatus,
    failCount: Int,
) {
    when (status) {
        BoardStatus.Active -> {
            CareerCompassBadge(
                label = stringResource(R.string.feed_board_status_active),
                tone = CareerCompassBadgeTone.Brand,
            )
        }

        BoardStatus.Paused -> {
            CareerCompassBadge(
                label = stringResource(R.string.feed_board_status_paused),
                tone = CareerCompassBadgeTone.Neutral,
            )
        }

        BoardStatus.Failing -> {
            CareerCompassBadge(
                label = stringResource(R.string.feed_board_status_failing, failCount),
                tone = CareerCompassBadgeTone.Error,
            )
        }

        BoardStatus.Deactivated -> {
            CareerCompassBadge(
                label = stringResource(R.string.feed_board_status_deactivated),
                tone = CareerCompassBadgeTone.Error,
            )
        }
    }
}

/**
 * 연속 실패로 서버가 끈 게시판에만 붙는 안내 — 왜 꺼졌는지와 무엇을 하면 되는지를 카드 안에서 끝낸다.
 *
 * 배지만으로는 사용자가 끈 「일시중지」와 구분되지 않는다. 첫 줄이 이유와 다음 행동(재시도·주소 확인·삭제)을,
 * 둘째 줄이 토글과 재시도의 차이와 어느 쪽을 권하는지를 말한다.
 */
@Composable
private fun BoardDeactivatedNotice(failCount: Int) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.errorContainer, CareerCompassTheme.shapes.largeControl)
                .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.xxSmall),
    ) {
        Text(
            text = stringResource(R.string.feed_board_deactivated_reason, failCount),
            color = colors.onErrorContainer,
            style = CareerCompassTheme.typography.caption,
        )
        Text(
            text = stringResource(R.string.feed_board_deactivated_action_hint),
            color = colors.onErrorContainer,
            style = CareerCompassTheme.typography.caption,
        )
    }
}

@Composable
private fun BoardListEmpty(
    onAddBoardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(spacing.xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                text = stringResource(R.string.feed_icon_board_empty),
                modifier = Modifier.clearAndSetSemantics {},
                style = CareerCompassTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
            )
            Text(
                text = stringResource(R.string.feed_board_list_empty_title),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                style = CareerCompassTheme.typography.headline4,
            )
            Text(
                text = stringResource(R.string.feed_board_list_empty_description),
                color = colors.mutedContent,
                textAlign = TextAlign.Center,
                style = CareerCompassTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(spacing.small))
            CareerCompassButton(
                text = stringResource(R.string.feed_board_list_empty_action),
                onClick = onAddBoardClick,
            )
        }
    }
}

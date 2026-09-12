package com.careercompass.feature.feed.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careercompass.core.ui.component.CareerCompassBadge
import com.careercompass.core.ui.component.CareerCompassBadgeTone
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.CareerCompassEmptyState
import com.careercompass.core.ui.component.CareerCompassTag
import com.careercompass.core.ui.icon.CareerCompassIcons
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.feed.presentation.shared.component.FEED_ICON_SIZE
import com.careercompass.feature.feed.presentation.shared.component.FEED_INLINE_ICON_SIZE
import com.careercompass.feature.feed.presentation.shared.component.FeedIconButton
import com.careercompass.feature.feed.presentation.shared.component.FeedReadBadge
import com.careercompass.feature.feed.presentation.shared.component.FeedSuitabilityChip
import com.careercompass.feature.feed.presentation.shared.component.feedMetaTextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Stateless main feed matching the CareerCompass feed design. */
@Composable
public fun FeedContent(
    state: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedContent(
        state = state,
        onEvent = onEvent,
        listState = rememberLazyListState(),
        onLoadMore = null,
        loadMore = FeedLoadMoreState.Ready,
        modifier = modifier,
    )
}

/**
 * Stateless main feed with infinite-scroll hooks.
 *
 * [onLoadMore] is invoked when the last items of [listState] come into view; pass `null` when the
 * host has no further pages to offer.
 *
 * [loadMore] 는 목록 끝의 이어 읽기가 어디까지 왔는지다. 자동 트리거는 [FeedLoadMoreState.Ready] 일 때만
 * 무장하고, 나머지 상태는 목록 끝에 진행 표시·「더 찾아보기」·「다시 시도」 한 줄로 드러난다 — 자동으로
 * 가지 않는 자리를 비워 두면 목록이 끝난 것처럼 보인다.
 */
@Composable
public fun FeedContent(
    state: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit,
    listState: LazyListState,
    onLoadMore: (() -> Unit)?,
    loadMore: FeedLoadMoreState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(CareerCompassTheme.colors.subtleSurface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        FeedHeader(state = state, onEvent = onEvent)
        state.offlineNotice?.let { notice -> FeedOfflineBanner(notice = notice) }
        if (state.isProfileNoticeVisible) {
            FeedProfileNoticeBanner(onClick = { onEvent(FeedUiEvent.CompleteProfileSelected) })
        }
        FeedSortRow(state = state, onEvent = onEvent)
        when (val content = state.content) {
            FeedContentState.Loading -> {
                FeedLoading(modifier = Modifier.weight(1f))
            }

            is FeedContentState.Empty -> {
                FeedEmpty(
                    reason = content.reason,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }

            is FeedContentState.Loaded -> {
                FeedListingList(
                    listings = content.listings,
                    onEvent = onEvent,
                    listState = listState,
                    onLoadMore = onLoadMore,
                    loadMore = loadMore,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeedHeader(
    state: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing
    val greetingStyle =
        CareerCompassTheme.typography.headline4.copy(
            fontSize = 17.sp,
            lineHeight = 25.5.sp,
            letterSpacing = (-0.2).sp,
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = spacing.large, top = spacing.small, end = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = spacing.small),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.feed_greeting, state.userName),
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() },
                        color = CareerCompassTheme.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = greetingStyle,
                    )
                    // 손 흔드는 이모지는 장식이다. 한 Text 에 이어 붙이면 스크린 리더가 제목 끝에서
                    // 이모지 이름까지 읽으므로, 배너들과 같게 따로 떼어 접근성 트리에서 지운다.
                    Text(
                        text = stringResource(R.string.feed_icon_wave),
                        modifier = Modifier.clearAndSetSemantics {},
                        style = greetingStyle,
                    )
                }
                Text(
                    text =
                        stringResource(
                            R.string.feed_today_new_listing_count,
                            state.newListingCount,
                        ),
                    color = CareerCompassTheme.colors.onSurfaceVariant,
                    style =
                        CareerCompassTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                )
            }
            FeedIconButton(
                icon = CareerCompassIcons.Notifications,
                contentDescription = stringResource(R.string.feed_notification_content_description),
                onClick = { onEvent(FeedUiEvent.NotificationsSelected) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedSearchField(
                value = state.searchQuery,
                onValueChange = { onEvent(FeedUiEvent.SearchQueryChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            FeedFilterButton(
                activeFilterCount = state.activeFilterCount,
                onClick = { onEvent(FeedUiEvent.FilterRequested) },
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            state.filters.forEach { filter ->
                val selected = filter.category == state.selectedFilter
                CareerCompassTag(
                    label = filter.label,
                    selected = selected,
                    onClick = { onEvent(FeedUiEvent.FilterSelected(filter.category)) },
                    stateDescription =
                        stringResource(
                            if (selected) {
                                R.string.feed_filter_selected_state
                            } else {
                                R.string.feed_filter_unselected_state
                            },
                        ),
                    role = Role.RadioButton,
                )
            }
        }
    }
}

@Composable
private fun FeedSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val shape = CareerCompassTheme.shapes.largeControl
    val searchContentDescription =
        stringResource(R.string.feed_search_content_description)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(colors.surface)
                .border(width = 1.dp, color = colors.interactiveOutline, shape = shape)
                .semantics {
                    contentDescription = searchContentDescription
                },
        singleLine = true,
        textStyle =
            CareerCompassTheme.typography.bodyMedium.copy(
                color = colors.onSurface,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
        cursorBrush = SolidColor(colors.primaryEmphasis),
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CareerCompassIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(FEED_INLINE_ICON_SIZE),
                    tint = colors.mutedContent,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        // 큰 글꼴에서는 안내 문구가 48dp 입력칸 폭을 넘는다. 접히면 두 번째 줄이
                        // 고정 높이에 잘려 사라지므로 한 줄로 두고 말줄임으로 끝맺는다.
                        Text(
                            text = stringResource(R.string.feed_search_placeholder),
                            color = colors.mutedContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                CareerCompassTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                ),
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

/** 48dp filter trigger. The badge shows how many sheet conditions are active (spec F2-3 「필터 조건」). */
@Composable
private fun FeedFilterButton(
    activeFilterCount: Int,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val shape = CareerCompassTheme.shapes.largeControl
    val filterDescription = stringResource(R.string.feed_filter_content_description)
    val activeStateDescription =
        if (activeFilterCount > 0) {
            stringResource(R.string.feed_filter_active_count_state, activeFilterCount)
        } else {
            null
        }

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(shape)
                .background(colors.surface)
                .border(width = 1.dp, color = colors.interactiveOutline, shape = shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = filterDescription
                    role = Role.Button
                    if (activeStateDescription != null) {
                        stateDescription = activeStateDescription
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CareerCompassIcons.Filter,
            contentDescription = null,
            modifier = Modifier.size(FEED_INLINE_ICON_SIZE),
            tint = if (activeFilterCount > 0) colors.primaryEmphasis else colors.onSurface,
        )
        if (activeFilterCount > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(16.dp)
                        .background(colors.primary, CareerCompassTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeFilterCount.toString(),
                    modifier = Modifier.clearAndSetSemantics {},
                    color = colors.onPrimary,
                    maxLines = 1,
                    style =
                        CareerCompassTheme.typography.caption.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FeedSortRow(
    state: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = CareerCompassTheme.spacing.large),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_listing_count, state.totalListingCount),
            color = CareerCompassTheme.colors.onSurfaceVariant,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
        CareerCompassButton(
            text = state.selectedSort.label,
            onClick = { onEvent(FeedUiEvent.SortMenuRequested) },
            variant = CareerCompassButtonVariant.Ghost,
            size = CareerCompassButtonSize.Small,
            trailingIcon = {
                Icon(
                    imageVector = CareerCompassIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(FEED_INLINE_ICON_SIZE),
                )
            },
            contentDescription =
                stringResource(
                    R.string.feed_sort_content_description,
                    state.selectedSort.label,
                ),
        )
    }
}

/**
 * 목록과 그 끝의 이어 읽기 한 줄.
 *
 * **자동 트리거는 [FeedLoadMoreState.Ready] 일 때만 무장한다.** 멈춘 자리([FeedLoadMoreState.Paused])와
 * 실패한 자리([FeedLoadMoreState.Failed])에서도 스크롤로 다시 걸리게 두면, 바닥에 머무른 사용자에게
 * 같은 요청이 끝없이 되풀이된다 — 네트워크가 죽어 있으면 실패만 반복하고, 필터가 다 걸러 내는 구간이면
 * 걸러질 페이지만 계속 받는다. 그 두 자리에서는 이어 갈 길을 [FeedLoadMoreFooter] 의 버튼 하나로만 연다.
 */
@Composable
private fun FeedListingList(
    listings: List<FeedListingUiModel>,
    onEvent: (FeedUiEvent) -> Unit,
    listState: LazyListState,
    onLoadMore: (() -> Unit)?,
    loadMore: FeedLoadMoreState,
    modifier: Modifier = Modifier,
) {
    if (onLoadMore != null && loadMore == FeedLoadMoreState.Ready) {
        val currentOnLoadMore by rememberUpdatedState(onLoadMore)
        LaunchedEffect(listState, listings.size) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .filter { it }
                .collect { currentOnLoadMore() }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = CareerCompassTheme.spacing.large,
                end = CareerCompassTheme.spacing.large,
                bottom = CareerCompassTheme.spacing.large,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = listings, key = FeedListingUiModel::id) { listing ->
            FeedListingCard(
                listing = listing,
                onSelected = { onEvent(FeedUiEvent.ListingSelected(listing.id)) },
                onBookmarkToggled = { onEvent(FeedUiEvent.BookmarkToggled(listing.id)) },
            )
        }
        if (onLoadMore != null && loadMore != FeedLoadMoreState.Ready) {
            item(key = "load-more-footer") {
                FeedLoadMoreFooter(
                    loadMore = loadMore,
                    onRetry = { onEvent(FeedUiEvent.LoadMoreSelected) },
                )
            }
        }
    }
}

/**
 * 목록 끝 한 줄 — 이어 읽기가 지금 무엇을 하고 있는지, 사용자가 무엇을 할 수 있는지.
 *
 * [FeedLoadMoreState.Ready] 는 여기 오지 않는다(자동으로 굴러가는 중이라 할 말이 없다).
 */
@Composable
private fun FeedLoadMoreFooter(
    loadMore: FeedLoadMoreState,
    onRetry: () -> Unit,
) {
    when (loadMore) {
        FeedLoadMoreState.Ready -> {
            Unit
        }

        FeedLoadMoreState.Loading -> {
            FeedLoadingMoreRow()
        }

        FeedLoadMoreState.Paused -> {
            FeedLoadMoreActionRow(
                message = stringResource(R.string.feed_load_more_paused),
                actionText = stringResource(R.string.feed_load_more_action),
                onClick = onRetry,
            )
        }

        FeedLoadMoreState.Failed -> {
            FeedLoadMoreActionRow(
                message = stringResource(R.string.feed_load_more_failed),
                actionText = stringResource(R.string.feed_error_retry),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun FeedLoadingMoreRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = CareerCompassTheme.spacing.medium),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = CareerCompassTheme.colors.primaryEmphasis,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(CareerCompassTheme.spacing.small))
        Text(
            text = stringResource(R.string.feed_loading_more),
            color = CareerCompassTheme.colors.onSurfaceVariant,
            style = CareerCompassTheme.typography.caption,
        )
    }
}

/**
 * 멈춘 사유 한 줄과 이어 갈 버튼. 사유를 글자로 함께 내보내 버튼만 덩그러니 남지 않게 한다 — 왜 멈췄는지
 * 모르면 눌러야 할 이유도 없다.
 */
@Composable
private fun FeedLoadMoreActionRow(
    message: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = CareerCompassTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.small),
    ) {
        Text(
            text = message,
            color = CareerCompassTheme.colors.onSurfaceVariant,
            style = CareerCompassTheme.typography.caption,
        )
        CareerCompassButton(
            text = actionText,
            onClick = onClick,
            variant = CareerCompassButtonVariant.Secondary,
            size = CareerCompassButtonSize.Small,
        )
    }
}

/**
 * A reusable listing card that emits separate card and bookmark events.
 *
 * ### 무엇을 어디에 넣었나 (#140)
 *
 * 위 줄(배지·적합도 칩)에는 **아무것도 더하지 않았다.** 배지 둘과 칩이 이미 폭을 다투고 있어
 * fontScale 2.0 에서 한 조각만 더해도 출처 배지가 잘린다. 수집일과 읽음 표시는 둘 다 여유가 있는
 * 아래 메타 줄로 보내고, 그 줄을 [FlowRow] 로 바꿔 큰 글꼴에서는 잘리는 대신 **접히게** 했다 —
 * 카드가 세로로 길어질 뿐 마감일·북마크가 밀려나지 않는다.
 *
 * ### 읽음을 무엇으로 가르나
 *
 * 색·굵기만으로 가르지 않는다(색각 이상·고대비 모드). 정보를 지는 것은 **문구(「읽음」)와 형태(체크
 * 표시)** 이고, 제목의 흐린 색은 훑어볼 때를 돕는 덧칠일 뿐이다 — 적합도 축의 충족 배지(#141,
 * `SuitabilityBreakdownRow`)와 같은 원칙이다. 스크린 리더에는 카드의 `stateDescription` 으로
 * **읽음·읽지 않음을 모두** 실어, 표시가 없는 쪽(읽지 않음)도 침묵하지 않게 한다.
 */
@Composable
internal fun FeedListingCard(
    listing: FeedListingUiModel,
    onSelected: () -> Unit,
    onBookmarkToggled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val cardShape = RoundedCornerShape(16.dp)
    val readStateDescription =
        stringResource(
            if (listing.isRead) R.string.feed_listing_read_state else R.string.feed_listing_unread_state,
        )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .semantics { stateDescription = readStateDescription }
                .clickable(role = Role.Button, onClick = onSelected),
        shape = cardShape,
        color = colors.surface,
        border = BorderStroke(width = 1.dp, color = colors.surfaceVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CareerCompassBadge(
                        label = listing.categoryLabel,
                        tone = listing.category.badgeTone(),
                    )
                    CareerCompassBadge(
                        label = listing.sourceLabel,
                        tone = CareerCompassBadgeTone.Neutral,
                    )
                    if (listing.isNew) {
                        // 「오늘 수집」 문구가 아래 줄에서 같은 말을 하므로 점은 훑어보기용 덧표시다 —
                        // 색만으로 지던 정보를 문구가 넘겨받았으니 읽히는 것까지 두 번 할 필요는 없다.
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .background(colors.success, CareerCompassTheme.shapes.pill)
                                    .clearAndSetSemantics {},
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                FeedSuitabilityChip(state = listing.suitability)
            }

            Text(
                text = listing.title,
                modifier = Modifier.padding(end = 12.dp),
                // 읽은 공고는 한 단계 흐리다 — 「읽음」 배지가 정보를 지고 이 색은 훑어볼 때만 거든다.
                color = if (listing.isRead) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style =
                    CareerCompassTheme.typography.headline4.copy(
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.1).sp,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedListingMeta(
                    listing = listing,
                    modifier = Modifier.weight(1f),
                )
                FeedBookmarkToggle(
                    title = listing.title,
                    bookmarked = listing.isBookmarked,
                    onClick = onBookmarkToggled,
                )
            }
        }
    }
}

/**
 * 카드 아래 메타 줄 — 마감일·수집일·읽음 표시.
 *
 * [FlowRow] 인 이유는 큰 글꼴이다. 한 줄 [Row] 였다면 fontScale 2.0 에서 세 조각이 폭을 다투다
 * 마감일이 잘리거나 북마크 버튼이 밀려난다. 접히면 카드가 세로로 길어질 뿐, 잃는 정보가 없다.
 *
 * 가운뎃점 같은 구분자를 두지 않는다 — 접힐 때 줄 앞머리에 남아 어색해지고, 마감일(굵은 강조)과
 * 수집일(흐린 보조)은 굵기·색이 이미 다르므로 사이 여백만으로 갈린다.
 */
@Composable
private fun FeedListingMeta(
    listing: FeedListingUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val metaTextStyle = feedMetaTextStyle

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listing.deadlineLabel,
            color = if (listing.isDeadlineUrgent) colors.actionDanger else colors.mutedContent,
            style = metaTextStyle,
        )
        Text(
            text = listing.collectedAtLabel,
            color = colors.mutedContent,
            style = metaTextStyle.copy(fontWeight = FontWeight.Normal),
        )
        if (listing.isRead) {
            FeedReadBadge()
        }
    }
}

@Composable
private fun FeedBookmarkToggle(
    title: String,
    bookmarked: Boolean,
    onClick: () -> Unit,
) {
    val bookmarkDescription =
        stringResource(
            R.string.feed_bookmark_content_description,
            title,
        )
    val bookmarkStateDescription =
        stringResource(
            if (bookmarked) {
                R.string.feed_bookmark_saved_state
            } else {
                R.string.feed_bookmark_not_saved_state
            },
        )

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = bookmarkDescription
                    stateDescription = bookmarkStateDescription
                }.toggleable(
                    value = bookmarked,
                    role = Role.Checkbox,
                    onValueChange = { onClick() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (bookmarked) CareerCompassIcons.Bookmark else CareerCompassIcons.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(FEED_ICON_SIZE),
            tint = if (bookmarked) CareerCompassTheme.colors.primaryEmphasis else CareerCompassTheme.colors.mutedContent,
        )
    }
}

@Composable
private fun FeedLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.feed_loading),
                color = CareerCompassTheme.colors.onSurfaceVariant,
                style = CareerCompassTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * 저장해 둔 스냅샷을 보여 주는 중이라는 표시 — 목록의 값이 지금 서버 상태가 아니라는 것을 화면에 남긴다.
 *
 * 아이콘은 장식이라 스크린 리더에서 지우고([clearAndSetSemantics]), 문구 하나만 읽히게 둔다.
 */
@Composable
private fun FeedOfflineBanner(notice: String) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large, vertical = spacing.xSmall)
                .background(color = colors.warningContainer, shape = CareerCompassTheme.shapes.control)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_icon_offline),
            modifier = Modifier.clearAndSetSemantics {},
            style = CareerCompassTheme.typography.bodyMedium,
        )
        Text(
            text = notice,
            color = colors.onWarningContainer,
            style = CareerCompassTheme.typography.caption,
        )
    }
}

/**
 * 프로필이 비어 적합도를 못 내는 항목이 목록에 있을 때의 안내 — 누르면 마이 탭(프로필 입력)으로 간다.
 *
 * 카드마다 같은 말을 되풀이하지 않고 목록 위에 한 번만 얹는다. 행 전체가 버튼이라 아이콘·문구는
 * 스크린 리더에서 하나로 합치고([semantics] `mergeDescendants`), 터치 높이는 48dp 아래로 내려가지 않게 잡는다.
 */
@Composable
private fun FeedProfileNoticeBanner(onClick: () -> Unit) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val shape = CareerCompassTheme.shapes.control
    val noticeDescription = stringResource(R.string.feed_profile_notice_content_description)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large, vertical = spacing.xSmall)
                .clip(shape)
                .background(color = colors.primaryContainer, shape = shape)
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.medium, vertical = spacing.small)
                .semantics(mergeDescendants = true) {
                    contentDescription = noticeDescription
                },
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_icon_profile_notice),
            modifier = Modifier.clearAndSetSemantics {},
            style = CareerCompassTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.feed_profile_notice),
            modifier = Modifier.weight(1f),
            color = colors.onPrimaryContainer,
            style = CareerCompassTheme.typography.caption,
        )
        Text(
            text = stringResource(R.string.feed_profile_notice_action),
            color = colors.primaryEmphasis,
            maxLines = 1,
            style = CareerCompassTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * 빈 목록 — [FeedEmptyReason] 마다 다른 안내와 행동을 준다.
 *
 * 사유를 가르는 규칙과 우선순위는 [FeedEmptyReason] 의 KDoc 에 있다. 여기서는 사유 하나를 문구와
 * 행동으로 옮기기만 한다.
 *
 * 되돌릴 조건이 없는 사유([FeedEmptyReason.NotCollected]·[FeedEmptyReason.OfflineSnapshot])에는 행동을
 * 주지 않는다 — 눌러도 목록이 달라지지 않는 버튼은 기다리라는 안내와 모순된다. 새로고침은 화면 전체에
 * 걸린 당겨서 새로고침이 이미 맡고 있다.
 */
@Composable
private fun FeedEmpty(
    reason: FeedEmptyReason,
    onEvent: (FeedUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (reason) {
        FeedEmptyReason.NoBoards -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_no_boards_title),
                description = stringResource(R.string.feed_empty_no_boards_description),
                actionText = stringResource(R.string.feed_empty_no_boards_action),
                onActionClick = { onEvent(FeedUiEvent.BoardRegisterSelected) },
                modifier = modifier,
            )
        }

        is FeedEmptyReason.Search -> {
            // 검색어를 지우는 것도 검색어 변경이다 — 입력칸의 글자와 조회 조건이 한 이벤트로만 바뀌게
            // 두어, 지우기 전용 경로가 둘 사이를 어긋나게 만들지 않는다.
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_search_title, reason.query),
                description = stringResource(R.string.feed_empty_search_description),
                actionText = stringResource(R.string.feed_empty_search_action),
                onActionClick = { onEvent(FeedUiEvent.SearchQueryChanged("")) },
                modifier = modifier,
            )
        }

        FeedEmptyReason.Filter -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_filter_title),
                description = stringResource(R.string.feed_empty_filter_description),
                actionText = stringResource(R.string.feed_empty_filter_action),
                onActionClick = { onEvent(FeedUiEvent.FilterResetSelected) },
                modifier = modifier,
            )
        }

        is FeedEmptyReason.MissingBoards -> {
            // 행동은 시트를 여는 것이 아니라 그 조건을 바로 빼는 것이다 — 시트 안 태그를 찾아 누르게 하면
            // 「열어 보기 전에는 원인을 알 수 없다」는, 이 사유가 생긴 이유가 그대로 남는다(이슈 #206).
            //
            // 문구는 뺀 뒤에 무엇이 남는지에 따라 갈린다. 다른 조건이 남아 있는데 「빼면 보여요」라고 하면
            // 눌러도 같은 빈 화면이 나와, 화면이 방금 한 약속을 스스로 깬다.
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_missing_boards_title, reason.count),
                description =
                    stringResource(
                        if (reason.isOnlyCondition) {
                            R.string.feed_empty_missing_boards_description
                        } else {
                            R.string.feed_empty_missing_boards_description_partial
                        },
                    ),
                actionText = stringResource(R.string.feed_empty_missing_boards_action),
                onActionClick = { onEvent(FeedUiEvent.MissingBoardsCleared) },
                modifier = modifier,
            )
        }

        is FeedEmptyReason.NotCollected -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_not_collected_title),
                description =
                    reason.collectNotice?.let { notice ->
                        stringResource(R.string.feed_empty_not_collected_description_with_notice, notice)
                    } ?: stringResource(R.string.feed_empty_not_collected_description),
                actionText = null,
                onActionClick = null,
                modifier = modifier,
            )
        }

        FeedEmptyReason.OfflineSnapshot -> {
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_offline_title),
                description = stringResource(R.string.feed_empty_offline_description),
                actionText = null,
                onActionClick = null,
                modifier = modifier,
            )
        }

        FeedEmptyReason.MoreAvailable -> {
            // 유일하게 조건을 되돌리라고 하지 않는 「빈」 화면이다 — 되돌릴 조건이 잘못됐다는 근거가 없고,
            // 아직 안 읽은 페이지가 남아 있다는 근거만 있다. 그래서 행동도 이어 읽기 하나다.
            CareerCompassEmptyState(
                title = stringResource(R.string.feed_empty_more_available_title),
                description = stringResource(R.string.feed_empty_more_available_description),
                actionText = stringResource(R.string.feed_load_more_action),
                onActionClick = { onEvent(FeedUiEvent.LoadMoreSelected) },
                modifier = modifier,
            )
        }
    }
}

private fun FeedListingCategory.badgeTone(): CareerCompassBadgeTone =
    when (this) {
        FeedListingCategory.All,
        FeedListingCategory.Employment,
        -> CareerCompassBadgeTone.Brand

        FeedListingCategory.Scholarship -> CareerCompassBadgeTone.Info

        FeedListingCategory.Contest -> CareerCompassBadgeTone.Warning

        FeedListingCategory.ExternalActivity,
        FeedListingCategory.Other,
        -> CareerCompassBadgeTone.Neutral
    }

/** Trigger the next page when this many items (or fewer) remain below the viewport. */
private const val LOAD_MORE_THRESHOLD = 3

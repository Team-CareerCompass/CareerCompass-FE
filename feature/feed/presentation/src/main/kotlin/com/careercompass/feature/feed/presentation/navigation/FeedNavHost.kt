package com.careercompass.feature.feed.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.careercompass.core.ui.navigation.FeatureNavDisplay
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.feature.feed.presentation.board.BoardListScreen
import com.careercompass.feature.feed.presentation.board.BoardRegisterScreen
import com.careercompass.feature.feed.presentation.feed.FeedScreen
import com.careercompass.feature.feed.presentation.postingdetail.PostingDetailScreen
import com.careercompass.feature.feed.presentation.postingdetail.PostingDetailViewModel
import com.careercompass.feature.feed.presentation.postingraw.PostingRawScreen
import com.careercompass.feature.feed.presentation.postingraw.PostingRawViewModel

/**
 * 피드 피처가 소유하는 로컬 Navigation 3 스택 — 홈 위에 상세·원문·게시판 등록·게시판 목록이 쌓인다.
 *
 * 화면마다 ViewModel 을 `entry { }` 안에서 만든다 — entry 범위라 그 화면이 스택에서 빠지면 정리되고, 위에 다른 화면이
 * 쌓이는 동안은 살아 있다. 상세·원문은 키의 공고 id 를 assisted 주입으로 받는다(Nav3 entry 엔 `SavedStateHandle`
 * 자동 채움이 없다). 폼 초안용 `SavedStateHandle` 은 그대로 쓴다 — entry 스코프 owner 가 저장 상태 레지스트리를 물고
 * 있어 프로세스 재생성 성질이 유지된다.
 *
 * 바텀바는 피드 홈에서만 보인다. 루트 스택에서 이 host 는 키 하나라 그 키만으로는 상세가 쌓였는지 알 수 없으므로,
 * [boundary] 의 `onAtRootChanged` 로 깊이를 셸에 올려 판정에 합성한다.
 *
 * @param pendingEntry 셸이 부탁한 진입(딥링크 상세·온보딩 완료의 게시판 등록). 반영한 뒤 [onPendingEntryConsumed] 로 비운다.
 */
@Composable
public fun FeedNavHost(
    boundary: FeatureStackBoundary,
    externalActions: FeedExternalActions,
    pendingEntry: FeedEntryRequest?,
    onPendingEntryConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(FeedRoute.Home)
    val actions =
        remember(backStack, boundary, externalActions) {
            FeedLocalNavActions(backStack, boundary, externalActions)
        }

    val onPendingEntryConsumedState by rememberUpdatedState(onPendingEntryConsumed)
    LaunchedEffect(pendingEntry) {
        if (pendingEntry == null) return@LaunchedEffect
        actions.applyEntryRequest(pendingEntry)
        onPendingEntryConsumedState()
    }

    FeatureNavDisplay(
        backStack = backStack,
        boundary = boundary,
        modifier = modifier,
        entryProvider =
            entryProvider {
                entry<FeedRoute.Home> {
                    FeedScreen(
                        onPostingClick = actions::navigateToPostingDetail,
                        onNotificationsClick = actions::navigateToNotifications,
                        onBoardRegisterClick = actions::navigateToBoardRegister,
                        onBoardListClick = actions::navigateToBoardList,
                        onProfileClick = actions::navigateToProfileTab,
                        onSessionEnded = actions::onSessionEnded,
                    )
                }
                entry<FeedRoute.PostingDetail> { key ->
                    PostingDetailScreen(
                        onBackClick = actions::popBack,
                        onPostingClick = actions::navigateToPostingDetail,
                        onRawClick = actions::navigateToPostingRaw,
                        onProfileClick = actions::navigateToProfileTab,
                        onCreateDraftClick = actions::navigateToApplicationSetup,
                        onSessionEnded = actions::onSessionEnded,
                        viewModel =
                            hiltViewModel<PostingDetailViewModel, PostingDetailViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key) },
                            ),
                    )
                }
                entry<FeedRoute.PostingRaw> { key ->
                    PostingRawScreen(
                        onBackClick = actions::popBack,
                        onSessionEnded = actions::onSessionEnded,
                        viewModel =
                            hiltViewModel<PostingRawViewModel, PostingRawViewModel.Factory>(
                                creationCallback = { factory -> factory.create(key) },
                            ),
                    )
                }
                entry<FeedRoute.BoardRegister> {
                    BoardRegisterScreen(
                        onBackClick = actions::popBack,
                        onSessionEnded = actions::onSessionEnded,
                    )
                }
                entry<FeedRoute.BoardList> {
                    BoardListScreen(
                        onBackClick = actions::popBack,
                        onAddBoardClick = actions::navigateToBoardRegister,
                        onSessionEnded = actions::onSessionEnded,
                    )
                }
            },
    )
}

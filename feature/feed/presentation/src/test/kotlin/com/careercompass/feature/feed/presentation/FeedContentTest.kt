package com.careercompass.feature.feed.presentation

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingState_showsProgressCopyWithoutListings() {
        composeRule.setFeedContent(state = sampleState(content = FeedContentState.Loading))

        composeRule.onNodeWithText("공고를 불러오는 중이에요").assertIsDisplayed()
        composeRule.onAllNodesWithText(SAMPLE_LISTING_TITLE).assertCountEquals(0)
    }

    @Test
    fun emptyStateWithoutBoards_offersBoardRegistrationInsteadOfChangingConditions() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(state = emptyState(FeedEmptyReason.NoBoards), onEvent = events::add)

        composeRule.onNodeWithText("아직 등록한 게시판이 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("검색어 지우기").assertCountEquals(0)
        composeRule.onAllNodesWithText(SAMPLE_LISTING_TITLE).assertCountEquals(0)
        composeRule.onNodeWithText("게시판 등록하기").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.BoardRegisterSelected), events)
        }
    }

    @Test
    fun emptyStateWithSearchQuery_namesTheQueryAndClearsIt() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(
            state = emptyState(FeedEmptyReason.Search("백엔드")),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("‘백엔드’ 검색 결과가 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("검색어 지우기").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.SearchQueryChanged("")), events)
        }
    }

    @Test
    fun emptyStateWithFilters_offersResetInsteadOfBoardRegistration() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(state = emptyState(FeedEmptyReason.Filter), onEvent = events::add)

        composeRule.onNodeWithText("필터에 맞는 공고가 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("게시판 등록하기").assertCountEquals(0)
        composeRule.onNodeWithText("필터 초기화").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.FilterResetSelected), events)
        }
    }

    /**
     * 이슈 #206 — 필터 시트를 열어 보기 전에 원인과 손잡이가 화면에 있어야 한다.
     *
     * 그래서 행동이 「필터 열기」가 아니라 조건을 바로 빼는 [FeedUiEvent.MissingBoardsCleared] 다. 시트를
     * 열어 주는 것으로 끝나면 태그를 찾아 누르고 「적용」까지 눌러야 해서, 열기 전에는 모른다는 문제가
     * 그대로 남는다.
     *
     * 안내와 행동이 **함께** 보여야 하는데 기본 로볼렉트릭 화면(h470dp)에는 헤더까지 얹은 빈 상태가 다
     * 들어가지 않는다 — 실제 기기에 가까운 크기로 잰다.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun emptyStateWithDeletedBoards_clearsThatConditionInsteadOfOpeningTheSheet() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(
            state = emptyState(FeedEmptyReason.MissingBoards(count = 2, isOnlyCondition = true)),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("고른 게시판 2개가 지워졌어요").assertIsDisplayed()
        composeRule.onNodeWithText("조건에서 빼면 다른 공고가 보여요").assertIsDisplayed()
        composeRule.onAllNodesWithText("필터 초기화").assertCountEquals(0)
        composeRule.onNodeWithText("사라진 게시판 조건 빼기").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.MissingBoardsCleared), events)
        }
    }

    /**
     * 조건이 더 남아 있으면 빼도 여전히 0개일 수 있다 — 문구가 「빼면 보여요」라고 약속하지 않는다.
     *
     * 화면 크기를 키운 이유는 위 테스트와 같다.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp")
    fun emptyStateWithDeletedBoardsAmongOtherConditions_doesNotPromiseResults() {
        composeRule.setFeedContent(
            state = emptyState(FeedEmptyReason.MissingBoards(count = 1, isOnlyCondition = false)),
        )

        composeRule.onNodeWithText("고른 게시판 1개가 지워졌어요").assertIsDisplayed()
        composeRule.onNodeWithText("이 조건을 뺀 뒤에도 비면 남은 조건을 풀어 보세요").assertIsDisplayed()
        composeRule.onAllNodesWithText("조건에서 빼면 다른 공고가 보여요").assertCountEquals(0)
        composeRule.onNodeWithText("사라진 게시판 조건 빼기").assertIsDisplayed()
    }

    @Test
    fun emptyStateBeforeFirstCollection_saysWhenPostingsArriveAndOffersNoAction() {
        composeRule.setFeedContent(
            state = emptyState(FeedEmptyReason.NotCollected("등록한 게시판을 1일 1회 확인하고 있어요")),
        )

        composeRule
            .onNodeWithText("아직 모인 공고가 없어요")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("등록한 게시판을 1일 1회 확인하고 있어요", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction() and hasText("게시판 등록하기")).assertCountEquals(0)
    }

    @Test
    fun emptyOfflineSnapshot_saysTheStoredListIsEmptyWithoutBlamingConditions() {
        composeRule.setFeedContent(state = emptyState(FeedEmptyReason.OfflineSnapshot))

        composeRule.onNodeWithText("저장해 둔 목록에는 공고가 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("필터 초기화").assertCountEquals(0)
        composeRule.onAllNodesWithText("게시판 등록하기").assertCountEquals(0)
    }

    @Test
    fun selectedFilter_exposesCheckedAccessibilityState() {
        composeRule.setFeedContent(state = sampleState())

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertExists()
        composeRule
            .onNodeWithText("전체")
            .assertIsOn()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.RadioButton,
                ),
            )
        composeRule
            .onNode(hasText("채용") and hasStateDescription("선택 안 됨"))
            .assertIsOff()
    }

    /**
     * 인사말 뒤의 손 흔드는 이모지는 장식이다. 제목과 한 Text 로 이어 붙으면 스크린 리더가
     * 「안녕하세요, 일혁님」 뒤에 이모지 이름까지 읽는다. 제목 노드는 인사말만 지녀야 하고,
     * 이모지는 배너들과 같게 접근성 트리에서 빠져 있어야 한다.
     */
    @Test
    fun greetingHeading_readsTheGreetingWithoutTheWaveEmoji() {
        composeRule.setFeedContent(state = sampleState(userName = "일혁"))

        val headingText =
            composeRule
                .onNode(isHeading())
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString(separator = "") { it.text }

        assertEquals("안녕하세요, 일혁님", headingText)
        composeRule
            .onAllNodesWithText(WAVE_EMOJI, substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun longUserNameAtLargeFontScale_keepsNotificationActionVisible() {
        composeRule.setFeedContent(
            state = sampleState(userName = "아주 길어서 한 줄에 전부 표시되지 않는 사용자 이름"),
            fontScale = 2f,
        )

        composeRule
            .onNodeWithContentDescription("알림 보기")
            .assertIsDisplayed()
    }

    @Test
    fun bookmark_exposesStateAndEmitsOnlyBookmarkIntent() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(
            state = sampleState(listing = sampleListing(isBookmarked = true)),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithContentDescription("$SAMPLE_LISTING_TITLE 북마크")
            .assertIsOn()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "저장됨",
                ),
            ).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.BookmarkToggled(SAMPLE_LISTING_ID)), events)
        }
    }

    @Test
    fun filterButton_exposesActiveCountAndEmitsFilterRequested() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(state = sampleState().copy(activeFilterCount = 2), onEvent = events::add)

        composeRule
            .onNodeWithContentDescription("필터")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2개 적용"))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.FilterRequested), events)
        }
    }

    @Test
    fun filterButtonWithoutActiveFilters_hasNoStateDescription() {
        composeRule.setFeedContent(state = sampleState())

        composeRule
            .onNodeWithContentDescription("필터")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun listingWithoutScore_showsAnalyzingChipInsteadOfScore() {
        composeRule.setFeedContent(
            state = sampleState(listing = sampleListing().copy(suitability = FeedSuitabilityState.Analyzing)),
        )

        composeRule.onNodeWithContentDescription("적합도 분석 중").assertIsDisplayed()
        composeRule.onAllNodesWithText("88").assertCountEquals(0)
    }

    @Test
    fun listingWithoutProfile_saysProfileIsMissingInsteadOfAnalyzing() {
        composeRule.setFeedContent(
            state = sampleState(listing = sampleListing().copy(suitability = FeedSuitabilityState.ProfileIncomplete)),
        )

        composeRule
            .onNodeWithContentDescription("프로필을 입력하면 적합도를 확인할 수 있어요")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("분석 중").assertCountEquals(0)
    }

    @Test
    fun listing_showsCollectedAtLabelBesideTheDeadline() {
        composeRule.setFeedContent(state = sampleState(listing = sampleListing(collectedAtLabel = "수집 3일 전", isNew = false)))

        composeRule.onNodeWithText("수집 3일 전").assertIsDisplayed()
        composeRule.onNodeWithText("D-7").assertIsDisplayed()
    }

    /**
     * 오늘 수집분은 초록 점이 **색으로만** 말하던 것을 문구가 대신 말한다 — 점은 훑어보기용 덧표시라
     * 스크린 리더에서 지웠으므로, 「신규 공고」 대신 「오늘 수집」이 읽혀야 한다.
     */
    @Test
    fun newListing_saysCollectedTodayInWordsInsteadOfTheGreenDotAlone() {
        composeRule.setFeedContent(state = sampleState(listing = sampleListing(collectedAtLabel = "오늘 수집", isNew = true)))

        composeRule.onNodeWithText("오늘 수집").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("신규 공고").assertCountEquals(0)
    }

    /**
     * 읽음은 색·굵기 말고 **문구와 접근성 상태**로도 갈려야 한다 — 읽지 않은 카드도 침묵하지 않는다.
     *
     * 배지가 유사 공고 카드와 한 벌로 합쳐졌으므로(#170) 목록 쪽에서도 계약을 못 박아 둔다 — 문구가
     * 그려지되(unmerged 트리) 그 문구가 스크린 리더까지 새어 나가지는 않아야 한다.
     */
    @Test
    fun readListing_marksTheCardWithWordsAndAnAccessibilityState() {
        composeRule.setFeedContent(state = sampleState(listing = sampleListing(isRead = true)))

        composeRule.onNodeWithText("읽음", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNode(hasText(SAMPLE_LISTING_TITLE) and hasStateDescription("읽음"))
            .assertIsDisplayed()
    }

    /**
     * 배지는 `clearAndSetSemantics` 로 스스로를 지운다 — 카드가 이미 상태로 말하므로 배지까지 읽히면
     * 읽은 카드만 「읽음」을 두 번 듣는다. 병합 트리에 배지 문구가 없어야 그 약속이 지켜진 것이다.
     */
    @Test
    fun readListingBadge_isClearedFromTheSemanticsTree() {
        composeRule.setFeedContent(state = sampleState(listing = sampleListing(isRead = true)))

        composeRule.onAllNodesWithText("읽음").assertCountEquals(0)
    }

    @Test
    fun unreadListing_saysSoInsteadOfLeavingTheStateUnspoken() {
        composeRule.setFeedContent(state = sampleState(listing = sampleListing(isRead = false)))

        composeRule
            .onNode(hasText(SAMPLE_LISTING_TITLE) and hasStateDescription("읽지 않음"))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("읽음", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun profileNotice_isHiddenByDefault() {
        composeRule.setFeedContent(state = sampleState())

        composeRule.onAllNodesWithText("프로필을 입력하면 적합도를 확인할 수 있어요").assertCountEquals(0)
    }

    @Test
    fun profileNotice_isReachableAsButtonAndEmitsCompleteProfileIntent() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(
            state = sampleState().copy(isProfileNoticeVisible = true),
            onEvent = events::add,
        )

        composeRule
            .onNodeWithContentDescription("프로필을 입력하면 적합도를 확인할 수 있어요. 프로필 입력하기")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.CompleteProfileSelected), events)
        }
    }

    @Test
    fun loadingMore_appendsProgressRowBelowListings() {
        composeRule.setPagingFeedContent(loadMore = FeedLoadMoreState.Loading)

        composeRule.onNodeWithText(SAMPLE_LISTING_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText("공고를 더 불러오는 중이에요").assertIsDisplayed()
    }

    @Test
    fun pausedLoadMore_offersSearchFurtherRowAndDoesNotRetryOnScrollAlone() {
        // 자동 추적이 상한에서 선 자리 — 아무것도 안 그리면 목록이 끝난 것처럼 보인다. 그렇다고 스크롤만으로
        // 다시 걸리게 두면 걸러질 페이지만 끝없이 받는다. 이어 갈 길은 버튼 하나뿐이어야 한다.
        val events = mutableListOf<FeedUiEvent>()
        var autoLoadMoreCalls = 0
        composeRule.setPagingFeedContent(
            loadMore = FeedLoadMoreState.Paused,
            onEvent = events::add,
            onLoadMore = { autoLoadMoreCalls++ },
        )

        composeRule.onNodeWithText("여기까지 찾았어요").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("더 찾아보기").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(0, autoLoadMoreCalls)
            assertEquals(listOf(FeedUiEvent.LoadMoreSelected), events)
        }
    }

    @Test
    fun failedLoadMore_offersRetryRowAndDoesNotRetryOnScrollAlone() {
        // 스낵바는 지나가 버린다 — 다시 시도할 길이 목록 안에 남아 있어야 한다. 대신 바닥에 머무르는 것만으로
        // 재시도가 돌면, 네트워크가 죽어 있는 동안 같은 실패가 무한히 되풀이된다.
        val events = mutableListOf<FeedUiEvent>()
        var autoLoadMoreCalls = 0
        composeRule.setPagingFeedContent(
            loadMore = FeedLoadMoreState.Failed,
            onEvent = events::add,
            onLoadMore = { autoLoadMoreCalls++ },
        )

        composeRule.onNodeWithText("공고를 더 불러오지 못했어요").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(0, autoLoadMoreCalls)
            assertEquals(listOf(FeedUiEvent.LoadMoreSelected), events)
        }
    }

    @Test
    fun readyLoadMore_pagesAutomaticallyWithoutAnyFooterRow() {
        var autoLoadMoreCalls = 0
        composeRule.setPagingFeedContent(loadMore = FeedLoadMoreState.Ready, onLoadMore = { autoLoadMoreCalls++ })

        composeRule.onAllNodesWithText("여기까지 찾았어요").assertCountEquals(0)
        composeRule.onAllNodesWithText("다시 시도").assertCountEquals(0)
        composeRule.onAllNodesWithText("공고를 더 불러오는 중이에요").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(1, autoLoadMoreCalls) }
    }

    @Test
    fun emptyStateWithMorePages_offersToKeepReadingInsteadOfClearingConditions() {
        // 커서가 남았으면 「결과 없음」이 아니다 — 지울 검색어를 권하는 대신 이어 읽게 한다.
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(state = emptyState(FeedEmptyReason.MoreAvailable), onEvent = events::add)

        composeRule.onNodeWithText("여기까지는 찾지 못했어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("검색어 지우기").assertCountEquals(0)
        composeRule.onAllNodesWithText("필터 초기화").assertCountEquals(0)
        composeRule.onNodeWithText("더 찾아보기").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(FeedUiEvent.LoadMoreSelected), events)
        }
    }

    @Test
    fun searchFilterSortAndCard_emitSeparateIntents() {
        val events = mutableListOf<FeedUiEvent>()
        composeRule.setFeedContent(state = sampleState(), onEvent = events::add)

        composeRule
            .onNodeWithContentDescription("공고 검색")
            .performTextReplacement("Kotlin")
        composeRule
            .onNode(hasText("채용") and hasStateDescription("선택 안 됨"))
            .performClick()
        composeRule.onNodeWithContentDescription("정렬: 적합도 높은순").performClick()
        composeRule.onNodeWithText(SAMPLE_LISTING_TITLE).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    FeedUiEvent.SearchQueryChanged("Kotlin"),
                    FeedUiEvent.FilterSelected(FeedListingCategory.Employment),
                    FeedUiEvent.SortMenuRequested,
                    FeedUiEvent.ListingSelected(SAMPLE_LISTING_ID),
                ),
                events,
            )
        }
    }
}

/** 이어 읽기가 걸린 목록 — 페이징 상태만 갈아 끼운다. 카드가 하나라 목록은 처음부터 바닥이다. */
private fun ComposeContentTestRule.setPagingFeedContent(
    loadMore: FeedLoadMoreState,
    onEvent: (FeedUiEvent) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    setContent {
        CareerCompassTheme {
            FeedContent(
                state = sampleState(),
                onEvent = onEvent,
                listState = rememberLazyListState(),
                onLoadMore = onLoadMore,
                loadMore = loadMore,
            )
        }
    }
}

private fun ComposeContentTestRule.setFeedContent(
    state: FeedUiState,
    onEvent: (FeedUiEvent) -> Unit = {},
    fontScale: Float = 1f,
) {
    setContent {
        val currentDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(currentDensity.density, fontScale),
        ) {
            CareerCompassTheme {
                FeedContent(state = state, onEvent = onEvent)
            }
        }
    }
}

private fun emptyState(reason: FeedEmptyReason): FeedUiState = sampleState(totalListingCount = 0, content = FeedContentState.Empty(reason))

private fun sampleState(
    userName: String = "일혁",
    totalListingCount: Int = 1,
    content: FeedContentState = FeedContentState.Loaded(listOf(sampleListing())),
    listing: FeedListingUiModel? = null,
): FeedUiState =
    FeedUiState(
        userName = userName,
        newListingCount = 12,
        searchQuery = "",
        filters =
            listOf(
                FeedFilterUiModel(FeedListingCategory.All, "전체"),
                FeedFilterUiModel(FeedListingCategory.Employment, "채용"),
                FeedFilterUiModel(FeedListingCategory.Scholarship, "장학금"),
            ),
        selectedFilter = FeedListingCategory.All,
        selectedSort = FeedSortUiModel(id = "fit", label = "적합도 높은순"),
        totalListingCount = totalListingCount,
        content =
            listing?.let { FeedContentState.Loaded(listOf(it)) }
                ?: content,
    )

private fun sampleListing(
    isBookmarked: Boolean = false,
    collectedAtLabel: String = SAMPLE_COLLECTED_LABEL,
    isNew: Boolean = true,
    isRead: Boolean = false,
): FeedListingUiModel =
    FeedListingUiModel(
        id = SAMPLE_LISTING_ID,
        title = SAMPLE_LISTING_TITLE,
        category = FeedListingCategory.Employment,
        categoryLabel = "채용",
        sourceLabel = "공식 채용",
        suitability = FeedSuitabilityState.Scored(88),
        deadlineLabel = "D-7",
        isDeadlineUrgent = false,
        collectedAtLabel = collectedAtLabel,
        isNew = isNew,
        isRead = isRead,
        isBookmarked = isBookmarked,
    )

private const val SAMPLE_COLLECTED_LABEL = "오늘 수집"
private const val SAMPLE_LISTING_ID = "listing-1"
private const val SAMPLE_LISTING_TITLE = "2026 카카오 SW 인턴십 모집"
private const val WAVE_EMOJI = "👋"

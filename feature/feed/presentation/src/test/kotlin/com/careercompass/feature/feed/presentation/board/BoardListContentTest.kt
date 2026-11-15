package com.careercompass.feature.feed.presentation.board

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardListContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loaded_showsCountStatusAndActivationSwitches() {
        composeRule.setListContent(state = loadedState())

        composeRule.onNodeWithText("2/20개 등록").assertIsDisplayed()
        composeRule.onNodeWithText("수집 중").assertIsDisplayed()
        composeRule.onNodeWithText("수집 실패 2회").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("$ACTIVE_NAME 수집 활성화")
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        composeRule.onNodeWithContentDescription("$FAILING_NAME 수집 활성화").assertIsOn()
        composeRule.onNodeWithText("마지막 수집 방금").assertIsDisplayed()
        composeRule.onNodeWithText("공고 12건").assertIsDisplayed()
    }

    @Test
    fun unknownPostingCount_hidesCountRow() {
        val loaded = loadedState().content as BoardListContentState.Loaded
        composeRule.setListContent(
            state = BoardListUiState(content = BoardListContentState.Loaded(loaded.boards.map { it.copy(postingCount = null) })),
        )

        composeRule.onAllNodesWithText("공고 12건").assertCountEquals(0)
        composeRule.onNodeWithText("마지막 수집 방금").assertIsDisplayed()
    }

    @Test
    fun retry_isOfferedOnlyForFailingBoards() {
        composeRule.setListContent(state = loadedState())

        composeRule.onAllNodesWithContentDescription("$ACTIVE_NAME 재시도").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("$FAILING_NAME 재시도").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun boardControls_emitSeparateIntentsWithBoardIds() {
        val events = mutableListOf<BoardListEvent>()
        composeRule.setListContent(state = loadedState(), onEvent = events::add)

        composeRule.onNodeWithContentDescription("$ACTIVE_NAME 수집 활성화").performClick()
        composeRule.onNodeWithContentDescription("$FAILING_NAME 재시도").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("$ACTIVE_NAME 삭제").performScrollTo().performClick()
        composeRule.onNodeWithText(ACTIVE_NAME).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("게시판 추가").performClick()
        composeRule.onNodeWithContentDescription("뒤로가기").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    BoardListEvent.BoardToggled(ACTIVE_ID),
                    BoardListEvent.RetryClicked(FAILING_ID),
                    BoardListEvent.DeleteClicked(ACTIVE_ID),
                    BoardListEvent.BoardSelected(ACTIVE_ID),
                    BoardListEvent.AddBoardClicked,
                    BoardListEvent.BackClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun empty_showsGuidanceAndEmitsAddBoard() {
        val events = mutableListOf<BoardListEvent>()
        composeRule.setListContent(
            state = BoardListUiState(content = BoardListContentState.Empty),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("등록된 게시판이 없어요").assertIsDisplayed()
        composeRule.onNodeWithText("학교 공지·채용 사이트 URL 을 등록하면 자동으로 공고를 모아요").assertIsDisplayed()
        composeRule.onAllNodesWithText(ACTIVE_NAME).assertCountEquals(0)
        composeRule.onNode(hasText("게시판 등록하기") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(BoardListEvent.AddBoardClicked), events)
        }
    }

    @Test
    fun loading_showsProgressCopyWithoutBoards() {
        composeRule.setListContent(state = BoardListUiState(content = BoardListContentState.Loading))

        composeRule.onNodeWithText("게시판을 불러오는 중이에요").assertIsDisplayed()
        composeRule.onAllNodesWithText(ACTIVE_NAME).assertCountEquals(0)
    }

    @Test
    fun deactivated_explainsWhyItStoppedAndOffersRetry() {
        composeRule.setListContent(state = deactivatedState())

        composeRule.onNodeWithText("수집 중단됨").assertIsDisplayed()
        composeRule
            .onNodeWithText("수집이 3회 연속 실패해 자동으로 중단됐어요. 재시도하거나 게시판 주소를 확인하고, 더 안 쓴다면 삭제해 주세요")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("재시도를 누르면 지금 바로 다시 수집해요. 토글만 켜면 다음 수집 주기까지 기다려요")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$DEACTIVATED_NAME 재시도").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$DEACTIVATED_NAME 수집 활성화").assertIsOff()
        composeRule.onAllNodesWithText("일시중지").assertCountEquals(0)
    }

    @Test
    fun paused_isDrawnWithoutTheDeactivationNotice() {
        composeRule.setListContent(state = pausedState())

        composeRule.onNodeWithText("일시중지").assertIsDisplayed()
        composeRule.onAllNodesWithText("수집 중단됨").assertCountEquals(0)
        composeRule
            .onAllNodesWithText("재시도를 누르면 지금 바로 다시 수집해요. 토글만 켜면 다음 수집 주기까지 기다려요")
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("$PAUSED_NAME 재시도").assertCountEquals(0)
    }

    /** 재시도 요청이 오가는 동안에는 버튼이 잠겨 연타가 요청을 늘리지 못한다(#341). */
    @Test
    fun retryInFlight_locksTheRetryButton() {
        val loaded = deactivatedState().content as BoardListContentState.Loaded
        val events = mutableListOf<BoardListEvent>()
        composeRule.setListContent(
            state = BoardListUiState(content = BoardListContentState.Loaded(loaded.boards.map { it.copy(isRetrying = true) })),
            onEvent = events::add,
        )

        composeRule.onNodeWithContentDescription("$DEACTIVATED_NAME 재시도").assertIsNotEnabled().performClick()

        composeRule.runOnIdle { assertEquals(emptyList<BoardListEvent>(), events) }
    }

    @Test
    fun deactivatedControls_emitRetryAndToggleSeparately() {
        val events = mutableListOf<BoardListEvent>()
        composeRule.setListContent(state = deactivatedState(), onEvent = events::add)

        composeRule.onNodeWithContentDescription("$DEACTIVATED_NAME 재시도").performClick()
        composeRule.onNodeWithContentDescription("$DEACTIVATED_NAME 수집 활성화").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    BoardListEvent.RetryClicked(DEACTIVATED_ID),
                    BoardListEvent.BoardToggled(DEACTIVATED_ID),
                ),
                events,
            )
        }
    }
}

private fun ComposeContentTestRule.setListContent(
    state: BoardListUiState,
    onEvent: (BoardListEvent) -> Unit = {},
) {
    setContent {
        CareerCompassTheme {
            BoardListContent(state = state, onEvent = onEvent)
        }
    }
}

private fun loadedState(): BoardListUiState =
    BoardListUiState(
        content =
            BoardListContentState.Loaded(
                listOf(
                    BoardUiModel(
                        id = ACTIVE_ID,
                        name = ACTIVE_NAME,
                        url = "https://konkuk.ac.kr/board/notice",
                        type = BoardType.Employment,
                        typeLabel = "채용",
                        status = BoardStatus.Active,
                        isActive = true,
                        failCount = 0,
                        lastCollectedLabel = "방금",
                        postingCount = 12,
                    ),
                    BoardUiModel(
                        id = FAILING_ID,
                        name = FAILING_NAME,
                        url = "https://wettheidol.com/contest",
                        type = BoardType.Contest,
                        typeLabel = "공모전",
                        status = BoardStatus.Failing,
                        isActive = true,
                        failCount = 2,
                        lastCollectedLabel = "6시간 전",
                        postingCount = 4,
                    ),
                ),
            ),
    )

/** 연속 실패로 서버가 끈 게시판. 카드 하나만 그려 안내가 그 카드에서 나오는지 본다. */
private fun deactivatedState(): BoardListUiState =
    BoardListUiState(
        content =
            BoardListContentState.Loaded(
                listOf(
                    BoardUiModel(
                        id = DEACTIVATED_ID,
                        name = DEACTIVATED_NAME,
                        url = "https://wettheidol.com/contest",
                        type = BoardType.Contest,
                        typeLabel = "공모전",
                        status = BoardStatus.Deactivated,
                        isActive = false,
                        failCount = 3,
                        lastCollectedLabel = "3일 전",
                        postingCount = 4,
                    ),
                ),
            ),
    )

/** 사용자가 직접 끈 게시판 — 같은 「꺼짐」이지만 중단 안내도 재시도도 붙지 않는다. */
private fun pausedState(): BoardListUiState =
    BoardListUiState(
        content =
            BoardListContentState.Loaded(
                listOf(
                    BoardUiModel(
                        id = PAUSED_ID,
                        name = PAUSED_NAME,
                        url = "https://recruit.navercorp.com/rcrt/list.do",
                        type = BoardType.Employment,
                        typeLabel = "채용",
                        status = BoardStatus.Paused,
                        isActive = false,
                        failCount = 0,
                        lastCollectedLabel = null,
                        postingCount = 0,
                    ),
                ),
            ),
    )

private const val ACTIVE_ID = "konkuk"
private const val ACTIVE_NAME = "건국대 공지사항"
private const val FAILING_ID = "wettheidol"
private const val FAILING_NAME = "공모전 사이트"
private const val DEACTIVATED_ID = "wettheidol-off"
private const val DEACTIVATED_NAME = "멈춘 공모전 사이트"
private const val PAUSED_ID = "naver"
private const val PAUSED_NAME = "네이버 채용"

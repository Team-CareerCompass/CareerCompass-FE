package com.careercompass.feature.profile.presentation.home

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.home.component.PROFILE_BIOMETRIC_SWITCH_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileHomeContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `요약 카드에 이름과 소속과 개수를 그린다`() {
        composeRule.setContent(
            ProfileHomeUiState(profile = profile(), experienceCardCount = 12, pastApplicationCount = 5),
        )

        composeRule.onNodeWithText("정일혁").assertIsDisplayed()
        composeRule.onNodeWithText("건국대학교 · 컴퓨터공학부 · 2027년 졸업 예정").assertIsDisplayed()
        composeRule.onNodeWithText("78%").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `메뉴 줄에 개수 배지를 붙인다`() {
        composeRule.setContent(
            ProfileHomeUiState(profile = profile(), experienceCardCount = 12, pastApplicationCount = 5),
        )

        composeRule.scrollTo(hasText("수상, 인턴, 프로젝트 등"))

        // 배지는 스스로 병합 경계를 세우므로(CareerCompassBadge) 줄의 병합 텍스트에 섞이지 않는다 — 자식으로 찾는다.
        composeRule
            .onNode(hasText("수상, 인턴, 프로젝트 등") and hasClickAction())
            .onChildren()
            .filterToOne(hasText("12"))
            .assertIsDisplayed()
    }

    /** 못 센 개수를 0 으로 적으면 「하나도 없다」는 거짓이 된다 — 배지를 아예 그리지 않는다. */
    @Test
    fun `개수를 모르면 배지를 그리지 않고 요약에 대시를 쓴다`() {
        composeRule.setContent(
            ProfileHomeUiState(profile = profile(), experienceCardCount = null, pastApplicationCount = null),
        )

        composeRule.onAllNodesWithText("0").assertCountEquals(0)
        composeRule.onAllNodesWithText("—").assertCountEquals(2)
    }

    @Test
    fun `빈 칸이 있으면 무엇을 채우면 되는지 알린다`() {
        composeRule.setContent(
            ProfileHomeUiState(
                profile = profile(gpa = null, tags = emptyList()),
                experienceCardCount = 2,
                pastApplicationCount = 0,
            ),
        )

        composeRule.onNodeWithText("학점·관심 분야 정보를 채우면 적합도 분석이 더 정확해져요").assertIsDisplayed()
    }

    @Test
    fun `다 채웠으면 완성 안내로 끝난다`() {
        composeRule.setContent(ProfileHomeUiState(profile = profile(), experienceCardCount = 1, pastApplicationCount = 0))

        composeRule.onNodeWithText("필요한 항목을 모두 채웠어요").assertIsDisplayed()
    }

    @Test
    fun `메뉴를 누르면 그 목적지를 올려 보낸다`() {
        val events = mutableListOf<ProfileHomeEvent>()
        composeRule.setContent(
            state = ProfileHomeUiState(profile = profile(), experienceCardCount = 1, pastApplicationCount = 0),
            onEvent = { events += it },
        )

        composeRule.scrollTo(hasText("AI 학습 데이터"))
        composeRule.onNode(hasText("AI 학습 데이터") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ProfileHomeEvent.MenuClicked(ProfileHomeMenu.PastApplications)), events)
        }
    }

    /** 캐시가 없을 때만 실패가 화면을 덮는다. 문구는 실패 표(#204)가 정한다. */
    @Test
    fun `캐시가 없는 실패는 화면을 덮고 재시도를 연다`() {
        val events = mutableListOf<ProfileHomeEvent>()
        composeRule.setContent(
            state = ProfileHomeUiState(profile = null, loadFailure = FailureKind.NoConnection),
            onEvent = { events += it },
        )

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
        composeRule.onNode(hasText("다시 시도") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(listOf(ProfileHomeEvent.RetryClicked), events) }
    }

    @Test
    fun `캐시가 있으면 실패해도 프로필을 계속 보여 준다`() {
        composeRule.setContent(
            ProfileHomeUiState(profile = profile(), loadFailure = FailureKind.NoConnection, message = ProfileHomeMessage.RefreshFailed),
        )

        composeRule.onNodeWithText("정일혁").assertIsDisplayed()
        composeRule.onAllNodesWithText("연결할 수 없어요").assertCountEquals(0)
    }

    @Test
    fun `첫 조회 중에는 진행 표시만 그린다`() {
        composeRule.setContent(ProfileHomeUiState(profile = null, isLoading = true))

        composeRule.onNodeWithText("프로필을 불러오는 중이에요").assertIsDisplayed()
        composeRule.onAllNodesWithText("프로필 편집").assertCountEquals(0)
    }

    /** 판정 전에는 켤 수 없고, 그렇다고 「쓸 수 없다」를 스치듯 보이지도 않는다. */
    @Test
    fun `기기 판정 전에는 지문 스위치가 잠긴다`() {
        composeRule.setContent(ProfileHomeUiState(profile = profile(), experienceCardCount = 1, pastApplicationCount = 0))

        composeRule.scrollTo(hasTestTag(PROFILE_BIOMETRIC_SWITCH_TAG))

        composeRule.onNodeWithTag(PROFILE_BIOMETRIC_SWITCH_TAG).assertIsNotEnabled()
        composeRule.onAllNodesWithText("이 기기에서는 지문 로그인을 켤 수 없어요").assertCountEquals(0)
    }

    @Test
    fun `로그아웃을 누르면 확인 다이얼로그가 뜬다`() {
        composeRule.setContent(
            ProfileHomeUiState(profile = profile(), experienceCardCount = 1, pastApplicationCount = 0, isLogoutDialogVisible = true),
        )

        composeRule.onNodeWithText("로그아웃할까요?").assertIsDisplayed()
        composeRule.onNodeWithText("네, 로그아웃").assertIsDisplayed()
    }

    /** 목록이 길어 화면 밖에 있는 줄은 LazyColumn 이 아직 만들지 않는다 — 먼저 그 자리로 굴린다. */
    private fun ComposeContentTestRule.scrollTo(matcher: SemanticsMatcher) {
        onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun ComposeContentTestRule.setContent(
        state: ProfileHomeUiState,
        onEvent: (ProfileHomeEvent) -> Unit = {},
    ) {
        setContent {
            CareerCompassTheme {
                ProfileHomeContent(state = state, onEvent = onEvent)
            }
        }
    }

    private fun profile(
        gpa: Double? = 3.9,
        tags: List<String> = listOf("모바일"),
    ) = UserProfile(
        id = 1,
        name = "정일혁",
        school = "건국대학교",
        department = "컴퓨터공학부",
        gpa = gpa,
        gradYear = 2027,
        jobInterests = listOf(JobInterest(code = "android", priority = 1)),
        tags = tags,
        onboardingDone = true,
        completion = 78,
    )
}

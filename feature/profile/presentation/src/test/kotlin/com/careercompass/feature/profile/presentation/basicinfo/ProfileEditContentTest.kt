package com.careercompass.feature.profile.presentation.basicinfo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.theme.CareerCompassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileEditContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `서버 값이 채워진 다섯 칸을 그린다`() {
        composeRule.setContent(loadedState())

        composeRule.onNodeWithText("이준혁").assertIsDisplayed()
        composeRule.onNodeWithText("건국대학교").assertIsDisplayed()
        composeRule.onNodeWithText("컴퓨터공학부").assertIsDisplayed()
        composeRule.onNodeWithText("3.9").assertIsDisplayed()
        composeRule.onNodeWithText("2027").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `검증 오류가 있으면 문구를 붙이고 저장을 잠근다`() {
        composeRule.setContent(loadedState().copy(nameError = ProfileFieldViolation.TooLong(20)))

        composeRule.onNodeWithText("20자 이내로 입력해 주세요").assertIsDisplayed()
        composeRule.onNode(hasText("저장") and hasClickAction()).performScrollTo().assertIsNotEnabled()
    }

    /** 서버가 짚어 준 칸은 로컬 규칙으로 다시 만들 수 없으므로 전용 문구를 쓴다. */
    @Test
    fun `서버가 거부한 칸에 문구를 붙인다`() {
        composeRule.setContent(loadedState().copy(serverRejectedField = ProfileEditField.Department))

        composeRule.onNodeWithText("서버가 이 값을 받지 않았어요").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `저장을 누르면 올려 보낸다`() {
        val events = mutableListOf<ProfileEditEvent>()
        composeRule.setContent(loadedState(), onEvent = { events += it })

        composeRule.onNode(hasText("저장") and hasClickAction()).performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf(ProfileEditEvent.SaveClicked), events) }
    }

    @Test
    fun `학교 칸을 누르면 피커를 연다`() {
        val events = mutableListOf<ProfileEditEvent>()
        composeRule.setContent(loadedState(), onEvent = { events += it })

        composeRule.onNodeWithText("건국대학교").performClick()

        composeRule.runOnIdle { assertEquals(listOf(ProfileEditEvent.SchoolPickerClicked), events) }
    }

    /** 저장 실패는 입력을 덮지 않는다 — 화면을 덮는 것은 프리필할 값이 아예 없을 때뿐이다. */
    @Test
    fun `프리필할 값이 없는 실패만 화면을 덮는다`() {
        composeRule.setContent(ProfileEditUiState(loadFailure = FailureKind.NoConnection))

        composeRule.onNodeWithText("연결할 수 없어요").assertIsDisplayed()
        composeRule.onAllNodesWithText("저장").assertCountEquals(0)
    }

    @Test
    fun `첫 조회 중에는 진행 표시만 그린다`() {
        composeRule.setContent(ProfileEditUiState())

        composeRule.onNodeWithText("프로필을 불러오는 중이에요").assertIsDisplayed()
        composeRule.onAllNodesWithText("저장").assertCountEquals(0)
    }

    private fun loadedState(): ProfileEditUiState {
        val profile =
            UserProfile(
                id = 1,
                name = "이준혁",
                school = "건국대학교",
                department = "컴퓨터공학부",
                gpa = 3.9,
                gradYear = 2027,
                jobInterests = listOf(JobInterest(code = "android", priority = 1)),
                tags = listOf("모바일"),
                onboardingDone = true,
                completion = 78,
            )
        return ProfileEditUiState(
            original = profile,
            name = "이준혁",
            school = "건국대학교",
            department = "컴퓨터공학부",
            gradePointAverage = "3.9",
            graduationDate = "2027",
        )
    }

    private fun ComposeContentTestRule.setContent(
        state: ProfileEditUiState,
        onEvent: (ProfileEditEvent) -> Unit = {},
    ) {
        setContent {
            CareerCompassTheme {
                ProfileEditContent(state = state, onEvent = onEvent)
            }
        }
    }
}

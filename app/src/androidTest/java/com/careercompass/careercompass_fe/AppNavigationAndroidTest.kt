package com.careercompass.careercompass_fe

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeAppSettingsRepository
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeBoardRepository
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.board.Board
import com.careercompass.core.model.board.BoardStatus
import com.careercompass.core.model.board.BoardType
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingBoardRef
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.model.posting.PostingType
import com.careercompass.core.model.settings.ThemeMode
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import javax.inject.Inject

/**
 * 앱 셸의 시작 목적지 분기·그래프 배선·딥링크 인증 게이트를 fake 세션으로 확인한다 — 실제 서버·OAuth 없이.
 *
 * `createEmptyComposeRule` 을 쓰는 이유: fake 의 세션·프로필 상태를 Activity 가 뜨기 **전에** 정해야 시작 목적지가
 * 그 상태로 계산된다. `createAndroidComposeRule` 은 규칙 시작 시점에 Activity 를 띄운다.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppNavigationAndroidTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var fakeAuthRepository: FakeAuthRepository

    @Inject
    lateinit var fakeUserProfileRepository: FakeUserProfileRepository

    @Inject
    lateinit var fakePostingRepository: FakePostingRepository

    @Inject
    lateinit var fakeBoardRepository: FakeBoardRepository

    @Inject
    lateinit var fakeAppSettingsRepository: FakeAppSettingsRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun withoutSession_startsAtLoginScreen() {
        fakeAuthRepository.loggedIn = false

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithTag("careercompass_app_start", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("카카오 로그인").assertIsDisplayed()
        composeRule.onNodeWithText("Google 계정으로 로그인").assertIsDisplayed()
    }

    @Test
    fun withSessionAndOnboardingDone_startsAtFeedWithBottomTabs() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("안녕하세요, 정일혁님", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("피드").assertIsDisplayed()
        composeRule.onNodeWithText("마이").performClick()
        composeRule.onNodeWithText("프로필 완성도").assertIsDisplayed()
    }

    /**
     * 마이 홈의 로그아웃 — 확인 다이얼로그를 거쳐 세션이 끝나고 셸이 로그인 화면으로 되돌린다.
     *
     * 사용자가 방금 누른 결과라 만료 안내는 붙지 않는다(#128).
     */
    @Test
    fun myTabLogout_returnsToLoginScreenWithoutNotice() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("마이").performClick()
        composeRule.onNodeWithText("정일혁").assertIsDisplayed()
        scrollMyHomeTo(hasText("로그아웃"))
        composeRule.onNodeWithText("로그아웃").performClick()
        composeRule.onNodeWithText("네, 로그아웃").performClick()

        // 로그아웃 → 시작 목적지 재계산 → NavHost 재생성까지가 비동기다.
        composeRule.waitUntil(timeoutMillis = LOGOUT_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText("카카오 로그인")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithText("카카오 로그인").assertIsDisplayed()
        composeRule.onNodeWithText(SESSION_EXPIRED_TEXT).assertDoesNotExist()
        assertEquals(1, fakeAuthRepository.logoutCalls)
    }

    /**
     * 피드에서 401 — 셸이 로그인 화면으로 되돌리며 이유를 한 줄 남긴다(#128).
     *
     * 실제로는 `TokenAuthenticator` 가 401 에서 세션을 정리한 뒤 실패가 화면까지 올라오므로, fake 도 실패를
     * 돌려주기 전에 세션을 비운다 — 그래야 셸의 재계산이 로그인으로 간다.
     */
    @Test
    fun feedSessionExpiry_returnsToLoginScreenWithNotice() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)
        fakePostingRepository.onGetPostings = {
            fakeAuthRepository.loggedIn = false
            Result.failure(CoreDataFailure.Unauthorized("AUTH_INVALID", IllegalStateException("만료")))
        }

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.waitUntil(timeoutMillis = SESSION_EXPIRY_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(SESSION_EXPIRED_TEXT)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithText("카카오 로그인").assertIsDisplayed()
        composeRule.onNodeWithText(SESSION_EXPIRED_TEXT).assertIsDisplayed()

        // 닫으면 사라지고, 남은 로그인 화면은 그대로다.
        composeRule.onNodeWithContentDescription(DISMISS_NOTICE_DESCRIPTION).performClick()
        composeRule.waitUntil(timeoutMillis = SESSION_EXPIRY_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(SESSION_EXPIRED_TEXT)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
        composeRule.onNodeWithText("카카오 로그인").assertIsDisplayed()
    }

    /**
     * 지문을 등록할 수 없는 기기(관리형 에뮬레이터엔 등록된 지문이 없다) — 스위치는 꺼진 채 잠기고 이유가 한 줄 붙는다.
     */
    @Test
    fun myTabBiometricSwitch_withoutDeviceBiometrics_isOffAndDisabled() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("마이").performClick()
        scrollMyHomeTo(hasText("지문 로그인"))
        composeRule.onNodeWithText("지문 로그인").assertIsDisplayed()
        composeRule.onNodeWithTag(BIOMETRIC_SWITCH_TAG, useUnmergedTree = true).assertIsOff().assertIsNotEnabled()
        composeRule.onNodeWithText(BIOMETRIC_UNAVAILABLE_TEXT).assertIsDisplayed()
    }

    /**
     * 끄는 경로(#113) — 이 기기에 등록이 남아 있으면 지문을 쓸 수 없는 기기에서도 스위치가 열려 있고, 끄면 등록
     * 기록이 지워진다. 켜는 방향은 실기 지문이 있어야 프롬프트가 뜨므로 계측이 아니라 ViewModel 테스트가 덮는다.
     */
    @Test
    fun myTabBiometricSwitch_turnsOffQuickLogin() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("마이").performClick()
        scrollMyHomeTo(hasTestTag(BIOMETRIC_SWITCH_TAG))
        // 등록 상태를 마이 탭에 들어온 뒤에 켠다 — 처음부터 켜져 있으면 시작 목적지가 지문 화면이라 여기 못 온다.
        fakeAuthRepository.biometricEnabledState.value = true
        composeRule.waitUntil(timeoutMillis = BIOMETRIC_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(BIOMETRIC_UNAVAILABLE_TEXT)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
        composeRule
            .onNodeWithTag(BIOMETRIC_SWITCH_TAG, useUnmergedTree = true)
            .assertIsOn()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = BIOMETRIC_TIMEOUT_MILLIS) { !fakeAuthRepository.biometricEnabledState.value }
        composeRule.onNodeWithTag(BIOMETRIC_SWITCH_TAG, useUnmergedTree = true).assertIsOff()
        assertEquals(1, fakeAuthRepository.setBiometricEnabledCalls)
    }

    /**
     * 화면 테마(#210) — 마이 탭에서 고른 값이 저장소에 남고 줄의 표시가 그 값을 따라간다.
     *
     * 실제로 어둡게 그려지는지는 계측이 색을 재지 않으므로 골든·수동 QA 몫이다. 여기서 지키는 것은 **고르는
     * 경로가 살아 있는가** 다 — 셸이 값을 읽어 테마에 넣는 배선은 `MainViewModelTest` 가 덮는다.
     */
    @Test
    fun myTabThemeMode_picksDarkAndKeepsIt() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("마이").performClick()
        scrollMyHomeTo(hasTestTag(THEME_ROW_TAG))
        composeRule.onNodeWithText("화면 테마").assertIsDisplayed()
        composeRule.onNodeWithTag(THEME_ROW_TAG, useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("어둡게").performClick()

        composeRule.waitUntil(timeoutMillis = BIOMETRIC_TIMEOUT_MILLIS) {
            fakeAppSettingsRepository.themeModeState.value == ThemeMode.Dark
        }
        composeRule.onNodeWithText("어둡게").assertIsDisplayed()
    }

    @Test
    fun withSessionButOnboardingNotDone_startsAtOnboardingStep1() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = false)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("기본 정보를 알려주세요").assertIsDisplayed()
    }

    @Test
    fun withSessionAndDeepLink_opensPostingDetail() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)
        fakePostingRepository.details += postingDetail(id = DEEP_LINK_POSTING_ID, title = DEEP_LINK_POSTING_TITLE)

        scenario = ActivityScenario.launch<MainActivity>(postingDeepLinkIntent(DEEP_LINK_POSTING_ID))

        // 피드 홈에서 시작한 뒤 상세로 이동하고, 상세 ViewModel 이 fake 에서 공고를 읽어 제목을 그린다.
        composeRule.waitUntil(timeoutMillis = DEEP_LINK_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(DEEP_LINK_POSTING_TITLE, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.onNodeWithText(DEEP_LINK_POSTING_TITLE, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun withoutSessionAndDeepLink_staysOnLogin() {
        fakeAuthRepository.loggedIn = false
        fakePostingRepository.details += postingDetail(id = DEEP_LINK_POSTING_ID, title = DEEP_LINK_POSTING_TITLE)

        scenario = ActivityScenario.launch<MainActivity>(postingDeepLinkIntent(DEEP_LINK_POSTING_ID))

        // 인증 게이트 — 세션이 없으면 딥링크가 있어도 로그인 화면이며, 상세가 백스택에 오르지 않는다.
        composeRule.onNodeWithText("카카오 로그인").assertIsDisplayed()
        composeRule.onNodeWithText(DEEP_LINK_POSTING_TITLE, useUnmergedTree = true).assertDoesNotExist()
    }

    // ── 인셋 (#145) ──────────────────────────────────────────────────────────

    /**
     * 시스템 바 인셋이 한 번만 들어가는지 — 콘텐츠의 아래끝과 탭 바의 위끝이 맞닿아야 한다.
     *
     * 앱 셸의 `Scaffold` 가 `contentWindowInsets` 로 비운 자리를 자식 화면이 `WindowInsets.safeDrawing` 으로
     * 다시 비우면, 목록이 탭 바에 닿지 못하고 내비게이션 바 높이만큼(3버튼 48dp) 떠서 끝난다. `Modifier.padding`
     * 은 자리만 비울 뿐 인셋을 **소비하지 않기** 때문이다.
     *
     * 골든 스크린샷은 이걸 못 잡는다 — 화면 컴포저블을 셸 없이 단독으로 그리기 때문이다. 그래서 실제 셸 위에서
     * 잰다. 세로 스크롤 노드로 목록을 집는 이유는 피드 헤더에 가로 스크롤(카테고리 칩)이 따로 있어서다.
     */
    @Test
    fun mainRoot_appliesSystemBarInsetsOnce() {
        fakeAuthRepository.loggedIn = true
        fakeUserProfileRepository.profileState.value = profile(onboardingDone = true)
        fakeBoardRepository.boards += board()
        fakePostingRepository.postings += posting()

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.waitUntil(timeoutMillis = FEED_TIMEOUT_MILLIS) {
            composeRule
                .onAllNodesWithText(FEED_POSTING_TITLE, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        val list =
            composeRule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNode()
                .boundsInRoot
        val bottomBar =
            composeRule
                .onNodeWithTag(BOTTOM_BAR_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertEquals(bottomBar.top, list.bottom, 1f)
    }

    /** 알림 모듈이 만들 intent 와 같은 모양 — `careercompass://postings/{id}` 를 VIEW 로 앱에 보낸다. */
    private fun postingDeepLinkIntent(postingId: Long): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("careercompass://postings/$postingId"))
            .setClass(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)

    private fun postingDetail(
        id: Long,
        title: String,
    ) = PostingDetail(
        id = id,
        title = title,
        type = PostingType.Recruit,
        board = PostingBoardRef(id = 1, name = "취업정보 게시판"),
        url = "https://example.com/postings/$id",
        rawContent = "본문",
        dueDate = null,
        collectedAt = Instant.parse("2026-09-01T00:00:00Z"),
        isRead = false,
        isBookmarked = false,
        parsed = null,
        suitability = null,
        similar = emptyList(),
    )

    private fun board() =
        Board(
            id = 1,
            url = "https://example.com/board",
            name = "취업정보 게시판",
            type = BoardType.Recruit,
            cycleHours = 6,
            isActive = true,
            status = BoardStatus.Active,
            failCount = 0,
            lastCollectedAt = Instant.parse("2026-09-01T00:00:00Z"),
        )

    private fun posting() =
        Posting(
            id = 201,
            title = FEED_POSTING_TITLE,
            type = PostingType.Recruit,
            board = PostingBoardRef(id = 1, name = "취업정보 게시판"),
            dueDate = null,
            collectedAt = Instant.parse("2026-09-01T00:00:00Z"),
            score = null,
            scoreLabel = null,
            isRead = false,
            isBookmarked = false,
        )

    /**
     * 마이 홈은 목록이라 계정 설정(지문·테마·로그아웃)이 화면 밖에 있다 — LazyColumn 은 화면 밖 줄을 아직
     * 만들지 않으므로 먼저 그 자리로 굴린다.
     */
    private fun scrollMyHomeTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun profile(onboardingDone: Boolean) =
        UserProfile(
            id = 1,
            name = "정일혁",
            school = "건국대학교",
            department = "컴퓨터공학부",
            gpa = 3.87,
            gradYear = 2027,
            jobInterests = listOf(JobInterest("backend", 1)),
            tags = listOf("AI"),
            onboardingDone = onboardingDone,
            completion = 78,
        )

    private companion object {
        const val DEEP_LINK_POSTING_ID = 101L
        const val FEED_POSTING_TITLE = "피드 목록에 뜨는 2026 신입 공채"
        const val FEED_TIMEOUT_MILLIS = 10_000L

        /** `CAREER_COMPASS_BOTTOM_BAR_TAG` — core:ui 의 internal 상수라 값으로 맞춘다. */
        const val BOTTOM_BAR_TAG = "careercompass_bottom_bar"
        const val DEEP_LINK_POSTING_TITLE = "딥링크로 연 2026 하반기 공채"
        const val DEEP_LINK_TIMEOUT_MILLIS = 10_000L
        const val LOGOUT_TIMEOUT_MILLIS = 10_000L
        const val BIOMETRIC_TIMEOUT_MILLIS = 10_000L
        const val SESSION_EXPIRY_TIMEOUT_MILLIS = 10_000L

        /** `onboarding_failure_session_expired` — 온보딩 저장 실패와 로그인 화면의 만료 안내가 함께 쓰는 문구. */
        const val SESSION_EXPIRED_TEXT = "로그인이 만료됐어요. 다시 로그인해 주세요"

        /** `OnboardingErrorCard` 의 닫기 버튼 — 라벨이 아니라 접근성 이름으로 찾는다. */
        const val DISMISS_NOTICE_DESCRIPTION = "오류 안내 닫기"

        /** `ProfileAccountSection` 의 `PROFILE_BIOMETRIC_SWITCH_TAG` — 라벨은 스위치의 토글 상태를 병합하지 않는다. */
        const val BIOMETRIC_SWITCH_TAG = "profile_biometric_switch"
        const val BIOMETRIC_UNAVAILABLE_TEXT = "이 기기에서는 지문 로그인을 켤 수 없어요"

        /** `ProfileAccountSection` 의 `PROFILE_THEME_ROW_TAG` — 라벨과 현재 값이 한 줄에 병합돼 문구만으로는 집기 어렵다. */
        const val THEME_ROW_TAG = "profile_theme_row"
    }
}

package com.careercompass.feature.onboarding.presentation.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.onboarding.domain.testing.FakeOnboardingProgressRepository
import com.careercompass.feature.onboarding.domain.usecase.AddExperienceUseCase
import com.careercompass.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.careercompass.feature.onboarding.domain.usecase.DeleteExperienceUseCase
import com.careercompass.feature.onboarding.domain.usecase.DeletePastApplicationUseCase
import com.careercompass.feature.onboarding.domain.usecase.GetOnboardingExperiencesUseCase
import com.careercompass.feature.onboarding.domain.usecase.GetOnboardingPastApplicationsUseCase
import com.careercompass.feature.onboarding.domain.usecase.ProceedToPastApplicationUseCase
import com.careercompass.feature.onboarding.domain.usecase.ResolveOnboardingEntryUseCase
import com.careercompass.feature.onboarding.domain.usecase.SaveBasicInfoUseCase
import com.careercompass.feature.onboarding.domain.usecase.SaveJobPreferencesUseCase
import com.careercompass.feature.onboarding.domain.usecase.UpdateExperienceUseCase
import com.careercompass.feature.onboarding.domain.usecase.UpdatePastApplicationItemCategoryUseCase
import com.careercompass.feature.onboarding.domain.usecase.UploadPastApplicationUseCase
import com.careercompass.feature.onboarding.presentation.OnboardingStep1Event
import com.careercompass.feature.onboarding.presentation.reporting.RecordingErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * 단계 화면이 세션 종료를 **화면 밖으로** 넘기는지 — 배너로 삼키지 않는지 본다(#211).
 *
 * ViewModel 테스트는 「401 이면 신호를 올린다」까지만 볼 수 있다. 그 신호가 앱 셸의 콜백까지 닿았는지는 Screen
 * 을 실제로 합성해야 드러난다 — 그래프에서 콜백을 빠뜨리면 ViewModel 테스트는 그대로 초록색이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingStepScreenSessionEndTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val userProfileRepository = FakeUserProfileRepository(initialProfile = sampleProfile())
    private val progressRepository = FakeOnboardingProgressRepository()
    private val experienceRepository = FakeExperienceRepository()
    private val pastApplicationRepository = FakePastApplicationRepository()
    private val authRepository = FakeAuthRepository(loggedIn = true)
    private val reporter = RecordingErrorReporter()

    private var sessionEndedCount = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `401 은 배너로 막히지 않고 앱 셸까지 올라간다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401"))) }
        val viewModel = setStep1Content()

        composeRule.runOnIdle { viewModel.onStep1Event(OnboardingStep1Event.NextClicked) }
        composeRule.waitForIdle()

        assertEquals(1, sessionEndedCount)
        // 신호는 넘기면서 소비된다 — 되돌아온 화면이 같은 종료를 다시 올리지 않는다.
        assertFalse(viewModel.uiState.value.sessionEnded)
        composeRule.onNodeWithContentDescription(DISMISS_DESCRIPTION).assertDoesNotExist()
    }

    /** 반대 방향 — 만료가 아닌 실패는 화면에 남는다. 모든 실패를 이동으로 바꾸면 사용자가 이유를 못 본다. */
    @Test
    fun `만료가 아닌 실패는 배너로 남고 셸을 부르지 않는다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }
        val viewModel = setStep1Content()

        composeRule.runOnIdle { viewModel.onStep1Event(OnboardingStep1Event.NextClicked) }
        composeRule.waitForIdle()

        assertEquals(0, sessionEndedCount)
        composeRule.onNodeWithContentDescription(DISMISS_DESCRIPTION).assertIsDisplayed()
    }

    private fun setStep1Content(): OnboardingViewModel {
        val viewModel = createViewModel()
        composeRule.setContent {
            CareerCompassTheme {
                OnboardingStep1Screen(
                    viewModel = viewModel,
                    onNavigate = {},
                    onBack = {},
                    onSessionEnded = { sessionEndedCount++ },
                )
            }
        }
        return viewModel
    }

    private fun createViewModel() =
        OnboardingViewModel(
            resolveOnboardingEntry = ResolveOnboardingEntryUseCase(userProfileRepository, progressRepository),
            saveBasicInfo = SaveBasicInfoUseCase(userProfileRepository, progressRepository),
            saveJobPreferences = SaveJobPreferencesUseCase(userProfileRepository, progressRepository),
            getOnboardingExperiences = GetOnboardingExperiencesUseCase(experienceRepository),
            addExperience = AddExperienceUseCase(experienceRepository),
            updateExperience = UpdateExperienceUseCase(experienceRepository),
            deleteExperience = DeleteExperienceUseCase(experienceRepository),
            proceedToPastApplication = ProceedToPastApplicationUseCase(progressRepository),
            getOnboardingPastApplications = GetOnboardingPastApplicationsUseCase(pastApplicationRepository),
            uploadPastApplication = UploadPastApplicationUseCase(pastApplicationRepository),
            deletePastApplication = DeletePastApplicationUseCase(pastApplicationRepository),
            updatePastApplicationItemCategory = UpdatePastApplicationItemCategoryUseCase(pastApplicationRepository),
            completeOnboarding = CompleteOnboardingUseCase(progressRepository, userProfileRepository),
            authRepository = authRepository,
            errorReporter = reporter,
            savedStateHandle = SavedStateHandle(),
        )

    private fun sampleProfile() =
        UserProfile(
            id = 1L,
            name = "정일혁",
            school = "건국대학교",
            department = "컴퓨터공학부",
            gpa = 3.87,
            gradYear = 2027,
            jobInterests = listOf(JobInterest("backend", 1)),
            tags = listOf("AI"),
            onboardingDone = false,
            completion = 78,
        )

    private companion object {
        /** `onboarding_error_dismiss_description` — 배너가 떴는지를 닫기 버튼의 접근성 이름으로 가른다. */
        const val DISMISS_DESCRIPTION = "오류 안내 닫기"
    }
}

package com.careercompass.feature.onboarding.presentation.flow

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeAuthRepository
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperiencePrecision
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS
import com.careercompass.core.model.experience.MAX_EXPERIENCE_LINK_LENGTH
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAGS
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAG_LENGTH
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.model.user.SchoolCatalog
import com.careercompass.core.model.user.SchoolNameRules
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.model.user.UserProfileUpdate
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceEditorRules
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.PastApplicationItemCategoryEvent
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.core.ui.component.toDraft
import com.careercompass.core.ui.component.toEditorState
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.feature.onboarding.domain.model.OnboardingProgress
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
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
import com.careercompass.feature.onboarding.presentation.OnboardingStep2Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep3Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Event
import com.careercompass.feature.onboarding.presentation.complete.OnboardingCompleteEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelEvent
import com.careercompass.feature.onboarding.presentation.reporting.RecordingErrorReporter
import com.careercompass.feature.onboarding.presentation.shared.model.OnboardingFieldError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.Year

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val userProfileRepository = FakeUserProfileRepository(initialProfile = sampleProfile())
    private val progressRepository = FakeOnboardingProgressRepository()
    private val experienceRepository = FakeExperienceRepository()
    private val pastApplicationRepository = FakePastApplicationRepository()
    private val authRepository = FakeAuthRepository(loggedIn = true)
    private val reporter = RecordingErrorReporter()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
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
            savedStateHandle = savedStateHandle,
        )

    // ---- 진입·재개 ----

    @Test
    fun `기록이 없으면 첫 단계에 머물고 프로필로 프리필한다`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isResolvingEntry)
        assertTrue(state.isInputEnabled)
        assertNull(state.pendingNavigation)
        assertEquals("정일혁", state.userName)
        assertEquals("정일혁", state.step1.name)
        assertEquals("건국대학교", state.step1.school)
        assertEquals("컴퓨터공학부", state.step1.major)
        assertEquals("3.87", state.step1.gradePointAverage)
        assertEquals("2027", state.step1.graduationDate)
        assertEquals(listOf("backend", "frontend"), state.step2.selectedJobCodes)
        assertEquals(listOf("AI", "스타트업"), state.step2.interestTags)
    }

    @Test
    fun `중단된 단계가 있으면 그 단계로 이동하고 목록을 미리 읽는다`() {
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        experienceRepository.experiences += sampleExperience(id = 5L)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(OnboardingDestination.Step(OnboardingStep.Experience), state.pendingNavigation)
        assertTrue(state.step3.isLoaded)
        assertEquals(listOf(5L), state.step3.experiences.map(Experience::id))
    }

    @Test
    fun `이미 완료한 사용자는 피드로 보낸다`() {
        userProfileRepository.profileState.value = sampleProfile(onboardingDone = true)

        val viewModel = createViewModel()

        assertEquals(OnboardingDestination.Feed, viewModel.uiState.value.pendingNavigation)
    }

    /**
     * #335 — 이 ViewModel 은 온보딩 스택 전체와 수명을 같이하므로 지문 화면에서 「다른 방법으로 로그인」을 거쳐
     * 다음 계정이 들어와도 살아 있다. 앞 계정으로 낸 판정을 들고 있으면 그 계정이 Step 1 대신 피드로 들어가고
     * 폼에는 앞 계정의 이름이 채워진다.
     */
    @Test
    fun `세션이 바뀌면 앞 세션으로 낸 진입 판정을 버린다`() {
        userProfileRepository.profileState.value = sampleProfile(onboardingDone = true)
        val viewModel = createViewModel()
        assertEquals(OnboardingDestination.Feed, viewModel.uiState.value.pendingNavigation)
        assertEquals("정일혁", viewModel.uiState.value.step1.name)

        authRepository.sessionGenerationState.value += 1

        val state = viewModel.uiState.value
        assertNull(state.pendingNavigation)
        assertNull(state.userName)
        assertEquals("", state.step1.name)
        assertTrue(state.isInputEnabled)
    }

    @Test
    fun `프로필 갱신 실패는 진입을 막지 않고 기록만 한다`() {
        userProfileRepository.onRefreshProfile = { Result.failure(IOException("offline")) }

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isResolvingEntry)
        assertEquals("정일혁", viewModel.uiState.value.step1.name)
        assertEquals(listOf("resolve_entry"), reporter.stages())
        assertNull(viewModel.uiState.value.failure)
    }

    // ---- Step 1 ----

    @Test
    fun `입력 중 검증은 길이와 형식만 본다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NameChanged("가".repeat(21)))
        viewModel.onStep1Event(OnboardingStep1Event.MajorChanged(""))
        viewModel.onStep1Event(OnboardingStep1Event.GradePointAverageChanged("abc"))

        val form = viewModel.uiState.value.step1
        assertEquals(OnboardingFieldError.TooLong(20), form.nameError)
        assertNull(form.majorError)
        assertEquals(OnboardingFieldError.InvalidFormat, form.gradePointAverageError)

        viewModel.onStep1Event(OnboardingStep1Event.GradePointAverageChanged("4.6"))
        assertEquals(OnboardingFieldError.OutOfRange, viewModel.uiState.value.step1.gradePointAverageError)
    }

    @Test
    fun `다음을 누르면 필수 값을 검사하고 저장하지 않는다`() {
        userProfileRepository.profileState.value = null
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NameChanged("정일혁"))
        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        val form = viewModel.uiState.value.step1
        assertEquals(OnboardingFieldError.Required, form.schoolError)
        assertEquals(OnboardingFieldError.Required, form.majorError)
        assertNull(form.nameError)
        assertTrue(userProfileRepository.updates.isEmpty())
        assertNull(viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `기본 정보 저장은 프로필 수정 후 Step 2 로 이동한다`() {
        val viewModel = createViewModel()
        viewModel.onStep1Event(OnboardingStep1Event.NameChanged(" 정일혁 "))
        viewModel.onStep1Event(OnboardingStep1Event.GradePointAverageChanged("3.5"))
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.SchoolSelected("연세대학교"))

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        assertEquals(
            listOf(UserProfileUpdate(name = "정일혁", school = "연세대학교", department = "컴퓨터공학부", gpa = 3.5, gradYear = 2027)),
            userProfileRepository.updates,
        )
        assertEquals(listOf(OnboardingStep.JobPreference), progressRepository.savedSteps)
        val state = viewModel.uiState.value
        assertEquals(OnboardingDestination.Step(OnboardingStep.JobPreference), state.pendingNavigation)
        assertFalse(state.isSubmitting)
        assertEquals("정일혁", state.userName)
    }

    @Test
    fun `저장 실패는 사유를 표시하고 단계를 기록한다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        val state = viewModel.uiState.value
        assertEquals(OnboardingFailureReason.Network, state.failure)
        assertFalse(state.isSubmitting)
        assertNull(state.pendingNavigation)
        assertEquals(listOf("save_basic_info"), reporter.stages())
        assertTrue(progressRepository.savedSteps.isEmpty())

        viewModel.onFailureConsumed()
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `저장 중에는 입력을 잠근다`() {
        val gate = CompletableDeferred<Unit>()
        userProfileRepository.onUpdateProfile = { update ->
            gate.await()
            Result.success(sampleProfile().copy(name = update.name))
        }
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isInputEnabled)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `학교 피커는 검색어로 목록을 거르고 선택하면 닫힌다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        assertEquals(
            SchoolCatalog.schools,
            viewModel.uiState.value.schoolPicker
                ?.results,
        )

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.QueryChanged("건국"))
        assertEquals(
            listOf("건국대학교"),
            viewModel.uiState.value.schoolPicker
                ?.results,
        )

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.SchoolSelected("건국대학교"))
        assertNull(viewModel.uiState.value.schoolPicker)
        assertEquals("건국대학교", viewModel.uiState.value.step1.school)

        viewModel.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.Dismissed)
        assertNull(viewModel.uiState.value.schoolPicker)
    }

    /**
     * 목록 40개에 없는 학교를 쓰는 사용자가 Step 1 을 통과할 수 있어야 한다(#138) — 여기가 막히면 앱 전체가 막힌다.
     */
    @Test
    fun `목록에 없는 학교는 직접 입력으로 정할 수 있다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.QueryChanged("서울예술"))
        val searching = viewModel.uiState.value.schoolPicker
        assertEquals(emptyList<String>(), searching?.results)
        assertTrue(searching?.isDirectInputOffered == true)

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputRequested)
        assertEquals(
            "서울예술",
            viewModel.uiState.value.schoolPicker
                ?.directInput
                ?.value,
        )

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputChanged("  서울예술   대학교 "))
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputConfirmed)

        assertNull(viewModel.uiState.value.schoolPicker)
        assertEquals("서울예술 대학교", viewModel.uiState.value.step1.school)
        assertNull(viewModel.uiState.value.step1.schoolError)

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)
        assertEquals(
            listOf(UserProfileUpdate(name = "정일혁", school = "서울예술 대학교", department = "컴퓨터공학부", gpa = 3.87, gradYear = 2027)),
            userProfileRepository.updates,
        )
    }

    @Test
    fun `직접 입력은 공백만이거나 상한을 넘으면 확정되지 않는다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.QueryChanged("없는대"))
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputRequested)

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputChanged("   "))
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputConfirmed)
        assertEquals(
            ProfileFieldViolation.Required,
            viewModel.uiState.value.schoolPicker
                ?.directInput
                ?.error,
        )
        assertEquals("건국대학교", viewModel.uiState.value.step1.school)

        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputChanged("가".repeat(SchoolNameRules.MAX_LENGTH + 1)))
        assertEquals(
            ProfileFieldViolation.TooLong(SchoolNameRules.MAX_LENGTH),
            viewModel.uiState.value.schoolPicker
                ?.directInput
                ?.error,
        )
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputConfirmed)
        assertNotNull(viewModel.uiState.value.schoolPicker)
        assertEquals("건국대학교", viewModel.uiState.value.step1.school)
    }

    @Test
    fun `직접 입력을 접으면 목록으로 돌아가고 시트는 열려 있다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.QueryChanged("건국"))
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputRequested)
        viewModel.onSchoolPickerEvent(SchoolPickerEvent.DirectInputCancelled)

        val picker = viewModel.uiState.value.schoolPicker
        assertNotNull(picker)
        assertNull(picker?.directInput)
        assertEquals(listOf("건국대학교"), picker?.results)
    }

    /** 서버에서 프리필된 값에 잉여 공백이 있어도 저장 모양은 목록 선택과 같아야 한다. */
    @Test
    fun `프리필된 학교도 저장 직전에 같은 규칙으로 다듬는다`() {
        userProfileRepository.profileState.value = sampleProfile().copy(school = "  건국  대학교 ")
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        assertEquals("건국 대학교", userProfileRepository.updates.single().school)
    }

    @Test
    fun `졸업 피커는 입력값을 기본으로 열고 확정하면 YYYY점MM 을 채운다`() {
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.GraduationDatePickerClicked)
        val picker = viewModel.uiState.value.graduationPicker
        assertNotNull(picker)
        assertEquals(2027, picker!!.selectedYear)
        assertEquals(2, picker.selectedMonth)
        assertEquals(2000, picker.years.first())
        assertTrue(picker.years.contains(Year.now().value))

        viewModel.onGraduationPickerEvent(GraduationDatePickerEvent.YearSelected(2026))
        viewModel.onGraduationPickerEvent(GraduationDatePickerEvent.MonthSelected(8))
        viewModel.onGraduationPickerEvent(GraduationDatePickerEvent.Confirmed)

        assertNull(viewModel.uiState.value.graduationPicker)
        assertEquals("2026.08", viewModel.uiState.value.step1.graduationDate)
    }

    // ---- Step 2 ----

    @Test
    fun `직무는 세 개까지만 고르고 다시 누르면 해제된다`() {
        val viewModel = createViewModel()

        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("mobile"))
        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("qa"))
        assertEquals(listOf("backend", "frontend", "mobile"), viewModel.uiState.value.step2.selectedJobCodes)

        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("backend"))
        assertEquals(listOf("frontend", "mobile"), viewModel.uiState.value.step2.selectedJobCodes)

        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("unknown"))
        assertEquals(listOf("frontend", "mobile"), viewModel.uiState.value.step2.selectedJobCodes)
    }

    @Test
    fun `태그는 제출 시 정규화해 추가하고 중복과 공백은 거부한다`() {
        val viewModel = createViewModel()

        viewModel.onStep2Event(OnboardingStep2Event.InterestInputChanged(" #환경 "))
        viewModel.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)
        assertEquals(listOf("AI", "스타트업", "환경"), viewModel.uiState.value.step2.interestTags)
        assertEquals("", viewModel.uiState.value.step2.interestInput)

        viewModel.onStep2Event(OnboardingStep2Event.InterestInputChanged("AI"))
        viewModel.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)
        assertEquals(listOf("AI", "스타트업", "환경"), viewModel.uiState.value.step2.interestTags)

        viewModel.onStep2Event(OnboardingStep2Event.InterestInputChanged("   "))
        viewModel.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)
        assertEquals(3, viewModel.uiState.value.step2.interestTags.size)

        viewModel.onStep2Event(OnboardingStep2Event.InterestTagRemoved("AI"))
        assertEquals(listOf("스타트업", "환경"), viewModel.uiState.value.step2.interestTags)
    }

    @Test
    fun `태그가 다섯 개면 더 추가할 수 없다고 알린다`() {
        val viewModel = createViewModel()
        listOf("환경", "교육", "핀테크").forEach { tag ->
            viewModel.onStep2Event(OnboardingStep2Event.InterestInputChanged(tag))
            viewModel.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)
        }
        assertEquals(5, viewModel.uiState.value.step2.interestTags.size)

        viewModel.onStep2Event(OnboardingStep2Event.InterestInputChanged("여섯"))
        viewModel.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)

        assertEquals(5, viewModel.uiState.value.step2.interestTags.size)
        assertEquals(OnboardingFailureReason.LimitExceeded(FailureSurface.Unspecified), viewModel.uiState.value.failure)
    }

    @Test
    fun `직무 선호 저장은 우선순위 순서로 보내고 Step 3 로 이동한다`() {
        val viewModel = createViewModel()
        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("backend"))
        viewModel.onStep2Event(OnboardingStep2Event.JobSelectionToggled("backend"))

        viewModel.onStep2Event(OnboardingStep2Event.NextClicked)

        assertEquals(
            listOf(JobInterest("frontend", 1), JobInterest("backend", 2)),
            userProfileRepository.replacedJobInterests.single(),
        )
        assertEquals(listOf("AI", "스타트업"), userProfileRepository.replacedTags.single())
        assertEquals(listOf(OnboardingStep.Experience), progressRepository.savedSteps)
        assertEquals(OnboardingDestination.Step(OnboardingStep.Experience), viewModel.uiState.value.pendingNavigation)
        assertTrue(viewModel.uiState.value.step3.isLoaded)
    }

    @Test
    fun `직무 선호 저장 실패는 이동하지 않는다`() {
        userProfileRepository.onReplaceTags = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", IOException("500"))) }
        val viewModel = createViewModel()

        viewModel.onStep2Event(OnboardingStep2Event.NextClicked)

        assertEquals(OnboardingFailureReason.Server, viewModel.uiState.value.failure)
        assertNull(viewModel.uiState.value.pendingNavigation)
        assertEquals(listOf("save_job_preferences"), reporter.stages())
    }

    // ---- Step 3 ----

    @Test
    fun `경험 추가 시트는 선택한 유형으로 열리고 검증 실패를 필드에 돌려준다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.ExperienceTypeSelected("intern"))

        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        assertEquals(
            ExperienceType.Intern,
            viewModel.uiState.value.experienceEditor
                ?.type,
        )

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("카카오 인턴"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val editor = viewModel.uiState.value.experienceEditor
        assertNotNull(editor)
        assertEquals(ProfileFieldViolation.Required, editor!!.startDateError)
        assertEquals(ProfileFieldViolation.Required, editor.primaryError)
        assertEquals(ProfileFieldViolation.Required, editor.secondaryError)
        assertTrue(experienceRepository.createdDrafts.isEmpty())

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.13"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)
        assertEquals(
            ProfileFieldViolation.InvalidFormat,
            viewModel.uiState.value.experienceEditor
                ?.startDateError,
        )
    }

    @Test
    fun `경험을 등록하면 목록 맨 앞에 넣고 시트를 닫는다`() {
        val viewModel = createViewModel()
        experienceRepository.experiences += sampleExperience(id = 1L)
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Intern))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("카카오 인턴"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.01"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.EndDateChanged("2025.02"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("카카오"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.SecondaryChanged("안드로이드 개발"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val draft = experienceRepository.createdDrafts.single()
        assertEquals("카카오 인턴", draft.title)
        // 칸이 `YYYY.MM` 이라 새 카드의 시점은 연월 정밀도다 — 없는 일을 지어내지 않는다.
        assertEquals(ExperiencePoint.YearMonth(2025, 1), draft.startPoint)
        assertEquals(ExperiencePoint.YearMonth(2025, 2), draft.endPoint)
        assertEquals(ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = null), draft.details)
        val state = viewModel.uiState.value
        assertNull(state.experienceEditor)
        assertEquals(ExperienceType.Intern, state.step3.selectedType)
        assertEquals(
            "카카오 인턴",
            state.step3.experiences
                .first()
                .title,
        )
    }

    @Test
    fun `경험 등록 실패는 시트를 열어 둔 채 사유를 알린다`() {
        experienceRepository.onCreateExperience = { Result.failure(CoreDataFailure.LimitExceeded("LIMIT_EXCEEDED", IOException("422"))) }
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Award))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("공모전"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("대상"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val state = viewModel.uiState.value
        assertNotNull(state.experienceEditor)
        assertFalse(state.experienceEditor!!.isSubmitting)
        assertEquals(OnboardingFailureReason.LimitExceeded(FailureSurface.ExperienceCard), state.failure)
        assertEquals(listOf("add_experience"), reporter.stages())
    }

    @Test
    fun `제출 중에는 시트를 닫지 않는다`() {
        val gate = CompletableDeferred<Unit>()
        experienceRepository.onCreateExperience = { draft ->
            gate.await()
            Result.success(sampleCard(id = 5L, details = draft.details).copy(title = draft.title))
        }
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Award))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("공모전"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("대상"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)
        assertTrue(submittingEditor(viewModel))

        // 스크림 탭·스와이프·뒤로가기가 모두 이 이벤트로 들어온다.
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Dismissed)

        assertTrue(submittingEditor(viewModel))

        gate.complete(Unit)
        assertNull(viewModel.uiState.value.experienceEditor)
        assertEquals(listOf("공모전"), experienceTitles(viewModel))
    }

    @Test
    fun `늦게 온 응답은 그 사이 연 다른 시트를 건드리지 않는다`() {
        val gate = CompletableDeferred<Unit>()
        experienceRepository.onCreateExperience = { draft ->
            gate.await()
            Result.success(sampleCard(id = 5L, details = draft.details).copy(title = draft.title))
        }
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Award))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("공모전"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("대상"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        // 앞 제출이 끝나기 전에 다음 시트가 열린 상태. 여기 친 글자를 늦은 응답이 지우면 안 된다.
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("두 번째 카드"))

        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals("두 번째 카드", state.experienceEditor?.title)
        assertEquals(listOf("공모전"), experienceTitles(viewModel))
    }

    @Test
    fun `카드를 누르면 기존 값이 채워진 수정 시트가 열리고 유형은 바뀌지 않는다`() {
        experienceRepository.experiences += internExperience(id = 3L)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("3"))

        val editor = viewModel.uiState.value.experienceEditor
        assertNotNull(editor)
        assertEquals(3L, editor!!.experienceId)
        assertTrue(editor.isEditing)
        assertEquals(ExperienceType.Intern, editor.type)
        assertEquals("카카오 인턴", editor.title)
        assertEquals("2025.01", editor.startDate)
        assertEquals("2025.02", editor.endDate)
        assertEquals("카카오", editor.primary)
        assertEquals("안드로이드 개발", editor.secondary)

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Award))

        assertEquals(
            ExperienceType.Intern,
            viewModel.uiState.value.experienceEditor
                ?.type,
        )
    }

    @Test
    fun `수정 저장은 그 카드만 갱신하고 시트에 없는 값은 보존한다`() {
        experienceRepository.experiences += sampleExperience(id = 1L)
        experienceRepository.experiences += sampleExperience(id = 2L)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("2"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("CareerCompass 리뉴얼"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val state = viewModel.uiState.value
        assertNull(state.experienceEditor)
        assertEquals(listOf(1L, 2L), state.step3.experiences.map(Experience::id))
        val updated = state.step3.experiences.first { it.id == 2L }
        assertEquals("CareerCompass 리뉴얼", updated.title)
        // 손대지 않은 기술 태그는 수정 저장에 쓸려 나가지 않는다 — #139 이후로는 시트 왕복이 값을 그대로 실어 오는 것이 그 근거다.
        assertEquals(listOf("Kotlin"), (updated.details as ExperienceDetails.Project).techs)
        assertTrue(experienceRepository.createdDrafts.isEmpty())
    }

    @Test
    fun `카드의 상세 필드는 시트를 왕복해도 그대로다`() {
        // 「시트가 모르는 필드를 저장 때 지우지 않는다」를 물려받기 대신 이 무손실 왕복이 지킨다(#139).
        // 수상의 `contestName` 은 시트에서 제목 칸 그 자체라 제목을 공모전명으로 둔다.
        val cards =
            listOf(
                ExperienceDetails.Project(role = "안드로이드", techs = listOf("Kotlin", "Compose"), summary = "요약", link = "https://a.io"),
                ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = "주관사"),
                ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = "주요 업무"),
                ExperienceDetails.Activity(organization = "동아리", role = "기획팀장", summary = "성과"),
                ExperienceDetails.Certificate(issuer = "한국산업인력공단"),
            )

        cards.forEach { details ->
            val card =
                sampleCard(
                    startPoint = startPointFor(details.type),
                    endPoint = endPointFor(details.type),
                    details = details,
                )

            assertEquals(details, card.toEditorState().toDraft().details)
        }
    }

    @Test
    fun `빈 카드는 시트를 왕복해도 없던 값이 생기지 않는다`() {
        // #139 의 왕복은 「지우지 않는다」(무손실)를 지킨다. 이 왕복은 그 반대편인 「만들지 않는다」(무생성)를 지킨다 —
        // 열었다 저장만 했는데 사용자가 준 적 없는 값이 서버에 남으면 이후 정렬·표시가 그걸 사실로 취급한다(#166).
        // 프로젝트·인턴은 모델(`ExperienceDraft`)이 시작일을 필수로 두므로 빈 카드에도 시작일만은 있다.
        val cards =
            listOf(
                sampleCard(
                    startPoint = ExperiencePoint.YearMonth(2025, 6),
                    details = ExperienceDetails.Project(role = null, techs = emptyList(), summary = null, link = null),
                ),
                sampleCard(details = ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = null)),
                // 이슈 #166 그 자체 — 연도만 있고 날짜가 없는 수상 카드.
                sampleCard(
                    startPoint = ExperiencePoint.Year(2025),
                    details = ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = null),
                ),
                sampleCard(
                    startPoint = ExperiencePoint.YearMonth(2025, 6),
                    details = ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = null),
                ),
                sampleCard(details = ExperienceDetails.Activity(organization = "동아리", role = null, summary = null)),
                sampleCard(details = ExperienceDetails.Certificate(issuer = null)),
                // 취득 연월만 있는 자격증도 같은 함정이었다 — 연월을 그 달 1일로 넓혀 없던 날짜를 만들었다.
                sampleCard(startPoint = ExperiencePoint.YearMonth(2025, 6), details = ExperienceDetails.Certificate(issuer = null)),
            )

        cards.forEach { card ->
            val draft = card.toEditorState().toDraft()
            assertEquals(card.details, draft.details)
            assertEquals(card.details.type.toString(), card.startPoint, draft.startPoint)
            assertNull(card.details.type.toString(), draft.endPoint)
        }
    }

    @Test
    fun `카드의 날짜는 시트를 왕복해도 일까지 그대로다`() {
        // #139 의 왕복은 「있던 값을 지우지 않는다」를, #166 의 왕복은 「없던 값을 만들지 않는다」를 지킨다.
        // 이 왕복은 셋째 축인 **「있던 값을 바꾸지 않는다」**다 — 시점 칸이 월 정밀도라 서버가 준 15일이 저장 때
        // 1일로 깎였다(#171). 다섯 유형을 전부 돌린다.
        val cards =
            listOf(
                ExperienceDetails.Project(role = "안드로이드", techs = listOf("Kotlin"), summary = "요약", link = "https://a.io"),
                ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = "주관사"),
                ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = "주요 업무"),
                ExperienceDetails.Activity(organization = "동아리", role = "기획팀장", summary = "성과"),
                ExperienceDetails.Certificate(issuer = "한국산업인력공단"),
            ).map { sampleCard(startPoint = startPointFor(it.type), endPoint = endPointFor(it.type), details = it) }

        cards.forEach { card ->
            val draft = card.toEditorState().toDraft()
            val label = card.type.toString()
            assertEquals(label, card.details, draft.details)
            // 유형마다 정밀도가 다르지만 규칙은 하나다 — 시트를 왕복해도 그 카드가 아는 만큼 그대로 남는다.
            assertEquals(label, card.startPoint, draft.startPoint)
            assertEquals(label, card.endPoint, draft.endPoint)
        }
    }

    @Test
    fun `일자가 있는 카드는 제목만 고쳐 저장해도 날짜의 일이 깎이지 않는다`() {
        // 이슈 #171 그 자체 — 서버나 다른 클라이언트가 만든 `2025-06-15` 짜리 카드를 열었다 저장만 하는 경로다.
        experienceRepository.experiences +=
            sampleCard(
                id = 5L,
                startPoint = ExperiencePoint.Date(LocalDate.of(2025, 6, 15)),
                endPoint = ExperiencePoint.Date(LocalDate.of(2025, 8, 20)),
                details = ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = null),
            )
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("5"))
        // 칸은 `YYYY.MM` 이라 15일을 그릴 수단이 없다 — 화면은 달까지만 보여 준다.
        assertEquals(
            "2025.06",
            viewModel.uiState.value.experienceEditor
                ?.startDate,
        )
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("카카오 인턴"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val updated =
            viewModel.uiState.value.step3.experiences
                .single { it.id == 5L }
        assertEquals(ExperiencePoint.Date(LocalDate.of(2025, 6, 15)), updated.startPoint)
        assertEquals(ExperiencePoint.Date(LocalDate.of(2025, 8, 20)), updated.endPoint)
    }

    @Test
    fun `시점 칸을 실제로 고치면 사용자가 준 정밀도로 바뀐다`() {
        // 지키는 것은 「손대지 않은 값」뿐이다. 달을 고쳤으면 원본의 일은 더 이상 같은 시점이 아니라 버린다.
        experienceRepository.experiences +=
            sampleCard(
                id = 6L,
                startPoint = ExperiencePoint.Date(LocalDate.of(2025, 6, 15)),
                endPoint = ExperiencePoint.Date(LocalDate.of(2025, 8, 20)),
                details = ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = null),
            )
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("6"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.07"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val updated =
            viewModel.uiState.value.step3.experiences
                .single { it.id == 6L }
        // 사용자가 준 정밀도는 연월이다 — 없는 일을 지어내 채우지 않는다.
        assertEquals(ExperiencePoint.YearMonth(2025, 7), updated.startPoint)
        // 손대지 않은 종료일의 20일은 그대로 남는다.
        assertEquals(ExperiencePoint.Date(LocalDate.of(2025, 8, 20)), updated.endPoint)
    }

    @Test
    fun `지켜 낸 일과 새로 친 달이 같은 달이면 둘 다 남는다`() {
        // 6월 20일 시작을 그대로 두고 종료만 6월로 당긴 경우다. 예전에는 지켜 낸 20일 때문에 「종료가 시작보다
        // 빠르다」가 되어 시작의 일을 버렸지만, 이제 모델이 두 시점을 **더 굵은 쪽 정밀도로** 견주므로
        // (`ExperiencePoint.isBefore`) 어긋나지 않는다 — 종료가 말한 것은 달까지뿐이다. 버릴 값이 없다.
        val editor =
            sampleCard(
                startPoint = ExperiencePoint.Date(LocalDate.of(2025, 6, 20)),
                endPoint = ExperiencePoint.Date(LocalDate.of(2025, 8, 10)),
                details = ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = null),
            ).toEditorState()

        val draft = editor.copy(endDate = "2025.06").toDraft()

        assertEquals(ExperiencePoint.Date(LocalDate.of(2025, 6, 20)), draft.startPoint)
        assertEquals(ExperiencePoint.YearMonth(2025, 6), draft.endPoint)
    }

    @Test
    fun `연도만 있는 수상 카드는 열었다 저장해도 날짜가 생기지 않는다`() {
        experienceRepository.experiences +=
            sampleCard(
                id = 4L,
                startPoint = ExperiencePoint.Year(2025),
                details = ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = null),
            )
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("4"))

        // 연도는 연도 그대로 연다 — 「2025.01」로 열면 사용자가 준 적 없는 1월이 화면에도 뜬다.
        assertEquals(
            "2025",
            viewModel.uiState.value.experienceEditor
                ?.startDate,
        )

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("공모전 대상"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val updated =
            viewModel.uiState.value.step3.experiences
                .single { it.id == 4L }
        // 시점의 정본은 이제 기간 한 곳이다 — 연 정밀도 그대로 남고 월·일은 생기지 않는다.
        assertEquals(ExperiencePoint.Year(2025), updated.startPoint)
    }

    @Test
    fun `수상 시점은 연도 칸으로 받아 연도에만 적는다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Award))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("공모전"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("대상"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.13"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        assertEquals(
            ProfileFieldViolation.InvalidFormat,
            viewModel.uiState.value.experienceEditor
                ?.startDateError,
        )
        assertTrue(experienceRepository.createdDrafts.isEmpty())

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val draft = experienceRepository.createdDrafts.single()
        // 수상에는 기간이 없다 — 시점 하나가 연 정밀도로 남는다.
        assertEquals(ExperiencePoint.Year(2025), draft.startPoint)
        assertNull(draft.endPoint)
        assertEquals(ExperienceDetails.Award(contestName = "공모전", rank = "대상", organizer = null), draft.details)
    }

    @Test
    fun `상세 값이 있는 카드는 자세히를 펼친 채로 연다`() {
        experienceRepository.experiences += sampleExperience(id = 1L)
        experienceRepository.experiences +=
            Experience(
                id = 2L,
                title = "무지개 동아리",
                startPoint = ExperiencePoint.YearMonth(2025, 3),
                endPoint = null,
                details = ExperienceDetails.Activity(organization = "무지개", role = null, summary = null),
                createdAt = null,
            )
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("1"))
        val withDetails = viewModel.uiState.value.experienceEditor
        assertNotNull(withDetails)
        assertTrue(withDetails!!.isDetailExpanded)
        assertEquals(listOf("Kotlin"), withDetails.techs)

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Dismissed)
        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("2"))

        // 채울 것이 없는 카드까지 펼쳐 열면 선택 단계인 Step 3 가 괜히 길어 보인다.
        assertFalse(
            viewModel.uiState.value.experienceEditor
                ?.isDetailExpanded ?: true,
        )
    }

    @Test
    fun `프로젝트 상세는 기술 태그와 링크를 실어 보낸다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("CareerCompass"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.09"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("안드로이드"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.LinkChanged("https://github.com/Team-CamBridge"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("#Kotlin "))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        // 대소문자만 다른 태그는 같은 기술로 보고 먼저 친 표기를 남긴다.
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("kotlin"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("Compose"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        // 입력칸에 남은 글자도 제출이 태그로 확정한다.
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("Hilt"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val draft = experienceRepository.createdDrafts.single()
        assertEquals(
            ExperienceDetails.Project(
                role = "안드로이드",
                techs = listOf("Kotlin", "Compose", "Hilt"),
                summary = null,
                link = "https://github.com/Team-CamBridge",
            ),
            draft.details,
        )
    }

    @Test
    fun `기술 태그는 개수와 길이 상한을 넘으면 필드 오류로 막힌다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        repeat(MAX_EXPERIENCE_TECH_TAGS) { index ->
            viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("tech$index"))
            viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        }
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("overflow"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)

        val overflowed = viewModel.uiState.value.experienceEditor
        assertNotNull(overflowed)
        assertEquals(MAX_EXPERIENCE_TECH_TAGS, overflowed!!.techs.size)
        assertEquals(ProfileFieldViolation.OutOfRange, overflowed.techInputError)
        // 상한에 걸린 글자는 입력칸에 남는다 — 하나 지우고 다시 완료를 누르면 그대로 들어간다.
        assertEquals("overflow", overflowed.techInput)

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagRemoved("tech0"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        assertEquals(
            "overflow",
            viewModel.uiState.value.experienceEditor
                ?.techs
                ?.last(),
        )

        viewModel.onExperienceEditorEvent(
            ExperienceQuickAddEvent.TechInputChanged("a".repeat(MAX_EXPERIENCE_TECH_TAG_LENGTH + 1)),
        )
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        assertEquals(
            ProfileFieldViolation.TooLong(MAX_EXPERIENCE_TECH_TAG_LENGTH),
            viewModel.uiState.value.experienceEditor
                ?.techInputError,
        )
    }

    @Test
    fun `링크는 http https 절대 주소만 받는다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("CareerCompass"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.09"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.LinkChanged("javascript:alert(1)"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        assertEquals(
            ProfileFieldViolation.InvalidFormat,
            viewModel.uiState.value.experienceEditor
                ?.linkError,
        )
        assertTrue(experienceRepository.createdDrafts.isEmpty())

        viewModel.onExperienceEditorEvent(
            ExperienceQuickAddEvent.LinkChanged("https://example.com/" + "a".repeat(MAX_EXPERIENCE_LINK_LENGTH)),
        )
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)
        assertEquals(
            ProfileFieldViolation.TooLong(MAX_EXPERIENCE_LINK_LENGTH),
            viewModel.uiState.value.experienceEditor
                ?.linkError,
        )

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.LinkChanged("https://example.com/demo"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)
        assertEquals(
            "https://example.com/demo",
            (experienceRepository.createdDrafts.single().details as ExperienceDetails.Project).link,
        )
    }

    @Test
    fun `접힌 상세에서 난 오류는 영역을 펼쳐 보여 준다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("CareerCompass"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.09"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.LinkChanged("github.com/foo"))
        // 링크를 치고 영역을 다시 접은 상태에서 제출한다.
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.DetailSectionToggled)
        assertTrue(
            viewModel.uiState.value.experienceEditor
                ?.isDetailExpanded == true,
        )
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.DetailSectionToggled)

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val editor = viewModel.uiState.value.experienceEditor
        assertNotNull(editor)
        assertEquals(ProfileFieldViolation.InvalidFormat, editor!!.linkError)
        // 접힌 채로 막히면 사용자에게는 「버튼이 안 먹는다」로만 보인다.
        assertTrue(editor.isDetailExpanded)
        assertTrue(experienceRepository.createdDrafts.isEmpty())
    }

    @Test
    fun `인턴과 대외활동의 상세는 각각 업무 요약과 역할로 저장된다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Intern))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("카카오 인턴"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.01"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("카카오"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.SecondaryChanged("안드로이드 개발"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.DetailChanged(" 공고 피드 화면 개발 "))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        assertEquals(
            ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = "공고 피드 화면 개발"),
            experienceRepository.createdDrafts.single().details,
        )

        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Activity))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("멋쟁이사자처럼"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("멋사"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.SecondaryChanged("해커톤 최우수상"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.DetailChanged("기획팀장"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        assertEquals(
            ExperienceDetails.Activity(organization = "멋사", role = "기획팀장", summary = "해커톤 최우수상"),
            experienceRepository.createdDrafts.last().details,
        )
    }

    @Test
    fun `유형을 바꿔도 이전 값은 시트에 남고 저장에서만 빠진다`() {
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TitleChanged("CareerCompass"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.StartDateChanged("2025.09"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechInputChanged("Kotlin"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TechTagSubmitted)
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.LinkChanged("https://example.com"))

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.TypeSelected(ExperienceType.Activity))

        // 칩을 잘못 눌렀다 되돌아오는 사용자를 위해 값은 지우지 않는다.
        val switched = viewModel.uiState.value.experienceEditor
        assertNotNull(switched)
        assertEquals(listOf("Kotlin"), switched!!.techs)
        assertEquals("https://example.com", switched.link)

        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.PrimaryChanged("무지개"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.DetailChanged("기획팀장"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        // 대외활동에는 기술 태그·링크 자리가 없어 저장에서 빠진다.
        assertEquals(
            ExperienceDetails.Activity(organization = "무지개", role = "기획팀장", summary = null),
            experienceRepository.createdDrafts.single().details,
        )
    }

    @Test
    fun `수정 실패는 시트를 열어 둔 채 사유를 알린다`() {
        experienceRepository.experiences += sampleExperience(id = 1L)
        experienceRepository.onUpdateExperience = { _, _ -> Result.failure(IOException("offline")) }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceSelected("1"))
        viewModel.onExperienceEditorEvent(ExperienceQuickAddEvent.Submitted)

        val state = viewModel.uiState.value
        assertNotNull(state.experienceEditor)
        assertFalse(state.experienceEditor!!.isSubmitting)
        assertEquals(OnboardingFailureReason.Network, state.failure)
        assertEquals(listOf("update_experience"), reporter.stages())
        assertEquals(
            "CareerCompass",
            state.step3.experiences
                .single()
                .title,
        )
    }

    @Test
    fun `삭제는 확인 다이얼로그를 거쳐 목록에서 뺀다`() {
        experienceRepository.experiences += sampleExperience(id = 1L)
        experienceRepository.experiences += sampleExperience(id = 2L)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceDeleteClicked("1"))
        val pending = viewModel.uiState.value.experienceDelete
        assertNotNull(pending)
        assertEquals(1L, pending!!.experienceId)
        assertEquals("CareerCompass", pending.title)

        viewModel.onExperienceDeleteEvent(ExperienceDeleteEvent.Dismissed)
        assertNull(viewModel.uiState.value.experienceDelete)
        assertEquals(2, viewModel.uiState.value.step3.experiences.size)

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceDeleteClicked("1"))
        viewModel.onExperienceDeleteEvent(ExperienceDeleteEvent.Confirmed)

        val state = viewModel.uiState.value
        assertNull(state.experienceDelete)
        assertEquals(listOf(2L), state.step3.experiences.map(Experience::id))
        assertTrue(experienceRepository.experiences.none { it.id == 1L })
    }

    @Test
    fun `삭제 실패는 카드를 원래 자리에 되돌리고 사유를 알린다`() {
        experienceRepository.experiences += sampleExperience(id = 1L)
        experienceRepository.experiences += sampleExperience(id = 2L)
        experienceRepository.experiences += sampleExperience(id = 3L)
        experienceRepository.onDeleteExperience = {
            Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", IOException("500")))
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.ExperienceDeleteClicked("2"))
        viewModel.onExperienceDeleteEvent(ExperienceDeleteEvent.Confirmed)

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.step3.experiences.map(Experience::id))
        assertEquals(OnboardingFailureReason.Server, state.failure)
        assertEquals(listOf("delete_experience"), reporter.stages())
    }

    @Test
    fun `상한에 닿으면 추가를 막고 하나를 지우면 다시 열린다`() {
        repeat(MAX_EXPERIENCE_CARDS) { index -> experienceRepository.experiences += sampleExperience(id = index + 1L) }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)

        assertNull(viewModel.uiState.value.experienceEditor)
        assertEquals(OnboardingFailureReason.LimitExceeded(FailureSurface.ExperienceCard), viewModel.uiState.value.failure)

        viewModel.onFailureConsumed()
        viewModel.onStep3Event(OnboardingStep3Event.ExperienceDeleteClicked("1"))
        viewModel.onExperienceDeleteEvent(ExperienceDeleteEvent.Confirmed)
        viewModel.onStep3Event(OnboardingStep3Event.AddExperienceClicked)

        assertEquals(MAX_EXPERIENCE_CARDS - 1, viewModel.uiState.value.step3.experiences.size)
        assertNotNull(viewModel.uiState.value.experienceEditor)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `Step 3 다음은 진행 기록만 바꾸고 Step 4 로 이동하며 목록을 미리 읽는다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 3)
        val viewModel = createViewModel()

        viewModel.onStep3Event(OnboardingStep3Event.NextClicked)

        assertEquals(listOf(OnboardingStep.PastApplication), progressRepository.savedSteps)
        val state = viewModel.uiState.value
        assertEquals(OnboardingDestination.Step(OnboardingStep.PastApplication), state.pendingNavigation)
        assertTrue(state.step4.isLoaded)
        val document = state.step4.documents.single()
        assertEquals("remote-9", document.id)
        assertEquals(9L, document.remoteId)
        assertEquals(3, (document.status as OnboardingUploadStatus.Completed).classifiedItemCount)
        assertNull(document.file)
    }

    // ---- Step 4 ----

    @Test
    fun `파일 업로드는 처리 중을 거쳐 완료로 바뀐다`() {
        val gate = CompletableDeferred<Unit>()
        pastApplicationRepository.onUpload = { _, label ->
            gate.await()
            Result.success(samplePastApplication(id = 11L, itemCount = 4, label = label))
        }
        val viewModel = createViewModel()

        viewModel.confirmUpload(uploadFile("resume.pdf"))

        val processing =
            viewModel.uiState.value.step4.documents
                .single()
        assertEquals(OnboardingUploadStatus.Processing, processing.status)
        // 기본 라벨을 그대로 확인하면 확장자만 빠진다.
        assertEquals("resume", processing.label)

        gate.complete(Unit)
        val completed =
            viewModel.uiState.value.step4.documents
                .single()
        assertEquals(4, (completed.status as OnboardingUploadStatus.Completed).classifiedItemCount)
        assertEquals(11L, completed.remoteId)
        assertEquals("resume", pastApplicationRepository.uploads.single().second)
    }

    @Test
    fun `업로드 실패는 실패 상태로 두고 재시도는 같은 파일을 다시 올린다`() {
        pastApplicationRepository.onUpload = { _, _ -> Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }
        val viewModel = createViewModel()
        val file = uploadFile("resume.docx")

        viewModel.confirmUpload(file, label = "2024 카카오 인턴 자소서")
        val failed =
            viewModel.uiState.value.step4.documents
                .single()
        assertEquals(OnboardingUploadStatus.Failed(OnboardingFailureReason.Network), failed.status)
        assertEquals(listOf("upload_past_application"), reporter.stages())

        pastApplicationRepository.onUpload = null
        viewModel.onStep4Event(OnboardingStep4Event.DocumentRetryClicked(failed.id))

        val retried =
            viewModel.uiState.value.step4.documents
                .single()
        assertEquals(0, (retried.status as OnboardingUploadStatus.Completed).classifiedItemCount)
        assertEquals(2, pastApplicationRepository.uploads.size)
        assertTrue(pastApplicationRepository.uploads.all { it.first === file })
        // 재시도는 파일명이 아니라 사용자가 정한 라벨을 그대로 다시 보낸다.
        assertTrue(pastApplicationRepository.uploads.all { it.second == "2024 카카오 인턴 자소서" })
    }

    @Test
    fun `메뉴는 서버 문서를 삭제하고 실패한 로컬 문서는 목록에서만 뺀다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1)
        pastApplicationRepository.onUpload = { _, _ -> Result.failure(IOException("offline")) }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()
        viewModel.confirmUpload(uploadFile("resume.txt"))
        val localId =
            viewModel.uiState.value.step4.documents
                .first { it.remoteId == null }
                .id

        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked("remote-9"))
        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked(localId))

        assertTrue(
            viewModel.uiState.value.step4.documents
                .isEmpty(),
        )
        assertTrue(pastApplicationRepository.applications.none { it.id == 9L })
    }

    @Test
    fun `서버 문서 삭제 실패는 문서를 원래 자리에 되돌리고 사유를 알린다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1)
        pastApplicationRepository.applications += samplePastApplication(id = 10L, itemCount = 1)
        pastApplicationRepository.onDelete = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", IOException("500"))) }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked("remote-9"))

        assertEquals(listOf("remote-9", "remote-10"), documentIds(viewModel))
        assertEquals(OnboardingFailureReason.Server, viewModel.uiState.value.failure)
        assertEquals(listOf("delete_past_application"), reporter.stages())
    }

    @Test
    fun `문서 삭제 연타는 지운 문서로 DELETE 를 다시 보내지 않는다`() {
        val gate = CompletableDeferred<Unit>()
        var deleteCount = 0
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1)
        pastApplicationRepository.onDelete = { id ->
            deleteCount++
            gate.await()
            if (pastApplicationRepository.applications.removeIf { it.id == id }) {
                Result.success(Unit)
            } else {
                Result.failure(CoreDataFailure.NotFound("RESOURCE_NOT_FOUND", IOException("404")))
            }
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked("remote-9"))
        // 첫 요청이 끝나기 전에 한 번 더 누른다 — 카드는 이미 목록에서 빠져 있어야 한다.
        assertEquals(emptyList<String>(), documentIds(viewModel))
        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked("remote-9"))

        gate.complete(Unit)

        assertEquals(1, deleteCount)
        assertEquals(emptyList<String>(), documentIds(viewModel))
        // 두 번째 404 가 이미 지워진 문서에 배너와 계측 표본을 남기지 않는다.
        assertNull(viewModel.uiState.value.failure)
        assertEquals(emptyList<String>(), reporter.stages())
    }

    @Test
    fun `파일 선택 실패는 사유를 표시하고 기록한다`() {
        val viewModel = createViewModel()

        viewModel.onFileSelectionFailed(OnboardingFailureReason.UnsupportedFile, IllegalArgumentException("png"))

        assertEquals(OnboardingFailureReason.UnsupportedFile, viewModel.uiState.value.failure)
        assertEquals(listOf("upload_past_application"), reporter.stages())
        assertTrue(
            viewModel.uiState.value.step4.documents
                .isEmpty(),
        )
    }

    @Test
    fun `파일을 고르면 올리기 전에 라벨 시트를 열고 기본 라벨은 확장자를 뺀 파일명이다`() {
        val viewModel = createViewModel()

        viewModel.onFileSelected(uploadFile("이력서_최종_v3(2).pdf"))

        val sheet = viewModel.uiState.value.uploadLabel
        assertNotNull(sheet)
        assertEquals("이력서_최종_v3(2)", sheet?.label)
        assertEquals("이력서_최종_v3(2).pdf", sheet?.fileName)
        // 확인하기 전에는 올리지도, 목록에 넣지도 않는다.
        assertTrue(pastApplicationRepository.uploads.isEmpty())
        assertTrue(
            viewModel.uiState.value.step4.documents
                .isEmpty(),
        )
    }

    @Test
    fun `라벨을 고쳐 확인하면 파일명 대신 그 라벨로 올린다`() {
        val viewModel = createViewModel()
        viewModel.onFileSelected(uploadFile("resume.pdf"))

        viewModel.onUploadLabelEvent(UploadLabelEvent.LabelChanged(" 2024 카카오 인턴 자소서 "))
        viewModel.onUploadLabelEvent(UploadLabelEvent.Submitted)

        assertNull(viewModel.uiState.value.uploadLabel)
        val (file, label) = pastApplicationRepository.uploads.single()
        assertEquals("2024 카카오 인턴 자소서", label)
        assertEquals("resume.pdf", file.fileName)
        assertEquals(
            "2024 카카오 인턴 자소서",
            viewModel.uiState.value.step4.documents
                .single()
                .label,
        )
    }

    @Test
    fun `업로드 라벨은 직접 입력과 같은 규칙으로 거른다`() {
        val viewModel = createViewModel()
        viewModel.onFileSelected(uploadFile("resume.pdf"))

        viewModel.onUploadLabelEvent(UploadLabelEvent.LabelChanged("   "))
        viewModel.onUploadLabelEvent(UploadLabelEvent.Submitted)
        assertEquals(
            OnboardingFieldError.Required,
            viewModel.uiState.value.uploadLabel
                ?.labelError,
        )

        viewModel.onUploadLabelEvent(UploadLabelEvent.LabelChanged("가".repeat(51)))
        viewModel.onUploadLabelEvent(UploadLabelEvent.Submitted)
        assertEquals(
            OnboardingFieldError.TooLong(50),
            viewModel.uiState.value.uploadLabel
                ?.labelError,
        )
        assertTrue(pastApplicationRepository.uploads.isEmpty())
    }

    @Test
    fun `라벨 시트를 취소하면 고른 파일을 올리지 않는다`() {
        val viewModel = createViewModel()
        viewModel.onFileSelected(uploadFile("resume.pdf"))

        viewModel.onUploadLabelEvent(UploadLabelEvent.Dismissed)

        assertNull(viewModel.uiState.value.uploadLabel)
        assertTrue(pastApplicationRepository.uploads.isEmpty())
        assertTrue(
            viewModel.uiState.value.step4.documents
                .isEmpty(),
        )
    }

    @Test
    fun `문서 상한이 차면 라벨 시트를 열지 않고 사유만 알린다`() {
        repeat(MAX_PAST_APPLICATIONS) { index ->
            pastApplicationRepository.applications += samplePastApplication(id = index + 1L, itemCount = 0)
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onFileSelected(uploadFile("resume.pdf"))

        assertNull(viewModel.uiState.value.uploadLabel)
        assertEquals(OnboardingFailureReason.LimitExceeded(FailureSurface.Application), viewModel.uiState.value.failure)
        assertEquals(MAX_PAST_APPLICATIONS, viewModel.uiState.value.step4.documents.size)
    }

    @Test
    fun `직접 입력은 라벨과 본문을 검증한 뒤 TXT 로 업로드한다`() {
        val viewModel = createViewModel()
        viewModel.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        assertNotNull(viewModel.uiState.value.directInput)

        viewModel.onDirectInputEvent(DirectInputEvent.Submitted)
        assertEquals(
            OnboardingFieldError.Required,
            viewModel.uiState.value.directInput
                ?.labelError,
        )
        assertEquals(
            OnboardingFieldError.Required,
            viewModel.uiState.value.directInput
                ?.contentError,
        )

        viewModel.onDirectInputEvent(DirectInputEvent.LabelChanged(" 2024 카카오 인턴 자소서 "))
        viewModel.onDirectInputEvent(DirectInputEvent.ContentChanged("지원 동기는 ..."))
        viewModel.onDirectInputEvent(DirectInputEvent.Submitted)

        assertNull(viewModel.uiState.value.directInput)
        val (file, label) = pastApplicationRepository.uploads.single()
        assertEquals("2024 카카오 인턴 자소서", label)
        assertEquals("2024 카카오 인턴 자소서.txt", file.fileName)
        assertEquals("지원 동기는 ...", file.openStream().readBytes().toString(Charsets.UTF_8))
        val document =
            viewModel.uiState.value.step4.documents
                .single()
        assertEquals("2024 카카오 인턴 자소서", document.label)
        assertEquals(0, (document.status as OnboardingUploadStatus.Completed).classifiedItemCount)
    }

    @Test
    fun `분류 항목은 펼쳤다 접을 수 있고 문서를 지우면 펼침도 함께 사라진다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 2)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.DocumentExpandToggled("remote-9"))
        assertEquals("remote-9", viewModel.uiState.value.step4.expandedDocumentId)

        viewModel.onStep4Event(OnboardingStep4Event.DocumentExpandToggled("remote-9"))
        assertNull(viewModel.uiState.value.step4.expandedDocumentId)

        viewModel.onStep4Event(OnboardingStep4Event.DocumentExpandToggled("remote-9"))
        viewModel.onStep4Event(OnboardingStep4Event.DocumentMenuClicked("remote-9"))

        assertTrue(
            viewModel.uiState.value.step4.documents
                .isEmpty(),
        )
        assertNull(viewModel.uiState.value.step4.expandedDocumentId)
    }

    @Test
    fun `항목이 없는 문서는 펼치지 않는다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 0)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.DocumentExpandToggled("remote-9"))

        assertNull(viewModel.uiState.value.step4.expandedDocumentId)
    }

    @Test
    fun `분류 조정은 낙관적으로 반영하고 성공하면 서버 항목으로 맞춘다`() {
        val gate = CompletableDeferred<Unit>()
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 2, confident = false)
        pastApplicationRepository.onUpdateItemCategory = { _, itemId, category ->
            gate.await()
            Result.success(PastApplicationItem(id = itemId, category = category, content = "서버 본문", confident = true))
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.ItemCategoryClicked("remote-9", 1L))
        val picker = viewModel.uiState.value.itemCategoryPicker
        assertNotNull(picker)
        assertEquals(1L, picker!!.itemId)
        assertEquals(PastApplicationCategory.Other, picker.selected)
        assertEquals("내용 0", picker.contentPreview)

        viewModel.onItemCategoryPickerEvent(PastApplicationItemCategoryEvent.CategorySelected(PastApplicationCategory.Motivation))

        assertNull(viewModel.uiState.value.itemCategoryPicker)
        val optimistic = items(viewModel, "remote-9")
        assertEquals(PastApplicationCategory.Motivation, optimistic.first().category)
        assertEquals("내용 0", optimistic.first().content)
        assertEquals(PastApplicationCategory.Other, optimistic.last().category)

        gate.complete(Unit)

        val saved = items(viewModel, "remote-9")
        assertEquals("서버 본문", saved.first().content)
        assertTrue(saved.first().confident)
        assertFalse(saved.last().confident)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `분류 조정 실패는 이전 분류로 되돌리고 사유를 알린다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1, confident = false)
        pastApplicationRepository.onUpdateItemCategory = { _, _, _ ->
            Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", IOException("500")))
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.ItemCategoryClicked("remote-9", 1L))
        viewModel.onItemCategoryPickerEvent(PastApplicationItemCategoryEvent.CategorySelected(PastApplicationCategory.Growth))

        val reverted = items(viewModel, "remote-9").single()
        assertEquals(PastApplicationCategory.Other, reverted.category)
        assertFalse(reverted.confident)
        assertEquals(OnboardingFailureReason.Server, viewModel.uiState.value.failure)
        assertEquals(listOf("update_past_application_item_category"), reporter.stages())
    }

    @Test
    fun `같은 분류를 다시 고르면 요청하지 않는다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1)
        pastApplicationRepository.onUpdateItemCategory = { _, _, _ -> error("요청하면 안 된다") }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.ItemCategoryClicked("remote-9", 1L))
        viewModel.onItemCategoryPickerEvent(PastApplicationItemCategoryEvent.CategorySelected(PastApplicationCategory.Other))

        assertNull(viewModel.uiState.value.itemCategoryPicker)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun `분류 조정 중에도 건너뛰기로 온보딩을 끝낼 수 있다`() {
        pastApplicationRepository.applications += samplePastApplication(id = 9L, itemCount = 1, confident = false)
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.PastApplication)
        val viewModel = createViewModel()
        viewModel.onStep4Event(OnboardingStep4Event.DocumentExpandToggled("remote-9"))

        viewModel.onStep4Event(OnboardingStep4Event.SkipClicked)

        assertEquals(OnboardingDestination.Complete, viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `완료와 건너뛰기는 완료 기록 후 완료 화면으로 보낸다`() {
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.SkipClicked)

        assertEquals(1, progressRepository.markCompletedCalls)
        assertEquals(OnboardingProgress.Completed, progressRepository.progressState.value)
        assertEquals(OnboardingDestination.Complete, viewModel.uiState.value.pendingNavigation)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `완료 기록 실패는 사유를 알린다`() {
        progressRepository.onMarkCompleted = { Result.failure(IOException("disk")) }
        val viewModel = createViewModel()

        viewModel.onStep4Event(OnboardingStep4Event.CompleteClicked)

        assertEquals(OnboardingFailureReason.Network, viewModel.uiState.value.failure)
        assertNull(viewModel.uiState.value.pendingNavigation)
        assertEquals(listOf("complete"), reporter.stages())
    }

    // ---- 완료 ----

    @Test
    fun `완료 화면 액션은 피드 또는 게시판 등록으로 보낸다`() {
        val viewModel = createViewModel()

        viewModel.onCompleteEvent(OnboardingCompleteEvent.ViewFeedClicked)
        assertEquals(OnboardingDestination.Feed, viewModel.uiState.value.pendingNavigation)
        viewModel.onNavigationConsumed()
        assertNull(viewModel.uiState.value.pendingNavigation)

        viewModel.onCompleteEvent(OnboardingCompleteEvent.RegisterBoardClicked)
        assertEquals(OnboardingDestination.BoardRegister, viewModel.uiState.value.pendingNavigation)
    }

    // ---- 실패 사유의 갈래 (#236) ----

    /** 503 은 서버가 쉬는 것이라 일반 서버 오류로 접지 않는다 — 배너가 점검 문구를 읽어야 한다. */
    @Test
    fun `서버 점검은 일반 서버 오류가 아니라 점검 사유로 갈린다`() {
        userProfileRepository.onUpdateProfile =
            { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", IOException("503"))) }
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        assertEquals(OnboardingFailureReason.Maintenance, viewModel.uiState.value.failure)
    }

    /** 타임아웃은 연결 없음과 처방이 다르다 — 표의 두 행으로 가른다. */
    @Test
    fun `타임아웃은 연결 없음과 다른 사유로 갈린다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.NetworkUnavailable(SocketTimeoutException("timeout"))) }
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        assertEquals(OnboardingFailureReason.Timeout, viewModel.uiState.value.failure)
    }

    // ---- 세션 만료 (#211) ----

    @Test
    fun `단계 저장이 401 을 물면 배너 대신 세션 종료를 올린다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401"))) }
        val viewModel = createViewModel()

        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)

        val state = viewModel.uiState.value
        assertTrue(state.sessionEnded)
        // 배너를 띄우지 않는다 — 로그인 화면이 같은 문구를 스스로 켜고 「다시 로그인」까지 준다(#128).
        assertNull(state.failure)
        assertFalse(state.isSubmitting)
        assertNull(state.pendingNavigation)
        assertEquals(listOf("save_basic_info"), reporter.stages())
    }

    @Test
    fun `세션 종료 신호는 소비하면 꺼진다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401"))) }
        val viewModel = createViewModel()
        viewModel.onStep1Event(OnboardingStep1Event.NextClicked)
        assertTrue(viewModel.uiState.value.sessionEnded)

        viewModel.onSessionEndedConsumed()

        assertFalse(viewModel.uiState.value.sessionEnded)
    }

    @Test
    fun `경험 삭제가 401 을 물면 목록을 되돌리고 세션 종료를 올린다`() {
        experienceRepository.experiences += sampleExperience(id = 7L)
        experienceRepository.onDeleteExperience = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401"))) }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)
        val viewModel = createViewModel()
        viewModel.onStep3Event(OnboardingStep3Event.ExperienceDeleteClicked("7"))
        viewModel.onExperienceDeleteEvent(ExperienceDeleteEvent.Confirmed)

        val state = viewModel.uiState.value
        assertTrue(state.sessionEnded)
        assertNull(state.failure)
        assertEquals(listOf(7L), state.step3.experiences.map(Experience::id))
    }

    @Test
    fun `업로드가 401 을 물면 카드를 실패로 칠하지 않고 세션 종료를 올린다`() {
        pastApplicationRepository.onUpload = { _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401"))) }
        val viewModel = createViewModel()

        viewModel.confirmUpload(uploadFile("resume.pdf"))

        val state = viewModel.uiState.value
        assertTrue(state.sessionEnded)
        assertNull(state.failure)
        // 「로그인 만료 · 재시도」는 눌러 봐야 같은 401 을 다시 무는 행동이라 그리지 않는다.
        assertEquals(
            OnboardingUploadStatus.Processing,
            state.step4.documents
                .single()
                .status,
        )
        assertEquals(listOf("upload_past_application"), reporter.stages())
    }

    /**
     * 화면에 들어오기만 해도 도는 조회는 세션 종료를 올리지 않는다.
     *
     * 세션 정리가 실패해 토큰이 남은 기기에서 「만료 → 셸 재계산 → 다시 온보딩 → 같은 조회 → 만료」가 사용자의
     * 손 없이 도는 고리를 만들지 않기 위해서다. 사용자가 조작하면 그때 한 번에 로그인 화면까지 간다.
     */
    @Test
    fun `자동 조회의 401 은 기록만 남기고 세션 종료를 올리지 않는다`() {
        experienceRepository.onGetExperiences = { _, _, _ ->
            Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", IOException("401")))
        }
        progressRepository.progressState.value = OnboardingProgress.InProgress(OnboardingStep.Experience)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.sessionEnded)
        assertNull(state.failure)
        assertEquals(listOf("load_experiences"), reporter.stages())
    }

    private fun sampleProfile(onboardingDone: Boolean = false) =
        UserProfile(
            id = 1L,
            name = "정일혁",
            school = "건국대학교",
            department = "컴퓨터공학부",
            gpa = 3.87,
            gradYear = 2027,
            jobInterests = listOf(JobInterest("frontend", 2), JobInterest("backend", 1)),
            tags = listOf("AI", "스타트업"),
            onboardingDone = onboardingDone,
            completion = 40,
        )

    /** 제출 잠금이 걸린 시트가 열려 있는가. */
    private fun submittingEditor(viewModel: OnboardingViewModel): Boolean =
        viewModel.uiState.value.experienceEditor
            ?.isSubmitting == true

    private fun experienceTitles(viewModel: OnboardingViewModel): List<String> =
        viewModel.uiState.value.step3.experiences
            .map(Experience::title)

    private fun sampleExperience(id: Long) =
        Experience(
            id = id,
            title = "CareerCompass",
            startPoint = ExperiencePoint.Date(LocalDate.of(2025, 9, 1)),
            endPoint = null,
            details = ExperienceDetails.Project(role = "안드로이드", techs = listOf("Kotlin"), summary = null, link = null),
            createdAt = null,
        )

    /** 시점 계약을 보는 테스트용 카드 — 기본은 「아무 시점도 없는」 카드다. */
    private fun sampleCard(
        id: Long = 7L,
        startPoint: ExperiencePoint? = null,
        endPoint: ExperiencePoint? = null,
        details: ExperienceDetails,
    ) = Experience(
        id = id,
        // 수상의 `contestName` 은 시트에서 제목 칸 그 자체라 제목을 공모전명으로 둔다.
        title = "공모전",
        startPoint = startPoint,
        endPoint = endPoint,
        details = details,
        createdAt = null,
    )

    /** 그 유형이 담을 수 있는 가장 자세한 시점 — 왕복 테스트가 유형마다 다른 정밀도를 그대로 물게 한다. */
    private fun startPointFor(type: ExperienceType): ExperiencePoint =
        when (type.maxPointPrecision) {
            ExperiencePrecision.Year -> ExperiencePoint.Year(2025)
            ExperiencePrecision.YearMonth -> ExperiencePoint.YearMonth(2025, 6)
            ExperiencePrecision.Date -> ExperiencePoint.Date(LocalDate.of(2025, 6, 15))
        }

    /** 기간이 있는 유형의 종료 시점. 수상·자격증은 종료가 없다. */
    private fun endPointFor(type: ExperienceType): ExperiencePoint? =
        if (type.hasPeriod) ExperiencePoint.Date(LocalDate.of(2025, 8, 20)) else null

    private fun internExperience(id: Long) =
        Experience(
            id = id,
            title = "카카오 인턴",
            startPoint = ExperiencePoint.Date(LocalDate.of(2025, 1, 1)),
            endPoint = ExperiencePoint.Date(LocalDate.of(2025, 2, 1)),
            details = ExperienceDetails.Intern(company = "카카오", role = "안드로이드 개발", summary = "요약"),
            createdAt = null,
        )

    private fun samplePastApplication(
        id: Long,
        itemCount: Int,
        label: String = "지원서 $id",
        confident: Boolean = true,
    ) = PastApplication(
        id = id,
        label = label,
        items =
            List(itemCount) { index ->
                PastApplicationItem(
                    id = index + 1L,
                    category = PastApplicationCategory.Other,
                    content = "내용 $index",
                    confident = confident,
                )
            },
        createdAt = null,
    )

    private fun documentIds(viewModel: OnboardingViewModel): List<String> {
        val documents = viewModel.uiState.value.step4.documents
        return documents.map(OnboardingUploadDocument::id)
    }

    private fun items(
        viewModel: OnboardingViewModel,
        documentId: String,
    ): List<PastApplicationItem> {
        val status =
            viewModel.uiState.value.step4.documents
                .first { it.id == documentId }
                .status
        return (status as OnboardingUploadStatus.Completed).items
    }

    private fun uploadFile(fileName: String) = UploadFile(fileName = fileName, sizeBytes = 16L) { ByteArrayInputStream(ByteArray(16)) }

    /** 파일 선택 → 라벨 시트 확인까지. [label] 을 주지 않으면 기본 라벨(확장자를 뺀 파일명)을 그대로 쓴다. */
    private fun OnboardingViewModel.confirmUpload(
        file: UploadFile,
        label: String? = null,
    ) {
        onFileSelected(file)
        if (label != null) onUploadLabelEvent(UploadLabelEvent.LabelChanged(label))
        onUploadLabelEvent(UploadLabelEvent.Submitted)
    }
}

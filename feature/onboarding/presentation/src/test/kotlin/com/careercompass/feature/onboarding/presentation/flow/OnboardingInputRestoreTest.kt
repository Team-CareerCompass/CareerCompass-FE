package com.careercompass.feature.onboarding.presentation.flow

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.component.DirectInputEvent
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.SchoolPickerEvent
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
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Event
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelEvent
import com.careercompass.feature.onboarding.presentation.reporting.RecordingErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * 프로세스 사망 뒤 온보딩 입력이 살아 돌아오는지 — 이슈 #133.
 *
 * ### 왜 재구성이 아니라 이 방식인가
 * Robolectric 의 화면 재구성(회전·테마 변경)으로는 이 결함이 잡히지 않는다. 재구성은 ViewModel 을 **살려 두므로**
 * 저장 없이도 상태가 그대로 남는다. 여기서는 진짜 경로를 태운다 — [SavedStateHandle] 을 `Bundle` 로 저장하고,
 * 그 번들을 [Parcel] 에 마샬링했다가 되읽어(안드로이드가 프로세스 경계를 넘길 때 하는 그대로) 새 ViewModel 을
 * 세운다. 저장할 수 없는 값을 넣으면 여기서 깨지고, 배선을 빠뜨리면 값이 비어 돌아온다.
 *
 * 서버와의 우선순위(서버 > 초안 > 빈 값)와 복원하지 않기로 한 것들의 근거는 [OnboardingInputDraft] KDoc 에 있다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingInputRestoreTest {
    private val userProfileRepository = FakeUserProfileRepository()
    private val progressRepository = FakeOnboardingProgressRepository()
    private val experienceRepository = FakeExperienceRepository()
    private val pastApplicationRepository = FakePastApplicationRepository()
    private val reporter = RecordingErrorReporter()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Step 1 ----

    @Test
    fun `Step 1 에 친 글자는 프로세스가 죽어도 남는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep1Event(OnboardingStep1Event.NameChanged("정일혁"))
        before.onStep1Event(OnboardingStep1Event.MajorChanged("컴퓨터공학부"))
        before.onStep1Event(OnboardingStep1Event.GradePointAverageChanged("3.87"))

        val after = createViewModel(handle.acrossProcessDeath())

        val step1 = after.uiState.value.step1
        assertEquals("정일혁", step1.name)
        assertEquals("컴퓨터공학부", step1.major)
        assertEquals("3.87", step1.gradePointAverage)
    }

    @Test
    fun `피커로 고른 학교와 졸업 연월도 함께 남는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep1Event(OnboardingStep1Event.SchoolPickerClicked)
        before.onSchoolPickerEvent(SchoolPickerEvent.SchoolSelected("건국대학교"))
        before.onStep1Event(OnboardingStep1Event.GraduationDatePickerClicked)
        before.onGraduationPickerEvent(GraduationDatePickerEvent.YearSelected(2027))
        before.onGraduationPickerEvent(GraduationDatePickerEvent.Confirmed)

        val after = createViewModel(handle.acrossProcessDeath())

        assertEquals("건국대학교", after.uiState.value.step1.school)
        assertEquals("2027.02", after.uiState.value.step1.graduationDate)
        // 피커가 열려 있었는지는 복원하지 않는다 — 사용자가 의도한 이동이 아니라 시스템 사정으로 닫힌 것이다.
        assertNull(after.uiState.value.schoolPicker)
        assertNull(after.uiState.value.graduationPicker)
    }

    @Test
    fun `필드 오류와 진행 상태는 복원하지 않는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep1Event(OnboardingStep1Event.GradePointAverageChanged("9.9"))
        // 그 순간의 피드백이 남아 있는지 먼저 확인한다 — 복원 대상이 아니라는 판정이 의미를 가지려면.
        assertTrue(before.uiState.value.step1.hasErrors)

        val after = createViewModel(handle.acrossProcessDeath())

        assertEquals("9.9", after.uiState.value.step1.gradePointAverage)
        assertFalse(after.uiState.value.step1.hasErrors)
        assertFalse(after.uiState.value.isSubmitting)
        assertNull(after.uiState.value.failure)
    }

    // ---- 서버 값과의 우선순위 ----

    @Test
    fun `서버가 아는 필드는 서버 값이 이기고 모르는 필드에만 초안이 남는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep1Event(OnboardingStep1Event.NameChanged("초안 이름"))
        before.onStep1Event(OnboardingStep1Event.MajorChanged("초안 학과"))

        // 「다음」으로 이름만 서버에 올라간 상태를 흉내낸다 — 학과는 아직 서버가 모른다.
        userProfileRepository.profileState.value = profile(name = "서버 이름", department = null)
        val after = createViewModel(handle.acrossProcessDeath())

        assertEquals("서버 이름", after.uiState.value.step1.name)
        assertEquals("초안 학과", after.uiState.value.step1.major)
    }

    @Test
    fun `서버가 직무와 태그를 알면 초안 대신 서버 목록을 쓴다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep2Event(OnboardingStep2Event.JobSelectionToggled("qa"))
        before.onStep2Event(OnboardingStep2Event.InterestInputChanged("초안태그"))
        before.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)

        userProfileRepository.profileState.value =
            profile(jobInterests = listOf(JobInterest("backend", 1)), tags = listOf("서버태그"))
        val after = createViewModel(handle.acrossProcessDeath())

        assertEquals(listOf("backend"), after.uiState.value.step2.selectedJobCodes)
        assertEquals(listOf("서버태그"), after.uiState.value.step2.interestTags)
    }

    @Test
    fun `서버가 아직 모르는 Step 2 입력은 초안이 남는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep2Event(OnboardingStep2Event.JobSelectionToggled("qa"))
        before.onStep2Event(OnboardingStep2Event.InterestInputChanged("등록전"))
        before.onStep2Event(OnboardingStep2Event.InterestTagSubmitted)
        before.onStep2Event(OnboardingStep2Event.InterestInputChanged("아직 #입력중"))

        // 프로필은 있지만 직무·태그는 비어 있다 — Step 2 의 「다음」을 아직 누르지 않은 사용자다.
        userProfileRepository.profileState.value = profile()
        val after = createViewModel(handle.acrossProcessDeath())

        assertEquals(listOf("qa"), after.uiState.value.step2.selectedJobCodes)
        assertEquals(listOf("등록전"), after.uiState.value.step2.interestTags)
        assertEquals("아직 #입력중", after.uiState.value.step2.interestInput)
    }

    // ---- 직접 입력 자소서 ----

    @Test
    fun `직접 입력 자소서는 살아남지만 시트가 저절로 열리지는 않는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        before.onDirectInputEvent(DirectInputEvent.LabelChanged("2026 카카오 지원서"))
        before.onDirectInputEvent(DirectInputEvent.ContentChanged(LONG_ESSAY))

        val after = createViewModel(handle.acrossProcessDeath())

        assertNull(after.uiState.value.directInput)

        after.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        val sheet = requireNotNull(after.uiState.value.directInput)
        assertEquals("2026 카카오 지원서", sheet.label)
        assertEquals(LONG_ESSAY, sheet.content)
    }

    @Test
    fun `직접 입력을 취소하면 초안도 함께 버린다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        before.onDirectInputEvent(DirectInputEvent.ContentChanged("버릴 글"))
        before.onDirectInputEvent(DirectInputEvent.Dismissed)

        val after = createViewModel(handle.acrossProcessDeath())
        after.onStep4Event(OnboardingStep4Event.DirectInputClicked)

        assertEquals("", requireNotNull(after.uiState.value.directInput).content)
    }

    @Test
    fun `업로드한 직접 입력은 초안으로 되살아나지 않는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        before.onDirectInputEvent(DirectInputEvent.LabelChanged("올린 자소서"))
        before.onDirectInputEvent(DirectInputEvent.ContentChanged("올린 본문"))
        before.onDirectInputEvent(DirectInputEvent.Submitted)
        assertNull(before.uiState.value.directInput)

        val after = createViewModel(handle.acrossProcessDeath())
        after.onStep4Event(OnboardingStep4Event.DirectInputClicked)

        val sheet = requireNotNull(after.uiState.value.directInput)
        assertEquals("", sheet.label)
        assertEquals("", sheet.content)
    }

    /**
     * 저장 상태는 Binder 트랜잭션을 통과한다 — 파일을 통째로 붙여 넣은 글이 그 한도를 위협하면 본문 초안을
     * 포기한다. 짧은 라벨은 그대로 남겨 둔다: 다시 열었을 때 이름은 살아 있고 본문만 비어 있어, 무엇을 다시
     * 붙여 넣어야 하는지가 사용자에게 보인다.
     */
    @Test
    fun `한도를 넘게 붙여 넣은 자소서는 본문 초안을 남기지 않는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onStep4Event(OnboardingStep4Event.DirectInputClicked)
        before.onDirectInputEvent(DirectInputEvent.LabelChanged("통째로 붙여 넣은 지원서"))
        before.onDirectInputEvent(DirectInputEvent.ContentChanged(OVERSIZED_ESSAY))

        val after = createViewModel(handle.acrossProcessDeath())
        after.onStep4Event(OnboardingStep4Event.DirectInputClicked)

        val sheet = requireNotNull(after.uiState.value.directInput)
        assertEquals("통째로 붙여 넣은 지원서", sheet.label)
        assertEquals("", sheet.content)
    }

    // ---- 업로드 라벨 시트 ----

    @Test
    fun `라벨 시트가 열린 채 죽으면 같은 파일을 다시 읽었을 때 쓰던 라벨이 선다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onFileSelected(uploadFile())
        before.onUploadLabelEvent(UploadLabelEvent.LabelChanged("내가 정한 라벨"))

        // 화면(OnboardingStep4Screen)이 rememberSaveable 로 들고 있던 Uri 를 다시 읽어 넘기는 자리다.
        val after = createViewModel(handle.acrossProcessDeath())
        assertNull(after.uiState.value.uploadLabel)
        after.onFileSelected(uploadFile())

        assertEquals("내가 정한 라벨", requireNotNull(after.uiState.value.uploadLabel).label)
    }

    @Test
    fun `라벨 시트를 취소하면 다음에 고른 파일에 그 라벨이 따라붙지 않는다`() {
        val handle = SavedStateHandle()
        val before = createViewModel(handle)
        before.onFileSelected(uploadFile())
        before.onUploadLabelEvent(UploadLabelEvent.LabelChanged("버릴 라벨"))
        before.onUploadLabelEvent(UploadLabelEvent.Dismissed)

        val after = createViewModel(handle.acrossProcessDeath())
        after.onFileSelected(uploadFile(fileName = "다른 파일.pdf"))

        assertEquals("다른 파일", requireNotNull(after.uiState.value.uploadLabel).label)
    }

    // ---- 저장소 방어 ----

    @Test
    fun `낡거나 망가진 초안이 들어와도 계약을 지켜 되살린다`() {
        val handle =
            SavedStateHandle(
                mapOf(
                    // 카탈로그에서 사라진 코드·중복·상한 초과가 섞인 초안. 그대로 넣으면 상태 계약이 깨져 앱이 죽는다.
                    "onboarding.draft.step2.jobCodes" to arrayListOf("backend", "backend", "사라진직무", "qa", "devops", "security"),
                    "onboarding.draft.step2.interestTags" to arrayListOf("AI", "AI", " ", "1", "2", "3", "4", "5"),
                ),
            )

        val step2 = createViewModel(handle.acrossProcessDeath()).uiState.value.step2

        assertEquals(listOf("backend", "qa", "devops"), step2.selectedJobCodes)
        assertEquals(listOf("AI", "1", "2", "3", "4"), step2.interestTags)
    }

    // ---- 조립 ----

    /**
     * 안드로이드가 프로세스를 죽였다 되살릴 때 하는 그대로 — 핸들을 `Bundle` 로 저장하고 [Parcel] 에 마샬링했다가
     * 되읽는다. 번들에 담기지 못하는 값을 저장하면 여기서 드러난다.
     */
    private fun SavedStateHandle.acrossProcessDeath(): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(savedStateProvider().saveState())
            parcel.setDataPosition(0)
            SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle) =
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
            errorReporter = reporter,
            savedStateHandle = savedStateHandle,
        )

    private fun profile(
        name: String? = null,
        department: String? = null,
        jobInterests: List<JobInterest> = emptyList(),
        tags: List<String> = emptyList(),
    ) = UserProfile(
        id = 1L,
        name = name,
        school = null,
        department = department,
        gpa = null,
        gradYear = null,
        jobInterests = jobInterests,
        tags = tags,
        onboardingDone = false,
        completion = 10,
    )

    private fun uploadFile(fileName: String = "카카오 자소서.pdf") =
        UploadFile(fileName = fileName, sizeBytes = 1_024L) { ByteArrayInputStream(ByteArray(1)) }

    private companion object {
        /** 다른 앱에서 복사해 오는 흐름이 프로세스 사망을 부르는, 이 이슈가 가장 아프다고 말한 자리. */
        const val LONG_ESSAY = "저는 사용자의 문제를 끝까지 따라가는 개발자입니다. 첫 프로젝트에서…"

        /** 초안 상한(10만 자)을 한 글자 넘긴 글 — 사람이 쓴 자소서가 아니라 파일을 통째로 붙여 넣은 경우다. */
        val OVERSIZED_ESSAY = "가".repeat(100_001)
    }
}

package com.careercompass.feature.profile.presentation.basicinfo

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.feature.profile.domain.usecase.ObserveProfileUseCase
import com.careercompass.feature.profile.domain.usecase.RefreshProfileUseCase
import com.careercompass.feature.profile.domain.usecase.SaveJobInterestsUseCase
import com.careercompass.feature.profile.domain.usecase.SaveProfileTagsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdateProfileBasicInfoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 실제 Bundle/Parcel 과 새 ViewModel 을 사용해 입력 보존과 최신 서버 프리필의 경계를 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileEditInputRestoreTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `기본 정보 다섯 칸과 직무 태그 입력이 새 프로세스에서 돌아온다`() {
        val handle = SavedStateHandle()
        val before = viewModel(handle)
        before.event(ProfileEditEvent.NameChanged("수정한 이름"))
        before.event(ProfileEditEvent.DepartmentChanged("소프트웨어학부"))
        before.event(ProfileEditEvent.GradePointAverageChanged("4.1"))
        before.event(ProfileEditEvent.SchoolPickerClicked)
        before.school(SchoolPickerEvent.QueryChanged("한양"))
        before.school(SchoolPickerEvent.DirectInputRequested)
        before.school(SchoolPickerEvent.DirectInputChanged("한양대학교 ERICA"))
        before.school(SchoolPickerEvent.DirectInputConfirmed)
        before.event(ProfileEditEvent.GraduationPickerClicked)
        before.graduation(GraduationDatePickerEvent.YearSelected(2028))
        before.graduation(GraduationDatePickerEvent.MonthSelected(8))
        before.graduation(GraduationDatePickerEvent.Confirmed)
        before.event(ProfileEditEvent.JobToggled("backend"))
        before.event(ProfileEditEvent.InterestInputChanged("AI"))
        before.event(ProfileEditEvent.InterestTagAdded)
        before.event(ProfileEditEvent.InterestInputChanged("아직 추가 전"))

        val after = viewModel(handle.acrossProcessDeath()).uiState.value

        assertEquals("수정한 이름", after.name)
        assertEquals("한양대학교 ERICA", after.school)
        assertEquals("소프트웨어학부", after.department)
        assertEquals("4.1", after.gradePointAverage)
        assertEquals("2028.08", after.graduationDate)
        assertEquals(listOf("android", "backend"), after.selectedJobCodes)
        assertEquals(listOf("모바일", "AI"), after.interestTags)
        assertEquals("아직 추가 전", after.interestInput)
        assertEquals(profile(), after.original)
    }

    @Test
    fun `손대지 않은 칸은 최신 서버 값으로 채우고 수정한 칸만 저장 요청에 싣는다`() {
        val handle = SavedStateHandle()
        viewModel(handle).event(ProfileEditEvent.NameChanged("내가 고친 이름"))
        val fresh = profile().copy(school = "새 학교", department = "새 학과", gpa = 4.0, gradYear = 2029)
        val repository = FakeUserProfileRepository(fresh)
        val after = viewModel(handle.acrossProcessDeath(), repository)

        assertEquals("내가 고친 이름", after.uiState.value.name)
        assertEquals("새 학교", after.uiState.value.school)
        assertEquals("새 학과", after.uiState.value.department)
        assertEquals("4.0", after.uiState.value.gradePointAverage)
        assertEquals("2029", after.uiState.value.graduationDate)
        after.event(ProfileEditEvent.SaveClicked)
        assertEquals("내가 고친 이름", repository.updates.single().name)
        assertNull(repository.updates.single().school)
        assertNull(repository.updates.single().department)
    }

    @Test
    fun `사용자가 빈 값으로 고친 칸과 빈 선택 목록도 서버 값으로 덮지 않는다`() {
        val handle = SavedStateHandle()
        val before = viewModel(handle)
        before.event(ProfileEditEvent.NameChanged(""))
        before.event(ProfileEditEvent.JobToggled("android"))
        before.event(ProfileEditEvent.InterestTagRemoved("모바일"))

        val after = viewModel(handle.acrossProcessDeath()).uiState.value

        assertEquals("", after.name)
        assertTrue(after.selectedJobCodes.isEmpty())
        assertTrue(after.interestTags.isEmpty())
        assertFalse(after.isSaveEnabled)
    }

    @Test
    fun `필드 오류와 열린 피커는 복원하지 않고 입력만 복원한다`() {
        val handle = SavedStateHandle()
        val before = viewModel(handle)
        before.event(ProfileEditEvent.NameChanged(""))
        before.event(ProfileEditEvent.SaveClicked)
        assertNotNull(before.uiState.value.nameError)
        before.event(ProfileEditEvent.SchoolPickerClicked)

        val after = viewModel(handle.acrossProcessDeath()).uiState.value

        assertEquals("", after.name)
        assertNull(after.nameError)
        assertNull(after.schoolPicker)
        assertNull(after.graduationPicker)
        assertNull(after.message)
        assertFalse(after.isSaving)
        assertFalse(after.isSaved)
    }

    @Test
    fun `저장 중 프로세스가 죽어도 입력만 복원하고 요청은 다시 보내지 않는다`() {
        val handle = SavedStateHandle()
        val repository = FakeUserProfileRepository(profile(), onUpdateProfile = { awaitCancellation() })
        val before = viewModel(handle, repository)
        before.event(ProfileEditEvent.NameChanged("저장 중 이름"))
        before.event(ProfileEditEvent.SaveClicked)
        assertTrue(before.uiState.value.isSaving)
        val freshRepository = FakeUserProfileRepository(profile())

        val after = viewModel(handle.acrossProcessDeath(), freshRepository).uiState.value

        assertEquals("저장 중 이름", after.name)
        assertFalse(after.isSaving)
        assertTrue(freshRepository.updates.isEmpty())
    }

    @Test
    fun `저장 완료와 성공 신호 소비 뒤에는 초안을 다시 남기지 않는다`() {
        val handle = SavedStateHandle()
        val before = viewModel(handle)
        before.event(ProfileEditEvent.NameChanged("저장한 이름"))
        before.event(ProfileEditEvent.SaveClicked)
        before.onIntent(ProfileEditIntent.ConsumeSaved)

        assertTrue(handle.keys().isEmpty())
        val after = viewModel(handle.acrossProcessDeath()).uiState.value
        assertEquals(profile().name, after.name)
        assertFalse(after.isSaved)
    }

    @Test
    fun `세션 만료 뒤에는 이전 계정의 입력을 지운다`() {
        val handle = SavedStateHandle()
        val repository =
            FakeUserProfileRepository(profile(), onUpdateProfile = {
                Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException()))
            })
        val before = viewModel(handle, repository)
        before.event(ProfileEditEvent.NameChanged("이전 계정 입력"))
        before.event(ProfileEditEvent.SaveClicked)
        before.onIntent(ProfileEditIntent.ConsumeSessionEnded)

        assertTrue(handle.keys().isEmpty())
        assertEquals(profile().name, viewModel(handle.acrossProcessDeath()).uiState.value.name)
    }

    @Test
    fun `첫 서버 조회가 실패해도 초안은 보관하고 재시도 성공 뒤 적용한다`() {
        val handle = SavedStateHandle()
        viewModel(handle).event(ProfileEditEvent.NameChanged("재시도 뒤 복원할 이름"))
        val repository = FakeUserProfileRepository(onRefreshProfile = { Result.failure(IOException("offline")) })
        val after = viewModel(handle.acrossProcessDeath(), repository)
        assertTrue(after.uiState.value.isFailureVisible)
        repository.onRefreshProfile = { Result.success(profile().copy(school = "새 학교")) }

        after.event(ProfileEditEvent.RetryClicked)

        assertEquals("재시도 뒤 복원할 이름", after.uiState.value.name)
        assertEquals("새 학교", after.uiState.value.school)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        repository: FakeUserProfileRepository = FakeUserProfileRepository(profile()),
    ) = ProfileEditViewModel(
        observeProfile = ObserveProfileUseCase(repository),
        refreshProfile = RefreshProfileUseCase(repository),
        updateProfileBasicInfo = UpdateProfileBasicInfoUseCase(repository),
        saveJobInterests = SaveJobInterestsUseCase(repository),
        saveProfileTags = SaveProfileTagsUseCase(repository),
        errorReporter =
            object : ErrorReporter {
                override fun writeFailure(
                    throwable: Throwable,
                    attributes: Map<String, String>,
                ) = Unit
            },
        clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC),
        savedStateHandle = handle,
    )

    private fun ProfileEditViewModel.event(event: ProfileEditEvent) = onIntent(ProfileEditIntent.Screen(event))

    private fun ProfileEditViewModel.school(event: SchoolPickerEvent) = onIntent(ProfileEditIntent.SchoolPicker(event))

    private fun ProfileEditViewModel.graduation(event: GraduationDatePickerEvent) = onIntent(ProfileEditIntent.GraduationPicker(event))

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

    private fun profile() =
        UserProfile(
            id = 1,
            name = "이준혁",
            school = "건국대학교",
            department = "컴퓨터공학부",
            gpa = 3.9,
            gradYear = 2027,
            jobInterests = listOf(JobInterest("android", 1)),
            tags = listOf("모바일"),
            onboardingDone = true,
            completion = 78,
        )
}

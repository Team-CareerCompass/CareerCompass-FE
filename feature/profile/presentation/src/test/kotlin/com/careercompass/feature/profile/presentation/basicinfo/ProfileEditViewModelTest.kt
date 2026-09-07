package com.careercompass.feature.profile.presentation.basicinfo

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.feature.profile.domain.usecase.ObserveProfileUseCase
import com.careercompass.feature.profile.domain.usecase.RefreshProfileUseCase
import com.careercompass.feature.profile.domain.usecase.SaveJobInterestsUseCase
import com.careercompass.feature.profile.domain.usecase.SaveProfileTagsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdateProfileBasicInfoUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.CompletableDeferred
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
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
    private class RecordingReporter : ErrorReporter {
        val recorded = mutableListOf<Map<String, String>>()

        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) {
            recorded += attributes
        }
    }

    private val reporter = RecordingReporter()
    private val userProfileRepository = FakeUserProfileRepository(initialProfile = profile())
    private val clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ProfileEditViewModel(
            observeProfile = ObserveProfileUseCase(userProfileRepository),
            refreshProfile = RefreshProfileUseCase(userProfileRepository),
            updateProfileBasicInfo = UpdateProfileBasicInfoUseCase(userProfileRepository),
            saveJobInterests = SaveJobInterestsUseCase(userProfileRepository),
            saveProfileTags = SaveProfileTagsUseCase(userProfileRepository),
            errorReporter = reporter,
            clock = clock,
        )

    @Test
    fun `서버 값으로 다섯 칸을 채운다`() {
        val state = viewModel().state.value

        assertEquals("이준혁", state.name)
        assertEquals("건국대학교", state.school)
        assertEquals("컴퓨터공학부", state.department)
        assertEquals("3.9", state.gradePointAverage)
        assertEquals("2027", state.graduationDate)
    }

    /** 캐시가 늦게 도착해 방금 친 글자를 덮으면 사용자는 자기 입력이 사라지는 것을 본다. */
    @Test
    fun `사용자가 고친 칸은 프리필이 덮지 않는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged("이준"))

        userProfileRepository.profileState.value = profile(name = "다른이름")

        assertEquals("이준", viewModel.state.value.name)
        assertEquals("건국대학교", viewModel.state.value.school)
    }

    @Test
    fun `입력 중에는 필수 오류를 내지 않고 길이만 본다`() {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileEditEvent.NameChanged(""))
        assertNull(viewModel.state.value.nameError)

        viewModel.onEvent(ProfileEditEvent.NameChanged("가".repeat(21)))
        assertEquals(ProfileFieldViolation.TooLong(20), viewModel.state.value.nameError)
    }

    @Test
    fun `저장을 누를 때만 필수 오류를 내고 요청은 보내지 않는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged(""))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals(ProfileFieldViolation.Required, viewModel.state.value.nameError)
        assertTrue(userProfileRepository.updates.isEmpty())
    }

    @Test
    fun `바뀐 필드만 보낸다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.DepartmentChanged("소프트웨어학부"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        val update = userProfileRepository.updates.single()
        assertEquals("소프트웨어학부", update.department)
        assertNull(update.name)
        assertNull(update.school)
        assertNull(update.gpa)
        assertNull(update.gradYear)
        assertTrue(viewModel.state.value.isSaved)
    }

    @Test
    fun `서버가 짚어 준 칸에 오류를 붙이고 그 칸을 고치면 지운다`() {
        userProfileRepository.onUpdateProfile = {
            Result.failure(CoreDataFailure.InvalidInput(code = "INVALID_INPUT", field = "department", cause = IllegalStateException()))
        }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.DepartmentChanged("소프트웨어학부"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)
        assertEquals(ProfileEditField.Department, viewModel.state.value.serverRejectedField)
        assertNull(viewModel.state.value.message)

        viewModel.onEvent(ProfileEditEvent.DepartmentChanged("컴퓨터공학과"))
        assertNull(viewModel.state.value.serverRejectedField)
    }

    /** 모르는 필드 이름에 빨간 줄을 그으면 사용자가 멀쩡한 값을 고친다. */
    @Test
    fun `서버가 필드를 말해 주지 않으면 칸을 짚지 않고 알리기만 한다`() {
        userProfileRepository.onUpdateProfile = {
            Result.failure(CoreDataFailure.InvalidInput(code = "INVALID_INPUT", field = "unknown", cause = IllegalStateException()))
        }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged("이준혁2"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertNull(viewModel.state.value.serverRejectedField)
        assertEquals(ProfileEditMessage.SaveRejected, viewModel.state.value.message)
    }

    @Test
    fun `저장이 실패하면 입력을 그대로 두고 스낵바로만 알린다`() {
        userProfileRepository.onUpdateProfile = { Result.failure(IOException("offline")) }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged("이준혁2"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals("이준혁2", viewModel.state.value.name)
        assertEquals(ProfileEditMessage.SaveFailed, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isSaved)
        assertEquals("basic_info_save", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `401 은 세션 종료로 올린다`() {
        userProfileRepository.onUpdateProfile = {
            Result.failure(CoreDataFailure.Unauthorized(code = "AUTH_INVALID", cause = IllegalStateException()))
        }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged("이준혁2"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals(ProfileSessionEnd.Expired, viewModel.state.value.sessionEnd)
    }

    @Test
    fun `저장 중 다시 눌러도 요청은 한 번이다`() {
        val server = CompletableDeferred<Unit>()
        var calls = 0
        userProfileRepository.onUpdateProfile = {
            calls++
            server.await()
            Result.success(profile())
        }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.NameChanged("이준혁2"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)
        assertTrue(viewModel.state.value.isSaving)
        viewModel.onEvent(ProfileEditEvent.SaveClicked)
        server.complete(Unit)

        assertEquals(1, calls)
    }

    @Test
    fun `프리필할 값이 없는데 조회가 실패하면 화면을 덮는다`() {
        userProfileRepository.profileState.value = null
        userProfileRepository.onRefreshProfile = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }

        val state = viewModel().state.value

        assertTrue(state.isFailureVisible)
        assertEquals("basic_info_load", reporter.recorded.single()["profile_stage"])
    }

    // ── 학교 선택 (온보딩과 같은 규칙 · #138) ────────────────────────────────────

    @Test
    fun `직접 입력은 검색어를 넣은 뒤에만 열린다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.SchoolPickerClicked)
        assertFalse(
            viewModel.state.value.schoolPicker!!
                .isDirectInputOffered,
        )

        viewModel.onSchoolPicker(SchoolPickerEvent.QueryChanged("한국"))

        assertTrue(
            viewModel.state.value.schoolPicker!!
                .isDirectInputOffered,
        )
    }

    @Test
    fun `직접 입력값도 목록 값과 같은 규칙으로 다듬어 담는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.SchoolPickerClicked)
        viewModel.onSchoolPicker(SchoolPickerEvent.QueryChanged("한양"))
        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputRequested)
        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputChanged("  한양대학교   ERICA "))

        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputConfirmed)

        assertEquals("한양대학교 ERICA", viewModel.state.value.school)
        assertNull(viewModel.state.value.schoolPicker)
    }

    @Test
    fun `공백만 남은 직접 입력은 확정되지 않는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.SchoolPickerClicked)
        viewModel.onSchoolPicker(SchoolPickerEvent.QueryChanged("없는"))
        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputRequested)
        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputChanged("   "))

        viewModel.onSchoolPicker(SchoolPickerEvent.DirectInputConfirmed)

        assertEquals(
            ProfileFieldViolation.Required,
            viewModel.state.value.schoolPicker!!
                .directInput!!
                .error,
        )
        assertEquals("건국대학교", viewModel.state.value.school)
    }

    @Test
    fun `졸업 피커가 고른 연월을 칸에 적고 연도만 보낸다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.GraduationPickerClicked)
        val picker = viewModel.state.value.graduationPicker!!
        assertTrue(picker.years.contains(2032))

        viewModel.onGraduationPicker(GraduationDatePickerEvent.YearSelected(2028))
        viewModel.onGraduationPicker(GraduationDatePickerEvent.MonthSelected(8))
        viewModel.onGraduationPicker(GraduationDatePickerEvent.Confirmed)

        assertEquals("2028.08", viewModel.state.value.graduationDate)

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals(2028, userProfileRepository.updates.single().gradYear)
    }

    // ── 희망 직무·관심 태그 (#177) ─────────────────────────────────────────────

    @Test
    fun `서버 값으로 직무와 태그를 채우되 우선순위 순으로 세운다`() {
        userProfileRepository.profileState.value =
            profile().copy(
                jobInterests = listOf(JobInterest("backend", 2), JobInterest("android", 1)),
                tags = listOf("모바일", "AI"),
            )

        val state = viewModel().state.value

        assertEquals(listOf("android", "backend"), state.selectedJobCodes)
        assertEquals(listOf("모바일", "AI"), state.interestTags)
    }

    @Test
    fun `직무는 세 개까지 고르고 상한에 닿으면 이유를 말한다`() {
        val viewModel = viewModel()
        listOf("backend", "frontend", "mobile").forEach { viewModel.onEvent(ProfileEditEvent.JobToggled(it)) }

        viewModel.onEvent(ProfileEditEvent.JobToggled("devops"))

        assertEquals(
            listOf("android", "backend", "frontend"),
            viewModel.state.value.selectedJobCodes
                .take(3),
        )
        assertEquals(ProfileEditMessage.JobLimitReached, viewModel.state.value.message)
    }

    @Test
    fun `이미 고른 직무는 상한에 닿아도 뺄 수 있다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.JobToggled("backend"))
        viewModel.onEvent(ProfileEditEvent.JobToggled("frontend"))
        assertTrue(viewModel.state.value.isJobLimitReached)

        viewModel.onEvent(ProfileEditEvent.JobToggled("backend"))

        assertEquals(listOf("android", "frontend"), viewModel.state.value.selectedJobCodes)
    }

    @Test
    fun `태그는 다듬어 담고 중복은 흘린다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.InterestInputChanged(" #AI "))
        viewModel.onEvent(ProfileEditEvent.InterestTagAdded)

        viewModel.onEvent(ProfileEditEvent.InterestInputChanged("AI"))
        viewModel.onEvent(ProfileEditEvent.InterestTagAdded)

        assertEquals(listOf("모바일", "AI"), viewModel.state.value.interestTags)
        assertEquals("", viewModel.state.value.interestInput)
    }

    @Test
    fun `태그 상한에 닿으면 이유를 말한다`() {
        val viewModel = viewModel()
        listOf("A", "B", "C", "D").forEach {
            viewModel.onEvent(ProfileEditEvent.InterestInputChanged(it))
            viewModel.onEvent(ProfileEditEvent.InterestTagAdded)
        }
        assertTrue(viewModel.state.value.isTagLimitReached)

        viewModel.onEvent(ProfileEditEvent.InterestInputChanged("E"))
        viewModel.onEvent(ProfileEditEvent.InterestTagAdded)

        assertEquals(5, viewModel.state.value.interestTags.size)
        assertEquals(ProfileEditMessage.TagLimitReached, viewModel.state.value.message)
    }

    @Test
    fun `바뀐 목록만 전체 교체로 보낸다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.JobToggled("backend"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals(listOf("android", "backend"), userProfileRepository.replacedJobInterests.single().map { it.code })
        assertEquals(listOf(1, 2), userProfileRepository.replacedJobInterests.single().map { it.priority })
        // 태그는 바뀌지 않았으므로 왕복하지 않는다.
        assertTrue(userProfileRepository.replacedTags.isEmpty())
    }

    /** 전체 교체는 부분 성공이 없다 — 화면만 새 값으로 남으면 사용자는 저장된 줄 안다. */
    @Test
    fun `직무 저장이 실패하면 화면 값을 서버 값으로 되돌린다`() {
        userProfileRepository.onReplaceJobInterests = { Result.failure(IOException("offline")) }
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.JobToggled("backend"))

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertEquals(listOf("android"), viewModel.state.value.selectedJobCodes)
        assertEquals(ProfileEditMessage.InterestsReverted, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isSaved)
        assertEquals("interests_save", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `직무나 태그가 비면 저장을 잠그고 요청도 보내지 않는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ProfileEditEvent.JobToggled("android"))
        assertFalse(viewModel.state.value.isSaveEnabled)

        viewModel.onEvent(ProfileEditEvent.SaveClicked)

        assertTrue(userProfileRepository.replacedJobInterests.isEmpty())
        assertFalse(viewModel.state.value.isSaved)
    }

    private companion object {
        fun profile(name: String = "이준혁") =
            UserProfile(
                id = 1,
                name = name,
                school = "건국대학교",
                department = "컴퓨터공학부",
                gpa = 3.9,
                gradYear = 2027,
                jobInterests = listOf(JobInterest(code = "android", priority = 1)),
                tags = listOf("모바일"),
                onboardingDone = true,
                completion = 78,
            )
    }
}

private fun ProfileEditViewModel.onEvent(event: ProfileEditEvent) = onIntent(ProfileEditIntent.Screen(event))

private fun ProfileEditViewModel.onSchoolPicker(event: SchoolPickerEvent) = onIntent(ProfileEditIntent.SchoolPicker(event))

private fun ProfileEditViewModel.onGraduationPicker(event: GraduationDatePickerEvent) = onIntent(ProfileEditIntent.GraduationPicker(event))

private val ProfileEditViewModel.state get() = uiState

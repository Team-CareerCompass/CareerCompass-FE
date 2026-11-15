package com.careercompass.feature.profile.presentation.pastapplication

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.usecase.UploadPastApplicationUseCase
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.application.UploadFileSelectionReason
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.profile.domain.usecase.DeletePastApplicationUseCase
import com.careercompass.feature.profile.domain.usecase.GetPastApplicationsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdatePastApplicationItemCategoryUseCase
import com.careercompass.feature.profile.domain.usecase.UploadPastApplicationTextUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PastApplicationFileUploadTest {
    private val repository = FakePastApplicationRepository()
    private val reporter =
        object : ErrorReporter {
            override fun writeFailure(
                throwable: Throwable,
                attributes: Map<String, String>,
            ) = Unit
        }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `두 추가 방식은 선택지를 거쳐 열리고 열 장이면 둘 다 거부한다`() {
        val vm = viewModel()
        vm.screen(PastApplicationListEvent.AddClicked)
        assertTrue(vm.uiState.value.showAddOptions)
        assertNull(vm.uiState.value.directInput)
        vm.screen(PastApplicationListEvent.FileUploadClicked)
        assertTrue(vm.uiState.value.filePickerRequested)
        assertFalse(vm.uiState.value.showAddOptions)
        vm.onIntent(PastApplicationListIntent.ConsumeFilePicker)
        assertFalse(vm.uiState.value.filePickerRequested)
        repository.applications.addAll((1L..10).map(::application))
        vm.onIntent(PastApplicationListIntent.Refresh)
        listOf(
            PastApplicationListEvent.AddClicked,
            PastApplicationListEvent.FileUploadClicked,
            PastApplicationListEvent.DirectInputClicked,
        ).forEach {
            vm.screen(it)
            assertEquals(PastApplicationListMessage.LimitReached, vm.uiState.value.message)
            assertFalse(vm.uiState.value.showAddOptions)
            assertFalse(vm.uiState.value.filePickerRequested)
            assertNull(vm.uiState.value.directInput)
        }
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        assertNull(vm.uiState.value.fileUpload.file)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun `업로드 응답을 즉시 목록에 추가하고 라벨은 다듬어 보낸다`() {
        repository.onUpload = { _, _ -> Result.success(application(42)) }
        val vm = viewModel()
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        assertEquals("지원서", vm.uiState.value.fileUpload.label)
        vm.file(PastApplicationFileUploadIntent.LabelChanged("  카카오 2026  "))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals("카카오 2026", repository.uploads.single().second)
        assertEquals(
            listOf(42L),
            vm.uiState.value.applications
                .map { it.id },
        )
        assertNull(vm.uiState.value.fileUpload.file)
        assertTrue(vm.uiState.value.fileUpload.clearUriRequested)
    }

    @Test fun `빈 라벨과 긴 라벨은 전송하지 않는다`() {
        val vm = viewModel()
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.LabelChanged("  "))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals(ProfileFieldViolation.Required, vm.uiState.value.fileUpload.labelError)
        vm.file(PastApplicationFileUploadIntent.LabelChanged("가".repeat(51)))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals(ProfileFieldViolation.TooLong(50), vm.uiState.value.fileUpload.labelError)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test fun `실패 후에도 입력을 보존하고 같은 파일을 다시 읽어 재시도한다`() {
        repository.onUpload = { file, _ ->
            assertEquals("내용", file.openStream().use { it.reader().readText() })
            Result.failure(CoreDataFailure.NetworkUnavailable(IOException()))
        }
        val vm = viewModel()
        val file = file()
        vm.file(PastApplicationFileUploadIntent.Selected(file))
        vm.file(PastApplicationFileUploadIntent.LabelChanged("새 이름"))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertSame(file, vm.uiState.value.fileUpload.file)
        assertEquals("새 이름", vm.uiState.value.fileUpload.label)
        assertEquals(FailureKind.NoConnection, vm.uiState.value.fileUpload.failure)
        repository.onUpload = { input, _ ->
            assertEquals("내용", input.openStream().use { it.reader().readText() })
            Result.success(application(2))
        }
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals(2, repository.uploads.size)
        assertNull(vm.uiState.value.fileUpload.file)
    }

    @Test fun `전송 중 중복 제출과 닫기를 막고 지연된 새로고침은 완료 뒤 실행한다`() {
        val response = CompletableDeferred<Result<PastApplication>>()
        repository.onUpload = { _, _ -> response.await() }
        val vm = viewModel()
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.Submit)
        vm.file(PastApplicationFileUploadIntent.Submit)
        vm.file(PastApplicationFileUploadIntent.Dismiss)
        vm.file(PastApplicationFileUploadIntent.LabelChanged("바뀌면 안 됨"))
        var reads = 0
        repository.onGetPastApplications = {
            reads++
            Result.success(listOf(application(1)))
        }
        vm.onIntent(PastApplicationListIntent.Refresh)
        assertEquals(0, reads)
        assertEquals(1, repository.uploads.size)
        assertTrue(vm.uiState.value.fileUpload.isSubmitting)
        assertEquals("지원서", vm.uiState.value.fileUpload.label)
        response.complete(Result.success(application(1)))
        assertEquals(1, reads)
        assertEquals(
            listOf(1L),
            vm.uiState.value.applications
                .map { it.id },
        )
    }

    @Test fun `전송 전에 이미 시작한 읽기의 늦은 응답은 업로드 결과를 덮지 않는다`() {
        val response = CompletableDeferred<Result<List<PastApplication>>>()
        val vm = viewModel()
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        repository.onGetPastApplications = { withContext(NonCancellable) { response.await() } }
        vm.onIntent(PastApplicationListIntent.Refresh)
        repository.onGetPastApplications = { Result.success(repository.applications.toList()) }
        vm.file(PastApplicationFileUploadIntent.Submit)
        response.complete(Result.success(emptyList()))
        assertEquals(1, vm.uiState.value.applications.size)
    }

    @Test fun `복원은 이름만 유지하고 요청과 잠금과 파일 스트림은 재생하지 않는다`() {
        val handle = SavedStateHandle()
        val response = CompletableDeferred<Result<PastApplication>>()
        repository.onUpload = { _, _ -> response.await() }
        val vm = viewModel(handle)
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.LabelChanged("수정한 이름"))
        vm.file(PastApplicationFileUploadIntent.Submit)
        val parcel = Parcel.obtain()
        val restoredHandle =
            try {
                parcel.writeBundle(handle.savedStateProvider().saveState())
                parcel.setDataPosition(0)
                SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
            } finally {
                parcel.recycle()
            }
        val restored = viewModel(restoredHandle)
        assertNull(restored.uiState.value.fileUpload.file)
        restored.file(PastApplicationFileUploadIntent.Selected(file(), restored = true))
        assertEquals("수정한 이름", restored.uiState.value.fileUpload.label)
        assertFalse(restored.uiState.value.fileUpload.isSubmitting)
        assertEquals(1, repository.uploads.size)
        response.complete(Result.success(application(1)))
    }

    @Test fun `취소와 세션 만료는 저장한 이름과 파일을 비우고 재요청을 막는다`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.LabelChanged("민감한 이름"))
        vm.file(PastApplicationFileUploadIntent.Dismiss)
        assertTrue(handle.keys().isEmpty())
        repository.onUpload = { _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_INVALID", IOException())) }
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals(ProfileSessionEnd.Expired, vm.uiState.value.sessionEnd)
        assertNull(vm.uiState.value.fileUpload.file)
        assertTrue(handle.keys().isEmpty())
        vm.onIntent(PastApplicationListIntent.ConsumeSessionEnded)
        vm.file(PastApplicationFileUploadIntent.Selected(file()))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertEquals(1, repository.uploads.size)
    }

    @Test fun `파일 선택 오류는 소비한 뒤 같은 이유로 다시 알릴 수 있다`() {
        val vm = viewModel()
        UploadFileSelectionReason.entries.forEach { reason ->
            repeat(2) {
                vm.file(PastApplicationFileUploadIntent.SelectionFailed(reason))
                assertEquals(reason, vm.uiState.value.fileUpload.selectionFailure)
                vm.file(PastApplicationFileUploadIntent.ConsumeSelectionFailure)
                assertNull(vm.uiState.value.fileUpload.selectionFailure)
            }
        }
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun `복원 직후 목록을 확인하기 전에는 상한을 추측해 업로드하지 않는다`() {
        val response = CompletableDeferred<Result<List<PastApplication>>>()
        repository.onGetPastApplications = { response.await() }
        val vm = viewModel()
        vm.file(PastApplicationFileUploadIntent.Selected(file(), restored = true))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertTrue(repository.uploads.isEmpty())
        response.complete(Result.success((1L..10).map(::application)))
        vm.file(PastApplicationFileUploadIntent.Submit)
        assertTrue(repository.uploads.isEmpty())
        assertEquals(PastApplicationListMessage.LimitReached, vm.uiState.value.message)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        PastApplicationListViewModel(
            getPastApplications = GetPastApplicationsUseCase(repository),
            updateItemCategory = UpdatePastApplicationItemCategoryUseCase(repository),
            deletePastApplication = DeletePastApplicationUseCase(repository),
            uploadPastApplicationText = UploadPastApplicationTextUseCase(repository),
            uploadPastApplication = UploadPastApplicationUseCase(repository),
            errorReporter = reporter,
            savedStateHandle = handle,
        )

    private fun PastApplicationListViewModel.file(event: PastApplicationFileUploadIntent) =
        onIntent(PastApplicationListIntent.FileUpload(event))

    private fun PastApplicationListViewModel.screen(event: PastApplicationListEvent) = onIntent(PastApplicationListIntent.Screen(event))

    private fun application(id: Long) = PastApplication(id, "지원서 $id", emptyList(), null)

    private fun file() = UploadFile("지원서.pdf", 6) { ByteArrayInputStream("내용".toByteArray()) }
}

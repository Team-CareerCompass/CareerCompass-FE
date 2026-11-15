package com.careercompass.feature.profile.presentation.pastapplication

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.usecase.UploadPastApplicationUseCase
import com.careercompass.core.ui.component.DirectInputEvent
import com.careercompass.feature.profile.domain.usecase.DeletePastApplicationUseCase
import com.careercompass.feature.profile.domain.usecase.GetPastApplicationsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdatePastApplicationItemCategoryUseCase
import com.careercompass.feature.profile.domain.usecase.UploadPastApplicationTextUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import java.io.IOException

/** 회전처럼 ViewModel 을 재사용하지 않고 Bundle 을 Parcel 로 왕복시켜 새 ViewModel 에 넘긴다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PastApplicationInputRestoreTest {
    private val repository = FakePastApplicationRepository()
    private val reporter =
        object : ErrorReporter {
            override fun writeFailure(
                throwable: Throwable,
                attributes: Map<String, String>,
            ) = Unit
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `프로세스가 죽어도 다시 연 시트에 라벨과 여러 문단이 남는다`() {
        val handle = SavedStateHandle()
        fillDraft(viewModel(handle))

        val restored = viewModel(handle.roundTrip())

        assertNull(restored.uiState.value.directInput)
        restored.open()
        assertDraft(restored)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun `업로드 중 죽어도 입력만 복원하고 요청을 재전송하거나 버튼을 잠그지 않는다`() {
        repository.onUpload = { _, _ -> awaitCancellation() }
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        original.input(DirectInputEvent.Submitted)
        assertTrue(
            original.uiState.value.directInput!!
                .isSubmitting,
        )

        val restored = viewModel(handle.roundTrip())
        restored.open()

        assertDraft(restored)
        assertFalse(
            restored.uiState.value.directInput!!
                .isSubmitting,
        )
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun `업로드 실패 뒤에도 초안은 남고 오류 신호는 되살리지 않는다`() {
        repository.onUpload = { _, _ -> Result.failure(IOException("offline")) }
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        original.input(DirectInputEvent.Submitted)

        val restored = viewModel(handle.roundTrip())
        restored.open()

        assertDraft(restored)
        assertNull(restored.uiState.value.message)
        assertNull(
            restored.uiState.value.directInput!!
                .labelError,
        )
        assertFalse(
            restored.uiState.value.directInput!!
                .isSubmitting,
        )
    }

    @Test
    fun `명시적으로 취소한 초안은 복원하지 않는다`() {
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        original.input(DirectInputEvent.Dismissed)

        assertEmptyDraft(handle)
    }

    @Test
    fun `업로드가 성공한 초안은 다시 등록되지 않게 지운다`() {
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        original.input(DirectInputEvent.Submitted)

        assertEmptyDraft(handle)
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun `세션이 만료되면 이전 계정의 초안을 지운다`() {
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        repository.onUpload = { _, _ -> Result.failure(CoreDataFailure.Unauthorized("UNAUTHORIZED", IllegalStateException())) }
        original.input(DirectInputEvent.Submitted)

        assertEmptyDraft(handle)
    }

    @Test
    fun `본문이 Bundle 한도를 넘으면 이전 본문을 완전한 초안처럼 복원하지 않는다`() {
        val handle = SavedStateHandle()
        val original = viewModel(handle)
        fillDraft(original)
        original.input(DirectInputEvent.ContentChanged("가".repeat(100_001)))

        val restored = viewModel(handle.roundTrip())
        restored.open()

        assertEquals(
            "인턴 지원서",
            restored.uiState.value.directInput!!
                .label,
        )
        assertEquals(
            "",
            restored.uiState.value.directInput!!
                .content,
        )
    }

    private fun assertEmptyDraft(handle: SavedStateHandle) {
        val restored = viewModel(handle.roundTrip())
        restored.open()
        assertEquals(
            "",
            restored.uiState.value.directInput!!
                .label,
        )
        assertEquals(
            "",
            restored.uiState.value.directInput!!
                .content,
        )
    }

    private fun fillDraft(viewModel: PastApplicationListViewModel) {
        viewModel.open()
        viewModel.input(DirectInputEvent.LabelChanged("인턴 지원서"))
        viewModel.input(DirectInputEvent.ContentChanged("지원 동기\n\n경험과 강점"))
    }

    private fun assertDraft(viewModel: PastApplicationListViewModel) {
        assertEquals(
            "인턴 지원서",
            viewModel.uiState.value.directInput!!
                .label,
        )
        assertEquals(
            "지원 동기\n\n경험과 강점",
            viewModel.uiState.value.directInput!!
                .content,
        )
    }

    private fun PastApplicationListViewModel.open() =
        onIntent(PastApplicationListIntent.Screen(PastApplicationListEvent.DirectInputClicked))

    private fun PastApplicationListViewModel.input(event: DirectInputEvent) = onIntent(PastApplicationListIntent.DirectInput(event))

    private fun SavedStateHandle.roundTrip(): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(savedStateProvider().saveState())
            parcel.setDataPosition(0)
            SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }

    private fun viewModel(savedStateHandle: SavedStateHandle) =
        PastApplicationListViewModel(
            getPastApplications = GetPastApplicationsUseCase(repository),
            updateItemCategory = UpdatePastApplicationItemCategoryUseCase(repository),
            deletePastApplication = DeletePastApplicationUseCase(repository),
            uploadPastApplicationText = UploadPastApplicationTextUseCase(repository),
            uploadPastApplication = UploadPastApplicationUseCase(repository),
            errorReporter = reporter,
            savedStateHandle = savedStateHandle,
        )
}

package com.careercompass.feature.foryou.presentation.export

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.testing.FakeForYouRepository
import com.careercompass.feature.foryou.domain.usecase.ExportStrengthsUseCase
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class StrengthExportViewModelTest {
    private val repository = FakeForYouRepository(onExport = { format, _ -> Result.success(StrengthExport(format, "내 강점")) })
    private val experiences = FakeExperienceRepository()
    private val reporter =
        object : ErrorReporter {
            override fun writeFailure(
                throwable: Throwable,
                attributes: Map<String, String>,
            ) {}
        }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        StrengthExportViewModel(ExportStrengthsUseCase(repository), experiences, reporter, handle)

    @Test fun `네 형식의 선택과 서버 본문을 그대로 미리 보고 복사한다`() {
        val vm = viewModel()
        ExportFormat.entries.forEach { format ->
            vm.onIntent(StrengthExportIntent.SelectFormat(format))
            vm.onIntent(StrengthExportIntent.GeneratePreview)
            assertEquals(
                format,
                vm.uiState.value.preview
                    ?.format,
            )
            vm.onIntent(StrengthExportIntent.CopyPreview)
            assertEquals("내 강점", vm.uiState.value.copyRequest)
            vm.onIntent(StrengthExportIntent.ConsumeCopy)
            assertNull(vm.uiState.value.copyRequest)
        }
        assertEquals(ExportFormat.entries, repository.exportCalls.map { it.first })
        assertTrue(repository.exportCalls.all { it.second == ExportSection.entries })
    }

    @Test fun `빈 선택은 요청하지 않고 선택을 바꾸면 이전 미리보기를 복사할 수 없다`() {
        val vm = viewModel()
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        ExportSection.entries.forEach { vm.onIntent(StrengthExportIntent.ToggleSection(it)) }
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        vm.onIntent(StrengthExportIntent.CopyPreview)
        assertEquals(1, repository.exportCalls.size)
        assertNull(vm.uiState.value.preview)
        assertNull(vm.uiState.value.copyRequest)
        vm.onIntent(StrengthExportIntent.ToggleSection(ExportSection.Basic))
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        assertEquals(listOf(ExportSection.Basic), repository.exportCalls.last().second)
        vm.onIntent(StrengthExportIntent.SelectFormat(ExportFormat.Html))
        assertFalse(vm.uiState.value.canCopy)
    }

    @Test fun `생성 중 중복 요청과 형식 변경을 막고 완료 후 다시 연다`() {
        val response = CompletableDeferred<Result<StrengthExport>>()
        repository.onExport = { _, _ -> response.await() }
        val vm = viewModel()
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        vm.onIntent(StrengthExportIntent.SelectFormat(ExportFormat.Html))
        vm.onIntent(StrengthExportIntent.ToggleSection(ExportSection.Basic))
        assertEquals(1, repository.exportCalls.size)
        assertEquals(ExportFormat.Markdown, vm.uiState.value.format)
        assertEquals(ExportSection.entries, vm.uiState.value.sections)
        response.complete(Result.success(StrengthExport(ExportFormat.Markdown, "완료")))
        assertTrue(vm.uiState.value.canCopy)
    }

    @Test fun `경험이 없음을 알리고 빈 서버 결과는 복사하지 않는다`() {
        var limit = 0
        experiences.onGetExperiences = { _, _, requested ->
            limit = requested
            Result.success(CursorPage(emptyList(), null))
        }
        repository.onExport = { format, _ -> Result.success(StrengthExport(format, "  ")) }
        val vm = viewModel()
        vm.onIntent(StrengthExportIntent.RefreshAvailability)
        assertEquals(1, limit)
        assertEquals(false, vm.uiState.value.hasExperiences)
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        vm.onIntent(StrengthExportIntent.CopyPreview)
        assertFalse(vm.uiState.value.canCopy)
        assertNull(vm.uiState.value.copyRequest)
    }

    @Test fun `경험 추가에서 돌아온 새 조회를 이전 응답이 덮지 않는다`() {
        lateinit var delayed: Continuation<Result<CursorPage<Experience>>>
        experiences.onGetExperiences = { _, _, _ -> suspendCoroutine { delayed = it } }
        val vm = viewModel()
        vm.onIntent(StrengthExportIntent.RefreshAvailability)
        experiences.onGetExperiences = null
        experiences.experiences += Experience(1, "자격증", null, null, ExperienceDetails.Certificate(null), null)
        vm.onIntent(StrengthExportIntent.RefreshAvailability)
        delayed.resume(Result.success(CursorPage(emptyList(), null)))
        assertEquals(true, vm.uiState.value.hasExperiences)
    }

    @Test fun `입력만 복원하고 결과와 복사와 요청은 재실행하지 않는다`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onIntent(StrengthExportIntent.SelectFormat(ExportFormat.Plain))
        vm.onIntent(StrengthExportIntent.ToggleSection(ExportSection.Awards))
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        vm.onIntent(StrengthExportIntent.CopyPreview)
        val restored = viewModel(handle).uiState.value
        assertEquals(ExportFormat.Plain, restored.format)
        assertFalse(ExportSection.Awards in restored.sections)
        assertNull(restored.preview)
        assertNull(restored.copyRequest)
        assertFalse(restored.isGenerating)
        assertEquals(1, repository.exportCalls.size)
    }

    @Test fun `세션 만료 뒤 늦은 생성 결과는 개인정보를 되살리지 않는다`() {
        lateinit var delayed: Continuation<Result<StrengthExport>>
        repository.onExport = { _, _ -> suspendCoroutine { delayed = it } }
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onIntent(StrengthExportIntent.SelectFormat(ExportFormat.Html))
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        experiences.onGetExperiences = { _, _, _ -> Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", Exception())) }
        vm.onIntent(StrengthExportIntent.RefreshAvailability)
        delayed.resume(Result.success(StrengthExport(ExportFormat.Html, "개인 정보")))
        assertTrue(vm.uiState.value.sessionEnded)
        assertNull(vm.uiState.value.preview)
        assertTrue(handle.keys().isEmpty())
        vm.onIntent(StrengthExportIntent.ConsumeSessionEnded)
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        assertEquals(1, repository.exportCalls.size)
    }

    @Test fun `생성 실패는 선택을 보존하고 같은 조건으로 재시도한다`() {
        repository.onExport = { _, _ -> Result.failure(CoreDataFailure.NetworkUnavailable(IOException())) }
        val vm = viewModel()
        vm.onIntent(StrengthExportIntent.SelectFormat(ExportFormat.Notion))
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        assertEquals(FailureKind.NoConnection, vm.uiState.value.failure)
        repository.onExport = { format, _ -> Result.success(StrengthExport(format, "다시 만든 본문")) }
        vm.onIntent(StrengthExportIntent.GeneratePreview)
        assertNull(vm.uiState.value.failure)
        assertEquals(ExportFormat.Notion, repository.exportCalls.last().first)
    }
}

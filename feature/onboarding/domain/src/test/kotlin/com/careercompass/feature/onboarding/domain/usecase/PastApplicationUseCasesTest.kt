package com.careercompass.feature.onboarding.domain.usecase

import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.usecase.UploadPastApplicationUseCase
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.UploadFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class PastApplicationUseCasesTest {
    private val file = UploadFile(fileName = "resume.pdf", sizeBytes = 12L) { ByteArrayInputStream(ByteArray(12)) }

    @Test
    fun `업로드는 라벨을 다듬어 저장소에 넘기고 결과를 돌려준다`() =
        runTest {
            val repository = FakePastApplicationRepository()

            val uploaded = UploadPastApplicationUseCase(repository)(file, " 2024 카카오 인턴 자소서 ").getOrThrow()

            assertEquals("2024 카카오 인턴 자소서", uploaded.label)
            assertEquals(listOf(file to "2024 카카오 인턴 자소서"), repository.uploads)
        }

    @Test
    fun `빈 라벨은 요청 전에 거부한다`() {
        val repository = FakePastApplicationRepository.strict()

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { UploadPastApplicationUseCase(repository)(file, "  ") }
        }
    }

    @Test
    fun `업로드 실패는 그대로 전파한다`() =
        runTest {
            val failure = IOException("offline")
            val repository = FakePastApplicationRepository(onUpload = { _, _ -> Result.failure(failure) })

            assertSame(failure, UploadPastApplicationUseCase(repository)(file, "label").exceptionOrNull())
        }

    @Test
    fun `목록과 삭제는 저장소에 위임한다`() =
        runTest {
            val existing = PastApplication(id = 3L, label = "지원서", items = emptyList(), createdAt = null)
            val repository = FakePastApplicationRepository(initial = listOf(existing))

            assertEquals(listOf(existing), GetOnboardingPastApplicationsUseCase(repository)().getOrThrow())
            assertTrue(DeletePastApplicationUseCase(repository)(3L).isSuccess)
            assertTrue(repository.applications.isEmpty())
            assertTrue(DeletePastApplicationUseCase(repository)(3L).isFailure)
        }

    @Test
    fun `분류 조정은 저장소가 돌려준 항목을 그대로 전달한다`() =
        runTest {
            val item = PastApplicationItem(id = 7L, category = PastApplicationCategory.Other, content = "내용", confident = false)
            val existing = PastApplication(id = 3L, label = "지원서", items = listOf(item), createdAt = null)
            val repository = FakePastApplicationRepository(initial = listOf(existing))

            val updated =
                UpdatePastApplicationItemCategoryUseCase(repository)(
                    applicationId = 3L,
                    itemId = 7L,
                    category = PastApplicationCategory.Motivation,
                ).getOrThrow()

            assertEquals(PastApplicationCategory.Motivation, updated.category)
            assertTrue(updated.confident)
            assertEquals(
                listOf(updated),
                repository.applications
                    .single()
                    .items,
            )
        }

    @Test
    fun `분류 조정 실패는 그대로 전파한다`() =
        runTest {
            val failure = IOException("offline")
            val repository = FakePastApplicationRepository(onUpdateItemCategory = { _, _, _ -> Result.failure(failure) })

            assertSame(
                failure,
                UpdatePastApplicationItemCategoryUseCase(repository)(
                    applicationId = 3L,
                    itemId = 7L,
                    category = PastApplicationCategory.Growth,
                ).exceptionOrNull(),
            )
        }
}

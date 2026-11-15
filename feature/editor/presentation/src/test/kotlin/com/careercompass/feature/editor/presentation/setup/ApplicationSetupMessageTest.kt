package com.careercompass.feature.editor.presentation.setup

import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class ApplicationSetupMessageTest {
    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun `생성 실패는 조회나 과거 지원서 상한 문맥 없이 실패 표의 문장을 읽는다`() {
        FailureKind.entries.forEach { kind ->
            assertEquals(
                kind.display(FailureSurface.Unspecified).sentence(resources),
                ApplicationSetupMessage.CreateFailed(kind).text(resources),
            )
        }
        assertFalse(ApplicationSetupMessage.CreateFailed(FailureKind.Unexpected).text(resources).contains("불러오"))
        assertFalse(ApplicationSetupMessage.CreateFailed(FailureKind.LimitExceeded).text(resources).contains("10"))
    }

    @Test
    fun `재시도로 바뀌지 않는 실패에는 잠시 후 다시 시도하라는 문구를 붙이지 않는다`() {
        FailureKind.entries.filterNot { it.display().isRetryable }.forEach { kind ->
            assertFalse(ApplicationSetupMessage.CreateFailed(kind).text(resources).contains("잠시 후 다시"))
        }
    }
}

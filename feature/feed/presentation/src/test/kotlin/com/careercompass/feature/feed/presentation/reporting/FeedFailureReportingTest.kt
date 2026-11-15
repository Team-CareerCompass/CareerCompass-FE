package com.careercompass.feature.feed.presentation.reporting

import com.careercompass.core.common.reporting.ERROR_REPORT_KEY_TRANSPORT
import com.careercompass.core.common.reporting.ERROR_REPORT_KEY_TYPE
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.net.UnknownServiceException

/**
 * 공통 규칙([com.careercompass.core.common.reporting.recordStagedFailure])이 피드 단계 속성과 함께
 * 적용되는지만 본다. 원인 분류 자체는 `core:common` 의 테스트가 고정한다.
 */
class FeedFailureReportingTest {
    private val reporter = RecordingErrorReporter()

    @Test
    fun `그 밖의 실패는 단계 속성과 함께 기록한다`() {
        val throwable = CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())

        reporter.recordFeedFailure(FeedFailureStage.FeedLoad, throwable)

        assertEquals(listOf("feed_load"), reporter.stages)
        assertEquals(
            CoreDataFailure.ServerError::class.java.name,
            reporter.records.single().second[ERROR_REPORT_KEY_TYPE],
        )
    }

    @Test
    fun `단계 밖 속성은 그대로 함께 실린다`() {
        // 예외 문구는 리포터가 버린다. 단계 말고 남겨야 할 값은 속성으로만 콘솔에 닿는다(#368).
        reporter.recordFeedFailure(
            stage = FeedFailureStage.PostingRaw,
            throwable = IllegalStateException("unsupported external url scheme: intent"),
            attributes = mapOf(FEED_REPORT_KEY_URL_SCHEME to "intent"),
        )

        val attributes = reporter.records.single().second
        assertEquals("posting_raw", attributes[FEED_REPORT_KEY_STAGE])
        assertEquals("intent", attributes[FEED_REPORT_KEY_URL_SCHEME])
    }

    @Test
    fun `cleartext 차단은 네트워크 단절로 접혀 와도 결함으로 기록한다`() {
        // usesCleartextTraffic=false 인데 http 로 요청한 우리 설정 결함이다 — 사용자 환경이 아니다.
        val throwable = CoreDataFailure.NetworkUnavailable(UnknownServiceException("CLEARTEXT not permitted"))

        reporter.recordFeedFailure(FeedFailureStage.BoardDetect, throwable)
        reporter.recordFeedFailure(FeedFailureStage.BoardDetect, throwable)

        assertEquals(listOf("board_detect", "board_detect"), reporter.stages)
        assertEquals("defect", reporter.records.first().second[ERROR_REPORT_KEY_TRANSPORT])
    }

    @Test
    fun `네트워크 단절은 원인과 단계 조합마다 세션 첫 건만 남긴다`() {
        repeat(4) {
            reporter.recordFeedFailure(FeedFailureStage.FeedLoad, CoreDataFailure.NetworkUnavailable(UnknownHostException()))
        }
        reporter.recordFeedFailure(FeedFailureStage.PostingDetail, CoreDataFailure.NetworkUnavailable(UnknownHostException()))

        assertEquals(listOf("feed_load", "posting_detail"), reporter.stages)
        assertEquals("transient", reporter.records.first().second[ERROR_REPORT_KEY_TRANSPORT])
    }

    @Test
    fun `서버 점검 503 은 서버가 알린 상태라 기록하지 않는다`() {
        reporter.recordFeedFailure(
            FeedFailureStage.PostingDetail,
            CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException()),
        )

        assertTrue(reporter.records.isEmpty())
    }
}

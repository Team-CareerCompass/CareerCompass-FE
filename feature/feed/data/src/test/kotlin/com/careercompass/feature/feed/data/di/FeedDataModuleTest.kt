package com.careercompass.feature.feed.data.di

import com.careercompass.core.datastore.StoreScope
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingBoardRef
import com.careercompass.core.model.posting.PostingType
import com.careercompass.feature.feed.data.FeedSnapshotRepositoryImpl
import com.careercompass.feature.feed.data.support.FakeLocalStoreRegistry
import com.careercompass.feature.feed.domain.model.FeedSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class FeedDataModuleTest {
    private val registry = FakeLocalStoreRegistry()

    @Test
    fun `스냅샷 저장소는 SESSION 스코프로 등록한다`() {
        FeedSnapshotStoreModule.provideFeedSnapshotDataStore(registry)

        assertEquals(listOf("FeedSnapshot" to StoreScope.SESSION), registry.registrations)
    }

    @Test
    fun `같은 이름을 다시 요청하면 같은 저장소를 돌려받는다`() {
        val first = FeedSnapshotStoreModule.provideFeedSnapshotDataStore(registry)
        val second = FeedSnapshotStoreModule.provideFeedSnapshotDataStore(registry)

        assertSame(first, second)
    }

    @Test
    fun `로그아웃으로 SESSION 스코프가 비워지면 스냅샷도 사라진다`() =
        runTest {
            val repository =
                FeedSnapshotRepositoryImpl(
                    dataStore = FeedSnapshotStoreModule.provideFeedSnapshotDataStore(registry),
                    json = Json { ignoreUnknownKeys = true },
                    localStoreRegistry = registry,
                )
            repository.save(FeedSnapshot(listOf(posting(id = 1)), Instant.parse("2026-09-03T05:20:00Z")))
            assertNotNull(repository.load().getOrThrow())

            registry.clearScope(StoreScope.SESSION)

            assertNull(repository.load().getOrThrow())
        }

    private fun posting(id: Long): Posting =
        Posting(
            id = id,
            title = "공고 $id",
            type = PostingType.Recruit,
            board = PostingBoardRef(id = 1L, name = "게시판 1"),
            dueDate = null,
            collectedAt = Instant.parse("2026-09-03T04:00:00Z"),
            score = null,
            scoreLabel = null,
            isRead = false,
            isBookmarked = false,
        )
}

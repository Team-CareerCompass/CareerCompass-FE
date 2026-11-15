package com.careercompass.feature.feed.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.careercompass.core.datastore.StoreScope
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingBoardRef
import com.careercompass.core.model.posting.PostingType
import com.careercompass.core.model.posting.SuitabilityLabel
import com.careercompass.feature.feed.data.support.FakeLocalStoreRegistry
import com.careercompass.feature.feed.data.support.InMemoryPreferencesDataStore
import com.careercompass.feature.feed.domain.model.FeedSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class FeedSnapshotRepositoryImplTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val registry = FakeLocalStoreRegistry()
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = FeedSnapshotRepositoryImpl(dataStore, json, registry)

    @Test
    fun `저장 전에는 스냅샷이 없다`() =
        runTest {
            assertNull(repository.load().getOrThrow())
        }

    @Test
    fun `저장한 스냅샷은 모든 필드가 같은 값으로 복원된다`() =
        runTest {
            val snapshot =
                FeedSnapshot(
                    postings =
                        listOf(
                            posting(id = 1, dueDate = LocalDate.of(2026, 9, 10), score = 88, isRead = true, isBookmarked = true),
                            posting(id = 2, type = PostingType.Scholarship, dueDate = null, score = null),
                        ),
                    savedAt = SAVED_AT,
                )

            assertTrue(repository.save(snapshot).isSuccess)

            assertEquals(snapshot, repository.load().getOrThrow())
            assertEquals(snapshot, FeedSnapshotRepositoryImpl(dataStore, json, registry).load().getOrThrow())
        }

    @Test
    fun `다시 저장하면 이전 스냅샷을 덮어쓴다`() =
        runTest {
            repository.save(FeedSnapshot(listOf(posting(id = 1)), SAVED_AT))

            repository.save(FeedSnapshot(listOf(posting(id = 2)), SAVED_AT.plusSeconds(60)))

            val loaded = requireNotNull(repository.load().getOrThrow())
            assertEquals(listOf(2L), loaded.postings.map(Posting::id))
            assertEquals(SAVED_AT.plusSeconds(60), loaded.savedAt)
        }

    @Test
    fun `첫 페이지 크기를 넘는 목록은 잘라 저장한다`() =
        runTest {
            val oversized = FeedSnapshot(List(FeedSnapshot.MAX_POSTINGS + 5) { posting(id = it + 1L) }, SAVED_AT)

            repository.save(oversized)

            val loaded = requireNotNull(repository.load().getOrThrow())
            assertEquals(FeedSnapshot.MAX_POSTINGS, loaded.postings.size)
            assertEquals((1L..FeedSnapshot.MAX_POSTINGS).toList(), loaded.postings.map(Posting::id))
        }

    @Test
    fun `손상된 JSON 은 스냅샷 없음으로 읽는다`() =
        runTest {
            val corrupted = InMemoryPreferencesDataStore(mutablePreferencesOf(SNAPSHOT_KEY to "{not json"))

            assertNull(FeedSnapshotRepositoryImpl(corrupted, json, registry).load().getOrThrow())
        }

    @Test
    fun `모르는 열거값이나 불변식 위반은 스냅샷 없음으로 읽는다`() =
        runTest {
            val unknownType = snapshotJson(type = "\"legacy\"")
            val scoreWithoutLabel = snapshotJson(score = "90", scoreLabel = "null")
            val invalidInstant = snapshotJson(savedAt = "\"어제\"")
            val emptyPostings = """{"savedAt":"$SAVED_AT","postings":[]}"""

            listOf(unknownType, scoreWithoutLabel, invalidInstant, emptyPostings).forEach { raw ->
                val store = InMemoryPreferencesDataStore(mutablePreferencesOf(SNAPSHOT_KEY to raw))

                assertNull(raw, FeedSnapshotRepositoryImpl(store, json, registry).load().getOrThrow())
            }
        }

    @Test
    fun `모르는 키가 섞여 있어도 아는 필드만으로 읽는다`() =
        runTest {
            val withExtraKeys = snapshotJson(extra = ""","futureField":123""")
            val store = InMemoryPreferencesDataStore(mutablePreferencesOf(SNAPSHOT_KEY to withExtraKeys))

            val loaded = requireNotNull(FeedSnapshotRepositoryImpl(store, json, registry).load().getOrThrow())

            assertEquals(listOf(7L), loaded.postings.map(Posting::id))
        }

    /**
     * #348 — 세션이 끝난 뒤에 커밋된 스냅샷은 다음 계정의 오프라인 피드에 앞 계정의 공고를 띄운다.
     */
    @Test
    fun `세션이 끝난 뒤 커밋되는 저장은 저장소를 비운 채로 둔다`() =
        runTest {
            val store = registry.store("FeedSnapshot", StoreScope.SESSION)
            val gate = CompletableDeferred<Unit>()
            val repository = FeedSnapshotRepositoryImpl(GatedPreferencesDataStore(store, gate), json, registry)
            val saved = async { repository.save(FeedSnapshot(listOf(posting(id = 1)), SAVED_AT)) }
            runCurrent()

            registry.clearScope(StoreScope.SESSION)
            gate.complete(Unit)
            saved.await().getOrThrow()

            assertNull(repository.load().getOrThrow())
        }

    @Test
    fun `clear 는 스냅샷을 지운다`() =
        runTest {
            repository.save(FeedSnapshot(listOf(posting(id = 1)), SAVED_AT))

            assertTrue(repository.clear().isSuccess)

            assertNull(repository.load().getOrThrow())
            assertTrue(dataStore.snapshot().asMap().isEmpty())
        }

    @Test
    fun `쓰기 실패는 Result 실패로 돌려준다`() =
        runTest {
            dataStore.failOnWrite = true

            val result = repository.save(FeedSnapshot(listOf(posting(id = 1)), SAVED_AT))

            assertTrue(result.exceptionOrNull() is IOException)
            assertNull(repository.load().getOrThrow())
        }

    private fun snapshotJson(
        savedAt: String = "\"$SAVED_AT\"",
        type: String = "\"recruit\"",
        score: String = "null",
        scoreLabel: String = "null",
        extra: String = "",
    ): String =
        """
        {"savedAt":$savedAt,"postings":[{"id":7,"title":"공고 7","type":$type,"boardId":1,"boardName":"게시판 1",
        "dueDate":null,"collectedAt":"$SAVED_AT","score":$score,"scoreLabel":$scoreLabel,"isRead":false,"isBookmarked":false$extra}]}
        """.trimIndent()

    private fun posting(
        id: Long,
        type: PostingType = PostingType.Recruit,
        dueDate: LocalDate? = null,
        score: Int? = null,
        isRead: Boolean = false,
        isBookmarked: Boolean = false,
    ): Posting =
        Posting(
            id = id,
            title = "공고 $id",
            type = type,
            board = PostingBoardRef(id = 1L, name = "게시판 1"),
            dueDate = dueDate,
            collectedAt = SAVED_AT.minusSeconds(3600),
            score = score,
            scoreLabel = score?.let(SuitabilityLabel::fromScore),
            isRead = isRead,
            isBookmarked = isBookmarked,
        )

    /** [gate] 가 열릴 때까지 쓰기를 붙잡아 둔다 — 세션이 끝난 뒤에 커밋되는 쓰기를 재현한다. */
    private class GatedPreferencesDataStore(
        private val delegate: DataStore<Preferences>,
        private val gate: CompletableDeferred<Unit>,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> get() = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            gate.await()
            return delegate.updateData(transform)
        }
    }

    private companion object {
        val SAVED_AT: Instant = Instant.parse("2026-09-03T05:20:00Z")
        val SNAPSHOT_KEY = stringPreferencesKey("snapshot_json")
    }
}

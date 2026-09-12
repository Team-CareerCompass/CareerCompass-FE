package com.careercompass.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.careercompass.core.common.reporting.ErrorReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalStoreRegistryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class RecordingReporter : ErrorReporter {
        val attributes = mutableListOf<Map<String, String>>()

        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) {
            this.attributes += attributes
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = stringPreferencesKey("value")
    private val reporter = RecordingReporter()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun registry(): LocalStoreRegistryImpl = registry(scope) { name -> File(folder.root, "$name.preferences_pb") }

    private fun registry(
        registryScope: CoroutineScope,
        produceFile: (name: String) -> File,
    ): LocalStoreRegistryImpl =
        LocalStoreRegistryImpl(
            produceFile = produceFile,
            registryScope = registryScope,
            errorReporter = reporter,
        )

    @Test
    fun `같은 이름은 같은 인스턴스를 돌려주고 다른 scope 로 재요청하면 실패한다`() {
        val registry = registry()

        val first = registry.store("Token", StoreScope.SESSION)
        val second = registry.store("Token", StoreScope.SESSION)

        assertSame(first, second)
        assertThrows(IllegalStateException::class.java) { registry.store("Token", StoreScope.DEVICE) }
    }

    @Test
    fun `clearScope 는 해당 scope 저장소만 비운다`() =
        runBlocking {
            val registry = registry()
            val session = registry.store("Token", StoreScope.SESSION)
            val device = registry.store("Device", StoreScope.DEVICE)
            session.edit { it[key] = "access" }
            device.edit { it[key] = "device-id" }

            registry.clearScope(StoreScope.SESSION)

            assertNull(session.data.first()[key])
            assertEquals("device-id", device.data.first()[key])
        }

    /**
     * #348 — 세션이 끝난 뒤에 커밋되는 앞 세션의 쓰기를 걸러내는 기준이 세대다. 세대를 비우기보다 늦게 올리면
     * 비운 직후부터 올리기 전까지가 그대로 구멍이 된다.
     */
    @Test
    fun `SESSION 을 비우면 세대가 오르고 그 전에 시작한 쓰기는 버려진다`() =
        runBlocking {
            val registry = registry()
            val session = registry.store("Profile", StoreScope.SESSION)
            val startedAt = registry.sessionGeneration.value

            registry.clearScope(StoreScope.SESSION)
            registry.editWithinSession(session, startedAt) { it[key] = "앞 세션 응답" }

            assertEquals(startedAt + 1, registry.sessionGeneration.value)
            assertNull(session.data.first()[key])

            registry.editWithinSession(session) { it[key] = "이번 세션 응답" }
            assertEquals("이번 세션 응답", session.data.first()[key])
        }

    @Test
    fun `DEVICE 를 비우는 것은 세션 세대와 무관하다`() =
        runBlocking {
            val registry = registry()
            registry.store("Device", StoreScope.DEVICE)

            registry.clearScope(StoreScope.DEVICE)

            assertEquals(0L, registry.sessionGeneration.value)
        }

    /**
     * #367 — 순차로 비우다 첫 예외에서 멈추면 남은 저장소가 그대로 다음 계정에 넘어가고, 호출부의 뒷정리도
     * 실행되지 않는다. 토큰을 가장 먼저 비우고 나머지는 각각 시도한 뒤 실패를 마지막에 던진다.
     */
    @Test
    fun `한 저장소가 실패해도 나머지를 비우고 마지막에 던진다`() =
        runBlocking {
            val brokenParent = File(folder.root, "broken").apply { mkdirs() }
            val registry =
                registry(scope) { name ->
                    val parent = if (name in BROKEN_NAMES) brokenParent else folder.root
                    File(parent, "$name.preferences_pb")
                }
            val token = registry.store(LocalStoreRegistry.TOKEN_STORE_NAME, StoreScope.SESSION)
            val profile = registry.store("Profile", StoreScope.SESSION)
            val broken = BROKEN_NAMES.map { registry.store(it, StoreScope.SESSION) }
            token.edit { it[key] = "access" }
            profile.edit { it[key] = "cached" }
            broken.forEach { store -> store.edit { it[key] = "지워지지 않는다" } }
            // 디렉터리에 쓸 수 없게 만들어 이 둘만 비우기가 IOException 으로 끝나게 한다.
            check(brokenParent.setWritable(false))

            try {
                val thrown = runCatching { registry.clearScope(StoreScope.SESSION) }.exceptionOrNull()

                assertTrue(thrown is IOException)
                // 실패한 저장소가 둘이면 실패도 둘이다 — 첫 예외에서 멈췄다면 suppressed 가 비어 있다.
                assertEquals(BROKEN_NAMES.size - 1, thrown?.suppressed?.size)
                assertNull(token.data.first()[key])
                assertNull(profile.data.first()[key])
            } finally {
                brokenParent.setWritable(true)
            }
        }

    @Test
    fun `이전 프로세스에서 등록된 저장소도 매니페스트로 찾아 비운다`() =
        runBlocking {
            val previous = registry()
            previous.store("Token", StoreScope.SESSION).edit { it[key] = "access" }
            previous.awaitPendingRegistrations()
            scope.cancel()

            val restartedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restarted = registry(restartedScope) { name -> File(folder.root, "$name.preferences_pb") }
            try {
                restarted.clearScope(StoreScope.SESSION)

                assertNull(restarted.store("Token", StoreScope.SESSION).data.first()[key])
            } finally {
                restartedScope.cancel()
            }
        }

    /**
     * #349 — 기본 핸들러는 손상 파일을 읽을 때마다 다시 던지고, 그 예외가 IOException 하위라 읽기는 빈 값으로
     * 가려지고 쓰기만 영구히 실패한다. 복구 수단이 앱 데이터 삭제뿐이 되지 않게 빈 값으로 갈아 쓰되, 손상
     * 사실은 리포터로 남긴다.
     */
    @Test
    fun `손상된 파일로 열어도 쓰기가 성공하고 손상을 남긴다`() =
        runBlocking {
            File(folder.root, "${LocalStoreRegistry.TOKEN_STORE_NAME}.preferences_pb").writeText("깨진 내용")
            val registry = registry()
            val token = registry.store(LocalStoreRegistry.TOKEN_STORE_NAME, StoreScope.SESSION)

            token.edit { it[key] = "access" }

            assertEquals("access", token.data.first()[key])
            assertEquals(
                listOf(LocalStoreRegistry.TOKEN_STORE_NAME),
                reporter.attributes.mapNotNull { it["local_store_name"] },
            )
        }

    /** 매니페스트도 같은 창구로 만든다 — 여기가 손상되면 이름 목록을 못 읽어 세션 정리가 통째로 막힌다. */
    @Test
    fun `매니페스트가 손상돼도 등록과 정리는 이어진다`() =
        runBlocking {
            File(folder.root, "${LocalStoreRegistryImpl.MANIFEST_NAME}.preferences_pb").writeText("깨진 내용")
            val registry = registry()
            val token = registry.store(LocalStoreRegistry.TOKEN_STORE_NAME, StoreScope.SESSION)
            token.edit { it[key] = "access" }

            registry.clearScope(StoreScope.SESSION)

            assertNull(token.data.first()[key])
            assertTrue(reporter.attributes.any { it["local_store_name"] == LocalStoreRegistryImpl.MANIFEST_NAME })
        }

    private companion object {
        /** 비우기가 실패하는 저장소 이름 — 이 저장소들의 파일만 쓸 수 없는 디렉터리에 둔다. */
        val BROKEN_NAMES = listOf("BrokenA", "BrokenB")
    }
}

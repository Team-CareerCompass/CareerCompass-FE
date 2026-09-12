package com.careercompass.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = stringPreferencesKey("value")

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun registry(): LocalStoreRegistryImpl =
        LocalStoreRegistryImpl(
            produceFile = { name -> File(folder.root, "$name.preferences_pb") },
            registryScope = scope,
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
                LocalStoreRegistryImpl(
                    produceFile = { name ->
                        val parent = if (name in BROKEN_NAMES) brokenParent else folder.root
                        File(parent, "$name.preferences_pb")
                    },
                    registryScope = scope,
                )
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
            val restarted =
                LocalStoreRegistryImpl(
                    produceFile = { name -> File(folder.root, "$name.preferences_pb") },
                    registryScope = restartedScope,
                )
            try {
                restarted.clearScope(StoreScope.SESSION)

                assertNull(restarted.store("Token", StoreScope.SESSION).data.first()[key])
            } finally {
                restartedScope.cancel()
            }
        }

    private companion object {
        /** 비우기가 실패하는 저장소 이름 — 이 저장소들의 파일만 쓸 수 없는 디렉터리에 둔다. */
        val BROKEN_NAMES = listOf("BrokenA", "BrokenB")
    }
}

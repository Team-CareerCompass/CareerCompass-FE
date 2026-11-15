package com.careercompass.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileDataSourceTest {
    private val registry = FakeLocalStoreRegistry()
    private val dataSource = ProfileDataSource(registry.store("Profile", StoreScope.SESSION), registry)

    @Test
    fun `프로필 JSON 을 저장하면 그대로 읽힌다`() =
        runTest {
            assertNull(dataSource.profileJson.first())

            dataSource.saveProfile("""{"id":1}""", userId = 1L)

            assertEquals("""{"id":1}""", dataSource.profileJson.first())
        }

    @Test
    fun `프로필과 함께 저장한 사용자 id 는 따로 읽힌다`() =
        runTest {
            assertNull(dataSource.userId.first())

            dataSource.saveProfile("""{"id":42}""", userId = 42L)
            assertEquals(42L, dataSource.userId.first())

            dataSource.saveProfile("""{"id":43}""", userId = 43L)
            assertEquals(43L, dataSource.userId.first())
        }

    @Test
    fun `온보딩 완료 힌트는 기록 전에는 null 이다`() =
        runTest {
            assertNull(dataSource.onboardingDoneHint.first())

            dataSource.setOnboardingDoneHint(false)

            assertEquals(false, dataSource.onboardingDoneHint.first())
        }

    @Test
    fun `빈 프로필 JSON 은 저장하지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dataSource.saveProfile(" ", userId = 1L) }
        }
    }
}

package com.careercompass.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TokenDataSourceTest {
    private val store = InMemoryPreferencesDataStore()
    private val dataSource = TokenDataSource(store)

    @Test
    fun `토큰을 저장하면 로그인 상태가 되고 읽을 수 있다`() =
        runTest {
            assertFalse(dataSource.isLoggedIn.first())

            dataSource.saveTokens(accessToken = "access", refreshToken = "refresh")

            assertTrue(dataSource.isLoggedIn.first())
            assertEquals("access", dataSource.getAccessToken())
            assertEquals("refresh", dataSource.getRefreshToken())
        }

    @Test
    fun `clear 는 두 토큰을 모두 지운다`() =
        runTest {
            dataSource.saveTokens(accessToken = "access", refreshToken = "refresh")

            dataSource.clear()

            assertNull(dataSource.getAccessToken())
            assertNull(dataSource.getRefreshToken())
            assertFalse(dataSource.isLoggedIn.first())
        }

    /**
     * #362 — 읽기는 [IOException] 을 빈 값으로 가리지만 쓰기는 가리지 않는다. 저장 실패를 여기서 삼키면
     * 호출부는 토큰이 선 줄 알고 다음 요청을 내보낸다.
     */
    @Test
    fun `저장 실패는 삼키지 않고 호출부로 올린다`() =
        runTest {
            store.failOnWrite = true

            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking { dataSource.saveTokens(accessToken = "access", refreshToken = "refresh") }
            }
            assertFalse(dataSource.isLoggedIn.first())
        }

    @Test
    fun `빈 토큰은 저장하지 않는다`() =
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { dataSource.saveTokens(accessToken = " ", refreshToken = "refresh") }
            }
        }
}

package com.careercompass.core.datastore.di

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.careercompass.core.datastore.FakeLocalStoreRegistry
import com.careercompass.core.datastore.StoreScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * provider 세 개가 등록하는 이름·스코프를 고정한다.
 *
 * 이름은 저장 파일명 계약이고 스코프는 로그아웃 정리 대상 여부다. 둘 다 provider 안에만 적혀 있어 바꿔도
 * 컴파일이 통과하므로, 값 자체를 여기서 문자열로 다시 적어 대조한다.
 */
class LocalStoreRegistryModuleTest {
    private val registry = FakeLocalStoreRegistry()

    @Test
    fun `토큰·기기·프로필 저장소는 정해진 이름과 스코프로 등록한다`() {
        LocalStoreRegistryModule.provideTokenDataStore(registry)
        LocalStoreRegistryModule.provideDeviceDataStore(registry)
        LocalStoreRegistryModule.provideProfileDataStore(registry)

        assertEquals(
            listOf(
                "Token" to StoreScope.SESSION,
                "Device" to StoreScope.DEVICE,
                "Profile" to StoreScope.SESSION,
            ),
            registry.registrations,
        )
    }

    @Test
    fun `같은 이름을 다시 요청하면 같은 저장소를 돌려받는다`() {
        val first = LocalStoreRegistryModule.provideTokenDataStore(registry)
        val second = LocalStoreRegistryModule.provideTokenDataStore(registry)

        assertSame(first, second)
    }

    @Test
    fun `로그아웃으로 SESSION 이 비워지면 기기 저장소만 값을 지킨다`() =
        runTest {
            val token = LocalStoreRegistryModule.provideTokenDataStore(registry)
            val device = LocalStoreRegistryModule.provideDeviceDataStore(registry)
            val profile = LocalStoreRegistryModule.provideProfileDataStore(registry)
            val key = booleanPreferencesKey("stored")
            listOf(token, device, profile).forEach { store -> store.edit { it[key] = true } }

            registry.clearScope(StoreScope.SESSION)

            assertNull(token.data.first()[key])
            assertNull(profile.data.first()[key])
            assertEquals(true, device.data.first()[key])
        }
}

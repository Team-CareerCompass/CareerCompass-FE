package com.careercompass.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.LocalStoreRegistryImpl
import com.careercompass.core.datastore.StoreScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocalStoreRegistryModule {
    @Binds
    @Singleton
    abstract fun bindLocalStoreRegistry(impl: LocalStoreRegistryImpl): LocalStoreRegistry

    companion object {
        // name 은 저장 파일명 계약 — 바꾸면 기존 사용자 로그인이 풀린다.
        @Provides
        @Singleton
        @TokenDataStore
        fun provideTokenDataStore(registry: LocalStoreRegistry): DataStore<Preferences> =
            registry.store(name = LocalStoreRegistry.TOKEN_STORE_NAME, scope = StoreScope.SESSION)

        @Provides
        @Singleton
        @DeviceDataStore
        fun provideDeviceDataStore(registry: LocalStoreRegistry): DataStore<Preferences> =
            registry.store(name = "Device", scope = StoreScope.DEVICE)

        @Provides
        @Singleton
        @ProfileDataStore
        fun provideProfileDataStore(registry: LocalStoreRegistry): DataStore<Preferences> =
            registry.store(name = "Profile", scope = StoreScope.SESSION)
    }
}

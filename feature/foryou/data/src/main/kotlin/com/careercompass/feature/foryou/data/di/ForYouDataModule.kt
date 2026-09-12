package com.careercompass.feature.foryou.data.di

import com.careercompass.feature.foryou.data.ForYouRepositoryImpl
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** §7 계약의 바인딩. androidTest 가 `@TestInstallIn(replaces = [ForYouDataModule::class])` 로 교체한다. */
@Module
@InstallIn(SingletonComponent::class)
public abstract class ForYouDataModule {
    @Binds
    @Singleton
    internal abstract fun bindForYouRepository(impl: ForYouRepositoryImpl): ForYouRepository
}

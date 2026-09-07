package com.careercompass.feature.editor.data.di

import com.careercompass.feature.editor.data.ApplicationRepositoryImpl
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** §6 지원서 계약의 바인딩. androidTest 가 `@TestInstallIn(replaces = [EditorDataModule::class])` 로 교체한다. */
@Module
@InstallIn(SingletonComponent::class)
public abstract class EditorDataModule {
    @Binds
    @Singleton
    internal abstract fun bindApplicationRepository(impl: ApplicationRepositoryImpl): ApplicationRepository
}

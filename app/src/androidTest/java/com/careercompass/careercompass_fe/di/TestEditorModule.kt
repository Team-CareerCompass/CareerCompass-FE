package com.careercompass.careercompass_fe.di

import com.careercompass.feature.editor.data.di.EditorDataModule
import com.careercompass.feature.editor.domain.repository.ApplicationRepository
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [EditorDataModule::class])
object TestEditorModule {
    @Provides @Singleton
    fun provideFakeApplicationRepository(): FakeApplicationRepository = FakeApplicationRepository()

    @Provides @Singleton
    fun provideApplicationRepository(fake: FakeApplicationRepository): ApplicationRepository = fake
}

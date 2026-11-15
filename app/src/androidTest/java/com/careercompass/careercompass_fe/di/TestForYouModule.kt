package com.careercompass.careercompass_fe.di

import com.careercompass.feature.foryou.data.di.ForYouDataModule
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import com.careercompass.feature.foryou.domain.testing.FakeForYouRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ForYouDataModule::class])
object TestForYouModule {
    @Provides @Singleton
    fun provideFake(): FakeForYouRepository =
        FakeForYouRepository(
            onGetRoadmapComparison = { Result.success(RoadmapComparison(it, 0, emptyList(), emptyList())) },
        )

    @Provides @Singleton
    fun provideRepository(fake: FakeForYouRepository): ForYouRepository = fake
}

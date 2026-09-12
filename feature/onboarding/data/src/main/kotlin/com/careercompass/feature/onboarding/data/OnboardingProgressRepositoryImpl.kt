package com.careercompass.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.editWithinSession
import com.careercompass.feature.onboarding.data.di.OnboardingProgressDataStore
import com.careercompass.feature.onboarding.domain.model.OnboardingProgress
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
import com.careercompass.feature.onboarding.domain.repository.OnboardingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 온보딩 진행 상태의 SESSION 스코프 DataStore 구현.
 *
 * 단계는 [OnboardingStep.name] 으로, 완료는 별도 플래그로 저장한다. 완료 플래그가 켜져 있으면 단계 값보다
 * 우선한다. 알 수 없는 단계 이름(스키마 변경)은 [OnboardingProgress.NotStarted] 로 읽어 처음부터 다시 시작하게
 * 한다. 읽기 스트림은 [IOException] 시 빈 값으로 복구한다.
 *
 * 기록은 세션 세대 가드를 지난다 — 세션이 끝난 뒤에 도착한 기록이 다음 계정의 진입 판정을 앞 계정의 재개
 * 지점이나 완료 상태로 열지 않게 한다(#348).
 */
@Singleton
internal class OnboardingProgressRepositoryImpl
    @Inject
    constructor(
        @param:OnboardingProgressDataStore private val dataStore: DataStore<Preferences>,
        private val localStoreRegistry: LocalStoreRegistry,
    ) : OnboardingProgressRepository {
        private object Keys {
            val STEP = stringPreferencesKey("step")
            val COMPLETED = booleanPreferencesKey("completed")
        }

        override val progress: Flow<OnboardingProgress> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) emit(emptyPreferences()) else throw exception
                }.map { prefs ->
                    when {
                        prefs[Keys.COMPLETED] == true -> {
                            OnboardingProgress.Completed
                        }

                        else -> {
                            prefs[Keys.STEP]
                                ?.let(OnboardingStep::fromName)
                                ?.let(OnboardingProgress::InProgress)
                                ?: OnboardingProgress.NotStarted
                        }
                    }
                }

        override suspend fun save(step: OnboardingStep): Result<Unit> =
            runCatchingCancellable {
                localStoreRegistry.editWithinSession(dataStore) { prefs ->
                    prefs[Keys.STEP] = step.name
                    prefs.remove(Keys.COMPLETED)
                }
            }

        override suspend fun markCompleted(): Result<Unit> =
            runCatchingCancellable {
                localStoreRegistry.editWithinSession(dataStore) { prefs ->
                    prefs[Keys.COMPLETED] = true
                    prefs.remove(Keys.STEP)
                }
            }

        override suspend fun clear(): Result<Unit> =
            runCatchingCancellable {
                dataStore.edit { prefs ->
                    prefs.remove(Keys.STEP)
                    prefs.remove(Keys.COMPLETED)
                }
                Unit
            }
    }

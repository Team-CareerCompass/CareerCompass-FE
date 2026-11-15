package com.careercompass.feature.editor.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.core.ui.navigation.FeatureNavDisplay
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.feature.editor.presentation.editor.ApplicationEditorScreen
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressScreen
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressViewModel
import com.careercompass.feature.editor.presentation.save.ApplicationSavedScreen
import com.careercompass.feature.editor.presentation.setup.ApplicationSetupScreen
import com.careercompass.feature.editor.presentation.setup.ApplicationSetupViewModel

@Composable
public fun EditorNavHost(
    postingId: Long,
    boundary: FeatureStackBoundary,
    externalActions: EditorExternalActions,
    modifier: Modifier = Modifier,
) {
    val stack = rememberNavBackStack(EditorRoute.Setup)
    val actions = remember(stack, boundary, externalActions) { EditorLocalNavActions(stack, boundary, externalActions) }
    // 초안은 Setup entry를 지워도 유지하고, editor host를 떠날 때 정리한다.
    val progress =
        hiltViewModel<ApplicationProgressViewModel, ApplicationProgressViewModel.Factory>(creationCallback = { it.create(postingId) })
    val state by progress.uiState.collectAsStateWithLifecycle()
    // Progress와 Editor를 오갈 때는 연결을 유지하고 host가 멈출 때만 구독을 닫는다.
    LifecycleResumeEffect(progress) {
        progress.onIntent(ApplicationProgressIntent.Resumed)
        onPauseOrDispose { progress.onIntent(ApplicationProgressIntent.Paused) }
    }
    ObserveSignal(
        signal = state.sessionEnded.takeIf { it },
        consumed = ApplicationProgressIntent.ConsumeSessionEnded,
        onIntent = progress::onIntent,
    ) { actions.endSession() }
    ObserveSignal(
        signal = state.savedNavigationPending.takeIf { it },
        consumed = ApplicationProgressIntent.ConsumeSaved,
        onIntent = progress::onIntent,
    ) { actions.showSaved() }
    val back = { if (!progress.uiState.value.save.isSaving) actions.popBack() }
    FeatureNavDisplay(
        backStack = stack,
        boundary = boundary,
        modifier = modifier,
        entryProvider =
            entryProvider {
                entry<EditorRoute.Setup> {
                    ApplicationSetupScreen(
                        onBackClick = actions::popBack,
                        onDraftStarted = { started ->
                            progress.onIntent(ApplicationProgressIntent.Started(started))
                            actions.showProgress()
                        },
                        onSessionEnded = actions::endSession,
                        viewModel =
                            hiltViewModel<ApplicationSetupViewModel, ApplicationSetupViewModel.Factory>(
                                creationCallback = { it.create(postingId) },
                            ),
                    )
                }
                entry<EditorRoute.Progress> {
                    ApplicationProgressScreen(
                        progress,
                        back,
                        actions::completeProfile,
                        onEditItem = { id ->
                            progress.onIntent(ApplicationProgressIntent.SelectItem(id))
                            actions.showEditor()
                        },
                    )
                }
                entry<EditorRoute.Editor> {
                    ApplicationEditorScreen(progress, back)
                }
                entry<EditorRoute.Saved> {
                    ApplicationSavedScreen(actions::popBack)
                }
            },
    )
    // FeatureNavDisplay와 루트 스택보다 나중에 등록해 시스템 뒤로가기까지 막는다.
    BackHandler(enabled = state.save.isSaving) { }
}

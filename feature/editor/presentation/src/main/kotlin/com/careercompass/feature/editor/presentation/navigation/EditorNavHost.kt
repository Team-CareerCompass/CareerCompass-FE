package com.careercompass.feature.editor.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.careercompass.core.ui.navigation.FeatureNavDisplay
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressIntent
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressScreen
import com.careercompass.feature.editor.presentation.progress.ApplicationProgressViewModel
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
                    ApplicationProgressScreen(progress, actions::popBack, actions::completeProfile, actions::endSession)
                }
            },
    )
}

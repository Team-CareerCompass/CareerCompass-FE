package com.careercompass.feature.editor.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.core.ui.navigation.popOrExit
import com.careercompass.core.ui.navigation.replaceAllWith
import kotlinx.serialization.Serializable

@Serializable
public sealed interface EditorRoute : NavKey {
    @Serializable public data object Setup : EditorRoute

    @Serializable public data object Progress : EditorRoute
}

public interface EditorExternalActions {
    public fun navigateToProfile()

    public fun onSessionEnded()
}

internal interface EditorNavActions {
    fun showProgress()

    fun popBack()

    fun completeProfile()

    fun endSession()
}

internal class EditorLocalNavActions(
    private val stack: NavBackStack<NavKey>,
    private val boundary: FeatureStackBoundary,
    private val external: EditorExternalActions,
) : EditorNavActions {
    override fun showProgress() = stack.replaceAllWith(EditorRoute.Progress)

    override fun popBack() = stack.popOrExit(boundary)

    override fun completeProfile() = external.navigateToProfile()

    override fun endSession() = external.onSessionEnded()
}

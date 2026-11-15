package com.careercompass.feature.foryou.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import com.careercompass.core.ui.navigation.popOrExit
import com.careercompass.core.ui.navigation.pushSingleTop
import kotlinx.serialization.Serializable

@Serializable
public sealed interface ForYouRoute : NavKey {
    @Serializable public data object Home : ForYouRoute

    @Serializable public data object Roadmap : ForYouRoute
}

public interface ForYouExternalActions {
    public fun navigateToProfile()

    public fun onSessionEnded()
}

internal interface ForYouNavActions {
    fun navigateToRoadmap()

    fun popBack()

    fun navigateToProfile()

    fun onSessionEnded()
}

internal class ForYouLocalNavActions(
    private val backStack: NavBackStack<NavKey>,
    private val boundary: FeatureStackBoundary,
    private val externalActions: ForYouExternalActions,
) : ForYouNavActions {
    override fun navigateToRoadmap() = backStack.pushSingleTop(ForYouRoute.Roadmap)

    override fun popBack() = backStack.popOrExit(boundary)

    override fun navigateToProfile() = externalActions.navigateToProfile()

    override fun onSessionEnded() = externalActions.onSessionEnded()
}

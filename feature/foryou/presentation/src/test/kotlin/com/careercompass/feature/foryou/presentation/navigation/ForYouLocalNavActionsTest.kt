package com.careercompass.feature.foryou.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import org.junit.Assert.assertEquals
import org.junit.Test

class ForYouLocalNavActionsTest {
    @Test fun `로드맵은 한 번만 쌓이고 뒤로 가면 분석 홈 뒤에는 셸로 돌아간다`() {
        val stack = NavBackStack<NavKey>(ForYouRoute.Home)
        var exits = 0
        var profiles = 0
        var sessions = 0
        val boundary =
            object : FeatureStackBoundary {
                override fun exit() {
                    exits++
                }
            }
        val external =
            object : ForYouExternalActions {
                override fun navigateToProfile() {
                    profiles++
                }

                override fun onSessionEnded() {
                    sessions++
                }
            }
        val actions = ForYouLocalNavActions(stack, boundary, external)
        actions.navigateToRoadmap()
        actions.navigateToRoadmap()
        assertEquals(listOf(ForYouRoute.Home, ForYouRoute.Roadmap), stack.toList())
        actions.navigateToProfile()
        actions.onSessionEnded()
        assertEquals(1, profiles)
        assertEquals(1, sessions)
        actions.popBack()
        assertEquals(listOf(ForYouRoute.Home), stack.toList())
        actions.popBack()
        assertEquals(1, exits)
    }
}

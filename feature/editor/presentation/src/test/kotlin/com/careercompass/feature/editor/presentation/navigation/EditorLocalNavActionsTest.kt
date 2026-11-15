package com.careercompass.feature.editor.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.careercompass.core.ui.navigation.FeatureStackBoundary
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLocalNavActionsTest {
    @Test fun `시작 후 setup은 남지 않고 뒤로는 editor host를 나간다`() {
        val stack = NavBackStack<NavKey>(EditorRoute.Setup)
        var exits = 0
        val boundary =
            object : FeatureStackBoundary {
                override fun exit() {
                    exits++
                }
            }
        val external =
            object : EditorExternalActions {
                override fun navigateToProfile() = Unit

                override fun onSessionEnded() = Unit
            }
        val actions = EditorLocalNavActions(stack, boundary, external)
        actions.showProgress()
        actions.showProgress()
        assertEquals(listOf(EditorRoute.Progress), stack.toList())
        actions.popBack()
        assertEquals(1, exits)
        assertEquals(listOf(EditorRoute.Progress), stack.toList())
    }
}

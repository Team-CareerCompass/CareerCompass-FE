package com.careercompass.core.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class DirectInputContractTest {
    @Test
    public fun submitEnabled_requiresLabelContentAndIdle() {
        assertFalse(DirectInputState().isSubmitEnabled)
        assertFalse(DirectInputState(label = "라벨").isSubmitEnabled)
        assertFalse(DirectInputState(content = "본문").isSubmitEnabled)
        assertTrue(DirectInputState(label = "라벨", content = "본문").isSubmitEnabled)
        assertFalse(DirectInputState(label = "라벨", content = "본문", isSubmitting = true).isSubmitEnabled)
        assertFalse(DirectInputState(isSubmitting = true).isInputEnabled)
    }
}

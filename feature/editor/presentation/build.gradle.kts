plugins {
    id("careercompass.android.library.compose")
    id("careercompass.android.hilt")
    id("careercompass.android.navigation")
    alias(libs.plugins.compose.screenshot)
    id("careercompass.kover")
}

android {
    namespace = "com.careercompass.feature.editor.presentation"
    resourcePrefix = "editor_"
    testOptions.unitTests.isIncludeAndroidResources = true
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(projects.feature.editor.domain)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.ui)

    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.core.domain))
    testImplementation(testFixtures(projects.feature.editor.domain))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

plugins {
    id("careercompass.android.library.compose")
    id("careercompass.android.hilt")
    id("careercompass.android.navigation")
    id("careercompass.kover")
}

android {
    namespace = "com.careercompass.feature.profile.presentation"
    resourcePrefix = "profile_"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.feature.profile.domain)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(testFixtures(projects.core.domain))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

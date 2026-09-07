plugins {
    id("careercompass.android.data")
    id("careercompass.kover")
}

android {
    namespace = "com.careercompass.feature.editor.data"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(projects.feature.editor.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.coroutines.core)

    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.feature.editor.domain))
}

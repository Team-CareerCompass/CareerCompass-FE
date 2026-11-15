plugins {
    id("careercompass.android.data")
    id("careercompass.kover")
}

android {
    namespace = "com.careercompass.feature.foryou.data"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(projects.feature.foryou.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)

    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.feature.foryou.domain))
}

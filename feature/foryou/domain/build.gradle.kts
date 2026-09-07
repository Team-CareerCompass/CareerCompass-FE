plugins {
    id("careercompass.jvm.domain")
    id("careercompass.kover")
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.coroutines.core)

    testFixturesImplementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.feature.foryou.domain))
}

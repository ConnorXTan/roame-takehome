plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("travel.roame.takehome.server.MainKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

tasks.named<JavaExec>("run") {
    // Serve the fixture in place, and let `-Pmode=empty` etc. pick a failure mode for manual testing.
    workingDir = rootProject.projectDir
    environment("FLIGHTS_MODE", providers.gradleProperty("mode").getOrElse("normal"))
    environment("PORT", providers.gradleProperty("port").getOrElse("8080"))
}

tasks.test {
    workingDir = rootProject.projectDir
}

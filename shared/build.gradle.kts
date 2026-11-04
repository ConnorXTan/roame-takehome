import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "travel.roame.takehome.shared"
        compileSdk { version = release(37) { minorApiLevel = 0 } }
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.datetime)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}

sqldelight {
    databases {
        create("FlightDatabase") {
            packageName.set("travel.roame.takehome.db")
        }
    }
}

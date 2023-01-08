import fluxo.setupBinaryCompatibilityValidator
import fluxo.setupMultiplatform
import fluxo.setupPublication
import fluxo.setupSourceSets

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.kotlin.dokka)
    id("fluxo-setup")
}

setupMultiplatform()
setupPublication()
setupBinaryCompatibilityValidator()

kotlin {
    setupSourceSets {
        matching { "Test" in it.name }.configureEach {
            languageSettings {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }

        common.main.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        common.test.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android.namespace = "kt.fluxo.data"

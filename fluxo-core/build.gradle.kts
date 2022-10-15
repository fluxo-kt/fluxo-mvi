import com.arkivanov.gradle.bundle
import com.arkivanov.gradle.dependsOn
import com.arkivanov.gradle.setupBinaryCompatibilityValidator
import com.arkivanov.gradle.setupMultiplatform
import com.arkivanov.gradle.setupSourceSets

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    id(libs.plugins.android.lib.get().pluginId)
    id(libs.plugins.kotlinx.kover.get().pluginId)
    id(libs.plugins.arkivanov.setup.get().pluginId)
    id(libs.plugins.kotlin.dokka.get().pluginId)
    id("fluxo-lint")
    id("fluxo-detekt")
}
apply<kotlinx.atomicfu.plugin.gradle.AtomicFUGradlePlugin>()

setupMultiplatform()
// TODO: setupPublication()
setupBinaryCompatibilityValidator()

kotlin {
    explicitApi()

    setupSourceSets {
        val android by bundle()
        val js by bundle()
        val jsNative by bundle()
        val java by bundle()

        jsNative dependsOn common
        java dependsOn common
        js dependsOn jsNative
        javaSet dependsOn java
        nativeSet dependsOn jsNative

        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        common.main.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        common.test.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        java.test.dependencies {
            implementation(libs.kotlinx.lincheck)
        }

        android.main.dependencies {
            implementation(libs.androidx.annotation)
        }
    }
}

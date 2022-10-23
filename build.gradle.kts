@file:Suppress("SuspiciousCollectionReassignment")

import fluxo.AndroidConfig
import fluxo.ensureUnreachableTasksDisabled
import fluxo.iosCompat
import fluxo.macosCompat
import fluxo.setupDefaults
import fluxo.tvosCompat
import fluxo.watchosCompat

buildscript {
    dependencies {
        classpath(libs.plugin.kotlinx.atomicfu)
        classpath(libs.kotlin.gradle.api)
        classpath(libs.kotlin.atomicfu)
    }
}

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("fluxo-setup")
    id("fluxo-build-convenience")
    id("fluxo-collect-sarif")
    id("release-dependencies-diff-compare")
    id("release-dependencies-diff-create") apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.deps.analysis)
    alias(libs.plugins.deps.versions)
    alias(libs.plugins.detekt)
}

setupDefaults(
    multiplatformConfigurator = {
        android()
        jvm()
        js(BOTH) { browser() }
        linuxX64()
        iosCompat()
        watchosCompat()
        tvosCompat()
        macosCompat()
    },
    androidConfig = AndroidConfig(
        minSdkVersion = libs.versions.androidMinSdk.get().toInt(),
        compileSdkVersion = libs.versions.androidCompileSdk.get().toInt(),
        targetSdkVersion = libs.versions.androidTargetSdk.get().toInt(),
        buildToolsVersion = libs.versions.androidBuildTools.get(),
    ),
)

ensureUnreachableTasksDisabled()

dependencyAnalysis {
    // https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/wiki/Customizing-plugin-behavior
    dependencies {
        bundle("kotlin-stdlib") {
            includeGroup("org.jetbrains.kotlin")
        }
    }
    issues {
        all {
            onIncorrectConfiguration {
                severity("fail")
            }
            onUnusedDependencies {
                severity("fail")
            }
        }
    }
}

allprojects {
    afterEvaluate {
        // Workaround for https://youtrack.jetbrains.com/issue/KT-52776
        rootProject.extensions.findByType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>()?.apply {
            versions.webpackCli.version = "4.10.0"
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions {
                val isCi by isCI()
                val isRelease by isRelease()
                if (isCi || isRelease) {
                    allWarningsAsErrors = true
                }

                val javaVersion = libs.versions.javaLangTarget.get()
                jvmTarget = javaVersion
                javaParameters = true

                val kotlinVersion = libs.versions.kotlinLangVersion.get()
                languageVersion = kotlinVersion
                apiVersion = kotlinVersion

                // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
                freeCompilerArgs += listOf(
                    "-Xcontext-receivers",
                    "-Xjsr305=strict",
                    "-Xjvm-default=all",
                    "-Xlambdas=indy",
                    "-Xsam-conversions=indy",
                    "-Xtype-enhancement-improvements-strict-mode",
                    "-opt-in=kotlin.RequiresOptIn",
                )
                if (isCi || isRelease) {
                    freeCompilerArgs += listOf(
                        "-Xvalidate-bytecode",
                        "-Xvalidate-ir",
                    )
                }
            }
        }

        // Remove log pollution until Android support in KMP improves.
        project.extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()?.let { kmpExt ->
            kmpExt.sourceSets.removeAll {
                setOf(
                    "androidAndroidTestRelease",
                    "androidTestFixtures",
                    "androidTestFixturesDebug",
                    "androidTestFixturesRelease",
                ).contains(it.name)
            }
        }
    }
}

subprojects {
    // Convenient task that will print full dependencies tree for any module
    // Use `buildEnvironment` task for plugins report
    // https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
    tasks.register<DependencyReportTask>("allDeps")

    plugins.apply("release-dependencies-diff-create")
}

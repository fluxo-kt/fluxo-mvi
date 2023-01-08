@file:Suppress("SuspiciousCollectionReassignment")

import fluxo.AndroidConfig
import fluxo.PublicationConfig
import fluxo.ensureUnreachableTasksDisabled
import fluxo.envOrPropValue
import fluxo.getValue
import fluxo.iosCompat
import fluxo.isCI
import fluxo.isRelease
import fluxo.macosCompat
import fluxo.scmTag
import fluxo.setupDefaults
import fluxo.setupVerification
import fluxo.signingKey
import fluxo.tvosCompat
import fluxo.useK2
import fluxo.useKotlinDebug
import fluxo.watchosCompat
import java.net.URL

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
    id("release-dependencies-diff-compare")
    id("release-dependencies-diff-create") apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.binCompatValidator) apply false
    alias(libs.plugins.kotlinx.kover)
    alias(libs.plugins.deps.analysis)
    alias(libs.plugins.deps.versions)
    alias(libs.plugins.task.tree)
}

setupDefaults(
    multiplatformConfigurator = {
        explicitApi()

        android()
        jvm()
        js(IR) {
            compilations.all {
                kotlinOptions {
                    moduleKind = "umd"
                    sourceMap = true
                    metaInfo = true
                }
            }
            nodejs()
            browser()
        }
        linuxX64()
        mingwX64()
        iosCompat()
        watchosCompat()
        tvosCompat()
        macosCompat()

        // Duplicate opt-ins here as IDEA don't catch settings from compile tasks.
        sourceSets.all {
            languageSettings {
                optIn("kotlin.contracts.ExperimentalContracts")
                optIn("kotlin.experimental.ExperimentalObjCName")
            }
        }

        // Configure a separate test where code runs in worker thread
        // https://kotlinlang.org/docs/compiler-reference.html#generate-worker-test-runner-trw
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests<*>>().all {
            binaries {
                test("background", listOf(DEBUG)) {
                    freeCompilerArgs += "-trw"
                }
            }
            testRuns {
                @Suppress("UNUSED_VARIABLE")
                val background by creating {
                    setExecutionSourceFrom(binaries.getTest("background", DEBUG))
                }
            }
        }
    },
    androidConfig = AndroidConfig(
        minSdkVersion = libs.versions.androidMinSdk.get().toInt(),
        compileSdkVersion = libs.versions.androidCompileSdk.get().toInt(),
        targetSdkVersion = libs.versions.androidTargetSdk.get().toInt(),
        buildToolsVersion = libs.versions.androidBuildTools.get(),
        configurator = {
            if (this is com.android.build.gradle.LibraryExtension) {
                // Optimize code with R8 for android release aar
                // TODO: On-device tests for aar
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        proguardFile(rootProject.file("rules.pro"))
                    }
                }
            }
        },
    ),
    publicationConfig = run {
        val version = libs.versions.fluxo.get()
        val isSnapshot = version.endsWith("SNAPSHOT", ignoreCase = true)
        val scmTag = if (isSnapshot) scmTag().orNull ?: "main" else "v$version"
        val url = "https://github.com/fluxo-kt/fluxo-mvi"
        val publicationUrl = "$url/tree/$scmTag"
        PublicationConfig(
            // https://central.sonatype.org/publish/publish-gradle/
            // https://central.sonatype.org/publish/publish-guide/#initial-setup
            // https://central.sonatype.org/publish/requirements/coordinates/#choose-your-coordinates
            // https://github.com/jonashackt/github-actions-release-maven
            // https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-creating-your-first-library-1bp8
            group = "io.github.fluxo-kt",
            version = version,
            projectName = "Fluxo",
            projectDescription = "Kotlin Multiplatform MVI / MVVM+ framework",
            projectUrl = url,
            publicationUrl = publicationUrl,
            scmUrl = "scm:git:git://github.com/fluxo-kt/fluxo-mvi.git",
            scmTag = scmTag,
            developerId = "amal",
            developerName = "Artyom Shendrik",
            developerEmail = "artyom.shendrik@gmail.com",
            signingKey = signingKey(),
            signingPassword = envOrPropValue("SIGNING_PASSWORD"),
            repositoryUserName = envOrPropValue("OSSRH_USER"),
            repositoryPassword = envOrPropValue("OSSRH_PASSWORD"),
        )
    },
)

setupVerification()

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

koverMerged {
    enable()

    val isCi by isCI()
    xmlReport {
        onCheck.set(true)
        reportFile.set(layout.buildDirectory.file("reports/kover-merged-report.xml"))
    }
    if (!isCi) {
        htmlReport {
            onCheck.set(true)
            reportDir.set(layout.buildDirectory.dir("reports/kover-merged-report-html")) // change report directory
        }
    }

    verify {
        onCheck.set(true)
        rule {
            isEnabled = true
            target = kotlinx.kover.api.VerificationTarget.ALL
            bound {
                minValue = 80
                counter = kotlinx.kover.api.CounterType.LINE
                valueType = kotlinx.kover.api.VerificationValueType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 74
                counter = kotlinx.kover.api.CounterType.INSTRUCTION
                valueType = kotlinx.kover.api.VerificationValueType.COVERED_PERCENTAGE
            }
            bound {
                minValue = 63
                counter = kotlinx.kover.api.CounterType.BRANCH
                valueType = kotlinx.kover.api.VerificationValueType.COVERED_PERCENTAGE
            }
        }
    }

    filters {
        classes {
            excludes += listOf(
                // Test classes
                "kt.fluxo.test.*",
                "kt.fluxo.tests.*",
                // Inline DSL, coverage not detected properly (still everything covered!)
                "kt.fluxo.core.FluxoKt*",
                "kt.fluxo.core.dsl.MigrationKt*",
            )
        }
        annotations {
            excludes += listOf(
                // Coverage not detected properly for inline methods (still everything covered!)
                "kotlin.internal.InlineOnly",
            )
        }
        projects {
            excludes += listOf("jmh")
        }
    }
}

if (hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

tasks.register<Task>(name = "resolveDependencies") {
    group = "other"
    description = "Resolve and prefetch dependencies"
    doLast {
        rootProject.allprojects.forEach { p ->
            p.configurations.plus(p.buildscript.configurations)
                .filter { it.isCanBeResolved }.forEach {
                    try {
                        it.resolve()
                    } catch (_: Throwable) {
                    }
                }
        }
    }
}

val pinnedDeps = arrayOf(
    libs.jackson.databind,
    libs.woodstox.core,
    libs.jsoup,
).map { it.get() }

allprojects {
    configurations.all {
        if (pinnedDeps.isNotEmpty()) {
            resolutionStrategy.eachDependency {
                val module = requested.module
                for (d in pinnedDeps) {
                    if (d.module == module) {
                        useVersion(d.versionConstraint.toString())
                        because("security recommendations")
                    }
                }
            }
        }
    }

    plugins.withType<org.jetbrains.dokka.gradle.DokkaPlugin> {
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
            dokkaSourceSets {
                configureEach {
                    if (name.startsWith("ios")) {
                        displayName.set("ios")
                    }

                    sourceLink {
                        localDirectory.set(rootDir)
                        remoteUrl.set(URL("https://github.com/fluxo-kt/fluxo-mvi/blob/main"))
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }
    }

    if (name == "jmh") {
        return@allprojects
    }

    afterEvaluate {
        // Fixes webpack-cli incompatibility by pinning the newest version.
        // Workaround for https://youtrack.jetbrains.com/issue/KT-52776
        // Also see https://github.com/rjaros/kvision/blob/d9044ab/build.gradle.kts#L28
        rootProject.extensions.findByType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>()?.apply {
            versions.karma.version = libs.versions.js.karma.get()
            versions.mocha.version = libs.versions.js.mocha.get()
            versions.webpack.version = libs.versions.js.webpack.get()
            versions.webpackCli.version = libs.versions.js.webpackCli.get()
            versions.webpackDevServer.version = libs.versions.js.webpackDevServer.get()
        }

        val isCi by isCI()
        val isRelease by isRelease()
        val enableK2 by useK2()
        val useKotlinDebug by useKotlinDebug()
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
            val isTestTask = "Test" in name
            val isDebugTask = "Debug" in name
            val isReleaseTask = "Release" in name
            val releaseSettings = isCi || isRelease || isReleaseTask
            compilerOptions {
                if (!isTestTask && (isCi || isRelease)) {
                    allWarningsAsErrors.set(true)
                }

                org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(libs.versions.kotlinLangVersion.get()).let {
                    languageVersion.set(it)
                    apiVersion.set(it)
                }

                if (this is org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions) {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.javaLangTarget.get()))
                    javaParameters.set(!isDebugTask)

                    // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
                    freeCompilerArgs.addAll(
                        "-Xjsr305=strict",
                        "-Xjvm-default=all",
                        "-Xtype-enhancement-improvements-strict-mode",
                        "-Xuse-fast-jar-file-system",
                        "-Xvalidate-bytecode",
                        "-Xvalidate-ir",
                    )

                    // more data on MVVM+ lambda intents for debugging
                    // class mode provides arguments names
                    freeCompilerArgs.addAll((if (releaseSettings) "indy" else "class").let {
                        listOf("-Xlambdas=$it", "-Xsam-conversions=$it")
                    })
                }

                // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
                // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
                freeCompilerArgs.addAll(
                    "-Xcontext-receivers",
                    "-Xklib-enable-signature-clash-checks",
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlin.contracts.ExperimentalContracts",
                    "-opt-in=kotlin.experimental.ExperimentalObjCName",
                    // w: '-progressive' is meaningful only for the latest language version (1.8)
                    //"-progressive",
                )

                // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
                if (!releaseSettings && useKotlinDebug) {
                    freeCompilerArgs.add("-Xdebug")
                }

                if (enableK2) {
                    useK2.set(true)
                }
            }
        }
    }
}

subprojects {
    // Convenience task that will print full dependencies tree for any module
    // Use `buildEnvironment` task for the report about plugins
    // https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
    tasks.register<DependencyReportTask>("allDeps")

    plugins.apply("release-dependencies-diff-create")
}

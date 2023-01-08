@file:Suppress("TooManyFunctions")

package fluxo

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import java.util.regex.Pattern
import kotlin.reflect.KClass

internal val Project.multiplatformExtension: KotlinMultiplatformExtension
    get() = kotlinExtension as KotlinMultiplatformExtension

internal inline fun <reified T : Any> Project.hasExtension(): Boolean =
    extensions.findByType(T::class.java) != null

internal inline fun Project.hasExtension(clazz: () -> KClass<*>): Boolean =
    try {
        extensions.findByType(clazz().java) != null
    } catch (_: NoClassDefFoundError) {
        false
    }

internal fun Project.checkIsRootProject() {
    require(rootProject == this) { "Must be called on a root project" }
}


operator fun Provider<Boolean>.getValue(thisRef: Any?, property: Any?): Boolean = orNull == true


private fun String?.tryAsBoolean() =
    arrayOf("true", "1", "on").any { it.equals(this, ignoreCase = true) }

@Suppress("unused")
private fun Project.booleanProperty(name: String): Provider<Boolean> {
    val provider = stringProperty(name)
    return provider {
        provider.orNull.tryAsBoolean()
    }
}

private fun Project.stringProperty(name: String): Provider<String> {
    return provider {
        // Exclude extensions, look only for simple props
        val filter: (Any) -> Boolean = { it is CharSequence || it is Number }
        var value = findProperty(name)?.takeIf(filter)?.toString()
        if (value.isNullOrEmpty() && '.' !in name) {
            value = findProperty("org.gradle.project.$name")?.takeIf(filter)?.toString()
        }
        value
    }
}

private fun Project.envVar(name: String): Provider<String?> {
    val env = providers.environmentVariable(name)
    return provider {
        env.orNull ?: System.getProperty(name)
    }
}


private fun Project.envOrPropFlag(name: String): Provider<Boolean> {
    val env = envVar(name)
    val prop = stringProperty(name)
    return provider { env.orNull != null || prop.orNull.tryAsBoolean() }
}

private fun Project.envOrProp(name: String): Provider<String?> {
    val env = envVar(name)
    val prop = stringProperty(name)
    return provider { env.orNull?.takeIf { it.isNotEmpty() } ?: prop.orNull }
}

fun Project.envOrPropValue(name: String): String? = envOrProp(name).orNull?.takeIf { it.isNotEmpty() }
fun Project.envOrPropInt(name: String): Int? = envOrPropValue(name)?.toIntOrNull()
fun Project.envOrPropList(name: String): List<String> = envOrPropValue(name)?.split(Pattern.compile("\\s*,\\s*")).orEmpty()

fun Project.isCI(): Provider<Boolean> = envOrPropFlag("CI")
fun Project.isRelease(): Provider<Boolean> = envOrPropFlag("RELEASE")
fun Project.useK2(): Provider<Boolean> = envOrPropFlag("USE_KOTLIN_K2")
fun Project.useKotlinDebug(): Provider<Boolean> = envOrPropFlag("USE_KOTLIN_DEBUG")
fun Project.disableTests(): Provider<Boolean> = envOrPropFlag("DISABLE_TESTS")
fun Project.scmTag(): Provider<String?> {
    val envOrProp = envOrProp("SCM_TAG")
    return provider {
        var result = envOrProp.orNull
        if (result.isNullOrEmpty()) {
            result = runCommand("git tag -l --points-at HEAD") // current tag name
                ?: runCommand("git rev-parse --short=8 HEAD") // current commit short hash
                    ?: runCommand("git rev-parse --abbrev-ref HEAD") // current branch name
        }
        result
    }
}

fun Project.signingKey(): String? = envOrPropValue("SIGNING_KEY")?.replace("\\n", "\n")


fun Project.runCommand(command: String): String? {
    // https://docs.gradle.org/7.5.1/userguide/configuration_cache.html#config_cache:requirements:external_processes
    return try {
        val exec = providers.exec {
            commandLine(command.split("\\s".toRegex()))
        }
        val error = exec.standardError.asText.get()
        when {
            error.isEmpty() -> exec.standardOutput.asText.get().trim().ifEmpty { null }
            else -> {
                logger.error("Error running command `$command`: $error")
                null
            }
        }
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        logger.error("Error running command `$command`: $e", e)
        null
    }
}

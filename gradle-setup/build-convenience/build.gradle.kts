@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.dsl)
    alias(libs.plugins.detekt)
}

dependencies {
    compileOnly(libs.plugin.kotlin)
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile::class.java).configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.name
}

detekt {
    buildUponDefaultConfig = true
    config.from(file("../../detekt.yml"))
}

gradlePlugin {
    plugins {
        create("fluxo-build-convenience") {
            id = "fluxo-build-convenience"
            implementationClass = "FluxoBuildConveniencePlugin"
        }
    }
}
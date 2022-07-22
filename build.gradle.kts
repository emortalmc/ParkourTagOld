import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"

    java
}

repositories {
    mavenCentral()

    maven(url = "https://jitpack.io")
    maven(url = "https://repo.crystalgames.net/snapshots/")
}

dependencies {
    //compileOnly(kotlin("stdlib"))

    compileOnly("com.github.Minestom:Minestom:0989c220f6")
    compileOnly("com.github.EmortalMC:Immortal:5be023dfae")
    //implementation("com.github.emortaldev:Scaffolding:2dc67e4459")
    //implementation(files("libs/Blocky-1.0-SNAPSHOT.jar"))

    implementation("com.github.EmortalMC:TNT:34bae5bb0c")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

}

tasks {
    processResources {
        filesMatching("extension.json") {
            expand(project.properties)
        }
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set(project.name)
        mergeServiceFiles()
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }

}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}

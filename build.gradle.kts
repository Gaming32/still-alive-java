import net.raphimc.javadowngrader.gradle.task.DowngradeSourceSetTask

plugins {
    java
    application
    id("net.raphimc.java-downgrader") version "1.1.2-SNAPSHOT"
}

group = "io.github.gaming32"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io") {
        content {
            includeGroupAndSubgroups("com.github")
        }
    }
}

dependencies {
    implementation("com.tagtraum:ffsampledsp-complete:0.9.53")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    compileOnly("org.jetbrains:annotations:24.1.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

application {
    mainClass = "io.github.gaming32.stillalive.StillAlive"
}

val downgradeMain by tasks.registering(DowngradeSourceSetTask::class) {
    sourceSet = sourceSets.main
    dependsOn(tasks.classes)
}
tasks.classes.get().finalizedBy(downgradeMain)

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(configurations.runtimeClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.WARN
}

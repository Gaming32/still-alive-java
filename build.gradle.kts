plugins {
    java
    application
}

group = "io.github.gaming32"
version = "1.0-SNAPSHOT"

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

application {
    mainClass = "io.github.gaming32.stillalive.StillAlive"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(configurations.runtimeClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.WARN
}

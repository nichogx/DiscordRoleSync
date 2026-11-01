plugins {
    // Downloads the JDK 17 toolchain that shrinkJar uses, if it isn't installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "discord_role_sync"

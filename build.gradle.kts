import com.github.spotbugs.snom.Effort

plugins {
    java
    jacoco
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotbugs)
}

group = "dev.nicho.rolesync"
// When built locally, the version is 'develop'.
// Versions are set automatically by CI with -Ppackage_version. See CONTRIBUTING.md for more information.
version = providers.gradleProperty("package_version").getOrElse("develop")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // For VaultAPI
    maven("https://jitpack.io")
    // For PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Test only
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Packaged
    implementation(libs.bstats.bukkit)
    implementation(libs.bstats.base)
    implementation(libs.jda) {
        // Disables audio natives, to reduce .jar size.
        exclude(group = "club.minnced", module = "opus-java")
        // Only used for voice encryption (DAVE), which the plugin doesn't use.
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.hikaricp)

    // On server
    compileOnly(libs.spigot.api) {
        // json-simple wrongly declares junit 4.10 as a compile dependency
        exclude(group = "junit", module = "junit")
    }
    // Should be installed by the user
    compileOnly(libs.vault.api)
    // Bundled with Paper/Spigot
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.guava)

    // Compile only
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.spotbugs.annotations)

    // Optional dependencies
    compileOnly(libs.placeholderapi)

    spotbugsPlugins(libs.findsecbugs)
}

// Tests run against the server-provided dependencies, like they do on a server
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

configurations.runtimeClasspath {
    // Brought in by Kotlin (used by JDA's HTTP client). Annotations aren't needed at runtime.
    exclude(group = "org.jetbrains", module = "annotations")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Use 'release' instead of a toolchain so compiling on a newer JDK
    // also fails on usages of Java APIs newer than Java 17.
    options.release = 17
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    // Only plugin.yml: language files use %placeholders% of their own
    filesMatching("plugin.yml") {
        filter { it.replace("%package_version%", version) }
    }
}

// Only the shaded JAR is published
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier = ""
    // The plugin's own license. Third-party notices are in META-INF/THIRD-PARTY-NOTICES.txt.
    from("LICENSE")
    // META-INF/versions/** is excluded below, so the JAR isn't multi-release
    addMultiReleaseAttribute = false

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // Not needed at runtime. Keeps LICENSE/NOTICE files.
    exclude("META-INF/maven/**", "META-INF/versions/**", "module-info.class")
    // Several dependencies ship license and notice files with the same name. Keep all of them,
    // instead of only the first one, so every shaded dependency's license is in the JAR.
    val sharedLicenseFiles = listOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
    sharedLicenseFiles.forEach { append(it) }

    // Let Shadow's transformers see every copy of these, instead of keeping only the first
    filesMatching(sharedLicenseFiles + "META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    // Some plugins use old versions of bstats with different APIs
    relocate("org.bstats", "DiscordRoleSync.org.bstats")
    // Other plugins such as Minecord use JDA at old versions
    relocate("net.dv8tion", "DiscordRoleSync.net.dv8tion")
    // Other plugins may shade their own HikariCP version
    relocate("com.zaxxer.hikari", "DiscordRoleSync.com.zaxxer.hikari")

    minimize()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Test coverage report, written to build/reports/jacoco/ when running the tests
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        // CI reads the total line coverage from the CSV
        csv.required = true
        html.required = true
    }
}

// Static analysis for bugs and security issues. Run with: ./gradlew spotbugsMain
spotbugs {
    toolVersion = libs.versions.spotbugs.get()
    effort = Effort.MAX
    excludeFilter = file("spotbugs-exclude.xml")
}

// Only the plugin code is analyzed, not the tests
tasks.spotbugsTest {
    enabled = false
}

tasks.spotbugsMain {
    reports.create("html") {
        required = true
    }
}

# ProGuard rules for shrinkJar in build.gradle.kts.
# Only removes unused code: class names stay the same and nothing is rewritten.
# Bundled dependencies are relocated under DiscordRoleSync.* by shadowJar, except SLF4J.
-dontobfuscate
-dontoptimize

# Annotations used at runtime (e.g. Bukkit's @EventHandler), generics, and line numbers for stack traces
-keepattributes RuntimeVisible*Annotation*,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

# The plugin itself, which Bukkit and PlaceholderAPI load and call through reflection
-keep class dev.nicho.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# bStats reads fields of its classes through reflection
-keep class DiscordRoleSync.org.bstats.** { *; }
# OkHttp picks a TLS implementation through reflection
-keep class DiscordRoleSync.okhttp3.internal.platform.** { *; }
# Database connection pool, only used with MySQL. HikariConfig copies its fields through reflection.
# Leaves out its optional integrations (metrics libraries, Hibernate) and its build-time proxy generator.
-keep class !DiscordRoleSync.com.zaxxer.hikari.metrics.*.**,!DiscordRoleSync.com.zaxxer.hikari.hibernate.**,!DiscordRoleSync.com.zaxxer.hikari.util.JavassistProxyFactory*,DiscordRoleSync.com.zaxxer.hikari.** { *; }
# Jackson loads optional support for JDK types through reflection
-keep class DiscordRoleSync.com.fasterxml.jackson.databind.ext.** { *; }
# JDA creates its fallback logger through reflection, used when the server has no SLF4J provider
-keep class DiscordRoleSync.net.dv8tion.jda.internal.utils.FallbackLogger { <init>(java.lang.String); }
# Not relocated, so other plugins can load it from this JAR. Keep it whole so they don't get a stripped copy.
-keep class org.slf4j.** { *; }

# Optional dependencies that aren't bundled. Any other missing class fails the build.
# Voice support, excluded from JDA in build.gradle.kts
-dontwarn club.minnced.opus.**,tomp2p.opuswrapper.**,com.sun.jna.**,com.google.crypto.tink.**
# Other TLS implementations OkHttp uses if they are installed
-dontwarn org.conscrypt.**,org.bouncycastle.**,org.openjsse.**
# Metrics libraries and Hibernate, which the database pool supports if they are installed, and its build-time proxy generator
-dontwarn com.codahale.metrics.**,io.dropwizard.metrics5.**,io.micrometer.**,io.prometheus.**,org.hibernate.**,javassist.**
# GraalVM native image and build-time annotations
-dontwarn com.oracle.svm.**,org.graalvm.**,org.codehaus.mojo.animal_sniffer.**
# MethodHandle.invoke takes any arguments, so ProGuard can't find the exact method being called
-dontwarn java.lang.invoke.MethodHandle

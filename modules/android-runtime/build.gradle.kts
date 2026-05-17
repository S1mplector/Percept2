plugins {
  `java-library`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  api(project(":core"))
  api(project(":render-api"))
  api(project(":audio"))

  // Android SDK dependencies will be added when building actual APK
  // For now, this module contains the Java source code
  // In a real build, the Android Gradle Plugin would be applied

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.android")
    }
}

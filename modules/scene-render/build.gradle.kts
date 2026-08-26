plugins {
  `java-library`
  `java-test-fixtures`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  api(project(":core"))
  api(project(":render-api"))

  implementation("org.jspecify:jspecify:1.0.0")

  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  // Needed by VnCharacterCompositorTest's missing-layer-warning coverage, which asserts on
  // RenderDiagnostics output through a logback ListAppender (same dependency :core and :fx declare).
  testImplementation("ch.qos.logback:logback-classic:1.5.6")

  testFixturesImplementation(project(":core"))
  testFixturesImplementation(project(":render-api"))

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.scenerender")
    }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

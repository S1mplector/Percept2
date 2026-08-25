plugins {
  `java-library`
  `java-test-fixtures`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  api(project(":core"))
  api(project(":render-api"))

  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

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

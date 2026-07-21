plugins {
  `java-library`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  api(project(":plugin-api"))
  api("org.slf4j:slf4j-api:2.0.13")
  api("org.jspecify:jspecify:1.0.0")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")

  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.core.vn,com.jvn.core.assets,com.jvn.core.animation")
      ep.excludedPaths.set(".*/vn/script/VnScriptParser\\.java")
    }
}

tasks.test {
  useJUnitPlatform()
}

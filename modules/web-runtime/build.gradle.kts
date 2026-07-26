plugins {
  `java-library`
  id("org.teavm") version "0.15.0"
  id("net.ltgt.errorprone") version "4.0.1"
}

val webDistributionDir = layout.buildDirectory.dir("distributions/web")
val webBundleFile = webDistributionDir.map { it.file("js/jvn-web.js") }

dependencies {
  api(project(":core"))
  api(project(":render-api"))
  api(project(":audio"))
  implementation(teavm.libs.jsoApis)
  implementation("org.teavm:teavm-extras-slf4j:0.15.0")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

teavm.js {
  mainClass = "com.jvn.web.WebMain"
  targetFileName = "jvn-web.js"
  sourceMap = true
  obfuscated = false
}

tasks.register<Sync>("webDist") {
  group = "distribution"
  description = "Build a static JVN Canvas 2D browser bootstrap."
  dependsOn(tasks.named("generateJavaScript"))
  from("src/main/webapp")
  from(layout.buildDirectory.dir("generated/teavm/js")) {
    into("js")
  }
  into(webDistributionDir)
}

tasks.register<Exec>("webSmoke") {
  group = "verification"
  description = "Execute the generated JVN web bundle against a minimal DOM smoke harness."
  dependsOn(tasks.named("webDist"))
  commandLine(
    "node",
    rootProject.file("scripts/test-web-runtime-bundle.mjs"),
    webBundleFile.get().asFile
  )
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.web")
    }
}

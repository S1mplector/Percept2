plugins {
  application
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":core"))
  implementation(project(":plugin-runtime"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  implementation(project(":audio"))
  // Include demo game so its resources (e.g., scripts, images) are on the runtime classpath
  implementation(project(":demo-game"))
  // Include Swing UI backend
  implementation(project(":swing"))
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.runtime")
    }
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

val fastLaunchDirectory = rootProject.layout.buildDirectory.dir("fast-launch/runtime")

tasks.register("prepareFastLaunch") {
  group = "application"
  description = "Compiles the runtime and writes reusable direct-launch classpath metadata."
  dependsOn(tasks.named("classes"))
  val runtimeClasspath = sourceSets["main"].runtimeClasspath
  inputs.files(runtimeClasspath)
  outputs.dir(fastLaunchDirectory)
  doLast {
    val outputDir = fastLaunchDirectory.get().asFile
    val javafxFiles = runtimeClasspath.files
      .filter { it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
      .sortedBy { it.absolutePath }
    val classpathFiles = runtimeClasspath.files
      .filterNot { it in javafxFiles }
      .sortedBy { it.absolutePath }
    outputDir.mkdirs()
    outputDir.resolve("classpath.txt").writeText(
      classpathFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("module-path.txt").writeText(
      javafxFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("version.txt").writeText(rootProject.version.toString() + "\n")
  }
}

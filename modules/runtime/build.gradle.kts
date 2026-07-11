import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
  application
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":core"))
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

abstract class DevLaunchMetadataTask : DefaultTask() {
  @get:Input
  abstract val launchMainClass: org.gradle.api.provider.Property<String>

  @get:Input
  abstract val launchJavaFxModules: org.gradle.api.provider.ListProperty<String>

  @get:Classpath
  abstract val launchRuntimeClasspath: org.gradle.api.file.ConfigurableFileCollection

  @get:OutputFile
  abstract val outputFile: org.gradle.api.file.RegularFileProperty

  @TaskAction
  fun writeMetadata() {
    val runtimeFiles = launchRuntimeClasspath.files.toList()
    val javafxFiles = runtimeFiles.filter { it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
    val classpathFiles = runtimeFiles.filterNot { it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
    val target = outputFile.get().asFile
    target.parentFile.mkdirs()
    target.writeText(
      buildString {
        appendLine("mainClass=${launchMainClass.get()}")
        appendLine("workingDir=${project.rootProject.projectDir.absolutePath}")
        appendLine("logbackConfig=${project.layout.buildDirectory.file("resources/main/logback.xml").get().asFile.absolutePath}")
        appendLine("javafxModules=${launchJavaFxModules.get().joinToString(",")}")
        appendLine("javafxModulePath=${javafxFiles.joinToString(File.pathSeparator) { it.absolutePath }}")
        appendLine("classpath=${classpathFiles.joinToString(File.pathSeparator) { it.absolutePath }}")
      }
    )
  }
}

tasks.register<DevLaunchMetadataTask>("writeRuntimeDevLaunchMetadata") {
  group = "application"
  description = "Writes direct-launch metadata for com.jvn.runtime.JvnApp."
  dependsOn(tasks.named("classes"))
  launchMainClass.set("com.jvn.runtime.JvnApp")
  launchJavaFxModules.set(
    listOf(
      "javafx.controls",
      "javafx.graphics",
      "javafx.base",
      "javafx.media",
      "javafx.swing",
      "javafx.fxml"
    )
  )
  launchRuntimeClasspath.from(sourceSets["main"].runtimeClasspath)
  outputFile.set(layout.buildDirectory.file("jvn-dev-launch/runtime.properties"))
}

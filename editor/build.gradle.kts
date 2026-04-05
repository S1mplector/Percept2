import java.io.File
import org.gradle.api.tasks.JavaExec

plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":audio"))
  implementation(project(":audio-fx"))
  implementation(project(":scripting"))
  implementation("org.fxmisc.richtext:richtextfx:0.11.2")
}

application {
  mainClass.set("com.jvn.editor.EditorApp")
}

fun JavaExec.configureJavaFxRuntime() {
  val fxModules = listOf(
    "javafx.controls",
    "javafx.graphics",
    "javafx.base",
    "javafx.media",
    "javafx.swing"
  )
  doFirst {
    val runtimeFiles = classpath.files
    val javafxFiles = runtimeFiles.filter { file ->
      file.name.startsWith("javafx-") && file.name.endsWith(".jar")
    }
    val nonJavafxFiles = runtimeFiles.filterNot { file ->
      file.name.startsWith("javafx-") && file.name.endsWith(".jar")
    }

    // Keep non-JavaFX dependencies on the classpath to avoid JPMS split-package
    // issues (e.g. vorbisspi/mp3spi both exporting javazoom.spi).
    classpath = files(nonJavafxFiles)

    val modulePath = javafxFiles.joinToString(File.pathSeparator) { it.absolutePath }
    jvmArgs(
      "--module-path", modulePath,
      "--add-modules", fxModules.joinToString(",")
    )
  }
}

// Ensure JavaFX modules are available at runtime when launching via :editor:run
// This avoids the "JavaFX runtime components are missing" error.
tasks.named<JavaExec>("run") {
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generatePuppeteerDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Puppeteer UI screenshots, then updates Puppeteer docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=puppeteer")
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateImageTintDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Scene Lighting Lab screenshots, then updates its docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=image-tint")
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateSidebarDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates screenshots for sidebar utility docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=asset-browser,help-center,image-attributes,image-tint,inspector,label-flow-map,layered-image-visualizer,layout-launcher,menu-flow-editor,puppeteer-launcher,version-control,vns-diagnostics,story-timeline")
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates docs screenshots for all configured editor profiles."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  configureJavaFxRuntime()
}

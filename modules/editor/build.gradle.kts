import java.io.File
import org.gradle.api.tasks.JavaExec

plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":audio"))
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
    "javafx.swing",
    "javafx.fxml"
  )
  doFirst {
    val runtimeFiles = classpath.files
    val javafxFiles = runtimeFiles.filter { file ->
      file.name.startsWith("javafx-") && file.name.endsWith(".jar")
    }
    val nonJavafxFiles = runtimeFiles.filterNot { file ->
      file.name.startsWith("javafx-") && file.name.endsWith(".jar")
    }
    if (javafxFiles.isEmpty()) {
      throw GradleException("No JavaFX runtime jars found on the editor runtime classpath.")
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

fun JavaExec.forwardHubLaunchSystemProps() {
  listOf(
    "jvn.hub.safeMode",
    "jvn.editor.safeMode",
    "jvn.launcher.safeMode",
    "jvn.help.safeMode"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

// Ensure JavaFX modules are available at runtime when launching via :editor:run
// This avoids the "JavaFX runtime components are missing" error.
tasks.named<JavaExec>("run") {
  configureJavaFxRuntime()
  systemProperty("jvn.version", rootProject.version.toString())
  forwardHubLaunchSystemProps()
}

tasks.register<JavaExec>("runLauncher") {
  group = "application"
  description = "Runs the standalone JVN launcher application."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.JvnLauncherApp")
  workingDir = rootProject.projectDir
  systemProperty("jvn.version", rootProject.version.toString())
  forwardHubLaunchSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("runHelpCenter") {
  group = "application"
  description = "Runs the standalone JVN Help Center pop-out window."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.HelpCenterApp")
  workingDir = rootProject.projectDir
  systemProperty("jvn.version", rootProject.version.toString())
  systemProperty("jvn.repoRoot", rootProject.projectDir.absolutePath)
  systemProperty("jvn.help.workspaceRoot", rootProject.projectDir.absolutePath)
  forwardHubLaunchSystemProps()
  configureJavaFxRuntime()
}

fun JavaExec.forwardDocsScreenshotSystemProps() {
  listOf(
    "jvn.docs.profile",
    "jvn.docs.screenshots.shots",
    "jvn.docs.screenshots.annotate",
    "jvn.docs.screenshots.includeRaw",
    "jvn.docs.screenshots.updateDocs",
    "jvn.docs.screenshots.contactSheet",
    "jvn.repoRoot"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

tasks.register<JavaExec>("generatePuppeteerDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Puppeteer UI screenshots, then updates Puppeteer docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=puppeteer")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateImageTintDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Scene Lighting Studio screenshots, then updates its docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=image-tint")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateSidebarDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates screenshots for sidebar utility docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=asset-browser,help-center,project-explorer,image-attributes,image-tint,inspector,label-flow-map,layered-image-visualizer,layout-launcher,menu-flow-editor,puppeteer-launcher,text-editor,version-control,vns-diagnostics,story-timeline")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateCoreDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates screenshots for core editor docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=welcome-center,run-console")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates docs screenshots for all configured editor profiles."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

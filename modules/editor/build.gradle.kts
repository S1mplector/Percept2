import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject

plugins {
  application
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":audio"))
  implementation(project(":scripting"))
  implementation("org.fxmisc.richtext:richtextfx:0.11.2")
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
      ep.option("NullAway:AnnotatedPackages", "com.jvn.editor")
    }
}

application {
  mainClass.set("com.jvn.editor.EditorApp")
}

abstract class DevLaunchMetadataTask : DefaultTask() {
  @get:Input
  abstract val launchMainClass: org.gradle.api.provider.Property<String>

  @get:Input
  abstract val launchJavaFxModules: org.gradle.api.provider.ListProperty<String>

  @get:Classpath
  abstract val launchRuntimeClasspath: ConfigurableFileCollection

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

fun registerDevLaunchMetadataTask(
  taskName: String,
  outputName: String,
  mainClassName: String
) {
  tasks.register<DevLaunchMetadataTask>(taskName) {
    group = "application"
    description = "Writes direct-launch metadata for $mainClassName."
    dependsOn(tasks.named("classes"))
    launchMainClass.set(mainClassName)
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
    outputFile.set(layout.buildDirectory.file("jvn-dev-launch/$outputName.properties"))
  }
}

registerDevLaunchMetadataTask(
  "writeEditorDevLaunchMetadata",
  "editor",
  "com.jvn.editor.EditorBootstrap"
)

registerDevLaunchMetadataTask(
  "writeLauncherDevLaunchMetadata",
  "launcher",
  "com.jvn.editor.JvnLauncherBootstrap"
)

abstract class JavaFxModuleArgumentProvider @Inject constructor(objects: ObjectFactory) : CommandLineArgumentProvider {
  @get:Classpath
  val javafxClasspath: ConfigurableFileCollection = objects.fileCollection()

  @get:Input
  val modules = objects.listProperty(String::class.java)

  override fun asArguments(): Iterable<String> {
    val javafxFiles = javafxClasspath.files.sortedBy { it.name }
    if (javafxFiles.isEmpty()) {
      throw GradleException("No JavaFX runtime jars found on the editor runtime classpath.")
    }
    return listOf(
      "--module-path",
      javafxFiles.joinToString(File.pathSeparator) { it.absolutePath },
      "--add-modules",
      modules.get().joinToString(",")
    )
  }
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
  val runtimeClasspath = classpath
  val javafxFiles = runtimeClasspath.filter { file ->
    file.name.startsWith("javafx-") && file.name.endsWith(".jar")
  }
  val nonJavafxFiles = runtimeClasspath.filter { file ->
    !(file.name.startsWith("javafx-") && file.name.endsWith(".jar"))
  }

  // Keep non-JavaFX dependencies on the classpath to avoid JPMS split-package
  // issues (e.g. vorbisspi/mp3spi both exporting javazoom.spi).
  classpath = nonJavafxFiles

  jvmArgumentProviders.add(objects.newInstance(JavaFxModuleArgumentProvider::class.java).apply {
    javafxClasspath.from(javafxFiles)
    modules.set(fxModules)
  })
}

fun JavaExec.forwardHubLaunchSystemProps() {
  listOf(
    "jvn.hub.safeMode",
    "jvn.editor.safeMode",
    "jvn.launcher.safeMode",
    "jvn.help.safeMode",
    "jvn.hub.developerMode",
    "jvn.editor.developerMode",
    "jvn.launcher.developerMode",
    "jvn.help.developerMode"
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

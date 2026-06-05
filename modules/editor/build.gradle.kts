import java.io.File
import java.net.URI
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
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

fun JavaExec.forwardJaneGeminiProperties() {
  listOf(
    "jvn.jane.gemini.apiKey",
    "jvn.jane.gemini.model",
    "jvn.jane.gemini.endpoint",
    "jvn.jane.gemini.maxOutputTokens",
    "jvn.jane.gemini.temperature",
    "jvn.jane.gemini.timeoutSeconds",
    "jvn.jane.gemini.config"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

fun JavaExec.forwardJaneOnnxProperties() {
  listOf(
    "jvn.jane.onnx.enabled",
    "jvn.jane.onnx.model",
    "jvn.jane.onnx.tokenizer",
    "jvn.jane.onnx.vocab",
    "jvn.jane.onnx.name",
    "jvn.jane.onnx.maxPromptTokens",
    "jvn.jane.onnx.maxNewTokens",
    "jvn.jane.onnx.topK",
    "jvn.jane.onnx.temperature"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

fun JavaExec.configureJaneModelProperties() {
  systemProperty("jvn.jane.workspaceRoot", rootProject.projectDir.absolutePath)
  systemProperty("jvn.repoRoot", rootProject.projectDir.absolutePath)
  forwardJaneGeminiProperties()
  forwardJaneOnnxProperties()
}

// Ensure JavaFX modules are available at runtime when launching via :editor:run
// This avoids the "JavaFX runtime components are missing" error.
tasks.named<JavaExec>("run") {
  configureJavaFxRuntime()
  systemProperty("jvn.version", rootProject.version.toString())
  configureJaneModelProperties()
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

tasks.register<JavaExec>("runJane") {
  group = "application"
  description = "Runs Jane, JVN's local assistant, in the terminal."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.JaneConsoleApp")
  workingDir = rootProject.projectDir
  standardInput = System.`in`
  configureJaneModelProperties()
}

tasks.register("provisionJaneModel") {
  group = "application"
  description = "Downloads Jane's opt-in local Qwen2.5 1.5B Instruct ONNX model if it is missing."
  val modelDir = rootProject.layout.projectDirectory.dir(".jvn/jane-model/qwen2.5-1.5b-instruct")
  outputs.dir(modelDir)
  doLast {
    val base = "https://huggingface.co/onnx-community/Qwen2.5-1.5B-Instruct/resolve/6287331f475a3e20e8c879be8fd4bf3551ad9d34"
    val files = listOf(
      "model_quantized.onnx" to "$base/onnx/model_quantized.onnx",
      "tokenizer.json" to "$base/tokenizer.json",
      "tokenizer_config.json" to "$base/tokenizer_config.json",
      "special_tokens_map.json" to "$base/special_tokens_map.json",
      "generation_config.json" to "$base/generation_config.json",
      "config.json" to "$base/config.json"
    )
    val dir = modelDir.asFile
    dir.mkdirs()
    files.forEach { (name, url) ->
      val target = File(dir, name)
      if (target.isFile && target.length() > 0L) return@forEach
      val tmp = File(dir, "$name.part")
      logger.lifecycle("Downloading Jane model asset: $name")
      URI(url).toURL().openStream().use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
      }
      if (!tmp.renameTo(target)) {
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
      }
    }
  }
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

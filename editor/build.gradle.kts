import java.io.File

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

// Ensure JavaFX modules are available at runtime when launching via :editor:run
// This avoids the "JavaFX runtime components are missing" error
tasks.named<org.gradle.api.tasks.JavaExec>("run") {
  // Add JavaFX modules explicitly; jars are already on the runtimeClasspath via :fx
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

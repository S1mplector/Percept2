plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
}

application {
  mainClass.set("com.jvn.editor.EditorApp")
}

plugins {
  application
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
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

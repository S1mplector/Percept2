plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  implementation(project(":audio-integration"))
  // Include demo game so its resources (e.g., scripts, images) are on the runtime classpath
  implementation(project(":demo-game"))
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

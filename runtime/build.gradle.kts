plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  // Keep the adapter module available, but avoid pulling Simp3 fat jar onto the default runtime classpath.
  // The Simp3 jar bundles its own old slf4j/logback classes, which conflicts with runtime logging.
  implementation(project(":audio-integration")) {
    exclude(group = "com.musicplayer", module = "simp3")
  }
  // Include demo game so its resources (e.g., scripts, images) are on the runtime classpath
  implementation(project(":demo-game"))
  // Include billiards game module
  implementation(project(":billiards-game"))
  // Include Swing UI backend
  implementation(project(":swing"))
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

plugins {
  application
}

dependencies {
  implementation(project(":core"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  implementation(project(":audio-integration"))
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

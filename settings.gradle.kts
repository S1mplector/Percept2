pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

rootProject.name = "JVN"

include(
  ":core",
  ":fx",
  ":runtime",
  ":scripting",
  ":audio-integration",
  ":editor",
  ":demo-game",
  ":testkit"
)

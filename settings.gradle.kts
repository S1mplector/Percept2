pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

rootProject.name = "JVN"

include(
  ":core",
  ":core-3d",
  ":fx",
  ":runtime",
  ":scripting",
  ":audio-integration",
  ":editor",
  ":demo-game",
  ":testkit"
)

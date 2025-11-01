pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

plugins {
  // Enables automatic JDK toolchain resolution & download
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
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

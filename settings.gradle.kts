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
  ":fx",
  ":runtime",
  ":scripting",
  ":audio",
  ":editor",
  ":demo-game",
  ":billiards-game",
  ":swing",
  ":testkit",
  ":scala-utils",
  ":clojure-utils"
)

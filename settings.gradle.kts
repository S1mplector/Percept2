pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  plugins {
    id("net.ltgt.errorprone") version "4.0.1"
  }
}

plugins {
  // Enables automatic JDK toolchain resolution & download
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "JVN"

include(
  ":core",
  ":render-api",
  ":fx",
  ":runtime",
  ":scripting",
  ":audio",
  ":editor",
  ":demo-game",
  ":swing",
  ":hub",
  ":testkit",
  ":web-runtime",
  ":android-runtime",
  ":ios-runtime",
  ":scala-utils",
  ":clojure-utils"
)

listOf(
  "core",
  "render-api",
  "fx",
  "runtime",
  "scripting",
  "audio",
  "editor",
  "demo-game",
  "swing",
  "hub",
  "testkit",
  "web-runtime",
  "android-runtime",
  "ios-runtime",
  "scala-utils",
  "clojure-utils"
).forEach { moduleName ->
  project(":$moduleName").projectDir = file("modules/$moduleName")
}

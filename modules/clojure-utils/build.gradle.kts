plugins {
  `java-library`
  id("dev.clojurephant.clojure") version "0.8.0-beta.7"
}

repositories {
  maven { url = uri("https://repo.clojars.org") }
}

dependencies {
  implementation(project(":core"))
  implementation("org.clojure:clojure:1.11.2")
}

clojure {
  builds.named("main") {
    aotAll()
  }
}

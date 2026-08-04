plugins {
  `java-library`
  id("me.champeau.jmh") version "0.7.2"
}

dependencies {
  api(project(":core"))

  api(platform("org.junit:junit-bom:5.11.0"))
  api("org.junit.jupiter:junit-jupiter-api")

  jmh(project(":core"))
  jmh(project(":editor"))
  jmh(project(":fx"))
  jmh("org.openjdk.jmh:jmh-core:1.37")
  jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
  warmupIterations.set(3)
  iterations.set(5)
  fork.set(1)
  resultFormat.set("JSON")
  resultsFile.set(project.file("build/reports/jmh/results.json"))
}

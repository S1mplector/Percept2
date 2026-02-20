plugins {
  scala
  `java-library`
}

dependencies {
  implementation(project(":core"))
  implementation("org.scala-lang:scala3-library_3:3.3.3")
}

tasks.withType<ScalaCompile> {
  scalaCompileOptions.additionalParameters = listOf("-feature", "-deprecation")
}

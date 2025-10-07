plugins {
  `java-library`
}

sourceSets {
  main {
    java {
      exclude("**/simp3/Simp3/**")
    }
  }
}

dependencies {
  api(project(":core"))
  // Later: implementation(project(":external:simp3"))
}

plugins {
  `java-library`
}

sourceSets {
  main {
    java {
      exclude("**/simp3/Simp3/**")
      // keep legacy exclusions
    }
  }
}

dependencies {
  api(project(":core"))
  // Optional external backend:
  // - By default this module compiles without Simp3 on classpath (reflection runtime bridge).
  // - Enable explicit Simp3 linkage with: `-PuseSimp3=true`
  val useSimp3 = (findProperty("useSimp3") as String?)?.toBoolean() ?: false
  if (useSimp3) {
    implementation("com.musicplayer:simp3:1.0.0")
  }
}

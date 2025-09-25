plugins {
  java
}

allprojects {
  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  }

  tasks.test {
    useJUnitPlatform()
  }

  dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.slf4j:slf4j-api:2.0.13")
  }
}

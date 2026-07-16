plugins { `java-library` }

dependencies {
  api(project(":plugin-api"))
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
  implementation("org.slf4j:slf4j-api:2.0.13")
  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test { useJUnitPlatform() }

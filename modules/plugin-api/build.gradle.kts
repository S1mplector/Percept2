plugins { `java-library` }

dependencies {
  api("org.slf4j:slf4j-api:2.0.13")
  testImplementation(platform("org.junit:junit-bom:5.11.0"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test { useJUnitPlatform() }

plugins {
  `java-library`
}

dependencies {
  api(platform("org.junit:junit-bom:5.11.0"))
  api("org.junit.jupiter:junit-jupiter-api")
}

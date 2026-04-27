plugins {
  `java`
  application
}

application {
  mainClass.set("com.jvn.hub.JvnHub")
}

// The hub is intentionally dependency-free: JDK Swing only.
// It shells out to ./gradlew and git as child processes.

tasks.named<JavaExec>("run") {
  // Run from the project root so relative paths (gradlew, .git) resolve naturally.
  workingDir = rootProject.projectDir
  // Propagate the root into the forked JVM; JvnHub reads this to anchor every
  // child-process (gradlew, git) invocation regardless of where the user launched from.
  systemProperty("jvn.projectRoot", rootProject.projectDir.absolutePath)
  // Keep the launching console quiet: the hub is a GUI.
  standardInput = System.`in`
}

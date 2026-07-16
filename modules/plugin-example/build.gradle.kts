plugins { java }

dependencies { compileOnly(project(":plugin-api")) }

tasks.jar {
  archiveBaseName.set("jvn-example-plugin")
}

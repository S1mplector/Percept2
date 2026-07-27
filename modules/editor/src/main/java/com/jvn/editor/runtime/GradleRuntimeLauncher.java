package com.jvn.editor.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds and starts runtime Gradle commands consistently for the editor and launcher. */
public final class GradleRuntimeLauncher {
  private GradleRuntimeLauncher() {}

  public static Process start(
      File runRoot,
      @Nullable File gradleWrapper,
      String task,
      String @Nullable [] taskArguments,
      @Nullable RuntimeGradleOptions requestedOptions) throws IOException {
    RuntimeGradleOptions options =
        requestedOptions == null ? RuntimeGradleOptions.fastDefaults() : requestedOptions;
    Path gradleHome = gradleHome(runRoot, options);
    Files.createDirectories(gradleHome);

    ProcessBuilder builder =
        new ProcessBuilder(command(runRoot, gradleWrapper, task, taskArguments, options));
    builder.directory(runRoot);
    builder.redirectErrorStream(true);
    builder.environment().put("GRADLE_USER_HOME", gradleHome.toAbsolutePath().toString());
    return builder.start();
  }

  public static List<String> command(
      File runRoot,
      @Nullable File gradleWrapper,
      String task,
      String @Nullable [] taskArguments,
      @Nullable RuntimeGradleOptions requestedOptions) {
    RuntimeGradleOptions options =
        requestedOptions == null ? RuntimeGradleOptions.fastDefaults() : requestedOptions;
    Path gradleHome = gradleHome(runRoot, options);
    ArrayList<String> command = new ArrayList<>();
    command.add(
        gradleWrapper != null && gradleWrapper.isFile()
            ? gradleWrapper.getAbsolutePath()
            : "gradle");
    command.add("--console=plain");
    command.add("--gradle-user-home");
    command.add(gradleHome.toAbsolutePath().toString());
    command.addAll(options.performanceArguments());
    command.add("-Dorg.gradle.vfs.watch=false");
    if (task != null && !task.isBlank()) command.add(task.trim());
    if (taskArguments != null) {
      for (String argument : taskArguments) {
        if (argument != null && !argument.isBlank()) command.add(argument);
      }
    }
    return List.copyOf(command);
  }

  public static Path gradleHome(
      @Nullable File runRoot, @Nullable RuntimeGradleOptions requestedOptions) {
    RuntimeGradleOptions options =
        requestedOptions == null ? RuntimeGradleOptions.fastDefaults() : requestedOptions;
    if (options.sharedDependencyCache()) {
      return RuntimeGradleOptionsStore.sharedGradleHome();
    }
    File safeRoot =
        runRoot == null ? new File(System.getProperty("user.dir", ".")) : runRoot;
    return safeRoot.toPath().resolve(".jvn-gradle-user-home");
  }
}

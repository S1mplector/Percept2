package com.jvn.hub;

import java.util.List;

/** Runtime launch options shared by Classic and JavaFX Engine Hub views. */
record HubGradleOptions(
    boolean developerMode,
    boolean safeMode,
    boolean stacktrace,
    boolean infoLogging,
    boolean debugLogging,
    boolean offline,
    boolean refreshDependencies,
    boolean noBuildCache,
    boolean noDaemon,
    String extraArgs) {

  static HubGradleOptions standard(boolean developerMode, boolean safeMode) {
    return new HubGradleOptions(
        developerMode,
        safeMode,
        true,
        false,
        false,
        false,
        false,
        false,
        false,
        "");
  }

  List<String> splitExtraArgs() {
    return HubCommandFactory.splitArgs(extraArgs);
  }
}

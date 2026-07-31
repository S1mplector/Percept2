package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JvnHubUpdateLifecycleTest {
  @Test
  void unixRelaunchForcesTheDirectHubLauncherToRecompile() {
    Path root = Path.of("workspace").toAbsolutePath().normalize();

    assertEquals(
        List.of("bash", root.resolve("jvn").toString(), "--rebuild-launcher"),
        JvnHub.hubRelaunchCommand(root, "Linux"));
  }

  @Test
  void windowsRelaunchUsesTheDetachedHubLauncher() {
    Path root = Path.of("workspace").toAbsolutePath().normalize();

    assertEquals(
        List.of("cmd.exe", "/c", "start", "", root.resolve("jvn.bat").toString()),
        JvnHub.hubRelaunchCommand(root, "Windows 11"));
  }

  @Test
  void integrationRequiresRevisionUpstreamAndCheckoutChecksToPass() {
    assertTrue(JvnHub.updateProperlyIntegrated(0, 0, 0, false, false, "abc1234"));

    assertFalse(JvnHub.updateProperlyIntegrated(1, 0, 0, false, false, "abc1234"));
    assertFalse(JvnHub.updateProperlyIntegrated(0, 1, -1, false, false, "abc1234"));
    assertFalse(JvnHub.updateProperlyIntegrated(0, 0, 2, false, false, "abc1234"));
    assertFalse(JvnHub.updateProperlyIntegrated(0, 0, 0, true, false, "abc1234"));
    assertFalse(JvnHub.updateProperlyIntegrated(0, 0, 0, false, true, "abc1234"));
    assertFalse(JvnHub.updateProperlyIntegrated(0, 0, 0, false, false, "unknown"));
  }
}

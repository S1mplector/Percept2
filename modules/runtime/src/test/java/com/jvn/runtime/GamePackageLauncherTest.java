package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class GamePackageLauncherTest {

  @Test
  void findsBundledGameDirectoryNextToPortableLayout() throws Exception {
    Path root = Files.createTempDirectory("jvn-packaged-game-");
    Path lib = Files.createDirectories(root.resolve("lib"));
    Path game = Files.createDirectories(root.resolve("game"));
    Files.writeString(game.resolve("jvn.project"), "type=vn\nentryVns=scripts/story/prologue.vns\n");

    assertEquals(
        game.toFile().getCanonicalFile(),
        GamePackageLauncher.findPackagedGameRoot(List.of(lib.toFile())).getCanonicalFile());
  }

  @Test
  void findsBundledGameDirectoryInsideJpackageAppContent() throws Exception {
    Path root = Files.createTempDirectory("jvn-app-image-");
    Path app = Files.createDirectories(root.resolve("Contents").resolve("app"));
    Path game = Files.createDirectories(root.resolve("Contents").resolve("content").resolve("game"));
    Files.writeString(game.resolve("jvn.project"), "type=jes\nentry=scripts/main.jes\n");

    assertEquals(
        game.toFile().getCanonicalFile(),
        GamePackageLauncher.findPackagedGameRoot(List.of(app.toFile())).getCanonicalFile());
  }

  @Test
  void buildsVnRuntimeArgsFromManifestWhenNoExplicitScriptWasProvided() throws Exception {
    Path root = Files.createTempDirectory("jvn-vn-launcher-");
    Files.writeString(root.resolve("jvn.project"), "type=vn\nentryVns=scripts/story/prologue.vns\n");

    List<String> args = GamePackageLauncher.buildRuntimeArgs(root.toFile(), new String[] { "--perf-hud" });

    assertEquals("--assets", args.get(0));
    assertEquals(root.toFile().getAbsolutePath(), args.get(1));
    assertEquals("--script", args.get(2));
    assertEquals("story/prologue.vns", args.get(3));
    assertEquals("--perf-hud", args.get(4));
  }

  @Test
  void preservesExplicitJesArgumentInsteadOfOverwritingFromManifest() throws Exception {
    Path root = Files.createTempDirectory("jvn-jes-launcher-");
    Files.writeString(root.resolve("jvn.project"), "type=jes\nentry=scripts/main.jes\n");

    List<String> args = GamePackageLauncher.buildRuntimeArgs(
        root.toFile(),
        new String[] { "--jes", "scripts/custom_scene.jes", "--title", "Custom" });

    assertEquals("--assets", args.get(0));
    assertEquals(root.toFile().getAbsolutePath(), args.get(1));
    assertEquals("--jes", args.get(2));
    assertEquals("scripts/custom_scene.jes", args.get(3));
    assertEquals("--title", args.get(4));
    assertEquals("Custom", args.get(5));
  }

  @Test
  void resolvesGameRootFromExplicitSystemProperty() throws Exception {
    Path root = Files.createTempDirectory("jvn-packaged-game-root-");
    Files.writeString(root.resolve("jvn.project"), "type=vn\n");
    String previous = System.getProperty("jvn.packaged.gameRoot");
    try {
      System.setProperty("jvn.packaged.gameRoot", root.toString());
      File resolved = GamePackageLauncher.resolvePackagedGameRoot();
      assertNotNull(resolved);
      assertTrue(new java.io.File(resolved, "jvn.project").isFile());
    } finally {
      if (previous == null) System.clearProperty("jvn.packaged.gameRoot");
      else System.setProperty("jvn.packaged.gameRoot", previous);
    }
  }

  @Test
  void bundledGameWinsOverWorkingDirectoryProject() throws Exception {
    Path bundle = Files.createTempDirectory("jvn-bundle-priority-");
    Path app = Files.createDirectories(bundle.resolve("Contents/app"));
    Path bundledGame = Files.createDirectories(bundle.resolve("Contents/content/game"));
    Files.writeString(bundledGame.resolve("jvn.project"), "type=vn\n");
    Path workingProject = Files.createTempDirectory("jvn-working-project-");
    Files.writeString(workingProject.resolve("jvn.project"), "type=vn\n");

    assertEquals(
        bundledGame.toFile().getCanonicalFile(),
        GamePackageLauncher.findPackagedGameRoot(List.of(app.toFile(), workingProject.toFile())).getCanonicalFile());
  }
}

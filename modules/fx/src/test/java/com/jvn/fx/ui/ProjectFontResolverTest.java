package com.jvn.fx.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFontResolverTest {

  @Test
  void loadsProjectFontWhenProjectPathContainsSpacesAndSpecialCharacters(@TempDir Path tempDir) throws Exception {
    Optional<Path> availableFont = findLoadablePlatformFont();
    Assumptions.assumeTrue(availableFont.isPresent(), "No loadable platform font is available for this test");
    Path source = availableFont.orElseThrow();

    String extension = extensionOf(source);
    Path projectRoot = tempDir.resolve("JVN Source & Games");
    Path projectFont = projectRoot.resolve("assets/fonts/project-font" + extension);
    Files.createDirectories(projectFont.getParent());
    Files.copy(source, projectFont, StandardCopyOption.REPLACE_EXISTING);

    Font expected;
    try (InputStream input = Files.newInputStream(source)) {
      expected = Font.loadFont(input, 24.0);
    }
    assertNotNull(expected);

    ProjectFontResolver.clearCache();
    Font actual = ProjectFontResolver.resolve(
        projectRoot.toFile(),
        "assets/fonts/project-font" + extension,
        FontWeight.NORMAL,
        24.0,
        "SansSerif");

    assertEquals(expected.getFamily(), actual.getFamily());
    assertEquals(expected.getName(), actual.getName());
  }

  @Test
  void reusesResolvedSystemFontsForIdenticalRequests() {
    ProjectFontResolver.clearCache();

    Font first = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.BOLD, 18.0, "SansSerif");
    Font second = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.BOLD, 18.0, "SansSerif");
    Font differentWeight = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.NORMAL, 18.0, "SansSerif");

    assertNotNull(first);
    assertSame(first, second);
    assertNotSame(first, differentWeight);
  }

  private static Optional<Path> findLoadablePlatformFont() throws IOException {
    List<Path> roots = List.of(
        Path.of("/usr/share/fonts"),
        Path.of("/usr/local/share/fonts"),
        Path.of(System.getProperty("java.home"), "lib", "fonts"),
        Path.of("/System/Library/Fonts"),
        Path.of("/Library/Fonts")
    );
    for (Path root : roots) {
      if (!Files.isDirectory(root)) continue;
      try (Stream<Path> paths = Files.walk(root)) {
        Optional<Path> candidate = paths
            .filter(Files::isRegularFile)
            .filter(ProjectFontResolverTest::isSupportedFont)
            .filter(ProjectFontResolverTest::canLoadFont)
            .findFirst();
        if (candidate.isPresent()) return candidate;
      }
    }
    return Optional.empty();
  }

  private static boolean canLoadFont(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      return Font.loadFont(input, 24.0) != null;
    } catch (IOException | RuntimeException ignored) {
      return false;
    }
  }

  private static boolean isSupportedFont(Path path) {
    String extension = extensionOf(path);
    return ".ttf".equals(extension) || ".otf".equals(extension);
  }

  private static String extensionOf(Path path) {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
  }
}

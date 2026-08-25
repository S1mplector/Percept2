package com.jvn.scenerender.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jvn.core.assets.AssetCatalog;

class AssetDimensionProbeTest {

  @Test
  void resolvesViaAssetCatalogTier() {
    Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(new AssetCatalog(), "probe/tier1.png");
    assertTrue(dims.isPresent(), "expected tier 1 (AssetCatalog-prefixed path) to resolve the test fixture");
    assertEquals(4.0, dims.get()[0]);
    assertEquals(3.0, dims.get()[1]);
  }

  @Test
  void resolvesViaRawClasspathTierWhenAssetCatalogPrefixDoesNotMatch() {
    Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(new AssetCatalog(), "raw-assets/tier2.png");
    assertTrue(dims.isPresent(), "expected tier 2 (raw classpath) to resolve a path outside game/images/");
    assertEquals(5.0, dims.get()[0]);
    assertEquals(2.0, dims.get()[1]);
  }

  @Test
  void resolvesViaFilesystemTierForAbsolutePath() throws IOException {
    Path tempPng = Files.createTempFile("jvn-asset-probe-fs-", ".png");
    try {
      writeTestPng(tempPng, 6, 7);
      Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(new AssetCatalog(), tempPng.toAbsolutePath().toString());
      assertTrue(dims.isPresent(), "expected tier 3 (filesystem, absolute path) to resolve a temp file");
      assertEquals(6.0, dims.get()[0]);
      assertEquals(7.0, dims.get()[1]);
    } finally {
      Files.deleteIfExists(tempPng);
    }
  }

  @Test
  void resolvesViaProjectRootRelativeTierWhenGivenAProjectRoot() throws IOException {
    Path projectDir = Files.createTempDirectory("jvn-asset-probe-project-");
    try {
      Path relativeImage = projectDir.resolve("art/cover.png");
      Files.createDirectories(relativeImage.getParent());
      writeTestPng(relativeImage, 9, 11);

      Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(
          new AssetCatalog(), "art/cover.png", projectDir.toFile());
      assertTrue(dims.isPresent(), "expected the projectRoot-relative tier to resolve a project-relative path");
      assertEquals(9.0, dims.get()[0]);
      assertEquals(11.0, dims.get()[1]);
    } finally {
      deleteRecursively(projectDir.toFile());
    }
  }

  @Test
  void returnsEmptyWhenNoTierResolves() {
    Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(new AssetCatalog(), "does/not/exist/anywhere.png");
    assertTrue(dims.isEmpty());
  }

  private static void writeTestPng(Path path, int width, int height) throws IOException {
    java.awt.image.BufferedImage image =
        new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(image, "png", path.toFile());
  }

  private static void deleteRecursively(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) deleteRecursively(child);
    }
    file.delete();
  }
}

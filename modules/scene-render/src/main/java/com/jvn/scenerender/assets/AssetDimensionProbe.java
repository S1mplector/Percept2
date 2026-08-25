package com.jvn.scenerender.assets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import javax.imageio.ImageIO;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

/**
 * Reads pixel dimensions of an image asset without JavaFX, for layout math only.
 *
 * <p>Mirrors the multi-tier fallback {@code FxBlitter2D.resolveMediaUrl} uses, so a themed
 * project's configured asset paths resolve the same way here as they do when the image is
 * actually drawn:
 * <ol>
 *   <li>Asset-catalog-prefixed path (e.g. {@code game/images/<path>}), via {@link AssetCatalog#open}</li>
 *   <li>Raw classpath resource, path used as-is (no prefix)</li>
 *   <li>Filesystem: absolute or relative-to-CWD, and (if a project root is supplied)
 *       relative to that project root — matching {@code FxBlitter2D.resolveMediaUrl}'s
 *       {@code projectRoot}-relative fallback</li>
 * </ol>
 * Each tier is tried in order and any failure (missing resource, decode error) falls through to
 * the next; only if all tiers fail is {@link Optional#empty()} returned.
 *
 * <p>Shared by every {@code scene-render} collaborator that needs an image's pixel dimensions
 * for layout math (aspect-ratio scaling, sprite bounds) without drawing it — originally written
 * for {@code MenuRenderer}, promoted here so {@code VnRenderer}'s collaborators use the same
 * implementation rather than a duplicate copy.
 */
public final class AssetDimensionProbe {
  private AssetDimensionProbe() {}

  public static Optional<double[]> dimensionsOf(AssetCatalog assets, String path) {
    return dimensionsOf(assets, path, null);
  }

  public static Optional<double[]> dimensionsOf(AssetCatalog assets, String path, File projectRoot) {
    if (path == null || path.isBlank()) return Optional.empty();

    // Tier 1: asset-catalog-prefixed path (e.g. game/images/<path>).
    if (assets != null) {
      Optional<double[]> viaCatalog = tryDimensions(() -> assets.open(AssetType.IMAGE, path));
      if (viaCatalog.isPresent()) return viaCatalog;
    }

    // Tier 2: raw classpath resource, path used as-is.
    Optional<double[]> viaClasspath = tryDimensions(() -> {
      URL url = AssetDimensionProbe.class.getClassLoader().getResource(path);
      return url != null ? url.openStream() : null;
    });
    if (viaClasspath.isPresent()) return viaClasspath;

    // Tier 3: filesystem, absolute or relative-to-CWD.
    Optional<double[]> viaFilesystem = tryDimensions(() -> {
      File f = new File(path);
      return f.exists() ? new FileInputStream(f) : null;
    });
    if (viaFilesystem.isPresent()) return viaFilesystem;

    // Tier 4: filesystem, relative to the configured project root (parity with
    // FxBlitter2D.resolveMediaUrl's projectRoot-relative fallback).
    if (projectRoot != null) {
      return tryDimensions(() -> {
        File f = new File(projectRoot, path);
        return f.exists() ? new FileInputStream(f) : null;
      });
    }
    return Optional.empty();
  }

  private static Optional<double[]> tryDimensions(StreamSupplier supplier) {
    try (InputStream in = supplier.get()) {
      if (in == null) return Optional.empty();
      BufferedImage image = ImageIO.read(in);
      if (image == null) return Optional.empty();
      return Optional.of(new double[] { image.getWidth(), image.getHeight() });
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @FunctionalInterface
  private interface StreamSupplier {
    InputStream get() throws IOException;
  }
}

package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PuppeteerWindowIconTest {
  private static boolean toolkitAvailable;

  @BeforeAll
  static void startToolkit() throws Exception {
    if (System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank()) {
      toolkitAvailable = false;
      return;
    }
    CountDownLatch ready = new CountDownLatch(1);
    try {
      Platform.startup(ready::countDown);
      toolkitAvailable = ready.await(5, TimeUnit.SECONDS);
    } catch (IllegalStateException alreadyStarted) {
      toolkitAvailable = true;
    } catch (RuntimeException unavailable) {
      toolkitAvailable = false;
    }
  }

  @Test
  void loadsDedicatedPuppeteerProcessIconAtFullResolution() throws Exception {
    URL png = PuppeteerWindow.class.getResource(PuppeteerWindow.WINDOW_ICON_RESOURCE);
    assertNotNull(png);
    BufferedImage raster = ImageIO.read(png);
    assertNotNull(raster);
    assertEquals(512, raster.getWidth());
    assertEquals(512, raster.getHeight());

    URL editorPng = PuppeteerWindow.class.getResource("/com/jvn/editor/images/jvn_editor_icon.png");
    assertNotNull(editorPng);
    assertFalse(Arrays.equals(readResource(png), readResource(editorPng)));
  }

  @Test
  void javaFxWindowLoaderAcceptsThePackagedIcon() {
    Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit is unavailable in this environment");
    javafx.scene.image.Image windowIcon = PuppeteerWindow.loadWindowIcon().orElseThrow();
    assertFalse(windowIcon.isError());
    assertEquals(512.0, windowIcon.getWidth());
    assertEquals(512.0, windowIcon.getHeight());
  }

  @Test
  void svgKeepsJvnArtworkAndReplacesWrenchWithBothTheaterMasks() throws Exception {
    URL svg = PuppeteerWindow.class.getResource("/com/jvn/editor/images/jvn_puppeteer_icon.svg");
    assertNotNull(svg);
    String source = new String(readResource(svg), StandardCharsets.UTF_8);

    assertTrue(source.contains("id=\"tragedy-mask\""));
    assertTrue(source.contains("id=\"comedy-mask\""));
    assertTrue(source.contains(">JVN</text>"));
    assertTrue(source.contains("id=\"theater-badge\""));
    assertFalse(source.toLowerCase().contains("wrench"));
    assertEquals(
        "/com/jvn/editor/images/jvn_puppeteer_icon.png",
        PuppeteerWindow.WINDOW_ICON_RESOURCE);
  }

  private static byte[] readResource(URL resource) throws IOException {
    try (InputStream input = resource.openStream()) {
      return input.readAllBytes();
    }
  }
}

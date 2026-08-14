package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssetBrowserViewTest {

  @Test
  void classifiesTheAssetFormatsDocumentedByTheBrowser() {
    assertEquals("Image", AssetBrowserView.typeFor("portrait.TIFF"));
    assertEquals("Audio", AssetBrowserView.typeFor("theme.m4a"));
    assertEquals("Video", AssetBrowserView.typeFor("cutscene.mkv"));
    assertEquals("Font", AssetBrowserView.typeFor("dialogue.otf"));
    assertEquals("Data", AssetBrowserView.typeFor("localization.properties"));
    assertEquals("File", AssetBrowserView.typeFor("README.md"));
  }

  @Test
  void limitsPreviewLoadingToJavaFxRasterFormats() {
    assertTrue(AssetBrowserView.isPreviewableImage("portrait.gif"));
    assertTrue(AssetBrowserView.isPreviewableImage("portrait.webp"));
    assertFalse(AssetBrowserView.isPreviewableImage("vector.svg"));
    assertFalse(AssetBrowserView.isPreviewableImage("source.tiff"));
  }

  @Test
  void formatsFileSizesForQuickMetadata() {
    assertEquals("800 B", AssetBrowserView.humanFileSize(800));
    assertEquals("1.5 KB", AssetBrowserView.humanFileSize(1536));
    assertEquals("2.0 MB", AssetBrowserView.humanFileSize(2L * 1024 * 1024));
  }

  @Test
  void formatsInsertedPathsAsSafeVnsTokens() {
    assertEquals("assets/hero.png", AssetBrowserView.vnsTokenForPath("assets/hero.png"));
    assertEquals("\"assets/hero poses/wave.png\"",
        AssetBrowserView.vnsTokenForPath("assets/hero poses/wave.png"));
    assertEquals("\"assets/special/hero\\\"alt.png\"",
        AssetBrowserView.vnsTokenForPath("assets/special/hero\"alt.png"));
  }
}

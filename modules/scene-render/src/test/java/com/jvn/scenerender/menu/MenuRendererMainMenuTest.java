package com.jvn.scenerender.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.jvn.scenerender.testkit.DrawCall;
import com.jvn.scenerender.testkit.RecordingBlitter2D;

class MenuRendererMainMenuTest {

  @Test
  void rendersTitleTextWhenNoLogoConfigured() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());

    renderer.renderMainMenu(null, 1280.0, 720.0);

    assertTrue(blitter.calls().stream().anyMatch(c -> c.method().equals("drawText")),
        "expected the fallback title text to be drawn when no theme logo image is set");
  }

  /**
   * Regression coverage for Finding 1 of the whole-branch review: with a theme that configures a
   * *real, resolvable* logo image path, renderMainMenu must actually record a drawImage call for
   * that path rather than silently falling back to placeholder/text rendering. Before the
   * AssetDimensionProbe fix, this path would have gotten the game/images/ prefix unconditionally
   * prepended by AssetCatalog.open, failed to resolve (the fixture lives outside game/images/,
   * exercising the raw-classpath fallback tier), and this assertion would have failed because no
   * drawImage call for the logo path would ever have been recorded.
   */
  @Test
  void rendersLogoImageWhenThemeConfiguresAResolvableLogoPath() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    Properties props = new Properties();
    props.setProperty("logoImage", "raw-assets/tier2.png");
    MenuTheme theme = MenuTheme.defaults();
    theme.apply(props);
    MenuRenderer renderer = new MenuRenderer(blitter, theme);

    renderer.renderMainMenu(null, 1280.0, 720.0);

    assertTrue(blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawImage")
            && c.args().get(0) instanceof String s
            && s.equals("raw-assets/tier2.png")),
        "expected a drawImage call recorded for the theme's configured, resolvable logo path: "
            + describeDrawImageCalls(blitter.calls()));
  }

  private static String describeDrawImageCalls(java.util.List<DrawCall> calls) {
    StringBuilder sb = new StringBuilder();
    for (DrawCall c : calls) {
      if (c.method().equals("drawImage")) sb.append(c.args().get(0)).append("; ");
    }
    return sb.toString();
  }

  @Test
  void rendersPauseMenuResumeItemByDefault() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer renderer = new MenuRenderer(blitter, MenuTheme.defaults());

    renderer.renderPauseMenu(null, 1280.0, 720.0);

    // The default theme prefixes the currently-selected item with "> " (MenuTheme.itemSelectedPrefix),
    // and "Resume" is item 0 (selected by default when no scene is supplied), so the drawn label is
    // "> Resume" rather than a bare "Resume" — pre-existing, unchanged withPrefix() behavior.
    assertTrue(blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawText")
            && c.args().get(0) instanceof String s
            && s.contains("Resume")));
  }
}

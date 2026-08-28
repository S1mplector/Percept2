package com.jvn.scenerender.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.vn.VnSettings;
import com.jvn.scenerender.menu.MenuRenderer;
import com.jvn.scenerender.menu.MenuTheme;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import com.jvn.scenerender.vn.VnRenderer;

/**
 * Covers the SettingsScene slider branch of SceneInputRouter.handleClick (Task 4's
 * port of FxLauncher's slider-drag/reset handling), exercising the value-set and
 * reset-hit paths that Task 4's own test suite left uncovered (it only exercised
 * the plain main-menu item-select path).
 *
 * clickingSliderTrackSetsValue uses the default settings screen (via
 * MenuProfile.defaultSettingsScreen), whose rows are: 0 text_speed,
 * 1 auto_play_delay, 2 bgm_volume, 3 sfx_volume, 4 voice_volume, ... — bgm_volume
 * (index 2) is used as a representative slider row.
 *
 * clickingSliderResetRestoresDefaultValue uses a purpose-built single-item profile
 * instead (see profileWithSliderReset), because the default profile's slider items
 * define no sliderReset* extras and so never render/hit-test a reset button (see
 * that method's Javadoc for the full explanation).
 */
class SceneInputRouterSettingsSliderTest {

  private static final double W = 1280.0;
  private static final double H = 720.0;
  private static final int BGM_VOLUME_INDEX = 2;

  @Test
  void clickingSliderTrackSetsValue() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnSettings settingsModel = new VnSettings();
    settingsModel.setBgmVolume(0.2f);
    SettingsScene scene = new SettingsScene(settingsModel);
    assertTrue(scene.hasSliderAt(BGM_VOLUME_INDEX), "expected bgm_volume row to be a slider");

    double before = scene.sliderValue01At(BGM_VOLUME_INDEX);

    // Find coordinates that land on the bgm_volume row's slider track (not its
    // reset button) via a grid scan, reusing the coordinate-resolution approach
    // established in SceneInputRouterMenuClickTest/SceneInputRouterHoverTest —
    // adapted here for a continuous slider range rather than a discrete hit-test,
    // by cross-checking getHoverIndexForSettings (row) + isSettingsSliderResetHit
    // (excluding the reset button) + computeSettingsSliderValue01 (must land away
    // from the current value so the click is a meaningful mutation).
    double[] coords = findSliderTrackHit(menuRenderer, scene, BGM_VOLUME_INDEX, 0.9);
    assertTrue(coords != null, "failed to find coordinates hitting bgm_volume slider track via grid scan");

    router.handleClick(scene, null, W, H, coords[0], coords[1]);

    double after = scene.sliderValue01At(BGM_VOLUME_INDEX);
    assertNotEquals(before, after, 0.001, "clicking the slider track should change its value");
    assertTrue(after > 0.5, "expected click near the right end of the track to raise the value");
  }

  @Test
  void clickingSliderResetRestoresDefaultValue() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    MenuRenderer menuRenderer = new MenuRenderer(blitter, MenuTheme.defaults());
    VnRenderer vnRenderer = new VnRenderer(blitter);
    SceneInputRouter router = new SceneInputRouter(menuRenderer, vnRenderer, new DefaultMenuSceneFactory());

    VnSettings settingsModel = new VnSettings();
    settingsModel.setBgmVolume(0.1f);
    // The default menu profile's bgm_volume item defines no sliderReset* extras, so
    // MenuRenderer.isSettingsSliderResetHit never returns a rect for it under
    // MenuProfile.defaults() (see SettingsSliderRenderer.resolveSettingsSliderResetRect:
    // it returns null when neither a reset asset path nor explicit reset geometry
    // extras are configured). A reset button is a theme/profile-driven, opt-in
    // element — not present out of the box — so this test supplies a profile whose
    // bgm_volume item sets sliderResetWidth/sliderResetHeight extras, giving the
    // renderer real (default-positioned) reset-button geometry to hit-test against.
    MenuProfile profile = profileWithSliderReset();
    SettingsScene scene = new SettingsScene(settingsModel, null, null, profile, "settings");
    int sliderIndex = 0; // profileWithSliderReset's "settings" screen has a single item
    assertTrue(scene.hasSliderAt(sliderIndex), "expected bgm_volume row to be a slider");

    double defaultValue = new VnSettings().getBgmVolume();
    assertNotEquals(defaultValue, scene.model().getBgmVolume(), 0.001,
        "fixture must start away from the default value for this test to be meaningful");

    double[] coords = findResetHit(menuRenderer, scene, sliderIndex);
    assertTrue(coords != null, "failed to find coordinates hitting bgm_volume slider reset button via grid scan");

    router.handleClick(scene, null, W, H, coords[0], coords[1]);

    assertEquals(defaultValue, scene.model().getBgmVolume(), 0.001);
  }

  /**
   * A MenuProfile whose "settings" screen has a single bgm_volume slider item
   * carrying sliderResetWidth/sliderResetHeight extras, so
   * SettingsSliderRenderer.resolveSettingsSliderResetRect computes a real
   * (default-positioned, to the left of the slider track) reset-button rect for
   * it — letting this test hit-test a reset button the same way a themed profile
   * with a configured reset icon would.
   */
  private static MenuProfile profileWithSliderReset() {
    MenuItemSpec bgmVolumeWithReset = new MenuItemSpec(
        "bgm_volume", "Music {value}", "settings", null, true, MenuActionSpec.noop(),
        null, null, null, null, null, null, null,
        false, null, null, null, null, null, null,
        Map.of("sliderResetWidth", "24", "sliderResetHeight", "24"),
        null, null, null
    );
    MenuScreenSpec settingsScreen = new MenuScreenSpec(
        "settings", "Settings", null, "default", "default", true,
        List.of(bgmVolumeWithReset)
    );
    Map<String, MenuLayoutSpec> layouts = Map.of("default", MenuProfile.defaultLayout());
    Map<String, MenuStyleSpec> styles = Map.of("default", MenuProfile.defaultStyle());
    Map<String, MenuScreenSpec> screens = new LinkedHashMap<>();
    screens.put("main", MenuProfile.defaultMainScreen());
    screens.put("settings", settingsScreen);
    return new MenuProfile("main", screens, layouts, styles);
  }

  /**
   * Scans a grid of (x, y) candidates for one that: hovers the given settings
   * item index, is NOT the slider's reset-button hit, and resolves (via
   * MenuRenderer.computeSettingsSliderValue01) to a value at least
   * minDesiredValue01 — so the resulting click is a real, checkable mutation
   * rather than a no-op landing exactly on the current value.
   */
  private static double[] findSliderTrackHit(
      MenuRenderer menuRenderer, SettingsScene scene, int itemIndex, double minDesiredValue01) {
    for (double gy = 0; gy < H; gy += 2.0) {
      for (double gx = 0; gx < W; gx += 2.0) {
        if (menuRenderer.getHoverIndexForSettings(scene, W, H, gx, gy) != itemIndex) continue;
        if (menuRenderer.isSettingsSliderResetHit(scene, itemIndex, W, H, gx, gy)) continue;
        double v = menuRenderer.computeSettingsSliderValue01(scene, itemIndex, W, H, gx);
        if (v >= minDesiredValue01) {
          return new double[] {gx, gy};
        }
      }
    }
    return null;
  }

  /**
   * Scans a grid of (x, y) candidates for one that hovers the given settings
   * item index and hits its slider's reset button, per
   * MenuRenderer.isSettingsSliderResetHit.
   */
  private static double[] findResetHit(MenuRenderer menuRenderer, SettingsScene scene, int itemIndex) {
    for (double gy = 0; gy < H; gy += 2.0) {
      for (double gx = 0; gx < W; gx += 2.0) {
        if (menuRenderer.getHoverIndexForSettings(scene, W, H, gx, gy) != itemIndex) continue;
        if (menuRenderer.isSettingsSliderResetHit(scene, itemIndex, W, H, gx, gy)) {
          return new double[] {gx, gy};
        }
      }
    }
    return null;
  }
}

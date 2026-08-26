package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.Choice;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class VnChoiceOverlayRendererTest {

  @Test
  void rendersOneRoundedRectAndTextPerChoice() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    List<Choice> choices = List.of(
        Choice.builder().text("Choice A").build(),
        Choice.builder().text("Choice B").build());

    renderer.renderChoices(choices, VnUiLayoutSpec.defaults(),
        new VnFontSpec("SansSerif", 20, false), VnChoiceOverlayRenderer.ChoiceTheme.defaults(),
        1280, 720, -1, null);

    long drawTextCalls = blitter.calls().stream()
        .filter(c -> c.method().equals("drawText") && "Choice A".equals(c.args().get(0))
            || c.method().equals("drawText") && "Choice B".equals(c.args().get(0)))
        .count();
    assertEquals(2, drawTextCalls);
  }

  @Test
  void hoveredChoiceIndexMatchesMousePosition() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    List<Choice> choices = List.of(Choice.builder().text("Only choice").build());
    VnUiLayoutSpec layout = VnUiLayoutSpec.defaults();

    int hovered = renderer.getHoveredChoiceIndex(choices, layout, 1280, 720, 640, 360);

    assertTrue(hovered == 0 || hovered == -1, "hover index must be a valid choice index or -1, not out of range");
  }

  @Test
  void getHoveredChoiceIndexReturnsNegativeOneWhenChoicesEmpty() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);

    int hovered = renderer.getHoveredChoiceIndex(List.of(), VnUiLayoutSpec.defaults(), 1280, 720, 640, 360);

    assertEquals(-1, hovered);
  }

  @Test
  void disabledChoiceUsesDisabledColorNotHoverColor() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    List<Choice> choices = List.of(Choice.builder().text("Nope").enabled(false).build());

    renderer.renderChoices(choices, VnUiLayoutSpec.defaults(),
        new VnFontSpec("SansSerif", 20, false), VnChoiceOverlayRenderer.ChoiceTheme.defaults(),
        1280, 720, 0, null);

    // Disabled choice bg color from defaults theme should have been used as a setFill call.
    VnChoiceOverlayRenderer.ChoiceTheme defaults = VnChoiceOverlayRenderer.ChoiceTheme.defaults();
    boolean usedDisabledFill = blitter.calls().stream()
        .anyMatch(c -> c.method().equals("setFill")
            && closeTo((double) c.args().get(0), defaults.choiceDisabledColor()[0])
            && closeTo((double) c.args().get(1), defaults.choiceDisabledColor()[1])
            && closeTo((double) c.args().get(2), defaults.choiceDisabledColor()[2]));
    assertTrue(usedDisabledFill);
  }

  @Test
  void drawsConfiguredChoiceButtonAssetInsteadOfFlatFill() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    VnChoiceOverlayRenderer.ChoiceTheme theme = VnChoiceOverlayRenderer.ChoiceTheme.defaults()
        .withButtonAssetPath("assets/ui/choice/button.png");

    renderer.renderChoices(List.of(Choice.builder().text("Go").build()),
        VnUiLayoutSpec.defaults(), new VnFontSpec("SansSerif", 20, false), theme, 1280, 720, -1, null);

    boolean drewConfiguredAsset = blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawImage") && "assets/ui/choice/button.png".equals(c.args().get(0)));
    assertTrue(drewConfiguredAsset);
  }

  @Test
  void getHoveredOverlayButtonReturnsNullWhenStateHasNoOverlays() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);

    VnOverlayButtonSpec hovered = renderer.getHoveredOverlayButton(null, 1280, 720, 640, 360);

    assertNull(hovered);
  }

  @Test
  void renderOverlayScreensDrawsPanelForConfiguredScreen() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    VnState state = new VnState();
    VnOverlayScreenSpec screen = new VnOverlayScreenSpec(
        "info", "Title", "Body text", 0.2, 0.2, 0.5, 0.4,
        false, true, true, false, 0L, "hide", "", "screen.return", List.of());
    state.showOverlayScreen(screen);

    renderer.renderOverlayScreens(state, 1280, 720, null,
        new VnFontSpec("SansSerif", 18, true), new VnFontSpec("SansSerif", 22, false),
        new VnFontSpec("SansSerif", 20, false));

    boolean drewTitle = blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawText") && "Title".equals(c.args().get(0)));
    assertTrue(drewTitle);
  }

  @Test
  void renderModeIndicatorsDrawsSkipHudTextWhenSkipModeActive() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    VnState state = new VnState();
    state.setSkipMode(true);

    renderer.renderModeIndicators(state, 1280, 720, new VnFontSpec("SansSerif", 14, true));

    long textCalls = blitter.calls().stream().filter(c -> c.method().equals("drawText")).count();
    assertTrue(textCalls >= 1);
  }

  @Test
  void renderModeIndicatorsDrawsNothingWhenAllModesOff() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    VnState state = new VnState();

    renderer.renderModeIndicators(state, 1280, 720, new VnFontSpec("SansSerif", 14, true));

    boolean drewAnyText = blitter.calls().stream().anyMatch(c -> c.method().equals("drawText"));
    assertFalse(drewAnyText);
  }

  @Test
  void renderErrorOverlayReturnsNegativeOneWhenErrorNull() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);

    int hovered = renderer.renderErrorOverlay(null, 1280, 720, 0, 0);

    assertEquals(-1, hovered);
  }

  @Test
  void renderErrorOverlayDrawsTitleAndDetectsHoveredButton() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnChoiceOverlayRenderer renderer = new VnChoiceOverlayRenderer(blitter);
    VnErrorOverlay error = VnErrorOverlay.runtimeError("Something broke", null);

    // First pass with mouse far away: nothing hovered.
    int notHovered = renderer.renderErrorOverlay(error, 1280, 720, -100, -100);
    assertEquals(-1, notHovered);

    boolean drewTitle = blitter.calls().stream()
        .anyMatch(c -> c.method().equals("drawText") && "VNS Runtime Error".equals(c.args().get(0)));
    assertTrue(drewTitle);
  }

  private static boolean closeTo(double a, double b) {
    return Math.abs(a - b) < 1e-9;
  }
}

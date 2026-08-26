package com.jvn.scenerender.vn;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jvn.core.localization.Localization;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.ui.VnFacetSpec;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.core.vn.ui.VnReactiveOverlayScreenSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import org.jspecify.annotations.Nullable;

/**
 * Choice list, overlay-screen/facet, mode-indicator, and fatal-error-overlay rendering
 * collaborator ported from the original monolithic {@code VnRenderer} (JavaFX
 * {@code GraphicsContext}-bound) onto the platform-agnostic {@link Blitter2D} drawing
 * abstraction.
 *
 * <h2>Known port limitations</h2>
 * <ul>
 *   <li><b>Polygon-shaped clip regions are not applied.</b> The original clipped choice-button
 *   drawing to an arbitrary polygon (via {@code gc.clip()}) whenever a theme configured a
 *   {@code choiceButtonBoundsPolygon}. {@link Blitter2D#setClipRect} is rectangular-only and
 *   {@code Blitter2D} has no arbitrary-polygon clip primitive at all, so this port draws the
 *   image/fill unclipped in that case instead of faking an ineffective clip — the same convention
 *   {@code VnDialogueRenderer} uses. Polygon <em>stroking</em> is unaffected since
 *   {@link Blitter2D#strokePolygon} exists and is used as-is.</li>
 *   <li><b>Rounded rectangles draw with square corners.</b> {@link Blitter2D} has no rounded-rect
 *   primitive (see {@code MenuBackgroundRenderer.fillRoundRect} for the same accepted limitation
 *   duplicated here as a small private helper); every former {@code fillRoundRect}/
 *   {@code strokeRoundRect} call becomes a plain {@link Blitter2D#fillRect}/{@link Blitter2D#strokeRect}.</li>
 *   <li><b>The error-overlay marker is drawn as a circle.</b> The original oval marker was
 *   {@code markerSize x markerSize} (equal width/height), so it maps directly to
 *   {@link Blitter2D#fillCircle}/{@link Blitter2D#strokeCircle}.</li>
 *   <li><b>Choice-condition variable lookups take an explicit {@code Map<String,Object>}.</b> The
 *   original read {@code currentState.getVariables()} from an instance field this stateless
 *   collaborator does not hold; {@code renderChoices}/{@code getHoveredChoiceIndex}'s condition
 *   evaluation instead takes the variables map directly (nullable; a null/absent map behaves like
 *   the original's "no current state" case, where every condition lookup resolves to {@code null}).</li>
 * </ul>
 */
final class VnChoiceOverlayRenderer {

  private static final String DEFAULT_FONT_FAMILY = "SansSerif";

  private static final double[] CHOICE_BG_COLOR = parseHex("#1A2640D8");
  private static final double[] CHOICE_HOVER_COLOR = parseHex("#243358E8");
  private static final double[] CHOICE_DISABLED_COLOR = parseHex("#121826A0");
  private static final double[] TEXT_COLOR = parseHex("#E8EDF6");
  private static final double[] TEXT_COLOR_DISABLED = parseHex("#6878A0");
  private static final double[] CHOICE_DISABLED_BORDER_COLOR = parseHex("#28345060");
  private static final double DEFAULT_CHOICE_RADIUS = 8.0;
  private static final double DEFAULT_CHOICE_BORDER_WIDTH = 1.5;
  private static final double DEFAULT_CHOICE_TEXT_BASELINE_OFFSET = 4.0;

  private static final double[] ERROR_BG_COLOR = {28.0 / 255, 30.0 / 255, 34.0 / 255, 0.97};
  private static final double[] ERROR_TEXT_COLOR = parseHex("#F2F2F2");
  private static final double[] ERROR_DIM_TEXT_COLOR = parseHex("#C8CDD4");
  private static final double[] ERROR_BOX_COLOR = {18.0 / 255, 20.0 / 255, 24.0 / 255, 0.88};
  private static final double[] ERROR_ACCENT_COLOR = {230.0 / 255, 62.0 / 255, 72.0 / 255, 1.0};
  private static final double[] ERROR_PANEL_BORDER_COLOR = {86.0 / 255, 92.0 / 255, 102.0 / 255, 1.0};
  private static final double[] ERROR_BUTTON_COLOR = {56.0 / 255, 60.0 / 255, 68.0 / 255, 1.0};
  private static final double[] ERROR_BUTTON_HOVER_COLOR = {76.0 / 255, 82.0 / 255, 92.0 / 255, 1.0};
  private static final double[] ERROR_BUTTON_TEXT_COLOR = parseHex("#F0F3F7");
  private static final double[] ERROR_PRIMARY_BUTTON_COLOR = parseHex("#236b9a");
  private static final double[] ERROR_PRIMARY_BUTTON_HOVER_COLOR = parseHex("#2e84b9");
  private static final double[] ERROR_PRIMARY_BUTTON_STROKE = parseHex("#7cc8f4");

  private final Blitter2D blitter;

  VnChoiceOverlayRenderer(Blitter2D blitter) {
    this.blitter = blitter;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  ChoiceTheme — theme-derived fields VnRenderer previously held directly
  // ─────────────────────────────────────────────────────────────────────────

  record ChoiceTheme(
      double[] choiceBgColor,
      double[] choiceHoverColor,
      double[] choiceDisabledColor,
      double[] choiceTextColor,
      double[] choiceHoverTextColor,
      double[] choiceDisabledTextColor,
      double[] choiceBorderColor,
      double[] choiceHoverBorderColor,
      double[] choiceDisabledBorderColor,
      double choiceCornerRadius,
      double choiceBorderWidth,
      double choiceTextBaselineOffset,
      double choiceTextXAlign,
      @Nullable String choiceButtonAssetPath,
      @Nullable String choiceButtonHoverAssetPath,
      @Nullable String choiceButtonDisabledAssetPath,
      List<BoundsPointCodec.Point> choiceButtonBoundsPolygon) {

    static ChoiceTheme defaults() {
      return new ChoiceTheme(
          CHOICE_BG_COLOR, CHOICE_HOVER_COLOR, CHOICE_DISABLED_COLOR,
          TEXT_COLOR, TEXT_COLOR, TEXT_COLOR_DISABLED,
          TEXT_COLOR, TEXT_COLOR, CHOICE_DISABLED_BORDER_COLOR,
          DEFAULT_CHOICE_RADIUS, DEFAULT_CHOICE_BORDER_WIDTH, DEFAULT_CHOICE_TEXT_BASELINE_OFFSET,
          0.0, null, null, null, List.of());
    }

    ChoiceTheme withButtonAssetPath(String path) {
      return new ChoiceTheme(
          choiceBgColor, choiceHoverColor, choiceDisabledColor,
          choiceTextColor, choiceHoverTextColor, choiceDisabledTextColor,
          choiceBorderColor, choiceHoverBorderColor, choiceDisabledBorderColor,
          choiceCornerRadius, choiceBorderWidth, choiceTextBaselineOffset, choiceTextXAlign,
          path, choiceButtonHoverAssetPath, choiceButtonDisabledAssetPath, choiceButtonBoundsPolygon);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Choices
  // ─────────────────────────────────────────────────────────────────────────

  void renderChoices(
      List<Choice> choices,
      VnUiLayoutSpec uiLayout,
      VnFontSpec choiceFont,
      ChoiceTheme theme,
      double width,
      double height,
      int hoverIndex,
      @Nullable Map<String, Object> variables) {
    if (choices == null || choices.isEmpty()) return;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), uiLayout, width, height);
    boolean clipChoiceButton = hasPolygon(theme.choiceButtonBoundsPolygon());

    for (int i = 0; i < choices.size(); i++) {
      Choice choice = choices.get(i);
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      boolean enabled = choice.isEnabled() && choiceConditionSatisfied(choice, variables);
      boolean hovered = i == hoverIndex;

      String buttonAsset = !enabled
          ? firstNonNull(theme.choiceButtonDisabledAssetPath(), theme.choiceButtonAssetPath())
          : (hovered ? firstNonNull(theme.choiceButtonHoverAssetPath(), theme.choiceButtonAssetPath())
                     : theme.choiceButtonAssetPath());
      double radius = theme.choiceCornerRadius();
      if (buttonAsset != null) {
        if (clipChoiceButton) {
          // Known limitation: unclipped fallback — see class Javadoc.
          blitter.push();
          blitter.drawImage(buttonAsset, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          blitter.pop();
        } else {
          blitter.drawImage(buttonAsset, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
        }
      } else {
        double[] bg = !enabled ? theme.choiceDisabledColor() : (hovered ? theme.choiceHoverColor() : theme.choiceBgColor());
        blitter.setFill(bg[0], bg[1], bg[2], bg[3]);
        if (clipChoiceButton) {
          // Known limitation: unclipped fallback — see class Javadoc.
          blitter.push();
          blitter.fillRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          blitter.pop();
        } else {
          fillRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
        }
      }

      // Border
      double[] borderColor = !enabled
          ? theme.choiceDisabledBorderColor()
          : (hovered ? theme.choiceHoverBorderColor() : theme.choiceBorderColor());
      blitter.setStroke(borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
      blitter.setStrokeWidth(theme.choiceBorderWidth());
      if (clipChoiceButton) {
        strokeLocalPolygon(theme.choiceButtonBoundsPolygon(), geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
      } else {
        strokeRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
      }

      // Text
      double[] textColor = !enabled
          ? theme.choiceDisabledTextColor()
          : (hovered ? theme.choiceHoverTextColor() : theme.choiceTextColor());
      blitter.setFill(textColor[0], textColor[1], textColor[2], textColor[3]);
      blitter.setFont(choiceFont.family(), choiceFont.size(), choiceFont.bold());
      String choiceText = resolveRuntimeText(choice.getText(), variables);
      double contentX = geo.choiceX() + uiLayout.choiceTextXPadding();
      double contentWidth = Math.max(0, geo.choiceWidth() - uiLayout.choiceTextXPadding() * 2);
      double textWidth = computeTextWidth(choiceText, choiceFont);
      double textBaselineY = uiLayout.choiceTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              y, geo.choiceHeight(), uiLayout.choiceTextTopPadding(), uiLayout.choiceTextBottomPadding(),
              choiceFont, uiLayout.choiceTextYAlign())
          : y + geo.choiceHeight() / 2 + theme.choiceTextBaselineOffset();
      blitter.drawText(
          choiceText,
          resolveAlignedTextX(contentX, contentWidth, textWidth, theme.choiceTextXAlign()),
          textBaselineY,
          choiceFont.size(),
          choiceFont.bold());
    }
  }

  int getHoveredChoiceIndex(
      List<Choice> choices, VnUiLayoutSpec uiLayout, double width, double height, double mouseX, double mouseY) {
    return getHoveredChoiceIndex(choices, uiLayout, ChoiceTheme.defaults(), width, height, mouseX, mouseY);
  }

  int getHoveredChoiceIndex(
      List<Choice> choices, VnUiLayoutSpec uiLayout, ChoiceTheme theme, double width, double height,
      double mouseX, double mouseY) {
    if (choices == null || choices.isEmpty()) return -1;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), uiLayout, width, height);
    List<BoundsPointCodec.Point> polygon = theme.choiceButtonBoundsPolygon();

    for (int i = 0; i < choices.size(); i++) {
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      if (hasPolygon(polygon)) {
        if (BoundsPointCodec.containsInRect(polygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), mouseX, mouseY)) {
          return i;
        }
      } else if (mouseX >= geo.choiceX() && mouseX <= geo.choiceX() + geo.choiceWidth()
          && mouseY >= y && mouseY <= y + geo.choiceHeight()) {
        return i;
      }
    }
    return -1;
  }

  private boolean choiceConditionSatisfied(Choice c, @Nullable Map<String, Object> variables) {
    String cond = c.getCondition();
    if (cond == null || cond.isEmpty()) return true;
    String[] toks = cond.trim().split("\\s+");
    if (toks.length < 3) return true;
    Object lhs = getVariableSafe(toks[0], variables);
    String op = toks[1];
    String rhsRaw = toks[2];
    Object rhs = parseScalar(rhsRaw);
    if (lhs instanceof Number ln && rhs instanceof Number rn) {
      double a = ln.doubleValue();
      double b = rn.doubleValue();
      if ("==".equals(op)) return a == b;
      if ("!=".equals(op)) return a != b;
      if (">".equals(op)) return a > b;
      if ("<".equals(op)) return a < b;
      if (">=".equals(op)) return a >= b;
      if ("<=".equals(op)) return a <= b;
      return false;
    }
    String a = lhs == null ? "" : lhs.toString();
    String b = rhs == null ? "" : rhs.toString();
    if ("==".equals(op)) return a.equals(b);
    if ("!=".equals(op)) return !a.equals(b);
    return false;
  }

  private @Nullable Object getVariableSafe(String key, @Nullable Map<String, Object> variables) {
    return key == null || variables == null ? null : variables.get(key);
  }

  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try {
      if (t.contains(".")) return Double.parseDouble(t);
      else return Integer.parseInt(t);
    } catch (Exception ignored) {
      // reason: not a number; caller treats it as a string
    }
    return t;
  }

  private ChoiceGeometry computeChoiceGeometry(int count, VnUiLayoutSpec uiLayout, double width, double height) {
    double choiceHeight = Math.max(12, uiLayout.choiceHeight());
    double choiceGap = Math.max(0, uiLayout.choiceGap());
    double choiceWidth = clamp(width * uiLayout.choiceWidthFactor(), 20, width);
    double choiceX = width * uiLayout.choiceXCenter() - choiceWidth / 2.0;
    choiceX = clamp(choiceX, 0, Math.max(0, width - choiceWidth));
    double totalHeight = count * choiceHeight + Math.max(0, count - 1) * choiceGap;
    double startY = uiLayout.choiceYStart() < 0
        ? (height - totalHeight) / 2.0
        : (height * uiLayout.choiceYStart()) - totalHeight * uiLayout.choiceYAnchor();
    startY = clamp(startY, 0, Math.max(0, height - totalHeight));
    return new ChoiceGeometry(choiceX, startY, choiceWidth, choiceHeight, choiceGap);
  }

  private record ChoiceGeometry(double choiceX, double startY, double choiceWidth, double choiceHeight, double choiceGap) {}

  // ─────────────────────────────────────────────────────────────────────────
  //  Overlay screens / facets / buttons
  // ─────────────────────────────────────────────────────────────────────────

  void renderOverlayScreens(
      @Nullable VnState state,
      double width,
      double height,
      @Nullable VnOverlayButtonSpec hoveredButton,
      VnFontSpec nameFont,
      VnFontSpec dialogueFont,
      VnFontSpec choiceFont) {
    if (state == null || !state.hasOverlayScreens()) return;
    boolean dimDrawn = false;
    for (VnOverlayScreenSpec screen : state.getOverlayScreens()) {
      if (screen == null) continue;
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && !reactive.isVisibleNow()) continue;
      if (screen.isDimBackground() && !dimDrawn) {
        blitter.setFill(0.0, 0.0, 0.0, 0.42);
        blitter.fillRect(0, 0, width, height);
        dimDrawn = true;
      }
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      renderOverlayPanel(screen, screenGeometry, nameFont, dialogueFont);
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && reactive.getFacet() != null) {
        renderFacet(reactive, screenGeometry, dialogueFont);
      }
      for (VnOverlayButtonSpec button : screen.getButtons()) {
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        renderOverlayButton(button, geometry, sameOverlayButton(hoveredButton, button), choiceFont);
      }
    }
  }

  private void renderFacet(VnReactiveOverlayScreenSpec screen, ScreenGeometry root, VnFontSpec dialogueFont) {
    VnFacetSpec facet = screen.getFacet();
    if (facet == null) return;
    Map<String, ScreenGeometry> geometryById = new HashMap<>();
    geometryById.put(facet.rootId(), root);
    geometryById.put("root", root);
    for (VnFacetSpec.Node node : facet.nodes()) {
      if (node == null || !screen.isFacetNodeVisible(node)) continue;
      ScreenGeometry parent = geometryById.getOrDefault(node.parent(), root);
      ScreenGeometry box = new ScreenGeometry(
          parent.x() + parent.width() * node.x(),
          parent.y() + parent.height() * node.y(),
          Math.max(1, parent.width() * node.width()),
          Math.max(1, parent.height() * node.height()));
      geometryById.put(node.id(), box);
      switch (node.type()) {
        case GROUP -> { }
        case TEXT -> {
          String value = screen.resolveFacetText(node.text());
          if (!value.isBlank()) {
            double fontSize = Math.max(12, box.height() * 0.42);
            blitter.setFill(236.0 / 255, 240.0 / 255, 248.0 / 255, 0.98);
            blitter.setFont(dialogueFont.family(), fontSize, false);
            double lineY = box.y() + Math.min(box.height() * 0.7, 22);
            for (String line : wrapText(value, box.width(), dialogueFont.family(), fontSize, false)) {
              blitter.drawText(line, box.x(), lineY, fontSize, false);
              lineY += Math.max(16, fontSize * 1.25);
              if (lineY > box.y() + box.height()) break;
            }
          }
        }
        case IMAGE -> {
          String path = screen.resolveFacetText(node.value());
          if (!path.isBlank()) {
            blitter.drawImage(path, box.x(), box.y(), box.width(), box.height());
          }
        }
        case BAR -> {
          double value = Math.max(0.0, Math.min(1.0, screen.resolveFacetNumber(node.value(), 0.0)));
          blitter.setFill(1.0, 1.0, 1.0, 0.12);
          fillRoundRect(box.x(), box.y(), box.width(), box.height(), box.height(), box.height());
          blitter.setFill(82.0 / 255, 210.0 / 255, 255.0 / 255, 0.88);
          fillRoundRect(box.x(), box.y(), box.width() * value, box.height(), box.height(), box.height());
        }
      }
    }
  }

  private void renderOverlayPanel(
      VnOverlayScreenSpec screen, ScreenGeometry geometry, VnFontSpec nameFont, VnFontSpec dialogueFont) {
    blitter.setFill(18.0 / 255, 21.0 / 255, 28.0 / 255, 0.95);
    fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);
    blitter.setStroke(210.0 / 255, 220.0 / 255, 240.0 / 255, 0.22);
    blitter.setStrokeWidth(1.5);
    strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);

    double innerX = geometry.x() + 22;
    double innerY = geometry.y() + 20;
    double innerWidth = Math.max(40, geometry.width() - 44);
    blitter.setFill(1.0, 1.0, 1.0, 1.0);
    blitter.setFont(nameFont.family(), 22, true);
    blitter.drawText(resolveRuntimeText(screen.getTitle(), null), innerX, innerY + 4, 22, true);

    String screenText = resolveRuntimeText(screen.getText(), null);
    if (screenText != null && !screenText.isBlank()) {
      blitter.setFill(228.0 / 255, 232.0 / 255, 240.0 / 255, 0.95);
      blitter.setFont(dialogueFont.family(), 17, false);
      double textY = innerY + 34;
      for (String line : wrapText(screenText, innerWidth, dialogueFont.family(), 17, false)) {
        blitter.drawText(line, innerX, textY, 17, false);
        textY += 22;
        if (textY > geometry.y() + geometry.height() - 44) break;
      }
    }

    if (screen.getTimerRemainingMs() > 0) {
      double ratio = Math.max(0.0, Math.min(1.0, screen.getTimerRemainingMs() / 5000.0));
      blitter.setFill(82.0 / 255, 210.0 / 255, 255.0 / 255, 0.65);
      fillRoundRect(
          geometry.x() + 18, geometry.y() + geometry.height() - 12,
          Math.max(18, (geometry.width() - 36) * ratio), 4, 4, 4);
    }
  }

  private void renderOverlayButton(
      VnOverlayButtonSpec button, ButtonGeometry geometry, boolean hovered, VnFontSpec choiceFont) {
    double[] fill = hovered ? new double[] {74.0 / 255, 122.0 / 255, 214.0 / 255, 0.92}
                            : new double[] {43.0 / 255, 49.0 / 255, 60.0 / 255, 0.92};
    double[] stroke = hovered ? new double[] {144.0 / 255, 192.0 / 255, 255.0 / 255, 0.95}
                              : new double[] {1.0, 1.0, 1.0, 0.18};
    blitter.setFill(fill[0], fill[1], fill[2], fill[3]);
    fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    blitter.setStroke(stroke[0], stroke[1], stroke[2], stroke[3]);
    blitter.setStrokeWidth(1.2);
    strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    String label = resolveRuntimeText(button.label(), null);
    if (label != null && !label.isBlank()) {
      blitter.setFill(1.0, 1.0, 1.0, 1.0);
      blitter.setFont(choiceFont.family(), 16, false);
      blitter.drawText(label, geometry.x() + 12, geometry.y() + geometry.height() * 0.62, 16, false);
    }
  }

  private boolean sameOverlayButton(@Nullable VnOverlayButtonSpec a, @Nullable VnOverlayButtonSpec b) {
    if (a == null || b == null) return false;
    return Objects.equals(a.screenId(), b.screenId()) && Objects.equals(a.id(), b.id());
  }

  @Nullable VnOverlayButtonSpec getHoveredOverlayButton(
      @Nullable VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || !state.hasOverlayScreens()) return null;
    List<VnOverlayScreenSpec> screens = state.getOverlayScreens();
    for (int screenIndex = screens.size() - 1; screenIndex >= 0; screenIndex--) {
      VnOverlayScreenSpec screen = screens.get(screenIndex);
      if (screen == null) continue;
      if (screen instanceof VnReactiveOverlayScreenSpec reactive && !reactive.isVisibleNow()) continue;
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      List<VnOverlayButtonSpec> buttons = screen.getButtons();
      for (int i = buttons.size() - 1; i >= 0; i--) {
        VnOverlayButtonSpec button = buttons.get(i);
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        if (geometry.contains(mouseX, mouseY)) return button;
      }
      if (screen.isModal()) break;
    }
    return null;
  }

  private ScreenGeometry computeOverlayScreenGeometry(VnOverlayScreenSpec screen, double viewportWidth, double viewportHeight) {
    double x = clamp(viewportWidth * screen.getX(), 0, viewportWidth);
    double y = clamp(viewportHeight * screen.getY(), 0, viewportHeight);
    double width = clamp(viewportWidth * screen.getWidth(), 40, viewportWidth - x);
    double height = clamp(viewportHeight * screen.getHeight(), 40, viewportHeight - y);
    return new ScreenGeometry(x, y, width, height);
  }

  private ButtonGeometry computeOverlayButtonGeometry(
      VnOverlayButtonSpec button, ScreenGeometry screen, double viewportWidth, double viewportHeight) {
    double baseX = button.viewportSpace() ? 0.0 : screen.x();
    double baseY = button.viewportSpace() ? 0.0 : screen.y();
    double baseW = button.viewportSpace() ? viewportWidth : screen.width();
    double baseH = button.viewportSpace() ? viewportHeight : screen.height();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private record ScreenGeometry(double x, double y, double width, double height) {}

  private record ButtonGeometry(double x, double y, double width, double height) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Mode indicators
  // ─────────────────────────────────────────────────────────────────────────

  void renderModeIndicators(VnState state, double width, double height, VnFontSpec nameFont) {
    blitter.setFont(nameFont.family(), 14, true);
    blitter.setFill(1.0, 1.0, 1.0, 0.9);

    double y = 25;

    if (state.isSkipMode()) {
      blitter.drawText(Localization.t("hud.skip"), width - 100, y, 14, true);
      y += 20;
    }

    if (state.isAutoPlayMode()) {
      blitter.drawText(Localization.t("hud.auto"), width - 100, y, 14, true);
      y += 20;
    }

    if (state.isUiHidden()) {
      blitter.drawText(Localization.t("hud.ui_off"), width - 110, y, 14, true);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Error overlay
  // ─────────────────────────────────────────────────────────────────────────

  int renderErrorOverlay(@Nullable VnErrorOverlay error, double width, double height, double mouseX, double mouseY) {
    if (error == null) return -1;

    blitter.setFill(ERROR_BG_COLOR[0], ERROR_BG_COLOR[1], ERROR_BG_COLOR[2], ERROR_BG_COLOR[3]);
    blitter.fillRect(0, 0, width, height);
    blitter.setFillLinearGradient(0, 0, 0, 6,
        new double[] {0.0, 1.0},
        new double[] {
            ERROR_ACCENT_COLOR[0], ERROR_ACCENT_COLOR[1], ERROR_ACCENT_COLOR[2], ERROR_ACCENT_COLOR[3],
            ERROR_ACCENT_COLOR[0], ERROR_ACCENT_COLOR[1], ERROR_ACCENT_COLOR[2], 0.0
        });
    blitter.fillRect(0, 0, width, 6);

    double outerPadding = Math.max(16, Math.min(42, width * 0.045));
    double contentW = Math.min(1080, Math.max(240, width - outerPadding * 2));
    double contentX = (width - contentW) * 0.5;
    double y = Math.max(20, Math.min(36, height * 0.045));

    double markerSize = Math.min(38, Math.max(30, height * 0.055));
    blitter.setFill(88.0 / 255, 30.0 / 255, 36.0 / 255, 0.96);
    blitter.fillCircle(contentX + markerSize / 2.0, y + markerSize / 2.0, markerSize / 2.0);
    blitter.setStroke(244.0 / 255, 100.0 / 255, 108.0 / 255, 0.92);
    blitter.setStrokeWidth(1.5);
    blitter.strokeCircle(contentX + markerSize / 2.0, y + markerSize / 2.0, (markerSize - 1.5) / 2.0);
    double markerFontSize = markerSize * 0.66;
    blitter.setFont(DEFAULT_FONT_FAMILY, markerFontSize, true);
    blitter.setFill(1.0, 228.0 / 255, 230.0 / 255, 1.0);
    blitter.drawText("!", contentX + markerSize * 0.40, y + markerSize * 0.73, markerFontSize, true);

    double titleFontSize = Math.min(30, Math.max(21, height * 0.040));
    blitter.setFont(DEFAULT_FONT_FAMILY, titleFontSize, true);
    blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
    String title = error.getTitle() == null || error.getTitle().isBlank() ? "Runtime Error" : error.getTitle();
    blitter.drawText(title, contentX + markerSize + 14, y + titleFontSize, titleFontSize, true);

    double subtitleFontSize = Math.min(15, Math.max(12, height * 0.020));
    String subtitle = switch (error.getType()) {
      case PARSE_ERROR, DSL_PARSE_ERROR, COMPILATION_ERROR ->
          "The script could not be loaded. Fix the source, then reload to try again.";
      default ->
          "Playback paused safely. You can reload, copy the details, or continue past this error.";
    };
    blitter.setFont(DEFAULT_FONT_FAMILY, subtitleFontSize, false);
    blitter.setFill(ERROR_DIM_TEXT_COLOR[0], ERROR_DIM_TEXT_COLOR[1], ERROR_DIM_TEXT_COLOR[2], ERROR_DIM_TEXT_COLOR[3]);
    blitter.drawText(subtitle, contentX + markerSize + 14, y + titleFontSize + subtitleFontSize + 5, subtitleFontSize, false);
    y += Math.max(markerSize, titleFontSize + subtitleFontSize + 7) + 14;

    double infoFontSize = Math.min(14, Math.max(11, height * 0.018));
    String source = error.getSourceName() == null || error.getSourceName().isBlank() ? "Unknown source" : error.getSourceName();
    String location = error.getLineNumber() > 0 ? source + ":" + error.getLineNumber() : source;
    String type = error.getType().name().replace('_', ' ');
    String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date(error.getTimestamp()));
    String metadata = location + "   •   " + type + "   •   " + timeStr;
    blitter.setFont(DEFAULT_FONT_FAMILY, infoFontSize, true);
    blitter.setFill(ERROR_DIM_TEXT_COLOR[0], ERROR_DIM_TEXT_COLOR[1], ERROR_DIM_TEXT_COLOR[2], ERROR_DIM_TEXT_COLOR[3]);
    blitter.drawText(metadata, contentX, y + infoFontSize, infoFontSize, true);
    y += infoFontSize + 12;

    double buttonH = Math.min(44, Math.max(38, height * 0.065));
    double buttonY = Math.max(8, height - outerPadding - buttonH);
    double bodyBottom = buttonY - 16;
    double bodyAvailable = Math.max(72, bodyBottom - y);

    double sectionFontSize = Math.min(15, Math.max(12, height * 0.019));
    double msgFontSize = Math.min(15, Math.max(12, height * 0.019));

    String message = error.getMessage() != null ? error.getMessage() : "(unknown error)";
    blitter.setFont(DEFAULT_FONT_FAMILY, sectionFontSize, true);
    blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
    blitter.drawText("What happened", contentX, y + sectionFontSize, sectionFontSize, true);
    y += sectionFontSize + 7;
    double messageRoom = Math.max(34, bodyBottom - y);
    double msgBoxH = Math.min(92, Math.max(54, Math.min(bodyAvailable * 0.25, messageRoom)));
    blitter.setFill(ERROR_BOX_COLOR[0], ERROR_BOX_COLOR[1], ERROR_BOX_COLOR[2], ERROR_BOX_COLOR[3]);
    fillRoundRect(contentX, y, contentW, msgBoxH, 8, 8);
    blitter.setStroke(ERROR_PANEL_BORDER_COLOR[0], ERROR_PANEL_BORDER_COLOR[1], ERROR_PANEL_BORDER_COLOR[2], ERROR_PANEL_BORDER_COLOR[3]);
    blitter.setStrokeWidth(1);
    strokeRoundRect(contentX, y, contentW, msgBoxH, 8, 8);

    blitter.setFont("Monospaced", msgFontSize, false);
    blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
    drawWrappedText(message, contentX + 14, y + 22, contentW - 28, msgBoxH - 10, "Monospaced", msgFontSize, false);
    y += msgBoxH + 12;

    String rawLine = error.getRawLine();
    boolean showRawLine = rawLine != null && !rawLine.isBlank() && y + 72 <= bodyBottom;
    if (showRawLine) {
      blitter.setFont(DEFAULT_FONT_FAMILY, sectionFontSize, true);
      blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
      blitter.drawText("Source line", contentX, y + sectionFontSize, sectionFontSize, true);
      y += sectionFontSize + 7;
      double lineBoxH = Math.min(66, bodyBottom - y);
      blitter.setFill(ERROR_BOX_COLOR[0], ERROR_BOX_COLOR[1], ERROR_BOX_COLOR[2], ERROR_BOX_COLOR[3]);
      fillRoundRect(contentX, y, contentW, lineBoxH, 8, 8);
      blitter.setStroke(ERROR_PANEL_BORDER_COLOR[0], ERROR_PANEL_BORDER_COLOR[1], ERROR_PANEL_BORDER_COLOR[2], ERROR_PANEL_BORDER_COLOR[3]);
      blitter.setStrokeWidth(1);
      strokeRoundRect(contentX, y, contentW, lineBoxH, 8, 8);
      double lineFontSize = Math.min(14, Math.max(11, height * 0.018));
      blitter.setFont("Monospaced", lineFontSize, true);
      blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
      drawWrappedText(rawLine, contentX + 14, y + 20, contentW - 28, lineBoxH - 10, "Monospaced", lineFontSize, true);
      y += lineBoxH + 12;
    }

    String likelyCause = error.getLikelyCause();
    boolean showLikelyCause = likelyCause != null && !likelyCause.isBlank() && y + 90 <= bodyBottom;
    if (showLikelyCause) {
      blitter.setFont(DEFAULT_FONT_FAMILY, sectionFontSize, true);
      blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
      blitter.drawText("Likely cause", contentX, y + sectionFontSize, sectionFontSize, true);
      y += sectionFontSize + 7;

      double likelyBoxH = Math.min(88, Math.max(62, (bodyBottom - y) * 0.34));
      blitter.setFill(ERROR_BOX_COLOR[0], ERROR_BOX_COLOR[1], ERROR_BOX_COLOR[2], ERROR_BOX_COLOR[3]);
      fillRoundRect(contentX, y, contentW, likelyBoxH, 8, 8);
      blitter.setStroke(ERROR_PANEL_BORDER_COLOR[0], ERROR_PANEL_BORDER_COLOR[1], ERROR_PANEL_BORDER_COLOR[2], ERROR_PANEL_BORDER_COLOR[3]);
      blitter.setStrokeWidth(1);
      strokeRoundRect(contentX, y, contentW, likelyBoxH, 8, 8);

      blitter.setFont("Monospaced", msgFontSize, false);
      blitter.setFill(ERROR_TEXT_COLOR[0], ERROR_TEXT_COLOR[1], ERROR_TEXT_COLOR[2], ERROR_TEXT_COLOR[3]);
      drawWrappedText(likelyCause, contentX + 14, y + 22, contentW - 28, likelyBoxH - 10, "Monospaced", msgFontSize, false);
      y += likelyBoxH + 12;
    }

    String trace = error.getStackTrace();
    if (trace != null && !trace.isBlank() && y + 72 <= bodyBottom) {
      blitter.setFont(DEFAULT_FONT_FAMILY, sectionFontSize, true);
      blitter.setFill(ERROR_DIM_TEXT_COLOR[0], ERROR_DIM_TEXT_COLOR[1], ERROR_DIM_TEXT_COLOR[2], ERROR_DIM_TEXT_COLOR[3]);
      blitter.drawText("Technical details", contentX, y + sectionFontSize, sectionFontSize, true);
      y += sectionFontSize + 7;
      double traceBoxH = Math.max(54, bodyBottom - y);
      blitter.setFill(ERROR_BOX_COLOR[0], ERROR_BOX_COLOR[1], ERROR_BOX_COLOR[2], ERROR_BOX_COLOR[3]);
      fillRoundRect(contentX, y, contentW, traceBoxH, 8, 8);
      blitter.setStroke(ERROR_PANEL_BORDER_COLOR[0], ERROR_PANEL_BORDER_COLOR[1], ERROR_PANEL_BORDER_COLOR[2], ERROR_PANEL_BORDER_COLOR[3]);
      blitter.setStrokeWidth(1);
      strokeRoundRect(contentX, y, contentW, traceBoxH, 8, 8);
      double traceFontSize = Math.min(13, Math.max(10, height * 0.016));
      blitter.setFont("Monospaced", traceFontSize, false);
      blitter.setFill(ERROR_DIM_TEXT_COLOR[0], ERROR_DIM_TEXT_COLOR[1], ERROR_DIM_TEXT_COLOR[2], ERROR_DIM_TEXT_COLOR[3]);
      drawWrappedText(trace, contentX + 14, y + 18, contentW - 28, traceBoxH - 12, "Monospaced", traceFontSize, false);
    }

    double buttonGap = Math.max(8, Math.min(14, contentW * 0.018));
    double buttonW = Math.min(164, (contentW - buttonGap * 2) / 3.0);
    double buttonsWidth = buttonW * 3 + buttonGap * 2;
    double buttonsStartX = contentX + Math.max(0, contentW - buttonsWidth);
    int hoveredButton = -1;

    if (contentW - buttonsWidth >= 250) {
      double hintFontSize = Math.min(12, Math.max(10, height * 0.016));
      blitter.setFont(DEFAULT_FONT_FAMILY, hintFontSize, false);
      blitter.setFill(ERROR_DIM_TEXT_COLOR[0], ERROR_DIM_TEXT_COLOR[1], ERROR_DIM_TEXT_COLOR[2], ERROR_DIM_TEXT_COLOR[3]);
      blitter.drawText("Enter / R  Reload    Esc  Continue    C  Copy",
          contentX, buttonY + buttonH / 2 + 5, hintFontSize, false);
    }

    String[] labels = {"Continue", "Reload Script", "Copy Details"};
    for (int i = 0; i < labels.length; i++) {
      double bx = buttonsStartX + i * (buttonW + buttonGap);
      boolean hovered = mouseX >= bx && mouseX <= bx + buttonW && mouseY >= buttonY && mouseY <= buttonY + buttonH;
      if (hovered) hoveredButton = i;

      boolean primary = i == 1;
      double[] buttonColor = primary
          ? (hovered ? ERROR_PRIMARY_BUTTON_HOVER_COLOR : ERROR_PRIMARY_BUTTON_COLOR)
          : (hovered ? ERROR_BUTTON_HOVER_COLOR : ERROR_BUTTON_COLOR);
      blitter.setFill(buttonColor[0], buttonColor[1], buttonColor[2], buttonColor[3]);
      fillRoundRect(bx, buttonY, buttonW, buttonH, 6, 6);
      double[] strokeColor = primary ? ERROR_PRIMARY_BUTTON_STROKE : ERROR_PANEL_BORDER_COLOR;
      blitter.setStroke(strokeColor[0], strokeColor[1], strokeColor[2], strokeColor[3]);
      blitter.setStrokeWidth(1);
      strokeRoundRect(bx, buttonY, buttonW, buttonH, 6, 6);

      double labelFontSize = Math.min(14, Math.max(11, buttonW / 11.0));
      blitter.setFont(DEFAULT_FONT_FAMILY, labelFontSize, true);
      blitter.setFill(ERROR_BUTTON_TEXT_COLOR[0], ERROR_BUTTON_TEXT_COLOR[1], ERROR_BUTTON_TEXT_COLOR[2], ERROR_BUTTON_TEXT_COLOR[3]);
      double textW = blitter.measureTextMetrics(labels[i], DEFAULT_FONT_FAMILY, labelFontSize, true).width();
      blitter.drawText(labels[i], bx + (buttonW - textW) / 2, buttonY + buttonH / 2 + 6, labelFontSize, true);
    }

    return hoveredButton;
  }

  private void drawWrappedText(
      String text, double x, double y, double maxWidth, double maxHeight, String family, double fontSize, boolean bold) {
    if (text == null || text.isEmpty()) return;
    double lineH = fontSize * 1.3;
    double currentY = y;
    String[] lines = text.split("\n");
    for (String line : lines) {
      if (line.isEmpty()) {
        currentY += lineH;
        continue;
      }
      String remaining = line;
      while (!remaining.isEmpty()) {
        if (currentY + lineH > y + maxHeight) {
          blitter.drawText("…", x, Math.min(currentY, y + maxHeight), fontSize, bold);
          return;
        }
        int end = fittingTextEnd(remaining, maxWidth, family, fontSize, bold);
        if (end < remaining.length()) {
          int whitespace = lastWhitespaceBefore(remaining, end);
          if (whitespace >= Math.max(1, end / 3)) end = whitespace;
        }
        end = Math.max(1, end);
        String visualLine = remaining.substring(0, end).stripTrailing();
        blitter.drawText(visualLine, x, currentY, fontSize, bold);
        currentY += lineH;
        remaining = remaining.substring(end).stripLeading();
      }
    }
  }

  private int fittingTextEnd(String text, double maxWidth, String family, double fontSize, boolean bold) {
    if (text == null || text.isEmpty()) return 0;
    if (blitter.measureTextMetrics(text, family, fontSize, bold).width() <= maxWidth) return text.length();
    int low = 1;
    int high = text.length();
    while (low < high) {
      int mid = (low + high + 1) >>> 1;
      if (blitter.measureTextMetrics(text.substring(0, mid), family, fontSize, bold).width() <= maxWidth) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return low;
  }

  private static int lastWhitespaceBefore(String text, int end) {
    int safeEnd = Math.min(text == null ? 0 : text.length(), Math.max(0, end));
    for (int i = safeEnd - 1; i > 0; i--) {
      if (Character.isWhitespace(text.charAt(i))) return i;
    }
    return -1;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Shared helpers
  // ─────────────────────────────────────────────────────────────────────────

  private List<String> wrapText(String text, double maxWidth, String family, double fontSize, boolean bold) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) return lines;
    String[] words = text.split("\\s+");
    StringBuilder current = new StringBuilder();
    for (String word : words) {
      String candidate = current.length() == 0 ? word : (current + " " + word);
      if (blitter.measureTextMetrics(candidate, family, fontSize, bold).width() <= maxWidth || current.length() == 0) {
        current.setLength(0);
        current.append(candidate);
      } else {
        lines.add(current.toString());
        current.setLength(0);
        current.append(word);
      }
    }
    if (current.length() > 0) lines.add(current.toString());
    return lines;
  }

  private double computeTextWidth(String text, VnFontSpec font) {
    return blitter.measureTextMetrics(text, font.family(), font.size(), font.bold()).width();
  }

  private double computeTextHeight(VnFontSpec font) {
    return blitter.measureTextMetrics(text(font), font.family(), font.size(), font.bold()).lineHeight();
  }

  private double computeTextAscent(VnFontSpec font) {
    return blitter.measureTextMetrics(text(font), font.family(), font.size(), font.bold()).ascent();
  }

  private static String text(VnFontSpec font) {
    return "Mg";
  }

  private double resolvePaddedTextBaselineY(
      double boxY, double boxHeight, double topPadding, double bottomPadding, VnFontSpec font, double yAlign) {
    double contentTop = boxY + Math.max(0.0, topPadding);
    double contentHeight = Math.max(1.0, boxHeight - Math.max(0.0, topPadding) - Math.max(0.0, bottomPadding));
    double textHeight = computeTextHeight(font);
    double ascent = computeTextAscent(font);
    double clampedAlign = clamp(yAlign, 0.0, 1.0);
    double extra = Math.max(0.0, contentHeight - textHeight);
    return contentTop + ascent + extra * clampedAlign;
  }

  private double resolveAlignedTextX(double contentX, double contentWidth, double textWidth, double xAlign) {
    double clampedAlign = clamp(xAlign, 0.0, 1.0);
    double available = Math.max(0.0, contentWidth - textWidth);
    return contentX + available * clampedAlign;
  }

  private double clamp(double value, double min, double max) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private String resolveRuntimeText(@Nullable String text, @Nullable Map<String, Object> variables) {
    if (text == null) return "";
    String translated = Localization.translateText(text);
    if (variables == null) return translated;
    return VnVariableInterpolator.interpolate(translated, variables);
  }

  private boolean hasPolygon(List<BoundsPointCodec.Point> points) {
    return points != null && points.size() >= 3;
  }

  private void strokeLocalPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    if (!hasPolygon(localPoints)) return;
    double[] xy = new double[localPoints.size() * 2];
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      xy[i * 2] = rectX + rectW * point.x();
      xy[i * 2 + 1] = rectY + rectH * point.y();
    }
    blitter.strokePolygon(xy);
  }

  private static @Nullable String firstNonNull(@Nullable String primary, @Nullable String fallback) {
    return primary != null ? primary : fallback;
  }

  /**
   * Approximates {@code GraphicsContext.fillRoundRect} as a plain {@link Blitter2D#fillRect}.
   * {@code Blitter2D} has no rounded-rect primitive (see {@code MenuBackgroundRenderer.fillRoundRect}
   * for the same accepted limitation); square corners are a deliberate, accepted simplification. The
   * arcW/arcH parameters are kept (unused) so call sites don't need to change if a rounded-rect
   * primitive is added later.
   */
  private void fillRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
    blitter.fillRect(x, y, w, h);
  }

  /** Approximates {@code GraphicsContext.strokeRoundRect} the same way as {@link #fillRoundRect}. */
  private void strokeRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
    blitter.strokeRect(x, y, w, h);
  }

  private static double[] parseHex(String hex) {
    String h = hex.startsWith("#") ? hex.substring(1) : hex;
    int r = Integer.parseInt(h.substring(0, 2), 16);
    int g = Integer.parseInt(h.substring(2, 4), 16);
    int b = Integer.parseInt(h.substring(4, 6), 16);
    double a = h.length() >= 8 ? Integer.parseInt(h.substring(6, 8), 16) / 255.0 : 1.0;
    return new double[] {r / 255.0, g / 255.0, b / 255.0, a};
  }
}

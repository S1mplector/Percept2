package com.jvn.core.vn.ui;

import com.jvn.core.localization.Localization;
import com.jvn.core.vn.VnConditionEvaluator;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Overlay screen that re-evaluates text and button state from {@link VnState}.
 */
public final class VnReactiveOverlayScreenSpec extends VnOverlayScreenSpec {
  private final VnReactiveScreenSpec definition;
  private final VnState state;

  public VnReactiveOverlayScreenSpec(VnReactiveScreenSpec definition, VnState state, boolean callScreenOverride) {
    super(
        definition == null ? "screen" : definition.id(),
        definition == null ? "" : definition.title(),
        definition == null ? "" : definition.text(),
        definition == null ? 0.18 : definition.x(),
        definition == null ? 0.18 : definition.y(),
        definition == null ? 0.64 : definition.width(),
        definition == null ? 0.42 : definition.height(),
        definition != null && (definition.modal() || callScreenOverride),
        definition == null || definition.dimBackground(),
        definition != null && callScreenOverride ? false : definition == null || definition.dismissOnAdvance(),
        definition != null && (definition.callScreen() || callScreenOverride),
        definition == null ? 0L : definition.timerMs(),
        definition == null ? "hide" : definition.timerAction(),
        definition == null ? "" : definition.timerTarget(),
        definition == null ? "screen.return" : definition.returnKey(),
        List.of()
    );
    this.definition = definition;
    this.state = state;
  }

  public VnReactiveScreenSpec getDefinition() {
    return definition;
  }

  public VnFacetSpec getFacet() {
    return definition == null ? new VnFacetSpec("root", List.of()) : definition.facet();
  }

  public boolean isFacetNodeVisible(VnFacetSpec.Node node) {
    return node != null && evalBool(node.visibleIf(), true);
  }

  public String resolveFacetText(String raw) {
    return resolveText(raw);
  }

  public double resolveFacetNumber(String raw, double fallback) {
    String value = resolveText(raw);
    if (value == null || value.isBlank()) return fallback;
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  public boolean isVisibleNow() {
    return definition == null || evalBool(definition.visibleIf(), true);
  }

  @Override
  public String getTitle() {
    return resolveText(definition == null ? "" : definition.title());
  }

  @Override
  public String getText() {
    return resolveText(definition == null ? "" : definition.text());
  }

  @Override
  public List<VnOverlayButtonSpec> getButtons() {
    if (definition == null || definition.buttons().isEmpty() || !isVisibleNow()) return List.of();
    List<VnOverlayButtonSpec> out = new ArrayList<>();
    for (VnReactiveScreenSpec.Button button : definition.buttons()) {
      if (button == null || !evalBool(button.visibleIf(), true)) continue;
      boolean enabled = button.enabled() && evalBool(button.enabledIf(), true);
      out.add(new VnOverlayButtonSpec(
          button.id(),
          definition.id(),
          resolveText(button.label()),
          resolveText(button.action()),
          resolveText(button.target()),
          enabled,
          button.x(),
          button.y(),
          button.width(),
          button.height(),
          button.coordinateSpace()
      ));
    }
    return List.copyOf(out);
  }

  private String resolveText(String raw) {
    if (raw == null) return "";
    String translated = Localization.translateText(raw);
    Map<String, Object> variables = state == null ? Map.of() : state.getVariables();
    return VnVariableInterpolator.interpolate(translated, variables);
  }

  private boolean evalBool(String expression, boolean fallback) {
    if (expression == null || expression.isBlank()) return fallback;
    try {
      return VnConditionEvaluator.evaluate(expression, state == null ? Map.of() : state.getVariables());
    } catch (Exception ex) {
      return fallback;
    }
  }
}

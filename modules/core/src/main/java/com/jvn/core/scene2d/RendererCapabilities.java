package com.jvn.core.scene2d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable description of the optional features exposed by a renderer. */
public final class RendererCapabilities {
  private final String rendererName;
  private final Set<RenderFeature> supportedFeatures;

  private RendererCapabilities(String rendererName, Set<RenderFeature> supportedFeatures) {
    this.rendererName = Objects.requireNonNull(rendererName, "rendererName");
    this.supportedFeatures = Collections.unmodifiableSet(
        supportedFeatures.isEmpty()
            ? EnumSet.noneOf(RenderFeature.class)
            : EnumSet.copyOf(supportedFeatures));
  }

  public static RendererCapabilities baseline(String rendererName) {
    return new RendererCapabilities(rendererName, EnumSet.noneOf(RenderFeature.class));
  }

  public static RendererCapabilities of(String rendererName, RenderFeature... features) {
    EnumSet<RenderFeature> supported = EnumSet.noneOf(RenderFeature.class);
    if (features != null) Collections.addAll(supported, features);
    return new RendererCapabilities(rendererName, supported);
  }

  public String rendererName() {
    return rendererName;
  }

  public Set<RenderFeature> supportedFeatures() {
    return supportedFeatures;
  }

  public boolean supports(RenderFeature feature) {
    return supportedFeatures.contains(Objects.requireNonNull(feature, "feature"));
  }

  public boolean supportsAll(RenderFeature... features) {
    if (features == null) return true;
    for (RenderFeature feature : features) {
      if (!supports(feature)) return false;
    }
    return true;
  }

  public void require(RenderFeature feature) {
    if (!supports(feature)) {
      throw new UnsupportedOperationException(
          "Renderer '" + rendererName + "' does not support " + feature);
    }
  }

  public void requireAll(RenderFeature... features) {
    if (features == null) return;
    for (RenderFeature feature : features) require(feature);
  }

  @Override
  public String toString() {
    return rendererName + supportedFeatures;
  }
}

package com.jvn.fx.ui;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javafx.scene.text.Font;
import javafx.scene.text.Text;

public final class FxTextMetrics {
  private static final int MAX_WIDTH_CACHE_ENTRIES_PER_FONT = 256;
  private final Text probe = new Text();
  private final IdentityHashMap<Font, Map<String, Double>> widthCache = new IdentityHashMap<>();
  private final IdentityHashMap<Font, FontMetrics> fontMetricsCache = new IdentityHashMap<>();

  public double width(String text, Font font) {
    if (text == null || text.isEmpty() || font == null) return 0.0;
    Map<String, Double> widths = widthCache.computeIfAbsent(font, ignored -> createWidthCache());
    Double cached = widths.get(text);
    if (cached != null) {
      return cached;
    }
    probe.setText(text);
    probe.setFont(font);
    double measured = probe.getLayoutBounds().getWidth();
    widths.put(text, measured);
    return measured;
  }

  public double ascent(Font font) {
    return resolveFontMetrics(font).ascent();
  }

  public double height(Font font) {
    return resolveFontMetrics(font).height();
  }

  public void clear() {
    widthCache.clear();
    fontMetricsCache.clear();
  }

  private FontMetrics resolveFontMetrics(Font font) {
    if (font == null) {
      return new FontMetrics(1.0, 1.0);
    }
    FontMetrics cached = fontMetricsCache.get(font);
    if (cached != null) {
      return cached;
    }
    probe.setText("Hg");
    probe.setFont(font);
    double ascent = -probe.getLayoutBounds().getMinY();
    if (ascent <= 0.0) {
      ascent = Math.max(1.0, font.getSize() * 0.8);
    }
    double height = probe.getLayoutBounds().getHeight();
    if (height <= 0.0) {
      height = Math.max(1.0, font.getSize());
    }
    FontMetrics metrics = new FontMetrics(ascent, height);
    fontMetricsCache.put(font, metrics);
    return metrics;
  }

  private static Map<String, Double> createWidthCache() {
    return new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
        return size() > MAX_WIDTH_CACHE_ENTRIES_PER_FONT;
      }
    };
  }

  private record FontMetrics(double ascent, double height) {
  }
}

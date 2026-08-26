package com.jvn.scenerender.vn;

import java.util.Arrays;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.vn.VnAudioVisualizerConfig;
import org.jspecify.annotations.Nullable;

/**
 * Self-contained audio visualizer bar/backdrop rendering and beat/level state machine, ported
 * from the original monolithic {@code VnRenderer} (JavaFX {@code GraphicsContext}-bound) onto
 * the platform-agnostic {@link Blitter2D} drawing abstraction.
 *
 * <h2>Known port limitations</h2>
 * <ul>
 *   <li>{@link Blitter2D} has no rounded-rect primitive (see {@code MenuBackgroundRenderer}'s
 *   {@code fillRoundRect} for the same accepted limitation): bar/glow rounded corners become
 *   square via a plain {@link Blitter2D#fillRect}.</li>
 * </ul>
 */
final class VnAudioVisualizerRenderer {

  private static final int VISUALIZER_BAR_COUNT = VnAudioVisualizerConfig.MAX_BARS;
  private static final VnStageLightingSupport.Rgba FALLBACK_BASE_COLOR =
      VnStageLightingSupport.Rgba.parse("#7DE2FF", VnStageLightingSupport.Rgba.WHITE);

  private final Blitter2D blitter;

  private final double[] visualizerLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerTargets = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerLevelVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthMultipliers = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerGlowLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerBassHistory = new double[12];
  private int visualizerBassHistoryIndex = 0;
  private long visualizerLastBeatAtNanos = 0L;
  private double visualizerBeatFlashIntensity = 0.0;
  private double visualizerHue = 182.0;

  VnAudioVisualizerRenderer(Blitter2D blitter) {
    this.blitter = blitter;
    Arrays.fill(visualizerWidthMultipliers, 1.0);
  }

  /** Resolves {@code {x, y, width, height}} for the region the visualizer must stay above. */
  interface TextBoxGeometryProvider {
    double[] apply(double width, double height);
  }

  record AudioVisualizerSettings(
      boolean enabled,
      int bars,
      String style,
      String colorToken,
      String accentToken,
      double alpha,
      boolean glow,
      double heightFactor,
      int zIndex) {}

  private record AudioVisualizerPalette(
      VnStageLightingSupport.Rgba base,
      VnStageLightingSupport.Rgba accent,
      VnStageLightingSupport.Rgba shadow) {}

  void render(
      double width,
      double height,
      AudioVisualizerSettings settings,
      @Nullable AudioFacade audioFacade,
      TextBoxGeometryProvider textBoxGeometry) {
    if (!settings.enabled()) {
      decayVisualizer(0.86, true);
      return;
    }
    int activeBars = settings.bars();
    if (activeBars <= 0) {
      decayVisualizer(0.86, true);
      return;
    }
    if (audioFacade == null) {
      decayVisualizer(0.86, true);
      return;
    }

    float[] magnitudes = audioFacade.getBgmSpectrumMagnitudes();
    long updatedAt = audioFacade.getBgmSpectrumUpdatedAtNanos();
    long nowNs = System.nanoTime();
    boolean hasFreshData = magnitudes != null
        && magnitudes.length > 0
        && (updatedAt <= 0L || (nowNs - updatedAt) <= VnAudioVisualizerConfig.STALE_NS);

    if (hasFreshData) {
      mapSpectrumToTargets(magnitudes, visualizerTargets, activeBars);
      updateAudioVisualizerState(activeBars);
    } else {
      decayVisualizer(0.9, false);
      clearInactiveVisualizerState(activeBars);
    }

    double maxLevel = 0.0;
    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level > maxLevel) maxLevel = level;
    }
    if (maxLevel < 0.015) return;

    double[] textBox = textBoxGeometry.apply(width, height);
    double textBoxY = textBox[1];
    double regionBottom = Math.min(height, textBoxY - 2.0);
    if (regionBottom <= 8.0) return;
    double regionHeight = Math.max(24.0, regionBottom * settings.heightFactor());
    double regionTop = Math.max(0.0, regionBottom - regionHeight);
    double sidePadding = Math.max(0.0, width * 0.018);
    double regionWidth = Math.max(1.0, width - sidePadding * 2.0);
    if (regionWidth <= 8.0) return;

    AudioVisualizerPalette palette = resolveAudioVisualizerPalette(settings, maxLevel);

    blitter.push();
    blitter.setGlobalAlpha(1.0);
    drawAudioVisualizerBackdrop(settings, palette, sidePadding, regionTop, regionWidth, regionHeight, regionBottom);
    drawAudioVisualizerBars(settings, palette, activeBars, sidePadding, regionWidth, regionTop, regionBottom, regionHeight);
    blitter.pop();
  }

  private void updateAudioVisualizerState(int activeBars) {
    boolean beat = detectVisualizerBeat(activeBars);
    visualizerBeatFlashIntensity = beat ? 1.0 : visualizerBeatFlashIntensity * 0.90;

    for (int i = 0; i < activeBars; i++) {
      double target = visualizerTargets[i];
      double diff = target - visualizerLevels[i];

      visualizerLevelVelocities[i] = (visualizerLevelVelocities[i] + diff * 0.28) * 0.84;
      visualizerLevels[i] = clamp(visualizerLevels[i] + visualizerLevelVelocities[i], 0.0, 1.0);
      if (Math.abs(diff) < 0.015) {
        visualizerLevels[i] = clamp(visualizerLevels[i] * 0.82 + target * 0.18, 0.0, 1.0);
      }

      if (visualizerLevels[i] > visualizerPeakLevels[i]) {
        visualizerPeakLevels[i] = visualizerLevels[i];
        visualizerPeakVelocities[i] = 0.0;
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.18 + visualizerLevels[i] * 0.82);
      } else {
        visualizerPeakVelocities[i] += 0.012 + (1.0 - visualizerLevels[i]) * 0.010;
        visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * 0.045);
      }

      double targetWidth = 0.70 + Math.pow(visualizerLevels[i], 0.72) * 0.62;
      visualizerWidthVelocities[i] = (visualizerWidthVelocities[i]
          + (targetWidth - visualizerWidthMultipliers[i]) * 0.22) * 0.86;
      visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + visualizerWidthVelocities[i], 0.62, 1.42);
      visualizerGlowLevels[i] *= 0.91;

      if (beat) {
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.28 + visualizerLevels[i] * 0.45);
        visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + 0.06, 0.62, 1.42);
      }
    }

    clearInactiveVisualizerState(activeBars);
  }

  private boolean detectVisualizerBeat(int activeBars) {
    int bassBars = Math.min(6, activeBars);
    if (bassBars <= 0) return false;

    double bassEnergy = 0.0;
    for (int i = 0; i < bassBars; i++) {
      bassEnergy += visualizerTargets[i];
    }
    bassEnergy /= bassBars;

    double average = 0.0;
    for (double value : visualizerBassHistory) {
      average += value;
    }
    average /= visualizerBassHistory.length;

    visualizerBassHistory[visualizerBassHistoryIndex] = bassEnergy;
    visualizerBassHistoryIndex = (visualizerBassHistoryIndex + 1) % visualizerBassHistory.length;

    long nowNs = System.nanoTime();
    double threshold = average * 1.35 + 0.06;
    if (bassEnergy > threshold && (nowNs - visualizerLastBeatAtNanos) > 180_000_000L) {
      visualizerLastBeatAtNanos = nowNs;
      return true;
    }
    return false;
  }

  private AudioVisualizerPalette resolveAudioVisualizerPalette(AudioVisualizerSettings settings, double maxLevel) {
    boolean cycleColors = VnAudioVisualizerConfig.isAutoToken(settings.colorToken());
    if (cycleColors) {
      visualizerHue += 0.55 + maxLevel * 0.45 + visualizerBeatFlashIntensity * 0.30;
      while (visualizerHue >= 360.0) visualizerHue -= 360.0;
    }

    VnStageLightingSupport.Rgba base = cycleColors
        ? hsbToRgb(visualizerHue, 0.76, 1.0)
        : VnStageLightingSupport.Rgba.parse(settings.colorToken(), FALLBACK_BASE_COLOR);
    VnStageLightingSupport.Rgba accent = VnAudioVisualizerConfig.isAutoToken(settings.accentToken())
        ? interpolate(base, VnStageLightingSupport.Rgba.WHITE, 0.36)
        : VnStageLightingSupport.Rgba.parse(settings.accentToken(), interpolate(base, VnStageLightingSupport.Rgba.WHITE, 0.36));
    return new AudioVisualizerPalette(base, accent, darker(darker(base)));
  }

  private void drawAudioVisualizerBackdrop(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      double x,
      double regionTop,
      double regionWidth,
      double regionHeight,
      double regionBottom) {
    blitter.setFillLinearGradient(
        0, regionTop, 0, regionBottom,
        new double[] {0.0, 0.42, 1.0},
        new double[] {
            palette.base().r(), palette.base().g(), palette.base().b(), settings.alpha() * 0.12,
            palette.base().r(), palette.base().g(), palette.base().b(), settings.alpha() * 0.03,
            0.0, 0.0, 0.0, 0.0
        });
    blitter.fillRect(x, regionTop, regionWidth, regionHeight);

    if (visualizerBeatFlashIntensity > 0.02 && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      VnStageLightingSupport.Rgba accent = palette.accent();
      blitter.setFill(accent.r(), accent.g(), accent.b(), settings.alpha() * 0.08 * visualizerBeatFlashIntensity);
      blitter.fillRect(x, regionTop, regionWidth, regionHeight);
    }

    VnStageLightingSupport.Rgba accent = palette.accent();
    blitter.setStroke(accent.r(), accent.g(), accent.b(), settings.alpha() * 0.28);
    blitter.setStrokeWidth(1.0);
    blitter.drawLine(x, regionBottom + 0.5, x + regionWidth, regionBottom + 0.5);
  }

  private void drawAudioVisualizerBars(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      int activeBars,
      double sidePadding,
      double regionWidth,
      double regionTop,
      double regionBottom,
      double regionHeight) {
    boolean dynamic = VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style());
    double bandWidth = regionWidth / activeBars;
    double baseBarWidth = bandWidth * (dynamic ? 0.76 : 0.68);
    boolean traceStarted = false;

    if (dynamic) {
      VnStageLightingSupport.Rgba accent = palette.accent();
      blitter.setStroke(accent.r(), accent.g(), accent.b(), settings.alpha() * 0.52);
      blitter.setStrokeWidth(1.8);
      blitter.beginPath();
    }

    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level <= 0.002) continue;

      double normalized = Math.pow(level, dynamic ? 0.68 : 0.78);
      double barHeight = Math.max(2.0, normalized * regionHeight);
      double widthMultiplier = dynamic ? visualizerWidthMultipliers[i] : 1.0;
      double actualWidth = clamp(baseBarWidth * widthMultiplier, 1.0, Math.max(1.0, bandWidth - 0.6));
      double barX = sidePadding + i * bandWidth + (bandWidth - actualWidth) * 0.5;
      double barY = regionBottom - barHeight;

      VnStageLightingSupport.Rgba barBase = interpolate(palette.base(), palette.accent(), (i / (double) Math.max(1, activeBars - 1)) * 0.24);
      VnStageLightingSupport.Rgba barTop = interpolate(
          interpolate(barBase, palette.accent(), 0.46), VnStageLightingSupport.Rgba.WHITE, Math.min(0.28, level * 0.24));
      VnStageLightingSupport.Rgba barBottom = interpolate(palette.shadow(), barBase, 0.30);

      if (settings.glow() && dynamic) {
        double glowPad = 2.0 + visualizerGlowLevels[i] * 5.0;
        blitter.setFill(barBase.r(), barBase.g(), barBase.b(), settings.alpha() * (0.08 + visualizerGlowLevels[i] * 0.12));
        blitter.fillRect(
            barX - glowPad * 0.5,
            Math.max(regionTop, barY - glowPad),
            actualWidth + glowPad,
            Math.min(regionHeight, barHeight + glowPad * 1.5));
      }

      blitter.setFillLinearGradient(
          0, barY, 0, regionBottom,
          new double[] {0.0, 0.55, 1.0},
          new double[] {
              barTop.r(), barTop.g(), barTop.b(), settings.alpha(),
              barBase.r(), barBase.g(), barBase.b(), settings.alpha() * 0.96,
              barBottom.r(), barBottom.g(), barBottom.b(), settings.alpha() * 0.92
          });
      blitter.fillRect(barX, barY, actualWidth, barHeight);

      if (dynamic) {
        double peakLevel = Math.max(level, visualizerPeakLevels[i]);
        if (peakLevel > level + 0.015) {
          double peakY = regionBottom - Math.max(2.0, Math.pow(peakLevel, 0.70) * regionHeight);
          VnStageLightingSupport.Rgba peakColor = interpolate(palette.accent(), VnStageLightingSupport.Rgba.WHITE, 0.20);
          blitter.setFill(peakColor.r(), peakColor.g(), peakColor.b(), settings.alpha() * 0.90);
          blitter.fillRect(barX, peakY, actualWidth, 3.0);
        }

        double traceX = barX + actualWidth * 0.5;
        double traceY = Math.max(regionTop, barY - Math.min(14.0, 3.0 + visualizerGlowLevels[i] * 7.0));
        if (!traceStarted) {
          blitter.moveTo(traceX, traceY);
          traceStarted = true;
        } else {
          blitter.lineTo(traceX, traceY);
        }
      }
    }

    if (traceStarted && dynamic) {
      blitter.strokePath();
    }
  }

  private void decayVisualizer(double factor, boolean hard) {
    for (int i = 0; i < visualizerLevels.length; i++) {
      visualizerLevels[i] *= factor;
      if (visualizerLevels[i] < 0.0001) visualizerLevels[i] = 0.0;
      visualizerLevelVelocities[i] *= hard ? 0.68 : 0.82;
      visualizerPeakVelocities[i] += hard ? 0.006 : 0.010;
      visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * (hard ? 0.065 : 0.045));
      if (visualizerPeakLevels[i] < 0.0001) visualizerPeakLevels[i] = 0.0;
      visualizerWidthVelocities[i] *= hard ? 0.72 : 0.82;
      visualizerWidthMultipliers[i] = clamp(1.0 + (visualizerWidthMultipliers[i] - 1.0) * factor, 0.62, 1.42);
      if (Math.abs(visualizerWidthMultipliers[i] - 1.0) < 0.002) visualizerWidthMultipliers[i] = 1.0;
      visualizerGlowLevels[i] *= hard ? 0.80 : 0.88;
    }
    visualizerBeatFlashIntensity *= hard ? 0.82 : 0.90;
  }

  private void clearInactiveVisualizerState(int activeBars) {
    for (int i = activeBars; i < visualizerLevels.length; i++) {
      visualizerLevels[i] = 0.0;
      visualizerTargets[i] = 0.0;
      visualizerLevelVelocities[i] = 0.0;
      visualizerPeakLevels[i] = 0.0;
      visualizerPeakVelocities[i] = 0.0;
      visualizerWidthMultipliers[i] = 1.0;
      visualizerWidthVelocities[i] = 0.0;
      visualizerGlowLevels[i] = 0.0;
    }
  }

  private void mapSpectrumToTargets(float[] magnitudes, double[] out, int activeBars) {
    Arrays.fill(out, 0.0);
    if (magnitudes == null || magnitudes.length == 0 || out.length == 0 || activeBars <= 0) return;
    double bandsPerBar = magnitudes.length / (double) activeBars;

    for (int i = 0; i < activeBars; i++) {
      int start = (int) Math.floor(i * bandsPerBar);
      int end = (int) Math.ceil((i + 1) * bandsPerBar);
      if (end <= start) end = start + 1;
      start = Math.max(0, Math.min(start, magnitudes.length - 1));
      end = Math.max(start + 1, Math.min(end, magnitudes.length));

      double sum = 0.0;
      int count = 0;
      for (int j = start; j < end; j++) {
        double db = magnitudes[j];
        double normalized = (db + 60.0) / 60.0;
        normalized = clamp(normalized, 0.0, 1.0);
        normalized = Math.pow(normalized, 0.72);
        sum += normalized;
        count++;
      }
      double avg = count == 0 ? 0.0 : (sum / count);
      double freqWeight = 1.0 - (i / (double) activeBars) * 0.35;
      out[i] = clamp(avg * freqWeight, 0.0, 1.0);
    }
  }

  private static VnStageLightingSupport.Rgba interpolate(VnStageLightingSupport.Rgba a, VnStageLightingSupport.Rgba b, double t) {
    return new VnStageLightingSupport.Rgba(
        a.r() + (b.r() - a.r()) * t,
        a.g() + (b.g() - a.g()) * t,
        a.b() + (b.b() - a.b()) * t,
        a.a() + (b.a() - a.a()) * t);
  }

  /** Matches JavaFX {@code Color.darker()}'s ~0.7 per-channel factor. */
  private static VnStageLightingSupport.Rgba darker(VnStageLightingSupport.Rgba rgb) {
    return new VnStageLightingSupport.Rgba(rgb.r() * 0.7, rgb.g() * 0.7, rgb.b() * 0.7, rgb.a());
  }

  private static VnStageLightingSupport.Rgba hsbToRgb(double hue, double saturation, double brightness) {
    double h = ((hue % 360.0) + 360.0) % 360.0;
    double s = clamp(saturation, 0.0, 1.0);
    double v = clamp(brightness, 0.0, 1.0);

    double c = v * s;
    double hPrime = h / 60.0;
    double x = c * (1 - Math.abs(hPrime % 2 - 1));
    double r1;
    double g1;
    double b1;
    if (hPrime < 1) {
      r1 = c; g1 = x; b1 = 0;
    } else if (hPrime < 2) {
      r1 = x; g1 = c; b1 = 0;
    } else if (hPrime < 3) {
      r1 = 0; g1 = c; b1 = x;
    } else if (hPrime < 4) {
      r1 = 0; g1 = x; b1 = c;
    } else if (hPrime < 5) {
      r1 = x; g1 = 0; b1 = c;
    } else {
      r1 = c; g1 = 0; b1 = x;
    }
    double m = v - c;
    return new VnStageLightingSupport.Rgba(r1 + m, g1 + m, b1 + m, 1.0);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}

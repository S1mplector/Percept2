package com.jvn.scenerender.vn;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnTransition;
import org.jspecify.annotations.Nullable;

/**
 * Background crossfade/slide/wipe rendering and full-screen transition overlay effects, ported
 * from the original monolithic {@code VnRenderer} (JavaFX {@code GraphicsContext}-bound) onto the
 * platform-agnostic {@link Blitter2D} drawing abstraction.
 *
 * <h2>Known port limitations</h2>
 * <ul>
 *   <li><b>{@code IRIS_IN}/{@code IRIS_OUT} are approximated as a plain fade.</b> The original
 *   clipped to a circular path (via {@code gc.arc}) to punch a growing/shrinking transparent hole
 *   through a black overlay. {@code Blitter2D} has no circular-clip primitive and no
 *   destination-clearing blend mode that could fake one, so a pixel-accurate port isn't possible
 *   with the primitives available here. This port substitutes the same math as the {@code FADE}
 *   case (using the iris's own eased progress) as an accepted, documented visual simplification —
 *   revisit only if a real project scenario is found to rely on the circular reveal shape.</li>
 *   <li>Background image loading/caching is not this class's responsibility: callers supply a
 *   {@link BackgroundPathResolver} that resolves a {@link VnBackground} to a classpath string;
 *   {@link Blitter2D#drawImage} backends already cache decoded images internally, so no local
 *   image cache is kept here (mirrors the original's retirement of {@code loadBackgroundImage}
 *   once nothing calls it directly).</li>
 * </ul>
 */
final class VnTransitionRenderer {

  private final Blitter2D blitter;

  VnTransitionRenderer(Blitter2D blitter) {
    this.blitter = blitter;
  }

  /** Resolves a {@link VnBackground} to a classpath image path, or {@code null} if unresolvable. */
  interface BackgroundPathResolver {
    @Nullable String resolve(@Nullable VnBackground background);
  }

  void renderSlideBackground(
      @Nullable VnBackground prev,
      @Nullable VnBackground cur,
      float progress,
      double width,
      double height,
      boolean left,
      BackgroundPathResolver resolver) {
    double p = Math.max(0, Math.min(1, progress));
    double offset = width * p;
    double prevX = left ? -offset : offset;
    double curX = left ? (width - offset) : (-width + offset);
    if (prev != null || cur != null) {
      drawBackgroundAt(prev, prevX, 0, width, height, resolver);
      drawBackgroundAt(cur, curX, 0, width, height, resolver);
    }
  }

  void renderWipeBackground(
      @Nullable VnBackground prev,
      @Nullable VnBackground cur,
      float progress,
      double width,
      double height,
      BackgroundPathResolver resolver) {
    drawBackgroundAt(prev, 0, 0, width, height, resolver);
    if (cur != null) {
      double p = Math.max(0, Math.min(1, progress));
      double wipeW = width * p;
      blitter.push();
      blitter.setClipRect(0, 0, wipeW, height);
      drawBackgroundAt(cur, 0, 0, width, height, resolver);
      blitter.pop();
    }
  }

  void renderCrossfadeBackground(
      @Nullable VnBackground prev,
      @Nullable VnBackground cur,
      float progress,
      double width,
      double height,
      BackgroundPathResolver resolver) {
    double alphaCur = Math.max(0, Math.min(1, progress));
    double alphaPrev = 1.0 - alphaCur;
    if (prev != null) {
      String pathPrev = resolver.resolve(prev);
      if (pathPrev != null) {
        blitter.setGlobalAlpha(alphaPrev);
        blitter.drawImage(pathPrev, 0, 0, width, height);
      }
    }
    if (cur != null) {
      String pathCur = resolver.resolve(cur);
      if (pathCur != null) {
        blitter.setGlobalAlpha(alphaCur);
        blitter.drawImage(pathCur, 0, 0, width, height);
      }
    }
    blitter.setGlobalAlpha(1.0);
  }

  private void drawBackgroundAt(
      @Nullable VnBackground background, double x, double y, double width, double height, BackgroundPathResolver resolver) {
    if (background == null) {
      blitter.setFill(0.184, 0.310, 0.310, 1.0);
      blitter.fillRect(x, y, width, height);
      return;
    }
    String path = resolver.resolve(background);
    if (path != null) {
      blitter.drawImage(path, x, y, width, height);
    } else {
      blitter.setFill(0.184, 0.310, 0.310, 1.0);
      blitter.fillRect(x, y, width, height);
    }
  }

  void renderTransitionOverlay(VnState state, double width, double height) {
    if (state.getActiveTransition() == null) return;
    float progress = state.getTransitionProgress();
    VnTransition.TransitionType transitionType = state.getActiveTransition().getType();

    switch (transitionType) {
      case FADE -> {
        double opacity = 1.0 - progress;
        blitter.setFill(0.0, 0.0, 0.0, opacity);
        blitter.fillRect(0, 0, width, height);
      }
      case DISSOLVE -> {
        double eased = easeInOutQuad(progress);
        double opacity = 1.0 - eased;
        blitter.setFill(0.0, 0.0, 0.0, opacity * 0.85);
        blitter.fillRect(0, 0, width, height);
      }
      case SLIDE_LEFT -> {
        double eased = easeOutCubic(progress);
        double panelX = -width * eased;
        blitter.setFill(0.0, 0.0, 0.0, 1.0);
        blitter.fillRect(panelX, 0, width, height);
      }
      case SLIDE_RIGHT -> {
        double eased = easeOutCubic(progress);
        double panelX = width * (1.0 - eased) - width;
        blitter.setFill(0.0, 0.0, 0.0, 1.0);
        blitter.fillRect(width - panelX - width, 0, width, height);
      }
      case WIPE -> {
        double eased = easeInOutQuad(progress);
        double wipeWidth = width * (1.0 - eased);
        blitter.setFill(0.0, 0.0, 0.0, 1.0);
        blitter.fillRect(0, 0, wipeWidth, height);
      }
      case PIXELATE -> {
        double eased = easeInOutQuad(progress);
        double opacity = 1.0 - eased;
        int blockSize = Math.max(2, (int) (40 * (1.0 - eased)));
        blitter.setFill(0.0, 0.0, 0.0, Math.max(0, Math.min(1, opacity * 0.8)));
        for (int bx = 0; bx < width; bx += blockSize * 2) {
          for (int by = 0; by < height; by += blockSize * 2) {
            blitter.fillRect(bx, by, blockSize, blockSize);
          }
        }
      }
      case BLINDS -> {
        double eased = easeInOutQuad(progress);
        int slats = 12;
        double slatH = height / slats;
        double slatVisible = slatH * (1.0 - eased);
        blitter.setFill(0.0, 0.0, 0.0, 1.0);
        for (int i = 0; i < slats; i++) {
          blitter.fillRect(0, i * slatH, width, slatVisible);
        }
      }
      case IRIS_IN -> {
        // Known limitation: approximated as a fade — see class Javadoc.
        double eased = easeOutCubic(progress);
        double opacity = 1.0 - eased;
        blitter.setFill(0.0, 0.0, 0.0, opacity);
        blitter.fillRect(0, 0, width, height);
      }
      case IRIS_OUT -> {
        // Known limitation: approximated as a fade — see class Javadoc.
        double eased = easeOutCubic(progress);
        double opacity = eased;
        blitter.setFill(0.0, 0.0, 0.0, opacity);
        blitter.fillRect(0, 0, width, height);
      }
      case CROSSFADE -> {
        // Crossfade is handled separately by renderCrossfadeBackground for backgrounds.
      }
      case NONE -> {
        // No visual effect.
      }
      default -> {
        // MASK and any future transition types: no visual effect (matches original's
        // non-exhaustive switch statement, which silently ignored unmatched cases).
      }
    }
  }

  void renderFlashOverlay(VnState state, double width, double height) {
    float alpha = state.getFlashAlpha();
    if (alpha <= 0.001f) return;
    float r = state.getFlashR();
    float g = state.getFlashG();
    float b = state.getFlashB();
    blitter.setFill(r, g, b, Math.min(1.0, alpha));
    blitter.fillRect(0, 0, width, height);
  }

  private double easeInOutQuad(double t) {
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }

  private double easeOutCubic(double t) {
    return 1 - Math.pow(1 - t, 3);
  }

  private double easeInCubic(double t) {
    return t * t * t;
  }
}

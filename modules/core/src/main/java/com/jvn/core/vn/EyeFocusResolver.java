package com.jvn.core.vn;

/**
 * Resolves a look-at vector to the nearest keypad-style eye pose.
 *
 * <p>The keypad layout is:</p>
 * <pre>
 * 7 8 9
 * 4 5 6
 * 1 2 3
 * </pre>
 */
public final class EyeFocusResolver {
  private static final double AXIS_THRESHOLD = 0.35;

  private EyeFocusResolver() {}

  public record Result(
      int keypadIndex,
      double normalizedX,
      double normalizedY,
      double nudgeX,
      double nudgeY
  ) {
    public boolean neutral() {
      return keypadIndex == 5;
    }
  }

  /**
   * Resolve a source-to-target vector.
   *
   * <p>For authored canvas points, callers should pass values in the same local
   * coordinate space. For sprite-relative gaze, pass normalized coordinates such
   * as {@code dx / spriteWidth} and {@code dy / spriteHeight}; this makes the
   * {@code deadZone} fraction meaningful across character sizes.</p>
   */
  public static Result resolve(
      double sourceX,
      double sourceY,
      double targetX,
      double targetY,
      double deadZone,
      double maxNudgePx,
      double strength
  ) {
    double dx = finite(targetX - sourceX, 0.0);
    double dy = finite(targetY - sourceY, 0.0);
    double distance = Math.hypot(dx, dy);
    double safeDeadZone = clamp(finite(deadZone, 0.12), 0.0, 1.0);
    if (distance <= safeDeadZone || distance <= 1e-9) {
      return new Result(5, 0.0, 0.0, 0.0, 0.0);
    }

    double nx = dx / distance;
    double ny = dy / distance;
    int col = axis(nx);
    int row = axis(ny);
    int index = switch (row) {
      case -1 -> switch (col) {
        case -1 -> 7;
        case 1 -> 9;
        default -> 8;
      };
      case 1 -> switch (col) {
        case -1 -> 1;
        case 1 -> 3;
        default -> 2;
      };
      default -> switch (col) {
        case -1 -> 4;
        case 1 -> 6;
        default -> 5;
      };
    };

    double nudgeScale = Math.max(0.0, finite(maxNudgePx, 3.0))
        * clamp(finite(strength, 1.0), 0.0, 2.0);
    return new Result(index, nx, ny, nx * nudgeScale, ny * nudgeScale);
  }

  private static int axis(double value) {
    if (value <= -AXIS_THRESHOLD) return -1;
    if (value >= AXIS_THRESHOLD) return 1;
    return 0;
  }

  private static double finite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}

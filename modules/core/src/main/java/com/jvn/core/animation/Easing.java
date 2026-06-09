package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;

/**
 * Library of easing functions for smooth animation interpolation.
 */
public class Easing {

  public enum Type {
    LINEAR,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
    EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE,
    SPRING, DAMPED_SPRING,
    HERO_POP, UI_SOFT_IN, CAMERA_GLIDE,
    CUSTOM, CURVE
  }

  public enum Interpolation {
    TWEEN,
    HOLD,
    STEP
  }

  public static double apply(Type type, double t) {
    return apply(type, null, t);
  }

  public static double apply(EasingSpec spec, double t) {
    EasingSpec resolved = spec != null ? spec : EasingSpec.of(Type.LINEAR);
    return apply(resolved.getType(), resolved.getParameters(), t);
  }

  public static double apply(Type type, double[] params, double t) {
    t = Math.max(0, Math.min(1, t));
    Type resolved = type != null ? type : Type.LINEAR;

    return switch (resolved) {
      case LINEAR -> t;

      case EASE_IN_QUAD -> t * t;
      case EASE_OUT_QUAD -> t * (2 - t);
      case EASE_IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

      case EASE_IN_CUBIC -> t * t * t;
      case EASE_OUT_CUBIC -> (--t) * t * t + 1;
      case EASE_IN_OUT_CUBIC ->
          t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;

      case EASE_IN_QUART -> t * t * t * t;
      case EASE_OUT_QUART -> 1 - (--t) * t * t * t;
      case EASE_IN_OUT_QUART -> t < 0.5 ? 8 * t * t * t * t : 1 - 8 * (--t) * t * t * t;

      case EASE_IN_QUINT -> t * t * t * t * t;
      case EASE_OUT_QUINT -> {
        double f = t - 1;
        yield 1 + f * f * f * f * f;
      }
      case EASE_IN_OUT_QUINT -> {
        if (t < 0.5) {
          yield 16 * t * t * t * t * t;
        } else {
          double f = 2 * t - 2;
          yield 1 + 0.5 * f * f * f * f * f;
        }
      }

      case EASE_IN_CIRC -> 1 - Math.sqrt(1 - t * t);
      case EASE_OUT_CIRC -> Math.sqrt(1 - (t - 1) * (t - 1));
      case EASE_IN_OUT_CIRC -> t < 0.5
          ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
          : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;

      case EASE_IN_EXPO -> t == 0 ? 0 : Math.pow(2, 10 * (t - 1));
      case EASE_OUT_EXPO -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
      case EASE_IN_OUT_EXPO -> {
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        if (t < 0.5) yield Math.pow(2, 20 * t - 10) / 2;
        yield (2 - Math.pow(2, -20 * t + 10)) / 2;
      }

      case EASE_IN_SINE -> 1 - Math.cos((t * Math.PI) / 2);
      case EASE_OUT_SINE -> Math.sin((t * Math.PI) / 2);
      case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;

      case EASE_IN_ELASTIC -> {
        double c4 = (2 * Math.PI) / 3;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        yield -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4);
      }
      case EASE_OUT_ELASTIC -> {
        double c4 = (2 * Math.PI) / 3;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        yield Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
      }
      case EASE_IN_OUT_ELASTIC -> {
        double c5 = (2 * Math.PI) / 4.5;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        if (t < 0.5) {
          yield -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2;
        }
        yield (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
      }

      case EASE_IN_BACK -> {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        yield c3 * t * t * t - c1 * t * t;
      }
      case EASE_OUT_BACK -> {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
      }
      case EASE_IN_OUT_BACK -> {
        double c1 = 1.70158;
        double c2 = c1 * 1.525;
        if (t < 0.5) yield (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2;
        yield (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
      }

      case EASE_IN_BOUNCE -> 1 - apply(Type.EASE_OUT_BOUNCE, 1 - t);
      case EASE_OUT_BOUNCE -> {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) yield n1 * t * t;
        else if (t < 2 / d1) yield n1 * (t -= 1.5 / d1) * t + 0.75;
        else if (t < 2.5 / d1) yield n1 * (t -= 2.25 / d1) * t + 0.9375;
        else yield n1 * (t -= 2.625 / d1) * t + 0.984375;
      }
      case EASE_IN_OUT_BOUNCE -> t < 0.5
          ? (1 - apply(Type.EASE_OUT_BOUNCE, 1 - 2 * t)) / 2
          : (1 + apply(Type.EASE_OUT_BOUNCE, 2 * t - 1)) / 2;
      case SPRING -> {
        double[] p = (params != null && params.length >= 4) ? params : defaultParameters(Type.SPRING);
        yield spring(Math.max(0.01, p[0]), Math.max(0.0, p[1]), Math.max(0.01, p[2]), p[3], t);
      }
      case DAMPED_SPRING -> {
        double[] p = (params != null && params.length >= 4) ? params : defaultParameters(Type.DAMPED_SPRING);
        yield dampedSpring(Math.max(0.01, p[0]), Math.max(0.0, p[1]), Math.max(0.01, p[2]), p[3], t);
      }
      case HERO_POP, UI_SOFT_IN, CAMERA_GLIDE -> apply(namedCurveSpec(resolved), t);
      case CUSTOM -> {
        double[] p = (params != null && params.length >= 4) ? params : defaultParameters(Type.CUSTOM);
        yield cubicBezier(p[0], p[1], p[2], p[3], t);
      }
      case CURVE -> {
        double[] p = (params != null && params.length >= 2) ? params : defaultParameters(Type.CURVE);
        yield curve(p, t);
      }
    };
  }

  public static double applyInterpolation(Type type, Interpolation interpolation, double t) {
    return applyInterpolation(type, interpolation, null, t);
  }

  public static double applyInterpolation(
      Type type,
      Interpolation interpolation,
      double[] params,
      double t
  ) {
    t = Math.max(0, Math.min(1, t));
    Interpolation mode = interpolation != null ? interpolation : Interpolation.TWEEN;
    return switch (mode) {
      case HOLD -> t >= 1.0 ? 1.0 : 0.0;
      case STEP -> t <= 0.0 ? 0.0 : 1.0;
      case TWEEN -> apply(type, params, t);
    };
  }

  public static double applyInterpolation(EasingSpec spec, Interpolation interpolation, double t) {
    EasingSpec resolved = spec != null ? spec : EasingSpec.of(Type.LINEAR);
    return applyInterpolation(resolved.getType(), interpolation, resolved.getParameters(), t);
  }

  public static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  public static double cubicBezier(double cx1, double cy1, double cx2, double cy2, double x) {
    x = Math.max(0, Math.min(1, x));
    if (x == 0) return 0;
    if (x == 1) return 1;

    double t = x;
    for (int i = 0; i < 8; i++) {
      double bx = bezierComponent(t, cx1, cx2) - x;
      double dx = bezierComponentDeriv(t, cx1, cx2);
      if (Math.abs(bx) < 1e-7 || Math.abs(dx) < 1e-7) break;
      t -= bx / dx;
    }
    t = Math.max(0, Math.min(1, t));
    return bezierComponent(t, cy1, cy2);
  }

  public static double curve(double[] params, double x) {
    double[] anchors = curveAnchors(params);
    int count = anchors.length / 2;
    x = Math.max(0, Math.min(1, x));
    if (x == 0.0) return 0.0;
    if (x == 1.0) return 1.0;
    if (count < 2) return x;

    double[] xs = new double[count];
    double[] ys = new double[count];
    for (int i = 0; i < count; i++) {
      xs[i] = anchors[i * 2];
      ys[i] = anchors[i * 2 + 1];
    }
    double[] tangents = computeCurveTangents(xs, ys);

    int segment = 0;
    while (segment < count - 2 && x > xs[segment + 1]) {
      segment++;
    }
    double x0 = xs[segment];
    double x1 = xs[segment + 1];
    double y0 = ys[segment];
    double y1 = ys[segment + 1];
    double h = Math.max(1e-6, x1 - x0);
    double t = Math.max(0.0, Math.min(1.0, (x - x0) / h));
    double t2 = t * t;
    double t3 = t2 * t;
    double h00 = 2 * t3 - 3 * t2 + 1;
    double h10 = t3 - 2 * t2 + t;
    double h01 = -2 * t3 + 3 * t2;
    double h11 = t3 - t2;
    return h00 * y0
        + h10 * h * tangents[segment]
        + h01 * y1
        + h11 * h * tangents[segment + 1];
  }

  private static double bezierComponent(double t, double c1, double c2) {
    double u = 1 - t;
    return 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t;
  }

  private static double bezierComponentDeriv(double t, double c1, double c2) {
    double u = 1 - t;
    return 3 * u * u * c1 + 6 * u * t * (c2 - c1) + 3 * t * t * (1 - c2);
  }

  public static double smoothstep(double edge0, double edge1, double x) {
    double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
    return t * t * (3 - 2 * t);
  }

  public static boolean usesParameters(Type type) {
    return type == Type.CUSTOM
        || type == Type.CURVE
        || type == Type.SPRING
        || type == Type.DAMPED_SPRING;
  }

  public static boolean isEditableCurve(Type type) {
    return type == Type.CUSTOM || type == Type.CURVE;
  }

  public static boolean isNamedCurve(Type type) {
    return type == Type.HERO_POP || type == Type.UI_SOFT_IN || type == Type.CAMERA_GLIDE;
  }

  public static String token(Type type) {
    return (type != null ? type : Type.LINEAR).name().toLowerCase(Locale.ROOT);
  }

  public static String displayName(Type type) {
    Type resolved = type != null ? type : Type.LINEAR;
    return switch (resolved) {
      case HERO_POP -> "Hero Pop";
      case UI_SOFT_IN -> "UI Soft In";
      case CAMERA_GLIDE -> "Camera Glide";
      case DAMPED_SPRING -> "Damped Spring";
      case CUSTOM -> "Custom Cubic Bezier";
      case CURVE -> "Multi-Point Curve";
      case LINEAR -> "Linear";
      default -> titleCase(resolved.name());
    };
  }

  public static double[] defaultParameters(Type type) {
    if (type == null) return null;
    return switch (type) {
      case CUSTOM -> new double[]{0.25, 0.10, 0.25, 1.00};
      case CURVE -> new double[]{0.25, 0.25, 0.50, 0.50, 0.75, 0.75};
      case SPRING -> new double[]{180.0, 20.0, 1.0, 0.0};
      case DAMPED_SPRING -> new double[]{2.2, 0.38, 1.0, 0.0};
      default -> null;
    };
  }

  public static double[] coerceParameters(Type type, double[] params) {
    Type resolved = type != null ? type : Type.LINEAR;
    double[] defaults = defaultParameters(resolved);
    if (defaults == null) return null;

    double[] out = defaults.clone();
    if (params != null) {
      for (int i = 0; i < Math.min(out.length, params.length); i++) {
        if (Double.isFinite(params[i])) out[i] = params[i];
      }
    }
    return sanitizeParameters(resolved, out);
  }

  public static String formatSpec(EasingSpec spec) {
    EasingSpec resolved = spec != null ? spec : EasingSpec.of(Type.LINEAR);
    Type type = resolved.getType();
    double[] params = coerceParameters(type, resolved.getParameters());
    return switch (type) {
      case CUSTOM -> "cubic_bezier(" + formatNumber(params[0]) + ", " + formatNumber(params[1])
          + ", " + formatNumber(params[2]) + ", " + formatNumber(params[3]) + ")";
      case CURVE -> "curve(" + formatCurveParameters(params) + ")";
      case SPRING -> "spring(" + formatNumber(params[0]) + ", " + formatNumber(params[1])
          + ", " + formatNumber(params[2]) + ", " + formatNumber(params[3]) + ")";
      case DAMPED_SPRING -> "damped_spring(" + formatNumber(params[0]) + ", "
          + formatNumber(params[1]) + ", " + formatNumber(params[2]) + ", "
          + formatNumber(params[3]) + ")";
      default -> token(type);
    };
  }

  public static EasingSpec namedCurveSpec(Type type) {
    if (type == null) return EasingSpec.of(Type.LINEAR);
    return switch (type) {
      case HERO_POP -> EasingSpec.dampedSpring(2.8, 0.26, 1.15, 0.0);
      case UI_SOFT_IN -> EasingSpec.cubicBezier(0.18, 0.96, 0.33, 1.00);
      case CAMERA_GLIDE -> EasingSpec.dampedSpring(1.25, 1.10, 0.92, 0.0);
      default -> EasingSpec.of(type);
    };
  }

  private static double[] sanitizeParameters(Type type, double[] params) {
    if (params == null) return null;
    return switch (type) {
      case CUSTOM -> new double[]{params[0], params[1], params[2], params[3]};
      case CURVE -> sanitizeCurveParameters(params);
      case SPRING -> new double[]{
          Math.max(0.01, params[0]),
          Math.max(0.0, params[1]),
          Math.max(0.01, params[2]),
          params[3]
      };
      case DAMPED_SPRING -> new double[]{
          Math.max(0.01, params[0]),
          Math.max(0.0, params[1]),
          Math.max(0.01, params[2]),
          params[3]
      };
      default -> params;
    };
  }

  public static double[] sampledCurveParameters(EasingSpec spec, int interiorPointCount) {
    EasingSpec resolved = spec != null ? spec : EasingSpec.of(Type.LINEAR);
    int count = Math.max(1, interiorPointCount);
    double[] params = new double[count * 2];
    for (int i = 0; i < count; i++) {
      double x = (i + 1.0) / (count + 1.0);
      params[i * 2] = x;
      params[i * 2 + 1] = apply(resolved, x);
    }
    return coerceParameters(Type.CURVE, params);
  }

  public static int curvePointCount(double[] params) {
    return coerceParameters(Type.CURVE, params).length / 2;
  }

  private static String formatCurveParameters(double[] params) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < params.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append(formatNumber(params[i]));
    }
    return sb.toString();
  }

  private static double[] sanitizeCurveParameters(double[] params) {
    double[] defaults = defaultParameters(Type.CURVE);
    if (params == null || params.length < 2) {
      return defaults.clone();
    }
    int usableLength = params.length - (params.length % 2);
    if (usableLength < 2) {
      return defaults.clone();
    }

    List<double[]> points = new ArrayList<>();
    for (int i = 0; i < usableLength; i += 2) {
      double x = Double.isFinite(params[i]) ? params[i] : defaults[Math.min(i, defaults.length - 2)];
      double y = Double.isFinite(params[i + 1]) ? params[i + 1] : x;
      points.add(new double[]{x, Math.max(-0.5, Math.min(1.5, y))});
    }
    points.sort(Comparator.comparingDouble(p -> p[0]));

    double minGap = 0.001;
    double[] out = new double[points.size() * 2];
    for (int i = 0; i < points.size(); i++) {
      int remaining = points.size() - i - 1;
      double lower = i == 0 ? minGap : out[(i - 1) * 2] + minGap;
      double upper = 1.0 - minGap - remaining * minGap;
      double x = Math.max(lower, Math.min(upper, points.get(i)[0]));
      out[i * 2] = x;
      out[i * 2 + 1] = points.get(i)[1];
    }
    return out;
  }

  private static double[] curveAnchors(double[] params) {
    double[] interior = coerceParameters(Type.CURVE, params);
    int count = interior.length / 2;
    double[] anchors = new double[(count + 2) * 2];
    anchors[0] = 0.0;
    anchors[1] = 0.0;
    for (int i = 0; i < count; i++) {
      anchors[(i + 1) * 2] = interior[i * 2];
      anchors[(i + 1) * 2 + 1] = interior[i * 2 + 1];
    }
    anchors[(count + 1) * 2] = 1.0;
    anchors[(count + 1) * 2 + 1] = 1.0;
    return anchors;
  }

  private static double[] computeCurveTangents(double[] xs, double[] ys) {
    int n = xs.length;
    if (n <= 1) return new double[]{0.0};
    double[] h = new double[n - 1];
    double[] delta = new double[n - 1];
    for (int i = 0; i < n - 1; i++) {
      h[i] = Math.max(1e-6, xs[i + 1] - xs[i]);
      delta[i] = (ys[i + 1] - ys[i]) / h[i];
    }
    double[] m = new double[n];
    if (n == 2) {
      m[0] = delta[0];
      m[1] = delta[0];
      return m;
    }
    m[0] = endpointSlope(h[0], h[1], delta[0], delta[1]);
    for (int i = 1; i < n - 1; i++) {
      if (delta[i - 1] == 0.0 || delta[i] == 0.0 || Math.signum(delta[i - 1]) != Math.signum(delta[i])) {
        m[i] = 0.0;
      } else {
        double w1 = 2.0 * h[i] + h[i - 1];
        double w2 = h[i] + 2.0 * h[i - 1];
        m[i] = (w1 + w2) / ((w1 / delta[i - 1]) + (w2 / delta[i]));
      }
    }
    m[n - 1] = endpointSlope(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);
    return m;
  }

  private static double endpointSlope(double h0, double h1, double delta0, double delta1) {
    double slope = ((2.0 * h0 + h1) * delta0 - h0 * delta1) / (h0 + h1);
    if (Math.signum(slope) != Math.signum(delta0)) {
      return 0.0;
    }
    if (Math.signum(delta0) != Math.signum(delta1) && Math.abs(slope) > Math.abs(3.0 * delta0)) {
      return 3.0 * delta0;
    }
    return slope;
  }

  private static double spring(double[] params, double t) {
    if (params == null || params.length < 4) return 0.0;
    return spring(Math.max(0.01, params[0]), Math.max(0.0, params[1]), Math.max(0.01, params[2]), params[3], t);
  }

  private static double dampedSpring(double frequency, double dampingRatio, double response, double velocity, double t) {
    double omega0 = frequency * response * Math.PI * 2.0;
    double stiffness = omega0 * omega0;
    double damping = 2.0 * dampingRatio * omega0;
    return spring(stiffness, damping, 1.0, velocity, t);
  }

  private static double spring(
      double stiffness,
      double damping,
      double mass,
      double velocity,
      double t
  ) {
    if (t <= 0.0) return 0.0;
    if (t >= 1.0) return 1.0;

    double safeMass = Math.max(0.01, mass);
    double safeStiffness = Math.max(0.01, stiffness);
    double omega0 = Math.sqrt(safeStiffness / safeMass);
    double zeta = damping / (2.0 * Math.sqrt(safeStiffness * safeMass));

    if (zeta < 1.0 - 1e-6) {
      double omegaD = omega0 * Math.sqrt(1.0 - zeta * zeta);
      double exp = Math.exp(-zeta * omega0 * t);
      double cos = Math.cos(omegaD * t);
      double sin = Math.sin(omegaD * t);
      return 1.0 - exp * (cos + ((zeta * omega0 - velocity) / omegaD) * sin);
    }

    if (Math.abs(zeta - 1.0) <= 1e-6) {
      double exp = Math.exp(-omega0 * t);
      return 1.0 - (1.0 + (omega0 - velocity) * t) * exp;
    }

    double root = Math.sqrt(zeta * zeta - 1.0);
    double r1 = -omega0 * (zeta - root);
    double r2 = -omega0 * (zeta + root);
    double c1 = (-velocity - r2) / (r1 - r2);
    double c2 = 1.0 - c1;
    double y = c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
    return 1.0 - y;
  }

  private static String titleCase(String token) {
    String[] parts = token.toLowerCase(Locale.ROOT).split("_");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) sb.append(part.substring(1));
    }
    return sb.toString();
  }

  private static String formatNumber(double value) {
    if (Math.abs(value - Math.rint(value)) < 1e-6) {
      return Long.toString(Math.round(value));
    }
    return String.format(Locale.US, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }
}

package com.jvn.fx.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.vn.stage.VnStagePreset;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

final class VnStageLightingSupport {
  private VnStageLightingSupport() {}

  static Image buildLitBackground(Image source, VnStagePreset stage, double width, double height) {
    if (source == null || width <= 0 || height <= 0) return source;
    int outWidth = Math.max(1, (int) Math.round(width));
    int outHeight = Math.max(1, (int) Math.round(height));
    WritableImage out = new WritableImage(outWidth, outHeight);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = out.getPixelWriter();
    if (reader == null) return source;

    VnStagePreset.BackgroundGrade grade = stage == null ? VnStagePreset.BackgroundGrade.defaults() : stage.getBackgroundGrade();
    Color tintColor = parseColor(grade.tintColor(), Color.WHITE);
    Color overlayColor = parseColor(grade.overlayColor(), Color.BLACK);
    double tintStrength = clamp(grade.tintStrength(), 0.0, 1.0);
    double satAdjust = clamp(grade.saturation(), -1.0, 1.0);
    double conAdjust = clamp(grade.contrast(), -1.0, 1.0);
    double overlayOpacity = clamp(grade.overlayOpacity(), 0.0, 1.0);

    double sourceWidth = Math.max(1.0, source.getWidth());
    double sourceHeight = Math.max(1.0, source.getHeight());
    double scale = Math.max(width / sourceWidth, height / sourceHeight);
    double drawWidth = sourceWidth * scale;
    double drawHeight = sourceHeight * scale;
    double drawX = (width - drawWidth) * 0.5;
    double drawY = (height - drawHeight) * 0.5;
    double minDimension = Math.max(1.0, Math.min(width, height));
    List<LightRuntime> runtimeLights = prepareLights(stage, width, height, minDimension);
    List<OccluderRuntime> occluders = prepareOccluders(stage, width, height);

    for (int y = 0; y < outHeight; y++) {
      double sourceY = clamp((y - drawY) / scale, 0.0, sourceHeight - 1.0);
      int sy = (int) Math.round(sourceY);
      for (int x = 0; x < outWidth; x++) {
        double sourceX = clamp((x - drawX) / scale, 0.0, sourceWidth - 1.0);
        int sx = (int) Math.round(sourceX);
        int argb = reader.getArgb(sx, sy);
        int a = (argb >>> 24) & 0xFF;
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;

        double lum = linearLuminance(r, g, b);
        r = lum + (r - lum) * (1.0 + satAdjust);
        g = lum + (g - lum) * (1.0 + satAdjust);
        b = lum + (b - lum) * (1.0 + satAdjust);
        r = (r - 0.5) * (1.0 + conAdjust) + 0.5;
        g = (g - 0.5) * (1.0 + conAdjust) + 0.5;
        b = (b - 0.5) * (1.0 + conAdjust) + 0.5;
        r = r * (1.0 - tintStrength) + tintColor.getRed() * tintStrength;
        g = g * (1.0 - tintStrength) + tintColor.getGreen() * tintStrength;
        b = b * (1.0 - tintStrength) + tintColor.getBlue() * tintStrength;

        if (overlayOpacity > 1e-6) {
          r = applyBlend(r, overlayColor.getRed(), overlayOpacity, 4);
          g = applyBlend(g, overlayColor.getGreen(), overlayOpacity, 4);
          b = applyBlend(b, overlayColor.getBlue(), overlayOpacity, 4);
        }

        for (LightRuntime light : runtimeLights) {
          if (light.layer != VnStagePreset.LightLayer.BACKGROUND) continue;
          double weight = lightWeight(light, x, y);
          if (weight <= 0.0) continue;
          double occlusion = occlusion(light.sourceX, light.sourceY, x, y, occluders);
          weight *= (1.0 - occlusion);
          if (weight <= 0.0) continue;
          double influence = weight * light.intensity;
          double albedo = linearLuminance(r, g, b);
          r = applySceneLightChannel(r, light.color.getRed(), influence, albedo, false);
          g = applySceneLightChannel(g, light.color.getGreen(), influence, albedo, false);
          b = applySceneLightChannel(b, light.color.getBlue(), influence, albedo, false);
          double tintWeight = clamp(influence * 0.14, 0.0, 0.32);
          r = applyBlend(r, light.color.getRed(), tintWeight, 4);
          g = applyBlend(g, light.color.getGreen(), tintWeight, 4);
          b = applyBlend(b, light.color.getBlue(), tintWeight, 4);
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        writer.setArgb(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
      }
    }
    return out;
  }

  static Image buildLitCharacter(
      Image source,
      String spriteTag,
      double drawX,
      double drawY,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage
  ) {
    if (source == null || stage == null || drawWidth <= 0 || drawHeight <= 0) return source;
    int outWidth = Math.max(1, (int) Math.round(drawWidth));
    int outHeight = Math.max(1, (int) Math.round(drawHeight));
    WritableImage out = new WritableImage(outWidth, outHeight);
    PixelReader reader = source.getPixelReader();
    PixelWriter writer = out.getPixelWriter();
    if (reader == null) return source;

    double sourceWidth = Math.max(1.0, source.getWidth());
    double sourceHeight = Math.max(1.0, source.getHeight());
    double minDimension = Math.max(1.0, Math.min(canvasWidth, canvasHeight));
    List<LightRuntime> runtimeLights = prepareLights(stage, canvasWidth, canvasHeight, minDimension);
    List<OccluderRuntime> occluders = prepareOccluders(stage, canvasWidth, canvasHeight);
    boolean hasSolo = stage.hasSoloLights();
    boolean subjectMatched = stage.getSubjectTag() == null
        || stage.getSubjectTag().isBlank()
        || stage.getSubjectTag().equals(spriteTag);
    List<ResponseRuntime> responseZones = prepareResponseZones(stage, sourceWidth, sourceHeight);

    for (int y = 0; y < outHeight; y++) {
      double srcYf = clamp((y / Math.max(1.0, outHeight - 1.0)) * (sourceHeight - 1.0), 0.0, sourceHeight - 1.0);
      int sy = (int) Math.round(srcYf);
      double normalizedY = outHeight <= 1 ? 0.5 : (y / (double) (outHeight - 1));
      double sceneY = drawY + normalizedY * drawHeight;
      for (int x = 0; x < outWidth; x++) {
        double srcXf = clamp((x / Math.max(1.0, outWidth - 1.0)) * (sourceWidth - 1.0), 0.0, sourceWidth - 1.0);
        int sx = (int) Math.round(srcXf);
        int argb = reader.getArgb(sx, sy);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
          writer.setArgb(x, y, 0);
          continue;
        }
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double normalizedX = outWidth <= 1 ? 0.5 : (x / (double) (outWidth - 1));
        double sceneX = drawX + normalizedX * drawWidth;

        ResponseInfluence response = subjectMatched
            ? resolveResponse(normalizedX, normalizedY, responseZones, sourceWidth / Math.max(1.0, sourceHeight))
            : ResponseInfluence.defaults();

        for (LightRuntime light : runtimeLights) {
          if (light.muted || (hasSolo && !light.solo)) continue;
          double weight = lightWeight(light, sceneX, sceneY);
          if (weight <= 0.0) continue;
          double occlusion = occlusion(light.sourceX, light.sourceY, sceneX, sceneY, occluders);
          weight *= (1.0 - occlusion);
          if (weight <= 0.0) continue;

          if (light.layer == VnStagePreset.LightLayer.CHARACTER) {
            double directional = sceneLightDirectionalBias(
                sceneX, sceneY, light.targetX, light.targetY, light.sourceX, light.sourceY);
            double influence = weight * light.intensity * directional
                * response.responseScale * response.directFactor * response.depthFactor;
            double albedo = linearLuminance(r, g, b);
            r = applySceneLightChannel(r, light.color.getRed(), influence, albedo, false);
            g = applySceneLightChannel(g, light.color.getGreen(), influence, albedo, false);
            b = applySceneLightChannel(b, light.color.getBlue(), influence, albedo, false);
            double tintWeight = clamp(influence * response.tintFactor, 0.0, 0.35);
            r = applyBlend(r, light.color.getRed(), tintWeight, 4);
            g = applyBlend(g, light.color.getGreen(), tintWeight, 4);
            b = applyBlend(b, light.color.getBlue(), tintWeight, 4);
          }

          double silhouetteStrength = light.silhouette * response.rimFactor;
          if (silhouetteStrength <= 1e-6) continue;
          double edgeFactor = alphaEdgeWeight(reader, sx, sy, (int) Math.round(sourceWidth), (int) Math.round(sourceHeight));
          if (edgeFactor <= 0.0) continue;
          double[] edgeNormal = alphaEdgeNormal(reader, sx, sy, (int) Math.round(sourceWidth), (int) Math.round(sourceHeight));
          double lightDirX = light.sourceX - sceneX;
          double lightDirY = light.sourceY - sceneY;
          double lightDirLength = Math.hypot(lightDirX, lightDirY);
          double facing = 1.0;
          if (lightDirLength > 1e-6 && (Math.abs(edgeNormal[0]) > 1e-6 || Math.abs(edgeNormal[1]) > 1e-6)) {
            facing = clamp(
                edgeNormal[0] * (lightDirX / lightDirLength) + edgeNormal[1] * (lightDirY / lightDirLength),
                0.0,
                1.0
            );
          }
          double rimWeight = weight * light.intensity * silhouetteStrength * edgeFactor
              * (0.28 + 0.72 * facing) * response.depthFactor;
          double albedo = linearLuminance(r, g, b);
          r = applySceneLightChannel(r, light.color.getRed(), rimWeight, albedo, true);
          g = applySceneLightChannel(g, light.color.getGreen(), rimWeight, albedo, true);
          b = applySceneLightChannel(b, light.color.getBlue(), rimWeight, albedo, true);
        }

        int rr = (int) Math.round(clamp(r, 0.0, 1.0) * 255.0);
        int gg = (int) Math.round(clamp(g, 0.0, 1.0) * 255.0);
        int bb = (int) Math.round(clamp(b, 0.0, 1.0) * 255.0);
        writer.setArgb(x, y, (a << 24) | (rr << 16) | (gg << 8) | bb);
      }
    }
    return out;
  }

  private static List<LightRuntime> prepareLights(VnStagePreset stage, double canvasWidth, double canvasHeight, double minDimension) {
    List<LightRuntime> lights = new ArrayList<>();
    if (stage == null) return lights;
    for (VnStagePreset.Light light : stage.getLights()) {
      if (light == null) continue;
      List<double[]> polygon = null;
      if (light.polygon() != null && !light.polygon().isEmpty()) {
        polygon = new ArrayList<>();
        for (VnStagePreset.Point point : light.polygon()) {
          polygon.add(new double[]{point.x() * canvasWidth, point.y() * canvasHeight});
        }
      }
      lights.add(new LightRuntime(
          light.type(),
          light.layer(),
          light.sceneX() * canvasWidth,
          light.sceneY() * canvasHeight,
          light.sourceX() * canvasWidth,
          light.sourceY() * canvasHeight,
          light.sceneX() * canvasWidth,
          light.sceneY() * canvasHeight,
          parseColor(light.color(), Color.web("#ffd7a8")),
          clamp(light.intensity(), 0.0, 1.0),
          Math.max(12.0, light.radius() * minDimension),
          clamp(light.softness(), 0.0, 1.0),
          clamp(light.silhouette(), 0.0, 1.0),
          light.muted(),
          light.solo(),
          polygon
      ));
    }
    return lights;
  }

  private static List<OccluderRuntime> prepareOccluders(VnStagePreset stage, double canvasWidth, double canvasHeight) {
    List<OccluderRuntime> occluders = new ArrayList<>();
    if (stage == null) return occluders;
    for (VnStagePreset.Occluder occluder : stage.getOccluders()) {
      if (occluder == null || !occluder.enabled() || occluder.polygon() == null || occluder.polygon().size() < 3) {
        continue;
      }
      List<double[]> polygon = new ArrayList<>();
      for (VnStagePreset.Point point : occluder.polygon()) {
        polygon.add(new double[]{point.x() * canvasWidth, point.y() * canvasHeight});
      }
      occluders.add(new OccluderRuntime(
          clamp(occluder.opacity(), 0.0, 1.0),
          clamp(occluder.softness(), 0.0, 1.0),
          polygon
      ));
    }
    return occluders;
  }

  private static List<ResponseRuntime> prepareResponseZones(VnStagePreset stage, double sourceWidth, double sourceHeight) {
    List<ResponseRuntime> zones = new ArrayList<>();
    if (stage == null) return zones;
    for (VnStagePreset.ResponseZone zone : stage.getResponseZones()) {
      if (zone == null) continue;
      List<double[]> polygon = null;
      if (zone.polygon() != null && !zone.polygon().isEmpty()) {
        polygon = new ArrayList<>();
        for (VnStagePreset.Point point : zone.polygon()) {
          polygon.add(new double[]{point.x(), point.y()});
        }
      }
      zones.add(new ResponseRuntime(
          zone.boundsX(),
          zone.boundsY(),
          zone.boundsW(),
          zone.boundsH(),
          zone.rotationDeg(),
          zone.surfaceClass(),
          zone.depthBias(),
          zone.responseScale(),
          polygon
      ));
    }
    return zones;
  }

  static Color parseColor(String raw, Color fallback) {
    try {
      if (raw == null || raw.isBlank()) return fallback;
      return Color.web(raw.trim());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return fallback;
    }
  }

  static double backgroundLightAlpha(VnStagePreset.Light light) {
    double intensity = light == null ? 0.0 : clamp(light.intensity(), 0.0, 1.0);
    return clamp(intensity * 0.18, 0.02, 0.24);
  }

  static double foregroundLightAlpha(VnStagePreset.Light light) {
    double intensity = light == null ? 0.0 : clamp(light.intensity(), 0.0, 1.0);
    return clamp(intensity * 0.16, 0.02, 0.22);
  }

  static double lightWeight(LightRuntime light, double x, double y) {
    return switch (light.type) {
      case POLYGON -> scenePolygonLightWeightPx(x, y, light.polygon, light.radius, light.softness);
      case CONE -> coneLightWeight(light, x, y);
      case STRIP, WINDOW -> stripLightWeight(light, x, y);
      case BOUNCE -> sceneLightWeightPx(x, y, light.targetX, light.targetY, light.radius, clamp(light.softness * 1.15, 0.0, 1.0));
      default -> sceneLightWeightPx(x, y, light.targetX, light.targetY, light.radius, light.softness);
    };
  }

  private static double coneLightWeight(LightRuntime light, double x, double y) {
    double dx = x - light.sourceX;
    double dy = y - light.sourceY;
    double distance = Math.hypot(dx, dy);
    if (distance <= 1e-6) return 0.0;
    double axisX = light.targetX - light.sourceX;
    double axisY = light.targetY - light.sourceY;
    double axisLength = Math.hypot(axisX, axisY);
    if (axisLength <= 1e-6) return 0.0;
    double dot = clamp((dx / distance) * (axisX / axisLength) + (dy / distance) * (axisY / axisLength), -1.0, 1.0);
    double angle = Math.acos(dot);
    double maxAngle = Math.toRadians(12.0 + light.radius * 0.14);
    if (angle >= maxAngle) return 0.0;
    double angular = 1.0 - (angle / maxAngle);
    double radial = clamp(1.0 - (distance / Math.max(axisLength, light.radius * 1.6)), 0.0, 1.0);
    return Math.pow(angular, 0.55 + (1.0 - light.softness) * 1.8) * radial;
  }

  private static double stripLightWeight(LightRuntime light, double x, double y) {
    double axisX = light.targetX - light.sourceX;
    double axisY = light.targetY - light.sourceY;
    double axisLength = Math.hypot(axisX, axisY);
    if (axisLength <= 1e-6) return 0.0;
    double t = clamp(((x - light.sourceX) * axisX + (y - light.sourceY) * axisY) / (axisLength * axisLength), 0.0, 1.0);
    double closestX = light.sourceX + axisX * t;
    double closestY = light.sourceY + axisY * t;
    double width = Math.max(10.0, light.radius * 0.42);
    double distance = Math.hypot(x - closestX, y - closestY);
    if (distance >= width) return 0.0;
    double lateral = 1.0 - (distance / width);
    double along = Math.sin(t * Math.PI);
    return Math.pow(lateral, 0.65 + (1.0 - light.softness) * 2.0) * clamp(along + 0.18, 0.0, 1.0);
  }

  private static double occlusion(double sourceX, double sourceY, double targetX, double targetY, List<OccluderRuntime> occluders) {
    if (occluders == null || occluders.isEmpty()) return 0.0;
    double blocked = 0.0;
    for (OccluderRuntime occluder : occluders) {
      if (occluder == null || occluder.polygon == null || occluder.polygon.size() < 3) continue;
      if (!segmentIntersectsPolygon(sourceX, sourceY, targetX, targetY, occluder.polygon)) continue;
      blocked = Math.max(blocked, occluder.opacity * (0.78 + 0.22 * (1.0 - occluder.softness)));
    }
    return blocked;
  }

  private static boolean segmentIntersectsPolygon(double ax, double ay, double bx, double by, List<double[]> polygon) {
    int size = polygon == null ? 0 : polygon.size();
    if (size < 3) return false;
    for (int i = 0, j = size - 1; i < size; j = i++) {
      double[] p1 = polygon.get(j);
      double[] p2 = polygon.get(i);
      if (segmentsIntersect(ax, ay, bx, by, p1[0], p1[1], p2[0], p2[1])) return true;
    }
    return false;
  }

  private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                           double cx, double cy, double dx, double dy) {
    double d1 = direction(cx, cy, dx, dy, ax, ay);
    double d2 = direction(cx, cy, dx, dy, bx, by);
    double d3 = direction(ax, ay, bx, by, cx, cy);
    double d4 = direction(ax, ay, bx, by, dx, dy);
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
        && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true;
    }
    return d1 == 0 && onSegment(cx, cy, dx, dy, ax, ay)
        || d2 == 0 && onSegment(cx, cy, dx, dy, bx, by)
        || d3 == 0 && onSegment(ax, ay, bx, by, cx, cy)
        || d4 == 0 && onSegment(ax, ay, bx, by, dx, dy);
  }

  private static double direction(double ax, double ay, double bx, double by, double px, double py) {
    return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
  }

  private static boolean onSegment(double ax, double ay, double bx, double by, double px, double py) {
    return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
        && py >= Math.min(ay, by) && py <= Math.max(ay, by);
  }

  private static ResponseInfluence resolveResponse(double nx, double ny, List<ResponseRuntime> zones, double aspect) {
    ResponseInfluence influence = ResponseInfluence.defaults();
    for (ResponseRuntime zone : zones) {
      double weight = zone.polygon != null && zone.polygon.size() >= 3
          ? polyZoneWeight(nx, ny, zone.polygon, 0.12)
          : zoneWeight(nx, ny, zone.boundsX, zone.boundsY, zone.boundsW, zone.boundsH, 0.14, Math.toRadians(zone.rotationDeg), aspect);
      if (weight <= 0.0) continue;
      ResponseInfluence candidate = influenceFor(zone.surfaceClass, zone.depthBias, zone.responseScale);
      influence = influence.mix(candidate, weight);
    }
    return influence;
  }

  private static ResponseInfluence influenceFor(VnStagePreset.SurfaceClass surfaceClass, double depthBias, double responseScale) {
    double scale = clamp(responseScale, 0.1, 2.5);
    return switch (surfaceClass) {
      case SKIN -> new ResponseInfluence(1.05 * scale, 0.92, 0.18, 0.92 + depthBias * 0.12, 0.88 + depthBias * 0.16);
      case HAIR -> new ResponseInfluence(0.96 * scale, 1.12, 0.14, 0.96 + depthBias * 0.10, 1.08 + depthBias * 0.20);
      case FABRIC -> new ResponseInfluence(0.92 * scale, 0.86, 0.10, 0.90 + depthBias * 0.08, 0.84 + depthBias * 0.12);
      case METAL -> new ResponseInfluence(1.18 * scale, 1.34, 0.20, 1.06 + depthBias * 0.14, 1.28 + depthBias * 0.24);
      case GLASS -> new ResponseInfluence(0.86 * scale, 1.22, 0.12, 0.88 + depthBias * 0.10, 1.10 + depthBias * 0.18);
      default -> new ResponseInfluence(scale, 1.0, 0.12, 1.0 + depthBias * 0.10, 1.0 + depthBias * 0.14);
    };
  }

  static double sceneLightWeightPx(double px, double py, double lightX, double lightY, double radiusPx, double softness) {
    double safeRadius = Math.max(1.0, radiusPx);
    double dx = px - lightX;
    double dy = py - lightY;
    double distance = Math.sqrt(dx * dx + dy * dy);
    if (distance >= safeRadius) return 0.0;
    double normalized = clamp(1.0 - (distance / safeRadius), 0.0, 1.0);
    double exponent = 0.65 + (1.0 - clamp(softness, 0.0, 1.0)) * 2.15;
    return Math.pow(normalized, exponent);
  }

  static double scenePolygonLightWeightPx(double px, double py, List<double[]> polygonPx, double featherPx, double softness) {
    if (polygonPx == null || polygonPx.size() < 3) return 0.0;
    double safeFeather = Math.max(1.0, featherPx);
    boolean inside = false;
    int n = polygonPx.size();
    double minDist = Double.MAX_VALUE;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double[] a = polygonPx.get(j);
      double[] b = polygonPx.get(i);
      if ((b[1] > py) != (a[1] > py)
          && px < (a[0] - b[0]) * (py - b[1]) / Math.max(1e-9, (a[1] - b[1])) + b[0]) {
        inside = !inside;
      }
      double dist = pointToSegmentDist(px, py, a[0], a[1], b[0], b[1]);
      if (dist < minDist) minDist = dist;
    }
    double exponent = 0.65 + (1.0 - clamp(softness, 0.0, 1.0)) * 2.15;
    if (inside) {
      double normalized = clamp(minDist / safeFeather, 0.0, 1.0);
      return 0.72 + (0.28 * (1.0 - Math.pow(1.0 - normalized, exponent)));
    }
    if (minDist >= safeFeather) return 0.0;
    double normalized = clamp(1.0 - (minDist / safeFeather), 0.0, 1.0);
    return Math.pow(normalized, exponent);
  }

  static double sceneLightDirectionalBias(double sceneX,
                                          double sceneY,
                                          double targetX,
                                          double targetY,
                                          double sourceX,
                                          double sourceY) {
    double towardSourceX = sourceX - targetX;
    double towardSourceY = sourceY - targetY;
    double towardSourceLength = Math.hypot(towardSourceX, towardSourceY);
    double pixelOffsetX = sceneX - targetX;
    double pixelOffsetY = sceneY - targetY;
    double pixelOffsetLength = Math.hypot(pixelOffsetX, pixelOffsetY);
    if (towardSourceLength < 1e-6 || pixelOffsetLength < 1e-6) return 1.0;
    double facing = clamp(
        (pixelOffsetX / pixelOffsetLength) * (towardSourceX / towardSourceLength)
            + (pixelOffsetY / pixelOffsetLength) * (towardSourceY / towardSourceLength),
        -1.0,
        1.0
    );
    return 0.78 + 0.22 * Math.max(0.0, facing);
  }

  static double applySceneLightChannel(double base, double light, double influence, double albedo, boolean rim) {
    double safeBase = clamp(base, 0.0, 1.0);
    double safeLight = clamp(light, 0.0, 1.0);
    double strength = Math.max(0.0, influence);
    if (strength <= 1e-6) return safeBase;
    double safeAlbedo = clamp(albedo, 0.0, 1.0);
    double exposure = strength * (rim ? 1.85 : 1.35) * (0.55 + 0.45 * Math.sqrt(Math.max(0.02, safeAlbedo)));
    double litLinear = 1.0 - Math.exp(-(srgbToLinear(safeBase) + srgbToLinear(safeLight) * exposure));
    double lit = linearToSrgb(clamp(litLinear, 0.0, 1.0));
    double blendWeight = clamp(strength * (rim ? 0.92 : 0.78), 0.0, 1.0);
    return clamp(safeBase * (1.0 - blendWeight) + lit * blendWeight, 0.0, 1.0);
  }

  static double applyBlend(double base, double zone, double weight, int mode) {
    double b = clamp(base, 0.0, 1.0);
    double s = clamp(zone, 0.0, 1.0);
    double w = clamp(weight, 0.0, 1.0);
    if (w <= 0.0) return b;
    if (mode == 0) {
      return b * (1.0 - w) + s * w;
    }
    double bLin = srgbToLinear(b);
    double sLin = srgbToLinear(s);
    double blended = switch (mode) {
      case 4 -> sLin <= 0.5
          ? bLin - (1.0 - 2.0 * sLin) * bLin * (1.0 - bLin)
          : bLin + (2.0 * sLin - 1.0) * (softLightCurve(bLin) - bLin);
      default -> sLin;
    };
    double blendedSrgb = linearToSrgb(clamp(blended, 0.0, 1.0));
    return b * (1.0 - w) + blendedSrgb * w;
  }

  static double alphaEdgeWeight(PixelReader reader, int x, int y, int width, int height) {
    if (reader == null || width <= 0 || height <= 0) return 0.0;
    int center = alphaAt(reader, x, y, width, height);
    if (center == 0) return 0.0;
    int left = alphaAt(reader, x - 1, y, width, height);
    int right = alphaAt(reader, x + 1, y, width, height);
    int up = alphaAt(reader, x, y - 1, width, height);
    int down = alphaAt(reader, x, y + 1, width, height);
    double diff = Math.max(
        Math.max(Math.abs(center - left), Math.abs(center - right)),
        Math.max(Math.abs(center - up), Math.abs(center - down))
    ) / 255.0;
    if (diff > 0.0) return clamp(diff, 0.0, 1.0);
    double neighborOpacity = (left + right + up + down) / (4.0 * 255.0);
    return clamp(1.0 - neighborOpacity, 0.0, 1.0);
  }

  static double[] alphaEdgeNormal(PixelReader reader, int x, int y, int width, int height) {
    double left = alphaAt(reader, x - 1, y, width, height);
    double right = alphaAt(reader, x + 1, y, width, height);
    double up = alphaAt(reader, x, y - 1, width, height);
    double down = alphaAt(reader, x, y + 1, width, height);
    double normalX = left - right;
    double normalY = up - down;
    double length = Math.hypot(normalX, normalY);
    if (length <= 1e-6) return new double[]{0.0, 0.0};
    return new double[]{normalX / length, normalY / length};
  }

  private static int alphaAt(PixelReader reader, int x, int y, int width, int height) {
    int safeX = Math.max(0, Math.min(width - 1, x));
    int safeY = Math.max(0, Math.min(height - 1, y));
    return (reader.getArgb(safeX, safeY) >>> 24) & 0xFF;
  }

  private static double zoneWeight(double nx, double ny, double zx, double zy, double zw, double zh, double feather, double rotRad, double imgAspect) {
    if (zw <= 0 || zh <= 0) return 0.0;
    if (rotRad != 0.0) {
      double cx = zx + zw * 0.5;
      double cy = zy + zh * 0.5;
      double cosR = Math.cos(-rotRad);
      double sinR = Math.sin(-rotRad);
      double dx = nx - cx;
      double dy = (ny - cy) * imgAspect;
      nx = cx + dx * cosR - dy * sinR;
      ny = cy + (dx * sinR + dy * cosR) / imgAspect;
    }
    double x1 = zx;
    double y1 = zy;
    double x2 = zx + zw;
    double y2 = zy + zh;
    if (nx < x1 || nx > x2 || ny < y1 || ny > y2) {
      if (feather <= 0.0) return 0.0;
      double dx = nx < x1 ? x1 - nx : (nx > x2 ? nx - x2 : 0.0);
      double dy = ny < y1 ? y1 - ny : (ny > y2 ? ny - y2 : 0.0);
      double dist = Math.sqrt(dx * dx + dy * dy);
      if (dist >= feather) return 0.0;
      return 1.0 - dist / feather;
    }
    return 1.0;
  }

  private static double polyZoneWeight(double px, double py, List<double[]> poly, double feather) {
    int n = poly == null ? 0 : poly.size();
    if (n < 3) return 0.0;
    boolean inside = false;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double yi = poly.get(i)[1];
      double yj = poly.get(j)[1];
      double xi = poly.get(i)[0];
      double xj = poly.get(j)[0];
      if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
        inside = !inside;
      }
    }
    if (inside) return 1.0;
    if (feather <= 0.0) return 0.0;
    double minDist = Double.MAX_VALUE;
    for (int i = 0, j = n - 1; i < n; j = i++) {
      double d = pointToSegmentDist(px, py, poly.get(j)[0], poly.get(j)[1], poly.get(i)[0], poly.get(i)[1]);
      if (d < minDist) minDist = d;
    }
    if (minDist >= feather) return 0.0;
    return 1.0 - minDist / feather;
  }

  private static double pointToSegmentDist(double px, double py, double ax, double ay, double bx, double by) {
    double dx = bx - ax;
    double dy = by - ay;
    double lenSq = dx * dx + dy * dy;
    if (lenSq < 1e-12) return Math.hypot(px - ax, py - ay);
    double t = clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0.0, 1.0);
    double cx = ax + t * dx;
    double cy = ay + t * dy;
    return Math.hypot(px - cx, py - cy);
  }

  static double linearLuminance(double r, double g, double b) {
    return clamp(0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b), 0.0, 1.0);
  }

  private static double softLightCurve(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.25) {
      return ((16.0 * v - 12.0) * v + 4.0) * v;
    }
    return Math.sqrt(v);
  }

  static double srgbToLinear(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.04045) return v / 12.92;
    return Math.pow((v + 0.055) / 1.055, 2.4);
  }

  static double linearToSrgb(double value) {
    double v = clamp(value, 0.0, 1.0);
    if (v <= 0.0031308) return v * 12.92;
    return 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
  }

  static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private record LightRuntime(
      VnStagePreset.LightType type,
      VnStagePreset.LightLayer layer,
      double targetX,
      double targetY,
      double sourceX,
      double sourceY,
      double sceneX,
      double sceneY,
      Color color,
      double intensity,
      double radius,
      double softness,
      double silhouette,
      boolean muted,
      boolean solo,
      List<double[]> polygon
  ) {}

  private record ResponseRuntime(
      double boundsX,
      double boundsY,
      double boundsW,
      double boundsH,
      double rotationDeg,
      VnStagePreset.SurfaceClass surfaceClass,
      double depthBias,
      double responseScale,
      List<double[]> polygon
  ) {}

  private record OccluderRuntime(
      double opacity,
      double softness,
      List<double[]> polygon
  ) {}

  private record ResponseInfluence(
      double responseScale,
      double rimFactor,
      double tintFactor,
      double directFactor,
      double depthFactor
  ) {
    static ResponseInfluence defaults() {
      return new ResponseInfluence(1.0, 1.0, 0.12, 1.0, 1.0);
    }

    ResponseInfluence mix(ResponseInfluence other, double weight) {
      double w = clamp(weight, 0.0, 1.0);
      return new ResponseInfluence(
          lerp(responseScale, other.responseScale, w),
          lerp(rimFactor, other.rimFactor, w),
          lerp(tintFactor, other.tintFactor, w),
          lerp(directFactor, other.directFactor, w),
          lerp(depthFactor, other.depthFactor, w)
      );
    }
  }

  private static double lerp(double a, double b, double t) {
    return a * (1.0 - t) + b * t;
  }
}

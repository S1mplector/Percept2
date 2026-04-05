package com.jvn.core.vn.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runtime-capable stage lighting preset exported from Scene Lighting Studio.
 */
public final class VnStagePreset {
  private final String id;
  private final String sourcePath;
  private final String backgroundTag;
  private final String subjectTag;
  private final BackgroundGrade backgroundGrade;
  private final List<Light> lights;
  private final List<Occluder> occluders;
  private final List<ResponseZone> responseZones;
  private final String cacheToken;

  public VnStagePreset(
      String id,
      String sourcePath,
      String backgroundTag,
      String subjectTag,
      BackgroundGrade backgroundGrade,
      List<Light> lights,
      List<Occluder> occluders,
      List<ResponseZone> responseZones
  ) {
    this.id = id == null ? "" : id.trim();
    this.sourcePath = sourcePath == null ? "" : sourcePath.trim();
    this.backgroundTag = backgroundTag == null ? "" : backgroundTag.trim();
    this.subjectTag = subjectTag == null ? "" : subjectTag.trim();
    this.backgroundGrade = backgroundGrade == null ? BackgroundGrade.defaults() : backgroundGrade;
    this.lights = lights == null ? List.of() : List.copyOf(new ArrayList<>(lights));
    this.occluders = occluders == null ? List.of() : List.copyOf(new ArrayList<>(occluders));
    this.responseZones = responseZones == null ? List.of() : List.copyOf(new ArrayList<>(responseZones));
    this.cacheToken = buildCacheToken();
  }

  public String getId() { return id; }
  public String getSourcePath() { return sourcePath; }
  public String getBackgroundTag() { return backgroundTag; }
  public String getSubjectTag() { return subjectTag; }
  public BackgroundGrade getBackgroundGrade() { return backgroundGrade; }
  public List<Light> getLights() { return lights; }
  public List<Occluder> getOccluders() { return occluders; }
  public List<ResponseZone> getResponseZones() { return responseZones; }
  public String getCacheToken() { return cacheToken; }

  public boolean hasSoloLights() {
    for (Light light : lights) {
      if (light != null && light.solo()) return true;
    }
    return false;
  }

  private String buildCacheToken() {
    StringBuilder sb = new StringBuilder();
    sb.append(id).append("|").append(sourcePath).append("|").append(backgroundTag).append("|").append(subjectTag);
    sb.append("|bg=").append(backgroundGrade.tintColor()).append(",")
        .append(backgroundGrade.tintStrength()).append(",")
        .append(backgroundGrade.saturation()).append(",")
        .append(backgroundGrade.contrast()).append(",")
        .append(backgroundGrade.overlayColor()).append(",")
        .append(backgroundGrade.overlayOpacity());
    for (int i = 0; i < lights.size(); i++) {
      Light light = lights.get(i);
      if (light == null) continue;
      sb.append("|l").append(i).append(":")
          .append(light.name()).append(",")
          .append(light.type().persisted()).append(",")
          .append(light.layer().persisted()).append(",")
          .append(light.sceneX()).append(",")
          .append(light.sceneY()).append(",")
          .append(light.sourceX()).append(",")
          .append(light.sourceY()).append(",")
          .append(light.color()).append(",")
          .append(light.intensity()).append(",")
          .append(light.radius()).append(",")
          .append(light.softness()).append(",")
          .append(light.silhouette()).append(",")
          .append(light.muted()).append(",")
          .append(light.locked()).append(",")
          .append(light.solo()).append(",")
          .append(light.group());
      if (light.polygon() != null && !light.polygon().isEmpty()) {
        for (Point point : light.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    for (int i = 0; i < occluders.size(); i++) {
      Occluder occluder = occluders.get(i);
      if (occluder == null) continue;
      sb.append("|o").append(i).append(":")
          .append(occluder.name()).append(",")
          .append(occluder.opacity()).append(",")
          .append(occluder.softness()).append(",")
          .append(occluder.enabled());
      if (occluder.polygon() != null && !occluder.polygon().isEmpty()) {
        for (Point point : occluder.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    for (int i = 0; i < responseZones.size(); i++) {
      ResponseZone zone = responseZones.get(i);
      if (zone == null) continue;
      sb.append("|z").append(i).append(":")
          .append(zone.name()).append(",")
          .append(zone.surfaceClass().persisted()).append(",")
          .append(zone.depthBias()).append(",")
          .append(zone.responseScale()).append(",")
          .append(zone.boundsX()).append(",")
          .append(zone.boundsY()).append(",")
          .append(zone.boundsW()).append(",")
          .append(zone.boundsH()).append(",")
          .append(zone.rotationDeg());
      if (zone.polygon() != null && !zone.polygon().isEmpty()) {
        for (Point point : zone.polygon()) {
          sb.append(";").append(point.x()).append(",").append(point.y());
        }
      }
    }
    return sb.toString();
  }

  public record BackgroundGrade(
      String tintColor,
      double tintStrength,
      double saturation,
      double contrast,
      String overlayColor,
      double overlayOpacity
  ) {
    public static BackgroundGrade defaults() {
      return new BackgroundGrade("#FFFFFF", 0.0, 0.0, 0.0, "#000000", 0.0);
    }
  }

  public record Point(double x, double y) {}

  public record Light(
      String name,
      LightType type,
      LightLayer layer,
      double sceneX,
      double sceneY,
      double sourceX,
      double sourceY,
      String color,
      double intensity,
      double radius,
      double softness,
      double silhouette,
      boolean muted,
      boolean locked,
      boolean solo,
      String group,
      List<Point> polygon
  ) {}

  public record Occluder(
      String name,
      double opacity,
      double softness,
      boolean enabled,
      List<Point> polygon
  ) {}

  public record ResponseZone(
      String name,
      double boundsX,
      double boundsY,
      double boundsW,
      double boundsH,
      double rotationDeg,
      SurfaceClass surfaceClass,
      double depthBias,
      double responseScale,
      List<Point> polygon
  ) {
    public boolean isPolygon() {
      return polygon != null && polygon.size() >= 3;
    }
  }

  public enum LightType {
    RADIAL("Radial", "radial"),
    POLYGON("Polygon", "polygon"),
    CONE("Cone", "cone"),
    STRIP("Strip", "strip"),
    WINDOW("Window", "window"),
    BOUNCE("Bounce", "bounce");

    private final String label;
    private final String persisted;

    LightType(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static LightType from(String raw) {
      if (raw == null || raw.isBlank()) return RADIAL;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (LightType value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return RADIAL;
    }
  }

  public enum LightLayer {
    BACKGROUND("Behind Character", "background"),
    CHARACTER("On Character", "character"),
    FOREGROUND("In Front", "foreground");

    private final String label;
    private final String persisted;

    LightLayer(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static LightLayer from(String raw) {
      if (raw == null || raw.isBlank()) return CHARACTER;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (LightLayer value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return CHARACTER;
    }
  }

  public enum SurfaceClass {
    DEFAULT("Default", "default"),
    SKIN("Skin", "skin"),
    HAIR("Hair", "hair"),
    FABRIC("Fabric", "fabric"),
    METAL("Metal", "metal"),
    GLASS("Glass", "glass");

    private final String label;
    private final String persisted;

    SurfaceClass(String label, String persisted) {
      this.label = label;
      this.persisted = persisted;
    }

    public String persisted() { return persisted; }

    public static SurfaceClass from(String raw) {
      if (raw == null || raw.isBlank()) return DEFAULT;
      String normalized = raw.trim().toLowerCase(Locale.ROOT);
      for (SurfaceClass value : values()) {
        if (value.persisted.equals(normalized) || value.label.toLowerCase(Locale.ROOT).equals(normalized)) {
          return value;
        }
      }
      return DEFAULT;
    }
  }
}

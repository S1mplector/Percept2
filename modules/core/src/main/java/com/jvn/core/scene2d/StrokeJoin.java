package com.jvn.core.scene2d;

public enum StrokeJoin {
  MITER("miter"), ROUND("round"), BEVEL("bevel");

  private final String apiName;
  StrokeJoin(String apiName) { this.apiName = apiName; }
  public String apiName() { return apiName; }
}

package com.jvn.core.scene2d;

public enum StrokeCap {
  BUTT("butt"), ROUND("round"), SQUARE("square");

  private final String apiName;
  StrokeCap(String apiName) { this.apiName = apiName; }
  public String apiName() { return apiName; }
}

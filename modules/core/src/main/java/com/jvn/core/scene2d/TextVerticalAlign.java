package com.jvn.core.scene2d;

public enum TextVerticalAlign {
  BASELINE("baseline"), TOP("top"), MIDDLE("middle"), BOTTOM("bottom");

  private final String apiName;
  TextVerticalAlign(String apiName) { this.apiName = apiName; }
  public String apiName() { return apiName; }
}

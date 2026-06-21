package com.jvn.core.scene2d;

public enum TextHorizontalAlign {
  LEFT("left"), CENTER("center"), RIGHT("right");

  private final String apiName;
  TextHorizontalAlign(String apiName) { this.apiName = apiName; }
  public String apiName() { return apiName; }
}

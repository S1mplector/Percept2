package com.jvn.core.scene2d;

/** Portable blend modes understood by capability-aware renderers. */
public enum RenderBlendMode {
  NORMAL("normal"),
  ADDITIVE("additive"),
  MULTIPLY("multiply"),
  SCREEN("screen"),
  DESTINATION_IN("destination-in");

  private final String apiName;

  RenderBlendMode(String apiName) {
    this.apiName = apiName;
  }

  public String apiName() {
    return apiName;
  }
}

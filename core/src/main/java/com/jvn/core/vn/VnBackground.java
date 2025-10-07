package com.jvn.core.vn;

/**
 * Represents a background image for a visual novel scene
 */
public class VnBackground {
  private final String id;
  private final String imagePath;

  public VnBackground(String id, String imagePath) {
    this.id = id;
    this.imagePath = imagePath;
  }

  public String getId() { return id; }
  public String getImagePath() { return imagePath; }
}

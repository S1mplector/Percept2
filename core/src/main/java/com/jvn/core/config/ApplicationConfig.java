package com.jvn.core.config;

public class ApplicationConfig {
  private final String title;
  private final int width;
  private final int height;

  private ApplicationConfig(Builder b) {
    this.title = b.title;
    this.width = b.width;
    this.height = b.height;
  }

  public String title() { return title; }
  public int width() { return width; }
  public int height() { return height; }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String title = "JVN";
    private int width = 960;
    private int height = 540;

    public Builder title(String title) { this.title = title; return this; }
    public Builder width(int width) { this.width = width; return this; }
    public Builder height(int height) { this.height = height; return this; }
    public ApplicationConfig build() { return new ApplicationConfig(this); }
  }
}

package com.jvn.core.config;

public class ApplicationConfig {
  private final String title;
  private final int width;
  private final int height;
  private final long fixedUpdateMs;
  private final int fixedUpdateMaxSteps;
  private final double timeScale;

  private ApplicationConfig(Builder b) {
    this.title = b.title;
    this.width = b.width;
    this.height = b.height;
    this.fixedUpdateMs = b.fixedUpdateMs;
    this.fixedUpdateMaxSteps = b.fixedUpdateMaxSteps;
    this.timeScale = b.timeScale;
  }

  public String title() { return title; }
  public int width() { return width; }
  public int height() { return height; }
  public long fixedUpdateMs() { return fixedUpdateMs; }
  public int fixedUpdateMaxSteps() { return fixedUpdateMaxSteps; }
  /** Initial time scale for the engine. Default 1.0 (real-time). */
  public double timeScale() { return timeScale; }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String title = "JVN";
    private int width = 960;
    private int height = 540;
    private long fixedUpdateMs = 0;
    private int fixedUpdateMaxSteps = 5;
    private double timeScale = 1.0;

    public Builder title(String title) { this.title = title; return this; }
    public Builder width(int width) { this.width = width; return this; }
    public Builder height(int height) { this.height = height; return this; }
    /**
     * Enable a fixed update step (ms) and maximum substeps per frame; set stepMs to 0 to disable.
     */
    public Builder fixedUpdate(long stepMs, int maxSteps) {
      this.fixedUpdateMs = Math.max(0, stepMs);
      this.fixedUpdateMaxSteps = Math.max(1, maxSteps);
      return this;
    }
    /**
     * Set the initial time scale multiplier. Default 1.0 (real-time).
     * Use 0.5 for slow-motion, 2.0 for fast-forward, 0.0 for frozen.
     */
    public Builder timeScale(double scale) {
      this.timeScale = scale;
      return this;
    }
    public ApplicationConfig build() { return new ApplicationConfig(this); }
  }
}

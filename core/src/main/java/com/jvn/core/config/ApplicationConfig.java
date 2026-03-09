package com.jvn.core.config;

/**
 * Immutable configuration snapshot for the engine runtime.
 *
 * <p>{@code ApplicationConfig} holds top-level settings such as window
 * title, resolution, fixed-update parameters, and time scale. Instances
 * are created via the fluent {@link Builder}:</p>
 *
 * <pre>{@code
 * ApplicationConfig cfg = ApplicationConfig.builder()
 *     .title("My Game")
 *     .width(1920).height(1080)
 *     .fixedUpdate(16, 5)     // ~60 Hz fixed step
 *     .timeScale(1.0)
 *     .build();
 * }</pre>
 *
 * @see Builder
 */
public class ApplicationConfig {

  /** Window / application title. */
  private final String title;

  /** Viewport width in pixels. */
  private final int width;

  /** Viewport height in pixels. */
  private final int height;

  /** Fixed-update step size in ms (0 = disabled). */
  private final long fixedUpdateMs;

  /** Maximum number of fixed sub-steps per frame. */
  private final int fixedUpdateMaxSteps;

  /** Initial time-scale multiplier (1.0 = real-time). */
  private final double timeScale;

  private ApplicationConfig(Builder b) {
    this.title = b.title;
    this.width = b.width;
    this.height = b.height;
    this.fixedUpdateMs = b.fixedUpdateMs;
    this.fixedUpdateMaxSteps = b.fixedUpdateMaxSteps;
    this.timeScale = b.timeScale;
  }

  /** @return the application title */
  public String title() { return title; }

  /** @return the viewport width in pixels */
  public int width() { return width; }

  /** @return the viewport height in pixels */
  public int height() { return height; }

  /** @return the fixed-update step size in ms (0 = disabled) */
  public long fixedUpdateMs() { return fixedUpdateMs; }

  /** @return the maximum number of fixed sub-steps per frame */
  public int fixedUpdateMaxSteps() { return fixedUpdateMaxSteps; }

  /** @return the initial time-scale multiplier (1.0 = real-time) */
  public double timeScale() { return timeScale; }

  /** @return a new {@link Builder} with default values */
  public static Builder builder() { return new Builder(); }

  /**
   * Fluent builder for {@link ApplicationConfig}.
   *
   * <p>Defaults:</p>
   * <ul>
   *   <li><b>title</b> — "JVN"</li>
   *   <li><b>width × height</b> — 960 × 540</li>
   *   <li><b>fixedUpdateMs</b> — 0 (disabled)</li>
   *   <li><b>fixedUpdateMaxSteps</b> — 5</li>
   *   <li><b>timeScale</b> — 1.0</li>
   * </ul>
   */
  public static final class Builder {
    private String title = "JVN";
    private int width = 960;
    private int height = 540;
    private long fixedUpdateMs = 0;
    private int fixedUpdateMaxSteps = 5;
    private double timeScale = 1.0;

    /** Set the application title. */
    public Builder title(String title) { this.title = title; return this; }

    /** Set the viewport width in pixels. */
    public Builder width(int width) { this.width = width; return this; }

    /** Set the viewport height in pixels. */
    public Builder height(int height) { this.height = height; return this; }

    /**
     * Enable a fixed update step (ms) and maximum substeps per frame;
     * set {@code stepMs} to 0 to disable.
     *
     * @param stepMs   fixed step duration in ms
     * @param maxSteps maximum sub-steps per frame
     * @return this builder
     */
    public Builder fixedUpdate(long stepMs, int maxSteps) {
      this.fixedUpdateMs = Math.max(0, stepMs);
      this.fixedUpdateMaxSteps = Math.max(1, maxSteps);
      return this;
    }

    /**
     * Set the initial time-scale multiplier. Default 1.0 (real-time).
     * Use 0.5 for slow-motion, 2.0 for fast-forward, 0.0 for frozen.
     *
     * @param scale the time-scale multiplier
     * @return this builder
     */
    public Builder timeScale(double scale) {
      this.timeScale = scale;
      return this;
    }

    /** Build and return the immutable config. */
    public ApplicationConfig build() { return new ApplicationConfig(this); }
  }
}

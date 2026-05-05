package com.jvn.core.vn.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VnOverlayScreenSpec {
  private final String id;
  private final String title;
  private final String text;
  private final boolean modal;
  private final boolean dimBackground;
  private final boolean dismissOnAdvance;
  private final boolean callScreen;
  private final String returnKey;
  private final String timerAction;
  private final String timerTarget;
  private final List<VnOverlayButtonSpec> buttons;
  private final double x;
  private final double y;
  private final double width;
  private final double height;
  private long timerRemainingMs;

  public VnOverlayScreenSpec(
      String id,
      String title,
      String text,
      double x,
      double y,
      double width,
      double height,
      boolean modal,
      boolean dimBackground,
      boolean dismissOnAdvance,
      boolean callScreen,
      long timerMs,
      String timerAction,
      String timerTarget,
      String returnKey,
      List<VnOverlayButtonSpec> buttons
  ) {
    this.id = normalize(id, "screen");
    this.title = normalize(title, "");
    this.text = normalize(text, "");
    this.x = clamp01(sane(x, 0.18));
    this.y = clamp01(sane(y, 0.18));
    this.width = clamp(sane(width, 0.64), 0.05, 1.0);
    this.height = clamp(sane(height, 0.42), 0.05, 1.0);
    this.modal = modal;
    this.dimBackground = dimBackground;
    this.dismissOnAdvance = dismissOnAdvance;
    this.callScreen = callScreen;
    this.timerRemainingMs = Math.max(0L, timerMs);
    this.timerAction = normalize(timerAction, "hide");
    this.timerTarget = normalize(timerTarget, "");
    this.returnKey = normalize(returnKey, "screen.return");
    this.buttons = buttons == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(buttons));
  }

  public String getId() { return id; }
  public String getTitle() { return title; }
  public String getText() { return text; }
  public double getX() { return x; }
  public double getY() { return y; }
  public double getWidth() { return width; }
  public double getHeight() { return height; }
  public boolean isModal() { return modal; }
  public boolean isDimBackground() { return dimBackground; }
  public boolean isDismissOnAdvance() { return dismissOnAdvance; }
  public boolean isCallScreen() { return callScreen; }
  public String getTimerAction() { return timerAction; }
  public String getTimerTarget() { return timerTarget; }
  public String getReturnKey() { return returnKey; }
  public List<VnOverlayButtonSpec> getButtons() { return buttons; }
  public long getTimerRemainingMs() { return timerRemainingMs; }

  public boolean tick(long deltaMs) {
    if (timerRemainingMs <= 0) return false;
    timerRemainingMs = Math.max(0L, timerRemainingMs - Math.max(0L, deltaMs));
    return timerRemainingMs == 0L;
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }

  private static double sane(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  private static double clamp01(double value) {
    return clamp(value, 0.0, 1.0);
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }
}

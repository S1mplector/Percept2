package com.jvn.core.vn;

public class VnInteropResult {
  private final boolean advance;
  private final boolean continueProcessing;

  private VnInteropResult(boolean advance, boolean continueProcessing) {
    this.advance = advance;
    this.continueProcessing = continueProcessing;
  }
  public boolean shouldAdvance() { return advance; }
  public boolean shouldContinueProcessing() { return continueProcessing; }

  public static VnInteropResult advance() { return new VnInteropResult(true, true); }
  public static VnInteropResult stay() { return new VnInteropResult(false, true); }
  public static VnInteropResult block() { return new VnInteropResult(false, false); }
}

package com.jvn.core.vn;

/**
 * Defines where a character sprite should be positioned on screen.
 * <p>
 * Five predefined constants match the classic VN slots. Custom positions
 * carry normalised x/y fractions (0-1) and are created with
 * {@link #named(String, double, double)} or {@link #at(double, double)}.
 */
public final class CharacterPosition {

  // ── Predefined constants ─────────────────────────────────────────
  public static final CharacterPosition FAR_LEFT  = new CharacterPosition("FAR_LEFT",  0.10, -1.0, false);
  public static final CharacterPosition LEFT      = new CharacterPosition("LEFT",      0.25, -1.0, false);
  public static final CharacterPosition CENTER    = new CharacterPosition("CENTER",    0.50, -1.0, false);
  public static final CharacterPosition RIGHT     = new CharacterPosition("RIGHT",     0.75, -1.0, false);
  public static final CharacterPosition FAR_RIGHT = new CharacterPosition("FAR_RIGHT", 0.90, -1.0, false);

  private static final double TWEEN_OFFSET = 60.0;
  private static final double MOVE_REFERENCE_WIDTH = 1100.0;

  // ── Fields ───────────────────────────────────────────────────────
  private final String name;
  private final double xFraction;  // 0-1 centre of character on screen
  private final double yFraction;  // <0 means "use default baseline"
  private final boolean custom;
  private final CharacterPosition basePosition;
  private final String displaySlot;

  private CharacterPosition(String name, double xFraction, double yFraction, boolean custom) {
    this(name, xFraction, yFraction, custom, null, null);
  }

  private CharacterPosition(String name,
                            double xFraction,
                            double yFraction,
                            boolean custom,
                            CharacterPosition basePosition,
                            String displaySlot) {
    this.name = name;
    this.xFraction = xFraction;
    this.yFraction = yFraction;
    this.custom = custom;
    this.basePosition = basePosition;
    this.displaySlot = displaySlot;
  }

  /** Create a named custom position (from {@code @position} directive). */
  public static CharacterPosition named(String name, double x, double y) {
    return new CharacterPosition(name, x, y, true);
  }

  /** Create a named custom position with default baseline y. */
  public static CharacterPosition named(String name, double x) {
    return new CharacterPosition(name, x, -1.0, true);
  }

  /** Create an inline custom position. */
  public static CharacterPosition at(double x, double y) {
    String syntheticName = "_at_" + x + "_" + y;
    return new CharacterPosition(syntheticName, x, y, true);
  }

  /** Create an inline custom position with default baseline y. */
  public static CharacterPosition at(double x) {
    return at(x, -1.0);
  }

  /**
   * Create a distinct display slot at the same visual coordinates as {@code base}.
   * The returned position compares by its synthetic slot name, but all rendering
   * helpers delegate to the base position so predefined slots keep their classic
   * layout math.
   */
  public static CharacterPosition slotted(CharacterPosition base, String displaySlot) {
    String slot = displaySlot == null ? "" : displaySlot.trim();
    if (slot.isEmpty()) return base == null ? CENTER : base;
    CharacterPosition resolvedBase = base == null ? CENTER : base.getBasePosition();
    return new CharacterPosition(
        resolvedBase.getName() + "#slot:" + slot,
        resolvedBase.getXFraction(),
        resolvedBase.getYFraction(),
        resolvedBase.isCustom(),
        resolvedBase,
        slot);
  }

  /** Try to resolve a predefined constant by name (case-insensitive). Returns null if not found. */
  public static CharacterPosition predefined(String token) {
    if (token == null || token.isBlank()) return null;
    return switch (token.trim().toUpperCase(java.util.Locale.ENGLISH)) {
      case "FAR_LEFT", "FL", "FARLEFT" -> FAR_LEFT;
      case "LEFT", "L"                 -> LEFT;
      case "CENTER", "C", "CENTRE"     -> CENTER;
      case "RIGHT", "R"                -> RIGHT;
      case "FAR_RIGHT", "FR", "FARRIGHT" -> FAR_RIGHT;
      default -> null;
    };
  }

  // ── Accessors ────────────────────────────────────────────────────
  public String  getName()      { return name; }
  public double  getXFraction() { return xFraction; }
  public double  getYFraction() { return yFraction; }
  public boolean isCustom()     { return custom; }
  public boolean hasCustomY()   { return yFraction >= 0.0; }
  public boolean isDisplaySlot() { return displaySlot != null && !displaySlot.isBlank(); }
  public String  getDisplaySlot() { return displaySlot; }
  public CharacterPosition getBasePosition() { return basePosition == null ? this : basePosition; }

  // ── Rendering helpers ────────────────────────────────────────────

  /**
   * Compute the pixel x for the left edge of the sprite.
   * For predefined positions the classic VN layout is used;
   * for custom positions the sprite is centred on {@code xFraction}.
   */
  public double computeScreenX(double width, double spriteWidth) {
    if (basePosition != null) return basePosition.computeScreenX(width, spriteWidth);
    if (custom) return width * xFraction - spriteWidth / 2.0;
    return switch (name) {
      case "FAR_LEFT"  -> width * 0.05;
      case "LEFT"      -> width * 0.2;
      case "RIGHT"     -> width * 0.8 - spriteWidth;
      case "FAR_RIGHT" -> width * 0.95 - spriteWidth;
      default          -> (width - spriteWidth) / 2.0; // CENTER and fallback
    };
  }

  /**
   * Compute the pixel y for the top edge of the sprite.
   * If the position has a custom y, the sprite bottom sits at {@code height * yFraction};
   * otherwise the global baseline is used.
   */
  public double computeScreenY(double height, double spriteHeight, double baselineY) {
    if (basePosition != null) return basePosition.computeScreenY(height, spriteHeight, baselineY);
    double baseline = hasCustomY() ? yFraction : baselineY;
    return height * baseline - spriteHeight;
  }

  /** Sort ordinal for layer tie-breaking. */
  public int getOrdinal() {
    if (basePosition != null) return basePosition.getOrdinal();
    if (custom) return (int) (xFraction * 100);
    return switch (name) {
      case "FAR_LEFT"  -> -2;
      case "LEFT"      -> -1;
      case "RIGHT"     ->  1;
      case "FAR_RIGHT" ->  2;
      default          ->  0;
    };
  }

  /** Entrance animation x offset (pixels). */
  public double getEntranceOffsetX() {
    if (basePosition != null) return basePosition.getEntranceOffsetX();
    if (custom) return xFraction < 0.5 ? -TWEEN_OFFSET : (xFraction > 0.5 ? TWEEN_OFFSET : 0.0);
    return switch (name) {
      case "FAR_LEFT", "LEFT"   -> -TWEEN_OFFSET;
      case "FAR_RIGHT", "RIGHT" ->  TWEEN_OFFSET;
      default                   ->  0.0;
    };
  }

  /** Default layer order when none is specified. */
  public int getDefaultLayerOrder() {
    if (basePosition != null) return basePosition.getDefaultLayerOrder();
    if (custom) return (int) ((xFraction - 0.5) * 40);
    return switch (name) {
      case "FAR_LEFT"  -> -20;
      case "LEFT"      -> -10;
      case "RIGHT"     ->  10;
      case "FAR_RIGHT" ->  20;
      default          ->   0;
    };
  }

  /**
   * Pixel offset that an animation should start at when moving from
   * {@code from} to {@code this} position (target).
   */
  public double moveDeltaFrom(CharacterPosition from) {
    if (from == null) return 0.0;
    CharacterPosition source = from.getBasePosition();
    CharacterPosition target = getBasePosition();
    return (source.xFraction - target.xFraction) * MOVE_REFERENCE_WIDTH;
  }

  // ── Object identity ──────────────────────────────────────────────

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CharacterPosition cp)) return false;
    return name.equals(cp.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    if (custom) return name + "(" + xFraction + "," + yFraction + ")";
    return name;
  }
}

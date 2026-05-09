package com.jvn.editor.ui.actioneditor;

import java.util.Objects;

/**
 * Represents a named anchor point on an entity.
 * Anchors can be used as rotation centers or attachment points for other entities.
 * Unlike the single pivot point, multiple anchors can exist per entity.
 */
public class Anchor {
    
    private final String name;
    private final double x;
    private final double y;
    private final boolean isRelative; // true = normalized (0-1), false = world pixels
    
    public Anchor(String name, double x, double y) {
        this(name, x, y, true);
    }
    
    public Anchor(String name, double x, double y, boolean isRelative) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.isRelative = isRelative;
    }
    
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isRelative() { return isRelative; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Anchor that)) return false;
        return Objects.equals(name, that.name)
            && Double.compare(x, that.x) == 0
            && Double.compare(y, that.y) == 0
            && isRelative == that.isRelative;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, x, y, isRelative);
    }
    
    @Override
    public String toString() {
        return "Anchor{" +
            "name='" + name + '\'' +
            ", x=" + x +
            ", y=" + y +
            ", isRelative=" + isRelative +
            '}';
    }
    
    /**
     * Create a relative (normalized) anchor.
     */
    public static Anchor relative(String name, double x, double y) {
        return new Anchor(name, x, y, true);
    }
    
    /**
     * Create a world-space anchor (in pixels).
     */
    public static Anchor world(String name, double x, double y) {
        return new Anchor(name, x, y, false);
    }
}

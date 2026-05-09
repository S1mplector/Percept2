package com.jvn.editor.ui.actioneditor;

import java.util.Objects;

/**
 * Constraint that affects an entity's transform based on another entity.
 * Constraints are evaluated after keyframe animation to apply additional
 * transform rules.
 */
public class Constraint {
    
    public enum Type {
        /** Child entity follows parent's position, rotation, and scale */
        PARENT_CHILD,
        /** Entity rotates to always face the target entity */
        LOOK_AT
    }
    
    private final Type type;
    private final String targetEntityName;
    private final double offsetX;
    private final double offsetY;
    private final boolean inheritRotation;
    private final boolean inheritScale;
    
    public Constraint(Type type, String targetEntityName) {
        this(type, targetEntityName, 0.0, 0.0, true, true);
    }
    
    public Constraint(Type type, String targetEntityName, 
                     double offsetX, double offsetY, 
                     boolean inheritRotation, boolean inheritScale) {
        this.type = type;
        this.targetEntityName = targetEntityName;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.inheritRotation = inheritRotation;
        this.inheritScale = inheritScale;
    }
    
    public Type getType() { return type; }
    public String getTargetEntityName() { return targetEntityName; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public boolean isInheritRotation() { return inheritRotation; }
    public boolean isInheritScale() { return inheritScale; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Constraint that)) return false;
        return type == that.type
            && Objects.equals(targetEntityName, that.targetEntityName)
            && Double.compare(offsetX, that.offsetX) == 0
            && Double.compare(offsetY, that.offsetY) == 0
            && inheritRotation == that.inheritRotation
            && inheritScale == that.inheritScale;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, targetEntityName, offsetX, offsetY, inheritRotation, inheritScale);
    }
    
    @Override
    public String toString() {
        return "Constraint{" +
            "type=" + type +
            ", target='" + targetEntityName + '\'' +
            ", offset=(" + offsetX + "," + offsetY + ")" +
            ", inheritRotation=" + inheritRotation +
            ", inheritScale=" + inheritScale +
            '}';
    }
    
    /**
     * Create a parent-child constraint.
     */
    public static Constraint parentChild(String parentEntityName) {
        return new Constraint(Type.PARENT_CHILD, parentEntityName);
    }
    
    /**
     * Create a parent-child constraint with offset.
     */
    public static Constraint parentChild(String parentEntityName, 
                                         double offsetX, double offsetY,
                                         boolean inheritRotation, boolean inheritScale) {
        return new Constraint(Type.PARENT_CHILD, parentEntityName, 
                           offsetX, offsetY, inheritRotation, inheritScale);
    }
    
    /**
     * Create a look-at constraint.
     */
    public static Constraint lookAt(String targetEntityName) {
        return new Constraint(Type.LOOK_AT, targetEntityName);
    }
}

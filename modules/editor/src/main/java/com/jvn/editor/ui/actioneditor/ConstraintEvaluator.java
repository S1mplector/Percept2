package com.jvn.editor.ui.actioneditor;

/**
 * Evaluates constraints and applies them to entity transforms.
 * Constraints are applied after keyframe animation to modify the final transform.
 */
public class ConstraintEvaluator {
    
    /**
     * Result of applying constraints to an entity's transform.
     */
    public static class ConstrainedTransform {
        public final double x;
        public final double y;
        public final double rotationDeg;
        public final double scaleX;
        public final double scaleY;
        
        public ConstrainedTransform(double x, double y, double rotationDeg, double scaleX, double scaleY) {
            this.x = x;
            this.y = y;
            this.rotationDeg = rotationDeg;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }
    
    /**
     * Evaluate and apply constraints to get the final constrained transform.
     * 
     * @param entityName the entity being constrained
     * @param baseX base X position from keyframes
     * @param baseY base Y position from keyframes
     * @param baseRotationDeg base rotation from keyframes
     * @param baseScaleX base scale X from keyframes
     * @param baseScaleY base scale Y from keyframes
     * @param constraint the constraint to apply
     * @param project the animation project (to get parent entity transforms)
     * @param timeMs current time in ms
     * @return the constrained transform
     */
    public static ConstrainedTransform evaluate(
            String entityName,
            double baseX, double baseY,
            double baseRotationDeg,
            double baseScaleX, double baseScaleY,
            Constraint constraint,
            AnimationProject project,
            double timeMs) {
        
        if (constraint == null || project == null) {
            return new ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }
        
        String targetName = constraint.getTargetEntityName();
        if (targetName == null || targetName.isBlank()) {
            return new ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }
        
        // Prevent circular constraints
        if (entityName != null && entityName.equals(targetName)) {
            return new ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }
        
        if (project.getTrack(targetName) == null) {
            return new ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }

        AnimationProject.EffectiveEntityTransform targetTransform =
            project.computeEffectiveEntityTransform(targetName, timeMs);
        if (targetTransform == null) {
            return new ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }
        
        // Use the target's group-aware transform. This intentionally excludes
        // target constraints, avoiding recursion while respecting parent rigs.
        double parentX = targetTransform.x();
        double parentY = targetTransform.y();
        double parentRot = targetTransform.rotationDeg();
        double parentScaleX = targetTransform.scaleX();
        double parentScaleY = targetTransform.scaleY();
        
        // Apply constraint based on type
        return switch (constraint.getType()) {
            case PARENT_CHILD -> applyParentChild(
                baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY,
                parentX, parentY, parentRot, parentScaleX, parentScaleY,
                constraint);
            case LOOK_AT -> applyLookAt(
                baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY,
                parentX, parentY, constraint);
        };
    }
    
    /**
     * Apply parent-child constraint.
     * Child follows parent's transform with optional offset.
     */
    @SuppressWarnings("unused") // childX, childY kept for future use (local offset)
    private static ConstrainedTransform applyParentChild(
            double childX, double childY, double childRot, double childScaleX, double childScaleY,
            double parentX, double parentY, double parentRot, double parentScaleX, double parentScaleY,
            Constraint constraint) {
        
        double offsetX = constraint.getOffsetX();
        double offsetY = constraint.getOffsetY();
        boolean inheritRotation = constraint.isInheritRotation();
        boolean inheritScale = constraint.isInheritScale();
        
        // Calculate parent rotation in radians
        double parentRotRad = Math.toRadians(parentRot);
        double cosParent = Math.cos(parentRotRad);
        double sinParent = Math.sin(parentRotRad);
        
        // Apply offset rotated by parent rotation
        double rotatedOffsetX = offsetX * cosParent - offsetY * sinParent;
        double rotatedOffsetY = offsetX * sinParent + offsetY * cosParent;
        
        // Child position = parent position + rotated offset
        double finalX = parentX + rotatedOffsetX;
        double finalY = parentY + rotatedOffsetY;
        
        // Child rotation = parent rotation + child rotation (if inheriting)
        double finalRot = inheritRotation ? parentRot + childRot : childRot;
        
        // Child scale = parent scale * child scale (if inheriting)
        double finalScaleX = inheritScale ? parentScaleX * childScaleX : childScaleX;
        double finalScaleY = inheritScale ? parentScaleY * childScaleY : childScaleY;
        
        return new ConstrainedTransform(finalX, finalY, finalRot, finalScaleX, finalScaleY);
    }
    
    /**
     * Apply look-at constraint.
     * Entity rotates to face the target entity.
     */
    private static ConstrainedTransform applyLookAt(
            double entityX, double entityY, double entityRot, double entityScaleX, double entityScaleY,
            double targetX, double targetY,
            Constraint constraint) {
        
        // Calculate angle to target
        double dx = targetX - entityX;
        double dy = targetY - entityY;
        
        // Don't rotate if entity is at target position
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) {
            return new ConstrainedTransform(entityX, entityY, entityRot, entityScaleX, entityScaleY);
        }
        
        // Calculate angle in degrees
        double angleToTarget = Math.toDegrees(Math.atan2(dy, dx));
        
        // Apply offset to angle if needed (for sprites that face right vs up by default)
        // Most sprites face right by default, so no offset needed
        
        return new ConstrainedTransform(entityX, entityY, angleToTarget, entityScaleX, entityScaleY);
    }
}

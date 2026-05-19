package com.jvn.core.animation;

/**
 * Optional marker for scene entities that need to know which position axes were
 * explicitly driven by a timeline frame.
 */
public interface TimelineDrivenEntity {
    void setTimelinePosition(double x, double y, boolean hasX, boolean hasY);
    boolean hasTimelineX();
    boolean hasTimelineY();
}

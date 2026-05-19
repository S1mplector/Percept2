package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineDrivenEntity;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Sprite2D;

/**
 * Lightweight {@link SceneAccessor} for the VNS preview that creates virtual
 * {@link Entity2D} proxies for VN character names.  When an inline timeline
 * animates x/y on a proxy, the VN renderer can apply those authored offsets
 * on top of the slot-based positioning.
 */
public class VnCharacterSceneAccessor implements SceneAccessor {
    private final Map<String, TimelineProxyEntity> proxies = new ConcurrentHashMap<>();
    private final List<EventRecord> eventLog = new ArrayList<>();
    private String lastExpressionTarget = "";
    private String lastExpressionValue = "";

    /** Immutable record of a dispatched event cue for diagnostics / preview log. */
    public record EventRecord(String type, Map<String, String> payload) {}

    @Override
    public Entity2D findEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return proxies.computeIfAbsent(name, k -> new TimelineProxyEntity());
    }

    @Override
    public void onEventCue(String type, Map<String, String> payload) {
        eventLog.add(new EventRecord(type, payload));
        if ("expression".equals(type)) {
            lastExpressionTarget = payload.getOrDefault("target", "");
            lastExpressionValue = payload.getOrDefault("value", "");
        }
    }

    /**
     * Returns the proxy entity for a character if one was created by a timeline,
     * or {@code null} if no timeline has referenced this name.
     */
    public Entity2D getProxy(String characterName) {
        return proxies.get(characterName);
    }

    /** Returns true if any proxy has been created. */
    public boolean hasProxies() {
        return !proxies.isEmpty();
    }

    /** Returns an unmodifiable view of all event cues dispatched so far. */
    public List<EventRecord> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public String getLastExpressionTarget() { return lastExpressionTarget; }
    public String getLastExpressionValue() { return lastExpressionValue; }

    /** Clears all proxy entities and event log (call when reloading a scenario). */
    public void clear() {
        proxies.clear();
        eventLog.clear();
        lastExpressionTarget = "";
        lastExpressionValue = "";
    }

    /**
     * Virtual sprite used by VN timelines. It preserves which position axes were
     * authored so renderers can keep unkeyed VN slot placement intact.
     */
    public static final class TimelineProxyEntity extends Sprite2D implements TimelineDrivenEntity {
        private boolean timelineX;
        private boolean timelineY;

        private TimelineProxyEntity() {
            super("", 0, 0);
            // VN character timelines are applied as offsets from the slot top-left.
            // Without an authored pivot, mirror/scale should still use the normal
            // character footing rather than the sprite's top-left corner.
            setOrigin(0.5, 1.0);
        }

        @Override
        public void setTimelinePosition(double x, double y, boolean hasX, boolean hasY) {
            if (hasX) {
                this.x = x;
                timelineX = true;
            }
            if (hasY) {
                this.y = y;
                timelineY = true;
            }
        }

        @Override
        public void setPosition(double x, double y) {
            super.setPosition(x, y);
            timelineX = true;
            timelineY = true;
        }

        @Override
        public boolean hasTimelineX() {
            return timelineX;
        }

        @Override
        public boolean hasTimelineY() {
            return timelineY;
        }
    }
}

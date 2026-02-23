package com.jvn.core.vn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.scene2d.Entity2D;

/**
 * Lightweight {@link SceneAccessor} for the VNS preview that creates virtual
 * {@link Entity2D} proxies for VN character names.  When an inline timeline
 * animates x/y on a proxy, the VN renderer can read those coordinates directly
 * instead of using the slot-based positioning.
 */
public class VnCharacterSceneAccessor implements SceneAccessor {
    private final Map<String, Entity2D> proxies = new ConcurrentHashMap<>();

    @Override
    public Entity2D findEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return proxies.computeIfAbsent(name, k -> new Entity2D());
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

    /** Clears all proxy entities (call when reloading a scenario). */
    public void clear() {
        proxies.clear();
    }
}

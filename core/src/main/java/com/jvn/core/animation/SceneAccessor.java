package com.jvn.core.animation;

import com.jvn.core.scene2d.Entity2D;

/**
 * Abstraction over a scene graph for timeline playback.
 * Allows {@link TimelineRunner} to look up entities by name without
 * depending on the scripting module's JesScene2D directly.
 */
public interface SceneAccessor {

    /**
     * Find an entity by its name in the scene.
     * @return the entity, or null if not found
     */
    Entity2D findEntity(String name);

    /**
     * Optional camera hook used by timeline playback.
     */
    default void setCameraX(double x) {}

    /**
     * Optional camera hook used by timeline playback.
     */
    default void setCameraY(double y) {}

    /**
     * Optional camera hook used by timeline playback.
     */
    default void setCameraZoom(double zoom) {}

    /**
     * Optional audio hook used by timeline playback.
     */
    default void playAudioCue(String trackPath, String channel, double volume, boolean loop, double fadeInMs) {}

    /**
     * Optional audio stop hook used by timeline playback.
     */
    default void stopAudio(String channel) {}

    /**
     * Optional event cue callback used by timeline playback.
     * Fired once when the playhead crosses an event cue.
     *
     * @param type    the event type (e.g. "expression", "dialogue_marker", "script_call")
     * @param payload key-value pairs describing the event
     */
    default void onEventCue(String type, java.util.Map<String, String> payload) {}
}

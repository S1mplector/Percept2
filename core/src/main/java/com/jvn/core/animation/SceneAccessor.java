package com.jvn.core.animation;

import com.jvn.core.scene2d.Entity2D;

/**
 * Abstraction over a scene graph for timeline playback.
 *
 * <p>{@code SceneAccessor} decouples {@link TimelineRunner} from any
 * specific scene implementation (e.g. {@code JesScene2D} in the scripting
 * module). Implementations supply entity lookup, camera control, audio
 * triggering, and event-cue callbacks so that timelines can drive the
 * scene without compile-time dependencies.</p>
 *
 * <p>All methods except {@link #findEntity(String)} have default no-op
 * implementations, so an adapter only needs to override the hooks it
 * actually supports.</p>
 *
 * @see TimelineRunner
 * @see TimelineData
 */
public interface SceneAccessor {

    /**
     * Look up an entity by its name in the scene graph.
     *
     * @param name the entity name
     * @return the matching entity, or {@code null} if not found
     */
    Entity2D findEntity(String name);

    /**
     * Set the camera's horizontal position.
     * Default implementation is a no-op.
     *
     * @param x the new camera X position
     */
    default void setCameraX(double x) {}

    /**
     * Set the camera's vertical position.
     * Default implementation is a no-op.
     *
     * @param y the new camera Y position
     */
    default void setCameraY(double y) {}

    /**
     * Set the camera's zoom level.
     * Default implementation is a no-op.
     *
     * @param zoom the new zoom factor (1.0 = default)
     */
    default void setCameraZoom(double zoom) {}

    /**
     * Start playing an audio cue triggered by the timeline.
     * Default implementation is a no-op.
     *
     * @param trackPath the asset path to the audio file
     * @param channel   the audio channel name (e.g. "music", "sound")
     * @param volume    playback volume [0, 1]
     * @param loop      whether to loop the audio
     * @param fadeInMs   fade-in duration in milliseconds
     */
    default void playAudioCue(String trackPath, String channel, double volume, boolean loop, double fadeInMs) {}

    /**
     * Stop audio playback on a given channel.
     * Default implementation is a no-op.
     *
     * @param channel the audio channel to stop
     */
    default void stopAudio(String channel) {}

    /**
     * Handle a discrete event cue fired when the playhead crosses its time.
     * Default implementation is a no-op.
     *
     * @param type    the event type (e.g. "expression", "dialogue_marker", "script_call")
     * @param payload key-value pairs describing the event
     */
    default void onEventCue(String type, java.util.Map<String, String> payload) {}
}

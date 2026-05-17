package com.jvn.editor.ui.actioneditor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Character-aware authoring helper for Puppeteer.
 * Provides quick-placement coordinates based on VN character slot semantics
 * (far_left / left / center / right / far_right) using the current viewport
 * resolution.
 * <p>
 * Also provides a convenience method for inserting expression-change event cues.
 */
public class VnSlotHelper {

    /** Standard VN character slot positions as fractional X across the viewport. */
    public enum Slot {
        FAR_LEFT(0.1),
        LEFT(0.25),
        CENTER(0.5),
        RIGHT(0.75),
        FAR_RIGHT(0.9);

        private final double fraction;

        Slot(double fraction) { this.fraction = fraction; }

        public double getFraction() { return fraction; }
    }

    /**
     * Compute the absolute X coordinate for a slot given a viewport width.
     *
     * @param slot the character slot
     * @param viewportWidth the viewport width in pixels
     * @return X coordinate
     */
    public static double slotX(Slot slot, double viewportWidth) {
        return slot.getFraction() * viewportWidth;
    }

    /**
     * Compute a standard Y baseline for character placement given viewport height.
     * Uses a bottom-third default (character feet near bottom).
     *
     * @param viewportHeight the viewport height in pixels
     * @return Y coordinate (top-left origin)
     */
    public static double baselineY(double viewportHeight) {
        return viewportHeight * 0.65;
    }

    /**
     * Place an entity at a VN slot position by inserting X and Y keyframes at the
     * given playhead time.
     *
     * @param project      the current animation project
     * @param entityName   the entity to position
     * @param slot         the target VN slot
     * @param timeMs       the playhead time
     * @param viewportWidth  viewport width in pixels
     * @param viewportHeight viewport height in pixels
     */
    public static void placeAtSlot(AnimationProject project, String entityName,
                                    Slot slot, double timeMs,
                                    double viewportWidth, double viewportHeight) {
        EntityTrack track = project.getOrCreateTrack(entityName);
        double x = slotX(slot, viewportWidth);
        double y = baselineY(viewportHeight);
        track.addKeyframe(PropertyType.X, new Keyframe(timeMs, x));
        track.addKeyframe(PropertyType.Y, new Keyframe(timeMs, y));
    }

    /**
     * Insert an expression-change event cue for a character at the current playhead.
     *
     * @param project    the current animation project
     * @param target     the character name / entity
     * @param expression the expression name (e.g. "smile", "neutral", "angry")
     * @param timeMs     the playhead time
     */
    public static void insertExpressionCue(AnimationProject project, String target,
                                            String expression, double timeMs) {
        insertExpressionCue(project, target, expression, timeMs, "", Map.of());
    }

    /**
     * Insert an expression-change cue with optional resolved sprite data.
     *
     * <p>{@code pathSpec} may be a single image or a pipe-separated layered
     * sprite path. {@code layersById}, when present, is exported as lightweight
     * layer metadata so Puppeteer can swap layered preview entities by layer id.</p>
     */
    public static void insertExpressionCue(AnimationProject project,
                                            String target,
                                            String expression,
                                            double timeMs,
                                            String pathSpec,
                                            Map<String, String> layersById) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("target", target);
        payload.put("value", expression);
        if (pathSpec != null && !pathSpec.isBlank()) {
            payload.put("path", pathSpec.trim());
        }
        String layerPayload = encodeLayers(layersById);
        if (!layerPayload.isBlank()) {
            payload.put("layers", layerPayload);
        }
        project.addEditorEventCue(new EditorEventCue(timeMs, "expression", payload));
    }

    private static String encodeLayers(Map<String, String> layersById) {
        if (layersById == null || layersById.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (Map.Entry<String, String> entry : layersById.entrySet()) {
            if (entry == null || entry.getValue() == null || entry.getValue().isBlank()) continue;
            if (!out.isEmpty()) out.append(" | ");
            String layerId = entry.getKey() == null || entry.getKey().isBlank()
                ? "layer" + index
                : entry.getKey().trim();
            out.append(layerId).append('=').append(entry.getValue().trim());
            index++;
        }
        return out.toString();
    }

    /**
     * Insert a dialogue marker event cue at the current playhead.
     *
     * @param project the current animation project
     * @param markerId the marker identifier
     * @param timeMs  the playhead time
     */
    public static void insertDialogueMarker(AnimationProject project, String markerId,
                                             double timeMs) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("id", markerId);
        project.addEditorEventCue(new EditorEventCue(timeMs, "dialogue_marker", payload));
    }
}

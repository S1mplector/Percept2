package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.jvn.core.animation.Easing;

/**
 * Diagnostic engine for Puppeteer timelines.  Scans an {@link AnimationProject}
 * and produces non-blocking warning messages with optional quick-fix suggestions.
 * <p>
 * Categories:
 * <ul>
 *   <li>Unknown property / action names</li>
 *   <li>Invalid easing names</li>
 *   <li>Missing target entities</li>
 *   <li>Out-of-range alpha / zoom / pivot values</li>
 * </ul>
 */
public class TimelineDiagnostic {

    public enum Severity { INFO, WARNING, ERROR }

    public record Message(
        Severity severity,
        String entityOrTrack,
        String description,
        String quickFix
    ) {}

    private static final Set<String> KNOWN_EASINGS;
    static {
        Set<String> s = new java.util.LinkedHashSet<>();
        for (Easing.Type t : Easing.Type.values()) {
            s.add(t.name().toLowerCase());
        }
        KNOWN_EASINGS = Collections.unmodifiableSet(s);
    }

    /**
     * Run diagnostics on the given project and return a list of messages.
     * Never blocks editing — always returns even if there are problems.
     *
     * @param project       the editor project to validate
     * @param knownEntities optional set of entity names known to exist in the scene (may be null)
     * @return list of diagnostic messages, possibly empty
     */
    public static List<Message> diagnose(AnimationProject project, Set<String> knownEntities) {
        if (project == null) return Collections.emptyList();
        List<Message> msgs = new ArrayList<>();

        for (EntityTrack track : project.getTracks()) {
            String entity = track.getEntityName();

            // Check if entity is known to the scene
            if (knownEntities != null && !knownEntities.isEmpty()
                && !entity.startsWith("__") && !knownEntities.contains(entity)) {
                msgs.add(new Message(Severity.WARNING, entity,
                    "Entity \"" + entity + "\" not found in current scene",
                    "Create placeholder entity or check spelling"));
            }

            for (PropertyType prop : PropertyType.values()) {
                List<Keyframe> kfs = track.getKeyframes(prop);
                for (Keyframe kf : kfs) {
                    // Alpha range check
                    if (prop == PropertyType.ALPHA) {
                        if (kf.getValue() < 0.0 || kf.getValue() > 1.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Alpha value " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is out of [0,1] range",
                                "Clamp to " + Math.max(0, Math.min(1, kf.getValue()))));
                        }
                    }
                    // Camera zoom range check
                    if (prop == PropertyType.CAMERA_ZOOM) {
                        if (kf.getValue() <= 0.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Camera zoom " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is non-positive",
                                "Set to 0.01 minimum"));
                        }
                        if (kf.getValue() > 10.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                "Camera zoom " + kf.getValue() + " at " + kf.getTimeMs()
                                    + "ms is unusually large (>10x)",
                                null));
                        }
                    }
                    // Pivot range check
                    if (prop == PropertyType.PIVOT_X || prop == PropertyType.PIVOT_Y) {
                        if (kf.getValue() < 0.0 || kf.getValue() > 1.0) {
                            msgs.add(new Message(Severity.WARNING, entity,
                                prop.getDisplayName() + " value " + kf.getValue()
                                    + " at " + kf.getTimeMs() + "ms is out of [0,1] range",
                                "Clamp to " + Math.max(0, Math.min(1, kf.getValue()))));
                        }
                    }
                    // Easing validity check
                    if (kf.getEasing() != null && kf.getEasing() != Easing.Type.CUSTOM) {
                        String easingName = kf.getEasing().name().toLowerCase();
                        if (!KNOWN_EASINGS.contains(easingName)) {
                            msgs.add(new Message(Severity.ERROR, entity,
                                "Unknown easing \"" + easingName + "\" at " + kf.getTimeMs() + "ms",
                                "Use 'linear' or another known easing"));
                        }
                    }
                }
            }
        }

        // Check event cues
        for (EditorEventCue evt : project.getEditorEventCues()) {
            if (evt.getType().isBlank()) {
                msgs.add(new Message(Severity.ERROR, "(event)",
                    "Event cue at " + evt.getTimeMs() + "ms has empty type",
                    "Set a type like 'expression', 'dialogue_marker', or 'script_call'"));
            }
        }

        return Collections.unmodifiableList(msgs);
    }

    /**
     * Validate an easing name string and return the closest known match, or null.
     */
    public static String suggestEasing(String input) {
        if (input == null || input.isBlank()) return "linear";
        String normalized = input.trim().toUpperCase().replace('-', '_');
        try {
            Easing.Type.valueOf(normalized);
            return normalized.toLowerCase();
        } catch (Exception e) {
            // Find closest by edit distance
            String best = null;
            int bestDist = Integer.MAX_VALUE;
            for (String known : KNOWN_EASINGS) {
                int dist = editDistance(normalized.toLowerCase(), known);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = known;
                }
            }
            return bestDist <= 3 ? best : null;
        }
    }

    private static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}

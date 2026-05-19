package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.vn.EyeFocusResolver;
import com.jvn.core.vn.VnEyeFocusProfile;

/**
 * Bakes an eye-focus resolution into ordinary Puppeteer keyframes.
 */
public final class PuppeteerEyeFocusBaker {
    private static final double DEFAULT_SAMPLE_MS = 100.0;

    private PuppeteerEyeFocusBaker() {}

    public record BakeResult(int keypadIndex, String selectedLayerId, String selectedTargetName, double nudgeX, double nudgeY) {}

    public static BakeResult applyAt(
            AnimationProject project,
            VnEyeFocusProfile profile,
            double timeMs,
            double sourceX,
            double sourceY,
            double targetX,
            double targetY
    ) {
        if (project == null || profile == null || profile.characterId().isBlank()) return null;
        EyeFocusResolver.Result resolved = EyeFocusResolver.resolve(
            sourceX,
            sourceY,
            targetX,
            targetY,
            profile.deadZone(),
            profile.maxNudgePx(),
            profile.strength()
        );
        return bakeResolved(project, profile, Math.max(0.0, timeMs), resolved);
    }

    public static void applyRange(
            AnimationProject project,
            VnEyeFocusProfile profile,
            double startMs,
            double endMs,
            double sourceX,
            double sourceY,
            double targetX,
            double targetY,
            double sampleMs
    ) {
        if (project == null || profile == null) return;
        double start = Math.max(0.0, Math.min(startMs, endMs));
        double end = Math.max(start, Math.max(startMs, endMs));
        double step = sampleMs > 0.0 && Double.isFinite(sampleMs) ? sampleMs : DEFAULT_SAMPLE_MS;
        for (double time = start; time <= end + 0.001; time += step) {
            applyAt(project, profile, time, sourceX, sourceY, targetX, targetY);
        }
        if (end > start && Math.abs(((end - start) % step)) > 0.001) {
            applyAt(project, profile, end, sourceX, sourceY, targetX, targetY);
        }
    }

    public static String targetName(String characterId, String expression, String layerId) {
        String safeCharacter = selectorSafeName(characterId);
        String safeExpression = selectorSafeName(expression == null || expression.isBlank() ? "neutral" : expression);
        String safeLayer = selectorSafeName(layerId);
        if (safeCharacter.isBlank() || safeExpression.isBlank() || safeLayer.isBlank()) return "";
        return safeCharacter + "_" + safeExpression + "_" + safeLayer;
    }

    private static BakeResult bakeResolved(AnimationProject project, VnEyeFocusProfile profile, double timeMs, EyeFocusResolver.Result resolved) {
        if (resolved == null) return null;
        String selectedLayer = profile.layerIdFor(resolved.keypadIndex());
        if (selectedLayer == null || selectedLayer.isBlank()) {
            selectedLayer = profile.layerIdFor(5);
        }
        if (selectedLayer == null || selectedLayer.isBlank()) return null;

        for (int keypad = 1; keypad <= 9; keypad++) {
            String layerId = profile.layerIdFor(keypad);
            if (layerId == null || layerId.isBlank()) continue;
            String targetName = targetName(profile.characterId(), profile.expression(), layerId);
            if (targetName.isBlank()) continue;
            EntityTrack track = project.getOrCreateTrack(targetName);
            track.upsertKeyframe(PropertyType.VISIBILITY, hold(timeMs, layerId.equals(selectedLayer) ? 1.0 : 0.0));
        }

        String selectedTarget = targetName(profile.characterId(), profile.expression(), selectedLayer);
        if (selectedTarget.isBlank()) return null;
        EntityTrack selectedTrack = project.getOrCreateTrack(selectedTarget);
        selectedTrack.upsertKeyframe(PropertyType.X, tween(timeMs, resolved.nudgeX()));
        selectedTrack.upsertKeyframe(PropertyType.Y, tween(timeMs, resolved.nudgeY()));
        project.setEyeFocusProfile(profile);
        return new BakeResult(resolved.keypadIndex(), selectedLayer, selectedTarget, resolved.nudgeX(), resolved.nudgeY());
    }

    private static Keyframe hold(double timeMs, double value) {
        return new Keyframe(timeMs, value, EasingSpec.of(Easing.Type.LINEAR), Easing.Interpolation.HOLD);
    }

    private static Keyframe tween(double timeMs, double value) {
        return new Keyframe(timeMs, value, EasingSpec.of(Easing.Type.LINEAR), Easing.Interpolation.TWEEN);
    }

    private static String selectorSafeName(String raw) {
        String value = raw == null ? "" : raw.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                out.append(ch);
            } else {
                out.append('_');
            }
        }
        String cleaned = out.toString().replaceAll("_+", "_");
        while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }
}

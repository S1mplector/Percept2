package com.jvn.editor.ui.actioneditor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.jvn.core.animation.TimelineData;

/**
 * Compact summary of a generated timeline export, shown to creators before
 * they commit to registering or copying it.
 */
public record TimelineExportSummary(
    long commentLineCount,
    long actionLineCount,
    int trackCount,
    int actionCount,
    double durationMs,
    Set<String> affectedEntityNames
) {
    private static final int LARGE_LINE_THRESHOLD = 400;
    private static final int LARGE_ACTION_THRESHOLD = 150;

    public long totalLineCount() {
        return commentLineCount + actionLineCount;
    }

    public boolean isLarge() {
        return totalLineCount() > LARGE_LINE_THRESHOLD || actionCount > LARGE_ACTION_THRESHOLD;
    }

    public static TimelineExportSummary of(AnimationProject project, String generatedCode, TimelineData data) {
        long commentLines = 0;
        long actionLines = 0;
        if (generatedCode != null) {
            for (String rawLine : generatedCode.split("\n", -1)) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("//")) {
                    commentLines++;
                } else {
                    actionLines++;
                }
            }
        }

        int trackCount = 0;
        int actionCount = 0;
        Set<String> affectedEntityNames = new LinkedHashSet<>();
        if (data != null) {
            List<TimelineData.Track> tracks = data.getTracks();
            trackCount = tracks.size();
            for (TimelineData.Track track : tracks) {
                if (!track.getEntityName().isBlank()) {
                    affectedEntityNames.add(track.getEntityName());
                }
                for (List<TimelineData.Keyframe> keyframes : track.getAllKeyframes().values()) {
                    actionCount += keyframes.size();
                }
                for (List<TimelineData.Keyframe> keyframes : track.getAllCustomKeyframes().values()) {
                    actionCount += keyframes.size();
                }
            }
            actionCount += data.getAudioCues().size();
            actionCount += data.getEventCues().size();
        }

        double durationMs = data != null ? data.getDurationMs() : 0.0;

        return new TimelineExportSummary(
            commentLines,
            actionLines,
            trackCount,
            actionCount,
            durationMs,
            affectedEntityNames
        );
    }
}

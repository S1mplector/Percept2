package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.animation.TimelineData;
import org.junit.jupiter.api.Test;

class TimelineExportSummaryTest {

    private static AnimationProject sampleProject() {
        AnimationProject project = new AnimationProject();
        project.setTotalDurationMs(2500);

        project.setSceneEntitySnapshots(java.util.List.of(
            new AnimationProject.SceneEntitySnapshot(
                "alice", "character", "alice.png", 0, 0, 100, 100, 0.5, 0.5, 0, true, 1.0),
            new AnimationProject.SceneEntitySnapshot(
                "bg_layer1", "layer", "bg.png", 0, 0, 200, 200, 0.5, 0.5, -1, true, 1.0)
        ));

        EntityTrack aliceTrack = project.getOrCreateTrack("alice");
        aliceTrack.addKeyframe(PropertyType.X, new Keyframe(0, 0));
        aliceTrack.addKeyframe(PropertyType.X, new Keyframe(1000, 100));
        aliceTrack.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 1.0));

        EntityTrack bgTrack = project.getOrCreateTrack("bg_layer1");
        bgTrack.addKeyframe(PropertyType.ALPHA, new Keyframe(0, 0));
        bgTrack.addKeyframe(PropertyType.ALPHA, new Keyframe(500, 1.0));

        return project;
    }

    private static String sampleGeneratedCode() {
        return String.join("\n",
            "// Timeline: demo",
            "// Usage in VNS: @external jes_timeline demo",
            "",
            "// Puppeteer scene metadata. Runtime parsers ignore these comments.",
            "// @jvn-puppeteer-entity name=alice type=character",
            "// @jvn-puppeteer-entity name=bg_layer1 type=layer",
            "",
            "timeline {",
            "  track alice {",
            "    keyframe x 0 0",
            "    keyframe x 1000 100",
            "    keyframe alpha 0 1.0",
            "  }",
            "  track bg_layer1 {",
            "    keyframe alpha 0 0",
            "    keyframe alpha 500 1.0",
            "  }",
            "}",
            ""
        );
    }

    @Test
    void countsLinesSplitBetweenMetadataCommentsAndScriptActions() {
        AnimationProject project = sampleProject();
        TimelineData data = project.toTimelineData("demo");
        String code = sampleGeneratedCode();

        TimelineExportSummary summary = TimelineExportSummary.of(project, code, data);

        long expectedCommentLines = 5; // header (2) + metadata header (1) + 2 entity comments
        long expectedNonBlankLines = code.lines().filter(line -> !line.isBlank()).count();

        assertEquals(expectedCommentLines, summary.commentLineCount());
        assertEquals(expectedNonBlankLines - expectedCommentLines, summary.actionLineCount());
        assertEquals(expectedNonBlankLines, summary.commentLineCount() + summary.actionLineCount());
    }

    @Test
    void countsTracksActionsDurationAndAffectedEntities() {
        AnimationProject project = sampleProject();
        TimelineData data = project.toTimelineData("demo");
        String code = sampleGeneratedCode();

        TimelineExportSummary summary = TimelineExportSummary.of(project, code, data);

        assertEquals(2, summary.trackCount());
        assertEquals(5, summary.actionCount()); // alice: 2 x + 1 alpha; bg_layer1: 2 alpha
        assertEquals(2500.0, summary.durationMs(), 0.001);
        assertTrue(summary.affectedEntityNames().contains("alice"));
        assertTrue(summary.affectedEntityNames().contains("bg_layer1"));
        assertEquals(2, summary.affectedEntityNames().size());
    }

    @Test
    void flagsLargeExportsAboveThreshold() {
        AnimationProject small = sampleProject();
        TimelineData smallData = small.toTimelineData("demo");
        TimelineExportSummary smallSummary = TimelineExportSummary.of(small, sampleGeneratedCode(), smallData);
        assertFalse(smallSummary.isLarge());

        AnimationProject large = new AnimationProject();
        large.setTotalDurationMs(60000);
        StringBuilder bigCode = new StringBuilder();
        for (int t = 0; t < 60; t++) {
            String entityName = "entity" + t;
            EntityTrack track = large.getOrCreateTrack(entityName);
            bigCode.append("  track ").append(entityName).append(" {\n");
            for (int k = 0; k < 10; k++) {
                track.addKeyframe(PropertyType.X, new Keyframe(k * 100, k));
                bigCode.append("    keyframe x ").append(k * 100).append(' ').append(k).append('\n');
            }
            bigCode.append("  }\n");
        }
        TimelineData largeData = large.toTimelineData("demo-large");
        TimelineExportSummary largeSummary = TimelineExportSummary.of(large, bigCode.toString(), largeData);
        assertTrue(largeSummary.isLarge());
    }
}

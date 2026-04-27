package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TimelineDataDiagnosticsTest {

    @Test
    void flagsRuntimeStructureProblems() {
        TimelineData data = new TimelineData("", 100);

        TimelineData.Track blank = new TimelineData.Track(" ");
        blank.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(Double.NaN, 10, Easing.Type.LINEAR));
        data.addTrack(blank);

        TimelineData.Track camera = new TimelineData.Track("__camera__");
        camera.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 5, Easing.Type.LINEAR));
        data.addTrack(camera);

        TimelineData.Track hero = new TimelineData.Track("hero");
        hero.addKeyframe(TimelineData.Property.CAMERA_X, new TimelineData.Keyframe(0, 20, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.ALPHA, new TimelineData.Keyframe(10, 2.0, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.ALPHA, new TimelineData.Keyframe(10, 0.5, Easing.Type.LINEAR));
        data.addTrack(hero);
        data.addTrack(new TimelineData.Track("hero"));

        List<TimelineDataDiagnostics.Message> messages = TimelineDataDiagnostics.diagnose(data);

        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("Timeline name is empty")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.ERROR
                && message.description().contains("Track has no entity target")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("Multiple runtime tracks target 'hero'")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("Entity property X is stored on the camera track")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("Camera property CAMERA_X is stored on non-camera track")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.ERROR
                && message.description().contains("non-finite time")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("multiple keyframes at 10ms")));
        assertTrue(messages.stream().anyMatch(message ->
            message.severity() == TimelineDataDiagnostics.Severity.WARNING
                && message.description().contains("Alpha value 2 at 10ms is outside [0,1]")));
    }

    @Test
    void timelineDataTracksStaySortedForRuntimeInterpolation() {
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(1000, 100, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(500, 50, Easing.Type.LINEAR));

        List<TimelineData.Keyframe> keyframes = track.getKeyframes(TimelineData.Property.X);

        assertEquals(0.0, keyframes.get(0).getTimeMs(), 0.001);
        assertEquals(500.0, keyframes.get(1).getTimeMs(), 0.001);
        assertEquals(1000.0, keyframes.get(2).getTimeMs(), 0.001);
        assertEquals(25.0, track.getValueAt(TimelineData.Property.X, 250), 0.001);
    }
}

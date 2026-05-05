package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.scene2d.Entity2D;
import org.junit.jupiter.api.Test;

/**
 * Parity tests: compare Puppeteer-style preview sampling (via Track.getValueAt)
 * with TimelineRunner property application for representative tracks.
 * Covers category B (runtime parity) and H (regression protection).
 */
class TimelineParityTest {

    private static class TestAccessor implements SceneAccessor {
        final Entity2D hero = new Entity2D();
        double cameraX, cameraY, cameraZoom = 1.0;

        @Override public Entity2D findEntity(String name) {
            return "hero".equals(name) ? hero : null;
        }
        @Override public void setCameraX(double x) { cameraX = x; }
        @Override public void setCameraY(double y) { cameraY = y; }
        @Override public void setCameraZoom(double z) { cameraZoom = z; }
    }

    @Test
    void linearMoveParityBetweenTrackAndRunner() {
        TimelineData data = new TimelineData("parity_move", 1000);
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(1000, 200, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.Y, new TimelineData.Keyframe(0, 100, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.Y, new TimelineData.Keyframe(1000, 300, Easing.Type.LINEAR));
        data.addTrack(track);

        TestAccessor scene = new TestAccessor();
        TimelineRunner runner = new TimelineRunner(data, scene);

        // Sample at multiple points and verify runner matches Track.getValueAt
        double[] times = {0, 100, 250, 500, 750, 999, 1000};
        for (double t : times) {
            double expectedX = track.getValueAt(TimelineData.Property.X, t);
            double expectedY = track.getValueAt(TimelineData.Property.Y, t);

            // Reset runner
            scene.hero.setPosition(0, 0);
            TimelineRunner freshRunner = new TimelineRunner(data, scene);
            freshRunner.update((long) t);

            assertEquals(expectedX, scene.hero.getX(), 0.01,
                "X mismatch at t=" + t);
            assertEquals(expectedY, scene.hero.getY(), 0.01,
                "Y mismatch at t=" + t);
        }
    }

    @Test
    void easedScaleParityBetweenTrackAndRunner() {
        TimelineData data = new TimelineData("parity_scale", 1000);
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.SCALE_X,
            new TimelineData.Keyframe(0, 1.0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.SCALE_X,
            new TimelineData.Keyframe(1000, 2.0, Easing.Type.EASE_IN_OUT_QUAD));
        data.addTrack(track);

        TestAccessor scene = new TestAccessor();

        double[] times = {0, 250, 500, 750, 1000};
        for (double t : times) {
            double expected = track.getValueAt(TimelineData.Property.SCALE_X, t);

            scene.hero.setScale(1.0, 1.0);
            TimelineRunner freshRunner = new TimelineRunner(data, scene);
            freshRunner.update((long) t);

            assertEquals(expected, scene.hero.getScaleX(), 0.01,
                "ScaleX mismatch at t=" + t);
        }
    }

    @Test
    void cameraTrackParityBetweenTrackAndRunner() {
        TimelineData data = new TimelineData("parity_cam", 1000);
        TimelineData.Track cam = new TimelineData.Track("__camera__");
        cam.addKeyframe(TimelineData.Property.CAMERA_X,
            new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        cam.addKeyframe(TimelineData.Property.CAMERA_X,
            new TimelineData.Keyframe(1000, 100, Easing.Type.EASE_OUT_SINE));
        cam.addKeyframe(TimelineData.Property.CAMERA_ZOOM,
            new TimelineData.Keyframe(0, 1.0, Easing.Type.LINEAR));
        cam.addKeyframe(TimelineData.Property.CAMERA_ZOOM,
            new TimelineData.Keyframe(1000, 1.5, Easing.Type.EASE_OUT_SINE));
        data.addTrack(cam);

        TestAccessor scene = new TestAccessor();

        double[] times = {0, 250, 500, 750, 1000};
        for (double t : times) {
            double expectedCX = cam.getValueAt(TimelineData.Property.CAMERA_X, t);
            double expectedZoom = cam.getValueAt(TimelineData.Property.CAMERA_ZOOM, t);

            scene.cameraX = 0;
            scene.cameraZoom = 1.0;
            TimelineRunner freshRunner = new TimelineRunner(data, scene);
            freshRunner.update((long) t);

            assertEquals(expectedCX, scene.cameraX, 0.01,
                "CameraX mismatch at t=" + t);
            assertEquals(expectedZoom, scene.cameraZoom, 0.01,
                "CameraZoom mismatch at t=" + t);
        }
    }

    @Test
    void alphaAndRotationParity() {
        TimelineData data = new TimelineData("parity_alpha_rot", 1000);
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(0, 1.0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(500, 0.0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(1000, 1.0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ROTATION,
            new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ROTATION,
            new TimelineData.Keyframe(1000, 360, Easing.Type.EASE_IN_OUT_CUBIC));
        data.addTrack(track);

        TestAccessor scene = new TestAccessor();

        double[] times = {0, 250, 500, 750, 1000};
        for (double t : times) {
            double expectedRot = track.getValueAt(TimelineData.Property.ROTATION, t);

            scene.hero.setPosition(0, 0);
            scene.hero.setRotationDeg(0);
            TimelineRunner freshRunner = new TimelineRunner(data, scene);
            freshRunner.update((long) t);

            assertEquals(expectedRot, scene.hero.getRotationDeg(), 0.01,
                "Rotation mismatch at t=" + t);
        }
    }

    @Test
    void unkeyedChannelPreservedByRunner() {
        // If only X is keyed, Y should not be reset to default
        TimelineData data = new TimelineData("unkeyed_preserve", 1000);
        TimelineData.Track track = new TimelineData.Track("hero");
        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(0, 50, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(1000, 150, Easing.Type.LINEAR));
        data.addTrack(track);

        TestAccessor scene = new TestAccessor();
        scene.hero.setPosition(10, 42);
        scene.hero.setScale(1.5, 1.75);

        TimelineRunner runner = new TimelineRunner(data, scene);
        runner.update(500);

        assertEquals(100.0, scene.hero.getX(), 0.01);
        assertEquals(42.0, scene.hero.getY(), 0.01, "Y should be preserved");
        assertEquals(1.5, scene.hero.getScaleX(), 0.01, "ScaleX should be preserved");
        assertEquals(1.75, scene.hero.getScaleY(), 0.01, "ScaleY should be preserved");
    }
}

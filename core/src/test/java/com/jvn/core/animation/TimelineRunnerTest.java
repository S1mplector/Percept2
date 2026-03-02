package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.Entity2D;
import org.junit.jupiter.api.Test;

class TimelineRunnerTest {
    private static class RecordingSceneAccessor implements SceneAccessor {
        private final Entity2D hero = new Entity2D();
        private double cameraX;
        private double cameraY;
        private double cameraZoom = 1.0;
        private int audioPlayCount;
        private String lastAudioTrack = "";
        private String lastAudioChannel = "";
        private double lastAudioVolume;
        private boolean lastAudioLoop;
        private double lastAudioFadeInMs;

        @Override
        public Entity2D findEntity(String name) {
            return "hero".equals(name) ? hero : null;
        }

        @Override
        public void setCameraX(double x) {
            this.cameraX = x;
        }

        @Override
        public void setCameraY(double y) {
            this.cameraY = y;
        }

        @Override
        public void setCameraZoom(double zoom) {
            this.cameraZoom = zoom;
        }

        @Override
        public void playAudioCue(String trackPath, String channel, double volume, boolean loop, double fadeInMs) {
            audioPlayCount++;
            lastAudioTrack = trackPath;
            lastAudioChannel = channel;
            lastAudioVolume = volume;
            lastAudioLoop = loop;
            lastAudioFadeInMs = fadeInMs;
        }
    }

    @Test
    void appliesEntityAndCameraTracksAndDispatchesAudioCue() {
        TimelineData data = new TimelineData("demo", 1000);

        TimelineData.Track hero = new TimelineData.Track("hero");
        hero.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(1000, 100, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.Y, new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.Y, new TimelineData.Keyframe(1000, 50, Easing.Type.LINEAR));
        data.addTrack(hero);

        TimelineData.Track camera = new TimelineData.Track("__camera__");
        camera.addKeyframe(TimelineData.Property.CAMERA_X, new TimelineData.Keyframe(0, 10, Easing.Type.LINEAR));
        camera.addKeyframe(TimelineData.Property.CAMERA_X, new TimelineData.Keyframe(1000, 110, Easing.Type.LINEAR));
        camera.addKeyframe(TimelineData.Property.CAMERA_Y, new TimelineData.Keyframe(0, 20, Easing.Type.LINEAR));
        camera.addKeyframe(TimelineData.Property.CAMERA_Y, new TimelineData.Keyframe(1000, 40, Easing.Type.LINEAR));
        camera.addKeyframe(TimelineData.Property.CAMERA_ZOOM, new TimelineData.Keyframe(0, 1.0, Easing.Type.LINEAR));
        camera.addKeyframe(TimelineData.Property.CAMERA_ZOOM, new TimelineData.Keyframe(1000, 1.5, Easing.Type.LINEAR));
        data.addTrack(camera);

        data.addAudioCue(new TimelineData.AudioCue(
            200,
            "assets/audio/bgm/theme.mp3",
            "music",
            0.7,
            true,
            300
        ));

        RecordingSceneAccessor scene = new RecordingSceneAccessor();
        TimelineRunner runner = new TimelineRunner(data, scene);
        runner.update(200);

        assertEquals(20.0, scene.hero.getX(), 0.001);
        assertEquals(10.0, scene.hero.getY(), 0.001);
        assertEquals(30.0, scene.cameraX, 0.001);
        assertEquals(24.0, scene.cameraY, 0.001);
        assertEquals(1.1, scene.cameraZoom, 0.001);

        assertEquals(1, scene.audioPlayCount);
        assertEquals("assets/audio/bgm/theme.mp3", scene.lastAudioTrack);
        assertEquals("music", scene.lastAudioChannel);
        assertEquals(0.7, scene.lastAudioVolume, 0.0001);
        assertTrue(scene.lastAudioLoop);
        assertEquals(300.0, scene.lastAudioFadeInMs, 0.0001);
    }

    @Test
    void replaysAudioCueAtLoopBoundaries() {
        TimelineData data = new TimelineData("looped", 500);
        data.setLooping(true);
        data.addAudioCue(new TimelineData.AudioCue(0, "assets/audio/sfx/chime.wav", "sound", 1.0, false, 0));

        RecordingSceneAccessor scene = new RecordingSceneAccessor();
        TimelineRunner runner = new TimelineRunner(data, scene);

        runner.update(250);
        assertEquals(1, scene.audioPlayCount);
        runner.update(250);
        assertEquals(2, scene.audioPlayCount);
    }

    @Test
    void preservesUntouchedAxesWhenOnlyOneAxisIsKeyed() {
        TimelineData data = new TimelineData("axis_preserve", 1000);
        TimelineData.Track hero = new TimelineData.Track("hero");
        hero.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(0, 100, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.X, new TimelineData.Keyframe(1000, 300, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.SCALE_X, new TimelineData.Keyframe(0, 2.0, Easing.Type.LINEAR));
        hero.addKeyframe(TimelineData.Property.SCALE_X, new TimelineData.Keyframe(1000, 3.0, Easing.Type.LINEAR));
        data.addTrack(hero);

        RecordingSceneAccessor scene = new RecordingSceneAccessor();
        scene.hero.setPosition(10, 42);
        scene.hero.setScale(1.5, 1.75);

        TimelineRunner runner = new TimelineRunner(data, scene);
        runner.update(500);

        assertEquals(200.0, scene.hero.getX(), 0.001);
        assertEquals(42.0, scene.hero.getY(), 0.001);
        assertEquals(2.5, scene.hero.getScaleX(), 0.001);
        assertEquals(1.75, scene.hero.getScaleY(), 0.001);
    }
}

package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimelineDataParserTest {

    @Test
    void parsesCameraAndAudioActionsFromInlineTimeline() {
        String inline = """
            timeline {
              move "hero" {
                x: 120
                y: 80
                dur: 100
              }
              wait 50
              cameraMove {
                x: 320
                y: 180
                dur: 200
                easing: ease_out
              }
              cameraZoom {
                zoom: 1.25
                dur: 200
              }
              playAudio "assets/audio/bgm/theme.mp3" {
                volume: 0.8
                loop: true
                bgm: true
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_demo", inline);
        assertNotNull(data);
        assertEquals(250.0, data.getDurationMs(), 0.001);

        TimelineData.Track heroTrack = data.getTrack("hero");
        assertNotNull(heroTrack);
        assertEquals(120.0, heroTrack.getValueAt(TimelineData.Property.X, 100), 0.001);
        assertEquals(80.0, heroTrack.getValueAt(TimelineData.Property.Y, 100), 0.001);

        TimelineData.Track cameraTrack = data.getTrack("__camera__");
        assertNotNull(cameraTrack);
        assertEquals(320.0, cameraTrack.getValueAt(TimelineData.Property.CAMERA_X, 250), 0.001);
        assertEquals(180.0, cameraTrack.getValueAt(TimelineData.Property.CAMERA_Y, 250), 0.001);
        assertEquals(1.25, cameraTrack.getValueAt(TimelineData.Property.CAMERA_ZOOM, 250), 0.001);

        assertEquals(1, data.getAudioCues().size());
        TimelineData.AudioCue cue = data.getAudioCues().get(0);
        assertEquals(50.0, cue.getTimeMs(), 0.001);
        assertEquals("assets/audio/bgm/theme.mp3", cue.getTrackPath());
        assertEquals("music", cue.getChannel());
        assertEquals(0.8, cue.getVolume(), 0.001);
        assertTrue(cue.isLoop());
    }

    @Test
    void defaultsPlayAudioChannelToSoundWhenBgmFlagMissing() {
        String inline = """
            timeline {
              wait 120
              playAudio "assets/audio/sfx/click.wav" {
                volume: 0.4
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_audio_defaults", inline);
        assertEquals(120.0, data.getDurationMs(), 0.001);
        assertEquals(1, data.getAudioCues().size());
        TimelineData.AudioCue cue = data.getAudioCues().get(0);
        assertEquals("sound", cue.getChannel());
        assertEquals(120.0, cue.getTimeMs(), 0.001);
        assertFalse(cue.isLoop());
        assertEquals(0.4, cue.getVolume(), 0.001);
    }
}

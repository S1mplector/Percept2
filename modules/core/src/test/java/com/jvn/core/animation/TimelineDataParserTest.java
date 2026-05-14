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

    @Test
    void moveWithDurationInterpolatesFromCurrentValue() {
        String inline = """
            timeline {
              move "hero" {
                x: 100
                y: 50
                dur: 200
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_move_interp", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);
        assertEquals(200.0, data.getDurationMs(), 0.001);
        assertEquals(0.0, hero.getValueAt(TimelineData.Property.X, 0), 0.001);
        assertEquals(50.0, hero.getValueAt(TimelineData.Property.X, 100), 0.001);
        assertEquals(100.0, hero.getValueAt(TimelineData.Property.X, 200), 0.001);
        assertEquals(25.0, hero.getValueAt(TimelineData.Property.Y, 100), 0.001);
    }

    @Test
    void parsesInterpolationModeAndCubicBezierEasing() {
        String inline = """
            timeline {
              move "hero" {
                x: 100
                dur: 100
                interp: hold
              }
              wait 100
              move "hero" {
                x: 200
                dur: 100
                easing: cubic_bezier(0.25, 0.1, 0.25, 1.0)
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_interp_bezier", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);

        assertEquals(0.0, hero.getValueAt(TimelineData.Property.X, 50), 0.001);
        assertEquals(100.0, hero.getValueAt(TimelineData.Property.X, 100), 0.001);
        assertEquals(200.0, hero.getValueAt(TimelineData.Property.X, 200), 0.001);

        var xKeyframes = hero.getKeyframes(TimelineData.Property.X);
        assertEquals(Easing.Interpolation.HOLD, xKeyframes.get(1).getInterpolation());
        assertEquals(Easing.Type.CUSTOM, xKeyframes.get(2).getEasing());
        assertTrue(xKeyframes.get(2).hasBezierParams());
    }

    @Test
    void parsesCssStyleCubicBezierEasing() {
        String inline = """
            timeline {
              move "hero" {
                x: 160
                dur: 120
                easing: cubic-bezier(0.22, 1.0, 0.36, 1.0)
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_css_bezier", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);

        var xKeyframes = hero.getKeyframes(TimelineData.Property.X);
        assertEquals(Easing.Type.CUSTOM, xKeyframes.get(1).getEasing());
        assertTrue(xKeyframes.get(1).hasBezierParams());
        assertEquals(0.22, xKeyframes.get(1).getBezierParams()[0], 0.001);
        assertEquals(1.0, xKeyframes.get(1).getBezierParams()[1], 0.001);
        assertEquals(0.36, xKeyframes.get(1).getBezierParams()[2], 0.001);
        assertEquals(1.0, xKeyframes.get(1).getBezierParams()[3], 0.001);
    }

    @Test
    void parsesSpringAndNamedCurveEasings() {
        String inline = """
            timeline {
              move "hero" {
                x: 100
                dur: 120
                easing: spring(220, 24, 1.0, 0)
              }
              wait 120
              move "hero" {
                x: 240
                dur: 150
                easing: hero_pop
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_spring_named", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);

        var xKeyframes = hero.getKeyframes(TimelineData.Property.X);
        assertEquals(Easing.Type.SPRING, xKeyframes.get(1).getEasing());
        assertEquals(Easing.Type.HERO_POP, xKeyframes.get(2).getEasing());
        assertTrue(xKeyframes.get(1).hasEasingParams());
        assertEquals(220.0, xKeyframes.get(1).getEasingParams()[0], 0.001);
    }

    @Test
    void parsesMultiPointCurveEasing() {
        String inline = """
            timeline {
              move "hero" {
                x: 220
                dur: 180
                easing: curve(0.20, 0.05, 0.45, 0.92, 0.72, 1.08)
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_curve", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);

        var xKeyframes = hero.getKeyframes(TimelineData.Property.X);
        assertEquals(Easing.Type.CURVE, xKeyframes.get(1).getEasing());
        assertTrue(xKeyframes.get(1).hasEasingParams());
        assertEquals(6, xKeyframes.get(1).getEasingParams().length);
    }

    @Test
    void acceptsPuppeteerExporterAliasesForRotateAndScale() {
        String inline = """
            timeline {
              rotate "hero" {
                deg: 45
                duration: 100
              }
              scale "hero" {
                sx: 1.5
                sy: 0.75
                duration: 100
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("inline_aliases", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);
        assertEquals(45.0, hero.getValueAt(TimelineData.Property.ROTATION, 100), 0.001);
        assertEquals(1.5, hero.getValueAt(TimelineData.Property.SCALE_X, 100), 0.001);
        assertEquals(0.75, hero.getValueAt(TimelineData.Property.SCALE_Y, 100), 0.001);
    }

    @Test
    void parsesCompactPuppeteerExportWithMirrorAndCustomProperties() {
        String inline = """
            timeline {
              parallel {
                move "hero" { x:100 y:200 dur:300 easing:ease_in_out }
                mirror "hero" { mirrorX:1 dur:300 }
                property "hero" { key:"color.m04" value:0.25 dur:300 }
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("compact_export", inline);
        TimelineData.Track hero = data.getTrack("hero");
        assertNotNull(hero);
        assertEquals(300.0, data.getDurationMs(), 0.001);
        assertEquals(100.0, hero.getValueAt(TimelineData.Property.X, 300), 0.001);
        assertEquals(200.0, hero.getValueAt(TimelineData.Property.Y, 300), 0.001);
        assertEquals(1.0, hero.getValueAt(TimelineData.Property.MIRROR_X, 300), 0.001);
        assertEquals(0.25, hero.getCustomValueAt("color.m04", 300, 0.0), 0.001);
    }
}

package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Round-trip tests: source -> model -> export -> model -> export.
 * Verifies semantic equality and stable output (category A + H).
 */
class CodeRoundTripTest {

    @Test
    void simpleMoveRoundTrip() {
        String original = """
            timeline {
              move "hero" {
                x: 100
                y: 50
                dur: 200
              }
            }
            """;

        // Pass 1: import
        AnimationProject project1 = CodeImporter.importCode("rt_move", original);
        assertNotNull(project1);
        EntityTrack hero1 = project1.getTrack("hero");
        assertNotNull(hero1);
        assertTrue(hero1.hasKeyframes(PropertyType.X));
        assertTrue(hero1.hasKeyframes(PropertyType.Y));

        // Pass 1: export
        String exported1 = CodeExporter.export(project1);
        assertNotNull(exported1);
        assertTrue(exported1.contains("timeline"));

        // Pass 2: import the export
        AnimationProject project2 = CodeImporter.importCode("rt_move_2", exported1);
        assertNotNull(project2);
        EntityTrack hero2 = project2.getTrack("hero");
        assertNotNull(hero2);

        // Pass 2: export again
        String exported2 = CodeExporter.export(project2);
        assertNotNull(exported2);

        // Semantic equality: X at endpoint should match
        assertEquals(
            hero1.getValueAt(PropertyType.X, 200),
            hero2.getValueAt(PropertyType.X, 200),
            0.5, "X at t=200 should match across round-trips"
        );
        assertEquals(
            hero1.getValueAt(PropertyType.Y, 200),
            hero2.getValueAt(PropertyType.Y, 200),
            0.5, "Y at t=200 should match across round-trips"
        );
    }

    @Test
    void sceneMetadataRoundTripRestoresImportedStartKeyframes() {
        AnimationProject project = new AnimationProject();
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.X, new Keyframe(0, 420, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.X, new Keyframe(500, 640, Easing.Type.EASE_OUT_QUAD));
        hero.addKeyframe(PropertyType.Y, new Keyframe(0, 690, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.Y, new Keyframe(500, 620, Easing.Type.EASE_OUT_QUAD));
        project.setSceneEntitySnapshots(List.of(new AnimationProject.SceneEntitySnapshot(
            "hero",
            "sprite",
            "assets/characters/hero.png",
            420,
            690,
            240,
            480,
            0.5,
            1.0,
            3,
            true,
            1.0
        )));

        String exported = CodeExporter.exportNamed(project, "hero_reopen");
        assertTrue(exported.contains("@jvn-puppeteer-entity"));

        AnimationProject imported = CodeImporter.importCode("hero_reopen", exported);
        EntityTrack importedHero = imported.getTrack("hero");
        assertNotNull(importedHero);
        assertEquals(420.0, importedHero.getKeyframes(PropertyType.X).get(0).getValue(), 0.001);
        assertEquals(690.0, importedHero.getKeyframes(PropertyType.Y).get(0).getValue(), 0.001);
        assertEquals("assets/characters/hero.png", imported.getSceneEntitySnapshot("hero").imagePath());
    }

    @Test
    void stageMetadataRoundTripPreservesLightingHandoff() {
        AnimationProject project = new AnimationProject();
        project.setStageContext(new AnimationProject.StageContext(
            "sunset_park",
            "config/stage/sunset_park.stagepreset",
            "park_day",
            "hero",
            3,
            1,
            2
        ));

        String exported = CodeExporter.exportNamed(project, "hero_reopen");
        assertTrue(exported.contains("@jvn-puppeteer-stage"));

        AnimationProject imported = CodeImporter.importCode("hero_reopen", exported);
        assertNotNull(imported.getStageContext());
        assertEquals("sunset_park", imported.getStageContext().presetId());
        assertEquals("config/stage/sunset_park.stagepreset", imported.getStageContext().sourcePath());
        assertEquals(3, imported.getStageContext().lightCount());
        assertEquals(1, imported.getStageContext().occluderCount());
        assertEquals(2, imported.getStageContext().responseZoneCount());
    }

    @Test
    void editorMetadataRoundTripPreservesLoopRegionAndOriginalLocalKeyframes() {
        AnimationProject project = new AnimationProject();
        project.setName("rigged_intro");
        project.setTotalDurationMs(1800);
        project.setLooping(true);
        project.setLoopRegion(250, 1250);

        EntityGroup rig = project.getOrCreateGroup("hero_rig");
        rig.setLayerOrder(7);
        rig.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(0, 100, Easing.Type.LINEAR));
        rig.getGroupTrack().addKeyframe(PropertyType.X, new Keyframe(500, 140.654321, Easing.Type.EASE_OUT_QUAD));

        EntityTrack hand = project.getOrCreateTrack("hero_hand");
        hand.setLayerOrder(2);
        hand.addKeyframe(PropertyType.X, new Keyframe(0, 10, Easing.Type.LINEAR));
        hand.addKeyframe(PropertyType.X, new Keyframe(500, 30.123456, Easing.Type.EASE_IN_OUT_CUBIC));
        project.addEntityToGroup("hero_hand", "hero_rig");

        String exported = CodeExporter.exportNamed(project, "rigged_intro");
        assertTrue(exported.contains("@jvn-puppeteer-project"));
        assertTrue(exported.contains("@jvn-puppeteer-group"));
        assertTrue(exported.contains("@jvn-puppeteer-key"));

        AnimationProject imported = CodeImporter.importCode("rigged_intro", exported);
        assertEquals(1800.0, imported.getTotalDurationMs(), 0.001);
        assertTrue(imported.isLooping());
        assertTrue(imported.hasLoopRegion());
        assertEquals(250.0, imported.getLoopStartMs(), 0.001);
        assertEquals(1250.0, imported.getLoopEndMs(), 0.001);

        EntityGroup importedRig = imported.getGroup("hero_rig");
        EntityTrack importedHand = imported.getTrack("hero_hand");
        assertNotNull(importedRig);
        assertNotNull(importedHand);
        assertEquals("hero_rig", importedHand.getParentGroupName());
        assertEquals(7, importedRig.getLayerOrder());
        assertEquals(2, importedHand.getLayerOrder());
        assertEquals(140.654321, importedRig.getGroupTrack().getValueAt(PropertyType.X, 500), 0.000001);
        assertEquals(30.123456, importedHand.getValueAt(PropertyType.X, 500), 0.000001);
        assertEquals(170.777777, imported.computeValueAt("hero_hand", PropertyType.X, 500), 0.000001);
    }

    @Test
    void cameraAndAudioRoundTrip() {
        String original = """
            timeline {
              cameraMove {
                x: 50
                y: -20
                dur: 300
                easing: ease_in_out_sine
              }
              cameraZoom {
                zoom: 1.25
                dur: 300
              }
              playAudio "assets/sfx/click.wav" {
                volume: 0.8
              }
            }
            """;

        AnimationProject project1 = CodeImporter.importCode("rt_cam", original);
        assertNotNull(project1);

        // Verify camera track imported
        EntityTrack camTrack = project1.getTrack("__camera__");
        assertNotNull(camTrack, "Camera track should be imported");
        assertTrue(camTrack.hasKeyframes(PropertyType.CAMERA_X));
        assertTrue(camTrack.hasKeyframes(PropertyType.CAMERA_ZOOM));

        // Verify audio cue imported
        assertEquals(1, project1.getAudioCues().size());
        assertEquals("assets/sfx/click.wav", project1.getAudioCues().get(0).getAudioFile());

        // Export and re-import
        String exported = CodeExporter.export(project1);
        AnimationProject project2 = CodeImporter.importCode("rt_cam_2", exported);
        assertNotNull(project2.getTrack("__camera__"));
    }

    @Test
    void eventCueRoundTrip() {
        String original = """
            timeline {
              wait 100
              event "expression" {
                target: lavender
                value: smile
              }
              wait 50
              event "dialogue_marker" {
                id: beat_1
              }
            }
            """;

        AnimationProject project1 = CodeImporter.importCode("rt_evt", original);
        assertNotNull(project1);
        assertEquals(2, project1.getEditorEventCues().size());

        EditorEventCue e1 = project1.getEditorEventCues().get(0);
        assertEquals("expression", e1.getType());
        assertEquals("lavender", e1.getPayloadValue("target"));

        // Export and check it contains event blocks
        String exported = CodeExporter.export(project1);
        assertTrue(
            exported.contains("expression \"lavender\"")
                || exported.contains("event \"expression\""),
            "expected expression action/event header"
        );
        assertTrue(exported.contains("event \"dialogue_marker\""));

        // Re-import
        AnimationProject project2 = CodeImporter.importCode("rt_evt_2", exported);
        assertEquals(2, project2.getEditorEventCues().size());
        assertEquals("expression", project2.getEditorEventCues().get(0).getType());
    }

    @Test
    void exportFormattingIsDeterministic() {
        AnimationProject project = new AnimationProject();
        project.setName("deterministic");
        EntityTrack track = project.getOrCreateTrack("sprite");
        track.addKeyframe(PropertyType.X, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.X, new Keyframe(500, 100, Easing.Type.EASE_OUT_QUAD));
        track.addKeyframe(PropertyType.Y, new Keyframe(0, 0, Easing.Type.LINEAR));
        track.addKeyframe(PropertyType.Y, new Keyframe(500, 50, Easing.Type.EASE_OUT_QUAD));

        String export1 = CodeExporter.export(project);
        String export2 = CodeExporter.export(project);
        assertEquals(export1, export2, "Export output should be deterministic");
    }

    @Test
    void multiActionWithWaitRoundTrip() {
        String original = """
            timeline {
              move "a" {
                x: 100
                dur: 200
              }
              wait 200
              fade "a" {
                alpha: 0.5
                dur: 300
              }
              wait 100
              scale "a" {
                x: 2.0
                y: 2.0
                dur: 150
                easing: ease_in_out_cubic
              }
            }
            """;

        AnimationProject p = CodeImporter.importCode("rt_multi", original);
        assertNotNull(p);
        EntityTrack t = p.getTrack("a");
        assertNotNull(t);
        assertTrue(t.hasKeyframes(PropertyType.X));
        assertTrue(t.hasKeyframes(PropertyType.ALPHA));
        assertTrue(t.hasKeyframes(PropertyType.SCALE_X));
        assertTrue(t.hasKeyframes(PropertyType.SCALE_Y));

        // Values at endpoints
        assertEquals(100.0, t.getValueAt(PropertyType.X, 200), 0.5);
        assertEquals(0.5, t.getValueAt(PropertyType.ALPHA, 500), 0.1);
        assertEquals(2.0, t.getValueAt(PropertyType.SCALE_X, 600 + 150), 0.5);
    }

    @Test
    void interpolationControlModesRoundTrip() {
        String original = """
            timeline {
              move "hero" {
                x: 100
                dur: 200
                interp: hold
              }
            }
            """;

        AnimationProject p1 = CodeImporter.importCode("rt_interp", original);
        EntityTrack t1 = p1.getTrack("hero");
        assertNotNull(t1);
        assertEquals(0.0, t1.getValueAt(PropertyType.X, 100), 0.001);
        assertEquals(100.0, t1.getValueAt(PropertyType.X, 200), 0.001);

        String exported = CodeExporter.export(p1);
        assertTrue(exported.contains("interp: hold"));

        AnimationProject p2 = CodeImporter.importCode("rt_interp_2", exported);
        EntityTrack t2 = p2.getTrack("hero");
        assertNotNull(t2);
        assertEquals(0.0, t2.getValueAt(PropertyType.X, 100), 0.001);
        assertEquals(100.0, t2.getValueAt(PropertyType.X, 200), 0.001);
    }

    @Test
    void springAndNamedCurvesRoundTrip() {
        String original = """
            timeline {
              move "hero" {
                x: 220
                dur: 300
                easing: spring(220, 24, 1.0, 0)
              }
              wait 300
              move "hero" {
                x: 480
                dur: 260
                easing: hero_pop
              }
            }
            """;

        AnimationProject project = CodeImporter.importCode("rt_spring_named", original);
        EntityTrack hero = project.getTrack("hero");
        assertNotNull(hero);

        String exported = CodeExporter.export(project);
        assertTrue(exported.contains("easing: spring(220, 24, 1, 0)"));
        assertTrue(exported.contains("easing: hero_pop"));

        AnimationProject roundTripped = CodeImporter.importCode("rt_spring_named_2", exported);
        EntityTrack hero2 = roundTripped.getTrack("hero");
        assertNotNull(hero2);
        assertEquals(hero.getKeyframes(PropertyType.X).get(1).getEasing(), hero2.getKeyframes(PropertyType.X).get(1).getEasing());
        assertEquals(hero.getKeyframes(PropertyType.X).get(1).getEasingParams()[0],
            hero2.getKeyframes(PropertyType.X).get(1).getEasingParams()[0], 0.001);
        assertEquals(Easing.Type.HERO_POP, hero2.getKeyframes(PropertyType.X).get(2).getEasing());
    }
}

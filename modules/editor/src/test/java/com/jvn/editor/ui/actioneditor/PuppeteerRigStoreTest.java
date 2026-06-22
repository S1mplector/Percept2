package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PuppeteerRigStoreTest {

    @Test
    void roundTripPreservesGroupsTrackMembershipAndLayerLinks(@TempDir Path tempDir) throws Exception {
        AnimationProject project = new AnimationProject();
        project.getOrCreateTrack("lily_head");
        EntityTrack hair = project.getOrCreateTrack("lily_hair_back");
        hair.setLayerOrder(-3);
        hair.setLocked(true);

        EntityGroup character = project.getOrCreateGroup("lily_character");
        character.setLayerOrder(12);
        EntityGroup face = project.getOrCreateGroup("lily_face");
        face.setExpanded(false);
        project.addGroupToGroup("lily_face", "lily_character");
        project.addEntityToGroup("lily_head", "lily_face");

        project.setConstraint("lily_hair_back",
            Constraint.parentChild("lily_head", -8.5, 3.25, true, false));

        PuppeteerRigStore.save(tempDir.toFile(), project);

        AnimationProject restored = new AnimationProject();
        restored.getOrCreateTrack("lily_head");
        restored.getOrCreateTrack("lily_hair_back");
        restored.getOrCreateGroup("stale_group");
        restored.setConstraint("stale_layer", Constraint.lookAt("lily_head"));

        PuppeteerRigStore.load(tempDir.toFile(), restored);

        assertNull(restored.getGroup("stale_group"));
        assertEquals("lily_character", restored.getGroup("lily_face").getParentGroupName());
        assertEquals("lily_face", restored.getTrack("lily_head").getParentGroupName());
        assertEquals(12, restored.getGroup("lily_character").getLayerOrder());
        assertFalse(restored.getGroup("lily_face").isExpanded());
        assertEquals(-3, restored.getTrack("lily_hair_back").getLayerOrder());
        assertEquals(Constraint.parentChild("lily_head", -8.5, 3.25, true, false),
            restored.getConstraint("lily_hair_back"));
        assertNull(restored.getConstraint("stale_layer"));
    }

    @Test
    void saveDeletesEmptyRigFile(@TempDir Path tempDir) throws Exception {
        AnimationProject project = new AnimationProject();
        project.getOrCreateGroup("temporary");
        PuppeteerRigStore.save(tempDir.toFile(), project);

        Path file = tempDir.resolve("config").resolve("puppeteer").resolve("rig.properties");
        assertTrue(Files.isRegularFile(file));

        PuppeteerRigStore.save(tempDir.toFile(), new AnimationProject());

        assertFalse(Files.exists(file));
    }

    @Test
    void loadDoesNotCreateGroupsOrTracksAbsentFromCurrentScene(@TempDir Path tempDir) {
        AnimationProject previousScene = new AnimationProject();
        previousScene.getOrCreateTrack("john_neutral_body_default");
        previousScene.getOrCreateGroup("john_neutral");
        previousScene.addEntityToGroup("john_neutral_body_default", "john_neutral");
        previousScene.getOrCreateTrack("lily_neutral_body_default");
        previousScene.getOrCreateGroup("lily_neutral");
        previousScene.addEntityToGroup("lily_neutral_body_default", "lily_neutral");
        PuppeteerRigStore.save(tempDir.toFile(), previousScene);

        AnimationProject emptyScene = new AnimationProject();

        PuppeteerRigStore.load(tempDir.toFile(), emptyScene);

        assertEquals(0, emptyScene.getTrackCount());
        assertNull(emptyScene.getGroup("john_neutral"));
        assertNull(emptyScene.getGroup("lily_neutral"));
    }

    @Test
    void loadRestoresOnlyTheRigSubsetPresentInTheCurrentScene(@TempDir Path tempDir) {
        AnimationProject previousScene = new AnimationProject();
        previousScene.getOrCreateTrack("john_neutral_body_default");
        EntityTrack lily = previousScene.getOrCreateTrack("lily_neutral_body_default");
        lily.setLayerOrder(7);
        previousScene.getOrCreateGroup("john_neutral");
        previousScene.getOrCreateGroup("lily_neutral");
        previousScene.addEntityToGroup("john_neutral_body_default", "john_neutral");
        previousScene.addEntityToGroup("lily_neutral_body_default", "lily_neutral");
        PuppeteerRigStore.save(tempDir.toFile(), previousScene);

        AnimationProject currentScene = new AnimationProject();
        currentScene.getOrCreateTrack("lily_neutral_body_default");

        PuppeteerRigStore.load(tempDir.toFile(), currentScene);

        assertNull(currentScene.getTrack("john_neutral_body_default"));
        assertNull(currentScene.getGroup("john_neutral"));
        assertNotNull(currentScene.getGroup("lily_neutral"));
        assertEquals("lily_neutral",
            currentScene.getTrack("lily_neutral_body_default").getParentGroupName());
        assertEquals(7, currentScene.getTrack("lily_neutral_body_default").getLayerOrder());
    }

    @Test
    void loadRejectsUndeclaredParentsAndUnsupportedVersions(@TempDir Path tempDir) throws Exception {
        Path rigDir = Files.createDirectories(tempDir.resolve("config").resolve("puppeteer"));
        Path rigFile = rigDir.resolve("rig.properties");
        Files.writeString(rigFile, """
            rig.version=1
            group.count=0
            track.count=1
            track.0.name=hero
            track.0.parent=ghost_group
            constraint.count=0
            """);
        AnimationProject currentScene = new AnimationProject();
        currentScene.getOrCreateTrack("hero");

        PuppeteerRigStore.load(tempDir.toFile(), currentScene);

        assertNull(currentScene.getGroup("ghost_group"));
        assertNull(currentScene.getTrack("hero").getParentGroupName());

        currentScene.getOrCreateGroup("current_group");
        currentScene.addEntityToGroup("hero", "current_group");
        Files.writeString(rigFile, """
            rig.version=999
            group.count=0
            track.count=0
            constraint.count=0
            """);

        PuppeteerRigStore.load(tempDir.toFile(), currentScene);

        assertNotNull(currentScene.getGroup("current_group"));
        assertEquals("current_group", currentScene.getTrack("hero").getParentGroupName());
    }
}

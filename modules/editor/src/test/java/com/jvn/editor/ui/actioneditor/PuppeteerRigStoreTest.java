package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}

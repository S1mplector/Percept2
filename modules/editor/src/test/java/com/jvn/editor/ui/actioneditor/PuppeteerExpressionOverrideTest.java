package com.jvn.editor.ui.actioneditor;

import com.jvn.core.scene2d.Sprite2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuppeteerExpressionOverrideTest {

    @Test
    void snapshotCapturesCurrentImagePathAndVisibility() {
        Sprite2D sprite = new Sprite2D("chars/alice/neutral_body.png", 100, 100);
        sprite.setVisible(true);

        PuppeteerExpressionOverride override = new PuppeteerExpressionOverride();
        List<PuppeteerExpressionOverride.SpriteSnapshot> snapshots =
            override.snapshot(List.of(sprite));

        assertEquals(1, snapshots.size());
        assertEquals("chars/alice/neutral_body.png", snapshots.get(0).imagePath());
        assertTrue(snapshots.get(0).visible());
    }

    @Test
    void revertRestoresImagePathAndVisibilityAfterMutation() {
        Sprite2D sprite = new Sprite2D("chars/alice/neutral_body.png", 100, 100);
        sprite.setVisible(true);

        PuppeteerExpressionOverride override = new PuppeteerExpressionOverride();
        List<PuppeteerExpressionOverride.SpriteSnapshot> snapshots =
            override.snapshot(List.of(sprite));

        // Simulate a preview mutation (as applyLayeredExpressionCue-style code would do)
        sprite.setImagePath("chars/alice/happy_body.png");
        sprite.setVisible(false);

        override.revert(snapshots);

        assertEquals("chars/alice/neutral_body.png", sprite.getImagePath());
        assertTrue(sprite.isVisible());
    }

    @Test
    void revertHandlesEmptySnapshotListWithoutError() {
        PuppeteerExpressionOverride override = new PuppeteerExpressionOverride();
        override.revert(List.of());
        // no exception = pass
    }
}

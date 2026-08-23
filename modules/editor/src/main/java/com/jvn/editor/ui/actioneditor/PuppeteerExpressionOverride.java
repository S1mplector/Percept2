package com.jvn.editor.ui.actioneditor;

import com.jvn.core.scene2d.Sprite2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot/revert helper for transiently previewing an expression's sprite
 * state without persisting to project data. See design doc
 * docs/superpowers/specs/2026-08-19-puppeteer-expression-preview-design.md.
 */
public final class PuppeteerExpressionOverride {

    public record SpriteSnapshot(Sprite2D sprite, String imagePath, boolean visible) {}

    public List<SpriteSnapshot> snapshot(List<Sprite2D> sprites) {
        List<SpriteSnapshot> snapshots = new ArrayList<>();
        if (sprites == null) return snapshots;
        for (Sprite2D sprite : sprites) {
            if (sprite == null) continue;
            snapshots.add(new SpriteSnapshot(sprite, sprite.getImagePath(), sprite.isVisible()));
        }
        return snapshots;
    }

    public void revert(List<SpriteSnapshot> snapshots) {
        if (snapshots == null) return;
        for (SpriteSnapshot snap : snapshots) {
            if (snap == null || snap.sprite() == null) continue;
            snap.sprite().setImagePath(snap.imagePath());
            snap.sprite().setVisible(snap.visible());
        }
    }
}

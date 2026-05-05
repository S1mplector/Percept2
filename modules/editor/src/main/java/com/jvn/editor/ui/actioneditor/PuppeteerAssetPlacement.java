package com.jvn.editor.ui.actioneditor;

import com.jvn.editor.ui.ProjectViewportSpec;

final class PuppeteerAssetPlacement {
    record Placement(
        double width,
        double height,
        double originX,
        double originY,
        double x,
        double y,
        double z
    ) {}

    private PuppeteerAssetPlacement() {
    }

    static Placement plan(
        PuppeteerAssetPlacementRole role,
        ProjectViewportSpec.Dimensions viewport,
        double naturalWidth,
        double naturalHeight
    ) {
        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
        double viewportWidth = Math.max(1.0, viewport != null ? viewport.width() : ProjectViewportSpec.DEFAULT_WIDTH);
        double viewportHeight = Math.max(1.0, viewport != null ? viewport.height() : ProjectViewportSpec.DEFAULT_HEIGHT);
        double centerX = viewportWidth * 0.5;
        double centerY = viewportHeight * 0.5;

        double safeNaturalWidth = naturalWidth > 1.0 ? naturalWidth : -1.0;
        double safeNaturalHeight = naturalHeight > 1.0 ? naturalHeight : -1.0;

        return switch (resolvedRole) {
            case BACKGROUND -> planBackground(viewportWidth, viewportHeight, centerX, centerY, safeNaturalWidth, safeNaturalHeight);
            case CHARACTER -> planCharacter(viewportWidth, viewportHeight, centerX, safeNaturalWidth, safeNaturalHeight);
            case PROP -> planProp(viewportWidth, viewportHeight, centerX, centerY, safeNaturalWidth, safeNaturalHeight);
        };
    }

    private static Placement planBackground(
        double viewportWidth,
        double viewportHeight,
        double centerX,
        double centerY,
        double naturalWidth,
        double naturalHeight
    ) {
        double width = viewportWidth;
        double height = viewportHeight;
        if (naturalWidth > 0.0 && naturalHeight > 0.0) {
            double scale = Math.max(viewportWidth / naturalWidth, viewportHeight / naturalHeight);
            width = naturalWidth * scale;
            height = naturalHeight * scale;
        }
        return new Placement(width, height, 0.5, 0.5, centerX, centerY, PuppeteerAssetPlacementRole.BACKGROUND.defaultZ());
    }

    private static Placement planCharacter(
        double viewportWidth,
        double viewportHeight,
        double centerX,
        double naturalWidth,
        double naturalHeight
    ) {
        double targetHeight = viewportHeight * 0.85;
        double width = Math.max(160.0, viewportWidth * 0.24);
        double height = targetHeight;
        if (naturalWidth > 0.0 && naturalHeight > 0.0) {
            double scale = Math.min(
                targetHeight / naturalHeight,
                (viewportWidth * 0.55) / naturalWidth
            );
            width = naturalWidth * scale;
            height = naturalHeight * scale;
        }
        return new Placement(width, height, 0.5, 1.0, centerX, viewportHeight, PuppeteerAssetPlacementRole.CHARACTER.defaultZ());
    }

    private static Placement planProp(
        double viewportWidth,
        double viewportHeight,
        double centerX,
        double centerY,
        double naturalWidth,
        double naturalHeight
    ) {
        double targetWidth = viewportWidth * 0.28;
        double targetHeight = viewportHeight * 0.32;
        double width = targetWidth;
        double height = targetHeight;
        if (naturalWidth > 0.0 && naturalHeight > 0.0) {
            double scale = Math.min(1.0, Math.min(targetWidth / naturalWidth, targetHeight / naturalHeight));
            width = naturalWidth * scale;
            height = naturalHeight * scale;
        }
        return new Placement(width, height, 0.5, 0.5, centerX, centerY, PuppeteerAssetPlacementRole.PROP.defaultZ());
    }
}

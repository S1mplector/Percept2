package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.jvn.editor.ui.ProjectViewportSpec;

class PuppeteerAssetPlacementTest {

    private static final ProjectViewportSpec.Dimensions VIEWPORT =
        new ProjectViewportSpec.Dimensions(1920, 1080);

    @Test
    void backgroundPlacementCoversViewportAndCenters() {
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            PuppeteerAssetPlacementRole.BACKGROUND,
            VIEWPORT,
            1600,
            900
        );

        assertEquals(1920.0, placement.width(), 0.0001);
        assertEquals(1080.0, placement.height(), 0.0001);
        assertEquals(0.5, placement.originX(), 0.0001);
        assertEquals(0.5, placement.originY(), 0.0001);
        assertEquals(960.0, placement.x(), 0.0001);
        assertEquals(540.0, placement.y(), 0.0001);
        assertEquals(-1000.0, placement.z(), 0.0001);
    }

    @Test
    void characterPlacementUsesBottomCenterAndViewportScale() {
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            PuppeteerAssetPlacementRole.CHARACTER,
            VIEWPORT,
            1000,
            2000
        );

        assertEquals(459.0, placement.width(), 0.0001);
        assertEquals(918.0, placement.height(), 0.0001);
        assertEquals(0.5, placement.originX(), 0.0001);
        assertEquals(1.0, placement.originY(), 0.0001);
        assertEquals(960.0, placement.x(), 0.0001);
        assertEquals(1080.0, placement.y(), 0.0001);
        assertEquals(0.0, placement.z(), 0.0001);
    }

    @Test
    void propPlacementCentersAndClampsOversizedAssets() {
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            PuppeteerAssetPlacementRole.PROP,
            VIEWPORT,
            2400,
            1600
        );

        assertEquals(518.4, placement.width(), 0.0001);
        assertEquals(345.6, placement.height(), 0.0001);
        assertEquals(0.5, placement.originX(), 0.0001);
        assertEquals(0.5, placement.originY(), 0.0001);
        assertEquals(960.0, placement.x(), 0.0001);
        assertEquals(540.0, placement.y(), 0.0001);
        assertEquals(100.0, placement.z(), 0.0001);
    }
}

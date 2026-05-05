package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

class PuppeteerEasingCatalogTest {

    @Test
    void buildEntriesIncludesBuiltinsAndProjectPresets() {
        List<PuppeteerEasingCatalog.Entry> entries = PuppeteerEasingCatalog.buildEntries(List.of(
            new PuppeteerEasingPresetStore.Preset(
                "hero_soft",
                "Hero Soft",
                EasingSpec.cubicBezier(0.16, 1.0, 0.3, 1.0))
        ));

        assertTrue(entries.stream().anyMatch(entry ->
            !entry.isPreset() && "Linear".equals(entry.label())));
        assertTrue(entries.stream().anyMatch(entry ->
            entry.isPreset() && "Hero Soft".equals(entry.label())));
    }

    @Test
    void matchForSpecPrefersPresetOverBuiltinFallback() {
        EasingSpec custom = EasingSpec.cubicBezier(0.20, 0.90, 0.20, 1.00);
        List<PuppeteerEasingCatalog.Entry> entries = PuppeteerEasingCatalog.buildEntries(List.of(
            new PuppeteerEasingPresetStore.Preset("snap", "Snap", custom)
        ));

        PuppeteerEasingCatalog.Entry exact = PuppeteerEasingCatalog.matchForSpec(entries, custom);
        assertNotNull(exact);
        assertEquals("Snap", exact.label());
        assertTrue(exact.isPreset());

        PuppeteerEasingCatalog.Entry fallback = PuppeteerEasingCatalog.matchForSpec(
            entries,
            EasingSpec.of(Easing.Type.CUSTOM, new double[]{0.4, 0.0, 0.2, 1.0})
        );
        assertNotNull(fallback);
        assertEquals("Custom Cubic Bezier", fallback.label());
        assertTrue(!fallback.isPreset());
    }

    @Test
    void filterMatchesPresetNamesAndDslTokens() {
        List<PuppeteerEasingCatalog.Entry> entries = PuppeteerEasingCatalog.buildEntries(List.of(
            new PuppeteerEasingPresetStore.Preset(
                "soft_landing",
                "Soft Landing",
                EasingSpec.cubicBezier(0.22, 1.0, 0.36, 1.0))
        ));

        List<PuppeteerEasingCatalog.Entry> byName = PuppeteerEasingCatalog.filter(entries, "soft");
        assertTrue(byName.stream().anyMatch(entry -> "Soft Landing".equals(entry.label())));

        List<PuppeteerEasingCatalog.Entry> byDsl = PuppeteerEasingCatalog.filter(entries, "cubic_bezier");
        assertTrue(byDsl.stream().anyMatch(entry -> "Soft Landing".equals(entry.label())));
    }
}

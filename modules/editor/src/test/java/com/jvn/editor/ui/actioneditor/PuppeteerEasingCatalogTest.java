package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.animation.EasingExtensions;
import com.jvn.plugin.api.ExtensionEntry;
import com.jvn.plugin.api.ExtensionRegistry;
import com.jvn.plugin.api.Registration;
import com.jvn.plugin.api.animation.AnimationEasing;
import java.util.Optional;
import static com.jvn.plugin.api.animation.AnimationEasingDefinition.easing;

class PuppeteerEasingCatalogTest {
    @AfterEach void clearExtensions() { EasingExtensions.clear(); }

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

    @Test
    void includesPluginMetadataAndMatchesExtensionSpecs() {
        AnimationEasing pluginEasing = easing("Elastic Pop")
            .description("Playful hero entrance")
            .category("Expressive")
            .evaluate(frame -> frame.progress());
        EasingExtensions.install(registry("studio.elastic-pop", pluginEasing));

        List<PuppeteerEasingCatalog.Entry> entries = PuppeteerEasingCatalog.buildEntries(List.of());
        PuppeteerEasingCatalog.Entry plugin = entries.stream()
            .filter(PuppeteerEasingCatalog.Entry::isPlugin)
            .findFirst().orElseThrow();
        assertEquals("Elastic Pop", plugin.label());
        assertEquals("Expressive", plugin.group());
        assertEquals("Plugin", plugin.badge());
        assertEquals(plugin, PuppeteerEasingCatalog.matchForSpec(
            entries, EasingSpec.tryParse("studio.elastic-pop")));
        assertTrue(PuppeteerEasingCatalog.filter(entries, "hero entrance").contains(plugin));
    }

    private static ExtensionRegistry<AnimationEasing> registry(String id, AnimationEasing easing) {
        ExtensionEntry<AnimationEasing> entry = new ExtensionEntry<>(id, "test", easing);
        return new ExtensionRegistry<>() {
            @Override public Registration register(String ignored, AnimationEasing value) {
                throw new UnsupportedOperationException();
            }
            @Override public Optional<AnimationEasing> find(String requested) {
                return id.equals(requested) ? Optional.of(easing) : Optional.empty();
            }
            @Override public List<ExtensionEntry<AnimationEasing>> entries() { return List.of(entry); }
        };
    }
}

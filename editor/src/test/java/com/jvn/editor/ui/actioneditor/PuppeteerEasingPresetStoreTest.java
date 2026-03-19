package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

class PuppeteerEasingPresetStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsProjectPresetFile() throws Exception {
        List<PuppeteerEasingPresetStore.Preset> expected = List.of(
            new PuppeteerEasingPresetStore.Preset(
                "snappy_out",
                "Snappy Out",
                EasingSpec.cubicBezier(0.16, 1.00, 0.30, 1.00)),
            new PuppeteerEasingPresetStore.Preset(
                "heroish",
                "Hero-ish",
                EasingSpec.of(Easing.Type.HERO_POP))
        );

        PuppeteerEasingPresetStore.save(tempDir.toFile(), expected);

        Path presetFile = tempDir.resolve(PuppeteerEasingPresetStore.CONFIG_PATH);
        assertTrue(Files.isRegularFile(presetFile));
        assertIterableEquals(expected, PuppeteerEasingPresetStore.load(tempDir.toFile()));
    }

    @Test
    void skipsMalformedEntriesAndBackfillsMissingIds() throws Exception {
        Path presetFile = tempDir.resolve(PuppeteerEasingPresetStore.CONFIG_PATH);
        Files.createDirectories(presetFile.getParent());
        Files.writeString(presetFile, """
            preset.001.name=Broken
            preset.001.spec=not_a_curve
            preset.002.name=Soft Landing
            preset.002.spec=cubic_bezier(0.22, 1.0, 0.36, 1.0)
            """, StandardCharsets.UTF_8);

        List<PuppeteerEasingPresetStore.Preset> loaded = PuppeteerEasingPresetStore.load(tempDir.toFile());

        assertEquals(1, loaded.size());
        assertEquals("soft_landing", loaded.get(0).id());
        assertEquals("Soft Landing", loaded.get(0).name());
        assertEquals(EasingSpec.cubicBezier(0.22, 1.0, 0.36, 1.0), loaded.get(0).spec());
    }
}

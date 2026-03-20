package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetPickerPanelTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveUniqueImportTargetUsesOriginalNameWhenFree() {
        Path importDir = tempDir.resolve("assets/puppeteer/imported");

        Path target = AssetPickerPanel.resolveUniqueImportTarget(importDir, "hero.png");

        assertEquals(importDir.resolve("hero.png"), target);
    }

    @Test
    void resolveUniqueImportTargetAppendsNumericSuffixWhenNameExists() throws Exception {
        Path importDir = tempDir.resolve("assets/puppeteer/imported");
        Files.createDirectories(importDir);
        Files.createFile(importDir.resolve("hero.png"));
        Files.createFile(importDir.resolve("hero-2.png"));

        Path target = AssetPickerPanel.resolveUniqueImportTarget(importDir, "hero.png");

        assertEquals(importDir.resolve("hero-3.png"), target);
    }
}

package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void buildCharpresetSnippetSanitizesIdentifiersAndFormatsPreset() {
        String snippet = AssetPickerPanel.buildCharpresetSnippet(
            "Hero Face",
            "Happy Smile",
            List.of(
                new AssetPickerPanel.CharpresetSnippetLayer("Hair Front", "assets/characters/hero/hair/front.png"),
                new AssetPickerPanel.CharpresetSnippetLayer("Eyes", "assets/characters/hero/eyes/open.png")
            )
        );

        assertEquals(
            "@charlayer hero_face hair_front assets/characters/hero/hair/front.png\n"
                + "@charlayer hero_face eyes assets/characters/hero/eyes/open.png\n"
                + "@charpreset hero_face happy_smile $hair_front | $eyes\n",
            snippet
        );
    }

    @Test
    void resolveCharpresetImportTargetUsesCharacterAndLayerFolders() {
        Path projectRoot = tempDir;

        Path target = AssetPickerPanel.resolveCharpresetImportTarget(projectRoot, "Hero Face", "Hair Front", "layer.png");

        assertEquals(
            projectRoot.resolve("assets/characters/hero_face/hair_front/layer.png"),
            target
        );
    }

    @Test
    void appendCharpresetSnippetSeparatesAppendedBlocks() throws Exception {
        Path scriptFile = tempDir.resolve("scene.vns");
        Files.writeString(scriptFile, "@show hero @neutral\n", StandardCharsets.UTF_8);

        AssetPickerPanel.appendCharpresetSnippet(scriptFile, "@charpreset hero neutral $base\n");

        String content = Files.readString(scriptFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("@show hero @neutral\n\n@charpreset hero neutral $base\n"));
    }
}

package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void resolveCharpresetPathsExpandsNestedCharacterGroups() {
        Map<String, Map<String, String>> layers = Map.of(
            "john",
            Map.of(
                "body", "assets/characters/john/body.png",
                "head_base", "assets/characters/john/head.png",
                "eyes_neutral", "assets/characters/john/eyes.png",
                "mouth_smile", "assets/characters/john/mouth.png"
            )
        );
        Map<String, Map<String, List<String>>> groups = new LinkedHashMap<>();
        Map<String, List<String>> johnGroups = new LinkedHashMap<>();
        groups.put("john", johnGroups);

        johnGroups.put(
            "face",
            AssetPickerPanel.resolveGroupLayerIds(layers, groups, "john", "$eyes_neutral | $mouth_smile")
        );
        johnGroups.put(
            "head",
            AssetPickerPanel.resolveGroupLayerIds(layers, groups, "john", "pivot=0.5,0.28 $head_base | $face")
        );

        assertEquals(
            List.of(
                "assets/characters/john/body.png",
                "assets/characters/john/head.png",
                "assets/characters/john/eyes.png",
                "assets/characters/john/mouth.png"
            ),
            AssetPickerPanel.resolvePresetPaths(layers, groups, Map.of(), "john", "$body | $head")
        );
    }
}

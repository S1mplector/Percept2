package com.jvn.core.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class TranslationExtractorTest {

  @Test
  void extractsVnsAndMenuTextWithStableSourceKeys() throws Exception {
    Path root = Files.createTempDirectory("jvn-translation-extract-");
    Files.createDirectories(root.resolve("scripts/story"));
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("scripts/story/prologue.vns"), """
        @character alice "Alice" color=#897CBF
        alice: Welcome to JVN.
        - Open the door -> open
        [hud Save complete]
        """);
    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        titleText=Main Menu
        items=start
        item.start.label=Start Game
        item.start.action=new_game
        """);

    List<TranslationEntry> entries = TranslationExtractor.extract(root);

    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Alice"))));
    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Welcome to JVN."))));
    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Open the door"))));
    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Save complete"))));
    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Main Menu"))));
    assertTrue(entries.stream().anyMatch(e -> e.key().equals(Localization.sourceKey("Start Game"))));
  }

  @Test
  void writerPreservesExistingTranslationsAndKeepsObsoleteManualEntries() throws Exception {
    Path root = Files.createTempDirectory("jvn-translation-write-");
    Files.createDirectories(root.resolve("config/locales"));
    TranslationEntry entry = new TranslationEntry(
        Localization.sourceKey("Start Game"),
        "Start Game",
        "label",
        List.of("config/menu/menus/main.menu:3 property item.start.label"));
    Path output = root.resolve("config/locales/ja.properties");
    Files.writeString(output, Localization.sourceKey("Start Game") + "=開始\nmanual.note=keep me\n");

    TranslationCatalogWriter.WriteResult result =
        TranslationCatalogWriter.write(output, List.of(entry), "ja", "en", true);
    String catalog = Files.readString(output);

    assertEquals(1, result.preserved());
    assertEquals(1, result.retained());
    assertTrue(catalog.contains(Localization.sourceKey("Start Game") + "=開始"));
    assertTrue(catalog.contains("manual.note=keep me"));
  }
}

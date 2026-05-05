package com.jvn.core.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;

class LocalizationTest {

  @Test
  void loadsProjectRootStringTablesThroughAssetCatalogOverlay() throws Exception {
    Path root = Files.createTempDirectory("jvn-localization-");
    Files.createDirectories(root.resolve("game/strings"));
    Files.writeString(root.resolve("game/strings/ja.properties"), "menu.start=Start JA\n");

    AssetCatalog.setDefaultManager(new FilesystemAssetManager(root));
    try {
      Localization.init("ja", Thread.currentThread().getContextClassLoader());
      assertEquals("Start JA", Localization.t("menu.start"));
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
      Localization.init("en", Thread.currentThread().getContextClassLoader());
    }
  }
}

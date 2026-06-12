package com.jvn.core.vn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.VnState;

class VnReactiveScreenLoaderTest {

  @Test
  void loadsReactiveScreenAndEvaluatesVariablesAndButtonState() throws Exception {
    Path root = Files.createTempDirectory("jvn-reactive-screen-");
    Files.createDirectories(root.resolve("config/screens"));
    Files.writeString(root.resolve("config/screens/shop.screen"), """
        title=Shop
        text=Coins: ${coins}
        modal=true
        buttons=buy,leave
        button.buy.label=Buy potion
        button.buy.action=return
        button.buy.target=buy
        button.buy.enabledIf=coins >= 10
        button.leave.label=Leave
        button.leave.action=return
        button.leave.target=leave
        """);

    AssetCatalog.setDefaultManager(new FilesystemAssetManager(root));
    try {
      VnReactiveScreenLoader.LoadResult result = VnReactiveScreenLoader.loadFromAssets("shop");
      assertNotNull(result.screen());

      VnState state = new VnState();
      state.setVariable("coins", 5);
      VnReactiveOverlayScreenSpec overlay = new VnReactiveOverlayScreenSpec(result.screen(), state, true);
      assertEquals("Coins: 5", overlay.getText());
      List<VnOverlayButtonSpec> buttons = overlay.getButtons();
      assertEquals(2, buttons.size());
      assertFalse(buttons.get(0).enabled());
      assertTrue(buttons.get(1).enabled());

      state.setVariable("coins", 12);
      assertTrue(overlay.getButtons().get(0).enabled());
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
    }
  }
}

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

  @Test
  void loadsComposableFacetNodesAndEvaluatesTheirBindings() throws Exception {
    Path root = Files.createTempDirectory("jvn-facet-");
    Files.createDirectories(root.resolve("config/facets"));
    Files.writeString(root.resolve("config/facets/status.facet"), """
        title=Status
        nodes=content,name,health
        node.content.type=group
        node.content.x=0.08
        node.content.y=0.18
        node.content.width=0.84
        node.content.height=0.60
        node.name.type=text
        node.name.parent=content
        node.name.text=${player_name}
        node.name.height=0.25
        node.health.type=bar
        node.health.parent=content
        node.health.value=${health_ratio}
        node.health.visibleIf=health_ratio > 0
        node.health.y=0.40
        node.health.height=0.12
        """);

    AssetCatalog.setDefaultManager(new FilesystemAssetManager(root));
    try {
      VnReactiveScreenLoader.LoadResult result = VnReactiveScreenLoader.loadFromAssets("status");
      assertNotNull(result.screen());
      assertNotNull(result.screen().facet());
      assertEquals(3, result.screen().facet().nodes().size());

      VnState state = new VnState();
      state.setVariable("player_name", "Lavender");
      state.setVariable("health_ratio", 0.75);
      VnReactiveOverlayScreenSpec overlay = new VnReactiveOverlayScreenSpec(result.screen(), state, false);
      VnFacetSpec.Node name = result.screen().facet().nodes().get(1);
      VnFacetSpec.Node health = result.screen().facet().nodes().get(2);
      assertEquals("Lavender", overlay.resolveFacetText(name.text()));
      assertEquals(0.75, overlay.resolveFacetNumber(health.value(), 0), 0.001);
      assertTrue(overlay.isFacetNodeVisible(health));
    } finally {
      AssetCatalog.setDefaultManager(new ClasspathAssetManager());
    }
  }
}

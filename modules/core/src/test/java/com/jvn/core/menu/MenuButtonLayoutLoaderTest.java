package com.jvn.core.menu;

import com.jvn.core.menu.config.MenuButtonLayoutLoader;
import com.jvn.core.menu.config.MenuButtonLayoutSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MenuButtonLayoutLoaderTest {

  @Test
  void parsesEmptyProperties() {
    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(new Properties());
    assertEquals("default", spec.menuId());
    assertEquals("default", spec.resolution());
    assertNull(spec.menuType());
    assertTrue(spec.buttons().isEmpty());
  }

  @Test
  void parsesNullProperties() {
    MenuButtonLayoutLoader.ParseResult result = MenuButtonLayoutLoader.parseWithDiagnostics(null);
    assertNotNull(result.spec());
    assertFalse(result.diagnostics().isEmpty());
  }

  @Test
  void parsesHeaderFields() {
    Properties p = new Properties();
    p.setProperty("menuId", "main");
    p.setProperty("resolution", "1920x1080");
    p.setProperty("menuType", "save");

    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(p);
    assertEquals("main", spec.menuId());
    assertEquals("1920x1080", spec.resolution());
    assertEquals("save", spec.menuType());
  }

  @Test
  void parsesButtonBoundsFromExplicitIds() {
    Properties p = new Properties();
    p.setProperty("menuId", "main");
    p.setProperty("button.ids", "new_game,load");

    p.setProperty("button.new_game.label", "New Game");
    p.setProperty("button.new_game.boundsX", "0.25");
    p.setProperty("button.new_game.boundsY", "0.30");
    p.setProperty("button.new_game.boundsW", "0.50");
    p.setProperty("button.new_game.boundsH", "0.08");
    p.setProperty("button.new_game.tag", "primary");
    p.setProperty("button.new_game.asset", "assets/ui/btn.png");

    p.setProperty("button.load.label", "Load Game");
    p.setProperty("button.load.boundsX", "0.25");
    p.setProperty("button.load.boundsY", "0.40");
    p.setProperty("button.load.boundsW", "0.50");
    p.setProperty("button.load.boundsH", "0.08");

    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(p);
    assertEquals(2, spec.buttons().size());

    MenuButtonLayoutSpec.ButtonBounds ng = spec.buttons().get(0);
    assertEquals("new_game", ng.id());
    assertEquals("New Game", ng.label());
    assertEquals("primary", ng.tag());
    assertEquals(0.25, ng.boundsX(), 1e-6);
    assertEquals(0.30, ng.boundsY(), 1e-6);
    assertEquals(0.50, ng.boundsW(), 1e-6);
    assertEquals(0.08, ng.boundsH(), 1e-6);
    assertEquals("assets/ui/btn.png", ng.assetPath());
    assertTrue(ng.hasBounds());

    MenuButtonLayoutSpec.ButtonBounds load = spec.buttons().get(1);
    assertEquals("load", load.id());
    assertEquals("Load Game", load.label());
    assertNull(load.tag());
    assertTrue(load.hasBounds());
  }

  @Test
  void discoversButtonIdsWhenNotExplicit() {
    Properties p = new Properties();
    p.setProperty("button.quit.label", "Quit");
    p.setProperty("button.quit.boundsX", "0.3");
    p.setProperty("button.quit.boundsY", "0.8");
    p.setProperty("button.quit.boundsW", "0.4");
    p.setProperty("button.quit.boundsH", "0.06");

    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(p);
    assertEquals(1, spec.buttons().size());
    assertEquals("quit", spec.buttons().get(0).id());
  }

  @Test
  void preservesPerButtonExtras() {
    Properties p = new Properties();
    p.setProperty("button.ids", "btn1");
    p.setProperty("button.btn1.label", "Button 1");
    p.setProperty("button.btn1.boundsX", "0.1");
    p.setProperty("button.btn1.customKey", "customValue");

    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(p);
    assertEquals(1, spec.buttons().size());
    assertEquals("customValue", spec.buttons().get(0).extras().get("customKey"));
  }

  @Test
  void preservesTopLevelExtras() {
    Properties p = new Properties();
    p.setProperty("menuId", "main");
    p.setProperty("customTop", "topValue");

    MenuButtonLayoutSpec spec = MenuButtonLayoutLoader.parse(p);
    assertEquals("topValue", spec.extras().get("customTop"));
  }

  @Test
  void roundTripsViaSerialize() {
    MenuButtonLayoutSpec original = new MenuButtonLayoutSpec(
        "save_menu",
        "1280x720",
        "save",
        List.of(
            new MenuButtonLayoutSpec.ButtonBounds(
                "slot1", "Slot 1", "slot",
                0.1, 0.2, 0.8, 0.1,
                "btn.png", "btn_hover.png", null,
                Map.of()
            ),
            new MenuButtonLayoutSpec.ButtonBounds(
                "slot2", "Slot 2", null,
                0.1, 0.35, 0.8, 0.1,
                null, null, null,
                Map.of("custom", "val")
            )
        ),
        Map.of()
    );

    String serialized = MenuButtonLayoutLoader.serialize(original);
    assertNotNull(serialized);
    assertTrue(serialized.contains("menuId=save_menu"));
    assertTrue(serialized.contains("resolution=1280x720"));
    assertTrue(serialized.contains("menuType=save"));
    assertTrue(serialized.contains("button.ids=slot1,slot2"));
    assertTrue(serialized.contains("button.slot1.boundsX=0.1"));
    assertTrue(serialized.contains("button.slot1.asset=btn.png"));
    assertTrue(serialized.contains("button.slot2.custom=val"));

    // Parse back
    Properties p = new Properties();
    try {
      p.load(new java.io.StringReader(serialized));
    } catch (Exception e) {
      fail("Failed to parse serialized output: " + e.getMessage());
    }
    MenuButtonLayoutSpec parsed = MenuButtonLayoutLoader.parse(p);
    assertEquals("save_menu", parsed.menuId());
    assertEquals("1280x720", parsed.resolution());
    assertEquals("save", parsed.menuType());
    assertEquals(2, parsed.buttons().size());
    assertEquals("slot1", parsed.buttons().get(0).id());
    assertEquals("Slot 1", parsed.buttons().get(0).label());
    assertEquals(0.1, parsed.buttons().get(0).boundsX(), 1e-6);
    assertEquals("val", parsed.buttons().get(1).extras().get("custom"));
  }

  @Test
  void roundTripsViaToProperties() {
    MenuButtonLayoutSpec original = new MenuButtonLayoutSpec(
        "main", "default", null,
        List.of(
            new MenuButtonLayoutSpec.ButtonBounds(
                "start", "Start", "primary",
                0.2, 0.5, 0.6, 0.1,
                null, null, null, Map.of()
            )
        ),
        Map.of()
    );

    Properties p = MenuButtonLayoutLoader.toProperties(original);
    assertEquals("main", p.getProperty("menuId"));
    assertEquals("start", p.getProperty("button.ids"));
    assertEquals("0.2", p.getProperty("button.start.boundsX"));
    assertEquals("primary", p.getProperty("button.start.tag"));
    assertNull(p.getProperty("menuType")); // null menuType should not be set

    MenuButtonLayoutSpec parsed = MenuButtonLayoutLoader.parse(p);
    assertEquals(1, parsed.buttons().size());
    assertEquals("start", parsed.buttons().get(0).id());
    assertTrue(parsed.buttons().get(0).hasBounds());
  }

  @Test
  void diagnosticsReportInvalidDoubles() {
    Properties p = new Properties();
    p.setProperty("button.ids", "bad");
    p.setProperty("button.bad.boundsX", "notANumber");

    MenuButtonLayoutLoader.ParseResult result = MenuButtonLayoutLoader.parseWithDiagnostics(p);
    assertFalse(result.diagnostics().isEmpty());
    assertTrue(result.diagnostics().get(0).contains("notANumber"));
    // boundsX should be null since it failed to parse
    assertNull(result.spec().buttons().get(0).boundsX());
  }

  @Test
  void hasBoundsReturnsFalseWhenPartial() {
    MenuButtonLayoutSpec.ButtonBounds partial = new MenuButtonLayoutSpec.ButtonBounds(
        "test", null, null,
        0.1, 0.2, null, 0.1,
        null, null, null, Map.of()
    );
    assertFalse(partial.hasBounds());
  }

  @Test
  void emptySpecFactory() {
    MenuButtonLayoutSpec empty = MenuButtonLayoutSpec.empty("test", "800x600", "custom");
    assertEquals("test", empty.menuId());
    assertEquals("800x600", empty.resolution());
    assertEquals("custom", empty.menuType());
    assertTrue(empty.buttons().isEmpty());
    assertTrue(empty.extras().isEmpty());
  }

  @Test
  void emitsDiagnosticsForDuplicateIdsPartialBoundsAndTypoKeys() {
    Properties p = new Properties();
    p.setProperty("button.ids", "start,start");
    p.setProperty("button.start.boundsX", "0.1");
    p.setProperty("button.start.hoverAsseet", "assets/ui/hover.png");

    MenuButtonLayoutLoader.ParseResult result = MenuButtonLayoutLoader.parseWithDiagnostics(p);

    assertEquals(1, result.spec().buttons().size());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Duplicate button id 'start'")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("partial bounds")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("did you mean 'hoverAsset'")));
  }
}

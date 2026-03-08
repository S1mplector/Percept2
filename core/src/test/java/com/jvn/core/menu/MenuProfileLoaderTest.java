package com.jvn.core.menu;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;

class MenuProfileLoaderTest {

  @Test
  void loadsDefaultsWhenConfigFilesAreMissing() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-defaults-");
    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));

    MenuProfile profile = MenuProfileLoader.load(assets);
    MenuScreenSpec main = profile.screen("main");

    assertNotNull(main);
    assertNotNull(profile.screen("history"));
    assertEquals(5, main.items().size());
    assertEquals(MenuActionType.NEW_GAME, main.items().get(0).action().type());
    assertEquals(MenuActionType.LOAD_MENU, main.items().get(1).action().type());
    assertEquals(MenuActionType.SETTINGS_MENU, main.items().get(2).action().type());
    assertEquals(MenuActionType.OPEN_MENU, main.items().get(3).action().type());
    assertEquals("extras", main.items().get(3).action().target());
    assertEquals(MenuActionType.OPEN_MENU, main.items().get(4).action().type());
    assertEquals("confirm_exit", main.items().get(4).action().target());
  }

  @Test
  void loadsMenusLayoutsAndStylesFromRegistry() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-registry-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.createDirectories(root.resolve("config/menu/layouts"));
    Files.createDirectories(root.resolve("config/menu/styles"));

    Files.writeString(root.resolve("config/menu/menu.registry"), """
        defaultMenu=main
        menus=main,extras
        layouts=default,wide
        styles=default,neon
        """);

    Files.writeString(root.resolve("config/menu/layouts/wide.layout"), """
        listYStart=0.22
        lineHeight=52
        listWidthFactor=0.8
        textAlign=left
        hintsBottomMargin=32
        """);

    Files.writeString(root.resolve("config/menu/styles/neon.style"), """
        itemColor=#44f5ff
        itemSelectedColor=#00ffff
        itemDisabledColor=#145a63
        itemPrefix=>>
        itemSelectedPrefix=**
        itemDisabledPrefix=--
        itemFontFamily=Monaco
        itemFontWeight=BOLD
        itemFontSize=22
        buttonAsset=assets/ui/menu/btn.png
        buttonSelectedAsset=assets/ui/menu/btn_sel.png
        buttonDisabledAsset=assets/ui/menu/btn_dis.png
        buttonTextPaddingX=24
        buttonTextPaddingY=2
        """);

    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        titleText=Neon Title
        hintsText=Use arrows and Enter
        layout=wide
        defaultItemStyle=neon
        items=start,extras,quit
        item.start.label=Start Story
        item.start.action=run_script:scripts/story/prologue.vns
        item.start.bgAsset=assets/ui/menu/start_btn.png
        item.start.boundsX=0.2
        item.start.boundsY=0.3
        item.start.boundsWidth=0.6
        item.start.boundsHeight=0.09
        item.start.slotPreviewEnabled=true
        item.start.slotPreviewPlaceholderAsset=config/menu/assets/slots/empty.png
        item.start.slotPreviewFrameAsset=config/menu/assets/slots/frame.png
        item.start.slotPreviewX=0.58
        item.start.slotPreviewY=0.1
        item.start.slotPreviewWidth=0.36
        item.start.slotPreviewHeight=0.8
        item.extras.label=Extras
        item.extras.action=open_menu
        item.extras.target=extras
        item.extras.style=neon
        item.quit.label=Exit
        item.quit.action=quit
        """);

    Files.writeString(root.resolve("config/menu/menus/extras.menu"), """
        titleText=Extras
        defaultItemStyle=neon
        items=credits,back
        item.credits.label=Credits
        item.credits.action=noop
        item.back.label=Back
        item.back.action=back
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    assertEquals("main", profile.defaultScreenId());
    MenuScreenSpec main = profile.screen("main");
    assertEquals("Neon Title", main.titleText());
    assertEquals("wide", main.layoutId());
    assertEquals(3, main.items().size());
    assertEquals(MenuActionType.RUN_SCRIPT, main.items().get(0).action().type());
    assertEquals("scripts/story/prologue.vns", main.items().get(0).action().target());
    assertEquals("assets/ui/menu/start_btn.png", main.items().get(0).buttonAssetPath());
    assertEquals(Double.valueOf(0.2), main.items().get(0).boundsX());
    assertEquals(Double.valueOf(0.6), main.items().get(0).boundsWidth());
    assertTrue(main.items().get(0).slotPreviewEnabled());
    assertEquals("config/menu/assets/slots/empty.png", main.items().get(0).slotPreviewPlaceholderAssetPath());
    assertEquals("config/menu/assets/slots/frame.png", main.items().get(0).slotPreviewFrameAssetPath());
    assertEquals(Double.valueOf(0.58), main.items().get(0).slotPreviewX());
    assertEquals(Double.valueOf(0.36), main.items().get(0).slotPreviewWidth());
    assertEquals(MenuActionType.OPEN_MENU, main.items().get(1).action().type());
    assertEquals("extras", main.items().get(1).action().target());

    MenuScreenSpec extras = profile.screen("extras");
    assertEquals(2, extras.items().size());
    assertEquals(MenuActionType.BACK, extras.items().get(1).action().type());

    assertEquals(52.0, profile.layout("wide").lineHeight());
    assertEquals("left", profile.layout("wide").textAlign());
    assertEquals("#00ffff", profile.style("neon").itemSelectedColor());
    assertEquals(Integer.valueOf(22), profile.style("neon").itemFontSize());
    assertEquals("assets/ui/menu/btn.png", profile.style("neon").buttonAssetPath());
    assertEquals(Double.valueOf(24), profile.style("neon").buttonTextPaddingX());
  }

  @Test
  void fallsBackToNoopForUnknownAction() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-unknown-action-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=main\n");
    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        items=test
        item.test.action=totally_custom_action
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    assertEquals(MenuActionType.NOOP, profile.screen("main").items().get(0).action().type());
    assertEquals("totally_custom_action", profile.screen("main").items().get(0).action().actionKey());
  }

  @Test
  void autoDiscoversMenusWithoutRegistry() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-autodiscovery-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.createDirectories(root.resolve("config/menu/layouts"));
    Files.createDirectories(root.resolve("config/menu/styles"));

    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        titleText=Auto Main
        items=start,quit
        item.start.action=new_game
        item.quit.action=quit
        """);
    Files.writeString(root.resolve("config/menu/menus/extras.menu"), """
        titleText=Extras
        items=back
        item.back.action=back
        """);
    Files.writeString(root.resolve("config/menu/layouts/compact.layout"), """
        listYStart=0.3
        lineHeight=32
        """);
    Files.writeString(root.resolve("config/menu/styles/silver.style"), """
        itemColor=#cccccc
        itemSelectedColor=#ffffff
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    assertEquals("Auto Main", profile.screen("main").titleText());
    assertEquals(MenuActionType.BACK, profile.screen("extras").items().get(0).action().type());
    assertEquals(32.0, profile.layout("compact").lineHeight());
    assertEquals("#cccccc", profile.style("silver").itemColor());
  }

  @Test
  void supportsExtendsForLayoutsStylesAndMenus() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-extends-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.createDirectories(root.resolve("config/menu/layouts"));
    Files.createDirectories(root.resolve("config/menu/styles"));
    Files.writeString(root.resolve("config/menu/menu.registry"), """
        defaultMenu=extras
        menus=main,extras
        layouts=default,wide
        styles=default,neon,soft
        """);

    Files.writeString(root.resolve("config/menu/layouts/wide.layout"), """
        extends=default
        listYStart=0.2
        lineHeight=48
        """);

    Files.writeString(root.resolve("config/menu/styles/neon.style"), """
        itemColor=#00ffff
        itemSelectedColor=#ff00ff
        itemPrefix=>>
        """);
    Files.writeString(root.resolve("config/menu/styles/soft.style"), """
        extends=neon
        itemSelectedColor=#8cff66
        """);

    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        titleText=Main
        items=start,extras
        item.start.action=new_game
        item.extras.action=open_menu
        item.extras.target=extras
        """);
    Files.writeString(root.resolve("config/menu/menus/extras.menu"), """
        extends=main
        titleText=Extras
        defaultItemStyle=soft
        items=gallery,back
        item.gallery.action=noop
        item.back.action=back
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    assertEquals("extras", profile.defaultScreenId());
    assertEquals(48.0, profile.layout("wide").lineHeight());
    assertEquals("#8cff66", profile.style("soft").itemSelectedColor());
    assertEquals(">>", profile.style("soft").itemPrefix());
    assertEquals("soft", profile.screen("extras").defaultStyleId());
    assertEquals(MenuActionType.BACK, profile.screen("extras").items().get(1).action().type());
  }

  @Test
  void emitsDiagnosticsForInvalidValuesAndUnknownAction() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-diagnostics-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.createDirectories(root.resolve("config/menu/layouts"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=main\nlayouts=default\n");
    Files.writeString(root.resolve("config/menu/layouts/default.layout"), """
        lineHeight=nan-value
        """);
    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        wrapSelection=not_bool
        items=bad
        item.bad.action=unknown_custom_action
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfileLoader.LoadResult result = MenuProfileLoader.loadWithDiagnostics(assets);

    assertEquals(MenuActionType.NOOP, result.profile().screen("main").items().get(0).action().type());
    assertEquals("unknown_custom_action", result.profile().screen("main").items().get(0).action().actionKey());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("lineHeight")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("wrapSelection")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("unknown_custom_action")));
  }

  @Test
  void emitsDiagnosticsForUnknownKeysAndInvalidItemBounds() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-hardening-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=main\n");
    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        defaultItemStyl=default
        items=play,play
        item.play.action=open_menu
        item.play.boundsX=0.2
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfileLoader.LoadResult result = MenuProfileLoader.loadWithDiagnostics(assets);

    assertEquals(1, result.profile().screen("main").items().size());
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Unknown menu screen key 'defaultItemStyl'")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("Duplicate item id 'play'")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("partial bounds")));
    assertTrue(result.diagnostics().stream().anyMatch(d -> d.contains("OPEN_MENU action requires a target")));
  }
}

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
    assertNotNull(profile.screen("help"));
    assertTrue(profile.hasLayout("settings"));
    assertTrue(profile.hasStyle("settings"));
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
        titleAlign=left
        hintsBottomMargin=32
        subtitleGap=18
        hintsAlign=right
        hintsX=0.82
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
        subtitleText=A sharper supporting line
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
        item.extras.renderAs=section
        item.quit.label=Exit
        item.quit.action=quit
        """);

    Files.writeString(root.resolve("config/menu/menus/extras.menu"), """
        titleText=Extras
        defaultItemStyle=neon
        items=credits,guide,back
        item.credits.label=Credits
        item.credits.action=noop
        item.guide.label=Use Enter to confirm choices. Esc always backs out of this screen.
        item.guide.action=noop
        item.guide.enabled=false
        item.guide.renderAs=body
        item.guide.rowSpan=3
        item.guide.bodyAlign=left
        item.back.label=Back
        item.back.action=back
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    assertEquals("main", profile.defaultScreenId());
    MenuScreenSpec main = profile.screen("main");
    assertEquals("Neon Title", main.titleText());
    assertEquals("A sharper supporting line", main.subtitleText());
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
    assertEquals(3, extras.items().size());
    assertEquals("body", extras.items().get(1).extras().get("renderAs"));
    assertEquals("3", extras.items().get(1).extras().get("rowSpan"));
    assertEquals("left", extras.items().get(1).extras().get("bodyAlign"));
    assertEquals(MenuActionType.BACK, extras.items().get(2).action().type());

    assertEquals(52.0, profile.layout("wide").lineHeight());
    assertEquals("left", profile.layout("wide").textAlign());
    assertEquals("left", profile.layout("wide").titleAlign());
    assertEquals(18.0, profile.layout("wide").subtitleGap());
    assertEquals("right", profile.layout("wide").hintsAlign());
    assertEquals(Double.valueOf(0.82), profile.layout("wide").hintsX());
    assertEquals("#00ffff", profile.style("neon").itemSelectedColor());
    assertEquals(Integer.valueOf(22), profile.style("neon").itemFontSize());
    assertEquals("assets/ui/menu/btn.png", profile.style("neon").buttonAssetPath());
    assertEquals(Double.valueOf(24), profile.style("neon").buttonTextPaddingX());
    assertEquals("section", main.items().get(1).extras().get("renderAs"));
  }

  @Test
  void preservesSettingsToggleExtrasFromMenuDsl() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-settings-toggle-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=settings\n");
    Files.writeString(root.resolve("config/menu/menus/settings.menu"), """
        layout=settings
        defaultItemStyle=settings
        items=skip_unread
        item.skip_unread.label=Skip Unread
        item.skip_unread.toggleCheckedAsset=assets/ui/toggle_on.png
        item.skip_unread.toggleUncheckedAsset=assets/ui/toggle_off.png
        item.skip_unread.toggleX=0.61
        item.skip_unread.toggleY=0.48
        item.skip_unread.toggleWidth=0.03
        item.skip_unread.toggleHeight=0.05
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);

    MenuScreenSpec settings = profile.screen("settings");
    assertEquals("assets/ui/toggle_on.png", settings.items().get(0).extras().get("toggleCheckedAsset"));
    assertEquals("0.61", settings.items().get(0).extras().get("toggleX"));
  }

  @Test
  void preservesExplicitBlankScreenTextAndItemLabels() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-blank-text-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=settings\n");
    Files.writeString(root.resolve("config/menu/menus/settings.menu"), """
        titleText=
        subtitleText=
        hintsText=
        layout=settings
        defaultItemStyle=settings
        items=decor,resume
        item.decor.enabled=false
        item.decor.label=
        item.resume.label=
        item.resume.action=back
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfile profile = MenuProfileLoader.load(assets);
    MenuScreenSpec settings = profile.screen("settings");

    assertEquals("", settings.titleText());
    assertEquals("", settings.subtitleText());
    assertEquals("", settings.hintsText());
    assertEquals("", settings.items().get(0).label());
    assertEquals("", settings.items().get(1).label());
  }

  @Test
  void settingsMenuTargetsAreNotFlaggedAsUnused() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-settings-target-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), """
        defaultMenu=settings
        menus=settings,settings_audio
        """);
    Files.writeString(root.resolve("config/menu/menus/settings.menu"), """
        layout=settings
        defaultItemStyle=settings
        items=audio_tab
        item.audio_tab.label=Audio
        item.audio_tab.action=settings_menu
        item.audio_tab.target=settings_audio
        """);
    Files.writeString(root.resolve("config/menu/menus/settings_audio.menu"), """
        layout=settings
        defaultItemStyle=settings
        items=back
        item.back.label=Back
        item.back.action=back
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfileLoader.LoadResult result = MenuProfileLoader.loadWithDiagnostics(assets);

    assertTrue(result.diagnostics().stream().noneMatch(d -> d.contains("unused action target")));
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

  @Test
  void parsesInlineQuitTargetWhenBaseItemProvidesInheritedTarget() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-inline-quit-target-");
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=main\n");
    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        items=quit
        item.quit.action=quit:confirm_exit
        """);

    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));
    MenuProfileLoader.LoadResult result = MenuProfileLoader.loadWithDiagnostics(assets);
    MenuScreenSpec main = result.profile().screen("main");

    assertEquals(MenuActionType.QUIT, main.items().get(0).action().type());
    assertEquals("confirm_exit", main.items().get(0).action().target());
    assertTrue(result.diagnostics().stream().noneMatch(d -> d.contains("Unknown menu action 'quit:confirm_exit'")));
  }
}

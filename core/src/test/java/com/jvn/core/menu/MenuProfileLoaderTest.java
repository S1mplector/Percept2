package com.jvn.core.menu;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MenuProfileLoaderTest {

  @Test
  void loadsDefaultsWhenConfigFilesAreMissing() throws Exception {
    Path root = Files.createTempDirectory("jvn-menu-defaults-");
    AssetCatalog assets = new AssetCatalog(new FilesystemAssetManager(root));

    MenuProfile profile = MenuProfileLoader.load(assets);
    MenuScreenSpec main = profile.screen("main");

    assertNotNull(main);
    assertEquals(4, main.items().size());
    assertEquals(MenuActionType.NEW_GAME, main.items().get(0).action().type());
    assertEquals(MenuActionType.LOAD_MENU, main.items().get(1).action().type());
    assertEquals(MenuActionType.SETTINGS_MENU, main.items().get(2).action().type());
    assertEquals(MenuActionType.QUIT, main.items().get(3).action().type());
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
        """);

    Files.writeString(root.resolve("config/menu/menus/main.menu"), """
        titleText=Neon Title
        hintsText=Use arrows and Enter
        layout=wide
        defaultItemStyle=neon
        items=start,extras,quit
        item.start.label=Start Story
        item.start.action=run_script:scripts/story/prologue.vns
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
    assertEquals(MenuActionType.OPEN_MENU, main.items().get(1).action().type());
    assertEquals("extras", main.items().get(1).action().target());

    MenuScreenSpec extras = profile.screen("extras");
    assertEquals(2, extras.items().size());
    assertEquals(MenuActionType.BACK, extras.items().get(1).action().type());

    assertEquals(52.0, profile.layout("wide").lineHeight());
    assertEquals("left", profile.layout("wide").textAlign());
    assertEquals("#00ffff", profile.style("neon").itemSelectedColor());
    assertEquals(Integer.valueOf(22), profile.style("neon").itemFontSize());
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
  }
}

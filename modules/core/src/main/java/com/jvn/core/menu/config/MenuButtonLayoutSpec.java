package com.jvn.core.menu.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-button layout specification for a menu screen.
 *
 * <p>Each button gets explicit bounds (normalized 0..1 or pixel values &gt; 1)
 * within the menu viewport. A button layout file carries context about which
 * menu it targets, an optional resolution hint, and a menu-type tag.
 *
 * <h3>DSL property format</h3>
 * <pre>
 * # Button layout header
 * menuId=main
 * resolution=1920x1080
 * menuType=main
 *
 * # Button definitions
 * button.ids=new_game,load,settings,quit
 *
 * button.new_game.label=New Game
 * button.new_game.boundsX=0.25
 * button.new_game.boundsY=0.30
 * button.new_game.boundsW=0.50
 * button.new_game.boundsH=0.08
 * button.new_game.tag=primary
 * button.new_game.asset=assets/ui/btn_main.png
 * button.new_game.hoverAsset=assets/ui/btn_main_hover.png
 * button.new_game.disabledAsset=assets/ui/btn_main_disabled.png
 *
 * button.load.label=Load Game
 * button.load.boundsX=0.25
 * button.load.boundsY=0.40
 * button.load.boundsW=0.50
 * button.load.boundsH=0.08
 * </pre>
 */
public record MenuButtonLayoutSpec(
    String menuId,
    String resolution,
    String menuType,
    List<ButtonBounds> buttons,
    Map<String, String> extras
) {
  public MenuButtonLayoutSpec {
    menuId = normalize(menuId, "default");
    resolution = normalize(resolution, "default");
    menuType = normalize(menuType, null);
    buttons = buttons == null ? List.of() : List.copyOf(buttons);
    extras = extras == null ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
  }

  /**
   * A single button's bounds and metadata within the menu viewport.
   *
   * <p>Bounds values &le; 1.0 are treated as fractions of the viewport dimension;
   * values &gt; 1.0 are treated as absolute pixels. {@code null} means "use
   * the layout-computed default".
   */
  public record ButtonBounds(
      String id,
      String label,
      String tag,
      Double boundsX,
      Double boundsY,
      Double boundsW,
      Double boundsH,
      String assetPath,
      String hoverAssetPath,
      String disabledAssetPath,
      Map<String, String> extras
  ) {
    public ButtonBounds {
      id = normalize(id, "button");
      label = normalize(label, null);
      tag = normalize(tag, null);
      if (boundsX != null && !Double.isFinite(boundsX)) boundsX = null;
      if (boundsY != null && !Double.isFinite(boundsY)) boundsY = null;
      if (boundsW != null && !Double.isFinite(boundsW)) boundsW = null;
      if (boundsH != null && !Double.isFinite(boundsH)) boundsH = null;
      assetPath = normalize(assetPath, null);
      hoverAssetPath = normalize(hoverAssetPath, null);
      disabledAssetPath = normalize(disabledAssetPath, null);
      extras = extras == null ? Collections.emptyMap()
          : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
    }

    /** True if explicit bounds are fully defined. */
    public boolean hasBounds() {
      return boundsX != null && boundsY != null && boundsW != null && boundsH != null;
    }

    private static String normalize(String v, String def) {
      if (v == null) return def;
      String t = v.trim();
      return t.isEmpty() ? def : t;
    }
  }

  /** Creates an empty spec for the given menu context. */
  public static MenuButtonLayoutSpec empty(String menuId, String resolution, String menuType) {
    return new MenuButtonLayoutSpec(menuId, resolution, menuType, List.of(), Map.of());
  }

  private static String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}

package com.jvn.scenerender.input;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScene;

/**
 * A seam for constructing the menu scenes that hold a persistence dependency
 * ({@code VnSaveManager}/{@code ActionBindingProfile}), which some platforms
 * (the TeaVM browser target) cannot cross-compile because their underlying
 * save/settings machinery uses JDK APIs the TeaVM classlib does not support
 * ({@code ObjectInputStream}/{@code ObjectOutputStream}, {@code StringJoiner},
 * {@code BufferedReader.transferTo}).
 *
 * <p>{@link SceneInputRouter} calls this interface instead of constructing
 * {@code SaveMenuScene}/{@code LoadMenuScene}/{@code SettingsScene}/
 * {@code MainMenuScene}/{@code PauseMenuScene} directly. Desktop
 * ({@code FxLauncher}) supplies {@code DefaultMenuSceneFactory}, which
 * constructs the real scenes with zero behavior change. A browser-target
 * factory can supply no-op/unsupported stubs whose method bodies never
 * reference the real scene constructors — keeping those constructors
 * unreachable from TeaVM's whole-program static analysis when compiling the
 * web module, since Java reachability analysis for an interface method call
 * only needs to consider the concrete implementation actually wired in per
 * compilation unit.</p>
 *
 * <p>{@code HistoryMenuScene} IS covered by this interface too (via
 * {@link #createHistoryMenuScene}), despite having no persistence
 * dependency of its own — its constructor unconditionally calls
 * {@code MenuProfileLoader.loadWithDiagnostics()}, which reaches
 * {@code ClasspathAssetManager.list()} -&gt; {@code ClassLoader.getResources(dir)},
 * a different TeaVM-unsupported API family (whole-program reachability
 * analysis, not persistence), so it needs the same seam.</p>
 *
 * <p>This interface also carries {@link #quickSave}/{@link #quickLoad} —
 * not scene constructions, but the same TeaVM-unreachability problem:
 * {@code VnScene.quickSave()}/{@code quickLoad()} delegate to
 * {@code VnQuickSaveManager}, which holds a {@code VnSaveManager} whose
 * {@code load()}/{@code save()} methods use the same unsupported JDK APIs.
 * Reusing this interface (rather than adding a second one) keeps the
 * "platform capability seam" pattern in one place.</p>
 */
public interface MenuSceneFactory {

  /** Backing construction for the {@code "save_slots"}/{@code "save_menu"} UI actions. */
  Scene createSaveMenuScene(Engine engine, VnScene vnScene);

  /** Backing construction for {@code SceneInputRouter.openSaveMenu}. */
  Scene createSaveMenuScene(Engine engine, VnScene vnScene, String defaultScriptName);

  /** Backing construction for the {@code "load_slots"}/{@code "load_menu"} UI actions and {@code openLoadMenu}. */
  Scene createLoadMenuScene(Engine engine, VnScene vnScene, String defaultScriptName);

  /**
   * Backing construction for the {@code "settings_menu"}/{@code "open_settings_menu"}/
   * {@code "menu_settings"} UI action.
   */
  Scene createSettingsScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId);

  /**
   * Backing construction for the {@code "main_menu"}/{@code "open_main_menu"}/{@code "menu_main"}
   * UI action (root menu, no explicit target screen).
   */
  Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName);

  /**
   * Backing construction for the {@code "open_menu"}/{@code "menu_open"} and
   * {@code "quit"}/{@code "quit_game"}/{@code "close_game"}/{@code "exit"} UI actions
   * (a specific target menu screen).
   */
  Scene createMainMenuScene(Engine engine, VnScene vnScene, String defaultScriptName, String menuId);

  /** Backing construction for {@code SceneInputRouter.openPauseMenu}. */
  Scene createPauseMenuScene(Engine engine, VnScene vnScene, String defaultScriptName);

  /**
   * Backing construction for the {@code "toggle_history"}/{@code "history"} UI action and
   * {@code SceneInputRouter.toggleHistory}'s push branch.
   */
  Scene createHistoryMenuScene(Engine engine, VnScene vnScene);

  /**
   * Backing implementation for {@code SceneInputRouter.quickSave} and the
   * {@code "quick_save"}/{@code "save_quick"} UI action. Not a scene construction, but the same
   * TeaVM-unreachability problem as the {@code createXScene} methods above (see class Javadoc).
   *
   * @return true if the quick-save succeeded, false otherwise
   */
  boolean quickSave(VnScene vnScene);

  /**
   * Backing implementation for {@code SceneInputRouter.quickLoad} and the
   * {@code "quick_load"}/{@code "load_quick"} UI action.
   *
   * @return true if the quick-load succeeded, false otherwise
   */
  boolean quickLoad(VnScene vnScene);

  /**
   * Hook invoked by {@link SceneInputRouter#menuEnter} immediately after a
   * {@code SaveMenuScene} item-selection successfully writes/overwrites a
   * save slot (i.e. {@code slotName} is non-null). This exists to let
   * platforms that can produce a save-slot thumbnail
   * ({@code Canvas.snapshot()}-based, JavaFX-only, desktop) do so on the
   * click path too, matching {@code FxLauncher}'s original keyboard-Enter
   * behavior ({@code writeSaveThumbnail}/{@code captureVnThumbnail}) —
   * {@code menuEnter} itself deliberately omits the thumbnail write since
   * it is shared by both desktop and browser, and the browser has no
   * equivalent capture API.
   *
   * <p>Default no-op so implementations without thumbnail support (e.g.
   * {@code UnsupportedMenuSceneFactory}) need not override it.</p>
   *
   * @param vnScene  the {@code VnScene} being saved from
   * @param slotName the slot name just written (never null when called)
   */
  default void afterSaveSlotWritten(Engine engine, VnScene vnScene, String slotName) {
    // no-op by default
  }
}

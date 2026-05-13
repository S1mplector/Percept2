package com.jvn.core.vn.script;

import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnState;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade API for inline Java code inside VNS scripts.
 * Provides convenient static methods accessible from [java] blocks,
 * analogous to Ren'Py's {@code renpy.*} namespace.
 *
 * <p>Usage inside a VNS {@code [java]} block:
 * <pre>
 * [java]
 * Vn.setVar("hp", Vn.intVar("hp") - 10);
 * Vn.show("alice", "happy", "center");
 * Vn.playBgm("battle_theme");
 * [/java]
 * </pre>
 *
 * <p>The current scene is bound via a thread-local before execution
 * and cleared afterwards.
 */
public final class Vn {

  private static final Logger log = LoggerFactory.getLogger(Vn.class);

  private static final ThreadLocal<VnScene> CURRENT_SCENE = new ThreadLocal<>();

  private Vn() {}

  // --- Lifecycle (called by the compiler/interop, not by user code) ---

  public static void bind(VnScene scene) {
    CURRENT_SCENE.set(scene);
  }

  public static void unbind() {
    CURRENT_SCENE.remove();
  }

  private static VnScene scene() {
    VnScene s = CURRENT_SCENE.get();
    if (s == null) throw new IllegalStateException("Vn: no active scene (are you inside a [java] block?)");
    return s;
  }

  private static VnState state() {
    return scene().getState();
  }

  // --- Variables ---

  public static void setVar(String key, Object value) {
    state().setVariable(key, value);
  }

  public static Object getVar(String key) {
    return state().getVariable(key);
  }

  public static Object getVar(String key, Object defaultValue) {
    Object v = state().getVariable(key);
    return v != null ? v : defaultValue;
  }

  public static int intVar(String key) {
    Object v = state().getVariable(key);
    if (v instanceof Number n) return n.intValue();
    if (v == null) return 0;
    return Integer.parseInt(v.toString());
  }

  public static int intVar(String key, int defaultValue) {
    Object v = state().getVariable(key);
    if (v instanceof Number n) return n.intValue();
    if (v == null) return defaultValue;
    try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return defaultValue; }
  }

  public static double doubleVar(String key) {
    Object v = state().getVariable(key);
    if (v instanceof Number n) return n.doubleValue();
    if (v == null) return 0.0;
    return Double.parseDouble(v.toString());
  }

  public static double doubleVar(String key, double defaultValue) {
    Object v = state().getVariable(key);
    if (v instanceof Number n) return n.doubleValue();
    if (v == null) return defaultValue;
    try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return defaultValue; }
  }

  public static String strVar(String key) {
    Object v = state().getVariable(key);
    return v != null ? v.toString() : "";
  }

  public static String strVar(String key, String defaultValue) {
    Object v = state().getVariable(key);
    return v != null ? v.toString() : defaultValue;
  }

  public static boolean boolVar(String key) {
    Object v = state().getVariable(key);
    if (v instanceof Boolean b) return b;
    if (v == null) return false;
    String s = v.toString().toLowerCase();
    return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
  }

  public static boolean hasVar(String key) {
    return state().getVariable(key) != null;
  }

  public static Map<String, Object> vars() {
    return state().getVariables();
  }

  // --- Persistent Store ---

  public static void setPersistent(String key, Object value) {
    state().setPersistentValue(key, value);
  }

  public static Object getPersistent(String key) {
    return state().getPersistentValue(key);
  }

  // --- Character Display ---

  public static void show(String characterId, String expression, String position) {
    com.jvn.core.vn.CharacterPosition pos = com.jvn.core.vn.CharacterPosition.predefined(position);
    if (pos == null) pos = com.jvn.core.vn.CharacterPosition.CENTER;
    state().showCharacter(pos, characterId, expression);
  }

  public static void show(String characterId, String expression) {
    show(characterId, expression, "center");
  }

  public static void show(String characterId) {
    show(characterId, "neutral", "center");
  }

  public static void hide(String characterId) {
    var visible = state().getVisibleCharacters();
    var toRemove = new java.util.ArrayList<com.jvn.core.vn.CharacterPosition>();
    for (var entry : visible.entrySet()) {
      if (entry.getValue() != null && characterId.equals(entry.getValue().getCharacterId())) {
        toRemove.add(entry.getKey());
      }
    }
    for (var pos : toRemove) {
      visible.remove(pos);
    }
  }

  // --- Audio ---

  public static void playBgm(String trackId) {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().playBgm(trackId, true);
    }
  }

  public static void playBgm(String trackId, boolean loop) {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().playBgm(trackId, loop);
    }
  }

  public static void stopBgm() {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().stopBgm();
    }
  }

  public static void playSfx(String trackId) {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().playSfx(trackId);
    }
  }

  public static void playVoice(String trackId) {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().playVoice(trackId);
    }
  }

  public static void stopVoice() {
    if (scene().getAudioFacade() != null) {
      scene().getAudioFacade().stopVoice();
    }
  }

  // --- Scene Control ---

  public static void setBackground(String backgroundId) {
    state().setCurrentBackgroundId(backgroundId);
  }

  public static String getBackground() {
    return state().getCurrentBackgroundId();
  }

  public static void jump(String label) {
    state().jumpToLabel(label);
  }

  public static void hud(String message) {
    state().showHudMessage(message, 2000);
  }

  public static void hud(String message, int durationMs) {
    state().showHudMessage(message, durationMs);
  }

  // --- Screen Effects ---

  public static void screenShake(float intensity, long durationMs) {
    state().triggerScreenShake(intensity, durationMs);
  }

  public static void flash(float r, float g, float b, float strength, long durationMs) {
    state().triggerFlash(r, g, b, strength, durationMs);
  }

  public static void flash(long durationMs) {
    state().triggerFlash(1f, 1f, 1f, 1f, durationMs);
  }

  // --- Character Queries ---

  public static boolean isVisible(String characterId) {
    return state().getCharacterPosition(characterId) != null;
  }

  public static String getExpression(String characterId) {
    return state().getCharacterExpression(characterId);
  }

  public static com.jvn.core.vn.CharacterPosition getPosition(String characterId) {
    return state().getCharacterPosition(characterId);
  }

  public static void clearCharacters() {
    state().clearAllCharacters();
  }

  // --- UI Visibility ---

  public static void hideUi() {
    state().setUiHidden(true);
  }

  public static void showUi() {
    state().setUiHidden(false);
  }

  public static void toggleUi() {
    state().toggleUiHidden();
  }

  public static boolean isUiHidden() {
    return state().isUiHidden();
  }

  // --- Scene State ---

  public static boolean isComplete() {
    return state().isScenarioComplete();
  }

  public static int nodeIndex() {
    return state().getCurrentNodeIndex();
  }

  public static void wait(int durationMs) throws InterruptedException {
    Thread.sleep(Math.max(0, durationMs));
  }

  // --- Utility ---

  public static VnScene currentScene() {
    return scene();
  }

  public static VnState currentState() {
    return state();
  }

  public static void log(String message) {
    log.debug("[VNS Java] {}", message);
  }

  public static void log(String format, Object... args) {
    log.debug("[VNS Java] " + format, args);
  }
}

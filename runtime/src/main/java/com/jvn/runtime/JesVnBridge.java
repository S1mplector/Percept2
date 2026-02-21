package com.jvn.runtime;

import java.util.HashMap;
import java.util.Map;

import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.scripting.jes.runtime.JesScene2D;

/**
 * Bridges JES scenes to VNS scenes: allows JES to start VN segments and resumes on exit.
 */
public class JesVnBridge {
  private final Engine engine;
  private final VnScenarioLoader scenarioLoader = new VnScenarioLoader();

  public JesVnBridge(Engine engine) {
    this.engine = engine;
  }

  public void attach(JesScene2D jes) {
    if (jes == null) return;
    jes.registerCall("startVns", props -> startVns(jes, props));
    jes.registerCall("startVn", props -> startVns(jes, props));
    jes.registerCall("vns", props -> startVns(jes, props));
  }

  private void startVns(JesScene2D jes, Map<String,Object> props) {
    if (jes == null) return;
    String script = str(props, "script", str(props, "name", "demo.vns"));
    String label = str(props, "label", null);
    boolean replace = bool(props, "replace", false);
    boolean popOnExit = bool(props, "popOnExit", true);
    try {
      VnScene vn = loadVnScene(script, topVn());
      if (vn == null) return;
      if (label != null && !label.isBlank()) {
        vn.getState().jumpToLabel(label);
      }
      jes.setPaused(true);
      if (vn instanceof BridgedVnScene b) {
        b.setOnEnter(() -> {
          // ensure VN inherits latest audio facade if present on top VN
          var cur = topVn();
          if (cur != null && cur.getAudioFacade() != null) {
            vn.setAudioFacade(cur.getAudioFacade());
          }
        });
        b.setOnExit(() -> {
          jes.setPaused(false);
          Map<String,Object> ev = new HashMap<>();
          ev.put("script", script);
          if (label != null) ev.put("label", label);
          jes.invokeCall("vnsEnded", ev);
          if (popOnExit) {
            try { engine.scenes().pop(); } catch (Exception ignored) {}
          }
        });
      }
      if (replace) {
        engine.scenes().replace(vn);
      } else {
        engine.scenes().push(vn);
      }
    } catch (Exception ignored) {}
  }

  private VnScene loadVnScene(String script, VnScene current) throws Exception {
    if (script == null || script.isBlank()) return null;
    VnScenario sc = scenarioLoader.load(script);
    BridgedVnScene vn = new BridgedVnScene(sc);
    vn.setInterop(new RuntimeVnInterop(engine));
    VnSettings settings = new VnSettings();
    if (current != null) copySettings(current.getState(), settings);
    copySettingsIntoState(settings, vn.getState());
    if (current != null && current.getAudioFacade() != null) {
      vn.setAudioFacade(current.getAudioFacade());
    }
    return vn;
  }

  private VnScene topVn() {
    if (engine == null || engine.scenes() == null) return null;
    com.jvn.core.scene.Scene top = engine.scenes().peek();
    if (top instanceof VnScene vn) return vn;
    return null;
  }

  private static void copySettings(VnState from, VnSettings to) {
    if (from == null || to == null) return;
    VnSettings s = from.getSettings();
    if (s == null) return;
    to.setTextSpeed(s.getTextSpeed());
    to.setBgmVolume(s.getBgmVolume());
    to.setSfxVolume(s.getSfxVolume());
    to.setVoiceVolume(s.getVoiceVolume());
    to.setAutoPlayDelay(s.getAutoPlayDelay());
    to.setSkipUnreadText(s.isSkipUnreadText());
    to.setSkipAfterChoices(s.isSkipAfterChoices());
    to.setPhysicsFixedStepMs(s.getPhysicsFixedStepMs());
    to.setPhysicsMaxSubSteps(s.getPhysicsMaxSubSteps());
    to.setPhysicsDefaultFriction(s.getPhysicsDefaultFriction());
    to.setInputProfilePath(s.getInputProfilePath());
    to.setInputProfileSerialized(s.getInputProfileSerialized());
  }

  private static void copySettingsIntoState(VnSettings src, VnState state) {
    if (src == null || state == null) return;
    VnSettings dst = state.getSettings();
    if (dst == null) return;
    dst.setTextSpeed(src.getTextSpeed());
    dst.setBgmVolume(src.getBgmVolume());
    dst.setSfxVolume(src.getSfxVolume());
    dst.setVoiceVolume(src.getVoiceVolume());
    dst.setAutoPlayDelay(src.getAutoPlayDelay());
    dst.setSkipUnreadText(src.isSkipUnreadText());
    dst.setSkipAfterChoices(src.isSkipAfterChoices());
    dst.setPhysicsFixedStepMs(src.getPhysicsFixedStepMs());
    dst.setPhysicsMaxSubSteps(src.getPhysicsMaxSubSteps());
    dst.setPhysicsDefaultFriction(src.getPhysicsDefaultFriction());
    dst.setInputProfilePath(src.getInputProfilePath());
    dst.setInputProfileSerialized(src.getInputProfileSerialized());
  }

  private static String str(Map<String,Object> props, String key, String def) {
    if (props == null) return def;
    Object v = props.get(key);
    return v instanceof String s ? s : def;
  }

  private static boolean bool(Map<String,Object> props, String key, boolean def) {
    if (props == null) return def;
    Object v = props.get(key);
    if (v instanceof Boolean b) return b;
    if (v instanceof String s) {
      return "true".equalsIgnoreCase(s) || "1".equals(s);
    }
    return def;
  }
}

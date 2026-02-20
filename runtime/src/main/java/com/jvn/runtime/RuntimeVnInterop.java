package com.jvn.runtime;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.*;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RuntimeVnInterop implements VnInterop {
  private final Engine engine;
  private final DefaultVnInterop base = new DefaultVnInterop();

  public RuntimeVnInterop(Engine engine) { this.engine = engine; }

  @Override
  public VnInteropResult handle(VnExternalCommand command, VnScene scene) {
    String provider = safe(command.getProvider());
    String payload = safe(command.getPayload());
    switch (provider.toLowerCase()) {
      case "jes":
        return handleJes(payload, scene);
      case "menu":
        return handleMenu(payload, scene);
      case "vns":
        return handleVns(payload, scene);
      default:
        return base.handle(command, scene);
    }
  }

  private VnInteropResult handleJes(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String cmd = toks[0].toLowerCase();
    try {
      switch (cmd) {
        case "push": {
          String script = toks.length >= 2 ? toks[1] : null;
          String label = null;
          for (int i = 2; i < toks.length - 1; i++) {
            if ("label".equalsIgnoreCase(toks[i])) { label = toks[i+1]; break; }
          }
          java.util.Map<String,Object> initProps = new java.util.HashMap<>();
          int withIdx = -1;
          for (int i = 2; i < toks.length; i++) { if ("with".equalsIgnoreCase(toks[i])) { withIdx = i; break; } }
          if (withIdx >= 0) {
            for (int i = withIdx + 1; i < toks.length; i++) {
              String t = toks[i]; int eq = t.indexOf('='); if (eq > 0) {
                String k = t.substring(0, eq); String v = t.substring(eq+1);
                initProps.put(k, parseScalar(v));
              }
            }
          }
          JesScene2D js = loadJes(script, scene, label, initProps);
          if (js != null) engine.scenes().push(js);
          return VnInteropResult.advance();
        }
        case "replace": {
          String script = toks.length >= 2 ? toks[1] : null;
          String label = null;
          for (int i = 2; i < toks.length - 1; i++) {
            if ("label".equalsIgnoreCase(toks[i])) { label = toks[i+1]; break; }
          }
          java.util.Map<String,Object> initProps = new java.util.HashMap<>();
          int withIdx = -1;
          for (int i = 2; i < toks.length; i++) { if ("with".equalsIgnoreCase(toks[i])) { withIdx = i; break; } }
          if (withIdx >= 0) {
            for (int i = withIdx + 1; i < toks.length; i++) {
              String t = toks[i]; int eq = t.indexOf('='); if (eq > 0) {
                String k = t.substring(0, eq); String v = t.substring(eq+1);
                initProps.put(k, parseScalar(v));
              }
            }
          }
          JesScene2D js = loadJes(script, scene, label, initProps);
          if (js != null) engine.scenes().replace(js);
          return VnInteropResult.advance();
        }
        case "pop": {
          engine.scenes().pop();
          return VnInteropResult.advance();
        }
        case "call": {
          String name = toks.length >= 2 ? toks[1] : null;
          if (name != null) {
            java.util.Map<String,Object> props = new java.util.HashMap<>();
            for (int i = 2; i < toks.length; i++) {
              String t = toks[i];
              int eq = t.indexOf('=');
              if (eq > 0) {
                String k = t.substring(0, eq);
                String v = t.substring(eq + 1);
                props.put(k, parseScalar(v));
              }
            }
            Scene top = engine.scenes().peek();
            if (top instanceof JesScene2D jes) {
              jes.invokeCall(name, props);
            }
          }
          return VnInteropResult.advance();
        }
      }
    } catch (Exception ignored) {}
    return VnInteropResult.advance();
  }

  private JesScene2D loadJes(String script, VnScene vnScene, String defaultReturnLabel, java.util.Map<String,Object> initProps) throws Exception {
    if (script == null || script.isBlank()) return null;
    AssetCatalog cat = new AssetCatalog();
    try (InputStream in = cat.open(AssetType.SCRIPT, script)) {
      JesScene2D js = JesLoader.load(in);
      // Bridge calls from JES back into VN/runtime
      js.registerCall("hud", props -> {
        Object msg = props == null ? null : props.get("msg");
        if (msg != null) vnScene.getState().showHudMessage(String.valueOf(msg), 1500);
      });
      js.registerCall("pop", props -> engine.scenes().pop());
      new JesVnBridge(engine).attach(js);
      java.util.function.Consumer<java.util.Map<String,Object>> doReturn = props -> {
        // Set variables if provided
        if (props != null) {
          for (var e : props.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("label".equalsIgnoreCase(k) || "goto".equalsIgnoreCase(k)) continue;
            vnScene.getState().setVariable(k, e.getValue());
          }
        }
        // Pop JES and jump to label if specified or default
        String label = null;
        if (props != null) {
          Object l1 = props.get("label");
          Object l2 = props.get("goto");
          if (l1 != null) label = String.valueOf(l1);
          else if (l2 != null) label = String.valueOf(l2);
        }
        if (label == null) label = defaultReturnLabel;
        engine.scenes().pop();
        if (label != null && !label.isBlank()) {
          vnScene.getState().jumpToLabel(label);
        }
      };
      js.registerCall("return", doReturn);
      js.registerCall("vns", doReturn); // alias
      if (initProps != null && !initProps.isEmpty()) {
        try { js.invokeCall("init", initProps); } catch (Exception ignored) {}
      }
      return js;
    }
  }

  private VnInteropResult handleMenu(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String kind = toks[0].toLowerCase();
    switch (kind) {
      case "settings": {
        VnSettings s = scene.getState().getSettings();
        SettingsScene m = new SettingsScene(
            engine,
            new com.jvn.core.vn.save.VnSaveManager(),
            "demo.vns",
            s,
            scene.getAudioFacade()
        );
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "save": {
        SaveMenuScene m = new SaveMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), scene);
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "load": {
        String defScript = toks.length >= 2 ? toks[1] : "demo.vns";
        LoadMenuScene m = new LoadMenuScene(engine, new com.jvn.core.vn.save.VnSaveManager(), defScript, scene.getState().getSettings(), scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "main": {
        String script = toks.length >= 2 ? toks[1] : "demo.vns";
        MainMenuScene m = new MainMenuScene(engine, new VnSettings(), new com.jvn.core.vn.save.VnSaveManager(), script, scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      default:
        // Treat unknown menu kind as a configured custom menu id.
        // Optional second token can override default script for run_script/new_game actions.
        String script = toks.length >= 2 ? toks[1] : "demo.vns";
        MainMenuScene custom = new MainMenuScene(
            engine,
            new VnSettings(),
            new com.jvn.core.vn.save.VnSaveManager(),
            script,
            scene.getAudioFacade(),
            kind
        );
        engine.scenes().push(custom);
        return VnInteropResult.advance();
    }
  }

  private VnInteropResult handleVns(String payload, VnScene scene) {
    // payload: push|replace scriptName [label LABEL]
    List<String> toks = new ArrayList<>(java.util.Arrays.asList(split(payload)));
    if (toks.isEmpty()) return VnInteropResult.advance();
    String cmd = toks.remove(0).toLowerCase();
    if ("goto".equals(cmd)) {
      if (toks.isEmpty()) return VnInteropResult.advance();
      String target = toks.remove(0);
      int colon = target.indexOf(':');
      if (colon < 0) {
        scene.getState().jumpToLabel(target);
        return VnInteropResult.stay();
      }
      String arc = target.substring(0, colon);
      String label = target.substring(colon + 1);
      String script = arc.contains(".") ? arc : arc + ".vns";
      try {
        VnScene newScene = loadVnScene(script, scene);
        if (newScene != null) {
          if (label != null && !label.isBlank()) newScene.getState().jumpToLabel(label);
          engine.scenes().replace(newScene);
        }
      } catch (Exception ignored) {}
      return VnInteropResult.advance();
    }
    String script = toks.isEmpty() ? null : toks.remove(0);
    String label = null;
    if (!toks.isEmpty() && "label".equalsIgnoreCase(toks.get(0))) {
      toks.remove(0);
      if (!toks.isEmpty()) label = toks.remove(0);
    }
    if (script == null) return VnInteropResult.advance();
    try {
      VnScene newScene = loadVnScene(script, scene);
      if (newScene == null) return VnInteropResult.advance();
      if (label != null) newScene.getState().jumpToLabel(label);
      switch (cmd) {
        case "push":
          engine.scenes().push(newScene);
          break;
        case "replace":
        default:
          engine.scenes().replace(newScene);
          break;
      }
    } catch (Exception ignored) {}
    return VnInteropResult.advance();
  }

  private VnScene loadVnScene(String script, VnScene current) throws Exception {
    AssetCatalog assets = new AssetCatalog();
    try (InputStream in = assets.open(AssetType.SCRIPT, script)) {
      VnScenario sc = new VnScriptParser().parse(in);
      VnScene vn = new VnScene(sc);
      if (current.getAudioFacade() != null) vn.setAudioFacade(current.getAudioFacade());
      // carry settings
      copySettings(current.getState().getSettings(), vn.getState().getSettings());
      vn.setInterop(this);
      return vn;
    }
  }

  private void copySettings(VnSettings src, VnSettings dst) {
    if (src == null || dst == null) return;
    dst.setTextSpeed(src.getTextSpeed());
    dst.setBgmVolume(src.getBgmVolume());
    dst.setSfxVolume(src.getSfxVolume());
    dst.setVoiceVolume(src.getVoiceVolume());
    dst.setAutoPlayDelay(src.getAutoPlayDelay());
    dst.setSkipUnreadText(src.isSkipUnreadText());
    dst.setSkipAfterChoices(src.isSkipAfterChoices());
  }

  private static String safe(String s) { return s == null ? "" : s; }
  private static String[] split(String s) { return VnArgTokenizer.tokenizeToArray(s); }
  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        t = t.substring(1, t.length() - 1);
      }
    }
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { if (t.contains(".")) return Double.parseDouble(t); else return Integer.parseInt(t); }
    catch (Exception ignored) {}
    return t;
  }
}

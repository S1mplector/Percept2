package com.jvn.core.vn;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic interop implementation.
 * Providers:
 *  - hud: show a temporary HUD message with the payload
 *  - java: invoke a static method using reflection, payload format:
 *          fully.qualified.Class#method [arg1 arg2 ...]
 *          args are parsed as int/double/boolean if possible, else String
 *  - jes: placeholder (no-op for now), shows HUD notice
 */
public class DefaultVnInterop implements VnInterop {
  @Override
  public VnInteropResult handle(VnExternalCommand command, VnScene scene) {
    if (command == null || scene == null) return VnInteropResult.advance();
    String provider = safe(command.getProvider()).toLowerCase();
    String payload = safe(command.getPayload());

    switch (provider) {
      case "hud":
        scene.getState().showHudMessage(payload, 2000);
        return VnInteropResult.advance();
      case "java":
        handleJava(payload, scene);
        return VnInteropResult.advance();
      case "jes":
        scene.getState().showHudMessage("[jes] " + payload, 1500);
        return VnInteropResult.advance();
      case "var":
        handleVar(payload, scene);
        return VnInteropResult.advance();
      case "cond":
        boolean jumped = handleCond(payload, scene);
        return jumped ? VnInteropResult.stay() : VnInteropResult.advance();
      case "settings":
        handleSettings(payload, scene);
        return VnInteropResult.advance();
      case "save":
        handleSave(payload, scene);
        return VnInteropResult.advance();
      case "mode":
        handleMode(payload, scene);
        return VnInteropResult.advance();
      case "ui":
        handleUi(payload, scene);
        return VnInteropResult.advance();
      case "history":
        handleHistory(payload, scene);
        return VnInteropResult.advance();
      case "audio":
        handleAudio(payload, scene);
        return VnInteropResult.advance();
      case "screen":
        handleScreen(payload, scene);
        return VnInteropResult.advance();
      default:
        scene.getState().showHudMessage("[call " + provider + "] " + payload, 1200);
        return VnInteropResult.advance();
    }
  }

  private void handleJava(String payload, VnScene scene) {
    try {
      String[] parts = payload.split("\\s+", 2);
      String target = parts.length > 0 ? parts[0] : "";
      String argsStr = parts.length > 1 ? parts[1] : "";
      int idx = target.lastIndexOf('#');
      if (idx < 0 || idx == target.length() - 1) {
        scene.getState().showHudMessage("java: invalid target", 1800);
        return;
      }
      String clsName = target.substring(0, idx);
      String methodName = target.substring(idx + 1);
      Object[] args = parseArgs(argsStr);

      Class<?> cls = Class.forName(clsName);
      Method method = findStaticMethod(cls, methodName, args.length);
      if (method == null) {
        scene.getState().showHudMessage("java: method not found", 1800);
        return;
      }
      Object res = method.invoke(null, coerceArgs(method.getParameterTypes(), args));
      String msg = (res == null) ? "java: ok" : ("java: " + res);
      scene.getState().showHudMessage(msg, 2000);
    } catch (Throwable t) {
      scene.getState().showHudMessage("java: " + t.getClass().getSimpleName(), 2000);
    }
  }

  private void handleVar(String payload, VnScene scene) {
    String[] parts = (payload == null ? "" : payload.trim()).split("\\s+", 3);
    if (parts.length == 0) return;
    String op = parts[0].toLowerCase();
    String key = parts.length >= 2 ? parts[1] : "";
    String val = parts.length >= 3 ? parts[2] : "";
    var vars = scene.getState().getVariables();
    switch (op) {
      case "set":
        vars.put(key, parseScalar(val));
        break;
      case "inc":
        numberOp(vars, key, val, true);
        break;
      case "dec":
        numberOp(vars, key, val, false);
        break;
      case "flag":
        vars.put(key, Boolean.TRUE);
        break;
      case "unflag":
        vars.put(key, Boolean.FALSE);
        break;
      case "clear":
        vars.remove(key);
        break;
    }
  }

  private void numberOp(java.util.Map<String,Object> vars, String key, String deltaStr, boolean inc) {
    Object cur = vars.get(key);
    double curVal = 0.0;
    if (cur instanceof Number n) curVal = n.doubleValue();
    else if (cur instanceof String s) try { curVal = Double.parseDouble(s); } catch (Exception ignored) {}
    double delta = 1.0;
    try { delta = Double.parseDouble(deltaStr); } catch (Exception ignored) {}
    double res = inc ? curVal + delta : curVal - delta;
    if (isWhole(res)) vars.put(key, (int)Math.round(res)); else vars.put(key, res);
  }

  private boolean isWhole(double d) { return Math.abs(d - Math.rint(d)) < 1e-9; }

  private boolean handleCond(String payload, VnScene scene) {
    if (payload == null) return false;
    String[] toks = payload.trim().split("\\s+");
    if (toks.length < 5) return false;
    int i = 0;
    String kw = toks[i++].toLowerCase();
    if (!"if".equals(kw)) return false;
    String var = toks[i++];
    String op = toks[i++];
    String value = toks[i++];
    String gotoKw = toks[i++].toLowerCase();
    if (!"goto".equals(gotoKw)) return false;
    String label = toks.length > i ? toks[i] : null;
    Object lhs = scene.getState().getVariable(var);
    boolean ok = compare(lhs, op, value);
    if (ok && label != null) {
      scene.getState().jumpToLabel(label);
      return true;
    }
    return false;
  }

  private void handleSettings(String payload, VnScene scene) {
    String[] toks = (payload == null ? "" : payload.trim()).split("\\s+");
    if (toks.length == 0) return;
    String cmd = toks[0].toLowerCase();
    VnSettings s = scene.getState().getSettings();
    switch (cmd) {
      case "textspeed": {
        if (toks.length >= 2) {
          try { s.setTextSpeed(Integer.parseInt(toks[1])); } catch (Exception ignored) {}
        }
        break;
      }
      case "autodelay": {
        if (toks.length >= 2) {
          try { s.setAutoPlayDelay(Long.parseLong(toks[1])); } catch (Exception ignored) {}
        }
        break;
      }
      case "volume": {
        if (toks.length >= 3) {
          String which = toks[1].toLowerCase();
          try {
            float v = Float.parseFloat(toks[2]);
            v = Math.max(0f, Math.min(1f, v));
            if ("bgm".equals(which)) s.setBgmVolume(v);
            else if ("sfx".equals(which)) s.setSfxVolume(v);
            else if ("voice".equals(which)) s.setVoiceVolume(v);
            if (scene.getAudioFacade() != null) {
              if ("bgm".equals(which)) scene.getAudioFacade().setBgmVolume(s.getBgmVolume());
              if ("sfx".equals(which)) scene.getAudioFacade().setSfxVolume(s.getSfxVolume());
              if ("voice".equals(which)) scene.getAudioFacade().setVoiceVolume(s.getVoiceVolume());
            }
          } catch (Exception ignored) {}
        }
        break;
      }
    }
  }

  private void handleSave(String payload, VnScene scene) {
    String p = payload == null ? "" : payload.trim().toLowerCase();
    if (p.startsWith("quickload")) {
      boolean ok = scene.quickLoad();
      scene.getState().showHudMessage(ok ? "Loaded" : "No quick save", 1200);
    } else {
      boolean ok = scene.quickSave();
      scene.getState().showHudMessage(ok ? "Saved" : "Save failed", 1200);
    }
  }

  private void handleMode(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return;
    String which = toks[0].toLowerCase();
    String arg = toks.length >= 2 ? toks[1].toLowerCase() : "toggle";
    switch (which) {
      case "skip": {
        if ("toggle".equals(arg)) scene.toggleSkipMode();
        else {
          boolean on = "on".equals(arg) || "true".equals(arg) || "1".equals(arg);
          scene.getState().setSkipMode(on);
          if (on) scene.getState().setAutoPlayMode(false);
        }
        break;
      }
      case "auto": {
        if ("toggle".equals(arg)) scene.toggleAutoPlayMode();
        else {
          boolean on = "on".equals(arg) || "true".equals(arg) || "1".equals(arg);
          scene.getState().setAutoPlayMode(on);
          if (on) scene.getState().setSkipMode(false);
        }
        break;
      }
    }
  }

  private void handleUi(String payload, VnScene scene) {
    String arg = (payload == null ? "" : payload.trim().toLowerCase());
    if (arg.isEmpty() || "toggle".equals(arg)) { scene.getState().toggleUiHidden(); return; }
    if ("hide".equals(arg) || "on".equals(arg)) { scene.getState().setUiHidden(true); return; }
    if ("show".equals(arg) || "off".equals(arg)) { scene.getState().setUiHidden(false); }
  }

  private void handleHistory(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) { scene.getState().toggleHistoryOverlay(); return; }
    String cmd = toks[0].toLowerCase();
    switch (cmd) {
      case "toggle": scene.getState().toggleHistoryOverlay(); break;
      case "show": scene.getState().setHistoryOverlayShown(true); break;
      case "hide": scene.getState().setHistoryOverlayShown(false); break;
      case "scroll": {
        if (toks.length >= 2) {
          try { int d = Integer.parseInt(toks[1]); scene.getState().scrollHistoryByLines(d); } catch (Exception ignored) {}
        }
        break;
      }
      case "clear": scene.getState().clearHistoryScroll(); break;
    }
  }

  private void handleAudio(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return;
    String cmd = toks[0].toLowerCase();
    var a = scene.getAudioFacade(); if (a == null) return;
    switch (cmd) {
      case "pause":
        a.pauseBgm();
        break;
      case "resume":
        a.resumeBgm();
        break;
      case "seek":
        if (toks.length >= 2) {
          try { a.seekBgmSeconds(Double.parseDouble(toks[1])); } catch (Exception ignored) {}
        }
        break;
      case "crossfade":
        if (toks.length >= 3) {
          String track = toks[1];
          try {
            long ms = Long.parseLong(toks[2]);
            boolean loop = toks.length >= 4 && ("true".equalsIgnoreCase(toks[3]) || "on".equalsIgnoreCase(toks[3]) || "1".equals(toks[3]));
            a.crossfadeBgm(track, ms, loop);
          } catch (Exception ignored) {}
        }
        break;
    }
  }

  private void handleScreen(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return;
    String cmd = toks[0].toLowerCase();
    switch (cmd) {
      case "shake": {
        float intensity = toks.length >= 2 ? parseFloatSafe(toks[1], 8f) : 8f;
        long ms = toks.length >= 3 ? parseLongSafe(toks[2], 300L) : 300L;
        scene.getState().triggerScreenShake(intensity, ms);
        break;
      }
      case "flash": {
        float strength = toks.length >= 2 ? parseFloatSafe(toks[1], 0.7f) : 0.7f;
        long ms = toks.length >= 3 ? parseLongSafe(toks[2], 180L) : 180L;
        float r = 1f;
        float g = 1f;
        float b = 1f;
        if (toks.length >= 6) {
          r = parseFloatSafe(toks[3], 1f);
          g = parseFloatSafe(toks[4], 1f);
          b = parseFloatSafe(toks[5], 1f);
        }
        scene.getState().triggerFlash(r, g, b, strength, ms);
        break;
      }
      case "clear":
        scene.getState().triggerScreenShake(0f, 0);
        scene.getState().triggerFlash(1f, 1f, 1f, 0f, 0);
        break;
    }
  }

  private boolean compare(Object lhs, String op, String rhsRaw) {
    Object rhs = parseScalar(rhsRaw);
    if (lhs instanceof Number ln && rhs instanceof Number rn) {
      double a = ln.doubleValue();
      double b = rn.doubleValue();
      if ("==".equals(op)) return a == b;
      if ("!=".equals(op)) return a != b;
      if (">".equals(op)) return a > b;
      if ("<".equals(op)) return a < b;
      if (">=".equals(op)) return a >= b;
      if ("<=".equals(op)) return a <= b;
      return false;
    }
    String a = lhs == null ? "" : lhs.toString();
    String b = rhs == null ? "" : rhs.toString();
    if ("==".equals(op)) return a.equals(b);
    if ("!=".equals(op)) return !a.equals(b);
    return false;
  }

  private static Method findStaticMethod(Class<?> cls, String name, int arity) {
    for (Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
      if (!m.getName().equals(name)) continue;
      if (m.getParameterCount() != arity) continue;
      return m;
    }
    return null;
  }

  private static Object[] parseArgs(String argsStr) {
    argsStr = argsStr == null ? "" : argsStr.trim();
    if (argsStr.isEmpty()) return new Object[0];
    List<Object> list = new ArrayList<>();
    // naive split on whitespace; later we may add quoted args support
    for (String tok : argsStr.split("\\s+")) {
      list.add(parseScalar(tok));
    }
    return list.toArray();
  }

  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { if (t.contains(".")) return Double.parseDouble(t); else return Integer.parseInt(t); }
    catch (Exception ignored) {}
    return t;
  }

  private static Object[] coerceArgs(Class<?>[] types, Object[] args) {
    Object[] out = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      out[i] = coerce(types[i], args[i]);
    }
    return out;
  }

  private static Object coerce(Class<?> t, Object v) {
    if (v == null) return null;
    if (t.isInstance(v)) return v;
    if (t == int.class || t == Integer.class) {
      if (v instanceof Number n) return n.intValue();
      try { return Integer.parseInt(v.toString()); } catch (Exception ignored) {}
    }
    if (t == long.class || t == Long.class) {
      if (v instanceof Number n) return n.longValue();
      try { return Long.parseLong(v.toString()); } catch (Exception ignored) {}
    }
    if (t == double.class || t == Double.class) {
      if (v instanceof Number n) return n.doubleValue();
      try { return Double.parseDouble(v.toString()); } catch (Exception ignored) {}
    }
    if (t == boolean.class || t == Boolean.class) {
      if (v instanceof Boolean b) return b;
      return Boolean.parseBoolean(v.toString());
    }
    return v.toString();
  }

  private static String safe(String s) { return s == null ? "" : s; }
  private static String[] split(String s) {
    String t = safe(s).trim();
    return t.isEmpty() ? new String[0] : t.split("\\s+");
  }

  private static float parseFloatSafe(String s, float fallback) {
    try { return Float.parseFloat(s); } catch (Exception ignored) {}
    return fallback;
  }

  private static long parseLongSafe(String s, long fallback) {
    try { return Long.parseLong(s); } catch (Exception ignored) {}
    return fallback;
  }
}

package com.jvn.core.vn;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.SceneAccessor;
import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.core.animation.TimelineRunner;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.core.vn.ui.VnReactiveOverlayScreenSpec;
import com.jvn.core.vn.ui.VnReactiveScreenLoader;
import com.jvn.core.vn.ui.VnReactiveScreenSpec;

/**
 * Interoperability implementation.
 * Providers:
 *  - hud: show a temporary HUD message with the payload
 *  - java: invoke a static method using reflection, payload format:
 *          fully.qualified.Class#method [arg1 arg2 ...]
 *          args are parsed as int/double/boolean if possible, else String
 *  - jes: placeholder (no-op for now), shows HUD notice
 */
public class DefaultVnInterop implements VnInterop {
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("(?i)^if\\s+(.+?)\\s+goto\\s+(\\S+)\\s*$");
  private static final Pattern EXPR_GOTO_PATTERN = Pattern.compile("(?i)^(.+?)\\s+goto\\s+(\\S+)\\s*$");
  private static final String[] ALLOWED_JAVA_CLASS_PREFIXES = {"com.jvn."};
  private SceneAccessor sceneAccessor;

  public void setSceneAccessor(SceneAccessor accessor) { this.sceneAccessor = accessor; }

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
      case "inline_java":
        handleInlineJava(payload, scene);
        return VnInteropResult.advance();
      case "init_java":
        handleInitJava(payload, scene);
        return VnInteropResult.advance();
      case "java_class":
        handleJavaClass(payload, scene);
        return VnInteropResult.advance();
      case "jes":
        scene.getState().showHudMessage("[jes] " + payload, 1500);
        return VnInteropResult.advance();
      case "jes_timeline":
        return handleJesTimeline(payload, scene, false);
      case "jes_timeline_blocking":
        return handleJesTimeline(payload, scene, true);
      case "jes_timeline_inline":
        return handleJesTimelineInline(payload, scene, false);
      case "jes_timeline_blocking_inline":
        return handleJesTimelineInline(payload, scene, true);
      case "var":
        handleVar(payload, scene);
        return VnInteropResult.advance();
      case "eval":
        handleEval(payload, scene);
        return VnInteropResult.advance();
      case "persistent":
        handlePersistent(payload, scene);
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
        return handleScreen(payload, scene);
      case "char":
        handleCharacter(payload, scene);
        return VnInteropResult.advance();
      case "eye_focus":
        handleEyeFocus(payload, scene);
        return VnInteropResult.advance();
      case "stage":
        handleStage(payload, scene);
        return VnInteropResult.advance();
      default:
        scene.getState().showHudMessage("[call " + provider + "] " + payload, 1200);
        return VnInteropResult.advance();
    }
  }

  private void handleJava(String payload, VnScene scene) {
    try {
      String[] tokens = split(payload);
      String target = tokens.length > 0 ? tokens[0] : "";
      int idx = target.lastIndexOf('#');
      if (idx < 0 || idx == target.length() - 1) {
        failJava(scene, "java: invalid target", new IllegalArgumentException("Expected Class#method"));
        return;
      }
      String clsName = target.substring(0, idx);
      String methodName = target.substring(idx + 1);
      if (!isJavaClassAllowed(clsName)) {
        failJava(scene, "java: class not allowed", new SecurityException("Class is outside the allowed Java interop prefixes: " + clsName));
        return;
      }
      Object[] args = parseArgs(tokens, 1);

      Class<?> cls = Class.forName(clsName);
      MethodSelection selection = findStaticMethod(cls, methodName, args);
      if (selection.errorMessage() != null) {
        failJava(scene, selection.errorMessage(), new NoSuchMethodException(clsName + "#" + methodName));
        return;
      }
      Method method = selection.method();
      Object res = method.invoke(null, coerceArgs(method.getParameterTypes(), args));
      String msg = (res == null) ? "java: ok" : ("java: " + res);
      scene.getState().showHudMessage(msg, 2000);
    } catch (java.lang.reflect.InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      failJava(scene, "java: " + cause.getClass().getSimpleName(), cause);
    } catch (Throwable t) {
      failJava(scene, "java: " + t.getClass().getSimpleName(), t);
    }
  }

  private void handleInlineJava(String payload, VnScene scene) {
    if (payload == null || payload.isBlank()) {
      scene.getState().showHudMessage("inline_java: empty block", 1500);
      return;
    }
    try {
      com.jvn.core.vn.script.InMemoryJavaCompiler.execute(payload, scene);
    } catch (com.jvn.core.vn.script.InMemoryJavaCompiler.JavaCompilationException e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) msg = e.getClass().getSimpleName();
      scene.getState().showHudMessage("inline_java error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.compilationError(e.getSourceName(), e.getLineNumber(), msg));
    } catch (com.jvn.core.vn.script.InMemoryJavaCompiler.JavaRuntimeException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      String msg = cause.getClass().getSimpleName();
      if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
        msg += ": " + cause.getMessage();
      }
      if (msg.length() > 120) msg = msg.substring(0, 120) + "…";
      scene.getState().showHudMessage("inline_java runtime error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.compilationError(e.getSourceName(), e.getLineNumber(), msg));
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) {
        msg = e.getClass().getSimpleName();
      }
      scene.getState().showHudMessage("inline_java error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.interopError("inline_java", msg, e));
    }
  }

  private void handleInitJava(String payload, VnScene scene) {
    if (payload == null || payload.isBlank()) {
      scene.getState().showHudMessage("init_java: empty block", 1500);
      return;
    }
    try {
      var ctx = com.jvn.core.vn.script.InMemoryJavaCompiler.ExecutionContext.parse(payload);
      com.jvn.core.vn.script.InMemoryJavaCompiler.executeInit(
          "init_" + Integer.toHexString(payload.hashCode()),
          ctx.code, ctx.imports, ctx.scenarioId, ctx.sourceLine, ctx.sourceName, scene);
    } catch (com.jvn.core.vn.script.InMemoryJavaCompiler.JavaCompilationException e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) msg = e.getClass().getSimpleName();
      scene.getState().showHudMessage("init_java error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.compilationError(e.getSourceName(), e.getLineNumber(), msg));
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) msg = e.getClass().getSimpleName();
      scene.getState().showHudMessage("init_java error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.interopError("init_java", msg, e));
    }
  }

  private void handleJavaClass(String payload, VnScene scene) {
    if (payload == null || payload.isBlank()) {
      scene.getState().showHudMessage("java_class: empty block", 1500);
      return;
    }
    try {
      var ctx = com.jvn.core.vn.script.InMemoryJavaCompiler.ExecutionContext.parse(payload);
      // The code starts with "ClassName\n" followed by the class body
      String code = ctx.code;
      int nl = code.indexOf('\n');
      String className = nl >= 0 ? code.substring(0, nl).trim() : code.trim();
      String body = nl >= 0 ? code.substring(nl + 1) : "";
      com.jvn.core.vn.script.InMemoryJavaCompiler.compileUserClass(
          className, body, ctx.imports, ctx.scenarioId, ctx.sourceLine, ctx.sourceName);
    } catch (com.jvn.core.vn.script.InMemoryJavaCompiler.JavaCompilationException e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) msg = e.getClass().getSimpleName();
      scene.getState().showHudMessage("java_class error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.compilationError(e.getSourceName(), e.getLineNumber(), msg));
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null || msg.length() > 120) msg = e.getClass().getSimpleName();
      scene.getState().showHudMessage("java_class error: " + msg, 3000);
      scene.setActiveError(VnErrorOverlay.compilationError(null, -1, msg));
    }
  }

  private static int inlineTimelineCounter = 0;

  private VnInteropResult handleJesTimelineInline(String payload, VnScene scene, boolean forceBlocking) {
    if (payload == null || payload.isBlank()) {
      scene.getState().showHudMessage("inline timeline: empty block", 1500);
      return VnInteropResult.advance();
    }
    if (sceneAccessor == null) {
      scene.getState().showHudMessage("inline timeline: no scene accessor", 1500);
      return VnInteropResult.advance();
    }
    InlineTimelineInvocation invocation = parseInlineTimelineInvocation(payload);
    boolean blocking = forceBlocking || invocation.waitForCompletion();
    boolean started = false;
    try {
      String name = "_inline_timeline_" + (++inlineTimelineCounter);
      TimelineData data = TimelineDataParser.parse(name, invocation.block());
      startTimelinePlayback(data, scene, blocking, invocation.chain());
      started = true;
    } catch (Exception ex) {
      scene.getState().showHudMessage("inline timeline error: " + ex.getMessage(), 2000);
      scene.setActiveError(VnErrorOverlay.puppeteerJesParseError(
          scene.getState().getSourceScriptName(),
          invocation.block(),
          ex));
    }
    return started && blocking ? VnInteropResult.block() : VnInteropResult.advance();
  }

  private VnInteropResult handleJesTimeline(String payload, VnScene scene, boolean forceBlocking) {
    TimelineInvocation invocation = parseTimelineInvocation(payload);
    String name = invocation.name();
    if (name.isEmpty()) {
      scene.getState().showHudMessage("jes_timeline: no name specified", 1500);
      return VnInteropResult.advance();
    }
    TimelineResolveResult resolved = resolveTimelineData(name, scene);
    if (resolved.data().isEmpty()) {
      if (resolved.parseFailed()) return VnInteropResult.advance();
      scene.getState().showHudMessage("jes_timeline: not found: " + name, 1500);
      return VnInteropResult.advance();
    }
    TimelineData data = resolved.data().get();
    if (sceneAccessor == null) {
      scene.getState().showHudMessage("jes_timeline: no scene accessor", 1500);
      return VnInteropResult.advance();
    }
    boolean blocking = forceBlocking || invocation.waitForCompletion();
    startTimelinePlayback(data, scene, blocking, invocation.chain());
    return blocking ? VnInteropResult.block() : VnInteropResult.advance();
  }

  private void handleVar(String payload, VnScene scene) {
    String[] parts = split(payload);
    if (parts.length == 0) return;
    String op = parts[0].toLowerCase();
    String key = parts.length >= 2 ? parts[1] : "";
    String val = joinTail(parts, 2);
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
      case "mul":
        multiplyOp(vars, key, val);
        break;
      case "div":
        divideOp(vars, key, val);
        break;
      case "flag":
        vars.put(key, Boolean.TRUE);
        break;
      case "unflag":
        vars.put(key, Boolean.FALSE);
        break;
      case "toggle": {
        Object current = vars.get(key);
        boolean next = !(current instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(current)));
        vars.put(key, next);
        break;
      }
      case "clear":
        vars.remove(key);
        break;
    }
  }

  private void handleEval(String payload, VnScene scene) {
    if (payload == null) return;
    int eq = payload.indexOf('=');
    if (eq <= 0) {
      scene.getState().showHudMessage("eval: missing '='", 1800);
      return;
    }
    String targetVar = payload.substring(0, eq).trim();
    String expr = payload.substring(eq + 1).trim();
    if (targetVar.isEmpty() || expr.isEmpty()) return;
    try {
      Object result = VnConditionEvaluator.evaluateValue(expr, scene.getState().getVariables());
      scene.getState().getVariables().put(targetVar, result);
    } catch (Exception ex) {
      scene.getState().showHudMessage("eval err: " + ex.getMessage(), 2000);
    }
  }

  private void handlePersistent(String payload, VnScene scene) {
    String[] parts = split(payload);
    if (parts.length == 0) return;
    String op = parts[0].toLowerCase(Locale.ROOT);
    String key = parts.length >= 2 ? parts[1] : "";
    String tail = joinTail(parts, 2);
    VnState state = scene.getState();
    var vars = state.getVariables();
    switch (op) {
      case "set" -> state.setPersistentValue(key, parseScalar(tail));
      case "inc" -> state.addPersistentValue(key, parseDoubleSafe(tail, 1.0));
      case "dec" -> state.addPersistentValue(key, -parseDoubleSafe(tail, 1.0));
      case "flag" -> state.setPersistentValue(key, Boolean.TRUE);
      case "unflag" -> state.setPersistentValue(key, Boolean.FALSE);
      case "toggle" -> {
        Object current = state.getPersistentValue(key);
        boolean next = !(current instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(current)));
        state.setPersistentValue(key, next);
      }
      case "clear" -> state.removePersistentValue(key);
      case "load" -> {
        String targetVar = parts.length >= 3 ? parts[2] : key;
        if (!targetVar.isBlank()) vars.put(targetVar, state.getPersistentValue(key));
      }
      case "store" -> {
        String sourceVar = parts.length >= 3 ? parts[2] : key;
        if (!key.isBlank()) state.setPersistentValue(key, vars.get(sourceVar));
      }
      case "reload" -> {
        state.getPersistentStore().load();
        state.setVariables(new LinkedHashMap<>(vars));
      }
      case "reset" -> {
        state.getPersistentStore().clear();
        state.setVariables(new LinkedHashMap<>(vars));
      }
      default -> scene.getState().showHudMessage("persistent: unknown op " + op, 1400);
    }
  }

  private void handleStage(String payload, VnScene scene) {
    String requested = safe(payload).trim();
    if (requested.isEmpty() || "clear".equalsIgnoreCase(requested) || "off".equalsIgnoreCase(requested) || "none".equalsIgnoreCase(requested)) {
      scene.getState().setActiveStagePresetId(null);
      return;
    }
    VnScenario scenario = scene.getState().getScenario();
    VnStagePreset preset = scenario == null ? null : scenario.getStagePreset(requested);
    if (preset == null) {
      scene.getState().showHudMessage("stage: not found: " + requested, 1600);
      return;
    }
    scene.getState().setActiveStagePresetId(requested);
  }

  private void numberOp(java.util.Map<String,Object> vars, String key, String deltaStr, boolean inc) {
    Object cur = vars.get(key);
    double curVal = 0.0;
    if (cur instanceof Number n) curVal = n.doubleValue();
    else if (cur instanceof String s) try { curVal = Double.parseDouble(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    double delta = 1.0;
    try { delta = Double.parseDouble(deltaStr); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    double res = inc ? curVal + delta : curVal - delta;
    if (isWhole(res)) vars.put(key, (int)Math.round(res)); else vars.put(key, res);
  }

  private void multiplyOp(java.util.Map<String,Object> vars, String key, String factorStr) {
    Object cur = vars.get(key);
    double curVal = 0.0;
    if (cur instanceof Number n) curVal = n.doubleValue();
    else if (cur instanceof String s) try { curVal = Double.parseDouble(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    double factor = 1.0;
    try { factor = Double.parseDouble(factorStr); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    double res = curVal * factor;
    if (isWhole(res)) vars.put(key, (int)Math.round(res)); else vars.put(key, res);
  }

  private void divideOp(java.util.Map<String,Object> vars, String key, String divisorStr) {
    Object cur = vars.get(key);
    double curVal = 0.0;
    if (cur instanceof Number n) curVal = n.doubleValue();
    else if (cur instanceof String s) try { curVal = Double.parseDouble(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    double divisor = 1.0;
    try { divisor = Double.parseDouble(divisorStr); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    if (Math.abs(divisor) < 1e-15) return;
    double res = curVal / divisor;
    if (isWhole(res)) vars.put(key, (int)Math.round(res)); else vars.put(key, res);
  }

  private boolean isWhole(double d) { return Math.abs(d - Math.rint(d)) < 1e-9; }

  private boolean handleCond(String payload, VnScene scene) {
    if (payload == null || scene == null) return false;
    Matcher m = IF_GOTO_PATTERN.matcher(payload.trim());
    if (!m.matches()) {
      m = EXPR_GOTO_PATTERN.matcher(payload.trim());
      if (!m.matches()) return false;
    }
    String expression = m.group(1) == null ? "" : m.group(1).trim();
    String label = m.group(2) == null ? "" : m.group(2).trim();
    if (expression.isEmpty() || label.isEmpty()) return false;
    boolean ok;
    try {
      ok = VnConditionEvaluator.evaluate(expression, scene.getState().getVariables());
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return false;
    }
    if (ok) {
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
          try { s.setTextSpeed(Integer.parseInt(toks[1])); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        }
        break;
      }
      case "autodelay": {
        if (toks.length >= 2) {
          try { s.setAutoPlayDelay(Long.parseLong(toks[1])); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
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
          } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
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
      case "dialogue":
      case "presentation":
      case "say":
        scene.getState().setDialoguePresentationMode(DialoguePresentationMode.fromToken(arg));
        break;
      case "nvl": {
        if ("toggle".equals(arg)) {
          DialoguePresentationMode current = scene.getState().getDialoguePresentationMode();
          scene.getState().setDialoguePresentationMode(
              current == DialoguePresentationMode.NVL ? DialoguePresentationMode.STANDARD : DialoguePresentationMode.NVL);
        } else {
          boolean on = "on".equals(arg) || "true".equals(arg) || "1".equals(arg);
          scene.getState().setDialoguePresentationMode(on ? DialoguePresentationMode.NVL : DialoguePresentationMode.STANDARD);
        }
        break;
      }
      case "bubble": {
        if ("toggle".equals(arg)) {
          DialoguePresentationMode current = scene.getState().getDialoguePresentationMode();
          scene.getState().setDialoguePresentationMode(
              current == DialoguePresentationMode.BUBBLE ? DialoguePresentationMode.STANDARD : DialoguePresentationMode.BUBBLE);
        } else {
          boolean on = "on".equals(arg) || "true".equals(arg) || "1".equals(arg);
          scene.getState().setDialoguePresentationMode(on ? DialoguePresentationMode.BUBBLE : DialoguePresentationMode.STANDARD);
        }
        break;
      }
    }
  }

  private void handleUi(String payload, VnScene scene) {
    String raw = payload == null ? "" : payload.trim();
    String[] toks = split(raw);
    if (toks.length > 0 && ("visualizer".equalsIgnoreCase(toks[0]) || "viz".equalsIgnoreCase(toks[0]))) {
      handleVisualizerCommand(toks, 1, scene);
      return;
    }
    String arg = raw.toLowerCase();
    if (arg.isEmpty() || "toggle".equals(arg)) { scene.getState().toggleUiHidden(); return; }
    if ("hide".equals(arg) || "on".equals(arg)) { scene.getState().setUiHidden(true); return; }
    if ("show".equals(arg) || "off".equals(arg)) { scene.getState().setUiHidden(false); }
  }

  private void handleVisualizerCommand(String[] toks, int start, VnScene scene) {
    if (toks == null || start >= toks.length) {
      setVisualizerEnabled(scene, !isVisualizerEnabled(scene));
      return;
    }

    String first = toks[start] == null ? "" : toks[start].trim();
    String lower = first.toLowerCase();
    VisualizerCommandMode mode = VisualizerCommandMode.CONFIGURE_ONLY;
    int optStart = start;

    if ("toggle".equals(lower)) {
      mode = VisualizerCommandMode.TOGGLE;
      optStart = start + 1;
    } else if ("set".equals(lower) || "config".equals(lower)) {
      mode = VisualizerCommandMode.CONFIGURE_ONLY;
      optStart = start + 1;
    } else if ("reset".equals(lower)) {
      mode = VisualizerCommandMode.RESET;
      optStart = start + 1;
    } else if ("status".equals(lower)) {
      mode = VisualizerCommandMode.STATUS;
      optStart = start + 1;
    } else {
      Boolean bool = VnAudioVisualizerConfig.parseBooleanToken(lower);
      if (bool != null) {
        mode = bool ? VisualizerCommandMode.ENABLE : VisualizerCommandMode.DISABLE;
        optStart = start + 1;
      }
    }

    if (mode == VisualizerCommandMode.STATUS) {
      if (optStart < toks.length) {
        scene.getState().showHudMessage("viz: status does not accept options", 1800);
        return;
      }
      scene.getState().showHudMessage(buildVisualizerStatus(scene), 2200);
      return;
    }
    if (mode == VisualizerCommandMode.RESET) {
      if (optStart < toks.length) {
        scene.getState().showHudMessage("viz: reset does not accept options", 1800);
        return;
      }
      resetVisualizerOptions(scene);
      return;
    }

    VisualizerOptions options = parseVisualizerOptions(toks, optStart);
    if (mode == VisualizerCommandMode.TOGGLE) {
      setVisualizerEnabled(scene, !isVisualizerEnabled(scene));
    } else if (mode == VisualizerCommandMode.ENABLE) {
      setVisualizerEnabled(scene, true);
    } else if (mode == VisualizerCommandMode.DISABLE) {
      setVisualizerEnabled(scene, false);
    }
    applyVisualizerOptions(scene, options);

    if (!options.warnings().isEmpty()) {
      scene.getState().showHudMessage("viz: " + String.join("; ", options.warnings()), 2400);
    }
  }

  private boolean isVisualizerEnabled(VnScene scene) {
    return VnAudioVisualizerConfig.isTruthy(scene.getState().getVariables().get(VnAudioVisualizerConfig.VAR_ENABLED));
  }

  private void setVisualizerEnabled(VnScene scene, boolean enabled) {
    scene.getState().setVariable(VnAudioVisualizerConfig.VAR_ENABLED, enabled);
  }

  private void resetVisualizerOptions(VnScene scene) {
    var vars = scene.getState().getVariables();
    vars.remove(VnAudioVisualizerConfig.VAR_BARS);
    vars.remove(VnAudioVisualizerConfig.VAR_COLOR);
    vars.remove(VnAudioVisualizerConfig.VAR_ACCENT);
    vars.remove(VnAudioVisualizerConfig.VAR_ALPHA);
    vars.remove(VnAudioVisualizerConfig.VAR_GLOW);
    vars.remove(VnAudioVisualizerConfig.VAR_STYLE);
    vars.remove(VnAudioVisualizerConfig.VAR_HEIGHT);
    vars.remove(VnAudioVisualizerConfig.VAR_Z);
  }

  private String buildVisualizerStatus(VnScene scene) {
    boolean enabled = isVisualizerEnabled(scene);
    int bars = readVisualizerBars(scene);
    String style = readVisualizerStyle(scene);
    String color = readVisualizerColor(scene);
    String spectrum = resolveVisualizerSpectrumStatus(scene.getAudioFacade());
    StringBuilder sb = new StringBuilder("Viz ");
    sb.append(enabled ? "on" : "off");
    sb.append(' ').append(bars).append(" bars ");
    sb.append(style).append(' ');
    sb.append("z=").append(readVisualizerZ(scene)).append(' ');
    sb.append(spectrum);
    if (!VnAudioVisualizerConfig.isAutoToken(color)) {
      sb.append(' ').append(color);
    }
    return sb.toString();
  }

  private String resolveVisualizerSpectrumStatus(AudioFacade audio) {
    if (audio == null) return "no-audio";
    if (!audio.supportsBgmSpectrum()) return "unsupported";
    float[] magnitudes = audio.getBgmSpectrumMagnitudes();
    long updatedAt = audio.getBgmSpectrumUpdatedAtNanos();
    if (magnitudes != null && magnitudes.length > 0) {
      if (updatedAt <= 0L || (System.nanoTime() - updatedAt) <= VnAudioVisualizerConfig.STALE_NS) return "live";
      return "stale";
    }
    return "waiting";
  }

  private int readVisualizerBars(VnScene scene) {
    Object value = scene.getState().getVariables().get(VnAudioVisualizerConfig.VAR_BARS);
    if (value instanceof Number n) return VnAudioVisualizerConfig.clampBars(n.intValue());
    if (value instanceof String s) {
      try {
        return VnAudioVisualizerConfig.clampBars(Integer.parseInt(s.trim()));
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
    return VnAudioVisualizerConfig.DEFAULT_BARS;
  }

  private String readVisualizerStyle(VnScene scene) {
    Object value = scene.getState().getVariables().get(VnAudioVisualizerConfig.VAR_STYLE);
    return value instanceof String s ? VnAudioVisualizerConfig.normalizeStyle(s) : VnAudioVisualizerConfig.DEFAULT_STYLE;
  }

  private String readVisualizerColor(VnScene scene) {
    Object value = scene.getState().getVariables().get(VnAudioVisualizerConfig.VAR_COLOR);
    return value == null ? VnAudioVisualizerConfig.AUTO : value.toString().trim();
  }

  private int readVisualizerZ(VnScene scene) {
    Object value = scene.getState().getVariables().get(VnAudioVisualizerConfig.VAR_Z);
    if (value instanceof Number n) return n.intValue();
    if (value instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      }
    }
    return VnAudioVisualizerConfig.DEFAULT_Z;
  }

  private VisualizerOptions parseVisualizerOptions(String[] toks, int start) {
    Integer bars = null;
    String color = null;
    String accent = null;
    Double alpha = null;
    Boolean glow = null;
    String style = null;
    Double height = null;
    Integer z = null;
    List<String> warnings = new ArrayList<>();

    if (toks == null || start >= toks.length) {
      return new VisualizerOptions(bars, color, accent, alpha, glow, style, height, z, warnings);
    }

    for (int i = start; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;

      String key = null;
      String value = null;
      int eq = token.indexOf('=');
      if (eq >= 0) {
        key = token.substring(0, eq).trim().toLowerCase();
        value = token.substring(eq + 1).trim();
      } else {
        String lower = token.toLowerCase();
        if (token.chars().allMatch(Character::isDigit)) {
          key = "bars";
          value = token;
        } else if (isVisualizerOptionKey(lower)) {
          if (i + 1 >= toks.length) {
            warnings.add("missing " + lower);
            continue;
          }
          key = lower;
          value = toks[++i];
        } else {
          warnings.add("ignored " + token);
          continue;
        }
      }

      if (value == null || value.isBlank()) {
        warnings.add("missing " + key);
        continue;
      }

      switch (key) {
        case "bars" -> {
          try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) warnings.add("bars>0");
            else bars = VnAudioVisualizerConfig.clampBars(parsed);
          } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            warnings.add("bad bars");
          }
        }
        case "color" -> color = value.trim();
        case "accent" -> accent = value.trim();
        case "alpha" -> {
          try {
            alpha = VnAudioVisualizerConfig.clampAlpha(Double.parseDouble(value.trim()));
          } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            warnings.add("bad alpha");
          }
        }
        case "glow" -> {
          Boolean parsed = VnAudioVisualizerConfig.parseBooleanToken(value);
          if (parsed == null) warnings.add("bad glow");
          else glow = parsed;
        }
        case "style" -> {
          String normalized = value.trim().toLowerCase();
          if (!VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(normalized)
              && !VnAudioVisualizerConfig.STYLE_MINIMAL.equals(normalized)) {
            warnings.add("bad style");
          } else {
            style = normalized;
          }
        }
        case "height" -> {
          try {
            height = VnAudioVisualizerConfig.clampHeight(Double.parseDouble(value.trim()));
          } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            warnings.add("bad height");
          }
        }
        case "z", "zindex", "z-index", "layer", "layerorder", "layer-order" -> {
          try {
            z = Integer.parseInt(value.trim());
          } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            warnings.add("bad z");
          }
        }
        default -> warnings.add("unknown " + key);
      }
    }

    return new VisualizerOptions(bars, color, accent, alpha, glow, style, height, z, warnings);
  }

  private boolean isVisualizerOptionKey(String key) {
    return "bars".equals(key)
        || "color".equals(key)
        || "accent".equals(key)
        || "alpha".equals(key)
        || "glow".equals(key)
        || "style".equals(key)
        || "height".equals(key)
        || "z".equals(key)
        || "zindex".equals(key)
        || "z-index".equals(key)
        || "layer".equals(key)
        || "layerorder".equals(key)
        || "layer-order".equals(key);
  }

  private void applyVisualizerOptions(VnScene scene, VisualizerOptions options) {
    if (scene == null || options == null) return;
    var vars = scene.getState().getVariables();

    if (options.bars() != null) {
      vars.put(VnAudioVisualizerConfig.VAR_BARS, options.bars());
    }
    if (options.color() != null) {
      if (VnAudioVisualizerConfig.isAutoToken(options.color())) vars.remove(VnAudioVisualizerConfig.VAR_COLOR);
      else vars.put(VnAudioVisualizerConfig.VAR_COLOR, options.color().trim());
    }
    if (options.accent() != null) {
      if (VnAudioVisualizerConfig.isAutoToken(options.accent())) vars.remove(VnAudioVisualizerConfig.VAR_ACCENT);
      else vars.put(VnAudioVisualizerConfig.VAR_ACCENT, options.accent().trim());
    }
    if (options.alpha() != null) {
      if (Math.abs(options.alpha() - VnAudioVisualizerConfig.DEFAULT_ALPHA) < 0.0001) vars.remove(VnAudioVisualizerConfig.VAR_ALPHA);
      else vars.put(VnAudioVisualizerConfig.VAR_ALPHA, options.alpha());
    }
    if (options.glow() != null) {
      if (options.glow()) vars.remove(VnAudioVisualizerConfig.VAR_GLOW);
      else vars.put(VnAudioVisualizerConfig.VAR_GLOW, Boolean.FALSE);
    }
    if (options.style() != null) {
      if (VnAudioVisualizerConfig.DEFAULT_STYLE.equals(options.style())) vars.remove(VnAudioVisualizerConfig.VAR_STYLE);
      else vars.put(VnAudioVisualizerConfig.VAR_STYLE, options.style());
    }
    if (options.height() != null) {
      if (Math.abs(options.height() - VnAudioVisualizerConfig.DEFAULT_HEIGHT) < 0.0001) vars.remove(VnAudioVisualizerConfig.VAR_HEIGHT);
      else vars.put(VnAudioVisualizerConfig.VAR_HEIGHT, options.height());
    }
    if (options.z() != null) {
      if (options.z() == VnAudioVisualizerConfig.DEFAULT_Z) vars.remove(VnAudioVisualizerConfig.VAR_Z);
      else vars.put(VnAudioVisualizerConfig.VAR_Z, options.z());
    }
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
          try { int d = Integer.parseInt(toks[1]); scene.getState().scrollHistoryByLines(d); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        }
        break;
      }
      case "clear": scene.getState().clearHistoryScroll(); break;
    }
  }

  private void handleAudio(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return;
    String cmd = toks[0].toLowerCase(Locale.ROOT);
    var a = scene.getAudioFacade(); if (a == null) return;
    switch (cmd) {
      case "pause":
        if (toks.length >= 2 && "all".equals(normalizeAudioChannel(toks[1]))) a.pauseAllAudio();
        else a.pauseBgm();
        break;
      case "resume":
        if (toks.length >= 2 && "all".equals(normalizeAudioChannel(toks[1]))) a.resumeAllAudio();
        else a.resumeBgm();
        break;
      case "stop":
        handleAudioStop(toks, a);
        break;
      case "volume":
      case "vol":
        handleAudioVolume(toks, scene, a);
        break;
      case "pause_all":
      case "pauseall":
        a.pauseAllAudio();
        break;
      case "resume_all":
      case "resumeall":
        a.resumeAllAudio();
        break;
      case "bgm_stop":
      case "stop_bgm":
        a.stopBgm();
        break;
      case "sfx_stop":
      case "stop_sfx":
        a.stopSfx();
        break;
      case "voice_stop":
      case "stop_voice":
        a.stopVoice();
        break;
      case "stop_all":
      case "all_stop":
      case "audio_stop_all":
        a.stopAllAudio();
        break;
      case "seek":
        if (toks.length >= 2) {
          try { a.seekBgmSeconds(Double.parseDouble(toks[1])); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        }
        break;
      case "crossfade":
        handleAudioCrossfade(toks, a);
        break;
    }
  }

  private void handleAudioStop(String[] toks, AudioFacade audio) {
    String channel = toks.length >= 2 ? normalizeAudioChannel(toks[1]) : "all";
    switch (channel) {
      case "bgm" -> audio.stopBgm();
      case "sfx" -> audio.stopSfx();
      case "voice" -> audio.stopVoice();
      default -> audio.stopAllAudio();
    }
  }

  private void handleAudioVolume(String[] toks, VnScene scene, AudioFacade audio) {
    if (toks == null || toks.length < 2 || scene == null || audio == null) return;

    String channelToken = null;
    String valueToken = null;
    if (toks.length >= 3) {
      channelToken = toks[1];
      valueToken = toks[2];
    } else {
      int sep = toks[1].indexOf('=');
      if (sep > 0 && sep < toks[1].length() - 1) {
        channelToken = toks[1].substring(0, sep);
        valueToken = toks[1].substring(sep + 1);
      }
    }
    if (channelToken == null || valueToken == null) return;

    String channel = normalizeAudioChannel(channelToken);
    if (!"bgm".equals(channel) && !"sfx".equals(channel) && !"voice".equals(channel)) return;

    try {
      float volume = clamp01(Float.parseFloat(valueToken));
      VnSettings settings = scene.getState().getSettings();
      switch (channel) {
        case "bgm" -> {
          settings.setBgmVolume(volume);
          audio.setBgmVolume(volume);
        }
        case "sfx" -> {
          settings.setSfxVolume(volume);
          audio.setSfxVolume(volume);
        }
        case "voice" -> {
          settings.setVoiceVolume(volume);
          audio.setVoiceVolume(volume);
        }
        default -> {
        }
      }
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  private void handleAudioCrossfade(String[] toks, AudioFacade audio) {
    if (toks == null || toks.length < 3 || audio == null) return;

    String track = null;
    Long durationMs = null;
    Boolean loop = null;

    for (int i = 1; i < toks.length; i++) {
      String token = toks[i];
      if (token == null || token.isBlank()) continue;

      int eq = token.indexOf('=');
      if (eq > 0 && eq < token.length() - 1) {
        String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = token.substring(eq + 1).trim();
        switch (key) {
          case "track", "id", "audio" -> track = value;
          case "dur", "duration", "ms" -> {
            long parsed = parseLongSafe(value, -1L);
            if (parsed >= 0L) durationMs = parsed;
          }
          case "loop" -> loop = parseBooleanMaybe(value);
          default -> {
          }
        }
        continue;
      }

      if (track == null) {
        track = token;
        continue;
      }
      if (durationMs == null) {
        long parsed = parseLongSafe(token, -1L);
        if (parsed >= 0L) {
          durationMs = parsed;
          continue;
        }
      }
      if (loop == null) {
        Boolean parsed = parseBooleanMaybe(token);
        if (parsed != null) {
          loop = parsed;
        }
      }
    }

    if (track == null || durationMs == null || durationMs < 0L) return;
    audio.crossfadeBgm(track, durationMs, loop == null || loop);
  }

  private String normalizeAudioChannel(String token) {
    if (token == null || token.isBlank()) return "all";
    return switch (token.trim().toLowerCase(Locale.ROOT)) {
      case "music" -> "bgm";
      case "sound" -> "sfx";
      case "master" -> "all";
      default -> token.trim().toLowerCase(Locale.ROOT);
    };
  }

  private VnInteropResult handleScreen(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String cmd = toks[0].toLowerCase();
    switch (cmd) {
      case "shake": {
        float intensity = toks.length >= 2 ? parseFloatSafe(toks[1], 8f) : 8f;
        long ms = toks.length >= 3 ? parseLongSafe(toks[2], 300L) : 300L;
        scene.getState().triggerScreenShake(intensity, ms);
        return VnInteropResult.advance();
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
        return VnInteropResult.advance();
      }
      case "clear":
        scene.getState().triggerScreenShake(0f, 0);
        scene.getState().triggerFlash(1f, 1f, 1f, 0f, 0);
        scene.getState().clearOverlayScreens();
        return VnInteropResult.advance();
      case "show", "call": {
        if (toks.length < 2) {
          scene.getState().showHudMessage("screen: missing id", 1200);
          return VnInteropResult.advance();
        }
        String id = toks[1];
        Map<String, String> options = parseKeyValueTokens(toks, 2);
        boolean callScreen = "call".equals(cmd) || parseBooleanOption(options, "call", false);
        VnOverlayScreenSpec spec = buildOverlayScreen(id, options, callScreen, scene);
        scene.getState().showOverlayScreen(spec);
        if (callScreen) {
          scene.beginInteropBlock(() -> !scene.getState().hasOverlayScreen(spec.getId()));
          return VnInteropResult.block();
        }
        return VnInteropResult.advance();
      }
      case "hide", "close":
        if (toks.length >= 2) scene.getState().hideOverlayScreen(toks[1]);
        else scene.getState().dismissTopOverlayScreen();
        return VnInteropResult.advance();
      case "return": {
        String id = toks.length >= 2 ? toks[1] : null;
        String value = toks.length >= 3 ? joinTail(toks, 2) : "";
        scene.getState().returnOverlayScreen(id, value);
        return VnInteropResult.advance();
      }
    }
    return VnInteropResult.advance();
  }

  private void startTimelinePlayback(
      TimelineData data,
      VnScene scene,
      boolean waitForCompletion,
      List<String> chain
  ) {
    if (data == null || scene == null) return;
    recordTimelineDisplacements(data, scene);
    final boolean[] completed = {false};
    TimelineRunner runner = createTimelineRunnerChain(data, scene, chain, 0, completed);
    scene.getState().addTimelineRunner(runner);
    if (waitForCompletion) {
      scene.beginInteropBlock(() -> completed[0]);
    }
  }

  private TimelineRunner createTimelineRunnerChain(
      TimelineData data,
      VnScene scene,
      List<String> chain,
      int index,
      boolean[] completed
  ) {
    TimelineRunner runner = new TimelineRunner(data, sceneAccessor);
    runner.setOnFinished(() -> {
      Optional<TimelineData> next = resolveChainedTimeline(chain, index, scene);
      if (next.isEmpty()) {
        completed[0] = true;
        return;
      }
      recordTimelineDisplacements(next.get(), scene);
      TimelineRunner nextRunner = createTimelineRunnerChain(next.get(), scene, chain, index + 1, completed);
      scene.getState().addTimelineRunner(nextRunner);
    });
    return runner;
  }

  private void recordTimelineDisplacements(TimelineData data, VnScene scene) {
    if (data == null || scene == null || scene.getState() == null) return;
    VnState state = scene.getState();
    Map<String, TimelineDisplacementAccumulator> displacements = new LinkedHashMap<>();
    Map<String, TimelineTransformAccumulator> transforms = new LinkedHashMap<>();
    for (TimelineData.Track track : data.getTracks()) {
      if (track == null) continue;
      String characterId = resolveTimelineTrackCharacter(track.getEntityName(), state);
      if (characterId == null || characterId.isBlank()) continue;
      boolean characterTrack = track.getEntityName() != null && track.getEntityName().trim().equals(characterId);

      boolean hasX = track.hasKeyframes(TimelineData.Property.X);
      boolean hasY = track.hasKeyframes(TimelineData.Property.Y);
      if (hasX || hasY) {
        double x = hasX ? lastKeyframeValue(track, TimelineData.Property.X) : 0.0;
        double y = hasY ? lastKeyframeValue(track, TimelineData.Property.Y) : 0.0;
        displacements
            .computeIfAbsent(characterId, ignored -> new TimelineDisplacementAccumulator())
            .accept(x, y, hasX, hasY);
      }

      boolean hasScaleX = track.hasKeyframes(TimelineData.Property.SCALE_X);
      boolean hasScaleY = track.hasKeyframes(TimelineData.Property.SCALE_Y);
      boolean hasMirrorX = track.hasKeyframes(TimelineData.Property.MIRROR_X);
      boolean hasRotation = track.hasKeyframes(TimelineData.Property.ROTATION);
      boolean hasPivotX = track.hasKeyframes(TimelineData.Property.PIVOT_X);
      boolean hasPivotY = track.hasKeyframes(TimelineData.Property.PIVOT_Y);
      if (!hasScaleX && !hasScaleY && !hasMirrorX && !hasRotation && !hasPivotX && !hasPivotY) continue;

      double scaleX = 1.0;
      if (hasScaleX || hasMirrorX) {
        scaleX = hasScaleX ? lastKeyframeValue(track, TimelineData.Property.SCALE_X) : 1.0;
        if (hasMirrorX) {
          scaleX *= mirrorFactor(lastKeyframeValue(track, TimelineData.Property.MIRROR_X));
        }
      }
      double scaleY = hasScaleY ? lastKeyframeValue(track, TimelineData.Property.SCALE_Y) : 1.0;
      double rotation = hasRotation ? lastKeyframeValue(track, TimelineData.Property.ROTATION) : 0.0;
      double pivotX = hasPivotX ? lastKeyframeValue(track, TimelineData.Property.PIVOT_X) : 0.5;
      double pivotY = hasPivotY ? lastKeyframeValue(track, TimelineData.Property.PIVOT_Y) : 1.0;
      transforms
          .computeIfAbsent(characterId, ignored -> new TimelineTransformAccumulator())
          .accept(
              scaleX,
              scaleY,
              hasScaleX || hasMirrorX,
              hasScaleY,
              rotation,
              hasRotation,
              pivotX,
              pivotY,
              hasPivotX,
              hasPivotY,
              characterTrack);
    }
    for (Map.Entry<String, TimelineDisplacementAccumulator> entry : displacements.entrySet()) {
      TimelineDisplacementAccumulator displacement = entry.getValue();
      state.recordTimelineDisplacement(
          entry.getKey(),
          displacement.x,
          displacement.y,
          displacement.hasX,
          displacement.hasY);
    }
    for (Map.Entry<String, TimelineTransformAccumulator> entry : transforms.entrySet()) {
      TimelineTransformAccumulator transform = entry.getValue();
      state.recordTimelineTransform(
          entry.getKey(),
          0.0,
          0.0,
          false,
          false,
          transform.scaleX,
          transform.scaleY,
          transform.shouldRecordScaleX(),
          transform.shouldRecordScaleY(),
          transform.rotation,
          transform.shouldRecordRotation(),
          transform.pivotX,
          transform.pivotY,
          transform.shouldRecordPivotX(),
          transform.shouldRecordPivotY());
    }
  }

  private String resolveTimelineTrackCharacter(String entityName, VnState state) {
    if (entityName == null || entityName.isBlank() || state == null) return null;
    String target = entityName.trim();
    List<VnState.CharacterSlot> slots = new ArrayList<>();
    slots.addAll(state.getVisibleCharacters().values());
    for (VnState.DetachedCharacterSlot detached : state.getDetachedCharacters().values()) {
      if (detached != null && detached.getSlot() != null) {
        slots.add(detached.getSlot());
      }
    }
    slots.sort((a, b) -> Integer.compare(
        safe(b == null ? "" : b.getCharacterId()).length(),
        safe(a == null ? "" : a.getCharacterId()).length()));
    for (VnState.CharacterSlot slot : slots) {
      if (slot == null || slot.getCharacterId() == null || slot.getCharacterId().isBlank()) continue;
      String characterId = slot.getCharacterId().trim();
      if (target.equals(characterId)) return characterId;
      String safeCharacter = selectorSafeName(characterId);
      String safeExpression = selectorSafeName(slot.getExpression() == null || slot.getExpression().isBlank()
          ? "neutral"
          : slot.getExpression());
      if (!safeCharacter.isBlank() && !safeExpression.isBlank()
          && target.startsWith(safeCharacter + "_" + safeExpression + "_")) {
        return characterId;
      }
      if (!safeCharacter.isBlank() && target.startsWith(safeCharacter + "_")) {
        return characterId;
      }
    }
    return null;
  }

  private double lastKeyframeValue(TimelineData.Track track, TimelineData.Property property) {
    List<TimelineData.Keyframe> keyframes = track.getKeyframes(property);
    if (keyframes.isEmpty()) return 0.0;
    return keyframes.get(keyframes.size() - 1).getValue();
  }

  private static double mirrorFactor(double mirrorX) {
    if (!Double.isFinite(mirrorX)) return 1.0;
    double clamped = Math.max(0.0, Math.min(1.0, mirrorX));
    return Math.cos(clamped * Math.PI);
  }

  private String selectorSafeName(String raw) {
    String value = raw == null ? "" : raw.trim();
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
        out.append(ch);
      } else {
        out.append('_');
      }
    }
    String cleaned = out.toString().replaceAll("_+", "_");
    while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
    while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
    return cleaned;
  }

  private static final class TimelineDisplacementAccumulator {
    private double x;
    private double y;
    private boolean hasX;
    private boolean hasY;

    private void accept(double x, double y, boolean hasX, boolean hasY) {
      if (hasX && Double.isFinite(x) && (!this.hasX || Math.abs(x) >= Math.abs(this.x))) {
        this.x = x;
        this.hasX = true;
      }
      if (hasY && Double.isFinite(y) && (!this.hasY || Math.abs(y) >= Math.abs(this.y))) {
        this.y = y;
        this.hasY = true;
      }
    }
  }

  private static final class TimelineTransformAccumulator {
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double rotation = 0.0;
    private double pivotX = 0.5;
    private double pivotY = 1.0;
    private int scaleXCount;
    private int scaleYCount;
    private int rotationCount;
    private int pivotXCount;
    private int pivotYCount;
    private boolean characterScaleX;
    private boolean characterScaleY;
    private boolean characterRotation;
    private boolean characterPivotX;
    private boolean characterPivotY;

    private void accept(
        double scaleX,
        double scaleY,
        boolean hasScaleX,
        boolean hasScaleY,
        double rotation,
        boolean hasRotation,
        double pivotX,
        double pivotY,
        boolean hasPivotX,
        boolean hasPivotY,
        boolean characterTrack) {
      if (hasScaleX && Double.isFinite(scaleX)) {
        this.scaleX = scaleX;
        scaleXCount++;
        characterScaleX |= characterTrack;
      }
      if (hasScaleY && Double.isFinite(scaleY)) {
        this.scaleY = scaleY;
        scaleYCount++;
        characterScaleY |= characterTrack;
      }
      if (hasRotation && Double.isFinite(rotation)) {
        this.rotation = rotation;
        rotationCount++;
        characterRotation |= characterTrack;
      }
      if (hasPivotX && Double.isFinite(pivotX)) {
        this.pivotX = pivotX;
        pivotXCount++;
        characterPivotX |= characterTrack;
      }
      if (hasPivotY && Double.isFinite(pivotY)) {
        this.pivotY = pivotY;
        pivotYCount++;
        characterPivotY |= characterTrack;
      }
    }

    private boolean shouldRecordScaleX() {
      return characterScaleX || scaleXCount > 1;
    }

    private boolean shouldRecordScaleY() {
      return characterScaleY || scaleYCount > 1;
    }

    private boolean shouldRecordRotation() {
      return characterRotation || rotationCount > 1;
    }

    private boolean shouldRecordPivotX() {
      return characterPivotX || pivotXCount > 1;
    }

    private boolean shouldRecordPivotY() {
      return characterPivotY || pivotYCount > 1;
    }
  }

  private Optional<TimelineData> resolveChainedTimeline(List<String> chain, int index, VnScene scene) {
    if (chain == null || index >= chain.size()) return Optional.empty();
    String name = chain.get(index);
    if (name == null || name.isBlank()) return Optional.empty();
    return resolveTimelineData(name.trim(), scene).data();
  }

  private TimelineResolveResult resolveTimelineData(String rawName, VnScene scene) {
    String name = rawName == null ? "" : rawName.trim();
    TimelineData data = TimelineRegistry.get(name);
    if (data != null) return new TimelineResolveResult(Optional.of(data), false);

    String lookupName = normalizedTimelineLookupName(name);
    if (!lookupName.equals(name)) {
      data = TimelineRegistry.get(lookupName);
      if (data != null) return new TimelineResolveResult(Optional.of(data), false);
    }

    return loadTimelineFromAssets(name, lookupName, scene);
  }

  private TimelineResolveResult loadTimelineFromAssets(String rawName, String lookupName, VnScene scene) {
    if (lookupName == null || lookupName.isBlank()) {
      return new TimelineResolveResult(Optional.empty(), false);
    }
    for (String candidate : timelineAssetCandidates(rawName, lookupName)) {
      String source = "";
      try (InputStream in = new AssetCatalog().open(AssetType.SCRIPT, candidate)) {
        source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        TimelineData data = TimelineDataParser.parse(lookupName, source);
        TimelineRegistry.register(data);
        return new TimelineResolveResult(Optional.of(data), false);
      } catch (IOException ignored) {
        // Try the next conventional timeline path.
      } catch (RuntimeException ex) {
        if (scene != null) {
          scene.getState().showHudMessage("jes_timeline parse error: " + shortMessage(ex), 2500);
          scene.setActiveError(VnErrorOverlay.puppeteerJesParseError(candidate, source, ex));
        }
        return new TimelineResolveResult(Optional.empty(), true);
      }
    }
    return new TimelineResolveResult(Optional.empty(), false);
  }

  private List<String> timelineAssetCandidates(String rawName, String lookupName) {
    String normalizedRaw = normalizeTimelineAssetPath(rawName);
    String normalizedLookup = normalizeTimelineAssetPath(lookupName);
    if (normalizedLookup.isBlank() || isUnsafeTimelineAssetPath(normalizedLookup)) return List.of();

    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    boolean explicitPath = normalizedRaw.contains("/") || normalizedRaw.endsWith(".jes");
    if (explicitPath && !isUnsafeTimelineAssetPath(normalizedRaw)) {
      candidates.add(ensureJesExtension(normalizedRaw));
    }
    if (!normalizedLookup.startsWith("timelines/") && !normalizedLookup.startsWith("scripts/")) {
      candidates.add("timelines/" + ensureJesExtension(normalizedLookup));
    }
    if (!explicitPath && !normalizedRaw.isBlank() && !isUnsafeTimelineAssetPath(normalizedRaw)) {
      candidates.add(ensureJesExtension(normalizedRaw));
    }
    candidates.add(ensureJesExtension(normalizedLookup));
    return new ArrayList<>(candidates);
  }

  private String normalizedTimelineLookupName(String rawName) {
    String normalized = normalizeTimelineAssetPath(rawName);
    if (normalized.endsWith(".jes")) {
      normalized = normalized.substring(0, normalized.length() - ".jes".length());
    }
    if (normalized.startsWith("scripts/timelines/")) {
      normalized = normalized.substring("scripts/timelines/".length());
    } else if (normalized.startsWith("timelines/")) {
      normalized = normalized.substring("timelines/".length());
    }
    return normalized;
  }

  private String normalizeTimelineAssetPath(String rawName) {
    String normalized = rawName == null ? "" : rawName.trim().replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    return normalized;
  }

  private boolean isUnsafeTimelineAssetPath(String normalized) {
    return normalized.contains("..") || normalized.contains(":");
  }

  private String ensureJesExtension(String name) {
    return name.endsWith(".jes") ? name : name + ".jes";
  }

  private String shortMessage(Throwable ex) {
    if (ex == null) return "unknown error";
    String message = ex.getMessage();
    if (message == null || message.isBlank()) message = ex.getClass().getSimpleName();
    return message.length() > 120 ? message.substring(0, 120) + "..." : message;
  }

  private TimelineInvocation parseTimelineInvocation(String payload) {
    String[] toks = split(payload);
    if (toks.length == 0) return new TimelineInvocation("", false, List.of());
    String name = toks[0].trim();
    boolean wait = false;
    List<String> chain = new ArrayList<>();
    boolean collectChainNames = false;
    for (int i = 1; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);
      if ("wait".equals(lower) || "sync".equals(lower) || "block".equals(lower) || "await".equals(lower)) {
        wait = true;
        collectChainNames = false;
        continue;
      }
      if ("then".equals(lower) || "chain".equals(lower)) {
        collectChainNames = true;
        continue;
      }
      if (lower.startsWith("chain=") || lower.startsWith("then=")) {
        collectChainTargets(token.substring(token.indexOf('=') + 1), chain);
        collectChainNames = false;
        continue;
      }
      if (collectChainNames) {
        collectChainTargets(token, chain);
      }
    }
    return new TimelineInvocation(name, wait, chain);
  }

  private InlineTimelineInvocation parseInlineTimelineInvocation(String payload) {
    String normalized = payload == null ? "" : payload.trim();
    boolean wait = true;
    List<String> chain = new ArrayList<>();
    String block = normalized;
    String tail = "";
    int lastBrace = normalized.lastIndexOf('}');
    if (lastBrace >= 0 && lastBrace < normalized.length() - 1) {
      block = normalized.substring(0, lastBrace + 1).trim();
      tail = normalized.substring(lastBrace + 1).trim();
    }
    if (!tail.isBlank()) {
      TimelineInvocation tailInvocation = parseTimelineInvocation("_inline_ " + tail);
      if (containsAnyToken(tail, "async", "nowait", "no_wait", "fire", "fire_and_forget")) {
        wait = false;
      }
      if (tailInvocation.waitForCompletion()) {
        wait = true;
      }
      chain.addAll(tailInvocation.chain());
    }
    return new InlineTimelineInvocation(block, wait, chain);
  }

  private boolean containsAnyToken(String raw, String... candidates) {
    if (raw == null || raw.isBlank() || candidates == null || candidates.length == 0) return false;
    Set<String> wanted = new HashSet<>();
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        wanted.add(candidate.trim().toLowerCase(Locale.ROOT));
      }
    }
    if (wanted.isEmpty()) return false;
    for (String token : split(raw)) {
      String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
      if (wanted.contains(normalized)) return true;
    }
    return false;
  }

  private void collectChainTargets(String raw, List<String> out) {
    if (raw == null || raw.isBlank() || out == null) return;
    for (String part : raw.split(",")) {
      String trimmed = part == null ? "" : part.trim();
      if (!trimmed.isEmpty()) out.add(trimmed);
    }
  }

  private Map<String, String> parseKeyValueTokens(String[] toks, int start) {
    Map<String, String> options = new LinkedHashMap<>();
    if (toks == null || start >= toks.length) return options;
    for (int i = start; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;
      int eq = token.indexOf('=');
      if (eq > 0 && eq < token.length() - 1) {
        options.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT), token.substring(eq + 1).trim());
      } else {
        options.put(token.toLowerCase(Locale.ROOT), "true");
      }
    }
    return options;
  }

  private VnOverlayScreenSpec buildOverlayScreen(String id, Map<String, String> options, boolean callScreen, VnScene scene) {
    VnOverlayScreenSpec reactive = loadReactiveOverlayScreen(id, callScreen, scene);
    if (reactive != null && shouldUseReactiveScreenDefinition(options)) {
      return reactive;
    }
    double x = parseDoubleSafe(option(options, "x", "0.18"), 0.18);
    double y = parseDoubleSafe(option(options, "y", "0.18"), 0.18);
    double width = parseDoubleSafe(option(options, "w", option(options, "width", "0.64")), 0.64);
    double height = parseDoubleSafe(option(options, "h", option(options, "height", "0.42")), 0.42);
    boolean modal = parseBooleanOption(options, "modal", callScreen);
    boolean dim = parseBooleanOption(options, "dim", true);
    boolean dismiss = parseBooleanOption(options, "dismiss", !callScreen);
    long timer = parseLongSafe(option(options, "timer", "0"), 0L);
    String timerAction = option(options, "timeraction", option(options, "timer_action", callScreen ? "return" : "hide"));
    String timerTarget = option(options, "timertarget", option(options, "timer_target", ""));
    String returnKey = option(options, "returnkey", option(options, "return_key", "screen.return." + id));
    String title = option(options, "title", id);
    String text = option(options, "text", option(options, "body", ""));
    List<VnOverlayButtonSpec> buttons = new ArrayList<>();
    buttons.addAll(parseAutoButtons(id, option(options, "buttons", ""), width, height));
    buttons.addAll(parseHotspots(id, option(options, "hotspots", "")));
    return new VnOverlayScreenSpec(
        id,
        title,
        text,
        x,
        y,
        width,
        height,
        modal,
        dim,
        dismiss,
        callScreen,
        timer,
        timerAction,
        timerTarget,
        returnKey,
        buttons
    );
  }

  private VnOverlayScreenSpec loadReactiveOverlayScreen(String id, boolean callScreen, VnScene scene) {
    VnReactiveScreenLoader.LoadResult result = VnReactiveScreenLoader.loadFromAssets(id);
    VnReactiveScreenSpec definition = result.screen();
    if (definition == null) return null;
    return new VnReactiveOverlayScreenSpec(definition, scene == null ? null : scene.getState(), callScreen);
  }

  private boolean shouldUseReactiveScreenDefinition(Map<String, String> options) {
    if (options == null || options.isEmpty()) return true;
    if (parseBooleanOption(options, "file", false) || parseBooleanOption(options, "definition", false)) return true;
    for (String key : options.keySet()) {
      if (key == null) continue;
      String normalized = key.trim().toLowerCase(Locale.ROOT);
      if (normalized.equals("call") || normalized.equals("modal") || normalized.equals("dim")) continue;
      if (normalized.equals("dismiss") || normalized.equals("timer") || normalized.equals("timeraction")) continue;
      if (normalized.equals("timer_action") || normalized.equals("timertarget") || normalized.equals("timer_target")) continue;
      if (normalized.equals("returnkey") || normalized.equals("return_key")) continue;
      return false;
    }
    return true;
  }

  private List<VnOverlayButtonSpec> parseAutoButtons(String screenId, String spec, double screenWidth, double screenHeight) {
    if (spec == null || spec.isBlank()) return List.of();
    List<VnOverlayButtonSpec> buttons = new ArrayList<>();
    String[] rows = spec.split(";");
    int count = 0;
    for (String row : rows) {
      if (row != null && !row.trim().isEmpty()) count++;
    }
    if (count == 0) return List.of();
    int visibleIndex = 0;
    double buttonWidth = Math.min(0.28, Math.max(0.18, (0.82 / count)));
    double gap = 0.03;
    double totalWidth = count * buttonWidth + Math.max(0, count - 1) * gap;
    double startX = Math.max(0.06, (1.0 - totalWidth) * 0.5);
    for (String row : rows) {
      if (row == null || row.trim().isEmpty()) continue;
      String[] parts = row.split("\\|", -1);
      String label = parts.length > 0 ? parts[0].trim() : "Button";
      String action = parts.length > 1 ? parts[1].trim() : "noop";
      String target = parts.length > 2 ? parts[2].trim() : "";
      buttons.add(new VnOverlayButtonSpec(
          "btn_" + screenId + "_" + visibleIndex,
          screenId,
          label,
          action,
          target,
          true,
          startX + visibleIndex * (buttonWidth + gap),
          0.74,
          buttonWidth,
          0.16,
          "screen"
      ));
      visibleIndex++;
    }
    return buttons;
  }

  private List<VnOverlayButtonSpec> parseHotspots(String screenId, String spec) {
    if (spec == null || spec.isBlank()) return List.of();
    List<VnOverlayButtonSpec> buttons = new ArrayList<>();
    String[] rows = spec.split(";");
    for (int i = 0; i < rows.length; i++) {
      String row = rows[i];
      if (row == null || row.trim().isEmpty()) continue;
      String[] parts = row.split("\\|", -1);
      if (parts.length < 7) continue;
      buttons.add(new VnOverlayButtonSpec(
          parts[0].trim().isEmpty() ? ("hotspot_" + i) : parts[0].trim(),
          screenId,
          parts[0].trim(),
          parts[1].trim(),
          parts[2].trim(),
          true,
          parseDoubleSafe(parts[3].trim(), 0.0),
          parseDoubleSafe(parts[4].trim(), 0.0),
          parseDoubleSafe(parts[5].trim(), 0.12),
          parseDoubleSafe(parts[6].trim(), 0.08),
          parts.length >= 8 ? parts[7].trim() : "viewport"
      ));
    }
    return buttons;
  }

  private boolean parseBooleanOption(Map<String, String> options, String key, boolean fallback) {
    String value = option(options, key, null);
    if (value == null) return fallback;
    Boolean parsed = parseBooleanMaybe(value);
    return parsed != null ? parsed : fallback;
  }

  private String option(Map<String, String> options, String key, String fallback) {
    if (options == null || key == null) return fallback;
    String value = options.get(key.toLowerCase(Locale.ROOT));
    return value == null ? fallback : value;
  }

  private record TimelineInvocation(String name, boolean waitForCompletion, List<String> chain) {}
  private record InlineTimelineInvocation(String block, boolean waitForCompletion, List<String> chain) {}
  private record TimelineResolveResult(Optional<TimelineData> data, boolean parseFailed) {}

  private void handleEyeFocus(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return;
    VnState state = scene.getState();
    if (state == null) return;

    String characterId = toks[0].trim();
    if (characterId.isBlank()) return;

    Map<String, String> options = new LinkedHashMap<>();
    String targetCharacter = "";
    Double targetX = null;
    Double targetY = null;
    boolean clear = false;

    for (int i = 1; i < toks.length; i++) {
      String token = toks[i] == null ? "" : toks[i].trim();
      if (token.isEmpty()) continue;
      if ("clear".equalsIgnoreCase(token) || "off".equalsIgnoreCase(token)) {
        clear = true;
        continue;
      }
      if ("at".equalsIgnoreCase(token) && i + 1 < toks.length) {
        double[] point = parsePoint(toks[++i]);
        if (point != null) {
          targetX = point[0];
          targetY = point[1];
        }
        continue;
      }
      int eq = token.indexOf('=');
      if (eq > 0 && eq < token.length() - 1) {
        String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = token.substring(eq + 1).trim();
        if ("at".equals(key) || "point".equals(key) || "xy".equals(key)) {
          double[] point = parsePoint(value);
          if (point != null) {
            targetX = point[0];
            targetY = point[1];
          }
        } else if ("target".equals(key) || "to".equals(key)) {
          targetCharacter = value;
        } else {
          options.put(key, value);
        }
        continue;
      }
      if (targetCharacter.isBlank()) {
        targetCharacter = token;
      }
    }

    if (clear) {
      state.clearEyeFocusRequest(characterId);
      return;
    }

    if (targetX == null && targetY == null && targetCharacter.isBlank()) {
      return;
    }

    String expression = option(options, "expression", option(options, "expr", state.getCharacterExpression(characterId)));
    long durationMs = parseLongSafe(option(options, "dur", option(options, "duration", "180")), 180L);
    double strength = parseDoubleSafe(option(options, "strength", "1"), 1.0);
    double deadZone = parseDoubleSafe(option(options, "deadzone", option(options, "deadZone", "0.12")), 0.12);
    state.setEyeFocusRequest(new VnState.EyeFocusRequest(
        characterId,
        expression,
        targetCharacter,
        targetX,
        targetY,
        durationMs,
        strength,
        deadZone));
  }

  private void handleCharacter(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length < 2) return;

    String characterId = toks[0].trim();
    String cmd = toks[1].toLowerCase();
    VnState state = scene.getState();
    if (characterId.isEmpty() || state == null) return;

    switch (cmd) {
      case "global":
      case "global_position": {
        if (toks.length < 3) return;
        state.setCharacterGlobalPositionEnabled(characterId, parseBoolean(toks[2]));
        break;
      }
      case "position":
      case "pos":
      case "at": {
        if (toks.length < 3) return;
        String displaySlot = null;
        CharacterPosition position;
        if ("at".equalsIgnoreCase(toks[2]) && toks.length >= 4) {
          position = parseInlinePosition(toks[3]);
        } else {
          position = parsePositionToken(toks[2]);
        }
        if (position == null) return;
        for (int ti = 3; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          int sep = optionSeparator(tok);
          if (sep <= 0) continue;
          String key = tok.substring(0, sep).trim().toLowerCase(Locale.ROOT);
          String value = tok.substring(sep + 1).trim();
          if (isDisplaySlotOption(key)) displaySlot = value;
        }
        if (displaySlot == null || displaySlot.isBlank()) {
          state.setCharacterDefinedPosition(characterId, position);
        }
        if (state.isCharacterGlobalPositionEnabled(characterId)) {
          String expression = state.getCharacterExpression(characterId, displaySlot);
          state.showCharacterAnimated(position, characterId, expression == null ? "neutral" : expression,
              null, null, 0, displaySlot);
        }
        break;
      }
      case "move": {
        if (toks.length < 3) return;
        CharacterPosition position;
        int startIdx;
        if ("at".equalsIgnoreCase(toks[2]) && toks.length >= 4) {
          position = parseInlinePosition(toks[3]);
          startIdx = 4;
        } else {
          position = parsePositionToken(toks[2]);
          startIdx = 3;
        }
        if (position == null) return;
        String expression = null;
        String displaySlot = null;
        Easing.Type easingType = null;
        long durationMs = 0;
        long expressionDurationMs = -1L;
        for (int ti = startIdx; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          int sep = optionSeparator(tok);
          if (sep > 0) {
            String key = tok.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String value = tok.substring(sep + 1).trim();
            switch (key) {
              case "expr":
              case "expression":
              case "preset":
                expression = value;
                break;
              case "slot":
              case "as":
              case "instance":
              case "display":
              case "display_slot":
              case "display-slot":
                displaySlot = value;
                break;
              case "dur":
              case "duration":
              case "ms":
                durationMs = Math.max(0L, parseLongSafe(value, durationMs));
                break;
              case "exprdur":
              case "exprduration":
              case "expr_duration":
              case "expr-duration":
              case "expressiondur":
              case "expressionduration":
              case "expression_duration":
              case "expression-duration":
                expressionDurationMs = Math.max(0L, parseLongSafe(value, expressionDurationMs));
                break;
              case "ease":
              case "easing":
                Easing.Type namedEasing = parseEasingType(value);
                if (namedEasing != null) easingType = namedEasing;
                break;
              default:
                break;
            }
            continue;
          }
          if (tok.matches("\\d+")) {
            durationMs = Long.parseLong(tok);
          } else {
            Easing.Type parsed = parseEasingType(tok);
            if (parsed != null) {
              easingType = parsed;
            } else if (expression == null) {
              expression = tok;
            }
          }
        }
        if (expression == null) expression = state.getCharacterExpression(characterId, displaySlot);
        if (displaySlot == null || displaySlot.isBlank()) {
          state.setCharacterDefinedPosition(characterId, position);
        }
        state.showCharacterAnimated(position, characterId,
            expression == null ? "neutral" : expression,
            null,
            easingType,
            durationMs,
            displaySlot,
            expressionDurationMs);
        break;
      }
      case "show": {
        if (toks.length < 3) return;
        CharacterPosition position;
        int showNextIdx;
        if ("at".equalsIgnoreCase(toks[2]) && toks.length >= 4) {
          position = parseInlinePosition(toks[3]);
          showNextIdx = 4;
        } else {
          position = parsePositionToken(toks[2]);
          showNextIdx = 3;
        }
        if (position == null) return;
        String expression = null;
        String displaySlot = null;
        Easing.Type easingType = null;
        long durationMs = 0L;
        for (int ti = showNextIdx; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          int sep = optionSeparator(tok);
          if (sep > 0) {
            String key = tok.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String value = tok.substring(sep + 1).trim();
            switch (key) {
              case "expr":
              case "expression":
              case "preset":
                expression = value;
                break;
              case "slot":
              case "as":
              case "instance":
              case "display":
              case "display_slot":
              case "display-slot":
                displaySlot = value;
                break;
              case "dur":
              case "duration":
              case "ms":
                durationMs = Math.max(0L, parseLongSafe(value, durationMs));
                break;
              case "ease":
              case "easing":
                Easing.Type namedEasing = parseEasingType(value);
                if (namedEasing != null) easingType = namedEasing;
                break;
              default:
                break;
            }
            continue;
          }
          if (tok.matches("\\d+")) {
            durationMs = Long.parseLong(tok);
          } else {
            Easing.Type parsed = parseEasingType(tok);
            if (parsed != null) {
              easingType = parsed;
            } else if (expression == null) {
              expression = tok;
            }
          }
        }
        if (expression == null) expression = "neutral";
        if (displaySlot == null || displaySlot.isBlank()) {
          state.setCharacterDefinedPosition(characterId, position);
        }
        state.showCharacterAnimated(position, characterId, expression, null, easingType, durationMs, displaySlot);
        break;
      }
      case "expression":
      case "expr": {
        if (toks.length < 3) return;
        String expression = toks[2];
        String displaySlot = null;
        long durationMs = VnState.DEFAULT_EXPRESSION_TRANSITION_MS;
        Easing.Type easingType = null;
        for (int ti = 3; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          int sep = optionSeparator(tok);
          if (sep > 0) {
            String key = tok.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String value = tok.substring(sep + 1).trim();
            switch (key) {
              case "dur":
              case "duration":
              case "ms":
              case "transition":
              case "transitionms":
                durationMs = Math.max(0L, parseLongSafe(value, durationMs));
                break;
              case "slot":
              case "as":
              case "instance":
              case "display":
              case "display_slot":
              case "display-slot":
                displaySlot = value;
                break;
              case "ease":
              case "easing":
                Easing.Type namedEasing = parseEasingType(value);
                if (namedEasing != null) easingType = namedEasing;
                break;
              default:
                break;
            }
            continue;
          }
          if (tok.matches("\\d+")) {
            durationMs = Long.parseLong(tok);
          } else {
            Easing.Type parsed = parseEasingType(tok);
            if (parsed != null) easingType = parsed;
          }
        }
        if (!state.setCharacterExpression(characterId, displaySlot, expression, durationMs, easingType)) {
          CharacterPosition position = state.getCharacterDefinedPosition(characterId);
          if (position == null) position = CharacterPosition.CENTER;
          state.showCharacterAnimated(position, characterId, expression, null, null, 0, displaySlot);
        }
        break;
      }
      case "hide": {
        String displaySlot = null;
        for (int ti = 2; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          int sep = optionSeparator(tok);
          if (sep <= 0) continue;
          String key = tok.substring(0, sep).trim().toLowerCase(Locale.ROOT);
          if (isDisplaySlotOption(key)) displaySlot = tok.substring(sep + 1).trim();
        }
        state.hideCharacterAnimated(characterId, displaySlot);
        break;
      }
      case "bubble": {
        if (toks.length < 3) return;
        String mode = toks[2].toLowerCase();
        if ("clear".equals(mode) || "reset".equals(mode) || "auto".equals(mode)) {
          state.clearBubblePlacementPreference(characterId);
          return;
        }
        state.setBubbleAnchorPreference(characterId, BubbleAnchor.fromToken(mode));
        break;
      }
      case "bubble_offset":
      case "bubbleoffset": {
        if (toks.length < 4) return;
        double x = parseDoubleSafe(toks[2], 0.0);
        double y = parseDoubleSafe(toks[3], 0.0);
        state.setBubbleOffsetPreference(characterId, x, y);
        break;
      }
      default:
        break;
    }
  }

  private boolean parseBoolean(String token) {
    if (token == null) return false;
    String t = token.trim().toLowerCase();
    return "on".equals(t) || "true".equals(t) || "1".equals(t) || "yes".equals(t);
  }

  private Boolean parseBooleanMaybe(String token) {
    if (token == null || token.isBlank()) return null;
    String t = token.trim().toLowerCase();
    return switch (t) {
      case "on", "true", "1", "yes" -> Boolean.TRUE;
      case "off", "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private float clamp01(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }

  private CharacterPosition parsePositionToken(String token) {
    return CharacterPosition.predefined(token);
  }

  private CharacterPosition parseInlinePosition(String coordToken) {
    if (coordToken == null || coordToken.isBlank()) return null;
    String[] parts = coordToken.split(",");
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : -1.0;
      return CharacterPosition.at(x, y);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Easing.Type parseEasingType(String token) {
    if (token == null || token.isBlank()) return null;
    String upper = token.trim().toUpperCase();
    try {
      return Easing.Type.valueOf(upper);
    } catch (IllegalArgumentException ignored) {
            // reason: invalid argument from untrusted input; caller handles absent result
      return null;
    }
  }

  private boolean isDisplaySlotOption(String key) {
    return "slot".equals(key)
        || "as".equals(key)
        || "instance".equals(key)
        || "display".equals(key)
        || "display_slot".equals(key)
        || "display-slot".equals(key);
  }

  private int optionSeparator(String token) {
    if (token == null) return -1;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    if (eq > 0 && colon > 0) return Math.min(eq, colon);
    return Math.max(eq, colon);
  }

  private record MethodSelection(Method method, String errorMessage) {}

  private static MethodSelection findStaticMethod(Class<?> cls, String name, Object[] args) {
    int arity = args == null ? 0 : args.length;
    Method best = null;
    int bestScore = Integer.MAX_VALUE;
    boolean ambiguous = false;
    boolean sawName = false;
    boolean sawArity = false;
    for (Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
      if (!m.getName().equals(name)) continue;
      sawName = true;
      if (m.getParameterCount() != arity) continue;
      sawArity = true;
      int score = coercionScore(m.getParameterTypes(), args);
      if (score < 0) continue;
      if (score < bestScore) {
        best = m;
        bestScore = score;
        ambiguous = false;
      } else if (score == bestScore) {
        ambiguous = true;
      }
    }
    if (best == null) {
      if (!sawName) return new MethodSelection(null, "java: method not found");
      if (!sawArity) return new MethodSelection(null, "java: method arity mismatch");
      return new MethodSelection(null, "java: argument types not supported");
    }
    if (ambiguous) return new MethodSelection(null, "java: ambiguous method overload");
    return new MethodSelection(best, null);
  }

  private static int coercionScore(Class<?>[] types, Object[] args) {
    if (types == null || args == null || types.length != args.length) return -1;
    int score = 0;
    for (int i = 0; i < types.length; i++) {
      int argScore = coercionScore(types[i], args[i]);
      if (argScore < 0) return -1;
      score += argScore;
    }
    return score;
  }

  private static int coercionScore(Class<?> type, Object value) {
    Class<?> t = wrap(type);
    if (value == null) return type != null && type.isPrimitive() ? -1 : 3;
    if (t.isInstance(value)) return 0;
    if (Number.class.isAssignableFrom(t)) {
      if (value instanceof Number) return 1;
      if (value instanceof String s && parseableNumber(t, s)) return 2;
      return -1;
    }
    if (t == Boolean.class) {
      if (value instanceof String s && isBooleanLiteral(s)) return 2;
      return -1;
    }
    if (t == String.class) return 4;
    return -1;
  }

  private static Object[] parseArgs(String[] tokens, int start) {
    if (tokens == null || start >= tokens.length) return new Object[0];
    List<Object> list = new java.util.ArrayList<>();
    for (int i = start; i < tokens.length; i++) {
      String tok = tokens[i];
      list.add(parseScalar(tok));
    }
    return list.toArray();
  }

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
    catch (Exception ignored) { // reason: not a number; caller treats it as a string
    }
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
    if (v == null) {
      if (t != null && t.isPrimitive()) {
        throw new IllegalArgumentException("Cannot pass null to primitive parameter " + t.getSimpleName());
      }
      return null;
    }
    Class<?> wrapped = wrap(t);
    if (wrapped.isInstance(v)) return v;
    if (wrapped == Integer.class) {
      if (v instanceof Number n) return n.intValue();
      return Integer.parseInt(v.toString());
    }
    if (wrapped == Long.class) {
      if (v instanceof Number n) return n.longValue();
      return Long.parseLong(v.toString());
    }
    if (wrapped == Double.class) {
      if (v instanceof Number n) return n.doubleValue();
      return Double.parseDouble(v.toString());
    }
    if (wrapped == Float.class) {
      if (v instanceof Number n) return n.floatValue();
      return Float.parseFloat(v.toString());
    }
    if (wrapped == Short.class) {
      if (v instanceof Number n) return n.shortValue();
      return Short.parseShort(v.toString());
    }
    if (wrapped == Byte.class) {
      if (v instanceof Number n) return n.byteValue();
      return Byte.parseByte(v.toString());
    }
    if (wrapped == Boolean.class) {
      if (v instanceof Boolean b) return b;
      String s = v.toString();
      if (!isBooleanLiteral(s)) throw new IllegalArgumentException("Expected boolean literal");
      return Boolean.parseBoolean(s);
    }
    if (wrapped == String.class) {
      return v.toString();
    }
    throw new IllegalArgumentException("Unsupported Java interop parameter type: " + t.getName());
  }

  private static Class<?> wrap(Class<?> type) {
    if (type == null || !type.isPrimitive()) return type;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static boolean parseableNumber(Class<?> type, String raw) {
    if (raw == null || raw.isBlank()) return false;
    try {
      if (type == Integer.class) Integer.parseInt(raw);
      else if (type == Long.class) Long.parseLong(raw);
      else if (type == Double.class) Double.parseDouble(raw);
      else if (type == Float.class) Float.parseFloat(raw);
      else if (type == Short.class) Short.parseShort(raw);
      else if (type == Byte.class) Byte.parseByte(raw);
      else return false;
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static boolean isBooleanLiteral(String raw) {
    return "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw);
  }

  private static void failJava(VnScene scene, String hudMessage, Throwable cause) {
    if (scene == null || scene.getState() == null) return;
    scene.getState().showHudMessage(hudMessage, 2000);
    Exception overlayCause = cause instanceof Exception e ? e : new RuntimeException(cause);
    scene.setActiveError(VnErrorOverlay.interopError("java", hudMessage, overlayCause));
  }

  private static boolean isJavaClassAllowed(String clsName) {
    if (clsName == null || clsName.isBlank()) return false;
    for (String prefix : ALLOWED_JAVA_CLASS_PREFIXES) {
      if (clsName.startsWith(prefix)) return true;
    }
    return false;
  }

  private static String safe(String s) { return s == null ? "" : s; }
  private static String[] split(String s) {
    return VnArgTokenizer.tokenizeToArray(s);
  }

  private static String joinTail(String[] tokens, int start) {
    return joinRange(tokens, start, tokens == null ? 0 : tokens.length);
  }

  private static String joinRange(String[] tokens, int start, int endExclusive) {
    if (tokens == null || start >= tokens.length || start >= endExclusive) return "";
    int end = Math.min(tokens.length, endExclusive);
    StringBuilder sb = new StringBuilder();
    for (int i = Math.max(0, start); i < end; i++) {
      if (sb.length() > 0) sb.append(' ');
      sb.append(tokens[i]);
    }
    return sb.toString();
  }

  private static double[] parsePoint(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String[] parts = raw.trim().split(",");
    if (parts.length < 2) return null;
    double x = parseDoubleSafe(parts[0].trim(), Double.NaN);
    double y = parseDoubleSafe(parts[1].trim(), Double.NaN);
    if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
    return new double[] {x, y};
  }

  private static float parseFloatSafe(String s, float fallback) {
    try { return Float.parseFloat(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    return fallback;
  }

  private static double parseDoubleSafe(String s, double fallback) {
    try { return Double.parseDouble(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    return fallback;
  }

  private static long parseLongSafe(String s, long fallback) {
    try { return Long.parseLong(s); } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    return fallback;
  }

  private enum VisualizerCommandMode {
    TOGGLE,
    ENABLE,
    DISABLE,
    CONFIGURE_ONLY,
    RESET,
    STATUS
  }

  private record VisualizerOptions(
      Integer bars,
      String color,
      String accent,
      Double alpha,
      Boolean glow,
      String style,
      Double height,
      Integer z,
      List<String> warnings) {}
}

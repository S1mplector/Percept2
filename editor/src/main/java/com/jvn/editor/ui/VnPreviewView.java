package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.audio.simp3.Simp3AudioService;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.menu.HistoryMenuScene;
import com.jvn.core.menu.LoadMenuScene;
import com.jvn.core.menu.SaveMenuScene;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneCommands;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.core.phone.VnPhoneStateStore;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnInteropResult;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import com.jvn.fx.phone.PhoneRenderer;
import com.jvn.fx.vn.VnRenderer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;

public class VnPreviewView extends StackPane {
  private static final String PREVIEW_HINT = String.join("\n",
      "VNS Preview Controls",
      "Click: Advance / Choose",
      "Space, Enter: Advance",
      "Ctrl/Cmd: Toggle Skip    A: Toggle Auto    H: Toggle UI",
      "B: Toggle History    Esc: Close overlays",
      "F5: Save menu    F9: Load menu",
      "Digits: Choice selection");

  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final VnRenderer renderer = new VnRenderer(gc);
  private final MenuRenderer menuRenderer = new MenuRenderer(gc, MenuTheme.fromAssets());
  private final PhoneRenderer phoneRenderer = new PhoneRenderer();
  private final Tooltip previewTooltip = new Tooltip(PREVIEW_HINT);
  private static final Pattern TIMELINE_ARC_PATTERN = Pattern.compile(
      "^\\s*arc\\s+(?:\"([^\"]+)\"|(\\S+))\\s+script\\s+(?:\"([^\"]+)\"|(\\S+)).*$");
  private VnScene scene;
  private double mouseX, mouseY;
  private AudioFacade audio;
  private File projectRoot;
  private String audioBackend = "auto";
  private String sourceScriptName;
  private VnUiLayoutSpec uiLayoutOverride;
  private VnUiStyleSpec uiStyleOverride;
  private List<VnUiActionButtonSpec> textBoxButtonsOverride;
  private final VnSaveManager previewSaveManager = new VnSaveManager();
  private Scene overlayScene;

  // Virtual viewport: render at the game's target resolution, scale to fit canvas
  private int virtualWidth = 0;
  private int virtualHeight = 0;

  public VnPreviewView() {
    getChildren().addAll(canvas, phoneRenderer);
    setFocusTraversable(true);
    canvas.setFocusTraversable(true);

    // Input handlers
    canvas.setOnMouseMoved(e -> {
      mouseX = e.getX();
      mouseY = e.getY();
      updateOverlayHover(mouseX, mouseY);
    });
    canvas.setOnMouseClicked(e -> handleMouseClick(e.getButton(), e.getClickCount(), e.getX(), e.getY()));
    canvas.setOnScroll(this::handleScroll);

    setOnKeyPressed(this::handleKeyPressed);

    Tooltip.install(canvas, previewTooltip);

    // Keep focus for key handling when mouse enters
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> requestFocus());
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> requestFocus());
  }

  public void setScenario(VnScenario scenario) {
    initializeScenario(scenario, null);
  }

  public void runScenario(VnScenario scenario, String label) {
    initializeScenario(scenario, label);
  }

  public void setSourceScriptName(String sourceScriptName) {
    this.sourceScriptName = normalizeScriptKey(sourceScriptName);
    if (scene != null) {
      scene.getState().setSourceScriptName(this.sourceScriptName);
    }
  }

  public void setProjectRoot(File root) {
    String nextBackend = readAudioBackendFromManifest(root);
    boolean rootChanged = !Objects.equals(this.projectRoot, root);
    boolean backendChanged = !Objects.equals(this.audioBackend, nextBackend);
    if (rootChanged || backendChanged) {
      stopAudio();
      audio = null;
    }
    this.audioBackend = nextBackend;
    this.projectRoot = root;
    renderer.setProjectRoot(root);
    phoneRenderer.setProjectRoot(root);
    resolveVirtualViewport(root);
    applyUiOverrides();
    bindProjectRoot(audio, root);
    if (scene != null) {
      if (audio == null) {
        audio = createAudioFacade(root, audioBackend);
      }
      scene.setAudioFacade(audio);
    }
  }

  private void resolveVirtualViewport(File root) {
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(root);
    this.virtualWidth = dims.width();
    this.virtualHeight = dims.height();
  }

  public void setSize(double w, double h) {
    double sw = sanitizeCanvasDimension(w);
    double sh = sanitizeCanvasDimension(h);
    if (Math.abs(canvas.getWidth() - sw) >= 0.5) canvas.setWidth(sw);
    if (Math.abs(canvas.getHeight() - sh) >= 0.5) canvas.setHeight(sh);
  }

  public void render(long deltaMs) {
    double canvasW = canvas.getWidth();
    double canvasH = canvas.getHeight();
    if (scene == null) {
      gc.setFill(javafx.scene.paint.Color.color(0.06, 0.06, 0.08));
      gc.fillRect(0, 0, canvasW, canvasH);
      gc.setFill(javafx.scene.paint.Color.WHITE);
      gc.fillText("Open a VNS file to preview", 20, 30);
      return;
    }
    scene.update(deltaMs);
    renderer.updateAnimation(deltaMs);
    renderer.setAudioFacade(scene.getAudioFacade());
    applyUiOverrides();
    syncRequestedOverlayScene();
    if (overlayScene instanceof PhoneScene phone && phone.consumeCloseRequested()) {
      closeOverlayScene();
    }

    double vw = virtualWidth > 0 ? virtualWidth : canvasW;
    double vh = virtualHeight > 0 ? virtualHeight : canvasH;
    double scale = Math.min(canvasW / vw, canvasH / vh);
    double scaledW = vw * scale;
    double scaledH = vh * scale;
    double offsetX = (canvasW - scaledW) / 2.0;
    double offsetY = (canvasH - scaledH) / 2.0;

    // Clear full canvas (letterbox bars)
    gc.setFill(javafx.scene.paint.Color.BLACK);
    gc.fillRect(0, 0, canvasW, canvasH);

    // Transform mouse from canvas space → virtual space
    double virtualMouseX = (mouseX - offsetX) / scale;
    double virtualMouseY = (mouseY - offsetY) / scale;

    gc.save();
    gc.translate(offsetX, offsetY);
    gc.scale(scale, scale);
    renderer.render(scene.getState(), scene.getScenario(), vw, vh, virtualMouseX, virtualMouseY);
    renderOverlayScene(vw, vh);
    gc.restore();
    syncPhoneOverlay();
  }

  public void setUiOverrides(VnUiLayoutSpec layout, VnUiStyleSpec style, List<VnUiActionButtonSpec> textBoxButtons) {
    uiLayoutOverride = layout;
    uiStyleOverride = style;
    // Null means "no override", preserving project/runtime defaults.
    textBoxButtonsOverride = textBoxButtons == null ? null : List.copyOf(textBoxButtons);
    renderer.reloadUiLayout();
    applyUiOverrides();
  }

  public void clearUiOverrides() {
    uiLayoutOverride = null;
    uiStyleOverride = null;
    textBoxButtonsOverride = null;
    renderer.reloadUiLayout();
    applyUiOverrides();
  }

  private void applyUiOverrides() {
    if (uiLayoutOverride != null) renderer.setUiLayout(uiLayoutOverride);
    if (uiStyleOverride != null) renderer.setUiStyle(uiStyleOverride);
    if (textBoxButtonsOverride != null) renderer.setTextBoxButtons(textBoxButtonsOverride);
  }

  private void initializeScenario(VnScenario scenario, String startLabel) {
    if (scenario == null) {
      stopAudio();
      this.scene = null;
      this.overlayScene = null;
      phoneRenderer.setSceneModel(null);
      renderer.setAudioFacade(null);
      return;
    }
    stopAudio();
    VnSettings existingSettings = scene == null ? null : scene.getState().getSettings();
    VnScene nextScene = buildScene(scenario, startLabel, sourceScriptName, existingSettings);
    this.scene = nextScene;
    this.overlayScene = null;
    phoneRenderer.setSceneModel(null);
    renderer.setAudioFacade(audio);
    requestFocus();
  }

  private VnScene buildScene(VnScenario scenario, String startLabel, String scriptName, VnSettings settingsTemplate) {
    VnScene nextScene = new VnScene(scenario);
    PreviewVnInterop interop = new PreviewVnInterop();
    com.jvn.core.vn.VnCharacterSceneAccessor accessor = new com.jvn.core.vn.VnCharacterSceneAccessor();
    interop.setSceneAccessor(accessor);
    renderer.setTimelineAccessor(accessor);
    nextScene.setInterop(interop);
    if (audio == null) audio = createAudioFacade(projectRoot, audioBackend);
    bindProjectRoot(audio, projectRoot);
    nextScene.setAudioFacade(audio);
    if (settingsTemplate != null) {
      copySettings(settingsTemplate, nextScene.getState().getSettings());
    }
    if (scriptName != null && !scriptName.isBlank()) {
      nextScene.getState().setSourceScriptName(scriptName);
    }
    if (startLabel != null && !startLabel.isBlank()) {
      nextScene.getState().jumpToLabel(startLabel);
      nextScene.preflightState(nextScene.getState().getCurrentNodeIndex());
    }
    nextScene.onEnter();
    return nextScene;
  }

  private void renderOverlayScene(double vw, double vh) {
    if (overlayScene instanceof SaveMenuScene save) {
      menuRenderer.renderSaveMenu(save, vw, vh);
    } else if (overlayScene instanceof LoadMenuScene load) {
      menuRenderer.renderLoadMenu(load, vw, vh);
    } else if (overlayScene instanceof HistoryMenuScene history) {
      menuRenderer.renderHistoryMenu(history, vw, vh);
    }
  }

  private void syncPhoneOverlay() {
    if (overlayScene instanceof PhoneScene phone) {
      phoneRenderer.setSceneModel(phone);
    } else {
      phoneRenderer.setSceneModel(null);
    }
  }

  private void syncRequestedOverlayScene() {
    if (scene == null || overlayScene != null) return;
    var state = scene.getState();
    if (state == null) return;
    if (state.isSaveSlotOverlayShown()) {
      boolean saveMode = state.isSaveSlotOverlaySaveMode();
      state.hideSaveSlotOverlay();
      if (saveMode) {
        overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
      } else {
        overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
      }
      return;
    }
    if (state.isHistoryOverlayShown()) {
      state.setHistoryOverlayShown(false);
      overlayScene = new HistoryMenuScene(null, scene);
    }
  }

  private void closeOverlayScene() {
    overlayScene = null;
    phoneRenderer.setSceneModel(null);
  }

  private final class PreviewVnInterop extends DefaultVnInterop {
    @Override
    public VnInteropResult handle(VnExternalCommand command, VnScene activeScene) {
      if (command != null && "phone".equalsIgnoreCase(command.getProvider())) {
        return handlePhoneCommand(command.getPayload(), activeScene);
      }
      if (command != null && "vns".equalsIgnoreCase(command.getProvider())) {
        return handleVnsCommand(command.getPayload(), activeScene);
      }
      return super.handle(command, activeScene);
    }
  }

  private VnInteropResult handlePhoneCommand(String payload, VnScene activeScene) {
    VnPhoneCommands.Result result = VnPhoneCommands.handle(payload, activeScene, this::loadPhoneSeed);
    switch (result.action()) {
      case OPEN_HOME -> overlayScene = new PhoneScene(
          activeScene,
          VnPhoneStateStore.load(activeScene.getState(), this::loadPhoneSeed),
          updated -> VnPhoneStateStore.save(activeScene.getState(), updated));
      case OPEN_CHAT -> overlayScene = new PhoneScene(
          activeScene,
          VnPhoneStateStore.load(activeScene.getState(), this::loadPhoneSeed),
          updated -> VnPhoneStateStore.save(activeScene.getState(), updated),
          result.chatId());
      case CLOSE -> closeOverlayScene();
      case NONE -> {
      }
    }
    syncPhoneOverlay();
    return VnInteropResult.advance();
  }

  private VnInteropResult handleVnsCommand(String payload, VnScene activeScene) {
    List<String> tokens = new ArrayList<>(Arrays.asList(splitPayload(payload)));
    if (tokens.isEmpty()) return VnInteropResult.advance();
    String cmd = tokens.remove(0).toLowerCase(Locale.ROOT);

    if ("goto".equals(cmd)) {
      if (tokens.isEmpty()) return VnInteropResult.advance();
      String target = tokens.remove(0);
      int colon = target.indexOf(':');
      if (colon < 0) {
        activeScene.getState().jumpToLabel(target);
        return VnInteropResult.stay();
      }
      String scriptToken = target.substring(0, colon).trim();
      String label = target.substring(colon + 1).trim();
      String script = resolveVnsScriptTarget(scriptToken);
      if (script == null) return VnInteropResult.advance();
      return switchPreviewScript(script, label, activeScene);
    }

    if ("replace".equals(cmd) || "push".equals(cmd)) {
      if (tokens.isEmpty()) return VnInteropResult.advance();
      String script = resolveVnsScriptTarget(tokens.remove(0));
      String label = null;
      if (!tokens.isEmpty() && "label".equalsIgnoreCase(tokens.get(0))) {
        tokens.remove(0);
        if (!tokens.isEmpty()) label = tokens.remove(0);
      }
      if (script == null) return VnInteropResult.advance();
      return switchPreviewScript(script, label, activeScene);
    }

    activeScene.getState().showHudMessage("Unsupported [vns] command in preview: " + cmd, 1500);
    return VnInteropResult.advance();
  }

  private VnInteropResult switchPreviewScript(String script, String label, VnScene activeScene) {
    try {
      VnScenario loaded = loadScenarioFromScript(script);
      VnSettings settings = activeScene == null ? null : activeScene.getState().getSettings();
      this.sourceScriptName = normalizeScriptKey(script);
      this.scene = buildScene(loaded, label, sourceScriptName, settings);
      renderer.setAudioFacade(audio);
    } catch (Exception ex) {
      if (activeScene != null) {
        activeScene.getState().showHudMessage("Preview could not load script: " + script, 1900);
      }
    }
    return VnInteropResult.advance();
  }

  private VnScenario loadScenarioFromScript(String script) throws IOException {
    VnScenarioLoader loader;
    if (projectRoot != null) {
      loader = new VnScenarioLoader(
          new AssetCatalog(new FilesystemAssetManager(projectRoot.toPath())),
          new com.jvn.core.vn.script.VnScriptParser(),
          "game/scripts/");
    } else {
      loader = new VnScenarioLoader();
    }
    IOException last = null;
    for (String candidate : scriptCandidates(script)) {
      try {
        return loader.load(candidate);
      } catch (IOException ex) {
        last = ex;
      }
    }
    if (last != null) throw last;
    throw new IOException("Script not found: " + script);
  }

  private List<String> scriptCandidates(String script) {
    String normalized = normalizeScriptKey(script);
    if (normalized == null || normalized.isBlank()) return List.of();
    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);
    if (normalized.startsWith("scripts/")) {
      candidates.add(normalized.substring("scripts/".length()));
    } else {
      candidates.add("scripts/" + normalized);
    }
    if (!normalized.contains("/")) {
      candidates.add("story/" + normalized);
    }
    return candidates.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
  }

  private String resolveVnsScriptTarget(String token) {
    String normalized = normalizeScriptKey(token);
    if (normalized == null || normalized.isBlank()) return null;
    if (!normalized.endsWith(".vns")) {
      String fromTimeline = resolveTimelineArcScript(normalized);
      if (fromTimeline != null && !fromTimeline.isBlank()) {
        return normalizeScriptKey(fromTimeline);
      }
      normalized = normalized + ".vns";
    }
    return normalized;
  }

  private String resolveTimelineArcScript(String arcName) {
    if (projectRoot == null || arcName == null || arcName.isBlank()) return null;
    File timeline = new File(projectRoot, "config/timeline/story.timeline");
    if (!timeline.isFile()) return null;
    try {
      for (String line : Files.readAllLines(timeline.toPath())) {
        if (line == null) continue;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        Matcher m = TIMELINE_ARC_PATTERN.matcher(trimmed);
        if (!m.matches()) continue;
        String arc = m.group(1) != null ? m.group(1) : m.group(2);
        String script = m.group(3) != null ? m.group(3) : m.group(4);
        if (arcName.equalsIgnoreCase(safe(arc))) {
          return script;
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private String[] splitPayload(String payload) {
    if (payload == null || payload.isBlank()) return new String[0];
    return payload.trim().split("\\s+");
  }

  private String normalizeScriptKey(String raw) {
    if (raw == null) return null;
    String normalized = raw.trim().replace('\\', '/');
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    return normalized;
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private void updateOverlayHover(double x, double y) {
    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();
    if (overlayScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, vw, vh, vx, vy);
      if (idx >= 0) save.setSelected(idx);
    } else if (overlayScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, vw, vh, vx, vy);
      if (idx >= 0) load.setSelected(idx);
    }
  }

  private void handleMouseClick(MouseButton button, int clickCount, double x, double y) {
    mouseX = x;
    mouseY = y;
    requestFocus();
    if (scene == null || button != MouseButton.PRIMARY) return;

    if (handleOverlayMouseClick(clickCount, x, y)) return;

    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();

    com.jvn.core.vn.ui.VnUiActionButtonSpec textBoxButton =
        renderer.getHoveredTextBoxButton(scene.getState(), vw, vh, vx, vy);
    if (textBoxButton != null && executeTextBoxButtonAction(textBoxButton)) return;

    VnNode node = scene.getState().getCurrentNode();
    if (node != null && node.getType() == VnNodeType.CHOICE) {
      int idx = renderer.getHoveredChoiceIndex(node.getChoices(), vw, vh, vx, vy);
      if (idx >= 0) {
        scene.selectChoice(idx);
      }
      return;
    }

    scene.advanceFromClick();
  }

  private boolean executeTextBoxButtonAction(com.jvn.core.vn.ui.VnUiActionButtonSpec button) {
    if (scene == null || button == null || !button.enabled()) return false;
    String action = normalizeButtonAction(button.action());
    var state = scene.getState();
    switch (action) {
      case "advance" -> {
        scene.advanceFromClick();
        return true;
      }
      case "quick_save", "save_quick" -> {
        saveToSlot(0);
        return true;
      }
      case "quick_load", "load_quick" -> {
        loadFromSlot(0);
        return true;
      }
      case "save_slots", "open_save_slots", "save_menu", "open_save_menu", "menu_save" -> {
        overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
        return true;
      }
      case "load_slots", "open_load_slots", "load_menu", "open_load_menu", "menu_load" -> {
        overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
        return true;
      }
      case "toggle_history", "history" -> {
        if (overlayScene instanceof HistoryMenuScene) {
          closeOverlayScene();
        } else {
          state.clearHistoryScroll();
          overlayScene = new HistoryMenuScene(null, scene);
        }
        return true;
      }
      case "toggle_skip", "skip" -> {
        scene.toggleSkipMode();
        return true;
      }
      case "toggle_auto", "auto" -> {
        scene.toggleAutoPlayMode();
        return true;
      }
      case "toggle_ui", "ui" -> {
        state.toggleUiHidden();
        return true;
      }
      case "noop", "none" -> {
        return true;
      }
      default -> {
        state.showHudMessage("Unsupported preview action: " + action, 1400);
        return true;
      }
    }
  }

  private static String normalizeButtonAction(String raw) {
    if (raw == null || raw.isBlank()) return "noop";
    return raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
  }

  private boolean handleOverlayMouseClick(int clickCount, double x, double y) {
    double vx = toVirtualX(x);
    double vy = toVirtualY(y);
    double vw = viewportW();
    double vh = viewportH();

    if (overlayScene instanceof SaveMenuScene save) {
      int idx = menuRenderer.getHoverIndexForSaveMenu(save, vw, vh, vx, vy);
      if (idx < 0) {
        closeOverlayScene();
        return true;
      }
      int previous = save.getSelected();
      save.setSelected(idx);
      if (clickCount >= 2 || previous == idx) {
        confirmOverlayAction();
      }
      return true;
    }

    if (overlayScene instanceof LoadMenuScene load) {
      int idx = menuRenderer.getHoverIndexForLoadMenu(load, vw, vh, vx, vy);
      if (idx < 0) {
        closeOverlayScene();
        return true;
      }
      int previous = load.getSelected();
      load.setSelected(idx);
      if (clickCount >= 2 || previous == idx) {
        confirmOverlayAction();
      }
      return true;
    }

    if (overlayScene instanceof HistoryMenuScene) {
      closeOverlayScene();
      return true;
    }

    return false;
  }

  private void handleKeyPressed(KeyEvent e) {
    if (scene == null) return;
    KeyCode code = e.getCode();
    var state = scene.getState();

    if (overlayScene != null) {
      if (overlayScene instanceof PhoneScene phone) {
        if (phoneRenderer.handleKeyPressed(code, e.isShiftDown())) {
          if (phone.consumeCloseRequested()) {
            closeOverlayScene();
          } else {
            syncPhoneOverlay();
          }
          e.consume();
          return;
        }
      }
      handleOverlayKey(code, e.isShiftDown());
      e.consume();
      return;
    }

    if (trySelectChoiceByDigit(code)) {
      e.consume();
      return;
    }

    if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
      advanceFromInput();
      e.consume();
    } else if (code == KeyCode.CONTROL || code == KeyCode.COMMAND) {
      scene.toggleSkipMode();
      e.consume();
    } else if (code == KeyCode.A) {
      scene.toggleAutoPlayMode();
      e.consume();
    } else if (code == KeyCode.H) {
      state.toggleUiHidden();
      e.consume();
    } else if (code == KeyCode.B) {
      state.clearHistoryScroll();
      overlayScene = new HistoryMenuScene(null, scene);
      e.consume();
    } else if (code == KeyCode.F5) {
      overlayScene = new SaveMenuScene(null, previewSaveManager, scene, sourceScriptName);
      e.consume();
    } else if (code == KeyCode.F9) {
      overlayScene = new LoadMenuScene(null, previewSaveManager, normalizeScriptKey(sourceScriptName), state.getSettings(), audio);
      e.consume();
    } else if (code == KeyCode.ESCAPE) {
      closeOverlayScene();
      e.consume();
    }
  }

  private void handleOverlayKey(KeyCode code, boolean shiftDown) {
    if (overlayScene instanceof HistoryMenuScene history) {
      int pageLines = history.linesPerPage(viewportH());
      int step = shiftDown ? 5 : 1;
      if (code == KeyCode.ESCAPE || code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.B) {
        closeOverlayScene();
      } else if (code == KeyCode.UP) {
        history.scrollByLines(step);
      } else if (code == KeyCode.DOWN) {
        history.scrollByLines(-step);
      } else if (code == KeyCode.PAGE_UP) {
        history.scrollByLines(pageLines);
      } else if (code == KeyCode.PAGE_DOWN) {
        history.scrollByLines(-pageLines);
      }
      return;
    }

    if (overlayScene instanceof SaveMenuScene save) {
      if (code == KeyCode.ESCAPE) {
        closeOverlayScene();
        return;
      }
      if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
        confirmOverlayAction();
        return;
      }
      if (code == KeyCode.UP) {
        save.moveSelection(-1);
        return;
      }
      if (code == KeyCode.DOWN) {
        save.moveSelection(1);
        return;
      }
      return;
    }

    if (overlayScene instanceof LoadMenuScene load) {
      if (code == KeyCode.ESCAPE) {
        closeOverlayScene();
        return;
      }
      if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
        confirmOverlayAction();
        return;
      }
      if (code == KeyCode.UP) {
        load.moveSelection(-1);
        return;
      }
      if (code == KeyCode.DOWN) {
        load.moveSelection(1);
      }
    }
  }

  private void handleScroll(ScrollEvent e) {
    if (scene == null) return;
    if (overlayScene instanceof PhoneScene phone) {
      phoneRenderer.scrollContent(e.getDeltaY(), e.isShiftDown());
      if (phone.consumeCloseRequested()) {
        closeOverlayScene();
      } else {
        syncPhoneOverlay();
      }
      e.consume();
      return;
    }
    if (overlayScene instanceof HistoryMenuScene history) {
      int step = e.isShiftDown() ? 6 : 2;
      if (e.getDeltaY() > 0) {
        history.scrollByLines(step);
      } else if (e.getDeltaY() < 0) {
        history.scrollByLines(-step);
      }
      e.consume();
    }
  }

  private void advanceFromInput() {
    VnNode node = scene.getState().getCurrentNode();
    if (node != null && node.getType() == VnNodeType.CHOICE) {
      int hover = renderer.getHoveredChoiceIndex(node.getChoices(), viewportW(), viewportH(), toVirtualX(mouseX), toVirtualY(mouseY));
      if (hover >= 0) {
        scene.selectChoice(hover);
      }
      return;
    }
    scene.advance();
  }

  private boolean trySelectChoiceByDigit(KeyCode code) {
    VnNode node = scene.getState().getCurrentNode();
    if (node == null || node.getType() != VnNodeType.CHOICE) return false;
    int digit = toDigit(code);
    if (digit <= 0) return false; // choices are 1-based
    int idx = digit - 1;
    if (idx >= 0 && idx < node.getChoices().size()) {
      scene.selectChoice(idx);
      return true;
    }
    return false;
  }

  private void confirmOverlayAction() {
    if (overlayScene instanceof SaveMenuScene save) {
      if (!save.activateSelectedWithoutPrompt()) {
        String slotName = save.isNewItemSelected()
            ? save.saveNew(save.generateSaveName())
            : save.saveOverwriteSelected();
        if (slotName != null) {
          closeOverlayScene();
        }
      }
      return;
    }
    if (overlayScene instanceof LoadMenuScene load) {
      String saveName = load.getSelectedName();
      if (saveName != null) {
        loadFromSaveName(saveName);
        closeOverlayScene();
      }
    }
  }

  private void saveToSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      previewSaveManager.save(scene.getState(), slotName);
      scene.getState().showHudMessage("Saved to " + slotLabel(slot), 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage("Save failed", 1500);
    }
  }

  private void loadFromSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    loadFromSaveName(slotName, slot == 0 ? "No quick save found" : ("Slot " + slot + " is empty"), "Loaded from " + slotLabel(slot));
  }

  private void loadFromSaveName(String saveName) {
    loadFromSaveName(saveName, "Save is empty", "Loaded from " + saveName);
  }

  private void loadFromSaveName(String saveName, String missingMessage, String successMessage) {
    try {
      var saveData = previewSaveManager.load(saveName);
      String expectedScenarioId = scene.getScenario() != null ? scene.getScenario().getId() : null;
      if (!Objects.equals(saveData.getScenarioId(), expectedScenarioId)) {
        scene.getState().showHudMessage("Save is for different scenario", 1800);
        return;
      }
      previewSaveManager.applyToState(saveData, scene.getState());
      if (audio != null) {
        var s = scene.getState().getSettings();
        audio.setBgmVolume(s.getBgmVolume());
        audio.setSfxVolume(s.getSfxVolume());
        audio.setVoiceVolume(s.getVoiceVolume());
      }
      scene.getState().showHudMessage(successMessage, 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage(missingMessage, 1800);
    }
  }

  private String slotLabel(int slot) {
    return slot == 0 ? "Quick Save" : ("Slot " + slot);
  }

  private int toDigit(KeyCode code) {
    return switch (code) {
      case DIGIT0, NUMPAD0 -> 0;
      case DIGIT1, NUMPAD1 -> 1;
      case DIGIT2, NUMPAD2 -> 2;
      case DIGIT3, NUMPAD3 -> 3;
      case DIGIT4, NUMPAD4 -> 4;
      case DIGIT5, NUMPAD5 -> 5;
      case DIGIT6, NUMPAD6 -> 6;
      case DIGIT7, NUMPAD7 -> 7;
      case DIGIT8, NUMPAD8 -> 8;
      case DIGIT9, NUMPAD9 -> 9;
      default -> -1;
    };
  }

  // ── Virtual viewport helpers ──────────────────────────────────────

  private double viewportW() { return virtualWidth > 0 ? virtualWidth : canvas.getWidth(); }
  private double viewportH() { return virtualHeight > 0 ? virtualHeight : canvas.getHeight(); }

  private double viewportScale() {
    return Math.min(canvas.getWidth() / viewportW(), canvas.getHeight() / viewportH());
  }

  private double toVirtualX(double canvasX) {
    double scale = viewportScale();
    double offsetX = (canvas.getWidth() - viewportW() * scale) / 2.0;
    return (canvasX - offsetX) / scale;
  }

  private double toVirtualY(double canvasY) {
    double scale = viewportScale();
    double offsetY = (canvas.getHeight() - viewportH() * scale) / 2.0;
    return (canvasY - offsetY) / scale;
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
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

  private VnPhoneData loadPhoneSeed() {
    try {
      if (projectRoot != null) {
        File direct = new File(projectRoot, "config/phone/phone.properties");
        if (direct.isFile()) {
          try (InputStream in = new FileInputStream(direct)) {
            return VnPhonePropertiesCodec.load(in);
          }
        }
        File gamePath = new File(projectRoot, "game/config/phone/phone.properties");
        if (gamePath.isFile()) {
          try (InputStream in = new FileInputStream(gamePath)) {
            return VnPhonePropertiesCodec.load(in);
          }
        }
      }
    } catch (Exception ignored) {
    }
    return VnPhonePropertiesCodec.loadSeedFromAssets();
  }

  public void stopAudio() {
    if (audio != null) {
      try {
        audio.stopAllAudio();
      } catch (Exception ignored) {
      }
    }
  }

  public void dispose() {
    stopAudio();
    scene = null;
    overlayScene = null;
    phoneRenderer.setSceneModel(null);
    projectRoot = null;
    audio = null;
    renderer.setAudioFacade(null);
    renderer.setProjectRoot(null);
  }

  static String normalizeAudioBackendValue(String raw) {
    if (raw == null || raw.isBlank()) return "auto";
    String key = raw.trim().toLowerCase(Locale.ROOT);
    if ("fx".equals(key) || "javafx".equals(key)) return "fx";
    if ("simp3".equals(key) || "simp".equals(key)) return "simp3";
    return "auto";
  }

  static String resolveAudioBackend(Properties manifest) {
    if (manifest == null) return "auto";
    return normalizeAudioBackendValue(manifest.getProperty("runtime.audio", "auto"));
  }

  private static String readAudioBackendFromManifest(File root) {
    if (root == null || !root.isDirectory()) return "auto";
    File manifest = new File(root, "jvn.project");
    if (!manifest.isFile()) return "auto";
    Properties props = new Properties();
    try (InputStream in = new FileInputStream(manifest)) {
      props.load(in);
      return resolveAudioBackend(props);
    } catch (Exception ignored) {
      return "auto";
    }
  }

  private AudioFacade createAudioFacade(File root, String backend) {
    String selected = normalizeAudioBackendValue(backend);
    AudioFacade facade;
    if ("fx".equals(selected)) {
      FxAudioService fx = new FxAudioService();
      fx.setProjectRoot(root);
      return fx;
    }
    try {
      Simp3AudioService simp3 = new Simp3AudioService();
      simp3.setProjectRoot(root);
      facade = simp3;
    } catch (Throwable t) {
      FxAudioService fx = new FxAudioService();
      fx.setProjectRoot(root);
      facade = fx;
    }
    return facade;
  }

  private void bindProjectRoot(AudioFacade facade, File root) {
    if (facade instanceof FxAudioService fx) {
      fx.setProjectRoot(root);
    } else if (facade instanceof Simp3AudioService simp3) {
      simp3.setProjectRoot(root);
    }
  }
}

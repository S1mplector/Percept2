package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import com.jvn.audio.simp3.Simp3AudioService;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.fx.audio.FxAudioService;
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
      "F5: Save slots    F9: Load slots",
      "Digits: Choice/save-slot selection");

  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final VnRenderer renderer = new VnRenderer(gc);
  private final Tooltip previewTooltip = new Tooltip(PREVIEW_HINT);
  private VnScene scene;
  private double mouseX, mouseY;
  private AudioFacade audio;
  private File projectRoot;
  private String audioBackend = "auto";
  private VnUiLayoutSpec uiLayoutOverride;
  private VnUiStyleSpec uiStyleOverride;
  private List<VnUiActionButtonSpec> textBoxButtonsOverride = List.of();

  // Virtual viewport: render at the game's target resolution, scale to fit canvas
  private int virtualWidth = 0;
  private int virtualHeight = 0;

  public VnPreviewView() {
    getChildren().add(canvas);
    setFocusTraversable(true);
    canvas.setFocusTraversable(true);

    // Input handlers
    canvas.setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
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
    gc.restore();
  }

  public void setUiOverrides(VnUiLayoutSpec layout, VnUiStyleSpec style, List<VnUiActionButtonSpec> textBoxButtons) {
    uiLayoutOverride = layout;
    uiStyleOverride = style;
    textBoxButtonsOverride = textBoxButtons == null ? List.of() : List.copyOf(textBoxButtons);
    applyUiOverrides();
  }

  public void clearUiOverrides() {
    uiLayoutOverride = null;
    uiStyleOverride = null;
    textBoxButtonsOverride = List.of();
  }

  private void applyUiOverrides() {
    if (uiLayoutOverride != null) renderer.setUiLayout(uiLayoutOverride);
    if (uiStyleOverride != null) renderer.setUiStyle(uiStyleOverride);
    renderer.setTextBoxButtons(textBoxButtonsOverride == null ? List.of() : textBoxButtonsOverride);
  }

  private void initializeScenario(VnScenario scenario, String startLabel) {
    if (scenario == null) {
      stopAudio();
      this.scene = null;
      renderer.setAudioFacade(null);
      return;
    }
    stopAudio();
    VnScene nextScene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();
    com.jvn.core.vn.VnCharacterSceneAccessor accessor = new com.jvn.core.vn.VnCharacterSceneAccessor();
    interop.setSceneAccessor(accessor);
    renderer.setTimelineAccessor(accessor);
    nextScene.setInterop(interop);
    if (audio == null) audio = createAudioFacade(projectRoot, audioBackend);
    bindProjectRoot(audio, projectRoot);
    nextScene.setAudioFacade(audio);
    if (startLabel != null && !startLabel.isBlank()) {
      nextScene.getState().jumpToLabel(startLabel);
      nextScene.preflightState(nextScene.getState().getCurrentNodeIndex());
    }
    nextScene.onEnter();
    this.scene = nextScene;
    renderer.setAudioFacade(audio);
    requestFocus();
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
      case "save_slots", "open_save_slots" -> {
        state.showSaveSlotOverlay(true);
        return true;
      }
      case "load_slots", "open_load_slots" -> {
        state.showSaveSlotOverlay(false);
        return true;
      }
      case "toggle_history", "history" -> {
        boolean wasShown = state.isHistoryOverlayShown();
        state.toggleHistoryOverlay();
        if (!wasShown) state.clearHistoryScroll();
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
    var state = scene.getState();

    if (state.isSaveSlotOverlayShown()) {
      int slot = renderer.getHoveredSaveSlotIndex(viewportW(), viewportH(), toVirtualX(x), toVirtualY(y));
      if (slot < 0) {
        state.hideSaveSlotOverlay();
        return true;
      }
      int previous = state.getSaveSlotSelected();
      state.setSaveSlotSelected(slot);
      if (clickCount >= 2 || previous == slot) {
        confirmSaveSlotAction();
      }
      return true;
    }

    if (state.isHistoryOverlayShown()) {
      state.setHistoryOverlayShown(false);
      return true;
    }

    return false;
  }

  private void handleKeyPressed(KeyEvent e) {
    if (scene == null) return;
    KeyCode code = e.getCode();
    var state = scene.getState();

    if (state.isSaveSlotOverlayShown()) {
      handleSaveSlotOverlayKey(code);
      e.consume();
      return;
    }

    if (state.isHistoryOverlayShown()) {
      handleHistoryOverlayKey(code, e.isShiftDown());
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
      boolean wasShown = state.isHistoryOverlayShown();
      state.toggleHistoryOverlay();
      if (!wasShown) state.clearHistoryScroll();
      e.consume();
    } else if (code == KeyCode.F5) {
      state.showSaveSlotOverlay(true);
      e.consume();
    } else if (code == KeyCode.F9) {
      state.showSaveSlotOverlay(false);
      e.consume();
    } else if (code == KeyCode.ESCAPE) {
      state.setHistoryOverlayShown(false);
      state.hideSaveSlotOverlay();
      e.consume();
    }
  }

  private void handleHistoryOverlayKey(KeyCode code, boolean shiftDown) {
    var state = scene.getState();
    int pageLines = renderer.getHistoryLinesPerPage(viewportH());
    int step = shiftDown ? 5 : 1;

    if (code == KeyCode.ESCAPE || code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.B) {
      state.setHistoryOverlayShown(false);
    } else if (code == KeyCode.UP) {
      state.scrollHistoryByLines(step);
    } else if (code == KeyCode.DOWN) {
      state.scrollHistoryByLines(-step);
    } else if (code == KeyCode.PAGE_UP) {
      state.scrollHistoryByLines(pageLines);
    } else if (code == KeyCode.PAGE_DOWN) {
      state.scrollHistoryByLines(-pageLines);
    }
  }

  private void handleSaveSlotOverlayKey(KeyCode code) {
    var state = scene.getState();
    if (code == KeyCode.ESCAPE) {
      state.hideSaveSlotOverlay();
      return;
    }
    if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
      confirmSaveSlotAction();
      return;
    }
    if (code == KeyCode.UP) {
      state.moveSaveSlotSelection(-2);
      return;
    }
    if (code == KeyCode.DOWN) {
      state.moveSaveSlotSelection(2);
      return;
    }
    if (code == KeyCode.LEFT) {
      state.moveSaveSlotSelection(-1);
      return;
    }
    if (code == KeyCode.RIGHT) {
      state.moveSaveSlotSelection(1);
      return;
    }

    int digit = toDigit(code);
    if (digit >= 0) {
      state.setSaveSlotSelected(digit);
    }
  }

  private void handleScroll(ScrollEvent e) {
    if (scene == null) return;
    var state = scene.getState();
    if (!state.isHistoryOverlayShown()) return;

    int step = e.isShiftDown() ? 6 : 2;
    if (e.getDeltaY() > 0) {
      state.scrollHistoryByLines(step);
    } else if (e.getDeltaY() < 0) {
      state.scrollHistoryByLines(-step);
    }
    e.consume();
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

  private void confirmSaveSlotAction() {
    var state = scene.getState();
    int slot = state.getSaveSlotSelected();
    boolean isSave = state.isSaveSlotOverlaySaveMode();
    state.hideSaveSlotOverlay();
    if (isSave) {
      saveToSlot(slot);
    } else {
      loadFromSlot(slot);
    }
  }

  private void saveToSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      new VnSaveManager().save(scene.getState(), slotName);
      scene.getState().showHudMessage("Saved to " + slotLabel(slot), 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage("Save failed", 1500);
    }
  }

  private void loadFromSlot(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    try {
      VnSaveManager saveManager = new VnSaveManager();
      var saveData = saveManager.load(slotName);
      String expectedScenarioId = scene.getScenario() != null ? scene.getScenario().getId() : null;
      if (!Objects.equals(saveData.getScenarioId(), expectedScenarioId)) {
        scene.getState().showHudMessage("Save is for different scenario", 1800);
        return;
      }
      saveManager.applyToState(saveData, scene.getState());
      if (audio != null) {
        var s = scene.getState().getSettings();
        audio.setBgmVolume(s.getBgmVolume());
        audio.setSfxVolume(s.getSfxVolume());
        audio.setVoiceVolume(s.getVoiceVolume());
      }
      scene.getState().showHudMessage("Loaded from " + slotLabel(slot), 1500);
    } catch (Exception e) {
      scene.getState().showHudMessage(slot == 0 ? "No quick save found" : ("Slot " + slot + " is empty"), 1800);
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

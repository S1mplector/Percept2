package com.jvn.editor.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class FileEditorTab extends BorderPane {
  public enum Kind { JES, VNS, JAVA, TIMELINE, THEME, MENU_SCREEN, MENU_LAYOUT, MENU_STYLE, DIALOGUE_LAYOUT, OTHER }
  private enum PreviewDockPosition { TOP, BOTTOM, LEFT, RIGHT, WINDOW }
  private enum PreviewLayoutMode { PREVIEW, CODE, SPLIT }

  private final File file;
  private final Kind kind;

  private final JesCodeEditor jesEditor;
  private final VnsCodeEditor vnsEditor;
  private final JavaCodeEditor javaEditor;
  private final JavaCodeEditor textEditor;
  private final TimelineCodeEditor timelineEditor;
  private final JavaCodeEditor themeEditor;
  private final JavaCodeEditor menuScreenEditor;
  private final JavaCodeEditor menuLayoutEditor;
  private final JavaCodeEditor menuStyleEditor;
  private final JavaCodeEditor dialogueLayoutEditor;
  private final StoryTimelineView timelineView;
  private final MenuScreenVisualEditor menuScreenVisualEditor;
  private final MenuLayoutVisualEditor menuLayoutVisualEditor;
  private final MenuStyleVisualEditor menuStyleVisualEditor;
  private final DialogueLayoutEditorView dialogueLayoutVisualEditor;

  private final ViewportView viewport; // JES preview
  private JesScene2D jesScene;

  private final VnPreviewView vnPreview; // VNS preview
  private final MenuThemePreviewView themePreview; // THEME preview

  private Consumer<Entity2D> onSelected;
  private Consumer<String> onStatus;
  private com.jvn.editor.commands.CommandStack commands;
  private File projectRoot;
  private String savedSnapshot = "";
  private double lastSizedWidth = -1;
  private double lastSizedHeight = -1;
  private SplitPane primarySplit;
  private int primarySplitCodeIndex = 1;
  private boolean editorFullscreen;
  private double restoreDividerPosition = 0.6;
  private BorderPane previewWorkspaceContent;
  private Node dockPreviewNode;
  private Node dockEditorNode;
  private ToggleButton previewModePreviewButton;
  private ToggleButton previewModeCodeButton;
  private ToggleButton previewModeSplitButton;
  private MenuButton previewDockMenu;
  private PreviewDockPosition previewDockPosition = PreviewDockPosition.TOP;
  private PreviewDockPosition lastEmbeddedPreviewDock = PreviewDockPosition.TOP;
  private Stage detachedPreviewStage;
  private AnimationTimer detachedPreviewTimer;
  private long detachedPreviewLastNs = -1L;
  private double verticalDockDivider = 0.6;
  private double horizontalDockDivider = 0.5;
  private PreviewLayoutMode previewModeBeforeDetach = PreviewLayoutMode.SPLIT;
  private boolean disposed;

  public FileEditorTab(File file) {
    this.file = file;
    String name = file != null ? file.getName().toLowerCase(Locale.ROOT) : "";
    String path = file != null ? file.getPath().replace('\\', '/').toLowerCase(Locale.ROOT) : "";
    if (name.endsWith(".jes") || name.endsWith(".txt")) this.kind = Kind.JES;
    else if (name.endsWith(".vns")) this.kind = Kind.VNS;
    else if (name.endsWith(".java")) this.kind = Kind.JAVA;
    else if (name.endsWith(".timeline")) this.kind = Kind.TIMELINE;
    else if (name.endsWith(".theme") || "menu.theme".equals(name)) this.kind = Kind.THEME;
    else if (name.endsWith(".menu")) this.kind = Kind.MENU_SCREEN;
    else if (name.endsWith(".layout") && (path.contains("/config/menu/layouts/") || path.contains("/menu/layouts/") || path.contains("/config/menu/"))) this.kind = Kind.MENU_LAYOUT;
    else if (name.endsWith(".style") || path.contains("/config/menu/styles/")) this.kind = Kind.MENU_STYLE;
    else if ("dialogue.layout".equals(name) || (name.endsWith(".layout") && (path.contains("/config/ui/") || path.contains("/config/vn/")))) this.kind = Kind.DIALOGUE_LAYOUT;
    else this.kind = Kind.OTHER;

    this.jesEditor = (kind == Kind.JES) ? new JesCodeEditor() : null;
    this.vnsEditor = (kind == Kind.VNS) ? new VnsCodeEditor() : null;
    this.javaEditor = (kind == Kind.JAVA) ? new JavaCodeEditor() : null;
    this.textEditor = (kind == Kind.OTHER) ? new JavaCodeEditor() : null;
    this.timelineEditor = (kind == Kind.TIMELINE) ? new TimelineCodeEditor() : null;
    this.themeEditor = (kind == Kind.THEME) ? newDslEditor() : null;
    this.menuScreenEditor = (kind == Kind.MENU_SCREEN) ? newDslEditor() : null;
    this.menuLayoutEditor = (kind == Kind.MENU_LAYOUT) ? newDslEditor() : null;
    this.menuStyleEditor = (kind == Kind.MENU_STYLE) ? newDslEditor() : null;
    this.dialogueLayoutEditor = (kind == Kind.DIALOGUE_LAYOUT) ? newDslEditor() : null;
    this.timelineView = (kind == Kind.TIMELINE) ? new StoryTimelineView() : null;
    this.menuScreenVisualEditor = (kind == Kind.MENU_SCREEN) ? new MenuScreenVisualEditor() : null;
    this.menuLayoutVisualEditor = (kind == Kind.MENU_LAYOUT) ? new MenuLayoutVisualEditor() : null;
    this.menuStyleVisualEditor = (kind == Kind.MENU_STYLE) ? new MenuStyleVisualEditor() : null;
    this.dialogueLayoutVisualEditor = (kind == Kind.DIALOGUE_LAYOUT) ? new DialogueLayoutEditorView() : null;

    this.viewport = (kind == Kind.JES) ? new ViewportView() : null;
    this.vnPreview = (kind == Kind.VNS) ? new VnPreviewView() : null;
    this.themePreview = (kind == Kind.THEME) ? new MenuThemePreviewView() : null;

    if (viewport != null) {
      viewport.setOnSelected(e -> { if (onSelected != null) onSelected.accept(e); });
      viewport.setOnStatus(s -> { if (onStatus != null) onStatus.accept(s); });
    }
    
    if (vnsEditor != null) {
      vnsEditor.setOnLaunchFromHere(this::runFromLabel);
    }

    setupLayout();

    if (file != null && file.exists()) reloadFromDisk();
    else savedSnapshot = getCurrentText();

    // Key forwarding for JES viewport camera controls
    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (viewport != null) viewport.getInput().keyDown(mapKey(e.getCode()));
    });
    addEventFilter(KeyEvent.KEY_RELEASED, e -> {
      if (viewport != null) viewport.getInput().keyUp(mapKey(e.getCode()));
    });

    // Timeline sync between code and graph
    if (kind == Kind.TIMELINE && timelineEditor != null && timelineView != null) {
      if (file != null) timelineView.setTimelineFile(file);
      timelineEditor.setOnTextChanged(text -> timelineView.fromText(text));
      timelineView.setOnChanged(() -> timelineEditor.setTextNoEvent(timelineView.toDsl()));
    } else if (kind == Kind.THEME) {
      String text = themeEditor.getText();
      if (text == null) text = "";
      themePreview.setThemeFromText(text);
    } else if (kind == Kind.MENU_SCREEN) {
      bindMenuScreenVisualSync();
    } else if (kind == Kind.MENU_LAYOUT) {
      bindMenuLayoutVisualSync();
    } else if (kind == Kind.MENU_STYLE) {
      bindMenuStyleVisualSync();
    } else if (kind == Kind.DIALOGUE_LAYOUT) {
      bindDialogueLayoutVisualSync();
    }
  }

  public void showSearchBar() {
    if (vnsEditor != null) vnsEditor.showSearchBar();
    else if (javaEditor != null) javaEditor.showSearchBar();
    else if (themeEditor != null) themeEditor.showSearchBar();
    else if (menuScreenEditor != null) menuScreenEditor.showSearchBar();
    else if (menuLayoutEditor != null) menuLayoutEditor.showSearchBar();
    else if (menuStyleEditor != null) menuStyleEditor.showSearchBar();
    else if (dialogueLayoutEditor != null) dialogueLayoutEditor.showSearchBar();
    else if (textEditor != null) textEditor.showSearchBar();
  }

  public void launchFromHere() {
    if (kind != Kind.VNS || vnsEditor == null) return;
    vnsEditor.setOnLaunchFromHere(this::runFromLabel);
    // Trigger the VNS editor's built-in launch-from-here logic
    javafx.scene.input.KeyEvent fakeF5 = new javafx.scene.input.KeyEvent(
        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.F5,
        false, false, false, false);
    vnsEditor.fireEvent(fakeF5);
  }

  public void runFromLabel(String label) {
    try {
      if (kind != Kind.VNS || vnsEditor == null || vnPreview == null) return;
      String code = vnsEditor.getText();
      if (code == null || code.isBlank()) return;
      VnScriptParser parser = new VnScriptParser();
      VnScenario scenario = parser.parseFromString(code);
      vnPreview.runScenario(scenario, label);
      if (onStatus != null) onStatus.accept("Run from label: " + (label == null ? "<start>" : label));
    } catch (Exception ignored) {}
  }

  private void setupLayout() {
    if (kind == Kind.JES) {
      setCenter(createPreviewWorkspace("JES Preview", viewport, jesEditor, 0.6));
    } else if (kind == Kind.VNS) {
      setCenter(createPreviewWorkspace("VNS Preview", vnPreview, vnsEditor, 0.6));
    } else if (kind == Kind.JAVA) {
      setCenter(javaEditor);
    } else if (kind == Kind.TIMELINE) {
      setCenter(createPreviewWorkspace("Timeline Preview", timelineView, timelineEditor, 0.6));
    } else if (kind == Kind.THEME) {
      setCenter(createPreviewWorkspace("Theme Preview", themePreview, themeEditor, 0.6));
      if (themeEditor != null && themePreview != null) {
        String text = themeEditor.getText(); if (text == null) text = "";
        themePreview.setThemeFromText(text);
        themeEditor.setOnTextChanged(t -> themePreview.setThemeFromText(t));
      }
    } else if (kind == Kind.MENU_SCREEN) {
      setCenter(createStudioWorkspace("Menu Screen Studio", menuScreenVisualEditor, menuScreenEditor, 0.62));
    } else if (kind == Kind.MENU_LAYOUT) {
      setCenter(createStudioWorkspace("Menu Layout Studio", menuLayoutVisualEditor, menuLayoutEditor, 0.58));
    } else if (kind == Kind.MENU_STYLE) {
      setCenter(createStudioWorkspace("Menu Style Studio", menuStyleVisualEditor, menuStyleEditor, 0.58));
    } else if (kind == Kind.DIALOGUE_LAYOUT) {
      setCenter(createStudioWorkspace("Dialogue Layout Studio", dialogueLayoutVisualEditor, dialogueLayoutEditor, 0.58));
    } else if (kind == Kind.OTHER) {
      setCenter(textEditor);
    } else {
      setCenter(new javafx.scene.control.Label("Unsupported file type"));
    }
  }

  public Kind getKind() { return kind; }
  public File getFile() { return file; }

  public void setOnSelected(Consumer<Entity2D> c) { this.onSelected = c; }
  public void setOnStatus(Consumer<String> c) { this.onStatus = c; }
  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (jesEditor != null) jesEditor.setProjectRoot(root);
    if (vnsEditor != null) vnsEditor.setProjectRoot(root);
    if (timelineEditor != null) timelineEditor.setProjectRoot(root);
    if (timelineView != null) timelineView.setProjectRoot(root);
    if (vnPreview != null) vnPreview.setProjectRoot(root);
    if (menuScreenVisualEditor != null) menuScreenVisualEditor.setProjectRoot(root);
    if (menuStyleVisualEditor != null) menuStyleVisualEditor.setProjectRoot(root);
    if (dialogueLayoutVisualEditor != null) dialogueLayoutVisualEditor.setProjectRoot(root);
  }

  public void setCommandStack(com.jvn.editor.commands.CommandStack cs) {
    this.commands = cs;
    if (viewport != null) viewport.setCommandStack(cs);
  }

  public void render(long dt) {
    if (isDetachedPreviewVisible()) return;
    if (kind == Kind.JES && viewport != null) {
      viewport.render(dt);
    } else if (kind == Kind.VNS && vnPreview != null) {
      vnPreview.render(dt);
    } else if (kind == Kind.THEME && themePreview != null) {
      themePreview.render(dt);
    }
  }

  public void setSize(double w, double h) {
    double safeW = sanitizeDimension(w);
    double safeH = sanitizeDimension(h);
    if (Math.abs(lastSizedWidth - safeW) < 0.5 && Math.abs(lastSizedHeight - safeH) < 0.5) return;
    lastSizedWidth = safeW;
    lastSizedHeight = safeH;
    applyPreviewSizing(safeW, safeH);
  }

  public void apply() throws Exception {
    if (kind == Kind.JES) {
      String code = jesEditor.getText();
      if (code == null || code.isBlank()) return;
      try {
        jesScene = JesLoader.load(code);
      } catch (com.jvn.scripting.jes.JesParseException ex) {
        if (onStatus != null) onStatus.accept("JES error: " + ex.getMessage());
        return;
      }
      if (jesScene != null && viewport != null) {
        jesScene.setInput(viewport.getInput());
        jesScene.setCamera(viewport.getCamera());
        viewport.setScene(jesScene);
      }
    } else if (kind == Kind.VNS) {
      String code = vnsEditor.getText();
      if (code == null || code.isBlank()) return;
      try {
        VnScriptParser parser = new VnScriptParser();
        VnScenario scenario = parser.parseFromString(code);
        if (vnPreview != null) vnPreview.setScenario(scenario);
      } catch (Exception ex) {
        if (onStatus != null) onStatus.accept("VNS error: " + ex.getMessage());
      }
    }
  }

  public void reloadFromDisk() {
    if (file == null) return;
    try {
      if (kind == Kind.JES) {
        String code = Files.readString(file.toPath());
        jesEditor.setText(code);
        try (FileInputStream in = new FileInputStream(file)) {
          jesScene = JesLoader.load(in);
        }
        if (jesScene != null && viewport != null) {
          jesScene.setInput(viewport.getInput());
          jesScene.setCamera(viewport.getCamera());
          viewport.setScene(jesScene);
        }
      } else if (kind == Kind.VNS) {
        String code = Files.readString(file.toPath());
        vnsEditor.setText(code);
        try (FileInputStream in = new FileInputStream(file)) {
          VnScriptParser parser = new VnScriptParser();
          VnScenario scenario = parser.parse(in);
          if (vnPreview != null) vnPreview.setScenario(scenario);
        } catch (Exception ex) {
          if (onStatus != null) onStatus.accept("VNS parse warning: " + ex.getMessage());
        }
      } else if (kind == Kind.TIMELINE) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (timelineEditor != null) timelineEditor.setText(text);
        if (timelineView != null) timelineView.fromText(text);
      } else if (kind == Kind.THEME) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (themeEditor != null) themeEditor.setText(text);
        if (themePreview != null) themePreview.setThemeFromText(text);
        if (themeEditor != null && themePreview != null) {
          themeEditor.setOnTextChanged(t -> themePreview.setThemeFromText(t));
        }
      } else if (kind == Kind.MENU_SCREEN) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuScreenEditor != null) menuScreenEditor.setText(text);
        if (menuScreenVisualEditor != null) {
          menuScreenVisualEditor.setScreenIdHint(screenIdFromFile());
          menuScreenVisualEditor.setMenuText(text);
        }
      } else if (kind == Kind.MENU_LAYOUT) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuLayoutEditor != null) menuLayoutEditor.setText(text);
        if (menuLayoutVisualEditor != null) menuLayoutVisualEditor.setLayoutText(text);
      } else if (kind == Kind.MENU_STYLE) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (menuStyleEditor != null) menuStyleEditor.setText(text);
        if (menuStyleVisualEditor != null) menuStyleVisualEditor.setStyleText(text);
      } else if (kind == Kind.DIALOGUE_LAYOUT) {
        String text = Files.exists(file.toPath()) ? Files.readString(file.toPath()) : "";
        if (dialogueLayoutEditor != null) dialogueLayoutEditor.setText(text);
        if (dialogueLayoutVisualEditor != null) dialogueLayoutVisualEditor.setLayoutText(text);
      } else if (kind == Kind.JAVA) {
        String code = Files.readString(file.toPath());
        javaEditor.setText(code);
      } else if (kind == Kind.OTHER && textEditor != null) {
        String code = Files.readString(file.toPath());
        textEditor.setText(code);
      }
      savedSnapshot = getCurrentText();
      if (onStatus != null) onStatus.accept("Reloaded: " + file.getName());
    } catch (Exception ex) {
      if (onStatus != null) onStatus.accept("Reload failed: " + ex.getMessage());
    }
  }

  public void saveTo(File target) {
    try {
      if (kind == Kind.JES) {
        String content = jesEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.VNS) {
        String content = vnsEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.TIMELINE) {
        String content = timelineEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.THEME) {
        String content = themeEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_SCREEN && menuScreenEditor != null) {
        String content = menuScreenEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_LAYOUT && menuLayoutEditor != null) {
        String content = menuLayoutEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.MENU_STYLE && menuStyleEditor != null) {
        String content = menuStyleEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutEditor != null) {
        String content = dialogueLayoutEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.JAVA) {
        String content = javaEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      } else if (kind == Kind.OTHER && textEditor != null) {
        String content = textEditor.getText();
        try (FileWriter fw = new FileWriter(target)) { fw.write(content); }
      }
      savedSnapshot = getCurrentText();
      if (onStatus != null) onStatus.accept("Saved: " + target.getName());
    } catch (Exception ex) {
      if (onStatus != null) onStatus.accept("Save failed");
    }
  }

  public boolean isDirty() {
    return !Objects.equals(savedSnapshot, getCurrentText());
  }

  public String getDisplayName() {
    return file != null ? file.getName() : "Untitled";
  }

  public void setOnVnsTextChanged(Consumer<String> listener) {
    if (vnsEditor != null) vnsEditor.setOnTextChanged(listener);
  }

  public void setOnVnsCaretLineChanged(Consumer<Integer> listener) {
    if (vnsEditor != null) vnsEditor.setOnCaretLineChanged(listener);
  }

  public int getVnsCaretLine() {
    return vnsEditor != null ? vnsEditor.getCurrentLine() : -1;
  }

  public String getCurrentTextSnapshot() {
    return getCurrentText();
  }

  public void navigateToLine(int oneBasedLine) {
    if (kind == Kind.VNS && vnsEditor != null) {
      vnsEditor.goToLine(oneBasedLine);
    }
  }

  public JesScene2D getJesScene() { return jesScene; }
  public ViewportView getViewport() { return viewport; }
  public VnPreviewView getVnPreview() { return vnPreview; }
  public void stopPreviewAudio() {
    if (vnPreview != null) vnPreview.stopAudio();
  }
  public void dispose() {
    if (disposed) return;
    disposed = true;
    closeDetachedPreviewWindow(true);
    stopPreviewAudio();
    if (vnPreview != null) vnPreview.dispose();
  }
  public Node getEditorNode() {
    if (kind == Kind.JES) return jesEditor;
    if (kind == Kind.VNS) return vnsEditor;
    if (kind == Kind.JAVA) return javaEditor;
    if (kind == Kind.TIMELINE) return timelineEditor;
    if (kind == Kind.MENU_SCREEN) return menuScreenEditor;
    if (kind == Kind.MENU_LAYOUT) return menuLayoutEditor;
    if (kind == Kind.MENU_STYLE) return menuStyleEditor;
    if (kind == Kind.DIALOGUE_LAYOUT) return dialogueLayoutEditor;
    if (kind == Kind.OTHER) return textEditor;
    return null;
  }

  public void setShowGrid(boolean b) { if (viewport != null) viewport.setShowGrid(b); }
  public void fitToContent() { if (viewport != null) viewport.fitToContent(); }
  public void focusEditor() { Node ed = getEditorNode(); if (ed != null) ed.requestFocus(); }
  public boolean supportsEditorFullscreenToggle() { return primarySplit != null; }
  public boolean isEditorFullscreen() { return editorFullscreen; }

  public void toggleEditorFullscreen() {
    if (!supportsEditorFullscreenToggle()) return;
    if (!editorFullscreen) {
      double pos = currentDividerPosition();
      restoreDividerPosition = (pos > 0.02 && pos < 0.98) ? pos : restoreDividerPosition;
      double codeOnly = primarySplitCodeIndex <= 0 ? 1.0 : 0.0;
      primarySplit.setDividerPositions(codeOnly);
      editorFullscreen = true;
    } else {
      double restore = Math.max(0.05, Math.min(0.95, restoreDividerPosition));
      primarySplit.setDividerPositions(restore);
      editorFullscreen = false;
    }
  }

  private SplitPane createVerticalSplit(Node top, Node bottom, double divider) {
    return createSplit(top, bottom, Orientation.VERTICAL, divider, 1);
  }

  private SplitPane createSplit(Node first, Node second, Orientation orientation, double divider, int codeIndex) {
    detachFromParent(first);
    detachFromParent(second);
    SplitPane sp = new SplitPane();
    sp.setOrientation(orientation);
    sp.getItems().addAll(first, second);
    sp.setDividerPositions(divider);
    primarySplit = sp;
    primarySplitCodeIndex = codeIndex <= 0 ? 0 : 1;
    restoreDividerPosition = divider;
    editorFullscreen = false;
    return sp;
  }

  private Node createPreviewWorkspace(String title, Node previewNode, Node editorNode, double divider) {
    BorderPane root = new BorderPane();
    previewWorkspaceContent = new BorderPane();
    dockPreviewNode = previewNode;
    dockEditorNode = editorNode;
    previewDockPosition = PreviewDockPosition.TOP;
    lastEmbeddedPreviewDock = PreviewDockPosition.TOP;
    verticalDockDivider = clampDivider(divider);
    horizontalDockDivider = 0.5;

    previewModePreviewButton = new ToggleButton("Preview");
    previewModeCodeButton = new ToggleButton("Code");
    previewModeSplitButton = new ToggleButton("Split");
    ToggleGroup modeGroup = new ToggleGroup();
    previewModePreviewButton.setToggleGroup(modeGroup);
    previewModeCodeButton.setToggleGroup(modeGroup);
    previewModeSplitButton.setToggleGroup(modeGroup);
    previewModeSplitButton.setSelected(true);

    MenuItem dockTop = new MenuItem("Snap Top");
    dockTop.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.TOP));
    MenuItem dockBottom = new MenuItem("Snap Bottom");
    dockBottom.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.BOTTOM));
    MenuItem dockLeft = new MenuItem("Snap Left");
    dockLeft.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.LEFT));
    MenuItem dockRight = new MenuItem("Snap Right");
    dockRight.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.RIGHT));
    MenuItem dockWindow = new MenuItem("Open Separate Window");
    dockWindow.setOnAction(e -> setPreviewDockPosition(PreviewDockPosition.WINDOW));
    MenuItem dockBack = new MenuItem("Re-dock from Window");
    dockBack.setOnAction(e -> closeDetachedPreviewWindow(false));

    previewDockMenu = new MenuButton("Snap");
    previewDockMenu.getItems().addAll(dockTop, dockBottom, dockLeft, dockRight, dockWindow, dockBack);

    Label titleLabel = new Label(title == null ? "Preview" : title);
    titleLabel.getStyleClass().add("muted");

    HBox toolbar = new HBox(8, titleLabel, previewModePreviewButton, previewModeCodeButton, previewModeSplitButton, previewDockMenu);
    toolbar.setPadding(new javafx.geometry.Insets(6, 6, 6, 6));
    toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    root.setTop(toolbar);
    root.setCenter(previewWorkspaceContent);

    modeGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
      if (nv == null) previewModeSplitButton.setSelected(true);
      applyPreviewWorkspaceMode();
    });

    applyPreviewWorkspaceMode();
    return root;
  }

  private void setPreviewDockPosition(PreviewDockPosition position) {
    if (position == null || dockPreviewNode == null || dockEditorNode == null) return;
    if (position == PreviewDockPosition.WINDOW) {
      openDetachedPreviewWindow();
      return;
    }
    lastEmbeddedPreviewDock = position;
    previewDockPosition = position;
    closeDetachedPreviewWindow(false);
    applyPreviewWorkspaceMode();
  }

  private PreviewLayoutMode currentPreviewLayoutMode() {
    if (previewModePreviewButton != null && previewModePreviewButton.isSelected()) return PreviewLayoutMode.PREVIEW;
    if (previewModeCodeButton != null && previewModeCodeButton.isSelected()) return PreviewLayoutMode.CODE;
    return PreviewLayoutMode.SPLIT;
  }

  private void restorePreviewLayoutMode() {
    if (previewModeBeforeDetach == PreviewLayoutMode.PREVIEW && previewModePreviewButton != null) {
      previewModePreviewButton.setSelected(true);
      return;
    }
    if (previewModeBeforeDetach == PreviewLayoutMode.SPLIT && previewModeSplitButton != null) {
      previewModeSplitButton.setSelected(true);
      return;
    }
    if (previewModeCodeButton != null) {
      previewModeCodeButton.setSelected(true);
      return;
    }
    applyPreviewWorkspaceMode();
  }

  private void applyPreviewWorkspaceMode() {
    if (previewWorkspaceContent == null || dockEditorNode == null) return;
    PreviewLayoutMode mode = currentPreviewLayoutMode();
    boolean detached = isDetachedPreviewVisible();

    if (!detached && mode == PreviewLayoutMode.SPLIT && dockPreviewNode != null) {
      PreviewDockPosition dock = previewDockPosition == PreviewDockPosition.WINDOW ? lastEmbeddedPreviewDock : previewDockPosition;
      previewWorkspaceContent.setCenter(buildDockedPreviewSplit(dock));
    } else if (!detached && mode == PreviewLayoutMode.PREVIEW && dockPreviewNode != null) {
      detachFromParent(dockPreviewNode);
      previewWorkspaceContent.setCenter(dockPreviewNode);
      primarySplit = null;
      editorFullscreen = false;
    } else {
      detachFromParent(dockEditorNode);
      previewWorkspaceContent.setCenter(dockEditorNode);
      primarySplit = null;
      editorFullscreen = false;
    }
    updatePreviewDockMenuText();
    refreshPreviewSizeFromLast();
  }

  private SplitPane buildDockedPreviewSplit(PreviewDockPosition dock) {
    if (dockPreviewNode == null || dockEditorNode == null) return createVerticalSplit(new Label("No preview"), dockEditorNode, 0.6);
    PreviewDockPosition resolved = dock == null ? PreviewDockPosition.TOP : dock;
    SplitPane split;
    if (resolved == PreviewDockPosition.BOTTOM) {
      split = createSplit(dockEditorNode, dockPreviewNode, Orientation.VERTICAL, verticalDockDivider, 0);
    } else if (resolved == PreviewDockPosition.LEFT) {
      split = createSplit(dockPreviewNode, dockEditorNode, Orientation.HORIZONTAL, horizontalDockDivider, 1);
    } else if (resolved == PreviewDockPosition.RIGHT) {
      split = createSplit(dockEditorNode, dockPreviewNode, Orientation.HORIZONTAL, horizontalDockDivider, 0);
    } else {
      split = createSplit(dockPreviewNode, dockEditorNode, Orientation.VERTICAL, verticalDockDivider, 1);
    }
    if (!split.getDividers().isEmpty()) {
      split.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> {
        if (split.getOrientation() == Orientation.VERTICAL) {
          verticalDockDivider = clampDivider(nv.doubleValue());
        } else {
          horizontalDockDivider = clampDivider(nv.doubleValue());
        }
      });
    }
    return split;
  }

  private void openDetachedPreviewWindow() {
    if (dockPreviewNode == null) return;
    if (detachedPreviewStage != null && detachedPreviewStage.isShowing()) {
      detachedPreviewStage.toFront();
      return;
    }
    previewModeBeforeDetach = currentPreviewLayoutMode();
    if (previewDockPosition != PreviewDockPosition.WINDOW) {
      lastEmbeddedPreviewDock = previewDockPosition;
    }
    previewDockPosition = PreviewDockPosition.WINDOW;

    detachFromParent(dockPreviewNode);
    StackPane host = new StackPane(dockPreviewNode);
    Scene scene = new Scene(host, Math.max(640.0, lastSizedWidth * 0.75), Math.max(360.0, lastSizedHeight * 0.65));
    scene.widthProperty().addListener((o, ov, nv) -> refreshPreviewSizeFromLast());
    scene.heightProperty().addListener((o, ov, nv) -> refreshPreviewSizeFromLast());

    Stage stage = new Stage();
    stage.setTitle((file != null ? file.getName() : "Preview") + " - Detached Preview");
    stage.setScene(scene);
    stage.setOnHidden(e -> handleDetachedPreviewStageHidden(stage));
    detachedPreviewStage = stage;
    stage.show();
    if (previewModeBeforeDetach != PreviewLayoutMode.CODE && previewModeCodeButton != null) {
      previewModeCodeButton.setSelected(true);
    }
    startDetachedPreviewTimer();
    applyPreviewWorkspaceMode();
  }

  private void closeDetachedPreviewWindow(boolean disposing) {
    if (detachedPreviewStage == null) return;
    Stage stage = detachedPreviewStage;
    detachedPreviewStage = null;
    stopDetachedPreviewTimer();
    removeDockPreviewFromParent();
    if (stage.isShowing()) stage.hide();
    if (disposing) return;
    previewDockPosition = lastEmbeddedPreviewDock;
    restorePreviewLayoutMode();
  }

  private boolean isDetachedPreviewVisible() {
    return detachedPreviewStage != null && detachedPreviewStage.isShowing();
  }

  private void startDetachedPreviewTimer() {
    if (detachedPreviewTimer != null) return;
    detachedPreviewLastNs = -1L;
    detachedPreviewTimer = new AnimationTimer() {
      @Override
      public void handle(long now) {
        if (!isDetachedPreviewVisible()) return;
        if (detachedPreviewLastNs < 0L) {
          detachedPreviewLastNs = now;
          return;
        }
        long dt = (now - detachedPreviewLastNs) / 1_000_000L;
        detachedPreviewLastNs = now;
        try {
          if (kind == Kind.JES && viewport != null) {
            viewport.render(dt);
          } else if (kind == Kind.VNS && vnPreview != null) {
            vnPreview.render(dt);
          } else if (kind == Kind.THEME && themePreview != null) {
            themePreview.render(dt);
          }
        } catch (Exception ex) {
          stopDetachedPreviewTimer();
          if (onStatus != null) onStatus.accept("Detached preview stopped: " + ex.getMessage());
        }
      }
    };
    detachedPreviewTimer.start();
  }

  private void handleDetachedPreviewStageHidden(Stage stage) {
    if (stage == null || detachedPreviewStage != stage) return;
    detachedPreviewStage = null;
    stopDetachedPreviewTimer();
    removeDockPreviewFromParent();
    if (disposed) return;

    Platform.runLater(() -> {
      if (disposed || detachedPreviewStage != null) return;
      previewDockPosition = lastEmbeddedPreviewDock;
      restorePreviewLayoutMode();
    });
  }

  private void removeDockPreviewFromParent() {
    Parent parent = dockPreviewNode == null ? null : dockPreviewNode.getParent();
    if (parent instanceof Pane pane) {
      pane.getChildren().remove(dockPreviewNode);
    } else if (parent instanceof BorderPane border && border.getCenter() == dockPreviewNode) {
      border.setCenter(null);
    }
  }

  private void stopDetachedPreviewTimer() {
    if (detachedPreviewTimer == null) return;
    detachedPreviewTimer.stop();
    detachedPreviewTimer = null;
    detachedPreviewLastNs = -1L;
  }

  private void updatePreviewDockMenuText() {
    if (previewDockMenu == null) return;
    String label;
    if (isDetachedPreviewVisible()) {
      label = "Window";
    } else {
      PreviewDockPosition dock = previewDockPosition == PreviewDockPosition.WINDOW ? lastEmbeddedPreviewDock : previewDockPosition;
      label = switch (dock) {
        case LEFT -> "Left";
        case RIGHT -> "Right";
        case BOTTOM -> "Bottom";
        case WINDOW -> "Window";
        case TOP -> "Top";
      };
    }
    previewDockMenu.setText("Snap: " + label);
  }

  private void refreshPreviewSizeFromLast() {
    if (lastSizedWidth <= 0 || lastSizedHeight <= 0) return;
    applyPreviewSizing(lastSizedWidth, lastSizedHeight);
  }

  private void applyPreviewSizing(double safeW, double safeH) {
    double previewW = safeW;
    double previewH = sanitizeDimension(safeH * 0.6);

    if (isDetachedPreviewVisible() && detachedPreviewStage != null && detachedPreviewStage.getScene() != null) {
      previewW = sanitizeDimension(detachedPreviewStage.getScene().getWidth());
      previewH = sanitizeDimension(detachedPreviewStage.getScene().getHeight());
    } else if (previewModePreviewButton != null && previewModePreviewButton.isSelected()) {
      previewW = safeW;
      previewH = safeH;
    } else if (previewModeSplitButton != null && previewModeSplitButton.isSelected() && primarySplit != null) {
      double pos = currentDividerPosition();
      double ratio = primarySplitCodeIndex == 1 ? pos : (1.0 - pos);
      ratio = clampDivider(ratio);
      if (primarySplit.getOrientation() == Orientation.HORIZONTAL) {
        previewW = sanitizeDimension(safeW * ratio);
        previewH = safeH;
      } else {
        previewW = safeW;
        previewH = sanitizeDimension(safeH * ratio);
      }
    }

    if (viewport != null) viewport.setSize(previewW, previewH);
    if (vnPreview != null) vnPreview.setSize(previewW, previewH);
    if (themePreview != null) themePreview.setSize(previewW, previewH);
  }

  private Node createStudioWorkspace(String title, Node designNode, Node codeNode, double divider) {
    BorderPane root = new BorderPane();
    BorderPane content = new BorderPane();

    ToggleButton bDesign = new ToggleButton("Design");
    ToggleButton bCode = new ToggleButton("Code");
    ToggleButton bSplit = new ToggleButton("Split");
    ToggleGroup group = new ToggleGroup();
    bDesign.setToggleGroup(group);
    bCode.setToggleGroup(group);
    bSplit.setToggleGroup(group);
    bDesign.setSelected(true);

    Label titleLabel = new Label(title == null ? "Studio" : title);
    titleLabel.getStyleClass().add("muted");

    HBox toolbar = new HBox(8, titleLabel, bDesign, bCode, bSplit);
    toolbar.setPadding(new javafx.geometry.Insets(6, 6, 6, 6));
    toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    root.setTop(toolbar);
    root.setCenter(content);

    Runnable applyMode = () -> {
      if (bSplit.isSelected()) {
        content.setCenter(createVerticalSplit(designNode, codeNode, divider));
      } else if (bCode.isSelected()) {
        detachFromParent(codeNode);
        content.setCenter(codeNode);
        primarySplit = null;
        primarySplitCodeIndex = 1;
        editorFullscreen = false;
      } else {
        detachFromParent(designNode);
        content.setCenter(designNode);
        primarySplit = null;
        primarySplitCodeIndex = 1;
        editorFullscreen = false;
      }
    };

    group.selectedToggleProperty().addListener((o, ov, nv) -> {
      if (nv == null) {
        bDesign.setSelected(true);
      }
      applyMode.run();
    });
    applyMode.run();
    return root;
  }

  private double currentDividerPosition() {
    if (primarySplit == null) return restoreDividerPosition;
    if (primarySplit.getDividers().isEmpty()) return restoreDividerPosition;
    return primarySplit.getDividers().get(0).getPosition();
  }

  private void bindMenuScreenVisualSync() {
    if (menuScreenEditor == null || menuScreenVisualEditor == null) return;
    menuScreenVisualEditor.setScreenIdHint(screenIdFromFile());
    final boolean[] syncing = new boolean[] {false};
    menuScreenEditor.setOnTextChanged(text -> {
      if (syncing[0]) return;
      syncing[0] = true;
      menuScreenVisualEditor.setMenuText(text);
      syncing[0] = false;
    });
    menuScreenVisualEditor.setOnMenuTextChanged(text -> {
      if (syncing[0]) return;
      if (Objects.equals(normalizeLineEndings(menuScreenEditor.getText()), normalizeLineEndings(text))) return;
      syncing[0] = true;
      menuScreenEditor.setTextNoEvent(text);
      syncing[0] = false;
    });
    String current = menuScreenEditor.getText();
    if (current == null) current = "";
    menuScreenVisualEditor.setMenuText(current);
  }

  private void bindMenuLayoutVisualSync() {
    if (menuLayoutEditor == null || menuLayoutVisualEditor == null) return;
    final boolean[] syncing = new boolean[] {false};
    menuLayoutEditor.setOnTextChanged(text -> {
      if (syncing[0]) return;
      syncing[0] = true;
      menuLayoutVisualEditor.setLayoutText(text);
      syncing[0] = false;
    });
    menuLayoutVisualEditor.setOnLayoutTextChanged(text -> {
      if (syncing[0]) return;
      if (Objects.equals(normalizeLineEndings(menuLayoutEditor.getText()), normalizeLineEndings(text))) return;
      syncing[0] = true;
      menuLayoutEditor.setTextNoEvent(text);
      syncing[0] = false;
    });
    String current = menuLayoutEditor.getText();
    if (current == null) current = "";
    menuLayoutVisualEditor.setLayoutText(current);
  }

  private void bindMenuStyleVisualSync() {
    if (menuStyleEditor == null || menuStyleVisualEditor == null) return;
    final boolean[] syncing = new boolean[] {false};
    menuStyleEditor.setOnTextChanged(text -> {
      if (syncing[0]) return;
      syncing[0] = true;
      menuStyleVisualEditor.setStyleText(text);
      syncing[0] = false;
    });
    menuStyleVisualEditor.setOnStyleTextChanged(text -> {
      if (syncing[0]) return;
      if (Objects.equals(normalizeLineEndings(menuStyleEditor.getText()), normalizeLineEndings(text))) return;
      syncing[0] = true;
      menuStyleEditor.setTextNoEvent(text);
      syncing[0] = false;
    });
    String current = menuStyleEditor.getText();
    if (current == null) current = "";
    menuStyleVisualEditor.setStyleText(current);
  }

  private void bindDialogueLayoutVisualSync() {
    if (dialogueLayoutEditor == null || dialogueLayoutVisualEditor == null) return;
    final boolean[] syncing = new boolean[] {false};
    dialogueLayoutEditor.setOnTextChanged(text -> {
      if (syncing[0]) return;
      syncing[0] = true;
      dialogueLayoutVisualEditor.setLayoutText(text);
      syncing[0] = false;
    });
    dialogueLayoutVisualEditor.setOnLayoutTextChanged(text -> {
      if (syncing[0]) return;
      if (Objects.equals(normalizeLineEndings(dialogueLayoutEditor.getText()), normalizeLineEndings(text))) return;
      syncing[0] = true;
      dialogueLayoutEditor.setTextNoEvent(text);
      syncing[0] = false;
    });
    String current = dialogueLayoutEditor.getText();
    if (current == null) current = "";
    dialogueLayoutVisualEditor.setLayoutText(current);
  }

  private String screenIdFromFile() {
    if (file == null) return "main";
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private String getCurrentText() {
    if (kind == Kind.JES && jesEditor != null) return jesEditor.getText();
    if (kind == Kind.VNS && vnsEditor != null) return vnsEditor.getText();
    if (kind == Kind.TIMELINE && timelineEditor != null) return timelineEditor.getText();
    if (kind == Kind.THEME && themeEditor != null) return themeEditor.getText();
    if (kind == Kind.MENU_SCREEN && menuScreenEditor != null) return menuScreenEditor.getText();
    if (kind == Kind.MENU_LAYOUT && menuLayoutEditor != null) return menuLayoutEditor.getText();
    if (kind == Kind.MENU_STYLE && menuStyleEditor != null) return menuStyleEditor.getText();
    if (kind == Kind.DIALOGUE_LAYOUT && dialogueLayoutEditor != null) return dialogueLayoutEditor.getText();
    if (kind == Kind.JAVA && javaEditor != null) return javaEditor.getText();
    if (kind == Kind.OTHER && textEditor != null) return textEditor.getText();
    return "";
  }

  private static void detachFromParent(Node node) {
    if (node == null) return;
    Parent parent = node.getParent();
    if (parent == null) return;
    if (parent instanceof SplitPane split) {
      split.getItems().remove(node);
      return;
    }
    if (parent instanceof BorderPane border) {
      if (border.getCenter() == node) border.setCenter(null);
      else if (border.getTop() == node) border.setTop(null);
      else if (border.getBottom() == node) border.setBottom(null);
      else if (border.getLeft() == node) border.setLeft(null);
      else if (border.getRight() == node) border.setRight(null);
      return;
    }
    if (parent instanceof Pane pane) {
      pane.getChildren().remove(node);
    }
  }

  private static double clampDivider(double value) {
    if (!Double.isFinite(value)) return 0.5;
    return Math.max(0.05, Math.min(0.95, value));
  }

  private static String mapKey(KeyCode code) {
    if (code == null) return "";
    String name = code.getName();
    if (name == null || name.isBlank()) name = code.toString();
    return name.toUpperCase();
  }

  private static String normalizeLineEndings(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static double sanitizeDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }

  private static JavaCodeEditor newDslEditor() {
    JavaCodeEditor editor = new JavaCodeEditor();
    editor.useDslHighlighting();
    return editor;
  }
}

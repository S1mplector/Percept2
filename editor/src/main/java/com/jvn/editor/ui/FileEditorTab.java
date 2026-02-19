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

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

public class FileEditorTab extends BorderPane {
  public enum Kind { JES, VNS, JAVA, TIMELINE, THEME, OTHER }

  private final File file;
  private final Kind kind;

  private final JesCodeEditor jesEditor;
  private final VnsCodeEditor vnsEditor;
  private final JavaCodeEditor javaEditor;
  private final JavaCodeEditor textEditor;
  private final TimelineCodeEditor timelineEditor;
  private final JavaCodeEditor themeEditor;
  private final StoryTimelineView timelineView;

  private final ViewportView viewport; // JES preview
  private JesScene2D jesScene;

  private final VnPreviewView vnPreview; // VNS preview
  private final MenuThemePreviewView themePreview; // THEME preview

  private Consumer<Entity2D> onSelected;
  private Consumer<String> onStatus;
  private com.jvn.editor.commands.CommandStack commands;
  private File projectRoot;
  private String savedSnapshot = "";

  public FileEditorTab(File file) {
    this.file = file;
    String name = file != null ? file.getName().toLowerCase(Locale.ROOT) : "";
    if (name.endsWith(".jes") || name.endsWith(".txt")) this.kind = Kind.JES;
    else if (name.endsWith(".vns")) this.kind = Kind.VNS;
    else if (name.endsWith(".java")) this.kind = Kind.JAVA;
    else if (name.endsWith(".timeline")) this.kind = Kind.TIMELINE;
    else if (name.endsWith(".theme") || "menu.theme".equals(name)) this.kind = Kind.THEME;
    else this.kind = Kind.OTHER;

    this.jesEditor = (kind == Kind.JES) ? new JesCodeEditor() : null;
    this.vnsEditor = (kind == Kind.VNS) ? new VnsCodeEditor() : null;
    this.javaEditor = (kind == Kind.JAVA) ? new JavaCodeEditor() : null;
    this.textEditor = (kind == Kind.OTHER) ? new JavaCodeEditor() : null;
    this.timelineEditor = (kind == Kind.TIMELINE) ? new TimelineCodeEditor() : null;
    this.themeEditor = (kind == Kind.THEME) ? new JavaCodeEditor() : null;
    this.timelineView = (kind == Kind.TIMELINE) ? new StoryTimelineView() : null;

    this.viewport = (kind == Kind.JES) ? new ViewportView() : null;
    this.vnPreview = (kind == Kind.VNS) ? new VnPreviewView() : null;
    this.themePreview = (kind == Kind.THEME) ? new MenuThemePreviewView() : null;

    if (viewport != null) {
      viewport.setOnSelected(e -> { if (onSelected != null) onSelected.accept(e); });
      viewport.setOnStatus(s -> { if (onStatus != null) onStatus.accept(s); });
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
    }
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
      SplitPane sp = new SplitPane();
      sp.setOrientation(javafx.geometry.Orientation.VERTICAL);
      sp.getItems().addAll(viewport, jesEditor);
      sp.setDividerPositions(0.6);
      setCenter(sp);
    } else if (kind == Kind.VNS) {
      SplitPane sp = new SplitPane();
      sp.setOrientation(javafx.geometry.Orientation.VERTICAL);
      sp.getItems().addAll(vnPreview, vnsEditor);
      sp.setDividerPositions(0.6);
      setCenter(sp);
    } else if (kind == Kind.JAVA) {
      setCenter(javaEditor);
    } else if (kind == Kind.TIMELINE) {
      SplitPane sp = new SplitPane();
      sp.setOrientation(javafx.geometry.Orientation.VERTICAL);
      sp.getItems().addAll(timelineView, timelineEditor);
      sp.setDividerPositions(0.6);
      setCenter(sp);
    } else if (kind == Kind.THEME) {
      SplitPane sp = new SplitPane();
      sp.setOrientation(javafx.geometry.Orientation.VERTICAL);
      sp.getItems().addAll(themePreview, themeEditor);
      sp.setDividerPositions(0.6);
      setCenter(sp);
      if (themeEditor != null && themePreview != null) {
        String text = themeEditor.getText(); if (text == null) text = "";
        themePreview.setThemeFromText(text);
        themeEditor.setOnTextChanged(t -> themePreview.setThemeFromText(t));
      }
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
  }

  public void setCommandStack(com.jvn.editor.commands.CommandStack cs) {
    this.commands = cs;
    if (viewport != null) viewport.setCommandStack(cs);
  }

  public void render(long dt) {
    if (kind == Kind.JES && viewport != null) {
      viewport.render(dt);
    } else if (kind == Kind.VNS && vnPreview != null) {
      vnPreview.render(dt);
    } else if (kind == Kind.THEME && themePreview != null) {
      themePreview.render(dt);
    }
  }

  public void setSize(double w, double h) {
    if (viewport != null) viewport.setSize(w, h * 0.6);
    if (vnPreview != null) vnPreview.setSize(w, h * 0.6);
    if (themePreview != null) themePreview.setSize(w, h * 0.6);
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

  public JesScene2D getJesScene() { return jesScene; }
  public ViewportView getViewport() { return viewport; }
  public VnPreviewView getVnPreview() { return vnPreview; }
  public Node getEditorNode() {
    if (kind == Kind.JES) return jesEditor;
    if (kind == Kind.VNS) return vnsEditor;
    if (kind == Kind.JAVA) return javaEditor;
    if (kind == Kind.TIMELINE) return timelineEditor;
    if (kind == Kind.OTHER) return textEditor;
    return null;
  }

  public void setShowGrid(boolean b) { if (viewport != null) viewport.setShowGrid(b); }
  public void fitToContent() { if (viewport != null) viewport.fitToContent(); }
  public void focusEditor() { Node ed = getEditorNode(); if (ed != null) ed.requestFocus(); }

  private String getCurrentText() {
    if (kind == Kind.JES && jesEditor != null) return jesEditor.getText();
    if (kind == Kind.VNS && vnsEditor != null) return vnsEditor.getText();
    if (kind == Kind.TIMELINE && timelineEditor != null) return timelineEditor.getText();
    if (kind == Kind.THEME && themeEditor != null) return themeEditor.getText();
    if (kind == Kind.JAVA && javaEditor != null) return javaEditor.getText();
    if (kind == Kind.OTHER && textEditor != null) return textEditor.getText();
    return "";
  }

  private static String mapKey(KeyCode code) {
    if (code == null) return "";
    String name = code.getName();
    if (name == null || name.isBlank()) name = code.toString();
    return name.toUpperCase();
  }
}

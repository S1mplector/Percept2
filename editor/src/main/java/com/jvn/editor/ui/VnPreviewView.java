package com.jvn.editor.ui;

import java.io.File;

import com.jvn.core.vn.DefaultVnInterop;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.fx.vn.VnRenderer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;

public class VnPreviewView extends StackPane {
  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private VnRenderer renderer = new VnRenderer(gc);
  private VnScene scene;
  private double mouseX, mouseY;
  private FxAudioService audio;
  private java.io.File projectRoot;

  public VnPreviewView() {
    getChildren().add(canvas);

    // Input handlers
    canvas.setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); });
    canvas.setOnMouseClicked(e -> {
      if (scene == null) return;
      if (e.getButton() == MouseButton.PRIMARY) {
        var state = scene.getState();
        var node = state.getCurrentNode();
        if (node != null && node.getType() == VnNodeType.CHOICE) {
          int idx = renderer.getHoveredChoiceIndex(node.getChoices(), canvas.getWidth(), canvas.getHeight(), mouseX, mouseY);
          if (idx >= 0) {
            scene.selectChoice(idx);
          }
        } else {
          scene.advance();
        }
      }
    });

    setOnKeyPressed(e -> {
      if (scene == null) return;
      KeyCode code = e.getCode();
      if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
        scene.advance();
      } else if (code == KeyCode.H) {
        scene.getState().toggleUiHidden();
      } else if (code == KeyCode.ESCAPE) {
        // could toggle history overlay or ignore; keep minimal
      }
    });

    Tooltip.install(canvas, new Tooltip("Click: Advance / Choose  •  Space/Enter: Advance  •  H: Toggle UI"));

    // Keep focus for key handling when mouse enters
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> canvas.requestFocus());
  }

  public void setScenario(VnScenario scenario) {
    if (scenario == null) { this.scene = null; return; }
    this.scene = new VnScene(scenario);
    this.scene.setInterop(new DefaultVnInterop());
    // Ensure audio works in preview
    if (audio == null) audio = new FxAudioService();
    if (projectRoot != null) audio.setProjectRoot(projectRoot);
    this.scene.setAudioFacade(audio);
    this.scene.onEnter();
  }

  public void runScenario(VnScenario scenario, String label) {
    setScenario(scenario);
    if (this.scene != null && label != null && !label.isBlank()) {
      this.scene.getState().jumpToLabel(label);
      this.scene.onEnter();
    }
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    if (renderer != null) renderer.setProjectRoot(root);
    if (audio != null) audio.setProjectRoot(root);
    if (scene != null) scene.setAudioFacade(audio == null ? (audio = new FxAudioService()) : audio);
  }

  public void setSize(double w, double h) {
    canvas.setWidth(Math.max(1, w));
    canvas.setHeight(Math.max(1, h));
  }

  public void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    if (scene == null) {
      gc.setFill(javafx.scene.paint.Color.color(0.06, 0.06, 0.08));
      gc.fillRect(0, 0, w, h);
      gc.setFill(javafx.scene.paint.Color.WHITE);
      gc.fillText("Open a VNS file to preview", 20, 30);
      return;
    }
    scene.update(deltaMs);
    renderer.updateAnimation(deltaMs);
    renderer.render(scene.getState(), scene.getScenario(), w, h, mouseX, mouseY);
  }
}

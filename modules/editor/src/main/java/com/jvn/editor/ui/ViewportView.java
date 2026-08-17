package com.jvn.editor.ui;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.Input;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.commands.MoveEntityCommand;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;

public class ViewportView extends StackPane {
  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final FxBlitter2D blitter = new FxBlitter2D(gc);
  private final Input input = new Input();
  private final Camera2D camera = new Camera2D();

  private JesScene2D scene;
  private LongConsumer beforeSceneUpdateHook;
  private Entity2D selected;
  private boolean showGrid = true;
  private StoryboardOverlayState storyboardOverlay = StoryboardOverlayState.none();
  private int overlayViewportWidth = ProjectViewportSpec.DEFAULT_WIDTH;
  private int overlayViewportHeight = ProjectViewportSpec.DEFAULT_HEIGHT;
  private VnErrorOverlay activeError;

  private boolean panning = false;
  private double panLastX, panLastY;
  private boolean dragging = false;
  private double dragOffsetX, dragOffsetY;
  private double dragStartX, dragStartY;
  private boolean storyboardOffsetDragging = false;
  private boolean storyboardOffsetDragMoved = false;
  private boolean suppressNextStoryboardClick = false;
  private double storyboardDragStartX, storyboardDragStartY;
  private double storyboardDragStartOffsetX, storyboardDragStartOffsetY;

  private Consumer<Entity2D> onSelected;
  private Consumer<String> onStatus;
  private Consumer<StoryboardOverlayState> onStoryboardStateAdjusted;
  private Runnable onHotReloadRequested;
  private CommandStack commands;

  public ViewportView() {
    getChildren().add(canvas);

    // Mouse handlers
    canvas.setOnMouseClicked(e -> {
      if (suppressNextStoryboardClick) {
        suppressNextStoryboardClick = false;
        return;
      }
      if (!isInsidePreviewSurface(e.getX(), e.getY())) return;
      pick(screenToPreviewX(e.getX()), screenToPreviewY(e.getY()));
      if (onSelected != null) onSelected.accept(selected);
    });
    canvas.setOnMouseMoved(e -> input.setMousePosition(screenToPreviewX(e.getX()), screenToPreviewY(e.getY())));
    canvas.setOnMouseDragged(e -> {
      input.setMousePosition(screenToPreviewX(e.getX()), screenToPreviewY(e.getY()));
      if (storyboardOffsetDragging && e.isPrimaryButtonDown()) {
        dragStoryboardOverlayTo(e.getX(), e.getY());
      } else if (dragging && selected != null && e.isPrimaryButtonDown()) {
        double z = camera.getZoom();
        double wx = camera.getX() + screenToPreviewX(e.getX()) / z;
        double wy = camera.getY() + screenToPreviewY(e.getY()) / z;
        selected.setPosition(wx + dragOffsetX, wy + dragOffsetY);
      } else if (panning) {
        double currentX = screenToPreviewX(e.getX());
        double currentY = screenToPreviewY(e.getY());
        double dx = currentX - panLastX;
        double dy = currentY - panLastY;
        panLastX = currentX; panLastY = currentY;
        camera.setPosition(camera.getX() - dx / camera.getZoom(), camera.getY() - dy / camera.getZoom());
      }
    });
    canvas.setOnScroll(e -> {
      input.addScrollDeltaY(e.getDeltaY());
      double z = camera.getZoom();
      double worldX = camera.getX() + screenToPreviewX(e.getX()) / z;
      double worldY = camera.getY() + screenToPreviewY(e.getY()) / z;
      double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
      double newZ = z * factor;
      camera.setZoom(newZ);
      camera.setPosition(worldX - screenToPreviewX(e.getX()) / newZ, worldY - screenToPreviewY(e.getY()) / newZ);
    });
    canvas.setOnMousePressed(e -> {
      input.mouseDown(mapButton(e.getButton()));
      if (e.getButton() == MouseButton.MIDDLE || e.getButton() == MouseButton.SECONDARY) {
        panning = true; panLastX = screenToPreviewX(e.getX()); panLastY = screenToPreviewY(e.getY());
      } else if (e.getButton() == MouseButton.PRIMARY) {
        if (!isInsidePreviewSurface(e.getX(), e.getY())) {
          dragging = false;
          selected = null;
          return;
        }
        if (storyboardModeActive() && isInsideStoryboardImage(e.getX(), e.getY())) {
          storyboardOffsetDragging = true;
          storyboardOffsetDragMoved = false;
          storyboardDragStartX = e.getX();
          storyboardDragStartY = e.getY();
          storyboardDragStartOffsetX = storyboardOverlay.offsetX();
          storyboardDragStartOffsetY = storyboardOverlay.offsetY();
          dragging = false;
          selected = null;
          return;
        }
        double z = camera.getZoom();
        double previewX = screenToPreviewX(e.getX());
        double previewY = screenToPreviewY(e.getY());
        double wx = camera.getX() + previewX / z;
        double wy = camera.getY() + previewY / z;
        pick(previewX, previewY);
        if (selected != null) {
          dragOffsetX = selected.getX() - wx;
          dragOffsetY = selected.getY() - wy;
          dragStartX = selected.getX();
          dragStartY = selected.getY();
          dragging = true;
        }
      }
    });
    canvas.setOnMouseReleased(e -> {
      input.mouseUp(mapButton(e.getButton()));
      if (e.getButton() == MouseButton.MIDDLE || e.getButton() == MouseButton.SECONDARY) panning = false;
      if (e.getButton() == MouseButton.PRIMARY) {
        if (storyboardOffsetDragging) {
          storyboardOffsetDragging = false;
          suppressNextStoryboardClick = storyboardOffsetDragMoved;
          storyboardOffsetDragMoved = false;
          return;
        }
        if (dragging && selected != null && commands != null) {
          double tx = selected.getX(), ty = selected.getY();
          if (Math.abs(tx - dragStartX) > 0.0001 || Math.abs(ty - dragStartY) > 0.0001) {
            commands.pushAndExecute(new MoveEntityCommand(selected, dragStartX, dragStartY, tx, ty));
            if (onStatus != null) onStatus.accept("Moved entity");
          }
        }
        dragging = false;
      }
    });

    // Tooltip for quick help
    Tooltip.install(canvas, new Tooltip("WASD/Arrows: Pan  •  Shift: Boost  •  Scroll/Q/E: Zoom  •  Drag: Move"));

    // Optional: keep focus to capture keys when mouse enters
    canvas.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> canvas.requestFocus());

    // Key handler to prevent text inputs eating keys when focused on viewport
    addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.isShiftDown() && e.getCode() == javafx.scene.input.KeyCode.R) {
        if (onHotReloadRequested != null) onHotReloadRequested.run();
        e.consume();
        return;
      }
      if (e.getTarget() == canvas) e.consume();
    });
  }

  public void setOnSelected(Consumer<Entity2D> c) { this.onSelected = c; }
  public void setOnStatus(Consumer<String> c) { this.onStatus = c; }
  public void setOnStoryboardStateAdjusted(Consumer<StoryboardOverlayState> c) { this.onStoryboardStateAdjusted = c; }
  public void setOnHotReloadRequested(Runnable c) { this.onHotReloadRequested = c; }
  public void setCommandStack(CommandStack cs) { this.commands = cs; }

  public void setScene(JesScene2D s) {
    this.scene = s;
    if (scene != null) { scene.setInput(input); scene.setCamera(camera); }
  }

  public void setBeforeSceneUpdateHook(LongConsumer hook) {
    this.beforeSceneUpdateHook = hook;
  }

  public void setActiveError(VnErrorOverlay error) {
    this.activeError = error;
  }

  public void clearActiveError() {
    this.activeError = null;
  }

  public Input getInput() { return input; }
  public Camera2D getCamera() { return camera; }

  public void setProjectRoot(java.io.File root) {
    blitter.clearCache();
    blitter.setProjectRoot(root);
    ProjectViewportSpec.Dimensions dims = ProjectViewportSpec.resolve(root);
    overlayViewportWidth = dims.width();
    overlayViewportHeight = dims.height();
  }
  public void setStoryboardOverlay(StoryboardOverlayState storyboardOverlay) {
    this.storyboardOverlay = storyboardOverlay == null ? StoryboardOverlayState.none() : storyboardOverlay;
  }

  public void dispose() {
    scene = null;
    beforeSceneUpdateHook = null;
    selected = null;
    blitter.clearCache();
    blitter.setProjectRoot(null);
  }
  public void setShowGrid(boolean b) { this.showGrid = b; }
  public void setSize(double w, double h) {
    double sw = sanitizeCanvasDimension(w);
    double sh = sanitizeCanvasDimension(h);
    if (Math.abs(canvas.getWidth() - sw) >= 0.5) canvas.setWidth(sw);
    if (Math.abs(canvas.getHeight() - sh) >= 0.5) canvas.setHeight(sh);
  }

  public void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    gc.setFill(javafx.scene.paint.Color.color(0.08,0.08,0.1));
    gc.fillRect(0, 0, w, h);

    handleKeyboardCamera(deltaMs);
    PreviewSurface surface = previewSurface(w, h);

    gc.save();
    gc.beginPath();
    gc.rect(surface.x, surface.y, surface.width, surface.height);
    gc.closePath();
    gc.clip();
    gc.translate(surface.x, surface.y);

    if (showGrid) drawGrid(surface.width, surface.height);

    blitter.setViewport(surface.width, surface.height);
    if (scene != null) {
      if (beforeSceneUpdateHook != null) {
        try {
          beforeSceneUpdateHook.accept(deltaMs);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
      }
      scene.update(deltaMs);
      scene.render(blitter, surface.width, surface.height);
      drawStoryboardOverlay(surface);
      drawSelectionOverlay();
      if (scene.getInput() != null) scene.getInput().endFrame();
    } else {
      drawStoryboardOverlay(surface);
      gc.setFill(javafx.scene.paint.Color.WHITE);
      gc.fillText("Open a JES file to preview", 20, 30);
    }
    if (activeError != null) {
      drawErrorOverlay(activeError, surface.width, surface.height);
    }
    gc.restore();
  }

  private void drawErrorOverlay(VnErrorOverlay error, double width, double height) {
    if (error == null) return;
    gc.save();
    gc.setGlobalAlpha(0.97);
    gc.setFill(javafx.scene.paint.Color.rgb(22, 24, 30));
    gc.fillRect(0, 0, width, height);
    gc.setGlobalAlpha(1.0);

    double pad = Math.max(24, Math.min(44, width * 0.04));
    double contentW = Math.max(120, width - pad * 2);
    double y = pad;

    gc.setFill(javafx.scene.paint.Color.rgb(255, 126, 126));
    gc.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, Math.min(30, Math.max(22, height * 0.046))));
    gc.fillText(error.getTitle(), pad, y + 30);
    y += 54;

    gc.setStroke(javafx.scene.paint.Color.rgb(83, 89, 108));
    gc.setLineWidth(1.5);
    gc.strokeLine(pad, y, pad + contentW, y);
    y += 22;

    gc.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.NORMAL, 15));
    gc.setFill(javafx.scene.paint.Color.rgb(190, 198, 214));
    if (error.getSourceName() != null && !error.getSourceName().isBlank()) {
      gc.fillText("File: " + error.getSourceName(), pad, y);
      y += 22;
    }
    if (error.getLineNumber() > 0) {
      gc.fillText("Line: " + error.getLineNumber(), pad, y);
      y += 22;
    }
    gc.fillText("Type: " + error.getType().name().replace('_', ' '), pad, y);
    y += 30;

    if (error.getRawLine() != null && !error.getRawLine().isBlank()) {
      y = drawOverlayBox("Script Line", error.getRawLine(), pad, y, contentW, Math.min(78, height * 0.14));
      y += 12;
    }
    y = drawOverlayBox("Cause", error.getMessage(), pad, y, contentW, Math.min(104, height * 0.18));
    y += 12;
    if (error.getLikelyCause() != null && !error.getLikelyCause().isBlank()) {
      drawOverlayBox("Likely Went Wrong", error.getLikelyCause(), pad, y, contentW, Math.min(118, height * 0.20));
    }
    gc.restore();
  }

  private double drawOverlayBox(String title, String body, double x, double y, double w, double h) {
    gc.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 16));
    gc.setFill(javafx.scene.paint.Color.rgb(236, 240, 248));
    gc.fillText(title + ":", x, y);
    y += 10;
    gc.setFill(javafx.scene.paint.Color.rgb(34, 37, 47));
    gc.fillRoundRect(x, y, w, h, 8, 8);
    gc.setStroke(javafx.scene.paint.Color.rgb(84, 91, 111));
    gc.strokeRoundRect(x, y, w, h, 8, 8);
    gc.setFont(javafx.scene.text.Font.font("Monospaced", javafx.scene.text.FontWeight.NORMAL, 14));
    gc.setFill(javafx.scene.paint.Color.rgb(231, 235, 245));
    drawWrappedOverlayText(body == null ? "(unknown)" : body, x + 12, y + 22, w - 24, h - 14);
    return y + h;
  }

  private void drawWrappedOverlayText(String text, double x, double y, double maxW, double maxH) {
    if (text == null || text.isBlank()) return;
    String[] words = text.replace('\n', ' ').split("\\s+");
    StringBuilder line = new StringBuilder();
    double lineH = 17;
    double cy = y;
    for (String word : words) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (measureOverlayText(candidate) > maxW && !line.isEmpty()) {
        if (cy > y + maxH) return;
        gc.fillText(line.toString(), x, cy);
        cy += lineH;
        line.setLength(0);
        line.append(word);
      } else {
        line.setLength(0);
        line.append(candidate);
      }
    }
    if (!line.isEmpty() && cy <= y + maxH) {
      gc.fillText(line.toString(), x, cy);
    }
  }

  private double measureOverlayText(String text) {
    javafx.scene.text.Text node = new javafx.scene.text.Text(text == null ? "" : text);
    node.setFont(gc.getFont());
    return node.getLayoutBounds().getWidth();
  }

  private void drawStoryboardOverlay(PreviewSurface surface) {
    if (!storyboardModeActive() || surface == null) return;
    gc.save();
    gc.setGlobalAlpha(storyboardOverlay.opacity());
    StoryboardOverlayPlacement.Rect placement = StoryboardOverlayPlacement.compute(
        storyboardOverlay,
        0.0,
        0.0,
        surface.width,
        surface.height);
    drawStoryboardImage(placement);
    gc.restore();
  }

  private void drawStoryboardImage(StoryboardOverlayPlacement.Rect placement) {
    if (placement == null || placement.width() <= 0.0 || placement.height() <= 0.0) return;
    javafx.scene.image.Image image = storyboardOverlay.image();
    if (storyboardOverlay.cropEnabled()) {
      double sx = Math.max(0.0, Math.min(image.getWidth(), storyboardOverlay.cropX()));
      double sy = Math.max(0.0, Math.min(image.getHeight(), storyboardOverlay.cropY()));
      double sw = Math.max(1.0, Math.min(image.getWidth() - sx, storyboardOverlay.cropWidth()));
      double sh = Math.max(1.0, Math.min(image.getHeight() - sy, storyboardOverlay.cropHeight()));
      gc.drawImage(image, sx, sy, sw, sh, placement.x(), placement.y(), placement.width(), placement.height());
      return;
    }
    gc.drawImage(image, placement.x(), placement.y(), placement.width(), placement.height());
  }

  private void drawGrid(double w, double h) {
    double z = Math.max(0.0001, camera.getZoom());
    double step = 50.0 * z;
    if (step < 8) return;
    gc.setStroke(javafx.scene.paint.Color.color(1,1,1,0.06));
    double ox = (-camera.getX() * z) % step;
    double oy = (-camera.getY() * z) % step;
    for (double x = ox; x <= w; x += step) gc.strokeLine(x, 0, x, h);
    for (double y = oy; y <= h; y += step) gc.strokeLine(0, y, w, y);
  }

  public void fitToContent() {
    Rect b = computeSceneBounds(); if (b == null) return;
    double w = canvas.getWidth(), h = canvas.getHeight(), pad = 40;
    double zx = (w - pad) / Math.max(1, b.w);
    double zy = (h - pad) / Math.max(1, b.h);
    double z = Math.max(0.05, Math.min(zx, zy));
    camera.setZoom(z);
    camera.setPosition(b.x + b.w/2.0 - w/(2.0*z), b.y + b.h/2.0 - h/(2.0*z));
  }

  public void fitToEntity(Entity2D e) {
    if (e == null) return;
    Rect r = null;
    if (e instanceof Panel2D p) r = new Rect(e.getX(), e.getY(), p.getWidth(), p.getHeight());
    else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
      double sx = e.getX() - s.getOriginX() * s.getWidth();
      double sy = e.getY() - s.getOriginY() * s.getHeight();
      r = new Rect(sx, sy, s.getWidth(), s.getHeight());
    } else if (e instanceof PhysicsBodyEntity2D pb) {
      var b = pb.getBody(); if (b != null) {
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
          double cx = b.getCircle().x, cy = b.getCircle().y, rr = b.getCircle().r;
          r = new Rect(cx - rr, cy - rr, rr * 2, rr * 2);
        } else { var a = b.getAabb(); r = new Rect(a.x, a.y, a.w, a.h); }
      }
    } else if (e instanceof Label2D) { r = new Rect(e.getX() - 50, e.getY() - 15, 100, 30); }
    if (r == null) return;
    double w = canvas.getWidth(), h = canvas.getHeight(), pad = 40;
    double zx = (w - pad) / Math.max(1, r.w);
    double zy = (h - pad) / Math.max(1, r.h);
    double z = Math.max(0.05, Math.min(zx, zy));
    camera.setZoom(z);
    camera.setPosition(r.x + r.w/2.0 - w/(2.0*z), r.y + r.h/2.0 - h/(2.0*z));
  }

  private void drawSelectionOverlay() {
    if (selected == null) return;
    blitter.push();
    blitter.setStroke(0.2, 0.8, 1, 1);
    blitter.setStrokeWidth(1.0);
    if (selected instanceof Panel2D p) {
      blitter.strokeRect(selected.getX(), selected.getY(), p.getWidth(), p.getHeight());
    } else if (selected instanceof PhysicsBodyEntity2D pb) {
      RigidBody2D b = pb.getBody();
      if (b != null) {
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) blitter.strokeCircle(b.getCircle().x, b.getCircle().y, b.getCircle().r);
        else { var aabb = b.getAabb(); blitter.strokeRect(aabb.x, aabb.y, aabb.w, aabb.h); }
      }
    }
    blitter.pop();
  }

  private void pick(double x, double y) {
    if (scene == null) return;
    selected = null;
    double z = camera.getZoom();
    double wx = camera.getX() + x / z;
    double wy = camera.getY() + y / z;
    var list = ((Scene2DBase)scene).getChildren();
    for (int i = list.size() - 1; i >= 0; i--) {
      Entity2D e = list.get(i);
      if (!e.isVisible()) continue;
      if (e instanceof Panel2D p) {
        if (wx >= e.getX() && wy >= e.getY() && wx <= e.getX() + p.getWidth() && wy <= e.getY() + p.getHeight()) { selected = e; break; }
      } else if (e instanceof PhysicsBodyEntity2D pb) {
        RigidBody2D b = pb.getBody(); if (b == null) continue;
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
          double dx = wx - b.getCircle().x, dy = wy - b.getCircle().y; double rr = b.getCircle().r; if (dx*dx + dy*dy <= rr*rr) { selected = e; break; }
        } else {
          var a = b.getAabb(); if (wx >= a.x && wy >= a.y && wx <= a.x + a.w && wy <= a.y + a.h) { selected = e; break; }
        }
      } else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
        double sx = e.getX() - s.getOriginX() * s.getWidth();
        double sy = e.getY() - s.getOriginY() * s.getHeight();
        if (wx >= sx && wy >= sy && wx <= sx + s.getWidth() && wy <= sy + s.getHeight()) { selected = e; break; }
      } else if (e instanceof Label2D) {
        double w = 100, h = 30;
        if (wx >= e.getX() - w/2 && wy >= e.getY() - h && wx <= e.getX() + w/2 && wy <= e.getY()) { selected = e; break; }
      }
    }
  }

  private void handleKeyboardCamera(long deltaMs) {
    double dt = Math.max(0, deltaMs) / 1000.0;
    double speed = 600.0 * dt;
    if (input.isKeyDown("SHIFT")) speed *= 2.5;
    double dx = 0, dy = 0;
    if (input.isKeyDown("A") || input.isKeyDown("LEFT")) dx -= speed;
    if (input.isKeyDown("D") || input.isKeyDown("RIGHT")) dx += speed;
    if (input.isKeyDown("W") || input.isKeyDown("UP")) dy -= speed;
    if (input.isKeyDown("S") || input.isKeyDown("DOWN")) dy += speed;
    if (dx != 0 || dy != 0) camera.setPosition(camera.getX() + dx, camera.getY() + dy);
    if (input.wasKeyPressed("Q")) camera.setZoom(camera.getZoom() * 0.9);
    if (input.wasKeyPressed("E")) camera.setZoom(camera.getZoom() * 1.1);
  }

  private boolean storyboardModeActive() {
    return storyboardOverlay != null && storyboardOverlay.enabled() && storyboardOverlay.hasImage();
  }

  private PreviewSurface previewSurface(double canvasWidth, double canvasHeight) {
    if (!storyboardModeActive()) {
      return new PreviewSurface(0.0, 0.0, canvasWidth, canvasHeight);
    }
    ViewportScaler2D.Transform transform =
        ViewportScaler2D.fit(overlayViewportWidth, overlayViewportHeight, canvasWidth, canvasHeight);
    return new PreviewSurface(
        transform.offsetX(),
        transform.offsetY(),
        transform.contentWidth(),
        transform.contentHeight());
  }

  private boolean isInsidePreviewSurface(double canvasX, double canvasY) {
    PreviewSurface surface = previewSurface(canvas.getWidth(), canvas.getHeight());
    return canvasX >= surface.x
        && canvasX <= surface.x + surface.width
        && canvasY >= surface.y
        && canvasY <= surface.y + surface.height;
  }

  private boolean isInsideStoryboardImage(double canvasX, double canvasY) {
    if (!storyboardModeActive()) return false;
    PreviewSurface surface = previewSurface(canvas.getWidth(), canvas.getHeight());
    StoryboardOverlayPlacement.Rect placement = StoryboardOverlayPlacement.compute(
        storyboardOverlay,
        surface.x,
        surface.y,
        surface.width,
        surface.height);
    return placement != null
        && canvasX >= placement.x()
        && canvasX <= placement.x() + placement.width()
        && canvasY >= placement.y()
        && canvasY <= placement.y() + placement.height();
  }

  private void dragStoryboardOverlayTo(double canvasX, double canvasY) {
    if (!storyboardModeActive()) return;
    PreviewSurface surface = previewSurface(canvas.getWidth(), canvas.getHeight());
    double runtimeWidth = storyboardOverlay.runtimeWidth() > 0.0 ? storyboardOverlay.runtimeWidth() : overlayViewportWidth;
    double runtimeHeight = storyboardOverlay.runtimeHeight() > 0.0 ? storyboardOverlay.runtimeHeight() : overlayViewportHeight;
    double scaleX = surface.width / Math.max(1.0, runtimeWidth);
    double scaleY = surface.height / Math.max(1.0, runtimeHeight);
    double nextOffsetX = storyboardDragStartOffsetX + (canvasX - storyboardDragStartX) / Math.max(1e-9, scaleX);
    double nextOffsetY = storyboardDragStartOffsetY + (canvasY - storyboardDragStartY) / Math.max(1e-9, scaleY);
    if (Math.abs(nextOffsetX - storyboardOverlay.offsetX()) < 0.01
        && Math.abs(nextOffsetY - storyboardOverlay.offsetY()) < 0.01) {
      return;
    }
    storyboardOffsetDragMoved = true;
    storyboardOverlay = storyboardWithOffset(nextOffsetX, nextOffsetY);
    if (onStoryboardStateAdjusted != null) onStoryboardStateAdjusted.accept(storyboardOverlay);
  }

  private StoryboardOverlayState storyboardWithOffset(double offsetX, double offsetY) {
    return new StoryboardOverlayState(
        true,
        storyboardOverlay.image(),
        storyboardOverlay.opacity(),
        storyboardOverlay.sourcePath(),
        storyboardOverlay.hideUi(),
        storyboardOverlay.fitMode(),
        storyboardOverlay.runtimeWidth(),
        storyboardOverlay.runtimeHeight(),
        storyboardOverlay.storyboardWidth(),
        storyboardOverlay.storyboardHeight(),
        storyboardOverlay.scale(),
        offsetX,
        offsetY,
        storyboardOverlay.cropEnabled(),
        storyboardOverlay.cropX(),
        storyboardOverlay.cropY(),
        storyboardOverlay.cropWidth(),
        storyboardOverlay.cropHeight());
  }

  private double screenToPreviewX(double canvasX) {
    PreviewSurface surface = previewSurface(canvas.getWidth(), canvas.getHeight());
    return canvasX - surface.x;
  }

  private double screenToPreviewY(double canvasY) {
    PreviewSurface surface = previewSurface(canvas.getWidth(), canvas.getHeight());
    return canvasY - surface.y;
  }

  private static int mapButton(MouseButton b) {
    if (b == MouseButton.PRIMARY) return 1;
    if (b == MouseButton.MIDDLE) return 2;
    if (b == MouseButton.SECONDARY) return 3;
    return 0;
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return Math.max(1.0, Math.min(8192.0, value));
  }

  private static class Rect { double x,y,w,h; Rect(double x,double y,double w,double h){this.x=x;this.y=y;this.w=w;this.h=h;} }
  private record PreviewSurface(double x, double y, double width, double height) {}

  private Rect computeSceneBounds() {
    if (scene == null) return null;
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Entity2D e : ((Scene2DBase)scene).getChildren()) {
      if (!e.isVisible()) continue;
      if (e instanceof Panel2D p) {
        minX = Math.min(minX, e.getX());
        minY = Math.min(minY, e.getY());
        maxX = Math.max(maxX, e.getX() + p.getWidth());
        maxY = Math.max(maxY, e.getY() + p.getHeight());
      } else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
        double sx = e.getX() - s.getOriginX() * s.getWidth();
        double sy = e.getY() - s.getOriginY() * s.getHeight();
        minX = Math.min(minX, sx);
        minY = Math.min(minY, sy);
        maxX = Math.max(maxX, sx + s.getWidth());
        maxY = Math.max(maxY, sy + s.getHeight());
      } else if (e instanceof PhysicsBodyEntity2D pb) {
        var b = pb.getBody(); if (b != null) {
          if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
            double cx = b.getCircle().x, cy = b.getCircle().y, r = b.getCircle().r;
            minX = Math.min(minX, cx - r); minY = Math.min(minY, cy - r);
            maxX = Math.max(maxX, cx + r); maxY = Math.max(maxY, cy + r);
          } else {
            var a = b.getAabb();
            minX = Math.min(minX, a.x); minY = Math.min(minY, a.y);
            maxX = Math.max(maxX, a.x + a.w); maxY = Math.max(maxY, a.y + a.h);
          }
        }
      } else if (e instanceof Label2D) {
        minX = Math.min(minX, e.getX() - 50);
        minY = Math.min(minY, e.getY() - 15);
        maxX = Math.max(maxX, e.getX() + 50);
        maxY = Math.max(maxY, e.getY());
      }
    }
    if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) return null;
    return new Rect(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
  }
}

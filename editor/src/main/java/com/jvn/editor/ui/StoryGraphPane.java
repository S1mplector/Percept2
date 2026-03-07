package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.util.*;
import java.util.function.Consumer;

/**
 * Lightweight graph view for StoryTimelineView. Nodes are draggable arc boxes and links are arrows.
 */
public class StoryGraphPane extends Pane {
  public static class NodeView extends Group {
    final StoryTimelineView.Arc arc;
    final Rectangle rect;
    final Text label;
    final Circle inHandle;
    final Circle outHandle;
    double dragDX, dragDY;
    java.util.function.Consumer<javafx.scene.input.MouseEvent> mousePressedHook;
    java.util.function.Consumer<javafx.scene.input.MouseEvent> mouseReleasedHook;
    NodeView(StoryTimelineView.Arc arc, Color accent) {
      this.arc = arc;
      Color fill = accent == null ? Color.web("#2b2f3a") : Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.22);
      this.rect = new Rectangle(140, 44, fill);
      rect.setArcWidth(8); rect.setArcHeight(8);
      rect.setStroke(accent == null ? Color.web("#475069") : accent.interpolate(Color.WHITE, 0.35));
      rect.setStrokeWidth(1.0);
      this.label = new Text(arc.name == null ? "(unnamed)" : arc.name);
      label.setFill(Color.web("#e6e9f0"));
      label.setTranslateX(10); label.setTranslateY(26);
      // Link handles (always visible)
      this.inHandle = new Circle(6, Color.web("#4a5568"));
      this.outHandle = new Circle(6, Color.web("#4a5568"));
      inHandle.setStroke(Color.web("#94a3b8")); inHandle.setStrokeWidth(1.0);
      outHandle.setStroke(Color.web("#94a3b8")); outHandle.setStrokeWidth(1.0);
      inHandle.setCenterX(4); inHandle.setCenterY(rect.getHeight()/2);
      outHandle.setCenterX(rect.getWidth()-4); outHandle.setCenterY(rect.getHeight()/2);
      inHandle.setCursor(Cursor.CROSSHAIR);
      outHandle.setCursor(Cursor.CROSSHAIR);
      getChildren().addAll(rect, label, inHandle, outHandle);
      setCursor(Cursor.HAND);
      setLayoutX(arc.x);
      setLayoutY(arc.y);
      enableDrag();
      this.setPickOnBounds(false);
      inHandle.setOnMouseEntered(e -> {
        inHandle.setScaleX(1.25); inHandle.setScaleY(1.25);
        inHandle.setFill(Color.web("#64748b"));
      });
      inHandle.setOnMouseExited(e -> {
        inHandle.setScaleX(1.0); inHandle.setScaleY(1.0);
        inHandle.setFill(Color.web("#4a5568"));
      });
      outHandle.setOnMouseEntered(e -> {
        outHandle.setScaleX(1.25); outHandle.setScaleY(1.25);
        outHandle.setFill(Color.web("#64748b"));
      });
      outHandle.setOnMouseExited(e -> {
        outHandle.setScaleX(1.0); outHandle.setScaleY(1.0);
        outHandle.setFill(Color.web("#4a5568"));
      });
    }
    private void enableDrag() {
      setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        dragDX = p.getX() - getLayoutX();
        dragDY = p.getY() - getLayoutY();
        if (e.getTarget() == outHandle && mousePressedHook != null) {
          mousePressedHook.accept(e);
        }
      });
      setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (e.getTarget() == outHandle) return; // don't drag node while starting a link
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = p.getX() - dragDX;
        double ny = p.getY() - dragDY;
        setLayoutX(nx); setLayoutY(ny);
        arc.x = nx; arc.y = ny;
        if (onMoved != null) onMoved.run();
      });
      setOnMouseReleased(e -> { if (mouseReleasedHook != null) mouseReleasedHook.accept(e); });
      inHandle.setOnMouseReleased(e -> {
        if (mouseReleasedHook != null) mouseReleasedHook.accept(e);
        e.consume();
      });
    }
    Runnable onMoved;
  }

  private final Map<String, NodeView> nodeMap = new HashMap<>();
  private final List<Group> linkViews = new ArrayList<>();
  private final List<Group> clusterViews = new ArrayList<>();
  private final Map<String, Color> clusterColorCache = new HashMap<>();
  private final Set<String> collapsedClusters = new HashSet<>();
  private List<StoryTimelineView.Arc> arcs = new ArrayList<>();
  private List<StoryTimelineView.Link> links = new ArrayList<>();
  private NodeView linkingFrom;
  private Line tempLine;
  private boolean requireShiftToLink = true;
  private String filterCluster;
  private static boolean HANDLE_TIP_SHOWN = false;

  private Consumer<StoryTimelineView.Arc> onRunArc;
  private Consumer<StoryTimelineView.Link> onRunLink;
  private Runnable onGraphChanged;
  private Runnable onLayoutCommitted;
  private Consumer<StoryTimelineView.Arc> onDeleteArc;

  public StoryGraphPane() {
    setPadding(new Insets(8));
    setPrefSize(1200, 800);
    setMinSize(600, 400);
    setOnMouseMoved(e -> {
      if (tempLine != null) {
        Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
        tempLine.setEndX(p.getX());
        tempLine.setEndY(p.getY());
      }
    });
    setOnMouseReleased(e -> cancelLinking());
  }

  public void setOnRunArc(Consumer<StoryTimelineView.Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<StoryTimelineView.Link> c) { this.onRunLink = c; }
  public void setOnGraphChanged(Runnable r) { this.onGraphChanged = r; }
  public void setOnLayoutCommitted(Runnable r) { this.onLayoutCommitted = r; }
  public void setOnDeleteArc(Consumer<StoryTimelineView.Arc> c) { this.onDeleteArc = c; }
  public void setSimpleLinkMode(boolean enabled) { this.requireShiftToLink = !enabled; }

  public void setModel(List<StoryTimelineView.Arc> arcs, List<StoryTimelineView.Link> links) {
    this.arcs = (arcs == null) ? new ArrayList<>() : arcs;
    this.links = (links == null) ? new ArrayList<>() : links;
    refresh();
  }

  public Set<String> getClusterNames() {
    Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (StoryTimelineView.Arc a : arcs) {
      if (a != null && a.cluster != null && !a.cluster.isBlank()) out.add(a.cluster);
    }
    return out;
  }

  public void setFilterCluster(String cluster) {
    this.filterCluster = (cluster == null || cluster.isBlank()) ? null : cluster;
    refresh();
  }

  public String getFilterCluster() { return filterCluster; }

  public void toggleClusterCollapse(String cluster) {
    if (cluster == null || cluster.isBlank()) return;
    if (collapsedClusters.contains(cluster)) collapsedClusters.remove(cluster); else collapsedClusters.add(cluster);
    refresh();
  }

  public void autoLayout() {
    if (arcs == null || arcs.isEmpty()) return;
    int cols = Math.max(1, (int) Math.ceil(Math.sqrt(arcs.size())));
    double gapX = 220, gapY = 140;
    for (int i = 0; i < arcs.size(); i++) {
      int r = i / cols; int c = i % cols;
      StoryTimelineView.Arc a = arcs.get(i);
      a.x = 40 + c * gapX; a.y = 40 + r * gapY;
    }
    refresh();
  }

  public void highlight(String term) {
    String t = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    for (NodeView nv : nodeMap.values()) {
      boolean hit = !t.isEmpty() && nv.arc.name != null && nv.arc.name.toLowerCase(Locale.ROOT).contains(t);
      nv.rect.setStroke(hit ? Color.web("#66d9ef") : Color.web("#475069"));
      nv.rect.setStrokeWidth(hit ? 2.0 : 1.0);
    }
  }

  private void refresh() {
    getChildren().clear();
    nodeMap.clear(); linkViews.clear(); clusterViews.clear();

    drawClusters();

    // Build nodes
    for (StoryTimelineView.Arc a : arcs) {
      if (Double.isNaN(a.x)) a.x = 40; if (Double.isNaN(a.y)) a.y = 40;
      if (filterCluster != null) {
        String c = (a.cluster == null) ? "" : a.cluster;
        if (!c.equals(filterCluster)) continue;
      }
      if (a.cluster != null && collapsedClusters.contains(a.cluster)) continue;
      NodeView nv = new NodeView(a, parseArcColor(a.color));
      nv.label.setText(nodeTitle(a));
      nv.onMoved = this::updateLinks;
      Tooltip.install(nv, new Tooltip(nodeTooltip(a)));
      nv.mousePressedHook = e -> {
        if (e.getButton() == MouseButton.PRIMARY) {
          if (!requireShiftToLink || e.isShiftDown()) {
            startLinking(nv, e.getSceneX(), e.getSceneY());
          }
        }
      };
      nv.mouseReleasedHook = e -> { if (linkingFrom != null) finishLinking(nv); };
      ContextMenu cm = new ContextMenu();
      MenuItem miOpen = new MenuItem("Open");
      miOpen.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(a); });
      MenuItem miRun = new MenuItem("Run from Entry");
      miRun.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(a); });
      MenuItem miCopyGoto = new MenuItem("Copy Goto (entry)");
      miCopyGoto.setOnAction(e -> {
        String label = a.entryLabel == null ? "" : a.entryLabel;
        String snip = "[goto " + a.name + ":" + label + "]";
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(snip);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
      });
      MenuItem miCluster = new MenuItem("Set Cluster...");
      miCluster.setOnAction(e -> {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(a.cluster == null ? "" : a.cluster);
        EditorTheme.apply(dlg);
        dlg.setHeaderText(null); dlg.setTitle("Cluster"); dlg.setContentText("Cluster name:");
        var r = dlg.showAndWait();
        if (r.isPresent()) {
          a.cluster = r.get().trim();
          refresh();
          if (onGraphChanged != null) onGraphChanged.run();
        }
      });
      MenuItem miDelete = new MenuItem("Delete Arc");
      miDelete.setOnAction(e -> { if (onDeleteArc != null) onDeleteArc.accept(a); if (onGraphChanged != null) onGraphChanged.run(); });
      cm.getItems().addAll(miOpen, miRun, miCopyGoto, miCluster, miDelete);
      nv.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          cm.show(nv, e.getScreenX(), e.getScreenY());
        } else {
          cm.hide();
          if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            // Rename arc on double click
            String old = nv.arc.name;
            javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(old == null ? "" : old);
            EditorTheme.apply(dlg);
            dlg.setHeaderText(null); dlg.setTitle("Rename Arc"); dlg.setContentText("Arc name:");
            var res = dlg.showAndWait();
            if (res.isPresent()) {
              String nn = res.get().trim();
              if (!nn.isEmpty() && !nn.equals(old)) {
                nv.arc.name = nn;
                nv.label.setText(nn);
                updateLinkArcNames(old, nn);
                refresh();
                if (onGraphChanged != null) onGraphChanged.run();
              }
            }
          }
        }
      });
      nodeMap.put(a.name, nv);
      getChildren().add(nv);
    }

    // Build links under nodes (lines first, then arrow head)
    for (StoryTimelineView.Link l : links) {
      NodeView from = nodeMap.get(l.fromArc);
      NodeView to = nodeMap.get(l.toArc);
      if (from == null || to == null) continue;
      Group g = drawArrow(from, to, l);
      ContextMenu cm = new ContextMenu();
      MenuItem miRun = new MenuItem("Run Link");
      miRun.setOnAction(e -> { if (onRunLink != null) onRunLink.accept(l); });
      MenuItem miDelete = new MenuItem("Delete Link");
      miDelete.setOnAction(e -> {
        links.remove(l);
        refresh();
        if (onGraphChanged != null) onGraphChanged.run();
      });
      cm.getItems().addAll(miRun, miDelete);
      g.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY) {
          if (onRunLink != null) onRunLink.accept(l);
        } else if (e.getButton() == MouseButton.SECONDARY) {
          cm.show(g, e.getScreenX(), e.getScreenY());
        }
      });
      linkViews.add(g);
      getChildren().add(clusterViews.size(), g);
    }
  }

  private void drawClusters() {
    Map<String, double[]> bounds = new HashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    for (StoryTimelineView.Arc a : arcs) {
      if (a == null || a.cluster == null || a.cluster.isBlank()) continue;
      if (filterCluster != null && !a.cluster.equals(filterCluster)) continue;
      double x1 = a.x, y1 = a.y, x2 = a.x + 140, y2 = a.y + 44;
      double[] b = bounds.get(a.cluster);
      if (b == null) { b = new double[]{x1, y1, x2, y2}; bounds.put(a.cluster, b); counts.put(a.cluster, 1); }
      else { b[0] = Math.min(b[0], x1); b[1] = Math.min(b[1], y1); b[2] = Math.max(b[2], x2); b[3] = Math.max(b[3], y2); counts.put(a.cluster, counts.getOrDefault(a.cluster,0)+1); }
    }
    for (Map.Entry<String, double[]> e : bounds.entrySet()) {
      String name = e.getKey(); double[] b = e.getValue();
      double pad = 16;
      Rectangle bg = new Rectangle(b[0]-pad, b[1]-pad, (b[2]-b[0])+2*pad, (b[3]-b[1])+2*pad);
      bg.setArcWidth(12); bg.setArcHeight(12);
      Color base = colorForCluster(name);
      bg.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.25));
      bg.setStroke(base.interpolate(Color.WHITE, 0.2)); bg.setStrokeWidth(1.0);
      Text t = new Text(name + (collapsedClusters.contains(name) ? " (" + counts.getOrDefault(name,0) + ")" : ""));
      t.setFill(base.interpolate(Color.WHITE, 0.7));
      t.setX(bg.getX()+8); t.setY(bg.getY()-6);
      Group g = new Group(bg, t);
      ContextMenu cm = new ContextMenu();
      MenuItem miToggle = new MenuItem(collapsedClusters.contains(name) ? "Expand Cluster" : "Collapse Cluster");
      miToggle.setOnAction(ae -> { toggleClusterCollapse(name); });
      cm.getItems().addAll(miToggle);
      g.setOnMouseClicked(me -> {
        if (me.getButton() == MouseButton.SECONDARY) {
          cm.show(g, me.getScreenX(), me.getScreenY());
        } else if (me.getButton() == MouseButton.PRIMARY && me.getClickCount() == 2) {
          toggleClusterCollapse(name);
        }
      });
      clusterViews.add(g);
      getChildren().add(g);
    }
  }

  private Group drawArrow(NodeView from, NodeView to, StoryTimelineView.Link link) {
    double sx = from.getLayoutX() + from.outHandle.getCenterX();
    double sy = from.getLayoutY() + from.outHandle.getCenterY();
    double ex = to.getLayoutX() + to.inHandle.getCenterX();
    double ey = to.getLayoutY() + to.inHandle.getCenterY();

    Line line = new Line(sx, sy, ex, ey);
    line.setStroke(Color.web("#7a8499"));

    Polygon arrow = new Polygon();
    double angle = Math.atan2(ey - sy, ex - sx);
    double len = 12;
    double aw = 6;
    double x1 = ex - len * Math.cos(angle) + aw * Math.sin(angle);
    double y1 = ey - len * Math.sin(angle) - aw * Math.cos(angle);
    double x2 = ex - len * Math.cos(angle) - aw * Math.sin(angle);
    double y2 = ey - len * Math.sin(angle) + aw * Math.cos(angle);
    arrow.getPoints().addAll(ex, ey, x1, y1, x2, y2);
    arrow.setFill(Color.web("#7a8499"));

    Group g = new Group(line, arrow);
    String hint = linkHint(link);
    if (!hint.isBlank()) {
      Text txt = new Text(hint);
      txt.setFill(Color.web("#c5cede"));
      txt.setX((sx + ex) * 0.5 + 6);
      txt.setY((sy + ey) * 0.5 - 6);
      g.getChildren().add(txt);
    }
    return g;
  }

  private void updateLinks() {
    // Rebuild links for simplicity
    setModel(arcs, links);
  }

  private void startLinking(NodeView from, double sceneX, double sceneY) {
    linkingFrom = from;
    tempLine = new Line();
    tempLine.setStroke(Color.web("#9fb3ff"));
    tempLine.getStrokeDashArray().setAll(8.0, 8.0);
    double sx = from.getLayoutX() + from.outHandle.getCenterX();
    double sy = from.getLayoutY() + from.outHandle.getCenterY();
    tempLine.setStartX(sx); tempLine.setStartY(sy);
    Point2D p = sceneToLocal(sceneX, sceneY);
    tempLine.setEndX(p.getX()); tempLine.setEndY(p.getY());
    getChildren().add(0, tempLine);
    // Highlight all in-handles as valid drop targets
    nodeMap.values().forEach(n -> {
      if (n != from) {
        n.inHandle.setFill(Color.web("#60a5fa"));
        n.inHandle.setScaleX(1.15); n.inHandle.setScaleY(1.15);
      }
    });
    if (!HANDLE_TIP_SHOWN) {
      try {
        HANDLE_TIP_SHOWN = true;
        Tooltip tip = new Tooltip("Drag from the out handle to link arcs\nDrop on the in handle to connect");
        javafx.geometry.Bounds scr = from.outHandle.localToScreen(from.outHandle.getBoundsInLocal());
        if (scr != null) {
          tip.show(from.outHandle, scr.getMinX(), scr.getMaxY() + 6);
          new Thread(() -> {
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(tip::hide);
          }).start();
        }
      } catch (Exception ignore) {}
    }
  }

  private void cancelLinking() {
    if (tempLine != null) getChildren().remove(tempLine);
    tempLine = null; linkingFrom = null;
    nodeMap.values().forEach(n -> {
      n.inHandle.setFill(Color.web("#4a5568"));
      n.inHandle.setScaleX(1.0); n.inHandle.setScaleY(1.0);
    });
  }

  private void finishLinking(NodeView target) {
    if (linkingFrom == null || target == null || linkingFrom == target) { cancelLinking(); return; }
    javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("");
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Link Label (optional)"); dlg.setContentText("To Label:");
    var res = dlg.showAndWait(); String toLabel = res.isPresent() ? res.get().trim() : "";
    StoryTimelineView.Link l = new StoryTimelineView.Link();
    l.fromArc = linkingFrom.arc.name; l.fromLabel = ""; l.toArc = target.arc.name; l.toLabel = toLabel;
    links.add(l);
    cancelLinking();
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void updateLinkArcNames(String oldName, String newName) {
    if (oldName == null || newName == null) return;
    for (StoryTimelineView.Link l : links) {
      if (oldName.equals(l.fromArc)) l.fromArc = newName;
      if (oldName.equals(l.toArc)) l.toArc = newName;
    }
  }

  { // initializer to add release hook to notify layout commit for all nodes created later via refresh
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    // Attach release hook to existing NodeViews to commit layout changes
    for (NodeView nv : nodeMap.values()) {
      if (nv != null) {
        nv.mouseReleasedHook = e -> {
          if (linkingFrom != null) {
            finishLinking(nv);
          } else {
            double step = 20.0;
            double nx = Math.round(nv.getLayoutX()/step)*step;
            double ny = Math.round(nv.getLayoutY()/step)*step;
            nv.setLayoutX(nx); nv.setLayoutY(ny);
            nv.arc.x = nx; nv.arc.y = ny;
            if (nv.onMoved != null) nv.onMoved.run();
          }
          if (onLayoutCommitted != null) onLayoutCommitted.run();
        };
      }
    }
  }

  private static String nodeTitle(StoryTimelineView.Arc arc) {
    if (arc == null) return "(unnamed)";
    String name = arc.name == null || arc.name.isBlank() ? "(unnamed)" : arc.name;
    return arc.priority == 0 ? name : name + "  p" + arc.priority;
  }

  private static String nodeTooltip(StoryTimelineView.Arc arc) {
    if (arc == null) return "";
    StringBuilder sb = new StringBuilder();
    sb.append(nodeTitle(arc));
    if (arc.script != null && !arc.script.isBlank()) sb.append("\nScript: ").append(arc.script);
    if (arc.entryLabel != null && !arc.entryLabel.isBlank()) sb.append("\nEntry: ").append(arc.entryLabel);
    if (arc.cluster != null && !arc.cluster.isBlank()) sb.append("\nCluster: ").append(arc.cluster);
    if (arc.tags != null && !arc.tags.isBlank()) sb.append("\nTags: ").append(arc.tags);
    if (arc.color != null && !arc.color.isBlank()) sb.append("\nColor: ").append(arc.color);
    return sb.toString();
  }

  private static String linkHint(StoryTimelineView.Link link) {
    if (link == null) return "";
    String from = link.fromLabel == null ? "" : link.fromLabel.trim();
    String to = link.toLabel == null ? "" : link.toLabel.trim();
    if (from.isBlank() && to.isBlank()) return "";
    if (from.isBlank()) return "-> " + to;
    if (to.isBlank()) return from + " ->";
    return from + " -> " + to;
  }

  private static Color parseArcColor(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private Color colorForCluster(String name) {
    if (name == null) return Color.web("#334155");
    Color c = clusterColorCache.get(name);
    if (c != null) return c;
    int h = name.hashCode();
    double hue = (h & 0xffff) / 65535.0 * 360.0;
    double s = 0.45;
    double l = 0.45;
    c = hsl(hue, s, l);
    clusterColorCache.put(name, c);
    return c;
  }

  private static Color hsl(double h, double s, double l) {
    h = (h % 360 + 360) % 360; s = Math.max(0, Math.min(1, s)); l = Math.max(0, Math.min(1, l));
    double c = (1 - Math.abs(2*l - 1)) * s;
    double x = c * (1 - Math.abs((h/60.0) % 2 - 1));
    double m = l - c/2;
    double r=0,g=0,b=0;
    if (h < 60) { r=c; g=x; b=0; }
    else if (h < 120) { r=x; g=c; b=0; }
    else if (h < 180) { r=0; g=c; b=x; }
    else if (h < 240) { r=0; g=x; b=c; }
    else if (h < 300) { r=x; g=0; b=c; }
    else { r=c; g=0; b=x; }
    return new Color(r+m, g+m, b+m, 1.0);
  }
}

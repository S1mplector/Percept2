package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Slider;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.stage.FileChooser;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Dragboard;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class StoryTimelineView extends BorderPane {
  public static class Arc {
    public String name;
    public String script;
    public String entryLabel;
    public String cluster;
    public double x = 40;
    public double y = 40;
    public String toLine() { return "ARC|" + nn(name) + "|" + nn(script) + "|" + nn(entryLabel) + "|" + x + "|" + y; }
  }

  private void validate() {
    StringBuilder sb = new StringBuilder();
    // Check arcs
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      File f = resolveFile(a.script);
      if (f == null || !f.exists()) {
        sb.append("Missing script for arc '").append(a.name).append("': ").append(a.script).append("\n");
        continue;
      }
      if (a.entryLabel != null && !a.entryLabel.isBlank()) {
        boolean ok = hasLabel(f, a.entryLabel);
        if (!ok) sb.append("Arc '").append(a.name).append("' missing entry label '").append(a.entryLabel).append("'\n");
      }
    }
    // Check links
    for (Link l : links.getItems()) {
      if (l == null) continue;
      Arc ta = findArc(l.toArc);
      if (ta == null) { sb.append("Link to unknown arc: ").append(l.toArc).append("\n"); continue; }
      File f = resolveFile(ta.script);
      if (f == null || !f.exists()) { sb.append("Link target arc script missing: ").append(ta.script).append("\n"); continue; }
      String lab = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
      if (lab != null && !lab.isBlank()) {
        boolean ok = hasLabel(f, lab);
        if (!ok) sb.append("Link target label missing: ").append(l.toArc).append(":").append(lab).append("\n");
      }
    }
    if (sb.length() == 0) {
      Alert a = new Alert(Alert.AlertType.INFORMATION, "Timeline OK"); EditorTheme.apply(a); a.setHeaderText(null); a.setTitle("Validate"); a.showAndWait();
    } else {
      TextArea ta = new TextArea(sb.toString()); ta.setEditable(false); ta.setWrapText(true);
      Dialog<Void> dlg = new Dialog<>(); EditorTheme.apply(dlg); dlg.setTitle("Validation Issues"); dlg.getDialogPane().setContent(ta); dlg.getDialogPane().getButtonTypes().add(ButtonType.OK); dlg.showAndWait();
    }
  }

  private boolean hasLabel(File vnsFile, String label) {
    try (FileInputStream in = new FileInputStream(vnsFile)) {
      com.jvn.core.vn.script.VnScriptParser p = new com.jvn.core.vn.script.VnScriptParser();
      com.jvn.core.vn.VnScenario sc = p.parse(in);
      return sc.getLabelIndex(label) != null;
    } catch (Exception e) {
      return false;
    }
  }
  public static class Link {
    public String fromArc;
    public String fromLabel;
    public String toArc;
    public String toLabel;
    public String toLine() { return "LINK|" + nn(fromArc) + "|" + nn(fromLabel) + "|" + nn(toArc) + "|" + nn(toLabel); }
  }

  private final ListView<Arc> arcs = new ListView<>();
  private final ListView<Link> links = new ListView<>();
  private final StoryGraphPane graph = new StoryGraphPane();
  private final ScrollPane graphScroll = new ScrollPane(graph);
  private Slider zoomSlider;
  private ComboBox<String> clusterFilter;
  private File projectRoot;
  private File timelineFile;
  private Consumer<Arc> onRunArc;
  private Consumer<Link> onRunLink;
  private Runnable onChanged;

  public StoryTimelineView() {
    arcs.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc a, boolean empty) {
        super.updateItem(a, empty);
        setText(empty || a == null ? null : a.name + "  [" + a.script + (a.entryLabel == null || a.entryLabel.isBlank() ? "" : (" :: " + a.entryLabel)) + "]");
      }
    });
    links.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Link l, boolean empty) {
        super.updateItem(l, empty);
        setText(empty || l == null ? null : l.fromArc + ":" + nn(l.fromLabel) + "  ->  " + l.toArc + ":" + nn(l.toLabel));
      }
    });

    // Graph on top, lists below
    SplitPane lists = new SplitPane();
    lists.setOrientation(javafx.geometry.Orientation.VERTICAL);
    lists.getItems().addAll(new TitledPane("Arcs", arcs), new TitledPane("Links", links));
    lists.setDividerPositions(0.5);
    graphScroll.setFitToWidth(true); graphScroll.setFitToHeight(true);
    graphScroll.setPannable(true);
    SplitPane rootSplit = new SplitPane();
    rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
    rootSplit.getItems().addAll(graphScroll, lists);
    rootSplit.setDividerPositions(0.55);
    setCenter(rootSplit);

    Button bAddArc = new Button("Add Arc"); bAddArc.setOnAction(e -> addArc());
    Button bAuto = new Button("Auto Layout"); bAuto.setOnAction(e -> { graph.autoLayout(); save(); });
    Button bFit = new Button("Fit"); bFit.setOnAction(e -> zoomToFit());
    Button bValidate = new Button("Validate"); bValidate.setOnAction(e -> validate());
    TextField tfSearch = new TextField(); tfSearch.setPromptText("Search arcs...");
    tfSearch.textProperty().addListener((o, ov, nv) -> graph.highlight(nv));
    zoomSlider = new Slider(0.6, 2.0, 1.0); zoomSlider.setPrefWidth(120);
    zoomSlider.valueProperty().addListener((o, ov, nv) -> { double s = nv.doubleValue(); graph.setScaleX(s); graph.setScaleY(s); });
    FlowPane actions = new FlowPane(6, 6);
    actions.setPadding(new Insets(6));
    clusterFilter = new ComboBox<>();
    clusterFilter.setPrefWidth(160);
    clusterFilter.setPromptText("Cluster: All");
    clusterFilter.valueProperty().addListener((o,ov,nv) -> {
      if (nv == null || nv.equals("All")) graph.setFilterCluster(null); else graph.setFilterCluster(nv);
    });
    actions.getChildren().addAll(bAddArc, tfSearch, clusterFilter, bAuto, bFit, bValidate);
    // Wrap to new rows instead of squeezing buttons to tiny squares
    actions.prefWrapLengthProperty().bind(widthProperty().subtract(24));
    setTop(actions);

    // Graph actions wiring
    graph.setOnRunArc(a -> { if (onRunArc != null) onRunArc.accept(a); });
    graph.setOnRunLink(l -> { if (onRunLink != null) onRunLink.accept(l); });
    graph.setOnGraphChanged(this::onGraphChanged);
    graph.setOnLayoutCommitted(this::save);
    graph.setOnDeleteArc(a -> { if (a != null) { removeArcAndLinks(a.name); refreshGraph(); save(); } });
    graph.setSimpleLinkMode(true);
    arcs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null) graph.highlight(nv.name);
    });

    // Wheel zoom with Ctrl/Cmd
    graphScroll.addEventFilter(ScrollEvent.SCROLL, e -> {
      if (e.isControlDown() || e.isShortcutDown()) {
        double v = zoomSlider.getValue();
        double step = (e.getDeltaY() > 0) ? 0.1 : -0.1;
        v = Math.max(zoomSlider.getMin(), Math.min(zoomSlider.getMax(), v + step));
        zoomSlider.setValue(v);
        e.consume();
      }
    });

    // Drag-and-drop .vns files onto graph to create arcs
    graph.setOnDragOver(e -> {
      Dragboard db = e.getDragboard();
      if (db.hasFiles()) {
        boolean ok = db.getFiles().stream().anyMatch(f -> f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".vns"));
        if (ok) { e.acceptTransferModes(TransferMode.COPY); e.consume(); }
      }
    });
    graph.setOnDragDropped(e -> {
      Dragboard db = e.getDragboard();
      boolean success = false;
      if (db.hasFiles()) {
        for (File f : db.getFiles()) {
          String name = f.getName().toLowerCase(java.util.Locale.ROOT);
          if (!name.endsWith(".vns")) continue;
          addArcFromFile(f);
          success = true;
        }
      }
      e.setDropCompleted(success); e.consume();
    });
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
    refreshGraph();
  }

  public void setOnRunArc(Consumer<Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<Link> c) { this.onRunLink = c; }
  public void setOnChanged(Runnable r) { this.onChanged = r; }
  public void setTimelineFile(File f) { this.timelineFile = f; load(); refreshGraph(); }
  public List<Arc> getArcs() { return new ArrayList<>(arcs.getItems()); }
  public List<Link> getLinks() { return new ArrayList<>(links.getItems()); }
  public Arc findArc(String name) {
    for (Arc a : arcs.getItems()) if (a != null && name != null && name.equals(a.name)) return a; return null;
  }

  private void addArc() {
    TextInputDialog dlg = new TextInputDialog("Arc");
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Arc Name"); dlg.setContentText("Name:");
    var res = dlg.showAndWait(); if (res.isEmpty()) return; String name = res.get().trim(); if (name.isEmpty()) return;
    FileChooser fc = new FileChooser();
    fc.setTitle("Select VNS Script");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
    File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (f == null) return;
    TextInputDialog ldlg = new TextInputDialog("");
    EditorTheme.apply(ldlg);
    ldlg.setHeaderText(null); ldlg.setTitle("Entry Label"); ldlg.setContentText("Label (optional):");
    var lres = ldlg.showAndWait(); String label = lres.isEmpty() ? "" : lres.get().trim();
    Arc a = new Arc(); a.name = name; a.script = toRelative(f); a.entryLabel = label;
    arcs.getItems().add(a);
    refreshGraph();
  }

  private void addLink() {
    if (arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>(); fromArc.getItems().setAll(arcs.getItems());
    TextField fromLabel = new TextField();
    ComboBox<Arc> toArc = new ComboBox<>(); toArc.getItems().setAll(arcs.getItems());
    TextField toLabel = new TextField();
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    Dialog<ButtonType> dlg = new Dialog<>(); EditorTheme.apply(dlg); dlg.setTitle("Add Link"); dlg.getDialogPane().setContent(g); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var res = dlg.showAndWait(); if (res.isEmpty() || res.get() != ButtonType.OK) return;
    Arc fa = fromArc.getValue(); Arc ta = toArc.getValue(); if (fa == null || ta == null) return;
    Link l = new Link(); l.fromArc = fa.name; l.fromLabel = fromLabel.getText(); l.toArc = ta.name; l.toLabel = toLabel.getText();
    links.getItems().add(l);
    refreshGraph();
  }

  private void openArc() {
    Arc a = arcs.getSelectionModel().getSelectedItem(); if (a == null) return;
    try { java.awt.Desktop.getDesktop().open(resolveFile(a.script)); } catch (Exception ignored) {}
  }

  private void copyGoto() {
    Link l = links.getSelectionModel().getSelectedItem(); if (l == null) return;
    String snip = "[goto " + l.toArc + ":" + nn(l.toLabel) + "]";
    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
    cc.putString(snip);
    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
  }

  private void save() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
      pw.print(toDsl());
    } catch (Exception ignored) {}
  }

  private void load() {
    File f = timelineFile != null ? timelineFile : defaultTimelineFile();
    if (f == null) return;
    if (!f.exists()) return;
    try {
      String text = java.nio.file.Files.readString(f.toPath());
      fromText(text);
    } catch (Exception ignored) {}
  }

  private File defaultTimelineFile() {
    if (projectRoot == null) return null;
    File modern = new File(projectRoot, "config/timeline/story.timeline");
    if (modern.exists()) return modern;
    File legacyStoryDir = new File(projectRoot, "story/story.timeline");
    if (legacyStoryDir.exists()) return legacyStoryDir;
    File legacyRoot = new File(projectRoot, "story.timeline");
    if (legacyRoot.exists()) return legacyRoot;
    return modern;
  }

  private static String nn(String s) { return s == null ? "" : s; }
  private static String n(String s) { return s == null ? "" : s; }

  private String toRelative(File f) {
    try {
      if (projectRoot == null) return f.getAbsolutePath();
      String root = projectRoot.getCanonicalPath();
      String abs = f.getCanonicalPath();
      if (abs.startsWith(root)) {
        String rel = abs.substring(root.length());
        if (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace('\\', '/');
      }
      return abs;
    } catch (Exception e) {
      return f.getPath();
    }
  }

  private File resolveFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void refreshGraph() {
    graph.setModel(arcs.getItems(), links.getItems());
    updateClusterFilter();
  }

  private void onGraphChanged() {
    // sync list views with graph model and save
    arcs.refresh(); links.refresh();
    save();
    updateClusterFilter();
    if (onChanged != null) onChanged.run();
  }

  private void updateClusterFilter() {
    if (clusterFilter == null) return;
    java.util.Set<String> names = graph.getClusterNames();
    String sel = clusterFilter.getValue();
    clusterFilter.getItems().setAll(new java.util.ArrayList<>());
    clusterFilter.getItems().add("All");
    for (String n : names) clusterFilter.getItems().add(n);
    if (sel != null && clusterFilter.getItems().contains(sel)) {
      clusterFilter.setValue(sel);
    } else {
      clusterFilter.setValue("All");
    }
  }

  private void removeArcAndLinks(String arcName) {
    if (arcName == null) return;
    Arc target = null;
    for (Arc a : arcs.getItems()) { if (a != null && arcName.equals(a.name)) { target = a; break; } }
    if (target != null) arcs.getItems().remove(target);
    links.getItems().removeIf(l -> arcName.equals(l.fromArc) || arcName.equals(l.toArc));
  }

  private void addArcFromFile(File f) {
    if (f == null) return;
    TextInputDialog dlg = new TextInputDialog(stripExt(f.getName()));
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Arc Name"); dlg.setContentText("Name:");
    var res = dlg.showAndWait(); if (res.isEmpty()) return; String name = res.get().trim(); if (name.isEmpty()) return;
    TextInputDialog ldlg = new TextInputDialog("");
    EditorTheme.apply(ldlg);
    ldlg.setHeaderText(null); ldlg.setTitle("Entry Label"); ldlg.setContentText("Label (optional):");
    var lres = ldlg.showAndWait(); String label = lres.isEmpty() ? "" : lres.get().trim();
    Arc a = new Arc(); a.name = name; a.script = toRelative(f); a.entryLabel = label;
    arcs.getItems().add(a);
    refreshGraph(); save();
  }

  private static String stripExt(String n) {
    if (n == null) return "Arc"; int i = n.lastIndexOf('.'); return (i>0) ? n.substring(0,i) : n;
  }

  public String toDsl() {
    StringBuilder sb = new StringBuilder();
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      sb.append("arc \"").append(nn(a.name)).append("\"");
      if (a.script != null && !a.script.isBlank()) sb.append(" script \"").append(nn(a.script)).append("\"");
      if (a.entryLabel != null && !a.entryLabel.isBlank()) sb.append(" entry \"").append(nn(a.entryLabel)).append("\"");
      if (a.cluster != null && !a.cluster.isBlank()) sb.append(" cluster \"").append(nn(a.cluster)).append("\"");
      sb.append(" at ").append(a.x).append(",").append(a.y);
      sb.append("\n");
    }
    for (Link l : links.getItems()) {
      if (l == null) continue;
      String fl = (l.fromLabel == null || l.fromLabel.isBlank()) ? nn(l.fromArc) : (nn(l.fromArc) + ":" + nn(l.fromLabel));
      String tl = (l.toLabel == null || l.toLabel.isBlank()) ? nn(l.toArc) : (nn(l.toArc) + ":" + nn(l.toLabel));
      sb.append("link ").append(fl).append(" -> ").append(tl).append("\n");
    }
    return sb.toString();
  }

  public void fromText(String text) {
    List<Arc> alist = new ArrayList<>();
    List<Link> llist = new ArrayList<>();
    if (text == null) text = "";
    String[] lines = text.split("\r?\n");
    Pattern parc = Pattern.compile("^\\s*arc\\s+(?:\"([^\"]+)\"|(\\S+))(?:\\s+script\\s+\"([^\"]+)\")?(?:\\s+entry\\s+\"([^\"]*)\")?(?:\\s+cluster\\s+\"([^\"]+)\")?(?:\\s+at\\s+(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?))?\\s*$", Pattern.CASE_INSENSITIVE);
    Pattern plink = Pattern.compile("^\\s*link\\s+([^\\s]+)\\s*->\\s*([^\\s]+)\\s*$", Pattern.CASE_INSENSITIVE);
    for (String line : lines) {
      if (line == null) continue;
      String s = line.trim();
      if (s.isEmpty() || s.startsWith("#")) continue;
      if (s.startsWith("ARC|")) {
        String[] t = s.split("\\|", -1);
        if (t.length >= 4) {
          Arc a = new Arc(); a.name = n(t[1]); a.script = n(t[2]); a.entryLabel = n(t[3]);
          if (t.length >= 6) {
            try { a.x = Double.parseDouble(t[4]); } catch (Exception ignore) {}
            try { a.y = Double.parseDouble(t[5]); } catch (Exception ignore) {}
          }
          alist.add(a);
        }
        continue;
      }
      if (s.startsWith("LINK|")) {
        String[] t = s.split("\\|", -1);
        if (t.length >= 5) { Link l = new Link(); l.fromArc = n(t[1]); l.fromLabel = n(t[2]); l.toArc = n(t[3]); l.toLabel = n(t[4]); llist.add(l); }
        continue;
      }
      Matcher ma = parc.matcher(s);
      if (ma.matches()) {
        Arc a = new Arc();
        a.name = ma.group(1) != null ? ma.group(1) : ma.group(2);
        a.script = nn(ma.group(3));
        a.entryLabel = nn(ma.group(4));
        a.cluster = nn(ma.group(5));
        if (ma.group(6) != null && ma.group(7) != null) {
          try { a.x = Double.parseDouble(ma.group(6)); } catch (Exception ignore) {}
          try { a.y = Double.parseDouble(ma.group(7)); } catch (Exception ignore) {}
        }
        alist.add(a);
        continue;
      }
      Matcher ml = plink.matcher(s);
      if (ml.matches()) {
        String left = ml.group(1);
        String right = ml.group(2);
        Link l = new Link();
        int ci = left.indexOf(':');
        if (ci >= 0) { l.fromArc = left.substring(0,ci); l.fromLabel = left.substring(ci+1); } else { l.fromArc = left; l.fromLabel = ""; }
        ci = right.indexOf(':');
        if (ci >= 0) { l.toArc = right.substring(0,ci); l.toLabel = right.substring(ci+1); } else { l.toArc = right; l.toLabel = ""; }
        llist.add(l);
      }
    }
    arcs.getItems().setAll(alist); links.getItems().setAll(llist); refreshGraph();
  }

  private void zoomToFit() {
    if (arcs.getItems().isEmpty()) return;
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      minX = Math.min(minX, a.x);
      minY = Math.min(minY, a.y);
      maxX = Math.max(maxX, a.x + 140);
      maxY = Math.max(maxY, a.y + 44);
    }
    double pad = 80;
    double widthNeeded = (maxX - minX) + pad;
    double heightNeeded = (maxY - minY) + pad;
    double vw = graphScroll.getViewportBounds().getWidth();
    double vh = graphScroll.getViewportBounds().getHeight();
    if (vw <= 0 || vh <= 0) return;
    double s = Math.min(vw / widthNeeded, vh / heightNeeded);
    s = Math.max(zoomSlider.getMin(), Math.min(zoomSlider.getMax(), s));
    zoomSlider.setValue(s);
    double contentW = Math.max(widthNeeded * s, vw);
    double contentH = Math.max(heightNeeded * s, vh);
    double centerX = (minX + maxX) / 2 * s;
    double centerY = (minY + maxY) / 2 * s;
    double hx = (centerX - vw / 2) / Math.max(1, (contentW - vw));
    double vy = (centerY - vh / 2) / Math.max(1, (contentH - vh));
    graphScroll.setHvalue(Math.max(0, Math.min(1, hx)));
    graphScroll.setVvalue(Math.max(0, Math.min(1, vy)));
  }
}

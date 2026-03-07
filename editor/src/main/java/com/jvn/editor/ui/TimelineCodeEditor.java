package com.jvn.editor.ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;

public class TimelineCodeEditor extends BorderPane {
  private final CodeArea code = new CodeArea();
  private Consumer<String> onTextChanged;
  private boolean suppressEvent;
  private File projectRoot;
  private List<Issue> issues = Collections.emptyList();

  private static final String[] KW = new String[] { "arc", "script", "entry", "at", "link", "cluster", "priority", "color", "tags" };
  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KW) + ")\\b";
  private static final String BOOL_PATTERN = "\\b(?:true|false)\\b";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String ARROW_PATTERN = "->";
  private static final String COMMENT_PATTERN = "(?m)#.*$";
  private static final String PUNCT_PATTERN = "[,:]";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
    + "|(?<STRING>" + STRING_PATTERN + ")"
    + "|(?<BOOL>" + BOOL_PATTERN + ")"
    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    + "|(?<ARROW>" + ARROW_PATTERN + ")"
    + "|(?<PUNCT>" + PUNCT_PATTERN + ")"
  );

  private static final Pattern ARC_LINE = Pattern.compile(StoryTimelineView.ARC_DSL_LINE.pattern(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  private static final Pattern LINK_LINE = Pattern.compile(StoryTimelineView.LINK_DSL_LINE.pattern(), Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  public TimelineCodeEditor() {
    code.setParagraphGraphicFactory(LineNumberFactory.get(code));
    code.textProperty().addListener((o,ov,nv) -> { issues = computeIssues(nv == null ? "" : nv); applyHighlighting(nv); if (!suppressEvent && onTextChanged != null) onTextChanged.accept(nv); });
    applyHighlighting("");
    var sp = new VirtualizedScrollPane<>(code);
    setCenter(sp);
    var css = TimelineCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) { getStylesheets().add(css.toExternalForm()); code.getStylesheets().add(css.toExternalForm()); }
    code.setOnContextMenuRequested(e -> {
      Issue is = issueAt(code.getCaretPosition());
      if (is == null) return;
      ContextMenu cm = new ContextMenu();
      if ("missing_arc".equals(is.kind)) {
        MenuItem miCreate = new MenuItem("Create arc \"" + is.arc + "\"");
        miCreate.setOnAction(ev -> createArc(is.arc));
        MenuItem miChange = new MenuItem("Change to existing...");
        miChange.setOnAction(ev -> changeArcAt(is.start, is.end));
        cm.getItems().addAll(miCreate, miChange);
      } else if ("missing_label".equals(is.kind)) {
        MenuItem miChange = new MenuItem("Change to existing label...");
        miChange.setOnAction(ev -> changeLabelAt(is));
        cm.getItems().addAll(miChange);
      }
      if (!cm.getItems().isEmpty()) {
        code.getCaretBounds().ifPresent(b -> cm.show(code, b.getMinX() + code.getScene().getWindow().getX(), b.getMaxY() + code.getScene().getWindow().getY()));
      }
    });
  }

  public void setOnTextChanged(Consumer<String> c) { this.onTextChanged = c; }
  public String getText() { return code.getText(); }
  public void setText(String s) { code.replaceText(s == null ? "" : s); }
  public void setTextNoEvent(String s) { try { suppressEvent = true; setText(s); } finally { suppressEvent = false; } }
  public void setProjectRoot(File root) { this.projectRoot = root; }

  private void applyHighlighting(String text) {
    code.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
  }

  private StyleSpans<Collection<String>> computeHighlighting(String text) {
    List<Span> spans = new ArrayList<>();
    Matcher matcher = PATTERN.matcher(text);
    int last = 0;
    while (matcher.find()) {
      String sc = matcher.group("COMMENT") != null ? "comment" :
        matcher.group("STRING") != null ? "string" :
        matcher.group("BOOL") != null ? "jes-bool" :
        matcher.group("KEYWORD") != null ? "tl-keyword" :
        matcher.group("NUMBER") != null ? "number" :
        matcher.group("ARROW") != null ? "tl-arrow" :
        matcher.group("PUNCT") != null ? "punct" : null;
      spans.add(new Span(last, matcher.start(), Collections.emptyList()));
      spans.add(new Span(matcher.start(), matcher.end(), Collections.singletonList(sc)));
      last = matcher.end();
    }
    spans.add(new Span(last, text.length(), Collections.emptyList()));
    List<Issue> iss = issues == null ? List.of() : issues;
    for (Issue is : iss) spans = overlay(spans, is.start, is.end, "error");
    StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
    for (Span s : compress(spans)) b.add(s.styles, Math.max(0, s.end - s.start));
    return b.create();
  }

  private static List<Span> overlay(List<Span> base, int s, int e, String cls) {
    if (e <= s) return base;
    List<Span> out = new ArrayList<>();
    for (Span sp : base) {
      if (sp.end <= s || sp.start >= e) { out.add(sp); continue; }
      if (sp.start < s) out.add(new Span(sp.start, s, sp.styles));
      int os = Math.max(sp.start, s);
      int oe = Math.min(sp.end, e);
      List<String> merged = new ArrayList<>(sp.styles);
      if (!merged.contains(cls)) merged.add(cls);
      out.add(new Span(os, oe, merged));
      if (sp.end > e) out.add(new Span(e, sp.end, sp.styles));
    }
    return out;
  }

  private static List<Span> compress(List<Span> in) {
    if (in.isEmpty()) return in;
    List<Span> out = new ArrayList<>();
    Span cur = in.get(0);
    for (int i=1;i<in.size();i++) {
      Span nx = in.get(i);
      if (cur.end == nx.start && cur.styles.equals(nx.styles)) cur = new Span(cur.start, nx.end, cur.styles);
      else { out.add(cur); cur = nx; }
    }
    out.add(cur);
    return out;
  }

  private static class Span {
    final int start, end; final List<String> styles;
    Span(int s, int e, List<String> st) { this.start=s; this.end=e; this.styles=st; }
  }

  private static class Issue {
    final String kind;
    final int start;
    final int end;
    final String arc;
    final String label;
    final String arcScript;
    Issue(String kind, int start, int end, String arc, String label, String arcScript) {
      this.kind = kind; this.start = start; this.end = end; this.arc = arc; this.label = label; this.arcScript = arcScript;
    }
  }

  private Issue issueAt(int caret) {
    if (issues == null) return null;
    for (Issue is : issues) if (caret >= is.start && caret <= is.end) return is; return null;
  }

  private List<Issue> computeIssues(String text) {
    try {
      Map<String, ArcInfo> arcs = new HashMap<>();
      Matcher ma = ARC_LINE.matcher(text);
      while (ma.find()) {
        String name = unescape(ma.group(1) != null ? ma.group(1) : ma.group(2));
        String script = unescape(ma.group(3));
        String entry = unescape(ma.group(4));
        int entryStart = ma.start(4);
        int entryEnd = ma.end(4);
        arcs.put(name, new ArcInfo(name, script, entry, entryStart, entryEnd));
      }
      List<Issue> out = new ArrayList<>();
      for (ArcInfo ai : arcs.values()) {
        if (ai.entry != null && !ai.entry.isBlank()) {
          File f = resolveFile(ai.script);
          if (f != null && f.exists()) {
            if (!labelExists(f, ai.entry)) {
              if (ai.entryStart >= 0 && ai.entryEnd > ai.entryStart) out.add(new Issue("missing_label", ai.entryStart, ai.entryEnd, ai.name, ai.entry, ai.script));
            }
          }
        }
      }
      Matcher ml = LINK_LINE.matcher(text);
      while (ml.find()) {
        String left = ml.group(1);
        String right = ml.group(2);
        int ls = ml.start(1), rs = ml.start(2);
        Token lt = parseToken(left);
        Token rt = parseToken(right);
        if (!lt.arc.isBlank() && !arcs.containsKey(lt.arc)) out.add(new Issue("missing_arc", ls + lt.arcStart, ls + lt.arcEnd, lt.arc, null, null));
        if (!rt.arc.isBlank() && !arcs.containsKey(rt.arc)) out.add(new Issue("missing_arc", rs + rt.arcStart, rs + rt.arcEnd, rt.arc, null, null));
        ArcInfo ta = arcs.get(rt.arc);
        if (ta != null) {
          String lab = (rt.label == null || rt.label.isBlank()) ? ta.entry : rt.label;
          if (lab != null && !lab.isBlank()) {
            File f = resolveFile(ta.script);
            if (f != null && f.exists() && !labelExists(f, lab)) {
              if (rt.labelStart >= 0 && rt.labelEnd > rt.labelStart) {
                out.add(new Issue("missing_label", rs + rt.labelStart, rs + rt.labelEnd, rt.arc, lab, ta.script));
              }
            }
          }
        }
      }
      return out;
    } catch (Exception e) { return List.of(); }
  }

  private void createArc(String name) {
    String t = getText();
    String nl = t.endsWith("\n") ? "" : "\n";
    String ins = "arc \"" + name + "\" at 40,40\n";
    setText(t + nl + ins);
  }

  private void changeArcAt(int start, int end) {
    Map<String, ArcInfo> arcs = new HashMap<>();
    Matcher ma = ARC_LINE.matcher(getText());
    while (ma.find()) {
      String nm = ma.group(1) != null ? ma.group(1) : ma.group(2);
      arcs.put(nm, new ArcInfo(nm, nn(ma.group(3)), nn(ma.group(4)), -1, -1));
    }
    List<String> names = new ArrayList<>(arcs.keySet());
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    if (names.isEmpty()) return;
    ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Choose Arc"); dlg.setContentText("Arc:");
    var r = dlg.showAndWait();
    if (r.isPresent()) code.replaceText(start, end, r.get());
  }

  private void changeLabelAt(Issue is) {
    if (is == null || is.arc == null) return;
    File f = resolveFile(is.arcScript);
    List<String> labs = listLabels(f);
    if (labs.isEmpty()) return;
    ChoiceDialog<String> dlg = new ChoiceDialog<>(labs.get(0), labs);
    EditorTheme.apply(dlg);
    dlg.setHeaderText(null); dlg.setTitle("Choose Label"); dlg.setContentText("Label:");
    var r = dlg.showAndWait();
    if (r.isPresent()) code.replaceText(is.start, is.end, r.get());
  }

  private static class ArcInfo {
    final String name, script, entry; final int entryStart, entryEnd;
    ArcInfo(String n, String s, String e, int es, int ee) { name=n; script=s; entry=e; entryStart=es; entryEnd=ee; }
  }

  private static class Token {
    final String arc;
    final String label;
    final int arcStart;
    final int arcEnd;
    final int labelStart;
    final int labelEnd;

    Token(String arc, String label, int arcStart, int arcEnd, int labelStart, int labelEnd) {
      this.arc = arc;
      this.label = label;
      this.arcStart = arcStart;
      this.arcEnd = arcEnd;
      this.labelStart = labelStart;
      this.labelEnd = labelEnd;
    }
  }

  private static Token parseToken(String t) {
    StoryTimelineView.LinkEndpoint ep = StoryTimelineView.parseLinkEndpoint(t);
    return new Token(ep.arc, ep.label, ep.arcStart, ep.arcEnd, ep.labelStart, ep.labelEnd);
  }

  private File resolveFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private static boolean labelExists(File vnsFile, String label) {
    try {
      if (vnsFile == null || label == null || label.isBlank()) return false;
      for (String ln : Files.readAllLines(vnsFile.toPath())) {
        String s = ln.trim();
        if (s.isEmpty() || s.startsWith("#")) continue;
        if (s.regionMatches(true, 0, "label ", 0, 6)) {
          String nm = s.substring(6).trim();
          int sp = nm.indexOf(' ');
          if (sp > 0) nm = nm.substring(0, sp);
          if (nm.equals(label)) return true;
        }
      }
      return false;
    } catch (Exception e) { return false; }
  }

  private static String nn(String s) { return s == null ? "" : s; }

  private static String unescape(String token) {
    if (token == null) return "";
    return token
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t");
  }

  private List<String> listLabels(File vnsFile) {
    try {
      if (vnsFile == null || !vnsFile.exists()) return List.of();
      List<String> out = new ArrayList<>();
      for (String ln : Files.readAllLines(vnsFile.toPath())) {
        String s = ln.trim();
        if (s.isEmpty() || s.startsWith("#")) continue;
        if (s.regionMatches(true, 0, "label ", 0, 6)) {
          String nm = s.substring(6).trim();
          int sp = nm.indexOf(' ');
          if (sp > 0) nm = nm.substring(0, sp);
          if (!nm.isEmpty()) out.add(nm);
        }
      }
      Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
      return out;
    } catch (Exception e) { return List.of(); }
  }
}

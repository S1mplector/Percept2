package com.jvn.editor.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class CodeAutoCompleter {
  public static class Suggestion {
    public final String insert;
    public final String label;
    public Suggestion(String insert) { this(insert, insert); }
    public Suggestion(String insert, String label) { this.insert = insert; this.label = label; }
    @Override public String toString() { return label; }
  }

  public static class Context {
    public final String text;
    public final int caret;
    public final String prefix; 
    public final File projectRoot;
    public Context(String text, int caret, String prefix, File projectRoot) {
      this.text = text; this.caret = caret; this.prefix = prefix; this.projectRoot = projectRoot;
    }
  }

  public interface Provider { List<Suggestion> suggest(Context ctx); }

  private final CodeArea area;
  private final Provider provider;
  private final Popup popup = new Popup();
  private final ListView<Suggestion> list = new ListView<>();
  private File projectRoot;

  public CodeAutoCompleter(CodeArea area, Provider provider) {
    this.area = area; this.provider = provider;
    list.setPrefWidth(360); list.setPrefHeight(200);
    list.setMaxWidth(400); list.setMaxHeight(260);
    list.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Suggestion it, boolean empty) {
        super.updateItem(it, empty);
        setText(empty || it == null ? null : it.label);
      }
    });
    popup.getContent().add(list);
    popup.setAutoHide(true);

    area.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
    area.addEventFilter(KeyEvent.KEY_TYPED, e -> {
      if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) maybeShow();
    });
    area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if ((e.getCode() == KeyCode.SPACE) && (e.isControlDown() || e.isMetaDown())) {
        e.consume();
        showNow();
      }
    });
    list.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commitSelected(); });
  }

  public void setProjectRoot(File root) { this.projectRoot = root; }

  private void handleKeyPressed(KeyEvent e) {
    if (!popup.isShowing()) return;
    if (e.getCode() == KeyCode.ESCAPE) { popup.hide(); e.consume(); }
    else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) { commitSelected(); e.consume(); }
    else if (e.getCode() == KeyCode.UP) { list.getSelectionModel().selectPrevious(); e.consume(); }
    else if (e.getCode() == KeyCode.DOWN) { list.getSelectionModel().selectNext(); e.consume(); }
    else if (e.getCode() == KeyCode.PAGE_UP) { list.getSelectionModel().select(0); e.consume(); }
    else if (e.getCode() == KeyCode.PAGE_DOWN) { list.getSelectionModel().select(list.getItems().size()-1); e.consume(); }
  }

  private void maybeShow() { showInternal(false); }
  private void showNow() { showInternal(true); }

  private void showInternal(boolean force) {
    int caret = area.getCaretPosition();
    String text = area.getText();
    String prefix = currentPrefix(text, caret);
    if (!force) {
      if (prefix.isEmpty()) { popup.hide(); return; }
      char last = prefix.charAt(prefix.length()-1);
      if (!(Character.isLetterOrDigit(last) || last == '@' || last == '[' || last == '"' || last == '.')) { popup.hide(); return; }
    }
    List<Suggestion> sugs = safe(() -> provider.suggest(new Context(text, caret, prefix, projectRoot)), Collections.emptyList());
    if (sugs == null || sugs.isEmpty()) { popup.hide(); return; }
    list.getItems().setAll(sugs);
    list.getSelectionModel().selectFirst();
    area.getCaretBounds().ifPresent(b -> {
      double x = b.getMinX() + area.getScene().getWindow().getX();
      double y = b.getMaxY() + area.getScene().getWindow().getY();
      if (!popup.isShowing()) popup.show(area.getScene().getWindow(), x, y); else popup.setX(x); popup.setY(y);
    });
  }

  private String currentPrefix(String text, int caret) {
    if (caret <= 0 || text == null) return "";
    int i = caret - 1;
    while (i >= 0) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '[' || c == ']' || c == ':' || c == '/' || c == '.' || c == '"') {
        i--;
        continue;
      }
      break;
    }
    int start = i + 1;
    if (start < 0 || start > caret) return "";
    return text.substring(start, caret);
  }

  private void commitSelected() {
    Suggestion s = list.getSelectionModel().getSelectedItem();
    if (s == null) { popup.hide(); return; }
    int caret = area.getCaretPosition();
    String text = area.getText();
    String prefix = currentPrefix(text, caret);
    int start = caret - prefix.length();
    if (start < 0) start = 0;
    area.replaceText(start, caret, s.insert);
    area.moveTo(start + s.insert.length());
    popup.hide();
  }

  public static List<String> listAssetIds(File root, String subDir, String... exts) {
    try {
      if (root == null) return List.of();
      File d = new File(root, subDir);
      if (!d.exists()) return List.of();
      List<String> out = new ArrayList<>();
      Files.walk(d.toPath(), 2).forEach(p -> {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String e : exts) {
          if (n.endsWith(e)) {
            String id = d.toPath().relativize(p).toString().replace('\\','/');
            int dot = id.lastIndexOf('.'); if (dot > 0) id = id.substring(0, dot);
            out.add(id);
            break;
          }
        }
      });
      Collections.sort(out);
      return out;
    } catch (Exception ignore) { return List.of(); }
  }

  private static <T> T safe(Supplier<T> s, T def) {
    try { return s.get(); } catch (Exception ignore) { return def; }
  }
}

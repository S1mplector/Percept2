package com.jvn.editor.ui;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class SceneGraphView extends VBox {
  private final Label header = new Label("Scene Graph");
  private final TextField filter = new TextField();
  private final ListView<String> list = new ListView<>();
  private Consumer<Entity2D> onSelected;
  private Consumer<Entity2D> onFit;
  private Consumer<String> setStatus;
  private JesScene2D scene;
  private List<String> allNames = new ArrayList<>();

  public SceneGraphView() {
    setSpacing(8);
    filter.setPromptText("Filter by name...");
    filter.setOnKeyReleased(e -> applyFilter());

    ContextMenu cm = new ContextMenu();
    MenuItem miRename = new MenuItem("Rename...");
    MenuItem miDelete = new MenuItem("Delete");
    MenuItem miFitSel = new MenuItem("Fit Selection");
    cm.getItems().addAll(miRename, miDelete, miFitSel);
    list.setContextMenu(cm);

    list.setOnMouseClicked(e -> {
      String name = list.getSelectionModel().getSelectedItem();
      if (name == null || scene == null) return;
      Entity2D ent = scene.find(name);
      if (onSelected != null) onSelected.accept(ent);
      if (e.getClickCount() == 2 && onFit != null && ent != null) onFit.accept(ent);
    });

    list.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);
        if (empty || name == null) { setText(null); setGraphic(null); return; }
        setText(name);
        Entity2D e = scene == null ? null : scene.find(name);
        if (e == null) { setGraphic(null); return; }
        Label badge = new Label();
        String txt;
        Color col;
        if (e instanceof Panel2D) { txt = "P"; col = Color.web("#2ea043"); }
        else if (e instanceof Label2D) { txt = "L"; col = Color.web("#58a6ff"); }
        else if (e instanceof com.jvn.core.scene2d.Sprite2D) { txt = "S"; col = Color.web("#d29922"); }
        else if (e instanceof PhysicsBodyEntity2D) { txt = "B"; col = Color.web("#e16e6e"); }
        else if (e instanceof ParticleEmitter2D) { txt = "E"; col = Color.web("#a371f7"); }
        else { txt = "?"; col = Color.web("#8b949e"); }
        badge.setText(txt);
        badge.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-weight: bold;");
        badge.setTextFill(col);
        setGraphic(badge);
      }
    });

    getChildren().addAll(header, filter, list);

    cm.setOnShowing(ev -> {
      String sel = list.getSelectionModel().getSelectedItem();
      boolean dis = sel == null || scene == null;
      miRename.setDisable(dis);
      miDelete.setDisable(dis);
      miFitSel.setDisable(dis);
    });

    miRename.setOnAction(ev -> {
      String oldName = list.getSelectionModel().getSelectedItem();
      if (oldName == null || scene == null) return;
      EditorDialogs.promptText(
          getScene() == null ? null : getScene().getWindow(),
          "Rename",
          "Rename " + oldName,
          "New name",
          oldName,
          oldName,
          "Rename").ifPresent(newName -> {
        if (scene.rename(oldName, newName)) {
          if (setStatus != null) setStatus.accept("Renamed " + oldName + " → " + newName);
          refresh();
        }
      });
    });

    miDelete.setOnAction(ev -> {
      String name = list.getSelectionModel().getSelectedItem();
      if (name == null || scene == null) return;
      if (scene.removeEntity(name)) {
        if (setStatus != null) setStatus.accept("Deleted " + name);
        refresh();
        if (onSelected != null) onSelected.accept(null);
      }
    });

    miFitSel.setOnAction(ev -> {
      String name = list.getSelectionModel().getSelectedItem();
      if (name == null || scene == null) return;
      Entity2D e = scene.find(name);
      if (e != null && onFit != null) onFit.accept(e);
    });
  }

  public void setContext(JesScene2D scene, Consumer<Entity2D> onSelected, Consumer<Entity2D> onFit, Consumer<String> setStatus) {
    this.scene = scene;
    this.onSelected = onSelected;
    this.onFit = onFit;
    this.setStatus = setStatus;
  }

  public void refresh() {
    getChildren().setAll(header, filter, list);
    if (scene == null) {
      list.setItems(FXCollections.observableArrayList());
      return;
    }
    allNames = new ArrayList<>(scene.names());
    Collections.sort(allNames);
    applyFilter();
  }

  private void applyFilter() {
    String q = filter.getText();
    if (q == null) q = "";
    String qq = q.toLowerCase();
    List<String> filtered = new ArrayList<>();
    for (String n : allNames) { if (n.toLowerCase().contains(qq)) filtered.add(n); }
    list.setItems(FXCollections.observableArrayList(filtered));
  }
}

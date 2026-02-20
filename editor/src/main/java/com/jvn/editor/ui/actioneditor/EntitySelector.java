package com.jvn.editor.ui.actioneditor;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class EntitySelector extends VBox {
    private final TextField filterField;
    private final TreeView<String> treeView;
    private final TreeItem<String> rootItem;

    private AnimationProject project;
    private Consumer<String> onEntitySelected;
    private Consumer<String> onCreateGroup;
    private BiConsumer<String, String> onAddToGroup;

    public EntitySelector() {
        setSpacing(6);
        setPadding(new Insets(8));
        setStyle("-fx-background-color: #1e1e2e;");

        Label header = new Label("Entities");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

        filterField = new TextField();
        filterField.setPromptText("Filter...");
        filterField.setOnKeyReleased(e -> applyFilter());

        rootItem = new TreeItem<>("Scene");
        rootItem.setExpanded(true);

        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        treeView.setCellFactory(tv -> new EntityTreeCell());
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onEntitySelected != null) {
                onEntitySelected.accept(newVal.getValue());
            }
        });

        VBox.setVgrow(treeView, Priority.ALWAYS);

        Button btnNewGroup = new Button("+ Group");
        btnNewGroup.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("NewGroup");
            dialog.setTitle("Create Group");
            dialog.setHeaderText(null);
            dialog.setContentText("Group name:");
            dialog.showAndWait().ifPresent(name -> {
                if (onCreateGroup != null && name != null && !name.isBlank()) {
                    onCreateGroup.accept(name);
                }
            });
        });

        HBox toolbar = new HBox(6, btnNewGroup);
        toolbar.setPadding(new Insets(4, 0, 0, 0));

        getChildren().addAll(header, filterField, treeView, toolbar);

        setupContextMenu();
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnCreateGroup(Consumer<String> callback) { this.onCreateGroup = callback; }
    public void setOnAddToGroup(BiConsumer<String, String> callback) { this.onAddToGroup = callback; }

    public void refresh(AnimationProject project) {
        this.project = project;
        rootItem.getChildren().clear();
        if (project == null) return;

        for (String groupName : project.getRootGroupNames()) {
            TreeItem<String> groupItem = buildGroupItem(groupName);
            rootItem.getChildren().add(groupItem);
        }

        for (String entityName : project.getRootEntityNames()) {
            TreeItem<String> entityItem = new TreeItem<>(entityName);
            rootItem.getChildren().add(entityItem);
        }

        applyFilter();
    }

    private TreeItem<String> buildGroupItem(String groupName) {
        EntityGroup group = project.getGroup(groupName);
        TreeItem<String> item = new TreeItem<>("📁 " + groupName);
        item.setExpanded(group != null && group.isExpanded());

        if (group != null) {
            for (String childGroup : group.getChildGroupNames()) {
                item.getChildren().add(buildGroupItem(childGroup));
            }
            for (String childEntity : group.getChildEntityNames()) {
                item.getChildren().add(new TreeItem<>(childEntity));
            }
        }
        return item;
    }

    private void applyFilter() {
        String query = filterField.getText();
        if (query == null || query.isBlank()) {
            treeView.setRoot(rootItem);
            return;
        }

        String q = query.toLowerCase();
        TreeItem<String> filtered = new TreeItem<>("Scene");
        filtered.setExpanded(true);

        for (TreeItem<String> child : rootItem.getChildren()) {
            if (matchesFilter(child, q)) {
                filtered.getChildren().add(copyItem(child, q));
            }
        }
        treeView.setRoot(filtered);
    }

    private boolean matchesFilter(TreeItem<String> item, String query) {
        if (item.getValue().toLowerCase().contains(query)) return true;
        for (TreeItem<String> child : item.getChildren()) {
            if (matchesFilter(child, query)) return true;
        }
        return false;
    }

    private TreeItem<String> copyItem(TreeItem<String> item, String query) {
        TreeItem<String> copy = new TreeItem<>(item.getValue());
        copy.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) {
            if (matchesFilter(child, query)) {
                copy.getChildren().add(copyItem(child, query));
            }
        }
        return copy;
    }

    private void setupContextMenu() {
        ContextMenu cm = new ContextMenu();

        Menu addToGroupMenu = new Menu("Add to Group");
        MenuItem removeFromGroup = new MenuItem("Remove from Group");
        MenuItem deleteItem = new MenuItem("Delete");

        cm.getItems().addAll(addToGroupMenu, removeFromGroup, new SeparatorMenuItem(), deleteItem);

        cm.setOnShowing(e -> { 
            addToGroupMenu.getItems().clear();
            if (project != null) {
                for (EntityGroup g : project.getGroups()) {
                    MenuItem mi = new MenuItem(g.getName());
                    mi.setOnAction(ev -> {
                        TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
                        if (sel != null && onAddToGroup != null) {
                            String name = sel.getValue().replace("📁 ", "");
                            onAddToGroup.accept(name, g.getName());
                        }
                    });
                    addToGroupMenu.getItems().add(mi);
                }
            }
        });

        removeFromGroup.setOnAction(e -> {
            TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && project != null) {
                String name = sel.getValue().replace("📁 ", "");
                project.removeEntityFromGroup(name);
                refresh(project);
            }
        });

        deleteItem.setOnAction(e -> {
            TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && project != null) {
                String name = sel.getValue().replace("📁 ", "");
                if (project.getGroup(name) != null) {
                    project.removeGroup(name);
                } else {
                    project.removeTrack(name);
                }
                refresh(project);
            }
        });

        treeView.setContextMenu(cm);
    }

    private class EntityTreeCell extends TreeCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);
                if (item.startsWith("📁")) {
                    setTextFill(Color.web("#f9e2af"));
                } else {
                    setTextFill(Color.web("#cdd6f4"));
                }
            }
        }
    }
}

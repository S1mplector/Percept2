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
    private static final String GROUP_PREFIX = "__group__:";

    private final TextField filterField;
    private final TreeView<String> treeView;
    private final TreeItem<String> rootItem;

    private final Label lblEmptyHint;

    private AnimationProject project;
    private Consumer<String> onEntitySelected;
    private BiConsumer<String, Boolean> onSelectionChanged;
    private Consumer<String> onCreateGroup;
    private BiConsumer<String, String> onAddToGroup;
    private AddToGroupRequest onAddSelectionToGroup;
    private BiConsumer<String, Integer> onEntityLayerDelta;
    private BiConsumer<String, Integer> onGroupLayerDelta;

    public EntitySelector() {
        setSpacing(4);
        setPadding(new Insets(6, 8, 6, 8));
        setStyle("-fx-background-color: #1a1a1a;");

        Label header = new Label("Entities");
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #e6e6e6; -fx-font-size: 12px;");

        filterField = new TextField();
        filterField.setPromptText("Filter entities...");
        filterField.setStyle("-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
            "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px; -fx-prompt-text-fill: #555;");
        filterField.setOnKeyReleased(e -> applyFilter());

        rootItem = new TreeItem<>("Scene");
        rootItem.setExpanded(true);

        lblEmptyHint = new Label("No entities in scene.\nLoad a scene or add entities\nvia the timeline.");
        lblEmptyHint.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-padding: 8 0 0 0;");
        lblEmptyHint.setWrapText(true);

        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        treeView.setStyle("-fx-background-color: #1a1a1a; -fx-control-inner-background: #1a1a1a;");
        treeView.setCellFactory(tv -> new EntityTreeCell());
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String encoded = newVal.getValue();
                String name = decodeTreeValue(encoded);
                boolean group = isEncodedGroupValue(encoded);
                if (onSelectionChanged != null) {
                    onSelectionChanged.accept(name, group);
                } else if (onEntitySelected != null) {
                    onEntitySelected.accept(name);
                }
            }
        });

        VBox.setVgrow(treeView, Priority.ALWAYS);

        Button btnNewGroup = new Button("+ Group");
        btnNewGroup.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #a0a0a0; -fx-background-radius: 3; " +
            "-fx-border-color: #3a3a3a; -fx-border-radius: 3; -fx-padding: 2 8; -fx-font-size: 10px; -fx-cursor: hand;");
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

        getChildren().addAll(header, filterField, lblEmptyHint, treeView, toolbar);
        updateEmptyState();

        setupContextMenu();
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnSelectionChanged(BiConsumer<String, Boolean> callback) { this.onSelectionChanged = callback; }
    public void setOnCreateGroup(Consumer<String> callback) { this.onCreateGroup = callback; }
    public void setOnAddToGroup(BiConsumer<String, String> callback) { this.onAddToGroup = callback; }
    public void setOnAddSelectionToGroup(AddToGroupRequest callback) { this.onAddSelectionToGroup = callback; }
    public void setOnEntityLayerDelta(BiConsumer<String, Integer> callback) { this.onEntityLayerDelta = callback; }
    public void setOnGroupLayerDelta(BiConsumer<String, Integer> callback) { this.onGroupLayerDelta = callback; }

    public boolean isGroupSelected() {
        TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
        return sel != null && isEncodedGroupValue(sel.getValue());
    }

    public void refresh(AnimationProject project) {
        String previousSelection = selectedEncodedValue();
        this.project = project;
        rootItem.getChildren().clear();
        if (project == null) {
            updateEmptyState();
            return;
        }

        for (String groupName : project.getRootGroupNames()) {
            TreeItem<String> groupItem = buildGroupItem(groupName);
            rootItem.getChildren().add(groupItem);
        }

        for (String entityName : project.getRootEntityNames()) {
            TreeItem<String> entityItem = new TreeItem<>(entityName);
            rootItem.getChildren().add(entityItem);
        }

        applyFilter();
        reselectByEncodedValue(previousSelection);
        updateEmptyState();
    }

    public void selectEntity(String name) {
        selectByName(name, false);
    }

    public void selectGroup(String name) {
        selectByName(name, true);
    }

    private void updateEmptyState() {
        boolean empty = rootItem.getChildren().isEmpty();
        lblEmptyHint.setVisible(empty);
        lblEmptyHint.setManaged(empty);
        treeView.setVisible(!empty);
        treeView.setManaged(!empty);
    }

    private TreeItem<String> buildGroupItem(String groupName) {
        EntityGroup group = project.getGroup(groupName);
        TreeItem<String> item = new TreeItem<>(encodeGroupValue(groupName));
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
        String selected = selectedEncodedValue();
        String query = filterField.getText();
        if (query == null || query.isBlank()) {
            treeView.setRoot(rootItem);
            reselectByEncodedValue(selected);
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
        reselectByEncodedValue(selected);
    }

    private boolean matchesFilter(TreeItem<String> item, String query) {
        if (toDisplayValue(item.getValue()).toLowerCase().contains(query)) return true;
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
        Menu layerMenu = new Menu("Layer Order");
        MenuItem layerUp = new MenuItem("Raise (+10)");
        MenuItem layerDown = new MenuItem("Lower (-10)");
        MenuItem deleteItem = new MenuItem("Delete");
        layerMenu.getItems().addAll(layerUp, layerDown);

        cm.getItems().addAll(addToGroupMenu, removeFromGroup, layerMenu, new SeparatorMenuItem(), deleteItem);

        cm.setOnShowing(e -> { 
            addToGroupMenu.getItems().clear();
            if (project != null) {
                TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
                String selectedName = selected == null ? null : decodeTreeValue(selected.getValue());
                for (EntityGroup g : project.getGroups()) {
                    if (selectedName != null && selectedName.equals(g.getName())) continue;
                    MenuItem mi = new MenuItem(g.getName());
                    mi.setOnAction(ev -> {
                        TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
                        if (sel != null) {
                            String encoded = sel.getValue();
                            String name = decodeTreeValue(encoded);
                            boolean selectedIsGroup = isEncodedGroupValue(encoded);
                            if (onAddSelectionToGroup != null) {
                                onAddSelectionToGroup.accept(name, selectedIsGroup, g.getName());
                            } else if (onAddToGroup != null) {
                                onAddToGroup.accept(name, g.getName());
                            }
                        }
                    });
                    addToGroupMenu.getItems().add(mi);
                }
            }
        });

        removeFromGroup.setOnAction(e -> {
            TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && project != null) {
                String encoded = sel.getValue();
                String name = decodeTreeValue(encoded);
                if (isEncodedGroupValue(encoded)) {
                    project.removeGroupFromParent(name);
                } else {
                    project.removeEntityFromGroup(name);
                }
                refresh(project);
            }
        });

        layerUp.setOnAction(e -> adjustLayerOrder(+10));
        layerDown.setOnAction(e -> adjustLayerOrder(-10));

        deleteItem.setOnAction(e -> {
            TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && project != null) {
                String encoded = sel.getValue();
                String name = decodeTreeValue(encoded);
                if (isEncodedGroupValue(encoded)) {
                    project.removeGroup(name);
                } else {
                    project.removeTrack(name);
                }
                refresh(project);
            }
        });

        treeView.setContextMenu(cm);
    }

    private static String encodeGroupValue(String name) {
        return GROUP_PREFIX + name;
    }

    private static boolean isEncodedGroupValue(String value) {
        return value != null && value.startsWith(GROUP_PREFIX);
    }

    private static String decodeTreeValue(String value) {
        if (value == null) return "";
        return isEncodedGroupValue(value) ? value.substring(GROUP_PREFIX.length()) : value;
    }

    private static String toDisplayValue(String value) {
        return decodeTreeValue(value);
    }

    private String selectedEncodedValue() {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    private void selectByName(String name, boolean group) {
        if (name == null || name.isBlank()) {
            treeView.getSelectionModel().clearSelection();
            return;
        }
        String encoded = group ? encodeGroupValue(name) : name;
        reselectByEncodedValue(encoded);
    }

    private void reselectByEncodedValue(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) return;
        TreeItem<String> root = treeView.getRoot();
        TreeItem<String> match = findTreeItem(root, encodedValue);
        if (match == null) return;
        expandTreePath(match);
        treeView.getSelectionModel().select(match);
    }

    private static TreeItem<String> findTreeItem(TreeItem<String> root, String value) {
        if (root == null || value == null) return null;
        if (value.equals(root.getValue())) return root;
        for (TreeItem<String> child : root.getChildren()) {
            TreeItem<String> found = findTreeItem(child, value);
            if (found != null) return found;
        }
        return null;
    }

    private static void expandTreePath(TreeItem<String> item) {
        TreeItem<String> cursor = item.getParent();
        while (cursor != null) {
            cursor.setExpanded(true);
            cursor = cursor.getParent();
        }
    }

    private void adjustLayerOrder(int delta) {
        TreeItem<String> sel = treeView.getSelectionModel().getSelectedItem();
        if (sel == null || project == null || delta == 0) return;

        String encoded = sel.getValue();
        String name = decodeTreeValue(encoded);
        if (isEncodedGroupValue(encoded)) {
            if (onGroupLayerDelta != null) onGroupLayerDelta.accept(name, delta);
        } else {
            if (onEntityLayerDelta != null) onEntityLayerDelta.accept(name, delta);
        }
    }

    private class EntityTreeCell extends TreeCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(isEncodedGroupValue(item) ? "📁 " + toDisplayValue(item) : toDisplayValue(item));
                if (isEncodedGroupValue(item)) {
                    setTextFill(Color.web("#f0b673"));
                } else {
                    setTextFill(Color.web("#e6e6e6"));
                }
            }
        }
    }

    @FunctionalInterface
    public interface AddToGroupRequest {
        void accept(String selectionName, boolean selectionIsGroup, String targetGroupName);
    }
}

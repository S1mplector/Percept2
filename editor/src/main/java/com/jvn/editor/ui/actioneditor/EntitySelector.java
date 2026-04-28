package com.jvn.editor.ui.actioneditor;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class EntitySelector extends VBox {
    private static final String GROUP_PREFIX = "__group__:";

    private final TextField filterField;
    private final TreeView<String> treeView;
    private final TreeItem<String> rootItem;
    private final ActionEditorTextPromptOverlay groupPromptOverlay;
    private final ActionEditorDialogOverlay actionOverlay;

    private final Label lblEmptyHint;

    private AnimationProject project;
    private JesScene2D scene;
    private Consumer<String> onEntitySelected;
    private BiConsumer<String, Boolean> onSelectionChanged;
    private Consumer<String> onCreateGroup;
    private BiConsumer<String, String> onAddToGroup;
    private AddToGroupRequest onAddSelectionToGroup;
    private BiConsumer<String, Boolean> onDeleteSelection;
    private BiConsumer<String, String> onRenameGroup;
    private BiConsumer<String, Integer> onEntityLayerDelta;
    private BiConsumer<String, Integer> onGroupLayerDelta;
    private BiConsumer<String, Boolean> onEntityVisibilityChanged;

    public EntitySelector() {
        setSpacing(0);
        setMinWidth(0);
        setStyle("-fx-background-color: #1a1a1a;");

        VBox content = new VBox(4);
        content.setPadding(new Insets(6, 8, 6, 8));

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
        treeView.setMinWidth(0);
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
        btnNewGroup.setOnAction(e -> showCreateGroupOverlay());

        Button btnActions = new Button("Actions");
        btnActions.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #a0a0a0; -fx-background-radius: 3; " +
            "-fx-border-color: #3a3a3a; -fx-border-radius: 3; -fx-padding: 2 8; -fx-font-size: 10px; -fx-cursor: hand;");
        btnActions.setOnAction(e -> showSelectionActionsOverlay());

        HBox toolbar = new HBox(6, btnNewGroup, btnActions);
        toolbar.setPadding(new Insets(4, 0, 0, 0));

        groupPromptOverlay = new ActionEditorTextPromptOverlay();
        actionOverlay = new ActionEditorDialogOverlay();

        content.getChildren().addAll(header, filterField, lblEmptyHint, treeView, toolbar);
        StackPane contentStack = new StackPane(content, actionOverlay, groupPromptOverlay);
        contentStack.setMinWidth(0);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().add(contentStack);
        updateEmptyState();
        treeView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                showSelectionActionsOverlay();
                event.consume();
            }
        });
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnSelectionChanged(BiConsumer<String, Boolean> callback) { this.onSelectionChanged = callback; }
    public void setOnCreateGroup(Consumer<String> callback) { this.onCreateGroup = callback; }
    public void setOnAddToGroup(BiConsumer<String, String> callback) { this.onAddToGroup = callback; }
    public void setOnAddSelectionToGroup(AddToGroupRequest callback) { this.onAddSelectionToGroup = callback; }
    public void setOnDeleteSelection(BiConsumer<String, Boolean> callback) { this.onDeleteSelection = callback; }
    public void setOnRenameGroup(BiConsumer<String, String> callback) { this.onRenameGroup = callback; }
    public void setOnEntityLayerDelta(BiConsumer<String, Integer> callback) { this.onEntityLayerDelta = callback; }
    public void setOnGroupLayerDelta(BiConsumer<String, Integer> callback) { this.onGroupLayerDelta = callback; }
    public void setOnEntityVisibilityChanged(BiConsumer<String, Boolean> callback) { this.onEntityVisibilityChanged = callback; }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
    }

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

    private void showCreateGroupOverlay() {
        groupPromptOverlay.showPrompt(
            "Create Group",
            "Add a new entity group inside the current Puppeteer project. Existing names will get a numeric suffix.",
            "Group name",
            "NewGroup",
            "Create",
            name -> {
                String resolvedName = resolveUniqueGroupName(name, null);
                if (onCreateGroup != null && name != null && !resolvedName.isBlank()) {
                    onCreateGroup.accept(resolvedName);
                }
            });
    }

    private TreeItem<String> buildGroupItem(String groupName) {
        EntityGroup group = project.getGroup(groupName);
        TreeItem<String> item = new TreeItem<>(encodeGroupValue(groupName));
        item.setExpanded(group != null && group.isExpanded());
        if (group != null) {
            item.expandedProperty().addListener((obs, oldValue, newValue) -> {
                EntityGroup current = project != null ? project.getGroup(groupName) : null;
                if (current != null) {
                    current.setExpanded(newValue);
                }
            });
        }

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

    private void showSelectionActionsOverlay() {
        TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || project == null) return;

        String encoded = selected.getValue();
        String name = decodeTreeValue(encoded);
        boolean group = isEncodedGroupValue(encoded);
        boolean hasParent = selectionHasParent(name, group);

        VBox menu = new VBox(8);
        menu.getChildren().add(buildActionMenuButton(
            group ? "Wrap in New Parent Group" : "Wrap in New Group",
            () -> showCreateParentGroupOverlay(name, group)));
        if (group) {
            menu.getChildren().add(buildActionMenuButton("Create Child Group", () -> showCreateChildGroupOverlay(name)));
            menu.getChildren().add(buildActionMenuButton("Rename Group", () -> showRenameGroupOverlay(name)));
        }
        menu.getChildren().add(buildActionMenuButton(
            group ? "Move Under Another Group" : "Add to Existing Group",
            () -> showAddToGroupOverlay(name, group)));
        if (hasParent) {
            menu.getChildren().add(buildActionMenuButton(
                group ? "Move Group to Root" : "Move to Root",
                () -> removeSelectionFromGroup(encoded)));
        }
        menu.getChildren().add(buildActionMenuButton("Raise Layer (+10)", () -> adjustLayerOrder(+10)));
        menu.getChildren().add(buildActionMenuButton("Lower Layer (-10)", () -> adjustLayerOrder(-10)));
        menu.getChildren().add(buildActionMenuButton(
            group ? "Ungroup Container" : "Delete Entity",
            () -> deleteSelection(encoded)));

        actionOverlay.showDialog(
            group ? "Group Actions" : "Entity Actions",
            name,
            menu,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", actionOverlay::hideOverlay).defaultFocus(true)
        );
    }

    private void showAddToGroupOverlay(String selectionName, boolean selectionIsGroup) {
        if (project == null || selectionName == null || selectionName.isBlank()) return;
        VBox menu = new VBox(8);
        boolean hasTargets = false;
        for (EntityGroup group : project.getGroups()) {
            if (group == null) continue;
            if (selectionName.equals(group.getName())) continue;
            if (selectionIsGroup && !project.canAddGroupToGroup(selectionName, group.getName())) continue;
            if (!selectionIsGroup) {
                EntityTrack track = project.getTrack(selectionName);
                if (track != null && group.getName().equals(track.getParentGroupName())) {
                    continue;
                }
            }
            hasTargets = true;
            menu.getChildren().add(buildActionMenuButton(group.getName(), () -> {
                if (onAddSelectionToGroup != null) {
                    onAddSelectionToGroup.accept(selectionName, selectionIsGroup, group.getName());
                } else if (!selectionIsGroup && onAddToGroup != null) {
                    onAddToGroup.accept(selectionName, group.getName());
                }
                actionOverlay.hideOverlay();
            }));
        }
        if (!hasTargets) {
            Label empty = new Label(selectionIsGroup
                ? "No valid parent groups are available for this group."
                : "Create another group first to organize this entity.");
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px;");
            menu.getChildren().add(empty);
        }
        actionOverlay.showDialog(
            "Add to Group",
            selectionName,
            menu,
            ActionEditorDialogOverlay.ActionSpec.neutral("Back", this::showSelectionActionsOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", actionOverlay::hideOverlay)
        );
    }

    private void showCreateParentGroupOverlay(String selectionName, boolean selectionIsGroup) {
        if (project == null || selectionName == null || selectionName.isBlank()) return;
        String suggestedName = resolveUniqueGroupName(selectionName + "Group", null);
        actionOverlay.hideOverlay();
        groupPromptOverlay.showPrompt(
            selectionIsGroup ? "Wrap Group in Parent Group" : "Wrap Entity in New Group",
            "Create a new group and place the current selection inside it. Existing names will get a numeric suffix.",
            "Group name",
            suggestedName,
            "Create",
            requestedName -> {
                String groupName = resolveUniqueGroupName(requestedName, null);
                if (groupName.isBlank()) return;
                if (onCreateGroup != null) {
                    onCreateGroup.accept(groupName);
                }
                if (onAddSelectionToGroup != null) {
                    onAddSelectionToGroup.accept(selectionName, selectionIsGroup, groupName);
                } else if (!selectionIsGroup && onAddToGroup != null) {
                    onAddToGroup.accept(selectionName, groupName);
                }
            }
        );
    }

    private void showCreateChildGroupOverlay(String parentGroupName) {
        if (project == null || parentGroupName == null || parentGroupName.isBlank()) return;
        String suggestedName = resolveUniqueGroupName(parentGroupName + "Child", null);
        actionOverlay.hideOverlay();
        groupPromptOverlay.showPrompt(
            "Create Child Group",
            "Create a new nested group inside the selected parent group.",
            "Group name",
            suggestedName,
            "Create",
            requestedName -> {
                String groupName = resolveUniqueGroupName(requestedName, null);
                if (groupName.isBlank()) return;
                if (onCreateGroup != null) {
                    onCreateGroup.accept(groupName);
                }
                if (onAddSelectionToGroup != null) {
                    onAddSelectionToGroup.accept(groupName, true, parentGroupName);
                }
                refresh(project);
                selectGroup(groupName);
            }
        );
    }

    private void showRenameGroupOverlay(String currentName) {
        if (project == null || currentName == null || currentName.isBlank()) return;
        actionOverlay.hideOverlay();
        groupPromptOverlay.showPrompt(
            "Rename Group",
            "Update the group label across the hierarchy. Existing names will get a numeric suffix.",
            "Group name",
            currentName,
            "Rename",
            requestedName -> {
                String nextName = resolveUniqueGroupName(requestedName, currentName);
                if (onRenameGroup != null && !nextName.equals(currentName)) {
                    onRenameGroup.accept(currentName, nextName);
                }
            }
        );
    }

    private void removeSelectionFromGroup(String encoded) {
        if (encoded == null || project == null) return;
        String name = decodeTreeValue(encoded);
        if (isEncodedGroupValue(encoded)) {
            project.removeGroupFromParent(name);
        } else {
            project.removeEntityFromGroup(name);
        }
        refresh(project);
        actionOverlay.hideOverlay();
    }

    private void deleteSelection(String encoded) {
        if (encoded == null || project == null) return;
        String name = decodeTreeValue(encoded);
        boolean isGroup = isEncodedGroupValue(encoded);
        if (onDeleteSelection != null) {
            onDeleteSelection.accept(name, isGroup);
        } else {
            if (isGroup) {
                project.removeGroup(name);
            } else {
                project.removeTrack(name);
            }
            refresh(project);
        }
        actionOverlay.hideOverlay();
    }

    private Button buildActionMenuButton(String label, Runnable action) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
            "-fx-background-color: #232323; -fx-text-fill: #d7d7d7; -fx-background-radius: 4; "
                + "-fx-border-color: #444444; -fx-border-radius: 4; -fx-padding: 7 10; -fx-font-size: 11px; -fx-cursor: hand;");
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
            event.consume();
        });
        return button;
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
        treeView.refresh();
    }

    private boolean selectionHasParent(String name, boolean group) {
        if (project == null || name == null || name.isBlank()) return false;
        if (group) {
            EntityGroup entityGroup = project.getGroup(name);
            return entityGroup != null && entityGroup.hasParent();
        }
        EntityTrack track = project.getTrack(name);
        return track != null && track.hasParent();
    }

    private String resolveUniqueGroupName(String requestedName, String currentName) {
        String base = requestedName == null ? "" : requestedName.trim();
        if (base.isBlank()) {
            base = "NewGroup";
        }
        if (project == null || base.equals(currentName) || project.getGroup(base) == null) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "_" + suffix;
        while (!candidate.equals(currentName) && project.getGroup(candidate) != null) {
            suffix++;
            candidate = base + "_" + suffix;
        }
        return candidate;
    }

    private boolean resolveEntityVisible(String entityName) {
        if (project == null || entityName == null || entityName.isBlank()) return true;
        EntityTrack track = project.getTrack(entityName);
        return track == null || track.isVisible();
    }

    private class EntityTreeCell extends TreeCell<String> {
        private static final double LASSO_ARC = 10.0;
        private final Canvas icon = new Canvas(16, 16);
        private final Canvas visibilityIcon = new Canvas(14, 14);
        private final Label label = new Label();
        private final Region spacer = new Region();
        private final Label layerBadge = new Label();
        private final HBox row = new HBox(6, icon, label, spacer, visibilityIcon, layerBadge);
        private final Canvas lassoCanvas = new Canvas();
        private final StackPane cellRoot = new StackPane(row, lassoCanvas);
        private final Timeline lassoTimeline;
        private double lassoDashOffset = 0.0;

        EntityTreeCell() {
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 6, 3, 6));
            label.setStyle("-fx-font-size: 11px;");
            label.setMaxWidth(Double.MAX_VALUE);
            spacer.setMinWidth(12);
            spacer.setPrefWidth(12);
            spacer.setMaxWidth(12);
            layerBadge.setStyle(
                "-fx-font-size: 10px; -fx-text-fill: #8a8f98; " +
                "-fx-background-color: #15181f; -fx-background-radius: 9; " +
                "-fx-border-color: #2b3240; -fx-border-radius: 9; -fx-padding: 1 7;"
            );
            layerBadge.setMinWidth(52);
            layerBadge.setAlignment(Pos.CENTER_RIGHT);

            visibilityIcon.setCursor(Cursor.HAND);
            visibilityIcon.setOnMouseClicked(event -> {
                if (isEmpty() || getItem() == null) return;
                String encoded = getItem();
                if (isEncodedGroupValue(encoded)) return;
                String entityName = decodeTreeValue(encoded);
                boolean nextVisible = !resolveEntityVisible(entityName);
                if (onEntityVisibilityChanged != null) {
                    onEntityVisibilityChanged.accept(entityName, nextVisible);
                }
                drawVisibilityIcon(visibilityIcon.getGraphicsContext2D(), nextVisible);
                event.consume();
            });

            lassoCanvas.setMouseTransparent(true);
            lassoCanvas.setManaged(false);
            StackPane.setAlignment(row, Pos.CENTER_LEFT);
            StackPane.setAlignment(lassoCanvas, Pos.CENTER_LEFT);

            lassoTimeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(40), e -> {
                lassoDashOffset -= 2.2;
                drawSelectionLasso();
            }));
            lassoTimeline.setCycleCount(Animation.INDEFINITE);

            lassoCanvas.widthProperty().bind(row.widthProperty());
            lassoCanvas.heightProperty().bind(row.heightProperty());
            
            lassoCanvas.widthProperty().addListener((obs, oldVal, newVal) -> drawSelectionLasso());
            lassoCanvas.heightProperty().addListener((obs, oldVal, newVal) -> drawSelectionLasso());
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                stopLasso();
            } else {
                String name = decodeTreeValue(item);
                boolean isGroup = isEncodedGroupValue(item);
                boolean isVisible = !isGroup && resolveEntityVisible(name);
                label.setText(name);
                label.setTextFill(isGroup
                    ? Color.web("#f0b673")
                    : (isVisible ? Color.web("#e6e6e6") : Color.web("#8a8f98")));
                layerBadge.setText(formatLayerBadge(name, isGroup));
                drawEntityIcon(icon.getGraphicsContext2D(), name, isGroup);
                if (isGroup) {
                    visibilityIcon.setVisible(false);
                    visibilityIcon.setManaged(false);
                    visibilityIcon.getGraphicsContext2D().clearRect(0, 0, visibilityIcon.getWidth(), visibilityIcon.getHeight());
                } else {
                    visibilityIcon.setVisible(true);
                    visibilityIcon.setManaged(true);
                    drawVisibilityIcon(visibilityIcon.getGraphicsContext2D(), isVisible);
                }
                setText(null);
                setGraphic(cellRoot);
                syncLassoCanvas();
                refreshLassoState();
            }
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            refreshLassoState();
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            syncLassoCanvas();
        }

        private String formatLayerBadge(String name, boolean isGroup) {
            int value = computeLayerValue(name, isGroup);
            return value >= 0 ? "Z +" + value : "Z " + value;
        }

        private void refreshLassoState() {
            if (isEmpty() || getItem() == null || !isSelected()) {
                stopLasso();
                return;
            }
            syncLassoCanvas();
            drawSelectionLasso();
            if (lassoTimeline.getStatus() != Animation.Status.RUNNING) {
                lassoTimeline.play();
            }
        }

        private void stopLasso() {
            lassoTimeline.stop();
            GraphicsContext gc = lassoCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, lassoCanvas.getWidth(), lassoCanvas.getHeight());
        }

        private void syncLassoCanvas() {
            drawSelectionLasso();
        }

        private void drawSelectionLasso() {
            GraphicsContext gc = lassoCanvas.getGraphicsContext2D();
            double width = lassoCanvas.getWidth();
            double height = lassoCanvas.getHeight();
            gc.clearRect(0, 0, width, height);
            if (!isSelected() || width <= 20.0 || height <= 6.0) {
                return;
            }

            double inset = 0.75;
            double drawWidth = Math.max(4.0, width - inset * 2.0);
            double drawHeight = Math.max(4.0, height - inset * 2.0);

            gc.setLineWidth(1.2);
            gc.setLineDashes(8.0, 5.0);
            gc.setLineDashOffset(lassoDashOffset);
            gc.setStroke(Color.web("#f0b673", 0.95));
            gc.strokeRoundRect(inset, inset, drawWidth, drawHeight, LASSO_ARC, LASSO_ARC);

            gc.setLineWidth(0.8);
            gc.setLineDashes(8.0, 5.0);
            gc.setLineDashOffset(lassoDashOffset - 6.5);
            gc.setStroke(Color.web("#fff4d6", 0.55));
            gc.strokeRoundRect(inset + 0.6, inset + 0.6, drawWidth - 1.2, drawHeight - 1.2, LASSO_ARC - 2.0, LASSO_ARC - 2.0);

            gc.setLineDashes((double[]) null);
        }

        private int computeLayerValue(String name, boolean isGroup) {
            if (project == null || name == null || name.isBlank()) return 0;
            if (!isGroup) return project.computeEffectiveLayerOrder(name);

            EntityGroup group = project.getGroup(name);
            if (group == null) return 0;

            int total = 0;
            Set<String> visited = new HashSet<>();
            String cursor = group.getName();
            while (cursor != null && visited.add(cursor)) {
                EntityGroup current = project.getGroup(cursor);
                if (current == null) break;
                total += current.getLayerOrder();
                cursor = current.getParentGroupName();
            }
            return total;
        }

        private void drawEntityIcon(GraphicsContext gc, String name, boolean isGroup) {
            gc.clearRect(0, 0, 16, 16);
            if (isGroup) {
                gc.setFill(Color.web("#f0b673", 0.6));
                gc.fillRoundRect(1, 2, 14, 12, 3, 3);
                gc.setFill(Color.web("#f0b673"));
                gc.fillRoundRect(1, 0, 8, 5, 2, 2);
                return;
            }
            if (scene == null) {
                gc.setFill(Color.web("#4da3ff", 0.7));
                gc.fillRoundRect(2, 2, 12, 12, 2, 2);
                return;
            }
            var entity = scene.find(name);
            if (entity instanceof com.jvn.core.scene2d.Sprite2D) {
                gc.setFill(Color.web("#58d68d"));
                gc.fillRect(2, 2, 12, 12);
                gc.setStroke(Color.web("#58d68d", 0.5));
                gc.strokeLine(3, 3, 13, 13);
                gc.strokeLine(13, 3, 3, 13);
            } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D) {
                gc.setFill(Color.web("#c77dff"));
                gc.fillOval(4, 1, 8, 8);
                gc.fillRoundRect(3, 9, 10, 6, 2, 2);
            } else if (entity instanceof com.jvn.core.scene2d.Label2D) {
                gc.setFill(Color.web("#f38ba8"));
                gc.setFont(javafx.scene.text.Font.font(11));
                gc.fillText("T", 4, 13);
            } else if (entity instanceof com.jvn.core.scene2d.Panel2D) {
                gc.setFill(Color.web("#f0b673", 0.5));
                gc.fillRect(2, 2, 12, 12);
                gc.setStroke(Color.web("#f0b673"));
                gc.strokeRect(2, 2, 12, 12);
            } else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D) {
                gc.setFill(Color.web("#4da3ff"));
                double[] xs = {3, 13, 13, 3};
                double[] ys = {3, 6, 10, 13};
                gc.fillPolygon(xs, ys, 4);
            } else {
                gc.setFill(Color.web("#4da3ff", 0.7));
                gc.fillRoundRect(2, 2, 12, 12, 2, 2);
            }
        }

        private void drawVisibilityIcon(GraphicsContext gc, boolean visible) {
            double w = visibilityIcon.getWidth();
            double h = visibilityIcon.getHeight();
            gc.clearRect(0, 0, w, h);

            Color stroke = visible ? Color.web("#9fb2cc") : Color.web("#6b7687");
            Color fill = visible ? Color.web("#d7e5ff", 0.45) : Color.web("#8a8f98", 0.22);
            double cx = w * 0.5;
            double cy = h * 0.5;

            gc.setLineWidth(1.2);
            gc.setStroke(stroke);
            gc.strokeOval(1.0, 3.0, w - 2.0, h - 6.0);

            gc.setFill(fill);
            gc.fillOval(cx - 2.0, cy - 2.0, 4.0, 4.0);

            if (!visible) {
                gc.setStroke(Color.web("#f0b673", 0.90));
                gc.setLineWidth(1.4);
                gc.strokeLine(2.0, h - 2.0, w - 2.0, 2.0);
            }
        }
    }

    @FunctionalInterface
    public interface AddToGroupRequest {
        void accept(String selectionName, boolean selectionIsGroup, String targetGroupName);
    }
}

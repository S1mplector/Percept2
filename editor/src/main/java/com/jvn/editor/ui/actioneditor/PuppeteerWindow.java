package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class PuppeteerWindow extends Stage {
    private final AnimationProject project;
    private JesScene2D scene;

    private final EntitySelector entitySelector;
    private final AssetPickerPanel assetPicker;
    private final TimelinePanel timelinePanel;
    private final KeyframeEditor keyframeEditor;
    private final AnimationPreview animationPreview;
    private final CodePreviewPane codePreview;

    private final Button btnPlay;
    private final Button btnPause;
    private final Button btnStop;
    private final Button btnRewind;
    private final TextField tfDuration;
    private final CheckBox cbLoop;
    private final Label lblTime;

    private AnimationTimer playbackTimer;
    private long lastNanos = 0;

    private final PuppeteerCommand.Stack commandStack = new PuppeteerCommand.Stack();
    private Consumer<String> onCopyCode;
    private final TextField tfTimelineName;

    public PuppeteerWindow() {
        this(new AnimationProject());
    }

    public PuppeteerWindow(AnimationProject project) {
        this.project = project != null ? project : new AnimationProject();

        setTitle("Puppeteer - " + this.project.getName());
        setWidth(1400);
        setHeight(900);

        this.project.captureInitialSnapshot();

        entitySelector = new EntitySelector();
        timelinePanel = new TimelinePanel(this.project);
        keyframeEditor = new KeyframeEditor();
        animationPreview = new AnimationPreview();
        animationPreview.setProject(this.project);
        codePreview = new CodePreviewPane();

        entitySelector.setOnEntitySelected(name -> {
            timelinePanel.setSelectedEntity(name);
        });

        entitySelector.setOnCreateGroup(groupName -> {
            this.project.getOrCreateGroup(groupName);
            entitySelector.refresh(this.project);
        });

        entitySelector.setOnAddToGroup((entityName, groupName) -> {
            this.project.addEntityToGroup(entityName, groupName);
            entitySelector.refresh(this.project);
            timelinePanel.refresh();
        });

        timelinePanel.setOnKeyframeSelected(kf -> {
            keyframeEditor.setKeyframe(kf, timelinePanel.getSelectedProperty());
        });

        timelinePanel.setOnPlayheadChanged(time -> {
            this.project.setPlayheadMs(time);
            updateTimeLabel();
            updatePreview();
            codePreview.setCode(CodeExporter.export(this.project));
        });

        keyframeEditor.setOnKeyframeChanged(() -> {
            timelinePanel.refresh();
            codePreview.setCode(CodeExporter.export(this.project));
        });

        keyframeEditor.setOnDeleteRequested(() -> {
            Keyframe kf = keyframeEditor.getCurrentKeyframe();
            PropertyType prop = keyframeEditor.getCurrentProperty();
            if (kf != null && prop != null && timelinePanel.getSelectedProperty() != null) {
                String entity = timelinePanel.getSelectedEntity();
                if (entity != null) {
                    EntityTrack track = this.project.getTrack(entity);
                    if (track != null) track.removeKeyframe(prop, kf);
                }
            }
            timelinePanel.refresh();
            codePreview.setCode(CodeExporter.export(this.project));
        });

        animationPreview.setOnEntitySelected(name -> {
            timelinePanel.setSelectedEntity(name);
            entitySelector.refresh(this.project);
        });

        animationPreview.setOnEntityMoved((name, pos) -> {
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            track.addKeyframe(PropertyType.X, new Keyframe(time, pos[0]));
            track.addKeyframe(PropertyType.Y, new Keyframe(time, pos[1]));
            timelinePanel.refresh();
            codePreview.setCode(CodeExporter.export(this.project));
        });

        codePreview.setOnCopy(() -> {
            String code = codePreview.getCode();
            copyToClipboard(code);
            if (onCopyCode != null) onCopyCode.accept(code);
        });

        // --- Transport controls ---
        btnRewind = makeToolbarButton("⏮", "Rewind (Home)", STYLE_BTN_DARK);
        btnPlay = makeToolbarButton("▶", "Play (Space)", STYLE_BTN_ACCENT);
        btnPause = makeToolbarButton("⏸", "Pause (Space)", STYLE_BTN_DARK);
        btnStop = makeToolbarButton("⏹", "Stop", STYLE_BTN_DARK);

        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());
        btnRewind.setOnAction(e -> rewind());

        lblTime = new Label("0 ms");
        lblTime.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 72; -fx-alignment: center;");

        HBox transportBox = new HBox(4, btnRewind, btnPlay, btnPause, btnStop, makeSpacer(6), lblTime);
        transportBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Duration controls ---
        tfDuration = new TextField(String.valueOf((int) this.project.getTotalDurationMs()));
        tfDuration.setPrefWidth(64);
        tfDuration.setStyle(STYLE_TEXT_FIELD);
        tfDuration.setOnAction(e -> {
            try {
                double dur = Double.parseDouble(tfDuration.getText());
                this.project.setTotalDurationMs(dur);
                timelinePanel.refresh();
            } catch (NumberFormatException ignored) {}
        });

        Button btnFitDuration = makeToolbarButton("Fit", "Fit duration to content", STYLE_BTN_DARK);
        btnFitDuration.setOnAction(e -> {
            this.project.fitDurationToContent();
            tfDuration.setText(String.valueOf((int) this.project.getTotalDurationMs()));
            timelinePanel.refresh();
        });

        cbLoop = new CheckBox("Loop");
        cbLoop.setSelected(this.project.isLooping());
        cbLoop.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        cbLoop.setOnAction(e -> this.project.setLooping(cbLoop.isSelected()));

        Label durLabel = makeToolbarLabel("Duration");
        Label msLabel = makeToolbarLabel("ms");
        HBox durationBox = new HBox(4, durLabel, tfDuration, msLabel, btnFitDuration, makeSpacer(4), cbLoop);
        durationBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Presets ---
        MenuButton presetMenu = buildPresetMenu();
        presetMenu.setStyle(STYLE_BTN_DARK);
        presetMenu.setTooltip(new Tooltip("Apply animation preset to selected entity"));

        // --- Timeline name + Register ---
        tfTimelineName = new TextField("my_animation");
        tfTimelineName.setPrefWidth(110);
        tfTimelineName.setPromptText("timeline_name");
        tfTimelineName.setStyle(STYLE_TEXT_FIELD);
        tfTimelineName.setTooltip(new Tooltip("Name for @external jes_timeline"));

        Button btnRegister = makeToolbarButton("Register", "Register timeline for VNS interop", STYLE_BTN_GREEN);
        btnRegister.setOnAction(e -> registerTimeline());

        Label nameLabel = makeToolbarLabel("Name");
        HBox nameBox = new HBox(4, nameLabel, tfTimelineName, btnRegister);
        nameBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Copy Code ---
        Button btnCopyCode = makeToolbarButton("Copy Code", "Copy generated code to clipboard (Cmd+C)", STYLE_BTN_ACCENT);
        btnCopyCode.setOnAction(e -> {
            String name = tfTimelineName.getText().trim();
            String code = name.isEmpty()
                ? CodeExporter.export(this.project)
                : CodeExporter.exportNamed(this.project, name);
            copyToClipboard(code);
            if (onCopyCode != null) onCopyCode.accept(code);
        });

        // --- Assemble toolbar ---
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(6,
            transportBox,
            makeVSep(),
            durationBox,
            makeVSep(),
            presetMenu,
            makeVSep(),
            nameBox,
            spacer,
            btnCopyCode
        );
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        toolbar.setStyle("-fx-background-color: #0a0a0a; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;");

        assetPicker = new AssetPickerPanel();
        assetPicker.setOnAddToScene((path, name) -> addAssetToScene(path, name));

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab assetsTab = new Tab("Assets", assetPicker);
        assetsTab.setClosable(false);
        TabPane leftTabs = new TabPane(entitiesTab, assetsTab);
        leftTabs.setTabMinWidth(60);
        leftTabs.setStyle("-fx-background-color: #1a1a1a;");

        SplitPane leftPane = new SplitPane();
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.getItems().addAll(leftTabs, keyframeEditor);
        leftPane.setDividerPositions(0.65);

        SplitPane centerPane = new SplitPane();
        centerPane.setOrientation(Orientation.VERTICAL);
        centerPane.getItems().addAll(animationPreview, timelinePanel);
        centerPane.setDividerPositions(0.4);

        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(leftPane, centerPane, codePreview);
        mainSplit.setDividerPositions(0.17, 0.78);

        // --- Shortcuts status bar ---
        Label shortcutsBar = new Label(
            "Space: Play/Pause   Home: Rewind   Ctrl+Z/Y: Undo/Redo   " +
            "Click timeline: Add keyframe   Drag preview: Move entity   Del: Delete keyframe"
        );
        shortcutsBar.setMaxWidth(Double.MAX_VALUE);
        shortcutsBar.setStyle("-fx-background-color: #0a0a0a; -fx-text-fill: #555; -fx-font-size: 10px; " +
            "-fx-padding: 4 10; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;");

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(mainSplit);
        root.setBottom(shortcutsBar);
        root.setStyle("-fx-background-color: #121212;");

        Scene fxScene = new Scene(root);
        EditorTheme.apply(fxScene);
        setScene(fxScene);

        setupKeyboardShortcuts(fxScene);
        setupPlaybackTimer();

        codePreview.setCode(CodeExporter.export(this.project));
    }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
        animationPreview.setScene(scene);
        if (scene != null) {
            for (String name : scene.names()) {
                project.getOrCreateTrack(name);
            }
            entitySelector.refresh(project);
            timelinePanel.refresh();
        }
    }

    private java.io.File projectRoot;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        animationPreview.setProjectRoot(root);
        assetPicker.setProjectRoot(root);
    }

    private void addAssetToScene(String relativePath, String suggestedName) {
        if (scene == null) {
            scene = new JesScene2D();
            animationPreview.setScene(scene);
        }

        // Deduplicate name
        String entityName = suggestedName;
        int suffix = 2;
        while (scene.find(entityName) != null) {
            entityName = suggestedName + "_" + suffix++;
        }

        // Load image to get dimensions
        double w = 200, h = 200;
        if (projectRoot != null) {
            java.io.File imgFile = new java.io.File(projectRoot, relativePath);
            if (imgFile.exists()) {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(
                        imgFile.toURI().toString(), 0, 0, true, false);
                    if (img.getWidth() > 0 && img.getHeight() > 0) {
                        w = img.getWidth();
                        h = img.getHeight();
                        // Scale down to fit within ~60% of scene height (720 * 0.6 = 432)
                        double maxH = 432;
                        if (h > maxH) {
                            double scale = maxH / h;
                            w *= scale;
                            h = maxH;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(relativePath, w, h);
        sprite.setOrigin(0.5, 0.5);
        sprite.setPosition(640, 360);

        scene.add(sprite);
        scene.registerEntity(entityName, sprite);

        project.getOrCreateTrack(entityName);
        entitySelector.refresh(project);
        timelinePanel.refresh();
        animationPreview.render();
        codePreview.setCode(CodeExporter.export(project));
    }

    public AnimationProject getProject() { return project; }

    public void setOnCopyCode(Consumer<String> callback) { this.onCopyCode = callback; }

    private void play() {
        if (project.isPlaying()) return;
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimer.start();
        btnPlay.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
        btnPlay.setDisable(true);
        btnPause.setStyle(STYLE_BTN_ACCENT);
        btnPause.setDisable(false);
    }

    private void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        btnPlay.setStyle(STYLE_BTN_ACCENT);
        btnPlay.setDisable(false);
        btnPause.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
        btnPause.setDisable(true);
    }

    private void stop() {
        pause();
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void rewind() {
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void setupPlaybackTimer() {
        playbackTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!project.isPlaying()) return;
                long deltaNanos = now - lastNanos;
                lastNanos = now;
                double deltaMs = deltaNanos / 1_000_000.0;

                double newTime = project.getPlayheadMs() + deltaMs;
                if (newTime >= project.getTotalDurationMs()) {
                    if (project.isLooping()) {
                        newTime = 0;
                    } else {
                        newTime = project.getTotalDurationMs();
                        pause();
                    }
                }
                project.setPlayheadMs(newTime);
                timelinePanel.setPlayhead(newTime);
                updateTimeLabel();
                updatePreview();
            }
        };
        btnPause.setDisable(true);
    }

    private void updateTimeLabel() {
        lblTime.setText(String.format("%.0f ms", project.getPlayheadMs()));
    }

    private void updatePreview() {
        if (scene == null) return;

        double time = project.getPlayheadMs();
        for (EntityTrack track : project.getTracks()) {
            var entity = scene.find(track.getEntityName());
            if (entity == null) continue;

            if (track.hasKeyframes(PropertyType.X) || track.hasKeyframes(PropertyType.Y)) {
                double x = project.computeValueAt(track.getEntityName(), PropertyType.X, time);
                double y = project.computeValueAt(track.getEntityName(), PropertyType.Y, time);
                entity.setPosition(x, y);
            }
            if (track.hasKeyframes(PropertyType.ROTATION)) {
                double rot = project.computeValueAt(track.getEntityName(), PropertyType.ROTATION, time);
                entity.setRotationDeg(rot);
            }
            if (track.hasKeyframes(PropertyType.SCALE_X) || track.hasKeyframes(PropertyType.SCALE_Y)) {
                double sx = project.computeValueAt(track.getEntityName(), PropertyType.SCALE_X, time);
                double sy = project.computeValueAt(track.getEntityName(), PropertyType.SCALE_Y, time);
                entity.setScale(sx, sy);
            }
            if (track.hasKeyframes(PropertyType.ALPHA)) {
                double alpha = project.computeValueAt(track.getEntityName(), PropertyType.ALPHA, time);
                setEntityAlpha(entity, alpha);
            }
        }

        animationPreview.render();
    }

    private void setEntityAlpha(com.jvn.core.scene2d.Entity2D entity, double alpha) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.Label2D l) 
            l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), alpha);
        else if (entity instanceof com.jvn.core.scene2d.Panel2D p)
            p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), alpha);
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.SPACE),
            () -> { if (project.isPlaying()) pause(); else play(); }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.HOME),
            this::rewind
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K),
            () -> timelinePanel.addKeyframeAtPlayhead()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.DELETE),
            () -> timelinePanel.deleteSelectedKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN),
            () -> {
                String code = CodeExporter.export(project);
                copyToClipboard(code);
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
            () -> {
                commandStack.undo();
                timelinePanel.refresh();
                codePreview.setCode(CodeExporter.export(project));
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                commandStack.redo();
                timelinePanel.refresh();
                codePreview.setCode(CodeExporter.export(project));
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            () -> animationPreview.setOnionSkinning(!animationPreview.isOnionSkinning())
        );
    }

    private MenuButton buildPresetMenu() {
        MenuButton mb = new MenuButton("Presets");
        mb.setTooltip(new Tooltip("Apply animation preset to selected entity"));

        String lastCategory = "";
        for (AnimationPreset preset : AnimationPreset.ALL) {
            if (!preset.getCategory().equals(lastCategory)) {
                if (!lastCategory.isEmpty()) mb.getItems().add(new SeparatorMenuItem());
                lastCategory = preset.getCategory();
            }
            MenuItem mi = new MenuItem(preset.getName() + "  [" + preset.getCategory() + "]");
            mi.setOnAction(e -> applyPreset(preset));
            mb.getItems().add(mi);
        }
        return mb;
    }

    private void applyPreset(AnimationPreset preset) {
        String entity = timelinePanel.getSelectedEntity();
        if (entity == null) return;
        EntityTrack track = project.getOrCreateTrack(entity);
        double startTime = project.getPlayheadMs();
        commandStack.execute(PuppeteerCommand.applyPreset(track, preset, startTime));
        timelinePanel.refresh();
        codePreview.setCode(CodeExporter.export(project));
    }

    public PuppeteerCommand.Stack getCommandStack() { return commandStack; }

    // --- Toolbar styling helpers ---

    private static final String STYLE_BTN_DARK =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; " +
        "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #5bb3ff; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_BTN_GREEN =
        "-fx-background-color: #58d68d; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #68e69d; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_TEXT_FIELD =
        "-fx-background-color: #1a1a1a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";

    private static Button makeToolbarButton(String text, String tooltip, String style) {
        Button btn = new Button(text);
        btn.setStyle(style);
        btn.setTooltip(new Tooltip(tooltip));
        final String baseStyle = style;
        btn.setOnMouseEntered(e -> btn.setStyle(baseStyle + "-fx-opacity: 0.85;"));
        btn.setOnMouseExited(e -> btn.setStyle(baseStyle));
        return btn;
    }

    private static Label makeToolbarLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        return lbl;
    }

    private static Region makeSpacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private static Separator makeVSep() {
        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-padding: 0 2;");
        return sep;
    }

    private void registerTimeline() {
        String name = tfTimelineName.getText().trim();
        if (name.isEmpty()) return;
        TimelineData data = project.toTimelineData(name);
        TimelineRegistry.register(data);
        codePreview.setCode(CodeExporter.exportNamed(project, name));
        setTitle("Puppeteer - " + name + " (registered)");
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public void close() {
        if (playbackTimer != null) playbackTimer.stop();
        super.close();
    }
}

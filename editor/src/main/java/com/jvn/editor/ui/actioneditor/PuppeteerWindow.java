package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

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

    private Consumer<String> onCopyCode;

    public PuppeteerWindow() {
        this(new AnimationProject());
    }

    public PuppeteerWindow(AnimationProject project) {
        this.project = project != null ? project : new AnimationProject();

        setTitle("Puppeteer - " + this.project.getName());
        setWidth(1400);
        setHeight(900);

        entitySelector = new EntitySelector();
        timelinePanel = new TimelinePanel(this.project);
        keyframeEditor = new KeyframeEditor();
        animationPreview = new AnimationPreview();
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

        btnPlay = new Button("▶");
        btnPause = new Button("⏸");
        btnStop = new Button("⏹");
        btnRewind = new Button("⏮");
        tfDuration = new TextField(String.valueOf((int) this.project.getTotalDurationMs()));
        tfDuration.setPrefWidth(70);
        cbLoop = new CheckBox("Loop");
        cbLoop.setSelected(this.project.isLooping());
        lblTime = new Label("0 ms");

        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());
        btnRewind.setOnAction(e -> rewind());

        tfDuration.setOnAction(e -> {
            try {
                double dur = Double.parseDouble(tfDuration.getText());
                this.project.setTotalDurationMs(dur);
                timelinePanel.refresh();
            } catch (NumberFormatException ignored) {}
        });

        cbLoop.setOnAction(e -> this.project.setLooping(cbLoop.isSelected()));

        Button btnCopyCode = new Button("Copy Code");
        btnCopyCode.setOnAction(e -> {
            String code = CodeExporter.export(this.project);
            copyToClipboard(code);
            if (onCopyCode != null) onCopyCode.accept(code);
        });

        Button btnFitDuration = new Button("Fit");
        btnFitDuration.setTooltip(new Tooltip("Fit duration to content"));
        btnFitDuration.setOnAction(e -> {
            this.project.fitDurationToContent();
            tfDuration.setText(String.valueOf((int) this.project.getTotalDurationMs()));
            timelinePanel.refresh();
        });

        MenuButton presetMenu = buildPresetMenu();

        HBox toolbar = new HBox(8,
            btnRewind, btnPlay, btnPause, btnStop,
            new Separator(Orientation.VERTICAL),
            new Label("Duration:"), tfDuration, new Label("ms"), btnFitDuration,
            new Separator(Orientation.VERTICAL),
            cbLoop,
            new Separator(Orientation.VERTICAL),
            presetMenu,
            new Separator(Orientation.VERTICAL),
            lblTime,
            new Region(),
            btnCopyCode
        );
        HBox.setHgrow(toolbar.getChildren().get(toolbar.getChildren().size() - 2), Priority.ALWAYS);
        toolbar.setPadding(new Insets(8));
        toolbar.setStyle("-fx-background-color: #1e1e2e;");

        SplitPane leftPane = new SplitPane();
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.getItems().addAll(entitySelector, keyframeEditor);
        leftPane.setDividerPositions(0.7);

        SplitPane centerPane = new SplitPane();
        centerPane.setOrientation(Orientation.VERTICAL);
        centerPane.getItems().addAll(animationPreview, timelinePanel);
        centerPane.setDividerPositions(0.45);

        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(leftPane, centerPane, codePreview);
        mainSplit.setDividerPositions(0.18, 0.75);

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(mainSplit);
        root.setStyle("-fx-background-color: #11111b;");

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

    public AnimationProject getProject() { return project; }

    public void setOnCopyCode(Consumer<String> callback) { this.onCopyCode = callback; }

    private void play() {
        if (project.isPlaying()) return;
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimer.start();
        btnPlay.setDisable(true);
        btnPause.setDisable(false);
    }

    private void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        btnPlay.setDisable(false);
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
        preset.applyTo(track, startTime);
        timelinePanel.refresh();
        codePreview.setCode(CodeExporter.export(project));
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

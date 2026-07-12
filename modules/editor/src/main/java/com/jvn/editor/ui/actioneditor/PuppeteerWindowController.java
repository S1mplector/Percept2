package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.actioneditor.TimelinePanel;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for {@link PuppeteerWindow}.  Owns all event-handler logic that was
 * previously inlined directly inside the view class (button click handlers, menu
 * action handlers, keyboard shortcuts).
 *
 * <p>Create via the {@link #bind(PuppeteerWindow)} factory; the constructor wires
 * every handler against the supplied view reference.</p>
 */
public final class PuppeteerWindowController {

    private static final Logger log = LoggerFactory.getLogger(PuppeteerWindowController.class);

    private final PuppeteerWindow view;

    // -----------------------------------------------------------------------
    // Construction / factory
    // -----------------------------------------------------------------------

    private PuppeteerWindowController(PuppeteerWindow view) {
        this.view = Objects.requireNonNull(view, "view must not be null");
    }

    /**
     * Creates a controller, wires all handlers against {@code view}, and returns
     * the controller instance.  Call this at the very end of
     * {@code PuppeteerWindow}'s constructor.
     */
    public static PuppeteerWindowController bind(PuppeteerWindow view) {
        PuppeteerWindowController ctrl = new PuppeteerWindowController(view);
        ctrl.bindTransportButtons();
        ctrl.bindKeyboardShortcuts(view.getScene());
        return ctrl;
    }

    // -----------------------------------------------------------------------
    // Transport / playback controls
    // -----------------------------------------------------------------------

    private void bindTransportButtons() {
        view.btnPlay.setOnAction(e -> play());
        view.btnPause.setOnAction(e -> pause());
        view.btnStop.setOnAction(e -> stop());
        view.btnRewind.setOnAction(e -> rewind());
        view.btnUndo.setOnAction(e -> executeUndo());
        view.btnRedo.setOnAction(e -> executeRedo());
    }

    void play() {
        if (view.project.isPlaying()) return;
        view.project.setPlaying(true);
        view.lastNanos = System.nanoTime();
        view.playbackTimer.start();
        view.refreshTransportButtonStates();
    }

    void pause() {
        view.project.setPlaying(false);
        view.playbackTimer.stop();
        view.refreshTransportButtonStates();
    }

    void stop() {
        pause();
        view.project.setPlayheadMs(0);
        view.timelinePanel.setPlayhead(0);
        view.updateTimeLabel();
        view.updatePreview();
    }

    void rewind() {
        view.project.setPlayheadMs(0);
        view.timelinePanel.setPlayhead(0);
        view.updateTimeLabel();
        view.updatePreview();
    }

    // -----------------------------------------------------------------------
    // Undo / redo
    // -----------------------------------------------------------------------

    void executeUndo() {
        if (!view.commandStack.canUndo()) return;
        view.commandStack.undo();
        view.timelinePanel.refresh();
        view.updatePreview();
        view.refreshExportPreviewAndMarkDirty();
        view.refreshUndoRedoControls();
    }

    void executeRedo() {
        if (!view.commandStack.canRedo()) return;
        view.commandStack.redo();
        view.timelinePanel.refresh();
        view.updatePreview();
        view.refreshExportPreviewAndMarkDirty();
        view.refreshUndoRedoControls();
    }

    // -----------------------------------------------------------------------
    // Clipboard / keyframe operations
    // -----------------------------------------------------------------------

    void copySelectedKeyframesToClipboard() {
        view.timelinePanel.copySelectedKeyframes();
        List<TimelinePanel.ClipboardEntry> current = view.timelinePanel.getCopiedKeyframes();
        if (!current.isEmpty()) {
            view.clipboardHistory.add(0, List.copyOf(current));
            if (view.clipboardHistory.size() > PuppeteerWindow.MAX_CLIPBOARD_HISTORY) {
                view.clipboardHistory.remove(view.clipboardHistory.size() - 1);
            }
        }
        view.refreshToolbarCommandSummary();
    }

    void pasteCopiedKeyframesAtPlayhead() {
        view.timelinePanel.pasteCopiedKeyframesAtPlayhead();
        view.refreshToolbarCommandSummary();
    }

    void duplicateSelectedKeyframesBySnapStep() {
        double delta = Math.max(1.0, view.timelinePanel.getSnapStepMs());
        view.timelinePanel.duplicateSelectedKeyframes(delta);
        view.refreshToolbarCommandSummary();
    }

    void copyExportedCodeToClipboard() {
        view.copyExportedCodeToClipboard();
    }

    // -----------------------------------------------------------------------
    // Keyboard shortcuts
    // -----------------------------------------------------------------------

    void bindKeyboardShortcuts(Scene scene) {
        if (scene == null) return;
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.SPACE),
            () -> { if (view.project.isPlaying()) pause(); else play(); }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.HOME),
            this::rewind
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.ESCAPE),
            () -> {
                if (view.isPreviewFullscreenActive()) {
                    view.exitFullscreenPreview();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_UP),
            () -> view.timelinePanel.jumpPlayheadToPreviousKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_DOWN),
            () -> view.timelinePanel.jumpPlayheadToNextKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            () -> view.timelinePanel.zoomToSelection()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K),
            () -> {
                view.timelinePanel.addKeyframeAtPlayhead();
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.DELETE),
            () -> {
                view.timelinePanel.deleteSelectedKeyframe();
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            this::copyExportedCodeToClipboard
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::copySelectedKeyframesToClipboard
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::pasteCopiedKeyframesAtPlayhead
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::duplicateSelectedKeyframesBySnapStep
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN),
            this::executeUndo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            () -> view.animationPreview.setOnionSkinning(!view.animationPreview.isOnionSkinning())
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
            () -> {
                view.timelinePanel.nudgeSelectedKeyframes(-view.timelinePanel.getSnapStepMs());
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
            () -> {
                view.timelinePanel.nudgeSelectedKeyframes(view.timelinePanel.getSnapStepMs());
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                view.timelinePanel.nudgeSelectedKeyframes(-1.0);
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                view.timelinePanel.nudgeSelectedKeyframes(1.0);
                view.refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (view.timelinePanel.reverseSelectedKeyframes()) {
                    view.refreshExportPreviewAndMarkDirty();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.E, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (view.timelinePanel.distributeSelectedKeyframes()) {
                    view.refreshExportPreviewAndMarkDirty();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A),
            () -> {
                if (view.cbOrbitTool == null) return;
                view.cbOrbitTool.setSelected(!view.cbOrbitTool.isSelected());
                view.animationPreview.setOrbitToolEnabled(view.cbOrbitTool.isSelected());
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN),
            () -> {
                view.animationPreview.clearOrbitAnchorForSelectedEntity();
                view.updatePreview();
            }
        );
    }

    // -----------------------------------------------------------------------
    // Menu bar construction
    // -----------------------------------------------------------------------

    /**
     * Builds and returns the menu bar + command bar HBox.  Extracted verbatim
     * from the former {@code buildToolbarCommandBar()} private method in
     * {@code PuppeteerWindow}.
     */
    HBox buildToolbarCommandBar() {
        MenuItem miSaveRegister = new MenuItem("Save & Register");
        miSaveRegister.setOnAction(e -> requestRegisterTimeline());
        MenuItem miVerifyRuntime = new MenuItem("Verify Runtime Registration...");
        miVerifyRuntime.setOnAction(e -> showRuntimeVerificationReport());
        MenuItem miRefreshCode = new MenuItem("Refresh Generated Code");
        miRefreshCode.setOnAction(e -> view.requestRefreshGeneratedCode());
        MenuItem miStagePreview = new MenuItem("Stage Code Preview");
        miStagePreview.setOnAction(e -> view.stagePreviewFromCode());
        MenuItem miCommitPreview = new MenuItem("Commit Staged Preview");
        miCommitPreview.setOnAction(e -> view.commitStagedPreview());
        MenuItem miDiscardPreview = new MenuItem("Discard Staged Preview");
        miDiscardPreview.setOnAction(e -> view.discardStagedPreview());
        MenuItem miCopyExportCode = new MenuItem("Copy Exported Code");
        miCopyExportCode.setOnAction(e -> copyExportedCodeToClipboard());
        MenuItem miSaveClip = new MenuItem("Save Selection as Clip");
        miSaveClip.setOnAction(e -> saveSelectionAsClip());
        MenuItem miLoadClip = new MenuItem("Load Clip at Playhead");
        miLoadClip.setOnAction(e -> loadAndApplyClip());
        MenuItem miImportAssets = new MenuItem("Import Assets...");
        miImportAssets.setOnAction(e -> view.showAssetImporterWindow());
        MenuItem miRecordGif = new MenuItem("Record Preview as GIF...");
        miRecordGif.setOnAction(e -> showRecordGifDialog());
        MenuItem miClose = new MenuItem("Close Puppeteer");
        miClose.setOnAction(e -> requestWindowClose());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
            miSaveRegister,
            miVerifyRuntime,
            new SeparatorMenuItem(),
            miRefreshCode,
            miStagePreview,
            miCommitPreview,
            miDiscardPreview,
            new SeparatorMenuItem(),
            miCopyExportCode,
            miSaveClip,
            miLoadClip,
            miImportAssets,
            miRecordGif,
            new SeparatorMenuItem(),
            miClose
        );
        fileMenu.setOnShowing(e -> {
            String timelineName = view.tfTimelineName != null ? view.tfTimelineName.getText().trim() : "";
            boolean hasTimelineName = timelineName != null && !timelineName.isBlank();
            boolean hasTrack = view.selectedTrackForEditing(false) != null;
            miSaveRegister.setText(view.dirty || view.previewStaged ? "Save & Register" : "Save & Register Again");
            miSaveRegister.setDisable(!hasTimelineName);
            miVerifyRuntime.setDisable(view.project == null);
            miStagePreview.setText(view.previewStaged ? "Restage Code Preview" : "Stage Code Preview");
            miStagePreview.setDisable(view.codePreview == null || view.codePreview.getCode() == null || view.codePreview.getCode().isBlank());
            miCommitPreview.setDisable(!view.previewStaged);
            miDiscardPreview.setDisable(!view.previewStaged);
            miCopyExportCode.setDisable(view.codePreview == null || view.codePreview.getCode() == null || view.codePreview.getCode().isBlank());
            miSaveClip.setDisable(!hasTrack || view.projectRoot == null);
            miLoadClip.setDisable(!hasTrack || !view.hasSavedClips());
            miImportAssets.setDisable(view.projectRoot == null || !view.projectRoot.isDirectory());
            miClose.setText(view.dirty || view.previewStaged ? "Close..." : "Close");
        });

        MenuItem miUndo = new MenuItem("Undo");
        miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        miUndo.setOnAction(e -> executeUndo());
        MenuItem miRedo = new MenuItem("Redo");
        miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        miRedo.setOnAction(e -> executeRedo());
        MenuItem miAddKeyframe = new MenuItem("Add Keyframe at Playhead");
        miAddKeyframe.setOnAction(e -> view.timelinePanel.addKeyframeAtPlayhead());
        MenuItem miDeleteKeyframes = new MenuItem("Delete Selected Keyframes");
        miDeleteKeyframes.setOnAction(e -> view.timelinePanel.deleteSelectedKeyframe());
        MenuItem miCopyKeyframes = new MenuItem("Copy Keyframes");
        miCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        MenuItem miPasteKeyframes = new MenuItem("Paste Keyframes at Playhead");
        miPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        MenuItem miDuplicateKeyframes = new MenuItem("Duplicate Keyframes");
        miDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        MenuItem miDistributeKeyframes = new MenuItem("Distribute Selected Keyframes");
        miDistributeKeyframes.setOnAction(e -> view.timelinePanel.distributeSelectedKeyframes());
        MenuItem miReverseKeyframes = new MenuItem("Reverse Selected Keyframes");
        miReverseKeyframes.setOnAction(e -> view.timelinePanel.reverseSelectedKeyframes());
        MenuItem miApplyPreset = new MenuItem("Apply Animation Preset...");
        miApplyPreset.setOnAction(e -> showPresetMenuOverlay());
        MenuItem miPlaceInSlot = new MenuItem("Place Entity in VN Slot...");
        miPlaceInSlot.setOnAction(e -> showSlotMenuOverlay());

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
            miUndo,
            miRedo,
            new SeparatorMenuItem(),
            miAddKeyframe,
            miDeleteKeyframes,
            new SeparatorMenuItem(),
            miCopyKeyframes,
            miPasteKeyframes,
            miDuplicateKeyframes,
            new SeparatorMenuItem(),
            miDistributeKeyframes,
            miReverseKeyframes,
            new SeparatorMenuItem(),
            miApplyPreset,
            miPlaceInSlot
        );
        editMenu.setOnShowing(e -> {
            int selectionCount = view.timelinePanel != null ? view.timelinePanel.getSelectionCount() : 0;
            boolean hasSelection = selectionCount > 0;
            boolean hasTarget = view.timelinePanel != null
                && view.timelinePanel.getSelectedEntity() != null
                && !view.timelinePanel.getSelectedEntity().isBlank();
            boolean entityTarget = hasTarget && !view.timelinePanel.isSelectedGroup();
            boolean hasEditableKeyframe = hasSelection || (view.keyframeEditor != null && view.keyframeEditor.getCurrentKeyframe() != null);

            miUndo.setText(view.commandStack.canUndo()
                ? "Undo " + view.commandStack.undoDescription()
                : "Undo");
            miRedo.setText(view.commandStack.canRedo()
                ? "Redo " + view.commandStack.redoDescription()
                : "Redo");
            miUndo.setDisable(!view.commandStack.canUndo());
            miRedo.setDisable(!view.commandStack.canRedo());
            miAddKeyframe.setDisable(!hasTarget);
            miDeleteKeyframes.setDisable(!hasEditableKeyframe);
            miCopyKeyframes.setDisable(!hasSelection);
            miPasteKeyframes.setDisable(!hasTarget || view.timelinePanel.getCopiedKeyframeCount() == 0);
            miDuplicateKeyframes.setDisable(!hasSelection);
            miDistributeKeyframes.setDisable(selectionCount < 3);
            miReverseKeyframes.setDisable(selectionCount < 2);
            miApplyPreset.setDisable(!entityTarget);
            miPlaceInSlot.setDisable(!entityTarget);
        });

        // === Timeline menu ===
        MenuItem miJumpStart = new MenuItem("Jump to Start");
        miJumpStart.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        miJumpStart.setOnAction(e -> rewind());

        MenuItem miJumpEnd = new MenuItem("Jump to End");
        miJumpEnd.setAccelerator(new KeyCodeCombination(KeyCode.END));
        miJumpEnd.setOnAction(e -> {
            if (view.project == null) return;
            view.project.setPlayheadMs(view.project.getTotalDurationMs());
            view.updateTimeLabel();
            view.updatePreview();
            view.timelinePanel.refresh();
        });

        MenuItem miTimelinePrevKey = new MenuItem("Previous Keyframe");
        miTimelinePrevKey.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN));
        miTimelinePrevKey.setOnAction(e -> {
            if (view.timelinePanel.jumpPlayheadToPreviousKeyframe()) { view.updateTimeLabel(); view.updatePreview(); }
            view.refreshSidebarTabs();
        });

        MenuItem miTimelineNextKey = new MenuItem("Next Keyframe");
        miTimelineNextKey.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN));
        miTimelineNextKey.setOnAction(e -> {
            if (view.timelinePanel.jumpPlayheadToNextKeyframe()) { view.updateTimeLabel(); view.updatePreview(); }
            view.refreshSidebarTabs();
        });

        MenuItem miTimelineFocusSel = new MenuItem("Focus Timeline on Selection");
        miTimelineFocusSel.setOnAction(e -> view.timelinePanel.zoomToSelection());
        MenuItem miTimelineZoomFit = new MenuItem("Zoom Timeline to Fit");
        miTimelineZoomFit.setOnAction(e -> view.timelinePanel.zoomToFit());

        Menu timelineMenu = new Menu("Timeline");
        timelineMenu.getItems().addAll(
            miJumpStart, miJumpEnd,
            new SeparatorMenuItem(),
            miTimelinePrevKey, miTimelineNextKey,
            new SeparatorMenuItem(),
            miTimelineFocusSel, miTimelineZoomFit
        );
        timelineMenu.setOnShowing(e -> {
            boolean hasTarget = view.timelinePanel != null && view.timelinePanel.getSelectedEntity() != null
                && !view.timelinePanel.getSelectedEntity().isBlank();
            boolean hasSelection = view.timelinePanel != null && view.timelinePanel.getSelectionCount() > 0;
            miTimelinePrevKey.setDisable(!hasTarget);
            miTimelineNextKey.setDisable(!hasTarget);
            miTimelineFocusSel.setDisable(!hasSelection);
        });

        // === Scene menu ===
        MenuItem miSceneImport = new MenuItem("Import Assets...");
        miSceneImport.setOnAction(e -> view.showAssetImporterWindow());

        MenuItem miSceneAddCue = new MenuItem("Add Audio Cue at Playhead");
        miSceneAddCue.setOnAction(e -> showAddAudioCueDialog());

        MenuItem miSceneClearCues = new MenuItem("Clear All Audio Cues");
        miSceneClearCues.setOnAction(e -> {
            if (view.project == null || view.project.getAudioCues().isEmpty()) return;
            view.overlayDialog.showDialog("Clear Audio Cues",
                "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", view.overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    view.project.clearAudioCues();
                    view.timelinePanel.refresh();
                    view.refreshExportPreviewAndMarkDirty();
                }));
        });

        MenuItem miSceneManageEvents = new MenuItem("Manage Event Cues...");
        miSceneManageEvents.setOnAction(e -> showEventCueManagerDialog(null));

        MenuItem miSceneClearEvents = new MenuItem("Clear All Event Cues");
        miSceneClearEvents.setOnAction(e -> {
            if (view.project == null || view.project.getEditorEventCues().isEmpty()) return;
            view.overlayDialog.showDialog("Clear Event Cues",
                "Remove all timeline event cues from this animation?",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", view.overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    view.project.clearEditorEventCues();
                    view.timelinePanel.refresh();
                    view.updatePreview();
                    view.refreshExportPreviewAndMarkDirty();
                }));
        });

        MenuItem miSceneFitPreview = new MenuItem("Fit Preview Viewport");
        miSceneFitPreview.setOnAction(e -> view.animationPreview.fitToContent());

        Menu sceneMenu = new Menu("Scene");
        sceneMenu.getItems().addAll(
            miSceneImport,
            new SeparatorMenuItem(),
            miSceneAddCue, miSceneClearCues,
            new SeparatorMenuItem(),
            miSceneManageEvents, miSceneClearEvents,
            new SeparatorMenuItem(),
            miSceneFitPreview
        );
        sceneMenu.setOnShowing(e -> {
            boolean hasRoot = view.projectRoot != null && view.projectRoot.isDirectory();
            miSceneImport.setDisable(!hasRoot);
            miSceneClearCues.setDisable(view.project == null || view.project.getAudioCues().isEmpty());
            miSceneClearEvents.setDisable(view.project == null || view.project.getEditorEventCues().isEmpty());
        });

        CheckMenuItem miShowCodePane = new CheckMenuItem("Show Code Pane");
        miShowCodePane.setOnAction(e -> view.setCodePaneVisible(miShowCodePane.isSelected()));
        CheckMenuItem miOnionSkin = new CheckMenuItem("Onion Skin Preview");
        miOnionSkin.setOnAction(e -> view.animationPreview.setOnionSkinning(miOnionSkin.isSelected()));
        CheckMenuItem miInterpolationGhosts = new CheckMenuItem("Interpolation Ghosts");
        miInterpolationGhosts.setOnAction(e -> view.animationPreview.setShowInterpolationGhosts(miInterpolationGhosts.isSelected()));
        CheckMenuItem miShowSafeGuides = new CheckMenuItem("Show Safe Guides");
        miShowSafeGuides.setOnAction(e -> view.animationPreview.setShowSafeGuides(miShowSafeGuides.isSelected()));
        CheckMenuItem miShowTitleGuides = new CheckMenuItem("Show Title Guides");
        miShowTitleGuides.setOnAction(e -> view.animationPreview.setShowTitleGuides(miShowTitleGuides.isSelected()));
        RadioMenuItem miLayoutDynamic = new RadioMenuItem("Toolbar Layout: Dynamic");
        RadioMenuItem miLayoutCompact = new RadioMenuItem("Toolbar Layout: Compact");
        ToggleGroup layoutMenuGroup = new ToggleGroup();
        miLayoutDynamic.setToggleGroup(layoutMenuGroup);
        miLayoutCompact.setToggleGroup(layoutMenuGroup);
        miLayoutDynamic.setOnAction(e -> view.setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.DYNAMIC));
        miLayoutCompact.setOnAction(e -> view.setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT));
        MenuItem miFocusTimeline = new MenuItem("Focus Timeline on Selection");
        miFocusTimeline.setOnAction(e -> view.timelinePanel.zoomToSelection());
        MenuItem miZoomFit = new MenuItem("Zoom Timeline to Fit");
        miZoomFit.setOnAction(e -> view.timelinePanel.zoomToFit());
        MenuItem miFullscreenPreview = new MenuItem("Toggle Focused Preview Layout");
        miFullscreenPreview.setOnAction(e -> view.togglePreviewFocusMode());

        MenuItem miExpandAll = new MenuItem("Expand All Clusters");
        miExpandAll.setOnAction(e -> view.toolbarClusters.values().forEach(c -> c.setExpanded(true)));
        MenuItem miCollapseAll = new MenuItem("Collapse All Clusters");
        miCollapseAll.setOnAction(e -> view.toolbarClusters.values().forEach(c -> c.setExpanded(false)));

        Menu toolbarClustersMenu = new Menu("Toolbar Clusters");
        toolbarClustersMenu.setOnShowing(e -> {
            toolbarClustersMenu.getItems().clear();
            for (CollapsibleToolbarCluster cluster : view.toolbarClusters.values()) {
                CheckMenuItem mi = new CheckMenuItem(cluster.getTitle());
                mi.setSelected(cluster.isExpanded());
                mi.setOnAction(ev -> cluster.setExpanded(mi.isSelected()));
                toolbarClustersMenu.getItems().add(mi);
            }
        });

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
            miShowCodePane,
            miOnionSkin,
            miInterpolationGhosts,
            miShowSafeGuides,
            miShowTitleGuides,
            new SeparatorMenuItem(),
            miLayoutDynamic,
            miLayoutCompact,
            new SeparatorMenuItem(),
            toolbarClustersMenu,
            miExpandAll,
            miCollapseAll,
            new SeparatorMenuItem(),
            miFocusTimeline,
            miZoomFit,
            miFullscreenPreview
        );
        viewMenu.setOnShowing(e -> {
            miShowCodePane.setSelected(view.codePaneVisible);
            miOnionSkin.setSelected(view.animationPreview.isOnionSkinning());
            miInterpolationGhosts.setSelected(view.animationPreview.isShowInterpolationGhosts());
            miShowSafeGuides.setSelected(view.animationPreview.isShowSafeGuides());
            miShowTitleGuides.setSelected(view.animationPreview.isShowTitleGuides());
            miLayoutDynamic.setSelected(view.getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.DYNAMIC);
            miLayoutCompact.setSelected(view.getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT);
            miFullscreenPreview.setDisable(view.scene == null && !view.isPreviewFullscreenActive());
        });

        MenuItem miPlayPause = new MenuItem("Play");
        miPlayPause.setOnAction(e -> {
            if (view.project.isPlaying()) pause();
            else play();
        });
        MenuItem miStop = new MenuItem("Stop");
        miStop.setOnAction(e -> stop());
        MenuItem miRewind = new MenuItem("Rewind");
        miRewind.setOnAction(e -> rewind());
        CheckMenuItem miLoopPlayback = new CheckMenuItem("Loop Playback");
        miLoopPlayback.setOnAction(e -> {
            view.project.setLooping(miLoopPlayback.isSelected());
            if (view.cbLoop != null) view.cbLoop.setSelected(miLoopPlayback.isSelected());
            view.refreshExportPreviewAndMarkDirty();
        });
        MenuItem miLoopIn = new MenuItem("Set Loop In at Playhead");
        miLoopIn.setOnAction(e -> {
            double inMs = view.project.getPlayheadMs();
            double outMs = view.project.hasLoopRegion() ? view.project.getLoopEndMs() : view.project.getTotalDurationMs();
            if (inMs < outMs) {
                view.project.setLoopRegion(inMs, outMs);
                view.timelinePanel.refresh();
                view.refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopOut = new MenuItem("Set Loop Out at Playhead");
        miLoopOut.setOnAction(e -> {
            double outMs = view.project.getPlayheadMs();
            double inMs = view.project.hasLoopRegion() ? view.project.getLoopStartMs() : 0.0;
            if (outMs > inMs) {
                view.project.setLoopRegion(inMs, outMs);
                view.timelinePanel.refresh();
                view.refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopClear = new MenuItem("Clear Loop Region");
        miLoopClear.setOnAction(e -> {
            view.project.clearLoopRegion();
            view.timelinePanel.refresh();
            view.refreshExportPreviewAndMarkDirty();
        });

        Menu playbackMenu = new Menu("Playback");
        playbackMenu.getItems().addAll(
            miPlayPause,
            miStop,
            miRewind,
            new SeparatorMenuItem(),
            miLoopPlayback,
            miLoopIn,
            miLoopOut,
            miLoopClear
        );
        playbackMenu.setOnShowing(e -> {
            miPlayPause.setText(view.project.isPlaying() ? "Pause" : "Play");
            miLoopPlayback.setSelected(view.project.isLooping());
            miLoopClear.setDisable(!view.project.hasLoopRegion());
        });

        MenuItem miShowShortcuts = new MenuItem("Keyboard Shortcuts");
        miShowShortcuts.setOnAction(e -> showShortcutsOverlay());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(miShowShortcuts);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, timelineMenu, viewMenu, playbackMenu, sceneMenu, helpMenu);
        menuBar.setUseSystemMenuBar(false);
        menuBar.setFocusTraversable(false);
        menuBar.setMinHeight(Region.USE_PREF_SIZE);
        menuBar.setMaxWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, menuBar, spacer);
        bar.getStyleClass().add("puppeteer-toolbar-command-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    // -----------------------------------------------------------------------
    // Overlay / dialog actions
    // -----------------------------------------------------------------------

    void showShortcutsOverlay() {
        view.showShortcutsOverlay();
    }

    void showPresetMenuOverlay() {
        view.showPresetMenuOverlay();
    }

    void showSlotMenuOverlay() {
        view.showSlotMenuOverlay();
    }

    void requestWindowClose() {
        view.requestWindowClose();
    }

    void requestRegisterTimeline() {
        view.requestRegisterTimeline();
    }

    void showRuntimeVerificationReport() {
        view.showRuntimeVerificationReport();
    }

    void showAddAudioCueDialog() {
        view.showAddAudioCueDialog();
    }

    void showEventCueManagerDialog(EditorEventCue initialSelection) {
        view.showEventCueManagerDialog(initialSelection);
    }

    void showRecordGifDialog() {
        view.showRecordGifDialog();
    }

    void saveSelectionAsClip() {
        view.saveSelectionAsClip();
    }

    void loadAndApplyClip() {
        view.loadAndApplyClip();
    }

    void showClipboardHistoryPopup() {
        view.showClipboardHistoryPopup();
    }
}

package com.jvn.editor.ui.actioneditor.docs;

import com.jvn.core.scene2d.Sprite2D;
import com.jvn.editor.ui.AssetBrowserView;
import com.jvn.editor.ui.HelpCenterView;
import com.jvn.editor.ui.ImageAttributesToolView;
import com.jvn.editor.ui.ImageTintToolView;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.LayeredImageVisualizerView;
import com.jvn.editor.ui.LayoutEditorLauncherView;
import com.jvn.editor.ui.MenuFlowEditorView;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.PuppeteerLauncherPanel;
import com.jvn.editor.ui.RunConsoleView;
import com.jvn.editor.ui.ScriptEditorLauncherView;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.VersionControlView;
import com.jvn.editor.ui.VnsDiagnosticsView;
import com.jvn.editor.ui.VnsFlowMapView;
import com.jvn.editor.ui.VnsScriptAnalyzer;
import com.jvn.editor.ui.WelcomeCenterView;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.Keyframe;
import com.jvn.editor.ui.actioneditor.PropertyType;
import com.jvn.editor.ui.actioneditor.PuppeteerWindow;
import com.jvn.scripting.jes.runtime.JesScene2D;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic docs screenshot generator for editor tools.
 *
 * Supported profiles:
 * - puppeteer
 * - image-tint
 * - right sidebar utilities (asset browser, help center, diagnostics, and more)
 */
public final class DocsScreenshotTool extends Application {
    private static final String CONTACT_SHEET_FILE = "docs_contact_sheet.png";

    private static final String PROP_PROFILE = "jvn.docs.profile";
    private static final String PROP_SHOTS = "jvn.docs.screenshots.shots";
    private static final String PROP_ANNOTATE = "jvn.docs.screenshots.annotate";
    private static final String PROP_INCLUDE_RAW = "jvn.docs.screenshots.includeRaw";
    private static final String PROP_UPDATE_DOCS = "jvn.docs.screenshots.updateDocs";
    private static final String PROP_INCLUDE_CONTACT_SHEET = "jvn.docs.screenshots.contactSheet";

    private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final ProfileSpec PUPPETEER_PROFILE = new ProfileSpec(
        "puppeteer",
        "Puppeteer",
        "docs/editor/puppeteer/puppeteer-editor-guide.md",
        "docs/editor/puppeteer/generated-puppeteer-screenshots.md",
        "docs/assets/images/puppeteer",
        "docs/assets/images/puppeteer/raw",
        "<!-- AUTO-PUPPETEER-SCREENSHOTS:START -->",
        "<!-- AUTO-PUPPETEER-SCREENSHOTS:END -->",
        900,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "puppeteer_ui_full.png",
                "Puppeteer Overview",
                "Complete editor layout: toolbar, entity list, preview, timeline, and code panel.",
                0,
                0,
                0,
                List.of(
                    new Callout("Toolbar", 0.01, 0.01, 0.98, 0.08),
                    new Callout("Entities + Keyframe Editor", 0.01, 0.10, 0.16, 0.86),
                    new Callout("Preview + Timeline", 0.18, 0.10, 0.60, 0.86),
                    new Callout("Code Panel", 0.79, 0.10, 0.20, 0.86)
                )
            ),
            new ShotSpec(
                "toolbar",
                "toolbar",
                "puppeteer_ui_toolbar.png",
                "Top Toolbar",
                "Transport, snapping, orbit tools, presets, audio cues, and timeline registration.",
                24,
                0,
                0,
                List.of(
                    new Callout("Playback", 0.01, 0.10, 0.16, 0.75),
                    new Callout("Property + Keyframe Ops", 0.30, 0.10, 0.18, 0.75),
                    new Callout("Orbit/Nail Tools", 0.64, 0.10, 0.14, 0.75),
                    new Callout("Register + Help", 0.87, 0.10, 0.12, 0.75)
                )
            ),
            new ShotSpec(
                "toolbar-transport",
                "toolbar_transport",
                "puppeteer_ui_toolbar_transport.png",
                "Toolbar - Transport + Duration",
                "Playback controls, playhead readout, duration, and loop controls.",
                6,
                760,
                0,
                List.of(
                    new Callout("Transport", 0.01, 0.08, 0.40, 0.80),
                    new Callout("Duration + Loop", 0.43, 0.08, 0.56, 0.80)
                )
            ),
            new ShotSpec(
                "toolbar-keyframe-ops",
                "toolbar_key_ops",
                "puppeteer_ui_toolbar_key_ops.png",
                "Toolbar - Keyframe Operations",
                "Property target, copy/paste/duplicate, batch keyframe, clip save/load, slot placement, and zoom fit.",
                6,
                1050,
                0,
                List.of(
                    new Callout("Property Track", 0.01, 0.08, 0.16, 0.80),
                    new Callout("Keyframe / Clip Ops", 0.19, 0.08, 0.80, 0.80)
                )
            ),
            new ShotSpec(
                "toolbar-preview-modes",
                "toolbar_preview_modes",
                "puppeteer_ui_toolbar_preview_modes.png",
                "Toolbar - Snap + Preview Modes",
                "Snap, auto-key, drag snapping helpers, playback speed, and wheel mode.",
                6,
                820,
                0,
                List.of(
                    new Callout("Snap", 0.01, 0.08, 0.24, 0.80),
                    new Callout("Auto-Key", 0.27, 0.08, 0.16, 0.80),
                    new Callout("Preview Modes", 0.45, 0.08, 0.54, 0.80)
                )
            ),
            new ShotSpec(
                "toolbar-orbit-audio-register",
                "toolbar_orbit_audio_register",
                "puppeteer_ui_toolbar_orbit_audio_register.png",
                "Toolbar - Orbit, Audio, Registration",
                "Orbit/nail workflow controls, audio cue controls, timeline naming, register, and shortcuts help.",
                6,
                980,
                0,
                List.of(
                    new Callout("Orbit Controls", 0.01, 0.08, 0.33, 0.80),
                    new Callout("Audio Cues", 0.36, 0.08, 0.20, 0.80),
                    new Callout("Name + Register + Help", 0.59, 0.08, 0.40, 0.80)
                )
            ),
            new ShotSpec(
                "entities",
                "entities",
                "puppeteer_ui_entities_panel.png",
                "Entity + Keyframe Side Panel",
                "Entity stack and keyframe controls for property/value/easing edits.",
                6,
                420,
                0,
                List.of(
                    new Callout("Entity Stack", 0.05, 0.04, 0.90, 0.48),
                    new Callout("Keyframe Editor", 0.05, 0.55, 0.90, 0.40)
                )
            ),
            new ShotSpec(
                "entities-tree",
                "left_tabs",
                "puppeteer_ui_entities_tree.png",
                "Entities / Assets Tabs",
                "Entity hierarchy, filter field, Z-order badges, and access to assets browser.",
                6,
                430,
                0,
                List.of(
                    new Callout("Entities Tree", 0.06, 0.10, 0.88, 0.68),
                    new Callout("Entity Actions", 0.06, 0.81, 0.88, 0.14)
                )
            ),
            new ShotSpec(
                "keyframe-editor",
                "keyframe_editor",
                "puppeteer_ui_keyframe_editor.png",
                "Keyframe Editor Panel",
                "Per-keyframe controls: time/value, interpolation, easing curve, pivot presets, and camera readout.",
                6,
                430,
                0,
                List.of(
                    new Callout("Keyframe Fields", 0.04, 0.08, 0.92, 0.44),
                    new Callout("Easing + Pivot + Camera", 0.04, 0.55, 0.92, 0.40)
                )
            ),
            new ShotSpec(
                "preview",
                "preview",
                "puppeteer_ui_preview.png",
                "Preview Canvas",
                "Scene viewport with runtime frame, selection controls, and motion visualization.",
                4,
                0,
                0,
                List.of(
                    new Callout("Runtime Frame", 0.08, 0.08, 0.84, 0.72),
                    new Callout("Camera/View HUD", 0.01, 0.01, 0.36, 0.10)
                )
            ),
            new ShotSpec(
                "preview-runtime-frame",
                "preview_canvas",
                "puppeteer_ui_preview_runtime_frame.png",
                "Preview - Runtime Framing",
                "Runtime frame boundaries and scene-overview composition outside the runtime area.",
                6,
                960,
                0,
                List.of(
                    new Callout("Runtime Frame", 0.08, 0.08, 0.84, 0.72),
                    new Callout("Extra Scene Coverage", 0.01, 0.14, 0.97, 0.78)
                )
            ),
            new ShotSpec(
                "preview-selection-orbit",
                "preview_canvas",
                "puppeteer_ui_preview_selection_orbit.png",
                "Preview - Selection, Pivot, Orbit",
                "Selection outlines, pivot handles, orbit anchor visualization, and motion paths.",
                6,
                960,
                0,
                List.of(
                    new Callout("Selection + Handles", 0.16, 0.18, 0.30, 0.58),
                    new Callout("Motion Paths / Orbit Context", 0.50, 0.18, 0.45, 0.58)
                )
            ),
            new ShotSpec(
                "timeline",
                "timeline",
                "puppeteer_ui_timeline.png",
                "Timeline Panel",
                "Track rows, keyframes, and playhead for direct timing edits.",
                4,
                0,
                0,
                List.of(
                    new Callout("Track Lanes", 0.01, 0.12, 0.98, 0.72),
                    new Callout("Time Ruler", 0.01, 0.01, 0.98, 0.16)
                )
            ),
            new ShotSpec(
                "timeline-details",
                "timeline",
                "puppeteer_ui_timeline_details.png",
                "Timeline - Keyframe Editing Detail",
                "Entity/property lanes, selected keyframes, and timeline scrubbing interactions.",
                6,
                980,
                0,
                List.of(
                    new Callout("Keyframe Lanes", 0.01, 0.18, 0.98, 0.62),
                    new Callout("Playhead", 0.48, 0.01, 0.04, 0.86)
                )
            ),
            new ShotSpec(
                "timeline-loop-audio",
                "timeline",
                "puppeteer_ui_timeline_loop_audio.png",
                "Timeline - Loop + Audio Cues",
                "Loop range visualization and timeline audio cue markers.",
                6,
                980,
                0,
                List.of(
                    new Callout("Loop Region", 0.20, 0.06, 0.58, 0.74),
                    new Callout("Audio Cues", 0.02, 0.83, 0.96, 0.14)
                )
            ),
            new ShotSpec(
                "code",
                "code",
                "puppeteer_ui_code_panel.png",
                "Live Code Export Panel",
                "Auto-generated timeline code with diagnostics and apply/commit controls.",
                6,
                420,
                0,
                List.of(
                    new Callout("Code Editor", 0.03, 0.05, 0.94, 0.75),
                    new Callout("Actions + Diagnostics", 0.03, 0.82, 0.94, 0.15)
                )
            ),
            new ShotSpec(
                "code-actions",
                "code_actions",
                "puppeteer_ui_code_actions.png",
                "Code Panel - Actions",
                "Copy/regenerate/preview/commit/discard actions used for text-first round-trip.",
                6,
                540,
                0,
                List.of(
                    new Callout("Action Buttons", 0.02, 0.10, 0.96, 0.78)
                )
            ),
            new ShotSpec(
                "status-bar",
                "status_bar",
                "puppeteer_ui_status_bar.png",
                "Status Bar",
                "Undo/redo descriptions, auto-key indicator, and playback speed.",
                6,
                900,
                0,
                List.of(
                    new Callout("Status / Undo / Redo", 0.01, 0.08, 0.98, 0.84)
                )
            )
        ),
        DocsScreenshotTool::openPuppeteerWindow,
        DocsScreenshotTool::resolvePuppeteerRegions
    );

    private static final ProfileSpec IMAGE_TINT_PROFILE = new ProfileSpec(
        "image-tint",
        "Scene Lighting Studio",
        "docs/editor/sidebars/right/sidebar-image-tint-tool.md",
        "docs/editor/sidebars/right/generated-image-tint-screenshots.md",
        "docs/assets/images/image-tint",
        "docs/assets/images/image-tint/raw",
        "<!-- AUTO-IMAGE-TINT-SCREENSHOTS:START -->",
        "<!-- AUTO-IMAGE-TINT-SCREENSHOTS:END -->",
        1400,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "image_tint_ui_full.png",
                "Scene Lighting Studio Overview",
                "Full tint tool workspace with preview canvas and controls sidebar.",
                0,
                0,
                0,
                List.of(
                    new Callout("Preview Canvas", 0.02, 0.08, 0.63, 0.88),
                    new Callout("Controls Sidebar", 0.66, 0.03, 0.32, 0.94)
                )
            ),
            new ShotSpec(
                "preview",
                "preview",
                "image_tint_ui_preview.png",
                "Lighting Preview Canvas",
                "Live composite preview with pan/zoom and zone drawing overlays.",
                8,
                900,
                0,
                List.of(
                    new Callout("Tinted Scene", 0.02, 0.04, 0.96, 0.92)
                )
            ),
            new ShotSpec(
                "sidebar",
                "sidebar",
                "image_tint_ui_sidebar.png",
                "Lighting Controls Sidebar",
                "Tag/setup controls, global grade sliders, background grade, light rig, and local grade settings.",
                8,
                460,
                0,
                List.of(
                    new Callout("Tags + Setup", 0.06, 0.02, 0.88, 0.22),
                    new Callout("Global/Background/Zone Controls", 0.06, 0.27, 0.88, 0.68)
                )
            )
        ),
        DocsScreenshotTool::openImageTintWindow,
        DocsScreenshotTool::resolveImageTintRegions
    );

    private static final ProfileSpec ASSET_BROWSER_PROFILE = basicSidebarProfile(
        "asset-browser",
        "Asset Browser",
        "docs/editor/sidebars/right/sidebar-asset-browser.md",
        "docs/editor/sidebars/right/generated-asset-browser-screenshots.md",
        "docs/assets/images/sidebars/asset-browser",
        900,
        DocsScreenshotTool::openAssetBrowserWindow
    );

    private static final ProfileSpec HELP_CENTER_PROFILE = new ProfileSpec(
        "help-center",
        "Help Center",
        "docs/editor/sidebars/right/sidebar-help-center.md",
        "docs/editor/sidebars/right/generated-help-center-screenshots.md",
        "docs/assets/images/sidebars/help-center",
        "docs/assets/images/sidebars/help-center/raw",
        "<!-- AUTO-HELP_CENTER-SCREENSHOTS:START -->",
        "<!-- AUTO-HELP_CENTER-SCREENSHOTS:END -->",
        1200,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "help_center_ui_full.png",
                "Help Center Overview",
                "Guide tree, quick access, and markdown preview in one workspace.",
                0,
                1200,
                0,
                List.of(
                    new Callout("Guide Tree", 0.01, 0.02, 0.31, 0.96),
                    new Callout("Preview Pane", 0.35, 0.02, 0.64, 0.96)
                )
            ),
            new ShotSpec(
                "guide-tree",
                "guide_tree",
                "help_center_guide_tree.png",
                "Guide Tree",
                "Progressive documentation tree with onboarding-first sections and full Markdown coverage.",
                8,
                560,
                0,
                List.of(
                    new Callout("Quick Access + Filter", 0.03, 0.02, 0.94, 0.22),
                    new Callout("Sectioned Doc Tree", 0.03, 0.27, 0.94, 0.68)
                )
            ),
            new ShotSpec(
                "preview",
                "preview",
                "help_center_preview.png",
                "Preview Pane",
                "Document metadata header and rendered Markdown preview for the selected page.",
                8,
                760,
                0,
                List.of(
                    new Callout("Preview Header", 0.02, 0.02, 0.96, 0.12),
                    new Callout("Rendered Markdown", 0.02, 0.18, 0.96, 0.78)
                )
            )
        ),
        DocsScreenshotTool::openHelpCenterWindow,
        DocsScreenshotTool::resolveHelpCenterRegions
    );

    private static final ProfileSpec PROJECT_EXPLORER_PROFILE = new ProfileSpec(
        "project-explorer",
        "Project Explorer",
        "docs/editor/sidebars/left/sidebar-project-explorer.md",
        "docs/editor/sidebars/left/generated-project-explorer-screenshots.md",
        "docs/assets/images/sidebars/project-explorer",
        "docs/assets/images/sidebars/project-explorer/raw",
        "<!-- AUTO-PROJECT_EXPLORER-SCREENSHOTS:START -->",
        "<!-- AUTO-PROJECT_EXPLORER-SCREENSHOTS:END -->",
        700,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "project_explorer_ui_full.png",
                "Project Explorer Overview",
                "Project tree with inline run action, filter field, and nested file hierarchy.",
                0,
                700,
                0,
                List.of(
                    new Callout("Filter", 0.05, 0.05, 0.90, 0.10),
                    new Callout("Project Tree", 0.03, 0.18, 0.94, 0.79)
                )
            ),
            new ShotSpec(
                "tree",
                "tree",
                "project_explorer_tree.png",
                "Project Tree Detail",
                "Expanded project hierarchy with the root run action and nested authoring files.",
                8,
                520,
                0,
                List.of(
                    new Callout("Root Run Action", 0.02, 0.01, 0.96, 0.13),
                    new Callout("Nested Files", 0.02, 0.16, 0.96, 0.82)
                )
            )
        ),
        DocsScreenshotTool::openProjectExplorerWindow,
        DocsScreenshotTool::resolveProjectExplorerRegions
    );

    private static final ProfileSpec IMAGE_ATTRIBUTES_PROFILE = basicSidebarProfile(
        "image-attributes",
        "Image Attributes Tool",
        "docs/editor/sidebars/right/sidebar-image-attributes-tool.md",
        "docs/editor/sidebars/right/generated-image-attributes-screenshots.md",
        "docs/assets/images/sidebars/image-attributes",
        1200,
        DocsScreenshotTool::openImageAttributesWindow
    );

    private static final ProfileSpec INSPECTOR_PROFILE = basicSidebarProfile(
        "inspector",
        "Inspector",
        "docs/editor/sidebars/right/sidebar-inspector.md",
        "docs/editor/sidebars/right/generated-inspector-screenshots.md",
        "docs/assets/images/sidebars/inspector",
        400,
        DocsScreenshotTool::openInspectorWindow
    );

    private static final ProfileSpec LABEL_FLOW_MAP_PROFILE = basicSidebarProfile(
        "label-flow-map",
        "Label Flow Map",
        "docs/editor/sidebars/right/sidebar-label-flow-map.md",
        "docs/editor/sidebars/right/generated-label-flow-map-screenshots.md",
        "docs/assets/images/sidebars/label-flow-map",
        700,
        DocsScreenshotTool::openLabelFlowMapWindow
    );

    private static final ProfileSpec LAYERED_IMAGE_VISUALIZER_PROFILE = basicSidebarProfile(
        "layered-image-visualizer",
        "Layered Image Visualizer",
        "docs/editor/sidebars/right/sidebar-layered-image-visualizer.md",
        "docs/editor/sidebars/right/generated-layered-image-visualizer-screenshots.md",
        "docs/assets/images/sidebars/layered-image-visualizer",
        1600,
        DocsScreenshotTool::openLayeredImageVisualizerWindow
    );

    private static final ProfileSpec LAYOUT_LAUNCHER_PROFILE = basicSidebarProfile(
        "layout-launcher",
        "Layout Launcher",
        "docs/editor/sidebars/right/sidebar-layout-launcher.md",
        "docs/editor/sidebars/right/generated-layout-launcher-screenshots.md",
        "docs/assets/images/sidebars/layout-launcher",
        800,
        DocsScreenshotTool::openLayoutLauncherWindow
    );

    private static final ProfileSpec MENU_FLOW_EDITOR_PROFILE = basicSidebarProfile(
        "menu-flow-editor",
        "Menu Flow Editor",
        "docs/editor/sidebars/right/sidebar-menu-flow-editor.md",
        "docs/editor/sidebars/right/generated-menu-flow-editor-screenshots.md",
        "docs/assets/images/sidebars/menu-flow-editor",
        1000,
        DocsScreenshotTool::openMenuFlowEditorWindow
    );

    private static final ProfileSpec PUPPETEER_LAUNCHER_PROFILE = basicSidebarProfile(
        "puppeteer-launcher",
        "Puppeteer Launcher",
        "docs/editor/sidebars/right/sidebar-puppeteer-launcher.md",
        "docs/editor/sidebars/right/generated-puppeteer-launcher-screenshots.md",
        "docs/assets/images/sidebars/puppeteer-launcher",
        350,
        DocsScreenshotTool::openPuppeteerLauncherWindow
    );

    private static final ProfileSpec TEXT_EDITOR_PROFILE = new ProfileSpec(
        "text-editor",
        "Text Editor",
        "docs/editor/sidebars/right/sidebar-script-editor.md",
        "docs/editor/sidebars/right/generated-script-editor-screenshots.md",
        "docs/assets/images/sidebars/text-editor",
        "docs/assets/images/sidebars/text-editor/raw",
        "<!-- AUTO-TEXT_EDITOR-SCREENSHOTS:START -->",
        "<!-- AUTO-TEXT_EDITOR-SCREENSHOTS:END -->",
        1200,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "text_editor_ui_full.png",
                "Text Editor Overview",
                "Header, search/filter controls, project tree, and selection inspector in the text workspace.",
                0,
                1200,
                0,
                List.of(
                    new Callout("Header + Search", 0.02, 0.02, 0.96, 0.22),
                    new Callout("Project Files", 0.02, 0.28, 0.48, 0.69),
                    new Callout("Selection Inspector", 0.53, 0.28, 0.45, 0.69)
                )
            ),
            new ShotSpec(
                "explorer",
                "explorer",
                "text_editor_explorer.png",
                "Text Workspace Explorer",
                "Indexed project text files with hierarchy browsing and file-search context.",
                8,
                620,
                0,
                List.of(
                    new Callout("Explorer Tree", 0.03, 0.08, 0.94, 0.70),
                    new Callout("Search Results", 0.03, 0.81, 0.94, 0.16)
                )
            ),
            new ShotSpec(
                "inspector",
                "inspector",
                "text_editor_inspector.png",
                "Selection Inspector",
                "Path, metadata, label outline, and include relationships for the selected text file.",
                8,
                620,
                0,
                List.of(
                    new Callout("Selected File", 0.03, 0.05, 0.94, 0.15),
                    new Callout("Outline + Includes", 0.03, 0.24, 0.94, 0.72)
                )
            )
        ),
        DocsScreenshotTool::openTextEditorWindow,
        DocsScreenshotTool::resolveTextEditorRegions
    );

    private static final ProfileSpec VERSION_CONTROL_PROFILE = basicSidebarProfile(
        "version-control",
        "Version Control",
        "docs/editor/sidebars/right/sidebar-version-control.md",
        "docs/editor/sidebars/right/generated-version-control-screenshots.md",
        "docs/assets/images/sidebars/version-control",
        1600,
        DocsScreenshotTool::openVersionControlWindow
    );

    private static final ProfileSpec VNS_DIAGNOSTICS_PROFILE = basicSidebarProfile(
        "vns-diagnostics",
        "VNS Diagnostics",
        "docs/editor/sidebars/right/sidebar-vns-diagnostics.md",
        "docs/editor/sidebars/right/generated-vns-diagnostics-screenshots.md",
        "docs/assets/images/sidebars/vns-diagnostics",
        550,
        DocsScreenshotTool::openVnsDiagnosticsWindow
    );

    private static final ProfileSpec STORY_TIMELINE_PROFILE = new ProfileSpec(
        "story-timeline",
        "Story Timeline",
        "docs/editor/sidebars/left/sidebar-story-timeline.md",
        "docs/editor/sidebars/left/generated-story-timeline-screenshots.md",
        "docs/assets/images/sidebars/story-timeline",
        "docs/assets/images/sidebars/story-timeline/raw",
        "<!-- AUTO-STORY_TIMELINE-SCREENSHOTS:START -->",
        "<!-- AUTO-STORY_TIMELINE-SCREENSHOTS:END -->",
        1000,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "story_timeline_ui_full.png",
                "Story Timeline Overview",
                "Complete Story Timeline workspace with toolbar, graph canvas, and arc/link tabs.",
                0,
                1220,
                0,
                List.of(
                    new Callout("Toolbar", 0.01, 0.01, 0.98, 0.12),
                    new Callout("Arc Graph Canvas", 0.02, 0.14, 0.96, 0.58),
                    new Callout("Arcs / Links Lists", 0.02, 0.74, 0.96, 0.24)
                )
            ),
            new ShotSpec(
                "toolbar",
                "toolbar",
                "story_timeline_toolbar.png",
                "Timeline Toolbar",
                "Create/edit/open arcs, auto-layout, fit, validate, find, and cluster filtering controls.",
                8,
                1180,
                0,
                List.of(
                    new Callout("Arc/Link Actions", 0.01, 0.06, 0.42, 0.84),
                    new Callout("Find + Cluster + Validation", 0.45, 0.06, 0.54, 0.84)
                )
            ),
            new ShotSpec(
                "graph",
                "graph",
                "story_timeline_graph.png",
                "Graph Canvas",
                "Drag nodes, connect arcs, inspect clustered routes, and review link directions.",
                8,
                1220,
                0,
                List.of(
                    new Callout("Arc Nodes", 0.04, 0.09, 0.44, 0.66),
                    new Callout("Directed Links", 0.50, 0.12, 0.44, 0.62)
                )
            ),
            new ShotSpec(
                "lists",
                "lists",
                "story_timeline_lists.png",
                "Arcs and Links Lists",
                "Use tabs for quick selection, rename/edit context menus, and keyboard operations.",
                6,
                1220,
                0,
                List.of(
                    new Callout("Arcs Tab", 0.02, 0.08, 0.47, 0.84),
                    new Callout("Links Tab", 0.51, 0.08, 0.47, 0.84)
                )
            )
        ),
        DocsScreenshotTool::openStoryTimelineWindow,
        DocsScreenshotTool::resolveStoryTimelineRegions
    );

    private static final ProfileSpec WELCOME_CENTER_PROFILE = new ProfileSpec(
        "welcome-center",
        "Welcome Center",
        "docs/editor/core/welcome-center.md",
        "docs/editor/core/generated-welcome-center-screenshots.md",
        "docs/assets/images/core/welcome-center",
        "docs/assets/images/core/welcome-center/raw",
        "<!-- AUTO-WELCOME_CENTER-SCREENSHOTS:START -->",
        "<!-- AUTO-WELCOME_CENTER-SCREENSHOTS:END -->",
        900,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "welcome_center_ui_full.png",
                "Welcome Center Overview",
                "Startup dashboard with the hero card, recent projects, and environment health.",
                0,
                1200,
                0,
                List.of(
                    new Callout("Hero Card", 0.03, 0.03, 0.94, 0.22),
                    new Callout("Recent Projects", 0.03, 0.28, 0.54, 0.68),
                    new Callout("Environment Health", 0.60, 0.28, 0.37, 0.68)
                )
            ),
            new ShotSpec(
                "hero",
                "hero",
                "welcome_center_hero.png",
                "Hero Card",
                "Workspace/project metadata and quick launch actions at editor startup.",
                8,
                860,
                0,
                List.of(
                    new Callout("Workspace + Project", 0.03, 0.10, 0.94, 0.38),
                    new Callout("Quick Actions", 0.03, 0.52, 0.52, 0.32)
                )
            ),
            new ShotSpec(
                "recent-projects",
                "recent_projects",
                "welcome_center_recent_projects.png",
                "Recent Projects",
                "Project history list with filtering and quick-open actions.",
                8,
                760,
                0,
                List.of(
                    new Callout("Recent List", 0.03, 0.12, 0.94, 0.80)
                )
            ),
            new ShotSpec(
                "environment-health",
                "environment_health",
                "welcome_center_environment_health.png",
                "Environment Health",
                "Runtime, Gradle, Git, and project artifact diagnostics shown at startup.",
                8,
                760,
                0,
                List.of(
                    new Callout("Health Checks", 0.03, 0.12, 0.94, 0.80)
                )
            )
        ),
        DocsScreenshotTool::openWelcomeCenterWindow,
        DocsScreenshotTool::resolveWelcomeCenterRegions
    );

    private static final ProfileSpec RUN_CONSOLE_PROFILE = new ProfileSpec(
        "run-console",
        "Run Console",
        "docs/editor/core/run-console.md",
        "docs/editor/core/generated-run-console-screenshots.md",
        "docs/assets/images/core/run-console",
        "docs/assets/images/core/run-console/raw",
        "<!-- AUTO-RUN_CONSOLE-SCREENSHOTS:START -->",
        "<!-- AUTO-RUN_CONSOLE-SCREENSHOTS:END -->",
        700,
        List.of(
            new ShotSpec(
                "full",
                "full",
                "run_console_ui_full.png",
                "Run Console Overview",
                "Toolbar, live output, performance graph, and status bar during a runtime launch.",
                0,
                1200,
                0,
                List.of(
                    new Callout("Toolbar + Perf Graph", 0.02, 0.02, 0.96, 0.12),
                    new Callout("Output Log", 0.02, 0.16, 0.96, 0.72),
                    new Callout("Status Bar", 0.02, 0.91, 0.96, 0.07)
                )
            ),
            new ShotSpec(
                "toolbar",
                "toolbar",
                "run_console_toolbar.png",
                "Toolbar",
                "Run controls, filters, search, and the compact runtime performance graph.",
                8,
                980,
                0,
                List.of(
                    new Callout("Controls", 0.02, 0.08, 0.38, 0.80),
                    new Callout("Perf Graph + Filters", 0.42, 0.08, 0.56, 0.80)
                )
            ),
            new ShotSpec(
                "output",
                "output",
                "run_console_output.png",
                "Output Log",
                "Color-coded runtime output with warnings, info lines, and filtered Gradle noise.",
                8,
                980,
                0,
                List.of(
                    new Callout("Live Output", 0.02, 0.04, 0.96, 0.92)
                )
            )
        ),
        DocsScreenshotTool::openRunConsoleWindow,
        DocsScreenshotTool::resolveRunConsoleRegions
    );

    private static final List<String> DEFAULT_PROFILE_KEYS = List.of(
        PUPPETEER_PROFILE.key(),
        IMAGE_TINT_PROFILE.key(),
        ASSET_BROWSER_PROFILE.key(),
        HELP_CENTER_PROFILE.key(),
        PROJECT_EXPLORER_PROFILE.key(),
        IMAGE_ATTRIBUTES_PROFILE.key(),
        INSPECTOR_PROFILE.key(),
        LABEL_FLOW_MAP_PROFILE.key(),
        LAYERED_IMAGE_VISUALIZER_PROFILE.key(),
        LAYOUT_LAUNCHER_PROFILE.key(),
        MENU_FLOW_EDITOR_PROFILE.key(),
        PUPPETEER_LAUNCHER_PROFILE.key(),
        TEXT_EDITOR_PROFILE.key(),
        VERSION_CONTROL_PROFILE.key(),
        VNS_DIAGNOSTICS_PROFILE.key(),
        STORY_TIMELINE_PROFILE.key(),
        WELCOME_CENTER_PROFILE.key(),
        RUN_CONSOLE_PROFILE.key()
    );

    private static final Map<String, ProfileSpec> PROFILES = Map.ofEntries(
        Map.entry(PUPPETEER_PROFILE.key(), PUPPETEER_PROFILE),
        Map.entry(IMAGE_TINT_PROFILE.key(), IMAGE_TINT_PROFILE),
        Map.entry(ASSET_BROWSER_PROFILE.key(), ASSET_BROWSER_PROFILE),
        Map.entry(HELP_CENTER_PROFILE.key(), HELP_CENTER_PROFILE),
        Map.entry(PROJECT_EXPLORER_PROFILE.key(), PROJECT_EXPLORER_PROFILE),
        Map.entry(IMAGE_ATTRIBUTES_PROFILE.key(), IMAGE_ATTRIBUTES_PROFILE),
        Map.entry(INSPECTOR_PROFILE.key(), INSPECTOR_PROFILE),
        Map.entry(LABEL_FLOW_MAP_PROFILE.key(), LABEL_FLOW_MAP_PROFILE),
        Map.entry(LAYERED_IMAGE_VISUALIZER_PROFILE.key(), LAYERED_IMAGE_VISUALIZER_PROFILE),
        Map.entry(LAYOUT_LAUNCHER_PROFILE.key(), LAYOUT_LAUNCHER_PROFILE),
        Map.entry(MENU_FLOW_EDITOR_PROFILE.key(), MENU_FLOW_EDITOR_PROFILE),
        Map.entry(PUPPETEER_LAUNCHER_PROFILE.key(), PUPPETEER_LAUNCHER_PROFILE),
        Map.entry(TEXT_EDITOR_PROFILE.key(), TEXT_EDITOR_PROFILE),
        Map.entry(VERSION_CONTROL_PROFILE.key(), VERSION_CONTROL_PROFILE),
        Map.entry(VNS_DIAGNOSTICS_PROFILE.key(), VNS_DIAGNOSTICS_PROFILE),
        Map.entry(STORY_TIMELINE_PROFILE.key(), STORY_TIMELINE_PROFILE),
        Map.entry(WELCOME_CENTER_PROFILE.key(), WELCOME_CENTER_PROFILE),
        Map.entry(RUN_CONSOLE_PROFILE.key(), RUN_CONSOLE_PROFILE)
    );

    private Path repoRoot;
    private RunOptions options;
    private List<ProfileSpec> profileQueue;
    private int profileIndex;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.repoRoot = resolveRepoRoot();
        this.options = resolveRunOptions();
        this.profileQueue = resolveProfiles(options.profileKeys());
        this.profileIndex = 0;
        runNextProfile();
    }

    private void runNextProfile() {
        if (profileIndex >= profileQueue.size()) {
            Platform.exit();
            return;
        }
        ProfileSpec profile = profileQueue.get(profileIndex++);
        Stage window = null;
        try {
            window = profile.stageFactory().create(repoRoot);
            Stage captureWindow = window;
            PauseTransition pause = new PauseTransition(Duration.millis(profile.warmupMs()));
            pause.setOnFinished(evt -> Platform.runLater(() -> captureProfile(profile, captureWindow)));
            pause.play();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            if (window != null) {
                try { window.close(); } catch (Exception ignored) {}
            }
            runNextProfile();
        }
    }

    private void captureProfile(ProfileSpec profile, Stage stage) {
        try {
            Scene scene = stage.getScene();
            if (scene == null || scene.getRoot() == null) {
                throw new IllegalStateException("No scene/root available for profile " + profile.key());
            }

            scene.getRoot().applyCss();
            scene.getRoot().layout();

            WritableImage snap = scene.snapshot(null);
            BufferedImage fullImage = SwingFXUtils.fromFXImage(snap, null);
            if (fullImage == null) {
                throw new IllegalStateException("Snapshot capture returned null image for profile " + profile.key());
            }

            Map<String, Node> regions = profile.regionResolver().resolve(stage);
            List<ShotSpec> selected = selectShots(profile.shots(), options.shotFilter());
            if (selected.isEmpty()) {
                throw new IllegalStateException("No shots selected for profile " + profile.key() + ".");
            }

            Path imageDir = repoRoot.resolve(profile.imageDir());
            Files.createDirectories(imageDir);
            Path rawDir = repoRoot.resolve(profile.rawImageDir());
            if (options.includeRaw()) {
                Files.createDirectories(rawDir);
            }

            List<RenderedShot> rendered = new ArrayList<>();
            for (ShotSpec spec : selected) {
                Node node = regions.get(spec.regionKey());
                if (node == null) node = regions.get("full");

                BufferedImage rawCrop = cropForNode(fullImage, node, spec.paddingPx());
                BufferedImage adjusted = upscaleIfNeeded(rawCrop, spec.minWidthPx(), spec.minHeightPx());
                if (adjusted == null) continue;

                BufferedImage output = options.annotate()
                    ? annotate(adjusted, spec.title(), spec.callouts())
                    : adjusted;

                Path annotatedOut = imageDir.resolve(spec.fileName());
                ImageIO.write(output, "png", annotatedOut.toFile());
                System.out.println("Wrote " + annotatedOut);

                Path rawOut = null;
                if (options.includeRaw()) {
                    rawOut = rawDir.resolve(spec.fileName());
                    ImageIO.write(adjusted, "png", rawOut.toFile());
                    System.out.println("Wrote " + rawOut);
                }

                rendered.add(new RenderedShot(spec, output, rawOut != null));
            }

            boolean hasContactSheet = false;
            if (options.includeContactSheet() && !rendered.isEmpty()) {
                BufferedImage sheet = createContactSheet(rendered);
                if (sheet != null) {
                    Path contactOut = imageDir.resolve(CONTACT_SHEET_FILE);
                    ImageIO.write(sheet, "png", contactOut.toFile());
                    System.out.println("Wrote " + contactOut);
                    hasContactSheet = true;
                }
            }

            String markdown = buildMarkdownSection(profile, rendered, hasContactSheet, options.includeRaw());
            writeGeneratedSnippet(repoRoot.resolve(profile.snippetDoc()), markdown, profile.displayName());
            if (options.updateDocs()) {
                updateGuideBlock(repoRoot.resolve(profile.guideDoc()), markdown, profile.startMarker(), profile.endMarker());
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        } finally {
            try { stage.close(); } catch (Exception ignored) {}
            runNextProfile();
        }
    }

    private static Stage openPuppeteerWindow(Path repoRoot) {
        AnimationProject project = buildPuppeteerDemoProject();
        PuppeteerWindow window = new PuppeteerWindow(project);
        window.setProjectRoot(repoRoot.toFile());
        window.setScene(buildPuppeteerDemoScene());
        window.setTitle("Docs Screenshot Session - Puppeteer");
        window.setWidth(2200);
        window.setHeight(1300);
        window.show();
        window.setToolbarClustersExpanded(true);
        return window;
    }

    private static Stage openImageTintWindow(Path repoRoot) {
        ImageTintToolView view = new ImageTintToolView();
        try {
            view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to prepare docs fixture project for image tint screenshots.", ex);
        }
        Scene scene = new Scene(view, 1680, 980);
        Stage stage = new Stage();
        stage.setTitle("Docs Screenshot Session - Scene Lighting Studio");
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    private static Stage openAssetBrowserWindow(Path repoRoot) throws Exception {
        AssetBrowserView view = new AssetBrowserView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Asset Browser", view, 1120, 900);
    }

    private static Stage openHelpCenterWindow(Path repoRoot) throws Exception {
        HelpCenterView view = new HelpCenterView();
        view.setWorkspaceRoot(repoRoot.toFile());
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Help Center", view, 1400, 900);
    }

    private static Stage openProjectExplorerWindow(Path repoRoot) throws Exception {
        ProjectExplorerView view = new ProjectExplorerView();
        view.setRootDirectory(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Project Explorer", view, 520, 980);
    }

    private static Stage openImageAttributesWindow(Path repoRoot) throws Exception {
        ImageAttributesToolView view = new ImageAttributesToolView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Image Attributes Tool", view, 1380, 930);
    }

    private static Stage openInspectorWindow(Path repoRoot) {
        InspectorView view = new InspectorView(msg -> {});
        JesScene2D scene = new JesScene2D();
        Sprite2D sprite = new Sprite2D("demo-assets/Lavender_test_sprite/base/lavender_test_sprite_base.png", 430, 760);
        sprite.setOrigin(0.5, 1.0);
        sprite.setPosition(640, 710);
        scene.add(sprite);
        view.setScene(scene);
        view.setSelection(sprite);

        BorderPane host = new BorderPane(view);
        host.setPadding(new javafx.geometry.Insets(10));
        return openToolStage("Docs Screenshot Session - Inspector", host, 760, 940);
    }

    private static Stage openLabelFlowMapWindow(Path repoRoot) throws Exception {
        Path fixtureRoot = ensureDocsFixtureProject(repoRoot);
        String source = docsSidebarVnsSource();
        Path script = fixtureRoot.resolve("scripts/docs_flow_map.vns");
        writeTextFile(script, source);

        VnsFlowMapView view = new VnsFlowMapView();
        view.setAnalysis(script.toFile(), VnsScriptAnalyzer.analyze(source, fixtureRoot.toFile()));
        return openToolStage("Docs Screenshot Session - Label Flow Map", view, 1260, 860);
    }

    private static Stage openLayeredImageVisualizerWindow(Path repoRoot) throws Exception {
        LayeredImageVisualizerView view = new LayeredImageVisualizerView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Layered Image Visualizer", view, 1420, 980);
    }

    private static Stage openLayoutLauncherWindow(Path repoRoot) throws Exception {
        LayoutEditorLauncherView view = new LayoutEditorLauncherView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Layout Launcher", view, 1220, 900);
    }

    private static Stage openMenuFlowEditorWindow(Path repoRoot) throws Exception {
        MenuFlowEditorView view = new MenuFlowEditorView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Menu Flow Editor", view, 1520, 980);
    }

    private static Stage openPuppeteerLauncherWindow(Path repoRoot) throws Exception {
        Path fixtureRoot = ensureDocsFixtureProject(repoRoot);
        String source = docsSidebarVnsSource();
        Path script = fixtureRoot.resolve("scripts/docs_puppeteer_launcher.vns");
        Path timelinesDir = fixtureRoot.resolve("scripts/timelines");
        writeTextFile(script, source);
        writeTextFile(timelinesDir.resolve("hero_intro.jes"), """
            timeline {
              move "lavender" {
                x: 180
                dur: 320
              }
            }
            """);
        writeTextFile(timelinesDir.resolve("camera_pan.jes"), """
            timeline {
              cameraMove {
                x: 96
                dur: 420
              }
            }
            """);

        PuppeteerLauncherPanel panel = new PuppeteerLauncherPanel();
        panel.setProjectRoot(fixtureRoot.toFile());
        panel.setSource(source);
        panel.setCaretLine(12);
        panel.setOnLaunch(request -> {});
        return openToolStage("Docs Screenshot Session - Puppeteer Launcher", panel, 620, 940);
    }

    private static Stage openTextEditorWindow(Path repoRoot) throws Exception {
        ScriptEditorLauncherView view = new ScriptEditorLauncherView();
        Path fixtureRoot = ensureDocsFixtureProject(repoRoot);
        view.setWorkspaceRoot(repoRoot.toFile());
        view.setProjectRoot(fixtureRoot.toFile());
        return openToolStage("Docs Screenshot Session - Text Editor", view, 1460, 980);
    }

    private static Stage openVersionControlWindow(Path repoRoot) {
        VersionControlView view = new VersionControlView();
        view.setProjectRoot(repoRoot.toFile());
        return openToolStage("Docs Screenshot Session - Version Control", view, 980, 900);
    }

    private static Stage openVnsDiagnosticsWindow(Path repoRoot) throws Exception {
        Path fixtureRoot = ensureDocsFixtureProject(repoRoot);
        String source = docsSidebarVnsSource();
        Path script = fixtureRoot.resolve("scripts/docs_diagnostics.vns");
        writeTextFile(script, source);

        VnsDiagnosticsView view = new VnsDiagnosticsView();
        view.setAnalysis(script.toFile(), VnsScriptAnalyzer.analyze(source, fixtureRoot.toFile()));
        return openToolStage("Docs Screenshot Session - VNS Diagnostics", view, 980, 860);
    }

    private static Stage openStoryTimelineWindow(Path repoRoot) throws Exception {
        StoryTimelineView view = new StoryTimelineView();
        view.setProjectRoot(ensureDocsFixtureProject(repoRoot).toFile());
        return openToolStage("Docs Screenshot Session - Story Timeline", view, 1600, 980);
    }

    private static Stage openWelcomeCenterWindow(Path repoRoot) throws Exception {
        WelcomeCenterView view = new WelcomeCenterView();
        Path fixtureRoot = ensureDocsFixtureProject(repoRoot);
        view.setEditorVersion("dev");
        view.setWorkspaceRoot(repoRoot.toFile());
        view.setCurrentProject(fixtureRoot.toFile());
        return openToolStage("Docs Screenshot Session - Welcome Center", view, 1600, 980);
    }

    private static Stage openRunConsoleWindow(Path repoRoot) {
        RunConsoleView view = new RunConsoleView("JVN Runtime");
        Stage stage = openToolStage("Docs Screenshot Session - Run Console", view, 1540, 960);
        populateRunConsole(view, ensureDocsFixtureProjectQuiet(repoRoot));
        return stage;
    }

    private static Stage openToolStage(String title, Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    private static ProfileSpec basicSidebarProfile(String key,
                                                   String displayName,
                                                   String guideDoc,
                                                   String snippetDoc,
                                                   String imageDir,
                                                   long warmupMs,
                                                   StageFactory stageFactory) {
        String markerBase = key == null ? "" : key.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        String startMarker = "<!-- AUTO-" + markerBase + "-SCREENSHOTS:START -->";
        String endMarker = "<!-- AUTO-" + markerBase + "-SCREENSHOTS:END -->";
        String fileStem = (key == null ? "sidebar" : key.trim()).replace('-', '_');

        ShotSpec fullShot = new ShotSpec(
            "full",
            "full",
            fileStem + "_ui_full.png",
            displayName + " Overview",
            "Full " + displayName + " sidebar utility view.",
            0,
            960,
            0,
            List.of()
        );

        return new ProfileSpec(
            key,
            displayName,
            guideDoc,
            snippetDoc,
            imageDir,
            imageDir + "/raw",
            startMarker,
            endMarker,
            warmupMs,
            List.of(fullShot),
            stageFactory,
            DocsScreenshotTool::resolveFullRegions
        );
    }

    private static Map<String, Node> resolveFullRegions(Stage stage) {
        Map<String, Node> regions = new LinkedHashMap<>();
        if (stage == null || stage.getScene() == null || stage.getScene().getRoot() == null) {
            return regions;
        }
        regions.put("full", stage.getScene().getRoot());
        return regions;
    }

    private static Path ensureDocsFixtureProject(Path repoRoot) throws IOException {
        Path fixtureRoot = repoRoot.resolve("build/docs-screenshots/sidebar-fixture");
        Files.createDirectories(fixtureRoot);

        Path lavenderBase = fixtureRoot.resolve("assets/characters/lavender/base/lavender_test_sprite_base.png");
        Path lavenderEyesNeutral = fixtureRoot.resolve("assets/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png");
        Path lavenderEyesHalfClosed = fixtureRoot.resolve("assets/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png");
        Path lavenderMouthNeutral = fixtureRoot.resolve("assets/characters/lavender/mouth/lavender_test_sprite_mouth_neutral.png");
        Path lavenderMouthSmile = fixtureRoot.resolve("assets/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png");
        Path background = fixtureRoot.resolve("assets/bg/school_day.png");

        copyFile(repoRoot.resolve("demo-assets/Lavender_test_sprite/base/lavender_test_sprite_base.png"), lavenderBase);
        copyFile(repoRoot.resolve("demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_neutral.png"), lavenderEyesNeutral);
        copyFile(repoRoot.resolve("demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_half_closed.png"), lavenderEyesHalfClosed);
        copyFile(repoRoot.resolve("demo-assets/Lavender_test_sprite/mouth/lavender_test_sprite_mouth_neutral.png"), lavenderMouthNeutral);
        copyFile(repoRoot.resolve("demo-assets/Lavender_test_sprite/mouth/lavender_test_sprite_mouth_smile.png"), lavenderMouthSmile);
        copyFile(repoRoot.resolve("demo-assets/demo_bg/game.png"), background);

        writeTextFile(
            fixtureRoot.resolve("jvn.project"),
            String.join("\n",
                "name=docs_sidebar_fixture",
                "menuRegistry=config/menu/registry/menu.registry",
                "menuDefaultLayout=config/menu/layouts/default.layout",
                "menuDefaultStyle=config/menu/styles/default.style",
                "dialogueLayout=config/ui/dialogue.layout",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/registry/menu.registry"),
            String.join("\n",
                "menus=main,settings,load",
                "layouts=default",
                "styles=default",
                "defaultMenu=main",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/menus/main.menu"),
            String.join("\n",
                "items=new_game,load_game,settings,quit",
                "item.new_game.action=new_game",
                "item.load_game.action=open_menu",
                "item.load_game.target=load",
                "item.settings.action=open_menu",
                "item.settings.target=settings",
                "item.quit.action=quit",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/menus/settings.menu"),
            String.join("\n",
                "items=audio,back",
                "item.audio.action=noop",
                "item.back.action=back",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/menus/load.menu"),
            String.join("\n",
                "items=slot_1,slot_2,back",
                "item.slot_1.action=load_menu",
                "item.slot_2.action=load_menu",
                "item.back.action=back",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/layouts/default.layout"),
            String.join("\n",
                "listYStart=0.24",
                "lineHeight=44",
                "listWidthFactor=0.46",
                "textAlign=left",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/menu/styles/default.style"),
            String.join("\n",
                "itemColor=#e6edf8",
                "itemSelectedColor=#66ccff",
                "itemDisabledColor=#808080",
                "itemFontSize=36",
                "itemPrefix=  ",
                "itemSelectedPrefix=> ",
                "itemDisabledPrefix=- ",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("config/ui/dialogue.layout"),
            String.join("\n",
                "textBoxX=0.04",
                "textBoxY=0.70",
                "textBoxW=0.92",
                "textBoxH=0.24",
                "nameBoxH=0.08",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("docs/project-docs.md"),
            String.join("\n",
                "# Project Docs Fixture",
                "",
                "This fixture project is used for auto-generated editor sidebar screenshots.",
                ""
            )
        );

        writeTextFile(
            fixtureRoot.resolve("scripts/story/prologue.vns"),
            String.join("\n",
                "@label start",
                "narrator: The school day begins.",
                "",
                "@label branch_a",
                "narrator: Route A branch.",
                "",
                "@label branch_b",
                "narrator: Route B branch.",
                ""
            )
        );
        writeTextFile(
            fixtureRoot.resolve("scripts/story/route_a.vns"),
            String.join("\n",
                "@label start",
                "lavender: Welcome to Route A.",
                "",
                "@label converge",
                "lavender: Returning to the core story.",
                ""
            )
        );
        writeTextFile(
            fixtureRoot.resolve("scripts/story/route_b.vns"),
            String.join("\n",
                "@label start",
                "lavender: Welcome to Route B.",
                "",
                "@label converge",
                "lavender: Returning to the core story.",
                ""
            )
        );
        writeTextFile(
            fixtureRoot.resolve("scripts/story/epilogue.vns"),
            String.join("\n",
                "@label start",
                "narrator: End of this fixture timeline.",
                ""
            )
        );
        writeTextFile(
            fixtureRoot.resolve("config/timeline/story.timeline"),
            String.join("\n",
                "arc \"Prologue\" script \"scripts/story/prologue.vns\" entry \"start\" cluster \"Main\" priority 10 color \"#84c7ff\" tags \"intro,main\" at 80,80",
                "arc \"Route A\" script \"scripts/story/route_a.vns\" entry \"start\" cluster \"Routes\" priority 6 color \"#88e0b7\" tags \"branch,a\" at 420,40",
                "arc \"Route B\" script \"scripts/story/route_b.vns\" entry \"start\" cluster \"Routes\" priority 6 color \"#f4b184\" tags \"branch,b\" at 420,220",
                "arc \"Epilogue\" script \"scripts/story/epilogue.vns\" entry \"start\" cluster \"Main\" priority 8 color \"#f2a8d9\" tags \"ending\" at 760,130",
                "link Prologue:branch_a -> \"Route A\":start",
                "link Prologue:branch_b -> \"Route B\":start",
                "link \"Route A\":converge -> Epilogue:start",
                "link \"Route B\":converge -> Epilogue:start",
                ""
            )
        );

        writeTextFile(fixtureRoot.resolve("scripts/docs_fixture.vns"), docsSidebarVnsSource());
        return fixtureRoot;
    }

    private static Path ensureDocsFixtureProjectQuiet(Path repoRoot) {
        try {
            return ensureDocsFixtureProject(repoRoot);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to prepare docs fixture project.", ex);
        }
    }

    private static String docsSidebarVnsSource() {
        return String.join("\n",
            "@background school assets/bg/school_day.png",
            "@charimg lavender neutral assets/characters/lavender/base/lavender_test_sprite_base.png",
            "@charimg lavender happy assets/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png",
            "",
            "@label start",
            "[bg school]",
            "[show lavender center neutral]",
            "> Continue | jump intro",
            "",
            "@label intro",
            "[if affinity > 5 goto route_good]",
            "[jump route_bad]",
            "",
            "@label route_good",
            "[show lavender center happy]",
            "[jump ending]",
            "",
            "@label route_bad",
            "[jump missing_label]",
            "",
            "@label ending",
            "[hide lavender]",
            "",
            "@label orphan",
            "[bg school]",
            ""
        );
    }

    private static void copyFile(Path source, Path target) throws IOException {
        if (source == null || target == null) return;
        if (!Files.exists(source)) {
            throw new IOException("Missing fixture source asset: " + source);
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeTextFile(Path file, String content) throws IOException {
        if (file == null) return;
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    private static Map<String, Node> resolvePuppeteerRegions(Stage stage) {
        Map<String, Node> regions = new LinkedHashMap<>();
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        regions.put("full", root);
        Node toolbar = root.getTop();
        regions.put("toolbar", toolbar);
        if (toolbar instanceof Parent toolbarRoot) {
            putIfFound(regions, toolbarRoot, "toolbar_transport", "toolbar-group-transport-duration");
            putIfFound(regions, toolbarRoot, "toolbar_duration", "toolbar-cluster-duration");
            putIfFound(regions, toolbarRoot, "toolbar_presets", "toolbar-cluster-presets");
            putIfFound(regions, toolbarRoot, "toolbar_property", "toolbar-cluster-property");
            putIfFound(regions, toolbarRoot, "toolbar_key_ops", "toolbar-group-keyframe-ops");
            putIfFound(regions, toolbarRoot, "toolbar_snap", "toolbar-cluster-snap");
            putIfFound(regions, toolbarRoot, "toolbar_autokey", "toolbar-cluster-preview");
            putIfFound(regions, toolbarRoot, "toolbar_preview_modes", "toolbar-group-preview-modes");
            putIfFound(regions, toolbarRoot, "toolbar_orbit", "toolbar-cluster-orbit");
            putIfFound(regions, toolbarRoot, "toolbar_audio", "toolbar-cluster-audio");
            putIfFound(regions, toolbarRoot, "toolbar_register", "toolbar-cluster-register");
            putIfFound(regions, toolbarRoot, "toolbar_help_btn", "toolbar-cluster-help");
            putIfFound(regions, toolbarRoot, "toolbar_orbit_audio_register", "toolbar-group-orbit-audio-register");
        }

        Node centerNode = root.getCenter();
        if (centerNode instanceof SplitPane mainSplit) {
            List<Node> mainItems = mainSplit.getItems();
            if (!mainItems.isEmpty()) {
                regions.put("entities", mainItems.get(0));
                if (mainItems.get(0) instanceof SplitPane leftSplit) {
                    List<Node> leftItems = leftSplit.getItems();
                    putIfPresent(regions, "left_tabs", leftItems, 0);
                    putIfPresent(regions, "keyframe_editor", leftItems, 1);
                }
            }
            if (mainItems.size() >= 2 && mainItems.get(1) instanceof SplitPane centerSplit) {
                List<Node> centerItems = centerSplit.getItems();
                if (!centerItems.isEmpty()) {
                    regions.put("preview", centerItems.get(0));
                    if (centerItems.get(0) instanceof BorderPane previewPane) {
                        regions.put("preview_info", previewPane.getTop());
                        regions.put("preview_canvas", previewPane.getCenter());
                    }
                }
                if (centerItems.size() >= 2) {
                    regions.put("timeline", centerItems.get(1));
                }
            }
            if (mainItems.size() >= 3) {
                regions.put("code", mainItems.get(2));
                if (mainItems.get(2) instanceof javafx.scene.layout.VBox codeBox) {
                    List<Node> codeChildren = codeBox.getChildren();
                    putIfPresent(regions, "code_header", codeChildren, 0);
                    putIfPresent(regions, "code_status", codeChildren, 1);
                    putIfPresent(regions, "code_editor", codeChildren, 2);
                    putIfPresent(regions, "code_actions", codeChildren, 3);
                    putIfPresent(regions, "code_diagnostics", codeChildren, 4);
                }
            }
        }
        regions.put("status_bar", root.getBottom());
        return regions;
    }

    private static void putIfPresent(Map<String, Node> out, String key, List<Node> nodes, int index) {
        if (out == null || key == null || nodes == null) return;
        if (index < 0 || index >= nodes.size()) return;
        Node node = nodes.get(index);
        if (node != null) out.put(key, node);
    }

    private static void putIfFound(Map<String, Node> out, Parent root, String key, String nodeId) {
        if (out == null || key == null || root == null || nodeId == null || nodeId.isBlank()) return;
        Node node = root.lookup("#" + nodeId);
        if (node != null) {
            out.put(key, node);
        }
    }

    private static Map<String, Node> resolveImageTintRegions(Stage stage) {
        Map<String, Node> regions = new LinkedHashMap<>();
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        regions.put("full", root);
        regions.put("preview", root.getCenter());
        regions.put("sidebar", root.getRight());
        return regions;
    }

    private static Map<String, Node> resolveHelpCenterRegions(Stage stage) {
        Map<String, Node> regions = resolveFullRegions(stage);
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        Node center = root.getCenter();
        if (center instanceof SplitPane split && split.getItems().size() >= 2) {
            regions.put("guide_tree", split.getItems().get(0));
            regions.put("preview", split.getItems().get(1));
        }
        return regions;
    }

    private static Map<String, Node> resolveProjectExplorerRegions(Stage stage) {
        Map<String, Node> regions = resolveFullRegions(stage);
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof VBox root)) {
            return regions;
        }
        List<Node> children = root.getChildren();
        if (!children.isEmpty()) {
            regions.put("header", children.get(0));
        }
        if (children.size() >= 2) {
            regions.put("filter", children.get(1));
        }
        if (children.size() >= 3) {
            regions.put("tree", children.get(2));
        }
        return regions;
    }

    private static Map<String, Node> resolveTextEditorRegions(Stage stage) {
        Map<String, Node> regions = resolveFullRegions(stage);
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        regions.put("header", root.getTop());
        Node center = root.getCenter();
        if (center instanceof SplitPane split && split.getItems().size() >= 2) {
            regions.put("explorer", split.getItems().get(0));
            regions.put("inspector", split.getItems().get(1));
        }
        return regions;
    }

    private static Map<String, Node> resolveWelcomeCenterRegions(Stage stage) {
        Map<String, Node> regions = resolveFullRegions(stage);
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        Node center = root.getCenter();
        if (center instanceof VBox box && box.getChildren().size() >= 2) {
            regions.put("hero", box.getChildren().get(0));
            Node splitNode = box.getChildren().get(1);
            if (splitNode instanceof SplitPane split && split.getItems().size() >= 2) {
                regions.put("recent_projects", split.getItems().get(0));
                regions.put("environment_health", split.getItems().get(1));
            }
        }
        return regions;
    }

    private static Map<String, Node> resolveRunConsoleRegions(Stage stage) {
        Map<String, Node> regions = resolveFullRegions(stage);
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        Node top = root.getTop();
        if (top instanceof VBox topBox && topBox.getChildren().size() >= 2) {
            regions.put("menu", topBox.getChildren().get(0));
            regions.put("toolbar", topBox.getChildren().get(1));
        } else if (top != null) {
            regions.put("toolbar", top);
        }
        regions.put("output", root.getCenter());
        regions.put("status", root.getBottom());
        return regions;
    }

    private static void populateRunConsole(RunConsoleView view, Path fixtureRoot) {
        if (view == null) return;
        String projectPath = fixtureRoot == null ? "<project>" : fixtureRoot.toAbsolutePath().normalize().toString();
        view.appendLine("> Task :runtime:run");
        view.appendLine("INFO  [main] com.jvn.runtime.JvnApp - Project root -> " + projectPath);
        view.appendLine("INFO  [main] com.jvn.runtime.JvnApp - Loading jvn.project");
        view.appendLine("INFO  [main] com.jvn.runtime.JvnApp - Assets -> images=12, audio=4, scripts=6");
        view.appendLine("WARN  [main] com.jvn.runtime.JvnApp - Missing optional locale 'fr-FR'; falling back to en-US");
        view.appendLine("INFO  [JavaFX App Thread] com.jvn.fx.FxLauncher - Runtime viewport -> window=1280x720, logical=1920x1080");
        view.appendLine("INFO  [JavaFX App Thread] com.jvn.fx.FxLauncher - Applied custom cursor 'assets/ui/cursor/cursor.png'");
        view.appendLine("INFO  [JavaFX App Thread] com.jvn.fx.FxLauncher - Scene ready: start");
        view.appendLine("WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @7abc1234'");
        view.appendLine("INFO  [JavaFX App Thread] com.jvn.fx.FxLauncher - Perf HUD active");
        view.appendLine("at scripts/story/prologue.vns:18 unresolved optional branch marker");
        view.setState(RunConsoleView.EngineState.RUNNING);
    }

    private static Map<String, Node> resolveStoryTimelineRegions(Stage stage) {
        Map<String, Node> regions = new LinkedHashMap<>();
        if (stage == null || stage.getScene() == null || !(stage.getScene().getRoot() instanceof BorderPane root)) {
            return regions;
        }
        regions.put("full", root);
        regions.put("toolbar", root.getTop());
        Node center = root.getCenter();
        if (center instanceof SplitPane split && split.getItems().size() >= 2) {
            regions.put("graph", split.getItems().get(0));
            regions.put("lists", split.getItems().get(1));
            if (split.getItems().get(1) instanceof javafx.scene.control.TabPane tabs && !tabs.getTabs().isEmpty()) {
                if (tabs.getTabs().size() > 1) {
                    tabs.getSelectionModel().select(1);
                    tabs.applyCss();
                    tabs.layout();
                    Node links = tabs.getTabs().get(1).getContent();
                    if (links != null) regions.put("links_list", links);
                    tabs.getSelectionModel().select(0);
                }
                Node arcs = tabs.getTabs().get(0).getContent();
                if (arcs != null) regions.put("arcs_list", arcs);
            }
        }
        return regions;
    }

    private static AnimationProject buildPuppeteerDemoProject() {
        AnimationProject project = new AnimationProject();
        project.setName("docs_demo");
        project.setTotalDurationMs(3200);
        project.setPlayheadMs(900);

        EntityTrack bg = project.getOrCreateTrack("bg_field_day");
        bg.setLayerOrder(0);
        bg.upsertKeyframe(PropertyType.X, new Keyframe(0, 0));
        bg.upsertKeyframe(PropertyType.Y, new Keyframe(0, 0));

        EntityTrack body = project.getOrCreateTrack("lavender");
        body.setLayerOrder(10);
        body.upsertKeyframe(PropertyType.X, new Keyframe(0, 640));
        body.upsertKeyframe(PropertyType.X, new Keyframe(1200, 690));
        body.upsertKeyframe(PropertyType.X, new Keyframe(2600, 610));
        body.upsertKeyframe(PropertyType.Y, new Keyframe(0, 705));
        body.upsertKeyframe(PropertyType.Y, new Keyframe(2600, 715));
        body.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
        body.upsertKeyframe(PropertyType.ROTATION, new Keyframe(2200, 4));

        EntityTrack head = project.getOrCreateTrack("lavender_head");
        head.setLayerOrder(12);
        head.upsertKeyframe(PropertyType.X, new Keyframe(0, 640));
        head.upsertKeyframe(PropertyType.X, new Keyframe(1200, 695));
        head.upsertKeyframe(PropertyType.X, new Keyframe(2600, 615));
        head.upsertKeyframe(PropertyType.Y, new Keyframe(0, 345));
        head.upsertKeyframe(PropertyType.Y, new Keyframe(2600, 355));
        head.upsertKeyframe(PropertyType.ROTATION, new Keyframe(0, 0));
        head.upsertKeyframe(PropertyType.ROTATION, new Keyframe(1200, -7));
        head.upsertKeyframe(PropertyType.ROTATION, new Keyframe(2600, 6));

        project.setOrbitAnchor("lavender_head", 640, 395);
        project.setOrbitAnchorSource("lavender_head", "lavender", 0, -310);
        return project;
    }

    private static JesScene2D buildPuppeteerDemoScene() {
        JesScene2D scene = new JesScene2D();

        Sprite2D bg = new Sprite2D("demo-assets/demo_bg/game.png", 1280, 720);
        bg.setOrigin(0.0, 0.0);
        bg.setPosition(0.0, 0.0);
        bg.setZ(0.0);
        scene.add(bg);
        scene.registerEntity("bg_field_day", bg);

        Sprite2D body = new Sprite2D("demo-assets/Lavender_test_sprite/base/lavender_test_sprite_base.png", 430, 760);
        body.setOrigin(0.5, 1.0);
        body.setPosition(640, 705);
        body.setZ(10.0);
        scene.add(body);
        scene.registerEntity("lavender", body);

        Sprite2D head = new Sprite2D("demo-assets/Lavender_test_sprite/eyes/lavender_test_sprite_eyes_half_closed.png", 205, 116);
        head.setOrigin(0.5, 0.5);
        head.setPosition(640, 345);
        head.setZ(12.0);
        scene.add(head);
        scene.registerEntity("lavender_head", head);

        return scene;
    }

    private static BufferedImage cropForNode(BufferedImage source, Node node, int paddingPx) {
        if (node == null) return source;
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        int x = (int) Math.floor(bounds.getMinX()) - Math.max(0, paddingPx);
        int y = (int) Math.floor(bounds.getMinY()) - Math.max(0, paddingPx);
        int w = (int) Math.ceil(bounds.getWidth()) + Math.max(0, paddingPx) * 2;
        int h = (int) Math.ceil(bounds.getHeight()) + Math.max(0, paddingPx) * 2;
        if (w <= 0 || h <= 0) return source;

        x = clamp(x, 0, source.getWidth() - 1);
        y = clamp(y, 0, source.getHeight() - 1);
        int maxW = source.getWidth() - x;
        int maxH = source.getHeight() - y;
        w = clamp(w, 1, maxW);
        h = clamp(h, 1, maxH);
        return source.getSubimage(x, y, w, h);
    }

    private static BufferedImage upscaleIfNeeded(BufferedImage source, int minWidthPx, int minHeightPx) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) return source;
        if (minWidthPx <= 0 && minHeightPx <= 0) return source;

        double scaleW = minWidthPx > 0 ? (double) minWidthPx / width : 1.0;
        double scaleH = minHeightPx > 0 ? (double) minHeightPx / height : 1.0;
        double scale = Math.max(1.0, Math.max(scaleW, scaleH));
        scale = Math.min(scale, 2.0);
        if (scale <= 1.001) return source;

        int outW = Math.max(1, (int) Math.round(width * scale));
        int outH = Math.max(1, (int) Math.round(height * scale));
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, outW, outH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage annotate(BufferedImage source, String title, List<Callout> callouts) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (title != null && !title.isBlank()) {
                g.setComposite(AlphaComposite.SrcOver.derive(0.85f));
                g.setColor(new Color(5, 10, 18));
                g.fillRoundRect(12, 12, Math.min(source.getWidth() - 24, 420), 34, 10, 10);
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(255, 192, 110));
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                g.drawString(title, 22, 35);
            }

            g.setStroke(new BasicStroke(3f));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            for (Callout callout : callouts) {
                int x = (int) Math.round(callout.x() * source.getWidth());
                int y = (int) Math.round(callout.y() * source.getHeight());
                int w = (int) Math.round(callout.w() * source.getWidth());
                int h = (int) Math.round(callout.h() * source.getHeight());
                if (w <= 0 || h <= 0) continue;

                x = clamp(x, 0, source.getWidth() - 1);
                y = clamp(y, 0, source.getHeight() - 1);
                w = clamp(w, 1, source.getWidth() - x);
                h = clamp(h, 1, source.getHeight() - y);

                g.setColor(new Color(255, 160, 72, 220));
                g.drawRoundRect(x, y, w, h, 10, 10);

                int labelPadding = 8;
                int labelW = g.getFontMetrics().stringWidth(callout.label()) + labelPadding * 2;
                int labelH = 24;
                int labelX = clamp(x, 4, Math.max(4, source.getWidth() - labelW - 4));
                int labelY = y > (labelH + 8) ? y - (labelH + 4) : y + 8;
                labelY = clamp(labelY, 4, Math.max(4, source.getHeight() - labelH - 4));

                g.setColor(new Color(8, 14, 24, 230));
                g.fillRoundRect(labelX, labelY, labelW, labelH, 8, 8);
                g.setColor(new Color(255, 211, 147));
                g.drawRoundRect(labelX, labelY, labelW, labelH, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(callout.label(), labelX + labelPadding, labelY + 16);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage createContactSheet(List<RenderedShot> shots) {
        if (shots == null || shots.isEmpty()) return null;

        List<RenderedShot> sorted = new ArrayList<>(shots);
        sorted.sort(Comparator.comparing(s -> s.spec().key()));

        int columns = 2;
        int rows = (int) Math.ceil(sorted.size() / (double) columns);
        int margin = 20;
        int cellW = 860;
        int cellH = 560;
        int headerH = 64;

        int width = columns * cellW + (columns + 1) * margin;
        int height = rows * cellH + (rows + 1) * margin + headerH;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(new Color(10, 14, 20));
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(255, 210, 144));
            g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString("Docs Screenshot Contact Sheet", margin, 42);

            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(180, 190, 206));
            g.drawString(LocalDateTime.now().format(STAMP_FMT), margin, 58);

            for (int i = 0; i < sorted.size(); i++) {
                RenderedShot shot = sorted.get(i);
                int row = i / columns;
                int col = i % columns;
                int x = margin + col * (cellW + margin);
                int y = headerH + margin + row * (cellH + margin);

                g.setColor(new Color(26, 32, 44));
                g.fillRoundRect(x, y, cellW, cellH, 14, 14);
                g.setColor(new Color(70, 82, 102));
                g.drawRoundRect(x, y, cellW, cellH, 14, 14);

                BufferedImage img = shot.image();
                if (img == null) continue;
                double sx = (cellW - 30.0) / Math.max(1.0, img.getWidth());
                double sy = (cellH - 70.0) / Math.max(1.0, img.getHeight());
                double scale = Math.min(1.0, Math.min(sx, sy));
                int drawW = Math.max(1, (int) Math.round(img.getWidth() * scale));
                int drawH = Math.max(1, (int) Math.round(img.getHeight() * scale));
                int drawX = x + (cellW - drawW) / 2;
                int drawY = y + 40 + (cellH - 55 - drawH) / 2;
                g.drawImage(img, drawX, drawY, drawW, drawH, null);

                g.setColor(new Color(239, 244, 255));
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                g.drawString(shot.spec().title(), x + 12, y + 27);
            }
        } finally {
            g.dispose();
        }
        return sheet;
    }

    private String buildMarkdownSection(ProfileSpec profile, List<RenderedShot> shots, boolean hasContactSheet, boolean includeRawLinks) {
        String stamp = LocalDateTime.now().format(STAMP_FMT);
        Path guidePath = repoRoot.resolve(profile.guideDoc());
        Path imageDir = repoRoot.resolve(profile.imageDir());
        Path rawDir = repoRoot.resolve(profile.rawImageDir());

        StringBuilder sb = new StringBuilder();
        sb.append("### Visual Reference (Auto-Generated)\n\n");
        sb.append("_Generated by `./gradlew :editor:generateDocsScreenshots -D")
            .append(PROP_PROFILE).append("=").append(profile.key())
            .append("` on ").append(stamp).append("._\n\n");

        if (hasContactSheet) {
            Path contact = imageDir.resolve(CONTACT_SHEET_FILE);
            sb.append("#### Contact Sheet\n\n");
            sb.append("![Contact Sheet](")
                .append(relativeDocPath(guidePath, contact))
                .append(")\n\n");
        }

        for (RenderedShot shot : shots) {
            Path annotated = imageDir.resolve(shot.spec().fileName());
            sb.append("#### ").append(shot.spec().title()).append("\n\n");
            sb.append("![").append(shot.spec().title()).append("](")
                .append(relativeDocPath(guidePath, annotated)).append(")\n\n");
            sb.append(shot.spec().caption()).append("\n\n");
            if (includeRawLinks && shot.hasRaw()) {
                Path raw = rawDir.resolve(shot.spec().fileName());
                sb.append("Raw capture: [")
                    .append(shot.spec().fileName())
                    .append("](")
                    .append(relativeDocPath(guidePath, raw))
                    .append(")\n\n");
            }
        }
        return sb.toString().trim();
    }

    private static void writeGeneratedSnippet(Path snippetPath, String markdown, String profileName) throws IOException {
        Files.createDirectories(snippetPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(profileName).append(" Screenshots (Generated)\n\n");
        sb.append(markdown).append('\n');
        Files.writeString(snippetPath, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Updated " + snippetPath);
    }

    private static void updateGuideBlock(Path guidePath, String markdown, String startMarker, String endMarker) throws IOException {
        Files.createDirectories(guidePath.getParent());
        String existing = Files.exists(guidePath)
            ? Files.readString(guidePath, StandardCharsets.UTF_8)
            : "";

        String replacementBlock = startMarker + "\n" + markdown + "\n" + endMarker;
        int start = existing.indexOf(startMarker);
        int end = existing.indexOf(endMarker);
        String updated;
        if (start >= 0 && end > start) {
            int endExclusive = end + endMarker.length();
            updated = existing.substring(0, start) + replacementBlock + existing.substring(endExclusive);
        } else if (existing.isBlank()) {
            updated = replacementBlock + "\n";
        } else {
            System.out.println("Skipped " + guidePath + " (markers missing: " + startMarker + " / " + endMarker + ")");
            return;
        }
        Files.writeString(guidePath, updated, StandardCharsets.UTF_8);
        System.out.println("Updated " + guidePath);
    }

    private static RunOptions resolveRunOptions() {
        Set<String> profiles = parseProfileKeys(System.getProperty(PROP_PROFILE, "all"));
        Set<String> shotFilter = parseCsvLower(System.getProperty(PROP_SHOTS, ""));
        boolean annotate = parseBooleanProperty(PROP_ANNOTATE, true);
        boolean includeRaw = parseBooleanProperty(PROP_INCLUDE_RAW, false);
        boolean updateDocs = parseBooleanProperty(PROP_UPDATE_DOCS, true);
        boolean includeContactSheet = parseBooleanProperty(PROP_INCLUDE_CONTACT_SHEET, true);
        return new RunOptions(profiles, shotFilter, annotate, includeRaw, updateDocs, includeContactSheet);
    }

    private static List<ProfileSpec> resolveProfiles(Set<String> profileKeys) {
        List<ProfileSpec> out = new ArrayList<>();
        for (String key : profileKeys) {
            ProfileSpec spec = PROFILES.get(key);
            if (spec != null) out.add(spec);
            else System.err.println("Skipping unknown docs screenshot profile: " + key);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("No valid profiles resolved from -D" + PROP_PROFILE + ".");
        }
        return out;
    }

    private static Set<String> parseProfileKeys(String value) {
        String norm = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (norm.isBlank() || "all".equals(norm)) {
            return new LinkedHashSet<>(DEFAULT_PROFILE_KEYS);
        }
        return parseCsvLower(norm);
    }

    private static List<ShotSpec> selectShots(List<ShotSpec> allShots, Set<String> filter) {
        if (filter == null || filter.isEmpty()) return allShots;
        List<ShotSpec> selected = new ArrayList<>();
        for (ShotSpec shot : allShots) {
            String key = shot.key().toLowerCase(Locale.ROOT);
            String region = shot.regionKey().toLowerCase(Locale.ROOT);
            if (filter.contains(key) || filter.contains(region) || filter.contains(shot.fileName().toLowerCase(Locale.ROOT))) {
                selected.add(shot);
            }
        }
        return selected;
    }

    private static Set<String> parseCsvLower(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;
        String[] parts = raw.split(",");
        for (String part : parts) {
            String s = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private static boolean parseBooleanProperty(String key, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("1".equals(value) || "true".equals(value) || "yes".equals(value) || "on".equals(value)) return true;
        if ("0".equals(value) || "false".equals(value) || "no".equals(value) || "off".equals(value)) return false;
        return defaultValue;
    }

    private static String relativeDocPath(Path guideDocPath, Path targetPath) {
        Path fromDir = guideDocPath.getParent();
        if (fromDir == null) return targetPath.toString().replace('\\', '/');
        return fromDir.relativize(targetPath).toString().replace('\\', '/');
    }

    private static Path resolveRepoRoot() {
        String configured = System.getProperty("jvn.repoRoot");
        Path candidate = configured == null || configured.isBlank()
            ? Path.of("").toAbsolutePath().normalize()
            : Path.of(configured).toAbsolutePath().normalize();
        if (Files.isDirectory(candidate.resolve("docs")) && Files.isDirectory(candidate.resolve("editor"))) {
            return candidate;
        }
        throw new IllegalStateException("Could not resolve repository root. Use -Djvn.repoRoot=/abs/path.");
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    private record ShotSpec(
        String key,
        String regionKey,
        String fileName,
        String title,
        String caption,
        int paddingPx,
        int minWidthPx,
        int minHeightPx,
        List<Callout> callouts
    ) {}

    private record Callout(
        String label,
        double x,
        double y,
        double w,
        double h
    ) {}

    private record RenderedShot(
        ShotSpec spec,
        BufferedImage image,
        boolean hasRaw
    ) {}

    private record RunOptions(
        Set<String> profileKeys,
        Set<String> shotFilter,
        boolean annotate,
        boolean includeRaw,
        boolean updateDocs,
        boolean includeContactSheet
    ) {}

    private record ProfileSpec(
        String key,
        String displayName,
        String guideDoc,
        String snippetDoc,
        String imageDir,
        String rawImageDir,
        String startMarker,
        String endMarker,
        long warmupMs,
        List<ShotSpec> shots,
        StageFactory stageFactory,
        RegionResolver regionResolver
    ) {}

    @FunctionalInterface
    private interface StageFactory {
        Stage create(Path repoRoot) throws Exception;
    }

    @FunctionalInterface
    private interface RegionResolver {
        Map<String, Node> resolve(Stage stage);
    }
}

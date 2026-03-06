package com.jvn.editor.ui.actioneditor.docs;

import com.jvn.core.scene2d.Sprite2D;
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
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
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
 * Opens Puppeteer with a deterministic demo state, captures key UI regions, annotates
 * screenshots, writes image assets under docs/, and updates the docs guide block.
 */
public final class PuppeteerDocsScreenshotTool extends Application {
    private static final String START_MARKER = "<!-- AUTO-PUPPETEER-SCREENSHOTS:START -->";
    private static final String END_MARKER = "<!-- AUTO-PUPPETEER-SCREENSHOTS:END -->";

    private static final String GUIDE_DOC = "docs/editor/puppeteer/puppeteer-editor-guide.md";
    private static final String GENERATED_SNIPPET_DOC = "docs/editor/puppeteer/generated-puppeteer-screenshots.md";
    private static final String IMAGE_DIR = "docs/assets/images/puppeteer";
    private static final String RAW_IMAGE_DIR = "docs/assets/images/puppeteer/raw";
    private static final String CONTACT_SHEET_FILE = "puppeteer_ui_contact_sheet.png";

    private static final String PROP_SHOTS = "jvn.docs.screenshots.shots";
    private static final String PROP_ANNOTATE = "jvn.docs.screenshots.annotate";
    private static final String PROP_INCLUDE_RAW = "jvn.docs.screenshots.includeRaw";
    private static final String PROP_UPDATE_DOCS = "jvn.docs.screenshots.updateDocs";
    private static final String PROP_INCLUDE_CONTACT_SHEET = "jvn.docs.screenshots.contactSheet";

    private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final List<ShotSpec> SHOTS = List.of(
        new ShotSpec(
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
        )
    );

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Path repoRoot = resolveRepoRoot();
        PuppeteerWindow window = null;
        try {
            AnimationProject project = buildDemoProject();
            window = new PuppeteerWindow(project);
            window.setProjectRoot(repoRoot.toFile());
            window.setScene(buildDemoScene());
            window.setTitle("Puppeteer Docs Screenshot Session");
            window.setWidth(2200);
            window.setHeight(1300);
            window.show();

            PuppeteerWindow finalWindow = window;
            PauseTransition pause = new PauseTransition(Duration.millis(900));
            pause.setOnFinished(evt -> Platform.runLater(() -> captureAndWrite(finalWindow, repoRoot)));
            pause.play();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            if (window != null) window.close();
            Platform.exit();
            System.exit(1);
        }
    }

    private void captureAndWrite(PuppeteerWindow window, Path repoRoot) {
        try {
            Scene scene = window.getScene();
            if (scene == null || !(scene.getRoot() instanceof BorderPane root)) {
                throw new IllegalStateException("Puppeteer root layout is not available.");
            }

            scene.getRoot().applyCss();
            scene.getRoot().layout();

            WritableImage snap = scene.snapshot(null);
            BufferedImage fullImage = SwingFXUtils.fromFXImage(snap, null);
            if (fullImage == null) {
                throw new IllegalStateException("Snapshot capture returned null image.");
            }

            Map<String, Node> regionNodes = mapRegions(root);
            Path imageDir = repoRoot.resolve(IMAGE_DIR);
            Files.createDirectories(imageDir);

            for (ShotSpec spec : SHOTS) {
                Node node = regionNodes.get(spec.regionKey());
                BufferedImage rawCrop = cropForNode(fullImage, node, spec.paddingPx());
                BufferedImage adjusted = upscaleIfNeeded(rawCrop, spec.minWidthPx(), spec.minHeightPx());
                BufferedImage annotated = annotate(adjusted, spec.title(), spec.callouts());
                Path out = imageDir.resolve(spec.fileName());
                ImageIO.write(annotated, "png", out.toFile());
                System.out.println("Wrote " + out);
            }

            String markdown = buildMarkdownSection();
            writeGeneratedSnippet(repoRoot.resolve(GENERATED_SNIPPET_DOC), markdown);
            updateGuideBlock(repoRoot.resolve(GUIDE_DOC), markdown);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        } finally {
            window.close();
            Platform.exit();
        }
    }

    private static AnimationProject buildDemoProject() {
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

    private static JesScene2D buildDemoScene() {
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

    private static Map<String, Node> mapRegions(BorderPane root) {
        Map<String, Node> regions = new LinkedHashMap<>();
        regions.put("full", root);
        regions.put("toolbar", root.getTop());

        Node centerNode = root.getCenter();
        if (centerNode instanceof SplitPane mainSplit) {
            List<Node> mainItems = mainSplit.getItems();
            if (!mainItems.isEmpty()) {
                regions.put("entities", mainItems.get(0));
            }
            if (mainItems.size() >= 2 && mainItems.get(1) instanceof SplitPane centerSplit) {
                List<Node> centerItems = centerSplit.getItems();
                if (!centerItems.isEmpty()) {
                    regions.put("preview", centerItems.get(0));
                }
                if (centerItems.size() >= 2) {
                    regions.put("timeline", centerItems.get(1));
                }
            }
            if (mainItems.size() >= 3) {
                regions.put("code", mainItems.get(2));
            }
        }
        return regions;
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

    private static String buildMarkdownSection() {
        String stamp = LocalDateTime.now().format(STAMP_FMT);
        StringBuilder sb = new StringBuilder();
        sb.append("### Visual Reference (Auto-Generated)\n\n");
        sb.append("_Generated by `./gradlew :editor:generatePuppeteerDocsScreenshots` on ").append(stamp).append("._\n\n");
        for (ShotSpec spec : SHOTS) {
            sb.append("#### ").append(spec.title()).append("\n\n");
            sb.append("![").append(spec.title()).append("](../../assets/images/puppeteer/")
                .append(spec.fileName()).append(")\n\n");
            sb.append(spec.caption()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static void writeGeneratedSnippet(Path snippetPath, String markdown) throws IOException {
        Files.createDirectories(snippetPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Puppeteer Screenshots (Generated)\n\n");
        sb.append(markdown).append('\n');
        Files.writeString(snippetPath, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Updated " + snippetPath);
    }

    private static void updateGuideBlock(Path guidePath, String markdown) throws IOException {
        Files.createDirectories(guidePath.getParent());
        String existing = Files.exists(guidePath)
            ? Files.readString(guidePath, StandardCharsets.UTF_8)
            : "";

        String replacementBlock = START_MARKER + "\n" + markdown + "\n" + END_MARKER;
        int start = existing.indexOf(START_MARKER);
        int end = existing.indexOf(END_MARKER);
        String updated;
        if (start >= 0 && end > start) {
            int endExclusive = end + END_MARKER.length();
            updated = existing.substring(0, start) + replacementBlock + existing.substring(endExclusive);
        } else if (existing.isBlank()) {
            updated = replacementBlock + "\n";
        } else {
            String sep = existing.endsWith("\n") ? "\n" : "\n\n";
            updated = existing + sep + replacementBlock + "\n";
        }
        Files.writeString(guidePath, updated, StandardCharsets.UTF_8);
        System.out.println("Updated " + guidePath);
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
}

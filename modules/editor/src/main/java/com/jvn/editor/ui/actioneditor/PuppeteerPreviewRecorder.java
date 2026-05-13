package com.jvn.editor.ui.actioneditor;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Frame-accurate recorder that captures the Puppeteer preview {@link Canvas} at a fixed
 * FPS over a time window and writes:
 *
 * <ul>
 *   <li>A PNG sequence into {@code <projectRoot>/exports/puppeteer/<name>/frame_NNNN.png}</li>
 *   <li>An animated GIF at {@code <projectRoot>/exports/puppeteer/<name>.gif}</li>
 * </ul>
 *
 * <p>The recorder drives the playhead frame-by-frame on the JavaFX thread using an
 * {@link AnimationTimer}, so the user sees the recording happen in real time. It does not
 * block the UI. Encoding to GIF is done incrementally as frames are captured.
 *
 * <p>Use via {@link #record(Spec, Hooks)}.
 */
public final class PuppeteerPreviewRecorder {

    public record Spec(
        File outputDir,
        String baseName,
        double startMs,
        double endMs,
        int fps,
        boolean writePngSequence,
        boolean writeGif,
        int outputWidth,
        int outputHeight
    ) {
        public Spec {
            if (fps < 1) fps = 1;
            if (fps > 60) fps = 60;
            if (endMs < startMs) endMs = startMs;
            if (outputWidth < 0) outputWidth = 0;
            if (outputHeight < 0) outputHeight = 0;
        }
        public int frameCount() {
            return Math.max(1, (int) Math.round((endMs - startMs) / 1000.0 * fps) + 1);
        }
        public long frameDelayCentiseconds() {
            return Math.max(1L, Math.round(100.0 / fps));
        }
        /** Whether the recorder should rescale captured frames to a custom resolution. */
        public boolean hasCustomResolution() {
            return outputWidth > 0 && outputHeight > 0;
        }
    }

    /** Hooks the recorder uses to drive the editor and react to progress/results. */
    public interface Hooks {
        /** Move playhead to the absolute time and render the canvas synchronously. */
        void seekAndRender(double timeMs);
        /** Called on the FX thread for every frame written; values 0..1. */
        default void onProgress(double normalizedProgress) {}
        /** Final outcome on the FX thread. */
        default void onFinished(Result result) {}
    }

    public record Result(boolean success, List<File> outputs, String error) {
        public static Result ok(List<File> outputs) {
            return new Result(true, List.copyOf(outputs), null);
        }
        public static Result fail(String error) {
            return new Result(false, List.of(), error);
        }
    }

    private final Canvas canvas;
    private boolean active;

    public PuppeteerPreviewRecorder(Canvas canvas) {
        this.canvas = canvas;
    }

    public boolean isActive() { return active; }

    /** Cancel the current recording (best effort — finishes the current frame). */
    public void cancel() { this.active = false; }

    /**
     * Begin recording. Must be called on the FX thread. Returns immediately; the
     * {@link Hooks#onFinished(Result)} callback fires when the recording completes
     * or fails.
     */
    public void record(Spec spec, Hooks hooks) {
        if (canvas == null) {
            hooks.onFinished(Result.fail("Preview canvas is unavailable."));
            return;
        }
        if (active) {
            hooks.onFinished(Result.fail("A recording is already in progress."));
            return;
        }
        if (!spec.writePngSequence && !spec.writeGif) {
            hooks.onFinished(Result.fail("Choose at least one output format."));
            return;
        }
        try {
            Files.createDirectories(spec.outputDir.toPath());
        } catch (IOException ex) {
            hooks.onFinished(Result.fail("Cannot create output directory: " + ex.getMessage()));
            return;
        }

        Path pngDir = spec.writePngSequence
            ? spec.outputDir.toPath().resolve(spec.baseName)
            : null;
        if (pngDir != null) {
            try {
                Files.createDirectories(pngDir);
            } catch (IOException ex) {
                hooks.onFinished(Result.fail("Cannot create PNG output dir: " + ex.getMessage()));
                return;
            }
        }

        File gifFile = spec.writeGif
            ? spec.outputDir.toPath().resolve(spec.baseName + ".gif").toFile()
            : null;

        GifSequenceWriter gifWriter;
        ImageOutputStream gifStream;
        try {
            if (gifFile != null) {
                gifStream = new FileImageOutputStream(gifFile);
                gifWriter = new GifSequenceWriter(gifStream, BufferedImage.TYPE_INT_ARGB,
                    (int) spec.frameDelayCentiseconds() * 10, true);
            } else {
                gifStream = null;
                gifWriter = null;
            }
        } catch (IOException ex) {
            hooks.onFinished(Result.fail("Cannot open GIF stream: " + ex.getMessage()));
            return;
        }

        active = true;
        new RecordingDriver(spec, pngDir, gifWriter, gifStream, gifFile, hooks).start();
    }

    private final class RecordingDriver extends AnimationTimer {
        private final Spec spec;
        private final Path pngDir;
        private final GifSequenceWriter gifWriter;
        private final ImageOutputStream gifStream;
        private final File gifFile;
        private final Hooks hooks;
        private final List<File> outputs = new ArrayList<>();
        private int frameIndex = 0;

        RecordingDriver(Spec spec, Path pngDir, GifSequenceWriter gifWriter,
                        ImageOutputStream gifStream, File gifFile, Hooks hooks) {
            this.spec = spec;
            this.pngDir = pngDir;
            this.gifWriter = gifWriter;
            this.gifStream = gifStream;
            this.gifFile = gifFile;
            this.hooks = hooks;
        }

        @Override
        public void handle(long now) {
            if (!active) {
                close(false, "Recording cancelled.");
                return;
            }
            int total = spec.frameCount();
            if (frameIndex >= total) {
                close(true, null);
                return;
            }
            double t = total <= 1
                ? spec.startMs
                : spec.startMs + (spec.endMs - spec.startMs) * frameIndex / (double) (total - 1);
            try {
                hooks.seekAndRender(t);
            } catch (RuntimeException ex) {
                close(false, "Frame render failed: " + ex.getMessage());
                return;
            }
            try {
                BufferedImage image = snapshotCanvas();
                if (pngDir != null) {
                    File png = pngDir.resolve(String.format(java.util.Locale.ROOT,
                        "frame_%04d.png", frameIndex)).toFile();
                    ImageIO.write(image, "png", png);
                    if (frameIndex == 0) outputs.add(png.getParentFile());
                }
                if (gifWriter != null) {
                    gifWriter.writeToSequence(image);
                }
                frameIndex++;
                final double progress = total <= 1 ? 1.0 : frameIndex / (double) total;
                Platform.runLater(() -> hooks.onProgress(Math.min(1.0, progress)));
            } catch (IOException ex) {
                close(false, "Encode failed at frame " + frameIndex + ": " + ex.getMessage());
            }
        }

        private void close(boolean success, String error) {
            stop();
            active = false;
            try {
                if (gifWriter != null) gifWriter.close();
            } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
            try {
                if (gifStream != null) gifStream.close();
            } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
            if (success && gifFile != null && gifFile.isFile()) outputs.add(gifFile);
            Result result = success ? Result.ok(outputs) : Result.fail(error);
            Platform.runLater(() -> hooks.onFinished(result));
        }

        private BufferedImage snapshotCanvas() {
            int w = (int) Math.max(1, Math.round(canvas.getWidth()));
            int h = (int) Math.max(1, Math.round(canvas.getHeight()));
            WritableImage fxImage = new WritableImage(w, h);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#121212"));
            canvas.snapshot(params, fxImage);
            BufferedImage awt = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            BufferedImage source = SwingFXUtils.fromFXImage(fxImage, awt);

            if (!spec.hasCustomResolution() ||
                (spec.outputWidth() == w && spec.outputHeight() == h)) {
                return source;
            }
            int targetW = Math.max(1, spec.outputWidth());
            int targetH = Math.max(1, spec.outputHeight());
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(source, 0, 0, targetW, targetH, null);
            } finally {
                g.dispose();
            }
            return scaled;
        }
    }

    /**
     * Minimal animated GIF writer using ImageIO's GIF plugin. Builds the loop-control
     * application extension on the first frame so the GIF loops indefinitely.
     */
    private static final class GifSequenceWriter implements AutoCloseable {
        private final ImageWriter writer;
        private final ImageWriteParam params;
        private final IIOMetadata metadata;

        GifSequenceWriter(ImageOutputStream out, int imageType, int delayMs, boolean loop)
            throws IOException {
            this.writer = ImageIO.getImageWritersBySuffix("gif").next();
            this.params = writer.getDefaultWriteParam();
            ImageTypeSpecifier spec = ImageTypeSpecifier.createFromBufferedImageType(imageType);
            this.metadata = writer.getDefaultImageMetadata(spec, params);

            String formatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);

            IIOMetadataNode gce = node(root, "GraphicControlExtension");
            gce.setAttribute("disposalMethod", "none");
            gce.setAttribute("userInputFlag", "FALSE");
            gce.setAttribute("transparentColorFlag", "FALSE");
            gce.setAttribute("delayTime", Integer.toString(Math.max(1, delayMs / 10)));
            gce.setAttribute("transparentColorIndex", "0");

            IIOMetadataNode appExtensions = node(root, "ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            int loopFlag = loop ? 0 : 1;
            appExt.setUserObject(new byte[]{0x1, (byte) (loopFlag & 0xFF), (byte) ((loopFlag >> 8) & 0xFF)});
            appExtensions.appendChild(appExt);

            metadata.setFromTree(formatName, root);
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
        }

        void writeToSequence(BufferedImage img) throws IOException {
            writer.writeToSequence(new IIOImage(img, null, metadata), params);
        }

        @Override
        public void close() throws IOException {
            writer.endWriteSequence();
            writer.dispose();
        }

        private static IIOMetadataNode node(IIOMetadataNode root, String name) {
            for (int i = 0; i < root.getLength(); i++) {
                if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                    return (IIOMetadataNode) root.item(i);
                }
            }
            IIOMetadataNode created = new IIOMetadataNode(name);
            root.appendChild(created);
            return created;
        }
    }

}

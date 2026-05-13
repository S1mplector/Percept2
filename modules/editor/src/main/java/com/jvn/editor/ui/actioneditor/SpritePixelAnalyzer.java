package com.jvn.editor.ui.actioneditor;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes sprite images to detect non-transparent pixel regions (body parts /
 * discrete layers). Uses connected-component labeling (BFS, 4-connectivity) to
 * identify contiguous opaque areas, and returns their bounding boxes so the
 * editor can draw selection borders around them.
 */
public class SpritePixelAnalyzer {

    /** Bounding box of a single contiguous opaque region in image-pixel space. */
    public static class DetectedRegion {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;
        public final int width;
        public final int height;
        public final int pixelCount;

        public DetectedRegion(int minX, int minY, int maxX, int maxY, int pixelCount) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.width  = maxX - minX + 1;
            this.height = maxY - minY + 1;
            this.pixelCount = pixelCount;
        }

        public double centerX() { return (minX + maxX) / 2.0; }
        public double centerY() { return (minY + maxY) / 2.0; }
    }

    private static final int ALPHA_THRESHOLD  = 32;   // pixels with alpha ≤ this are transparent
    private static final int MIN_REGION_PIXELS = 10;  // ignore noise regions smaller than this

    private final File projectRoot;

    public SpritePixelAnalyzer(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    /**
     * Detects non-transparent regions in the sprite at {@code imagePath}.
     *
     * @param imagePath relative (to project root) or absolute path
     * @return bounding boxes of each contiguous opaque region, in image-pixel space
     */
    public List<DetectedRegion> detectRegions(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return List.of();

        Image image = loadImage(imagePath);
        if (image == null) return List.of();

        PixelReader reader = image.getPixelReader();
        if (reader == null) return List.of();

        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        if (w <= 0 || h <= 0) return List.of();

        // Build opaque-pixel map and a BFS visited / label array in one pass.
        boolean[][] opaque  = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (reader.getArgb(x, y) >>> 24) & 0xff;
                opaque[y][x] = alpha > ALPHA_THRESHOLD;
            }
        }

        // Connected-component labeling via BFS.
        int[][] labels   = new int[h][w];
        boolean[][] visited = new boolean[h][w];
        int nextLabel = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (opaque[y][x] && !visited[y][x]) {
                    ++nextLabel;
                    bfsFill(opaque, labels, visited, x, y, w, h, nextLabel);
                }
            }
        }

        if (nextLabel == 0) return List.of();

        // Accumulate bounding boxes per label.
        int[] minX = new int[nextLabel + 1];
        int[] minY = new int[nextLabel + 1];
        int[] maxX = new int[nextLabel + 1];
        int[] maxY = new int[nextLabel + 1];
        int[] count = new int[nextLabel + 1];

        for (int i = 1; i <= nextLabel; i++) {
            minX[i] = Integer.MAX_VALUE;
            minY[i] = Integer.MAX_VALUE;
            maxX[i] = Integer.MIN_VALUE;
            maxY[i] = Integer.MIN_VALUE;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int lbl = labels[y][x];
                if (lbl <= 0) continue;
                if (x < minX[lbl]) minX[lbl] = x;
                if (y < minY[lbl]) minY[lbl] = y;
                if (x > maxX[lbl]) maxX[lbl] = x;
                if (y > maxY[lbl]) maxY[lbl] = y;
                count[lbl]++;
            }
        }

        List<DetectedRegion> result = new ArrayList<>();
        for (int i = 1; i <= nextLabel; i++) {
            if (count[i] >= MIN_REGION_PIXELS) {
                result.add(new DetectedRegion(minX[i], minY[i], maxX[i], maxY[i], count[i]));
            }
        }
        return result;
    }

    // ── BFS flood fill ───────────────────────────────────────────────────────

    private static void bfsFill(boolean[][] opaque, int[][] labels, boolean[][] visited,
                                 int startX, int startY, int width, int height, int label) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startY][startX] = true;

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cy = cur[1];
            labels[cy][cx] = label;

            // 4-connected neighbours
            if (cx + 1 < width  && opaque[cy][cx + 1] && !visited[cy][cx + 1]) { visited[cy][cx + 1] = true; queue.add(new int[]{cx + 1, cy}); }
            if (cx - 1 >= 0     && opaque[cy][cx - 1] && !visited[cy][cx - 1]) { visited[cy][cx - 1] = true; queue.add(new int[]{cx - 1, cy}); }
            if (cy + 1 < height && opaque[cy + 1][cx] && !visited[cy + 1][cx]) { visited[cy + 1][cx] = true; queue.add(new int[]{cx, cy + 1}); }
            if (cy - 1 >= 0     && opaque[cy - 1][cx] && !visited[cy - 1][cx]) { visited[cy - 1][cx] = true; queue.add(new int[]{cx, cy - 1}); }
        }
    }

    // ── Image loading ────────────────────────────────────────────────────────

    private Image loadImage(String path) {
        try {
            // Absolute path
            File f = new File(path);
            if (f.isAbsolute() && f.exists()) return new Image(f.toURI().toString());

            // Relative to project root
            if (projectRoot != null) {
                File pf = new File(projectRoot, path);
                if (pf.exists()) return new Image(pf.toURI().toString());
            }

            // Classpath resource
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url != null) return new Image(url.toExternalForm());
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
        return null;
    }
}

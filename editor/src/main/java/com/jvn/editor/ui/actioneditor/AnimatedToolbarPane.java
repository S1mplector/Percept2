package com.jvn.editor.ui.actioneditor;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnimatedToolbarPane extends Region {
    public enum LayoutMode {
        DYNAMIC,
        COMPACT
    }

    private static final Duration REORDER_DURATION = Duration.millis(220);
    private static final Interpolator REORDER_INTERPOLATOR = Interpolator.SPLINE(0.2, 0.0, 0.1, 1.0);

    private final double hgap;
    private final double vgap;
    private final List<CollapsibleToolbarCluster> clusters = new ArrayList<>();
    private final Map<CollapsibleToolbarCluster, Integer> baseOrder = new IdentityHashMap<>();
    private final Map<Node, LayoutCell> lastTargets = new IdentityHashMap<>();
    private final Map<Node, Timeline> activeAnimations = new IdentityHashMap<>();
    private final Map<String, MarkerRegion> markers = new LinkedHashMap<>();
    private List<CollapsibleToolbarCluster> lastOrderedClusters = List.of();

    private boolean animateNextLayout;
    private LayoutMode layoutMode = LayoutMode.DYNAMIC;

    public AnimatedToolbarPane(double hgap, double vgap) {
        this.hgap = Math.max(0.0, hgap);
        this.vgap = Math.max(0.0, vgap);
        widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (oldWidth == null || newWidth == null) return;
            if (Math.abs(newWidth.doubleValue() - oldWidth.doubleValue()) > 0.5) {
                triggerAnimatedLayout();
            }
        });
    }

    public void addCluster(CollapsibleToolbarCluster cluster) {
        if (cluster == null || clusters.contains(cluster)) {
            return;
        }
        clusters.add(cluster);
        baseOrder.put(cluster, clusters.size() - 1);
        cluster.setLayoutMode(layoutMode);
        getChildren().add(cluster);
        cluster.expandedProperty().addListener((obs, oldValue, newValue) -> triggerAnimatedLayout());
        cluster.pinnedProperty().addListener((obs, oldValue, newValue) -> triggerAnimatedLayout());
    }

    public LayoutMode getLayoutMode() {
        return layoutMode;
    }

    public void setLayoutMode(LayoutMode mode) {
        LayoutMode resolved = mode != null ? mode : LayoutMode.DYNAMIC;
        if (layoutMode == resolved) {
            return;
        }
        layoutMode = resolved;
        for (CollapsibleToolbarCluster cluster : clusters) {
            cluster.setLayoutMode(resolved);
        }
        animateNextLayout = resolved == LayoutMode.DYNAMIC;
        requestLayout();
    }

    public void registerMarker(String id, Node... members) {
        if (id == null || id.isBlank() || members == null || members.length == 0 || markers.containsKey(id)) {
            return;
        }
        MarkerRegion marker = new MarkerRegion(id, List.of(members));
        markers.put(id, marker);
        getChildren().add(marker);
    }

    public void triggerAnimatedLayout() {
        animateNextLayout = true;
        requestLayout();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets insets = getInsets();
        return insets.getLeft() + insets.getRight() + naturalContentWidth();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets insets = getInsets();
        double availableWidth = width <= 0
            ? naturalContentWidth()
            : Math.max(1.0, width - insets.getLeft() - insets.getRight());
        LayoutPlan plan = buildLayoutPlan(availableWidth, insets.getLeft(), insets.getTop());
        return insets.getTop() + plan.height() + insets.getBottom();
    }

    @Override
    protected void layoutChildren() {
        Insets insets = getInsets();
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double availableWidth = Math.max(1.0, getWidth() - left - snappedRightInset());
        LayoutPlan plan = buildLayoutPlan(availableWidth, left, top);
        boolean animateTransitions = layoutMode == LayoutMode.DYNAMIC
            && (animateNextLayout || !sameOrder(plan.orderedClusters(), lastOrderedClusters));
        animateNextLayout = false;
        lastOrderedClusters = List.copyOf(plan.orderedClusters());

        for (CollapsibleToolbarCluster cluster : clusters) {
            LayoutCell cell = plan.cells().get(cluster);
            if (cell == null) {
                continue;
            }
            placeCluster(cluster, cell, animateTransitions);
        }

        for (MarkerRegion marker : markers.values()) {
            marker.update(plan.cells());
        }
    }

    private void placeCluster(Node node, LayoutCell cell, boolean animateTransitions) {
        LayoutCell lastCell = lastTargets.get(node);
        double visualX = node.getLayoutX() + node.getTranslateX();
        double visualY = node.getLayoutY() + node.getTranslateY();

        node.resizeRelocate(cell.x(), cell.y(), cell.width(), cell.height());

        if (lastCell == null) {
            lastTargets.put(node, cell);
            node.setTranslateX(0.0);
            node.setTranslateY(0.0);
            return;
        }

        if (animateTransitions && !approximatelySame(lastCell, cell)) {
            Timeline timeline = activeAnimations.remove(node);
            if (timeline != null) {
                timeline.stop();
            }
            double offsetX = visualX - cell.x();
            double offsetY = visualY - cell.y();
            node.setTranslateX(offsetX);
            node.setTranslateY(offsetY);

            Timeline motion = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(node.translateXProperty(), offsetX),
                    new KeyValue(node.translateYProperty(), offsetY)
                ),
                new KeyFrame(REORDER_DURATION,
                    new KeyValue(node.translateXProperty(), 0.0, REORDER_INTERPOLATOR),
                    new KeyValue(node.translateYProperty(), 0.0, REORDER_INTERPOLATOR)
                )
            );
            motion.setOnFinished(event -> activeAnimations.remove(node));
            activeAnimations.put(node, motion);
            motion.play();
        }

        lastTargets.put(node, cell);
    }

    private LayoutPlan buildLayoutPlan(double availableWidth, double left, double top) {
        double resolvedHgap = effectiveHgap();
        double resolvedVgap = effectiveVgap();
        List<CollapsibleToolbarCluster> ordered = orderedClusters();
        Map<Node, LayoutCell> cells = new IdentityHashMap<>();
        if (ordered.isEmpty()) {
            return new LayoutPlan(List.of(), cells, 0.0, 0.0);
        }

        List<RowPlan> rows = new ArrayList<>();
        for (CollapsibleToolbarCluster cluster : ordered) {
            double width = snapSizeX(preferredClusterWidth(cluster));
            double height = snapSizeY(preferredClusterHeight(cluster));
            placeIntoBestRow(rows, cluster, width, height, availableWidth, resolvedHgap);
        }

        double y = top;
        double maxRight = left;
        List<CollapsibleToolbarCluster> visualOrder = new ArrayList<>(ordered.size());

        for (RowPlan row : rows) {
            double x = left;
            for (RowEntry entry : row.entries()) {
                cells.put(entry.cluster(), new LayoutCell(x, y, entry.width(), entry.height()));
                visualOrder.add(entry.cluster());
                x += entry.width() + resolvedHgap;
                maxRight = Math.max(maxRight, x - resolvedHgap);
            }
            y += row.height() + resolvedVgap;
        }

        double contentWidth = Math.max(0.0, maxRight - left);
        double contentHeight = Math.max(0.0, y - top - resolvedVgap);
        return new LayoutPlan(visualOrder, cells, contentWidth, contentHeight);
    }

    private List<CollapsibleToolbarCluster> orderedClusters() {
        if (layoutMode == LayoutMode.COMPACT) {
            return List.copyOf(clusters);
        }
        List<CollapsibleToolbarCluster> ordered = new ArrayList<>(clusters);
        Comparator<CollapsibleToolbarCluster> comparator = Comparator
            .comparing(CollapsibleToolbarCluster::isPinned)
            .reversed()
            .thenComparing(CollapsibleToolbarCluster::isExpanded)
            .reversed()
            .thenComparingDouble(this::preferredClusterWidth)
            .reversed()
            .thenComparingInt(cluster -> baseOrder.getOrDefault(cluster, Integer.MAX_VALUE));
        ordered.sort(comparator);
        return ordered;
    }

    private double preferredClusterWidth(CollapsibleToolbarCluster cluster) {
        if (cluster == null) return 0.0;
        double width = cluster.prefWidth(-1);
        if (!Double.isFinite(width) || width < 0.0) {
            width = cluster.minWidth(-1);
        }
        return Math.max(0.0, width);
    }

    private double preferredClusterHeight(CollapsibleToolbarCluster cluster) {
        if (cluster == null) return 0.0;
        double height = cluster.prefHeight(-1);
        if (!Double.isFinite(height) || height < 0.0) {
            height = cluster.minHeight(-1);
        }
        return Math.max(0.0, height);
    }

    private double naturalContentWidth() {
        if (clusters.isEmpty()) {
            return 0.0;
        }
        double width = 0.0;
        double resolvedHgap = effectiveHgap();
        for (int i = 0; i < clusters.size(); i++) {
            if (i > 0) {
                width += resolvedHgap;
            }
            width += preferredClusterWidth(clusters.get(i));
        }
        return width;
    }

    static List<List<Integer>> packWidthRows(List<Double> widths, double availableWidth, double hgap) {
        List<RowWidthPlan> rows = new ArrayList<>();
        if (widths == null || widths.isEmpty()) {
            return List.of();
        }
        double resolvedAvailableWidth = Math.max(1.0, availableWidth);
        double resolvedGap = Math.max(0.0, hgap);
        for (int i = 0; i < widths.size(); i++) {
            double width = widths.get(i) != null ? Math.max(0.0, widths.get(i)) : 0.0;
            placeWidthIntoBestRow(rows, i, width, resolvedAvailableWidth, resolvedGap);
        }
        List<List<Integer>> packed = new ArrayList<>(rows.size());
        for (RowWidthPlan row : rows) {
            packed.add(List.copyOf(row.indices()));
        }
        return List.copyOf(packed);
    }

    private void placeIntoBestRow(List<RowPlan> rows,
                                  CollapsibleToolbarCluster cluster,
                                  double width,
                                  double height,
                                  double availableWidth,
                                  double hgap) {
        RowPlan target = null;
        for (RowPlan row : rows) {
            if (row.canFit(width, availableWidth, hgap)) {
                target = row;
                break;
            }
        }
        if (target == null) {
            target = new RowPlan();
            rows.add(target);
        }
        target.add(cluster, width, height, hgap);
    }

    private double effectiveHgap() {
        return layoutMode == LayoutMode.COMPACT ? Math.max(4.0, hgap - 2.0) : hgap;
    }

    private double effectiveVgap() {
        return layoutMode == LayoutMode.COMPACT ? Math.max(4.0, vgap - 2.0) : vgap;
    }

    private static void placeWidthIntoBestRow(List<RowWidthPlan> rows,
                                              int index,
                                              double width,
                                              double availableWidth,
                                              double hgap) {
        RowWidthPlan target = null;
        for (RowWidthPlan row : rows) {
            if (row.canFit(width, availableWidth, hgap)) {
                target = row;
                break;
            }
        }
        if (target == null) {
            target = new RowWidthPlan();
            rows.add(target);
        }
        target.add(index, width, hgap);
    }

    private static boolean sameOrder(List<CollapsibleToolbarCluster> left, List<CollapsibleToolbarCluster> right) {
        if (left == right) return true;
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean approximatelySame(LayoutCell left, LayoutCell right) {
        if (left == null || right == null) return false;
        return Math.abs(left.x() - right.x()) < 0.5
            && Math.abs(left.y() - right.y()) < 0.5
            && Math.abs(left.width() - right.width()) < 0.5
            && Math.abs(left.height() - right.height()) < 0.5;
    }

    private record LayoutCell(double x, double y, double width, double height) {}

    private record RowEntry(CollapsibleToolbarCluster cluster, double width, double height) {}

    private record LayoutPlan(List<CollapsibleToolbarCluster> orderedClusters,
                              Map<Node, LayoutCell> cells,
                              double width,
                              double height) {}

    private static final class RowPlan {
        private final List<RowEntry> entries = new ArrayList<>();
        private double usedWidth;
        private double height;

        private boolean canFit(double width, double availableWidth, double hgap) {
            if (entries.isEmpty()) return true;
            return usedWidth + hgap + width <= availableWidth + 0.5;
        }

        private void add(CollapsibleToolbarCluster cluster, double width, double height, double hgap) {
            if (!entries.isEmpty()) {
                usedWidth += hgap;
            }
            entries.add(new RowEntry(cluster, width, height));
            usedWidth += width;
            this.height = Math.max(this.height, height);
        }

        private List<RowEntry> entries() {
            return entries;
        }

        private double height() {
            return height;
        }
    }

    private static final class RowWidthPlan {
        private final List<Integer> indices = new ArrayList<>();
        private double usedWidth;

        private boolean canFit(double width, double availableWidth, double hgap) {
            if (indices.isEmpty()) return true;
            return usedWidth + hgap + width <= availableWidth + 0.5;
        }

        private void add(int index, double width, double hgap) {
            if (!indices.isEmpty()) {
                usedWidth += hgap;
            }
            indices.add(index);
            usedWidth += width;
        }

        private List<Integer> indices() {
            return indices;
        }
    }

    private static final class MarkerRegion extends Region {
        private final List<Node> members;

        private MarkerRegion(String id, List<Node> members) {
            this.members = members == null ? List.of() : List.copyOf(members);
            setId(id);
            setManaged(false);
            setMouseTransparent(true);
            setOpacity(0.0);
        }

        private void update(Map<Node, LayoutCell> cells) {
            if (members.isEmpty() || cells == null) {
                resizeRelocate(0.0, 0.0, 0.0, 0.0);
                return;
            }

            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            for (Node member : members) {
                LayoutCell cell = cells.get(member);
                if (cell == null) continue;
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                maxX = Math.max(maxX, cell.x() + cell.width());
                maxY = Math.max(maxY, cell.y() + cell.height());
            }

            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
                resizeRelocate(0.0, 0.0, 0.0, 0.0);
                return;
            }

            resizeRelocate(minX, minY, Math.max(0.0, maxX - minX), Math.max(0.0, maxY - minY));
        }
    }
}

package com.jvn.editor.ui.actioneditor;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
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
        getChildren().add(cluster);
        cluster.expandedProperty().addListener((obs, oldValue, newValue) -> triggerAnimatedLayout());
        cluster.pinnedProperty().addListener((obs, oldValue, newValue) -> triggerAnimatedLayout());
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
    protected double computePrefWidth(double height) {
        Insets insets = getInsets();
        return insets.getLeft() + insets.getRight() + naturalContentWidth();
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
        boolean animateTransitions = animateNextLayout || !sameOrder(plan.orderedClusters(), lastOrderedClusters);
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
        List<CollapsibleToolbarCluster> ordered = orderedClusters();
        Map<Node, LayoutCell> cells = new IdentityHashMap<>();

        double x = left;
        double y = top;
        double rowHeight = 0.0;
        double maxRight = left;

        for (CollapsibleToolbarCluster cluster : ordered) {
            double width = snapSizeX(preferredClusterWidth(cluster));
            double height = snapSizeY(preferredClusterHeight(cluster));
            if (x > left && x + width > left + availableWidth + 0.5) {
                x = left;
                y += rowHeight + vgap;
                rowHeight = 0.0;
            }

            cells.put(cluster, new LayoutCell(x, y, width, height));
            x += width + hgap;
            rowHeight = Math.max(rowHeight, height);
            maxRight = Math.max(maxRight, x - hgap);
        }

        double contentWidth = ordered.isEmpty() ? 0.0 : Math.max(0.0, maxRight - left);
        double contentHeight = ordered.isEmpty() ? 0.0 : Math.max(0.0, y - top + rowHeight);
        return new LayoutPlan(ordered, cells, contentWidth, contentHeight);
    }

    private List<CollapsibleToolbarCluster> orderedClusters() {
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
        for (int i = 0; i < clusters.size(); i++) {
            if (i > 0) {
                width += hgap;
            }
            width += preferredClusterWidth(clusters.get(i));
        }
        return width;
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

    private record LayoutPlan(List<CollapsibleToolbarCluster> orderedClusters,
                              Map<Node, LayoutCell> cells,
                              double width,
                              double height) {}

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

package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryGraphPaneLayoutTest {

  @Test
  void autoLayoutMovesChildrenRightwardByGraphDepth() {
    StoryTimelineView.Arc root = arc("Root", "Intro");
    StoryTimelineView.Arc branch = arc("Branch", "Intro");
    StoryTimelineView.Arc ending = arc("Ending", "Routes");

    StoryTimelineView.Link l1 = link("Root", "Branch");
    StoryTimelineView.Link l2 = link("Branch", "Ending");

    Map<String, StoryGraphPane.LayoutPosition> layout =
        StoryGraphPane.computeAutoLayoutPositions(List.of(root, branch, ending), List.of(l1, l2));

    assertTrue(layout.get("Root").x < layout.get("Branch").x);
    assertTrue(layout.get("Branch").x < layout.get("Ending").x);
  }

  @Test
  void autoLayoutStacksSameRankNodesInsideClusterLane() {
    StoryTimelineView.Arc hub = arc("Hub", "Main");
    StoryTimelineView.Arc a = arc("A", "Main");
    StoryTimelineView.Arc b = arc("B", "Main");

    StoryTimelineView.Link la = link("Hub", "A");
    StoryTimelineView.Link lb = link("Hub", "B");

    Map<String, StoryGraphPane.LayoutPosition> layout =
        StoryGraphPane.computeAutoLayoutPositions(List.of(hub, a, b), List.of(la, lb));

    assertEquals(layout.get("A").x, layout.get("B").x, 0.001);
    assertTrue(layout.get("A").y != layout.get("B").y);
  }

  private static StoryTimelineView.Arc arc(String name, String cluster) {
    StoryTimelineView.Arc arc = new StoryTimelineView.Arc();
    arc.name = name;
    arc.cluster = cluster;
    return arc;
  }

  private static StoryTimelineView.Link link(String from, String to) {
    StoryTimelineView.Link link = new StoryTimelineView.Link();
    link.fromArc = from;
    link.toArc = to;
    return link;
  }
}

package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class StoryTimelineDslTest {

  @Test
  void parsesExtendedArcFieldsAndQuotedLinkEndpoints() {
    String dsl = """
        arc \"Route A\" script \"scripts/story/route_a.vns\" entry \"start\" cluster \"Main Story\" priority 7 color \"#88ccff\" tags \"route,romance\" at 120.5,90
        arc Hub script \"scripts/story/hub.vns\" entry \"begin\" at 40,60

        link \"Route A\":\"choice yes\" -> Hub:start
        link Hub -> \"Route A\":\"start\"
        """;

    StoryTimelineView.ParsedTimeline parsed = StoryTimelineView.parseTimelineDsl(dsl);

    assertEquals(2, parsed.arcs.size());
    assertEquals(2, parsed.links.size());

    StoryTimelineView.Arc route = parsed.arcs.get(0);
    assertEquals("Route A", route.name);
    assertEquals("scripts/story/route_a.vns", route.script);
    assertEquals("start", route.entryLabel);
    assertEquals("Main Story", route.cluster);
    assertEquals(7, route.priority);
    assertEquals("#88ccff", route.color);
    assertEquals("route,romance", route.tags);
    assertEquals(120.5, route.x, 0.0001);
    assertEquals(90.0, route.y, 0.0001);

    StoryTimelineView.Link first = parsed.links.get(0);
    assertEquals("Route A", first.fromArc);
    assertEquals("choice yes", first.fromLabel);
    assertEquals("Hub", first.toArc);
    assertEquals("start", first.toLabel);

    StoryTimelineView.Link second = parsed.links.get(1);
    assertEquals("Hub", second.fromArc);
    assertEquals("", second.fromLabel);
    assertEquals("Route A", second.toArc);
    assertEquals("start", second.toLabel);
  }

  @Test
  void serializesExtendedDslWithStableFields() {
    StoryTimelineView.Arc arc = new StoryTimelineView.Arc();
    arc.name = "Route A";
    arc.script = "scripts/story/route_a.vns";
    arc.entryLabel = "start";
    arc.cluster = "Main";
    arc.priority = 3;
    arc.color = "#ff8844";
    arc.tags = "route,romance";
    arc.x = 12.5;
    arc.y = 95.25;

    StoryTimelineView.Arc hub = new StoryTimelineView.Arc();
    hub.name = "Hub";
    hub.script = "scripts/story/hub.vns";
    hub.entryLabel = "entry";
    hub.x = 40;
    hub.y = 20;

    StoryTimelineView.Link link = new StoryTimelineView.Link();
    link.fromArc = "Route A";
    link.fromLabel = "choice yes";
    link.toArc = "Hub";
    link.toLabel = "entry";

    String out = StoryTimelineView.serializeDsl(List.of(arc, hub), List.of(link));

    assertTrue(out.contains("arc \"Route A\" script \"scripts/story/route_a.vns\" entry \"start\" cluster \"Main\" priority 3 color \"#ff8844\" tags \"route,romance\" at 12.5,95.25"));
    assertTrue(out.contains("arc \"Hub\" script \"scripts/story/hub.vns\" entry \"entry\" at 40.0,20.0"));
    assertTrue(out.contains("link \"Route A\":\"choice yes\" -> Hub:entry"));
  }

  @Test
  void parsesLegacyFormatIncludingNewArcMetadataSlots() {
    String legacy = """
        ARC|Legacy|scripts/legacy.vns|start|10|20|Cluster A|5|#112233|alpha,beta
        ARC|Other|scripts/other.vns|entry|50|60
        LINK|Legacy|from_label|Other|entry
        """;

    StoryTimelineView.ParsedTimeline parsed = StoryTimelineView.parseTimelineDsl(legacy);
    assertEquals(2, parsed.arcs.size());
    assertEquals(1, parsed.links.size());

    StoryTimelineView.Arc a = parsed.arcs.get(0);
    assertEquals("Legacy", a.name);
    assertEquals("Cluster A", a.cluster);
    assertEquals(5, a.priority);
    assertEquals("#112233", a.color);
    assertEquals("alpha,beta", a.tags);

    StoryTimelineView.Link l = parsed.links.get(0);
    assertEquals("Legacy", l.fromArc);
    assertEquals("from_label", l.fromLabel);
    assertEquals("Other", l.toArc);
    assertEquals("entry", l.toLabel);
  }

  @Test
  void parsesQuotedEndpointWithColons() {
    StoryTimelineView.LinkEndpoint endpoint = StoryTimelineView.parseLinkEndpoint("\"Act:One\":\"choice:a\"");
    assertNotNull(endpoint);
    assertEquals("Act:One", endpoint.arc);
    assertEquals("choice:a", endpoint.label);
    assertTrue(endpoint.arcEnd > endpoint.arcStart);
    assertTrue(endpoint.labelEnd > endpoint.labelStart);
  }
}

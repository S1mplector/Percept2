package com.jvn.core.animation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.scene2d.Entity2D;
import org.junit.jupiter.api.Test;

/**
 * Tests for EventCue parsing, runner dispatch, and loop semantics (category E + H).
 */
class TimelineEventCueTest {

    private static class RecordingAccessor implements SceneAccessor {
        final Entity2D hero = new Entity2D();
        final List<String> eventTypes = new ArrayList<>();
        final List<Map<String, String>> eventPayloads = new ArrayList<>();

        @Override
        public Entity2D findEntity(String name) {
            return "hero".equals(name) ? hero : null;
        }

        @Override
        public void onEventCue(String type, Map<String, String> payload) {
            eventTypes.add(type);
            eventPayloads.add(payload);
        }
    }

    @Test
    void parsesEventBlockFromTimelineDsl() {
        String dsl = """
            timeline {
              wait 100
              event "expression" {
                target: lavender
                value: smile
              }
              wait 200
              event "dialogue_marker" {
                id: intro_beat_3
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("evt_test", dsl);
        assertNotNull(data);
        assertEquals(2, data.getEventCues().size());

        TimelineData.EventCue e1 = data.getEventCues().get(0);
        assertEquals(100.0, e1.getTimeMs(), 0.001);
        assertEquals("expression", e1.getType());
        assertEquals("lavender", e1.getPayloadValue("target"));
        assertEquals("smile", e1.getPayloadValue("value"));

        TimelineData.EventCue e2 = data.getEventCues().get(1);
        assertEquals(300.0, e2.getTimeMs(), 0.001);
        assertEquals("dialogue_marker", e2.getType());
        assertEquals("intro_beat_3", e2.getPayloadValue("id"));
    }

    @Test
    void runnerTriggersEventCueOncePerPass() {
        TimelineData data = new TimelineData("evt_run", 500);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("target", "hero");
        payload.put("value", "angry");
        data.addEventCue(new TimelineData.EventCue(200, "expression", payload));

        RecordingAccessor scene = new RecordingAccessor();
        TimelineRunner runner = new TimelineRunner(data, scene);

        runner.update(100); // t=100, event at 200 not yet reached
        assertEquals(0, scene.eventTypes.size());

        runner.update(150); // t=250, event at 200 should fire
        assertEquals(1, scene.eventTypes.size());
        assertEquals("expression", scene.eventTypes.get(0));
        assertEquals("hero", scene.eventPayloads.get(0).get("target"));

        runner.update(100); // t=350, no new events
        assertEquals(1, scene.eventTypes.size());
    }

    @Test
    void runnerTriggersEventCueOnLoopBoundary() {
        TimelineData data = new TimelineData("evt_loop", 500);
        data.setLooping(true);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("id", "beat1");
        data.addEventCue(new TimelineData.EventCue(0, "dialogue_marker", payload));

        RecordingAccessor scene = new RecordingAccessor();
        TimelineRunner runner = new TimelineRunner(data, scene);

        runner.update(250); // first pass, event at 0 fires
        assertEquals(1, scene.eventTypes.size());

        runner.update(250); // reaches 500, loops back to 0, event fires again
        assertEquals(2, scene.eventTypes.size());
    }

    @Test
    void eventCuePayloadIsImmutable() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("key", "value");
        TimelineData.EventCue cue = new TimelineData.EventCue(0, "test", payload);

        // Modifying original should not affect cue
        payload.put("key", "changed");
        assertEquals("value", cue.getPayloadValue("key"));

        // Cue payload should be unmodifiable
        assertThrows(UnsupportedOperationException.class,
            () -> cue.getPayload().put("new", "val"));
    }

    @Test
    void parsesScriptCallEventBlock() {
        String dsl = """
            timeline {
              event "script_call" {
                provider: vn
                command: show hero center smile
              }
            }
            """;

        TimelineData data = TimelineDataParser.parse("script_evt", dsl);
        assertEquals(1, data.getEventCues().size());
        TimelineData.EventCue e = data.getEventCues().get(0);
        assertEquals("script_call", e.getType());
        assertEquals("vn", e.getPayloadValue("provider"));
        assertEquals("show hero center smile", e.getPayloadValue("command"));
    }
}

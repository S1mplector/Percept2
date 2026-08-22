package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EditorEventCueTest {

    @Test
    void dialogueMarkerTimelineLabelShowsSpeakerAndText() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("speaker", "Aria");
        payload.put("text", "Wait, look out!");
        EditorEventCue cue = new EditorEventCue(1200, "dialogue_marker", payload);

        assertEquals("Aria: Wait, look out!", cue.getTimelineLabel());
    }

    @Test
    void dialogueMarkerTimelineLabelFallsBackToSpeakerOnly() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("speaker", "Aria");
        EditorEventCue cue = new EditorEventCue(1200, "dialogue_marker", payload);

        assertEquals("Aria", cue.getTimelineLabel());
    }

    @Test
    void dialogueMarkerTimelineLabelFallsBackToGenericWhenBlank() {
        EditorEventCue cue = new EditorEventCue(1200, "dialogue_marker", new LinkedHashMap<>());

        assertEquals("CUE", cue.getTimelineLabel());
    }

    @Test
    void dialogueMarkerDisplayLabelIncludesTimeSpeakerAndText() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("speaker", "Aria");
        payload.put("text", "Wait, look out!");
        EditorEventCue cue = new EditorEventCue(1200, "dialogue_marker", payload);

        assertEquals("1200ms  dialogue  Aria: Wait, look out!", cue.getDisplayLabel());
    }
}

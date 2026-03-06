package com.jvn.editor.ui.actioneditor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PuppeteerCommandDragCoalesceTest {

    @Test
    void applyPositionAtTimeRestoresMissingKeyframesOnUndo() {
        EntityTrack track = new EntityTrack("hero");
        double time = 250.0;

        PuppeteerCommand cmd = PuppeteerCommand.applyPositionAtTime(
            track,
            time,
            false,
            0.0,
            false,
            0.0,
            140.0,
            280.0
        );

        cmd.execute();
        assertEquals(140.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertEquals(280.0, track.findKeyframeAt(PropertyType.Y, time).getValue(), 0.0001);

        cmd.undo();
        assertNull(track.findKeyframeAt(PropertyType.X, time));
        assertNull(track.findKeyframeAt(PropertyType.Y, time));
    }

    @Test
    void applyPositionAtTimeRestoresExistingValuesOnUndo() {
        EntityTrack track = new EntityTrack("hero");
        double time = 400.0;
        track.upsertKeyframe(PropertyType.X, new Keyframe(time, 10.0));
        track.upsertKeyframe(PropertyType.Y, new Keyframe(time, 20.0));

        PuppeteerCommand cmd = PuppeteerCommand.applyPositionAtTime(
            track,
            time,
            true,
            10.0,
            true,
            20.0,
            100.0,
            200.0
        );

        cmd.execute();
        assertEquals(100.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertEquals(200.0, track.findKeyframeAt(PropertyType.Y, time).getValue(), 0.0001);

        cmd.undo();
        assertEquals(10.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertEquals(20.0, track.findKeyframeAt(PropertyType.Y, time).getValue(), 0.0001);
    }
}

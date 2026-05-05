package com.jvn.editor.ui.actioneditor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void applyPropertiesAtTimeRestoresCompositeTransformStateOnUndo() {
        EntityTrack track = new EntityTrack("hero");
        double time = 500.0;
        track.upsertKeyframe(PropertyType.X, new Keyframe(time, 20.0));

        Map<PropertyType, PuppeteerCommand.PropertySnapshot> before = new EnumMap<>(PropertyType.class);
        before.put(PropertyType.X, new PuppeteerCommand.PropertySnapshot(true, 20.0));
        before.put(PropertyType.PIVOT_X, new PuppeteerCommand.PropertySnapshot(false, 0.5));

        Map<PropertyType, PuppeteerCommand.PropertySnapshot> after = new EnumMap<>(PropertyType.class);
        after.put(PropertyType.X, new PuppeteerCommand.PropertySnapshot(true, 90.0));
        after.put(PropertyType.PIVOT_X, new PuppeteerCommand.PropertySnapshot(true, 0.25));

        PuppeteerCommand cmd = PuppeteerCommand.applyPropertiesAtTime(track, time, before, after, "Edit transform");

        cmd.execute();
        assertEquals(90.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertEquals(0.25, track.findKeyframeAt(PropertyType.PIVOT_X, time).getValue(), 0.0001);

        cmd.undo();
        assertEquals(20.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertNull(track.findKeyframeAt(PropertyType.PIVOT_X, time));
    }

    @Test
    void compositeCommandUndoesChildrenInReverseOrder() {
        EntityTrack track = new EntityTrack("hero");
        double time = 300.0;
        PuppeteerCommand composite = PuppeteerCommand.composite("Composite", List.of(
            PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, 50.0),
            PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, 75.0)
        ));

        composite.execute();
        assertEquals(50.0, track.findKeyframeAt(PropertyType.X, time).getValue(), 0.0001);
        assertEquals(75.0, track.findKeyframeAt(PropertyType.Y, time).getValue(), 0.0001);

        composite.undo();
        assertNull(track.findKeyframeAt(PropertyType.X, time));
        assertNull(track.findKeyframeAt(PropertyType.Y, time));

        composite.execute();
        assertTrue(track.findKeyframeAt(PropertyType.X, time) != null);
        assertTrue(track.findKeyframeAt(PropertyType.Y, time) != null);
    }
}

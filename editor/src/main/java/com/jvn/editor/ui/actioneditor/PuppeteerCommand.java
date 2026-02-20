package com.jvn.editor.ui.actioneditor;

import java.util.ArrayDeque;
import java.util.Deque;

public class PuppeteerCommand {

    @FunctionalInterface
    public interface Action {
        void execute();
    }

    private final String description;
    private final Action doAction;
    private final Action undoAction;

    public PuppeteerCommand(String description, Action doAction, Action undoAction) {
        this.description = description != null ? description : "";
        this.doAction = doAction;
        this.undoAction = undoAction;
    }

    public String getDescription() { return description; }
    public void execute() { if (doAction != null) doAction.execute(); }
    public void undo() { if (undoAction != null) undoAction.execute(); }

    public static class Stack {
        private final Deque<PuppeteerCommand> undoStack = new ArrayDeque<>();
        private final Deque<PuppeteerCommand> redoStack = new ArrayDeque<>();
        private final int maxSize;

        public Stack() { this(100); }
        public Stack(int maxSize) { this.maxSize = maxSize; }

        public void execute(PuppeteerCommand cmd) {
            if (cmd == null) return;
            cmd.execute();
            undoStack.push(cmd);
            redoStack.clear();
            if (undoStack.size() > maxSize) {
                ((ArrayDeque<PuppeteerCommand>) undoStack).removeLast();
            }
        }

        public boolean canUndo() { return !undoStack.isEmpty(); }
        public boolean canRedo() { return !redoStack.isEmpty(); }

        public String undoDescription() {
            return canUndo() ? undoStack.peek().getDescription() : "";
        }

        public String redoDescription() {
            return canRedo() ? redoStack.peek().getDescription() : "";
        }

        public void undo() {
            if (!canUndo()) return;
            PuppeteerCommand cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }

        public void redo() {
            if (!canRedo()) return;
            PuppeteerCommand cmd = redoStack.pop();
            cmd.execute();
            undoStack.push(cmd);
        }

        public void clear() {
            undoStack.clear();
            redoStack.clear();
        }
    }

    public static PuppeteerCommand addKeyframe(EntityTrack track, PropertyType prop, Keyframe kf) {
        return new PuppeteerCommand("Add keyframe",
            () -> track.addKeyframe(prop, kf),
            () -> track.removeKeyframe(prop, kf)
        );
    }

    public static PuppeteerCommand removeKeyframe(EntityTrack track, PropertyType prop, Keyframe kf) {
        return new PuppeteerCommand("Remove keyframe",
            () -> track.removeKeyframe(prop, kf),
            () -> track.addKeyframe(prop, kf)
        );
    }

    public static PuppeteerCommand moveKeyframe(Keyframe kf, double oldTime, double newTime) {
        return new PuppeteerCommand("Move keyframe",
            () -> kf.setTimeMs(newTime),
            () -> kf.setTimeMs(oldTime)
        );
    }

    public static PuppeteerCommand changeValue(Keyframe kf, double oldValue, double newValue) {
        return new PuppeteerCommand("Change value",
            () -> kf.setValue(newValue),
            () -> kf.setValue(oldValue)
        );
    }

    public static PuppeteerCommand applyPreset(EntityTrack track, AnimationPreset preset, double startTime) {
        EntityTrack snapshot = track.copy();
        return new PuppeteerCommand("Apply preset: " + preset.getName(),
            () -> preset.applyTo(track, startTime),
            () -> {
                for (PropertyType p : PropertyType.values()) {
                    track.setKeyframes(p, snapshot.getKeyframes(p));
                }
            }
        );
    }
}

package com.jvn.editor.ui.actioneditor;

/**
 * Adapter over {@link PuppeteerCommand.Stack} that exposes the undo/redo stack
 * through the {@link PuppeteerUndoStack.Command} interface, which names its
 * operations {@code apply} and {@code revert} for clarity.
 *
 * <p>Existing call sites continue to use {@link PuppeteerCommand} and
 * {@link PuppeteerCommand.Stack} directly. New code may prefer this type.
 */
public final class PuppeteerUndoStack {

    /** Undoable command unit with symmetrical apply/revert operations. */
    public interface Command {
        String description();
        void apply();
        void revert();

        /** Wraps a {@link PuppeteerCommand} as a {@link Command}. */
        static Command of(PuppeteerCommand cmd) {
            return new Command() {
                @Override public String description() { return cmd.getDescription(); }
                @Override public void apply()  { cmd.execute(); }
                @Override public void revert() { cmd.undo(); }
            };
        }

        /** Converts this command to the legacy {@link PuppeteerCommand} type. */
        default PuppeteerCommand toLegacy() {
            Command self = this;
            return new PuppeteerCommand(description(), self::apply, self::revert);
        }
    }

    private final PuppeteerCommand.Stack delegate;

    public PuppeteerUndoStack() {
        this.delegate = new PuppeteerCommand.Stack();
    }

    public PuppeteerUndoStack(int maxSize) {
        this.delegate = new PuppeteerCommand.Stack(maxSize);
    }

    /** Wraps an existing stack so both share the same undo/redo deques. */
    public PuppeteerUndoStack(PuppeteerCommand.Stack existing) {
        this.delegate = existing;
    }

    // -----------------------------------------------------------------------
    // Command interface operations
    // -----------------------------------------------------------------------

    /** Applies {@code cmd} and pushes it onto the undo stack. */
    public void execute(Command cmd) {
        if (cmd == null) return;
        delegate.execute(cmd.toLegacy());
    }

    /** Pushes an already-executed command without re-applying it. */
    public void pushExecuted(Command cmd) {
        if (cmd == null) return;
        delegate.pushExecuted(cmd.toLegacy());
    }

    // -----------------------------------------------------------------------
    // Legacy PuppeteerCommand pass-through (unchanged call sites)
    // -----------------------------------------------------------------------

    public void execute(PuppeteerCommand cmd) { delegate.execute(cmd); }
    public void pushExecuted(PuppeteerCommand cmd) { delegate.pushExecuted(cmd); }

    // -----------------------------------------------------------------------
    // Query / navigation
    // -----------------------------------------------------------------------

    public boolean canUndo() { return delegate.canUndo(); }
    public boolean canRedo() { return delegate.canRedo(); }
    public String undoDescription() { return delegate.undoDescription(); }
    public String redoDescription() { return delegate.redoDescription(); }

    public void undo() { delegate.undo(); }
    public void redo() { delegate.redo(); }
    public void clear() { delegate.clear(); }

    /** Returns the underlying stack for call sites that accept {@link PuppeteerCommand.Stack}. */
    public PuppeteerCommand.Stack asLegacyStack() { return delegate; }
}

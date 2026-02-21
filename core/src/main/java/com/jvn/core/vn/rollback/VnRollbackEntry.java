package com.jvn.core.vn.rollback;

import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of VN state at a specific point for rollback.
 * Captures all gameplay-relevant state needed to restore the VN to this exact moment.
 */
public final class VnRollbackEntry {
    private final int nodeIndex;
    private final String backgroundId;
    private final Map<String, Object> variables;
    private final Map<CharacterPosition, CharacterSnapshot> visibleCharacters;
    private final Set<Integer> readNodes;
    private final String dialogueSpeaker;
    private final String dialogueText;
    private final long timestamp;

    private VnRollbackEntry(Builder builder) {
        this.nodeIndex = builder.nodeIndex;
        this.backgroundId = builder.backgroundId;
        this.variables = builder.variables != null ? new HashMap<>(builder.variables) : new HashMap<>();
        this.visibleCharacters = builder.visibleCharacters != null ? new HashMap<>(builder.visibleCharacters) : new HashMap<>();
        this.readNodes = builder.readNodes != null ? new HashSet<>(builder.readNodes) : new HashSet<>();
        this.dialogueSpeaker = builder.dialogueSpeaker;
        this.dialogueText = builder.dialogueText;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
    }

    public int getNodeIndex() { return nodeIndex; }
    public String getBackgroundId() { return backgroundId; }
    public Map<String, Object> getVariables() { return new HashMap<>(variables); }
    public Map<CharacterPosition, CharacterSnapshot> getVisibleCharacters() { return new HashMap<>(visibleCharacters); }
    public Set<Integer> getReadNodes() { return new HashSet<>(readNodes); }
    public String getDialogueSpeaker() { return dialogueSpeaker; }
    public String getDialogueText() { return dialogueText; }
    public long getTimestamp() { return timestamp; }

    /**
     * Capture current state into a rollback entry.
     */
    public static VnRollbackEntry capture(VnState state, String speaker, String text) {
        Builder builder = new Builder()
            .nodeIndex(state.getCurrentNodeIndex())
            .backgroundId(state.getCurrentBackgroundId())
            .variables(state.getVariables())
            .readNodes(state.getReadNodes())
            .dialogueSpeaker(speaker)
            .dialogueText(text)
            .timestamp(System.currentTimeMillis());

        // Capture visible characters
        Map<CharacterPosition, CharacterSnapshot> chars = new HashMap<>();
        for (var entry : state.getVisibleCharacters().entrySet()) {
            VnState.CharacterSlot slot = entry.getValue();
            if (slot != null) {
                chars.put(entry.getKey(), new CharacterSnapshot(slot.getCharacterId(), slot.getExpression()));
            }
        }
        builder.visibleCharacters(chars);

        return builder.build();
    }

    /**
     * Apply this rollback entry to restore VN state.
     */
    public void applyTo(VnState state) {
        state.setCurrentNodeIndex(nodeIndex);
        state.setCurrentBackgroundId(backgroundId);
        state.setVariables(variables);
        state.setReadNodes(readNodes);

        // Restore visible characters (immediate, no animation)
        state.clearAllCharacters();
        for (var entry : visibleCharacters.entrySet()) {
            CharacterSnapshot snap = entry.getValue();
            state.showCharacter(entry.getKey(), snap.characterId(), snap.expression());
        }

        // Reset UI state for clean replay
        state.setWaitingForInput(false);
        state.setTextRevealProgress(0);
        state.setSkipMode(false);
        state.setAutoPlayMode(false);
        state.resetAutoPlayTimer();
        state.setUiHidden(false);
        state.setHistoryOverlayShown(false);
        state.clearHistoryScroll();
        state.hideSaveSlotOverlay();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int nodeIndex;
        private String backgroundId;
        private Map<String, Object> variables;
        private Map<CharacterPosition, CharacterSnapshot> visibleCharacters;
        private Set<Integer> readNodes;
        private String dialogueSpeaker;
        private String dialogueText;
        private long timestamp;

        public Builder nodeIndex(int nodeIndex) { this.nodeIndex = nodeIndex; return this; }
        public Builder backgroundId(String backgroundId) { this.backgroundId = backgroundId; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        public Builder visibleCharacters(Map<CharacterPosition, CharacterSnapshot> visibleCharacters) { this.visibleCharacters = visibleCharacters; return this; }
        public Builder readNodes(Set<Integer> readNodes) { this.readNodes = readNodes; return this; }
        public Builder dialogueSpeaker(String dialogueSpeaker) { this.dialogueSpeaker = dialogueSpeaker; return this; }
        public Builder dialogueText(String dialogueText) { this.dialogueText = dialogueText; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public VnRollbackEntry build() { return new VnRollbackEntry(this); }
    }

    /**
     * Snapshot of a character's state at a position.
     */
    public record CharacterSnapshot(String characterId, String expression) {}
}

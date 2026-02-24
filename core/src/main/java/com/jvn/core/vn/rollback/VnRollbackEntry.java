package com.jvn.core.vn.rollback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnState;

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
    private final List<Integer> callStack;
    private final Set<String> globalPositionCharacters;
    private final Map<String, CharacterPosition> characterDefinedPositions;
    private final boolean skipMode;
    private final boolean autoPlayMode;
    private final long autoPlayTimer;
    private final boolean uiHidden;
    private final String dialogueSpeaker;
    private final String dialogueText;
    private final long timestamp;

    private VnRollbackEntry(Builder builder) {
        this.nodeIndex = builder.nodeIndex;
        this.backgroundId = builder.backgroundId;
        this.variables = builder.variables != null ? new HashMap<>(builder.variables) : new HashMap<>();
        this.visibleCharacters = builder.visibleCharacters != null ? new HashMap<>(builder.visibleCharacters) : new HashMap<>();
        this.readNodes = builder.readNodes != null ? new HashSet<>(builder.readNodes) : new HashSet<>();
        this.callStack = builder.callStack != null ? new ArrayList<>(builder.callStack) : new ArrayList<>();
        this.globalPositionCharacters = builder.globalPositionCharacters != null ? new HashSet<>(builder.globalPositionCharacters) : new HashSet<>();
        this.characterDefinedPositions = builder.characterDefinedPositions != null ? new HashMap<>(builder.characterDefinedPositions) : new HashMap<>();
        this.skipMode = builder.skipMode;
        this.autoPlayMode = builder.autoPlayMode;
        this.autoPlayTimer = builder.autoPlayTimer;
        this.uiHidden = builder.uiHidden;
        this.dialogueSpeaker = builder.dialogueSpeaker;
        this.dialogueText = builder.dialogueText;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
    }

    public int getNodeIndex() { return nodeIndex; }
    public String getBackgroundId() { return backgroundId; }
    public Map<String, Object> getVariables() { return new HashMap<>(variables); }
    public Map<CharacterPosition, CharacterSnapshot> getVisibleCharacters() { return new HashMap<>(visibleCharacters); }
    public Set<Integer> getReadNodes() { return new HashSet<>(readNodes); }
    public List<Integer> getCallStack() { return new ArrayList<>(callStack); }
    public Set<String> getGlobalPositionCharacters() { return new HashSet<>(globalPositionCharacters); }
    public Map<String, CharacterPosition> getCharacterDefinedPositions() { return new HashMap<>(characterDefinedPositions); }
    public boolean isSkipMode() { return skipMode; }
    public boolean isAutoPlayMode() { return autoPlayMode; }
    public long getAutoPlayTimer() { return autoPlayTimer; }
    public boolean isUiHidden() { return uiHidden; }
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
            .callStack(state.getCallStackSnapshot())
            .globalPositionCharacters(state.getGlobalPositionCharactersSnapshot())
            .characterDefinedPositions(state.getCharacterDefinedPositionsSnapshot())
            .skipMode(state.isSkipMode())
            .autoPlayMode(state.isAutoPlayMode())
            .autoPlayTimer(state.getAutoPlayTimer())
            .uiHidden(state.isUiHidden())
            .dialogueSpeaker(speaker)
            .dialogueText(text)
            .timestamp(System.currentTimeMillis());

        // Capture visible characters
        Map<CharacterPosition, CharacterSnapshot> chars = new HashMap<>();
        for (var entry : state.getVisibleCharacters().entrySet()) {
            VnState.CharacterSlot slot = entry.getValue();
            if (slot != null) {
                chars.put(entry.getKey(), new CharacterSnapshot(slot.getCharacterId(), slot.getExpression(), slot.getLayerOrder()));
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
        state.setCallStack(callStack);

        // Restore visible characters (immediate, no animation)
        state.clearAllCharacters();
        for (var entry : visibleCharacters.entrySet()) {
            CharacterSnapshot snap = entry.getValue();
            state.showCharacter(entry.getKey(), snap.characterId(), snap.expression(), snap.layerOrder());
        }
        state.setGlobalPositionState(globalPositionCharacters, characterDefinedPositions);

        // Reset UI state for clean replay
        state.setWaitingForInput(false);
        state.setTextRevealProgress(0);
        state.setSkipMode(skipMode);
        state.setAutoPlayMode(autoPlayMode);
        state.setAutoPlayTimer(autoPlayTimer);
        state.setUiHidden(uiHidden);
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
        private List<Integer> callStack;
        private Set<String> globalPositionCharacters;
        private Map<String, CharacterPosition> characterDefinedPositions;
        private boolean skipMode;
        private boolean autoPlayMode;
        private long autoPlayTimer;
        private boolean uiHidden;
        private String dialogueSpeaker;
        private String dialogueText;
        private long timestamp;

        public Builder nodeIndex(int nodeIndex) { this.nodeIndex = nodeIndex; return this; }
        public Builder backgroundId(String backgroundId) { this.backgroundId = backgroundId; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        public Builder visibleCharacters(Map<CharacterPosition, CharacterSnapshot> visibleCharacters) { this.visibleCharacters = visibleCharacters; return this; }
        public Builder readNodes(Set<Integer> readNodes) { this.readNodes = readNodes; return this; }
        public Builder callStack(List<Integer> callStack) { this.callStack = callStack; return this; }
        public Builder globalPositionCharacters(Set<String> globalPositionCharacters) {
            this.globalPositionCharacters = globalPositionCharacters;
            return this;
        }
        public Builder characterDefinedPositions(Map<String, CharacterPosition> characterDefinedPositions) {
            this.characterDefinedPositions = characterDefinedPositions;
            return this;
        }
        public Builder skipMode(boolean skipMode) { this.skipMode = skipMode; return this; }
        public Builder autoPlayMode(boolean autoPlayMode) { this.autoPlayMode = autoPlayMode; return this; }
        public Builder autoPlayTimer(long autoPlayTimer) { this.autoPlayTimer = autoPlayTimer; return this; }
        public Builder uiHidden(boolean uiHidden) { this.uiHidden = uiHidden; return this; }
        public Builder dialogueSpeaker(String dialogueSpeaker) { this.dialogueSpeaker = dialogueSpeaker; return this; }
        public Builder dialogueText(String dialogueText) { this.dialogueText = dialogueText; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public VnRollbackEntry build() { return new VnRollbackEntry(this); }
    }

    /**
     * Snapshot of a character's state at a position.
     */
    public record CharacterSnapshot(String characterId, String expression, int layerOrder) {}
}

package com.jvn.core.vn.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized service for save-slot filesystem operations.
 * Eliminates duplicated save-slot logic across VnRenderer, FxLauncher, and other components.
 */
public final class VnSaveSlotService {
    public static final int TOTAL_SLOTS = 10;
    public static final int QUICKSAVE_SLOT = 0;
    
    private static final String DEFAULT_SAVE_DIR = System.getProperty("user.home") + "/.jvn/saves";
    private static final String JSON_EXT = ".json";
    private static final String LEGACY_EXT = ".sav";
    private static final String THUMB_EXT = ".png";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path saveDirectory;

    public VnSaveSlotService() {
        this(DEFAULT_SAVE_DIR);
    }

    public VnSaveSlotService(String saveDir) {
        this.saveDirectory = Paths.get(saveDir);
        ensureDirectoryExists();
    }

    /**
     * Get the slot name for a given slot index.
     * @param slot 0 = quicksave, 1-9 = regular slots
     */
    public String getSlotName(int slot) {
        if (slot == QUICKSAVE_SLOT) return "_quicksave";
        return "slot_" + slot;
    }

    /**
     * Check if a slot has save data.
     */
    public boolean hasData(int slot) {
        String name = getSlotName(slot);
        Path jsonFile = saveDirectory.resolve(name + JSON_EXT);
        Path legacyFile = saveDirectory.resolve(name + LEGACY_EXT);
        return Files.exists(jsonFile) || Files.exists(legacyFile);
    }

    /**
     * Get the save file path for a slot (prefers JSON, falls back to legacy).
     */
    public Path getSaveFilePath(int slot) {
        String name = getSlotName(slot);
        Path jsonFile = saveDirectory.resolve(name + JSON_EXT);
        if (Files.exists(jsonFile)) return jsonFile;
        Path legacyFile = saveDirectory.resolve(name + LEGACY_EXT);
        if (Files.exists(legacyFile)) return legacyFile;
        return jsonFile; // Default to JSON for new saves
    }

    /**
     * Get the thumbnail path for a slot.
     */
    public Path getThumbnailPath(int slot) {
        String name = getSlotName(slot);
        return saveDirectory.resolve(name + THUMB_EXT);
    }

    /**
     * Check if a slot has a thumbnail.
     */
    public boolean hasThumbnail(int slot) {
        return Files.exists(getThumbnailPath(slot));
    }

    /**
     * Get formatted timestamp for a slot's save file.
     */
    public String getFormattedTimestamp(int slot) {
        if (!hasData(slot)) return "Empty";
        try {
            Path path = getSaveFilePath(slot);
            long millis = Files.getLastModifiedTime(path).toMillis();
            ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
            return zdt.format(TIMESTAMP_FORMAT);
        } catch (Exception e) {
            return "Saved";
        }
    }

    /**
     * Get raw timestamp (milliseconds since epoch) for a slot.
     */
    public long getTimestamp(int slot) {
        if (!hasData(slot)) return 0;
        try {
            Path path = getSaveFilePath(slot);
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get information about all slots.
     */
    public List<SlotInfo> getAllSlots() {
        List<SlotInfo> slots = new ArrayList<>();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots.add(getSlotInfo(i));
        }
        return slots;
    }

    /**
     * Get information about a specific slot.
     */
    public SlotInfo getSlotInfo(int slot) {
        boolean hasData = hasData(slot);
        boolean hasThumbnail = hasThumbnail(slot);
        String timestamp = hasData ? getFormattedTimestamp(slot) : null;
        String displayName = slot == QUICKSAVE_SLOT ? "Quick Save" : "Slot " + slot;
        return new SlotInfo(slot, displayName, hasData, hasThumbnail, timestamp);
    }

    /**
     * Delete save data and thumbnail for a slot.
     * @return true if anything was deleted
     */
    public boolean deleteSlot(int slot) {
        String name = getSlotName(slot);
        boolean deleted = false;
        try {
            deleted |= Files.deleteIfExists(saveDirectory.resolve(name + JSON_EXT));
            deleted |= Files.deleteIfExists(saveDirectory.resolve(name + LEGACY_EXT));
            deleted |= Files.deleteIfExists(saveDirectory.resolve(name + THUMB_EXT));
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        return deleted;
    }

    /**
     * Get the save directory path.
     */
    public Path getSaveDirectory() {
        return saveDirectory;
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(saveDirectory);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }

    /**
     * Information about a save slot.
     */
    public record SlotInfo(
        int slot,
        String displayName,
        boolean hasData,
        boolean hasThumbnail,
        String timestamp
    ) {
        public boolean isEmpty() {
            return !hasData;
        }
    }
}

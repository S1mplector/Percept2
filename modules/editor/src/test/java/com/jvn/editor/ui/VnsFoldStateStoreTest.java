package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnsFoldStateStoreTest {

    @Test
    void foldedBlocksRoundTripThroughStore(@TempDir Path tempDir) {
        VnsFoldStateStore store = VnsFoldStateStore.load(tempDir.toFile());
        List<VnsFoldStateStore.FoldedBlockKey> keys = List.of(
            new VnsFoldStateStore.FoldedBlockKey(0, "abc123"),
            new VnsFoldStateStore.FoldedBlockKey(2, "def456"));
        store.setFoldedBlocks("scripts/intro.vns", keys);
        store.save();

        VnsFoldStateStore loaded = VnsFoldStateStore.load(tempDir.toFile());

        assertEquals(keys, loaded.getFoldedBlocks("scripts/intro.vns"));
    }

    @Test
    void unknownScriptHasNoFoldedBlocks(@TempDir Path tempDir) {
        VnsFoldStateStore store = VnsFoldStateStore.load(tempDir.toFile());

        assertTrue(store.getFoldedBlocks("scripts/never-seen.vns").isEmpty());
    }
}

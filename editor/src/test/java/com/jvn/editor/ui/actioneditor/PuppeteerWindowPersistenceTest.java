package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PuppeteerWindowPersistenceTest {

    @Test
    void backupExistingTimelineFileKeepsPreviousSavedContent(@TempDir Path tempDir) throws Exception {
        Path timeline = tempDir.resolve("scripts").resolve("timelines").resolve("intro.jes");
        Files.createDirectories(timeline.getParent());
        Files.writeString(timeline, "timeline {\n  wait 100\n}\n");

        Path backup = PuppeteerWindow.backupExistingTimelineFile(timeline);

        assertNotNull(backup);
        assertEquals(timeline.getParent().resolve(".backups").resolve("intro.jes.bak"), backup);
        assertEquals("timeline {\n  wait 100\n}\n", Files.readString(backup));
    }

    @Test
    void backupExistingTimelineFileSkipsMissingFiles(@TempDir Path tempDir) throws Exception {
        assertNull(PuppeteerWindow.backupExistingTimelineFile(tempDir.resolve("missing.jes")));
    }
}

package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure I/O coordinator for Puppeteer project persistence.
 *
 * <p>All methods operate on the file system only — no JavaFX or UI references.
 * Callers are responsible for showing errors and driving UI state changes.
 *
 * <p>Covers:
 * <ul>
 *   <li>Timeline {@code .jes} file save with automatic {@code .bak} backup</li>
 *   <li>Workspace prefs load/save</li>
 *   <li>Anchor store load/save</li>
 *   <li>Draft store lifecycle (create, flush)</li>
 *   <li>On-close background persistence</li>
 * </ul>
 */
public final class PuppeteerPersistenceCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PuppeteerPersistenceCoordinator.class);

    private final File projectRoot;

    public PuppeteerPersistenceCoordinator(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    // -----------------------------------------------------------------------
    // Timeline file I/O
    // -----------------------------------------------------------------------

    /**
     * Saves {@code jesCode} to {@code <projectRoot>/scripts/timelines/<name>.jes}.
     * Creates the directory if absent. Backs up any pre-existing file to
     * {@code .backups/<name>.jes.bak} before overwriting.
     *
     * @throws IllegalArgumentException if {@code name} fails validation
     * @throws IOException              on file-system failure
     */
    public void saveTimelineFile(String name, String jesCode) throws IOException {
        if (projectRoot == null) {
            throw new IOException("No project root is set.");
        }
        if (!PuppeteerVerification.isValidTimelineName(name)) {
            throw new IllegalArgumentException("Timeline name '" + name + "' is not safe to use as a .jes filename.");
        }
        Path dir = projectRoot.toPath().resolve("scripts").resolve("timelines");
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".jes");
        backupExistingTimelineFile(file);
        Files.writeString(file, jesCode);
    }

    /**
     * Copies {@code file} to {@code .backups/<filename>.bak} if it exists.
     *
     * @return the backup path, or {@code null} if there was nothing to back up
     */
    public static Path backupExistingTimelineFile(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return null;
        Path backupDir = file.getParent().resolve(".backups");
        Files.createDirectories(backupDir);
        String fileName = file.getFileName().toString();
        Path backup = backupDir.resolve(fileName + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    // -----------------------------------------------------------------------
    // Workspace prefs
    // -----------------------------------------------------------------------

    /**
     * Loads workspace prefs for {@link #projectRoot}.
     * Returns {@code null} if {@code projectRoot} is {@code null}.
     */
    public PuppeteerWorkspacePrefs loadWorkspacePrefs() {
        if (projectRoot == null) return null;
        return PuppeteerWorkspacePrefs.load(projectRoot);
    }

    /** Saves {@code prefs} to disk, swallowing I/O failures. */
    public void saveWorkspacePrefs(PuppeteerWorkspacePrefs prefs) {
        if (prefs == null) return;
        try {
            prefs.save();
        } catch (Exception ex) {
            log.warn("Failed to save workspace prefs", ex);
        }
    }

    // -----------------------------------------------------------------------
    // Anchor store
    // -----------------------------------------------------------------------

    /** Loads orbit anchors from disk into {@code project}. */
    public void loadAnchors(AnimationProject project) {
        if (projectRoot == null || project == null) return;
        PuppeteerAnchorStore.load(projectRoot, project);
    }

    /** Saves orbit anchors from {@code project} to disk, swallowing I/O failures. */
    public void saveAnchors(AnimationProject project) {
        if (projectRoot == null || project == null) return;
        try {
            PuppeteerAnchorStore.save(projectRoot, project);
        } catch (Exception ex) {
            log.warn("Failed to save anchor store", ex);
        }
    }

    // -----------------------------------------------------------------------
    // Draft store
    // -----------------------------------------------------------------------

    /**
     * Creates a new {@link PuppeteerDraftStore} rooted at {@link #projectRoot}.
     * Returns {@code null} if {@code projectRoot} is {@code null}.
     */
    public PuppeteerDraftStore createDraftStore() {
        if (projectRoot == null) return null;
        return new PuppeteerDraftStore(projectRoot);
    }

    // -----------------------------------------------------------------------
    // On-close background flush
    // -----------------------------------------------------------------------

    /**
     * Spawns a daemon thread that persists prefs, flushes drafts, and saves
     * anchors. Safe to call from the FX thread — all I/O is off-thread.
     */
    public void flushOnClose(PuppeteerWorkspacePrefs prefs,
                             PuppeteerDraftStore drafts,
                             AnimationProject project) {
        if (prefs == null && drafts == null && (projectRoot == null || project == null)) return;
        Thread cleanup = new Thread(() -> {
            try { if (prefs != null) prefs.save(); } catch (Throwable ex) {
                log.warn("prefs save failed on close", ex);
            }
            try { if (drafts != null) drafts.shutdown(); } catch (Throwable ex) {
                log.warn("draft flush failed on close", ex);
            }
            try {
                if (projectRoot != null && project != null) PuppeteerAnchorStore.save(projectRoot, project);
            } catch (Throwable ex) {
                log.warn("anchor save failed on close", ex);
            }
        }, "puppeteer-close-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}

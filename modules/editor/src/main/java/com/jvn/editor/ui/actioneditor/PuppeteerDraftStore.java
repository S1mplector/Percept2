package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Debounced autosave for Puppeteer timeline drafts.
 *
 * <p>Drafts live in {@code <projectRoot>/config/puppeteer/.draft/<sanitized_name>.jes}.
 * Calls to {@link #scheduleSave(String, String)} coalesce: only the most recent payload
 * is written, after {@link #debounceMillis} of quiescence. {@link #flushNow()} writes
 * any pending payload synchronously and is safe to call from the JavaFX thread (it does
 * its own cancel + write).
 *
 * <p>{@link #findRestorableDraft(String, java.io.File)} returns a draft only when it is
 * strictly newer than the registered timeline file (or the registered file is missing
 * entirely). Empty drafts are ignored.
 *
 * <p>The store owns a single daemon scheduler thread, so it never blocks JVM exit.
 */
public final class PuppeteerDraftStore {
    private static final long DEFAULT_DEBOUNCE_MS = 30_000L;
    private static final String DRAFT_HEADER_PREFIX = "# puppeteer-draft:";

    private final File projectRoot;
    private final long debounceMillis;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<PendingSave> pending = new AtomicReference<>(null);
    private ScheduledFuture<?> scheduledTask;
    private Consumer<String> onSaveCallback;

    public PuppeteerDraftStore(File projectRoot) {
        this(projectRoot, DEFAULT_DEBOUNCE_MS);
    }

    public PuppeteerDraftStore(File projectRoot, long debounceMillis) {
        this.projectRoot = projectRoot;
        this.debounceMillis = Math.max(250L, debounceMillis);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "puppeteer-draft-store");
            t.setDaemon(true);
            return t;
        });
    }

    /** Coalesce-save the given timeline name + JES code after the debounce window. */
    public synchronized void scheduleSave(String timelineName, String jesCode) {
        if (projectRoot == null) return;
        if (timelineName == null || timelineName.isBlank()) return;
        if (jesCode == null) jesCode = "";
        pending.set(new PendingSave(timelineName.trim(), jesCode));
        if (scheduledTask != null) scheduledTask.cancel(false);
        scheduledTask = scheduler.schedule(this::writePendingSafely,
            debounceMillis, TimeUnit.MILLISECONDS);
    }

    /** Force any pending save to disk immediately. Safe to call from any thread. */
    public synchronized void flushNow() {
        if (scheduledTask != null) scheduledTask.cancel(false);
        writePendingSafely();
    }

    /** Set a callback to be invoked after a successful draft save. */
    public synchronized void setOnSaveCallback(Consumer<String> callback) {
        this.onSaveCallback = callback;
    }

    /** Delete the draft file for the given timeline (called after a successful Save & Register). */
    public void deleteDraft(String timelineName) {
        if (projectRoot == null || timelineName == null || timelineName.isBlank()) return;
        try {
            Path file = draftPath(timelineName);
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
    }

    /** Stop the background scheduler. The store should not be reused afterwards. */
    public void shutdown() {
        flushNow();
        scheduler.shutdownNow();
    }

    /**
     * Find a recoverable draft for the given timeline name.
     *
     * @param timelineName  current timeline name in the editor
     * @param registeredJes the file at {@code scripts/timelines/<name>.jes}; may not exist
     * @return a draft record only when the draft is strictly newer than the registered file
     */
    public Optional<DraftRecord> findRestorableDraft(String timelineName, File registeredJes) {
        if (projectRoot == null || timelineName == null || timelineName.isBlank()) {
            return Optional.empty();
        }
        Path draft = draftPath(timelineName);
        if (!Files.isRegularFile(draft)) return Optional.empty();
        try {
            long draftMs = Files.getLastModifiedTime(draft).toMillis();
            long registeredMs = (registeredJes != null && registeredJes.isFile())
                ? registeredJes.lastModified()
                : 0L;
            if (draftMs <= registeredMs) return Optional.empty();
            String content = Files.readString(draft, StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) return Optional.empty();
            return Optional.of(new DraftRecord(timelineName.trim(), draft.toFile(), content, draftMs));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private void writePendingSafely() {
        PendingSave snapshot = pending.getAndSet(null);
        if (snapshot == null) return;
        if (snapshot.code == null || snapshot.code.isBlank()) return;
        try {
            Path file = draftPath(snapshot.timelineName);
            Files.createDirectories(file.getParent());
            String header = DRAFT_HEADER_PREFIX + " " + snapshot.timelineName
                + " @ " + System.currentTimeMillis() + "\n";
            Files.writeString(file, header + snapshot.code, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            Consumer<String> callback = onSaveCallback;
            if (callback != null) {
                callback.accept(snapshot.timelineName);
            }
        } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
            // Drafts are best-effort. Re-pushing pending would risk an infinite retry loop.
        }
    }

    private Path draftPath(String timelineName) {
        return projectRoot.toPath()
            .resolve("config").resolve("puppeteer").resolve(".draft")
            .resolve(sanitize(timelineName) + ".jes");
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "untitled";
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.length() == 0 ? "untitled" : out.toString();
    }

    public record DraftRecord(String timelineName, File file, String code, long lastModifiedMs) {}

    private record PendingSave(String timelineName, String code) {}
}

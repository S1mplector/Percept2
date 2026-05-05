package com.musicplayer.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class that wraps basic operations around the external "yt-dlp" binary.
 * <p>
 * Responsibilities:
 * 1. Detect if the binary is present on the host system
 * 2. Download a single YouTube video as an audio-only file (default: MP3)
 * 3. Expose progress via simple listener callbacks (percentage updates only)
 *
 * This class purposefully keeps a very small surface-area. Anything related to
 * UI/album selection/library-integration is handled elsewhere so we keep the
 * download logic independent and easily testable.
 */
public class YouTubeDownloadService {

    /** Convenience overload that uses default format (mp3) and quality */
    public void downloadAudio(String url, File outputDirectory, DownloadListener listener) {
        downloadAudio(url, outputDirectory, "mp3", null, false, listener);
    }

    /** Callback interface for long-running download operations. */
    public interface DownloadListener {
        /**
         * @param percentage progress in the range 0-100. May be -1 if unknown.
         * @param message    arbitrary human-readable progress message
         */
        void onProgress(int percentage, String message);

        /** Called when the download finishes successfully. */
        void onSuccess(File downloadedFile);

        /** Called if the download fails for any reason. */
        void onError(String errorMessage, Exception exception);
    }

    /** Quick check by executing `yt-dlp --version`. */
    public boolean isYtDlpInstalled() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exit = process.waitFor();
            return exit == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Downloads the best audio stream as MP3 to the given directory.
     * <p>
     * The command executed resembles:
     * yt-dlp -f bestaudio --extract-audio --audio-format mp3 -o "%(title)s.%(ext)s" <url>
     *
     * @param url              the YouTube (or supported site) url
     * @param outputDirectory  directory to place the resulting file
     * @param listener         optional progress callback, may be null
     */
    public void downloadAudio(String url, File outputDirectory, String audioFormat, String audioQuality, boolean downloadPlaylist, DownloadListener listener) {
        if (url == null || url.isBlank()) {
            if (listener != null) listener.onError("URL is empty", null);
            return;
        }
        if (outputDirectory == null) {
            if (listener != null) listener.onError("Output directory is null", null);
            return;
        }

        /* Build argument list */
        List<String> cmd = new ArrayList<>();
        cmd.add("yt-dlp");
        cmd.add("-f");
        cmd.add("bestaudio");
        cmd.add("--extract-audio");
        cmd.add("--audio-format");
        cmd.add(audioFormat != null && !audioFormat.isBlank() ? audioFormat : "mp3");
        if (audioQuality != null && !audioQuality.isBlank()) {
            cmd.add("--audio-quality");
            cmd.add(audioQuality);
        }
        if (downloadPlaylist) {
            cmd.add("-o");
            cmd.add("%(playlist_title)s/%(playlist_index|05d)s - %(title)s.%(ext)s");
        } else {
            cmd.add("--no-playlist");
            cmd.add("-o");
            cmd.add("%(title)s.%(ext)s");
        }
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(outputDirectory);
        pb.redirectErrorStream(true);

        Thread worker = new Thread(() -> {
            try {
                Process proc = pb.start();
                /* Parse stdout line-by-line looking for progress information of the form:
                   [download]   5.3% of ... at  ... ETA ...
                */
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (listener != null) listener.onProgress(parsePercentage(line), line);
                    }
                }
                int exit = proc.waitFor();
                if (exit == 0) {
                    // Attempt to locate the downloaded file – simplest way is to list most recently modified mp3 in output dir
                    String ext = (audioFormat != null && !audioFormat.isBlank()) ? audioFormat : "mp3";
                    File downloaded = getMostRecentFile(outputDirectory, ext);
                    if (listener != null) listener.onSuccess(downloaded);
                } else {
                    if (listener != null) listener.onError("yt-dlp exited with code " + exit, null);
                }
            } catch (Exception ex) {
                if (listener != null) listener.onError("Error during download", ex);
            }
        }, "yt-dlp-download-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private int parsePercentage(String line) {
        try {
            int idxStart = line.indexOf('%');
            if (idxStart > 0) {
                // backtrack to first space then parse substring between space and %
                int space = line.lastIndexOf(' ', idxStart);
                if (space >= 0) {
                    String percentStr = line.substring(space, idxStart).trim();
                    if (percentStr.endsWith("%")) percentStr = percentStr.substring(0, percentStr.length() - 1);
                    return (int) Float.parseFloat(percentStr);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /* ---------------------- Info fetching ---------------------- */

    public static class InfoResult {
        private final boolean playlist;
        private final String title;
        private final List<String> entries; // video titles when playlist
        private final String thumbnailUrl;
        public InfoResult(boolean playlist, String title, List<String> entries, String thumbnailUrl) {
            this.playlist = playlist;
            this.title = title;
            this.entries = entries;
            this.thumbnailUrl = thumbnailUrl;
        }
        public boolean isPlaylist() { return playlist; }
        public String getTitle() { return title; }
        public List<String> getEntries() { return entries; }
        public String getThumbnailUrl() { return thumbnailUrl; }
    }

    public interface InfoListener {
        void onSuccess(InfoResult info);
        void onError(String errorMessage, Exception exception);
    }

    /**
     * Fetches metadata (title, entries) for a YouTube/playlist URL without downloading.
     * Requires yt-dlp.
     */
    private String extractVideoId(String url) {
        try {
            if (url == null) return null;
            if (url.contains("youtu.be/")) {
                return url.substring(url.lastIndexOf('/') + 1).split("[?&]")[0];
            }
            if (url.contains("v=")) {
                String[] parts = url.split("[?&]");
                for (String p : parts) {
                    if (p.startsWith("v=")) {
                        return p.substring(2);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void fetchInfo(String url, InfoListener listener) {
        if (url == null || url.isBlank()) {
            if (listener != null) listener.onError("URL is empty", null);
            return;
        }
        if (!isYtDlpInstalled()) {
            if (listener != null) listener.onError("yt-dlp not installed", null);
            return;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("yt-dlp");
        cmd.add("--dump-single-json");
        cmd.add("--no-warnings");
        cmd.add("--flat-playlist");
        cmd.add(url);

        Thread worker = new Thread(() -> {
            try {
                Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                }
                int exit = proc.waitFor();
                if (exit != 0) {
                    if (listener != null) listener.onError("yt-dlp exited with code " + exit, null);
                    return;
                }
                String json = sb.toString();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                boolean isPlaylist = root.has("entries");
                String title = root.hasNonNull("title") ? root.get("title").asText() : "(unknown)";
                List<String> entries = new ArrayList<>();
                String thumbnail = null;
                if (isPlaylist) {
                    com.fasterxml.jackson.databind.JsonNode first = root.get("entries").size() > 0 ? root.get("entries").get(0) : null;
                    String firstId = first != null && first.hasNonNull("id") ? first.get("id").asText() : null;
                    if (firstId != null) {
                        thumbnail = "https://img.youtube.com/vi/" + firstId + "/hqdefault.jpg";
                    }
                    for (com.fasterxml.jackson.databind.JsonNode entry : root.get("entries")) {
                        if (entry.hasNonNull("title")) {
                            entries.add(entry.get("title").asText());
                        }
                    }
                } else {
                    String vidId = root.hasNonNull("id") ? root.get("id").asText() : extractVideoId(url);
                    if (vidId != null) {
                        thumbnail = "https://img.youtube.com/vi/" + vidId + "/hqdefault.jpg";
                    }
                }
                InfoResult res = new InfoResult(isPlaylist, title, entries, thumbnail);
                if (listener != null) listener.onSuccess(res);
            } catch (Exception ex) {
                if (listener != null) listener.onError("Failed to fetch info", ex);
            }
        }, "yt-dlp-info-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /* ---------------------- Internals ---------------------- */
    private File getMostRecentFile(File dir, String extension) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith("." + extension));
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File f : files) {
            if (f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }
        return newest;
    }
}

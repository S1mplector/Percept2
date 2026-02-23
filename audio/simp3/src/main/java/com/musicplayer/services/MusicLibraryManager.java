package com.musicplayer.services;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.SimpleFileVisitor;

import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.PersistentSongRepository;
import com.musicplayer.data.repositories.SongRepository;
import com.musicplayer.utils.MusicScanner;

/**
 * Service class for managing the music library, including folder scanning and song management.
 * This class encapsulates all music library operations and provides a clean interface
 * for the UI controllers.
 */
public class MusicLibraryManager {
    
    private final SongRepository songRepository;
    private File currentMusicFolder;
    
    // Callback for notifying when library changes occur
    private Consumer<List<Song>> libraryUpdateCallback;
    private WatchService watchService;
    private Thread watcherThread;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private volatile boolean watching;
    private SettingsService settingsService;
    
    public MusicLibraryManager(SongRepository songRepository) {
        this.songRepository = songRepository;
        
        // If using persistent storage, log existing data
        if (songRepository instanceof PersistentSongRepository) {
            PersistentSongRepository persistentRepo = (PersistentSongRepository) songRepository;
            if (persistentRepo.size() > 0) {
                System.out.println("Found existing library with " + persistentRepo.size() + " songs");
                // Try to restore last music folder from first song's path
                List<Song> songs = persistentRepo.findAll();
                if (!songs.isEmpty() && songs.get(0).getFilePath() != null) {
                    File songFile = new File(songs.get(0).getFilePath());
                    if (songFile.exists()) {
                        this.currentMusicFolder = songFile.getParentFile();
                        while (currentMusicFolder != null && !isMusicFolder(currentMusicFolder)) {
                            currentMusicFolder = currentMusicFolder.getParentFile();
                        }
                        if (this.currentMusicFolder != null) {
                            restartWatching(this.currentMusicFolder);
                        }
                    }
                }
            }
        }
    }
    
    private boolean isMusicFolder(File folder) {
        // Simple heuristic: folder contains music files
        if (folder == null || !folder.isDirectory()) return false;
        File[] files = folder.listFiles();
        if (files == null) return false;
        
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".mp3") || name.endsWith(".m4a") || 
                name.endsWith(".wav") || name.endsWith(".flac")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initializes the library by loading existing data and notifying the callback.
     * Should be called after the UI is ready to receive updates.
     */
    public void initializeLibrary() {
        List<Song> existingSongs = getAllSongs();
        if (!existingSongs.isEmpty() && libraryUpdateCallback != null) {
            System.out.println("Initializing UI with " + existingSongs.size() + " existing songs");
            libraryUpdateCallback.accept(existingSongs);
        }
    }
    
    /**
     * Sets a callback to be notified when the library is updated.
     * 
     * @param callback Consumer that receives the updated list of songs
     */
    public void setLibraryUpdateCallback(Consumer<List<Song>> callback) {
        this.libraryUpdateCallback = callback;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
        initFromSettings();
    }

    private void initFromSettings() {
        if (settingsService == null || settingsService.getSettings() == null) return;
        String path = settingsService.getSettings().getMusicRootPath();
        if (path != null && !path.isBlank()) {
            File folder = new File(path);
            if (folder.exists() && folder.isDirectory()) {
                this.currentMusicFolder = folder;
                if (settingsService.getSettings().isLibraryWatcherEnabled()) {
                    restartWatching(folder);
                } else {
                    stopWatching();
                }
            }
        } else {
            if (!settingsService.getSettings().isLibraryWatcherEnabled()) {
                stopWatching();
            }
        }
    }
      /**
     * Scans a music folder and adds all found songs to the library.
     * This method runs asynchronously to avoid blocking the UI.
     * 
     * @param folder The folder to scan for music files
     * @param clearExisting Whether to clear existing songs before scanning
     */
    public void scanMusicFolder(File folder, boolean clearExisting) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder provided for scanning: " + folder);
            return;
        }

        this.currentMusicFolder = folder;
        if (settingsService != null && settingsService.getSettings() != null) {
            settingsService.getSettings().setMusicRootPath(folder.getAbsolutePath());
            settingsService.saveSettings();
            if (settingsService.getSettings().isLibraryWatcherEnabled()) {
                restartWatching(folder);
            } else {
                stopWatching();
            }
        } else {
            restartWatching(folder);
        }
        
        // Run scanning in a separate thread to avoid blocking the UI
        Thread scanThread = new Thread(() -> {
            try {
                System.out.println("Starting scan of folder: " + folder.getAbsolutePath());
                
                // Clear existing songs from repository if requested
                if (clearExisting) {
                    clearLibrary();
                }
                
                // Scan the folder for music files
                List<Song> scannedSongs = MusicScanner.scanDirectory(folder);
                
                // Build set of existing file paths to avoid duplicates
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Song s : songRepository.findAll()) {
                    if (s.getFilePath() != null) existing.add(s.getFilePath());
                }

                for (Song song : scannedSongs) {
                    if (song.getFilePath() == null) continue;
                    if (existing.contains(song.getFilePath())) {
                        // Update metadata in case tags changed
                        Song existingSong = songRepository.findAll().stream()
                                .filter(x -> song.getFilePath().equals(x.getFilePath()))
                                .findFirst().orElse(null);
                        if (existingSong != null) {
                            existingSong.setTitle(song.getTitle());
                            existingSong.setArtist(song.getArtist());
                            existingSong.setAlbum(song.getAlbum());
                            existingSong.setGenre(song.getGenre());
                            existingSong.setDuration(song.getDuration());
                            existingSong.setTrackNumber(song.getTrackNumber());
                            songRepository.save(existingSong);
                        }
                        continue;
                    }
                    songRepository.save(song);
                }
                
                System.out.println("Scan complete. Found " + scannedSongs.size() + " songs.");
                
                // Notify callback of library update (if set)
                if (libraryUpdateCallback != null) {
                    // Run callback on JavaFX Application Thread
                    javafx.application.Platform.runLater(() -> {
                        libraryUpdateCallback.accept(getAllSongs());
                    });
                }
                
            } catch (Exception e) {
                System.err.println("Error during music folder scan: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        scanThread.setDaemon(true);
        scanThread.setName("MusicScanner-" + folder.getName());
        scanThread.start();
    }
    
    /**
     * Scans a music folder and adds all found songs to the library.
     * This method clears existing songs before scanning.
     * 
     * @param folder The folder to scan for music files
     */
    public void scanMusicFolder(File folder) {
        scanMusicFolder(folder, true);
    }
    
    /**
     * Adds a single song to the library.
     * 
     * @param song The song to add
     */
    public void addSong(Song song) {
        songRepository.save(song);
        
        // Notify callback if set
        if (libraryUpdateCallback != null) {
            libraryUpdateCallback.accept(getAllSongs());
        }
    }
    
    /**
     * Removes a song from the library.
     * 
     * @param songId The ID of the song to remove
     */
    public void removeSong(long songId) {
        songRepository.delete(songId);
        
        // Notify callback if set
        if (libraryUpdateCallback != null) {
            libraryUpdateCallback.accept(getAllSongs());
        }
    }
    
    /**
     * Gets all songs in the library.
     * 
     * @return List of all songs
     */
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }
    
    /**
     * Gets a song by its ID.
     * 
     * @param id The song ID
     * @return The song, or null if not found
     */
    public Song getSongById(long id) {
        return songRepository.findById(id);
    }
    
    /**
     * Clears all songs from the library.
     */
    public void clearLibrary() {
        List<Song> allSongs = songRepository.findAll();
        for (Song song : allSongs) {
            songRepository.delete(song.getId());
        }
        
        // Notify callback if set
        if (libraryUpdateCallback != null) {
            libraryUpdateCallback.accept(getAllSongs());
        }
    }
    
    /**
     * Gets the currently set music folder.
     * 
     * @return The current music folder, or null if none is set
     */
    public File getCurrentMusicFolder() {
        return currentMusicFolder;
    }
    
    /**
     * Rescans the current music folder by performing a complete clear and rescan.
     * This method uses the same reliable logic as "Clear & Scan New Folder" to ensure
     * a thorough and consistent library refresh.
     */
    public void rescanCurrentFolder() {
        if (currentMusicFolder == null || !currentMusicFolder.exists()) {
            System.out.println("No current music folder set or folder doesn't exist. Cannot rescan.");
            return;
        }
        
        System.out.println("Starting full rescan of current music folder: " + currentMusicFolder.getAbsolutePath());
        
        // Use the proven scanMusicFolder logic with clearExisting=true
        // This ensures a complete refresh just like "Clear & Scan New Folder"
        scanMusicFolder(currentMusicFolder, true);
    }
    

    

    
    /**
     * Gets the total number of songs in the library.
     * 
     * @return Number of songs
     */
    public int getSongCount() {
        return getAllSongs().size();
    }
    
    /**
     * Forces a save of all current data to persistent storage (if supported).
     * This is useful for ensuring data is persisted before application shutdown.
     */
    public void forceSave() {
        if (songRepository instanceof PersistentSongRepository) {
            ((PersistentSongRepository) songRepository).forceSave();
        }
    }
    
    /**
     * Checks if there is existing data in the library.
     * 
     * @return true if the library contains songs, false otherwise
     */
    public boolean hasExistingData() {
        return getSongCount() > 0;
    }

    private void restartWatching(File folder) {
        if (settingsService != null && settingsService.getSettings() != null &&
            !settingsService.getSettings().isLibraryWatcherEnabled()) {
            return;
        }
        stopWatching();
        try {
            watchService = folder.toPath().getFileSystem().newWatchService();
            registerAll(folder.toPath());
            watching = true;
            watcherThread = new Thread(() -> {
                while (watching) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        break;
                    }
                    Path dir = watchKeys.get(key);
                    if (dir == null) {
                        key.reset();
                        continue;
                    }
                    boolean changed = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path name = ev.context();
                        Path child = dir.resolve(name);
                        File f = child.toFile();
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            if (f.isDirectory()) {
                                try { registerAll(child); } catch (Exception ignored) {}
                                List<Song> songs = MusicScanner.scanDirectory(f);
                                if (!songs.isEmpty()) {
                                    applyScanResults(songs);
                                    changed = true;
                                }
                            } else {
                                Song s = MusicScanner.scanFile(f);
                                if (s != null) {
                                    upsertSongByPath(s);
                                    changed = true;
                                }
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            if (f.isFile()) {
                                Song s = MusicScanner.scanFile(f);
                                if (s != null) {
                                    upsertSongByPath(s);
                                    changed = true;
                                }
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            String p = child.toAbsolutePath().toString();
                            Song existing = findByFilePath(p);
                            if (existing != null) {
                                songRepository.delete(existing.getId());
                                changed = true;
                            }
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        watchKeys.remove(key);
                    }
                    if (changed && libraryUpdateCallback != null) {
                        javafx.application.Platform.runLater(() -> libraryUpdateCallback.accept(getAllSongs()));
                    }
                }
            });
            watcherThread.setDaemon(true);
            watcherThread.setName("MusicWatcher-" + folder.getName());
            watcherThread.start();
        } catch (IOException e) {
            System.err.println("Failed to start watcher: " + e.getMessage());
        }
    }

    private void stopWatching() {
        watching = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
            watchService = null;
        }
        watchKeys.clear();
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchKeys.put(key, dir);
    }

    private void applyScanResults(List<Song> scannedSongs) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Song s : songRepository.findAll()) {
            if (s.getFilePath() != null) existing.add(s.getFilePath());
        }
        for (Song song : scannedSongs) {
            if (song.getFilePath() == null) continue;
            if (existing.contains(song.getFilePath())) {
                Song existingSong = songRepository.findAll().stream()
                        .filter(x -> song.getFilePath().equals(x.getFilePath()))
                        .findFirst().orElse(null);
                if (existingSong != null) {
                    existingSong.setTitle(song.getTitle());
                    existingSong.setArtist(song.getArtist());
                    existingSong.setAlbum(song.getAlbum());
                    existingSong.setGenre(song.getGenre());
                    existingSong.setDuration(song.getDuration());
                    existingSong.setTrackNumber(song.getTrackNumber());
                    existingSong.setYear(song.getYear());
                    songRepository.save(existingSong);
                }
            } else {
                songRepository.save(song);
            }
        }
    }

    private void upsertSongByPath(Song song) {
        if (song == null || song.getFilePath() == null) return;
        Song existingSong = findByFilePath(song.getFilePath());
        if (existingSong != null) {
            existingSong.setTitle(song.getTitle());
            existingSong.setArtist(song.getArtist());
            existingSong.setAlbum(song.getAlbum());
            existingSong.setGenre(song.getGenre());
            existingSong.setDuration(song.getDuration());
            existingSong.setTrackNumber(song.getTrackNumber());
            existingSong.setYear(song.getYear());
            songRepository.save(existingSong);
        } else {
            songRepository.save(song);
        }
    }

    private Song findByFilePath(String path) {
        if (path == null) return null;
        for (Song s : songRepository.findAll()) {
            if (path.equals(s.getFilePath())) return s;
        }
        return null;
    }

    public void refreshWatcherFromSettings() {
        if (settingsService == null || settingsService.getSettings() == null) return;
        String p = settingsService.getSettings().getMusicRootPath();
        boolean enabled = settingsService.getSettings().isLibraryWatcherEnabled();
        if (p == null || p.isBlank()) {
            stopWatching();
            return;
        }
        File folder = new File(p);
        if (!folder.exists() || !folder.isDirectory()) {
            stopWatching();
            return;
        }
        currentMusicFolder = folder;
        if (enabled) {
            restartWatching(folder);
            // Non-destructive scan to ingest existing files under the new root
            scanMusicFolder(folder, false);
        } else {
            stopWatching();
        }
    }

    public void shutdown() {
        stopWatching();
    }
}

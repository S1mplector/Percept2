package com.musicplayer.data.models;

import java.time.LocalDateTime;

/**
 * Application settings model.
 */
public class Settings {
    
    // Visual settings
    private boolean visualizerEnabled = true;
    private VisualizerColorMode visualizerColorMode; // No default - will be set from settings.json or inferred
    private String visualizerSolidColor = "#32CD32"; // Default lime green

    
    // Theme settings
    public enum Theme {
        LIGHT,
        DARK;
    }
    private Theme theme = Theme.LIGHT;

    // Mini player settings
    private double miniPlayerX = -1; // -1 means use default position
    private double miniPlayerY = -1; // -1 means use default position

    // Whether mini player window should stay always on top
    private boolean miniPlayerPinned = true;

    // Resume playback settings
    private boolean resumeOnStartup = true;
    private long lastSongId = -1; // -1 means none
    private double lastPositionSeconds = 0.0;
    private double lastVolume = 0.5; // default volume
    
    // Library settings
    private String musicRootPath;
    private boolean libraryWatcherEnabled = true;
    
    // Update settings
    private boolean autoCheckForUpdates = true;
    private int updateCheckIntervalHours = 24;
    private LocalDateTime lastUpdateCheck;
    private String skippedUpdateVersion;
    private boolean downloadUpdatesInBackground = true;
    private boolean showPreReleaseVersions = false;
    
    // Distribution type preferences
    private DistributionType preferredDistributionType = DistributionType.UNKNOWN;
    private boolean rememberDistributionChoice = false;
    
    public enum VisualizerColorMode {
        GRADIENT_CYCLING("Gradient Cycling"),
        SOLID_COLOR("Solid Color");
        
        private final String displayName;
        
        VisualizerColorMode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Getters and setters
    public boolean isVisualizerEnabled() {
        return visualizerEnabled;
    }
    
    public void setVisualizerEnabled(boolean visualizerEnabled) {
        this.visualizerEnabled = visualizerEnabled;
    }
    
    public VisualizerColorMode getVisualizerColorMode() {
        return visualizerColorMode;
    }
    
    public void setVisualizerColorMode(VisualizerColorMode visualizerColorMode) {
        this.visualizerColorMode = visualizerColorMode;
    }
    

    public String getVisualizerSolidColor() {
        return visualizerSolidColor;
    }
    
    public void setVisualizerSolidColor(String visualizerSolidColor) {
        this.visualizerSolidColor = visualizerSolidColor;
    }
    
    public double getMiniPlayerX() {
        return miniPlayerX;
    }
    
    public void setMiniPlayerX(double miniPlayerX) {
        this.miniPlayerX = miniPlayerX;
    }
    
    public double getMiniPlayerY() {
        return miniPlayerY;
    }
    
    public void setMiniPlayerY(double miniPlayerY) {
        this.miniPlayerY = miniPlayerY;
    }

    public boolean isMiniPlayerPinned() {
        return miniPlayerPinned;
    }

    public void setMiniPlayerPinned(boolean miniPlayerPinned) {
        this.miniPlayerPinned = miniPlayerPinned;
    }

    // Resume playback getters/setters
    public boolean isResumeOnStartup() {
        return resumeOnStartup;
    }

    public void setResumeOnStartup(boolean resumeOnStartup) {
        this.resumeOnStartup = resumeOnStartup;
    }

    public long getLastSongId() {
        return lastSongId;
    }

    public void setLastSongId(long lastSongId) {
        this.lastSongId = lastSongId;
    }

    public double getLastPositionSeconds() {
        return lastPositionSeconds;
    }

    public void setLastPositionSeconds(double lastPositionSeconds) {
        this.lastPositionSeconds = lastPositionSeconds;
    }

    public double getLastVolume() {
        return lastVolume;
    }

    public void setLastVolume(double lastVolume) {
        this.lastVolume = lastVolume;
    }
    
    public String getMusicRootPath() {
        return musicRootPath;
    }
    
    public void setMusicRootPath(String musicRootPath) {
        this.musicRootPath = musicRootPath;
    }
    
    public boolean isLibraryWatcherEnabled() {
        return libraryWatcherEnabled;
    }
    
    public void setLibraryWatcherEnabled(boolean libraryWatcherEnabled) {
        this.libraryWatcherEnabled = libraryWatcherEnabled;
    }
    
    // Update settings getters and setters
    public boolean isAutoCheckForUpdates() {
        return autoCheckForUpdates;
    }
    
    public void setAutoCheckForUpdates(boolean autoCheckForUpdates) {
        this.autoCheckForUpdates = autoCheckForUpdates;
    }
    
    public int getUpdateCheckIntervalHours() {
        return updateCheckIntervalHours;
    }
    
    public void setUpdateCheckIntervalHours(int updateCheckIntervalHours) {
        this.updateCheckIntervalHours = updateCheckIntervalHours;
    }
    
    public LocalDateTime getLastUpdateCheck() {
        return lastUpdateCheck;
    }
    
    public void setLastUpdateCheck(LocalDateTime lastUpdateCheck) {
        this.lastUpdateCheck = lastUpdateCheck;
    }
    
    public String getSkippedUpdateVersion() {
        return skippedUpdateVersion;
    }
    
    public void setSkippedUpdateVersion(String skippedUpdateVersion) {
        this.skippedUpdateVersion = skippedUpdateVersion;
    }
    
    public boolean isDownloadUpdatesInBackground() {
        return downloadUpdatesInBackground;
    }
    
    public void setDownloadUpdatesInBackground(boolean downloadUpdatesInBackground) {
        this.downloadUpdatesInBackground = downloadUpdatesInBackground;
    }

    // Theme getter and setter
    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }
    
    public boolean isShowPreReleaseVersions() {
        return showPreReleaseVersions;
    }
    
    public void setShowPreReleaseVersions(boolean showPreReleaseVersions) {
        this.showPreReleaseVersions = showPreReleaseVersions;
    }
    
    // Distribution type preference getters and setters
    public DistributionType getPreferredDistributionType() {
        return preferredDistributionType;
    }
    
    public void setPreferredDistributionType(DistributionType preferredDistributionType) {
        this.preferredDistributionType = preferredDistributionType;
    }
    
    public boolean isRememberDistributionChoice() {
        return rememberDistributionChoice;
    }
    
    public void setRememberDistributionChoice(boolean rememberDistributionChoice) {
        this.rememberDistributionChoice = rememberDistributionChoice;
    }
}
package com.musicplayer.data.models;

/**
 * Enum representing different distribution types of the application.
 */
public enum DistributionType {
    /**
     * Portable version - no installation required, runs from any location
     */
    PORTABLE("Portable", "portable"),
    
    /**
     * Installer version - requires installation via setup/installer
     */
    INSTALLER("Installer", "installer", "setup"),
    
    /**
     * Release executable - standalone executable that replaces the current one
     */
    RELEASE("Release", "release"),
    
    /**
     * Unknown distribution type - cannot be determined
     */
    UNKNOWN("Unknown");
    
    private final String displayName;
    private final String[] filePatterns;
    
    DistributionType(String displayName, String... filePatterns) {
        this.displayName = displayName;
        this.filePatterns = filePatterns;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String[] getFilePatterns() {
        return filePatterns;
    }
    
    /**
     * Detect distribution type from filename.
     * 
     * @param filename The filename to check
     * @return The detected distribution type
     */
    public static DistributionType fromFilename(String filename) {
        if (filename == null) {
            return UNKNOWN;
        }
        
        String lowerFilename = filename.toLowerCase();
        
        // Check for portable patterns (zip files containing portable)
        for (String pattern : PORTABLE.filePatterns) {
            if (lowerFilename.contains(pattern)) {
                return PORTABLE;
            }
        }
        
        // Check for installer patterns
        for (String pattern : INSTALLER.filePatterns) {
            if (lowerFilename.contains(pattern)) {
                return INSTALLER;
            }
        }
        
        // Check for release patterns
        for (String pattern : RELEASE.filePatterns) {
            if (lowerFilename.contains(pattern)) {
                return RELEASE;
            }
        }
        
        // If it's a standalone .exe file without specific keywords, assume it's a release executable
        if (lowerFilename.endsWith(".exe") &&
            !lowerFilename.contains("installer") &&
            !lowerFilename.contains("setup") &&
            !lowerFilename.contains("portable")) {
            return RELEASE;
        }
        
        // Default to UNKNOWN if no pattern matches
        return UNKNOWN;
    }
}
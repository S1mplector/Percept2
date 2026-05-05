package com.musicplayer.config;

/**
 * Configuration for the update system.
 * Update these values with your GitHub repository information.
 */
public class UpdateConfig {
    
    /**
     * GitHub username or organization name.
     * TODO: Replace with your actual GitHub username
     */
    public static final String GITHUB_OWNER = "S1mplector";
    
    /**
     * GitHub repository name.
     * TODO: Replace with your actual repository name
     */
    public static final String GITHUB_REPO = "Simp3";
    
    /**
     * Whether to check for updates on application startup.
     */
    public static final boolean CHECK_ON_STARTUP = true;
    
    /**
     * Delay in seconds before checking for updates on startup.
     * Set to 0 to check immediately on launch.
     */
    public static final int STARTUP_CHECK_DELAY = 0;
    
    /**
     * Connection timeout in milliseconds for update checks.
     */
    public static final int CONNECTION_TIMEOUT = 10000;
    
    /**
     * Read timeout in milliseconds for update checks.
     */
    public static final int READ_TIMEOUT = 10000;
}
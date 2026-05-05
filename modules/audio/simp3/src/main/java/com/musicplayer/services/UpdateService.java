package com.musicplayer.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicplayer.config.UpdateConfig;
import com.musicplayer.data.models.DistributionType;
import com.musicplayer.data.models.Settings;
import com.musicplayer.data.models.UpdateInfo;
import com.musicplayer.utils.VersionComparator;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Service for checking and applying application updates.
 */
public class UpdateService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);
    
    // GitHub API configuration
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_RELEASES_URL = GITHUB_API_BASE + "/repos/" +
        UpdateConfig.GITHUB_OWNER + "/" + UpdateConfig.GITHUB_REPO + "/releases";
    
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    
    // Properties for download progress
    private final DoubleProperty downloadProgress = new SimpleDoubleProperty(0.0);
    private final StringProperty downloadStatus = new SimpleStringProperty("");
    
    private String currentVersion;
    
    public UpdateService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("UpdateChecker");
            return t;
        });
        
        loadCurrentVersion();
    }
    
    /**
     * Load current version from application properties.
     */
    private void loadCurrentVersion() {
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            Properties props = new Properties();
            props.load(is);
            currentVersion = props.getProperty("app.version", "1.0.0");
            logger.info("Current application version: {}", currentVersion);
        } catch (Exception e) {
            logger.error("Failed to load application version", e);
            currentVersion = "1.0.0";
        }
    }
    
    /**
     * Start automatic update checking based on settings.
     */
    public void startAutoUpdateCheck() {
        Settings settings = settingsService.getSettings();
        if (!settings.isAutoCheckForUpdates()) {
            logger.info("Auto-update checking is disabled in settings");
            return;
        }
        
        logger.info("Starting auto-update check service");
        logger.info("Current version: {}", currentVersion);
        logger.info("GitHub repository: {}/{}", UpdateConfig.GITHUB_OWNER, UpdateConfig.GITHUB_REPO);
        
        // Schedule periodic update checks
        int intervalHours = settings.getUpdateCheckIntervalHours();
        int initialDelay = UpdateConfig.STARTUP_CHECK_DELAY;
        
        if (initialDelay == 0) {
            logger.info("Checking for updates immediately on startup");
        } else {
            logger.info("First update check will run in {} seconds", initialDelay);
        }
        
        scheduler.scheduleWithFixedDelay(
            this::checkForUpdatesInBackground,
            initialDelay,
            intervalHours * 60 * 60, // Convert hours to seconds
            TimeUnit.SECONDS
        );
        
        logger.info("Auto-update checking scheduled every {} hours", intervalHours);
    }
    
    /**
     * Check for updates in the background.
     */
    private void checkForUpdatesInBackground() {
        logger.info("Starting background update check");
        checkForUpdates().thenAccept(updateInfo -> {
            if (updateInfo != null) {
                logger.info("Update available: version {} (current: {})",
                    updateInfo.getVersion(), currentVersion);
                logger.info("Download URL: {}", updateInfo.getDownloadUrl());
                logger.info("File size: {}", updateInfo.getFileSizeFormatted());
                logger.info("Mandatory: {}", updateInfo.isMandatory());
                
                Platform.runLater(() -> {
                    // Notify UI about available update
                    logger.info("Notifying UI about available update");
                    // The UI will handle showing the update dialog
                });
            } else {
                logger.info("No updates available - current version {} is up to date", currentVersion);
            }
        }).exceptionally(e -> {
            logger.error("Background update check failed: {}", e.getMessage(), e);
            return null;
        });
    }
    
    /**
     * Check for updates from GitHub releases.
     * 
     * @return CompletableFuture with UpdateInfo if update is available, null otherwise
     */
    public CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Checking for updates...");
                Settings settings = settingsService.getSettings();
                settings.setLastUpdateCheck(LocalDateTime.now());
                settingsService.saveSettings();
                
                // Query GitHub API
                String apiUrl = settings.isShowPreReleaseVersions() ?
                    GITHUB_RELEASES_URL : GITHUB_RELEASES_URL + "/latest";
                logger.info("Querying GitHub API: {}", apiUrl);
                
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "SiMP3-UpdateChecker");
                conn.setConnectTimeout(UpdateConfig.CONNECTION_TIMEOUT);
                conn.setReadTimeout(UpdateConfig.READ_TIMEOUT);
                
                logger.debug("Sending request to GitHub API...");
                int responseCode = conn.getResponseCode();
                logger.info("GitHub API response code: {}", responseCode);
                
                if (responseCode != 200) {
                    logger.warn("GitHub API returned non-200 status: {}", responseCode);
                    return null;
                }
                
                // Parse response
                JsonNode response = objectMapper.readTree(conn.getInputStream());
                UpdateInfo latestUpdate = null;
                
                if (response.isArray()) {
                    // Multiple releases (including pre-releases)
                    for (JsonNode release : response) {
                        UpdateInfo info = parseReleaseInfo(release);
                        if (info != null && shouldConsiderUpdate(info, settings)) {
                            latestUpdate = info;
                            break;
                        }
                    }
                } else {
                    // Single release (latest stable)
                    latestUpdate = parseReleaseInfo(response);
                }
                
                if (latestUpdate != null && shouldConsiderUpdate(latestUpdate, settings)) {
                    logger.info("Found newer version: {} (current: {})",
                        latestUpdate.getVersion(), currentVersion);
                    return latestUpdate;
                }
                
                logger.info("No updates available - already on latest version");
                return null;
                
            } catch (Exception e) {
                logger.error("Failed to check for updates", e);
                throw new RuntimeException("Update check failed", e);
            }
        });
    }
    
    /**
     * Detect the distribution type of the current running application.
     *
     * @return The detected distribution type
     */
    private DistributionType detectDistributionType() {
        try {
            // Check if running from a typical installer location
            String userHome = System.getProperty("user.home");
            String currentPath = new File(".").getCanonicalPath().toLowerCase();
            
            // Common installer paths
            if (currentPath.contains("program files") ||
                currentPath.contains("programdata") ||
                currentPath.contains("appdata\\local") ||
                currentPath.contains("appdata\\roaming")) {
                logger.info("Detected installer distribution based on path: {}", currentPath);
                return DistributionType.INSTALLER;
            }
            
            // Check for portable indicators
            if (currentPath.contains("portable") ||
                currentPath.contains("desktop") ||
                currentPath.contains("downloads") ||
                currentPath.contains("documents")) {
                logger.info("Detected portable distribution based on path: {}", currentPath);
                return DistributionType.PORTABLE;
            }
            
            // Check for update directory (portable versions often have this)
            if (Files.exists(Paths.get("update")) || Files.exists(Paths.get("data"))) {
                logger.info("Detected portable distribution based on directory structure");
                return DistributionType.PORTABLE;
            }
            
        } catch (Exception e) {
            logger.error("Failed to detect distribution type", e);
        }
        
        logger.info("Could not determine distribution type");
        return DistributionType.UNKNOWN;
    }
    
    /**
     * Parse release information from GitHub API response.
     * This method now handles multiple assets and detects their distribution types.
     */
    private UpdateInfo parseReleaseInfo(JsonNode release) {
        try {
            String tagName = release.get("tag_name").asText();
            String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            
            logger.debug("Parsing release: {} (version: {})", tagName, version);
            
            // Skip if not a valid version
            if (!VersionComparator.isValidVersion(version)) {
                logger.debug("Skipping invalid version format: {}", version);
                return null;
            }
            
            // Get user's distribution preference
            Settings settings = settingsService.getSettings();
            DistributionType preferredType = settings.getPreferredDistributionType();
            
            // If no preference, detect current distribution type
            if (preferredType == DistributionType.UNKNOWN) {
                preferredType = detectDistributionType();
            }
            
            UpdateInfo info = new UpdateInfo();
            info.setVersion(version);
            info.setReleaseNotes(release.get("body").asText(""));
            info.setHtmlUrl(release.get("html_url").asText());
            info.setMandatory(release.get("name").asText("").toLowerCase().contains("mandatory"));
            
            // Parse release date
            String publishedAt = release.get("published_at").asText();
            LocalDateTime releaseDate = LocalDateTime.parse(publishedAt,
                DateTimeFormatter.ISO_DATE_TIME);
            info.setReleaseDate(releaseDate);
            
            // Find Windows executable assets
            JsonNode assets = release.get("assets");
            if (assets != null && assets.isArray()) {
                UpdateInfo preferredAsset = null;
                UpdateInfo fallbackAsset = null;
                
                // First pass: look for preferred distribution type
                for (JsonNode asset : assets) {
                    String name = asset.get("name").asText();
                    if (isWindowsExecutable(name)) {
                        DistributionType assetType = DistributionType.fromFilename(name);
                        
                        if (assetType == preferredType) {
                            preferredAsset = createUpdateInfoFromAsset(info, asset, assetType, assets);
                            logger.debug("Found preferred {} asset: {}", assetType, name);
                            break;
                        } else if (fallbackAsset == null && assetType != DistributionType.UNKNOWN) {
                            fallbackAsset = createUpdateInfoFromAsset(info, asset, assetType, assets);
                            logger.debug("Found fallback {} asset: {}", assetType, name);
                        }
                    }
                }
                
                // Use preferred asset if found, otherwise use fallback
                if (preferredAsset != null) {
                    return preferredAsset;
                } else if (fallbackAsset != null) {
                    logger.info("Preferred {} version not found, using {} version",
                        preferredType, fallbackAsset.getDistributionType());
                    return fallbackAsset;
                }
                
                // Last resort: find any Windows executable
                for (JsonNode asset : assets) {
                    String name = asset.get("name").asText();
                    if (isWindowsExecutable(name)) {
                        return createUpdateInfoFromAsset(info, asset, DistributionType.UNKNOWN, assets);
                    }
                }
            }
            
            logger.debug("No Windows executable found for version {}", version);
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to parse release info", e);
            return null;
        }
    }
    
    /**
     * Create UpdateInfo from a specific asset.
     */
    private UpdateInfo createUpdateInfoFromAsset(UpdateInfo baseInfo, JsonNode asset,
                                                 DistributionType distributionType,
                                                 JsonNode allAssets) {
        UpdateInfo info = new UpdateInfo();
        info.setVersion(baseInfo.getVersion());
        info.setReleaseNotes(baseInfo.getReleaseNotes());
        info.setHtmlUrl(baseInfo.getHtmlUrl());
        info.setMandatory(baseInfo.isMandatory());
        info.setReleaseDate(baseInfo.getReleaseDate());
        info.setDistributionType(distributionType);
        
        String name = asset.get("name").asText();
        info.setDownloadUrl(asset.get("browser_download_url").asText());
        info.setFileSize(asset.get("size").asLong());
        
        // Look for checksum file
        String checksumName = name + ".sha256";
        for (JsonNode checksumAsset : allAssets) {
            if (checksumAsset.get("name").asText().equals(checksumName)) {
                info.setChecksum(checksumAsset.get("browser_download_url").asText());
                break;
            }
        }
        
        return info;
    }
    
    /**
     * Check if a file name matches Windows executable patterns.
     * Enhanced to better identify different distribution types.
     */
    private boolean isWindowsExecutable(String fileName) {
        fileName = fileName.toLowerCase();
        
        // Check for executable files
        if (fileName.endsWith(".exe")) {
            return true;
        }
        
        // Check for Windows zip files
        if (fileName.endsWith(".zip") &&
            (fileName.contains("win") || fileName.contains("windows") || fileName.contains("portable"))) {
            return true;
        }
        
        // Check for MSI installer files
        if (fileName.endsWith(".msi")) {
            return true;
        }
        
        // Check for portable versions
        if (fileName.contains("portable") &&
            (fileName.endsWith(".zip") || fileName.endsWith(".exe"))) {
            return true;
        }
        
        // Check for installer/setup files
        if ((fileName.contains("installer") || fileName.contains("setup")) &&
            (fileName.endsWith(".exe") || fileName.endsWith(".msi"))) {
            return true;
        }
        
        // Check for release executables (standalone .exe files)
        if (fileName.contains("release") && fileName.endsWith(".exe")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if an update should be considered based on version and settings.
     */
    private boolean shouldConsiderUpdate(UpdateInfo info, Settings settings) {
        // Check if version is newer
        boolean isNewer = VersionComparator.isNewer(info.getVersion(), currentVersion);
        if (!isNewer) {
            logger.debug("Version {} is not newer than current version {}",
                info.getVersion(), currentVersion);
            return false;
        }
        
        // Check if version is skipped
        String skippedVersion = settings.getSkippedUpdateVersion();
        if (skippedVersion != null && skippedVersion.equals(info.getVersion()) && !info.isMandatory()) {
            logger.info("Version {} is skipped by user preference", info.getVersion());
            return false;
        }
        
        logger.debug("Version {} should be considered for update", info.getVersion());
        return true;
    }
    
    /**
     * Download an update file.
     * 
     * @param updateInfo Update information
     * @return CompletableFuture with the downloaded file
     */
    public CompletableFuture<File> downloadUpdate(UpdateInfo updateInfo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting download of version {}", updateInfo.getVersion());
                
                // Create temp directory for downloads
                Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "simp3-updates");
                Files.createDirectories(tempDir);
                logger.info("Download directory: {}", tempDir);
                
                // Determine file name from URL
                String fileName = updateInfo.getDownloadUrl().substring(
                    updateInfo.getDownloadUrl().lastIndexOf('/') + 1);
                Path targetFile = tempDir.resolve(fileName);
                logger.info("Target file: {}", targetFile);
                
                // Download file
                downloadFile(updateInfo.getDownloadUrl(), targetFile, updateInfo.getFileSize());
                
                // Verify checksum if available
                if (updateInfo.getChecksum() != null && updateInfo.getChecksum().startsWith("http")) {
                    logger.info("Verifying checksum...");
                    verifyChecksum(targetFile, updateInfo.getChecksum());
                } else {
                    logger.info("No checksum available for verification");
                }
                
                logger.info("Download completed successfully: {}", targetFile);
                return targetFile.toFile();
                
            } catch (Exception e) {
                logger.error("Failed to download update", e);
                throw new RuntimeException("Download failed", e);
            }
        });
    }
    
    /**
     * Download a file with progress tracking.
     */
    private void downloadFile(String urlStr, Path targetFile, long expectedSize) throws IOException {
        logger.info("Downloading from: {}", urlStr);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "SiMP3-Updater");
        
        long startTime = System.currentTimeMillis();
        
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(targetFile))) {
            
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            long lastLogTime = System.currentTimeMillis();
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                
                // Update progress
                if (expectedSize > 0) {
                    final long currentDownloaded = downloaded;
                    final double progress = (double) currentDownloaded / expectedSize;
                    Platform.runLater(() -> {
                        downloadProgress.set(progress);
                        downloadStatus.set(String.format("Downloaded %.1f MB of %.1f MB",
                            currentDownloaded / (1024.0 * 1024.0),
                            expectedSize / (1024.0 * 1024.0)));
                    });
                    
                    // Log progress every 5 seconds
                    if (System.currentTimeMillis() - lastLogTime > 5000) {
                        logger.info("Download progress: {:.1f}% ({:.1f} MB / {:.1f} MB)",
                            progress * 100,
                            currentDownloaded / (1024.0 * 1024.0),
                            expectedSize / (1024.0 * 1024.0));
                        lastLogTime = System.currentTimeMillis();
                    }
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Download completed in {} seconds", duration / 1000.0);
        
        Platform.runLater(() -> {
            downloadProgress.set(1.0);
            downloadStatus.set("Download complete");
        });
    }
    
    /**
     * Verify file checksum.
     */
    private void verifyChecksum(Path file, String checksumUrl) throws Exception {
        // Download checksum file
        Path checksumFile = file.getParent().resolve(file.getFileName() + ".sha256");
        downloadFile(checksumUrl, checksumFile, -1);
        
        // Read expected checksum
        String expectedChecksum = Files.readString(checksumFile).trim().split("\\s+")[0];
        
        // Calculate actual checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        String actualChecksum = bytesToHex(digest.digest());
        
        if (!expectedChecksum.equalsIgnoreCase(actualChecksum)) {
            throw new SecurityException("Checksum verification failed");
        }
        
        logger.info("Checksum verified successfully");
    }
    
    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    /**
     * Apply the downloaded update.
     *
     * @param updateFile Downloaded update file
     * @param updateInfo Update information containing distribution type
     * @return true if update was staged successfully
     */
    public boolean applyUpdate(File updateFile, UpdateInfo updateInfo) {
        try {
            logger.info("Staging update for installation: {}", updateFile.getName());
            
            // Create update directory
            Path updateDir = Paths.get("update");
            Files.createDirectories(updateDir);
            logger.info("Created update directory: {}", updateDir.toAbsolutePath());
            
            // Copy update file
            Path stagedUpdate = updateDir.resolve(updateFile.getName());
            Files.copy(updateFile.toPath(), stagedUpdate, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Staged update file: {}", stagedUpdate.toAbsolutePath());
            
            // Get distribution type from update info or detect from filename
            DistributionType updateDistType = updateInfo != null && updateInfo.getDistributionType() != DistributionType.UNKNOWN
                ? updateInfo.getDistributionType()
                : DistributionType.fromFilename(updateFile.getName());
            logger.info("Update distribution type: {}", updateDistType);
            
            // Detect current installation type
            DistributionType currentDistType = detectDistributionType();
            logger.info("Current installation type: {}", currentDistType);
            
            // Create update script with distribution type information
            createUpdateScript(stagedUpdate, updateDistType, currentDistType);
            
            logger.info("Update staged successfully - will be applied on next restart");
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to stage update: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Apply the downloaded update (backward compatibility).
     *
     * @param updateFile Downloaded update file
     * @return true if update was staged successfully
     */
    public boolean applyUpdate(File updateFile) {
        return applyUpdate(updateFile, null);
    }
    
    /**
     * Create update script for Windows.
     * Enhanced to handle different distribution types and update scenarios.
     *
     * @param updateFile The path to the staged update file
     * @param updateDistType The distribution type of the update file
     * @param currentDistType The distribution type of the current installation
     */
    private void createUpdateScript(Path updateFile, DistributionType updateDistType,
                                  DistributionType currentDistType) throws IOException {
        Path scriptPath = Paths.get("update", "apply-update.bat");
        
        // Get the update file name only (not full path)
        String updateFileName = updateFile.getFileName().toString();
        String lowerFileName = updateFileName.toLowerCase();
        
        // Determine update type based on distribution type and filename
        String script;
        
        if (updateDistType == DistributionType.INSTALLER ||
            lowerFileName.endsWith(".msi") ||
            (lowerFileName.endsWith(".exe") &&
             (lowerFileName.contains("setup") || lowerFileName.contains("installer")))) {
            // Create installer script
            script = createInstallerScript(updateFileName, lowerFileName);
        } else if (updateDistType == DistributionType.RELEASE ||
                   (lowerFileName.endsWith(".exe") && lowerFileName.contains("release"))) {
            // Create release executable script
            script = createReleaseScript(updateFileName, lowerFileName);
        } else {
            // Create portable update script (for PORTABLE or unknown types)
            script = createPortableScript(updateFileName, lowerFileName);
        }
        
        Files.writeString(scriptPath, script);
        logger.info("Update script created: {} (type: {})", scriptPath, updateDistType);
    }
    
    /**
     * Create update script for installer-based updates.
     */
    private String createInstallerScript(String updateFileName, String lowerFileName) {
        String silentFlags;
        
        if (lowerFileName.endsWith(".msi")) {
            // MSI installer - use Windows Installer silent flags
            silentFlags = "/qn /norestart";
        } else {
            // EXE installer - try common silent flags
            // Most installers support /S, /SILENT, or /VERYSILENT
            silentFlags = "/S /SILENT /VERYSILENT";
        }
        
        return String.format("""
            @echo off
            echo Applying SiMP3 update via installer...
            echo.
            
            :: Change to parent directory
            cd /d "%%~dp0\\.."
            
            :: Set update file path
            set "updateFile=update\\%s"
            
            :: Wait for application to close
            echo Waiting for application to close...
            timeout /t 5 /nobreak > nul
            
            :: Check if we're in a portable installation
            set "isPortable=0"
            if not exist "%%ProgramFiles%%\\SiMP3\\SiMP3.exe" (
                if not exist "%%ProgramFiles(x86)%%\\SiMP3\\SiMP3.exe" (
                    if not exist "%%LocalAppData%%\\Programs\\SiMP3\\SiMP3.exe" (
                        set "isPortable=1"
                    )
                )
            )
            
            :: Run installer
            echo Running installer...
            if /i "%%~x1"==".msi" (
                :: MSI installer
                msiexec /i "%%updateFile%%" %s
            ) else (
                :: EXE installer - try different silent flags
                "%%updateFile%%" /S >nul 2>&1
                if errorlevel 1 (
                    "%%updateFile%%" /SILENT >nul 2>&1
                    if errorlevel 1 (
                        "%%updateFile%%" /VERYSILENT >nul 2>&1
                        if errorlevel 1 (
                            :: If all silent flags fail, run with basic quiet flag
                            "%%updateFile%%" /q >nul 2>&1
                        )
                    )
                )
            )
            
            :: Clean up update file
            timeout /t 3 /nobreak > nul
            if exist "%%updateFile%%" del /Q "%%updateFile%%"
            
            :: If this was a portable installation, the installer might have installed to Program Files
            :: In that case, we should inform the user
            if "%%isPortable%%"=="1" (
                echo.
                echo NOTE: The installer may have installed SiMP3 to a different location.
                echo Please check your Start Menu or Program Files for the updated version.
                echo.
                pause
            ) else (
                :: Try to start the updated application from common installation paths
                if exist "%%ProgramFiles%%\\SiMP3\\SiMP3.exe" (
                    start "" "%%ProgramFiles%%\\SiMP3\\SiMP3.exe"
                ) else if exist "%%ProgramFiles(x86)%%\\SiMP3\\SiMP3.exe" (
                    start "" "%%ProgramFiles(x86)%%\\SiMP3\\SiMP3.exe"
                ) else if exist "%%LocalAppData%%\\Programs\\SiMP3\\SiMP3.exe" (
                    start "" "%%LocalAppData%%\\Programs\\SiMP3\\SiMP3.exe"
                ) else (
                    :: If not found in standard locations, try current directory
                    if exist "SiMP3.exe" start "" "SiMP3.exe"
                )
            )
            
            :: Clean up update directory
            timeout /t 2 /nobreak > nul
            rmdir /Q "update" 2>nul
            
            :: Exit
            exit
            """,
            updateFileName,
            silentFlags
        );
    }
    
    /**
     * Create update script for portable updates.
     */
    private String createPortableScript(String updateFileName, String lowerFileName) {
        return String.format("""
            @echo off
            echo Applying SiMP3 portable update...
            echo.
            
            :: Change to parent directory (where SiMP3.exe should be)
            cd /d "%%~dp0\\.."
            
            :: Wait for the application to fully close
            echo Waiting for application to close...
            timeout /t 3 /nobreak > nul
            
            :: Kill any remaining SiMP3 processes
            taskkill /F /IM SiMP3.exe >nul 2>&1
            timeout /t 2 /nobreak > nul
            
            :: Backup current executable
            if exist "SiMP3.exe" (
                echo Backing up current version...
                if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"
                move /Y "SiMP3.exe" "SiMP3.exe.backup" > nul 2>&1
            )
            
            :: Set update file path
            set "updateFile=update\\%s"
            
            :: Check if update file is a zip
            if /i "%%updateFile:~-4%%"==".zip" (
                echo Extracting update...
                :: Use PowerShell to extract, preserving directory structure
                powershell -NoProfile -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%%CD%%\\%%updateFile%%', '%%CD%%'); }"
                
                :: Check if extraction created a subdirectory
                for /d %%%%D in (*) do (
                    if exist "%%%%D\\SiMP3.exe" (
                        echo Moving files from extracted directory...
                        xcopy /E /Y "%%%%D\\*" "." > nul
                        rmdir /S /Q "%%%%D"
                    )
                )
            ) else if /i "%%updateFile:~-4%%"==".exe" (
                :: Check if it's a self-extracting archive or plain executable
                :: Try to extract first (some portable versions are self-extracting)
                echo Checking if update is self-extracting...
                "%%updateFile%%" /extract /quiet >nul 2>&1
                if errorlevel 1 (
                    :: Not self-extracting, just copy the executable
                    echo Installing new version...
                    copy /Y "%%updateFile%%" "SiMP3.exe" > nul
                )
            ) else (
                :: Unknown file type, try to copy
                echo Installing new version...
                copy /Y "%%updateFile%%" "SiMP3.exe" > nul
            )
            
            :: Verify update was successful
            if not exist "SiMP3.exe" (
                echo ERROR: Update failed - SiMP3.exe not found!
                if exist "SiMP3.exe.backup" (
                    echo Restoring backup...
                    move /Y "SiMP3.exe.backup" "SiMP3.exe" > nul
                )
                pause
                exit /b 1
            )
            
            :: Clean up update file
            if exist "%%updateFile%%" del /Q "%%updateFile%%"
            
            :: Clean up backup after successful update
            if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"
            
            :: Start updated application
            echo Starting SiMP3...
            start "" "SiMP3.exe"
            
            :: Clean up update directory
            timeout /t 2 /nobreak > nul
            rmdir /S /Q "update" 2>nul
            
            :: Exit
            exit
            """,
            updateFileName
        );
    }
    
    /**
     * Create update script for release executable updates.
     * This handles seamless replacement of the current executable.
     */
    private String createReleaseScript(String updateFileName, String lowerFileName) {
        return String.format("""
            @echo off
            echo Applying SiMP3 release update...
            echo.
            
            :: Change to parent directory (where SiMP3.exe should be)
            cd /d "%%~dp0\\.."
            
            :: Wait for the application to fully close
            echo Waiting for application to close...
            timeout /t 3 /nobreak > nul
            
            :: Kill any remaining SiMP3 processes
            taskkill /F /IM SiMP3.exe >nul 2>&1
            timeout /t 2 /nobreak > nul
            
            :: Backup current executable
            if exist "SiMP3.exe" (
                echo Backing up current version...
                if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"
                move /Y "SiMP3.exe" "SiMP3.exe.backup" > nul 2>&1
            )
            
            :: Set update file path
            set "updateFile=update\\%s"
            
            :: Replace the executable
            echo Installing new version...
            copy /Y "%%updateFile%%" "SiMP3.exe" > nul
            
            :: Verify update was successful
            if not exist "SiMP3.exe" (
                echo ERROR: Update failed - SiMP3.exe not found!
                if exist "SiMP3.exe.backup" (
                    echo Restoring backup...
                    move /Y "SiMP3.exe.backup" "SiMP3.exe" > nul
                )
                pause
                exit /b 1
            )
            
            :: Clean up update file
            if exist "%%updateFile%%" del /Q "%%updateFile%%"
            
            :: Clean up backup after successful update
            if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"
            
            :: Start updated application
            echo Starting SiMP3...
            start "" "SiMP3.exe"
            
            :: Clean up update directory
            timeout /t 2 /nobreak > nul
            rmdir /S /Q "update" 2>nul
            
            :: Exit
            exit
            """,
            updateFileName
        );
    }
    
    /**
     * Check if an update is staged and ready to apply.
     */
    public boolean isUpdateStaged() {
        return Files.exists(Paths.get("update", "apply-update.bat"));
    }
    
    /**
     * Get the current application version.
     */
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    /**
     * Get download progress property.
     */
    public DoubleProperty downloadProgressProperty() {
        return downloadProgress;
    }
    
    /**
     * Get download status property.
     */
    public StringProperty downloadStatusProperty() {
        return downloadStatus;
    }
    
    /**
     * Get the settings service instance.
     */
    public SettingsService getSettingsService() {
        return settingsService;
    }
    
    /**
     * Shutdown the update service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
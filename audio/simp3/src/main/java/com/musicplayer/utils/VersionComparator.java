package com.musicplayer.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for comparing semantic version strings.
 * Supports versions in the format: major.minor.patch[-prerelease][+build]
 */
public class VersionComparator {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(\\w+(?:\\.\\w+)*))?(?:\\+(\\w+(?:\\.\\w+)*))?$"
    );
    
    /**
     * Compare two version strings.
     * 
     * @param version1 First version string
     * @param version2 Second version string
     * @return negative if version1 < version2, 0 if equal, positive if version1 > version2
     */
    public static int compare(String version1, String version2) {
        if (version1 == null || version2 == null) {
            throw new IllegalArgumentException("Version strings cannot be null");
        }
        
        // Remove 'v' prefix if present
        version1 = version1.toLowerCase().startsWith("v") ? version1.substring(1) : version1;
        version2 = version2.toLowerCase().startsWith("v") ? version2.substring(1) : version2;
        
        VersionInfo v1 = parseVersion(version1);
        VersionInfo v2 = parseVersion(version2);
        
        // Compare major version
        int majorCompare = Integer.compare(v1.major, v2.major);
        if (majorCompare != 0) return majorCompare;
        
        // Compare minor version
        int minorCompare = Integer.compare(v1.minor, v2.minor);
        if (minorCompare != 0) return minorCompare;
        
        // Compare patch version
        int patchCompare = Integer.compare(v1.patch, v2.patch);
        if (patchCompare != 0) return patchCompare;
        
        // Compare pre-release versions
        // No pre-release is considered higher than any pre-release
        if (v1.preRelease == null && v2.preRelease == null) {
            return 0;
        } else if (v1.preRelease == null) {
            return 1; // v1 is higher (no pre-release)
        } else if (v2.preRelease == null) {
            return -1; // v2 is higher (no pre-release)
        } else {
            // Both have pre-release, compare lexicographically
            return v1.preRelease.compareTo(v2.preRelease);
        }
    }
    
    /**
     * Check if version1 is newer than version2.
     */
    public static boolean isNewer(String version1, String version2) {
        return compare(version1, version2) > 0;
    }
    
    /**
     * Check if version1 is older than version2.
     */
    public static boolean isOlder(String version1, String version2) {
        return compare(version1, version2) < 0;
    }
    
    /**
     * Check if two versions are equal.
     */
    public static boolean isEqual(String version1, String version2) {
        return compare(version1, version2) == 0;
    }
    
    /**
     * Check if the new version is newer than the current version.
     * This is an alias for isNewer with parameters reversed for clarity.
     *
     * @param currentVersion The current version
     * @param newVersion The new version to compare
     * @return true if newVersion is newer than currentVersion
     */
    public static boolean isNewerVersion(String currentVersion, String newVersion) {
        return compare(newVersion, currentVersion) > 0;
    }
    
    /**
     * Parse a version string into components.
     */
    private static VersionInfo parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }
        
        VersionInfo info = new VersionInfo();
        info.major = Integer.parseInt(matcher.group(1));
        info.minor = Integer.parseInt(matcher.group(2));
        info.patch = Integer.parseInt(matcher.group(3));
        info.preRelease = matcher.group(4);
        info.build = matcher.group(5);
        
        return info;
    }
    
    /**
     * Internal class to hold version components.
     */
    private static class VersionInfo {
        int major;
        int minor;
        int patch;
        String preRelease;
        String build;
    }
    
    /**
     * Validate if a version string is in the correct format.
     */
    public static boolean isValidVersion(String version) {
        if (version == null) return false;
        
        // Remove 'v' prefix if present
        version = version.toLowerCase().startsWith("v") ? version.substring(1) : version;
        
        return VERSION_PATTERN.matcher(version).matches();
    }
}
package com.musicplayer.data.models;

import java.time.LocalDateTime;

/**
 * Represents information about an available update.
 */
public class UpdateInfo {
    private String version;
    private String releaseNotes;
    private String downloadUrl;
    private long fileSize;
    private String checksum;
    private boolean isMandatory;
    private LocalDateTime releaseDate;
    private String htmlUrl; // GitHub release page URL
    private DistributionType distributionType = DistributionType.UNKNOWN;

    public UpdateInfo() {
    }

    public UpdateInfo(String version, String releaseNotes, String downloadUrl, 
                     long fileSize, String checksum, boolean isMandatory, 
                     LocalDateTime releaseDate, String htmlUrl) {
        this.version = version;
        this.releaseNotes = releaseNotes;
        this.downloadUrl = downloadUrl;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.isMandatory = isMandatory;
        this.releaseDate = releaseDate;
        this.htmlUrl = htmlUrl;
    }

    // Getters and setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    public void setMandatory(boolean mandatory) {
        isMandatory = mandatory;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public DistributionType getDistributionType() {
        return distributionType;
    }

    public void setDistributionType(DistributionType distributionType) {
        this.distributionType = distributionType;
    }

    /**
     * Get file size in MB as a formatted string.
     */
    public String getFileSizeFormatted() {
        double sizeMB = fileSize / (1024.0 * 1024.0);
        return String.format("%.1f MB", sizeMB);
    }
}
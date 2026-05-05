package com.musicplayer.data.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DistributionTypeTest {

    @Test
    void detects_installer_from_setup_filename() {
        // Given a typical installer filename
        String filename = "simp3-1.2.3-setup.exe";

        // When detecting distribution type
        DistributionType type = DistributionType.fromFilename(filename);

        // Then it should be recognized as INSTALLER
        assertEquals(DistributionType.INSTALLER, type);
    }

    @Test
    void detects_portable_from_portable_zip() {
        // Given a typical portable archive filename
        String filename = "simp3-2.0.0-portable.zip";

        // When detecting distribution type
        DistributionType type = DistributionType.fromFilename(filename);

        // Then it should be recognized as PORTABLE
        assertEquals(DistributionType.PORTABLE, type);
    }
}

package com.musicplayer.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VersionComparatorTest {
    
    @Test
    public void testVersionComparison() {
        // Test equal versions
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"));
        assertEquals(0, VersionComparator.compare("2.5.3", "2.5.3"));
        
        // Test major version differences
        assertTrue(VersionComparator.compare("2.0.0", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.0.0", "2.0.0") < 0);
        
        // Test minor version differences
        assertTrue(VersionComparator.compare("1.2.0", "1.1.0") > 0);
        assertTrue(VersionComparator.compare("1.1.0", "1.2.0") < 0);
        
        // Test patch version differences
        assertTrue(VersionComparator.compare("1.0.2", "1.0.1") > 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.2") < 0);
        
        // Test with v prefix
        assertTrue(VersionComparator.compare("v2.0.0", "v1.9.9") > 0);
        assertTrue(VersionComparator.compare("v1.0.0", "v1.0.1") < 0);
        
        // Test mixed (with and without v prefix)
        assertTrue(VersionComparator.compare("v2.0.0", "1.9.9") > 0);
        assertTrue(VersionComparator.compare("1.0.0", "v1.0.1") < 0);
        
        // Test complex versions
        assertTrue(VersionComparator.compare("10.2.8", "10.2.7") > 0);
        assertTrue(VersionComparator.compare("1.10.0", "1.9.0") > 0);
    }
    
    @Test
    public void testIsNewerVersion() {
        assertTrue(VersionComparator.isNewerVersion("1.0.0", "1.0.1"));
        assertTrue(VersionComparator.isNewerVersion("1.0.0", "1.1.0"));
        assertTrue(VersionComparator.isNewerVersion("1.0.0", "2.0.0"));
        assertTrue(VersionComparator.isNewerVersion("v1.0.0", "v2.0.0"));
        
        assertFalse(VersionComparator.isNewerVersion("2.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewerVersion("1.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewerVersion("1.1.0", "1.0.9"));
    }
}
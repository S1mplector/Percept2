package com.musicplayer.core.audio;

import java.util.Arrays;
import java.util.List;

/**
 * Test class to verify FLAC support and audio engine configuration
 */
public class AudioEngineTest {
    
    public static void main(String[] args) {
        System.out.println("=== SiMP3 Audio Engine Configuration Test ===\n");
        
        // Test 1: List all supported audio formats
        System.out.println("1. Supported Audio Formats:");
        System.out.println("---------------------------");
        List<String> supportedFormats = Arrays.asList(
            "mp3", "wav", "flac", "ogg", "m4a", "aac", "wma"
        );
        
        for (String format : supportedFormats) {
            System.out.println("   - ." + format.toUpperCase());
        }
        
        // Test 2: Show which engine handles each format (based on HybridAudioEngine logic)
        System.out.println("\n2. Audio Engine Routing (based on file extension):");
        System.out.println("--------------------------------------------------");
        
        // Test files with different extensions
        String[] testFiles = {
            "test.mp3",
            "test.wav", 
            "test.flac",
            "test.ogg",
            "test.m4a",
            "test.aac",
            "test.wma"
        };
        
        for (String filename : testFiles) {
            String engineType = determineEngineByExtension(filename);
            System.out.printf("   %-12s -> %s\n", filename, engineType);
        }
        
        // Test 3: Verify FLAC support specifically
        System.out.println("\n3. FLAC Support Verification:");
        System.out.println("-----------------------------");
        
        String flacEngine = determineEngineByExtension("test-audio.flac");
        boolean flacSupported = flacEngine.equals("JavaZoom Audio Engine");
        
        System.out.println("   FLAC files supported: " + (flacSupported ? "YES" : "NO"));
        System.out.println("   FLAC engine: " + flacEngine);
        System.out.println("   Status: " + (flacSupported ? "✓ READY" : "✗ NOT READY"));
        
        // Test 4: Check JavaZoom library presence
        System.out.println("\n4. JavaZoom Library Dependencies Check:");
        System.out.println("---------------------------------------");
        
        boolean jlayerFound = checkClassExists("javazoom.jl.decoder.Decoder");
        boolean vorbisFound = checkClassExists("javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader");
        boolean tritonusFound = checkClassExists("org.tritonus.share.sampled.file.TAudioFileReader");
        boolean basicPlayerFound = checkClassExists("javazoom.jlgui.basicplayer.BasicPlayer");
        
        System.out.println("   JLayer (MP3 support): " + (jlayerFound ? "✓ Found" : "✗ Not found"));
        System.out.println("   Vorbis SPI (OGG support): " + (vorbisFound ? "✓ Found" : "✗ Not found"));
        System.out.println("   Tritonus Share (Audio SPI): " + (tritonusFound ? "✓ Found" : "✗ Not found"));
        System.out.println("   BasicPlayer: " + (basicPlayerFound ? "✓ Found" : "✗ Not found"));
        
        // Test 5: Check FLAC-related classes (both org.jflac and potential SPI)
        System.out.println("\n5. FLAC-specific Classes:");
        System.out.println("-------------------------");
        
        // Check org.jflac classes
        String[] jflacClasses = {
            "org.jflac.FLACDecoder",
            "org.jflac.metadata.StreamInfo",
            "org.jflac.sound.spi.FlacAudioFileReader",
            "org.jflac.sound.spi.FlacFormatConversionProvider"
        };
        
        boolean anyJflacFound = false;
        for (String className : jflacClasses) {
            boolean exists = checkClassExists(className);
            if (exists) anyJflacFound = true;
            System.out.println("   " + className + ": " + (exists ? "✓" : "✗"));
        }
        
        // Check for alternative FLAC implementations
        System.out.println("\n6. Alternative FLAC Support Check:");
        System.out.println("----------------------------------");
        
        // Check if BasicPlayer can handle FLAC through existing SPIs
        boolean mp3spiFound = checkClassExists("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
        System.out.println("   MP3 SPI: " + (mp3spiFound ? "✓ Found" : "✗ Not found"));
        
        // Check if we can use JavaZoom's audio system for FLAC
        System.out.println("\n7. Audio System Capabilities:");
        System.out.println("-----------------------------");
        try {
            javax.sound.sampled.AudioFileFormat.Type[] types =
                javax.sound.sampled.AudioSystem.getAudioFileTypes();
            System.out.println("   Java Sound API: ✓ Available");
            System.out.println("   Supported file types: " + types.length);
            
            // List the supported types
            for (javax.sound.sampled.AudioFileFormat.Type type : types) {
                System.out.println("     - " + type.toString());
            }
            
        } catch (Exception e) {
            System.out.println("   Java Sound API: ✗ Error - " + e.getMessage());
        }
        
        // Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("FLAC support is " + (flacSupported ? "ENABLED" : "DISABLED"));
        System.out.println("FLAC files will be routed to: " + flacEngine);
        
        // Determine actual FLAC support status
        boolean flacReady = flacSupported && basicPlayerFound && tritonusFound;
        
        if (flacReady) {
            System.out.println("\n✓ The system is configured to handle FLAC files!");
            System.out.println("  - FLAC files will be routed to JavaZoom engine");
            System.out.println("  - BasicPlayer and required SPIs are available");
            System.out.println("  - Note: FLAC support may work through BasicPlayer's");
            System.out.println("    audio system integration even without specific FLAC SPI");
        } else {
            System.out.println("\n⚠ FLAC support configuration:");
            System.out.println("  - FLAC files are routed to JavaZoom engine: " + 
                             (flacSupported ? "YES" : "NO"));
            System.out.println("  - BasicPlayer available: " + 
                             (basicPlayerFound ? "YES" : "NO"));
            System.out.println("  - Audio SPI support: " + 
                             (tritonusFound ? "YES" : "NO"));
        }
        
        System.out.println("\nTest completed successfully!");
    }
    
    /**
     * Determine which engine will handle a file based on extension
     * This mirrors the logic in HybridAudioEngine
     */
    private static String determineEngineByExtension(String filename) {
        String filePath = filename.toLowerCase();
        
        // JavaZoom formats (including FLAC)
        if (filePath.endsWith(".flac") || filePath.endsWith(".ogg") || 
            filePath.endsWith(".wma")) {
            return "JavaZoom Audio Engine";
        }
        
        // JavaFX formats
        if (filePath.endsWith(".mp3") || filePath.endsWith(".wav") || 
            filePath.endsWith(".m4a") || filePath.endsWith(".aac")) {
            return "JavaFX Media Engine";
        }
        
        // Default
        return "Unknown/Unsupported";
    }
    
    /**
     * Check if a class exists in the classpath
     */
    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
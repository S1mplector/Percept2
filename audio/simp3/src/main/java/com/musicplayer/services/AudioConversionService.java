package com.musicplayer.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;

import com.musicplayer.data.models.Song;

/**
 * Service for converting audio files to JavaFX-compatible formats (WAV, AIFF)
 * to enable full feature support including visualizer functionality.
 */
public class AudioConversionService {
    
    private static final Logger LOGGER = Logger.getLogger(AudioConversionService.class.getName());
    
    // Supported input formats for conversion
    private static final List<String> CONVERTIBLE_FORMATS = Arrays.asList(
        "mp3", "flac", "ogg", "opus", "wma", "m4a"
    );
    
    // Conversion tracker to avoid repeated prompts
    private final ConversionTracker conversionTracker;
    
    // Target formats (JavaFX compatible with full feature support)
    public enum TargetFormat {
        WAV("wav", AudioFileFormat.Type.WAVE),
        AIFF("aiff", AudioFileFormat.Type.AIFF);
        
        private final String extension;
        private final AudioFileFormat.Type type;
        
        TargetFormat(String extension, AudioFileFormat.Type type) {
            this.extension = extension;
            this.type = type;
        }
        
        public String getExtension() { return extension; }
        public AudioFileFormat.Type getType() { return type; }
    }
    
    // Conversion settings
    public static class ConversionSettings {
        private TargetFormat targetFormat = TargetFormat.WAV;
        private boolean preserveOriginals = true;
        private boolean autoConvertOnImport = false;
        private String conversionDirectory = null; // null = same directory as original
        private AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
        private float sampleRate = 44100.0f;
        private int sampleSizeInBits = 16;
        private int channels = 2; // stereo
        
        // Getters and setters
        public TargetFormat getTargetFormat() { return targetFormat; }
        public void setTargetFormat(TargetFormat targetFormat) { this.targetFormat = targetFormat; }
        
        public boolean isPreserveOriginals() { return preserveOriginals; }
        public void setPreserveOriginals(boolean preserveOriginals) { this.preserveOriginals = preserveOriginals; }
        
        public boolean isAutoConvertOnImport() { return autoConvertOnImport; }
        public void setAutoConvertOnImport(boolean autoConvertOnImport) { this.autoConvertOnImport = autoConvertOnImport; }
        
        public String getConversionDirectory() { return conversionDirectory; }
        public void setConversionDirectory(String conversionDirectory) { this.conversionDirectory = conversionDirectory; }
        
        public AudioFormat.Encoding getEncoding() { return encoding; }
        public void setEncoding(AudioFormat.Encoding encoding) { this.encoding = encoding; }
        
        public float getSampleRate() { return sampleRate; }
        public void setSampleRate(float sampleRate) { this.sampleRate = sampleRate; }
        
        public int getSampleSizeInBits() { return sampleSizeInBits; }
        public void setSampleSizeInBits(int sampleSizeInBits) { this.sampleSizeInBits = sampleSizeInBits; }
        
        public int getChannels() { return channels; }
        public void setChannels(int channels) { this.channels = channels; }
    }
    
    // Conversion progress callback
    public interface ConversionProgressCallback {
        void onProgress(String fileName, int current, int total, double percentage);
        void onComplete(List<File> convertedFiles, List<String> errors);
        void onError(String fileName, Exception error);
    }
    
    private final ExecutorService conversionExecutor;
    private ConversionSettings settings;
    
    public AudioConversionService() {
        this.settings = new ConversionSettings();
        this.conversionTracker = new ConversionTracker();
        this.conversionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AudioConversion");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Check if a file format can be converted to JavaFX-compatible format.
     */
    public boolean isConvertible(String fileExtension) {
        return CONVERTIBLE_FORMATS.contains(fileExtension.toLowerCase());
    }
    
    /**
     * Check if a file format is already JavaFX-compatible with full features.
     */
    public boolean isJavaFXCompatible(String fileExtension) {
        String ext = fileExtension.toLowerCase();
        return "wav".equals(ext) || "aiff".equals(ext);
    }
    
    /**
     * Convert a single audio file asynchronously.
     */
    public CompletableFuture<File> convertFile(File inputFile, ConversionProgressCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertFileSync(inputFile, callback);
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(inputFile.getName(), e);
                }
                throw new RuntimeException("Conversion failed for: " + inputFile.getName(), e);
            }
        }, conversionExecutor);
    }
    
    /**
     * Convert multiple audio files asynchronously with progress tracking.
     */
    public CompletableFuture<List<File>> convertFiles(List<File> inputFiles, ConversionProgressCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            List<File> convertedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            for (int i = 0; i < inputFiles.size(); i++) {
                File inputFile = inputFiles.get(i);
                try {
                    if (callback != null) {
                        double percentage = ((double) i / inputFiles.size()) * 100;
                        callback.onProgress(inputFile.getName(), i + 1, inputFiles.size(), percentage);
                    }
                    
                    File convertedFile = convertFileSync(inputFile, callback);
                    if (convertedFile != null) {
                        convertedFiles.add(convertedFile);
                    }
                    
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to convert: " + inputFile.getName(), e);
                    errors.add(inputFile.getName() + ": " + e.getMessage());
                    if (callback != null) {
                        callback.onError(inputFile.getName(), e);
                    }
                }
            }
            
            if (callback != null) {
                callback.onComplete(convertedFiles, errors);
            }
            
            return convertedFiles;
        }, conversionExecutor);
    }
    
    /**
     * Convert files with automatic output directory creation (folder + "-converted").
     */
    public void convertFilesWithAutoDirectory(List<File> inputFiles, ConversionProgressCallback callback) {
        if (inputFiles.isEmpty()) {
            callback.onComplete(new ArrayList<>(), new ArrayList<>());
            return;
        }
        
        // Group files by their parent directory
        Map<File, List<File>> filesByDirectory = inputFiles.stream()
            .collect(Collectors.groupingBy(File::getParentFile));
        
        List<File> allConvertedFiles = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        
        for (Map.Entry<File, List<File>> entry : filesByDirectory.entrySet()) {
            File sourceDir = entry.getKey();
            List<File> filesInDir = entry.getValue();
            
            // Create converted directory
            File convertedDir = new File(sourceDir.getParentFile(), sourceDir.getName() + "-converted");
            if (!convertedDir.exists() && !convertedDir.mkdirs()) {
                String error = "Failed to create converted directory: " + convertedDir.getAbsolutePath();
                allErrors.add(error);
                continue;
            }
            
            // Convert files in this directory
            for (File inputFile : filesInDir) {
                try {
                    String outputFileName = getConvertedFileName(inputFile.getName());
                    File outputFile = new File(convertedDir, outputFileName);
                    
                    callback.onProgress(inputFile.getName(), 
                        allConvertedFiles.size() + 1, inputFiles.size(), 
                        (double)(allConvertedFiles.size()) / inputFiles.size() * 100);
                    
                    File convertedFile = convertFileSync(inputFile, callback);
                    if (convertedFile != null) {
                        allConvertedFiles.add(convertedFile);
                    } else {
                        allErrors.add("Failed to convert: " + inputFile.getName());
                    }
                } catch (Exception e) {
                    allErrors.add("Error converting " + inputFile.getName() + ": " + e.getMessage());
                    callback.onError(inputFile.getName(), e);
                }
            }
        }
        
        callback.onComplete(allConvertedFiles, allErrors);
    }
    
    /**
     * Analyze a directory and return statistics about convertible files.
     */
    public ConversionAnalysis analyzeDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return new ConversionAnalysis(0, 0, new ArrayList<>());
        }
        
        List<File> allAudioFiles = new ArrayList<>();
        List<File> convertibleFiles = new ArrayList<>();
        
        scanDirectoryForAudio(directory, allAudioFiles);
        
        for (File file : allAudioFiles) {
            String ext = getFileExtension(file.getName());
            if (isConvertible(ext) && !isJavaFXCompatible(ext)) {
                convertibleFiles.add(file);
            }
        }
        
        // Filter out files from albums that have already been converted
        List<File> unconvertedFiles = conversionTracker.filterUnconvertedFiles(convertibleFiles);
        
        LOGGER.info(String.format("Directory analysis: %d total audio files, %d convertible, %d not yet converted", 
            allAudioFiles.size(), convertibleFiles.size(), unconvertedFiles.size()));
        
        return new ConversionAnalysis(allAudioFiles.size(), unconvertedFiles.size(), unconvertedFiles);
    }
    
    /**
     * Recursively scan directory for audio files.
     */
    private void scanDirectoryForAudio(File directory, List<File> audioFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Skip already converted directories
                if (!file.getName().endsWith("-converted")) {
                    scanDirectoryForAudio(file, audioFiles);
                }
            } else if (file.isFile()) {
                String ext = getFileExtension(file.getName());
                if (isConvertible(ext)) {
                    audioFiles.add(file);
                }
            }
        }
    }
    
    private String getConvertedFileName(String originalName) {
        int lastDot = originalName.lastIndexOf('.');
        String baseName = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
        String targetExt = settings.getTargetFormat().getExtension().toLowerCase();
        return baseName + "." + targetExt;
    }
    
    /**
     * Analysis result for a directory scan.
     */
    public static class ConversionAnalysis {
        private final int totalAudioFiles;
        private final int convertibleFiles;
        private final List<File> filesToConvert;
        private final Map<String, Integer> directoryCounts;
        
        public ConversionAnalysis(int totalAudioFiles, int convertibleFiles, List<File> filesToConvert) {
            this.totalAudioFiles = totalAudioFiles;
            this.convertibleFiles = convertibleFiles;
            this.filesToConvert = filesToConvert;
            this.directoryCounts = calculateDirectoryCounts(filesToConvert);
        }
        
        private Map<String, Integer> calculateDirectoryCounts(List<File> files) {
            Map<String, Integer> counts = new HashMap<>();
            for (File file : files) {
                String parentPath = file.getParent();
                if (parentPath != null) {
                    counts.put(parentPath, counts.getOrDefault(parentPath, 0) + 1);
                }
            }
            return counts;
        }
        
        public int getTotalAudioFiles() { return totalAudioFiles; }
        public int getConvertibleFiles() { return convertibleFiles; }
        public List<File> getFilesToConvert() { return filesToConvert; }
        public Map<String, Integer> getDirectoryCounts() { return directoryCounts; }
        
        public boolean hasConvertibleFiles() { return convertibleFiles > 0; }
        
        public double getConvertiblePercentage() {
            return totalAudioFiles > 0 ? (double) convertibleFiles / totalAudioFiles * 100 : 0;
        }
        
        /**
         * Get a formatted string listing all directories with convertible files.
         */
        public String getDirectoryListString() {
            if (directoryCounts.isEmpty()) {
                return "No directories with convertible files found.";
            }
            
            StringBuilder sb = new StringBuilder();
            directoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    File dir = new File(entry.getKey());
                    String dirName = dir.getName();
                    if (dirName.isEmpty()) {
                        dirName = dir.getAbsolutePath();
                    }
                    sb.append(String.format("• %s (%d files)\n", dirName, entry.getValue()));
                });
            
            return sb.toString().trim();
        }
        
        @Override
        public String toString() {
            return String.format("Total audio files: %d, Convertible: %d (%.1f%%)", 
                totalAudioFiles, convertibleFiles, getConvertiblePercentage());
        }
    }
    
    /**
     * Synchronous file conversion implementation.
     */
    private File convertFileSync(File inputFile, ConversionProgressCallback callback) throws Exception {
        if (!inputFile.exists()) {
            throw new IOException("Input file does not exist: " + inputFile.getAbsolutePath());
        }
        
        String inputExtension = getFileExtension(inputFile.getName());
        if (!isConvertible(inputExtension)) {
            LOGGER.info("File format not convertible, skipping: " + inputFile.getName());
            return null;
        }
        
        if (isJavaFXCompatible(inputExtension)) {
            LOGGER.info("File already JavaFX compatible, skipping: " + inputFile.getName());
            return inputFile; // Return original file
        }
        
        // Determine output file path
        File outputFile = generateOutputFile(inputFile);
        
        // Check if converted file already exists
        if (outputFile.exists()) {
            LOGGER.info("Converted file already exists: " + outputFile.getName());
            return outputFile;
        }
        
        LOGGER.info("Converting: " + inputFile.getName() + " -> " + outputFile.getName());
        
        try {
            // Read the input audio file
            AudioInputStream inputStream = AudioSystem.getAudioInputStream(inputFile);
            AudioFormat inputFormat = inputStream.getFormat();
            
            // Create target format
            AudioFormat targetFormat = new AudioFormat(
                settings.getEncoding(),
                settings.getSampleRate(),
                settings.getSampleSizeInBits(),
                settings.getChannels(),
                (settings.getSampleSizeInBits() / 8) * settings.getChannels(),
                settings.getSampleRate(),
                false // little-endian
            );
            
            // Convert if necessary
            AudioInputStream convertedStream;
            if (!AudioSystem.isConversionSupported(targetFormat, inputFormat)) {
                // Try to find an intermediate format
                AudioFormat intermediateFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    inputFormat.getSampleRate(),
                    16,
                    inputFormat.getChannels(),
                    inputFormat.getChannels() * 2,
                    inputFormat.getSampleRate(),
                    false
                );
                
                if (AudioSystem.isConversionSupported(intermediateFormat, inputFormat)) {
                    AudioInputStream intermediateStream = AudioSystem.getAudioInputStream(intermediateFormat, inputStream);
                    convertedStream = AudioSystem.getAudioInputStream(targetFormat, intermediateStream);
                } else {
                    throw new UnsupportedAudioFileException("Cannot convert from " + inputFormat + " to " + targetFormat);
                }
            } else {
                convertedStream = AudioSystem.getAudioInputStream(targetFormat, inputStream);
            }
            
            // Write the converted audio
            AudioSystem.write(convertedStream, settings.getTargetFormat().getType(), outputFile);
            
            // Copy metadata if possible
            copyMetadata(inputFile, outputFile);
            
            // Clean up
            convertedStream.close();
            inputStream.close();
            
            // Record the conversion in the tracker
            String originalFormat = getFileExtension(inputFile.getName());
            String convertedFormat = settings.getTargetFormat().getExtension();
            conversionTracker.recordConversion(inputFile, outputFile, originalFormat, convertedFormat);
            
            LOGGER.info("Successfully converted: " + outputFile.getName());
            return outputFile;
            
        } catch (Exception e) {
            // Clean up failed conversion file
            if (outputFile.exists()) {
                outputFile.delete();
            }
            throw e;
        }
    }
    
    /**
     * Generate output file path based on conversion settings.
     */
    private File generateOutputFile(File inputFile) {
        String baseName = getFileNameWithoutExtension(inputFile.getName());
        String targetExtension = settings.getTargetFormat().getExtension();
        
        Path outputDir;
        if (settings.getConversionDirectory() != null) {
            outputDir = Paths.get(settings.getConversionDirectory());
        } else {
            outputDir = inputFile.getParentFile().toPath();
        }
        
        // Ensure output directory exists
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOGGER.warning("Could not create output directory: " + outputDir);
        }
        
        String outputFileName = baseName + "_converted." + targetExtension;
        return outputDir.resolve(outputFileName).toFile();
    }
    
    /**
     * Copy metadata from original file to converted file using JAudioTagger.
     */
    private void copyMetadata(File sourceFile, File targetFile) {
        try {
            AudioFile sourceAudioFile = AudioFileIO.read(sourceFile);
            Tag sourceTag = sourceAudioFile.getTag();

            if (sourceTag == null) {
                LOGGER.fine("No metadata found in source file " + sourceFile.getName());
                return;
            }

            AudioFile targetAudioFile = AudioFileIO.read(targetFile);
            Tag targetTag = targetAudioFile.getTagOrCreateAndSetDefault();

            // Copy commonly used fields one by one to avoid unsupported tag exceptions
            FieldKey[] keysToCopy = {
                FieldKey.TITLE,
                FieldKey.ARTIST,
                FieldKey.ALBUM,
                FieldKey.ALBUM_ARTIST,
                FieldKey.GENRE,
                FieldKey.YEAR,
                FieldKey.TRACK
            };

            for (FieldKey key : keysToCopy) {
                try {
                    String value = sourceTag.getFirst(key);
                    if (value != null && !value.isBlank()) {
                        targetTag.setField(key, value);
                    }
                } catch (Exception ex) {
                    // Some keys may not be supported for the target format – ignore
                }
            }

            targetAudioFile.commit();
            LOGGER.fine("Metadata copied from " + sourceFile.getName() + " to " + targetFile.getName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to copy metadata: " + e.getMessage(), e);
            // Non-fatal error, continue
        }
    }
    
    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
    
    /**
     * Get filename without extension.
     */
    private String getFileNameWithoutExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
    
    // Settings management
    public ConversionSettings getSettings() {
        return settings;
    }
    
    public void setSettings(ConversionSettings settings) {
        this.settings = settings;
    }
    
    /**
     * Get the conversion tracker for direct access.
     */
    public ConversionTracker getConversionTracker() {
        return conversionTracker;
    }
    
    /**
     * Check if an album has already been converted.
     */
    public boolean isAlbumConverted(File file) {
        return conversionTracker.isAlbumConverted(file);
    }
    
    /**
     * Force re-conversion by removing the conversion record.
     */
    public void forceReconversion(File file) {
        // This would require implementing a way to identify the album from a file
        // For now, we'll add this as a placeholder for future enhancement
        LOGGER.info("Force re-conversion requested for: " + file.getName());
    }
    
    /**
     * Shutdown the conversion service and cleanup resources.
     */
    public void shutdown() {
        conversionExecutor.shutdown();
    }
}

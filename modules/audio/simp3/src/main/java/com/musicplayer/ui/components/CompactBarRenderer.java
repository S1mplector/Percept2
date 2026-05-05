package com.musicplayer.ui.components;

import com.musicplayer.data.models.Settings;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Renders a compact horizontal bar spectrum visualization for the mini player.
 * Displays audio frequency data as horizontal bars optimized for 80x80 pixel constraint.
 */
public class CompactBarRenderer {
    
    private final VisualizerConfig config;
    private double[] smoothedData;
    private double[] peakValues;
    private double[] peakVelocities;
    private final int SPECTRUM_BANDS = 64;
    
    // Compact settings for mini player
    private static final int COMPACT_BAR_COUNT = 16; // Fewer bars for compact view
    private static final double PEAK_FALL_SPEED = 0.15;
    private static final double PEAK_HANG_TIME = 0.92;
    
    // Color animation
    private double currentHue = 120; // Start with green
    private static final double HUE_SHIFT_SPEED = 0.5; // Slower for mini player
    private Settings settings;
    
    public CompactBarRenderer(VisualizerConfig config) {
        this(config, null);
    }
    
    public CompactBarRenderer(VisualizerConfig config, Settings settings) {
        this.config = config;
        this.settings = settings;
        // Override bar count for compact view
        this.smoothedData = new double[COMPACT_BAR_COUNT];
        this.peakValues = new double[COMPACT_BAR_COUNT];
        this.peakVelocities = new double[COMPACT_BAR_COUNT];
        
        // Initialize arrays
        for (int i = 0; i < COMPACT_BAR_COUNT; i++) {
            smoothedData[i] = 0.0;
            peakValues[i] = 0.0;
            peakVelocities[i] = 0.0;
        }
    }
    
    /**
     * Render the compact bar spectrum visualization.
     * @param gc Graphics context to draw on
     * @param spectrum Raw spectrum data (64 bands)
     * @param width Canvas width (typically 80)
     * @param height Canvas height (typically 80)
     */
    public void render(GraphicsContext gc, double[] spectrum, double width, double height) {
        // Clear canvas
        gc.clearRect(0, 0, width, height);
        
        // Semi-transparent background for better visibility
        gc.setFill(Color.rgb(20, 20, 20, 0.3));
        gc.fillRect(0, 0, width, height);
        
        // Map spectrum data to compact bar count
        double[] mappedData = mapSpectrumData(spectrum);
        
        // Apply smoothing
        smoothData(mappedData);
        
        // Update peaks
        updatePeaks();
        
        // Update color based on settings
        Color baseColor;
        if (settings != null && settings.getVisualizerColorMode() == Settings.VisualizerColorMode.SOLID_COLOR) {
            // Use solid color from settings
            try {
                baseColor = Color.web(settings.getVisualizerSolidColor());
            } catch (Exception e) {
                // Fallback to default if color parsing fails
                baseColor = Color.web("#32CD32"); // Lime green
            }
        } else {
            // Use gradient cycling (default)
            currentHue += HUE_SHIFT_SPEED;
            if (currentHue >= 360) {
                currentHue -= 360;
            }
            baseColor = Color.hsb(currentHue, 0.8, 1.0);
        }
        
        // Calculate bar dimensions
        double barWidth = width / COMPACT_BAR_COUNT;
        double actualBarWidth = barWidth * 0.8; // 80% bar, 20% spacing
        double spacing = barWidth * 0.1; // Half spacing on each side
        
        // Apply subtle glow effect
        if (config.isGlowEffect()) {
            DropShadow glow = new DropShadow();
            glow.setColor(baseColor);
            glow.setRadius(5); // Smaller glow for compact view
            glow.setSpread(0.2);
            gc.setEffect(glow);
        }
        
        // Draw bars
        for (int i = 0; i < COMPACT_BAR_COUNT; i++) {
            double magnitude = smoothedData[i];
            double peak = peakValues[i];
            
            // Calculate bar height (vertical bars in compact view)
            double barHeight = magnitude * height * 0.8; // Leave 20% margin
            double peakY = height - (peak * height * 0.8);
            
            double x = i * barWidth + spacing;
            double y = height - barHeight;
            
            // Create gradient for bars
            LinearGradient gradient = new LinearGradient(
                0, y, 0, height,
                false, null,
                new Stop(0, baseColor.brighter()),
                new Stop(0.5, baseColor),
                new Stop(1, baseColor.darker().darker())
            );
            
            // Draw main bar
            gc.setFill(gradient);
            gc.fillRect(x, y, actualBarWidth, barHeight);
            
            // Draw peak cap
            if (peak > magnitude && peakY < height - 2) {
                gc.setFill(Color.rgb(255, 255, 255, 0.9));
                gc.fillRect(x, peakY, actualBarWidth, 2); // 2 pixel tall peak cap
            }
        }
        
        // Remove effect
        if (config.isGlowEffect()) {
            gc.setEffect(null);
        }
        
        // Draw subtle border
        gc.setStroke(Color.rgb(255, 255, 255, 0.1));
        gc.setLineWidth(1);
        gc.strokeRect(0.5, 0.5, width - 1, height - 1);
    }
    
    /**
     * Map spectrum data from 64 bands to compact bar count.
     */
    private double[] mapSpectrumData(double[] spectrum) {
        double[] mapped = new double[COMPACT_BAR_COUNT];
        
        if (spectrum == null || spectrum.length == 0) {
            return mapped;
        }
        
        // Calculate how many spectrum bands per visual bar
        double bandsPerBar = (double) SPECTRUM_BANDS / COMPACT_BAR_COUNT;
        
        for (int i = 0; i < COMPACT_BAR_COUNT; i++) {
            double sum = 0.0;
            int count = 0;
            
            int startBand = (int) (i * bandsPerBar);
            int endBand = (int) ((i + 1) * bandsPerBar);
            
            // Apply logarithmic scaling to emphasize lower frequencies
            double freqWeight = 1.0 - (i / (double) COMPACT_BAR_COUNT) * 0.5;
            
            for (int j = startBand; j < endBand && j < spectrum.length; j++) {
                // The spectrum data is in dB (typically -60 to 0)
                // Convert to a normalized 0-1 range
                double dbValue = spectrum[j];
                double normalizedValue = (dbValue + 60.0) / 60.0; // Map -60..0 to 0..1
                normalizedValue = Math.max(0.0, Math.min(1.0, normalizedValue));
                
                // Apply non-linear scaling for better visual effect
                normalizedValue = Math.pow(normalizedValue, 0.7); // Make quieter sounds more visible
                
                sum += normalizedValue * freqWeight;
                count++;
            }
            
            if (count > 0) {
                // Average the values
                mapped[i] = sum / count;
            }
        }
        
        return mapped;
    }
    
    /**
     * Apply exponential smoothing to reduce jitter.
     */
    private void smoothData(double[] data) {
        for (int i = 0; i < data.length && i < smoothedData.length; i++) {
            smoothedData[i] = smoothedData[i] * config.getSmoothingFactor() + 
                             data[i] * (1.0 - config.getSmoothingFactor());
            
            // Update peaks
            if (smoothedData[i] > peakValues[i]) {
                peakValues[i] = smoothedData[i];
                peakVelocities[i] = 0.0;
            }
        }
    }
    
    /**
     * Update peak positions with gravity effect.
     */
    private void updatePeaks() {
        for (int i = 0; i < COMPACT_BAR_COUNT; i++) {
            if (peakValues[i] > smoothedData[i]) {
                // Apply gravity
                peakVelocities[i] += PEAK_FALL_SPEED;
                
                // Apply hang time
                double effectiveVelocity = peakVelocities[i] * (1.0 - PEAK_HANG_TIME);
                
                // Update peak position
                peakValues[i] -= effectiveVelocity * 0.01; // Slower fall for compact view
                
                // Don't let peak go below current bar
                if (peakValues[i] < smoothedData[i]) {
                    peakValues[i] = smoothedData[i];
                    peakVelocities[i] = 0.0;
                }
            }
        }
    }
    
    /**
     * Reset the visualizer state.
     */
    public void reset() {
        for (int i = 0; i < COMPACT_BAR_COUNT; i++) {
            smoothedData[i] = 0.0;
            peakValues[i] = 0.0;
            peakVelocities[i] = 0.0;
        }
        currentHue = 120; // Reset to green
    }
    
    /**
     * Check if the renderer supports the given audio format.
     * @param audioFormat The audio format (e.g., "mp3", "m4a", "flac")
     * @return true if the format is supported for visualization
     */
    public boolean supportsFormat(String audioFormat) {
        if (audioFormat == null) {
            return false;
        }
        
        String format = audioFormat.toLowerCase();
        // JavaFX MediaPlayer only provides spectrum data for MP3 and M4A
        return format.equals("mp3") || format.equals("m4a") ||
               format.equals("mp4") || format.equals("aac");
    }
    
    /**
     * Update the settings for color configuration.
     */
    public void updateSettings(Settings settings) {
        this.settings = settings;
    }
}
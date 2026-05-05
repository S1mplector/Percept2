package com.musicplayer.ui.components;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.effect.Glow;

/**
 * Enhanced spectrum bar visualizer with elastic animations, smooth interpolation, and variable bar widths.
 */
public class SpectrumVisualizer extends BaseVisualizer {
    
    // Peak values for each band
    private final float[] peakValues;
    // Peak fall velocities
    private final float[] peakVelocities;
    
    // Enhanced animation properties
    private final float[] targetMagnitudes;     // Target values for smooth interpolation
    private final float[] currentMagnitudes;    // Current animated values
    private final float[] barVelocities;        // Velocities for elastic animation
    private final float[] barWidthMultipliers;  // Dynamic width multipliers
    private final float[] barWidthVelocities;   // Velocities for width animation
    
    // Beat detection and glow effects
    private final float[] peakGlowIntensity;    // Glow intensity for each peak
    private final float[] peakGlowDecay;        // Decay rate for peak glow
    private final float[] bassHistory;          // History for beat detection
    private int bassHistoryIndex = 0;
    private double lastBeatTime = 0;
    private double beatFlashIntensity = 0;
    
    // Animation constants
    private static final double PEAK_FALL_SPEED = 0.15;
    private static final double PEAK_HANG_TIME = 0.92;
    private static final double BAR_SPRING_STRENGTH = 0.25;    // How strong the elastic effect is
    private static final double BAR_DAMPING = 0.85;            // Damping factor for oscillation
    private static final double INTERPOLATION_SPEED = 0.18;    // Speed of smooth interpolation
    private static final double WIDTH_SPRING_STRENGTH = 0.15;  // Spring strength for width changes
    private static final double WIDTH_DAMPING = 0.88;          // Damping for width oscillation
    private static final double MIN_WIDTH_MULTIPLIER = 0.6;    // Minimum width multiplier
    private static final double MAX_WIDTH_MULTIPLIER = 1.4;    // Maximum width multiplier
    
    // Beat detection and glow constants
    private static final double BEAT_DETECTION_THRESHOLD = 1.5; // Threshold for beat detection
    private static final double BEAT_COOLDOWN = 0.2;           // Minimum time between beats (seconds)
    private static final int BASS_HISTORY_SIZE = 10;           // Number of bass samples to track
    private static final double PEAK_GLOW_DECAY_RATE = 0.92;   // How fast peak glow fades
    private static final double BEAT_FLASH_DECAY = 0.88;       // How fast beat flash fades
    private static final double MAX_GLOW_INTENSITY = 1.0;      // Maximum glow intensity
    
    private final Color peakColor = Color.rgb(255, 255, 255, 0.9);

    public SpectrumVisualizer(int numBands) {
        super(numBands);
        this.peakValues = new float[numBands];
        this.peakVelocities = new float[numBands];
        
        // Initialize enhanced animation arrays
        this.targetMagnitudes = new float[numBands];
        this.currentMagnitudes = new float[numBands];
        this.barVelocities = new float[numBands];
        this.barWidthMultipliers = new float[numBands];
        this.barWidthVelocities = new float[numBands];
        
        // Initialize beat detection and glow arrays
        this.peakGlowIntensity = new float[numBands];
        this.peakGlowDecay = new float[numBands];
        this.bassHistory = new float[BASS_HISTORY_SIZE];
        
        // Initialize all arrays
        for (int i = 0; i < numBands; i++) {
            peakValues[i] = -60.0f;
            peakVelocities[i] = 0.0f;
            targetMagnitudes[i] = -60.0f;
            currentMagnitudes[i] = -60.0f;
            barVelocities[i] = 0.0f;
            barWidthMultipliers[i] = 1.0f;
            barWidthVelocities[i] = 0.0f;
            peakGlowIntensity[i] = 0.0f;
            peakGlowDecay[i] = 0.0f;
        }
        
        // Initialize bass history
        for (int i = 0; i < BASS_HISTORY_SIZE; i++) {
            bassHistory[i] = -60.0f;
        }
    }

    @Override
    protected void updateSpecificData(float[] magnitudes) {
        int len = Math.min(magnitudes.length, numBands);
        
        for (int i = 0; i < len; i++) {
            // Update target magnitudes for smooth interpolation
            targetMagnitudes[i] = displayMagnitudes[i];
            
            // Update peaks and trigger glow effects
            if (displayMagnitudes[i] > peakValues[i]) {
                peakValues[i] = displayMagnitudes[i];
                peakVelocities[i] = 0.0f;
                
                // Trigger peak glow effect - calculate normalized magnitude for glow strength
                float normalizedMag = (60 + displayMagnitudes[i]) / 60.0f;
                normalizedMag = Math.max(0, Math.min(1, normalizedMag));
                float glowStrength = Math.min(1.0f, normalizedMag * 1.5f);
                peakGlowIntensity[i] = (float)MAX_GLOW_INTENSITY * glowStrength;
                peakGlowDecay[i] = (float)PEAK_GLOW_DECAY_RATE;
            }
            
            // Calculate target width multiplier based on magnitude intensity
            float normalizedMag = (60 + displayMagnitudes[i]) / 60.0f;
            normalizedMag = Math.max(0, Math.min(1, normalizedMag));
            
            // Higher magnitudes get wider bars
            float targetWidth = (float)(MIN_WIDTH_MULTIPLIER + 
                (MAX_WIDTH_MULTIPLIER - MIN_WIDTH_MULTIPLIER) * Math.pow(normalizedMag, 0.7));
            
            // Apply spring force for width animation
            float widthDiff = targetWidth - barWidthMultipliers[i];
            barWidthVelocities[i] += widthDiff * WIDTH_SPRING_STRENGTH;
        }
        
        // Beat detection using bass frequencies (first few bands)
        detectBeat();
    }
    
    /**
     * Simple beat detection algorithm based on bass frequency energy
     */
    private void detectBeat() {
        // Calculate average bass energy (first 3-4 bands typically represent bass)
        float bassEnergy = 0;
        int bassCount = Math.min(4, numBands);
        for (int i = 0; i < bassCount; i++) {
            float normalized = (60 + displayMagnitudes[i]) / 60.0f;
            bassEnergy += Math.max(0, normalized);
        }
        bassEnergy /= bassCount;
        
        // Store in circular buffer
        bassHistory[bassHistoryIndex] = bassEnergy;
        bassHistoryIndex = (bassHistoryIndex + 1) % BASS_HISTORY_SIZE;
        
        // Calculate average of recent history
        float avgBass = 0;
        for (float value : bassHistory) {
            avgBass += value;
        }
        avgBass /= BASS_HISTORY_SIZE;
        
        // Check for beat (current energy significantly higher than recent average)
        double currentTime = System.currentTimeMillis() / 1000.0;
        if (bassEnergy > avgBass * BEAT_DETECTION_THRESHOLD && 
            currentTime - lastBeatTime > BEAT_COOLDOWN) {
            
            lastBeatTime = currentTime;
            beatFlashIntensity = 1.0; // Trigger beat flash effect
            
            // Enhance peak glows on beat
            for (int i = 0; i < numBands; i++) {
                peakGlowIntensity[i] = Math.max(peakGlowIntensity[i], 0.8f);
            }
        }
    }

    @Override
    protected boolean performAnimationUpdate(double deltaTime) {
        boolean needsRedraw = false;
        
        for (int i = 0; i < numBands; i++) {
            // 1. Smooth interpolation for bar heights
            float heightDiff = targetMagnitudes[i] - currentMagnitudes[i];
            if (Math.abs(heightDiff) > 0.1f) {
                // Apply spring physics for elastic animation
                barVelocities[i] += heightDiff * BAR_SPRING_STRENGTH;
                barVelocities[i] *= BAR_DAMPING; // Apply damping
                
                currentMagnitudes[i] += barVelocities[i];
                
                // Prevent overshooting for very small differences
                if (Math.abs(heightDiff) < 1.0f && Math.abs(barVelocities[i]) < 0.5f) {
                    currentMagnitudes[i] += heightDiff * INTERPOLATION_SPEED;
                }
                
                needsRedraw = true;
            }
            
            // 2. Animate bar widths with elastic effect
            barWidthVelocities[i] *= WIDTH_DAMPING;
            barWidthMultipliers[i] += barWidthVelocities[i];
            
            // Clamp width multipliers
            barWidthMultipliers[i] = Math.max((float)MIN_WIDTH_MULTIPLIER, 
                Math.min((float)MAX_WIDTH_MULTIPLIER, barWidthMultipliers[i]));
            
            if (Math.abs(barWidthVelocities[i]) > 0.001f) {
                needsRedraw = true;
            }
            
            // 3. Update peak glow effects
            if (peakGlowIntensity[i] > 0.01f) {
                peakGlowIntensity[i] *= peakGlowDecay[i];
                needsRedraw = true;
            }
            
            // 4. Update peak positions with original logic
            if (peakValues[i] > currentMagnitudes[i]) {
                peakVelocities[i] += PEAK_FALL_SPEED;
                float effectiveVelocity = peakVelocities[i] * (1.0f - (float)PEAK_HANG_TIME);
                peakValues[i] -= effectiveVelocity;
                
                if (peakValues[i] < currentMagnitudes[i]) {
                    peakValues[i] = currentMagnitudes[i];
                    peakVelocities[i] = 0.0f;
                }
                needsRedraw = true;
            }
        }
        
        // Update beat flash effect
        if (beatFlashIntensity > 0.01) {
            beatFlashIntensity *= BEAT_FLASH_DECAY;
            needsRedraw = true;
        }
        
        return needsRedraw;
    }

    @Override
    protected void drawVisualization(GraphicsContext gc, double width, double height, Color baseColor) {
        double bandWidth = width / numBands;
        double baseBarWidth = bandWidth * 0.8;

        for (int i = 0; i < numBands; i++) {
            // Use animated current magnitudes instead of display magnitudes
            float magnitude = currentMagnitudes[i];
            float peak = peakValues[i];
            
            // Normalize values (assuming dB range of -60 to 0)
            double normalizedMag = (60 + magnitude) / 60.0;
            double normalizedPeak = (60 + peak) / 60.0;
            normalizedMag = Math.max(0, Math.min(1, normalizedMag));
            normalizedPeak = Math.max(0, Math.min(1, normalizedPeak));

            // Calculate dynamic bar dimensions
            double dynamicBarWidth = baseBarWidth * barWidthMultipliers[i];
            double barHeight = normalizedMag * height * 0.9;
            double peakY = height - (normalizedPeak * height * 0.9);
            
            // Center the bar horizontally within its band
            double x = i * bandWidth + (bandWidth - dynamicBarWidth) / 2;
            double y = height - barHeight;

            // Enhanced gradient with more vibrant colors for active bars
            Color topColor = baseColor.brighter().brighter();
            Color midColor = baseColor;
            Color bottomColor = baseColor.darker();
            
            // Make bars more vibrant when they're wider (more active)
            double intensity = (barWidthMultipliers[i] - MIN_WIDTH_MULTIPLIER) / 
                              (MAX_WIDTH_MULTIPLIER - MIN_WIDTH_MULTIPLIER);
            topColor = topColor.interpolate(Color.WHITE, intensity * 0.3);
            
            LinearGradient gradient = new LinearGradient(
                0, y, 0, height,
                false, null,
                new Stop(0, topColor),
                new Stop(0.4, midColor),
                new Stop(1, bottomColor)
            );
            
            // Draw main bar with enhanced gradient and dynamic width
            gc.setFill(gradient);
            gc.fillRect(x, y, dynamicBarWidth, barHeight);
            
            // Draw enhanced peak cap with dynamic width and glow effects
            if (normalizedPeak > normalizedMag && peakY < height - 2) {
                double peakWidth = dynamicBarWidth * 1.1;
                double peakX = x - (peakWidth - dynamicBarWidth) / 2;
                
                // Calculate glow color based on peak glow intensity
                float glowIntensity = peakGlowIntensity[i];
                Color glowPeakColor = peakColor;
                
                if (glowIntensity > 0.1f) {
                    // Create glowing effect
                    Color glowColor = baseColor.brighter().brighter();
                    glowPeakColor = peakColor.interpolate(glowColor, glowIntensity * 0.8);
                    
                    // Add glow effect
                    Glow glow = new Glow();
                    glow.setLevel(glowIntensity * 0.7);
                    gc.setEffect(glow);
                    
                    // Draw outer glow
                    gc.setFill(glowColor.deriveColor(0, 1, 1, glowIntensity * 0.3));
                    gc.fillRect(peakX - 2, peakY - 2, peakWidth + 4, 6);
                }
                
                // Beat flash effect - make peaks extra bright during beats
                if (beatFlashIntensity > 0.1) {
                    Color flashColor = Color.WHITE.interpolate(baseColor.brighter(), 0.3);
                    glowPeakColor = glowPeakColor.interpolate(flashColor, beatFlashIntensity * 0.5);
                }
                
                // Draw main peak cap
                gc.setFill(glowPeakColor);
                gc.fillRect(peakX, peakY - 1, peakWidth, 4);
                
                // Reset effects
                gc.setEffect(null);
            }
        }
        
        gc.setEffect(null);
    }

    @Override
    protected void clearSpecificData() {
        // Clear all animation data
        for (int i = 0; i < numBands; i++) {
            peakValues[i] = -60.0f;
            peakVelocities[i] = 0.0f;
            targetMagnitudes[i] = -60.0f;
            currentMagnitudes[i] = -60.0f;
            barVelocities[i] = 0.0f;
            barWidthMultipliers[i] = 1.0f;
            barWidthVelocities[i] = 0.0f;
            peakGlowIntensity[i] = 0.0f;
            peakGlowDecay[i] = 0.0f;
        }
        
        // Clear beat detection data
        for (int i = 0; i < BASS_HISTORY_SIZE; i++) {
            bassHistory[i] = -60.0f;
        }
        bassHistoryIndex = 0;
        lastBeatTime = 0;
        beatFlashIntensity = 0;
    }
}

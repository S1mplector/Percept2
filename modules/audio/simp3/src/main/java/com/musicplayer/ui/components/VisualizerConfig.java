package com.musicplayer.ui.components;

import javafx.scene.paint.Color;

/**
 * Configuration class for the audio visualizer.
 * Contains all customizable parameters for the visualizer appearance and behavior.
 */
public class VisualizerConfig {
    
    // Visual parameters
    private int barCount = 32;
    private double minBarHeight = 5.0;
    private double maxBarHeight = 30.0;
    private double smoothingFactor = 0.8;
    
    // Color scheme
    private Color primaryColor = Color.web("#4CAF50"); // Green
    private Color secondaryColor = Color.web("#81C784"); // Light green
    
    // Effects
    private boolean glowEffect = true;
    private double glowRadius = 8.0;
    
    // Animation
    private double rotationSpeed = 0.5; // Degrees per frame
    private boolean enableRotation = true;
    
    // Performance
    private int targetFPS = 60;
    
    // Constructor
    public VisualizerConfig() {
        // Default configuration
    }
    
    // Getters and setters
    public int getBarCount() {
        return barCount;
    }
    
    public void setBarCount(int barCount) {
        this.barCount = barCount;
    }
    
    public double getMinBarHeight() {
        return minBarHeight;
    }
    
    public void setMinBarHeight(double minBarHeight) {
        this.minBarHeight = minBarHeight;
    }
    
    public double getMaxBarHeight() {
        return maxBarHeight;
    }
    
    public void setMaxBarHeight(double maxBarHeight) {
        this.maxBarHeight = maxBarHeight;
    }
    
    public double getSmoothingFactor() {
        return smoothingFactor;
    }
    
    public void setSmoothingFactor(double smoothingFactor) {
        this.smoothingFactor = Math.max(0.0, Math.min(1.0, smoothingFactor));
    }
    
    public Color getPrimaryColor() {
        return primaryColor;
    }
    
    public void setPrimaryColor(Color primaryColor) {
        this.primaryColor = primaryColor;
    }
    
    public Color getSecondaryColor() {
        return secondaryColor;
    }
    
    public void setSecondaryColor(Color secondaryColor) {
        this.secondaryColor = secondaryColor;
    }
    
    public boolean isGlowEffect() {
        return glowEffect;
    }
    
    public void setGlowEffect(boolean glowEffect) {
        this.glowEffect = glowEffect;
    }
    
    public double getGlowRadius() {
        return glowRadius;
    }
    
    public void setGlowRadius(double glowRadius) {
        this.glowRadius = glowRadius;
    }
    
    public double getRotationSpeed() {
        return rotationSpeed;
    }
    
    public void setRotationSpeed(double rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }
    
    public boolean isEnableRotation() {
        return enableRotation;
    }
    
    public void setEnableRotation(boolean enableRotation) {
        this.enableRotation = enableRotation;
    }
    
    public int getTargetFPS() {
        return targetFPS;
    }
    
    public void setTargetFPS(int targetFPS) {
        this.targetFPS = targetFPS;
    }
    
    /**
     * Create a gradient color between primary and secondary colors.
     * @param position Value between 0.0 and 1.0
     * @return Interpolated color
     */
    public Color getGradientColor(double position) {
        position = Math.max(0.0, Math.min(1.0, position));
        return primaryColor.interpolate(secondaryColor, position);
    }
}
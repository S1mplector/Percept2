package com.musicplayer.ui.components;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.paint.Color;

/**
 * Manager class for handling different types of audio visualizers.
 * Provides a unified interface for creating and managing spectrum, waveform, and combined visualizers.
 */
public class VisualizerManager {
    
    public enum VisualizationType {
        SPECTRUM_BARS,
        WAVEFORM,
        COMBINED
    }
    
    private BaseVisualizer currentVisualizer;
    private VisualizationType currentType;
    private final int numBands;
    
    // Settings that need to be preserved across visualizer switches
    private boolean gradientCyclingEnabled = true;
    private Color solidColor = Color.LIMEGREEN;
    private boolean enabled = true;

    public VisualizerManager(int numBands) {
        this.numBands = numBands;
        this.currentType = VisualizationType.SPECTRUM_BARS;
        this.currentVisualizer = createVisualizer(currentType);
        applySettings();
    }

    /**
     * Set the visualization type and switch to the appropriate visualizer.
     * @param type The visualization type to switch to
     */
    public void setVisualizationType(VisualizationType type) {
        if (this.currentType == type) {
            return; // No change needed
        }
        
        // Stop current visualizer
        if (currentVisualizer != null) {
            currentVisualizer.stop();
        }
        
        // Create new visualizer
        this.currentType = type;
        this.currentVisualizer = createVisualizer(type);
        applySettings();
    }
    
    /**
     * Get the current visualization type.
     * @return The current visualization type
     */
    public VisualizationType getVisualizationType() {
        return currentType;
    }
    
    /**
     * Get the current active visualizer.
     * @return The current visualizer instance
     */
    public BaseVisualizer getCurrentVisualizer() {
        return currentVisualizer;
    }
    
    /**
     * Update the visualizer with new magnitude data.
     * @param magnitudes The audio magnitude data
     */
    public void update(float[] magnitudes) {
        if (currentVisualizer != null) {
            currentVisualizer.update(magnitudes);
        }
    }
    
    /**
     * Set whether the visualizer is enabled.
     * @param enabled True to enable visualization, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (currentVisualizer != null) {
            currentVisualizer.setEnabled(enabled);
        }
    }
    
    /**
     * Set whether gradient cycling is enabled.
     * @param enabled True for gradient cycling, false for solid color
     */
    public void setGradientCyclingEnabled(boolean enabled) {
        this.gradientCyclingEnabled = enabled;
        if (currentVisualizer != null) {
            currentVisualizer.setGradientCyclingEnabled(enabled);
        }
    }
    
    /**
     * Set the solid color to use when gradient cycling is disabled.
     * @param color The color to use
     */
    public void setSolidColor(Color color) {
        this.solidColor = color;
        if (currentVisualizer != null) {
            currentVisualizer.setSolidColor(color);
        }
    }
    
    /**
     * Pause the visualizer animation.
     */
    public void pause() {
        if (currentVisualizer != null) {
            currentVisualizer.pause();
        }
    }
    
    /**
     * Resume the visualizer animation.
     */
    public void resume() {
        if (currentVisualizer != null) {
            currentVisualizer.resume();
        }
    }
    
    /**
     * Force refresh the visualizer.
     */
    public void forceRefresh() {
        if (currentVisualizer != null) {
            currentVisualizer.forceRefresh();
        }
    }
    
    /**
     * Stop and clean up the current visualizer.
     */
    public void stop() {
        if (currentVisualizer != null) {
            currentVisualizer.stop();
            currentVisualizer = null;
        }
    }
    
    /**
     * Get the width of the current visualizer.
     * @return The width
     */
    public double getWidth() {
        return currentVisualizer != null ? currentVisualizer.getWidth() : 0;
    }
    
    /**
     * Get the height of the current visualizer.
     * @return The height
     */
    public double getHeight() {
        return currentVisualizer != null ? currentVisualizer.getHeight() : 0;
    }
    
    /**
     * Set the size of the current visualizer.
     * @param width The width
     * @param height The height
     */
    public void setSize(double width, double height) {
        if (currentVisualizer != null) {
            currentVisualizer.setWidth(width);
            currentVisualizer.setHeight(height);
        }
    }
    
    /**
     * Bind the visualizer's size to another node's size.
     * @param widthProperty The width property to bind to
     * @param heightProperty The height property to bind to
     */
    public void bindSize(ReadOnlyDoubleProperty widthProperty, 
                        ReadOnlyDoubleProperty heightProperty) {
        if (currentVisualizer != null) {
            currentVisualizer.widthProperty().bind(widthProperty);
            currentVisualizer.heightProperty().bind(heightProperty);
        }
    }
    
    /**
     * Set mouse transparency for the current visualizer.
     * @param mouseTransparent True to make mouse transparent
     */
    public void setMouseTransparent(boolean mouseTransparent) {
        if (currentVisualizer != null) {
            currentVisualizer.setMouseTransparent(mouseTransparent);
        }
    }
    
    /**
     * Set visibility for the current visualizer.
     * @param visible True to make visible
     */
    public void setVisible(boolean visible) {
        if (currentVisualizer != null) {
            currentVisualizer.setVisible(visible);
        }
    }
    
    private BaseVisualizer createVisualizer(VisualizationType type) {
        return switch (type) {
            case SPECTRUM_BARS -> new SpectrumVisualizer(numBands);
            case WAVEFORM -> new WaveformVisualizer(numBands);
            case COMBINED -> new CombinedVisualizer(numBands);
        };
    }
    
    private void applySettings() {
        if (currentVisualizer != null) {
            currentVisualizer.setEnabled(enabled);
            currentVisualizer.setGradientCyclingEnabled(gradientCyclingEnabled);
            currentVisualizer.setSolidColor(solidColor);
        }
    }
}

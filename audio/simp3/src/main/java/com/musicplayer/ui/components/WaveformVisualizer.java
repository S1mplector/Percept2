package com.musicplayer.ui.components;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Waveform visualizer that displays audio data as a continuous oscillating line.
 */
public class WaveformVisualizer extends BaseVisualizer {
    
    // Waveform data
    private final float[] waveformData;
    private final float[] smoothedWaveform;
    private static final int WAVEFORM_SAMPLES = 512; // Number of waveform samples to display

    public WaveformVisualizer(int numBands) {
        super(numBands);
        
        // Initialize waveform arrays
        this.waveformData = new float[WAVEFORM_SAMPLES];
        this.smoothedWaveform = new float[WAVEFORM_SAMPLES];
        
        // Initialize waveform data
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            waveformData[i] = 0.0f;
            smoothedWaveform[i] = 0.0f;
        }
    }

    @Override
    protected void updateSpecificData(float[] magnitudes) {
        updateWaveformData(magnitudes);
    }

    @Override
    protected boolean performAnimationUpdate(double deltaTime) {
        // Waveform doesn't need animation updates like spectrum peaks
        return false;
    }

    /**
     * Update waveform data by converting spectrum data to waveform-like visualization.
     * This creates a pseudo-waveform from frequency domain data.
     */
    private void updateWaveformData(float[] magnitudes) {
        // Convert spectrum magnitudes to waveform-like data
        // We'll create a circular buffer effect by shifting existing data
        System.arraycopy(waveformData, 1, waveformData, 0, WAVEFORM_SAMPLES - 1);
        
        // Add new data point - use average of all frequency bands as amplitude
        float amplitude = 0.0f;
        for (float magnitude : magnitudes) {
            amplitude += magnitude;
        }
        amplitude /= magnitudes.length;
        
        // Normalize and store
        waveformData[WAVEFORM_SAMPLES - 1] = Math.max(-60.0f, Math.min(0.0f, amplitude));
        
        // Apply smoothing to waveform
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            smoothedWaveform[i] = (float) (0.8 * smoothedWaveform[i] + 0.2 * waveformData[i]);
        }
    }

    @Override
    protected void drawVisualization(GraphicsContext gc, double width, double height, Color baseColor) {
        // Draw waveform as a continuous line
        gc.setStroke(baseColor);
        gc.setLineWidth(2.0);
        
        double baselineY = height; // Baseline at the bottom of the canvas
        double xStep = width / (WAVEFORM_SAMPLES - 1);
        
        // Begin path for smooth waveform line
        gc.beginPath();
        
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            double x = i * xStep;
            
            // Normalize waveform data (-60 to 0 dB) to screen coordinates
            double normalizedValue = (60 + smoothedWaveform[i]) / 60.0;
            normalizedValue = Math.max(0, Math.min(1, normalizedValue));
            
            // Convert to waveform amplitude (projecting upward from bottom)
            double amplitude = normalizedValue * height * 0.9; // 90% of height, projecting upward
            double y = baselineY - amplitude; // Subtract to go upward from bottom
            
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        
        gc.stroke();
        
        // Add a subtle fill under the waveform
        gc.setFill(baseColor.deriveColor(0, 1, 1, 0.3)); // 30% opacity
        gc.beginPath();
        gc.moveTo(0, baselineY);
        
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            double x = i * xStep;
            double normalizedValue = (60 + smoothedWaveform[i]) / 60.0;
            normalizedValue = Math.max(0, Math.min(1, normalizedValue));
            double amplitude = normalizedValue * height * 0.9;
            double y = baselineY - amplitude;
            gc.lineTo(x, y);
        }
        
        gc.lineTo(width, baselineY);
        gc.closePath();
        gc.fill();
        
        // Remove effect after drawing
        gc.setEffect(null);
    }

    @Override
    protected void clearSpecificData() {
        // Clear waveform data
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            waveformData[i] = 0.0f;
            smoothedWaveform[i] = 0.0f;
        }

    }
}

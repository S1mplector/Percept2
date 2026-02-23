package com.musicplayer.ui.components;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Combined visualizer that displays both spectrum bars and waveform together.
 */
public class CombinedVisualizer extends BaseVisualizer {
    
    // Peak values for spectrum bars
    private final float[] peakValues;
    private final float[] peakVelocities;
    
    // Waveform data
    private final float[] waveformData;
    private final float[] smoothedWaveform;
    private static final int WAVEFORM_SAMPLES = 512;
    
    private static final double PEAK_FALL_SPEED = 0.15;
    private static final double PEAK_HANG_TIME = 0.92;
    
    private final Color peakColor = Color.rgb(255, 255, 255, 0.9);

    public CombinedVisualizer(int numBands) {
        super(numBands);
        
        // Initialize spectrum data
        this.peakValues = new float[numBands];
        this.peakVelocities = new float[numBands];
        
        // Initialize waveform data
        this.waveformData = new float[WAVEFORM_SAMPLES];
        this.smoothedWaveform = new float[WAVEFORM_SAMPLES];
        
        // Initialize arrays
        for (int i = 0; i < numBands; i++) {
            peakValues[i] = -60.0f;
            peakVelocities[i] = 0.0f;
        }
        
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            waveformData[i] = 0.0f;
            smoothedWaveform[i] = 0.0f;
        }
    }

    @Override
    protected void updateSpecificData(float[] magnitudes) {
        // Update peaks for spectrum
        int len = Math.min(magnitudes.length, numBands);
        for (int i = 0; i < len; i++) {
            if (displayMagnitudes[i] > peakValues[i]) {
                peakValues[i] = displayMagnitudes[i];
                peakVelocities[i] = 0.0f;
            }
        }
        
        // Update waveform data
        updateWaveformData(magnitudes);
    }

    @Override
    protected boolean performAnimationUpdate(double deltaTime) {
        // Update peak positions for spectrum bars
        boolean needsRedraw = false;
        for (int i = 0; i < numBands; i++) {
            if (peakValues[i] > displayMagnitudes[i]) {
                peakVelocities[i] += PEAK_FALL_SPEED;
                float effectiveVelocity = peakVelocities[i] * (1.0f - (float)PEAK_HANG_TIME);
                peakValues[i] -= effectiveVelocity;
                
                if (peakValues[i] < displayMagnitudes[i]) {
                    peakValues[i] = displayMagnitudes[i];
                    peakVelocities[i] = 0.0f;
                }
                needsRedraw = true;
            }
        }
        return needsRedraw;
    }

    private void updateWaveformData(float[] magnitudes) {
        System.arraycopy(waveformData, 1, waveformData, 0, WAVEFORM_SAMPLES - 1);
        
        float amplitude = 0.0f;
        for (float magnitude : magnitudes) {
            amplitude += magnitude;
        }
        amplitude /= magnitudes.length;
        
        waveformData[WAVEFORM_SAMPLES - 1] = Math.max(-60.0f, Math.min(0.0f, amplitude));
        
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            smoothedWaveform[i] = (float) (0.8 * smoothedWaveform[i] + 0.2 * waveformData[i]);
        }
    }

    @Override
    protected void drawVisualization(GraphicsContext gc, double width, double height, Color baseColor) {
        // Draw spectrum bars in the bottom 60%
        double spectrumHeight = height * 0.6;
        drawSpectrumBarsInRegion(gc, width, spectrumHeight, height, baseColor);
        
        // Draw waveform in the top 40%
        double waveformHeight = height * 0.4;
        drawWaveformInRegion(gc, width, 0, waveformHeight, baseColor);
        
        // Remove effect after drawing
        gc.setEffect(null);
    }
    
    private void drawSpectrumBarsInRegion(GraphicsContext gc, double width, double startY, double endY, Color baseColor) {
        double regionHeight = endY - startY;
        double bandWidth = width / numBands;
        double barWidth = bandWidth * 0.8;
        double spacing = bandWidth * 0.2;
        
        for (int i = 0; i < numBands; i++) {
            float magnitude = displayMagnitudes[i];
            float peak = peakValues[i];
            
            double normalizedMag = (60 + magnitude) / 60.0;
            double normalizedPeak = (60 + peak) / 60.0;
            normalizedMag = Math.max(0, Math.min(1, normalizedMag));
            normalizedPeak = Math.max(0, Math.min(1, normalizedPeak));
            
            double barHeight = normalizedMag * regionHeight * 0.9;
            double peakY = endY - (normalizedPeak * regionHeight * 0.9);
            double x = i * bandWidth + spacing / 2;
            double y = endY - barHeight;
            
            LinearGradient gradient = new LinearGradient(
                0, y, 0, endY,
                false, null,
                new Stop(0, baseColor.brighter()),
                new Stop(0.5, baseColor),
                new Stop(1, baseColor.darker().darker())
            );
            
            gc.setFill(gradient);
            gc.fillRect(x, y, barWidth, barHeight);
            
            if (normalizedPeak > normalizedMag && peakY < endY - 2) {
                gc.setFill(peakColor);
                gc.fillRect(x, peakY, barWidth, 2);
            }
        }
    }
    
    private void drawWaveformInRegion(GraphicsContext gc, double width, double startY, double endY, Color baseColor) {
        double regionHeight = endY - startY;
        double centerY = startY + regionHeight / 2.0;
        double xStep = width / (WAVEFORM_SAMPLES - 1);
        
        gc.setStroke(baseColor);
        gc.setLineWidth(1.5);
        gc.beginPath();
        
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            double x = i * xStep;
            double normalizedValue = (60 + smoothedWaveform[i]) / 60.0;
            normalizedValue = Math.max(0, Math.min(1, normalizedValue));
            double amplitude = (normalizedValue - 0.5) * regionHeight * 0.6;
            double y = centerY + amplitude;
            
            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        
        gc.stroke();
    }

    @Override
    protected void clearSpecificData() {
        // Clear spectrum data
        for (int i = 0; i < numBands; i++) {
            peakValues[i] = -60.0f;
            peakVelocities[i] = 0.0f;
        }
        
        // Clear waveform data
        for (int i = 0; i < WAVEFORM_SAMPLES; i++) {
            waveformData[i] = 0.0f;
            smoothedWaveform[i] = 0.0f;
        }
    }
}

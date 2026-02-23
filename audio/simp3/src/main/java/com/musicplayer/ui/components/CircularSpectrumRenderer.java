package com.musicplayer.ui.components;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Renders a circular radial spectrum visualization.
 * Displays audio frequency data as bars radiating from the center in a circular pattern.
 */
public class CircularSpectrumRenderer {
    
    private final VisualizerConfig config;
    private double[] smoothedData;
    private double animationPhase = 0.0;
    private final int SPECTRUM_BANDS = 64;
    
    public CircularSpectrumRenderer(VisualizerConfig config) {
        this.config = config;
        this.smoothedData = new double[config.getBarCount()];
    }
    
    /**
     * Render the circular spectrum visualization.
     * @param gc Graphics context to draw on
     * @param spectrum Raw spectrum data (64 bands)
     * @param width Canvas width
     * @param height Canvas height
     */
    public void render(GraphicsContext gc, double[] spectrum, double width, double height) {
        // Debug log first few renders
        if (Math.random() < 0.01) { // Log 1% of renders
            System.out.println("CircularSpectrumRenderer.render() - Canvas: " + width + "x" + height);
            if (spectrum != null && spectrum.length > 0) {
                System.out.println("  First spectrum value: " + spectrum[0]);
            }
        }
        
        // Clear canvas
        gc.clearRect(0, 0, width, height);
        
        // Draw a test background to verify rendering
        gc.setFill(Color.rgb(20, 20, 20, 0.5)); // Semi-transparent dark background
        gc.fillRect(0, 0, width, height);
        
        // Calculate center point
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double radius = Math.min(width, height) / 2.0 - 5; // Leave 5px margin
        
        // Map 64 bands to configured bar count (default 32)
        double[] mappedData = mapSpectrumData(spectrum);
        
        // Apply smoothing
        smoothData(mappedData);
        
        // Check if we have any data to render
        boolean hasData = false;
        for (double value : smoothedData) {
            if (value > 0.001) {
                hasData = true;
                break;
            }
        }
        
        if (!hasData) {
            if (Math.random() < 0.01) {
                System.out.println("  No significant spectrum data to render - drawing test pattern");
            }
            // Draw a test pattern when there's no data
            drawTestPattern(gc, centerX, centerY, radius);
        }
        
        // Update animation phase
        if (config.isEnableRotation()) {
            animationPhase += config.getRotationSpeed();
            if (animationPhase >= 360) {
                animationPhase -= 360;
            }
        }
        
        // Save graphics state
        gc.save();
        
        // Apply rotation
        gc.translate(centerX, centerY);
        gc.rotate(animationPhase);
        gc.translate(-centerX, -centerY);
        
        // Draw bars
        double angleStep = 360.0 / config.getBarCount();
        
        for (int i = 0; i < config.getBarCount(); i++) {
            double angle = i * angleStep;
            double magnitude = smoothedData[i];
            
            // Calculate bar height
            double barHeight = calculateBarHeight(magnitude);
            
            // Calculate bar position
            double startRadius = radius * 0.3; // Start from 30% of radius
            double endRadius = startRadius + barHeight;
            
            // Calculate color based on magnitude
            Color barColor = config.getGradientColor(magnitude);
            
            // Draw bar
            drawRadialBar(gc, centerX, centerY, startRadius, endRadius, angle, angleStep * 0.8, barColor);
        }
        
        // Draw center circle with gradient
        drawCenterCircle(gc, centerX, centerY, radius * 0.25);
        
        // Restore graphics state
        gc.restore();
        
        // Apply glow effect if enabled
        if (config.isGlowEffect()) {
            Glow glow = new Glow();
            glow.setLevel(0.3);
            gc.applyEffect(glow);
        }
    }
    
    /**
     * Map spectrum data from 64 bands to configured bar count.
     */
    private double[] mapSpectrumData(double[] spectrum) {
        double[] mapped = new double[config.getBarCount()];
        
        if (spectrum == null || spectrum.length == 0) {
            return mapped;
        }
        
        // Calculate how many spectrum bands per visual bar
        double bandsPerBar = (double) SPECTRUM_BANDS / config.getBarCount();
        
        for (int i = 0; i < config.getBarCount(); i++) {
            double sum = 0.0;
            int count = 0;
            
            int startBand = (int) (i * bandsPerBar);
            int endBand = (int) ((i + 1) * bandsPerBar);
            
            for (int j = startBand; j < endBand && j < spectrum.length; j++) {
                // Convert from dB to linear scale (spectrum data is in dB)
                double linearValue = Math.pow(10, spectrum[j] / 20.0);
                sum += linearValue;
                count++;
            }
            
            if (count > 0) {
                // Average and normalize to 0-1 range
                mapped[i] = Math.min(1.0, sum / count / 100.0);
            }
        }
        
        return mapped;
    }
    
    /**
     * Apply exponential smoothing to reduce jitter.
     */
    private void smoothData(double[] data) {
        for (int i = 0; i < data.length; i++) {
            smoothedData[i] = smoothedData[i] * config.getSmoothingFactor() + 
                             data[i] * (1.0 - config.getSmoothingFactor());
        }
    }
    
    /**
     * Calculate bar height based on magnitude using logarithmic scaling.
     */
    private double calculateBarHeight(double magnitude) {
        // Apply logarithmic scaling for better visual representation
        double logScale = Math.log10(magnitude * 9 + 1); // log10(1) = 0, log10(10) = 1
        
        // Map to height range
        return config.getMinBarHeight() + 
               (config.getMaxBarHeight() - config.getMinBarHeight()) * logScale;
    }
    
    /**
     * Draw a radial bar.
     */
    private void drawRadialBar(GraphicsContext gc, double centerX, double centerY,
                              double innerRadius, double outerRadius,
                              double angle, double width, Color color) {
        gc.save();
        
        // Convert angle to radians
        double angleRad = Math.toRadians(angle);
        double widthRad = Math.toRadians(width);
        
        // Calculate bar vertices
        double x1 = centerX + innerRadius * Math.cos(angleRad - widthRad/2);
        double y1 = centerY + innerRadius * Math.sin(angleRad - widthRad/2);
        double x2 = centerX + outerRadius * Math.cos(angleRad - widthRad/2);
        double y2 = centerY + outerRadius * Math.sin(angleRad - widthRad/2);
        double x3 = centerX + outerRadius * Math.cos(angleRad + widthRad/2);
        double y3 = centerY + outerRadius * Math.sin(angleRad + widthRad/2);
        double x4 = centerX + innerRadius * Math.cos(angleRad + widthRad/2);
        double y4 = centerY + innerRadius * Math.sin(angleRad + widthRad/2);
        
        // Set fill color with gradient
        gc.setFill(color);
        
        // Draw the bar as a polygon
        gc.fillPolygon(new double[]{x1, x2, x3, x4},
                      new double[]{y1, y2, y3, y4}, 4);
        
        // Add rounded caps
        gc.fillOval(x2 - 2, y2 - 2, 4, 4);
        gc.fillOval(x3 - 2, y3 - 2, 4, 4);
        
        gc.restore();
    }
    
    /**
     * Draw the center circle with gradient.
     */
    private void drawCenterCircle(GraphicsContext gc, double centerX, double centerY, double radius) {
        // Create radial gradient
        RadialGradient gradient = new RadialGradient(
            0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
            new Stop(0, config.getPrimaryColor()),
            new Stop(0.7, config.getSecondaryColor()),
            new Stop(1, Color.TRANSPARENT)
        );
        
        gc.setFill(gradient);
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }
    
    /**
     * Draw a test pattern to verify visualizer is working
     */
    private void drawTestPattern(GraphicsContext gc, double centerX, double centerY, double radius) {
        // Draw concentric circles
        gc.setStroke(Color.rgb(76, 175, 80, 0.5));
        gc.setLineWidth(2);
        
        for (int i = 1; i <= 3; i++) {
            double r = radius * 0.3 * i;
            gc.strokeOval(centerX - r, centerY - r, r * 2, r * 2);
        }
        
        // Draw radial lines
        gc.setStroke(Color.rgb(76, 175, 80, 0.3));
        gc.setLineWidth(1);
        
        for (int i = 0; i < 8; i++) {
            double angle = i * 45;
            double angleRad = Math.toRadians(angle);
            double x1 = centerX + radius * 0.3 * Math.cos(angleRad);
            double y1 = centerY + radius * 0.3 * Math.sin(angleRad);
            double x2 = centerX + radius * 0.9 * Math.cos(angleRad);
            double y2 = centerY + radius * 0.9 * Math.sin(angleRad);
            gc.strokeLine(x1, y1, x2, y2);
        }
        
        // Draw center dot
        gc.setFill(Color.rgb(76, 175, 80, 0.8));
        gc.fillOval(centerX - 3, centerY - 3, 6, 6);
    }
    
    /**
     * Reset the visualizer state.
     */
    public void reset() {
        for (int i = 0; i < smoothedData.length; i++) {
            smoothedData[i] = 0.0;
        }
        animationPhase = 0.0;
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
}
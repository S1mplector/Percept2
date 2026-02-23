package com.musicplayer.ui.components;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

/**
 * Base class for audio visualizers containing shared functionality.
 */
public abstract class BaseVisualizer extends Canvas {
    
    protected final int numBands;
    protected final float[] displayMagnitudes;
    
    // Animation timer for smooth updates
    protected AnimationTimer animator;
    protected long lastUpdate = 0;
    
    // Color scheme with hue animation
    protected double currentHue = 120; // Start with green (120 degrees)
    protected static final double HUE_SHIFT_SPEED = 10; // Degrees per second
    
    // Track if visualizer is paused
    protected boolean isPaused = false;
    
    // Color mode settings
    protected boolean gradientCyclingEnabled = true;
    protected Color solidColor = Color.LIMEGREEN;
    protected boolean enabled = true;
    
    // Smoothing factor for magnitude updates
    protected static final double SMOOTHING_FACTOR = 0.7;

    public BaseVisualizer(int numBands) {
        this.numBands = numBands;
        this.displayMagnitudes = new float[numBands];
        
        // Initialize magnitudes
        for (int i = 0; i < numBands; i++) {
            displayMagnitudes[i] = -60.0f;
        }

        // Redraw when resized
        widthProperty().addListener((obs, o, n) -> draw());
        heightProperty().addListener((obs, o, n) -> draw());
        
        // Start animation
        startAnimation();
    }

    /**
     * Set whether the visualizer is enabled.
     * @param enabled True to enable visualization, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            // Clear the canvas when disabled
            GraphicsContext gc = getGraphicsContext2D();
            gc.clearRect(0, 0, getWidth(), getHeight());
        }
    }
    
    /**
     * Set whether gradient cycling is enabled.
     * @param enabled True for gradient cycling, false for solid color
     */
    public void setGradientCyclingEnabled(boolean enabled) {
        this.gradientCyclingEnabled = enabled;
    }
    
    /**
     * Set the solid color to use when gradient cycling is disabled.
     * @param color The color to use
     */
    public void setSolidColor(Color color) {
        this.solidColor = color;
    }

    /**
     * Update the visualizer with the latest magnitude data.
     * This should be called from the JavaFX Application Thread.
     */
    public void update(float[] newMagnitudes) {
        // Ignore updates when paused, disabled, or no data
        if (isPaused || !enabled || newMagnitudes == null) {
            return;
        }
        
        try {
            int len = Math.min(newMagnitudes.length, numBands);
            for (int i = 0; i < len; i++) {
                float newVal = newMagnitudes[i];
                // Exponential smoothing
                displayMagnitudes[i] = (float) (SMOOTHING_FACTOR * displayMagnitudes[i] + 
                                               (1 - SMOOTHING_FACTOR) * newVal);
            }
            
            // Allow subclasses to perform additional updates
            updateSpecificData(newMagnitudes);
            
            draw();
        } catch (Exception e) {
            // Log error but don't crash - prevent visualizer freezing
            System.err.println("Error updating visualizer: " + e.getMessage());
        }
    }
    
    /**
     * Allow subclasses to update their specific data structures.
     * @param magnitudes The new magnitude data
     */
    protected abstract void updateSpecificData(float[] magnitudes);

    protected void startAnimation() {
        animator = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    // Skip if paused
                    if (isPaused) {
                        return;
                    }
                    
                    if (lastUpdate == 0) {
                        lastUpdate = now;
                        return;
                    }
                    
                    // Calculate delta time in seconds
                    double deltaTime = (now - lastUpdate) / 1_000_000_000.0;
                    
                    // Prevent huge time jumps (e.g., after minimizing)
                    if (deltaTime > 1.0) {
                        lastUpdate = now;
                        return;
                    }
                    
                    lastUpdate = now;
                    
                    // Update hue for color animation only if gradient cycling is enabled
                    if (gradientCyclingEnabled) {
                        currentHue += HUE_SHIFT_SPEED * deltaTime;
                        if (currentHue >= 360) {
                            currentHue -= 360;
                        }
                    }
                    
                    // Allow subclasses to perform animation updates
                    if (performAnimationUpdate(deltaTime)) {
                        draw();
                    }
                } catch (Exception e) {
                    // Log but don't crash - prevent UI freezing
                    System.err.println("Error in visualizer animation: " + e.getMessage());
                }
            }
        };
        animator.start();
    }
    
    /**
     * Allow subclasses to perform animation-specific updates.
     * @param deltaTime Time elapsed since last update in seconds
     * @return True if a redraw is needed
     */
    protected abstract boolean performAnimationUpdate(double deltaTime);

    protected void draw() {
        // Skip drawing if disabled
        if (!enabled) {
            return;
        }
        
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);

        // Create base color from current hue or solid color
        Color baseColor;
        if (gradientCyclingEnabled) {
            baseColor = Color.hsb(currentHue, 0.8, 1.0); // High saturation and brightness
        } else {
            baseColor = solidColor;
        }

        // Apply glow effect to the entire canvas
        DropShadow glow = new DropShadow();
        glow.setColor(baseColor);
        glow.setRadius(10);
        glow.setSpread(0.2);
        gc.setEffect(glow);
        
        // Let subclasses handle the actual drawing
        drawVisualization(gc, width, height, baseColor);
    }
    
    /**
     * Abstract method for subclasses to implement their specific drawing logic.
     * @param gc Graphics context
     * @param width Canvas width
     * @param height Canvas height
     * @param baseColor Base color for drawing
     */
    protected abstract void drawVisualization(GraphicsContext gc, double width, double height, Color baseColor);

    /**
     * Pause the visualizer animation.
     * This should be called when the window is minimized to save CPU resources.
     */
    public void pause() {
        if (!isPaused && animator != null) {
            animator.stop();
            isPaused = true;
        }
    }

    /**
     * Resume the visualizer animation.
     * This should be called when the window is restored.
     */
    public void resume() {
        if (isPaused && animator != null) {
            startAnimation();
            isPaused = false;
        }
    }

    /**
     * Force clear and refresh the visualizer.
     * Useful when restoring from minimized state to ensure clean rendering.
     */
    public void forceRefresh() {
        // Clear all data
        for (int i = 0; i < numBands; i++) {
            displayMagnitudes[i] = -60.0f;
        }
        
        // Allow subclasses to clear their specific data
        clearSpecificData();
        
        // Force redraw
        draw();
    }
    
    /**
     * Allow subclasses to clear their specific data structures.
     */
    protected abstract void clearSpecificData();

    /**
     * Stop the visualizer and clean up resources.
     */
    public void stop() {
        if (animator != null) {
            animator.stop();
            animator = null;
        }
        isPaused = true;
    }
}

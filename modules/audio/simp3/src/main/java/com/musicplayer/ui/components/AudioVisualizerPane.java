package com.musicplayer.ui.components;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import com.musicplayer.data.models.Settings;

/**
 * Container component that manages the visualizer canvas and animation loop.
 * Extends StackPane for easy integration with existing album art container.
 */
public class AudioVisualizerPane extends StackPane {
    
    private static final int SPECTRUM_BANDS = 64;
    
    private Canvas canvas;
    private AnimationTimer animationTimer;
    private CompactBarRenderer renderer;
    private double[] spectrumData;
    private boolean isActive = false;
    
    // Configuration
    private final VisualizerConfig config;
    
    // Smooth transition support
    private FadeTransition fadeIn;
    private FadeTransition fadeOut;
    
    // Performance tracking
    private long lastFrameTime = 0;
    private final long targetFrameTime;
    private Settings settings;
    
    public AudioVisualizerPane() {
        this(null);
    }
    
    public AudioVisualizerPane(Settings settings) {
        this.settings = settings;
        this.config = new VisualizerConfig();
        this.targetFrameTime = 1_000_000_000L / config.getTargetFPS(); // Nanoseconds per frame
        this.spectrumData = new double[SPECTRUM_BANDS];
        
        initializeComponents();
        setupAnimations();
    }
    
    private void initializeComponents() {
        // Create canvas
        canvas = new Canvas();
        canvas.setMouseTransparent(true);
        
        // Create renderer with settings
        renderer = new CompactBarRenderer(config, settings);
        
        // Add canvas to pane
        getChildren().add(canvas);
        
        // Bind canvas size to pane size
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        
        // Debug canvas size changes
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("Visualizer canvas width changed: " + oldVal + " -> " + newVal);
        });
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("Visualizer canvas height changed: " + oldVal + " -> " + newVal);
        });
        
        // Initially hidden
        setOpacity(0);
        setVisible(false);
        
        // Add style class
        getStyleClass().add("visualizer-pane");
    }
    
    private void setupAnimations() {
        // Fade in transition
        fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setOnFinished(e -> {
            // Animation is already started in start() method
        });
        
        // Fade out transition
        fadeOut = new FadeTransition(Duration.millis(300), this);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            setVisible(false);
            stopAnimation();
        });
        
        // Animation timer for rendering
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Frame rate limiting
                if (lastFrameTime > 0 && (now - lastFrameTime) < targetFrameTime) {
                    return;
                }
                lastFrameTime = now;
                
                // Render frame
                if (isActive && canvas != null) {
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    
                    // Debug first few frames
                    if (lastFrameTime < 5_000_000_000L) { // First 5 seconds
                        System.out.println("Rendering frame - Canvas: " + canvas.getWidth() + "x" + canvas.getHeight() +
                                         ", Visible: " + isVisible() + ", Opacity: " + getOpacity());
                    }
                    
                    renderer.render(gc, spectrumData, canvas.getWidth(), canvas.getHeight());
                }
            }
        };
    }
    
    /**
     * Start the visualizer.
     */
    public void start() {
        System.out.println("AudioVisualizerPane.start() called - isActive: " + isActive);
        if (!isActive) {
            isActive = true;
            setVisible(true);
            setOpacity(0); // Start from transparent
            startAnimation(); // Start animation immediately
            fadeIn.play(); // Then fade in
            
            // Force layout update to ensure canvas is properly sized
            requestLayout();
            applyCss();
            layout();
            
            // Debug info
            System.out.println("Visualizer started - Canvas size: " + canvas.getWidth() + "x" + canvas.getHeight());
            System.out.println("Visualizer pane size: " + getWidth() + "x" + getHeight());
            
            // If the pane has no size, set a minimum size to ensure canvas gets sized
            if (getWidth() == 0 || getHeight() == 0) {
                System.out.println("Pane has no size, setting minimum size");
                setMinWidth(80);
                setMinHeight(80);
                setPrefWidth(80);
                setPrefHeight(80);
            }
        }
    }
    
    /**
     * Stop the visualizer.
     */
    public void stop() {
        if (isActive) {
            isActive = false;
            // Clear spectrum data to allow bars to fall
            clearSpectrum();
            // Continue animation during fade out to show bars falling
            fadeOut.play();
        }
    }
    
    /**
     * Update spectrum data from audio engine.
     * This method is called by the audio spectrum listener.
     *
     * @param timestamp Current playback timestamp
     * @param duration Total duration
     * @param magnitudes Frequency magnitudes (in dB)
     * @param phases Phase information (not used)
     */
    public void updateSpectrum(double timestamp, double duration,
                              float[] magnitudes, float[] phases) {
        if (magnitudes != null && magnitudes.length > 0) {
            // Only update spectrum if visualizer is active
            if (isActive) {
                // Debug spectrum data
                if (Math.random() < 0.01) { // Log 1% of updates to avoid spam
                    System.out.println("AudioVisualizerPane spectrum update - Active: " + isActive +
                                     ", Visible: " + isVisible() + ", Canvas: " + canvas.getWidth() + "x" + canvas.getHeight() +
                                     ", First magnitude: " + magnitudes[0]);
                }
                
                // Convert float array to double array
                int length = Math.min(magnitudes.length, SPECTRUM_BANDS);
                for (int i = 0; i < length; i++) {
                    spectrumData[i] = magnitudes[i];
                }
                
                // Fill remaining with zeros if needed
                for (int i = length; i < SPECTRUM_BANDS; i++) {
                    spectrumData[i] = -60.0; // Minimum dB value
                }
                
                // Force a render if we're not getting regular updates
                if (canvas != null && isVisible()) {
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    renderer.render(gc, spectrumData, canvas.getWidth(), canvas.getHeight());
                }
            }
        } else if (!isActive && isVisible()) {
            // If not active but still visible (during fade out), clear spectrum
            clearSpectrum();
        }
    }
    
    /**
     * Clear spectrum data.
     */
    public void clearSpectrum() {
        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            spectrumData[i] = -60.0; // Minimum dB value
        }
    }
    
    /**
     * Check if visualizer is currently active.
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Get the visualizer configuration.
     */
    public VisualizerConfig getConfig() {
        return config;
    }
    
    /**
     * Check if the visualizer supports the given audio format.
     */
    public boolean supportsFormat(String audioFormat) {
        return renderer.supportsFormat(audioFormat);
    }
    
    /**
     * Reset the visualizer to initial state.
     */
    public void reset() {
        clearSpectrum();
        renderer.reset();
    }
    
    private void startAnimation() {
        if (animationTimer != null) {
            animationTimer.start();
        }
    }
    
    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        lastFrameTime = 0;
    }
    
    /**
     * Update the settings for the visualizer.
     */
    public void updateSettings(Settings settings) {
        this.settings = settings;
        if (renderer instanceof CompactBarRenderer) {
            ((CompactBarRenderer) renderer).updateSettings(settings);
        }
    }
}
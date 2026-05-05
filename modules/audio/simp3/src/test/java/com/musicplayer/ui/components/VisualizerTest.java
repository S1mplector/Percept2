package com.musicplayer.ui.components;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Unit tests for audio visualizer components.
 * Also includes a visual test application for manual verification.
 */
public class VisualizerTest {
    
    private static boolean javaFxInitialized = false;
    
    @BeforeAll
    public static void initJavaFX() {
        if (!javaFxInitialized) {
            // Initialize JavaFX toolkit for tests
            try {
                Platform.startup(() -> {});
                javaFxInitialized = true;
            } catch (IllegalStateException e) {
                // JavaFX already initialized
                javaFxInitialized = true;
            }
        }
    }
    
    @Test
    @DisplayName("VisualizerConfig should have correct default values")
    public void testVisualizerConfigDefaults() {
        VisualizerConfig config = new VisualizerConfig();
        
        assertEquals(32, config.getBarCount());
        assertEquals(5.0, config.getMinBarHeight());
        assertEquals(30.0, config.getMaxBarHeight());
        assertEquals(0.8, config.getSmoothingFactor());
        assertEquals(Color.web("#4CAF50"), config.getPrimaryColor());
        assertEquals(Color.web("#81C784"), config.getSecondaryColor());
        assertTrue(config.isGlowEffect());
        assertEquals(8.0, config.getGlowRadius());
        assertEquals(0.5, config.getRotationSpeed());
        assertTrue(config.isEnableRotation());
        assertEquals(60, config.getTargetFPS());
    }
    
    @Test
    @DisplayName("VisualizerConfig should clamp smoothing factor between 0 and 1")
    public void testVisualizerConfigSmoothingFactorClamping() {
        VisualizerConfig config = new VisualizerConfig();
        
        config.setSmoothingFactor(1.5);
        assertEquals(1.0, config.getSmoothingFactor());
        
        config.setSmoothingFactor(-0.5);
        assertEquals(0.0, config.getSmoothingFactor());
        
        config.setSmoothingFactor(0.5);
        assertEquals(0.5, config.getSmoothingFactor());
    }
    
    @Test
    @DisplayName("VisualizerConfig should generate correct gradient colors")
    public void testVisualizerConfigGradientColor() {
        VisualizerConfig config = new VisualizerConfig();
        
        // Test boundary values
        Color startColor = config.getGradientColor(0.0);
        assertEquals(config.getPrimaryColor(), startColor);
        
        Color endColor = config.getGradientColor(1.0);
        assertEquals(config.getSecondaryColor(), endColor);
        
        // Test clamping
        Color clampedLow = config.getGradientColor(-0.5);
        assertEquals(config.getPrimaryColor(), clampedLow);
        
        Color clampedHigh = config.getGradientColor(1.5);
        assertEquals(config.getSecondaryColor(), clampedHigh);
    }
    
    @Test
    @DisplayName("CircularSpectrumRenderer should support correct audio formats")
    public void testCircularSpectrumRendererFormatSupport() {
        VisualizerConfig config = new VisualizerConfig();
        CircularSpectrumRenderer renderer = new CircularSpectrumRenderer(config);
        
        // Supported formats
        assertTrue(renderer.supportsFormat("mp3"));
        assertTrue(renderer.supportsFormat("MP3"));
        assertTrue(renderer.supportsFormat("m4a"));
        assertTrue(renderer.supportsFormat("M4A"));
        assertTrue(renderer.supportsFormat("mp4"));
        assertTrue(renderer.supportsFormat("aac"));
        
        // Unsupported formats
        assertFalse(renderer.supportsFormat("wav"));
        assertFalse(renderer.supportsFormat("flac"));
        assertFalse(renderer.supportsFormat("ogg"));
        assertFalse(renderer.supportsFormat(null));
        assertFalse(renderer.supportsFormat(""));
    }
    
    @Test
    @DisplayName("CircularSpectrumRenderer should handle null spectrum data")
    public void testCircularSpectrumRendererNullData() {
        Platform.runLater(() -> {
            VisualizerConfig config = new VisualizerConfig();
            CircularSpectrumRenderer renderer = new CircularSpectrumRenderer(config);
            
            Canvas canvas = new Canvas(100, 100);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            
            // Should not throw exception with null data
            assertDoesNotThrow(() -> {
                renderer.render(gc, null, 100, 100);
            });
        });
    }
    
    @Test
    @DisplayName("AudioVisualizerPane should initialize correctly")
    public void testAudioVisualizerPaneInitialization() {
        Platform.runLater(() -> {
            AudioVisualizerPane visualizer = new AudioVisualizerPane();
            
            assertNotNull(visualizer.getConfig());
            assertFalse(visualizer.isActive());
            assertEquals(0.0, visualizer.getOpacity());
            assertFalse(visualizer.isVisible());
            assertTrue(visualizer.getStyleClass().contains("visualizer-pane"));
        });
    }
    
    @Test
    @DisplayName("AudioVisualizerPane should handle spectrum updates")
    public void testAudioVisualizerPaneSpectrumUpdate() {
        Platform.runLater(() -> {
            AudioVisualizerPane visualizer = new AudioVisualizerPane();
            
            // Test with valid data
            float[] magnitudes = new float[64];
            for (int i = 0; i < 64; i++) {
                magnitudes[i] = -30.0f;
            }
            
            assertDoesNotThrow(() -> {
                visualizer.updateSpectrum(0, 0, magnitudes, null);
            });
            
            // Test with null data
            assertDoesNotThrow(() -> {
                visualizer.updateSpectrum(0, 0, null, null);
            });
            
            // Test with empty data
            assertDoesNotThrow(() -> {
                visualizer.updateSpectrum(0, 0, new float[0], null);
            });
        });
    }
    
    @Test
    @DisplayName("AudioVisualizerPane should support correct audio formats")
    public void testAudioVisualizerPaneFormatSupport() {
        Platform.runLater(() -> {
            AudioVisualizerPane visualizer = new AudioVisualizerPane();
            
            assertTrue(visualizer.supportsFormat("mp3"));
            assertTrue(visualizer.supportsFormat("m4a"));
            assertFalse(visualizer.supportsFormat("wav"));
            assertFalse(visualizer.supportsFormat("flac"));
        });
    }
    
    /**
     * Visual test application for manual verification.
     * Run this as a Java application (not a JUnit test) to see the visualizer in action.
     */
    public static class VisualTest extends Application {
        
        @Override
        public void start(Stage primaryStage) {
            // Create visualizer pane
            AudioVisualizerPane visualizer = new AudioVisualizerPane();
            
            // Create root container
            StackPane root = new StackPane();
            root.setPrefSize(400, 400);
            root.setStyle("-fx-background-color: #1a1a1a;");
            root.getChildren().add(visualizer);
            
            // Create scene
            Scene scene = new Scene(root);
            
            // Setup stage
            primaryStage.setTitle("Audio Visualizer Test");
            primaryStage.setScene(scene);
            primaryStage.show();
            
            // Start visualizer
            visualizer.start();
            
            // Simulate spectrum data
            simulateSpectrumData(visualizer);
        }
        
        private void simulateSpectrumData(AudioVisualizerPane visualizer) {
            // Create a timeline to simulate spectrum updates
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(16), // ~60 FPS
                    e -> {
                        float[] magnitudes = new float[64];
                        for (int i = 0; i < 64; i++) {
                            // Generate spectrum data with frequency-based pattern
                            double frequency = i / 64.0;
                            double amplitude = Math.sin(System.currentTimeMillis() / 1000.0 + frequency * Math.PI * 2) * 20;
                            magnitudes[i] = (float) (-40 + amplitude + Math.random() * 10);
                        }
                        visualizer.updateSpectrum(0, 0, magnitudes, null);
                    }
                )
            );
            timeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
            timeline.play();
        }
        
        public static void main(String[] args) {
            launch(args);
        }
    }
}
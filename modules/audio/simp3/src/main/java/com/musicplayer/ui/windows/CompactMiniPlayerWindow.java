package com.musicplayer.ui.windows;

import com.musicplayer.services.AudioPlayerService;
import com.musicplayer.services.SettingsService;
import com.musicplayer.ui.components.AudioVisualizerPane;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.animation.ScaleTransition;

/**
 * Ultra-compact mini player: single small square window
 * with album art + equalizer centered and essential controls.
 */
public class CompactMiniPlayerWindow {

    private final Stage stage;
    private final AudioPlayerService audioPlayerService;
    private final SettingsService settingsService;

    // UI
    private StackPane albumArtContainer;
    private ImageView albumArt;
    private AudioVisualizerPane visualizer;

    private Button btnPrev;
    private Button btnPlayPause;
    private Button btnNext;
    private Button btnSwitchToNormal;

    private Slider progressSlider;

    // Icons
    private final Image playIcon;
    private final Image pauseIcon;
    private final Image previousIcon;
    private final Image nextIcon;

    // Drag
    private double xOffset;
    private double yOffset;

    // Callback to switch back to normal mini player
    private Runnable onSwitchToNormal;

    // Listener ownership flag to avoid double binding
    private boolean listenerAttached = false;

    public CompactMiniPlayerWindow(AudioPlayerService audioPlayerService, SettingsService settingsService) {
        this.audioPlayerService = audioPlayerService;
        this.settingsService = settingsService;
        this.stage = new Stage();

        // Load icons shared with the app resources
        playIcon = new Image(getClass().getResourceAsStream("/images/icons/play.png"));
        pauseIcon = new Image(getClass().getResourceAsStream("/images/icons/pause.png"));
        previousIcon = new Image(getClass().getResourceAsStream("/images/icons/previous.png"));
        nextIcon = new Image(getClass().getResourceAsStream("/images/icons/next.png"));

        initUI();
        setupBindings();
    }

    private void initUI() {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        // Center piece: album art + visualizer
        albumArt = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/album_placeholder.png")));
        albumArt.setFitWidth(72);
        albumArt.setFitHeight(72);
        albumArt.setPreserveRatio(true);
        albumArt.setSmooth(true);

        visualizer = new AudioVisualizerPane(settingsService.getSettings());
        visualizer.setMouseTransparent(true);
        visualizer.setPrefSize(80, 80);
        visualizer.setMaxSize(80, 80);
        visualizer.setMinSize(80, 80);

        albumArtContainer = new StackPane(albumArt, visualizer);
        albumArtContainer.setAlignment(Pos.CENTER);

        // Essential controls in a compact row
        btnPrev = iconButton(previousIcon, "Previous");
        btnPlayPause = iconButton(playIcon, "Play/Pause");
        btnNext = iconButton(nextIcon, "Next");

        // Text-based button to avoid missing resource dependency
        btnSwitchToNormal = new Button("◱");
        btnSwitchToNormal.setTooltip(new Tooltip("Switch to Normal Mini Player"));
        btnSwitchToNormal.getStyleClass().add("compact-btn");
        btnSwitchToNormal.setOnAction(e -> {
            if (onSwitchToNormal != null) onSwitchToNormal.run();
        });

        HBox controls = new HBox(6, btnPrev, btnPlayPause, btnNext, btnSwitchToNormal);
        controls.setAlignment(Pos.CENTER);

        // Tiny progress bar (click to seek)
        progressSlider = new Slider();
        progressSlider.setMax(100);
        progressSlider.setPrefWidth(120);
        progressSlider.setMaxHeight(4);
        progressSlider.getStyleClass().add("compact-progress");

        VBox root = new VBox(6);
        root.setPadding(new Insets(6));
        root.setAlignment(Pos.CENTER);
        // Place progress just below the album cover
        root.getChildren().addAll(albumArtContainer, progressSlider, controls);
        root.getStyleClass().add("compact-mini-root");

        // Rounded clipping to match CSS radius
        Rectangle clip = new Rectangle();
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        // Square, small
        Scene scene = new Scene(root, 140, 140);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/mini-player-compact.css").toExternalForm());
        stage.setScene(scene);

        // Drag to move
        scene.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                xOffset = e.getSceneX();
                yOffset = e.getSceneY();
            }
        });
        scene.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            }
        });

        // Scroll to adjust volume
        scene.addEventFilter(ScrollEvent.SCROLL, e -> {
            double delta = e.getDeltaY();
            double v = audioPlayerService.getVolume();
            v = Math.max(0, Math.min(1, v + (delta > 0 ? 0.03 : -0.03)));
            audioPlayerService.setVolume(v);
        });
    }

    private Button iconButton(Image icon, String tooltip) {
        Button b = new Button();
        ImageView iv = new ImageView(icon);
        iv.setFitWidth(16);
        iv.setFitHeight(16);
        b.setGraphic(iv);
        b.setTooltip(new Tooltip(tooltip));
        b.getStyleClass().add("compact-btn");

        // Small press animation
        b.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
            st.setToX(0.9);
            st.setToY(0.9);
            st.play();
        });
        b.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), b);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        return b;
    }

    private void setupBindings() {
        // Controls
        btnPrev.setOnAction(e -> audioPlayerService.previousTrack());
        btnNext.setOnAction(e -> audioPlayerService.nextTrack());
        btnPlayPause.setOnAction(e -> audioPlayerService.togglePlayPause());

        // Update play/pause icon
        audioPlayerService.playingProperty().addListener((o, was, is) -> {
            ImageView iv = (ImageView) btnPlayPause.getGraphic();
            iv.setImage(is ? pauseIcon : playIcon);
        });

        // Progress
        progressSlider.maxProperty().bind(audioPlayerService.totalTimeProperty());
        audioPlayerService.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!progressSlider.isValueChanging()) {
                progressSlider.setValue(newTime.doubleValue());
            }
        });
        progressSlider.setOnMousePressed(e -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seek = (e.getX() / progressSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seek);
            }
        });
        progressSlider.setOnMouseDragged(e -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seek = (e.getX() / progressSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seek);
            }
        });

        // Visualizer
        audioPlayerService.playingProperty().addListener((obs, oldVal, isPlaying) -> updateVisualizerState());
        // Do not attach listener here; ownership will be handled in show()/hide()
        updateVisualizerState();
    }

    private void updateVisualizerState() {
        if (audioPlayerService.isPlaying()) {
            if (!visualizer.isActive()) {
                visualizer.start();
            }
            if (listenerAttached) {
                audioPlayerService.setAudioSpectrumListener(visualizer::updateSpectrum);
            }
        } else {
            // Let bars fall naturally; don't detach here. Detach on hide().
            if (visualizer.isActive()) {
                visualizer.stop();
            }
        }
    }

    public void show() {
        // Attach spectrum listener ownership to this window
        audioPlayerService.setAudioSpectrumListener(visualizer::updateSpectrum);
        listenerAttached = true;
        updateVisualizerState();
        // Ensure play/pause icon reflects current playback state immediately
        syncPlayPauseIcon();
        stage.show();
    }

    public void hide() {
        // Release listener ownership when hiding
        audioPlayerService.setAudioSpectrumListener(null);
        listenerAttached = false;
        if (visualizer != null && visualizer.isActive()) {
            visualizer.stop();
        }
        stage.hide();
    }

    /**
     * Sync the play/pause button icon with the current playback state.
     */
    private void syncPlayPauseIcon() {
        if (btnPlayPause != null && btnPlayPause.getGraphic() instanceof ImageView) {
            ImageView iv = (ImageView) btnPlayPause.getGraphic();
            boolean isPlaying = audioPlayerService.isPlaying();
            iv.setImage(isPlaying ? pauseIcon : playIcon);
        }
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public void setOnSwitchToNormal(Runnable onSwitchToNormal) {
        this.onSwitchToNormal = onSwitchToNormal;
    }

    public Stage getStage() {
        return stage;
    }
}

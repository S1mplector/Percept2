package com.musicplayer.ui.controllers;

import com.musicplayer.data.models.Song;
import com.musicplayer.services.AudioPlayerService;
import com.musicplayer.services.ListeningStatsService;
import com.musicplayer.ui.components.PlaybackModeButtons;
import com.musicplayer.ui.util.AlbumArtLoader;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Controller responsible for all audio playback functionality.
 * Handles play/pause, volume, seeking, album art display, and keyboard shortcuts.
 */
public class AudioController {
    
    // FXML Controls
    @FXML private Button previousButton;
    @FXML private Button playPauseButton;
    @FXML private Button nextButton;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Slider timeSlider;
    @FXML private Slider volumeSlider;
    @FXML private ImageView volumeIcon;
    @FXML private Label volumePercentageLabel;
    @FXML private HBox controlBar;
    
    // Album art display elements
    @FXML private StackPane albumArtContainer;
    @FXML private ImageView albumArtImageView;
    @FXML private ImageView albumArtImageView2;
    @FXML private Label songTitleLabel;
    @FXML private Label songArtistLabel;
    
    // Icons for play/pause button
    private Image playIcon;
    private Image pauseIcon;
    private ImageView playPauseImageView;
    
    // Services
    private AudioPlayerService audioPlayerService;
    private ListeningStatsService listeningStatsService;
    
    // References
    private ObservableList<Song> currentPlaylist;
    private TableView<Song> songsTableView;
    
    // Callbacks
    private Runnable onStatsUpdate;
    private Runnable onPlaybackStateChange;
    
    /**
     * Sets the UI components that this controller will manage.
     * This should be called before initialize() when not using FXML injection.
     */
    public void setUIComponents(
            Button playPauseButton,
            Button previousButton,
            Button nextButton,
            Slider timeSlider,
            Slider volumeSlider,
            ImageView volumeIcon,
            Label volumePercentageLabel,
            Label currentTimeLabel,
            Label totalTimeLabel,
            StackPane albumArtContainer,
            ImageView albumArtImageView,
            ImageView albumArtImageView2,
            Label songTitleLabel,
            Label songArtistLabel) {
        
        this.playPauseButton = playPauseButton;
        this.previousButton = previousButton;
        this.nextButton = nextButton;
        this.timeSlider = timeSlider;
        this.volumeSlider = volumeSlider;
        this.volumeIcon = volumeIcon;
        this.volumePercentageLabel = volumePercentageLabel;
        this.currentTimeLabel = currentTimeLabel;
        this.totalTimeLabel = totalTimeLabel;
        this.albumArtContainer = albumArtContainer;
        this.albumArtImageView = albumArtImageView;
        this.albumArtImageView2 = albumArtImageView2;
        this.songTitleLabel = songTitleLabel;
        this.songArtistLabel = songArtistLabel;
        
        // Also need to get the control bar from the play/pause button's parent
        if (playPauseButton != null && playPauseButton.getParent() instanceof HBox) {
            this.controlBar = (HBox) playPauseButton.getParent();
        }
    }
    
    /**
     * Initialize the audio controller with required services.
     */
    public void initialize(AudioPlayerService audioPlayerService,
                          ListeningStatsService listeningStatsService,
                          ObservableList<Song> songs,
                          TableView<Song> songsTableView) {
        this.audioPlayerService = audioPlayerService;
        this.listeningStatsService = listeningStatsService;
        this.currentPlaylist = songs;
        this.songsTableView = songsTableView;
        
        setupAudioControls();
        setupKeyboardShortcuts();
    }
    
    /**
     * Set callback for when listening stats are updated.
     */
    public void setOnStatsUpdate(Runnable callback) {
        this.onStatsUpdate = callback;
    }
    
    /**
     * Set callback for when playback state changes.
     */
    public void setOnPlaybackStateChange(Runnable callback) {
        this.onPlaybackStateChange = callback;
    }
    
    private void setupAudioControls() {
        // Initialize play/pause icons
        playIcon = new Image(getClass().getResourceAsStream("/images/icons/play.png"));
        pauseIcon = new Image(getClass().getResourceAsStream("/images/icons/pause.png"));
        
        // Get the ImageView from the play/pause button
        playPauseImageView = (ImageView) playPauseButton.getGraphic();
        
        // Set initial icon
        playPauseImageView.setImage(playIcon);
        
        // Bind play/pause button icon to playing state and track plays
        audioPlayerService.playingProperty().addListener((obs, oldPlaying, newPlaying) -> {
            playPauseImageView.setImage(newPlaying ? pauseIcon : playIcon);
            
            // Track when play starts (not when pausing)
            if (newPlaying && !oldPlaying) {
                Song currentSong = audioPlayerService.getCurrentSong();
                if (currentSong != null) {
                    listeningStatsService.recordPlay(currentSong);
                    if (onStatsUpdate != null) {
                        onStatsUpdate.run();
                    }
                }
            }
            
            if (onPlaybackStateChange != null) {
                onPlaybackStateChange.run();
            }
        });
        
        // Track when songs change while playing
        audioPlayerService.currentSongProperty().addListener((obs, oldSong, newSong) -> {
            if (newSong != null && newSong != oldSong && audioPlayerService.isPlaying()) {
                listeningStatsService.recordPlay(newSong);
                if (onStatsUpdate != null) {
                    onStatsUpdate.run();
                }
            }
            updateAlbumArt(newSong);
            if (songsTableView != null) {
                songsTableView.refresh(); // Refresh to update row highlighting
            }
        });
        
        // Add playback mode buttons
        setupPlaybackModeButtons();
        
        // Setup time slider
        setupTimeSlider();
        
        // Setup volume controls
        setupVolumeControls();
        
        // Bind time labels
        currentTimeLabel.textProperty().bind(
            Bindings.createStringBinding(
                () -> formatDuration((long) audioPlayerService.getCurrentTime()),
                audioPlayerService.currentTimeProperty()
            )
        );
        
        totalTimeLabel.textProperty().bind(
            Bindings.createStringBinding(
                () -> formatDuration((long) audioPlayerService.getTotalTime()),
                audioPlayerService.totalTimeProperty()
            )
        );
        
        // Update table row highlighting when playing state changes
        audioPlayerService.playingProperty().addListener((obs, oldPlaying, newPlaying) -> {
            if (songsTableView != null) {
                songsTableView.refresh();
            }
        });
    }
    
    private void setupPlaybackModeButtons() {
        Button shuffleBtn = PlaybackModeButtons.createShuffleButton(audioPlayerService);
        Button repeatBtn = PlaybackModeButtons.createRepeatButton(audioPlayerService);
        
        // Insert shuffle at beginning and repeat at end
        controlBar.getChildren().add(0, shuffleBtn);
        controlBar.getChildren().add(repeatBtn);
    }
    
    private void setupTimeSlider() {
        // Bind time slider to current time
        timeSlider.valueProperty().bind(audioPlayerService.currentTimeProperty());
        timeSlider.maxProperty().bind(audioPlayerService.totalTimeProperty());
        
        // Set up time slider for seeking (both click and drag)
        timeSlider.setOnMouseClicked(event -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seekTime = (event.getX() / timeSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seekTime);
            }
        });
        
        // Allow dragging to seek
        timeSlider.setOnMouseDragged(event -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seekTime = (event.getX() / timeSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seekTime);
            }
        });
    }
    
    private void setupVolumeControls() {
        // Bind volume slider to volume property
        volumeSlider.valueProperty().bindBidirectional(audioPlayerService.volumeProperty());
        volumeSlider.setMax(1.0); // Volume range 0.0 to 1.0
        
        // Set up volume icon and percentage display
        Image volIcon = new Image(getClass().getResourceAsStream("/images/icons/vol.png"));
        Image muteIcon = new Image(getClass().getResourceAsStream("/images/icons/mute.png"));
        
        // Update volume icon and percentage based on volume value
        audioPlayerService.volumeProperty().addListener((obs, oldVol, newVol) -> {
            double volume = newVol.doubleValue();
            int percentage = (int) Math.round(volume * 100);
            
            // Update percentage label
            volumePercentageLabel.setText(percentage + "%");
            
            // Update icon based on volume
            if (volume == 0) {
                volumeIcon.setImage(muteIcon);
            } else {
                volumeIcon.setImage(volIcon);
            }
        });
        
        // Set initial percentage from current audio volume (avoids overriding persisted value)
        int initialPercentage = (int) Math.round(audioPlayerService.getVolume() * 100);
        volumePercentageLabel.setText(initialPercentage + "%");
    }
    
    private void setupKeyboardShortcuts() {
        // Set up keyboard shortcuts for the main scene
        Platform.runLater(() -> {
            if (playPauseButton.getScene() != null) {
                playPauseButton.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
            }
        });
    }
    
    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case SPACE:
                if (!event.isConsumed()) {
                    handlePlayPause();
                    event.consume();
                }
                break;
            case LEFT:
                if (event.isControlDown()) {
                    handlePrevious();
                    event.consume();
                }
                break;
            case RIGHT:
                if (event.isControlDown()) {
                    handleNext();
                    event.consume();
                }
                break;
            case UP:
                if (event.isControlDown()) {
                    double currentVolume = audioPlayerService.getVolume();
                    audioPlayerService.setVolume(Math.min(1.0, currentVolume + 0.1));
                    event.consume();
                }
                break;
            case DOWN:
                if (event.isControlDown()) {
                    double currentVolume = audioPlayerService.getVolume();
                    audioPlayerService.setVolume(Math.max(0.0, currentVolume - 0.1));
                    event.consume();
                }
                break;
            default:
                break;
        }
    }
    
    @FXML
    void handlePlayPause() {
        if (currentPlaylist.isEmpty()) {
            System.out.println("No songs in library to play");
            return;
        }
        
        // If no song is currently selected, start with the first song
        if (audioPlayerService.getCurrentSong() == null) {
            audioPlayerService.setPlaylist(currentPlaylist);
            if (!currentPlaylist.isEmpty()) {
                audioPlayerService.playTrack(currentPlaylist.get(0));
            }
        } else {
            audioPlayerService.togglePlayPause();
        }
    }
    
    @FXML
    void handlePrevious() {
        audioPlayerService.previousTrack();
    }
    
    @FXML
    void handleNext() {
        audioPlayerService.nextTrack();
    }
    
    /**
     * Play a specific song from the current playlist.
     */
    public void playSelectedSong(Song song) {
        audioPlayerService.setPlaylist(currentPlaylist);
        audioPlayerService.playTrack(song);
    }
    
    /**
     * Stop playback completely.
     */
    public void stopPlayback() {
        audioPlayerService.stop();
    }
    
    /**
     * Get information about the currently playing song.
     */
    public String getCurrentSongInfo() {
        Song currentSong = audioPlayerService.getCurrentSong();
        if (currentSong != null) {
            return String.format("%s - %s (%s)", 
                currentSong.getArtist(), 
                currentSong.getTitle(), 
                currentSong.getAlbum());
        }
        return null;
    }
    
    /**
     * Update the current playlist.
     */
    public void updatePlaylist(ObservableList<Song> songs) {
        this.currentPlaylist = songs;
        audioPlayerService.setPlaylist(songs);
    }
    
    private String formatDuration(long durationInSeconds) {
        long minutes = durationInSeconds / 60;
        long seconds = durationInSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Updates the album art display for the current song.
     */
    private void updateAlbumArt(Song song) {
        if (song == null) {
            // Clear album art and labels when no song
            if (albumArtImageView != null) {
                albumArtImageView.setImage(null);
            }
            if (albumArtImageView2 != null) {
                albumArtImageView2.setImage(null);
            }
            if (songTitleLabel != null) {
                songTitleLabel.setText("");
            }
            if (songArtistLabel != null) {
                songArtistLabel.setText("");
            }
            return;
        }
        
        // Update song info labels
        if (songTitleLabel != null) {
            songTitleLabel.setText(song.getTitle());
        }
        if (songArtistLabel != null) {
            songArtistLabel.setText(song.getArtist());
        }
        
        // Load album art asynchronously
        if (albumArtContainer != null && albumArtImageView != null && albumArtImageView2 != null) {
            AlbumArtLoader.loadAlbumArt(song)
                .thenAcceptAsync(image -> transitionToImage(image), Platform::runLater);
        }
    }
    
    /**
     * Transitions to a new album art image with a fade effect.
     */
    private void transitionToImage(Image newImage) {
        if (albumArtImageView == null || albumArtImageView2 == null || albumArtContainer == null) {
            return;
        }
        
        // Determine which ImageView is currently visible
        ImageView currentView = albumArtImageView.getOpacity() > 0 ? albumArtImageView : albumArtImageView2;
        ImageView nextView = currentView == albumArtImageView ? albumArtImageView2 : albumArtImageView;
        
        // Set the new image on the hidden view
        nextView.setImage(newImage);
        
        // Create fade out transition for current view
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), currentView);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        // Create fade in transition for next view
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), nextView);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        // Play both transitions
        fadeOut.play();
        fadeIn.play();
    }
    
    /**
     * Clean up resources when shutting down.
     */
    public void cleanup() {
        if (audioPlayerService != null) {
            audioPlayerService.dispose();
        }
    }
}
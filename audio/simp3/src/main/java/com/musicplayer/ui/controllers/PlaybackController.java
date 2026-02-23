package com.musicplayer.ui.controllers;

import com.musicplayer.data.models.Song;
import com.musicplayer.services.AudioPlayerService;
import com.musicplayer.services.ListeningStatsService;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Controller responsible for all audio playback functionality. This controller
 * handles play/pause, volume, seeking, album art display, and keyboard
 * shortcuts. It serves as the main interface for playback operations in the
 * refactored architecture.
 */
public class PlaybackController implements IPlaybackController {

    // Core audio controller that handles the actual playback implementation
    private final AudioController audioController;

    // Communication interface for notifying other controllers
    private IControllerCommunication communicationInterface;

    /**
     * Constructor that initializes the underlying audio controller.
     */
    public PlaybackController() {
        this.audioController = new AudioController();
    }

    /**
     * Sets the UI components that this controller will manage. This method
     * delegates to the underlying AudioController.
     *
     * @param playPauseButton The play/pause button
     * @param previousButton The previous track button
     * @param nextButton The next track button
     * @param timeSlider The time/seek slider
     * @param volumeSlider The volume slider
     * @param volumeIcon The volume icon image view
     * @param volumePercentageLabel The volume percentage label
     * @param currentTimeLabel The current time label
     * @param totalTimeLabel The total time label
     * @param albumArtContainer The album art container
     * @param albumArtImageView The primary album art image view
     * @param albumArtImageView2 The secondary album art image view for
     * transitions
     * @param songTitleLabel The song title label
     * @param songArtistLabel The song artist label
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

        audioController.setUIComponents(
                playPauseButton, previousButton, nextButton, timeSlider, volumeSlider,
                volumeIcon, volumePercentageLabel, currentTimeLabel, totalTimeLabel,
                albumArtContainer, albumArtImageView, albumArtImageView2,
                songTitleLabel, songArtistLabel
        );
    }

    /**
     * Initialize the playback controller with required services.
     *
     * @param audioPlayerService The audio player service
     * @param listeningStatsService The listening stats service
     * @param songs The current playlist
     * @param songsTableView The songs table view
     */
    public void initialize(AudioPlayerService audioPlayerService,
            ListeningStatsService listeningStatsService,
            ObservableList<Song> songs,
            TableView<Song> songsTableView) {
        audioController.initialize(audioPlayerService, listeningStatsService, songs, songsTableView);
    }

    /**
     * Set the communication interface for inter-controller communication.
     *
     * @param communicationInterface The communication interface
     */
    public void setCommunicationInterface(IControllerCommunication communicationInterface) {
        this.communicationInterface = communicationInterface;
    }

    @Override
    public void handlePlayPause() {
        audioController.handlePlayPause();
        notifyPlaybackStateChange();
    }

    @Override
    public void handlePrevious() {
        audioController.handlePrevious();
        notifyPlaybackStateChange();
    }

    @Override
    public void handleNext() {
        audioController.handleNext();
        notifyPlaybackStateChange();
    }

    @Override
    public void playSelectedSong(Song song) {
        audioController.playSelectedSong(song);
        notifyCurrentSongChange(song);
        notifyPlaybackStateChange();
        // Force visualizer state update when playing a selected song
        notifyVisualizerUpdate();
    }

    @Override
    public void stopPlayback() {
        audioController.stopPlayback();
        notifyPlaybackStateChange();
    }

    @Override
    public String getCurrentSongInfo() {
        return audioController.getCurrentSongInfo();
    }

    @Override
    public void updatePlaylist(ObservableList<Song> songs) {
        audioController.updatePlaylist(songs);
        notifyPlaylistUpdate(songs);
    }

    @Override
    public void setOnStatsUpdate(Runnable callback) {
        audioController.setOnStatsUpdate(() -> {
            callback.run();
            notifyStatsUpdate();
        });
    }

    @Override
    public void setOnPlaybackStateChange(Runnable callback) {
        audioController.setOnPlaybackStateChange(() -> {
            callback.run();
            notifyPlaybackStateChange();
        });
    }

    @Override
    public void cleanup() {
        audioController.cleanup();
    }

    /**
     * Get the underlying audio controller for advanced operations. This method
     * provides access to the internal AudioController for cases where direct
     * access is needed.
     *
     * @return The underlying AudioController instance
     */
    public AudioController getAudioController() {
        return audioController;
    }

    // Private methods for communication interface notifications
    private void notifyPlaybackStateChange() {
        if (communicationInterface != null) {
            communicationInterface.onPlaybackStateChanged();
        }
    }

    private void notifyCurrentSongChange(Song song) {
        if (communicationInterface != null) {
            communicationInterface.onCurrentSongChanged(song);
        }
    }

    private void notifyPlaylistUpdate(ObservableList<Song> playlist) {
        if (communicationInterface != null) {
            communicationInterface.onPlaylistUpdated(playlist);
        }
    }

    private void notifyStatsUpdate() {
        if (communicationInterface != null) {
            communicationInterface.onListeningStatsUpdated();
        }
    }

    private void notifyRefreshSongTable() {
        if (communicationInterface != null) {
            communicationInterface.requestRefreshSongTable();
        }
    }

    private void notifyVisualizerUpdate() {
        if (communicationInterface != null) {
            communicationInterface.onVisualizerUpdateRequired();
        }
    }
}

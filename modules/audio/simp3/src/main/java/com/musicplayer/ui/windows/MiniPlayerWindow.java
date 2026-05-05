package com.musicplayer.ui.windows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import com.musicplayer.core.playlist.PlaylistEngine;
import com.musicplayer.data.models.Album;
import com.musicplayer.data.models.Song;
import com.musicplayer.data.repositories.AlbumRepository;
import com.musicplayer.services.AudioPlayerService;
import com.musicplayer.services.FavoritesService;
import com.musicplayer.services.PlaylistManager;
import com.musicplayer.services.SettingsService;
import com.musicplayer.ui.components.AudioVisualizerPane;
import com.musicplayer.ui.dialogs.PlaylistSelectionPopup;
import com.musicplayer.ui.util.AlbumArtLoader;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Compact mini player window that can be always-on-top.
 * Provides essential playback controls in a minimal interface.
 */
public class MiniPlayerWindow {
    
    private final Stage miniStage;
    private final AudioPlayerService audioPlayerService;
    private final Stage mainStage;
    private final SettingsService settingsService;
    private final FavoritesService favoritesService;
    private final PlaylistManager playlistManager;
    private final AlbumRepository albumRepository;
    
    // UI Components
    private Label titleLabel;
    private Label artistLabel;
    private Button playPauseButton;
    private Button previousButton;
    private Button nextButton;
    private Button shuffleButton;
    private Button repeatButton;
    private Button moreOptionsButton;
    private Button queueButton;
    private Button pinButton;
    private Button closeButton;
    private Button compactModeButton;
    private Slider progressSlider;
    private Slider volumeSlider;
    private StackPane albumArtContainer;
    private ImageView albumArt;
    private ImageView albumArtTransition;
    private AudioVisualizerPane visualizer;
    private Label timeLabel;
    
    // Queue view components
    private VBox queuePanel;
    private ListView<Song> queueListView;
    private boolean isQueueVisible = false;
    private VBox mainContainer;
    private CompactMiniPlayerWindow compactWindow;
    
    // Icons
    private final Image playIcon;
    private final Image pauseIcon;
    private final Image previousIcon;
    private final Image nextIcon;
    private final Image shuffleIcon;
    private final Image repeatIcon;
    private final Image pinIcon;
    private final Image unpinIcon;
    private final Image closeIcon;
    private final Image defaultAlbumArt;
    
    // Window state
    private boolean isPinned = true;
    private double xOffset = 0;
    private double yOffset = 0;
    
    // Sleep timer
    private javafx.animation.Timeline sleepTimer;
    private Label sleepTimerLabel;
    private int remainingMinutes = 0;
    
    // Logger for album art loading
    private static final Logger LOGGER = Logger.getLogger(MiniPlayerWindow.class.getName());
    
    public MiniPlayerWindow(AudioPlayerService audioPlayerService, Stage mainStage,
                           SettingsService settingsService, FavoritesService favoritesService,
                           PlaylistManager playlistManager, AlbumRepository albumRepository) {
        this.audioPlayerService = audioPlayerService;
        this.mainStage = mainStage;
        this.settingsService = settingsService;
        this.favoritesService = favoritesService;
        this.playlistManager = playlistManager;
        this.albumRepository = albumRepository;
        this.miniStage = new Stage();
        
        // Load icons
        playIcon = new Image(getClass().getResourceAsStream("/images/icons/play.png"));
        pauseIcon = new Image(getClass().getResourceAsStream("/images/icons/pause.png"));
        previousIcon = new Image(getClass().getResourceAsStream("/images/icons/previous.png"));
        nextIcon = new Image(getClass().getResourceAsStream("/images/icons/next.png"));
        shuffleIcon = new Image(getClass().getResourceAsStream("/images/icons/shuffle.png"));
        repeatIcon = new Image(getClass().getResourceAsStream("/images/icons/repeat.png"));
        // Use pin icon for both states – we'll rotate it to indicate unpinned
        pinIcon = new Image(getClass().getResourceAsStream("/images/icons/pin.png"));
        unpinIcon = pinIcon;
        closeIcon = new Image(getClass().getResourceAsStream("/images/icons/remove.png"));
        defaultAlbumArt = new Image(getClass().getResourceAsStream("/images/icons/album_placeholder.png"));
        
        initializeUI();
        setupBindings();
        setupDragHandling();
    }
    
    private void initializeUI() {
        // Use a transparent stage so rounded corners are perfectly smooth with no rectangular backdrop
        miniStage.initStyle(StageStyle.TRANSPARENT);
        miniStage.setTitle("SiMP3 Mini Player");
        miniStage.setAlwaysOnTop(isPinned);
        miniStage.setResizable(false);
        
        // Album art with container for transitions
        albumArtContainer = new StackPane();
        albumArtContainer.setPrefSize(80, 80);
        albumArtContainer.setMaxSize(80, 80);
        albumArtContainer.setMinSize(80, 80);
        
        albumArt = new ImageView(defaultAlbumArt);
        albumArt.setFitWidth(80);
        albumArt.setFitHeight(80);
        albumArt.setPreserveRatio(true);
        albumArt.setSmooth(true);
        
        albumArtTransition = new ImageView();
        albumArtTransition.setFitWidth(80);
        albumArtTransition.setFitHeight(80);
        albumArtTransition.setPreserveRatio(true);
        albumArtTransition.setSmooth(true);
        albumArtTransition.setOpacity(0);
        
        // Add shadow effect to album art
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5.0);
        shadow.setOffsetX(2.0);
        shadow.setOffsetY(2.0);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        albumArtContainer.setEffect(shadow);
        
        // Create visualizer with settings
        visualizer = new AudioVisualizerPane(settingsService.getSettings());
        visualizer.setMouseTransparent(true); // Allow clicks to pass through
        visualizer.setPrefSize(80, 80);
        visualizer.setMaxSize(80, 80);
        visualizer.setMinSize(80, 80);
        
        // Ensure visualizer fills the container
        StackPane.setAlignment(visualizer, javafx.geometry.Pos.CENTER);
        
        // Add components in correct order - album art at bottom, visualizer on top
        albumArtContainer.getChildren().addAll(albumArt, albumArtTransition, visualizer);
        
        // Ensure proper z-ordering
        albumArt.toBack();
        visualizer.toFront();
        
        // Start visualizer immediately
        visualizer.start();
        connectSpectrumListener();
        
        // Song info
        titleLabel = new Label("No song playing");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        titleLabel.setMaxWidth(200);
        titleLabel.setEllipsisString("...");
        
        artistLabel = new Label("");
        artistLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        artistLabel.setMaxWidth(200);
        artistLabel.setEllipsisString("...");
        
        VBox songInfo = new VBox(2);
        songInfo.getChildren().addAll(titleLabel, artistLabel);
        songInfo.setAlignment(Pos.CENTER_LEFT);
        
        // Time label
        timeLabel = new Label("0:00 / 0:00");
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        
        // Progress slider
        progressSlider = new Slider();
        progressSlider.setPrefWidth(200);
        progressSlider.setMaxHeight(5);
        progressSlider.setStyle("-fx-control-inner-background: #cccccc;");
        
        // Control buttons
        previousButton = createControlButton(previousIcon, "Previous");
        playPauseButton = createControlButton(playIcon, "Play/Pause");
        nextButton = createControlButton(nextIcon, "Next");
        
        // Playback mode buttons
        shuffleButton = createModeButton(shuffleIcon, "Shuffle");
        repeatButton = createModeButton(repeatIcon, "Repeat");
        updateModeButtons();
        
        // Volume slider (compact)
        volumeSlider = new Slider(0, 1, 0.5);
        volumeSlider.setPrefWidth(60);
        volumeSlider.setMaxHeight(5);
        volumeSlider.getStyleClass().add("volume-slider");
        
        Label volumeLabel = new Label("🔊");
        volumeLabel.setStyle("-fx-font-size: 12px;");
        
        HBox volumeControl = new HBox(3);
        volumeControl.setAlignment(Pos.CENTER);
        volumeControl.getChildren().addAll(volumeLabel, volumeSlider);
        
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER);
        controls.getChildren().addAll(previousButton, playPauseButton, nextButton, shuffleButton, repeatButton);
        
        // Window controls
        moreOptionsButton = createWindowButton("⋮", "More Options");
        moreOptionsButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; " +
                                  "-fx-background-color: transparent; -fx-border-color: transparent; " +
                                  "-fx-padding: 2; -fx-cursor: hand;");
        queueButton = createWindowButton("☰", "Show Queue");
        queueButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; " +
                            "-fx-background-color: transparent; -fx-border-color: transparent; " +
                            "-fx-padding: 2; -fx-cursor: hand;");
        pinButton = createWindowButton(isPinned ? pinIcon : unpinIcon, "Toggle Always on Top");
        // Rotate to indicate state when not pinned
        ((ImageView) pinButton.getGraphic()).setRotate(isPinned ? 0 : 45);
        closeButton = createWindowButton(closeIcon, "Close Mini Player");
        
        HBox windowControls = new HBox(5);
        windowControls.setAlignment(Pos.CENTER_RIGHT);
        // Add compact mode switch button
        compactModeButton = createWindowButton("◱", "Switch to Compact Mini Player");
        windowControls.getChildren().addAll(queueButton, moreOptionsButton, compactModeButton, pinButton, closeButton);
        
        // Layout
        VBox centerContent = new VBox(3);
        centerContent.setAlignment(Pos.CENTER_LEFT);
        centerContent.getChildren().addAll(songInfo, timeLabel, progressSlider, controls);
        centerContent.setPadding(new Insets(5));
        
        VBox rightContent = new VBox(5);
        rightContent.setAlignment(Pos.CENTER);
        rightContent.getChildren().addAll(volumeControl, windowControls);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox mainLayout = new HBox(10);
        mainLayout.setAlignment(Pos.CENTER_LEFT);
        mainLayout.setPadding(new Insets(10));
        mainLayout.getChildren().addAll(albumArtContainer, centerContent, spacer, rightContent);
        
        // Create queue panel
        setupQueuePanel();
        
        // Main container that holds both player and queue
        mainContainer = new VBox(0);
        mainContainer.getStyleClass().add("mini-player-root");
        mainContainer.getChildren().addAll(mainLayout, queuePanel);

        // Clip the container to rounded rect so all children respect rounded corners
        Rectangle clip = new Rectangle();
        // Match CSS radius in mini-player.css (.mini-player-root has radius 10)
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        clip.widthProperty().bind(mainContainer.widthProperty());
        clip.heightProperty().bind(mainContainer.heightProperty());
        mainContainer.setClip(clip);
        
        Scene scene = new Scene(mainContainer, 480, 120);
        // Make scene background transparent to avoid any rigid rectangular backdrop
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/mini-player.css").toExternalForm());
        miniStage.setScene(scene);
        
        // Add context menu
        setupContextMenu(mainLayout);
        
        // No longer need visualizer toggle - it's always on
        
        // Setup more options menu
        setupMoreOptionsMenu();
        
        // Setup queue button action
        queueButton.setOnAction(e -> toggleQueue());

        // Setup compact mode switch
        compactModeButton.setOnAction(e -> switchToCompact());
        
        // Load saved position or use default
        loadWindowPosition();
        
        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
    }
    
    private Button createControlButton(Image icon, String tooltip) {
        Button button = new Button();
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(20);
        imageView.setFitHeight(20);
        button.setGraphic(imageView);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("control-button");
        
        // Add click animation
        button.setOnMousePressed(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(100), button);
            scaleDown.setToX(0.9);
            scaleDown.setToY(0.9);
            scaleDown.play();
        });
        
        button.setOnMouseReleased(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(100), button);
            scaleUp.setToX(1.0);
            scaleUp.setToY(1.0);
            scaleUp.play();
        });
        
        return button;
    }
    
    private Button createWindowButton(Image icon, String tooltip) {
        Button button = new Button();
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        button.setGraphic(imageView);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("window-button");
        
        return button;
    }
    
    private Button createWindowButton(String text, String tooltip) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("window-button");
        
        return button;
    }
    
    private Button createModeButton(Image icon, String tooltip) {
        Button button = new Button();
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        button.setGraphic(imageView);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("mode-button");
        
        // Add click animation
        button.setOnMousePressed(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(100), button);
            scaleDown.setToX(0.9);
            scaleDown.setToY(0.9);
            scaleDown.play();
        });
        
        button.setOnMouseReleased(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(100), button);
            scaleUp.setToX(1.0);
            scaleUp.setToY(1.0);
            scaleUp.play();
        });
        
        return button;
    }
    
    private void setupBindings() {
        // Play/pause button
        playPauseButton.setOnAction(e -> audioPlayerService.togglePlayPause());
        previousButton.setOnAction(e -> audioPlayerService.previousTrack());
        nextButton.setOnAction(e -> audioPlayerService.nextTrack());
        
        // Shuffle and repeat buttons
        shuffleButton.setOnAction(e -> {
            audioPlayerService.setShuffle(!audioPlayerService.isShuffle());
            updateModeButtons();
        });
        
        repeatButton.setOnAction(e -> {
            PlaylistEngine.RepeatMode currentMode = audioPlayerService.getRepeatMode();
            PlaylistEngine.RepeatMode nextMode;
            switch (currentMode) {
                case NONE:
                    nextMode = PlaylistEngine.RepeatMode.ALL;
                    break;
                case ALL:
                    nextMode = PlaylistEngine.RepeatMode.ONE;
                    break;
                case ONE:
                default:
                    nextMode = PlaylistEngine.RepeatMode.NONE;
                    break;
            }
            audioPlayerService.setRepeatMode(nextMode);
            updateModeButtons();
        });
        
        // Update play/pause icon and visualizer state
        audioPlayerService.playingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            ImageView imageView = (ImageView) playPauseButton.getGraphic();
            imageView.setImage(isPlaying ? pauseIcon : playIcon);
            
            // Update visualizer state based on playback and settings
            updateVisualizerState();
        });
        
        // Update song info
        audioPlayerService.currentSongProperty().addListener((obs, oldSong, newSong) -> {
            updateSongInfo(newSong);
            // Update visualizer state when song changes
            updateVisualizerState();
        });
        
        // Progress slider - remove bidirectional binding to prevent conflicts
        progressSlider.maxProperty().bind(audioPlayerService.totalTimeProperty());
        
        // Update slider position from audio service
        audioPlayerService.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!progressSlider.isValueChanging()) {
                progressSlider.setValue(newTime.doubleValue());
            }
        });
        
        // Seek on click and drag
        progressSlider.setOnMousePressed(event -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seekTime = (event.getX() / progressSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seekTime);
            }
        });
        
        progressSlider.setOnMouseDragged(event -> {
            if (audioPlayerService.getTotalTime() > 0) {
                double seekTime = (event.getX() / progressSlider.getWidth()) * audioPlayerService.getTotalTime();
                audioPlayerService.seek(seekTime);
            }
        });
        
        // Volume control
        volumeSlider.valueProperty().bindBidirectional(audioPlayerService.volumeProperty());
        
        // Update time label
        audioPlayerService.currentTimeProperty().addListener((obs, oldTime, newTime) -> updateTimeLabel());
        audioPlayerService.totalTimeProperty().addListener((obs, oldTime, newTime) -> updateTimeLabel());
        
        // Pin button
        pinButton.setOnAction(e -> togglePin());
        
        // Close button
        closeButton.setOnAction(e -> hide());
        
        // Double-click to restore main window
        miniStage.getScene().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                restoreMainWindow();
            }
        });
    }

    /**
     * Switch to the ultra-compact mini player window.
     */
    private void switchToCompact() {
        if (compactWindow == null) {
            compactWindow = new CompactMiniPlayerWindow(audioPlayerService, settingsService);
            compactWindow.setOnSwitchToNormal(() -> {
                // Position this window to where compact currently is
                if (compactWindow.getStage() != null) {
                    miniStage.setX(compactWindow.getStage().getX());
                    miniStage.setY(compactWindow.getStage().getY());
                }
                // First hide compact (detaches its spectrum listener), then show this (attaches ours)
                compactWindow.hide();
                if (!miniStage.isShowing()) {
                    show();
                }
            });
        }
        // Position compact window roughly where the current window is
        double x = miniStage.getX();
        double y = miniStage.getY();

        // Detach our listener before showing compact to avoid overlap
        disconnectSpectrumListener();

        compactWindow.show();
        if (compactWindow.getStage() != null) {
            compactWindow.getStage().setX(x);
            compactWindow.getStage().setY(y);
        }

        // Now hide this window (already disconnected)
        miniStage.hide();
    }
    
    private void setupDragHandling() {
        VBox mainContainer = (VBox) miniStage.getScene().getRoot();
        
        mainContainer.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        
        mainContainer.setOnMouseDragged(event -> {
            miniStage.setX(event.getScreenX() - xOffset);
            miniStage.setY(event.getScreenY() - yOffset);
        });
        
        // Save position when drag ends
        mainContainer.setOnMouseReleased(event -> {
            saveWindowPosition();
        });
    }
    
    private void setupContextMenu(HBox mainLayout) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem showMainWindow = new MenuItem("Show Main Window");
        showMainWindow.setOnAction(e -> restoreMainWindow());
        
        // Visualizer is always on, no toggle needed
        
        MenuItem alwaysOnTop = new MenuItem("Always on Top");
        alwaysOnTop.setOnAction(e -> togglePin());
        
        MenuItem separator = new MenuItem();
        separator.setDisable(true);
        
        MenuItem close = new MenuItem("Close Mini Player");
        close.setOnAction(e -> hide());
        
        contextMenu.getItems().addAll(showMainWindow, alwaysOnTop, separator, close);
        
        // Set context menu on the main container instead of mainLayout
        mainContainer.setOnContextMenuRequested(event -> {
            alwaysOnTop.setText(isPinned ? "✓ Always on Top" : "Always on Top");
            contextMenu.show(mainContainer, event.getScreenX(), event.getScreenY());
        });
    }
    
    private void setupMoreOptionsMenu() {
        ContextMenu moreOptionsMenu = new ContextMenu();
        moreOptionsMenu.getStyleClass().add("more-options-menu");
        
        MenuItem addToFavorites = new MenuItem("❤ Add to Favorites");
        addToFavorites.setOnAction(e -> {
            Song currentSong = audioPlayerService.getCurrentSong();
            if (currentSong != null) {
                boolean isFavorite = favoritesService.toggleFavorite(currentSong);
                addToFavorites.setText(isFavorite ? "💔 Remove from Favorites" : "❤ Add to Favorites");
            }
        });
        
        MenuItem addToPlaylist = new MenuItem("➕ Add to Playlist");
        addToPlaylist.setOnAction(e -> {
            Song currentSong = audioPlayerService.getCurrentSong();
            if (currentSong != null && playlistManager != null) {
                // Convert playlists to ObservableList
                javafx.collections.ObservableList<com.musicplayer.data.models.Playlist> playlists =
                    javafx.collections.FXCollections.observableArrayList(playlistManager.getAllPlaylists());
                
                PlaylistSelectionPopup.show(miniStage, playlists).ifPresent(playlist -> {
                    playlistManager.addSongToPlaylist(playlist.getId(), currentSong);
                });
            }
        });
        
        MenuItem showInLibrary = new MenuItem("📁 Show in Library");
        showInLibrary.setOnAction(e -> {
            // Fire an event or call a method to show the song in the main window
            showSongInMainWindow();
        });
        
        Menu sleepTimerMenu = new Menu("⏱ Sleep Timer");
        MenuItem timer15 = new MenuItem("15 minutes");
        MenuItem timer30 = new MenuItem("30 minutes");
        MenuItem timer45 = new MenuItem("45 minutes");
        MenuItem timer60 = new MenuItem("60 minutes");
        MenuItem timerOff = new MenuItem("Turn Off");
        
        timer15.setOnAction(e -> setSleepTimer(15));
        timer30.setOnAction(e -> setSleepTimer(30));
        timer45.setOnAction(e -> setSleepTimer(45));
        timer60.setOnAction(e -> setSleepTimer(60));
        timerOff.setOnAction(e -> cancelSleepTimer());
        
        sleepTimerMenu.getItems().addAll(timer15, timer30, timer45, timer60,
                                         new SeparatorMenuItem(), timerOff);
        
        moreOptionsMenu.getItems().addAll(addToFavorites, addToPlaylist,
                                         new SeparatorMenuItem(), showInLibrary,
                                         new SeparatorMenuItem(), sleepTimerMenu);
        
        // Update favorites text when menu is about to show
        moreOptionsMenu.setOnShowing(e -> {
            Song currentSong = audioPlayerService.getCurrentSong();
            if (currentSong != null) {
                boolean isFavorite = favoritesService.isFavorite(currentSong.getId());
                addToFavorites.setText(isFavorite ? "💔 Remove from Favorites" : "❤ Add to Favorites");
            }
        });
        
        moreOptionsButton.setOnAction(e -> {
            moreOptionsMenu.show(moreOptionsButton,
                               javafx.geometry.Side.BOTTOM, 0, 0);
        });
    }
    
    private void setupQueuePanel() {
        queuePanel = new VBox(5);
        queuePanel.getStyleClass().add("queue-panel");
        queuePanel.setPadding(new Insets(10));
        queuePanel.setVisible(false);
        queuePanel.setManaged(false);
        
        Label queueTitle = new Label("Up Next");
        queueTitle.getStyleClass().add("queue-title");
        
        queueListView = new ListView<>();
        queueListView.getStyleClass().add("queue-list");
        queueListView.setPrefHeight(200);
        queueListView.setCellFactory(lv -> new QueueCell());
        
        // Update queue when current song changes
        audioPlayerService.currentSongProperty().addListener((obs, oldSong, newSong) -> {
            updateQueueView();
        });
        
        queuePanel.getChildren().addAll(queueTitle, queueListView);
    }
    
    private void updateQueueView() {
        queueListView.getItems().clear();
        
        List<Song> playlist = audioPlayerService.getCurrentPlaylist();
        if (playlist != null && !playlist.isEmpty()) {
            int currentIndex = audioPlayerService.getCurrentTrackIndex();
            
            // Add upcoming songs
            for (int i = currentIndex + 1; i < playlist.size() && i < currentIndex + 10; i++) {
                queueListView.getItems().add(playlist.get(i));
            }
            
            // If shuffle is on and we're near the end, show some songs from the beginning
            if (audioPlayerService.isShuffle() && queueListView.getItems().size() < 5) {
                for (int i = 0; i < Math.min(5 - queueListView.getItems().size(), currentIndex); i++) {
                    queueListView.getItems().add(playlist.get(i));
                }
            }
        }
        
        // Also add any queued songs
        List<Song> queue = audioPlayerService.getQueue();
        if (!queue.isEmpty()) {
            // Add a separator if we have playlist items
            if (!queueListView.getItems().isEmpty()) {
                // We'll handle this visually in the cell renderer
            }
            queueListView.getItems().addAll(queue);
        }
    }
    
    private void toggleQueue() {
        isQueueVisible = !isQueueVisible;
        
        if (isQueueVisible) {
            // Show queue
            queuePanel.setVisible(true);
            queuePanel.setManaged(true);
            
            // Set initial position (hidden below the visible area)
            queuePanel.setTranslateY(200);
            queuePanel.setOpacity(0);
            
            // Animate window height expansion
            miniStage.setHeight(320); // 120 (player) + 200 (queue)
            
            // Update queue view
            updateQueueView();
            
            // Update button text
            queueButton.setText("☰");
            queueButton.setTooltip(new Tooltip("Hide Queue"));
            
            // Animate queue panel sliding up from bottom with fade in
            TranslateTransition slideUp = new TranslateTransition(Duration.millis(300), queuePanel);
            slideUp.setFromY(200);
            slideUp.setToY(0);
            
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), queuePanel);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            
            slideUp.play();
            fadeIn.play();
        } else {
            // Hide queue - slide down and fade out
            TranslateTransition slideDown = new TranslateTransition(Duration.millis(300), queuePanel);
            slideDown.setFromY(0);
            slideDown.setToY(200);
            
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), queuePanel);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            
            slideDown.setOnFinished(e -> {
                queuePanel.setVisible(false);
                queuePanel.setManaged(false);
                miniStage.setHeight(120);
            });
            
            slideDown.play();
            fadeOut.play();
            
            // Update button text
            queueButton.setText("☰");
            queueButton.setTooltip(new Tooltip("Show Queue"));
        }
    }
    
    /**
     * Custom cell for queue list view
     */
    private class QueueCell extends ListCell<Song> {
        @Override
        protected void updateItem(Song song, boolean empty) {
            super.updateItem(song, empty);
            
            if (empty || song == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox cellContent = new HBox(10);
                cellContent.setAlignment(Pos.CENTER_LEFT);
                
                // Song number in queue
                Label numberLabel = new Label(String.valueOf(getIndex() + 1));
                numberLabel.getStyleClass().add("queue-number");
                numberLabel.setMinWidth(20);
                
                // Song info
                VBox songInfo = new VBox(2);
                Label titleLabel = new Label(song.getTitle());
                titleLabel.getStyleClass().add("queue-song-title");
                titleLabel.setMaxWidth(300);
                titleLabel.setEllipsisString("...");
                
                Label artistLabel = new Label(song.getArtist());
                artistLabel.getStyleClass().add("queue-song-artist");
                artistLabel.setMaxWidth(300);
                artistLabel.setEllipsisString("...");
                
                songInfo.getChildren().addAll(titleLabel, artistLabel);
                
                // Duration
                Label durationLabel = new Label(formatTime(song.getDuration()));
                durationLabel.getStyleClass().add("queue-song-duration");
                
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                cellContent.getChildren().addAll(numberLabel, songInfo, spacer, durationLabel);
                
                setGraphic(cellContent);
                setText(null);
                
                // Add hover effect
                setOnMouseEntered(e -> cellContent.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1);"));
                setOnMouseExited(e -> cellContent.setStyle(""));
                
                // Double-click to play
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        // Jump to this song in the queue
                        int targetIndex = audioPlayerService.getCurrentTrackIndex() + getIndex() + 1;
                        if (targetIndex < audioPlayerService.getCurrentPlaylist().size()) {
                            audioPlayerService.playTrack(targetIndex);
                        }
                    }
                });
            }
        }
    }
    
    private void setSleepTimer(int minutes) {
        cancelSleepTimer();
        remainingMinutes = minutes;
        
        // Create sleep timer label if it doesn't exist
        if (sleepTimerLabel == null) {
            sleepTimerLabel = new Label();
            sleepTimerLabel.getStyleClass().add("sleep-timer-label");
            sleepTimerLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #ff9800; -fx-font-weight: bold;");
        }
        
        // Update label and add to UI
        updateSleepTimerLabel();
        if (!((VBox) ((HBox) mainContainer.getChildren().get(0)).getChildren().get(1)).getChildren().contains(sleepTimerLabel)) {
            ((VBox) ((HBox) mainContainer.getChildren().get(0)).getChildren().get(1)).getChildren().add(1, sleepTimerLabel);
        }
        
        // Create timeline with minute updates
        sleepTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                Duration.minutes(1),
                e -> {
                    remainingMinutes--;
                    if (remainingMinutes <= 0) {
                        audioPlayerService.pause();
                        cancelSleepTimer();
                        Platform.runLater(() -> {
                            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.INFORMATION,
                                "Sleep timer has stopped playback."
                            );
                            alert.setTitle("Sleep Timer");
                            alert.setHeaderText(null);
                            alert.showAndWait();
                        });
                    } else {
                        updateSleepTimerLabel();
                    }
                }
            )
        );
        sleepTimer.setCycleCount(minutes);
        sleepTimer.play();
        
        // Show confirmation
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION,
            "Sleep timer set for " + minutes + " minutes."
        );
        alert.setTitle("Sleep Timer");
        alert.setHeaderText(null);
        alert.show();
    }
    
    private void updateSleepTimerLabel() {
        if (sleepTimerLabel != null) {
            sleepTimerLabel.setText("⏱ " + remainingMinutes + " min");
        }
    }
    
    private void cancelSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.stop();
            sleepTimer = null;
        }
        remainingMinutes = 0;
        if (sleepTimerLabel != null) {
            ((VBox) ((HBox) mainContainer.getChildren().get(0)).getChildren().get(1)).getChildren().remove(sleepTimerLabel);
        }
    }
    
    private void updateModeButtons() {
        // Update shuffle button
        boolean isShuffled = audioPlayerService.isShuffle();
        shuffleButton.setOpacity(isShuffled ? 1.0 : 0.5);
        shuffleButton.getStyleClass().removeAll("mode-button-active", "mode-button-inactive");
        shuffleButton.getStyleClass().add(isShuffled ? "mode-button-active" : "mode-button-inactive");
        
        // Update repeat button
        PlaylistEngine.RepeatMode repeatMode = audioPlayerService.getRepeatMode();
        double repeatOpacity = repeatMode == PlaylistEngine.RepeatMode.NONE ? 0.5 : 1.0;
        repeatButton.setOpacity(repeatOpacity);
        repeatButton.getStyleClass().removeAll("mode-button-active", "mode-button-inactive", "mode-button-repeat-one");
        
        switch (repeatMode) {
            case ALL:
                repeatButton.getStyleClass().add("mode-button-active");
                repeatButton.setTooltip(new Tooltip("Repeat All"));
                break;
            case ONE:
                repeatButton.getStyleClass().addAll("mode-button-active", "mode-button-repeat-one");
                repeatButton.setTooltip(new Tooltip("Repeat One"));
                break;
            case NONE:
            default:
                repeatButton.getStyleClass().add("mode-button-inactive");
                repeatButton.setTooltip(new Tooltip("Repeat Off"));
                break;
        }
    }
    
    private void updateTimeLabel() {
        double current = audioPlayerService.getCurrentTime();
        double total = audioPlayerService.getTotalTime();
        timeLabel.setText(formatTime(current) + " / " + formatTime(total));
    }
    
    private String formatTime(double seconds) {
        int mins = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", mins, secs);
    }
    
    private void updateSongInfo(Song song) {
        if (song != null) {
            titleLabel.setText(song.getTitle());
            artistLabel.setText(song.getArtist() + " • " + song.getAlbum());
            
            // Load album art asynchronously
            loadAlbumArt(song);
            
            // No tooltip needed since visualizer is always on
        } else {
            titleLabel.setText("No song playing");
            artistLabel.setText("");
            transitionToImage(defaultAlbumArt);
        }
        updateTimeLabel();
    }
    
    /**
     * Load album art from the song's file metadata.
     * @param song The song to load album art for
     */
    private void loadAlbumArt(Song song) {
        // Try to get album from repository to load custom cover art if present
        CompletableFuture.runAsync(() -> {
            if (albumRepository != null && song.getAlbum() != null) {
                Album album = albumRepository.findAll().stream()
                        .filter(a -> a.getTitle() != null && a.getTitle().equalsIgnoreCase(song.getAlbum()))
                        .findFirst().orElse(null);
                if (album != null) {
                    AlbumArtLoader.loadAlbumArt(album).thenAcceptAsync(img -> {
                        if (img != null) {
                            transitionToImage(img);
                        } else {
                            // fallback to metadata path below
                            loadAlbumArtFromMetadata(song);
                        }
                    }, Platform::runLater);
                    return;
                }
            }
            // If no repository album found, fallback to metadata
            loadAlbumArtFromMetadata(song);
        });
    }

    private void loadAlbumArtFromMetadata(Song song) {
        CompletableFuture.supplyAsync(() -> {
            try {
                File audioFile = new File(song.getFilePath());
                if (!audioFile.exists()) {
                    return null;
                }

                AudioFile f = AudioFileIO.read(audioFile);
                Tag tag = f.getTag();

                if (tag != null) {
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        byte[] imageData = artwork.getBinaryData();
                        return new Image(new ByteArrayInputStream(imageData));
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load album art for: " + song.getFilePath(), e);
            }
            return null;
        }).thenAcceptAsync(img -> {
            if (img != null) {
                transitionToImage(img);
            } else {
                transitionToImage(defaultAlbumArt);
            }
        }, Platform::runLater);
    }
    
    /**
     * Transition to a new album art image with fade effect.
     * @param newImage The new image to display
     */
    private void transitionToImage(Image newImage) {
        // Set the new image to the transition ImageView
        albumArtTransition.setImage(newImage);
        
        // Fade out current image and fade in new image
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), albumArt);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), albumArtTransition);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        fadeOut.setOnFinished(e -> {
            // Swap the images
            albumArt.setImage(newImage);
            albumArt.setOpacity(1.0);
            albumArtTransition.setOpacity(0.0);
        });
        
        fadeOut.play();
        fadeIn.play();
    }
    
    private void togglePin() {
        isPinned = !isPinned;
        miniStage.setAlwaysOnTop(isPinned);
        
        ImageView imageView = (ImageView) pinButton.getGraphic();
        imageView.setImage(pinIcon); // same icon
        imageView.setRotate(isPinned ? 0 : 45);
        pinButton.setTooltip(new Tooltip(isPinned ? "Disable Always on Top" : "Enable Always on Top"));
    }
    
    private void restoreMainWindow() {
        if (mainStage.isIconified()) {
            mainStage.setIconified(false);
        }
        mainStage.show();
        mainStage.toFront();
        hide();
    }
    
    /**
     * Show the current song in the main window library view.
     * This method communicates with the main window to highlight/show the current song.
     */
    private void showSongInMainWindow() {
        Song currentSong = audioPlayerService.getCurrentSong();
        if (currentSong != null) {
            // First restore the main window
            if (mainStage.isIconified()) {
                mainStage.setIconified(false);
            }
            mainStage.show();
            mainStage.toFront();
            
            // Fire a custom event that the main controller can listen to
            mainStage.fireEvent(new ShowSongInLibraryEvent(currentSong));
            
            // Hide mini player
            hide();
        }
    }
    
    /**
     * Custom event for showing a song in the library.
     */
    public static class ShowSongInLibraryEvent extends javafx.event.Event {
        public static final javafx.event.EventType<ShowSongInLibraryEvent> SHOW_SONG_IN_LIBRARY =
            new javafx.event.EventType<>(javafx.event.Event.ANY, "SHOW_SONG_IN_LIBRARY");
        
        private final Song song;
        
        public ShowSongInLibraryEvent(Song song) {
            super(SHOW_SONG_IN_LIBRARY);
            this.song = song;
        }
        
        public Song getSong() {
            return song;
        }
    }
    
    public void show() {
        // Update current song info before showing
        updateSongInfo(audioPlayerService.getCurrentSong());
        
        // Connect spectrum listener and show the window
        connectSpectrumListener();
        miniStage.show();
        
        // Update visualizer state based on current settings
        updateVisualizerState();
        
        // Ensure play/pause icon reflects current playback state immediately
        syncPlayPauseIcon();
    }
    
    public void hide() {
        // Disconnect spectrum listener when hiding the window
        disconnectSpectrumListener();
        
        // Save window position and hide
        saveWindowPosition();
        miniStage.hide();
    }
    
    public boolean isShowing() {
        return miniStage.isShowing();
    }
    
    public Stage getStage() {
        return miniStage;
    }
    
    // Visualizer is always active, no need for this method
    
    /**
     * Save the current window position to settings.
     */
    private void saveWindowPosition() {
        if (settingsService != null) {
            settingsService.setMiniPlayerPosition(miniStage.getX(), miniStage.getY());
        }
    }
    
    /**
     * Load window position from settings or use default.
     */
    private void loadWindowPosition() {
        double savedX = settingsService != null ? settingsService.getMiniPlayerX() : -1;
        double savedY = settingsService != null ? settingsService.getMiniPlayerY() : -1;
        
        if (savedX >= 0 && savedY >= 0) {
            // Validate that the saved position is still valid for current screen configuration
            if (isPositionValid(savedX, savedY)) {
                miniStage.setX(savedX);
                miniStage.setY(savedY);
            } else {
                // Use default position if saved position is invalid
                setDefaultPosition();
            }
        } else {
            // Use default position if no saved position
            setDefaultPosition();
        }
    }
    
    /**
     * Set the window to its default position (top-right of primary screen).
     */
    private void setDefaultPosition() {
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        miniStage.setX(primaryScreenBounds.getMaxX() - 500);
        miniStage.setY(20);
    }
    
    /**
     * Check if a position is valid for the current screen configuration.
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return true if the position is valid, false otherwise
     */
    private boolean isPositionValid(double x, double y) {
        // Check if the position is within any screen bounds
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getVisualBounds();
            // Check if at least part of the window would be visible
            if (x + 50 >= bounds.getMinX() && x <= bounds.getMaxX() - 50 &&
                y + 20 >= bounds.getMinY() && y <= bounds.getMaxY() - 20) {
                return true;
            }
        }
        return false;
    }
    
    // No longer need visualizer toggle - it's always on
    
    // No longer need toggle method - visualizer is always on
    
    /**
     * Update visualizer state based on current settings and song.
     */
    private void updateVisualizerState() {
        // Check if current format supports visualization
        Song currentSong = audioPlayerService.getCurrentSong();
        boolean isPlaying = audioPlayerService.isPlaying();
        boolean visualizerEnabled = settingsService != null && settingsService.getSettings().isVisualizerEnabled();
        
        if (currentSong != null && visualizer.supportsFormat(getFileExtension(currentSong.getFilePath())) && isPlaying && visualizerEnabled) {
            // Visualizer is supported, we're playing, and visualizer is enabled
            if (!visualizer.isActive()) {
                visualizer.start();
                connectSpectrumListener();
            }
        } else {
            // Format not supported, not playing, or visualizer disabled - stop visualizer
            if (visualizer.isActive()) {
                visualizer.stop();
                // Don't disconnect listener here - let it continue to receive empty data
                // This allows bars to fall naturally
            }
        }
    }
    
    // No longer need showAlbumArt method - visualizer is always visible
    
    /**
     * Connect audio spectrum listener to visualizer.
     */
    private void connectSpectrumListener() {
        System.out.println("Connecting spectrum listener for mini player visualizer");
        
        // Ensure visualizer is started if enabled in settings
        if (settingsService != null && settingsService.getSettings().isVisualizerEnabled()) {
            visualizer.start();
        }
        
        audioPlayerService.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
            if (visualizer != null && visualizer.isActive()) {
                // Update spectrum data on JavaFX thread if needed
                javafx.application.Platform.runLater(() -> {
                    visualizer.updateSpectrum(timestamp, duration, magnitudes, phases);
                });
            }
        });
    }
    
    /**
     * Disconnect audio spectrum listener and stop visualizer.
     */
    private void disconnectSpectrumListener() {
        // Stop receiving spectrum updates
        audioPlayerService.setAudioSpectrumListener(null);
        
        // Stop the visualizer animation
        if (visualizer != null && visualizer.isActive()) {
            visualizer.stop();
        }
    }

    /**
     * Sync the play/pause button icon with the current playback state.
     */
    private void syncPlayPauseIcon() {
        if (playPauseButton != null && playPauseButton.getGraphic() instanceof ImageView) {
            ImageView imageView = (ImageView) playPauseButton.getGraphic();
            boolean isPlaying = audioPlayerService.isPlaying();
            imageView.setImage(isPlaying ? pauseIcon : playIcon);
        }
    }
    
    /**
     * Get file extension from file path.
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    /**
     * Setup keyboard shortcuts.
     */
    private void setupKeyboardShortcuts() {
        miniStage.getScene().setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case T:
                    // Test visualizer with dummy data
                    if (event.isControlDown()) {
                        testVisualizerWithDummyData();
                    }
                    break;
                case SPACE:
                    // Play/pause
                    audioPlayerService.togglePlayPause();
                    break;
                case LEFT:
                    // Previous track
                    audioPlayerService.previousTrack();
                    break;
                case RIGHT:
                    // Next track
                    audioPlayerService.nextTrack();
                    break;
                default:
                    break;
            }
        });
    }
    
    /**
     * Test visualizer with dummy spectrum data.
     * Press Ctrl+T to trigger this test.
     */
    private void testVisualizerWithDummyData() {
        System.out.println("=== TESTING VISUALIZER WITH DUMMY DATA ===");
        
        // Create an animation timer to continuously send dummy data
        javafx.animation.AnimationTimer dummyDataTimer = new javafx.animation.AnimationTimer() {
            private long lastUpdate = 0;
            private double phase = 0;
            
            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 50_000_000) { // Update every 50ms
                    lastUpdate = now;
                    
                    // Create animated dummy spectrum data
                    float[] dummyMagnitudes = new float[64];
                    for (int i = 0; i < 64; i++) {
                        // Create an animated wave pattern
                        dummyMagnitudes[i] = (float) (-30 + 15 * Math.sin(i * 0.2 + phase) +
                                                      10 * Math.sin(i * 0.1 + phase * 2) +
                                                      5 * Math.random());
                    }
                    
                    // Send to visualizer
                    if (visualizer != null && visualizer.isActive()) {
                        visualizer.updateSpectrum(0, 0, dummyMagnitudes, null);
                    }
                    
                    phase += 0.1;
                }
            }
        };
        
        dummyDataTimer.start();
        System.out.println("Started dummy data animation timer");
        
        // Stop after 10 seconds
        javafx.animation.Timeline stopTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(10), e -> {
                dummyDataTimer.stop();
                System.out.println("Stopped dummy data animation timer");
            })
        );
        stopTimer.play();
    }
    
    /**
     * Update visualizer settings when they change in the main application.
     * This should be called by the main controller when settings are updated.
     */
    public void updateVisualizerSettings() {
        if (visualizer != null && settingsService != null) {
            visualizer.updateSettings(settingsService.getSettings());
        }
    }
}
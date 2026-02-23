package com.musicplayer.ui.dialogs;

import java.io.File;

import com.musicplayer.data.models.Song;
import com.musicplayer.services.MusicLibraryManager;
import com.musicplayer.services.YouTubeDownloadService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Simple dialog that allows the user to paste a YouTube link and download the audio using yt-dlp.
 * Success will immediately add the downloaded song to the library (via MusicLibraryManager).
 */
public class YouTubeDownloadDialog extends Stage {

    private final MusicLibraryManager libraryManager;
    private final com.musicplayer.data.repositories.AlbumRepository albumRepository;
    private final com.musicplayer.data.repositories.SongRepository songRepository;
    private final YouTubeDownloadService downloadService;

    private TextField urlField;
    private TextField outputDirField;
    private Button browseOutputDirBtn;
    private ComboBox<String> formatCombo;
    private ComboBox<String> qualityCombo;
    private com.musicplayer.data.models.Album selectedAlbum;
    private CheckBox playlistCheck;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label infoLabel;
    private ListView<String> entriesList;
    private javafx.scene.image.ImageView coverView;
    private Button downloadBtn;
    private Button cancelBtn;

    public YouTubeDownloadDialog(Window owner, MusicLibraryManager libraryManager,
                                com.musicplayer.data.repositories.AlbumRepository albumRepository,
                                com.musicplayer.data.repositories.SongRepository songRepository) {
        this.libraryManager = libraryManager;
        this.albumRepository = albumRepository;
        this.songRepository = songRepository;
        this.downloadService = new YouTubeDownloadService();

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);
        setTitle("Download from YouTube");

        initializeUI();
    }

    private void initializeUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        Label linkLabel = new Label("YouTube (or supported) URL:");
        urlField = new TextField();
        urlField.setPrefColumnCount(40);
        urlField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                fetchInfo(newVal.trim());
            } else {
                clearInfo();
            }
        });
        playlistCheck = new CheckBox("Download entire playlist (if URL is a playlist)");
        
        infoLabel = new Label();
        infoLabel.setStyle("-fx-font-weight: bold;");
        coverView = new javafx.scene.image.ImageView();
        coverView.setFitWidth(160);
        coverView.setFitHeight(160);
        coverView.setPreserveRatio(true);
        entriesList = new ListView<>();
        entriesList.setPrefHeight(150);
        entriesList.setVisible(false);

        // --- Download options ---
        Label optionsLabel = new Label("Download Options:");
        formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("mp3", "m4a", "flac", "wav", "opus", "best");
        formatCombo.getSelectionModel().select("mp3");
        qualityCombo = new ComboBox<>();
        qualityCombo.getItems().addAll("320K", "256K", "192K", "128K", "0 (best)", "1", "2", "5 (worst)");
        qualityCombo.getSelectionModel().select("320K");
        HBox optionsBox = new HBox(10, new Label("Format:"), formatCombo, new Label("Quality:"), qualityCombo);

        // --- Album selection ---
        Label albumLabel = new Label("Save to Album:");
        com.musicplayer.ui.components.AlbumGridView albumGrid = new com.musicplayer.ui.components.AlbumGridView(
                albumRepository.findAll(), album -> selectedAlbum = album, albumRepository, songRepository);
        albumGrid.setPrefHeight(200);
        Button newAlbumBtn = new Button("New Album...");
        newAlbumBtn.setOnAction(e -> createNewAlbum(albumGrid));

        // --- Output directory ---
        Label outDirLabel = new Label("Save to folder:");
        outputDirField = new TextField();
        outputDirField.setEditable(false);
        outputDirField.setPrefColumnCount(30);
        browseOutputDirBtn = new Button("Browse...");
        browseOutputDirBtn.setOnAction(e -> chooseOutputDir());
        HBox outDirBox = new HBox(5, outputDirField, browseOutputDirBtn);
        HBox.setHgrow(outputDirField, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(350);
        progressLabel = new Label();
        progressLabel.setVisible(false);

        downloadBtn = new Button("Download");
        cancelBtn = new Button("Cancel");
        HBox btnBox = new HBox(10, downloadBtn, cancelBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        downloadBtn.setOnAction(e -> startDownload());
        cancelBtn.setOnAction(e -> close());

        root.getChildren().addAll(linkLabel, urlField, infoLabel, coverView, entriesList, playlistCheck, optionsLabel, optionsBox, albumLabel, albumGrid, newAlbumBtn, outDirLabel, outDirBox, progressBar, progressLabel, btnBox);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private void chooseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose download folder");
        File initialDir = libraryManager.getCurrentMusicFolder();
        if (initialDir != null && initialDir.exists()) {
            chooser.setInitialDirectory(initialDir);
        }
        File selected = chooser.showDialog(this);
        if (selected != null) {
            outputDirField.setText(selected.getAbsolutePath());
        }
    }

    private void startDownload() {
        String url = urlField.getText();
        if (url == null || url.isBlank()) {
            showError("Please enter a URL.");
            return;
        }
        if (!downloadService.isYtDlpInstalled()) {
            showError("yt-dlp is not installed or not found in PATH. Please install it first.");
            return;
        }

        // Output directory
        File outDir;
        if (!outputDirField.getText().isBlank()) {
            outDir = new File(outputDirField.getText());
        } else {
            File base = determineRootMusicFolder();
            if (base != null) {
                outDir = new File(base, "Downloaded");
            } else {
                outDir = new File(System.getProperty("user.home"), "Music/Downloaded");
            }
        }
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                showError("Failed to create output directory: " + outDir);
                return;
            }
        }

        String albumName;
        if (selectedAlbum != null) {
            albumName = selectedAlbum.getTitle();
            // ensure physical directory
            File albumDir = new File(determineRootMusicFolder(), albumName);
            if (!albumDir.exists()) albumDir.mkdirs();
            outDir = albumDir;
        } else {
            albumName = "Downloaded";
        }

        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressBar.setProgress(0);
        progressLabel.setText("Starting download...");
        downloadBtn.setDisable(true);

        String format = formatCombo.getSelectionModel().getSelectedItem();
        String qualitySel = qualityCombo.getSelectionModel().getSelectedItem();
        // Map quality to yt-dlp argument (strip text before space or keep raw like 320K)
        String qualityArg = qualitySel.contains(" ") ? qualitySel.split(" ")[0] : qualitySel;

        downloadService.downloadAudio(url, outDir, format, qualityArg, playlistCheck.isSelected(), new YouTubeDownloadService.DownloadListener() {
            @Override
            public void onProgress(int percentage, String message) {
                Platform.runLater(() -> {
                    if (percentage >= 0) {
                        progressBar.setProgress(percentage / 100.0);
                    }
                    progressLabel.setText(message);
                });
            }

            @Override
            public void onSuccess(File downloadedFile) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressLabel.setText("Download completed");

                    if (downloadedFile != null && downloadedFile.exists()) {
                        Song song = new Song();
                        song.setTitle(stripExtension(downloadedFile.getName()));
                        song.setArtist("Unknown Artist");
                        song.setAlbum(albumName);
                        song.setFilePath(downloadedFile.getAbsolutePath());
                        song.setGenre("Unknown");
                        song.setDuration(0);

                        libraryManager.addSong(song);
                        if (selectedAlbum != null) {
                            selectedAlbum.addSong(song);
                            albumRepository.save(selectedAlbum);
                        }
                    }
                    downloadBtn.setDisable(false);
                    cancelBtn.setText("Close");
                });
            }

            @Override
            public void onError(String errorMessage, Exception exception) {
                Platform.runLater(() -> {
                    showError(errorMessage);
                    downloadBtn.setDisable(false);
                });
            }
        });
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private File determineRootMusicFolder() {
        File musicFolder = libraryManager.getCurrentMusicFolder();
        if (musicFolder == null) return null;
        File parent = musicFolder.getParentFile();
        return parent != null ? parent : musicFolder;
    }

    private void createNewAlbum(com.musicplayer.ui.components.AlbumGridView grid) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Album");
        dialog.setHeaderText("Create a new album");
        dialog.setContentText("Album name:");
        dialog.initOwner(this);
        dialog.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                com.musicplayer.data.models.Album album = new com.musicplayer.data.models.Album();
                album.setTitle(trimmed);
                albumRepository.save(album);
                grid.refreshAlbums();
                selectedAlbum = album;
            }
        });
    }

    private void fetchInfo(String url) {
        infoLabel.setText("Fetching info...");
        entriesList.getItems().clear();
        entriesList.setVisible(false);
        playlistCheck.setSelected(false);

        downloadService.fetchInfo(url, new YouTubeDownloadService.InfoListener() {
            @Override
            public void onSuccess(YouTubeDownloadService.InfoResult info) {
                Platform.runLater(() -> {
                    if (info.isPlaylist()) {
                        playlistCheck.setSelected(true);
                        infoLabel.setText("Playlist: " + info.getTitle() + " (" + info.getEntries().size() + " items)");
                        entriesList.getItems().setAll(info.getEntries());
                        entriesList.setVisible(true);
                    } else {
                        infoLabel.setText("Video: " + info.getTitle());
                        entriesList.setVisible(false);
                    }
                    if (info.getThumbnailUrl() != null) {
                        coverView.setImage(new javafx.scene.image.Image(info.getThumbnailUrl(), true));
                    } else {
                        coverView.setImage(null);
                    }
                });
            }

            @Override
            public void onError(String errorMessage, Exception exception) {
                Platform.runLater(() -> {
                    infoLabel.setText("Could not fetch info");
                });
            }
        });
    }

    private void clearInfo() {
        infoLabel.setText("");
        entriesList.getItems().clear();
        entriesList.setVisible(false);
        coverView.setImage(null);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.initOwner(this);
        alert.showAndWait();
    }
}

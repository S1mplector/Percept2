package com.musicplayer.ui.dialogs;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.Callback;

import com.musicplayer.services.AudioConversionService;
import com.musicplayer.services.AudioConversionService.ConversionSettings;
import com.musicplayer.services.AudioConversionService.TargetFormat;
import com.musicplayer.services.ConversionTracker.ConversionRecord;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Dialog for configuring and executing audio file conversion to JavaFX-compatible formats.
 */
public class AudioConversionDialog extends Stage {
    
    private final AudioConversionService conversionService;
    private final List<File> filesToConvert;
    private ConversionSettings settings;

    // Optional: records of already converted albums to display in the dialog
    private Map<String, ConversionRecord> convertedRecords;

    // Mapping of album name -> files belonging to that album that can be converted
    private Map<String, java.util.List<File>> convertibleAlbumFiles;

    // Album selection UI
    private ListView<String> albumListView;
    private java.util.Set<String> selectedAlbums = new java.util.HashSet<>();
    
    // UI Components
    private RadioButton wavRadio;
    private RadioButton aiffRadio;
    private CheckBox preserveOriginalsCheck;
    private CheckBox autoConvertCheck;
    private TextField conversionDirField;
    private Button browseDirButton;
    private ComboBox<String> sampleRateCombo;
    private ComboBox<String> bitDepthCombo;
    private ComboBox<String> channelsCombo;
    
    private ProgressBar progressBar;
    private Label progressLabel;
    private Button convertButton;
    private Button cancelButton;
    
    public AudioConversionDialog(Window owner,
                                 AudioConversionService conversionService,
                                 List<File> filesToConvert) {
        this(owner, conversionService, filesToConvert, null);
    }

    /**
     * Extended constructor that also accepts a map of already converted albums so that we can present
     * a full overview (converted vs convertible) to the user.
     */
    public AudioConversionDialog(Window owner,
                                 AudioConversionService conversionService,
                                 List<File> filesToConvert,
                                 Map<String, ConversionRecord> convertedRecords) {
        this.conversionService = conversionService;
        this.filesToConvert = filesToConvert;
        this.settings = new ConversionSettings();
        this.convertedRecords = convertedRecords;

        // Build album -> files mapping for selection list
        this.convertibleAlbumFiles = new java.util.HashMap<>();
        for (File f : this.filesToConvert) {
            File parent = f.getParentFile();
            String albumName = parent != null ? parent.getName() : f.getName();
            convertibleAlbumFiles.computeIfAbsent(albumName, k -> new java.util.ArrayList<>()).add(f);
        }
        selectedAlbums.addAll(convertibleAlbumFiles.keySet()); // default select all
 
        initOwner(owner);
        setTitle("Audio Conversion Settings");
        setResizable(false);
        initModality(Modality.APPLICATION_MODAL);
        
        initializeUI();
        loadSettings();
    }
    
    private void initializeUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: white;");
        
        // Header
        Label headerLabel = new Label("Audio Conversion Overview & Settings");
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label descLabel = new Label(
            "Converting to WAV/AIFF enables visualizer and full playback controls.\n" +
            "Potential files to convert: " + filesToConvert.size()
        );
        descLabel.setStyle("-fx-text-fill: #666666;");

        // Added overview of converted & convertible albums (if data provided)
        VBox overviewBox = null;
        if (convertedRecords != null) {
            overviewBox = createOverviewBox();
        }
        
        // Create scrollable content area
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(10));
        
        // Format selection
        VBox formatBox = createFormatSelectionBox();
        
        // Options
        VBox optionsBox = createOptionsBox();
        
        // Audio quality settings
        VBox qualityBox = createQualityBox();
        
        contentBox.getChildren().addAll(formatBox, optionsBox, qualityBox);
        
        // Make content scrollable
        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Progress section (initially hidden) - positioned after scrollable content
        VBox progressBox = createProgressBox();
        progressBox.setVisible(false);
        progressBox.setManaged(false);
        
        // Buttons - always at the bottom
        HBox buttonBox = createButtonBox(progressBox);
        
        // Album selector box
        VBox albumSelectorBox = createAlbumSelectorBox();

        if (overviewBox != null) {
            root.getChildren().addAll(headerLabel, descLabel, overviewBox, albumSelectorBox, scrollPane, progressBox, buttonBox);
        } else {
            root.getChildren().addAll(headerLabel, descLabel, albumSelectorBox, scrollPane, progressBox, buttonBox);
        }
        
        Scene scene = new Scene(root, 520, 650);
        setScene(scene);
    }

    /**
     * Build an overview box listing albums that are already converted and those that can be converted.
     */
    private VBox createOverviewBox() {
        VBox box = new VBox(6);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 10;");

        // Already converted albums
        int convertedCount = convertedRecords != null ? convertedRecords.size() : 0;
        Label convertedHeader = new Label("Already Converted Albums: " + convertedCount);
        convertedHeader.setStyle("-fx-font-weight: bold;");

        javafx.scene.control.TextArea convertedArea = new javafx.scene.control.TextArea();
        convertedArea.setEditable(false);
        convertedArea.setWrapText(true);
        convertedArea.setPrefRowCount(Math.min(convertedCount, 6));

        if (convertedRecords != null && !convertedRecords.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            convertedRecords.values().stream()
                .limit(20) // avoid huge walls of text
                .forEach(rec -> sb.append("• ").append(rec.getAlbumKey()).append(" (")
                                   .append(rec.getConvertedFormat().toUpperCase()).append(")\n"));
            if (convertedRecords.size() > 20) {
                sb.append("… and ").append(convertedRecords.size() - 20).append(" more");
            }
            convertedArea.setText(sb.toString());
        } else {
            convertedArea.setText("None");
        }

        // Albums that can be converted (based on filesToConvert list)
        Set<String> convertibleAlbumNames = new HashSet<>();
        for (File f : filesToConvert) {
            File parent = f.getParentFile();
            if (parent != null) {
                convertibleAlbumNames.add(parent.getName());
            }
        }

        Label convertibleHeader = new Label("Albums Containing Convertible Files: " + convertibleAlbumNames.size());
        convertibleHeader.setStyle("-fx-font-weight: bold;");

        javafx.scene.control.TextArea convertibleArea = new javafx.scene.control.TextArea();
        convertibleArea.setEditable(false);
        convertibleArea.setWrapText(true);
        convertibleArea.setPrefRowCount(Math.min(convertibleAlbumNames.size(), 6));

        if (!convertibleAlbumNames.isEmpty()) {
            StringBuilder sb2 = new StringBuilder();
            convertibleAlbumNames.stream()
                .limit(20)
                .forEach(name -> sb2.append("• ").append(name).append("\n"));
            if (convertibleAlbumNames.size() > 20) {
                sb2.append("… and ").append(convertibleAlbumNames.size() - 20).append(" more");
            }
            convertibleArea.setText(sb2.toString());
        } else {
            convertibleArea.setText("None – your library is fully optimized!");
        }

        box.getChildren().addAll(convertedHeader, convertedArea, convertibleHeader, convertibleArea);
        return box;
    }

    /**
     * Creates a box that lets the user pick which albums to convert.
     */
    private VBox createAlbumSelectorBox() {
        VBox box = new VBox(6);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 10;");

        Label title = new Label("Select Albums to Convert (default: all)");
        title.setStyle("-fx-font-weight: bold;");

        albumListView = new ListView<>(FXCollections.observableArrayList(convertibleAlbumFiles.keySet()));
        albumListView.setPrefHeight(150);

        albumListView.setCellFactory(CheckBoxListCell.forListView(new Callback<String, javafx.beans.value.ObservableValue<Boolean>>() {
            @Override
            public javafx.beans.value.ObservableValue<Boolean> call(String item) {
                SimpleBooleanProperty prop = new SimpleBooleanProperty(selectedAlbums.contains(item));
                prop.addListener((obs, wasSel, isSel) -> {
                    if (isSel) {
                        selectedAlbums.add(item);
                    } else {
                        selectedAlbums.remove(item);
                    }
                });
                return prop;
            }
        }));

        // Select All / None controls
        javafx.scene.control.CheckBox selectAllCheck = new javafx.scene.control.CheckBox("Select All");
        selectAllCheck.setSelected(true);
        selectAllCheck.selectedProperty().addListener((obs, was, now) -> {
            if (now) {
                selectedAlbums.addAll(convertibleAlbumFiles.keySet());
            } else {
                selectedAlbums.clear();
            }
            albumListView.refresh();
        });

        box.getChildren().addAll(title, selectAllCheck, albumListView);
        return box;
    }
    
    private VBox createFormatSelectionBox() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 15;");
        
        Label titleLabel = new Label("Target Format");
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        ToggleGroup formatGroup = new ToggleGroup();
        
        wavRadio = new RadioButton("WAV (Recommended)");
        wavRadio.setToggleGroup(formatGroup);
        wavRadio.setSelected(true);
        
        aiffRadio = new RadioButton("AIFF");
        aiffRadio.setToggleGroup(formatGroup);
        
        Label formatInfoLabel = new Label("Both formats provide full JavaFX compatibility.");
        formatInfoLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 12px;");
        
        box.getChildren().addAll(titleLabel, wavRadio, aiffRadio, formatInfoLabel);
        return box;
    }
    
    private VBox createOptionsBox() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 15;");
        
        Label titleLabel = new Label("Conversion Options");
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        preserveOriginalsCheck = new CheckBox("Keep original files");
        preserveOriginalsCheck.setSelected(true);
        
        autoConvertCheck = new CheckBox("Auto-convert new files on import");
        autoConvertCheck.setSelected(false);
        
        // Output directory selection
        Label dirLabel = new Label("Output Directory (leave empty for same as original):");
        
        HBox dirBox = new HBox(10);
        dirBox.setAlignment(Pos.CENTER_LEFT);
        
        conversionDirField = new TextField();
        conversionDirField.setPromptText("Same as original files");
        conversionDirField.setPrefWidth(300);
        
        browseDirButton = new Button("Browse...");
        browseDirButton.setOnAction(e -> browseOutputDirectory());
        
        dirBox.getChildren().addAll(conversionDirField, browseDirButton);
        
        box.getChildren().addAll(titleLabel, preserveOriginalsCheck, autoConvertCheck, dirLabel, dirBox);
        return box;
    }
    
    private VBox createQualityBox() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 15;");
        
        Label titleLabel = new Label("Audio Quality");
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        // Sample Rate
        grid.add(new Label("Sample Rate:"), 0, 0);
        sampleRateCombo = new ComboBox<>();
        sampleRateCombo.getItems().addAll("44100 Hz (CD Quality)", "48000 Hz (Studio)", "96000 Hz (High-Res)");
        sampleRateCombo.setValue("44100 Hz (CD Quality)");
        grid.add(sampleRateCombo, 1, 0);
        
        // Bit Depth
        grid.add(new Label("Bit Depth:"), 0, 1);
        bitDepthCombo = new ComboBox<>();
        bitDepthCombo.getItems().addAll("16-bit (CD Quality)", "24-bit (Studio)", "32-bit (High-Res)");
        bitDepthCombo.setValue("16-bit (CD Quality)");
        grid.add(bitDepthCombo, 1, 1);
        
        // Channels
        grid.add(new Label("Channels:"), 0, 2);
        channelsCombo = new ComboBox<>();
        channelsCombo.getItems().addAll("Stereo (2 channels)", "Mono (1 channel)");
        channelsCombo.setValue("Stereo (2 channels)");
        grid.add(channelsCombo, 1, 2);
        
        box.getChildren().addAll(titleLabel, grid);
        return box;
    }
    
    private VBox createProgressBox() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 15;");
        box.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("Conversion Progress");
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        
        progressLabel = new Label("Ready to convert...");
        progressLabel.setStyle("-fx-text-fill: #666666;");
        
        box.getChildren().addAll(titleLabel, progressBar, progressLabel);
        return box;
    }
    
    private HBox createButtonBox(VBox progressBox) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);
        
        cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> close());
        
        convertButton = new Button("Start Conversion");
        convertButton.setDefaultButton(true);
        convertButton.setOnAction(e -> startConversion(progressBox));
        
        box.getChildren().addAll(cancelButton, convertButton);
        return box;
    }
    
    private void browseOutputDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");
        
        File selectedDir = chooser.showDialog(this);
        if (selectedDir != null) {
            conversionDirField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    private void loadSettings() {
        ConversionSettings currentSettings = conversionService.getSettings();
        
        // Load format selection
        if (currentSettings.getTargetFormat() == TargetFormat.AIFF) {
            aiffRadio.setSelected(true);
        } else {
            wavRadio.setSelected(true);
        }
        
        // Load options
        preserveOriginalsCheck.setSelected(currentSettings.isPreserveOriginals());
        autoConvertCheck.setSelected(currentSettings.isAutoConvertOnImport());
        
        if (currentSettings.getConversionDirectory() != null) {
            conversionDirField.setText(currentSettings.getConversionDirectory());
        }
        
        // Load quality settings
        sampleRateCombo.setValue(getSampleRateDisplayText(currentSettings.getSampleRate()));
        bitDepthCombo.setValue(getBitDepthDisplayText(currentSettings.getSampleSizeInBits()));
        channelsCombo.setValue(getChannelsDisplayText(currentSettings.getChannels()));
    }
    
    private void saveSettings() {
        // Save format
        settings.setTargetFormat(wavRadio.isSelected() ? TargetFormat.WAV : TargetFormat.AIFF);
        
        // Save options
        settings.setPreserveOriginals(preserveOriginalsCheck.isSelected());
        settings.setAutoConvertOnImport(autoConvertCheck.isSelected());
        
        String conversionDir = conversionDirField.getText().trim();
        settings.setConversionDirectory(conversionDir.isEmpty() ? null : conversionDir);
        
        // Save quality settings
        settings.setSampleRate(parseSampleRate(sampleRateCombo.getValue()));
        settings.setSampleSizeInBits(parseBitDepth(bitDepthCombo.getValue()));
        settings.setChannels(parseChannels(channelsCombo.getValue()));
        
        // Apply settings to service
        conversionService.setSettings(settings);
    }
    
    private void startConversion(VBox progressBox) {
        saveSettings();
        
        // Show progress section
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        
        // Disable convert button
        convertButton.setDisable(true);
        
        // Determine files based on album selection
        List<File> convertibleFiles = new java.util.ArrayList<>();
        for (String album : selectedAlbums) {
            List<File> list = convertibleAlbumFiles.get(album);
            if (list != null) {
                convertibleFiles.addAll(list);
            }
        }
        // Ensure still respect format filter
        final java.util.List<File> filesToProcess = convertibleFiles.stream()
            .filter(file -> {
                String ext = getFileExtension(file.getName());
                return conversionService.isConvertible(ext) && !conversionService.isJavaFXCompatible(ext);
            })
            .collect(Collectors.toList());

        if (filesToProcess.isEmpty()) {
            showInfo("No Albums Selected", "Please select at least one album containing convertible files.");
            convertButton.setDisable(false);
            return;
        }
        
        // Create conversion task
        Task<List<File>> conversionTask = new Task<List<File>>() {
            @Override
            protected List<File> call() throws Exception {
                return conversionService.convertFiles(filesToProcess, new AudioConversionService.ConversionProgressCallback() {
                    @Override
                    public void onProgress(String fileName, int current, int total, double percentage) {
                        Platform.runLater(() -> {
                            progressBar.setProgress(percentage / 100.0);
                            progressLabel.setText(String.format("Converting %s (%d/%d) - %.1f%%", 
                                fileName, current, total, percentage));
                            
                            // Update window title to show progress
                            setTitle(String.format("Audio Conversion - %d/%d (%.0f%%)", current, total, percentage));
                        });
                    }
                    
                    @Override
                    public void onComplete(List<File> convertedFiles, List<String> errors) {
                        Platform.runLater(() -> {
                            progressBar.setProgress(1.0);
                            progressLabel.setText("Conversion completed successfully!");
                            setTitle("Audio Conversion - Complete");
                            
                            // Show detailed completion notification
                            String message;
                            if (!errors.isEmpty()) {
                                message = String.format(
                                    "Conversion completed with some issues:\n\n" +
                                    "✓ Successfully converted: %d files\n" +
                                    "⚠ Failed to convert: %d files\n\n" +
                                    "Target format: %s\n" +
                                    "Check the error details for more information.",
                                    convertedFiles.size(), errors.size(),
                                    settings.getTargetFormat().getExtension().toUpperCase()
                                );
                                showConversionResults(convertedFiles.size(), errors);
                            } else {
                                message = String.format(
                                    "🎉 Conversion completed successfully!\n\n" +
                                    "✓ Converted files: %d\n" +
                                    "📁 Target format: %s\n" +
                                    "🎵 Enhanced features now available:\n" +
                                    "  • Audio visualizer\n" +
                                    "  • Better playback performance\n" +
                                    "  • Enhanced audio controls",
                                    convertedFiles.size(),
                                    settings.getTargetFormat().getExtension().toUpperCase()
                                );
                                showInfo("Conversion Complete", message);
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String fileName, Exception error) {
                        // Individual file errors are handled in onComplete
                    }
                }).get(); // Wait for completion
            }
        };
        
        conversionTask.setOnSucceeded(e -> {
            convertButton.setDisable(false);
            cancelButton.setText("Close");
        });
        
        conversionTask.setOnFailed(e -> {
            convertButton.setDisable(false);
            progressLabel.setText("Conversion failed!");
            showError("Conversion Error", "An error occurred during conversion: " + conversionTask.getException().getMessage());
        });
        
        // Run conversion in background
        Thread conversionThread = new Thread(conversionTask);
        conversionThread.setDaemon(true);
        conversionThread.start();
    }
    
    private void showConversionResults(int successCount, List<String> errors) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Successfully converted %d files.\n\n", successCount));
        
        if (!errors.isEmpty()) {
            message.append("Errors encountered:\n");
            for (String error : errors) {
                message.append("• ").append(error).append("\n");
            }
        }
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Conversion Results");
        alert.setHeaderText("Conversion Completed with Some Issues");
        alert.setContentText(message.toString());
        alert.showAndWait();
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Helper methods for parsing UI values
    private float parseSampleRate(String displayText) {
        if (displayText.contains("44100")) return 44100.0f;
        if (displayText.contains("48000")) return 48000.0f;
        if (displayText.contains("96000")) return 96000.0f;
        return 44100.0f; // default
    }
    
    private int parseBitDepth(String displayText) {
        if (displayText.contains("16")) return 16;
        if (displayText.contains("24")) return 24;
        if (displayText.contains("32")) return 32;
        return 16; // default
    }
    
    private int parseChannels(String displayText) {
        if (displayText.contains("Mono")) return 1;
        return 2; // default stereo
    }
    
    private String getSampleRateDisplayText(float sampleRate) {
        if (sampleRate == 48000.0f) return "48000 Hz (Studio)";
        if (sampleRate == 96000.0f) return "96000 Hz (High-Res)";
        return "44100 Hz (CD Quality)";
    }
    
    private String getBitDepthDisplayText(int bitDepth) {
        if (bitDepth == 24) return "24-bit (Studio)";
        if (bitDepth == 32) return "32-bit (High-Res)";
        return "16-bit (CD Quality)";
    }
    
    private String getChannelsDisplayText(int channels) {
        if (channels == 1) return "Mono (1 channel)";
        return "Stereo (2 channels)";
    }
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
}

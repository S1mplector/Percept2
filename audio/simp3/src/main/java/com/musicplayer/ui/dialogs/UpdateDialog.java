package com.musicplayer.ui.dialogs;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.musicplayer.data.models.DistributionType;
import com.musicplayer.data.models.UpdateInfo;
import com.musicplayer.services.UpdateService;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog for displaying update information and managing the update process.
 */
public class UpdateDialog extends Stage {
    private static final Logger logger = LoggerFactory.getLogger(UpdateDialog.class);
    
    private final UpdateService updateService;
    private final List<UpdateInfo> availableUpdates;
    private UpdateInfo selectedUpdate;
    
    private Button updateButton;
    private Button skipButton;
    private Button laterButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea releaseNotesArea;
    
    // Distribution selection components
    private VBox distributionChoiceBox;
    private ToggleGroup distributionGroup;
    private CheckBox rememberChoiceCheckBox;
    private RadioButton portableRadio;
    private RadioButton installerRadio;
    private RadioButton releaseRadio;
    
    public UpdateDialog(UpdateService updateService, UpdateInfo updateInfo) {
        this.updateService = updateService;
        this.availableUpdates = new ArrayList<>();
        this.availableUpdates.add(updateInfo);
        this.selectedUpdate = updateInfo;
        
        initializeDialog();
    }
    
    public UpdateDialog(UpdateService updateService, List<UpdateInfo> availableUpdates) {
        this.updateService = updateService;
        this.availableUpdates = availableUpdates;
        
        // Pre-select based on user preference or default to first available
        DistributionType preferred = updateService.getSettingsService().getSettings().getPreferredDistributionType();
        this.selectedUpdate = availableUpdates.stream()
            .filter(update -> update.getDistributionType() == preferred)
            .findFirst()
            .orElse(availableUpdates.get(0));
        
        initializeDialog();
    }
    
    private void initializeDialog() {
        setTitle("Update Available - SiMP3");
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setResizable(false);
        
        // Set icon
        try {
            getIcons().add(new Image(getClass().getResourceAsStream("/images/icons/app.png")));
        } catch (Exception e) {
            logger.warn("Failed to load application icon", e);
        }
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("update-dialog");
        
        // Header
        HBox header = createHeader();
        
        // Version info
        VBox versionInfo = createVersionInfo();
        
        // Distribution choice (if multiple options available)
        distributionChoiceBox = createDistributionChoice();
        
        // Release notes
        VBox releaseNotes = createReleaseNotes();
        
        // Progress section (initially hidden)
        VBox progressSection = createProgressSection();
        progressSection.setVisible(false);
        progressSection.setManaged(false);
        
        // Buttons
        HBox buttons = createButtons(progressSection);
        
        // Add components to root
        root.getChildren().addAll(header, versionInfo);
        
        // Only show distribution choice if multiple options are available
        if (hasMultipleDistributionTypes()) {
            root.getChildren().add(distributionChoiceBox);
        }
        
        root.getChildren().addAll(releaseNotes, progressSection, buttons);
        
        Scene scene = new Scene(root, 500, 600);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        setScene(scene);
        
        // Center on screen
        centerOnScreen();
    }
    
    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Update icon
        ImageView icon = new ImageView();
        try {
            icon.setImage(new Image(getClass().getResourceAsStream("/images/icons/app.png")));
            icon.setFitWidth(48);
            icon.setFitHeight(48);
            icon.setPreserveRatio(true);
        } catch (Exception e) {
            logger.warn("Failed to load update icon", e);
        }
        
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("New Update Available!");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        
        Label subtitleLabel = new Label("A new version of SiMP3 is ready to install");
        subtitleLabel.setStyle("-fx-text-fill: #666666;");
        
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        
        header.getChildren().addAll(icon, titleBox);
        return header;
    }
    
    private VBox createVersionInfo() {
        VBox versionBox = new VBox(10);
        versionBox.setAlignment(Pos.CENTER_LEFT);
        versionBox.setPadding(new Insets(10));
        versionBox.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 5;");
        
        HBox currentVersion = new HBox(10);
        currentVersion.setAlignment(Pos.CENTER_LEFT);
        Label currentLabel = new Label("Current version:");
        currentLabel.setMinWidth(100);
        Label currentValue = new Label(updateService.getCurrentVersion());
        currentValue.setStyle("-fx-font-weight: bold;");
        currentVersion.getChildren().addAll(currentLabel, currentValue);
        
        HBox newVersion = new HBox(10);
        newVersion.setAlignment(Pos.CENTER_LEFT);
        Label newLabel = new Label("New version:");
        newLabel.setMinWidth(100);
        Label newValue = new Label(selectedUpdate.getVersion());
        newValue.setStyle("-fx-font-weight: bold; -fx-text-fill: #2ecc71;");
        newVersion.getChildren().addAll(newLabel, newValue);
        
        HBox fileSize = new HBox(10);
        fileSize.setAlignment(Pos.CENTER_LEFT);
        Label sizeLabel = new Label("Download size:");
        sizeLabel.setMinWidth(100);
        Label sizeValue = new Label(selectedUpdate.getFileSizeFormatted());
        fileSize.getChildren().addAll(sizeLabel, sizeValue);
        
        versionBox.getChildren().addAll(currentVersion, newVersion, fileSize);
        
        if (selectedUpdate.isMandatory()) {
            Label mandatoryLabel = new Label("⚠ This is a mandatory update");
            mandatoryLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            versionBox.getChildren().add(mandatoryLabel);
        }
        
        return versionBox;
    }
    
    private VBox createDistributionChoice() {
        VBox choiceBox = new VBox(15);
        choiceBox.setAlignment(Pos.TOP_LEFT);
        choiceBox.setPadding(new Insets(15));
        choiceBox.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 5; -fx-border-color: #e0e0e0; -fx-border-radius: 5;");
        
        Label choiceLabel = new Label("Choose Distribution Type:");
        choiceLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        distributionGroup = new ToggleGroup();
        
        // Installer option
        VBox installerBox = new VBox(5);
        installerRadio = new RadioButton("Installer Version");
        installerRadio.setToggleGroup(distributionGroup);
        installerRadio.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label installerDesc = new Label("• System-wide installation\n• Start menu integration\n• Automatic uninstaller");
        installerDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        installerDesc.setPadding(new Insets(0, 0, 0, 20));
        
        installerBox.getChildren().addAll(installerRadio, installerDesc);
        
        // Release option
        VBox releaseBox = new VBox(5);
        releaseRadio = new RadioButton("Release Executable");
        releaseRadio.setToggleGroup(distributionGroup);
        releaseRadio.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label releaseDesc = new Label("• Seamless update experience\n• Replaces current executable\n• No installation required");
        releaseDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        releaseDesc.setPadding(new Insets(0, 0, 0, 20));
        
        releaseBox.getChildren().addAll(releaseRadio, releaseDesc);
        
        // Portable option
        VBox portableBox = new VBox(5);
        portableRadio = new RadioButton("Portable Version");
        portableRadio.setToggleGroup(distributionGroup);
        portableRadio.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        Label portableDesc = new Label("• No installation required\n• Can run from USB drive\n• Settings stored locally");
        portableDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        portableDesc.setPadding(new Insets(0, 0, 0, 20));
        
        portableBox.getChildren().addAll(portableRadio, portableDesc);
        
        // Pre-select based on current selection
        if (selectedUpdate.getDistributionType() == DistributionType.INSTALLER) {
            installerRadio.setSelected(true);
        } else if (selectedUpdate.getDistributionType() == DistributionType.RELEASE) {
            releaseRadio.setSelected(true);
        } else if (selectedUpdate.getDistributionType() == DistributionType.PORTABLE) {
            portableRadio.setSelected(true);
        }
        
        // Add listener to update selected update when radio selection changes
        distributionGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == installerRadio) {
                updateSelectedDistribution(DistributionType.INSTALLER);
            } else if (newVal == releaseRadio) {
                updateSelectedDistribution(DistributionType.RELEASE);
            } else if (newVal == portableRadio) {
                updateSelectedDistribution(DistributionType.PORTABLE);
            }
        });
        
        // Remember choice checkbox
        rememberChoiceCheckBox = new CheckBox("Remember my choice");
        rememberChoiceCheckBox.setSelected(updateService.getSettingsService().getSettings().isRememberDistributionChoice());
        rememberChoiceCheckBox.setPadding(new Insets(10, 0, 0, 0));
        
        choiceBox.getChildren().addAll(choiceLabel, installerBox, releaseBox, portableBox, rememberChoiceCheckBox);
        
        return choiceBox;
    }
    
    private void updateSelectedDistribution(DistributionType type) {
        selectedUpdate = availableUpdates.stream()
            .filter(update -> update.getDistributionType() == type)
            .findFirst()
            .orElse(selectedUpdate);
        
        // Update UI elements that depend on the selected update
        updateVersionInfo();
        updateReleaseNotes();
    }
    
    private void updateVersionInfo() {
        // Find the file size label and update it
        Scene scene = getScene();
        if (scene != null) {
            VBox root = (VBox) scene.getRoot();
            VBox versionBox = (VBox) root.getChildren().stream()
                .filter(node -> node instanceof VBox && node.getStyle().contains("-fx-background-color: #f0f0f0"))
                .findFirst()
                .orElse(null);
            
            if (versionBox != null) {
                HBox fileSizeBox = (HBox) versionBox.getChildren().stream()
                    .filter(node -> node instanceof HBox)
                    .skip(2) // Skip current version and new version boxes
                    .findFirst()
                    .orElse(null);
                
                if (fileSizeBox != null && fileSizeBox.getChildren().size() > 1) {
                    Label sizeValue = (Label) fileSizeBox.getChildren().get(1);
                    sizeValue.setText(selectedUpdate.getFileSizeFormatted());
                }
            }
        }
    }
    
    private void updateReleaseNotes() {
        if (releaseNotesArea != null) {
            releaseNotesArea.setText(selectedUpdate.getReleaseNotes());
        }
    }
    
    private boolean hasMultipleDistributionTypes() {
        return availableUpdates.size() > 1 &&
               availableUpdates.stream()
                   .map(UpdateInfo::getDistributionType)
                   .distinct()
                   .count() > 1;
    }
    
    private VBox createReleaseNotes() {
        VBox notesBox = new VBox(10);
        notesBox.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(notesBox, Priority.ALWAYS);
        
        Label notesLabel = new Label("What's New:");
        notesLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        releaseNotesArea = new TextArea(selectedUpdate.getReleaseNotes());
        releaseNotesArea.setEditable(false);
        releaseNotesArea.setWrapText(true);
        releaseNotesArea.setPrefRowCount(10);
        VBox.setVgrow(releaseNotesArea, Priority.ALWAYS);
        
        Hyperlink viewOnGitHub = new Hyperlink("View on GitHub");
        viewOnGitHub.setOnAction(e -> openInBrowser(selectedUpdate.getHtmlUrl()));
        
        notesBox.getChildren().addAll(notesLabel, releaseNotesArea, viewOnGitHub);
        return notesBox;
    }
    
    private VBox createProgressSection() {
        VBox progressBox = new VBox(10);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setPadding(new Insets(10));
        progressBox.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 5;");
        
        statusLabel = new Label("Preparing download...");
        statusLabel.setStyle("-fx-font-weight: bold;");
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(460);
        progressBar.setPrefHeight(20);
        
        // Bind to update service progress
        progressBar.progressProperty().bind(updateService.downloadProgressProperty());
        statusLabel.textProperty().bind(updateService.downloadStatusProperty());
        
        progressBox.getChildren().addAll(statusLabel, progressBar);
        return progressBox;
    }
    
    private HBox createButtons(VBox progressSection) {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        updateButton = new Button("Update Now");
        updateButton.getStyleClass().add("primary-button");
        updateButton.setOnAction(e -> handleUpdate(progressSection));
        
        skipButton = new Button("Skip This Version");
        skipButton.setOnAction(e -> handleSkip());
        if (selectedUpdate.isMandatory()) {
            skipButton.setDisable(true);
            skipButton.setTooltip(new Tooltip("This is a mandatory update and cannot be skipped"));
        }
        
        laterButton = new Button("Remind Me Later");
        laterButton.setOnAction(e -> close());
        
        buttonBox.getChildren().addAll(skipButton, laterButton, updateButton);
        return buttonBox;
    }
    
    private void handleUpdate(VBox progressSection) {
        // Save distribution preference if remember choice is checked
        if (hasMultipleDistributionTypes() && rememberChoiceCheckBox.isSelected()) {
            updateService.getSettingsService().getSettings()
                .setPreferredDistributionType(selectedUpdate.getDistributionType());
            updateService.getSettingsService().getSettings()
                .setRememberDistributionChoice(true);
            updateService.getSettingsService().saveSettings();
        }
        
        // Show progress section
        progressSection.setVisible(true);
        progressSection.setManaged(true);
        
        // Disable buttons and distribution choice
        updateButton.setDisable(true);
        skipButton.setDisable(true);
        laterButton.setDisable(true);
        if (distributionChoiceBox != null) {
            distributionChoiceBox.setDisable(true);
        }
        
        // Start download task
        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                return updateService.downloadUpdate(selectedUpdate).get();
            }
        };
        
        downloadTask.setOnSucceeded(e -> {
            File updateFile = downloadTask.getValue();
            if (updateFile != null && updateFile.exists()) {
                handleDownloadComplete(updateFile);
            }
        });
        
        downloadTask.setOnFailed(e -> {
            Throwable error = downloadTask.getException();
            logger.error("Update download failed", error);
            showError("Download Failed", 
                "Failed to download the update. Please try again later or download manually from GitHub.");
            resetButtons();
        });
        
        new Thread(downloadTask).start();
    }
    
    private void handleDownloadComplete(File updateFile) {
        Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Download complete! Preparing update...");
            
            // Apply the update with the selected update info
            boolean staged = updateService.applyUpdate(updateFile, selectedUpdate);
            
            if (staged) {
                showUpdateReadyDialog();
            } else {
                showError("Update Failed", 
                    "Failed to prepare the update. Please try updating manually.");
                resetButtons();
            }
        });
    }
    
    private void showUpdateReadyDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update Ready");
        alert.setHeaderText("Update Downloaded Successfully");
        alert.setContentText("The update has been downloaded and will be installed when you restart the application.\n\n" +
                           "Would you like to restart now?");
        
        ButtonType restartNow = new ButtonType("Restart Now");
        ButtonType restartLater = new ButtonType("Restart Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(restartNow, restartLater);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == restartNow) {
                restartApplication();
            } else {
                close();
            }
        });
    }
    
    private void restartApplication() {
        try {
            // Execute the update script and exit
            String updateScript = "update/apply-update.bat";
            File scriptFile = new File(updateScript);
            if (scriptFile.exists()) {
                // Use the full path and quote it to handle spaces
                String fullPath = scriptFile.getAbsolutePath();
                new ProcessBuilder("cmd", "/c", "\"" + fullPath + "\"").start();
                Platform.exit();
                System.exit(0);
            }
        } catch (Exception e) {
            logger.error("Failed to restart application", e);
            showError("Restart Failed",
                "Failed to restart the application. Please close and restart manually.");
        }
    }
    
    private void handleSkip() {
        // Save skipped version
        updateService.getSettingsService().getSettings()
            .setSkippedUpdateVersion(selectedUpdate.getVersion());
        updateService.getSettingsService().saveSettings();
        
        close();
    }
    
    private void resetButtons() {
        Platform.runLater(() -> {
            updateButton.setDisable(false);
            skipButton.setDisable(selectedUpdate.isMandatory());
            laterButton.setDisable(false);
            if (distributionChoiceBox != null) {
                distributionChoiceBox.setDisable(false);
            }
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
        });
    }
    
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void openInBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            logger.error("Failed to open browser", e);
        }
    }
    
    /**
     * Show distribution choice dialog when multiple distribution types are available.
     * This method can be called from UpdateService when it detects multiple distribution options.
     *
     * @param updateService The update service instance
     * @param availableUpdates List of available updates with different distribution types
     * @return The created UpdateDialog instance, or null if no updates available
     */
    public static UpdateDialog showDistributionChoice(UpdateService updateService, List<UpdateInfo> availableUpdates) {
        if (availableUpdates == null || availableUpdates.isEmpty()) {
            return null;
        }
        
        // If only one update available, show regular dialog
        if (availableUpdates.size() == 1) {
            return new UpdateDialog(updateService, availableUpdates.get(0));
        }
        
        // Show dialog with distribution choice
        UpdateDialog dialog = new UpdateDialog(updateService, availableUpdates);
        dialog.show();
        return dialog;
    }
}
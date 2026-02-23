package com.musicplayer.ui.controllers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;

import com.musicplayer.data.models.DistributionType;
import com.musicplayer.data.models.Settings;
import com.musicplayer.services.SettingsService;
import com.musicplayer.services.UpdateService;
import com.musicplayer.services.MusicLibraryManager;
import com.musicplayer.ui.dialogs.UpdateDialog;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Controller for the settings dialog.
 */
public class SettingsController {
    
    @FXML private CheckBox visualizerEnabledCheckBox;
    @FXML private VBox colorModeSection;

    @FXML private RadioButton gradientCyclingRadio;
    @FXML private RadioButton solidColorRadio;
    @FXML private ToggleGroup colorModeGroup;
    @FXML private HBox colorPickerSection;
    @FXML private ColorPicker solidColorPicker;
    
    // Theme controls
    @FXML private ChoiceBox<com.musicplayer.data.models.Settings.Theme> themeChoiceBox;
    @FXML private javafx.scene.control.TextField musicRootPathField;
    @FXML private Button browseMusicFolderButton;
    @FXML private CheckBox libraryWatcherEnabledCheckBox;

    // Update settings controls
    @FXML private CheckBox autoCheckUpdatesCheckBox;
    @FXML private HBox updateIntervalSection;
    @FXML private Spinner<Integer> updateIntervalSpinner;
    @FXML private CheckBox showPreReleaseCheckBox;
    @FXML private CheckBox downloadInBackgroundCheckBox;
    @FXML private Label lastUpdateCheckLabel;
    @FXML private Button checkNowButton;
    
    // Distribution type controls
    @FXML private RadioButton installerRadio;
    @FXML private RadioButton releaseRadio;
    @FXML private RadioButton portableRadio;
    @FXML private ToggleGroup distributionTypeGroup;
    
    // Custom button bar
    @FXML private HBox buttonBar;
    private Button saveButton;
    private Button cancelButton;
    
    private SettingsService settingsService;
    private UpdateService updateService;
    private Settings settings;
    private MusicLibraryManager musicLibraryManager;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");
    
    /**
     * Set the settings service and load current settings.
     * @param settingsService The settings service to use
     */
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.settings = settingsService.getSettings();
        loadSettings();
    }
    
    /**
     * Set the update service for checking updates.
     * @param updateService The update service to use
     */
    public void setUpdateService(UpdateService updateService) {
        this.updateService = updateService;
    }
    
    public void setMusicLibraryManager(MusicLibraryManager musicLibraryManager) {
        this.musicLibraryManager = musicLibraryManager;
    }
    
    /**
     * Initialize the controller.
     */
    @FXML
    public void initialize() {

            // Populate theme choice box
        if (themeChoiceBox != null) {
            themeChoiceBox.getItems().setAll(com.musicplayer.data.models.Settings.Theme.values());
            // Display user-friendly names
            themeChoiceBox.setConverter(new javafx.util.StringConverter<com.musicplayer.data.models.Settings.Theme>() {
                @Override
                public String toString(com.musicplayer.data.models.Settings.Theme theme) {
                    if (theme == null) return "";
                    switch (theme) {
                        case DARK: return "Dark";
                        case LIGHT:
                        default: return "Light";
                    }
                }
                @Override
                public com.musicplayer.data.models.Settings.Theme fromString(String string) {
                    return "Dark".equalsIgnoreCase(string) ? com.musicplayer.data.models.Settings.Theme.DARK : com.musicplayer.data.models.Settings.Theme.LIGHT;
                }
            });
        }

        // Enable/disable color options based on visualizer state
        visualizerEnabledCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            colorModeSection.setDisable(!newVal);
            colorPickerSection.setDisable(!newVal || !solidColorRadio.isSelected());
        });
        
        // Show/hide color picker based on color mode
        colorModeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            colorPickerSection.setDisable(newVal != solidColorRadio || !visualizerEnabledCheckBox.isSelected());
        });
        
        // Enable/disable update interval based on auto-check state
        autoCheckUpdatesCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateIntervalSection.setDisable(!newVal);
        });
        
        // Create custom icon buttons if buttonBar exists
        if (buttonBar != null) {
            setupIconButtons();
        }

        if (browseMusicFolderButton != null) {
            browseMusicFolderButton.setOnAction(e -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Select Music Folder");
                File selected = chooser.showDialog(browseMusicFolderButton.getScene().getWindow());
                if (selected != null && selected.isDirectory()) {
                    if (musicRootPathField != null) {
                        musicRootPathField.setText(selected.getAbsolutePath());
                    }
                }
            });
        }
    }
    
    /**
     * Set up custom icon buttons for OK and Cancel.
     */
    private void setupIconButtons() {
        // Clear any existing content
        buttonBar.getChildren().clear();
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setSpacing(10);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));
        
        // Create Save button with icon
        saveButton = new Button();
        ImageView saveIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/save.png")));
        saveIcon.setFitWidth(24);
        saveIcon.setFitHeight(24);
        saveIcon.setPreserveRatio(true);
        saveButton.setGraphic(saveIcon);
        saveButton.getStyleClass().add("icon-button");
        
        // Create Cancel button with icon
        cancelButton = new Button();
        ImageView cancelIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/cancel.png")));
        cancelIcon.setFitWidth(24);
        cancelIcon.setFitHeight(24);
        cancelIcon.setPreserveRatio(true);
        cancelButton.setGraphic(cancelIcon);
        cancelButton.getStyleClass().add("icon-button");
        
        // Add buttons to the bar
        buttonBar.getChildren().addAll(saveButton, cancelButton);
    }
    
    /**
     * Get the save button for external event handling.
     */
    public Button getSaveButton() {
        return saveButton;
    }
    
    /**
     * Get the cancel button for external event handling.
     */
    public Button getCancelButton() {
        return cancelButton;
    }
    
    /**
     * Load current settings into the UI.
     */
    private void loadSettings() {
        // Set theme selection
        if (themeChoiceBox != null) {
            themeChoiceBox.setValue(settings.getTheme());
        }
        
        // Set visualizer enabled state
        visualizerEnabledCheckBox.setSelected(settings.isVisualizerEnabled());
        

        // Set color mode
        if (settings.getVisualizerColorMode() == Settings.VisualizerColorMode.GRADIENT_CYCLING) {
            gradientCyclingRadio.setSelected(true);
        } else {
            solidColorRadio.setSelected(true);
        }
        
        // Set solid color
        try {
            solidColorPicker.setValue(Color.web(settings.getVisualizerSolidColor()));
        } catch (Exception e) {
            // Default to green if color parsing fails
            solidColorPicker.setValue(Color.LIMEGREEN);
        }
        
        // Update enabled states
        colorModeSection.setDisable(!settings.isVisualizerEnabled());
        colorPickerSection.setDisable(!settings.isVisualizerEnabled() ||
                                     settings.getVisualizerColorMode() != Settings.VisualizerColorMode.SOLID_COLOR);
        
        // Load update settings
        autoCheckUpdatesCheckBox.setSelected(settings.isAutoCheckForUpdates());
        updateIntervalSpinner.getValueFactory().setValue(settings.getUpdateCheckIntervalHours());
        showPreReleaseCheckBox.setSelected(settings.isShowPreReleaseVersions());
        downloadInBackgroundCheckBox.setSelected(settings.isDownloadUpdatesInBackground());
        
        // Load distribution type preference
        DistributionType preferredType = settings.getPreferredDistributionType();
        switch (preferredType) {
            case INSTALLER:
                installerRadio.setSelected(true);
                break;
            case RELEASE:
                releaseRadio.setSelected(true);
                break;
            case PORTABLE:
                portableRadio.setSelected(true);
                break;
            default:
                // Default to release if unknown
                releaseRadio.setSelected(true);
                break;
        }
        
        // Update interval enabled state
        updateIntervalSection.setDisable(!settings.isAutoCheckForUpdates());
        
        // Last update check
        LocalDateTime lastCheck = settings.getLastUpdateCheck();
        if (lastCheck != null) {
            lastUpdateCheckLabel.setText(lastCheck.format(DATE_TIME_FORMATTER));
        } else {
            lastUpdateCheckLabel.setText("Never");
        }

        if (musicRootPathField != null) {
            String path = settings.getMusicRootPath();
            musicRootPathField.setText(path != null ? path : "");
        }
        if (libraryWatcherEnabledCheckBox != null) {
            libraryWatcherEnabledCheckBox.setSelected(settings.isLibraryWatcherEnabled());
        }
    }
    
    /**
     * Save the current settings when OK is clicked.
     * This method is called before the dialog is closed.
     */
    public void saveSettings() {
        // Update settings from UI
        if (themeChoiceBox != null) {
            settings.setTheme(themeChoiceBox.getValue());
        }
        settings.setVisualizerEnabled(visualizerEnabledCheckBox.isSelected());
        
        if (gradientCyclingRadio.isSelected()) {
            settings.setVisualizerColorMode(Settings.VisualizerColorMode.GRADIENT_CYCLING);
        } else {
            settings.setVisualizerColorMode(Settings.VisualizerColorMode.SOLID_COLOR);
        }
        
        // Convert Color to hex string
        Color color = solidColorPicker.getValue();
        String hexColor = String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
        settings.setVisualizerSolidColor(hexColor);
        
        // Save update settings
        settings.setAutoCheckForUpdates(autoCheckUpdatesCheckBox.isSelected());
        settings.setUpdateCheckIntervalHours(updateIntervalSpinner.getValue());
        settings.setShowPreReleaseVersions(showPreReleaseCheckBox.isSelected());
        settings.setDownloadUpdatesInBackground(downloadInBackgroundCheckBox.isSelected());
        
        // Save distribution type preference
        if (installerRadio.isSelected()) {
            settings.setPreferredDistributionType(DistributionType.INSTALLER);
        } else if (releaseRadio.isSelected()) {
            settings.setPreferredDistributionType(DistributionType.RELEASE);
        } else if (portableRadio.isSelected()) {
            settings.setPreferredDistributionType(DistributionType.PORTABLE);
        } else {
            // Default to release if none selected
            settings.setPreferredDistributionType(DistributionType.RELEASE);
        }
        
        // Save library settings
        if (musicRootPathField != null) {
            settings.setMusicRootPath(musicRootPathField.getText());
        }
        if (libraryWatcherEnabledCheckBox != null) {
            settings.setLibraryWatcherEnabled(libraryWatcherEnabledCheckBox.isSelected());
        }
        
        // Save to file
        settingsService.saveSettings();

        if (musicLibraryManager != null) {
            musicLibraryManager.refreshWatcherFromSettings();
        }
    }
    
    /**
     * Handle check for updates button click.
     */
    @FXML
    private void onCheckForUpdates() {
        if (updateService == null) {
            showError("Update service not available");
            return;
        }
        
        // Disable button and show checking status
        checkNowButton.setDisable(true);
        checkNowButton.setText("Checking...");
        
        // Check for updates
        updateService.checkForUpdates().thenAccept(updateInfo -> {
            Platform.runLater(() -> {
                checkNowButton.setDisable(false);
                checkNowButton.setText("Check for Updates Now");
                
                if (updateInfo != null) {
                    // Show update dialog
                    UpdateDialog updateDialog = new UpdateDialog(updateService, updateInfo);
                    updateDialog.showAndWait();
                    
                    // Refresh last check time
                    LocalDateTime lastCheck = settings.getLastUpdateCheck();
                    if (lastCheck != null) {
                        lastUpdateCheckLabel.setText(lastCheck.format(DATE_TIME_FORMATTER));
                    }
                } else {
                    // Show up-to-date message
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("No Updates Available");
                    alert.setHeaderText(null);
                    alert.setContentText("You are running the latest version of SiMP3 (" +
                        updateService.getCurrentVersion() + ")");
                    alert.showAndWait();
                }
            });
        }).exceptionally(e -> {
            Platform.runLater(() -> {
                checkNowButton.setDisable(false);
                checkNowButton.setText("Check for Updates Now");
                showError("Failed to check for updates: " + e.getMessage());
            });
            return null;
        });
    }
    
    /**
     * Show an error alert.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
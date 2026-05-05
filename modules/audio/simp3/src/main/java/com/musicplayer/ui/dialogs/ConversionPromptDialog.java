package com.musicplayer.ui.dialogs;

import java.io.File;
import java.util.List;

import com.musicplayer.services.AudioConversionService;
import com.musicplayer.services.AudioConversionService.ConversionAnalysis;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Dialog that prompts users to convert less-ideal audio formats for better JavaFX support.
 */
public class ConversionPromptDialog {
    
    private final Window owner;
    private final AudioConversionService conversionService;
    private final ConversionAnalysis analysis;
    private Stage dialog;
    private boolean userAccepted = false;
    private boolean rememberChoice = false;
    
    public ConversionPromptDialog(Window owner, AudioConversionService conversionService, ConversionAnalysis analysis) {
        this.owner = owner;
        this.conversionService = conversionService;
        this.analysis = analysis;
    }
    
    /**
     * Show the dialog and return user's choice.
     * @return true if user wants to convert, false otherwise
     */
    public boolean showAndWait() {
        createDialog();
        dialog.showAndWait();
        return userAccepted;
    }
    
    /**
     * Check if user wants to remember their choice.
     */
    public boolean shouldRememberChoice() {
        return rememberChoice;
    }
    
    private void createDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Audio Format Optimization");
        dialog.setResizable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);
        
        // Header
        Label headerLabel = new Label("Optimize Audio Files for Better Performance");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        // Analysis info
        VBox infoBox = new VBox(8);
        infoBox.getChildren().addAll(
            new Label(String.format("Found %d audio files in your music library:", analysis.getTotalAudioFiles())),
            new Label(String.format("• %d files are already optimized (WAV/AIFF)", 
                analysis.getTotalAudioFiles() - analysis.getConvertibleFiles())),
            new Label(String.format("• %d files can be optimized (MP3/FLAC/OGG) - %.1f%%", 
                analysis.getConvertibleFiles(), analysis.getConvertiblePercentage()))
        );
        
        // Benefits explanation
        TextArea benefitsArea = new TextArea();
        benefitsArea.setEditable(false);
        benefitsArea.setPrefRowCount(4);
        benefitsArea.setWrapText(true);
        benefitsArea.setText(
            "Converting MP3, FLAC, and OGG files to WAV format will:\n" +
            "• Enable better visualizer performance\n" +
            "• Improve playback performance and reliability on Linux\n" +
            "• Ensure all audio controls work consistently\n" +
            "• Provide better long-term compatibility"
        );
        
        // Technical details
        Label techLabel = new Label("Technical Details:");
        techLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        VBox techBox = new VBox(5);
        techBox.getChildren().addAll(
            new Label("• Converted files will be saved in folders with '-converted' suffix"),
            new Label("• Original files will be preserved"),
            new Label("• Metadata (artist, album, title) will be copied"),
            new Label("• High-quality conversion settings will be used")
        );
        
        // Remember choice checkbox
        CheckBox rememberCheckBox = new CheckBox("Remember my choice and don't ask again");
        rememberCheckBox.setOnAction(e -> rememberChoice = rememberCheckBox.isSelected());
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button convertButton = new Button("Convert Files");
        convertButton.setPrefWidth(120);
        convertButton.setDefaultButton(true);
        convertButton.setOnAction(e -> {
            userAccepted = true;
            startConversion();
        });
        
        Button skipButton = new Button("Skip");
        skipButton.setPrefWidth(80);
        skipButton.setOnAction(e -> {
            userAccepted = false;
            dialog.close();
        });
        
        Button laterButton = new Button("Ask Later");
        laterButton.setPrefWidth(80);
        laterButton.setOnAction(e -> {
            userAccepted = false;
            rememberChoice = false;
            dialog.close();
        });
        
        buttonBox.getChildren().addAll(laterButton, skipButton, convertButton);
        
        // Add all components
        root.getChildren().addAll(
            headerLabel,
            infoBox,
            benefitsArea,
            techLabel,
            techBox,
            rememberCheckBox,
            buttonBox
        );
        
        Scene scene = new Scene(root, 500, 450);
        dialog.setScene(scene);
    }
    
    private void startConversion() {
        // Replace dialog content with progress view
        VBox progressRoot = new VBox(15);
        progressRoot.setPadding(new Insets(20));
        progressRoot.setAlignment(Pos.CENTER);
        
        Label progressTitle = new Label("Converting Audio Files...");
        progressTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        Label progressLabel = new Label("Preparing conversion...");
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        
        Label fileLabel = new Label("");
        
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            // TODO: Implement cancellation
            dialog.close();
        });
        
        progressRoot.getChildren().addAll(
            progressTitle,
            progressLabel,
            progressBar,
            fileLabel,
            cancelButton
        );
        
        Scene progressScene = new Scene(progressRoot, 500, 200);
        dialog.setScene(progressScene);
        
        // Start conversion
        conversionService.convertFilesWithAutoDirectory(analysis.getFilesToConvert(), 
            new AudioConversionService.ConversionProgressCallback() {
                @Override
                public void onProgress(String fileName, int current, int total, double percentage) {
                    javafx.application.Platform.runLater(() -> {
                        progressLabel.setText(String.format("Converting file %d of %d", current, total));
                        fileLabel.setText(fileName);
                        progressBar.setProgress(percentage / 100.0);
                    });
                }
                
                @Override
                public void onComplete(List<File> convertedFiles, List<String> errors) {
                    javafx.application.Platform.runLater(() -> {
                        showCompletionDialog(convertedFiles.size(), errors);
                    });
                }
                
                @Override
                public void onError(String fileName, Exception error) {
                    // Individual file errors are handled in onComplete
                }
            });
    }
    
    private void showCompletionDialog(int convertedCount, List<String> errors) {
        VBox completionRoot = new VBox(15);
        completionRoot.setPadding(new Insets(20));
        completionRoot.setAlignment(Pos.CENTER);
        
        Label completionTitle = new Label("Conversion Complete!");
        completionTitle.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        VBox resultsBox = new VBox(8);
        resultsBox.setAlignment(Pos.CENTER_LEFT);
        resultsBox.getChildren().add(new Label(String.format("Successfully converted %d files", convertedCount)));
        
        if (!errors.isEmpty()) {
            resultsBox.getChildren().add(new Label(String.format("%d files had errors", errors.size())));
            
            if (errors.size() <= 5) {
                for (String error : errors) {
                    Label errorLabel = new Label("• " + error);
                    errorLabel.setStyle("-fx-text-fill: #cc0000;");
                    resultsBox.getChildren().add(errorLabel);
                }
            }
        }
        
        resultsBox.getChildren().add(new Label(""));
        resultsBox.getChildren().add(new Label("Converted files are saved in folders with '-converted' suffix."));
        resultsBox.getChildren().add(new Label("Your library will be refreshed to include the new files."));
        
        Button closeButton = new Button("Close");
        closeButton.setPrefWidth(100);
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> dialog.close());
        
        completionRoot.getChildren().addAll(
            completionTitle,
            resultsBox,
            closeButton
        );
        
        Scene completionScene = new Scene(completionRoot, 500, 300);
        dialog.setScene(completionScene);
    }
}

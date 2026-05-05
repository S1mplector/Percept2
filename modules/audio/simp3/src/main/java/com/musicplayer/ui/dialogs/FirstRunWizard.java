package com.musicplayer.ui.dialogs;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * First-run wizard to help users set up their music library.
 */
public class FirstRunWizard {

    public static File show(Window owner) {
        Dialog<File> dialog = new Dialog<>();
        dialog.setTitle("Welcome to SiMP3!");
        dialog.initOwner(owner);
        dialog.setResizable(false);

        // Content
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(400);

        // Icon
        ImageView icon = new ImageView(new Image(FirstRunWizard.class.getResourceAsStream("/images/icons/folder.png")));
        icon.setFitHeight(64);
        icon.setFitWidth(64);

        // Title
        Label titleLabel = new Label("Let's add your music!");
        titleLabel.setFont(Font.font(18));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Description
        Label descLabel = new Label("To get started, please select the folder where your music files are stored.");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-alignment: center;");

        // Folder path display
        Label pathLabel = new Label("No folder selected");
        pathLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");

        // Browse button
        Button browseBtn = new Button("Choose Music Folder");
        browseBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 20 10 20;");

        final File[] selectedFolder = {null};

        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Your Music Folder");
            File folder = chooser.showDialog(dialog.getOwner());
            if (folder != null) {
                selectedFolder[0] = folder;
                pathLabel.setText(folder.getAbsolutePath());
                pathLabel.setStyle("-fx-text-fill: black; -fx-font-style: normal;");
            }
        });

        content.getChildren().addAll(icon, titleLabel, descLabel, browseBtn, pathLabel);

        dialog.getDialogPane().setContent(content);

        // Buttons
        ButtonType skipBtn = new ButtonType("Skip for Now");
        ButtonType okBtn = new ButtonType("Start");
        dialog.getDialogPane().getButtonTypes().addAll(skipBtn, okBtn);

        // Enable OK only when folder selected
        Button okButton = (Button) dialog.getDialogPane().lookupButton(okBtn);
        okButton.setDisable(true);
        
        pathLabel.textProperty().addListener((obs, old, text) -> {
            okButton.setDisable("No folder selected".equals(text));
        });

        // Result converter
        dialog.setResultConverter(buttonType -> {
            if (buttonType == okBtn) {
                return selectedFolder[0];
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
} 
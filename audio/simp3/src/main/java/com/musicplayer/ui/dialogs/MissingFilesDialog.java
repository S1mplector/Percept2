package com.musicplayer.ui.dialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * Dialog to handle missing music files.
 */
public class MissingFilesDialog {

    public static boolean show(Window owner) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle("Music Files Not Found");
        alert.setHeaderText("Your music files could not be found");
        alert.setContentText("The music files in your library appear to have been moved or deleted.\n\n" +
                           "Would you like to:\n" +
                           "• Clear the library and select a new music folder\n" +
                           "• Keep the library and try to play other songs");

        ButtonType clearBtn = new ButtonType("Clear & Select New Folder");
        ButtonType keepBtn = new ButtonType("Keep Library");
        
        alert.getButtonTypes().setAll(clearBtn, keepBtn);
        
        return alert.showAndWait()
                    .map(result -> result == clearBtn)
                    .orElse(false);
    }
} 
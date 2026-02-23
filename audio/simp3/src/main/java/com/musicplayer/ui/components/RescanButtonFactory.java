package com.musicplayer.ui.components;

import com.musicplayer.services.MusicLibraryManager;

import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Factory for creating a rescan library button with a green glow animation.
 */
public class RescanButtonFactory {

    private static final String BUTTON_STYLE = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

    public static Button createRescanButton(MusicLibraryManager libraryManager) {
        Image icon = new Image(RescanButtonFactory.class.getResourceAsStream("/images/icons/rescan.png"));
        ImageView iv = new ImageView(icon);
        iv.setFitHeight(20);
        iv.setFitWidth(20);

        Button btn = new Button();
        btn.setGraphic(iv);
        btn.setStyle(BUTTON_STYLE);
        btn.setMnemonicParsing(false);
        btn.setPrefSize(28, 28);

        btn.setOnAction(e -> {
            // Trigger rescan
            libraryManager.rescanCurrentFolder();

            // Apply green glow effect briefly
            DropShadow glow = new DropShadow(20, Color.LIMEGREEN);
            iv.setEffect(glow);
            PauseTransition pt = new PauseTransition(Duration.seconds(0.7));
            pt.setOnFinished(ev -> iv.setEffect(null));
            pt.play();
        });

        return btn;
    }
} 
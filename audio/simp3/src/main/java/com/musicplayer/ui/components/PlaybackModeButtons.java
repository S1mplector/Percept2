package com.musicplayer.ui.components;

import com.musicplayer.core.playlist.PlaylistEngine;
import com.musicplayer.services.AudioPlayerService;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Factory for creating playback mode buttons (shuffle & repeat) with encapsulated logic.
 */
public class PlaybackModeButtons {

    private static final String BUTTON_STYLE = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

    public static Button createShuffleButton(AudioPlayerService service) {
        Image shuffleImg = new Image(PlaybackModeButtons.class.getResourceAsStream("/images/icons/shuffle.png"));

        ImageView iv = new ImageView(shuffleImg);
        iv.setFitHeight(32);
        iv.setFitWidth(32);

        Button btn = new Button();
        btn.setGraphic(iv);
        btn.setStyle(BUTTON_STYLE);
        btn.setMnemonicParsing(false);

        // Initialize icon based on current state
        iv.setOpacity(service.isShuffle() ? 1.0 : 0.4);

        btn.setOnAction(e -> {
            boolean newState = !service.isShuffle();
            service.setShuffle(newState);
            iv.setOpacity(newState ? 1.0 : 0.4);
        });

        // Tooltip optional (not adding to avoid more imports)
        return btn;
    }

    public static Button createRepeatButton(AudioPlayerService service) {
        Image repeatImg = new Image(PlaybackModeButtons.class.getResourceAsStream("/images/icons/repeat.png"));

        ImageView iv = new ImageView(repeatImg);
        iv.setFitHeight(32);
        iv.setFitWidth(32);

        // Overlay label "1" for repeat-one indication
        Label lblOne = new Label("1");
        lblOne.setStyle("-fx-text-fill: black; -fx-font-size: 10px; -fx-font-weight: bold;");
        lblOne.setVisible(false);

        StackPane graphicPane = new StackPane(iv, lblOne);
        StackPane.setAlignment(lblOne, Pos.TOP_RIGHT);

        Button btn = new Button();
        btn.setGraphic(graphicPane);
        btn.setStyle(BUTTON_STYLE);
        btn.setMnemonicParsing(false);

        // Initialize state visuals
        updateRepeatUI(service.getRepeatMode(), iv, lblOne);

        btn.setOnAction(e -> {
            PlaylistEngine.RepeatMode current = service.getRepeatMode();
            PlaylistEngine.RepeatMode next;
            switch (current) {
                case NONE:
                    next = PlaylistEngine.RepeatMode.ALL;
                    break;
                case ALL:
                    next = PlaylistEngine.RepeatMode.ONE;
                    break;
                case ONE:
                default:
                    next = PlaylistEngine.RepeatMode.NONE;
                    break;
            }
            service.setRepeatMode(next);
            updateRepeatUI(next, iv, lblOne);
        });

        return btn;
    }

    private static void updateRepeatUI(PlaylistEngine.RepeatMode mode, ImageView iv, Label lblOne) {
        switch (mode) {
            case ONE:
                iv.setOpacity(1.0);
                // show label temporarily
                lblOne.setVisible(true);
                PauseTransition pt = new PauseTransition(Duration.seconds(1));
                pt.setOnFinished(ev -> lblOne.setVisible(false));
                pt.play();
                break;
            case ALL:
                lblOne.setVisible(false);
                iv.setOpacity(1.0);
                break;
            case NONE:
            default:
                lblOne.setVisible(false);
                iv.setOpacity(0.4);
                break;
        }
    }
} 
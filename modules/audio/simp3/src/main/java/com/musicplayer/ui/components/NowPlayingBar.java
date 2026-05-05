package com.musicplayer.ui.components;

import com.musicplayer.data.models.Song;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Compact bar that shows currently playing song with a pulsing icon.
 */
public class NowPlayingBar extends HBox {

    private final ImageView playingIcon;
    private final Label textLabel;
    private final FadeTransition fade;
    private TranslateTransition scroll;

    public NowPlayingBar() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setPadding(new Insets(0, 8, 0, 8));

        playingIcon = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/playing.png")));
        playingIcon.setFitHeight(16);
        playingIcon.setFitWidth(16);
        playingIcon.setOpacity(0);

        fade = new FadeTransition(Duration.seconds(1), playingIcon);
        fade.setFromValue(0.2);
        fade.setToValue(1.0);
        fade.setAutoReverse(true);
        fade.setCycleCount(FadeTransition.INDEFINITE);

        textLabel = new Label("");
        textLabel.setStyle("-fx-text-fill: #444444; -fx-font-size: 12px;");
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, javafx.scene.layout.Priority.ALWAYS);

        // Clip to keep text inside bar
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty().subtract(40)); // leave space for icon and padding
        clip.heightProperty().bind(heightProperty());
        this.setClip(clip);

        // Listen for width changes to adjust scrolling
        widthProperty().addListener((o, ov, nv) -> setupScroll());

        getChildren().addAll(playingIcon, textLabel);
        setVisible(false);
    }

    private void setupScroll() {
        if (scroll != null) {
            scroll.stop();
        }
        double labelWidth = textLabel.getBoundsInParent().getWidth();
        double available = getWidth() - 40; // icon + padding
        if (labelWidth > available) {
            scroll = new TranslateTransition(Duration.seconds(10 + labelWidth / 30), textLabel);
            scroll.setFromX(0);
            scroll.setToX(-(labelWidth - available));
            scroll.setAutoReverse(false);
            scroll.setCycleCount(TranslateTransition.INDEFINITE);
            scroll.play();
        } else {
            textLabel.setTranslateX(0);
        }
    }

    public void update(Song song, boolean isPlaying) {
        if (song == null) {
            setVisible(false);
            fade.stop();
            if (scroll != null) scroll.stop();
            return;
        }
        setVisible(true);
        textLabel.setText(song.getTitle() + " - " + song.getArtist());
        layout(); // ensure width calculated
        setupScroll();
        if (isPlaying) {
            playingIcon.setOpacity(0.8);
            fade.play();
        } else {
            fade.stop();
            playingIcon.setOpacity(0.4);
        }
    }
} 
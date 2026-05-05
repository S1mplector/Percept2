package com.musicplayer.ui.components;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * Represents an activity/notification item in the feed.
 */
public class ActivityFeedItem extends HBox {
    
    public enum ActivityType {
        SCAN_COMPLETE, NEW_TRACKS, ERROR, PLAYLIST_SAVED, FILES_MISSING
    }
    
    public ActivityFeedItem(ActivityType type, String message) {
        setupUI(type, message);
        
        // Fade in animation
        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private void setupUI(ActivityType type, String message) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(5));
        setMaxWidth(Double.MAX_VALUE);
        
        // Icon based on type
        String iconPath = switch (type) {
            case SCAN_COMPLETE -> "/images/icons/folder.png";
            case NEW_TRACKS -> "/images/icons/add.png";
            case ERROR -> "/images/icons/remove.png";
            case PLAYLIST_SAVED -> "/images/icons/song.png";
            case FILES_MISSING -> "/images/icons/search.png";
        };
        
        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(iconPath)));
        icon.setFitHeight(16);
        icon.setFitWidth(16);
        
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(180);
        
        getChildren().addAll(icon, messageLabel);
        
        // Style based on type
        String bgColor = switch (type) {
            case ERROR, FILES_MISSING -> "#4d2222"; // red tint
            case NEW_TRACKS, PLAYLIST_SAVED -> "#224d22"; // green tint
            default -> "#333333"; // neutral
        };
        
        setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 3;");
    }
} 
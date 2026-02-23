package com.musicplayer.ui.components;

import java.io.File;
import java.io.InputStream;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

/**
 * Represents a pinned item (album/playlist) in the pinboard.
 */
public class PinboardItem extends HBox {
    
    public enum ItemType {
        ALBUM, PLAYLIST, ARTIST
    }
    
    private final String id;
    private String name;
    private ItemType type;
    private Runnable onClickAction;
    private ImageView iconView;
    private Label nameLabel;
    private File customIconFile;
    private String actionId;
    
    public PinboardItem(String id, String name, ItemType type, Runnable onClickAction) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.onClickAction = onClickAction;
        
        setupUI();
    }
    
    private void setupUI() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setPadding(new Insets(5));
        setCursor(Cursor.HAND);
        setMaxWidth(Double.MAX_VALUE);
        
        iconView = new ImageView();
        iconView.setFitHeight(20);
        iconView.setFitWidth(20);
        updateIcon();
        
        nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #444444; -fx-font-size: 12px;");
        nameLabel.setMaxWidth(150);
        nameLabel.setEllipsisString("...");
        
        getChildren().addAll(iconView, nameLabel);
        
        // Hover effect
        setOnMouseEntered(e -> {
            // Semi-transparent lime overlay without blur
            setStyle("-fx-background-color: #32cd3246; -fx-background-radius: 3;");
            // Removed DropShadow to avoid blurring text
        });
        
        setOnMouseExited(e -> {
            setStyle("-fx-background-color: transparent;");
            setEffect(null);
        });
        
        setOnMouseClicked(e -> {
            if (onClickAction != null) {
                onClickAction.run();
            }
        });
    }
    
    private String getIconPath() {
        if ("favorites".equals(id)) {
            return "/images/icons/fav.png";
        }
        return switch (type) {
            case ALBUM -> "/images/icons/album_placeholder.png";
            case PLAYLIST -> "/images/icons/song.png";
            case ARTIST -> "/images/icons/app.png";
        };
    }
    
    private void updateIcon() {
        if (iconView == null) return;
        try {
            if (customIconFile != null && customIconFile.exists()) {
                iconView.setImage(new Image(customIconFile.toURI().toString()));
                return;
            }
            String path = getIconPath();
            InputStream is = getClass().getResourceAsStream(path);
            if (is != null) {
                iconView.setImage(new Image(is));
            }
        } catch (Exception ignored) {}
    }
    
    public String getItemId() {
        return id;
    }
    
    public ItemType getType() {
        return type;
    }
    
    public void setType(ItemType newType) {
        this.type = newType;
        updateIcon();
    }
    
    public Runnable getOnClickAction() {
        return onClickAction;
    }
    
    public void setOnClickAction(Runnable action) {
        this.onClickAction = action;
    }
    
    public void setCustomIconFile(File file) {
        this.customIconFile = file;
        updateIcon();
    }
    
    public void clearCustomIcon() {
        this.customIconFile = null;
        updateIcon();
    }
    
    public void setName(String newName) {
        this.name = newName;
        if (nameLabel != null) {
            nameLabel.setText(name);
        }
    }

    public String getName() {
        return name;
    }

    public File getCustomIconFile() {
        return customIconFile;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }
}
package com.musicplayer.ui.components;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Combined pinboard, library stats, and listening stats panel.
 */
public class PinboardPanel extends VBox {
    
    private final VBox libraryStatsSection;
    private final VBox pinboardSection;
    private final VBox listeningStatsSection;
    private final List<PinboardItem> pinnedItems = new ArrayList<>();
    private Button manageButton;
    
    // Labels for library stats
    private Label totalSongsLabel;
    private Label totalAlbumsLabel;
    private Label musicFolderLabel;
    
    // Labels for listening stats
    private Label listenedTodayLabel;
    private Label listenedWeeklyLabel;
    private Label listenedMonthlyLabel;
    private Label mostPlayedLabel;
    
    public PinboardPanel() {
        setSpacing(20); // Increased spacing for better distribution
        setPadding(new Insets(10));
        
        // Library stats section - placed under Library header
        libraryStatsSection = new VBox(3);
        libraryStatsSection.setPadding(new Insets(0, 0, 10, 0));
        
        // Initialize library stats labels
        totalSongsLabel = createStatsLabel("Songs: 0");
        totalAlbumsLabel = createStatsLabel("Albums: 0");
        musicFolderLabel = createStatsLabel("Folder: Not set");
        musicFolderLabel.setWrapText(true);
        
        libraryStatsSection.getChildren().addAll(
            totalSongsLabel,
            totalAlbumsLabel,
            musicFolderLabel
        );
        
        // Pinboard section
        Label pinboardLabel = new Label("Pinboard");
        pinboardLabel.setFont(Font.font(14));
        pinboardLabel.getStyleClass().add("sidebar-header");

        manageButton = new Button();
        manageButton.getStyleClass().add("icon-button");
        manageButton.setFocusTraversable(false);
        manageButton.setPrefSize(24, 24);
        manageButton.setMinSize(24, 24);
        try {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/images/icons/settings.png")));
            iv.setFitWidth(16);
            iv.setFitHeight(16);
            iv.setPreserveRatio(true);
            manageButton.setGraphic(iv);
        } catch (Exception ignored) {}

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, pinboardLabel, spacer, manageButton);
        header.setPadding(new Insets(0, 0, 0, 0));

        pinboardSection = new VBox(5);
        pinboardSection.setPadding(new Insets(5, 0, 10, 0));
        
        // Listening stats section (previously Activity)
        Label listeningStatsLabel = new Label("Listening Stats");
        listeningStatsLabel.setFont(Font.font(14));
        listeningStatsLabel.getStyleClass().add("sidebar-header");
        
        listeningStatsSection = new VBox(5);
        listeningStatsSection.setPadding(new Insets(5, 0, 0, 0));
        
        // Initialize listening stats labels
        listenedTodayLabel = createStatsLabel("Today: 0 songs");
        listenedWeeklyLabel = createStatsLabel("This week: 0 songs");
        listenedMonthlyLabel = createStatsLabel("This month: 0 songs");
        mostPlayedLabel = createStatsLabel("Most played: -");
        mostPlayedLabel.setWrapText(true);
        
        listeningStatsSection.getChildren().addAll(
            listenedTodayLabel,
            listenedWeeklyLabel,
            listenedMonthlyLabel,
            mostPlayedLabel
        );
        
        // Add all sections
        getChildren().addAll(
            libraryStatsSection,
            new Separator(),
            header,
            pinboardSection,
            new Separator(),
            listeningStatsLabel,
            listeningStatsSection
        );
    }
    
    private Label createStatsLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        return label;
    }
    
    // Library stats update methods (thread-safe)
    public void updateLibraryStats(int totalSongs, int totalAlbums, String musicFolder) {
        Runnable uiUpdate = () -> {
            totalSongsLabel.setText("Songs: " + totalSongs);
            totalAlbumsLabel.setText("Albums: " + totalAlbums);
            if (musicFolder != null && !musicFolder.isEmpty()) {
                // Show only folder name or last two directories for brevity
                File folder = new File(musicFolder);
                String displayPath = folder.getName();
                if (folder.getParentFile() != null) {
                    displayPath = folder.getParentFile().getName() + "/" + displayPath;
                }
                musicFolderLabel.setText("Folder: " + displayPath);
            } else {
                musicFolderLabel.setText("Folder: Not set");
            }
        };

        // Ensure the update happens on the JavaFX Application Thread
        if (javafx.application.Platform.isFxApplicationThread()) {
            uiUpdate.run();
        } else {
            javafx.application.Platform.runLater(uiUpdate);
        }
    }
    
    // Listening stats update methods (thread-safe)
    public void updateListeningStats(int todayCount, int weeklyCount, int monthlyCount, String mostPlayed) {
        Runnable uiUpdate = () -> {
            listenedTodayLabel.setText("Today: " + todayCount + " songs");
            listenedWeeklyLabel.setText("This week: " + weeklyCount + " songs");
            listenedMonthlyLabel.setText("This month: " + monthlyCount + " songs");
            mostPlayedLabel.setText("Most played: " + (mostPlayed != null ? mostPlayed : "-"));
        };
        if (javafx.application.Platform.isFxApplicationThread()) {
            uiUpdate.run();
        } else {
            javafx.application.Platform.runLater(uiUpdate);
        }
    }
    
    // Existing pinboard methods remain the same
    public PinboardItem addPinnedItem(String id, String name, PinboardItem.ItemType type, Runnable onClickAction) {
        // Check if already pinned
        if (pinnedItems.stream().anyMatch(item -> item.getItemId().equals(id))) {
            // Return the existing item
            return pinnedItems.stream().filter(item -> item.getItemId().equals(id)).findFirst().orElse(null);
        }
        
        PinboardItem item = new PinboardItem(id, name, type, onClickAction);
        pinnedItems.add(item);
        pinboardSection.getChildren().add(item);
        attachItemContextMenu(item);
        return item;
    }
    
    public void removePinnedItem(String id) {
        pinnedItems.removeIf(item -> {
            if (item.getItemId().equals(id)) {
                pinboardSection.getChildren().remove(item);
                return true;
            }
            return false;
        });
    }
    
    public boolean isPinned(String id) {
        return pinnedItems.stream().anyMatch(item -> item.getItemId().equals(id));
    }
    
    public Button getManageButton() {
        return manageButton;
    }
    
    public List<PinboardItem> getPinnedItems() {
        return pinnedItems;
    }
    
    // Keep the activity notification method for future toast implementation
    // This will be repurposed later
    public void addActivity(ActivityFeedItem.ActivityType type, String message) {
        // For now, this is a no-op. We'll implement toast notifications later.
        // The notification logic is preserved here for future use.
    }
    
    public void clearActivities() {
        // No-op for now, preserved for compatibility
    }

    private void attachItemContextMenu(PinboardItem item) {
        ContextMenu menu = new ContextMenu();
        MenuItem moveUp = new MenuItem("Move Up");
        MenuItem moveDown = new MenuItem("Move Down");
        Menu changeCat = new Menu("Change Icon");
        MenuItem asAlbum = new MenuItem("Album");
        MenuItem asPlaylist = new MenuItem("Playlist");
        MenuItem asArtist = new MenuItem("Artist");
        MenuItem asCustom = new MenuItem("Custom...");
        changeCat.getItems().addAll(asAlbum, asPlaylist, asArtist, new SeparatorMenuItem(), asCustom);
        menu.getItems().addAll(moveUp, moveDown, new SeparatorMenuItem(), changeCat);

        moveUp.setOnAction(e -> moveItem(item, -1));
        moveDown.setOnAction(e -> moveItem(item, +1));
        asAlbum.setOnAction(e -> item.setType(PinboardItem.ItemType.ALBUM));
        asPlaylist.setOnAction(e -> item.setType(PinboardItem.ItemType.PLAYLIST));
        asArtist.setOnAction(e -> item.setType(PinboardItem.ItemType.ARTIST));
        asCustom.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose Custom Icon");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File f = fc.showOpenDialog(item.getScene().getWindow());
            if (f != null) {
                item.setCustomIconFile(f);
            }
        });

        item.setOnContextMenuRequested(ev -> {
            menu.show(item, ev.getScreenX(), ev.getScreenY());
            ev.consume();
        });
    }

    private void moveItem(PinboardItem item, int delta) {
        int idx = pinboardSection.getChildren().indexOf(item);
        if (idx < 0) return;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= pinboardSection.getChildren().size()) return;

        pinboardSection.getChildren().remove(idx);
        pinboardSection.getChildren().add(newIdx, item);

        // Keep internal list in sync
        pinnedItems.remove(item);
        pinnedItems.add(newIdx, item);
    }
}
package com.jvn.fx.phone;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneData;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * JavaFX overlay renderer for {@link PhoneScene}.
 *
 * <p>The underlying VN continues to render on canvas; this control is only the
 * styled JavaFX layer shown above it.</p>
 */
public final class PhoneRenderer extends StackPane {
  private final Region backdrop = new Region();
  private final StackPane shell = new StackPane();
  private final ImageView wallpaperView = new ImageView();
  private final BorderPane phoneRoot = new BorderPane();

  private final Button navButton = new Button("Close");
  private final Button auxButton = new Button("Home");
  private final Label titleLabel = new Label("Phone");
  private final Label subtitleLabel = new Label("Messages");
  private final ScrollPane homeScroll = new ScrollPane();
  private final VBox homeList = new VBox(8);
  private final ScrollPane messageScroll = new ScrollPane();
  private final VBox messageList = new VBox(10);
  private final Label footerLabel = new Label("Esc closes");

  private final Map<String, Image> imageCache = new HashMap<>();

  private PhoneScene sceneModel;
  private File projectRoot;

  public PhoneRenderer() {
    getStyleClass().add("phone-overlay");
    setVisible(false);
    setManaged(false);
    setMouseTransparent(true);
    setPickOnBounds(false);

    backdrop.getStyleClass().add("phone-backdrop");
    backdrop.setOnMouseClicked(e -> {
      if (sceneModel == null) return;
      sceneModel.requestClose();
      refresh();
      e.consume();
    });

    wallpaperView.getStyleClass().add("phone-wallpaper");
    wallpaperView.setPreserveRatio(false);
    wallpaperView.setSmooth(true);
    wallpaperView.setManaged(false);
    wallpaperView.setMouseTransparent(true);

    shell.getStyleClass().add("phone-shell");
    shell.setOnMouseClicked(e -> e.consume());
    shell.setMaxWidth(380);
    shell.setPrefWidth(340);
    shell.setMinWidth(300);
    shell.setMaxHeight(760);
    shell.setPrefHeight(700);
    shell.setMinHeight(560);

    navButton.getStyleClass().add("phone-nav-button");
    navButton.setFocusTraversable(false);
    navButton.setOnAction(e -> {
      if (sceneModel == null) return;
      sceneModel.back();
      refresh();
    });

    auxButton.getStyleClass().add("phone-nav-button");
    auxButton.setFocusTraversable(false);
    auxButton.setOnAction(e -> {
      if (sceneModel == null) return;
      sceneModel.showHome();
      refresh();
    });

    titleLabel.getStyleClass().add("phone-title");
    subtitleLabel.getStyleClass().add("phone-subtitle");

    VBox titles = new VBox(2, titleLabel, subtitleLabel);
    titles.getStyleClass().add("phone-title-box");

    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    HBox header = new HBox(10, navButton, titles, headerSpacer, auxButton);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("phone-header");

    homeList.getStyleClass().add("phone-chat-list");
    homeScroll.setContent(homeList);
    homeScroll.setFitToWidth(true);
    homeScroll.getStyleClass().add("phone-scroll");
    homeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    messageList.getStyleClass().add("phone-message-list");
    messageScroll.setContent(messageList);
    messageScroll.setFitToWidth(true);
    messageScroll.getStyleClass().add("phone-scroll");
    messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    footerLabel.getStyleClass().add("phone-footer");
    footerLabel.setWrapText(true);

    phoneRoot.setTop(header);
    phoneRoot.setCenter(homeScroll);
    phoneRoot.setBottom(footerLabel);
    phoneRoot.getStyleClass().add("phone-root");
    BorderPane.setMargin(footerLabel, new Insets(12, 18, 18, 18));

    shell.getChildren().addAll(wallpaperView, phoneRoot);

    getChildren().addAll(backdrop, shell);
    StackPane.setAlignment(shell, Pos.CENTER);

    String css = getClass().getResource("/com/jvn/fx/phone/phone.css").toExternalForm();
    getStylesheets().add(css);
  }

  public void setProjectRoot(File projectRoot) {
    if ((this.projectRoot == null && projectRoot == null)
        || (this.projectRoot != null && this.projectRoot.equals(projectRoot))) {
      return;
    }
    this.projectRoot = projectRoot;
    imageCache.clear();
    refresh();
  }

  public PhoneScene getSceneModel() {
    return sceneModel;
  }

  public void setSceneModel(PhoneScene sceneModel) {
    if (this.sceneModel == sceneModel) {
      setVisible(sceneModel != null);
      setManaged(sceneModel != null);
      setMouseTransparent(sceneModel == null);
      return;
    }
    this.sceneModel = sceneModel;
    setVisible(sceneModel != null);
    setManaged(sceneModel != null);
    setMouseTransparent(sceneModel == null);
    refresh();
  }

  public boolean handleKeyPressed(KeyCode code, boolean shiftDown) {
    if (sceneModel == null || code == null) return false;
    if (code == KeyCode.ESCAPE || code == KeyCode.BACK_SPACE) {
      sceneModel.back();
      refresh();
      return true;
    }
    if (code == KeyCode.HOME && sceneModel.isShowingChat()) {
      sceneModel.showHome();
      refresh();
      return true;
    }
    if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
      if (sceneModel.isShowingHome()) {
        sceneModel.openSelectedChat();
        refresh();
      }
      return true;
    }
    if (sceneModel.isShowingHome()) {
      if (code == KeyCode.UP) {
        sceneModel.moveSelection(-1);
        refresh();
        return true;
      }
      if (code == KeyCode.DOWN) {
        sceneModel.moveSelection(1);
        refresh();
        return true;
      }
      return false;
    }

    double step = shiftDown ? 0.18 : 0.10;
    if (code == KeyCode.UP) {
      messageScroll.setVvalue(clamp(messageScroll.getVvalue() - step));
      return true;
    }
    if (code == KeyCode.DOWN) {
      messageScroll.setVvalue(clamp(messageScroll.getVvalue() + step));
      return true;
    }
    if (code == KeyCode.PAGE_UP) {
      messageScroll.setVvalue(clamp(messageScroll.getVvalue() - 0.45));
      return true;
    }
    if (code == KeyCode.PAGE_DOWN) {
      messageScroll.setVvalue(clamp(messageScroll.getVvalue() + 0.45));
      return true;
    }
    if (code == KeyCode.END) {
      messageScroll.setVvalue(1.0);
      return true;
    }
    return false;
  }

  public void scrollContent(double deltaY, boolean shiftDown) {
    if (sceneModel == null) return;
    if (sceneModel.isShowingHome()) {
      sceneModel.moveSelection(deltaY > 0 ? -1 : 1);
      refresh();
      return;
    }
    double amount = shiftDown ? 0.16 : 0.08;
    double direction = deltaY > 0 ? -amount : amount;
    messageScroll.setVvalue(clamp(messageScroll.getVvalue() + direction));
  }

  public void refresh() {
    if (sceneModel == null) {
      wallpaperView.setImage(null);
      homeList.getChildren().clear();
      messageList.getChildren().clear();
      return;
    }

    VnPhoneData data = sceneModel.getData();
    applyTheme(data);
    updateWallpaper(data);

    if (sceneModel.isShowingHome()) {
      titleLabel.setText(data.getTitle());
      subtitleLabel.setText(data.getSubtitle());
      navButton.setText("Close");
      auxButton.setVisible(false);
      auxButton.setManaged(false);
      phoneRoot.setCenter(homeScroll);
      footerLabel.setText("Enter opens the selected chat. Esc closes.");
      refreshHomeList();
    } else {
      VnPhoneData.Chat chat = sceneModel.getCurrentChat();
      titleLabel.setText(chat == null ? "Conversation" : firstNonBlank(chat.getTitle(), data.defaultChatTitle(chat)));
      subtitleLabel.setText(chat == null ? "" : chat.getParticipants().size() + " participant(s)");
      navButton.setText(sceneModel.canReturnHome() ? "Back" : "Close");
      auxButton.setVisible(sceneModel.canReturnHome());
      auxButton.setManaged(sceneModel.canReturnHome());
      phoneRoot.setCenter(messageScroll);
      footerLabel.setText("Arrow keys or wheel scroll. Home jumps back to the thread list.");
      refreshChatView(chat);
      sceneModel.markCurrentChatRead();
    }
  }

  private void refreshHomeList() {
    homeList.getChildren().clear();
    List<VnPhoneData.Chat> chats = sceneModel.getOrderedChats();
    if (chats.isEmpty()) {
      homeList.getChildren().add(emptyState("No chats yet", "Seed chats in config/phone/phone.properties or add them with [phone thread] and [phone message]."));
      return;
    }

    int selected = sceneModel.getSelectedHomeIndex();
    for (int i = 0; i < chats.size(); i++) {
      VnPhoneData.Chat chat = chats.get(i);
      final int rowIndex = i;
      Button row = new Button();
      row.setMaxWidth(Double.MAX_VALUE);
      row.getStyleClass().add("phone-chat-row");
      if (i == selected) row.getStyleClass().add("is-selected");
      if (chat != null && chat.isUnread()) row.getStyleClass().add("is-unread");
      row.setGraphic(buildChatRow(chat));
      row.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
      row.setOnAction(e -> {
        sceneModel.setSelectedHomeIndex(rowIndex);
        sceneModel.openSelectedChat();
        refresh();
      });
      homeList.getChildren().add(row);
    }
    scrollHomeSelectionIntoView();
  }

  private Node buildChatRow(VnPhoneData.Chat chat) {
    StackPane avatar = avatarNode(chat == null ? null : chat.getIconPath(), chat == null ? "?" : initials(chat.getTitle()), "phone-chat-avatar");
    Label title = new Label(chat == null ? "Conversation" : firstNonBlank(chat.getTitle(), sceneModel.getData().defaultChatTitle(chat)));
    title.getStyleClass().add("phone-chat-title");
    Label preview = new Label(chat == null ? "" : chat.getLastPreview());
    preview.getStyleClass().add("phone-chat-preview");
    preview.setWrapText(true);

    VBox textBox = new VBox(4, title, preview);
    textBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(textBox, Priority.ALWAYS);

    Label unread = new Label(chat != null && chat.isUnread() ? "New" : "");
    unread.getStyleClass().add("phone-chat-badge");

    HBox row = new HBox(12, avatar, textBox, unread);
    row.setAlignment(Pos.CENTER_LEFT);
    row.setFillHeight(true);
    return row;
  }

  private void refreshChatView(VnPhoneData.Chat chat) {
    messageList.getChildren().clear();
    if (chat == null || chat.getMessages().isEmpty()) {
      messageList.getChildren().add(emptyState("No messages", "Use [phone message <chat> <sender> \"text\"] to append conversation history."));
      return;
    }

    for (VnPhoneData.Message message : chat.getMessages()) {
      VnPhoneData.Contact sender = sceneModel.getData().getOrCreateContact(message.getSenderId());
      boolean outgoing = sender.isSelf();

      Label bubble = new Label(message.getText());
      bubble.getStyleClass().addAll("phone-bubble", outgoing ? "outgoing" : "incoming");
      bubble.setWrapText(true);
      bubble.setMaxWidth(220);
      if (outgoing) {
        bubble.setStyle("-fx-background-color: " + firstNonBlank(sceneModel.getData().getOutgoingBubbleColor(), "#2563eb") + ";");
      } else {
        bubble.setStyle("-fx-background-color: " + firstNonBlank(sceneModel.getData().getIncomingBubbleColor(), "#1c2738") + ";");
      }

      Label meta = new Label(formatMeta(sender, message));
      meta.getStyleClass().add("phone-message-meta");

      VBox stack = new VBox(4, bubble, meta);
      stack.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
      stack.getStyleClass().add("phone-message-stack");

      HBox row = new HBox(stack);
      row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
      row.getStyleClass().add("phone-message-row");
      messageList.getChildren().add(row);
    }
    Platform.runLater(() -> messageScroll.setVvalue(1.0));
  }

  private String formatMeta(VnPhoneData.Contact sender, VnPhoneData.Message message) {
    String name = sender == null ? message.getSenderId() : firstNonBlank(sender.getDisplayName(), sender.getId());
    String time = firstNonBlank(message.getTimeText(), "");
    return time.isBlank() ? name : name + "  " + time;
  }

  private Node emptyState(String title, String copy) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("phone-empty-title");
    Label copyLabel = new Label(copy);
    copyLabel.getStyleClass().add("phone-empty-copy");
    copyLabel.setWrapText(true);
    VBox box = new VBox(8, titleLabel, copyLabel);
    box.getStyleClass().add("phone-empty-state");
    return box;
  }

  private StackPane avatarNode(String imagePath, String fallback, String styleClass) {
    StackPane avatar = new StackPane();
    avatar.getStyleClass().add(styleClass);
    avatar.setMinSize(44, 44);
    avatar.setPrefSize(44, 44);
    avatar.setMaxSize(44, 44);

    Image image = loadImage(imagePath);
    if (image != null) {
      ImageView view = new ImageView(image);
      view.setFitWidth(44);
      view.setFitHeight(44);
      view.setPreserveRatio(false);
      avatar.getChildren().add(view);
    } else {
      Label initial = new Label(firstNonBlank(fallback, "?"));
      initial.getStyleClass().add("phone-avatar-fallback");
      avatar.getChildren().add(initial);
    }
    return avatar;
  }

  private void updateWallpaper(VnPhoneData data) {
    Image wallpaper = loadImage(data == null ? null : data.getWallpaperPath());
    wallpaperView.setImage(wallpaper);
    wallpaperView.setVisible(wallpaper != null);
    wallpaperView.setManaged(wallpaper != null);
  }

  private void applyTheme(VnPhoneData data) {
    if (data == null) {
      shell.setStyle(null);
      return;
    }
    shell.setStyle("-fx-background-color: " + firstNonBlank(data.getSurfaceColor(), "#101826") + ";");
  }

  private void scrollHomeSelectionIntoView() {
    List<VnPhoneData.Chat> chats = sceneModel.getOrderedChats();
    if (chats.isEmpty()) {
      homeScroll.setVvalue(0.0);
      return;
    }
    int index = Math.max(0, Math.min(sceneModel.getSelectedHomeIndex(), chats.size() - 1));
    double denominator = Math.max(1, chats.size() - 1);
    homeScroll.setVvalue(index / denominator);
  }

  private Image loadImage(String path) {
    if (path == null || path.isBlank()) return null;
    return imageCache.computeIfAbsent(path, p -> {
      try {
        var assetUrl = new AssetCatalog().url(AssetType.IMAGE, p);
        if (assetUrl != null) return new Image(assetUrl.toExternalForm());
        var uiUrl = new AssetCatalog().url(AssetType.UI, p);
        if (uiUrl != null) return new Image(uiUrl.toExternalForm());
        var cpUrl = getClass().getClassLoader().getResource(p);
        if (cpUrl != null) return new Image(cpUrl.toExternalForm());
        File direct = new File(p);
        if (direct.exists()) return new Image(direct.toURI().toString());
        if (projectRoot != null) {
          String normalized = p.replace('\\', '/');
          if (normalized.startsWith(projectRoot.getName() + "/")) {
            normalized = normalized.substring(projectRoot.getName().length() + 1);
          }
          File fromRoot = new File(projectRoot, normalized);
          if (fromRoot.exists()) return new Image(fromRoot.toURI().toString());
        }
      } catch (Exception ignored) {
      }
      return null;
    });
  }

  private static String initials(String value) {
    if (value == null || value.isBlank()) return "?";
    String[] parts = value.trim().split("\\s+");
    if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
    return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private static double clamp(double value) {
    if (value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }
}

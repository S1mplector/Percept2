package com.jvn.fx.phone;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * JavaFX overlay renderer for {@link PhoneScene}.
 *
 * <p>The underlying VN continues to render on canvas; this control is only the
 * styled JavaFX layer shown above it.</p>
 */
public final class PhoneRenderer extends StackPane {
  private static final double SHELL_WIDTH = 340.0;
  private static final double SHELL_HEIGHT = 700.0;

  private final Region backdrop = new Region();
  private final StackPane shell = new StackPane();
  private final Pane skinUnderlay = new Pane();
  private final Pane skinOverlay = new Pane();
  private final ImageView skinBackgroundView = new ImageView();
  private final ImageView skinTopBarView = new ImageView();
  private final ImageView skinBottomBarView = new ImageView();
  private final ImageView skinMessageFieldView = new ImageView();
  private final ImageView skinNavLeadingView = new ImageView();
  private final ImageView skinNavTrailingPrimaryView = new ImageView();
  private final ImageView skinNavTrailingSecondaryView = new ImageView();
  private final ImageView skinComposerLeadingView = new ImageView();
  private final ImageView skinComposerTrailingPrimaryView = new ImageView();
  private final ImageView skinComposerTrailingSecondaryView = new ImageView();
  private final ImageView skinStatusBackdropView = new ImageView();
  private final ImageView skinStatusIconView = new ImageView();
  private final ImageView skinFloatingActionView = new ImageView();
  private final ImageView wallpaperView = new ImageView();
  private final BorderPane phoneRoot = new BorderPane();
  private final Rectangle clipRect = new Rectangle();

  private final Button navButton = new Button("Close");
  private final Button auxSecondaryButton = new Button();
  private final Button auxButton = new Button("Home");
  private final Label statusTimeLabel = new Label();
  private final Label statusModeLabel = new Label();
  private final Label statusSignalLabel = new Label();
  private final Label statusBatteryLabel = new Label();
  private final HBox statusBar = new HBox(8);
  private final Label titleLabel = new Label("Phone");
  private final Label subtitleLabel = new Label("Messages");
  private final HBox header = new HBox(10);
  private final VBox topChrome = new VBox(4);
  private final ScrollPane homeScroll = new ScrollPane();
  private final VBox homeList = new VBox(8);
  private final TilePane appGrid = new TilePane();
  private final ScrollPane messageScroll = new ScrollPane();
  private final VBox messageList = new VBox(10);
  private final VBox callView = new VBox(14);
  private final Label composerPreviewLabel = new Label();
  private final Label footerLabel = new Label("Esc closes");
  private final VBox bottomChrome = new VBox(4);

  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<String, AlphaBounds> alphaBoundsCache = new HashMap<>();

  private PhoneScene sceneModel;
  private File projectRoot;
  private boolean embeddedPreview;

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

    skinUnderlay.getStyleClass().add("phone-skin-underlay");
    skinUnderlay.setMouseTransparent(true);
    skinUnderlay.setManaged(false);
    skinUnderlay.prefWidthProperty().bind(shell.widthProperty());
    skinUnderlay.prefHeightProperty().bind(shell.heightProperty());

    skinOverlay.getStyleClass().add("phone-skin-overlay");
    skinOverlay.setMouseTransparent(true);
    skinOverlay.setManaged(false);
    skinOverlay.prefWidthProperty().bind(shell.widthProperty());
    skinOverlay.prefHeightProperty().bind(shell.heightProperty());

    configureLayerImageView(wallpaperView, "phone-wallpaper");
    configureLayerImageView(skinBackgroundView, "phone-skin-background");
    configureLayerImageView(skinTopBarView, "phone-skin-top-bar");
    configureLayerImageView(skinBottomBarView, "phone-skin-bottom-bar");
    configureLayerImageView(skinMessageFieldView, "phone-skin-message-field");
    configureLayerImageView(skinNavLeadingView, "phone-skin-nav-leading");
    configureLayerImageView(skinNavTrailingPrimaryView, "phone-skin-nav-trailing-primary");
    configureLayerImageView(skinNavTrailingSecondaryView, "phone-skin-nav-trailing-secondary");
    configureLayerImageView(skinComposerLeadingView, "phone-skin-composer-leading");
    configureLayerImageView(skinComposerTrailingPrimaryView, "phone-skin-composer-trailing-primary");
    configureLayerImageView(skinComposerTrailingSecondaryView, "phone-skin-composer-trailing-secondary");
    configureLayerImageView(skinStatusBackdropView, "phone-skin-status-backdrop");
    configureLayerImageView(skinStatusIconView, "phone-skin-status-icon");
    configureLayerImageView(skinFloatingActionView, "phone-skin-floating-action");

    shell.getStyleClass().add("phone-shell");
    shell.setOnMouseClicked(e -> e.consume());
    shell.setMaxWidth(380);
    shell.setPrefWidth(SHELL_WIDTH);
    shell.setMinWidth(300);
    shell.setMaxHeight(760);
    shell.setPrefHeight(SHELL_HEIGHT);
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

    auxSecondaryButton.getStyleClass().add("phone-nav-button");
    auxSecondaryButton.setFocusTraversable(false);
    auxSecondaryButton.setOnAction(e -> {
      if (sceneModel == null) return;
      sceneModel.showHome();
      refresh();
    });

    statusTimeLabel.getStyleClass().add("phone-status-label");
    statusModeLabel.getStyleClass().add("phone-status-label");
    statusSignalLabel.getStyleClass().add("phone-status-label");
    statusBatteryLabel.getStyleClass().add("phone-status-label");
    Region statusSpacer = new Region();
    HBox.setHgrow(statusSpacer, Priority.ALWAYS);
    statusBar.getChildren().setAll(statusTimeLabel, statusSpacer, statusModeLabel, statusSignalLabel, statusBatteryLabel);
    statusBar.setAlignment(Pos.CENTER_LEFT);
    statusBar.getStyleClass().add("phone-status-bar");

    titleLabel.getStyleClass().add("phone-title");
    subtitleLabel.getStyleClass().add("phone-subtitle");

    VBox titles = new VBox(2, titleLabel, subtitleLabel);
    titles.getStyleClass().add("phone-title-box");

    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    header.getChildren().setAll(navButton, titles, headerSpacer, auxSecondaryButton, auxButton);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("phone-header");

    homeList.getStyleClass().add("phone-chat-list");
    homeScroll.setContent(homeList);
    homeScroll.setFitToWidth(true);
    homeScroll.getStyleClass().add("phone-scroll");
    homeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    appGrid.getStyleClass().add("phone-app-grid");
    appGrid.setPrefColumns(3);
    appGrid.setHgap(10);
    appGrid.setVgap(12);
    appGrid.setPrefTileWidth(86);
    appGrid.setAlignment(Pos.TOP_CENTER);

    messageList.getStyleClass().add("phone-message-list");
    messageScroll.setContent(messageList);
    messageScroll.setFitToWidth(true);
    messageScroll.getStyleClass().add("phone-scroll");
    messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

    callView.getStyleClass().add("phone-call-view");
    callView.setAlignment(Pos.CENTER);
    callView.setFillWidth(true);
    callView.setPadding(new Insets(18, 18, 12, 18));

    composerPreviewLabel.getStyleClass().add("phone-composer-preview");
    composerPreviewLabel.setWrapText(true);
    composerPreviewLabel.setVisible(false);
    composerPreviewLabel.setManaged(false);

    footerLabel.getStyleClass().add("phone-footer");
    footerLabel.setWrapText(true);

    topChrome.getChildren().setAll(statusBar, header);
    bottomChrome.getChildren().setAll(composerPreviewLabel, footerLabel);
    phoneRoot.setTop(topChrome);
    phoneRoot.setCenter(homeScroll);
    phoneRoot.setBottom(bottomChrome);
    phoneRoot.getStyleClass().add("phone-root");
    BorderPane.setMargin(bottomChrome, new Insets(12, 18, 18, 18));

    skinUnderlay.getChildren().addAll(
        skinBackgroundView,
        wallpaperView,
        skinTopBarView,
        skinBottomBarView,
        skinMessageFieldView);
    skinOverlay.getChildren().addAll(
        skinStatusBackdropView,
        skinStatusIconView,
        skinFloatingActionView,
        skinNavLeadingView,
        skinNavTrailingPrimaryView,
        skinNavTrailingSecondaryView,
        skinComposerLeadingView,
        skinComposerTrailingPrimaryView,
        skinComposerTrailingSecondaryView);
    shell.getChildren().addAll(skinUnderlay, phoneRoot, skinOverlay);

    getChildren().addAll(backdrop, shell);
    StackPane.setAlignment(shell, Pos.CENTER);

    clipRect.widthProperty().bind(widthProperty());
    clipRect.heightProperty().bind(heightProperty());
    setClip(clipRect);
    widthProperty().addListener((obs, oldValue, newValue) -> updateEmbeddedLayout());
    heightProperty().addListener((obs, oldValue, newValue) -> updateEmbeddedLayout());

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
    alphaBoundsCache.clear();
    refresh();
  }

  public void clearAssetCache() {
    imageCache.clear();
    alphaBoundsCache.clear();
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
    updateEmbeddedLayout();
    refresh();
  }

  public void setEmbeddedPreview(boolean embeddedPreview) {
    if (this.embeddedPreview == embeddedPreview) return;
    this.embeddedPreview = embeddedPreview;
    if (embeddedPreview) {
      if (!getStyleClass().contains("phone-overlay-embedded")) {
        getStyleClass().add("phone-overlay-embedded");
      }
    } else {
      getStyleClass().remove("phone-overlay-embedded");
    }
    updateEmbeddedLayout();
  }

  public boolean isEmbeddedPreview() {
    return embeddedPreview;
  }

  public boolean handleKeyPressed(KeyCode code, boolean shiftDown) {
    if (sceneModel == null || code == null) return false;
    if (code == KeyCode.ESCAPE || code == KeyCode.BACK_SPACE) {
      sceneModel.back();
      refresh();
      return true;
    }
    if (code == KeyCode.HOME && !sceneModel.isShowingHome()) {
      sceneModel.showHome();
      refresh();
      return true;
    }
    if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
      if (sceneModel.isShowingHome()) {
        sceneModel.openSelectedHomeEntry();
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

    if (sceneModel.isShowingCall()) return false;

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
      appGrid.getChildren().clear();
      messageList.getChildren().clear();
      callView.getChildren().clear();
      composerPreviewLabel.setText("");
      composerPreviewLabel.setVisible(false);
      composerPreviewLabel.setManaged(false);
      applySkinAssets(null);
      applyLayoutForSkin(null, false, true);
      updateEmbeddedLayout();
      return;
    }

    VnPhoneData data = sceneModel.getData();
    boolean skinned = applyTheme(data);
    updateWallpaper(data);
    applySkinAssets(data);
    updateStatusBar(data);

    if (sceneModel.isShowingHome()) {
      titleLabel.setText(data.getTitle());
      subtitleLabel.setText(data.getSubtitle());
      navButton.setText("Close");
      auxButton.setVisible(false);
      auxButton.setManaged(false);
      auxSecondaryButton.setVisible(false);
      auxSecondaryButton.setManaged(false);
      phoneRoot.setCenter(homeScroll);
      footerLabel.setText(data.getHomeMode() == VnPhoneData.HomeMode.APPS
          ? "Enter opens the selected app. Esc closes."
          : "Enter opens the selected chat. Esc closes.");
      updateComposerPreview(null);
      if (data.getHomeMode() == VnPhoneData.HomeMode.APPS) {
        refreshAppHome();
      } else {
        refreshHomeList();
      }
    } else if (sceneModel.isShowingCall()) {
      VnPhoneData.Call call = sceneModel.getCurrentCall();
      titleLabel.setText(call == null ? "Call" : firstNonBlank(call.getTitle(), call.getId()));
      subtitleLabel.setText(call == null ? "" : firstNonBlank(call.getSubtitle(), call.isVideo() ? "Video call" : "Voice call"));
      navButton.setText(sceneModel.canReturnHome() ? "Back" : "Close");
      auxButton.setVisible(sceneModel.canReturnHome());
      auxButton.setManaged(sceneModel.canReturnHome());
      auxSecondaryButton.setVisible(false);
      auxSecondaryButton.setManaged(false);
      phoneRoot.setCenter(callView);
      footerLabel.setText("Esc closes. Home jumps back to the launcher.");
      updateComposerPreview(null);
      refreshCallView(call);
    } else {
      VnPhoneData.Chat chat = sceneModel.getCurrentChat();
      boolean hasSecondaryNav = hasText(data.getSkinNavTrailingSecondaryPath());
      titleLabel.setText(chat == null ? "Conversation" : firstNonBlank(chat.getTitle(), data.defaultChatTitle(chat)));
      subtitleLabel.setText(chat == null ? "" : chat.getParticipants().size() + " participant(s)");
      navButton.setText(sceneModel.canReturnHome() ? "Back" : "Close");
      auxButton.setVisible(sceneModel.canReturnHome());
      auxButton.setManaged(sceneModel.canReturnHome());
      auxSecondaryButton.setVisible(sceneModel.canReturnHome() && hasSecondaryNav);
      auxSecondaryButton.setManaged(sceneModel.canReturnHome() && hasSecondaryNav);
      phoneRoot.setCenter(messageScroll);
      footerLabel.setText("Arrow keys or wheel scroll. Home jumps back to the thread list.");
      refreshChatView(chat);
      updateComposerPreview(chat);
      sceneModel.markCurrentChatRead();
    }
    applySceneChromeVisibility(data, sceneModel.isShowingHome());
    applyLayoutForSkin(data, skinned, sceneModel.isShowingHome());
    updateEmbeddedLayout();
  }

  private void refreshHomeList() {
    homeScroll.setContent(homeList);
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
      row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      row.setOnAction(e -> {
        sceneModel.setSelectedHomeIndex(rowIndex);
        sceneModel.openSelectedChat();
        refresh();
      });
      homeList.getChildren().add(row);
    }
    scrollHomeSelectionIntoView();
  }

  private void refreshAppHome() {
    homeScroll.setContent(appGrid);
    appGrid.getChildren().clear();
    List<VnPhoneData.PhoneApp> apps = sceneModel.getOrderedApps();
    if (apps.isEmpty()) {
      homeScroll.setContent(emptyState("No apps yet", "Create launchable apps in phone.properties or use the phone assets tool."));
      return;
    }

    int selected = sceneModel.getSelectedHomeIndex();
    for (int i = 0; i < apps.size(); i++) {
      VnPhoneData.PhoneApp app = apps.get(i);
      final int rowIndex = i;
      Button tile = new Button();
      tile.getStyleClass().add("phone-app-tile");
      if (i == selected) tile.getStyleClass().add("is-selected");
      tile.setMaxWidth(Double.MAX_VALUE);
      tile.setGraphic(buildAppTile(app));
      tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      tile.setOnAction(e -> {
        sceneModel.setSelectedHomeIndex(rowIndex);
        sceneModel.openSelectedHomeEntry();
        refresh();
      });
      appGrid.getChildren().add(tile);
    }
    scrollHomeSelectionIntoView();
  }

  private Node buildAppTile(VnPhoneData.PhoneApp app) {
    StackPane icon = avatarNode(app == null ? null : app.getIconPath(), app == null ? "?" : initials(app.getTitle()), "phone-app-icon");
    Label title = new Label(app == null ? "App" : firstNonBlank(app.getTitle(), app.getId()));
    title.getStyleClass().add("phone-app-title");
    title.setWrapText(true);
    title.setMaxWidth(82);
    title.setAlignment(Pos.CENTER);

    Label badge = new Label(app == null ? "" : firstNonBlank(app.getBadgeText(), ""));
    badge.getStyleClass().add("phone-app-badge");
    badge.setVisible(app != null && hasText(app.getBadgeText()));
    badge.setManaged(app != null && hasText(app.getBadgeText()));

    StackPane iconStack = new StackPane(icon, badge);
    StackPane.setAlignment(badge, Pos.TOP_RIGHT);

    VBox box = new VBox(6, iconStack, title);
    box.setAlignment(Pos.TOP_CENTER);
    return box;
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

  private void refreshCallView(VnPhoneData.Call call) {
    callView.getChildren().clear();
    if (call == null) {
      callView.getChildren().add(emptyState("No call selected", "Create a call entry, then preview it from the phone assets tool or open it from a phone app."));
      return;
    }

    String participantName = participantName(call);
    StackPane avatar = avatarNode(call.getAvatarPath(), initials(firstNonBlank(participantName, call.getTitle(), call.getId())), "phone-call-avatar");
    avatar.setMinSize(88, 88);
    avatar.setPrefSize(88, 88);
    avatar.setMaxSize(88, 88);

    Label title = new Label(firstNonBlank(call.getTitle(), participantName, "Call"));
    title.getStyleClass().add("phone-call-title");
    title.setWrapText(true);
    title.setAlignment(Pos.CENTER);
    title.setMaxWidth(Double.MAX_VALUE);

    Label subtitle = new Label(firstNonBlank(call.getSubtitle(), participantName, ""));
    subtitle.getStyleClass().add("phone-call-subtitle");
    subtitle.setWrapText(true);
    subtitle.setAlignment(Pos.CENTER);
    subtitle.setMaxWidth(Double.MAX_VALUE);

    Label status = new Label(firstNonBlank(call.getStatusText(), call.isVideo() ? "Connecting video..." : "Calling..."));
    status.getStyleClass().add("phone-call-status");

    Label mode = new Label(call.isVideo() ? "Video" : "Voice");
    mode.getStyleClass().add("phone-call-mode");

    HBox chips = new HBox(8, mode, new Label(firstNonBlank(participantName, "")));
    chips.setAlignment(Pos.CENTER);
    for (Node child : chips.getChildren()) {
      child.getStyleClass().add("phone-call-chip");
    }

    callView.getChildren().addAll(avatar, title, subtitle, status, chips);
  }

  private void refreshChatView(VnPhoneData.Chat chat) {
    messageScroll.setContent(messageList);
    messageList.getChildren().clear();
    if (chat == null || chat.getMessages().isEmpty()) {
      messageList.getChildren().add(emptyState("No messages", "Use [phone message <chat> <sender> \"text\"] to append conversation history."));
      return;
    }

    for (VnPhoneData.Message message : chat.getMessages()) {
      if (message.getType().isSystemType()) {
        messageList.getChildren().add(buildSystemMessageRow(message));
        continue;
      }

      VnPhoneData.Contact sender = message.getSenderId() == null ? null : sceneModel.getData().getContact(message.getSenderId());
      boolean outgoing = sender != null && sender.isSelf();
      Node bubble = buildMessageBubble(message, outgoing);
      if (bubble == null) continue;

      VBox stack = new VBox(4);
      stack.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
      stack.getStyleClass().add("phone-message-stack");
      stack.getChildren().add(bubble);

      String metaText = formatMeta(sender, message);
      if (!metaText.isBlank()) {
        Label meta = new Label(metaText);
        meta.getStyleClass().add("phone-message-meta");
        stack.getChildren().add(meta);
      }

      HBox row = new HBox(stack);
      row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
      row.getStyleClass().add("phone-message-row");
      messageList.getChildren().add(row);
    }
    Platform.runLater(() -> messageScroll.setVvalue(1.0));
  }

  private String formatMeta(VnPhoneData.Contact sender, VnPhoneData.Message message) {
    String name = sender == null ? firstNonBlank(message.getSenderId(), "") : firstNonBlank(sender.getDisplayName(), sender.getId());
    String time = firstNonBlank(message.getTimeText(), "");
    if (name.isBlank()) return time;
    return time.isBlank() ? name : name + "  " + time;
  }

  private Node buildMessageBubble(VnPhoneData.Message message, boolean outgoing) {
    if (message == null) return null;
    return switch (message.getType()) {
      case IMAGE -> buildImageBubble(message, outgoing);
      case AUDIO -> buildAudioBubble(message, outgoing);
      case MENU -> buildMenuBubble(message, outgoing);
      case DATE, LABEL -> null;
      case TEXT -> buildTextBubble(message, outgoing);
    };
  }

  private Label buildTextBubble(VnPhoneData.Message message, boolean outgoing) {
    Label bubble = new Label(firstNonBlank(message.getText(), message.getCaption(), ""));
    bubble.getStyleClass().addAll("phone-bubble", outgoing ? "outgoing" : "incoming");
    bubble.setWrapText(true);
    bubble.setMaxWidth(220);
    applyBubbleStyle(bubble, outgoing);
    return bubble;
  }

  private VBox buildImageBubble(VnPhoneData.Message message, boolean outgoing) {
    VBox bubble = bubbleCard(outgoing);
    Image image = loadImage(message.getAssetPath());
    if (image != null) {
      ImageView view = new ImageView(image);
      view.setFitWidth(186);
      view.setPreserveRatio(true);
      view.setSmooth(true);
      bubble.getChildren().add(view);
    } else {
      Label missing = new Label(firstNonBlank(message.getAssetPath(), "Missing image"));
      missing.getStyleClass().add("phone-media-copy");
      missing.setWrapText(true);
      bubble.getChildren().add(missing);
    }
    String caption = firstNonBlank(message.getCaption(), message.getText(), "");
    if (!caption.isBlank()) {
      Label label = new Label(caption);
      label.getStyleClass().add("phone-media-caption");
      label.setWrapText(true);
      bubble.getChildren().add(label);
    }
    return bubble;
  }

  private VBox buildAudioBubble(VnPhoneData.Message message, boolean outgoing) {
    VBox bubble = bubbleCard(outgoing);
    Label title = new Label(firstNonBlank(message.getCaption(), message.getText(), displayAssetName(message.getAssetPath()), "Voice message"));
    title.getStyleClass().add("phone-media-title");
    title.setWrapText(true);

    Label detail = new Label(firstNonBlank(message.getDurationText(), message.getAssetPath(), ""));
    detail.getStyleClass().add("phone-media-copy");
    detail.setWrapText(true);

    bubble.getChildren().addAll(title, detail);
    return bubble;
  }

  private VBox buildMenuBubble(VnPhoneData.Message message, boolean outgoing) {
    VBox bubble = bubbleCard(outgoing);
    String prompt = firstNonBlank(message.getText(), message.getCaption(), "");
    if (!prompt.isBlank()) {
      Label label = new Label(prompt);
      label.getStyleClass().add("phone-media-title");
      label.setWrapText(true);
      bubble.getChildren().add(label);
    }
    for (String option : message.getOptions()) {
      Label item = new Label(option);
      item.getStyleClass().add("phone-menu-option");
      item.setWrapText(true);
      bubble.getChildren().add(item);
    }
    return bubble;
  }

  private VBox bubbleCard(boolean outgoing) {
    VBox bubble = new VBox(8);
    bubble.getStyleClass().addAll("phone-bubble-card", outgoing ? "outgoing" : "incoming");
    bubble.setMaxWidth(220);
    applyBubbleStyle(bubble, outgoing);
    return bubble;
  }

  private Node buildSystemMessageRow(VnPhoneData.Message message) {
    Label label = new Label(firstNonBlank(message.getText(), message.getCaption(), message.getPreviewText()));
    label.getStyleClass().add("phone-system-message");
    if (message.getType() == VnPhoneData.MessageType.DATE) {
      label.getStyleClass().add("is-date");
    } else {
      label.getStyleClass().add("is-label");
    }
    HBox row = new HBox(label);
    row.setAlignment(Pos.CENTER);
    row.getStyleClass().add("phone-system-row");
    return row;
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

  private void updateStatusBar(VnPhoneData data) {
    statusTimeLabel.setText(data == null ? "" : data.getStatusTimeText());
    statusModeLabel.setText(data == null ? "" : firstNonBlank(data.getStatusModeText(), ""));
    statusSignalLabel.setText(data == null ? "" : data.getStatusSignalText());
    statusBatteryLabel.setText(data == null ? "" : data.getStatusBatteryText());

    statusModeLabel.setVisible(hasText(statusModeLabel.getText()));
    statusModeLabel.setManaged(hasText(statusModeLabel.getText()));
  }

  private void updateComposerPreview(VnPhoneData.Chat chat) {
    String value = chat == null
        ? ""
        : firstNonBlank(chat.getComposerText(), chat.getComposerHint(), "");
    boolean visible = hasText(value);
    composerPreviewLabel.setText(visible ? value : "");
    composerPreviewLabel.setVisible(visible);
    composerPreviewLabel.setManaged(visible);
  }

  private void updateWallpaper(VnPhoneData data) {
    Image wallpaper = loadImage(data == null ? null : data.getWallpaperPath());
    wallpaperView.setImage(wallpaper);
    wallpaperView.setVisible(wallpaper != null);
  }

  private boolean applyTheme(VnPhoneData data) {
    shell.getStyleClass().removeAll("is-skinned", "skin-sms", "skin-discord", "skin-custom");
    phoneRoot.getStyleClass().remove("is-skinned");
    header.getStyleClass().remove("is-skinned");
    navButton.getStyleClass().remove("is-skinned");
    auxButton.getStyleClass().remove("is-skinned");
    auxSecondaryButton.getStyleClass().remove("is-skinned");

    if (data == null) {
      shell.setStyle(null);
      return false;
    }
    boolean skinned = hasSkinAssets(data);
    if (skinned) {
      shell.getStyleClass().add("is-skinned");
      phoneRoot.getStyleClass().add("is-skinned");
      header.getStyleClass().add("is-skinned");
      navButton.getStyleClass().add("is-skinned");
      auxButton.getStyleClass().add("is-skinned");
      auxSecondaryButton.getStyleClass().add("is-skinned");
      String skin = firstNonBlank(data.getSkinId(), "default").toLowerCase(Locale.ROOT);
      if ("sms".equals(skin)) {
        shell.getStyleClass().add("skin-sms");
      } else if ("discord".equals(skin)) {
        shell.getStyleClass().add("skin-discord");
      } else {
        shell.getStyleClass().add("skin-custom");
      }
    }
    shell.setStyle("-fx-background-color: " + firstNonBlank(data.getSurfaceColor(), "#101826") + ";");
    return skinned;
  }

  private void applyBubbleStyle(Region bubble, boolean outgoing) {
    if (bubble == null || sceneModel == null) return;
    VnPhoneData data = sceneModel.getData();
    String imagePath = outgoing ? data.getOutgoingBubbleImagePath() : data.getIncomingBubbleImagePath();
    Image image = loadImage(imagePath);
    if (image != null && image.getUrl() != null && !image.getUrl().isBlank()) {
      String imageUrl = cssUrl(image.getUrl());
      bubble.setStyle(
          "-fx-background-color: transparent;"
              + "-fx-background-image: url(\"" + imageUrl + "\");"
              + "-fx-background-repeat: no-repeat;"
              + "-fx-background-size: 100% 100%;");
      return;
    }
    String color = outgoing
        ? firstNonBlank(data.getOutgoingBubbleColor(), "#2563eb")
        : firstNonBlank(data.getIncomingBubbleColor(), "#1c2738");
    bubble.setStyle("-fx-background-color: " + color + "; -fx-background-image: null;");
  }

  private void applySkinAssets(VnPhoneData data) {
    if (data == null) {
      setLayerImage(skinBackgroundView, null);
      setLayerImage(skinTopBarView, null);
      setLayerImage(skinBottomBarView, null);
      setLayerImage(skinMessageFieldView, null);
      setLayerImage(skinNavLeadingView, null);
      setLayerImage(skinNavTrailingPrimaryView, null);
      setLayerImage(skinNavTrailingSecondaryView, null);
      setLayerImage(skinComposerLeadingView, null);
      setLayerImage(skinComposerTrailingPrimaryView, null);
      setLayerImage(skinComposerTrailingSecondaryView, null);
      setLayerImage(skinStatusBackdropView, null);
      setLayerImage(skinStatusIconView, null);
      setLayerImage(skinFloatingActionView, null);
      return;
    }
    setLayerImage(skinBackgroundView, data.getSkinBackgroundPath());
    setLayerImage(skinTopBarView, data.getSkinTopBarPath());
    setLayerImage(skinBottomBarView, data.getSkinBottomBarPath());
    setLayerImage(skinMessageFieldView, data.getSkinMessageFieldPath());
    setLayerImage(skinNavLeadingView, data.getSkinNavLeadingPath());
    setLayerImage(skinNavTrailingPrimaryView, data.getSkinNavTrailingPrimaryPath());
    setLayerImage(skinNavTrailingSecondaryView, data.getSkinNavTrailingSecondaryPath());
    setLayerImage(skinComposerLeadingView, data.getSkinComposerLeadingPath());
    setLayerImage(skinComposerTrailingPrimaryView, data.getSkinComposerTrailingPrimaryPath());
    setLayerImage(skinComposerTrailingSecondaryView, data.getSkinComposerTrailingSecondaryPath());
    setLayerImage(skinStatusBackdropView, data.getSkinStatusBackdropPath());
    setLayerImage(skinStatusIconView, data.getSkinStatusIconPath());
    setLayerImage(skinFloatingActionView, data.getSkinFloatingActionPath());
  }

  private void applySceneChromeVisibility(VnPhoneData data, boolean showingHome) {
    boolean skinned = hasSkinAssets(data);
    skinUnderlay.setVisible(skinned);
    skinOverlay.setVisible(skinned);
    skinUnderlay.setManaged(skinned);
    skinOverlay.setManaged(skinned);
    if (!skinned) return;

    setVisibleIfImage(skinBackgroundView, true);
    setVisibleIfImage(skinTopBarView, true);
    setVisibleIfImage(skinBottomBarView, true);
    setVisibleIfImage(skinNavLeadingView, true);
    setVisibleIfImage(skinNavTrailingPrimaryView, !showingHome);
    setVisibleIfImage(skinNavTrailingSecondaryView, !showingHome);
    setVisibleIfImage(skinStatusBackdropView, !showingHome);
    setVisibleIfImage(skinStatusIconView, !showingHome);
    setVisibleIfImage(skinFloatingActionView, !showingHome);
    setVisibleIfImage(skinMessageFieldView, !showingHome);
    setVisibleIfImage(skinComposerLeadingView, !showingHome);
    setVisibleIfImage(skinComposerTrailingPrimaryView, !showingHome);
    setVisibleIfImage(skinComposerTrailingSecondaryView, !showingHome);
  }

  private void applyLayoutForSkin(VnPhoneData data, boolean skinned, boolean showingHome) {
    if (!skinned) {
      topChrome.setSpacing(4);
      header.setPadding(new Insets(6, 10, 12, 10));
      phoneRoot.setPadding(new Insets(8, 0, 0, 0));
      BorderPane.setMargin(homeScroll, Insets.EMPTY);
      BorderPane.setMargin(messageScroll, Insets.EMPTY);
      BorderPane.setMargin(callView, new Insets(8, 10, 8, 10));
      footerLabel.setVisible(true);
      footerLabel.setManaged(true);
      return;
    }

    navButton.setText("");
    auxButton.setText("");
    auxSecondaryButton.setText("");

    SkinInsets insets = resolveSkinInsets(data);
    topChrome.setSpacing(2);
    header.setPadding(new Insets(insets.headerTop(), insets.side(), insets.headerBottom(), insets.side()));
    phoneRoot.setPadding(Insets.EMPTY);
    BorderPane.setMargin(homeScroll, new Insets(insets.listTop(), insets.side(), insets.listBottom(), insets.side()));
    BorderPane.setMargin(messageScroll, new Insets(insets.listTop(), insets.side(), insets.listBottom(), insets.side()));
    BorderPane.setMargin(callView, new Insets(insets.listTop(), insets.side(), insets.listBottom(), insets.side()));
    footerLabel.setVisible(false);
    footerLabel.setManaged(false);

    if (showingHome) {
      homeList.setSpacing(10);
    } else {
      messageList.setSpacing(8);
    }
  }

  private SkinInsets resolveSkinInsets(VnPhoneData data) {
    if (data == null) return SkinInsets.DEFAULT;

    double height = SHELL_HEIGHT;
    AlphaBounds topBar = alphaBounds(data.getSkinTopBarPath(), skinTopBarView.getImage());
    AlphaBounds bottomBar = alphaBounds(data.getSkinBottomBarPath(), skinBottomBarView.getImage());
    AlphaBounds messageField = alphaBounds(data.getSkinMessageFieldPath(), skinMessageFieldView.getImage());

    double topCover = topBar == null ? 0.105 : topBar.maxY();
    double bottomStart = 0.90;
    if (bottomBar != null) bottomStart = Math.min(bottomStart, bottomBar.minY());
    if (messageField != null) bottomStart = Math.min(bottomStart, messageField.minY());

    String skin = firstNonBlank(data.getSkinId(), "default").toLowerCase(Locale.ROOT);
    double side = "discord".equals(skin) ? 14.0 : 16.0;
    double headerTop = clamp(topCover * height * 0.08, 4.0, 20.0);
    double headerBottom = clamp(topCover * height * 0.06, 6.0, 24.0);
    double listTop = clamp(topCover * height * 0.18, 16.0, 74.0);
    double listBottom = clamp((1.0 - bottomStart) * height + 10.0, 48.0, 180.0);
    return new SkinInsets(side, headerTop, headerBottom, listTop, listBottom);
  }

  private AlphaBounds alphaBounds(String path, Image image) {
    if (path == null || path.isBlank() || image == null) return null;
    AlphaBounds raw = alphaBoundsCache.computeIfAbsent(path, ignored -> computeAlphaBounds(image));
    if (raw == null) return null;
    double scaleX = image.getWidth() <= 0 ? 1.0 : 1.0 / image.getWidth();
    double scaleY = image.getHeight() <= 0 ? 1.0 : 1.0 / image.getHeight();
    return new AlphaBounds(
        raw.minX() * scaleX,
        raw.minY() * scaleY,
        raw.maxX() * scaleX,
        raw.maxY() * scaleY);
  }

  private AlphaBounds computeAlphaBounds(Image image) {
    if (image == null || image.getPixelReader() == null || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
    PixelReader reader = image.getPixelReader();
    int width = Math.max(1, (int) Math.round(image.getWidth()));
    int height = Math.max(1, (int) Math.round(image.getHeight()));

    int minX = width;
    int minY = height;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int alpha = (reader.getArgb(x, y) >>> 24) & 0xFF;
        if (alpha < 12) continue;
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
      }
    }
    if (maxX < minX || maxY < minY) return null;
    return new AlphaBounds(minX, minY, maxX + 1.0, maxY + 1.0);
  }

  private void setLayerImage(ImageView view, String path) {
    if (view == null) return;
    Image image = loadImage(path);
    view.setImage(image);
    view.setVisible(image != null);
  }

  private static void setVisibleIfImage(ImageView view, boolean visible) {
    if (view == null) return;
    view.setVisible(visible && view.getImage() != null);
  }

  private static boolean hasSkinAssets(VnPhoneData data) {
    if (data == null) return false;
    return hasText(data.getSkinBackgroundPath())
        || hasText(data.getSkinTopBarPath())
        || hasText(data.getSkinBottomBarPath())
        || hasText(data.getSkinMessageFieldPath())
        || hasText(data.getSkinNavLeadingPath())
        || hasText(data.getSkinNavTrailingPrimaryPath())
        || hasText(data.getSkinNavTrailingSecondaryPath())
        || hasText(data.getSkinComposerLeadingPath())
        || hasText(data.getSkinComposerTrailingPrimaryPath())
        || hasText(data.getSkinComposerTrailingSecondaryPath())
        || hasText(data.getSkinStatusBackdropPath())
        || hasText(data.getSkinStatusIconPath())
        || hasText(data.getSkinFloatingActionPath());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String cssUrl(String url) {
    if (url == null) return "";
    return url.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private void configureLayerImageView(ImageView view, String styleClass) {
    view.getStyleClass().add(styleClass);
    view.setPreserveRatio(false);
    view.setSmooth(true);
    view.setManaged(false);
    view.setMouseTransparent(true);
    view.fitWidthProperty().bind(shell.widthProperty());
    view.fitHeightProperty().bind(shell.heightProperty());
  }

  private void scrollHomeSelectionIntoView() {
    int count = sceneModel.getHomeEntryCount();
    if (count <= 0) {
      homeScroll.setVvalue(0.0);
      return;
    }
    int index = Math.max(0, Math.min(sceneModel.getSelectedHomeIndex(), count - 1));
    double denominator = Math.max(1, count - 1);
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

  private record AlphaBounds(double minX, double minY, double maxX, double maxY) {
  }

  private record SkinInsets(double side, double headerTop, double headerBottom, double listTop, double listBottom) {
    private static final SkinInsets DEFAULT = new SkinInsets(14.0, 8.0, 12.0, 18.0, 62.0);
  }

  private static String initials(String value) {
    if (value == null || value.isBlank()) return "?";
    String[] parts = value.trim().split("\\s+");
    if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
    return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
  }

  private String participantName(VnPhoneData.Call call) {
    if (call == null || call.getParticipantId() == null || sceneModel == null) return "";
    VnPhoneData.Contact contact = sceneModel.getData().getContact(call.getParticipantId());
    return contact == null ? call.getParticipantId() : firstNonBlank(contact.getDisplayName(), contact.getId());
  }

  private static String displayAssetName(String path) {
    if (path == null || path.isBlank()) return "";
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
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

  private void updateEmbeddedLayout() {
    boolean active = sceneModel != null;
    backdrop.setVisible(active && !embeddedPreview);
    backdrop.setManaged(active && !embeddedPreview);
    backdrop.setMouseTransparent(embeddedPreview || !active);

    if (!embeddedPreview) {
      shell.setScaleX(1.0);
      shell.setScaleY(1.0);
      shell.setTranslateX(0.0);
      shell.setTranslateY(0.0);
      return;
    }

    double availableWidth = Math.max(1.0, getWidth() - snappedLeftInset() - snappedRightInset() - 16.0);
    double availableHeight = Math.max(1.0, getHeight() - snappedTopInset() - snappedBottomInset() - 16.0);
    double scale = embeddedScaleFor(availableWidth, availableHeight);
    shell.setScaleX(scale);
    shell.setScaleY(scale);
    shell.setTranslateX(0.0);
    shell.setTranslateY(0.0);
  }

  static double embeddedScaleFor(double availableWidth, double availableHeight) {
    double width = Math.max(1.0, availableWidth);
    double height = Math.max(1.0, availableHeight);
    double scale = Math.min(width / SHELL_WIDTH, height / SHELL_HEIGHT);
    return clamp(scale, 0.0, 1.0);
  }

  private static double clamp(double value, double min, double max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }
}

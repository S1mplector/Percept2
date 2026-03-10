package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.fx.phone.PhoneRenderer;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Sidebar utility for editing phone configuration, importing phone assets,
 * and previewing the shared runtime phone renderer.
 */
public class PhoneAssetsToolView extends BorderPane {
  static final String STATE_FILE = ".jvn/phone-assets-tool.properties";
  static final String CONFIG_PATH = "config/phone/phone.properties";
  static final String GAME_CONFIG_PATH = "game/config/phone/phone.properties";
  static final String WALLPAPER_IMPORT_DIR = "assets/ui/phone";
  static final String CONTACT_IMPORT_DIR = "assets/phone/contacts";
  static final String CHAT_IMPORT_DIR = "assets/phone/chats";

  private final Label summaryLabel = new Label("Open a project to edit phone assets and configuration.");
  private final Label statusLabel = new Label("");
  private final Label configPathLabel = new Label(CONFIG_PATH);
  private final Label dirtyLabel = new Label("Saved");

  private final Button saveButton = headerButton("Save", CssIcon.save("#9ed67a"));
  private final Button refreshButton = headerButton("Refresh", CssIcon.redo("#7ec8e3"));
  private final Button importConfigButton = headerButton("Import Config", CssIcon.download("#8ab4f8"));
  private final Button openConfigButton = headerButton("Open File", CssIcon.folder("#f5c46b"));

  private final PhoneRenderer phoneRenderer = new PhoneRenderer();
  private final StackPane previewFrame = new StackPane();
  private final Button previewHomeButton = headerButton("Preview Home", CssIcon.home("#9cc7ff"));
  private final Button previewChatButton = headerButton("Preview Chat", CssIcon.speech("#f5c46b"));

  private final TabPane sections = new TabPane();

  private final TextField appTitleField = new TextField();
  private final TextField appSubtitleField = new TextField();
  private final TextField wallpaperField = new TextField();
  private final TextField accentField = new TextField();
  private final TextField surfaceField = new TextField();
  private final TextField incomingBubbleField = new TextField();
  private final TextField outgoingBubbleField = new TextField();

  private final ListView<String> contactList = new ListView<>();
  private final TextField contactIdField = readonlyField();
  private final TextField contactNameField = new TextField();
  private final TextField contactAvatarField = new TextField();
  private final TextField contactColorField = new TextField();
  private final CheckBox contactSelfCheck = new CheckBox("Treat as self / outgoing sender");
  private final VBox contactForm = new VBox(10);

  private final ListView<String> chatList = new ListView<>();
  private final TextField chatIdField = readonlyField();
  private final TextField chatTitleField = new TextField();
  private final TextField chatParticipantsField = new TextField();
  private final TextField chatIconField = new TextField();
  private final CheckBox chatUnreadCheck = new CheckBox("Mark thread unread on home list");
  private final VBox chatForm = new VBox(10);

  private final ListView<String> messageList = new ListView<>();
  private final Label messageIdLabel = new Label("No message selected");
  private final TextField messageSenderField = new TextField();
  private final TextField messageTimeField = new TextField();
  private final TextArea messageTextArea = new TextArea();
  private final VBox messageForm = new VBox(10);

  private final Properties persisted = new Properties();

  private File projectRoot;
  private Consumer<File> onOpenFile;
  private VnPhoneData workingData = new VnPhoneData();
  private File activeConfigFile;
  private boolean applyingUi;
  private boolean dirty;
  private boolean previewSelectedChat;
  private String selectedContactId;
  private String selectedChatId;
  private String selectedMessageId;

  public PhoneAssetsToolView() {
    getStyleClass().add("phone-tool-root");
    setPadding(new Insets(8));

    buildHeader();
    buildCenter();
    installListeners();
    installAssetDropTargets();
    configureLists();
    statusLabel.getStyleClass().add("phone-tool-status");
    statusLabel.setWrapText(true);
    setBottom(statusLabel);
    BorderPane.setMargin(statusLabel, new Insets(8, 0, 0, 0));
    refreshProjectState();
  }

  public void setProjectRoot(File projectRoot) {
    if (Objects.equals(this.projectRoot, projectRoot)) return;
    this.projectRoot = projectRoot;
    loadPersistedState();
    refreshProjectState();
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    this.onOpenFile = onOpenFile;
  }

  public void refreshFromDisk() {
    refreshProjectState();
    status("Phone configuration reloaded from disk.");
  }

  static Path resolveConfigPath(Path projectRoot) {
    if (projectRoot == null) return Path.of(CONFIG_PATH);
    Path direct = projectRoot.resolve(CONFIG_PATH);
    if (Files.isRegularFile(direct)) return direct;
    Path game = projectRoot.resolve(GAME_CONFIG_PATH);
    if (Files.isRegularFile(game)) return game;
    return direct;
  }

  static Path chooseImportTarget(Path projectRoot, String relativeDir, String fileName) {
    if (projectRoot == null) return Path.of(relativeDir == null ? "" : relativeDir, fileName == null ? "asset" : fileName);
    String normalizedDir = normalizeRelativePath(relativeDir);
    String normalizedName = sanitizeFileName(fileName);
    if (normalizedName.isBlank()) normalizedName = "asset";
    Path baseDir = normalizedDir.isBlank() ? projectRoot : projectRoot.resolve(normalizedDir);
    Path target = baseDir.resolve(normalizedName);
    if (!Files.exists(target)) return target;

    String stem = normalizedName;
    String ext = "";
    int dot = normalizedName.lastIndexOf('.');
    if (dot > 0) {
      stem = normalizedName.substring(0, dot);
      ext = normalizedName.substring(dot);
    }
    int index = 1;
    while (true) {
      Path candidate = baseDir.resolve(stem + "_" + index + ext);
      if (!Files.exists(candidate)) return candidate;
      index++;
    }
  }

  static String sanitizeId(String raw) {
    if (raw == null) return "";
    String value = raw.trim().toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder();
    boolean lastUnderscore = false;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      boolean allowed = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
      if (allowed) {
        out.append(ch);
        lastUnderscore = false;
      } else if (!lastUnderscore) {
        out.append('_');
        lastUnderscore = true;
      }
    }
    while (out.length() > 0 && out.charAt(0) == '_') out.deleteCharAt(0);
    while (out.length() > 0 && out.charAt(out.length() - 1) == '_') out.deleteCharAt(out.length() - 1);
    return out.toString();
  }

  private void buildHeader() {
    Label title = new Label("Phone Assets");
    title.getStyleClass().add("phone-tool-title");

    summaryLabel.getStyleClass().add("phone-tool-summary");
    summaryLabel.setWrapText(true);

    configPathLabel.getStyleClass().add("phone-tool-path");
    dirtyLabel.getStyleClass().addAll("phone-tool-chip", "is-saved");

    saveButton.getStyleClass().add("phone-tool-primary-button");
    saveButton.setOnAction(e -> saveConfig());
    refreshButton.setOnAction(e -> refreshFromDisk());
    importConfigButton.setOnAction(e -> importConfig());
    openConfigButton.setOnAction(e -> openConfigFile());

    HBox pathRow = new HBox(8, new Label("Config"), configPathLabel, dirtyLabel);
    pathRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(configPathLabel, Priority.ALWAYS);

    HBox actions = new HBox(8, refreshButton, importConfigButton, openConfigButton, saveButton);
    actions.setAlignment(Pos.CENTER_LEFT);
    actions.getStyleClass().add("phone-tool-actions");

    VBox header = new VBox(8, title, summaryLabel, pathRow, actions);
    header.getStyleClass().add("phone-tool-card");
    setTop(header);
    BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
  }

  private void buildCenter() {
    previewFrame.getStyleClass().add("phone-tool-preview-frame");
    previewFrame.setPrefHeight(470);
    previewFrame.setMinHeight(420);
    previewFrame.setAlignment(Pos.CENTER);
    phoneRenderer.setEmbeddedPreview(true);
    phoneRenderer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    previewFrame.getChildren().add(phoneRenderer);

    Label previewTitle = new Label("Live Preview");
    previewTitle.getStyleClass().add("phone-tool-section-title");
    Label previewCopy = new Label("Uses the same JavaFX phone renderer as runtime and preview.");
    previewCopy.getStyleClass().add("phone-tool-help");
    previewCopy.setWrapText(true);

    previewHomeButton.setOnAction(e -> {
      previewSelectedChat = false;
      refreshPreview();
      persistUiState();
    });
    previewChatButton.setOnAction(e -> {
      previewSelectedChat = true;
      refreshPreview();
      persistUiState();
    });

    HBox previewToolbar = new HBox(8, previewHomeButton, previewChatButton);
    previewToolbar.setAlignment(Pos.CENTER_LEFT);

    VBox previewCard = new VBox(10, previewTitle, previewCopy, previewToolbar, previewFrame);
    previewCard.getStyleClass().add("phone-tool-card");

    sections.getTabs().addAll(
        buildAppTab(),
        buildContactsTab(),
        buildChatsTab(),
        buildMessagesTab());
    sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    sections.getStyleClass().add("phone-tool-tabs");

    VBox content = new VBox(8, previewCard, sections);
    VBox.setVgrow(sections, Priority.ALWAYS);
    setCenter(content);
  }

  private Tab buildAppTab() {
    Label copy = new Label("App title, wallpaper, and theme colors written to phone.properties.");
    copy.getStyleClass().add("phone-tool-help");
    copy.setWrapText(true);

    appTitleField.setPromptText("Phone");
    appSubtitleField.setPromptText("Messages");
    wallpaperField.setPromptText("assets/ui/phone/wallpaper.png");
    accentField.setPromptText("#78b7ff");
    surfaceField.setPromptText("#101826");
    incomingBubbleField.setPromptText("#1c2738");
    outgoingBubbleField.setPromptText("#2563eb");

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "Title", appTitleField);
    addLabeledRow(grid, row++, "Subtitle", appSubtitleField);
    addLabeledRow(grid, row++, "Wallpaper", assetFieldRow(wallpaperField, WALLPAPER_IMPORT_DIR, "Wallpaper"));
    addLabeledRow(grid, row++, "Accent", accentField);
    addLabeledRow(grid, row++, "Surface", surfaceField);
    addLabeledRow(grid, row++, "Incoming Bubble", incomingBubbleField);
    addLabeledRow(grid, row++, "Outgoing Bubble", outgoingBubbleField);

    VBox root = new VBox(10, copy, grid);
    root.getStyleClass().add("phone-tool-card");
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("App", scroll);
  }

  private Tab buildContactsTab() {
    contactList.getStyleClass().add("phone-tool-list");
    contactList.setPlaceholder(emptyState("No contacts yet", "Add contacts here or create them by referencing new senders in messages."));

    contactNameField.setPromptText("Display name");
    contactAvatarField.setPromptText("assets/phone/contacts/lily.png");
    contactColorField.setPromptText("#f5a97f");

    Label contactTitle = new Label("Selected Contact");
    contactTitle.getStyleClass().add("phone-tool-section-title");

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", contactIdField);
    addLabeledRow(grid, row++, "Name", contactNameField);
    addLabeledRow(grid, row++, "Avatar", assetFieldRow(contactAvatarField, CONTACT_IMPORT_DIR, "Contact Avatar"));
    addLabeledRow(grid, row++, "Color", contactColorField);
    grid.add(contactSelfCheck, 1, row);

    contactForm.getChildren().setAll(contactTitle, grid);
    contactForm.getStyleClass().add("phone-tool-card");
    contactForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Contact", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addContact());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedContact());

    HBox actions = new HBox(8, addButton, removeButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox root = new VBox(
        8,
        actions,
        contactList,
        contactForm);
    VBox.setVgrow(contactList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Contacts", scroll);
  }

  private Tab buildChatsTab() {
    chatList.getStyleClass().add("phone-tool-list");
    chatList.setPlaceholder(emptyState("No threads yet", "Create a thread before adding messages."));

    chatTitleField.setPromptText("Lily");
    chatParticipantsField.setPromptText("mc,lily");
    chatIconField.setPromptText("assets/phone/chats/lily.png");

    Label chatTitle = new Label("Selected Thread");
    chatTitle.getStyleClass().add("phone-tool-section-title");

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", chatIdField);
    addLabeledRow(grid, row++, "Title", chatTitleField);
    addLabeledRow(grid, row++, "Participants", chatParticipantsField);
    addLabeledRow(grid, row++, "Icon", assetFieldRow(chatIconField, CHAT_IMPORT_DIR, "Chat Icon"));
    grid.add(chatUnreadCheck, 1, row);

    chatForm.getChildren().setAll(chatTitle, grid);
    chatForm.getStyleClass().add("phone-tool-card");
    chatForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Thread", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addChat());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedChat());

    HBox actions = new HBox(8, addButton, removeButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox root = new VBox(
        8,
        actions,
        chatList,
        chatForm);
    VBox.setVgrow(chatList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Threads", scroll);
  }

  private Tab buildMessagesTab() {
    messageList.getStyleClass().add("phone-tool-list");
    messageList.setPlaceholder(emptyState("No messages", "Select or create a thread, then add its messages here."));

    messageSenderField.setPromptText("mc");
    messageTimeField.setPromptText("08:15");
    messageTextArea.setPromptText("Message text");
    messageTextArea.setPrefRowCount(4);
    messageTextArea.setWrapText(true);

    Label messageTitle = new Label("Selected Message");
    messageTitle.getStyleClass().add("phone-tool-section-title");
    messageIdLabel.getStyleClass().add("phone-tool-path");

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "Message", messageIdLabel);
    addLabeledRow(grid, row++, "Sender", messageSenderField);
    addLabeledRow(grid, row++, "Time", messageTimeField);
    addLabeledRow(grid, row++, "Text", messageTextArea);

    messageForm.getChildren().setAll(messageTitle, grid);
    messageForm.getStyleClass().add("phone-tool-card");
    messageForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Message", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addMessage());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedMessage());
    Button previewThreadButton = smallButton("Preview Thread", CssIcon.speech("#f5c46b"));
    previewThreadButton.setOnAction(e -> {
      previewSelectedChat = true;
      refreshPreview();
      persistUiState();
    });

    HBox actions = new HBox(8, addButton, removeButton, previewThreadButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    VBox root = new VBox(
        8,
        actions,
        messageList,
        messageForm);
    VBox.setVgrow(messageList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Messages", scroll);
  }

  private void configureLists() {
    contactList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        VnPhoneData.Contact contact = workingData.getContact(item);
        String name = contact == null ? item : firstNonBlank(contact.getDisplayName(), contact.getId());
        String suffix = contact != null && contact.isSelf() ? "  [self]" : "";
        setText(name + "  (" + item + ")" + suffix);
      }
    });

    chatList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        VnPhoneData.Chat chat = workingData.getChat(item);
        if (chat == null) {
          setText(item);
          return;
        }
        String title = firstNonBlank(chat.getTitle(), workingData.defaultChatTitle(chat), chat.getId());
        String unread = chat.isUnread() ? "  [unread]" : "";
        setText(title + "  (" + chat.getMessages().size() + " msg)" + unread);
      }
    });

    messageList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        VnPhoneData.Message message = selectedMessage(item);
        if (message == null) {
          setText(item);
          return;
        }
        String sender = firstNonBlank(
            displayNameFor(message.getSenderId()),
            message.getSenderId());
        String time = firstNonBlank(message.getTimeText(), "");
        String text = message.getText() == null ? "" : message.getText().replace('\n', ' ');
        if (text.length() > 54) text = text.substring(0, 51) + "...";
        setText(sender + (time.isBlank() ? "" : "  " + time) + "  " + text);
      }
    });
  }

  private void installListeners() {
    appTitleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setTitle(newValue);
      changed("Updated phone title.");
    });
    appSubtitleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSubtitle(newValue);
      changed("Updated phone subtitle.");
    });
    wallpaperField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setWallpaperPath(normalizeRelativePath(newValue));
      changed("Updated wallpaper path.");
    });
    accentField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setAccentColor(trimToNull(newValue));
      changed("Updated accent color.");
    });
    surfaceField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSurfaceColor(trimToNull(newValue));
      changed("Updated surface color.");
    });
    incomingBubbleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setIncomingBubbleColor(trimToNull(newValue));
      changed("Updated incoming bubble color.");
    });
    outgoingBubbleField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setOutgoingBubbleColor(trimToNull(newValue));
      changed("Updated outgoing bubble color.");
    });

    contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedContactId = newValue;
      selectedContactId = coerceSelection(selectedContactId, new ArrayList<>(workingData.getContacts().keySet()));
      fillContactForm();
      persistUiState();
    });
    contactNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedContact(contact -> contact.setDisplayName(newValue), "Updated contact name."));
    contactAvatarField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedContact(contact -> contact.setAvatarPath(normalizeRelativePath(newValue)), "Updated contact avatar."));
    contactColorField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedContact(contact -> contact.setColor(trimToNull(newValue)), "Updated contact color."));
    contactSelfCheck.selectedProperty().addListener((obs, oldValue, newValue) -> updateSelectedContact(contact -> contact.setSelf(newValue), "Updated contact sender role."));

    chatList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedChatId = newValue;
      selectedChatId = coerceSelection(selectedChatId, chatIds());
      selectedMessageId = null;
      fillChatForm();
      refreshMessageList();
      fillMessageForm();
      refreshPreview();
      persistUiState();
    });
    chatTitleField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedChat(chat -> chat.setTitle(newValue), "Updated thread title."));
    chatParticipantsField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      VnPhoneData.Chat chat = selectedChat();
      if (chat == null) return;
      chat.setParticipants(parseIdCsv(newValue));
      for (String participant : chat.getParticipants()) {
        workingData.getOrCreateContact(participant);
      }
      changed("Updated thread participants.");
      refreshContactList();
      refreshChatList();
    });
    chatIconField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedChat(chat -> chat.setIconPath(normalizeRelativePath(newValue)), "Updated thread icon."));
    chatUnreadCheck.selectedProperty().addListener((obs, oldValue, newValue) -> updateSelectedChat(chat -> chat.setUnread(newValue), "Updated unread state."));

    messageList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedMessageId = newValue;
      fillMessageForm();
      persistUiState();
    });
    messageSenderField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message sender."));
    messageTimeField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message time."));
    messageTextArea.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message text."));
  }

  private void installAssetDropTargets() {
    AssetPickerSupport.installAssetDrop(wallpaperField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(contactAvatarField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(chatIconField, this::toProjectRelativePath);
  }

  private void refreshProjectState() {
    workingData = loadWorkingData();
    activeConfigFile = projectRoot == null ? null : resolveConfigPath(projectRoot.toPath()).toFile();
    configPathLabel.setText(activeConfigFile == null ? CONFIG_PATH : toProjectRelativePath(activeConfigFile));
    phoneRenderer.setProjectRoot(projectRoot);
    dirty = false;
    updateDirtyBadge();

    restoreSelections();
    fillAppForm();
    refreshContactList();
    fillContactForm();
    refreshChatList();
    fillChatForm();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
    updateSummary();
    updateControlsDisabledState();
  }

  private VnPhoneData loadWorkingData() {
    if (projectRoot == null || !projectRoot.isDirectory()) return new VnPhoneData();
    Path config = resolveConfigPath(projectRoot.toPath());
    if (!Files.isRegularFile(config)) return new VnPhoneData();
    try (InputStream in = Files.newInputStream(config)) {
      return VnPhonePropertiesCodec.load(in);
    } catch (Exception ex) {
      status("Failed to load phone config: " + ex.getMessage());
      return new VnPhoneData();
    }
  }

  private void restoreSelections() {
    selectedContactId = coerceSelection(persisted.getProperty("selection.contact"), new ArrayList<>(workingData.getContacts().keySet()));
    selectedChatId = coerceSelection(persisted.getProperty("selection.chat"), chatIds());
    previewSelectedChat = parseBoolean(persisted.getProperty("preview.selectedChat"), false);
    if (selectedChatId == null && !chatIds().isEmpty()) {
      selectedChatId = chatIds().get(0);
    }
    selectedMessageId = coerceSelection(
        persisted.getProperty("selection.message"),
        messageIdsFor(selectedChatId));
  }

  private void fillAppForm() {
    applyingUi = true;
    try {
      appTitleField.setText(workingData.getTitle());
      appSubtitleField.setText(workingData.getSubtitle());
      wallpaperField.setText(firstNonBlank(workingData.getWallpaperPath(), ""));
      accentField.setText(firstNonBlank(workingData.getAccentColor(), ""));
      surfaceField.setText(firstNonBlank(workingData.getSurfaceColor(), ""));
      incomingBubbleField.setText(firstNonBlank(workingData.getIncomingBubbleColor(), ""));
      outgoingBubbleField.setText(firstNonBlank(workingData.getOutgoingBubbleColor(), ""));
    } finally {
      applyingUi = false;
    }
  }

  private void refreshContactList() {
    List<String> ids = new ArrayList<>(workingData.getContacts().keySet());
    applyingUi = true;
    try {
      contactList.setItems(FXCollections.observableArrayList(ids));
      selectedContactId = coerceSelection(selectedContactId, ids);
      if (selectedContactId != null) contactList.getSelectionModel().select(selectedContactId);
      else contactList.getSelectionModel().clearSelection();
    } finally {
      applyingUi = false;
    }
  }

  private void fillContactForm() {
    VnPhoneData.Contact contact = selectedContact();
    applyingUi = true;
    try {
      contactIdField.setText(contact == null ? "" : contact.getId());
      contactNameField.setText(contact == null ? "" : firstNonBlank(contact.getDisplayName(), ""));
      contactAvatarField.setText(contact == null ? "" : firstNonBlank(contact.getAvatarPath(), ""));
      contactColorField.setText(contact == null ? "" : firstNonBlank(contact.getColor(), ""));
      contactSelfCheck.setSelected(contact != null && contact.isSelf());
    } finally {
      applyingUi = false;
    }
  }

  private void refreshChatList() {
    List<String> ids = chatIds();
    applyingUi = true;
    try {
      chatList.setItems(FXCollections.observableArrayList(ids));
      selectedChatId = coerceSelection(selectedChatId, ids);
      if (selectedChatId != null) chatList.getSelectionModel().select(selectedChatId);
      else chatList.getSelectionModel().clearSelection();
    } finally {
      applyingUi = false;
    }
  }

  private void fillChatForm() {
    VnPhoneData.Chat chat = selectedChat();
    applyingUi = true;
    try {
      chatIdField.setText(chat == null ? "" : chat.getId());
      chatTitleField.setText(chat == null ? "" : firstNonBlank(chat.getTitle(), ""));
      chatParticipantsField.setText(chat == null ? "" : String.join(",", chat.getParticipants()));
      chatIconField.setText(chat == null ? "" : firstNonBlank(chat.getIconPath(), ""));
      chatUnreadCheck.setSelected(chat != null && chat.isUnread());
    } finally {
      applyingUi = false;
    }
  }

  private void refreshMessageList() {
    List<String> ids = messageIdsFor(selectedChatId);
    applyingUi = true;
    try {
      messageList.setItems(FXCollections.observableArrayList(ids));
      selectedMessageId = coerceSelection(selectedMessageId, ids);
      if (selectedMessageId != null) messageList.getSelectionModel().select(selectedMessageId);
      else messageList.getSelectionModel().clearSelection();
    } finally {
      applyingUi = false;
    }
  }

  private void fillMessageForm() {
    VnPhoneData.Message message = selectedMessage(selectedMessageId);
    applyingUi = true;
    try {
      messageIdLabel.setText(message == null ? "No message selected" : message.getId());
      messageSenderField.setText(message == null ? "" : firstNonBlank(message.getSenderId(), ""));
      messageTimeField.setText(message == null ? "" : firstNonBlank(message.getTimeText(), ""));
      messageTextArea.setText(message == null ? "" : firstNonBlank(message.getText(), ""));
    } finally {
      applyingUi = false;
    }
  }

  private void updateSelectedContact(Consumer<VnPhoneData.Contact> mutator, String message) {
    if (applyingUi || mutator == null) return;
    VnPhoneData.Contact contact = selectedContact();
    if (contact == null) return;
    mutator.accept(contact);
    changed(message);
    refreshContactList();
    refreshChatList();
    refreshMessageList();
    refreshPreview();
  }

  private void updateSelectedChat(Consumer<VnPhoneData.Chat> mutator, String message) {
    if (applyingUi || mutator == null) return;
    VnPhoneData.Chat chat = selectedChat();
    if (chat == null) return;
    mutator.accept(chat);
    changed(message);
    refreshChatList();
    refreshMessageList();
    refreshPreview();
  }

  private void updateSelectedMessageFromFields(String message) {
    if (applyingUi) return;
    VnPhoneData.Chat chat = selectedChat();
    VnPhoneData.Message original = selectedMessage(selectedMessageId);
    if (chat == null || original == null) return;

    String senderId = sanitizeId(messageSenderField.getText());
    String time = trimToNull(messageTimeField.getText());
    String text = messageTextArea.getText();
    if (!senderId.isBlank()) {
      workingData.getOrCreateContact(senderId);
    }

    int index = messageIndex(chat, original.getId());
    if (index < 0) return;
    chat.getMessages().set(index, new VnPhoneData.Message(original.getId(), senderId, text, time));
    changed(message);
    refreshContactList();
    refreshChatList();
    refreshMessageList();
    refreshPreview();
  }

  private void addContact() {
    String id = promptForId("Add Contact", "Contact ID", nextId("contact", workingData.getContacts().keySet()));
    if (id == null) return;
    if (workingData.getContacts().containsKey(id)) {
      status("Contact already exists: " + id);
      return;
    }
    workingData.getOrCreateContact(id);
    selectedContactId = id;
    changed("Added contact " + id + ".");
    refreshContactList();
    fillContactForm();
    refreshPreview();
  }

  private void removeSelectedContact() {
    VnPhoneData.Contact contact = selectedContact();
    if (contact == null) return;
    if (isContactReferenced(contact.getId())) {
      showInfo("Contact In Use", "Remove the contact from thread participants and messages before deleting it.");
      return;
    }
    workingData.getContacts().remove(contact.getId());
    selectedContactId = null;
    changed("Removed contact " + contact.getId() + ".");
    refreshContactList();
    fillContactForm();
    refreshPreview();
  }

  private boolean isContactReferenced(String contactId) {
    if (contactId == null || contactId.isBlank()) return false;
    for (VnPhoneData.Chat chat : workingData.getChats().values()) {
      if (chat.getParticipants().contains(contactId)) return true;
      for (VnPhoneData.Message message : chat.getMessages()) {
        if (Objects.equals(contactId, message.getSenderId())) return true;
      }
    }
    return false;
  }

  private void addChat() {
    String id = promptForId("Add Thread", "Thread ID", nextId("thread", new LinkedHashSet<>(chatIds())));
    if (id == null) return;
    if (workingData.getChats().containsKey(id)) {
      status("Thread already exists: " + id);
      return;
    }
    VnPhoneData.Chat chat = workingData.getOrCreateChat(id);
    if (!workingData.getContacts().isEmpty()) {
      String first = new ArrayList<>(workingData.getContacts().keySet()).get(0);
      chat.addParticipant(first);
    }
    selectedChatId = id;
    selectedMessageId = null;
    previewSelectedChat = true;
    changed("Added thread " + id + ".");
    refreshChatList();
    fillChatForm();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
  }

  private void removeSelectedChat() {
    VnPhoneData.Chat chat = selectedChat();
    if (chat == null) return;
    workingData.removeChat(chat.getId());
    selectedChatId = null;
    selectedMessageId = null;
    changed("Removed thread " + chat.getId() + ".");
    refreshChatList();
    fillChatForm();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
  }

  private void addMessage() {
    VnPhoneData.Chat chat = selectedChat();
    if (chat == null) {
      status("Create or select a thread first.");
      return;
    }
    String senderId = !workingData.getContacts().isEmpty()
        ? new ArrayList<>(workingData.getContacts().keySet()).get(0)
        : "mc";
    workingData.getOrCreateContact(senderId);
    String messageId = nextMessageId(chat);
    chat.getMessages().add(new VnPhoneData.Message(messageId, senderId, "", null));
    selectedMessageId = messageId;
    previewSelectedChat = true;
    changed("Added message " + messageId + ".");
    refreshContactList();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
  }

  private void removeSelectedMessage() {
    VnPhoneData.Chat chat = selectedChat();
    VnPhoneData.Message message = selectedMessage(selectedMessageId);
    if (chat == null || message == null) return;
    int index = messageIndex(chat, message.getId());
    if (index < 0) return;
    chat.getMessages().remove(index);
    selectedMessageId = null;
    changed("Removed message " + message.getId() + ".");
    refreshMessageList();
    fillMessageForm();
    refreshChatList();
    refreshPreview();
  }

  private void saveConfig() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      status("Open a project before saving phone config.");
      return;
    }
    activeConfigFile = resolveConfigPath(projectRoot.toPath()).toFile();
    configPathLabel.setText(toProjectRelativePath(activeConfigFile));
    try {
      Files.createDirectories(activeConfigFile.toPath().getParent());
      try (OutputStream out = Files.newOutputStream(activeConfigFile.toPath())) {
        out.write(VnPhonePropertiesCodec.toBytes(workingData));
      }
      dirty = false;
      updateDirtyBadge();
      updateControlsDisabledState();
      updateSummary();
      persistUiState();
      status("Saved " + toProjectRelativePath(activeConfigFile) + ".");
    } catch (Exception ex) {
      status("Failed to save phone config: " + ex.getMessage());
    }
  }

  private void importConfig() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Import Phone Config");
    chooser.getExtensionFilters().setAll(
        new FileChooser.ExtensionFilter("Properties", "*.properties"),
        new FileChooser.ExtensionFilter("All Files", "*.*"));
    File initial = activeConfigFile != null && activeConfigFile.getParentFile() != null
        ? activeConfigFile.getParentFile()
        : initialDirectory("config");
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File selected = chooser.showOpenDialog(ownerWindow());
    if (selected == null) return;
    try (InputStream in = Files.newInputStream(selected.toPath())) {
      workingData = VnPhonePropertiesCodec.load(in);
      selectedContactId = null;
      selectedChatId = null;
      selectedMessageId = null;
      changed("Imported phone config from " + selected.getName() + ".");
      restoreSelections();
      fillAppForm();
      refreshContactList();
      fillContactForm();
      refreshChatList();
      fillChatForm();
      refreshMessageList();
      fillMessageForm();
      refreshPreview();
      updateSummary();
    } catch (Exception ex) {
      status("Failed to import config: " + ex.getMessage());
    }
  }

  private void openConfigFile() {
    if (activeConfigFile == null || !activeConfigFile.exists()) {
      status("Phone config has not been created yet. Save first.");
      return;
    }
    if (onOpenFile != null) {
      onOpenFile.accept(activeConfigFile);
      return;
    }
    AssetPickerSupport.revealFile(activeConfigFile);
  }

  private void refreshPreview() {
    phoneRenderer.setProjectRoot(projectRoot);
    previewHomeButton.getStyleClass().setAll(
        "phone-tool-button",
        "phone-tool-preview-toggle",
        previewSelectedChat ? "is-idle" : "is-active");
    previewChatButton.getStyleClass().setAll(
        "phone-tool-button",
        "phone-tool-preview-toggle",
        previewSelectedChat ? "is-active" : "is-idle");
    previewChatButton.setDisable(selectedChatId == null);

    VnPhoneData previewData = copyOf(workingData);
    PhoneScene scene = previewSelectedChat && selectedChatId != null
        ? new PhoneScene(null, previewData, ignored -> { }, selectedChatId)
        : new PhoneScene(null, previewData, ignored -> { });
    phoneRenderer.setSceneModel(scene);
    phoneRenderer.refresh();
  }

  private void updateSummary() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      summaryLabel.setText("Open a project to edit phone assets and configuration.");
      return;
    }
    int messageCount = 0;
    for (VnPhoneData.Chat chat : workingData.getChats().values()) {
      messageCount += chat.getMessages().size();
    }
    String fileLabel = activeConfigFile == null ? CONFIG_PATH : toProjectRelativePath(activeConfigFile);
    summaryLabel.setText(
        "Contacts: " + workingData.getContacts().size()
            + "  |  Threads: " + workingData.getChats().size()
            + "  |  Messages: " + messageCount
            + "  |  File: " + fileLabel);
  }

  private void updateControlsDisabledState() {
    boolean hasProject = projectRoot != null && projectRoot.isDirectory();
    saveButton.setDisable(!hasProject);
    refreshButton.setDisable(!hasProject);
    importConfigButton.setDisable(!hasProject);
    openConfigButton.setDisable(!hasProject || activeConfigFile == null || !activeConfigFile.exists());
    contactForm.setDisable(selectedContact() == null);
    chatForm.setDisable(selectedChat() == null);
    messageForm.setDisable(selectedMessage(selectedMessageId) == null);
  }

  private void changed(String message) {
    dirty = true;
    updateDirtyBadge();
    updateControlsDisabledState();
    updateSummary();
    refreshPreview();
    persistUiState();
    status(message);
  }

  private void updateDirtyBadge() {
    dirtyLabel.setText(dirty ? "Unsaved" : "Saved");
    dirtyLabel.getStyleClass().setAll("phone-tool-chip", dirty ? "is-dirty" : "is-saved");
  }

  private void status(String message) {
    statusLabel.setText(firstNonBlank(message, ""));
  }

  private void persistUiState() {
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    if (selectedContactId != null) persisted.setProperty("selection.contact", selectedContactId);
    else persisted.remove("selection.contact");
    if (selectedChatId != null) persisted.setProperty("selection.chat", selectedChatId);
    else persisted.remove("selection.chat");
    if (selectedMessageId != null) persisted.setProperty("selection.message", selectedMessageId);
    else persisted.remove("selection.message");
    persisted.setProperty("preview.selectedChat", Boolean.toString(previewSelectedChat));

    Path statePath = projectRoot.toPath().resolve(STATE_FILE);
    try {
      Files.createDirectories(statePath.getParent());
      try (OutputStream out = Files.newOutputStream(statePath)) {
        persisted.store(out, "JVN Phone Assets Tool State");
      }
    } catch (IOException ex) {
      status("Failed to save phone tool state: " + ex.getMessage());
    }
  }

  private void loadPersistedState() {
    persisted.clear();
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    Path statePath = projectRoot.toPath().resolve(STATE_FILE);
    if (!Files.isRegularFile(statePath)) return;
    try (InputStream in = Files.newInputStream(statePath)) {
      persisted.load(in);
    } catch (IOException ex) {
      status("Failed to load phone tool state: " + ex.getMessage());
    }
  }

  private Window ownerWindow() {
    return getScene() == null ? null : getScene().getWindow();
  }

  private String promptForId(String title, String content, String suggestion) {
    TextInputDialog dialog = new TextInputDialog(suggestion);
    dialog.setTitle(title);
    dialog.setHeaderText(null);
    dialog.setContentText(content + ":");
    EditorTheme.apply(dialog);
    return dialog.showAndWait()
        .map(PhoneAssetsToolView::sanitizeId)
        .filter(value -> !value.isBlank())
        .orElse(null);
  }

  private String nextId(String prefix, Set<String> usedIds) {
    String normalizedPrefix = sanitizeId(prefix);
    if (normalizedPrefix.isBlank()) normalizedPrefix = "item";
    if (usedIds == null || !usedIds.contains(normalizedPrefix)) return normalizedPrefix;
    int index = 2;
    while (usedIds.contains(normalizedPrefix + "_" + index)) {
      index++;
    }
    return normalizedPrefix + "_" + index;
  }

  private String nextMessageId(VnPhoneData.Chat chat) {
    if (chat == null) return "m1";
    Set<String> used = new LinkedHashSet<>();
    for (VnPhoneData.Message message : chat.getMessages()) {
      used.add(message.getId());
    }
    int index = 1;
    while (used.contains("m" + index)) {
      index++;
    }
    return "m" + index;
  }

  private VnPhoneData.Contact selectedContact() {
    return selectedContactId == null ? null : workingData.getContact(selectedContactId);
  }

  private VnPhoneData.Chat selectedChat() {
    return selectedChatId == null ? null : workingData.getChat(selectedChatId);
  }

  private VnPhoneData.Message selectedMessage(String messageId) {
    VnPhoneData.Chat chat = selectedChat();
    if (chat == null || messageId == null) return null;
    for (VnPhoneData.Message message : chat.getMessages()) {
      if (Objects.equals(messageId, message.getId())) return message;
    }
    return null;
  }

  private int messageIndex(VnPhoneData.Chat chat, String messageId) {
    if (chat == null || messageId == null) return -1;
    for (int i = 0; i < chat.getMessages().size(); i++) {
      if (Objects.equals(messageId, chat.getMessages().get(i).getId())) return i;
    }
    return -1;
  }

  private List<String> chatIds() {
    List<String> ids = new ArrayList<>();
    for (VnPhoneData.Chat chat : workingData.orderedChats()) {
      if (chat != null && chat.getId() != null) ids.add(chat.getId());
    }
    return ids;
  }

  private List<String> messageIdsFor(String chatId) {
    VnPhoneData.Chat chat = chatId == null ? null : workingData.getChat(chatId);
    List<String> ids = new ArrayList<>();
    if (chat == null) return ids;
    for (VnPhoneData.Message message : chat.getMessages()) {
      ids.add(message.getId());
    }
    return ids;
  }

  private String displayNameFor(String contactId) {
    VnPhoneData.Contact contact = workingData.getContact(contactId);
    return contact == null ? null : firstNonBlank(contact.getDisplayName(), contact.getId());
  }

  private HBox assetFieldRow(TextField field, String importDir, String label) {
    HBox row = new HBox(6);
    field.getStyleClass().add("phone-tool-asset-field");
    HBox.setHgrow(field, Priority.ALWAYS);

    Button browseButton = smallButton("", CssIcon.folder("#9cc7ff"));
    browseButton.setTooltip(new Tooltip("Choose " + label + " path"));
    browseButton.setOnAction(e -> chooseAsset(field));

    Button importButton = smallButton("", CssIcon.download("#8ab4f8"));
    importButton.setTooltip(new Tooltip("Import " + label + " into the project"));
    importButton.setOnAction(e -> importAsset(field, importDir));

    Button revealButton = smallButton("", CssIcon.link("#f5c46b"));
    revealButton.setTooltip(new Tooltip("Reveal current asset on disk"));
    revealButton.setOnAction(e -> revealAsset(field.getText()));

    row.getChildren().addAll(field, browseButton, importButton, revealButton);
    return row;
  }

  private void chooseAsset(TextField field) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose Asset");
    AssetPickerSupport.addAssetFilters(chooser);
    File initial = initialDirectory("assets");
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File selected = chooser.showOpenDialog(ownerWindow());
    if (selected == null) return;
    field.setText(toProjectRelativePath(selected));
  }

  private void importAsset(TextField field, String importDir) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Import Asset");
    AssetPickerSupport.addAssetFilters(chooser);
    File initial = initialDirectory(importDir);
    if (initial != null && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File selected = chooser.showOpenDialog(ownerWindow());
    if (selected == null) return;
    if (projectRoot == null || !projectRoot.isDirectory()) {
      field.setText(toProjectRelativePath(selected));
      return;
    }
    try {
      Path target = chooseImportTarget(projectRoot.toPath(), importDir, selected.getName());
      Files.createDirectories(target.getParent());
      Files.copy(selected.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
      field.setText(toProjectRelativePath(target.toFile()));
      status("Imported asset to " + toProjectRelativePath(target.toFile()) + ".");
    } catch (Exception ex) {
      status("Failed to import asset: " + ex.getMessage());
    }
  }

  private void revealAsset(String path) {
    File asset = resolveAssetFile(path);
    if (!AssetPickerSupport.revealFile(asset)) {
      status("Asset not found: " + normalizeRelativePath(path));
    }
  }

  private File resolveAssetFile(String path) {
    String normalized = normalizeRelativePath(path);
    if (normalized.isBlank()) return null;
    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;
    if (projectRoot != null) return new File(projectRoot, normalized);
    return direct;
  }

  private File initialDirectory(String relative) {
    if (projectRoot != null && projectRoot.isDirectory()) {
      File candidate = relative == null || relative.isBlank()
          ? projectRoot
          : new File(projectRoot, normalizeRelativePath(relative));
      if (candidate.isDirectory()) return candidate;
      if (candidate.getParentFile() != null && candidate.getParentFile().isDirectory()) {
        return candidate.getParentFile();
      }
      return projectRoot;
    }
    return new File(System.getProperty("user.home", "."));
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null || !projectRoot.isDirectory()) {
      return file.getAbsolutePath().replace('\\', '/');
    }
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path target = file.toPath().toAbsolutePath().normalize();
      if (target.startsWith(root)) {
        return normalizeRelativePath(root.relativize(target).toString());
      }
    } catch (Exception ignored) {
    }
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static VnPhoneData copyOf(VnPhoneData source) {
    VnPhoneData copy = new VnPhoneData();
    if (source == null) return copy;
    copy.setTitle(source.getTitle());
    copy.setSubtitle(source.getSubtitle());
    copy.setWallpaperPath(source.getWallpaperPath());
    copy.setAccentColor(source.getAccentColor());
    copy.setSurfaceColor(source.getSurfaceColor());
    copy.setIncomingBubbleColor(source.getIncomingBubbleColor());
    copy.setOutgoingBubbleColor(source.getOutgoingBubbleColor());

    for (VnPhoneData.Contact contact : source.getContacts().values()) {
      VnPhoneData.Contact target = copy.getOrCreateContact(contact.getId());
      target.setDisplayName(contact.getDisplayName());
      target.setAvatarPath(contact.getAvatarPath());
      target.setColor(contact.getColor());
      target.setSelf(contact.isSelf());
    }

    copy.setChatOrder(source.getChatOrder());
    for (VnPhoneData.Chat chat : source.orderedChats()) {
      VnPhoneData.Chat target = copy.getOrCreateChat(chat.getId());
      target.setTitle(chat.getTitle());
      target.setIconPath(chat.getIconPath());
      target.setParticipants(chat.getParticipants());
      target.setUnread(chat.isUnread());
      for (VnPhoneData.Message message : chat.getMessages()) {
        target.getMessages().add(new VnPhoneData.Message(
            message.getId(),
            message.getSenderId(),
            message.getText(),
            message.getTimeText()));
      }
    }
    return copy;
  }

  private static GridPane formGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    return grid;
  }

  private static void addLabeledRow(GridPane grid, int row, String label, javafx.scene.Node node) {
    Label fieldLabel = new Label(label);
    fieldLabel.getStyleClass().add("phone-tool-field-label");
    grid.add(fieldLabel, 0, row);
    grid.add(node, 1, row);
    GridPane.setHgrow(node, Priority.ALWAYS);
  }

  private static ScrollPane cardScroll(VBox content) {
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("phone-tool-scroll");
    return scroll;
  }

  private static javafx.scene.Node emptyState(String title, String copy) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("phone-tool-empty-title");
    Label copyLabel = new Label(copy);
    copyLabel.getStyleClass().add("phone-tool-empty-copy");
    copyLabel.setWrapText(true);
    VBox box = new VBox(6, titleLabel, copyLabel);
    box.getStyleClass().add("phone-tool-empty");
    return box;
  }

  private static TextField readonlyField() {
    TextField field = new TextField();
    field.setEditable(false);
    field.setFocusTraversable(false);
    field.getStyleClass().add("phone-tool-readonly");
    return field;
  }

  private static Button headerButton(String text, Region icon) {
    Button button = new Button(text, icon);
    button.getStyleClass().add("phone-tool-button");
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    return button;
  }

  private static Button smallButton(String text, Region icon) {
    Button button = new Button(text, icon);
    button.getStyleClass().addAll("phone-tool-button", "phone-tool-small-button");
    button.setContentDisplay(ContentDisplay.LEFT);
    button.setGraphicTextGap(6);
    return button;
  }

  private static List<String> parseIdCsv(String raw) {
    List<String> ids = new ArrayList<>();
    if (raw == null || raw.isBlank()) return ids;
    Set<String> seen = new LinkedHashSet<>();
    for (String token : raw.split("[,\\n]+")) {
      String normalized = sanitizeId(token);
      if (!normalized.isBlank() && seen.add(normalized)) {
        ids.add(normalized);
      }
    }
    return ids;
  }

  private static String normalizeRelativePath(String raw) {
    if (raw == null) return "";
    return raw.trim().replace('\\', '/');
  }

  private static String sanitizeFileName(String fileName) {
    if (fileName == null) return "";
    String normalized = fileName.trim().replace('\\', '_').replace('/', '_');
    return normalized.isBlank() ? "" : normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private static String coerceSelection(String preferred, List<String> visible) {
    if (visible == null || visible.isEmpty()) return null;
    if (preferred != null && visible.contains(preferred)) return preferred;
    return visible.get(0);
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on" -> true;
      case "false", "0", "no", "off" -> false;
      default -> fallback;
    };
  }

  private void showInfo(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    EditorTheme.apply(alert);
    alert.showAndWait();
  }
}

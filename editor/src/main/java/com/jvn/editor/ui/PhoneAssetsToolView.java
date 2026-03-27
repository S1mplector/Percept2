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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
 * 
 * The phone configuration is persisted to a properties file in the project and
 * assets are copied into the project when imported through the UI. Changes are
 * reflected in the live preview and can be saved to disk or discarded by refreshing
 * from disk. The phone renderer uses the same code and assets as the runtime and
 * is intended to help iterate on phone configuration and assets without needing to
 * constantly redeploy the game or use external image editing tools.
 * 
 * Designed to be resilient to manual edits to the configuration file and missing assets on disk,
 * with the goal of never losing data or getting into an unrecoverable state. If the configuration file
 * is missing or contains errors, the tool will fall back to reasonable defaults and allow the user to
 * edit and save a new configuration. If assets are missing, the tool will show blank or default placeholders
 * but allow the user to fix the paths and save the configuration with valid assets. The live preview will
 * always attempt to show the current state of the configuration and assets, even if they are incomplete or contain errors, to provide immediate feedback as the user iterates.
 * 
 * It is recommended to keep a backup of the project or use version control
 * when using this tool, as it will overwrite the phone configuration file and 
 * copy assets into the project when saving changes. However, the tool itself is designed to avoid data loss and make it easy to recover from mistakes by refreshing from disk or fixing asset paths.
 *  
 */
public class PhoneAssetsToolView extends BorderPane {
  static final String STATE_FILE = ".jvn/phone-assets-tool.properties";
  static final String CONFIG_PATH = "config/phone/phone.properties";
  static final String GAME_CONFIG_PATH = "game/config/phone/phone.properties";
  static final String WALLPAPER_IMPORT_DIR = "assets/ui/phone";
  static final String SKIN_IMPORT_DIR = "assets/ui/phone/skins";
  static final String CONTACT_IMPORT_DIR = "assets/phone/contacts";
  static final String CHAT_IMPORT_DIR = "assets/phone/chats";

  private final Label summaryLabel = new Label("Open a project to edit phone assets and configuration.");
  private final Label statusLabel = new Label("");
  private final Label configPathLabel = new Label(CONFIG_PATH);
  private final Label dirtyLabel = new Label("Saved");
  private final Label contactMetricLabel = metricChip("Contacts 0");
  private final Label threadMetricLabel = metricChip("Threads 0");
  private final Label messageMetricLabel = metricChip("Messages 0");
  private final Label previewMetricLabel = metricChip("Preview Home");

  private final Button saveButton = headerButton("Save", CssIcon.save("#9ed67a"));
  private final Button refreshButton = headerButton("Refresh", CssIcon.redo("#7ec8e3"));
  private final Button importConfigButton = headerButton("Import Config", CssIcon.download("#8ab4f8"));
  private final Button openConfigButton = headerButton("Open File", CssIcon.folder("#f5c46b"));

  private final PhoneRenderer phoneRenderer = new PhoneRenderer();
  private final StackPane previewFrame = new StackPane();
  private final Button previewHomeButton = headerButton("Preview Home", CssIcon.home("#9cc7ff"));
  private final Button previewChatButton = headerButton("Preview Chat", CssIcon.speech("#f5c46b"));
  private final Button previewCallButton = headerButton("Preview Call", CssIcon.play("#f38ba8"));
  private final Label previewSelectionLabel = new Label("Previewing home list");

  private final TabPane sections = new TabPane();
  private final SplitPane workspaceSplit = new SplitPane();

  private final TextField appTitleField = new TextField();
  private final TextField appSubtitleField = new TextField();
  private final TextField wallpaperField = new TextField();
  private final TextField accentField = new TextField();
  private final TextField surfaceField = new TextField();
  private final TextField incomingBubbleField = new TextField();
  private final TextField outgoingBubbleField = new TextField();
  private final TextField skinIdField = new TextField();
  private final TextField skinBackgroundField = new TextField();
  private final TextField skinTopBarField = new TextField();
  private final TextField skinBottomBarField = new TextField();
  private final TextField skinMessageField = new TextField();
  private final TextField skinNavLeadingField = new TextField();
  private final TextField skinNavTrailingPrimaryField = new TextField();
  private final TextField skinNavTrailingSecondaryField = new TextField();
  private final TextField skinComposerLeadingField = new TextField();
  private final TextField skinComposerTrailingPrimaryField = new TextField();
  private final TextField skinComposerTrailingSecondaryField = new TextField();
  private final TextField skinStatusBackdropField = new TextField();
  private final TextField skinStatusIconField = new TextField();
  private final TextField skinFloatingActionField = new TextField();
  private final TextField bubbleIncomingImageField = new TextField();
  private final TextField bubbleOutgoingImageField = new TextField();
  private final ComboBox<VnPhoneData.HomeMode> homeModeCombo = new ComboBox<>();
  private final TextField statusTimeField = new TextField();
  private final TextField statusModeField = new TextField();
  private final TextField statusSignalField = new TextField();
  private final TextField statusBatteryField = new TextField();

  private final ListView<String> contactList = new ListView<>();
  private final TextField contactFilterField = filterField("Filter contacts...");
  private final Label contactCountLabel = new Label("0 contacts");
  private final TextField contactIdField = readonlyField();
  private final TextField contactNameField = new TextField();
  private final TextField contactAvatarField = new TextField();
  private final TextField contactColorField = new TextField();
  private final CheckBox contactSelfCheck = new CheckBox("Treat as self / outgoing sender");
  private final VBox contactForm = new VBox(10);

  private final ListView<String> chatList = new ListView<>();
  private final TextField chatFilterField = filterField("Filter threads...");
  private final Label chatCountLabel = new Label("0 threads");
  private final TextField chatIdField = readonlyField();
  private final TextField chatTitleField = new TextField();
  private final TextField chatParticipantsField = new TextField();
  private final TextField chatIconField = new TextField();
  private final CheckBox chatUnreadCheck = new CheckBox("Mark thread unread on home list");
  private final VBox chatForm = new VBox(10);

  private final ListView<String> appList = new ListView<>();
  private final TextField appFilterField = filterField("Filter apps...");
  private final Label appCountLabel = new Label("0 apps");
  private final TextField appIdField = readonlyField();
  private final TextField appNameField = new TextField();
  private final TextField appIconField = new TextField();
  private final TextField appBadgeField = new TextField();
  private final TextField appAccentColorField = new TextField();
  private final TextField appPageField = new TextField();
  private final ComboBox<VnPhoneData.AppTargetType> appTargetTypeCombo = new ComboBox<>();
  private final TextField appTargetValueField = new TextField();
  private final VBox appForm = new VBox(10);

  private final ListView<String> messageList = new ListView<>();
  private final TextField messageFilterField = filterField("Filter messages...");
  private final Label messageCountLabel = new Label("0 messages");
  private final Label messageIdLabel = new Label("No message selected");
  private final ComboBox<VnPhoneData.MessageType> messageTypeCombo = new ComboBox<>();
  private final TextField messageSenderField = new TextField();
  private final TextField messageTimeField = new TextField();
  private final TextField messageAssetField = new TextField();
  private final TextField messageCaptionField = new TextField();
  private final TextField messageDurationField = new TextField();
  private final TextField messageOptionsField = new TextField();
  private final TextArea messageTextArea = new TextArea();
  private final VBox messageForm = new VBox(10);

  private final ListView<String> callList = new ListView<>();
  private final TextField callFilterField = filterField("Filter calls...");
  private final Label callCountLabel = new Label("0 calls");
  private final TextField callIdField = readonlyField();
  private final TextField callTitleField = new TextField();
  private final TextField callSubtitleField = new TextField();
  private final TextField callParticipantField = new TextField();
  private final TextField callAvatarField = new TextField();
  private final TextField callStatusField = new TextField();
  private final CheckBox callVideoCheck = new CheckBox("Video call");
  private final VBox callForm = new VBox(10);

  private final Properties persisted = new Properties();

  private File projectRoot;
  private Consumer<File> onOpenFile;
  private VnPhoneData workingData = new VnPhoneData();
  private File activeConfigFile;
  private boolean applyingUi;
  private boolean dirty;
  private PreviewMode previewMode = PreviewMode.HOME;
  private String selectedContactId;
  private String selectedChatId;
  private String selectedAppId;
  private String selectedMessageId;
  private String selectedCallId;
  private boolean disposed;

  private enum PreviewMode {
    HOME,
    CHAT,
    CALL
  }

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
    if (disposed) return;
    if (Objects.equals(this.projectRoot, projectRoot)) return;
    this.projectRoot = projectRoot;
    loadPersistedState();
    refreshProjectState();
  }

  public void setOnOpenFile(Consumer<File> onOpenFile) {
    if (disposed) return;
    this.onOpenFile = onOpenFile;
  }

  public void refreshFromDisk() {
    if (disposed) return;
    refreshProjectState();
    status("Phone configuration reloaded from disk.");
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    onOpenFile = null;
    projectRoot = null;
    activeConfigFile = null;
    selectedContactId = null;
    selectedChatId = null;
    selectedAppId = null;
    selectedMessageId = null;
    selectedCallId = null;
    previewMode = PreviewMode.HOME;
    persisted.clear();
    workingData = new VnPhoneData();
    applyingUi = true;
    try {
      contactList.getItems().clear();
      chatList.getItems().clear();
      messageList.getItems().clear();
    } finally {
      applyingUi = false;
    }
    phoneRenderer.setProjectRoot(null);
    phoneRenderer.setSceneModel(new PhoneScene(null, new VnPhoneData(), ignored -> { }));
    phoneRenderer.refresh();
    summaryLabel.setText("Phone assets tool disposed.");
    statusLabel.setText("");
    dirty = false;
    updateDirtyBadge();
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
    contactMetricLabel.getStyleClass().add("phone-tool-metric-chip");
    threadMetricLabel.getStyleClass().add("phone-tool-metric-chip");
    messageMetricLabel.getStyleClass().add("phone-tool-metric-chip");
    previewMetricLabel.getStyleClass().add("phone-tool-metric-chip");

    saveButton.getStyleClass().add("phone-tool-primary-button");
    saveButton.setOnAction(e -> saveConfig());
    refreshButton.setOnAction(e -> refreshFromDisk());
    importConfigButton.setOnAction(e -> importConfig());
    openConfigButton.setOnAction(e -> openConfigFile());

    Label configLabel = new Label("Config");
    configLabel.getStyleClass().add("phone-tool-section-title");
    HBox pathRow = new HBox(8, configLabel, configPathLabel);
    pathRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(configPathLabel, Priority.ALWAYS);

    HBox titleRow = new HBox(10, title, dirtyLabel);
    titleRow.setAlignment(Pos.CENTER_LEFT);

    FlowPane metrics = new FlowPane(8, 8, contactMetricLabel, threadMetricLabel, messageMetricLabel, previewMetricLabel);
    metrics.getStyleClass().add("phone-tool-metric-row");

    FlowPane actions = new FlowPane(8, 8, refreshButton, importConfigButton, openConfigButton, saveButton);
    actions.setAlignment(Pos.CENTER_LEFT);
    actions.getStyleClass().add("phone-tool-actions");

    VBox header = new VBox(10, titleRow, summaryLabel, metrics, pathRow, actions);
    header.getStyleClass().add("phone-tool-card");
    setTop(header);
    BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
  }

  private void buildCenter() {
    previewFrame.getStyleClass().add("phone-tool-preview-frame");
    previewFrame.setPrefHeight(390);
    previewFrame.setMinHeight(320);
    previewFrame.setAlignment(Pos.CENTER);
    phoneRenderer.setEmbeddedPreview(true);
    phoneRenderer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    previewFrame.getChildren().add(phoneRenderer);

    Label previewTitle = new Label("Live Preview");
    previewTitle.getStyleClass().add("phone-tool-section-title");
    Label previewCopy = new Label("Uses the same JavaFX phone renderer as runtime and preview.");
    previewCopy.getStyleClass().add("phone-tool-help");
    previewCopy.setWrapText(true);
    previewSelectionLabel.getStyleClass().add("phone-tool-preview-note");
    previewSelectionLabel.setWrapText(true);

    previewHomeButton.setOnAction(e -> {
      previewMode = PreviewMode.HOME;
      refreshPreview();
      persistUiState();
    });
    previewChatButton.setOnAction(e -> {
      previewMode = PreviewMode.CHAT;
      refreshPreview();
      persistUiState();
    });
    previewCallButton.setOnAction(e -> {
      previewMode = PreviewMode.CALL;
      refreshPreview();
      persistUiState();
    });

    HBox previewToolbar = new HBox(8, previewHomeButton, previewChatButton, previewCallButton);
    previewToolbar.setAlignment(Pos.CENTER_LEFT);

    VBox previewCard = new VBox(10, previewTitle, previewCopy, previewSelectionLabel, previewToolbar, previewFrame);
    previewCard.getStyleClass().add("phone-tool-card");
    previewCard.getStyleClass().add("phone-tool-preview-card");
    previewCard.setMaxWidth(560);
    previewCard.setFillWidth(true);
    VBox.setVgrow(previewFrame, Priority.ALWAYS);

    StackPane previewHost = new StackPane(previewCard);
    previewHost.getStyleClass().add("phone-tool-preview-host");
    previewHost.setAlignment(Pos.TOP_CENTER);

    sections.getTabs().addAll(
        buildAppTab(),
        buildContactsTab(),
        buildChatsTab(),
        buildAppsTab(),
        buildMessagesTab(),
        buildCallsTab());
    sections.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    sections.getStyleClass().add("phone-tool-tabs");

    workspaceSplit.getItems().setAll(sections, previewHost);
    workspaceSplit.getStyleClass().add("phone-tool-workspace");
    workspaceSplit.setDividerPositions(0.58);
    SplitPane.setResizableWithParent(previewHost, true);
    SplitPane.setResizableWithParent(sections, true);
    widthProperty().addListener((obs, oldWidth, newWidth) -> updateWorkspaceLayout());
    Platform.runLater(this::updateWorkspaceLayout);

    setCenter(workspaceSplit);
  }

  private void updateWorkspaceLayout() {
    double width = getWidth() > 0 ? getWidth() : getPrefWidth();
    boolean wide = width >= 1180.0;
    Orientation targetOrientation = wide ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    if (workspaceSplit.getOrientation() != targetOrientation) {
      workspaceSplit.setOrientation(targetOrientation);
      workspaceSplit.setDividerPositions(wide ? 0.56 : 0.60);
    }
  }

  private Tab buildAppTab() {
    Label copy = new Label("App title, wallpaper, theme colors, and optional skin/chrome overlays written to phone.properties.");
    copy.getStyleClass().add("phone-tool-help");
    copy.setWrapText(true);

    homeModeCombo.getItems().setAll(VnPhoneData.HomeMode.values());
    appTitleField.setPromptText("Phone");
    appSubtitleField.setPromptText("Messages");
    wallpaperField.setPromptText("assets/ui/phone/wallpaper.png");
    homeModeCombo.setPromptText("threads | apps");
    accentField.setPromptText("#78b7ff");
    surfaceField.setPromptText("#101826");
    incomingBubbleField.setPromptText("#1c2738");
    outgoingBubbleField.setPromptText("#2563eb");
    statusTimeField.setPromptText("9:41");
    statusModeField.setPromptText("Focus");
    statusSignalField.setPromptText("LTE");
    statusBatteryField.setPromptText("82%");
    skinIdField.setPromptText("sms | discord | custom");
    skinBackgroundField.setPromptText("assets/ui/phone/skins/sms/sms_background.png");
    skinTopBarField.setPromptText("assets/ui/phone/skins/sms/sms_top_bar.png");
    skinBottomBarField.setPromptText("assets/ui/phone/skins/sms/sms_bottom_bar.png");
    skinMessageField.setPromptText("assets/ui/phone/skins/sms/sms_message_field.png");
    skinNavLeadingField.setPromptText("assets/ui/phone/skins/sms/sms_back_arrow.png");
    skinNavTrailingPrimaryField.setPromptText("assets/ui/phone/skins/sms/sms_video_call_button.png");
    skinNavTrailingSecondaryField.setPromptText("assets/ui/phone/skins/sms/sms_enter_profile_arrow.png");
    skinComposerLeadingField.setPromptText("assets/ui/phone/skins/sms/sms_take_picture_button.png");
    skinComposerTrailingPrimaryField.setPromptText("assets/ui/phone/skins/sms/sms_record_voice_message_button.png");
    skinComposerTrailingSecondaryField.setPromptText("assets/ui/phone/skins/sms/sms_ping.png");
    skinStatusBackdropField.setPromptText("assets/ui/phone/skins/discord/d_status_backdrop.png");
    skinStatusIconField.setPromptText("assets/ui/phone/skins/discord/d_status_online.png");
    skinFloatingActionField.setPromptText("assets/ui/phone/skins/discord/d_jump_button.png");
    bubbleIncomingImageField.setPromptText("assets/ui/phone/skins/sms/speech bubbles/sms_their_message_-_FULL.png");
    bubbleOutgoingImageField.setPromptText("assets/ui/phone/skins/sms/speech bubbles/sms_your_message_FULL.png");

    GridPane basicsGrid = formGrid();
    int basicsRow = 0;
    addLabeledRow(basicsGrid, basicsRow++, "Title", appTitleField);
    addLabeledRow(basicsGrid, basicsRow++, "Subtitle", appSubtitleField);
    addLabeledRow(basicsGrid, basicsRow++, "Home Mode", homeModeCombo);
    addLabeledRow(basicsGrid, basicsRow++, "Wallpaper", assetFieldRow(wallpaperField, WALLPAPER_IMPORT_DIR, "Wallpaper"));
    addLabeledRow(basicsGrid, basicsRow++, "Skin", skinIdField);

    GridPane paletteGrid = formGrid();
    int paletteRow = 0;
    addLabeledRow(paletteGrid, paletteRow++, "Accent", accentField);
    addLabeledRow(paletteGrid, paletteRow++, "Surface", surfaceField);
    addLabeledRow(paletteGrid, paletteRow++, "Incoming Bubble", incomingBubbleField);
    addLabeledRow(paletteGrid, paletteRow++, "Outgoing Bubble", outgoingBubbleField);

    GridPane statusGrid = formGrid();
    int statusRow = 0;
    addLabeledRow(statusGrid, statusRow++, "Time", statusTimeField);
    addLabeledRow(statusGrid, statusRow++, "Mode", statusModeField);
    addLabeledRow(statusGrid, statusRow++, "Signal", statusSignalField);
    addLabeledRow(statusGrid, statusRow++, "Battery", statusBatteryField);

    GridPane shellGrid = formGrid();
    int shellRow = 0;
    addLabeledRow(shellGrid, shellRow++, "Skin Background", assetFieldRow(skinBackgroundField, SKIN_IMPORT_DIR, "Skin Background"));
    addLabeledRow(shellGrid, shellRow++, "Skin Top Bar", assetFieldRow(skinTopBarField, SKIN_IMPORT_DIR, "Skin Top Bar"));
    addLabeledRow(shellGrid, shellRow++, "Skin Bottom Bar", assetFieldRow(skinBottomBarField, SKIN_IMPORT_DIR, "Skin Bottom Bar"));
    addLabeledRow(shellGrid, shellRow++, "Skin Message Field", assetFieldRow(skinMessageField, SKIN_IMPORT_DIR, "Skin Message Field"));

    GridPane controlsGrid = formGrid();
    int controlsRow = 0;
    addLabeledRow(controlsGrid, controlsRow++, "Skin Nav Leading", assetFieldRow(skinNavLeadingField, SKIN_IMPORT_DIR, "Skin Nav Leading"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Nav Trailing 1", assetFieldRow(skinNavTrailingPrimaryField, SKIN_IMPORT_DIR, "Skin Nav Trailing Primary"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Nav Trailing 2", assetFieldRow(skinNavTrailingSecondaryField, SKIN_IMPORT_DIR, "Skin Nav Trailing Secondary"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Composer Leading", assetFieldRow(skinComposerLeadingField, SKIN_IMPORT_DIR, "Skin Composer Leading"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Composer Trailing 1", assetFieldRow(skinComposerTrailingPrimaryField, SKIN_IMPORT_DIR, "Skin Composer Trailing Primary"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Composer Trailing 2", assetFieldRow(skinComposerTrailingSecondaryField, SKIN_IMPORT_DIR, "Skin Composer Trailing Secondary"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Status Backdrop", assetFieldRow(skinStatusBackdropField, SKIN_IMPORT_DIR, "Skin Status Backdrop"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Status Icon", assetFieldRow(skinStatusIconField, SKIN_IMPORT_DIR, "Skin Status Icon"));
    addLabeledRow(controlsGrid, controlsRow++, "Skin Floating Action", assetFieldRow(skinFloatingActionField, SKIN_IMPORT_DIR, "Skin Floating Action"));

    GridPane bubbleGrid = formGrid();
    int bubbleRow = 0;
    addLabeledRow(bubbleGrid, bubbleRow++, "Incoming Bubble Image", assetFieldRow(bubbleIncomingImageField, SKIN_IMPORT_DIR, "Incoming Bubble Image"));
    addLabeledRow(bubbleGrid, bubbleRow++, "Outgoing Bubble Image", assetFieldRow(bubbleOutgoingImageField, SKIN_IMPORT_DIR, "Outgoing Bubble Image"));

    FlowPane sectionsGrid = new FlowPane(12, 12);
    sectionsGrid.getStyleClass().add("phone-tool-section-grid");
    sectionsGrid.setPrefWrapLength(960);
    sectionsGrid.getChildren().addAll(
        sectionCard("Basics", "Identity, wallpaper, and active phone skin.", basicsGrid),
        sectionCard("Palette", "Override key accent and bubble colors.", paletteGrid),
        sectionCard("Status Bar", "Semantic status text shown in home, chat, and call views.", statusGrid),
        sectionCard("Shell Assets", "Frame, top bar, bottom bar, and message field artwork.", shellGrid),
        sectionCard("Controls + Status", "Navigation, composer, and overlay icons used by the renderer.", controlsGrid),
        sectionCard("Bubble Images", "Optional image-based incoming and outgoing message bubbles.", bubbleGrid));

    VBox root = new VBox(10, copy, sectionsGrid);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("App", scroll);
  }

  private Tab buildContactsTab() {
    contactList.getStyleClass().add("phone-tool-list");
    contactList.setPlaceholder(emptyState("No contacts yet", "Add contacts here or create them by referencing new senders in messages."));
    contactCountLabel.getStyleClass().add("phone-tool-list-meta");

    contactNameField.setPromptText("Display name");
    contactAvatarField.setPromptText("assets/phone/contacts/lily.png");
    contactColorField.setPromptText("#f5a97f");

    Label contactTitle = new Label("Selected Contact");
    contactTitle.getStyleClass().add("phone-tool-section-title");
    Label detailCopy = new Label("Edit the selected contact's display name, avatar, accent color, and sender role.");
    detailCopy.getStyleClass().add("phone-tool-help");
    detailCopy.setWrapText(true);

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", contactIdField);
    addLabeledRow(grid, row++, "Name", contactNameField);
    addLabeledRow(grid, row++, "Avatar", assetFieldRow(contactAvatarField, CONTACT_IMPORT_DIR, "Contact Avatar"));
    addLabeledRow(grid, row++, "Color", contactColorField);
    grid.add(contactSelfCheck, 1, row);

    contactForm.getChildren().setAll(contactTitle, detailCopy, grid);
    contactForm.getStyleClass().add("phone-tool-card");
    contactForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Contact", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addContact());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedContact());

    HBox actions = new HBox(8, addButton, removeButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox listHeader = new HBox(8, sectionLabel("Contacts"), createSpacer(), contactCountLabel);
    listHeader.setAlignment(Pos.CENTER_LEFT);

    VBox listCard = new VBox(8, listHeader, contactFilterField, actions, contactList);
    listCard.getStyleClass().add("phone-tool-card");
    VBox.setVgrow(contactList, Priority.ALWAYS);

    VBox root = new VBox(10, listCard, contactForm);
    VBox.setVgrow(contactList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Contacts", scroll);
  }

  private Tab buildChatsTab() {
    chatList.getStyleClass().add("phone-tool-list");
    chatList.setPlaceholder(emptyState("No threads yet", "Create a thread before adding messages."));
    chatCountLabel.getStyleClass().add("phone-tool-list-meta");

    chatTitleField.setPromptText("Lily");
    chatParticipantsField.setPromptText("mc,lily");
    chatIconField.setPromptText("assets/phone/chats/lily.png");

    Label chatTitle = new Label("Selected Thread");
    chatTitle.getStyleClass().add("phone-tool-section-title");
    Label detailCopy = new Label("Define thread title, participants, unread state, and the icon shown on the home list.");
    detailCopy.getStyleClass().add("phone-tool-help");
    detailCopy.setWrapText(true);

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", chatIdField);
    addLabeledRow(grid, row++, "Title", chatTitleField);
    addLabeledRow(grid, row++, "Participants", chatParticipantsField);
    addLabeledRow(grid, row++, "Icon", assetFieldRow(chatIconField, CHAT_IMPORT_DIR, "Chat Icon"));
    grid.add(chatUnreadCheck, 1, row);

    chatForm.getChildren().setAll(chatTitle, detailCopy, grid);
    chatForm.getStyleClass().add("phone-tool-card");
    chatForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Thread", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addChat());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedChat());
    Button previewThreadButton = smallButton("Preview Selected Thread", CssIcon.speech("#f5c46b"));
    previewThreadButton.setOnAction(e -> {
      previewMode = PreviewMode.CHAT;
      refreshPreview();
      persistUiState();
    });

    HBox actions = new HBox(8, addButton, removeButton, previewThreadButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox listHeader = new HBox(8, sectionLabel("Threads"), createSpacer(), chatCountLabel);
    listHeader.setAlignment(Pos.CENTER_LEFT);

    VBox listCard = new VBox(8, listHeader, chatFilterField, actions, chatList);
    listCard.getStyleClass().add("phone-tool-card");
    VBox.setVgrow(chatList, Priority.ALWAYS);

    VBox root = new VBox(10, listCard, chatForm);
    VBox.setVgrow(chatList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Threads", scroll);
  }

  private Tab buildAppsTab() {
    appList.getStyleClass().add("phone-tool-list");
    appList.setPlaceholder(emptyState("No apps yet", "Add launchable apps for the home grid and route them to chats or calls."));
    appCountLabel.getStyleClass().add("phone-tool-list-meta");

    appNameField.setPromptText("Messages");
    appIconField.setPromptText("assets/ui/phone/apps/messages.png");
    appBadgeField.setPromptText("3");
    appAccentColorField.setPromptText("#78b7ff");
    appPageField.setPromptText("0");
    appTargetTypeCombo.getItems().setAll(VnPhoneData.AppTargetType.values());
    appTargetValueField.setPromptText("thread_or_call_id");

    Label title = new Label("Selected App");
    title.getStyleClass().add("phone-tool-section-title");
    Label detailCopy = new Label("Define the home-grid app icon, badge, page grouping, and where the app launches.");
    detailCopy.getStyleClass().add("phone-tool-help");
    detailCopy.setWrapText(true);

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", appIdField);
    addLabeledRow(grid, row++, "Title", appNameField);
    addLabeledRow(grid, row++, "Icon", assetFieldRow(appIconField, SKIN_IMPORT_DIR, "App Icon"));
    addLabeledRow(grid, row++, "Badge", appBadgeField);
    addLabeledRow(grid, row++, "Accent", appAccentColorField);
    addLabeledRow(grid, row++, "Page", appPageField);
    addLabeledRow(grid, row++, "Target", appTargetTypeCombo);
    addLabeledRow(grid, row++, "Target Value", appTargetValueField);

    appForm.getChildren().setAll(title, detailCopy, grid);
    appForm.getStyleClass().add("phone-tool-card");
    appForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add App", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addApp());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedApp());
    Button previewHomeGridButton = smallButton("Preview Home Grid", CssIcon.grid("#9cc7ff"));
    previewHomeGridButton.setOnAction(e -> {
      previewMode = PreviewMode.HOME;
      workingData.setHomeMode(VnPhoneData.HomeMode.APPS);
      refreshPreview();
      persistUiState();
    });

    HBox actions = new HBox(8, addButton, removeButton, previewHomeGridButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox listHeader = new HBox(8, sectionLabel("Apps"), createSpacer(), appCountLabel);
    listHeader.setAlignment(Pos.CENTER_LEFT);

    VBox listCard = new VBox(8, listHeader, appFilterField, actions, appList);
    listCard.getStyleClass().add("phone-tool-card");
    VBox.setVgrow(appList, Priority.ALWAYS);

    VBox root = new VBox(10, listCard, appForm);
    VBox.setVgrow(appList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Apps", scroll);
  }

  private Tab buildMessagesTab() {
    messageList.getStyleClass().add("phone-tool-list");
    messageList.setPlaceholder(emptyState("No messages", "Select or create a thread, then add its messages here."));
    messageCountLabel.getStyleClass().add("phone-tool-list-meta");

    messageTypeCombo.getItems().setAll(VnPhoneData.MessageType.values());
    messageSenderField.setPromptText("mc");
    messageTimeField.setPromptText("08:15");
    messageAssetField.setPromptText("assets/phone/messages/photo.png");
    messageCaptionField.setPromptText("Caption");
    messageDurationField.setPromptText("0:14");
    messageOptionsField.setPromptText("Option A | Option B");
    messageTextArea.setPromptText("Message text");
    messageTextArea.setPrefRowCount(4);
    messageTextArea.setWrapText(true);

    Label messageTitle = new Label("Selected Message");
    messageTitle.getStyleClass().add("phone-tool-section-title");
    messageIdLabel.getStyleClass().add("phone-tool-path");
    Label detailCopy = new Label("Edit sender, timestamp, and body text for the selected message.");
    detailCopy.getStyleClass().add("phone-tool-help");
    detailCopy.setWrapText(true);

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "Message", messageIdLabel);
    addLabeledRow(grid, row++, "Type", messageTypeCombo);
    addLabeledRow(grid, row++, "Sender", messageSenderField);
    addLabeledRow(grid, row++, "Time", messageTimeField);
    addLabeledRow(grid, row++, "Asset", assetFieldRow(messageAssetField, CHAT_IMPORT_DIR, "Message Asset"));
    addLabeledRow(grid, row++, "Caption", messageCaptionField);
    addLabeledRow(grid, row++, "Duration", messageDurationField);
    addLabeledRow(grid, row++, "Options", messageOptionsField);
    addLabeledRow(grid, row++, "Text", messageTextArea);

    messageForm.getChildren().setAll(messageTitle, detailCopy, grid);
    messageForm.getStyleClass().add("phone-tool-card");
    messageForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Message", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addMessage());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedMessage());
    Button previewThreadButton = smallButton("Preview Thread", CssIcon.speech("#f5c46b"));
    previewThreadButton.setOnAction(e -> {
      previewMode = PreviewMode.CHAT;
      refreshPreview();
      persistUiState();
    });

    HBox actions = new HBox(8, addButton, removeButton, previewThreadButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox listHeader = new HBox(8, sectionLabel("Messages"), createSpacer(), messageCountLabel);
    listHeader.setAlignment(Pos.CENTER_LEFT);

    VBox listCard = new VBox(8, listHeader, messageFilterField, actions, messageList);
    listCard.getStyleClass().add("phone-tool-card");
    VBox.setVgrow(messageList, Priority.ALWAYS);

    VBox root = new VBox(10, listCard, messageForm);
    VBox.setVgrow(messageList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Messages", scroll);
  }

  private Tab buildCallsTab() {
    callList.getStyleClass().add("phone-tool-list");
    callList.setPlaceholder(emptyState("No calls yet", "Create voice or video call surfaces that can be opened from apps or previewed directly."));
    callCountLabel.getStyleClass().add("phone-tool-list-meta");

    callTitleField.setPromptText("Lily");
    callSubtitleField.setPromptText("LostVarnacola");
    callParticipantField.setPromptText("lily");
    callAvatarField.setPromptText("assets/phone/contacts/lily.png");
    callStatusField.setPromptText("Calling...");

    Label title = new Label("Selected Call");
    title.getStyleClass().add("phone-tool-section-title");
    Label detailCopy = new Label("Edit the shared call surface used by the phone runtime and preview.");
    detailCopy.getStyleClass().add("phone-tool-help");
    detailCopy.setWrapText(true);

    GridPane grid = formGrid();
    int row = 0;
    addLabeledRow(grid, row++, "ID", callIdField);
    addLabeledRow(grid, row++, "Title", callTitleField);
    addLabeledRow(grid, row++, "Subtitle", callSubtitleField);
    addLabeledRow(grid, row++, "Participant", callParticipantField);
    addLabeledRow(grid, row++, "Avatar", assetFieldRow(callAvatarField, CONTACT_IMPORT_DIR, "Call Avatar"));
    addLabeledRow(grid, row++, "Status", callStatusField);
    grid.add(callVideoCheck, 1, row);

    callForm.getChildren().setAll(title, detailCopy, grid);
    callForm.getStyleClass().add("phone-tool-card");
    callForm.setPadding(new Insets(8));

    Button addButton = smallButton("Add Call", CssIcon.plus("#9ed67a"));
    addButton.setOnAction(e -> addCall());
    Button removeButton = smallButton("Remove", CssIcon.minus("#f38ba8"));
    removeButton.setOnAction(e -> removeSelectedCall());
    Button previewButton = smallButton("Preview Call", CssIcon.play("#f38ba8"));
    previewButton.setOnAction(e -> {
      previewMode = PreviewMode.CALL;
      refreshPreview();
      persistUiState();
    });

    HBox actions = new HBox(8, addButton, removeButton, previewButton);
    actions.setAlignment(Pos.CENTER_LEFT);

    HBox listHeader = new HBox(8, sectionLabel("Calls"), createSpacer(), callCountLabel);
    listHeader.setAlignment(Pos.CENTER_LEFT);

    VBox listCard = new VBox(8, listHeader, callFilterField, actions, callList);
    listCard.getStyleClass().add("phone-tool-card");
    VBox.setVgrow(callList, Priority.ALWAYS);

    VBox root = new VBox(10, listCard, callForm);
    VBox.setVgrow(callList, Priority.ALWAYS);
    root.setPadding(new Insets(8));

    ScrollPane scroll = cardScroll(root);
    return new Tab("Calls", scroll);
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

    appList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        VnPhoneData.PhoneApp app = workingData.getApp(item);
        if (app == null) {
          setText(item);
          return;
        }
        String title = firstNonBlank(app.getTitle(), app.getId());
        String badge = firstNonBlank(app.getBadgeText(), "");
        String suffix = badge.isBlank() ? "" : "  [" + badge + "]";
        setText(title + "  (page " + app.getPage() + ", " + app.getTargetType().token() + ")" + suffix);
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
        String text = firstNonBlank(message.getPreviewText(), "");
        text = text.replace('\n', ' ');
        if (text.length() > 54) text = text.substring(0, 51) + "...";
        String prefix = message.getType() == VnPhoneData.MessageType.TEXT ? "" : "[" + message.getType().token() + "] ";
        setText(sender + (time.isBlank() ? "" : "  " + time) + "  " + prefix + text);
      }
    });

    callList.setCellFactory(list -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        VnPhoneData.Call call = workingData.getCall(item);
        if (call == null) {
          setText(item);
          return;
        }
        String title = firstNonBlank(call.getTitle(), displayNameFor(call.getParticipantId()), call.getId());
        String mode = call.isVideo() ? "video" : "voice";
        setText(title + "  (" + mode + ")");
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
    homeModeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setHomeMode(newValue);
      changed("Updated home mode.");
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
    statusTimeField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setStatusTimeText(trimToNull(newValue));
      changed("Updated status time.");
    });
    statusModeField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setStatusModeText(trimToNull(newValue));
      changed("Updated status mode.");
    });
    statusSignalField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setStatusSignalText(trimToNull(newValue));
      changed("Updated status signal.");
    });
    statusBatteryField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setStatusBatteryText(trimToNull(newValue));
      changed("Updated status battery.");
    });
    skinIdField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinId(trimToNull(newValue));
      changed("Updated phone skin id.");
    });
    skinBackgroundField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinBackgroundPath(normalizeRelativePath(newValue));
      changed("Updated skin background.");
    });
    skinTopBarField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinTopBarPath(normalizeRelativePath(newValue));
      changed("Updated skin top bar.");
    });
    skinBottomBarField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinBottomBarPath(normalizeRelativePath(newValue));
      changed("Updated skin bottom bar.");
    });
    skinMessageField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinMessageFieldPath(normalizeRelativePath(newValue));
      changed("Updated skin message field.");
    });
    skinNavLeadingField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinNavLeadingPath(normalizeRelativePath(newValue));
      changed("Updated skin nav leading icon.");
    });
    skinNavTrailingPrimaryField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinNavTrailingPrimaryPath(normalizeRelativePath(newValue));
      changed("Updated skin nav trailing primary icon.");
    });
    skinNavTrailingSecondaryField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinNavTrailingSecondaryPath(normalizeRelativePath(newValue));
      changed("Updated skin nav trailing secondary icon.");
    });
    skinComposerLeadingField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinComposerLeadingPath(normalizeRelativePath(newValue));
      changed("Updated skin composer leading icon.");
    });
    skinComposerTrailingPrimaryField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinComposerTrailingPrimaryPath(normalizeRelativePath(newValue));
      changed("Updated skin composer trailing primary icon.");
    });
    skinComposerTrailingSecondaryField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinComposerTrailingSecondaryPath(normalizeRelativePath(newValue));
      changed("Updated skin composer trailing secondary icon.");
    });
    skinStatusBackdropField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinStatusBackdropPath(normalizeRelativePath(newValue));
      changed("Updated skin status backdrop.");
    });
    skinStatusIconField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinStatusIconPath(normalizeRelativePath(newValue));
      changed("Updated skin status icon.");
    });
    skinFloatingActionField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setSkinFloatingActionPath(normalizeRelativePath(newValue));
      changed("Updated skin floating action.");
    });
    bubbleIncomingImageField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setIncomingBubbleImagePath(normalizeRelativePath(newValue));
      changed("Updated incoming bubble image.");
    });
    bubbleOutgoingImageField.textProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      workingData.setOutgoingBubbleImagePath(normalizeRelativePath(newValue));
      changed("Updated outgoing bubble image.");
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

    appList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedAppId = newValue;
      selectedAppId = coerceSelection(selectedAppId, appIds());
      fillAppEntryForm();
      persistUiState();
    });
    appNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setTitle(newValue), "Updated app title."));
    appIconField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setIconPath(normalizeRelativePath(newValue)), "Updated app icon."));
    appBadgeField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setBadgeText(trimToNull(newValue)), "Updated app badge."));
    appAccentColorField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setAccentColor(trimToNull(newValue)), "Updated app accent."));
    appPageField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setPage(parseIntSafe(newValue, app.getPage())), "Updated app page."));
    appTargetTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setTargetType(newValue), "Updated app target type."));
    appTargetValueField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedApp(app -> app.setTargetValue(trimToNull(newValue)), "Updated app target value."));

    messageList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedMessageId = newValue;
      fillMessageForm();
      persistUiState();
    });
    messageTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message type."));
    messageSenderField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message sender."));
    messageTimeField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message time."));
    messageAssetField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message asset."));
    messageCaptionField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message caption."));
    messageDurationField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message duration."));
    messageOptionsField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message options."));
    messageTextArea.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedMessageFromFields("Updated message text."));

    callList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
      if (applyingUi) return;
      selectedCallId = newValue;
      selectedCallId = coerceSelection(selectedCallId, callIds());
      fillCallForm();
      persistUiState();
    });
    callTitleField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setTitle(newValue), "Updated call title."));
    callSubtitleField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setSubtitle(newValue), "Updated call subtitle."));
    callParticipantField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setParticipantId(newValue), "Updated call participant."));
    callAvatarField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setAvatarPath(normalizeRelativePath(newValue)), "Updated call avatar."));
    callStatusField.textProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setStatusText(newValue), "Updated call status."));
    callVideoCheck.selectedProperty().addListener((obs, oldValue, newValue) -> updateSelectedCall(call -> call.setVideo(newValue), "Updated call mode."));

    contactFilterField.textProperty().addListener((obs, oldValue, newValue) -> refreshContactList());
    chatFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
      refreshChatList();
      fillChatForm();
      refreshMessageList();
      fillMessageForm();
      refreshPreview();
    });
    messageFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
      refreshMessageList();
      fillMessageForm();
    });
    appFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
      refreshAppList();
      fillAppEntryForm();
    });
    callFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
      refreshCallList();
      fillCallForm();
    });
  }

  private void installAssetDropTargets() {
    AssetPickerSupport.installAssetDrop(wallpaperField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinBackgroundField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinTopBarField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinBottomBarField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinMessageField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinNavLeadingField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinNavTrailingPrimaryField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinNavTrailingSecondaryField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinComposerLeadingField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinComposerTrailingPrimaryField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinComposerTrailingSecondaryField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinStatusBackdropField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinStatusIconField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(skinFloatingActionField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(bubbleIncomingImageField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(bubbleOutgoingImageField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(contactAvatarField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(chatIconField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(appIconField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(messageAssetField, this::toProjectRelativePath);
    AssetPickerSupport.installAssetDrop(callAvatarField, this::toProjectRelativePath);
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
    refreshAppList();
    fillAppEntryForm();
    refreshMessageList();
    fillMessageForm();
    refreshCallList();
    fillCallForm();
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
    selectedAppId = coerceSelection(persisted.getProperty("selection.app"), appIds());
    selectedCallId = coerceSelection(persisted.getProperty("selection.call"), callIds());
    previewMode = parsePreviewMode(persisted.getProperty("preview.mode"));
    if (selectedChatId == null && !chatIds().isEmpty()) {
      selectedChatId = chatIds().get(0);
    }
    if (selectedAppId == null && !appIds().isEmpty()) {
      selectedAppId = appIds().get(0);
    }
    if (selectedCallId == null && !callIds().isEmpty()) {
      selectedCallId = callIds().get(0);
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
      homeModeCombo.setValue(workingData.getHomeMode());
      wallpaperField.setText(firstNonBlank(workingData.getWallpaperPath(), ""));
      accentField.setText(firstNonBlank(workingData.getAccentColor(), ""));
      surfaceField.setText(firstNonBlank(workingData.getSurfaceColor(), ""));
      incomingBubbleField.setText(firstNonBlank(workingData.getIncomingBubbleColor(), ""));
      outgoingBubbleField.setText(firstNonBlank(workingData.getOutgoingBubbleColor(), ""));
      statusTimeField.setText(firstNonBlank(workingData.getStatusTimeText(), ""));
      statusModeField.setText(firstNonBlank(workingData.getStatusModeText(), ""));
      statusSignalField.setText(firstNonBlank(workingData.getStatusSignalText(), ""));
      statusBatteryField.setText(firstNonBlank(workingData.getStatusBatteryText(), ""));
      skinIdField.setText(firstNonBlank(workingData.getSkinId(), ""));
      skinBackgroundField.setText(firstNonBlank(workingData.getSkinBackgroundPath(), ""));
      skinTopBarField.setText(firstNonBlank(workingData.getSkinTopBarPath(), ""));
      skinBottomBarField.setText(firstNonBlank(workingData.getSkinBottomBarPath(), ""));
      skinMessageField.setText(firstNonBlank(workingData.getSkinMessageFieldPath(), ""));
      skinNavLeadingField.setText(firstNonBlank(workingData.getSkinNavLeadingPath(), ""));
      skinNavTrailingPrimaryField.setText(firstNonBlank(workingData.getSkinNavTrailingPrimaryPath(), ""));
      skinNavTrailingSecondaryField.setText(firstNonBlank(workingData.getSkinNavTrailingSecondaryPath(), ""));
      skinComposerLeadingField.setText(firstNonBlank(workingData.getSkinComposerLeadingPath(), ""));
      skinComposerTrailingPrimaryField.setText(firstNonBlank(workingData.getSkinComposerTrailingPrimaryPath(), ""));
      skinComposerTrailingSecondaryField.setText(firstNonBlank(workingData.getSkinComposerTrailingSecondaryPath(), ""));
      skinStatusBackdropField.setText(firstNonBlank(workingData.getSkinStatusBackdropPath(), ""));
      skinStatusIconField.setText(firstNonBlank(workingData.getSkinStatusIconPath(), ""));
      skinFloatingActionField.setText(firstNonBlank(workingData.getSkinFloatingActionPath(), ""));
      bubbleIncomingImageField.setText(firstNonBlank(workingData.getIncomingBubbleImagePath(), ""));
      bubbleOutgoingImageField.setText(firstNonBlank(workingData.getOutgoingBubbleImagePath(), ""));
    } finally {
      applyingUi = false;
    }
  }

  private void refreshContactList() {
    List<String> allIds = new ArrayList<>(workingData.getContacts().keySet());
    List<String> ids = filterContactIds(allIds);
    contactCountLabel.setText(countLabelText(ids.size(), allIds.size(), "contact"));
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
    List<String> allIds = chatIds();
    List<String> ids = filterChatIds(allIds);
    chatCountLabel.setText(countLabelText(ids.size(), allIds.size(), "thread"));
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

  private void refreshAppList() {
    List<String> allIds = appIds();
    List<String> ids = filterAppIds(allIds);
    appCountLabel.setText(countLabelText(ids.size(), allIds.size(), "app"));
    applyingUi = true;
    try {
      appList.setItems(FXCollections.observableArrayList(ids));
      selectedAppId = coerceSelection(selectedAppId, ids);
      if (selectedAppId != null) appList.getSelectionModel().select(selectedAppId);
      else appList.getSelectionModel().clearSelection();
    } finally {
      applyingUi = false;
    }
  }

  private void fillAppEntryForm() {
    VnPhoneData.PhoneApp app = selectedApp();
    applyingUi = true;
    try {
      appIdField.setText(app == null ? "" : app.getId());
      appNameField.setText(app == null ? "" : firstNonBlank(app.getTitle(), ""));
      appIconField.setText(app == null ? "" : firstNonBlank(app.getIconPath(), ""));
      appBadgeField.setText(app == null ? "" : firstNonBlank(app.getBadgeText(), ""));
      appAccentColorField.setText(app == null ? "" : firstNonBlank(app.getAccentColor(), ""));
      appPageField.setText(app == null ? "" : Integer.toString(app.getPage()));
      appTargetTypeCombo.setValue(app == null ? null : app.getTargetType());
      appTargetValueField.setText(app == null ? "" : firstNonBlank(app.getTargetValue(), ""));
    } finally {
      applyingUi = false;
    }
  }

  private void refreshMessageList() {
    List<String> allIds = messageIdsFor(selectedChatId);
    List<String> ids = filterMessageIds(allIds);
    messageCountLabel.setText(countLabelText(ids.size(), allIds.size(), "message"));
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
      messageTypeCombo.setValue(message == null ? VnPhoneData.MessageType.TEXT : message.getType());
      messageSenderField.setText(message == null ? "" : firstNonBlank(message.getSenderId(), ""));
      messageTimeField.setText(message == null ? "" : firstNonBlank(message.getTimeText(), ""));
      messageAssetField.setText(message == null ? "" : firstNonBlank(message.getAssetPath(), ""));
      messageCaptionField.setText(message == null ? "" : firstNonBlank(message.getCaption(), ""));
      messageDurationField.setText(message == null ? "" : firstNonBlank(message.getDurationText(), ""));
      messageOptionsField.setText(message == null ? "" : String.join(" | ", message.getOptions()));
      messageTextArea.setText(message == null ? "" : firstNonBlank(message.getText(), ""));
    } finally {
      applyingUi = false;
    }
  }

  private void refreshCallList() {
    List<String> allIds = callIds();
    List<String> ids = filterCallIds(allIds);
    callCountLabel.setText(countLabelText(ids.size(), allIds.size(), "call"));
    applyingUi = true;
    try {
      callList.setItems(FXCollections.observableArrayList(ids));
      selectedCallId = coerceSelection(selectedCallId, ids);
      if (selectedCallId != null) callList.getSelectionModel().select(selectedCallId);
      else callList.getSelectionModel().clearSelection();
    } finally {
      applyingUi = false;
    }
  }

  private void fillCallForm() {
    VnPhoneData.Call call = selectedCall();
    applyingUi = true;
    try {
      callIdField.setText(call == null ? "" : call.getId());
      callTitleField.setText(call == null ? "" : firstNonBlank(call.getTitle(), ""));
      callSubtitleField.setText(call == null ? "" : firstNonBlank(call.getSubtitle(), ""));
      callParticipantField.setText(call == null ? "" : firstNonBlank(call.getParticipantId(), ""));
      callAvatarField.setText(call == null ? "" : firstNonBlank(call.getAvatarPath(), ""));
      callStatusField.setText(call == null ? "" : firstNonBlank(call.getStatusText(), ""));
      callVideoCheck.setSelected(call != null && call.isVideo());
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

  private void updateSelectedApp(Consumer<VnPhoneData.PhoneApp> mutator, String message) {
    if (applyingUi || mutator == null) return;
    VnPhoneData.PhoneApp app = selectedApp();
    if (app == null) return;
    mutator.accept(app);
    changed(message);
    refreshAppList();
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
    String asset = normalizeRelativePath(messageAssetField.getText());
    String caption = trimToNull(messageCaptionField.getText());
    String duration = trimToNull(messageDurationField.getText());
    List<String> options = parsePipeList(messageOptionsField.getText());
    VnPhoneData.MessageType type = messageTypeCombo.getValue() == null ? VnPhoneData.MessageType.TEXT : messageTypeCombo.getValue();
    if (!senderId.isBlank()) {
      workingData.getOrCreateContact(senderId);
    }

    int index = messageIndex(chat, original.getId());
    if (index < 0) return;
    chat.getMessages().set(index, new VnPhoneData.Message(
        original.getId(),
        senderId.isBlank() ? null : senderId,
        text,
        time,
        type,
        trimToNull(asset),
        caption,
        duration,
        options));
    changed(message);
    refreshContactList();
    refreshChatList();
    refreshMessageList();
    refreshPreview();
  }

  private void updateSelectedCall(Consumer<VnPhoneData.Call> mutator, String message) {
    if (applyingUi || mutator == null) return;
    VnPhoneData.Call call = selectedCall();
    if (call == null) return;
    mutator.accept(call);
    if (call.getParticipantId() != null && !call.getParticipantId().isBlank()) {
      workingData.getOrCreateContact(call.getParticipantId());
    }
    changed(message);
    refreshContactList();
    refreshCallList();
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
    previewMode = PreviewMode.CHAT;
    changed("Added thread " + id + ".");
    refreshChatList();
    fillChatForm();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
  }

  private void addApp() {
    String id = promptForId("Add App", "App ID", nextId("app", new LinkedHashSet<>(appIds())));
    if (id == null) return;
    if (workingData.getApps().containsKey(id)) {
      status("App already exists: " + id);
      return;
    }
    VnPhoneData.PhoneApp app = workingData.getOrCreateApp(id);
    app.setTitle(id);
    selectedAppId = id;
    workingData.setHomeMode(VnPhoneData.HomeMode.APPS);
    previewMode = PreviewMode.HOME;
    changed("Added app " + id + ".");
    refreshAppList();
    fillAppEntryForm();
    fillAppForm();
    refreshPreview();
  }

  private void removeSelectedApp() {
    VnPhoneData.PhoneApp app = selectedApp();
    if (app == null) return;
    workingData.removeApp(app.getId());
    selectedAppId = null;
    changed("Removed app " + app.getId() + ".");
    refreshAppList();
    fillAppEntryForm();
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
    previewMode = PreviewMode.CHAT;
    changed("Added message " + messageId + ".");
    refreshContactList();
    refreshMessageList();
    fillMessageForm();
    refreshPreview();
  }

  private void addCall() {
    String id = promptForId("Add Call", "Call ID", nextId("call", new LinkedHashSet<>(callIds())));
    if (id == null) return;
    if (workingData.getCalls().containsKey(id)) {
      status("Call already exists: " + id);
      return;
    }
    VnPhoneData.Call call = workingData.getOrCreateCall(id);
    call.setTitle(id);
    selectedCallId = id;
    previewMode = PreviewMode.CALL;
    changed("Added call " + id + ".");
    refreshCallList();
    fillCallForm();
    refreshPreview();
  }

  private void removeSelectedCall() {
    VnPhoneData.Call call = selectedCall();
    if (call == null) return;
    workingData.removeCall(call.getId());
    selectedCallId = null;
    changed("Removed call " + call.getId() + ".");
    refreshCallList();
    fillCallForm();
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
      refreshAppList();
      fillAppEntryForm();
      refreshMessageList();
      fillMessageForm();
      refreshCallList();
      fillCallForm();
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
    if (disposed) return;
    phoneRenderer.setProjectRoot(projectRoot);
    previewHomeButton.getStyleClass().setAll(
        "phone-tool-button",
        "phone-tool-preview-toggle",
        previewMode == PreviewMode.HOME ? "is-active" : "is-idle");
    previewChatButton.getStyleClass().setAll(
        "phone-tool-button",
        "phone-tool-preview-toggle",
        previewMode == PreviewMode.CHAT ? "is-active" : "is-idle");
    previewCallButton.getStyleClass().setAll(
        "phone-tool-button",
        "phone-tool-preview-toggle",
        previewMode == PreviewMode.CALL ? "is-active" : "is-idle");
    previewChatButton.setDisable(selectedChatId == null);
    previewCallButton.setDisable(selectedCallId == null);

    String selectedChatTitle = selectedChatId == null
        ? "No thread selected"
        : firstNonBlank(displayTitleForChat(selectedChatId), selectedChatId);
    String selectedCallTitle = selectedCallId == null
        ? "No call selected"
        : firstNonBlank(displayTitleForCall(selectedCallId), selectedCallId);

    switch (previewMode) {
      case CHAT -> {
        previewSelectionLabel.setText(selectedChatId != null
            ? "Previewing thread: " + selectedChatTitle
            : "Previewing thread view. Select a thread to inspect its conversation surface.");
        previewMetricLabel.setText("Preview Thread");
      }
      case CALL -> {
        previewSelectionLabel.setText(selectedCallId != null
            ? "Previewing call: " + selectedCallTitle
            : "Previewing call view. Select a call surface to inspect it.");
        previewMetricLabel.setText("Preview Call");
      }
      case HOME -> {
        String homeCopy = workingData.getHomeMode() == VnPhoneData.HomeMode.APPS
            ? "Previewing home grid. Select or author apps to inspect the launcher surface."
            : "Previewing home list. Select a thread to inspect its conversation view.";
        previewSelectionLabel.setText(homeCopy);
        previewMetricLabel.setText("Preview Home");
      }
    }

    VnPhoneData previewData = copyOf(workingData);
    PhoneScene scene = switch (previewMode) {
      case CHAT -> selectedChatId != null
          ? new PhoneScene(null, previewData, ignored -> { }, selectedChatId)
          : new PhoneScene(null, previewData, ignored -> { });
      case CALL -> {
        PhoneScene callScene = new PhoneScene(null, previewData, ignored -> { });
        if (selectedCallId != null) callScene.openCall(selectedCallId);
        yield callScene;
      }
      case HOME -> new PhoneScene(null, previewData, ignored -> { });
    };
    phoneRenderer.setSceneModel(scene);
    phoneRenderer.refresh();
  }

  private void updateSummary() {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      summaryLabel.setText("Open a project to edit phone assets and configuration.");
      contactMetricLabel.setText("Contacts 0");
      threadMetricLabel.setText("Threads 0");
      messageMetricLabel.setText("Messages 0");
      previewMetricLabel.setText("Preview Disabled");
      previewSelectionLabel.setText("Preview unavailable until a project is open.");
      return;
    }
    int messageCount = 0;
    for (VnPhoneData.Chat chat : workingData.getChats().values()) {
      messageCount += chat.getMessages().size();
    }
    String fileLabel = activeConfigFile == null ? CONFIG_PATH : toProjectRelativePath(activeConfigFile);
    summaryLabel.setText("Editing " + fileLabel + ". Drag assets into fields, preview the home surface, chats, or calls, then save back to the shared runtime config.");
    contactMetricLabel.setText("Contacts " + workingData.getContacts().size());
    threadMetricLabel.setText("Threads " + workingData.getChats().size());
    messageMetricLabel.setText("Messages " + messageCount);
  }

  private void updateControlsDisabledState() {
    boolean hasProject = projectRoot != null && projectRoot.isDirectory();
    saveButton.setDisable(!hasProject);
    refreshButton.setDisable(!hasProject);
    importConfigButton.setDisable(!hasProject);
    openConfigButton.setDisable(!hasProject || activeConfigFile == null || !activeConfigFile.exists());
    contactForm.setDisable(selectedContact() == null);
    chatForm.setDisable(selectedChat() == null);
    appForm.setDisable(selectedApp() == null);
    messageForm.setDisable(selectedMessage(selectedMessageId) == null);
    callForm.setDisable(selectedCall() == null);
  }

  private void changed(String message) {
    if (disposed) return;
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
    if (disposed) return;
    statusLabel.setText(firstNonBlank(message, ""));
  }

  private void persistUiState() {
    if (disposed) return;
    if (projectRoot == null || !projectRoot.isDirectory()) return;
    if (selectedContactId != null) persisted.setProperty("selection.contact", selectedContactId);
    else persisted.remove("selection.contact");
    if (selectedChatId != null) persisted.setProperty("selection.chat", selectedChatId);
    else persisted.remove("selection.chat");
    if (selectedAppId != null) persisted.setProperty("selection.app", selectedAppId);
    else persisted.remove("selection.app");
    if (selectedMessageId != null) persisted.setProperty("selection.message", selectedMessageId);
    else persisted.remove("selection.message");
    if (selectedCallId != null) persisted.setProperty("selection.call", selectedCallId);
    else persisted.remove("selection.call");
    persisted.setProperty("preview.mode", previewMode.name().toLowerCase(Locale.ROOT));

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
    if (disposed) return;
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

  private VnPhoneData.PhoneApp selectedApp() {
    return selectedAppId == null ? null : workingData.getApp(selectedAppId);
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

  private List<String> appIds() {
    List<String> ids = new ArrayList<>();
    for (VnPhoneData.PhoneApp app : workingData.orderedApps()) {
      if (app != null && app.getId() != null) ids.add(app.getId());
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

  private VnPhoneData.Call selectedCall() {
    return selectedCallId == null ? null : workingData.getCall(selectedCallId);
  }

  private List<String> callIds() {
    List<String> ids = new ArrayList<>();
    for (VnPhoneData.Call call : workingData.orderedCalls()) {
      if (call != null && call.getId() != null) ids.add(call.getId());
    }
    return ids;
  }

  private List<String> filterContactIds(List<String> ids) {
    String query = normalizedFilter(contactFilterField);
    if (query.isBlank()) return ids;
    List<String> filtered = new ArrayList<>();
    for (String id : ids) {
      VnPhoneData.Contact contact = workingData.getContact(id);
      String haystack = (id + " " + firstNonBlank(contact == null ? null : contact.getDisplayName(), "")).toLowerCase(Locale.ROOT);
      if (haystack.contains(query)) filtered.add(id);
    }
    return filtered;
  }

  private List<String> filterChatIds(List<String> ids) {
    String query = normalizedFilter(chatFilterField);
    if (query.isBlank()) return ids;
    List<String> filtered = new ArrayList<>();
    for (String id : ids) {
      VnPhoneData.Chat chat = workingData.getChat(id);
      String participants = chat == null ? "" : String.join(" ", chat.getParticipants());
      String haystack = (id + " " + firstNonBlank(chat == null ? null : chat.getTitle(), "") + " " + participants)
          .toLowerCase(Locale.ROOT);
      if (haystack.contains(query)) filtered.add(id);
    }
    return filtered;
  }

  private List<String> filterAppIds(List<String> ids) {
    String query = normalizedFilter(appFilterField);
    if (query.isBlank()) return ids;
    List<String> filtered = new ArrayList<>();
    for (String id : ids) {
      VnPhoneData.PhoneApp app = workingData.getApp(id);
      String haystack = (id + " "
          + firstNonBlank(app == null ? null : app.getTitle(), "") + " "
          + firstNonBlank(app == null ? null : app.getTargetValue(), ""))
          .toLowerCase(Locale.ROOT);
      if (haystack.contains(query)) filtered.add(id);
    }
    return filtered;
  }

  private List<String> filterMessageIds(List<String> ids) {
    String query = normalizedFilter(messageFilterField);
    if (query.isBlank()) return ids;
    List<String> filtered = new ArrayList<>();
    for (String id : ids) {
      VnPhoneData.Message message = selectedMessage(id);
      String haystack = message == null
          ? id.toLowerCase(Locale.ROOT)
          : (id + " "
              + firstNonBlank(message.getSenderId(), "") + " "
              + firstNonBlank(message.getTimeText(), "") + " "
              + firstNonBlank(message.getText(), ""))
              .toLowerCase(Locale.ROOT);
      if (haystack.contains(query)) filtered.add(id);
    }
    return filtered;
  }

  private List<String> filterCallIds(List<String> ids) {
    String query = normalizedFilter(callFilterField);
    if (query.isBlank()) return ids;
    List<String> filtered = new ArrayList<>();
    for (String id : ids) {
      VnPhoneData.Call call = workingData.getCall(id);
      String haystack = (id + " "
          + firstNonBlank(call == null ? null : call.getTitle(), "") + " "
          + firstNonBlank(call == null ? null : call.getParticipantId(), "") + " "
          + firstNonBlank(call == null ? null : call.getSubtitle(), ""))
          .toLowerCase(Locale.ROOT);
      if (haystack.contains(query)) filtered.add(id);
    }
    return filtered;
  }

  private String displayTitleForChat(String chatId) {
    VnPhoneData.Chat chat = chatId == null ? null : workingData.getChat(chatId);
    return chat == null ? null : firstNonBlank(chat.getTitle(), workingData.defaultChatTitle(chat), chat.getId());
  }

  private String displayTitleForCall(String callId) {
    VnPhoneData.Call call = callId == null ? null : workingData.getCall(callId);
    return call == null ? null : firstNonBlank(call.getTitle(), displayNameFor(call.getParticipantId()), call.getId());
  }

  private static String countLabelText(int visibleCount, int totalCount, String noun) {
    String label = totalCount == 1 ? noun : noun + "s";
    if (visibleCount == totalCount) {
      return totalCount + " " + label;
    }
    return visibleCount + " of " + totalCount + " " + label;
  }

  private static String normalizedFilter(TextField field) {
    if (field == null || field.getText() == null) return "";
    return field.getText().trim().toLowerCase(Locale.ROOT);
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
    copy.setHomeMode(source.getHomeMode());
    copy.setWallpaperPath(source.getWallpaperPath());
    copy.setAccentColor(source.getAccentColor());
    copy.setSurfaceColor(source.getSurfaceColor());
    copy.setIncomingBubbleColor(source.getIncomingBubbleColor());
    copy.setOutgoingBubbleColor(source.getOutgoingBubbleColor());
    copy.setStatusTimeText(source.getStatusTimeText());
    copy.setStatusModeText(source.getStatusModeText());
    copy.setStatusSignalText(source.getStatusSignalText());
    copy.setStatusBatteryText(source.getStatusBatteryText());
    copy.setSkinId(source.getSkinId());
    copy.setSkinBackgroundPath(source.getSkinBackgroundPath());
    copy.setSkinTopBarPath(source.getSkinTopBarPath());
    copy.setSkinBottomBarPath(source.getSkinBottomBarPath());
    copy.setSkinMessageFieldPath(source.getSkinMessageFieldPath());
    copy.setSkinNavLeadingPath(source.getSkinNavLeadingPath());
    copy.setSkinNavTrailingPrimaryPath(source.getSkinNavTrailingPrimaryPath());
    copy.setSkinNavTrailingSecondaryPath(source.getSkinNavTrailingSecondaryPath());
    copy.setSkinComposerLeadingPath(source.getSkinComposerLeadingPath());
    copy.setSkinComposerTrailingPrimaryPath(source.getSkinComposerTrailingPrimaryPath());
    copy.setSkinComposerTrailingSecondaryPath(source.getSkinComposerTrailingSecondaryPath());
    copy.setSkinStatusBackdropPath(source.getSkinStatusBackdropPath());
    copy.setSkinStatusIconPath(source.getSkinStatusIconPath());
    copy.setSkinFloatingActionPath(source.getSkinFloatingActionPath());
    copy.setIncomingBubbleImagePath(source.getIncomingBubbleImagePath());
    copy.setOutgoingBubbleImagePath(source.getOutgoingBubbleImagePath());

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
      target.setComposerText(chat.getComposerText());
      target.setComposerHint(chat.getComposerHint());
      for (VnPhoneData.Message message : chat.getMessages()) {
        target.getMessages().add(new VnPhoneData.Message(
            message.getId(),
            message.getSenderId(),
            message.getText(),
            message.getTimeText(),
            message.getType(),
            message.getAssetPath(),
            message.getCaption(),
            message.getDurationText(),
            message.getOptions()));
      }
    }

    copy.setAppOrder(source.getAppOrder());
    for (VnPhoneData.PhoneApp app : source.orderedApps()) {
      VnPhoneData.PhoneApp target = copy.getOrCreateApp(app.getId());
      target.setTitle(app.getTitle());
      target.setIconPath(app.getIconPath());
      target.setBadgeText(app.getBadgeText());
      target.setAccentColor(app.getAccentColor());
      target.setPage(app.getPage());
      target.setTargetType(app.getTargetType());
      target.setTargetValue(app.getTargetValue());
    }

    copy.setCallOrder(source.getCallOrder());
    for (VnPhoneData.Call call : source.orderedCalls()) {
      VnPhoneData.Call target = copy.getOrCreateCall(call.getId());
      target.setTitle(call.getTitle());
      target.setSubtitle(call.getSubtitle());
      target.setParticipantId(call.getParticipantId());
      target.setAvatarPath(call.getAvatarPath());
      target.setStatusText(call.getStatusText());
      target.setVideo(call.isVideo());
      if (target.getParticipantId() != null && !target.getParticipantId().isBlank()) {
        copy.getOrCreateContact(target.getParticipantId());
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

  private static VBox sectionCard(String title, String copy, javafx.scene.Node content) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("phone-tool-section-card-title");
    Label copyLabel = new Label(copy);
    copyLabel.getStyleClass().add("phone-tool-section-card-copy");
    copyLabel.setWrapText(true);

    VBox box = new VBox(8, titleLabel, copyLabel, content);
    box.getStyleClass().addAll("phone-tool-card", "phone-tool-section-card");
    box.setPadding(new Insets(10));
    box.setPrefWidth(430);
    box.setMinWidth(320);
    return box;
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

  private static TextField filterField(String promptText) {
    TextField field = new TextField();
    field.setPromptText(promptText);
    field.getStyleClass().add("phone-tool-filter-field");
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

  private static Label metricChip(String text) {
    return new Label(text);
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("phone-tool-section-title");
    return label;
  }

  private static Region createSpacer() {
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    return spacer;
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

  private static int parseIntSafe(String raw, int fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static List<String> parsePipeList(String raw) {
    List<String> values = new ArrayList<>();
    if (raw == null || raw.isBlank()) return values;
    for (String token : raw.split("\\|")) {
      String normalized = trimToNull(token);
      if (normalized != null) values.add(normalized);
    }
    return values;
  }

  private static PreviewMode parsePreviewMode(String raw) {
    if (raw == null || raw.isBlank()) return PreviewMode.HOME;
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "chat", "thread" -> PreviewMode.CHAT;
      case "call" -> PreviewMode.CALL;
      default -> PreviewMode.HOME;
    };
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

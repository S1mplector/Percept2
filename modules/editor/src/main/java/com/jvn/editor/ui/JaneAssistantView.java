package com.jvn.editor.ui;

import com.jvn.core.generalhelp.HelpAgentVote;
import com.jvn.core.generalhelp.HelpArticle;
import com.jvn.core.generalhelp.GeminiChatModel;
import com.jvn.core.generalhelp.JaneAssistant;
import com.jvn.core.generalhelp.JaneChatResponse;
import com.jvn.core.generalhelp.JaneTrainingCorpus;
import com.jvn.core.generalhelp.TagiGeneralHelpSystem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Sidebar chatbot for Jane, JVN's local assistant. */
public class JaneAssistantView extends BorderPane {
  private static final Pattern HEADING_LINE = Pattern.compile("^#\\s+(.+)$");
  private static final int SUMMARY_LIMIT = 220;
  private static final int ANSWER_COLLAPSE_LIMIT = 900;
  private static final int MIN_COLLAPSE_REMAINDER = 180;
  private static final int TYPEWRITER_CHARS_PER_TICK = 14;
  private static final String GEMINI_API_KEY_SETTING = "gemini.apiKey";
  private static final String GEMINI_API_KEY_SNAKE_SETTING = "gemini.api_key";
  private static final String GEMINI_API_KEY_LEGACY_SETTING = "apiKey";
  private static final String GEMINI_API_KEY_LEGACY_SNAKE_SETTING = "api_key";
  private static final String GEMINI_MODEL_SETTING = "gemini.model";
  private static final String GEMINI_MODEL_LEGACY_SETTING = "model";
  private static final String GEMINI_ENDPOINT_SETTING = "gemini.endpoint";
  private static final String GEMINI_ENDPOINT_LEGACY_SETTING = "endpoint";
  private static final String GEMINI_MAX_OUTPUT_TOKENS_SETTING = "gemini.maxOutputTokens";
  private static final String GEMINI_MAX_OUTPUT_TOKENS_LEGACY_SETTING = "maxOutputTokens";
  private static final String GEMINI_TEMPERATURE_SETTING = "gemini.temperature";
  private static final String GEMINI_TEMPERATURE_LEGACY_SETTING = "temperature";
  private static final String GEMINI_TIMEOUT_SECONDS_SETTING = "gemini.timeoutSeconds";
  private static final String GEMINI_TIMEOUT_SECONDS_LEGACY_SETTING = "timeoutSeconds";
  private static final String LEGACY_GEMINI_SETTINGS_RELATIVE_PATH = ".jvn/jane-gemini.properties";

  private final TagiGeneralHelpSystem generalHelp = new TagiGeneralHelpSystem();
  private final BorderPane contentPane = new BorderPane();
  private final StackPane contentStack = new StackPane();
  private final StackPane initializingOverlay = new StackPane();
  private final VBox initializingCard = new VBox(8);
  private final ProgressIndicator initializingSpinner = new ProgressIndicator();
  private final Label initializingTitleLabel = new Label("Initializing Jane");
  private final Label initializingBodyLabel = new Label("Preparing Jane and indexing workspace docs.");
  private final TextField askField = new TextField();
  private final VBox transcriptBox = new VBox(8);
  private final VBox evidenceBox = new VBox(8);
  private final ScrollPane transcriptScroll = new ScrollPane(transcriptBox);
  private final Label statusLabel = new Label("Indexing JVN docs...");
  private final Button askButton = new Button("Ask");
  private final Button refreshButton = new Button("Refresh");
  private final Button clearButton = new Button("Clear");
  private final Button sourcesButton = new Button("Sources");
  private final Button settingsButton = new Button();
  private final List<Timeline> textAnimations = new ArrayList<>();

  private File workspaceRoot;
  private File projectRoot;
  private Consumer<File> onOpenDoc;
  private JaneAssistant jane;
  private JaneChatResponse lastResponse;
  private VBox thinkingBubble;
  private Timeline thinkingAnimation;
  private boolean initializationScheduled;
  private boolean initialized;
  private boolean firstCorpusLoad = true;
  private boolean indexing;
  private boolean pendingRefresh;
  private Stage settingsStage;

  public JaneAssistantView() {
    getStyleClass().addAll("jane-assistant-root", "sidebar-tool-root");
    buildUi();
    scheduleInitialization();
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = normalizeDir(root);
    if (this.workspaceRoot != null) {
      System.setProperty("jvn.jane.workspaceRoot", this.workspaceRoot.getAbsolutePath());
    }
    if (initialized) {
      refreshModel();
      refreshCorpus();
    } else {
      scheduleInitialization();
    }
  }

  public void setProjectRoot(File root) {
    this.projectRoot = normalizeDir(root);
    if (initialized) {
      refreshCorpus();
    } else {
      scheduleInitialization();
    }
  }

  public void setOnOpenDoc(Consumer<File> onOpenDoc) {
    this.onOpenDoc = onOpenDoc;
  }

  public void refreshCorpus() {
    if (jane == null) {
      scheduleInitialization();
      return;
    }
    if (indexing) {
      pendingRefresh = true;
      return;
    }
    indexing = true;
    pendingRefresh = false;
    setInputDisabled(true);
    statusLabel.setText("Indexing JVN docs...");
    if (firstCorpusLoad) {
      showInitializingOverlay(
          "Initializing Jane",
          "Preparing Jane's grounded help corpus. The panel will be ready in a moment.");
    }
    Task<List<HelpArticle>> task = new Task<>() {
      @Override
      protected List<HelpArticle> call() {
        return JaneTrainingCorpus.train(indexDocs());
      }
    };
    task.setOnSucceeded(e -> {
      List<HelpArticle> articles = task.getValue() == null ? List.of() : task.getValue();
      generalHelp.setArticles(articles);
      indexing = false;
      if (pendingRefresh) {
        refreshCorpus();
        return;
      }
      firstCorpusLoad = false;
      hideInitializingOverlay();
      setInputDisabled(false);
      statusLabel.setText("Ready. Indexed " + articles.size() + " training articles.");
      if (transcriptBox.getChildren().isEmpty()) {
        addAssistantBubble("I'm Jane. Ask me about JVN languages, workflows, assets, editor tools, packaging, diagnostics, or engine internals.");
      }
    });
    task.setOnFailed(e -> {
      indexing = false;
      if (pendingRefresh) {
        refreshCorpus();
        return;
      }
      firstCorpusLoad = false;
      hideInitializingOverlay();
      setInputDisabled(false);
      statusLabel.setText("Index failed. Jane will use the built-in corpus.");
    });
    Thread thread = new Thread(task, "jane-corpus-index");
    thread.setDaemon(true);
    thread.start();
  }

  private void refreshModel() {
    if (jane == null) return;
    jane.reloadConfiguredModel();
  }

  private void scheduleInitialization() {
    if (initialized || initializationScheduled) return;
    initializationScheduled = true;
    setInputDisabled(true);
    showInitializingOverlay(
        "Initializing Jane",
        "Loading Jane's assistant shell and preparing indexed context.");
    Platform.runLater(this::initializeJane);
  }

  private void initializeJane() {
    initializationScheduled = false;
    if (initialized) return;
    try {
      jane = new JaneAssistant(generalHelp);
      initialized = true;
      refreshModel();
      refreshCorpus();
    } catch (RuntimeException ex) {
      initialized = true;
      firstCorpusLoad = false;
      hideInitializingOverlay();
      setInputDisabled(false);
      statusLabel.setText("Jane initialization failed: " + safeMessage(ex));
      addAssistantBubble("Jane could not initialize. Check Jane's Gemini settings and refresh the panel.");
    }
  }

  private void buildUi() {
    Label title = new Label("Jane");
    title.getStyleClass().addAll("sidebar-tool-title", "jane-title");
    Label subtitle = new Label("JVN assistant");
    subtitle.getStyleClass().addAll("sidebar-tool-subtitle", "jane-subtitle");
    Region icon = CssIcon.speech("#ffd166");
    icon.getStyleClass().add("jane-header-icon");
    VBox titleBox = new VBox(1, title, subtitle);
    HBox.setHgrow(titleBox, Priority.ALWAYS);
    HBox headerRow = new HBox(10, icon, titleBox);
    headerRow.setAlignment(Pos.CENTER_LEFT);

    statusLabel.getStyleClass().add("sidebar-tool-status");
    VBox header = new VBox(6, headerRow, statusLabel);
    header.getStyleClass().addAll("sidebar-tool-header", "jane-header");
    header.setPadding(new Insets(10));

    transcriptBox.getStyleClass().add("jane-transcript");
    transcriptBox.setFillWidth(true);
    transcriptScroll.setFitToWidth(true);
    transcriptScroll.getStyleClass().addAll("sidebar-tool-scroll", "jane-transcript-scroll");
    transcriptScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    transcriptScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    evidenceBox.getStyleClass().add("jane-evidence");
    evidenceBox.setPadding(new Insets(8, 10, 8, 10));
    evidenceBox.setVisible(false);
    evidenceBox.setManaged(false);

    askField.setPromptText("Ask Jane...");
    askField.getStyleClass().add("sidebar-tool-search-field");
    askField.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER) {
        askJane();
        e.consume();
      }
    });
    askButton.getStyleClass().add("sidebar-tool-btn");
    askButton.setTooltip(new Tooltip("Ask Jane"));
    askButton.setOnAction(e -> askJane());
    refreshButton.getStyleClass().add("sidebar-tool-btn");
    refreshButton.setTooltip(new Tooltip("Re-index JVN docs for Jane"));
    refreshButton.setOnAction(e -> refreshJane());
    clearButton.getStyleClass().add("sidebar-tool-btn");
    clearButton.setTooltip(new Tooltip("Clear Jane chat history"));
    clearButton.setOnAction(e -> clearChat());
    sourcesButton.getStyleClass().add("sidebar-tool-btn");
    sourcesButton.setTooltip(new Tooltip("Show or hide Jane's TAGI sources for the latest answer"));
    sourcesButton.setDisable(true);
    sourcesButton.setOnAction(e -> toggleSources());
    settingsButton.getStyleClass().addAll("sidebar-tool-btn", "jane-icon-button");
    settingsButton.setGraphic(CssIcon.settings("#d8b568"));
    settingsButton.setTooltip(new Tooltip("Configure Jane settings"));
    settingsButton.setOnAction(e -> openSettingsWindow());

    HBox inputRow = new HBox(6, askField, askButton);
    inputRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(askField, Priority.ALWAYS);
    HBox actions = new HBox(6, sourcesButton, refreshButton, clearButton, settingsButton);
    actions.setAlignment(Pos.CENTER_LEFT);
    VBox footer = new VBox(8, new Separator(), inputRow, actions);
    footer.getStyleClass().add("sidebar-tool-footer");
    footer.setPadding(new Insets(10));

    configureInitializingOverlay();

    VBox center = new VBox(8, transcriptScroll, evidenceBox);
    center.setPadding(new Insets(10));
    VBox.setVgrow(transcriptScroll, Priority.ALWAYS);
    contentPane.setTop(header);
    contentPane.setCenter(center);
    contentPane.setBottom(footer);
    contentStack.getChildren().addAll(contentPane, initializingOverlay);
    StackPane.setAlignment(initializingOverlay, Pos.CENTER);
    setCenter(contentStack);
  }

  private void configureInitializingOverlay() {
    initializingOverlay.getStyleClass().add("vcs-initializing-overlay");
    initializingOverlay.setVisible(false);
    initializingOverlay.setManaged(false);
    initializingOverlay.setPickOnBounds(true);
    initializingCard.getStyleClass().add("vcs-initializing-card");
    initializingCard.setAlignment(Pos.CENTER);
    initializingCard.prefWidthProperty().bind(initializingOverlay.widthProperty());
    initializingCard.maxWidthProperty().bind(initializingOverlay.widthProperty());
    initializingCard.prefHeightProperty().bind(initializingOverlay.heightProperty());
    initializingCard.maxHeightProperty().bind(initializingOverlay.heightProperty());
    initializingSpinner.getStyleClass().add("vcs-initializing-spinner");
    initializingSpinner.setMaxSize(36, 36);
    initializingTitleLabel.getStyleClass().add("vcs-initializing-title");
    initializingBodyLabel.getStyleClass().add("vcs-initializing-body");
    initializingBodyLabel.setWrapText(true);
    initializingBodyLabel.maxWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
        () -> Math.max(180.0, Math.min(640.0, initializingOverlay.getWidth() - 48.0)),
        initializingOverlay.widthProperty()));
    initializingCard.getChildren().addAll(initializingSpinner, initializingTitleLabel, initializingBodyLabel);
    initializingOverlay.getChildren().add(initializingCard);
    StackPane.setAlignment(initializingCard, Pos.CENTER);
  }

  private void showInitializingOverlay(String title, String body) {
    initializingTitleLabel.setText(title == null || title.isBlank() ? "Initializing Jane" : title);
    initializingBodyLabel.setText(body == null || body.isBlank()
        ? "Preparing Jane and indexing workspace docs."
        : body);
    initializingOverlay.setVisible(true);
    initializingOverlay.setManaged(true);
  }

  private void hideInitializingOverlay() {
    initializingOverlay.setVisible(false);
    initializingOverlay.setManaged(false);
  }

  private void askJane() {
    JaneAssistant activeJane = jane;
    if (activeJane == null) {
      scheduleInitialization();
      return;
    }
    String query = askField.getText();
    if (query == null || query.isBlank()) {
      askField.requestFocus();
      return;
    }
    askField.clear();
    addUserBubble(query.trim());
    setInputDisabled(true);
    statusLabel.setText("Jane is thinking...");
    showThinkingBubble();

    Task<JaneChatResponse> task = new Task<>() {
      @Override
      protected JaneChatResponse call() {
        return activeJane.ask(query);
      }
    };
    task.setOnSucceeded(e -> {
      JaneChatResponse response = task.getValue();
      hideThinkingBubble();
      setInputDisabled(false);
      statusLabel.setText("Ready. Indexed " + generalHelp.articles().size() + " training articles.");
      addAssistantBubbleAnimated(response == null ? "I could not produce a response." : response.answer());
      lastResponse = response;
      hideEvidence();
      sourcesButton.setDisable(!hasEvidence(response));
    });
    task.setOnFailed(e -> {
      hideThinkingBubble();
      setInputDisabled(false);
      statusLabel.setText("Jane response failed: " + safeMessage(task.getException()));
      addAssistantBubble("I hit a configured model error. Re-index docs or check Jane's Gemini settings.");
    });
    Thread thread = new Thread(task, "jane-chat");
    thread.setDaemon(true);
    thread.start();
  }

  private void clearChat() {
    if (jane != null) {
      jane.clearHistory();
    }
    lastResponse = null;
    hideThinkingBubble();
    stopTextAnimations();
    transcriptBox.getChildren().clear();
    hideEvidence();
    sourcesButton.setDisable(true);
    addAssistantBubble("Chat cleared. Ask me about JVN.");
    Platform.runLater(() -> askField.requestFocus());
  }

  private void refreshJane() {
    if (jane == null) {
      scheduleInitialization();
    } else {
      refreshModel();
      refreshCorpus();
    }
  }

  private void openSettingsWindow() {
    if (settingsStage != null && settingsStage.isShowing()) {
      settingsStage.toFront();
      settingsStage.requestFocus();
      return;
    }

    GeminiSettings settings = readGeminiSettings();
    PasswordField keyField = new PasswordField();
    keyField.setPromptText(settings.hasApiKey() ? "Saved key present" : "Google AI Studio API key");
    keyField.getStyleClass().add("editor-settings-text-field");
    keyField.setMaxWidth(Double.MAX_VALUE);

    TextField modelField = new TextField(settings.model());
    modelField.setPromptText(GeminiChatModel.defaultModel());
    modelField.getStyleClass().add("editor-settings-text-field");
    modelField.setMaxWidth(Double.MAX_VALUE);

    Label header = new Label("Jane Settings");
    header.getStyleClass().add("editor-settings-header");
    Label intro = new Label("Stored locally under " + GeminiChatModel.localSettingsRelativePath() + " for this workspace.");
    intro.setWrapText(true);
    intro.getStyleClass().add("editor-settings-copy");

    Label keyStateLabel = new Label(keyStorageText(settings));
    keyStateLabel.setWrapText(true);
    keyStateLabel.getStyleClass().add("editor-settings-copy");

    GridPane grid = settingsGrid(90);
    grid.addRow(0, settingsFieldLabel("API Key"), keyField);
    grid.addRow(1, settingsFieldLabel("Model"), modelField);

    VBox section = new VBox(10, keyStateLabel, grid);
    section.getStyleClass().add("editor-settings-section");

    Label dialogStatusLabel = new Label("Jane settings loaded.");
    dialogStatusLabel.getStyleClass().add("editor-settings-status");
    dialogStatusLabel.setMaxWidth(Double.MAX_VALUE);

    Button saveButton = settingsActionButton("Save");
    Button clearKeyButton = settingsActionButton("Clear Key");
    Button closeButton = settingsActionButton("Close");
    final boolean[] hasSavedKey = {settings.hasApiKey()};

    saveButton.setOnAction(e -> {
      String apiKey = trimmed(keyField.getText());
      String model = normalizedModelSetting(modelField.getText());
      if (!hasSavedKey[0] && apiKey.isBlank()) {
        dialogStatusLabel.setText("Enter a Gemini key before saving.");
        keyField.requestFocus();
        return;
      }
      try {
        saveGeminiSettings(apiKey, model, hasSavedKey[0]);
        keyField.clear();
        GeminiSettings updated = readGeminiSettings();
        hasSavedKey[0] = updated.hasApiKey();
        keyStateLabel.setText(keyStorageText(updated));
        modelField.setText(updated.model());
        dialogStatusLabel.setText("Jane settings saved.");
        reloadJaneAfterSettingsChange("Gemini settings saved. Jane will use them for new answers.");
      } catch (IOException ex) {
        dialogStatusLabel.setText("Failed to save Jane settings: " + safeMessage(ex));
      }
    });
    clearKeyButton.setOnAction(e -> {
      try {
        clearGeminiKey();
        keyField.clear();
        GeminiSettings updated = readGeminiSettings();
        hasSavedKey[0] = updated.hasApiKey();
        keyStateLabel.setText(keyStorageText(updated));
        dialogStatusLabel.setText("Local Gemini key cleared.");
        reloadJaneAfterSettingsChange("Local Gemini key cleared.");
      } catch (IOException ex) {
        dialogStatusLabel.setText("Failed to clear Gemini key: " + safeMessage(ex));
      }
    });
    closeButton.setOnAction(e -> settingsStage.close());

    HBox buttons = new HBox(8, saveButton, clearKeyButton, closeButton);
    buttons.getStyleClass().add("editor-settings-inline-row");

    VBox root = new VBox(12, header, intro, section, buttons, dialogStatusLabel);
    root.getStyleClass().addAll("editor-settings-view", "jane-settings-root");
    root.setPadding(new Insets(12));

    settingsStage = new Stage();
    Window owner = getScene() == null ? null : getScene().getWindow();
    if (owner != null) {
      settingsStage.initOwner(owner);
      settingsStage.initModality(Modality.WINDOW_MODAL);
    }
    settingsStage.setTitle("Jane Settings");
    Scene scene = new Scene(root, 460, 300);
    EditorTheme.apply(scene);
    settingsStage.setScene(scene);
    settingsStage.setMinWidth(420);
    settingsStage.setMinHeight(280);
    settingsStage.setOnCloseRequest(e -> settingsStage = null);
    settingsStage.show();
  }

  private Button settingsActionButton(String label) {
    Button button = new Button(label);
    button.getStyleClass().add("editor-settings-button");
    return button;
  }

  private void reloadJaneAfterSettingsChange(String status) {
    if (jane == null) {
      scheduleInitialization();
    } else {
      refreshModel();
    }
    statusLabel.setText(status);
  }

  private GeminiSettings readGeminiSettings() {
    Path path = janeSettingsPath();
    Properties props = new Properties();
    mergeProperties(props, readSettingsProperties(legacyGeminiSettingsPath()));
    mergeProperties(props, readSettingsProperties(path));
    String model = firstNonBlank(
        props.getProperty(GEMINI_MODEL_SETTING),
        props.getProperty(GEMINI_MODEL_LEGACY_SETTING),
        GeminiChatModel.defaultModel());
    return new GeminiSettings(path, !localApiKey(props).isBlank(), model);
  }

  private void saveGeminiSettings(String apiKey, String model, boolean keepExistingKeyWhenBlank) throws IOException {
    Path path = janeSettingsPath();
    Properties props = readSettingsProperties(path);
    Properties legacyProps = readSettingsProperties(legacyGeminiSettingsPath());
    String existingKey = firstNonBlank(localApiKey(props), localApiKey(legacyProps));

    copySettingIfMissing(props, legacyProps, GEMINI_ENDPOINT_SETTING, GEMINI_ENDPOINT_LEGACY_SETTING);
    copySettingIfMissing(props, legacyProps, GEMINI_MAX_OUTPUT_TOKENS_SETTING, GEMINI_MAX_OUTPUT_TOKENS_LEGACY_SETTING);
    copySettingIfMissing(props, legacyProps, GEMINI_TEMPERATURE_SETTING, GEMINI_TEMPERATURE_LEGACY_SETTING);
    copySettingIfMissing(props, legacyProps, GEMINI_TIMEOUT_SECONDS_SETTING, GEMINI_TIMEOUT_SECONDS_LEGACY_SETTING);

    String trimmedKey = trimmed(apiKey);
    if (!trimmedKey.isBlank()) {
      props.setProperty(GEMINI_API_KEY_SETTING, trimmedKey);
    } else if (keepExistingKeyWhenBlank && !existingKey.isBlank()) {
      props.setProperty(GEMINI_API_KEY_SETTING, existingKey);
    }
    props.setProperty(GEMINI_MODEL_SETTING, normalizedModelSetting(model));
    removeLegacyGeminiProperties(props);
    writeSettingsProperties(path, props);
    clearApiKeyInFile(legacyGeminiSettingsPath());
  }

  private void clearGeminiKey() throws IOException {
    clearApiKeyInFile(janeSettingsPath());
    clearApiKeyInFile(legacyGeminiSettingsPath());
  }

  private void clearApiKeyInFile(Path path) throws IOException {
    Properties props = readSettingsProperties(path);
    if (props.isEmpty()) return;
    props.remove(GEMINI_API_KEY_SETTING);
    props.remove(GEMINI_API_KEY_SNAKE_SETTING);
    props.remove(GEMINI_API_KEY_LEGACY_SETTING);
    props.remove(GEMINI_API_KEY_LEGACY_SNAKE_SETTING);
    if (props.isEmpty()) {
      Files.deleteIfExists(path);
    } else {
      writeSettingsProperties(path, props);
    }
  }

  private Path janeSettingsPath() {
    return settingsRootPath()
        .resolve(GeminiChatModel.localSettingsRelativePath())
        .toAbsolutePath()
        .normalize();
  }

  private Path legacyGeminiSettingsPath() {
    return settingsRootPath()
        .resolve(LEGACY_GEMINI_SETTINGS_RELATIVE_PATH)
        .toAbsolutePath()
        .normalize();
  }

  private Path settingsRootPath() {
    File root = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
    if (root != null) return root.toPath().toAbsolutePath().normalize();
    return Path.of(".").toAbsolutePath().normalize();
  }

  private Properties readSettingsProperties(Path path) {
    Properties props = new Properties();
    if (path == null || !Files.isRegularFile(path)) return props;
    try (var input = Files.newInputStream(path)) {
      props.load(input);
    } catch (IOException ignored) {
      // Saving the dialog will recreate the local Jane settings file if possible.
    }
    return props;
  }

  private void writeSettingsProperties(Path path, Properties props) throws IOException {
    if (path.getParent() != null) Files.createDirectories(path.getParent());
    try (var output = Files.newOutputStream(path)) {
      props.store(output, "Jane local settings");
    }
  }

  private static void mergeProperties(Properties target, Properties source) {
    if (target == null || source == null || source.isEmpty()) return;
    for (String name : source.stringPropertyNames()) {
      target.setProperty(name, source.getProperty(name));
    }
  }

  private static void copySettingIfMissing(Properties target, Properties source, String canonicalKey, String legacyKey) {
    if (!firstNonBlank(target.getProperty(canonicalKey), target.getProperty(legacyKey)).isBlank()) return;
    String value = firstNonBlank(source.getProperty(canonicalKey), source.getProperty(legacyKey));
    if (!value.isBlank()) target.setProperty(canonicalKey, value);
  }

  private static void removeLegacyGeminiProperties(Properties props) {
    props.remove(GEMINI_API_KEY_LEGACY_SETTING);
    props.remove(GEMINI_API_KEY_SNAKE_SETTING);
    props.remove(GEMINI_API_KEY_LEGACY_SNAKE_SETTING);
    props.remove(GEMINI_MODEL_LEGACY_SETTING);
    props.remove(GEMINI_ENDPOINT_LEGACY_SETTING);
    props.remove(GEMINI_MAX_OUTPUT_TOKENS_LEGACY_SETTING);
    props.remove(GEMINI_TEMPERATURE_LEGACY_SETTING);
    props.remove(GEMINI_TIMEOUT_SECONDS_LEGACY_SETTING);
  }

  private static String localApiKey(Properties props) {
    return firstNonBlank(
        props.getProperty(GEMINI_API_KEY_SETTING),
        props.getProperty(GEMINI_API_KEY_SNAKE_SETTING),
        props.getProperty(GEMINI_API_KEY_LEGACY_SETTING),
        props.getProperty(GEMINI_API_KEY_LEGACY_SNAKE_SETTING));
  }

  private static String keyStorageText(GeminiSettings settings) {
    return settings.hasApiKey()
        ? "Local Gemini key saved in " + GeminiChatModel.localSettingsRelativePath() + "."
        : "No local Gemini key saved yet.";
  }

  private static String normalizedModelSetting(String value) {
    String trimmed = trimmed(value);
    return trimmed.isBlank() ? GeminiChatModel.defaultModel() : trimmed;
  }

  private static GridPane settingsGrid(double labelWidth) {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    ColumnConstraints labelColumn = new ColumnConstraints();
    labelColumn.setMinWidth(labelWidth);
    ColumnConstraints fieldColumn = new ColumnConstraints();
    fieldColumn.setHgrow(Priority.ALWAYS);
    grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
    return grid;
  }

  private static Label settingsFieldLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("editor-settings-label");
    return label;
  }

  private static String trimmed(String value) {
    return value == null ? "" : value.trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private void toggleSources() {
    if (evidenceBox.isVisible()) {
      hideEvidence();
      return;
    }
    showEvidence(lastResponse);
    evidenceBox.setVisible(!evidenceBox.getChildren().isEmpty());
    evidenceBox.setManaged(evidenceBox.isVisible());
  }

  private void hideEvidence() {
    evidenceBox.getChildren().clear();
    evidenceBox.setVisible(false);
    evidenceBox.setManaged(false);
  }

  private static boolean hasEvidence(JaneChatResponse response) {
    if (response == null || response.grounding() == null) return false;
    return !response.grounding().recommendedArticles().isEmpty()
        || !response.grounding().votes().isEmpty();
  }

  private void showEvidence(JaneChatResponse response) {
    evidenceBox.getChildren().clear();
    if (response == null) return;
    if (!response.grounding().recommendedArticles().isEmpty()) {
      Label heading = new Label("Recommended Docs");
      heading.getStyleClass().add("jane-evidence-title");
      evidenceBox.getChildren().add(heading);
      response.grounding().recommendedArticles().stream()
          .limit(4)
          .forEach(article -> evidenceBox.getChildren().add(recommendationButton(article)));
    }
    if (!response.grounding().votes().isEmpty()) {
      Label heading = new Label("TAGI Votes");
      heading.getStyleClass().add("jane-evidence-title");
      evidenceBox.getChildren().add(heading);
      response.grounding().votes().stream()
          .limit(6)
          .forEach(vote -> evidenceBox.getChildren().add(voteRow(vote)));
    }
  }

  private Button recommendationButton(HelpArticle article) {
    String path = article.path().isBlank() ? article.id() : article.path();
    Button button = new Button(article.title());
    button.getStyleClass().add("jane-recommendation");
    button.setMaxWidth(Double.MAX_VALUE);
    button.setTooltip(new Tooltip(path));
    button.setOnAction(e -> openArticle(article));
    return button;
  }

  private HBox voteRow(HelpAgentVote vote) {
    Label agent = new Label(vote.agentName());
    agent.getStyleClass().add("jane-vote-agent");
    Label score = new Label(Math.round(vote.confidence() * 100.0) + "%");
    score.getStyleClass().add("jane-vote-score");
    Label title = new Label(vote.title());
    title.getStyleClass().add("jane-vote-title");
    title.setWrapText(true);
    HBox.setHgrow(title, Priority.ALWAYS);
    HBox row = new HBox(6, agent, score, title);
    row.getStyleClass().add("jane-vote-row");
    row.setAlignment(Pos.CENTER_LEFT);
    return row;
  }

  private void openArticle(HelpArticle article) {
    if (article == null || onOpenDoc == null) return;
    String target = !article.id().isBlank() ? article.id() : article.path();
    if (target == null || target.isBlank()) return;
    File file = new File(target);
    if (!file.isFile()) {
      File ws = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
      if (ws != null) file = new File(ws, article.path());
    }
    if (file.isFile()) onOpenDoc.accept(file);
  }

  private void addUserBubble(String text) {
    addBubble("You", text, "jane-chat-bubble-user");
  }

  private void addAssistantBubble(String text) {
    addAssistantBubble(text, false);
  }

  private void addAssistantBubbleAnimated(String text) {
    addAssistantBubble(text, true);
  }

  private void addAssistantBubble(String text, boolean animated) {
    addBubble("Jane", text, "jane-chat-bubble-assistant", true, animated);
  }

  private void addBubble(String speaker, String text, String styleClass) {
    addBubble(speaker, text, styleClass, false, false);
  }

  private void addBubble(String speaker, String text, String styleClass, boolean assistant, boolean animated) {
    Label speakerLabel = new Label(speaker);
    speakerLabel.getStyleClass().add("jane-chat-speaker");
    String fullText = text == null ? "" : text;
    boolean collapsible = assistant && shouldCollapseAnswer(fullText);
    String collapsedText = collapsible ? collapsedAnswer(fullText) : fullText;
    Label body = new Label(animated ? "" : collapsedText);
    body.setWrapText(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.getStyleClass().add("jane-chat-body");
    VBox bubble = new VBox(3, speakerLabel, body);
    bubble.getStyleClass().addAll("jane-chat-bubble", styleClass);
    bubble.setMaxWidth(Double.MAX_VALUE);
    if (collapsible) {
      Button moreButton = seeMoreButton(body, fullText, collapsedText);
      moreButton.setVisible(!animated);
      moreButton.setManaged(!animated);
      bubble.getChildren().add(moreButton);
      if (animated) {
        animateText(body, collapsedText, () -> {
          moreButton.setVisible(true);
          moreButton.setManaged(true);
        });
      }
    } else if (animated) {
      animateText(body, collapsedText);
    }
    transcriptBox.getChildren().add(bubble);
    Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
  }

  private Button seeMoreButton(Label body, String fullText, String collapsedText) {
    Button button = new Button("See more...");
    button.getStyleClass().add("jane-see-more-button");
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    final boolean[] expanded = {false};
    button.setOnAction(e -> {
      expanded[0] = !expanded[0];
      body.setText(expanded[0] ? fullText : collapsedText);
      button.setText(expanded[0] ? "See less" : "See more...");
      Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
    });
    return button;
  }

  private void showThinkingBubble() {
    hideThinkingBubble();
    Label speakerLabel = new Label("Jane");
    speakerLabel.getStyleClass().add("jane-chat-speaker");
    Label body = new Label("Thinking");
    body.setWrapText(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.getStyleClass().addAll("jane-chat-body", "jane-thinking-body");
    VBox bubble = new VBox(3, speakerLabel, body);
    bubble.getStyleClass().addAll("jane-chat-bubble", "jane-chat-bubble-assistant", "jane-chat-bubble-thinking");
    bubble.setMaxWidth(Double.MAX_VALUE);
    transcriptBox.getChildren().add(bubble);
    thinkingBubble = bubble;
    final int[] frame = {0};
    thinkingAnimation = new Timeline(new KeyFrame(Duration.millis(360), e -> {
      frame[0] = (frame[0] + 1) % 4;
      body.setText("Thinking" + ".".repeat(frame[0]));
      transcriptScroll.setVvalue(1.0);
    }));
    thinkingAnimation.setCycleCount(Animation.INDEFINITE);
    thinkingAnimation.playFromStart();
    Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
  }

  private void hideThinkingBubble() {
    if (thinkingAnimation != null) {
      thinkingAnimation.stop();
      thinkingAnimation = null;
    }
    if (thinkingBubble != null) {
      transcriptBox.getChildren().remove(thinkingBubble);
      thinkingBubble = null;
    }
  }

  private void animateText(Label label, String text, Runnable onFinished) {
    String target = text == null ? "" : text;
    if (target.isEmpty()) {
      label.setText("");
      if (onFinished != null) onFinished.run();
      return;
    }
    final int[] index = {0};
    Timeline animation = new Timeline();
    final Timeline[] animationRef = {animation};
    animation.getKeyFrames().add(new KeyFrame(Duration.millis(18), e -> {
      index[0] = Math.min(target.length(), index[0] + TYPEWRITER_CHARS_PER_TICK);
      label.setText(target.substring(0, index[0]));
      if (index[0] >= target.length()) {
        Timeline source = animationRef[0];
        source.stop();
        textAnimations.remove(source);
        if (onFinished != null) onFinished.run();
      }
      transcriptScroll.setVvalue(1.0);
    }));
    animation.setCycleCount(Animation.INDEFINITE);
    textAnimations.add(animation);
    animation.playFromStart();
  }

  private void animateText(Label label, String text) {
    animateText(label, text, () -> {});
  }

  private void stopTextAnimations() {
    for (Timeline animation : List.copyOf(textAnimations)) {
      animation.stop();
    }
    textAnimations.clear();
  }

  private static boolean shouldCollapseAnswer(String text) {
    return text != null && text.length() > ANSWER_COLLAPSE_LIMIT + MIN_COLLAPSE_REMAINDER;
  }

  private static String collapsedAnswer(String text) {
    if (!shouldCollapseAnswer(text)) return text == null ? "" : text;
    int cut = bestCollapseIndex(text);
    return text.substring(0, cut).trim() + "...";
  }

  private static int bestCollapseIndex(String text) {
    int limit = Math.min(text.length(), ANSWER_COLLAPSE_LIMIT);
    int paragraph = text.lastIndexOf("\n\n", limit);
    if (paragraph >= ANSWER_COLLAPSE_LIMIT / 2) return paragraph;
    int sentence = Math.max(text.lastIndexOf(". ", limit), Math.max(text.lastIndexOf("? ", limit), text.lastIndexOf("! ", limit)));
    if (sentence >= ANSWER_COLLAPSE_LIMIT / 2) return sentence + 1;
    int whitespace = text.lastIndexOf(' ', limit);
    if (whitespace >= ANSWER_COLLAPSE_LIMIT / 2) return whitespace;
    return limit;
  }

  private void setInputDisabled(boolean disabled) {
    askField.setDisable(disabled);
    askButton.setDisable(disabled);
    refreshButton.setDisable(disabled);
    clearButton.setDisable(disabled);
  }

  private List<HelpArticle> indexDocs() {
    List<HelpArticle> articles = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    File ws = workspaceRoot != null ? workspaceRoot : detectWorkspaceRoot();
    if (ws != null) collectMarkdown(ws.toPath(), ws.toPath(), "Workspace", articles, seen);
    File pr = normalizeDir(projectRoot);
    if (pr != null && (ws == null || !sameDir(pr, ws))) {
      collectMarkdown(pr.toPath(), pr.toPath(), "Project", articles, seen);
    }
    articles.sort(Comparator
        .comparing(HelpArticle::path, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(HelpArticle::title, String.CASE_INSENSITIVE_ORDER));
    return articles;
  }

  private void collectMarkdown(
      Path base,
      Path root,
      String source,
      List<HelpArticle> articles,
      Set<String> seen
  ) {
    if (root == null || !Files.isDirectory(root)) return;
    try (Stream<Path> stream = Files.walk(root)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
          .filter(path -> !shouldSkip(base, path))
          .forEach(path -> addArticle(base, path, source, articles, seen));
    } catch (Exception ignored) {
      // Best-effort indexing; Jane keeps built-in articles through JaneTrainingCorpus.
    }
  }

  private void addArticle(Path base, Path path, String source, List<HelpArticle> articles, Set<String> seen) {
    try {
      Path normalized = path.toAbsolutePath().normalize();
      if (!seen.add(normalized.toString())) return;
      String body = Files.readString(normalized);
      String relative = base.toAbsolutePath().normalize().relativize(normalized).toString().replace('\\', '/');
      articles.add(new HelpArticle(
          normalized.toString(),
          title(body, normalized),
          summary(body),
          relative,
          body,
          Map.of("source", source)));
    } catch (Exception ignored) {
      // Skip unreadable docs.
    }
  }

  private boolean shouldSkip(Path base, Path path) {
    Path relative;
    try {
      relative = base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
    } catch (Exception ex) {
      return false;
    }
    for (Path part : relative) {
      String name = part.toString();
      if (name.equals(".git")
          || name.equals(".gradle")
          || name.equals(".idea")
          || name.equals(".jvn")
          || name.equals(".jvn-gradle-user-home")
          || name.equals("build")
          || name.equals("out")
          || name.equals("target")) {
        return true;
      }
    }
    return false;
  }

  private static String title(String body, Path path) {
    if (body != null) {
      for (String raw : body.split("\\R")) {
        Matcher matcher = HEADING_LINE.matcher(raw.trim());
        if (matcher.matches()) return matcher.group(1).trim();
      }
    }
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static String summary(String body) {
    if (body == null || body.isBlank()) return "No summary available.";
    StringBuilder paragraph = new StringBuilder();
    for (String raw : body.split("\\R")) {
      String line = raw.trim();
      if (line.isBlank()) {
        if (paragraph.length() > 0) break;
        continue;
      }
      if (line.startsWith("#") || line.startsWith("|") || line.startsWith("```") || line.startsWith("- ")) {
        continue;
      }
      if (paragraph.length() > 0) paragraph.append(' ');
      paragraph.append(line);
      if (paragraph.length() > SUMMARY_LIMIT) break;
    }
    String value = paragraph.toString().replaceAll("\\s+", " ").trim();
    if (value.isBlank()) return "No summary available.";
    return value.length() <= SUMMARY_LIMIT ? value : value.substring(0, SUMMARY_LIMIT - 3).trim() + "...";
  }

  private File detectWorkspaceRoot() {
    String property = System.getProperty("jvn.repoRoot");
    if (property == null || property.isBlank()) property = System.getProperty("jvn.jane.workspaceRoot");
    if (property == null || property.isBlank()) property = System.getProperty("user.dir");
    return normalizeDir(property == null || property.isBlank() ? null : new File(property));
  }

  private static File normalizeDir(File file) {
    if (file == null) return null;
    File resolved = file.getAbsoluteFile();
    return resolved.isDirectory() ? resolved : null;
  }

  private static boolean sameDir(File left, File right) {
    if (left == null || right == null) return false;
    return left.getAbsoluteFile().toPath().normalize().equals(right.getAbsoluteFile().toPath().normalize());
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
      return "Unknown error";
    }
    return throwable.getMessage();
  }

  private record GeminiSettings(Path path, boolean hasApiKey, String model) {}
}

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
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
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Sidebar chatbot for Jane, JVN's local assistant. */
public class JaneAssistantView extends BorderPane {
  private static final Pattern HEADING_LINE = Pattern.compile("^#\\s+(.+)$");
  private static final Pattern RICH_HEADING_LINE = Pattern.compile("^(#{1,4})\\s+(.+)$");
  private static final Pattern RICH_UNORDERED_LIST_LINE = Pattern.compile("^\\s*[-*]\\s+(.+)$");
  private static final Pattern RICH_ORDERED_LIST_LINE = Pattern.compile("^\\s*(\\d+)\\.\\s+(.+)$");
  private static final Pattern JANE_PATCH_BLOCK = Pattern.compile("(?s)```\\s*jane_patch\\s*\\R(.*?)(?:\\R)?```");
  private static final Pattern QUERY_PATH_TOKEN = Pattern.compile("[A-Za-z0-9_./\\\\-]+\\.[A-Za-z0-9_]+");
  private static final Pattern SENSITIVE_LINE = Pattern.compile(
      "(?i).*(api[_-]?key|token|secret|password|passwd|credential|private[_-]?key|client[_-]?secret)\\s*[:=].*");
  private static final int SUMMARY_LIMIT = 220;
  private static final int ANSWER_COLLAPSE_LIMIT = 900;
  private static final int MIN_COLLAPSE_REMAINDER = 180;
  private static final int TYPEWRITER_CHARS_PER_TICK = 14;
  private static final int MAX_TOOL_FILES = 5;
  private static final int MAX_TOOL_CONTEXT_CHARS = 9000;
  private static final int MAX_TOOL_SNIPPET_CHARS = 1800;
  private static final long MAX_TOOL_FILE_BYTES = 128L * 1024L;
  private static final long MAX_TOOL_CREATE_BYTES = 96L * 1024L;
  private static final int MAX_TOOL_WALK_FILES = 2400;
  private static final String JANE_TOOL_AUDIT_RELATIVE_PATH = ".jvn/jane-tool-audit.log";
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
  private static final Set<String> TOOL_TEXT_EXTENSIONS = Set.of(
      "java", "kt", "kts", "gradle", "md", "txt", "vns", "jes", "json", "json5",
      "properties", "xml", "css", "scss", "html", "js", "ts", "tsx", "jsx",
      "yaml", "yml", "toml", "ini", "cfg", "conf", "menu", "layout", "style",
      "registry", "scene", "timeline", "atlas", "glsl", "frag", "vert", "sh", "ps1");
  private static final Set<String> TOOL_BLOCKED_PATH_PARTS = Set.of(
      ".git", ".gradle", ".idea", ".jvn", ".jvn-gradle-user-home", "build", "out", "target", "node_modules");
  private static final Set<String> TOOL_SENSITIVE_NAME_TOKENS = Set.of(
      ".env", "secret", "secrets", "token", "tokens", "credential", "credentials", "password", "passwd",
      "privatekey", "private-key", "apikey", "api-key", "keystore", "id_rsa", "id_dsa");
  private static final Set<String> TOOL_STOP_WORDS = Set.of(
      "about", "after", "again", "also", "and", "are", "can", "could", "create", "edit",
      "file", "files", "fix", "for", "from", "have", "how", "into", "jane", "make",
      "need", "please", "project", "read", "search", "should", "that", "the", "this",
      "tool", "what", "when", "where", "with", "would", "write");

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
  private final Button undoToolButton = new Button("Undo Edit");
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
  private JaneAppliedPatch lastAppliedPatch;

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
    undoToolButton.getStyleClass().add("sidebar-tool-btn");
    undoToolButton.setTooltip(new Tooltip("Undo the last Jane-approved file edit"));
    undoToolButton.setDisable(true);
    undoToolButton.setOnAction(e -> undoLastJanePatch());

    HBox inputRow = new HBox(6, askField, askButton);
    inputRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(askField, Priority.ALWAYS);
    HBox actions = new HBox(6, sourcesButton, refreshButton, clearButton, undoToolButton, settingsButton);
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
    String visibleQuery = query.trim();
    askField.clear();
    addUserBubble(visibleQuery);
    setInputDisabled(true);
    statusLabel.setText("Jane is thinking...");
    showThinkingBubble();

    Task<JaneInteractionResult> task = new Task<>() {
      @Override
      protected JaneInteractionResult call() {
        JaneToolContext toolContext = buildJaneToolContext(visibleQuery);
        JaneChatResponse response = activeJane.ask(visibleQuery, toolContext.prompt());
        JanePatchProposal proposal = parsePatchProposal(response == null ? "" : response.answer());
        JanePatchProposal validated = proposal == null ? null : validatePatchProposal(proposal);
        String displayAnswer = stripPatchBlocks(response == null ? "" : response.answer()).trim();
        if (displayAnswer.isBlank() && validated != null) {
          displayAnswer = validated.hasErrors()
              ? "I drafted a file change, but the editor blocked it during validation."
              : "I drafted a file change. Review the diff below before applying it.";
        }
        return new JaneInteractionResult(response, displayAnswer, validated, toolContext.summary());
      }
    };
    task.setOnSucceeded(e -> {
      JaneInteractionResult result = task.getValue();
      JaneChatResponse response = result == null ? null : result.response();
      hideThinkingBubble();
      setInputDisabled(false);
      statusLabel.setText("Ready. Indexed " + generalHelp.articles().size() + " training articles.");
      addAssistantBubbleAnimated(result == null || result.displayAnswer().isBlank()
          ? "I could not produce a response."
          : result.displayAnswer());
      if (result != null && result.patchProposal() != null) {
        addPatchProposalCard(result.patchProposal());
      }
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

  private JaneToolContext buildJaneToolContext(String query) {
    Path root = janeToolRoot();
    if (root == null) {
      return new JaneToolContext("", "No project root available for Jane file tools.");
    }
    List<JaneFileSnippet> snippets = collectJaneFileSnippets(root, query);
    StringBuilder prompt = new StringBuilder(2048);
    prompt.append("""
        Editor-provided workspace context and tool protocol:
        - You may use the read-only snippets below as project context.
        - You cannot directly read more files, write files, or run commands.
        - Do not say you applied a change. The editor requires user approval before writing.
        - If a file edit is useful, propose it with exactly one fenced jane_patch block.
        - Use only relative paths from the workspace root. Never use absolute paths.
        - Use action: replace for exact text replacements or action: create for new files.
        - For replace, the find block must exactly match text that is likely present in the current file.
        - Do not propose edits under .git, .jvn, build outputs, dependency folders, or binary files.

        jane_patch format:
        ```jane_patch
        summary: Short reason for the change
        file: relative/path.ext
        action: replace
        find:
        <<<
        exact old text
        >>>
        replace:
        <<<
        exact new text
        >>>
        file: relative/new-file.ext
        action: create
        content:
        <<<
        new file content
        >>>
        ```

        """);
    prompt.append("Workspace root: ").append(root).append('\n');
    if (snippets.isEmpty()) {
      prompt.append("No matching readable project snippets were found for this request.\n");
    } else {
      prompt.append("Readable project snippets selected by the editor:\n");
      int remaining = MAX_TOOL_CONTEXT_CHARS;
      for (JaneFileSnippet snippet : snippets) {
        String block = "\n### " + snippet.relativePath() + " (" + snippet.byteSize() + " bytes)\n"
            + "```text\n" + snippet.snippet() + "\n```\n";
        if (block.length() > remaining) break;
        prompt.append(block);
        remaining -= block.length();
      }
    }
    String summary = snippets.isEmpty()
        ? "Jane searched project files but found no safe matching snippets."
        : "Jane read " + snippets.size() + " safe project snippet" + (snippets.size() == 1 ? "" : "s") + ".";
    return new JaneToolContext(prompt.toString(), summary);
  }

  private List<JaneFileSnippet> collectJaneFileSnippets(Path root, String query) {
    if (root == null || !Files.isDirectory(root)) return List.of();
    Set<String> tokens = queryTokens(query);
    Set<String> explicitPaths = explicitQueryPaths(query);
    List<JaneScoredFile> scored = new ArrayList<>();
    Set<Path> seen = new HashSet<>();

    for (String explicit : explicitPaths) {
      Path path = root.resolve(explicit).normalize();
      JaneScoredFile candidate = scoreReadableFile(root, path, tokens, explicitPaths, 100);
      if (candidate != null && seen.add(candidate.path())) scored.add(candidate);
    }

    int[] visited = {0};
    try (Stream<Path> stream = Files.walk(root, 8)) {
      stream
          .filter(path -> visited[0]++ < MAX_TOOL_WALK_FILES)
          .filter(Files::isRegularFile)
          .filter(path -> seen.add(path.toAbsolutePath().normalize()))
          .map(path -> scoreReadableFile(root, path, tokens, explicitPaths, 0))
          .filter(file -> file != null && file.score() > 0)
          .forEach(scored::add);
    } catch (Exception ignored) {
      // Jane file context is best-effort. The answer can still use TAGI docs.
    }

    scored.sort(Comparator
        .comparingInt(JaneScoredFile::score).reversed()
        .thenComparing(JaneScoredFile::relativePath, String.CASE_INSENSITIVE_ORDER));
    List<JaneFileSnippet> snippets = new ArrayList<>();
    for (JaneScoredFile file : scored) {
      if (snippets.size() >= MAX_TOOL_FILES) break;
      snippets.add(new JaneFileSnippet(
          file.relativePath(),
          file.byteSize(),
          snippetFor(file.text(), tokens)));
    }
    return snippets;
  }

  private JaneScoredFile scoreReadableFile(
      Path root,
      Path path,
      Set<String> tokens,
      Set<String> explicitPaths,
      int baseScore
  ) {
    try {
      Path normalized = path.toAbsolutePath().normalize();
      if (!isSafeToolPath(root, normalized, false)) return null;
      long size = Files.size(normalized);
      if (size > MAX_TOOL_FILE_BYTES || size < 0L) return null;
      if (!isTextExtension(normalized)) return null;
      String text = Files.readString(normalized, StandardCharsets.UTF_8);
      if (looksBinary(text)) return null;
      text = redactSensitiveLines(text);
      String relative = relativePath(root, normalized);
      String haystack = (relative + "\n" + text).toLowerCase(Locale.ROOT);
      int score = baseScore;
      String lowerRelative = relative.toLowerCase(Locale.ROOT);
      for (String explicit : explicitPaths) {
        if (lowerRelative.equals(explicit.toLowerCase(Locale.ROOT))) score += 180;
      }
      for (String token : tokens) {
        if (lowerRelative.contains(token)) score += 18;
        score += Math.min(18, countOccurrences(haystack, token) * 3);
      }
      return new JaneScoredFile(normalized, relative, text, size, score);
    } catch (Exception ignored) {
      return null;
    }
  }

  private JanePatchProposal parsePatchProposal(String answer) {
    if (answer == null || answer.isBlank()) return null;
    Matcher matcher = JANE_PATCH_BLOCK.matcher(answer);
    if (!matcher.find()) return null;
    String block = matcher.group(1);
    String[] lines = block.split("\\R", -1);
    String summary = "";
    List<JanePatchChange> changes = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    PatchChangeBuilder current = null;
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isBlank()) {
        i++;
        continue;
      }
      if (trimmed.startsWith("summary:")) {
        summary = trimmed.substring("summary:".length()).trim();
        i++;
        continue;
      }
      if (trimmed.startsWith("file:")) {
        if (current != null) addParsedPatchChange(changes, errors, current);
        current = new PatchChangeBuilder(trimmed.substring("file:".length()).trim());
        i++;
        continue;
      }
      if (current == null) {
        errors.add("Patch content before file: " + trimmed);
        i++;
        continue;
      }
      if (trimmed.startsWith("action:")) {
        current.action = trimmed.substring("action:".length()).trim().toLowerCase(Locale.ROOT);
        i++;
        continue;
      }
      if (trimmed.equals("find:")) {
        BlockRead read = readPatchDelimitedBlock(lines, i + 1, "find");
        current.findText = read.text();
        errors.addAll(read.errors());
        i = read.nextIndex();
        continue;
      }
      if (trimmed.equals("replace:")) {
        BlockRead read = readPatchDelimitedBlock(lines, i + 1, "replace");
        current.replaceText = read.text();
        errors.addAll(read.errors());
        i = read.nextIndex();
        continue;
      }
      if (trimmed.equals("content:")) {
        BlockRead read = readPatchDelimitedBlock(lines, i + 1, "content");
        current.content = read.text();
        errors.addAll(read.errors());
        i = read.nextIndex();
        continue;
      }
      errors.add("Unrecognized patch line: " + trimmed);
      i++;
    }
    if (current != null) addParsedPatchChange(changes, errors, current);
    if (changes.isEmpty()) errors.add("Patch proposal did not contain any file changes.");
    return new JanePatchProposal(summary.isBlank() ? "Jane proposed file changes" : summary, changes, errors);
  }

  private void addParsedPatchChange(List<JanePatchChange> changes, List<String> errors, PatchChangeBuilder builder) {
    if (builder.path == null || builder.path.isBlank()) {
      errors.add("A patch change is missing file path.");
      return;
    }
    changes.add(new JanePatchChange(
        builder.path,
        builder.action == null || builder.action.isBlank() ? "" : builder.action,
        builder.findText == null ? "" : builder.findText,
        builder.replaceText == null ? "" : builder.replaceText,
        builder.content == null ? "" : builder.content,
        null));
  }

  private BlockRead readPatchDelimitedBlock(String[] lines, int start, String label) {
    List<String> errors = new ArrayList<>();
    int i = start;
    if (i >= lines.length || !lines[i].trim().equals("<<<")) {
      errors.add(label + " block must start with <<<.");
      return new BlockRead("", Math.min(lines.length, i + 1), errors);
    }
    i++;
    StringBuilder out = new StringBuilder();
    while (i < lines.length) {
      if (lines[i].trim().equals(">>>")) {
        return new BlockRead(out.toString(), i + 1, errors);
      }
      if (out.length() > 0) out.append('\n');
      out.append(lines[i]);
      i++;
    }
    errors.add(label + " block is missing >>>.");
    return new BlockRead(out.toString(), i, errors);
  }

  private JanePatchProposal validatePatchProposal(JanePatchProposal proposal) {
    if (proposal == null) return null;
    List<String> errors = new ArrayList<>(proposal.errors());
    List<JanePatchChange> changes = new ArrayList<>();
    Path root = janeToolRoot();
    if (root == null) {
      errors.add("No writable project root is available.");
      return new JanePatchProposal(proposal.summary(), proposal.changes(), errors);
    }
    if (proposal.changes().size() > 8) {
      errors.add("Patch proposals are limited to 8 files.");
    }
    for (JanePatchChange change : proposal.changes()) {
      JanePatchChange validated = validatePatchChange(root, change, errors);
      changes.add(validated);
    }
    return new JanePatchProposal(proposal.summary(), changes, errors);
  }

  private JanePatchChange validatePatchChange(Path root, JanePatchChange change, List<String> errors) {
    Path path = resolveToolRelativePath(root, change.path());
    if (path == null || !isSafeToolPath(root, path, true)) {
      errors.add("Blocked unsafe patch path: " + change.path());
      return change;
    }
    String action = change.action().toLowerCase(Locale.ROOT);
    if (!action.equals("replace") && !action.equals("create")) {
      errors.add("Unsupported action for " + change.path() + ": " + change.action());
      return change.withAbsolutePath(path);
    }
    if (!isTextExtension(path)) {
      errors.add("Blocked non-text file path: " + change.path());
    }
    try {
      if (action.equals("create")) {
        if (Files.exists(path)) errors.add("Create target already exists: " + change.path());
        if (change.content().getBytes(StandardCharsets.UTF_8).length > MAX_TOOL_CREATE_BYTES) {
          errors.add("Create content is too large: " + change.path());
        }
      } else {
        if (!Files.isRegularFile(path)) {
          errors.add("Replace target does not exist: " + change.path());
        } else if (Files.size(path) > MAX_TOOL_FILE_BYTES) {
          errors.add("Replace target is too large: " + change.path());
        } else {
          String existing = Files.readString(path, StandardCharsets.UTF_8);
          if (looksBinary(existing)) errors.add("Replace target appears binary: " + change.path());
          if (change.findText().isBlank()) {
            errors.add("Replace action has an empty find block: " + change.path());
          } else {
            int matches = countOccurrences(existing, change.findText());
            if (matches != 1) {
              errors.add("Find block must match exactly once in " + change.path() + " but matched " + matches + " time(s).");
            }
          }
        }
      }
    } catch (Exception ex) {
      errors.add("Could not validate " + change.path() + ": " + safeMessage(ex));
    }
    return change.withAbsolutePath(path);
  }

  private String stripPatchBlocks(String answer) {
    if (answer == null || answer.isBlank()) return "";
    return JANE_PATCH_BLOCK.matcher(answer).replaceAll("").trim();
  }

  private void addPatchProposalCard(JanePatchProposal proposal) {
    VBox card = new VBox(7);
    card.getStyleClass().add("jane-tool-card");
    Label title = new Label(proposal.hasErrors() ? "Blocked File Proposal" : "File Proposal");
    title.getStyleClass().add("jane-tool-title");
    Label summary = new Label(proposal.summary());
    summary.setWrapText(true);
    summary.getStyleClass().add("jane-tool-summary");
    Label files = new Label("Files: " + proposal.fileList());
    files.setWrapText(true);
    files.getStyleClass().add("jane-tool-files");
    TextArea diff = new TextArea(buildProposalDiff(proposal));
    diff.setEditable(false);
    diff.setWrapText(false);
    diff.setPrefRowCount(Math.min(12, Math.max(5, diff.getText().split("\\R", -1).length)));
    diff.getStyleClass().add("jane-tool-diff");

    Button reviewButton = new Button(proposal.hasErrors() ? "Review Errors" : "Review & Apply");
    reviewButton.getStyleClass().add("sidebar-tool-btn");
    reviewButton.setOnAction(e -> showPatchReviewDialog(proposal));
    Button auditButton = new Button("Audit Log");
    auditButton.getStyleClass().add("sidebar-tool-btn");
    auditButton.setOnAction(e -> showJaneAuditLog());
    HBox buttons = new HBox(6, reviewButton, auditButton);
    buttons.setAlignment(Pos.CENTER_LEFT);
    card.getChildren().addAll(title, summary, files, diff, buttons);
    transcriptBox.getChildren().add(card);
    Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
  }

  private void showPatchReviewDialog(JanePatchProposal proposal) {
    TextArea area = new TextArea(buildProposalDiff(proposal));
    area.setEditable(false);
    area.setWrapText(false);
    area.getStyleClass().add("editor-dialog-text-area");
    area.setPrefRowCount(22);
    if (proposal.hasErrors()) {
      EditorDialogs.show(
          ownerWindow(),
          "Jane File Proposal Blocked",
          "The editor refused this patch. Jane cannot write anything until every validation error is fixed.",
          area,
          EditorDialogs.ActionSpec.accent("close", "Close", null));
      return;
    }
    EditorDialogs.show(
        ownerWindow(),
        "Apply Jane File Edit?",
        "Review the exact file changes below. No shell commands will run. The write is limited to the current workspace.",
        area,
        area,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("apply", "Apply", () -> applyPatchProposal(proposal)));
  }

  private void applyPatchProposal(JanePatchProposal proposal) {
    JanePatchProposal validated = validatePatchProposal(proposal);
    if (validated.hasErrors()) {
      appendJaneAudit("APPLY_BLOCKED", validated, validated.errorText());
      EditorDialogs.showTextBlock(
          ownerWindow(),
          "Jane Edit Blocked",
          "The project changed or the patch failed validation.",
          validated.errorText(),
          "Close");
      return;
    }
    List<JaneWritePlan> plans;
    try {
      plans = prepareWritePlans(validated);
    } catch (IOException ex) {
      appendJaneAudit("APPLY_FAILED", validated, safeMessage(ex));
      EditorDialogs.error(ownerWindow(), "Jane Edit Failed", "Could not prepare Jane's edit.", ex);
      return;
    }

    List<JaneFileBackup> backups = new ArrayList<>();
    try {
      for (JaneWritePlan plan : plans) {
        Path path = plan.change().absolutePath();
        backups.add(new JaneFileBackup(plan.change().path(), path, plan.existed(), plan.beforeText()));
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, plan.afterText(), StandardCharsets.UTF_8);
      }
      lastAppliedPatch = new JaneAppliedPatch(validated.summary(), backups, Instant.now());
      undoToolButton.setDisable(false);
      appendJaneAudit("APPLY", validated, "Applied " + plans.size() + " file change(s).");
      addAssistantBubble("Applied Jane's approved edit to " + validated.fileList() + ". Use Undo Edit if you want to revert it.");
      if (onOpenDoc != null && !backups.isEmpty()) {
        File first = backups.get(0).path().toFile();
        if (first.isFile()) onOpenDoc.accept(first);
      }
    } catch (IOException ex) {
      restoreBackups(backups);
      appendJaneAudit("APPLY_FAILED_ROLLED_BACK", validated, safeMessage(ex));
      EditorDialogs.error(ownerWindow(), "Jane Edit Failed", "The edit failed and Jane attempted to restore previous file contents.", ex);
    }
  }

  private List<JaneWritePlan> prepareWritePlans(JanePatchProposal proposal) throws IOException {
    List<JaneWritePlan> plans = new ArrayList<>();
    for (JanePatchChange change : proposal.changes()) {
      Path path = change.absolutePath();
      boolean existed = Files.exists(path);
      String before = existed ? Files.readString(path, StandardCharsets.UTF_8) : "";
      String after;
      if ("create".equals(change.action())) {
        after = normalizeWrittenText(change.content());
      } else {
        int matches = countOccurrences(before, change.findText());
        if (matches != 1) throw new IOException("Find block no longer matches exactly once in " + change.path());
        after = before.replace(change.findText(), change.replaceText());
      }
      plans.add(new JaneWritePlan(change, existed, before, after));
    }
    return plans;
  }

  private void undoLastJanePatch() {
    JaneAppliedPatch applied = lastAppliedPatch;
    if (applied == null || applied.backups().isEmpty()) {
      undoToolButton.setDisable(true);
      return;
    }
    StringBuilder body = new StringBuilder();
    body.append("Undo Jane edit: ").append(applied.summary()).append("\n\n");
    for (JaneFileBackup backup : applied.backups()) {
      body.append("- ").append(backup.relativePath()).append('\n');
    }
    TextArea area = new TextArea(body.toString());
    area.setEditable(false);
    area.setWrapText(true);
    area.getStyleClass().add("editor-dialog-text-area");
    area.setPrefRowCount(9);
    EditorDialogs.show(
        ownerWindow(),
        "Undo Jane Edit?",
        "This restores the file contents captured before Jane's last approved edit.",
        area,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.danger("undo", "Undo Edit", () -> {
          restoreBackups(applied.backups());
          appendJaneAudit("UNDO", applied.toProposal(), "Restored " + applied.backups().size() + " file(s).");
          lastAppliedPatch = null;
          undoToolButton.setDisable(true);
          addAssistantBubble("Undid Jane's last approved file edit.");
        }));
  }

  private void restoreBackups(List<JaneFileBackup> backups) {
    if (backups == null) return;
    for (int i = backups.size() - 1; i >= 0; i--) {
      JaneFileBackup backup = backups.get(i);
      try {
        if (backup.existed()) {
          if (backup.path().getParent() != null) Files.createDirectories(backup.path().getParent());
          Files.writeString(backup.path(), backup.beforeText(), StandardCharsets.UTF_8);
        } else {
          Files.deleteIfExists(backup.path());
        }
      } catch (IOException ignored) {
        // The audit log records the failed operation; restore is best-effort.
      }
    }
  }

  private String buildProposalDiff(JanePatchProposal proposal) {
    StringBuilder diff = new StringBuilder();
    diff.append("Summary: ").append(proposal.summary()).append('\n');
    if (proposal.hasErrors()) {
      diff.append('\n').append("Validation errors:").append('\n');
      for (String error : proposal.errors()) {
        diff.append("- ").append(error).append('\n');
      }
    }
    for (JanePatchChange change : proposal.changes()) {
      diff.append('\n');
      if ("create".equals(change.action())) {
        diff.append("--- /dev/null\n");
        diff.append("+++ ").append(change.path()).append('\n');
        diff.append("@@\n");
        appendDiffLines(diff, "+", change.content());
      } else {
        diff.append("--- ").append(change.path()).append('\n');
        diff.append("+++ ").append(change.path()).append('\n');
        diff.append("@@\n");
        appendDiffLines(diff, "-", change.findText());
        appendDiffLines(diff, "+", change.replaceText());
      }
    }
    return diff.toString().trim();
  }

  private void appendDiffLines(StringBuilder diff, String prefix, String text) {
    String value = text == null ? "" : text;
    String[] lines = value.split("\\R", -1);
    for (String line : lines) {
      diff.append(prefix).append(line).append('\n');
    }
  }

  private void showJaneAuditLog() {
    Path audit = janeAuditPath();
    String body;
    try {
      body = Files.isRegularFile(audit) ? Files.readString(audit, StandardCharsets.UTF_8) : "No Jane file-tool audit entries yet.";
    } catch (IOException ex) {
      body = "Failed to read audit log: " + safeMessage(ex);
    }
    EditorDialogs.showTextBlock(ownerWindow(), "Jane File Tool Audit", "Approved writes and undo operations are recorded here.", body, "Close");
  }

  private void appendJaneAudit(String event, JanePatchProposal proposal, String detail) {
    Path audit = janeAuditPath();
    try {
      if (audit.getParent() != null) Files.createDirectories(audit.getParent());
      StringBuilder entry = new StringBuilder();
      entry.append(Instant.now()).append(" ").append(event == null ? "EVENT" : event).append('\n');
      entry.append("summary: ").append(proposal == null ? "" : proposal.summary()).append('\n');
      entry.append("files: ").append(proposal == null ? "" : proposal.fileList()).append('\n');
      if (detail != null && !detail.isBlank()) entry.append("detail: ").append(detail.replace('\n', ' ')).append('\n');
      entry.append('\n');
      Files.writeString(audit, entry.toString(), StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException ignored) {
      // Audit logging must not make an already-approved edit fail.
    }
  }

  private Path janeAuditPath() {
    Path root = settingsRootPath();
    return root.resolve(JANE_TOOL_AUDIT_RELATIVE_PATH).toAbsolutePath().normalize();
  }

  private Window ownerWindow() {
    return getScene() == null ? null : getScene().getWindow();
  }

  private Path janeToolRoot() {
    File root = normalizeDir(projectRoot);
    if (root == null) root = normalizeDir(workspaceRoot);
    if (root == null) root = detectWorkspaceRoot();
    return root == null ? null : root.toPath().toAbsolutePath().normalize();
  }

  private Set<String> queryTokens(String query) {
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    if (query == null || query.isBlank()) return tokens;
    for (String raw : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}_]+")) {
      String token = raw.trim();
      if (token.length() < 3 || TOOL_STOP_WORDS.contains(token)) continue;
      tokens.add(token);
      if (tokens.size() >= 24) break;
    }
    return tokens;
  }

  private Set<String> explicitQueryPaths(String query) {
    LinkedHashSet<String> paths = new LinkedHashSet<>();
    if (query == null || query.isBlank()) return paths;
    Matcher matcher = QUERY_PATH_TOKEN.matcher(query);
    while (matcher.find()) {
      String raw = matcher.group().replace('\\', '/');
      while (raw.startsWith("./")) raw = raw.substring(2);
      raw = raw.replaceAll("^['\"`]+|['\"`,;:]+$", "");
      if (!raw.isBlank() && !raw.startsWith("/") && !raw.contains("..")) {
        paths.add(raw);
      }
      if (paths.size() >= 12) break;
    }
    return paths;
  }

  private Path resolveToolRelativePath(Path root, String rawPath) {
    if (root == null || rawPath == null || rawPath.isBlank()) return null;
    String clean = rawPath.trim().replace('\\', '/');
    clean = clean.replaceAll("^['\"`]+|['\"`]+$", "");
    if (clean.startsWith("/") || clean.matches("^[A-Za-z]:/.*") || clean.contains("\u0000")) return null;
    Path relative = Path.of(clean).normalize();
    if (relative.isAbsolute() || relative.startsWith("..")) return null;
    return root.resolve(relative).toAbsolutePath().normalize();
  }

  private boolean isSafeToolPath(Path root, Path path, boolean forWrite) {
    if (root == null || path == null) return false;
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path normalizedPath = path.toAbsolutePath().normalize();
    if (!normalizedPath.startsWith(normalizedRoot)) return false;
    Path relative;
    try {
      relative = normalizedRoot.relativize(normalizedPath);
    } catch (IllegalArgumentException ex) {
      return false;
    }
    if (relative.toString().isBlank()) return false;
    for (Path part : relative) {
      String name = part.toString();
      String lowerName = name.toLowerCase(Locale.ROOT);
      if (name.equals("..") || name.indexOf('\0') >= 0) return false;
      if (TOOL_BLOCKED_PATH_PARTS.contains(name)) return false;
      if (TOOL_SENSITIVE_NAME_TOKENS.contains(lowerName)) return false;
      for (String token : TOOL_SENSITIVE_NAME_TOKENS) {
        if (!token.startsWith(".") && lowerName.contains(token)) return false;
      }
      if (forWrite && (name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".zip"))) return false;
    }
    return true;
  }

  private boolean isTextExtension(Path path) {
    if (path == null || path.getFileName() == null) return false;
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) return false;
    return TOOL_TEXT_EXTENSIONS.contains(name.substring(dot + 1));
  }

  private String relativePath(Path root, Path path) {
    try {
      return root.toAbsolutePath().normalize()
          .relativize(path.toAbsolutePath().normalize())
          .toString()
          .replace('\\', '/');
    } catch (Exception ex) {
      return path == null ? "" : path.getFileName().toString();
    }
  }

  private String snippetFor(String text, Set<String> tokens) {
    if (text == null || text.length() <= MAX_TOOL_SNIPPET_CHARS) return text == null ? "" : text;
    String lower = text.toLowerCase(Locale.ROOT);
    int hit = -1;
    if (tokens != null) {
      for (String token : tokens) {
        hit = lower.indexOf(token);
        if (hit >= 0) break;
      }
    }
    int start = hit < 0 ? 0 : Math.max(0, hit - MAX_TOOL_SNIPPET_CHARS / 3);
    int end = Math.min(text.length(), start + MAX_TOOL_SNIPPET_CHARS);
    start = Math.max(0, Math.min(start, Math.max(0, text.length() - MAX_TOOL_SNIPPET_CHARS)));
    String snippet = text.substring(start, end);
    if (start > 0) snippet = "...\n" + snippet;
    if (end < text.length()) snippet = snippet + "\n...";
    return snippet;
  }

  private String redactSensitiveLines(String text) {
    if (text == null || text.isBlank()) return text == null ? "" : text;
    StringBuilder out = new StringBuilder(text.length());
    String[] lines = text.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      out.append(SENSITIVE_LINE.matcher(line).matches() ? "[redacted sensitive line]" : line);
      if (i + 1 < lines.length) out.append('\n');
    }
    return out.toString();
  }

  private boolean looksBinary(String text) {
    if (text == null) return false;
    int limit = Math.min(text.length(), 4096);
    for (int i = 0; i < limit; i++) {
      if (text.charAt(i) == '\0') return true;
    }
    return false;
  }

  private int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) return 0;
    int count = 0;
    int index = 0;
    while (index <= haystack.length()) {
      index = haystack.indexOf(needle, index);
      if (index < 0) break;
      count++;
      index += needle.length();
    }
    return count;
  }

  private String normalizeWrittenText(String text) {
    return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
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
    Node body = assistant
        ? assistantAnswerBody(animated ? "" : collapsedText)
        : plainAnswerBody(animated ? "" : collapsedText);
    VBox bubble = new VBox(3, speakerLabel, body);
    bubble.getStyleClass().addAll("jane-chat-bubble", styleClass);
    bubble.setMaxWidth(Double.MAX_VALUE);
    if (collapsible) {
      Button moreButton = seeMoreButton(body, fullText, collapsedText);
      moreButton.setVisible(!animated);
      moreButton.setManaged(!animated);
      bubble.getChildren().add(moreButton);
      if (animated) {
        animateAnswerBody(body, collapsedText, assistant, () -> {
          moreButton.setVisible(true);
          moreButton.setManaged(true);
        });
      }
    } else if (animated) {
      animateAnswerBody(body, collapsedText, assistant);
    }
    transcriptBox.getChildren().add(bubble);
    Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
  }

  private Node plainAnswerBody(String text) {
    Label body = new Label(text == null ? "" : text);
    body.setWrapText(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.getStyleClass().add("jane-chat-body");
    return body;
  }

  private VBox assistantAnswerBody(String text) {
    VBox body = new VBox(7);
    body.setFillWidth(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.getStyleClass().addAll("jane-chat-body", "jane-rich-answer");
    renderRichAnswer(body, text);
    return body;
  }

  private Button seeMoreButton(Node body, String fullText, String collapsedText) {
    Button button = new Button("See more...");
    button.getStyleClass().add("jane-see-more-button");
    button.setMaxWidth(Double.MAX_VALUE);
    button.setAlignment(Pos.CENTER_LEFT);
    final boolean[] expanded = {false};
    button.setOnAction(e -> {
      expanded[0] = !expanded[0];
      setAnswerBodyText(body, expanded[0] ? fullText : collapsedText);
      button.setText(expanded[0] ? "See less" : "See more...");
      Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
    });
    return button;
  }

  private void setAnswerBodyText(Node body, String text) {
    if (body instanceof VBox richBody) {
      renderRichAnswer(richBody, text);
    } else if (body instanceof Label label) {
      label.setText(text == null ? "" : text);
    }
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

  private void animateAnswerBody(Node body, String text, boolean rich, Runnable onFinished) {
    String target = text == null ? "" : text;
    if (target.isEmpty()) {
      setAnswerBodyText(body, "");
      if (onFinished != null) onFinished.run();
      return;
    }
    final int[] index = {0};
    Timeline animation = new Timeline();
    final Timeline[] animationRef = {animation};
    animation.getKeyFrames().add(new KeyFrame(Duration.millis(18), e -> {
      index[0] = Math.min(target.length(), index[0] + TYPEWRITER_CHARS_PER_TICK);
      String current = target.substring(0, index[0]);
      if (rich) {
        setAnswerBodyText(body, current);
      } else if (body instanceof Label label) {
        label.setText(current);
      }
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

  private void animateAnswerBody(Node body, String text, boolean rich) {
    animateAnswerBody(body, text, rich, () -> {});
  }

  private void renderRichAnswer(VBox body, String markdown) {
    body.getChildren().clear();
    String normalized = normalizeRichMarkdown(markdown);
    if (normalized.isBlank()) {
      TextFlow empty = richInlineFlow("");
      body.getChildren().add(empty);
      return;
    }
    String[] lines = normalized.split("\n", -1);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isBlank()) {
        i++;
        continue;
      }
      if (trimmed.startsWith("```")) {
        i = renderRichCodeBlock(body, lines, i);
        continue;
      }
      Matcher heading = RICH_HEADING_LINE.matcher(line);
      if (heading.matches()) {
        TextFlow flow = richInlineFlow(heading.group(2).trim());
        flow.getStyleClass().add("jane-rich-heading-" + Math.min(4, heading.group(1).length()));
        body.getChildren().add(flow);
        i++;
        continue;
      }
      if (trimmed.startsWith(">")) {
        i = renderRichQuoteBlock(body, lines, i);
        continue;
      }
      Matcher unordered = RICH_UNORDERED_LIST_LINE.matcher(line);
      if (unordered.matches()) {
        i = renderRichListBlock(body, lines, i, false, 1);
        continue;
      }
      Matcher ordered = RICH_ORDERED_LIST_LINE.matcher(line);
      if (ordered.matches()) {
        i = renderRichListBlock(body, lines, i, true, safeParseInt(ordered.group(1), 1));
        continue;
      }
      i = renderRichParagraphBlock(body, lines, i);
    }
  }

  private int renderRichParagraphBlock(VBox body, String[] lines, int start) {
    StringBuilder paragraph = new StringBuilder();
    int i = start;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.trim();
      if (trimmed.isBlank()
          || trimmed.startsWith("```")
          || trimmed.startsWith(">")
          || RICH_HEADING_LINE.matcher(line).matches()
          || RICH_UNORDERED_LIST_LINE.matcher(line).matches()
          || RICH_ORDERED_LIST_LINE.matcher(line).matches()) {
        break;
      }
      if (paragraph.length() > 0) paragraph.append(' ');
      paragraph.append(trimmed);
      i++;
    }
    if (paragraph.length() > 0) {
      body.getChildren().add(richInlineFlow(paragraph.toString()));
    }
    return i;
  }

  private int renderRichCodeBlock(VBox body, String[] lines, int start) {
    String fence = lines[start].trim();
    String language = fence.length() > 3 ? fence.substring(3).trim() : "";
    StringBuilder code = new StringBuilder();
    int i = start + 1;
    while (i < lines.length) {
      String line = lines[i];
      if (line.trim().startsWith("```")) {
        i++;
        break;
      }
      if (code.length() > 0) code.append('\n');
      code.append(line);
      i++;
    }
    addRichCodeBlock(body, language, code.toString());
    return i;
  }

  private int renderRichQuoteBlock(VBox body, String[] lines, int start) {
    StringBuilder quote = new StringBuilder();
    int i = start;
    while (i < lines.length) {
      String trimmed = lines[i].trim();
      if (!trimmed.startsWith(">")) break;
      if (quote.length() > 0) quote.append('\n');
      quote.append(trimmed.length() > 1 ? trimmed.substring(1).trim() : "");
      i++;
    }
    TextFlow flow = richInlineFlow(quote.toString());
    flow.getStyleClass().add("jane-rich-quote");
    body.getChildren().add(flow);
    return i;
  }

  private int renderRichListBlock(VBox body, String[] lines, int start, boolean ordered, int orderedStart) {
    VBox list = new VBox(4);
    list.getStyleClass().add("jane-rich-list");
    int number = orderedStart;
    int i = start;
    while (i < lines.length) {
      String line = lines[i];
      Matcher matcher = ordered ? RICH_ORDERED_LIST_LINE.matcher(line) : RICH_UNORDERED_LIST_LINE.matcher(line);
      if (!matcher.matches()) break;
      String content = ordered ? matcher.group(2).trim() : matcher.group(1).trim();
      list.getChildren().add(richListItem(content, ordered ? number + "." : "-"));
      if (ordered) number++;
      i++;
    }
    body.getChildren().add(list);
    return i;
  }

  private HBox richListItem(String content, String markerText) {
    Label marker = new Label(markerText);
    marker.getStyleClass().add("jane-rich-list-marker");
    TextFlow flow = richInlineFlow(content);
    flow.getStyleClass().add("jane-rich-list-item");
    HBox row = new HBox(6, marker, flow);
    row.setAlignment(Pos.TOP_LEFT);
    HBox.setHgrow(flow, Priority.ALWAYS);
    return row;
  }

  private void addRichCodeBlock(VBox body, String language, String code) {
    VBox box = new VBox(4);
    box.getStyleClass().add("jane-rich-code-wrapper");
    box.setMaxWidth(Double.MAX_VALUE);
    if (language != null && !language.isBlank()) {
      Label lang = new Label(language);
      lang.getStyleClass().add("jane-rich-code-lang");
      box.getChildren().add(lang);
    }
    Label codeLabel = new Label(code == null ? "" : code);
    codeLabel.setWrapText(true);
    codeLabel.setMaxWidth(Double.MAX_VALUE);
    codeLabel.getStyleClass().add("jane-rich-code-block");
    box.getChildren().add(codeLabel);
    body.getChildren().add(box);
  }

  private TextFlow richInlineFlow(String source) {
    TextFlow flow = new TextFlow();
    flow.setLineSpacing(2);
    flow.setMaxWidth(Double.MAX_VALUE);
    flow.getStyleClass().add("jane-rich-flow");
    flow.maxWidthProperty().bind(transcriptScroll.viewportBoundsProperty()
        .map(bounds -> Math.max(120.0, bounds.getWidth() - 58.0)));
    appendRichInlineMarkdown(flow, source == null ? "" : source);
    return flow;
  }

  private void appendRichInlineMarkdown(TextFlow flow, String source) {
    int i = 0;
    while (i < source.length()) {
      if (source.startsWith("`", i)) {
        int end = source.indexOf('`', i + 1);
        if (end > i + 1) {
          appendRichText(flow, source.substring(i + 1, end), "jane-rich-inline-code");
          i = end + 1;
          continue;
        }
      }
      if (source.startsWith("**", i) || source.startsWith("__", i)) {
        String marker = source.substring(i, i + 2);
        int end = source.indexOf(marker, i + 2);
        if (end > i + 2) {
          appendRichText(flow, source.substring(i + 2, end), "jane-rich-bold");
          i = end + 2;
          continue;
        }
      }
      if (isSimpleItalicMarker(source, i)) {
        String marker = source.substring(i, i + 1);
        int end = source.indexOf(marker, i + 1);
        if (end > i + 1) {
          appendRichText(flow, source.substring(i + 1, end), "jane-rich-italic");
          i = end + 1;
          continue;
        }
      }
      int next = nextRichInlineToken(source, i + 1);
      int end = next < 0 ? source.length() : next;
      appendRichText(flow, source.substring(i, end));
      i = end;
    }
  }

  private void appendRichText(TextFlow flow, String content, String... styleClasses) {
    if (content == null || content.isEmpty()) return;
    Text text = new Text(content);
    text.getStyleClass().add("jane-rich-text");
    if (styleClasses != null) {
      for (String styleClass : styleClasses) {
        if (styleClass != null && !styleClass.isBlank()) text.getStyleClass().add(styleClass);
      }
    }
    flow.getChildren().add(text);
  }

  private int nextRichInlineToken(String source, int fromIndex) {
    int best = -1;
    for (char ch : new char[] {'`', '*', '_'}) {
      int idx = source.indexOf(ch, fromIndex);
      if (idx >= 0 && (best < 0 || idx < best)) best = idx;
    }
    return best;
  }

  private static boolean isSimpleItalicMarker(String source, int index) {
    if (source == null || index < 0 || index >= source.length()) return false;
    char marker = source.charAt(index);
    if (marker != '*' && marker != '_') return false;
    if (index + 1 < source.length() && source.charAt(index + 1) == marker) return false;
    if (index > 0 && Character.isLetterOrDigit(source.charAt(index - 1))) return false;
    return index + 1 < source.length() && !Character.isWhitespace(source.charAt(index + 1));
  }

  private static String normalizeRichMarkdown(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n').trim();
  }

  private static int safeParseInt(String value, int fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
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

  private record JaneToolContext(String prompt, String summary) {
    private JaneToolContext {
      prompt = prompt == null ? "" : prompt;
      summary = summary == null ? "" : summary;
    }
  }

  private record JaneInteractionResult(
      JaneChatResponse response,
      String displayAnswer,
      JanePatchProposal patchProposal,
      String toolContextSummary
  ) {
    private JaneInteractionResult {
      displayAnswer = displayAnswer == null ? "" : displayAnswer;
      toolContextSummary = toolContextSummary == null ? "" : toolContextSummary;
    }
  }

  private record JaneFileSnippet(String relativePath, long byteSize, String snippet) {
    private JaneFileSnippet {
      relativePath = relativePath == null ? "" : relativePath;
      snippet = snippet == null ? "" : snippet;
    }
  }

  private record JaneScoredFile(Path path, String relativePath, String text, long byteSize, int score) {
    private JaneScoredFile {
      relativePath = relativePath == null ? "" : relativePath;
      text = text == null ? "" : text;
    }
  }

  private record JanePatchProposal(String summary, List<JanePatchChange> changes, List<String> errors) {
    private JanePatchProposal {
      summary = summary == null || summary.isBlank() ? "Jane proposed file changes" : summary.trim();
      changes = changes == null ? List.of() : List.copyOf(changes);
      errors = errors == null ? List.of() : List.copyOf(errors);
    }

    boolean hasErrors() {
      return !errors.isEmpty();
    }

    String errorText() {
      return String.join("\n", errors);
    }

    String fileList() {
      if (changes.isEmpty()) return "(none)";
      List<String> files = new ArrayList<>();
      for (JanePatchChange change : changes) {
        if (change != null && !change.path().isBlank()) files.add(change.path());
      }
      return files.isEmpty() ? "(none)" : String.join(", ", files);
    }
  }

  private record JanePatchChange(
      String path,
      String action,
      String findText,
      String replaceText,
      String content,
      Path absolutePath
  ) {
    private JanePatchChange {
      path = path == null ? "" : path.trim().replace('\\', '/');
      action = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
      findText = findText == null ? "" : findText;
      replaceText = replaceText == null ? "" : replaceText;
      content = content == null ? "" : content;
    }

    JanePatchChange withAbsolutePath(Path path) {
      return new JanePatchChange(this.path, action, findText, replaceText, content, path);
    }
  }

  private record BlockRead(String text, int nextIndex, List<String> errors) {
    private BlockRead {
      text = text == null ? "" : text;
      errors = errors == null ? List.of() : List.copyOf(errors);
    }
  }

  private static final class PatchChangeBuilder {
    private final String path;
    private String action;
    private String findText;
    private String replaceText;
    private String content;

    private PatchChangeBuilder(String path) {
      this.path = path == null ? "" : path.trim();
    }
  }

  private record JaneWritePlan(JanePatchChange change, boolean existed, String beforeText, String afterText) {
    private JaneWritePlan {
      beforeText = beforeText == null ? "" : beforeText;
      afterText = afterText == null ? "" : afterText;
    }
  }

  private record JaneFileBackup(String relativePath, Path path, boolean existed, String beforeText) {
    private JaneFileBackup {
      relativePath = relativePath == null ? "" : relativePath;
      beforeText = beforeText == null ? "" : beforeText;
    }
  }

  private record JaneAppliedPatch(String summary, List<JaneFileBackup> backups, Instant appliedAt) {
    private JaneAppliedPatch {
      summary = summary == null || summary.isBlank() ? "Jane approved edit" : summary.trim();
      backups = backups == null ? List.of() : List.copyOf(backups);
      appliedAt = appliedAt == null ? Instant.now() : appliedAt;
    }

    JanePatchProposal toProposal() {
      List<JanePatchChange> changes = new ArrayList<>();
      for (JaneFileBackup backup : backups) {
        changes.add(new JanePatchChange(backup.relativePath(), "undo", "", "", "", backup.path()));
      }
      return new JanePatchProposal(summary, changes, List.of());
    }
  }

  private record GeminiSettings(Path path, boolean hasApiKey, String model) {}
}

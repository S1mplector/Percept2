package com.jvn.editor.ui;

import com.jvn.core.generalhelp.HelpAgentVote;
import com.jvn.core.generalhelp.HelpArticle;
import com.jvn.core.generalhelp.JaneAssistant;
import com.jvn.core.generalhelp.JaneChatResponse;
import com.jvn.core.generalhelp.JaneTrainingCorpus;
import com.jvn.core.generalhelp.TagiGeneralHelpSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Sidebar chatbot for Jane, JVN's local assistant. */
public class JaneAssistantView extends BorderPane {
  private static final Pattern HEADING_LINE = Pattern.compile("^#\\s+(.+)$");
  private static final int SUMMARY_LIMIT = 220;

  private final TagiGeneralHelpSystem generalHelp = new TagiGeneralHelpSystem();
  private final JaneAssistant jane = new JaneAssistant(generalHelp);
  private final TextField askField = new TextField();
  private final VBox transcriptBox = new VBox(8);
  private final VBox evidenceBox = new VBox(8);
  private final ScrollPane transcriptScroll = new ScrollPane(transcriptBox);
  private final Label statusLabel = new Label("Indexing JVN docs...");
  private final Label modelLabel = new Label("Model: " + jane.model().name());
  private final Button askButton = new Button("Ask");
  private final Button refreshButton = new Button("Refresh");
  private final Button clearButton = new Button("Clear");
  private final Button sourcesButton = new Button("Sources");

  private File workspaceRoot;
  private File projectRoot;
  private Consumer<File> onOpenDoc;
  private JaneChatResponse lastResponse;
  private boolean indexing;
  private boolean pendingRefresh;

  public JaneAssistantView() {
    getStyleClass().addAll("jane-assistant-root", "sidebar-tool-root");
    buildUi();
  }

  public void setWorkspaceRoot(File root) {
    this.workspaceRoot = normalizeDir(root);
    if (this.workspaceRoot != null) {
      System.setProperty("jvn.jane.workspaceRoot", this.workspaceRoot.getAbsolutePath());
    }
    refreshModel();
    refreshCorpus();
  }

  public void setProjectRoot(File root) {
    this.projectRoot = normalizeDir(root);
    refreshCorpus();
  }

  public void setOnOpenDoc(Consumer<File> onOpenDoc) {
    this.onOpenDoc = onOpenDoc;
  }

  public void refreshCorpus() {
    if (indexing) {
      pendingRefresh = true;
      return;
    }
    indexing = true;
    pendingRefresh = false;
    setInputDisabled(true);
    statusLabel.setText("Indexing JVN docs...");
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
      setInputDisabled(false);
      statusLabel.setText("Ready. Indexed " + articles.size() + " training articles.");
      modelLabel.setText("Model: " + jane.model().name());
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
      setInputDisabled(false);
      statusLabel.setText("Index failed. Jane will use the built-in corpus.");
      modelLabel.setText("Model: " + jane.model().name());
    });
    Thread thread = new Thread(task, "jane-corpus-index");
    thread.setDaemon(true);
    thread.start();
  }

  private void refreshModel() {
    jane.reloadConfiguredModel();
    modelLabel.setText("Model: " + jane.model().name());
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

    modelLabel.getStyleClass().add("jane-model-label");
    statusLabel.getStyleClass().add("sidebar-tool-status");
    VBox header = new VBox(6, headerRow, modelLabel, statusLabel);
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

    HBox inputRow = new HBox(6, askField, askButton);
    inputRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(askField, Priority.ALWAYS);
    HBox actions = new HBox(6, sourcesButton, refreshButton, clearButton);
    actions.setAlignment(Pos.CENTER_LEFT);
    VBox footer = new VBox(8, new Separator(), inputRow, actions);
    footer.getStyleClass().add("sidebar-tool-footer");
    footer.setPadding(new Insets(10));

    VBox center = new VBox(8, transcriptScroll, evidenceBox);
    center.setPadding(new Insets(10));
    VBox.setVgrow(transcriptScroll, Priority.ALWAYS);
    setTop(header);
    setCenter(center);
    setBottom(footer);
  }

  private void askJane() {
    String query = askField.getText();
    if (query == null || query.isBlank()) {
      askField.requestFocus();
      return;
    }
    askField.clear();
    addUserBubble(query.trim());
    setInputDisabled(true);
    statusLabel.setText("Jane is thinking...");

    Task<JaneChatResponse> task = new Task<>() {
      @Override
      protected JaneChatResponse call() {
        return jane.ask(query);
      }
    };
    task.setOnSucceeded(e -> {
      JaneChatResponse response = task.getValue();
      setInputDisabled(false);
      statusLabel.setText("Ready. Indexed " + generalHelp.articles().size() + " training articles.");
      modelLabel.setText("Model: " + (response == null ? jane.model().name() : response.modelName()));
      addAssistantBubble(response == null ? "I could not produce a response." : response.answer());
      lastResponse = response;
      hideEvidence();
      sourcesButton.setDisable(!hasEvidence(response));
    });
    task.setOnFailed(e -> {
      setInputDisabled(false);
      statusLabel.setText("Jane response failed: " + safeMessage(task.getException()));
      addAssistantBubble("I hit a configured model error. Re-index docs or check Jane's Gemini or ONNX settings.");
    });
    Thread thread = new Thread(task, "jane-chat");
    thread.setDaemon(true);
    thread.start();
  }

  private void clearChat() {
    jane.clearHistory();
    lastResponse = null;
    transcriptBox.getChildren().clear();
    hideEvidence();
    sourcesButton.setDisable(true);
    addAssistantBubble("Chat cleared. Ask me about JVN.");
    Platform.runLater(() -> askField.requestFocus());
  }

  private void refreshJane() {
    refreshModel();
    refreshCorpus();
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
    addBubble("Jane", text, "jane-chat-bubble-assistant");
  }

  private void addBubble(String speaker, String text, String styleClass) {
    Label speakerLabel = new Label(speaker);
    speakerLabel.getStyleClass().add("jane-chat-speaker");
    Label body = new Label(text == null ? "" : text);
    body.setWrapText(true);
    body.setMaxWidth(Double.MAX_VALUE);
    body.getStyleClass().add("jane-chat-body");
    VBox bubble = new VBox(3, speakerLabel, body);
    bubble.getStyleClass().addAll("jane-chat-bubble", styleClass);
    bubble.setMaxWidth(Double.MAX_VALUE);
    transcriptBox.getChildren().add(bubble);
    Platform.runLater(() -> transcriptScroll.setVvalue(1.0));
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
}

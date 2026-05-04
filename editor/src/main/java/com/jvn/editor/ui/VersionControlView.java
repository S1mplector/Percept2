package com.jvn.editor.ui;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import com.jvn.editor.vcs.GitVcsService;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Team-focused Git control panel for JVN projects.
 */
public class VersionControlView extends BorderPane {
  private final GitVcsService vcs = new GitVcsService();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-vcs-worker");
    t.setDaemon(true);
    return t;
  });

  private final Label titleLabel = new Label("Version Control");
  private final Label repoLabel = new Label("No project loaded");
  private final Label toolLabel = new Label("Git: --");
  private final Label branchLabel = new Label("Branch: --");
  private final Label syncLabel = new Label("Sync: --");
  private final Label summaryLabel = new Label("Status: --");
  private final Label conflictLabel = new Label();
  private final Label remoteLabel = new Label("Remote: not configured");
  private final Button btnConfigureRemote = new Button("Add Remote");
  private final Label initTitleLabel = new Label("\u26a0 Repository Not Initialized");
  private final Label initHintLabel = new Label("Repository is not initialized for this project.");
  private final VBox initBox = new VBox(6);

  private final VBox setupBox = new VBox(8);

  private final CheckBox chkInitCommit = new CheckBox("Create initial commit");

  private final Button btnRefresh = iconButton("vcs-icon-refresh", "Refresh status");
  private final Button btnInitialize = new Button("Initialize");
  private final Button btnFetch = iconButton("vcs-icon-fetch", "Fetch all remotes");
  private final Button btnPull = iconButton("vcs-icon-pull", "Pull with rebase");
  private final Button btnPush = iconButton("vcs-icon-push", "Push to remote");
  private final Button btnCommit = iconButton("vcs-icon-commit", "Commit all changes");
  private final Button btnStash = iconButton("vcs-icon-stash", "Stash changes");
  private final Button btnStashPop = iconButton("vcs-icon-stash-pop", "Pop stash");
  private final Button btnStageSelected = iconButton("vcs-icon-stage", "Stage selected");
  private final Button btnUnstageSelected = iconButton("vcs-icon-unstage", "Unstage selected");
  private final Button btnDiscardSelected = iconButton("vcs-icon-discard", "Discard changes");
  private final Button btnDiffSelected = iconButton("vcs-icon-diff", "Show diff");
  private final ComboBox<String> cbBranch = new ComboBox<>();
  private final Button btnNewBranch = iconButton("vcs-icon-new-branch", "Create branch");

  private static Button iconButton(String iconClass, String tooltip) {
    Button btn = new Button();
    btn.getStyleClass().add("vcs-icon-btn");
    javafx.scene.layout.Region icon = CssIcon.prepare(new javafx.scene.layout.Region());
    icon.getStyleClass().addAll("vcs-icon", iconClass);
    btn.setGraphic(icon);
    btn.setTooltip(new Tooltip(tooltip));
    return btn;
  }

  private final TextField txtCommitMessage = new TextField();
  private final ListView<GitVcsService.StatusEntry> listChanges = new ListView<>();
  private final TextArea txtLog = new TextArea();

  private File projectRoot;
  private boolean gitAvailable;
  private boolean repositoryInitialized;
  private boolean busy;
  private Consumer<String> onOpenRelativePath;
  private Timeline autoRefreshTimer;
  private boolean disposed;

  public VersionControlView() {
    getStyleClass().addAll("version-control-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vcs-title", "sidebar-tool-title");
    repoLabel.getStyleClass().addAll("vcs-muted", "sidebar-tool-subtitle");
    toolLabel.getStyleClass().add("vcs-muted");
    conflictLabel.getStyleClass().add("vcs-conflict");
    conflictLabel.setVisible(false);
    conflictLabel.setManaged(false);
    remoteLabel.getStyleClass().addAll("vcs-remote", "vcs-remote-missing");
    btnConfigureRemote.getStyleClass().add("vcs-text-button");
    btnConfigureRemote.setOnAction(e -> showAddRemoteDialog());
    initHintLabel.getStyleClass().add("vcs-banner-copy");
    initHintLabel.setWrapText(true);
    txtCommitMessage.getStyleClass().add("vcs-text-field");
    cbBranch.getStyleClass().add("vcs-combo");
    listChanges.getStyleClass().add("vcs-list");
    txtLog.getStyleClass().add("vcs-log");

    chkInitCommit.setSelected(true);

    txtCommitMessage.setPromptText("Commit message...");
    txtCommitMessage.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER && !txtCommitMessage.getText().isBlank()) runCommit();
    });

    txtLog.setEditable(false);
    txtLog.setWrapText(true);
    txtLog.setPrefRowCount(6);

    listChanges.setPlaceholder(new Label("No changed files"));
    listChanges.setCellFactory(lv -> new StatusCell());
    listChanges.setOnMouseClicked(e -> {
      if (e.getClickCount() < 2) return;
      GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
      if (entry == null) return;
      openChangedEntry(entry);
    });
    listChanges.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.ENTER) return;
      GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
      if (entry == null) return;
      openChangedEntry(entry);
    });

    btnRefresh.setOnAction(e -> refreshStatus());
    btnInitialize.setOnAction(e -> initializeRepository());
    btnInitialize.getStyleClass().add("vcs-icon-btn");
    javafx.scene.layout.Region initIcon = CssIcon.prepare(new javafx.scene.layout.Region());
    initIcon.getStyleClass().addAll("vcs-icon", "vcs-icon-init");
    btnInitialize.setGraphic(initIcon);
    btnFetch.setOnAction(e -> runFetch());
    btnPull.setOnAction(e -> runPull());
    btnPush.setOnAction(e -> runPush());
    btnCommit.setOnAction(e -> runCommit());
    btnStash.setOnAction(e -> runStash());
    btnStashPop.setOnAction(e -> runStashPop());
    btnStageSelected.setOnAction(e -> runStageSelected());
    btnUnstageSelected.setOnAction(e -> runUnstageSelected());
    btnDiscardSelected.setOnAction(e -> runDiscardSelected());
    btnDiffSelected.setOnAction(e -> runDiffSelected());
    btnNewBranch.setOnAction(e -> runCreateBranch());
    cbBranch.setEditable(false);
    cbBranch.setPromptText("Switch branch...");
    cbBranch.setOnAction(e -> {
      String selected = cbBranch.getValue();
      if (selected != null && !selected.isBlank()) runSwitchBranch(selected);
    });

    // Sync toolbar: refresh, fetch, pull, push
    HBox syncRow = new HBox(4, btnRefresh, btnFetch, btnPull, btnPush);
    syncRow.setAlignment(Pos.CENTER_LEFT);

    // Stash toolbar
    HBox stashRow = new HBox(4, btnStash, btnStashPop);
    stashRow.setAlignment(Pos.CENTER_LEFT);

    // Combined toolbar
    HBox toolbar = new HBox(12, syncRow, stashRow);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setPadding(new Insets(4, 0, 4, 0));

    // Init controls - styled as prominent warning banner
    HBox initOptionsRow = new HBox(12, chkInitCommit);
    HBox initActionRow = new HBox(6, btnInitialize);
    initTitleLabel.getStyleClass().addAll("vcs-banner-title", "vcs-banner-title-warn");
    initBox.getStyleClass().addAll("vcs-banner", "vcs-banner-warn");
    initBox.getChildren().addAll(initTitleLabel, initHintLabel, initOptionsRow, initActionRow);

    // Setup guide banner - shown when repo exists but no remote configured
    Label setupTitle = new Label("\u2699 Setup Required");
    setupTitle.getStyleClass().addAll("vcs-banner-title", "vcs-banner-title-info");
    Label setupDesc = new Label("Your project needs a remote repository to push, pull, and collaborate.");
    setupDesc.getStyleClass().add("vcs-banner-copy");
    setupDesc.setWrapText(true);

    // Option A: Create GitHub repo directly (if gh CLI available)
    Label optionALabel = new Label("Option A — Create a new GitHub repository:");
    optionALabel.getStyleClass().add("vcs-option-label");
    ComboBox<String> cbVisibility = new ComboBox<>(FXCollections.observableArrayList("Private", "Public"));
    cbVisibility.setValue("Private");
    cbVisibility.getStyleClass().add("vcs-combo");
    cbVisibility.setMaxWidth(100);
    Button btnCreateGitHub = new Button("Create GitHub Repository");
    btnCreateGitHub.getStyleClass().addAll("vcs-action-button", "vcs-action-button-success");
    btnCreateGitHub.setOnAction(e -> {
      boolean isPrivate = "Private".equals(cbVisibility.getValue());
      showCreateGitHubRepoDialog(isPrivate);
    });
    HBox ghRow = new HBox(8, cbVisibility, btnCreateGitHub);
    ghRow.setAlignment(Pos.CENTER_LEFT);
    Label ghHint = new Label("Requires GitHub CLI (gh). Installs remote + pushes in one step.");
    ghHint.getStyleClass().add("vcs-hint");
    VBox optionA = new VBox(4, optionALabel, ghRow, ghHint);

    // Option B: Manually connect existing repo
    Label optionBLabel = new Label("Option B — Connect an existing remote repository:");
    optionBLabel.getStyleClass().add("vcs-option-label");
    Button btnSetupRemote = new Button("Connect to Remote Repository");
    btnSetupRemote.getStyleClass().addAll("vcs-action-button", "vcs-action-button-accent");
    btnSetupRemote.setOnAction(e -> showAddRemoteDialog());
    Label manualHint = new Label("Already created a repo on GitHub/GitLab/Bitbucket? Paste its URL.");
    manualHint.getStyleClass().add("vcs-hint");
    VBox optionB = new VBox(4, optionBLabel, btnSetupRemote, manualHint);

    Label setupSkip = new Label("You can also work offline and add a remote later.");
    setupSkip.getStyleClass().add("vcs-hint");

    setupBox.getStyleClass().addAll("vcs-banner", "vcs-banner-info");
    setupBox.getChildren().addAll(setupTitle, setupDesc, optionA, optionB, setupSkip);
    setupBox.setVisible(false);
    setupBox.setManaged(false);

    // Commit row
    HBox commitRow = new HBox(4, txtCommitMessage, btnCommit);
    HBox.setHgrow(txtCommitMessage, Priority.ALWAYS);
    commitRow.setAlignment(Pos.CENTER_LEFT);

    // Branch row
    cbBranch.setMaxWidth(120);
    HBox branchRow = new HBox(4, cbBranch, btnNewBranch);
    branchRow.setAlignment(Pos.CENTER_LEFT);

    // File action toolbar
    HBox fileActionRow = new HBox(4, btnStageSelected, btnUnstageSelected, btnDiscardSelected, btnDiffSelected);
    fileActionRow.setAlignment(Pos.CENTER_LEFT);

    // Remote row
    HBox remoteRow = new HBox(6, remoteLabel, btnConfigureRemote);
    remoteRow.setAlignment(Pos.CENTER_LEFT);

    // Header section
    VBox statusBox = new VBox(2, branchLabel, remoteRow, syncLabel, summaryLabel, conflictLabel);
    statusBox.getStyleClass().add("vcs-status-stack");

    VBox top = new VBox(
        6,
        titleLabel,
        repoLabel,
        toolLabel,
        statusBox,
        initBox,
        setupBox,
        toolbar,
        branchRow,
        commitRow
    );
    top.getStyleClass().add("vcs-top");
    top.setPadding(new Insets(8));

    Label changesLabel = new Label("Changes");
    changesLabel.getStyleClass().add("vcs-section-label");
    Label logLabel = new Label("Log");
    logLabel.getStyleClass().add("vcs-section-label");
    VBox center = new VBox(4, changesLabel, fileActionRow, listChanges, logLabel, txtLog);
    center.getStyleClass().add("vcs-center");
    center.setPadding(new Insets(0, 10, 10, 10));
    VBox.setVgrow(listChanges, Priority.ALWAYS);

    setTop(top);
    setCenter(center);

    setInitControlsVisible(false, null);
    updateToolAvailabilityLabel(false);
    setBusy(false);

    // Auto-refresh every 30 seconds
    autoRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
      if (!busy && projectRoot != null) refreshStatus();
    }));
    autoRefreshTimer.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimer.play();
  }

  public void setOnOpenRelativePath(Consumer<String> onOpenRelativePath) {
    this.onOpenRelativePath = onOpenRelativePath;
  }

  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    this.projectRoot = projectRoot;
    repoLabel.setText(projectRoot == null ? "No project loaded" : "Project: " + projectRoot.getAbsolutePath());
    refreshStatus();
  }

  public void refreshStatus() {
    if (disposed) return;
    runAsync("Refresh status", () -> {
      boolean git = vcs.isGitAvailable();
      Platform.runLater(() -> {
        gitAvailable = git;
        updateToolAvailabilityLabel(git);
      });

      if (projectRoot == null) {
        Platform.runLater(() -> {
          repositoryInitialized = false;
          branchLabel.setText("Branch: --");
          syncLabel.setText("Sync: --");
          summaryLabel.setText("Status: no project selected");
          listChanges.getItems().clear();
          setInitControlsVisible(false, null);
          updateControlsForState();
        });
        return;
      }

      if (!git) {
        Platform.runLater(() -> {
          repositoryInitialized = false;
          branchLabel.setText("Branch: --");
          syncLabel.setText("Sync: --");
          summaryLabel.setText("Status: Git unavailable");
          listChanges.getItems().clear();
          setInitControlsVisible(false, null);
          updateControlsForState();
        });
        return;
      }

      if (!vcs.isRepository(projectRoot)) {
        Platform.runLater(() -> {
          repositoryInitialized = false;
          branchLabel.setText("Branch: (not initialized)");
          syncLabel.setText("Sync: --");
          summaryLabel.setText("Status: repository not initialized");
          listChanges.getItems().clear();
          setInitControlsVisible(true, "This project is not a repository yet. Initialize it to enable commit/pull/push.");
          updateControlsForState();
        });
        appendLog("Repository is not initialized. Use Initialize Repository.");
        return;
      }

      try {
        GitVcsService.RepositoryStatus status = vcs.getRepositoryStatus(projectRoot);
        boolean hasRemote = vcs.hasRemote(projectRoot);
        String remoteUrl = hasRemote ? vcs.getRemoteUrl(projectRoot) : null;
        Platform.runLater(() -> {
          repositoryInitialized = true;
          branchLabel.setText("Branch: " + safe(status.branch()));
          String upstream = status.upstream() == null || status.upstream().isBlank() ? "(no upstream)" : status.upstream();
          syncLabel.setText("Sync: " + upstream + "  [ahead " + status.ahead() + ", behind " + status.behind() + "]");
          if (status.clean()) {
            summaryLabel.setText("Status: clean working tree");
          } else {
            summaryLabel.setText("Status: " + status.entries().size() + " changed files");
          }
          // Update remote status and setup guide
          remoteLabel.getStyleClass().removeAll("vcs-remote-configured", "vcs-remote-missing");
          if (hasRemote && remoteUrl != null) {
            remoteLabel.setText("Remote: " + remoteUrl);
            remoteLabel.getStyleClass().add("vcs-remote-configured");
            btnConfigureRemote.setText("Change");
            setupBox.setVisible(false);
            setupBox.setManaged(false);
          } else {
            remoteLabel.setText("Remote: not configured");
            remoteLabel.getStyleClass().add("vcs-remote-missing");
            btnConfigureRemote.setText("Add Remote");
            setupBox.setVisible(true);
            setupBox.setManaged(true);
          }
          listChanges.setItems(FXCollections.observableArrayList(status.entries()));
          setInitControlsVisible(false, null);
          updateControlsForState();
        });
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
    });
  }

  private void initializeRepository() {
    runAsync("Initialize repository", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before initializing version control.");
        return;
      }
      try {
        boolean initialCommit = chkInitCommit.isSelected();
        vcs.bootstrapRepository(projectRoot, initialCommit, "Initialize JVN project scaffold");
        appendLog("Repository initialized.");
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runFetch() {
    runAsync("Fetch", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before fetching.");
        return;
      }
      try {
        appendCommandResult(vcs.fetch(projectRoot));
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runCommit() {
    runAsync("Commit", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before committing.");
        return;
      }
      String message = txtCommitMessage.getText();
      if (message == null || message.isBlank()) {
        message = "Update project files";
      }
      try {
        GitVcsService.CommandResult result = vcs.commitAll(projectRoot, message.trim());
        appendCommandResult(result);
        Platform.runLater(() -> txtCommitMessage.clear());
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runPull() {
    runAsync("Pull", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before pulling.");
        return;
      }
      try {
        appendCommandResult(vcs.pullRebase(projectRoot));
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runPush() {
    runAsync("Push", () -> {
      if (projectRoot == null) {
        appendLog("Select a project before pushing.");
        return;
      }
      try {
        appendCommandResult(vcs.pushSafe(projectRoot));
      } catch (Exception ex) {
        appendLog(ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runStash() {
    runAsync("Stash", () -> {
      if (projectRoot == null) return;
      try {
        appendCommandResult(vcs.stash(projectRoot, null));
      } catch (Exception ex) {
        appendLog("Stash failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runStashPop() {
    runAsync("Stash Pop", () -> {
      if (projectRoot == null) return;
      try {
        appendCommandResult(vcs.stashPop(projectRoot));
      } catch (Exception ex) {
        appendLog("Stash pop failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runStageSelected() {
    GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    runAsync("Stage", () -> {
      try {
        vcs.stageFile(projectRoot, entry.path());
        appendLog("Staged: " + entry.path());
      } catch (Exception ex) {
        appendLog("Stage failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runUnstageSelected() {
    GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    runAsync("Unstage", () -> {
      try {
        vcs.unstageFile(projectRoot, entry.path());
        appendLog("Unstaged: " + entry.path());
      } catch (Exception ex) {
        appendLog("Unstage failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runDiscardSelected() {
    GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    runAsync("Discard", () -> {
      try {
        vcs.discardFile(projectRoot, entry.path());
        appendLog("Discarded: " + entry.path());
      } catch (Exception ex) {
        appendLog("Discard failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runDiffSelected() {
    GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    runAsync("Diff", () -> {
      try {
        String diff = vcs.diffFile(projectRoot, entry.path());
        if (diff.isBlank()) {
          appendLog("No diff available for: " + entry.path());
        } else {
          appendLog("--- diff " + entry.path() + " ---\n" + diff);
        }
      } catch (Exception ex) {
        appendLog("Diff failed: " + ex.getMessage());
      }
    });
  }

  private void runSwitchBranch(String branchName) {
    runAsync("Switch branch", () -> {
      try {
        appendCommandResult(vcs.switchBranch(projectRoot, branchName));
      } catch (Exception ex) {
        appendLog("Branch switch failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runCreateBranch() {
    String name = cbBranch.getEditor() != null ? cbBranch.getEditor().getText() : null;
    if (name == null || name.isBlank()) {
      appendLog("Enter a branch name in the branch selector to create a new branch.");
      return;
    }
    runAsync("Create branch", () -> {
      try {
        appendCommandResult(vcs.createBranch(projectRoot, name.trim()));
      } catch (Exception ex) {
        appendLog("Create branch failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void showAddRemoteDialog() {
    VBox content = new VBox(12);
    content.getStyleClass().add("editor-dialog-form");

    Label nameLabel = new Label("Remote Name");
    nameLabel.getStyleClass().add("editor-dialog-field-label");
    TextField nameField = new TextField("origin");
    nameField.getStyleClass().add("editor-dialog-text-field");
    nameField.setPromptText("origin");
    Label nameHint = new Label("Usually \"origin\" — only change if you know what you're doing");
    nameHint.getStyleClass().add("editor-dialog-message");

    Label urlLabel = new Label("Repository URL");
    urlLabel.getStyleClass().add("editor-dialog-field-label");
    TextField urlField = new TextField();
    urlField.getStyleClass().add("editor-dialog-text-field");
    urlField.setPromptText("https://github.com/username/repository.git");
    urlField.setPrefWidth(380);
    Label urlHint = new Label("Example: https://github.com/YourName/YourProject.git");
    urlHint.getStyleClass().add("editor-dialog-message");

    Label privacyNote = new Label(
        "\uD83D\uDD12 Public vs Private: Create your repository on GitHub/GitLab first, then paste the URL here.\n" +
        "The repository's visibility (public/private) is set on the hosting service, not in Git.");
    privacyNote.getStyleClass().add("editor-dialog-message");
    privacyNote.setWrapText(true);
    privacyNote.setMaxWidth(380);

    content.getChildren().addAll(
        nameLabel, nameField, nameHint,
        urlLabel, urlField, urlHint,
        privacyNote
    );
    Optional<String> result = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Add Git Remote",
        "Connect to a remote repository by pasting its Git URL.",
        content,
        urlField,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("add", "Add Remote", null));
    if (result.isPresent() && "add".equals(result.get()) && !urlField.getText().trim().isBlank()) {
      runAddRemote(nameField.getText().trim(), urlField.getText().trim());
    }
  }

  private void showCreateGitHubRepoDialog(boolean isPrivate) {
    // First check gh CLI availability
    if (!vcs.isGhCliAvailable()) {
      EditorDialogs.showTextBlock(
          getScene() == null ? null : getScene().getWindow(),
          "GitHub CLI Not Found",
          "GitHub CLI (gh) is not installed",
          "To create repositories directly, install the GitHub CLI:\n\n" +
          "  macOS:   brew install gh\n" +
          "  Windows: winget install GitHub.cli\n" +
          "  Linux:   https://github.com/cli/cli#installation\n\n" +
          "After installing, run:  gh auth login\n\n" +
          "Alternatively, create a repository on github.com and use\n\"Connect to Remote Repository\" to paste the URL.",
          "Close");
      return;
    }

    if (!vcs.isGhCliAuthenticated()) {
      EditorDialogs.showTextBlock(
          getScene() == null ? null : getScene().getWindow(),
          "GitHub CLI Not Authenticated",
          "You need to log in to GitHub first",
          "Run this command in your terminal:\n\n  gh auth login\n\nThen try again.",
          "Close");
      return;
    }

    VBox content = new VBox(10);
    content.getStyleClass().add("editor-dialog-form");

    Label nameLabel = new Label("Repository Name");
    nameLabel.getStyleClass().add("editor-dialog-field-label");
    String defaultName = projectRoot != null ? projectRoot.getName() : "my-project";
    TextField repoNameField = new TextField(defaultName);
    repoNameField.getStyleClass().add("editor-dialog-text-field");
    repoNameField.setPrefWidth(350);

    Label visLabel = new Label(isPrivate
        ? "\uD83D\uDD12 This repository will be private — only you and collaborators can see it."
        : "\uD83C\uDF10 This repository will be public — anyone can see it.");
    visLabel.getStyleClass().addAll("vcs-dialog-visibility", isPrivate ? "vcs-dialog-visibility-private" : "vcs-dialog-visibility-public");
    visLabel.setWrapText(true);
    visLabel.setMaxWidth(350);

    content.getChildren().addAll(nameLabel, repoNameField, visLabel);

    Optional<String> result = EditorDialogs.show(
        getScene() == null ? null : getScene().getWindow(),
        "Create GitHub Repository",
        "This will create a new repository on your GitHub account, set it as the remote, and push your code.",
        content,
        repoNameField,
        EditorDialogs.ActionSpec.neutral("cancel", "Cancel", null),
        EditorDialogs.ActionSpec.accent("create", isPrivate ? "Create Private Repo" : "Create Public Repo", null));
    if (result.isPresent() && "create".equals(result.get()) && !repoNameField.getText().trim().isBlank()) {
      runCreateGitHubRepo(repoNameField.getText().trim(), isPrivate);
    }
  }

  private void runCreateGitHubRepo(String repoName, boolean isPrivate) {
    runAsync("Create GitHub repo", () -> {
      try {
        appendLog("Creating " + (isPrivate ? "private" : "public") + " GitHub repository: " + repoName + "...");
        appendCommandResult(vcs.createGitHubRepo(projectRoot, repoName, isPrivate));
        appendLog("Repository created successfully on GitHub!");
        appendLog("Setting up remote 'origin'...");
        
        appendLog("Pushing code to GitHub...");
        try {
          appendCommandResult(vcs.pushSafe(projectRoot));
          appendLog("Code pushed successfully! Your project is now on GitHub.");
        } catch (Exception pushEx) {
          appendLog("⚠ " + pushEx.getMessage());
          appendLog("\nRepository was created but initial push failed.");
          appendLog("You can push manually later using the Push button.");
        }
      } catch (Exception ex) {
        appendLog("Create GitHub repo failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void runAddRemote(String name, String url) {
    runAsync("Add remote", () -> {
      try {
        appendCommandResult(vcs.addRemote(projectRoot, name, url));
        appendLog("Remote '" + name + "' added: " + url);
      } catch (Exception ex) {
        appendLog("Add remote failed: " + ex.getMessage());
      }
      refreshStatus();
    });
  }

  private void appendCommandResult(GitVcsService.CommandResult result) {
    if (result == null) return;
    String output = result.output();
    if (output == null || output.isBlank()) {
      appendLog(result.commandLine() + " (exit " + result.exitCode() + ")");
      return;
    }
    appendLog(result.commandLine() + "\n" + output);
  }

  private void openChangedEntry(GitVcsService.StatusEntry entry) {
    if (entry == null || onOpenRelativePath == null) return;
    String relative = entry.path();
    if (relative == null || relative.isBlank()) return;
    if (relative.contains(" -> ")) {
      String[] parts = relative.split(" -> ", 2);
      relative = parts.length == 2 ? parts[1] : relative;
    }
    onOpenRelativePath.accept(relative);
  }

  private void updateToolAvailabilityLabel(boolean git) {
    toolLabel.setText("Git: " + (git ? "ok" : "missing"));
  }

  private void runAsync(String actionName, Runnable action) {
    if (busy || disposed) return;
    setBusy(true);
    try {
      worker.submit(() -> {
        try {
          action.run();
        } catch (Exception ex) {
          appendLog(actionName + " failed: " + ex.getMessage());
        } finally {
          Platform.runLater(() -> setBusy(false));
        }
      });
    } catch (RejectedExecutionException ex) {
      setBusy(false);
    }
  }

  private void setBusy(boolean busy) {
    this.busy = busy;
    updateControlsForState();
  }

  private void updateControlsForState() {
    boolean hasProject = projectRoot != null;
    boolean repoReady = hasProject && gitAvailable && repositoryInitialized;

    btnRefresh.setDisable(busy);
    btnInitialize.setDisable(busy || !hasProject || !gitAvailable || repositoryInitialized);
    btnFetch.setDisable(busy || !repoReady);
    btnPull.setDisable(busy || !repoReady);
    btnPush.setDisable(busy || !repoReady);
    btnCommit.setDisable(busy || !repoReady);
    btnStash.setDisable(busy || !repoReady);
    btnStashPop.setDisable(busy || !repoReady);
    btnStageSelected.setDisable(busy || !repoReady);
    btnUnstageSelected.setDisable(busy || !repoReady);
    btnDiscardSelected.setDisable(busy || !repoReady);
    btnDiffSelected.setDisable(busy || !repoReady);
    cbBranch.setDisable(busy || !repoReady);
    btnNewBranch.setDisable(busy || !repoReady);
    txtCommitMessage.setDisable(busy || !repoReady);
    listChanges.setDisable(!repoReady);
    chkInitCommit.setDisable(busy || !hasProject || repositoryInitialized);
  }

  private void setInitControlsVisible(boolean visible, String hintText) {
    initHintLabel.setText((hintText == null || hintText.isBlank())
        ? "This project has no Git repository. Click Initialize to enable version control."
        : hintText);
    // Control entire initBox visibility
    initBox.setVisible(visible);
    initBox.setManaged(visible);
  }

  private void appendLog(String message) {
    if (message == null || message.isBlank()) return;
    Platform.runLater(() -> {
      if (!txtLog.getText().isBlank()) txtLog.appendText("\n\n");
      txtLog.appendText(message.trim());
      txtLog.setScrollTop(Double.MAX_VALUE);
    });
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "--" : value;
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    if (autoRefreshTimer != null) {
      autoRefreshTimer.stop();
      autoRefreshTimer = null;
    }
    worker.shutdownNow();
  }

  private static final class StatusCell extends ListCell<GitVcsService.StatusEntry> {
    @Override
    protected void updateItem(GitVcsService.StatusEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        getStyleClass().removeAll("vcs-status-untracked", "vcs-status-modified", "vcs-status-added", "vcs-status-deleted", "vcs-status-renamed");
        return;
      }

      setText(item.code() + "  " + item.path());
      getStyleClass().removeAll("vcs-status-untracked", "vcs-status-modified", "vcs-status-added", "vcs-status-deleted", "vcs-status-renamed");

      String code = item.code().toUpperCase(Locale.ROOT);
      if (item.isUntracked()) {
        getStyleClass().add("vcs-status-untracked");
      } else if (code.contains("M")) {
        getStyleClass().add("vcs-status-modified");
      } else if (code.contains("A")) {
        getStyleClass().add("vcs-status-added");
      } else if (code.contains("D")) {
        getStyleClass().add("vcs-status-deleted");
      } else if (code.contains("R") || code.contains("C")) {
        getStyleClass().add("vcs-status-renamed");
      }
    }
  }
}

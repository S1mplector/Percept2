package com.jvn.editor.ui;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * Team-focused Git + Git LFS control panel for JVN projects.
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
  private final Label toolLabel = new Label("Git: --   Git LFS: --");
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

  private final CheckBox chkInitWithLfs = new CheckBox("Enable Git LFS tracking");
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
    javafx.scene.layout.Region icon = new javafx.scene.layout.Region();
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

  public VersionControlView() {
    titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
    repoLabel.setStyle("-fx-text-fill: #9aa0a6;");
    toolLabel.setStyle("-fx-text-fill: #9aa0a6;");
    conflictLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
    conflictLabel.setVisible(false);
    conflictLabel.setManaged(false);
    remoteLabel.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px;");
    btnConfigureRemote.setStyle("-fx-font-size: 10px; -fx-padding: 2 8 2 8;");
    btnConfigureRemote.setOnAction(e -> showAddRemoteDialog());
    initHintLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11px;");
    initHintLabel.setWrapText(true);

    chkInitWithLfs.setSelected(true);
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
    javafx.scene.layout.Region initIcon = new javafx.scene.layout.Region();
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
    HBox initOptionsRow = new HBox(12, chkInitWithLfs, chkInitCommit);
    HBox initActionRow = new HBox(6, btnInitialize);
    initTitleLabel.setStyle("-fx-text-fill: #f0b673; -fx-font-weight: bold; -fx-font-size: 12px;");
    initBox.getChildren().addAll(initTitleLabel, initHintLabel, initOptionsRow, initActionRow);
    initBox.setStyle("-fx-background-color: rgba(240, 182, 115, 0.12); -fx-padding: 10; -fx-background-radius: 6; -fx-border-color: #f0b673; -fx-border-radius: 6; -fx-border-width: 1;");

    // Setup guide banner - shown when repo exists but no remote configured
    Label setupTitle = new Label("\u2699 Setup Required");
    setupTitle.setStyle("-fx-text-fill: #4da3ff; -fx-font-weight: bold; -fx-font-size: 12px;");
    Label setupDesc = new Label("Your project needs a remote repository to push, pull, and collaborate.");
    setupDesc.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    setupDesc.setWrapText(true);

    // Option A: Create GitHub repo directly (if gh CLI available)
    Label optionALabel = new Label("Option A — Create a new GitHub repository:");
    optionALabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
    ComboBox<String> cbVisibility = new ComboBox<>(FXCollections.observableArrayList("Private", "Public"));
    cbVisibility.setValue("Private");
    cbVisibility.setStyle("-fx-font-size: 11px;");
    cbVisibility.setMaxWidth(100);
    Button btnCreateGitHub = new Button("Create GitHub Repository");
    btnCreateGitHub.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-padding: 6 16 6 16; -fx-background-radius: 4;");
    btnCreateGitHub.setOnAction(e -> {
      boolean isPrivate = "Private".equals(cbVisibility.getValue());
      showCreateGitHubRepoDialog(isPrivate);
    });
    HBox ghRow = new HBox(8, cbVisibility, btnCreateGitHub);
    ghRow.setAlignment(Pos.CENTER_LEFT);
    Label ghHint = new Label("Requires GitHub CLI (gh). Installs remote + pushes in one step.");
    ghHint.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");
    VBox optionA = new VBox(4, optionALabel, ghRow, ghHint);

    // Option B: Manually connect existing repo
    Label optionBLabel = new Label("Option B — Connect an existing remote repository:");
    optionBLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px; -fx-font-weight: bold;");
    Button btnSetupRemote = new Button("Connect to Remote Repository");
    btnSetupRemote.setStyle("-fx-background-color: #4da3ff; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-padding: 6 16 6 16; -fx-background-radius: 4;");
    btnSetupRemote.setOnAction(e -> showAddRemoteDialog());
    Label manualHint = new Label("Already created a repo on GitHub/GitLab/Bitbucket? Paste its URL.");
    manualHint.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");
    VBox optionB = new VBox(4, optionBLabel, btnSetupRemote, manualHint);

    Label setupSkip = new Label("You can also work offline and add a remote later.");
    setupSkip.setStyle("-fx-text-fill: #555555; -fx-font-size: 10px;");

    setupBox.getChildren().addAll(setupTitle, setupDesc, optionA, optionB, setupSkip);
    setupBox.setStyle("-fx-background-color: rgba(77, 163, 255, 0.10); -fx-padding: 12; -fx-background-radius: 6; -fx-border-color: #4da3ff; -fx-border-radius: 6; -fx-border-width: 1;");
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
    top.setPadding(new Insets(8));

    Label changesLabel = new Label("Changes");
    changesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #e6e6e6;");
    Label logLabel = new Label("Log");
    logLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #e6e6e6;");
    VBox center = new VBox(4, changesLabel, fileActionRow, listChanges, logLabel, txtLog);
    center.setPadding(new Insets(0, 10, 10, 10));
    VBox.setVgrow(listChanges, Priority.ALWAYS);

    setTop(top);
    setCenter(center);

    setInitControlsVisible(false, null);
    updateToolAvailabilityLabel(false, false);
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
    this.projectRoot = projectRoot;
    repoLabel.setText(projectRoot == null ? "No project loaded" : "Project: " + projectRoot.getAbsolutePath());
    refreshStatus();
  }

  public void refreshStatus() {
    runAsync("Refresh status", () -> {
      boolean git = vcs.isGitAvailable();
      boolean lfs = vcs.isGitLfsAvailable();
      Platform.runLater(() -> {
        gitAvailable = git;
        updateToolAvailabilityLabel(git, lfs);
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
          if (hasRemote && remoteUrl != null) {
            remoteLabel.setText("Remote: " + remoteUrl);
            remoteLabel.setStyle("-fx-text-fill: #9aa0a6; -fx-font-size: 11px;");
            btnConfigureRemote.setText("Change");
            setupBox.setVisible(false);
            setupBox.setManaged(false);
          } else {
            remoteLabel.setText("Remote: not configured");
            remoteLabel.setStyle("-fx-text-fill: #f0b673; -fx-font-size: 11px;");
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
        boolean useLfs = chkInitWithLfs.isSelected();
        boolean initialCommit = chkInitCommit.isSelected();
        vcs.bootstrapRepository(projectRoot, useLfs, initialCommit, "Initialize JVN project scaffold");
        appendLog("Repository initialized" + (useLfs ? " with Git LFS defaults." : "."));
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
    javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
    dialog.setTitle("Add Git Remote");

    javafx.scene.control.ButtonType addBtn = new javafx.scene.control.ButtonType("Add Remote", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(addBtn, javafx.scene.control.ButtonType.CANCEL);

    // Dark theme styling
    javafx.scene.control.DialogPane pane = dialog.getDialogPane();
    pane.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #3a3a3a;");

    VBox content = new VBox(12);
    content.setPadding(new Insets(16));
    content.setStyle("-fx-background-color: #1a1a1a;");

    // Instructions
    Label titleLabel = new Label("Connect to a Remote Repository");
    titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6;");

    Label instructionLabel = new Label(
        "Paste the URL of your Git repository from GitHub, GitLab, Bitbucket, or any Git hosting service.\n" +
        "You can find this URL on your repository page — look for the \"Clone\" or \"Code\" button.");
    instructionLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    instructionLabel.setWrapText(true);
    instructionLabel.setMaxWidth(380);

    // Name field
    Label nameLabel = new Label("Remote Name");
    nameLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px;");
    TextField nameField = new TextField("origin");
    nameField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-background-radius: 4;");
    nameField.setPromptText("origin");
    Label nameHint = new Label("Usually \"origin\" — only change if you know what you're doing");
    nameHint.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");

    // URL field
    Label urlLabel = new Label("Repository URL");
    urlLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px;");
    TextField urlField = new TextField();
    urlField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-background-radius: 4;");
    urlField.setPromptText("https://github.com/username/repository.git");
    urlField.setPrefWidth(380);
    Label urlHint = new Label("Example: https://github.com/YourName/YourProject.git");
    urlHint.setStyle("-fx-text-fill: #4da3ff; -fx-font-size: 10px;");

    // Note about public/private
    Label privacyNote = new Label(
        "\uD83D\uDD12 Public vs Private: Create your repository on GitHub/GitLab first, then paste the URL here.\n" +
        "The repository's visibility (public/private) is set on the hosting service, not in Git.");
    privacyNote.setStyle("-fx-text-fill: #9aa0a6; -fx-font-size: 10px;");
    privacyNote.setWrapText(true);
    privacyNote.setMaxWidth(380);

    content.getChildren().addAll(
        titleLabel,
        instructionLabel,
        new javafx.scene.control.Separator(),
        nameLabel, nameField, nameHint,
        urlLabel, urlField, urlHint,
        new javafx.scene.control.Separator(),
        privacyNote
    );

    pane.setContent(content);
    Platform.runLater(urlField::requestFocus);

    dialog.setResultConverter(dialogButton -> {
      if (dialogButton == addBtn) {
        return nameField.getText().trim() + "|" + urlField.getText().trim();
      }
      return null;
    });

    dialog.showAndWait().ifPresent(result -> {
      String[] parts = result.split("\\|", 2);
      if (parts.length == 2 && !parts[1].isBlank()) {
        runAddRemote(parts[0], parts[1]);
      }
    });
  }

  private void showCreateGitHubRepoDialog(boolean isPrivate) {
    // First check gh CLI availability
    if (!vcs.isGhCliAvailable()) {
      javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
      alert.setTitle("GitHub CLI Not Found");
      alert.setHeaderText("GitHub CLI (gh) is not installed");
      alert.setContentText(
          "To create repositories directly, install the GitHub CLI:\n\n" +
          "  macOS:   brew install gh\n" +
          "  Windows: winget install GitHub.cli\n" +
          "  Linux:   https://github.com/cli/cli#installation\n\n" +
          "After installing, run:  gh auth login\n\n" +
          "Alternatively, create a repository on github.com and use\n\"Connect to Remote Repository\" to paste the URL.");
      alert.getDialogPane().setStyle("-fx-background-color: #1a1a1a;");
      alert.showAndWait();
      return;
    }

    if (!vcs.isGhCliAuthenticated()) {
      javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
      alert.setTitle("GitHub CLI Not Authenticated");
      alert.setHeaderText("You need to log in to GitHub first");
      alert.setContentText("Run this command in your terminal:\n\n  gh auth login\n\nThen try again.");
      alert.getDialogPane().setStyle("-fx-background-color: #1a1a1a;");
      alert.showAndWait();
      return;
    }

    // Show repo name dialog
    javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
    dialog.setTitle("Create GitHub Repository");

    javafx.scene.control.ButtonType createBtn = new javafx.scene.control.ButtonType(
        isPrivate ? "Create Private Repo" : "Create Public Repo",
        javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(createBtn, javafx.scene.control.ButtonType.CANCEL);

    javafx.scene.control.DialogPane pane = dialog.getDialogPane();
    pane.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #3a3a3a;");

    VBox content = new VBox(10);
    content.setPadding(new Insets(16));
    content.setStyle("-fx-background-color: #1a1a1a;");

    Label dlgTitle = new Label("Create a " + (isPrivate ? "Private" : "Public") + " GitHub Repository");
    dlgTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6;");

    Label dlgDesc = new Label("This will create a new repository on your GitHub account, set it as the remote, and push your code.");
    dlgDesc.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    dlgDesc.setWrapText(true);
    dlgDesc.setMaxWidth(350);

    Label nameLabel = new Label("Repository Name");
    nameLabel.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 11px;");
    String defaultName = projectRoot != null ? projectRoot.getName() : "my-project";
    TextField repoNameField = new TextField(defaultName);
    repoNameField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-background-radius: 4;");
    repoNameField.setPrefWidth(350);

    Label visLabel = new Label(isPrivate
        ? "\uD83D\uDD12 This repository will be private — only you and collaborators can see it."
        : "\uD83C\uDF10 This repository will be public — anyone can see it.");
    visLabel.setStyle("-fx-text-fill: " + (isPrivate ? "#8bcf98" : "#f0b673") + "; -fx-font-size: 10px;");
    visLabel.setWrapText(true);
    visLabel.setMaxWidth(350);

    content.getChildren().addAll(dlgTitle, dlgDesc, nameLabel, repoNameField, visLabel);
    pane.setContent(content);
    Platform.runLater(repoNameField::requestFocus);

    dialog.setResultConverter(dialogButton -> {
      if (dialogButton == createBtn) return repoNameField.getText().trim();
      return null;
    });

    dialog.showAndWait().ifPresent(repoName -> {
      if (!repoName.isBlank()) {
        runCreateGitHubRepo(repoName, isPrivate);
      }
    });
  }

  private void runCreateGitHubRepo(String repoName, boolean isPrivate) {
    runAsync("Create GitHub repo", () -> {
      try {
        appendLog("Creating " + (isPrivate ? "private" : "public") + " GitHub repository: " + repoName + "...");
        appendCommandResult(vcs.createGitHubRepo(projectRoot, repoName, isPrivate));
        appendLog("Repository created successfully on GitHub!");
        appendLog("Setting up remote 'origin'...");
        
        // Now try to push with LFS check
        appendLog("Pushing code to GitHub...");
        try {
          appendCommandResult(vcs.pushWithLfsCheck(projectRoot));
          appendLog("Code pushed successfully! Your project is now on GitHub.");
        } catch (Exception pushEx) {
          // LFS or other push error - repo is created but push failed
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

  private void updateToolAvailabilityLabel(boolean git, boolean lfs) {
    toolLabel.setText("Git: " + (git ? "ok" : "missing") + "   Git LFS: " + (lfs ? "ok" : "missing"));
  }

  private void runAsync(String actionName, Runnable action) {
    if (busy) return;
    setBusy(true);
    worker.submit(() -> {
      try {
        action.run();
      } catch (Exception ex) {
        appendLog(actionName + " failed: " + ex.getMessage());
      } finally {
        Platform.runLater(() -> setBusy(false));
      }
    });
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
    chkInitWithLfs.setDisable(busy || !hasProject || repositoryInitialized);
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

  private static final class StatusCell extends ListCell<GitVcsService.StatusEntry> {
    @Override
    protected void updateItem(GitVcsService.StatusEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setStyle("");
        return;
      }

      setText(item.code() + "  " + item.path());

      String code = item.code().toUpperCase(Locale.ROOT);
      if (item.isUntracked()) {
        setStyle("-fx-text-fill: #8bd17c;");
      } else if (code.contains("M")) {
        setStyle("-fx-text-fill: #66d9ef;");
      } else if (code.contains("A")) {
        setStyle("-fx-text-fill: #8bd17c;");
      } else if (code.contains("D")) {
        setStyle("-fx-text-fill: #f38ba8;");
      } else if (code.contains("R") || code.contains("C")) {
        setStyle("-fx-text-fill: #f0b673;");
      } else {
        setStyle("");
      }
    }
  }
}

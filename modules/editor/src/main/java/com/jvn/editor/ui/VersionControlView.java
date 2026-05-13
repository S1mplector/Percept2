package com.jvn.editor.ui;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import javafx.scene.control.ContentDisplay;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Team-focused Git control panel for JVN projects.
 */
public class VersionControlView extends BorderPane {
  private static final long REMOTE_CHECK_INTERVAL_MS = 120_000L;
  private static final DateTimeFormatter CHECK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

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
  private final Label incomingLabel = new Label("Incoming: --");
  private final Label summaryLabel = new Label("Status: --");
  private final Label nextStepLabel = new Label("Open a project to begin.");
  private final Label lastRemoteCheckLabel = new Label("Online check: --");
  private final Label conflictLabel = new Label();
  private final Label remoteLabel = new Label("Remote: not configured");
  private final Button btnConfigureRemote = new Button("Add Remote");
  private final Label initTitleLabel = new Label("\u26a0 Repository Not Initialized");
  private final Label initHintLabel = new Label("Repository is not initialized for this project.");
  private final VBox initBox = new VBox(6);

  private final VBox setupBox = new VBox(8);

  private final CheckBox chkInitCommit = new CheckBox("Create initial commit");

  private final Button btnRefresh = actionButton(
      "Refresh",
      CssIcon.redo(),
      "Refresh the project status and check whether the online repository has new work.",
      "vcs-action-button-neutral");
  private final Button btnInitialize = new Button("Initialize");
  private final Button btnFetch = actionButton(
      "Check Online",
      CssIcon.download(),
      "Contact the remote repository and check for work you do not have yet.",
      "vcs-action-button-accent");
  private final Button btnPull = actionButton(
      "Get Updates",
      CssIcon.arrowDown(),
      "Download incoming commits and replay your local work on top.",
      "vcs-action-button-warning");
  private final Button btnPush = actionButton(
      "Send Online",
      CssIcon.arrowUp(),
      "Upload your saved snapshots to the remote repository.",
      "vcs-action-button-success");
  private final Button btnCommit = actionButton(
      "Save Snapshot",
      CssIcon.save(),
      "Save all current project changes into a local version snapshot.",
      "vcs-action-button-success");
  private final Button btnStash = actionButton(
      "Shelve",
      CssIcon.memory(),
      "Temporarily put current changes aside without saving a snapshot.",
      "vcs-action-button-neutral");
  private final Button btnStashPop = actionButton(
      "Restore Shelf",
      CssIcon.popOut(),
      "Bring back the latest shelved changes.",
      "vcs-action-button-neutral");
  private final Button btnStageSelected = iconButton(CssIcon.plus(), "Mark the selected file for the next Git commit.");
  private final Button btnUnstageSelected = iconButton(CssIcon.minus(), "Remove the selected file from the staged Git area.");
  private final Button btnDiscardSelected = iconButton(CssIcon.delete(), "Permanently discard the selected file change.");
  private final Button btnDiffSelected = iconButton(CssIcon.search(), "Show the selected file diff in the activity log.");
  private final ComboBox<String> cbBranch = new ComboBox<>();
  private final Button btnNewBranch = actionButton(
      "New Branch",
      CssIcon.rocket(),
      "Create a branch with the typed name and switch to it.",
      "vcs-action-button-neutral");

  private static Button iconButton(Region iconClass, String tooltip) {
    Button btn = new Button();
    btn.getStyleClass().add("vcs-icon-btn");
    btn.setGraphic(iconClass);
    btn.setTooltip(new Tooltip(tooltip));
    return btn;
  }

  private static Button actionButton(Region iconClass, String tooltip, String... styleClasses) {
    return actionButton(null, iconClass, tooltip, styleClasses);
  }

  private static Button actionButton(String text, Region iconClass, String tooltip, String... styleClasses) {
    Button btn = new Button(text == null ? "" : text);
    btn.getStyleClass().add("vcs-action-button");
    if (styleClasses != null) btn.getStyleClass().addAll(styleClasses);
    btn.setGraphic(iconClass);
    btn.setTooltip(new Tooltip(tooltip));
    btn.setContentDisplay(ContentDisplay.LEFT);
    return btn;
  }

  private final TextField txtCommitMessage = new TextField();
  private final ListView<GitVcsService.StatusEntry> listChanges = new ListView<>();
  private final TextArea txtLog = new TextArea();

  private File projectRoot;
  private boolean gitAvailable;
  private boolean repositoryInitialized;
  private boolean busy;
  private boolean currentHasRemote;
  private boolean currentHasUpstream;
  private boolean currentHasConflicts;
  private boolean updatingBranchSelection;
  private int currentAhead;
  private int currentBehind;
  private int currentChangeCount;
  private long lastRemoteCheckMs = -1L;
  private String lastRemoteCheckDisplay = "Online check: not checked yet";
  private String currentBranch = "";
  private Consumer<String> onOpenRelativePath;
  private Timeline autoRefreshTimer;
  private boolean disposed;

  public VersionControlView() {
    getStyleClass().addAll("version-control-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vcs-title", "sidebar-tool-title");
    repoLabel.getStyleClass().addAll("vcs-muted", "sidebar-tool-subtitle");
    toolLabel.getStyleClass().add("vcs-muted");
    incomingLabel.getStyleClass().addAll("vcs-sync-chip", "vcs-sync-neutral");
    lastRemoteCheckLabel.getStyleClass().add("vcs-muted");
    nextStepLabel.getStyleClass().addAll("vcs-next-step", "vcs-next-step-info");
    nextStepLabel.setWrapText(true);
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

    txtCommitMessage.setPromptText("Describe what changed...");
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
    listChanges.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> updateControlsForState());

    btnRefresh.setOnAction(e -> refreshStatus(true));
    btnInitialize.setOnAction(e -> initializeRepository());
    btnInitialize.getStyleClass().addAll("vcs-action-button", "vcs-action-button-success");
    btnInitialize.setGraphic(CssIcon.plus());
    btnInitialize.setContentDisplay(ContentDisplay.LEFT);
    btnInitialize.setTooltip(new Tooltip("Create Git tracking for this project so snapshots and sync are available."));
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
    cbBranch.setEditable(true);
    cbBranch.setPromptText("Branch name...");
    cbBranch.setOnAction(e -> {
      if (updatingBranchSelection) return;
      String selected = cbBranch.getValue();
      if ((selected == null || selected.isBlank()) && cbBranch.getEditor() != null) {
        selected = cbBranch.getEditor().getText();
      }
      if (selected == null || selected.isBlank() || selected.equals(currentBranch)) return;
      if (selected != null && !selected.isBlank()) runSwitchBranch(selected);
    });

    // Sync toolbar: refresh, fetch, pull, push
    HBox syncRow = new HBox(6, btnRefresh, btnFetch, btnPull, btnPush);
    syncRow.setAlignment(Pos.CENTER_LEFT);

    // Stash toolbar
    HBox stashRow = new HBox(6, btnStash, btnStashPop);
    stashRow.setAlignment(Pos.CENTER_LEFT);

    // Combined toolbar
    VBox toolbar = new VBox(6, syncRow, stashRow);
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
    HBox commitRow = new HBox(6, txtCommitMessage, btnCommit);
    HBox.setHgrow(txtCommitMessage, Priority.ALWAYS);
    commitRow.setAlignment(Pos.CENTER_LEFT);

    // Branch row
    cbBranch.setMaxWidth(Double.MAX_VALUE);
    HBox branchRow = new HBox(6, cbBranch, btnNewBranch);
    HBox.setHgrow(cbBranch, Priority.ALWAYS);
    branchRow.setAlignment(Pos.CENTER_LEFT);

    // File action toolbar
    HBox fileActionRow = new HBox(4, btnStageSelected, btnUnstageSelected, btnDiscardSelected, btnDiffSelected);
    fileActionRow.setAlignment(Pos.CENTER_LEFT);

    // Remote row
    HBox remoteRow = new HBox(6, remoteLabel, btnConfigureRemote);
    remoteRow.setAlignment(Pos.CENTER_LEFT);

    // Header section
    VBox statusBox = new VBox(4, nextStepLabel, branchLabel, remoteRow, incomingLabel, syncLabel, summaryLabel, lastRemoteCheckLabel, conflictLabel);
    statusBox.getStyleClass().add("vcs-status-stack");

    HBox titleRow = new HBox(6, titleLabel, SidebarToolHelp.button(this, "Version Control", """
        The Version Control panel provides Git integration for your JVN project.

Core workflow:
  • Stage files — tick the checkboxes next to changed files
  • Write a commit message and click Commit to record a snapshot
  • Push / Pull sync your local commits with the remote repository

Branch management:
  • The current branch is shown in the header
  • Use the branch row to create or switch branches

Remote setup:
  If your project doesn't have a remote yet, the panel will guide you through \
connecting to GitHub or another host. You can use Option A (create a new \
GitHub repo via the gh CLI) or Option B (paste an existing remote URL).

The Changes list shows files that differ from the last commit. \
The Log shows recent commits on the current branch."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    VBox top = new VBox(
        6,
        titleRow,
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
    refreshStatus(true);
  }

  public void refreshStatus() {
    refreshStatus(false);
  }

  private void refreshStatus(boolean checkRemote) {
    if (disposed) return;
    runAsync("Refresh status", () -> loadStatus(checkRemote));
  }

  private void loadStatus(boolean forceRemoteCheck) {
    boolean git = vcs.isGitAvailable();
    Platform.runLater(() -> {
      gitAvailable = git;
      updateToolAvailabilityLabel(git);
    });

    if (projectRoot == null) {
      Platform.runLater(() -> applyNoProjectState());
      return;
    }

    if (!git) {
      Platform.runLater(() -> applyGitMissingState());
      return;
    }

    if (!vcs.isRepository(projectRoot)) {
      Platform.runLater(() -> applyNotInitializedState());
      return;
    }

    try {
      boolean hasRemote = vcs.hasRemote(projectRoot);
      String remoteCheckText = lastRemoteCheckText();
      if (!hasRemote) {
        remoteCheckText = "Online check: remote not connected";
      }
      if (hasRemote && shouldCheckRemote(forceRemoteCheck)) {
        try {
          GitVcsService.CommandResult fetch = vcs.fetch(projectRoot);
          lastRemoteCheckMs = System.currentTimeMillis();
          remoteCheckText = "Online check: " + LocalTime.now().format(CHECK_TIME_FORMAT);
          if (!fetch.success()) {
            remoteCheckText += " (failed)";
          }
          lastRemoteCheckDisplay = remoteCheckText;
        } catch (Exception ex) {
          lastRemoteCheckMs = System.currentTimeMillis();
          remoteCheckText = "Online check failed: " + safeMessage(ex);
          lastRemoteCheckDisplay = remoteCheckText;
          appendLog("Online check failed: " + safeMessage(ex));
        }
      }

      GitVcsService.RepositoryStatus status = vcs.getRepositoryStatus(projectRoot);
      String remoteUrl = hasRemote ? vcs.getRemoteUrl(projectRoot) : null;
      List<String> branches = vcs.listBranches(projectRoot);
      boolean hasConflicts = hasConflicts(status);
      String finalRemoteCheckText = remoteCheckText;
      Platform.runLater(() -> applyRepositoryStatus(status, hasRemote, remoteUrl, branches, hasConflicts, finalRemoteCheckText));
    } catch (Exception ex) {
      appendLog(safeMessage(ex));
      Platform.runLater(() -> {
        setNextStep("Could not read version-control status. See Activity for details.", "vcs-next-step-danger");
        updateControlsForState();
      });
    }
  }

  private void applyNoProjectState() {
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    branchLabel.setText("Branch: --");
    incomingLabel.setText("Incoming: --");
    syncLabel.setText("Sync: --");
    summaryLabel.setText("Status: no project selected");
    lastRemoteCheckLabel.setText("Online check: --");
    remoteLabel.setText("Remote: not configured");
    listChanges.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(false, null);
    setNextStep("Open a project to begin.", "vcs-next-step-info");
    updateControlsForState();
  }

  private void applyGitMissingState() {
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    branchLabel.setText("Branch: --");
    incomingLabel.setText("Incoming: --");
    syncLabel.setText("Sync: --");
    summaryLabel.setText("Status: Git unavailable");
    listChanges.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(false, null);
    setNextStep("Install Git before using version control.", "vcs-next-step-danger");
    updateControlsForState();
  }

  private void applyNotInitializedState() {
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    branchLabel.setText("Branch: not initialized");
    incomingLabel.setText("Incoming: unavailable until initialized");
    syncLabel.setText("Sync: not connected");
    summaryLabel.setText("Status: repository not initialized");
    listChanges.getItems().clear();
    cbBranch.getItems().clear();
    setSetupVisible(false);
    setInitControlsVisible(true, "This project is not a repository yet. Initialize it to enable snapshots, backup, and sync.");
    setNextStep("Start here: initialize version control for this project.", "vcs-next-step-warning");
    updateControlsForState();
  }

  private void applyRepositoryStatus(GitVcsService.RepositoryStatus status,
                                     boolean hasRemote,
                                     String remoteUrl,
                                     List<String> branches,
                                     boolean hasConflicts,
                                     String remoteCheckText) {
    repositoryInitialized = true;
    currentHasRemote = hasRemote;
    currentHasUpstream = status.upstream() != null && !status.upstream().isBlank();
    currentHasConflicts = hasConflicts;
    currentAhead = Math.max(0, status.ahead());
    currentBehind = Math.max(0, status.behind());
    currentChangeCount = status.entries() == null ? 0 : status.entries().size();
    currentBranch = safe(status.branch());

    branchLabel.setText("Branch: " + currentBranch);
    String upstream = status.upstream() == null || status.upstream().isBlank() ? "not linked to online branch" : status.upstream();
    syncLabel.setText("Outgoing: " + currentAhead + " local snapshot" + plural(currentAhead) + " not uploaded (" + upstream + ")");
    summaryLabel.setText(status.clean()
        ? "Status: no local file changes"
        : "Status: " + currentChangeCount + " changed file" + plural(currentChangeCount));
    incomingLabel.setText(buildIncomingText(hasRemote, currentBehind));
    lastRemoteCheckLabel.setText(remoteCheckText == null || remoteCheckText.isBlank() ? lastRemoteCheckText() : remoteCheckText);
    conflictLabel.setText(hasConflicts ? "Conflicts need manual resolution before syncing." : "");
    conflictLabel.setVisible(hasConflicts);
    conflictLabel.setManaged(hasConflicts);

    remoteLabel.getStyleClass().removeAll("vcs-remote-configured", "vcs-remote-missing");
    if (hasRemote && remoteUrl != null && !remoteUrl.isBlank()) {
      remoteLabel.setText("Remote: " + remoteUrl);
      remoteLabel.getStyleClass().add("vcs-remote-configured");
      btnConfigureRemote.setText("Change");
      setSetupVisible(false);
    } else {
      remoteLabel.setText("Remote: not configured");
      remoteLabel.getStyleClass().add("vcs-remote-missing");
      btnConfigureRemote.setText("Add Remote");
      setSetupVisible(true);
    }

    listChanges.setItems(FXCollections.observableArrayList(status.entries()));
    updateBranchChoices(branches, currentBranch);
    setInitControlsVisible(false, null);
    updateNextStep(status, hasRemote, hasConflicts);
    updateSyncChipStyle();
    updateControlsForState();
  }

  private boolean shouldCheckRemote(boolean forceRemoteCheck) {
    if (forceRemoteCheck) return true;
    return lastRemoteCheckMs < 0L || System.currentTimeMillis() - lastRemoteCheckMs >= REMOTE_CHECK_INTERVAL_MS;
  }

  private String lastRemoteCheckText() {
    return lastRemoteCheckDisplay;
  }

  private String buildIncomingText(boolean hasRemote, int behind) {
    if (!hasRemote) return "Incoming: remote not connected";
    if (behind > 0) return "Incoming: " + behind + " online snapshot" + plural(behind) + " waiting";
    return "Incoming: none found";
  }

  private void updateNextStep(GitVcsService.RepositoryStatus status, boolean hasRemote, boolean hasConflicts) {
    if (hasConflicts) {
      setNextStep("Resolve the listed conflicts before saving or syncing.", "vcs-next-step-danger");
    } else if (!hasRemote) {
      setNextStep("Recommended: connect a remote repository so the project can be backed up online.", "vcs-next-step-warning");
    } else if (currentBehind > 0) {
      setNextStep("Get Updates first. The online repository has work this copy does not have yet.", "vcs-next-step-warning");
    } else if (currentChangeCount > 0) {
      setNextStep("Describe the change and click Save Snapshot.", "vcs-next-step-info");
    } else if (currentAhead > 0 || !currentHasUpstream) {
      setNextStep("Send Online to upload your saved snapshot" + plural(Math.max(1, currentAhead)) + ".", "vcs-next-step-success");
    } else {
      setNextStep("Everything is up to date.", "vcs-next-step-success");
    }
  }

  private void setNextStep(String text, String toneClass) {
    nextStepLabel.setText(text == null || text.isBlank() ? "Status unavailable." : text);
    nextStepLabel.getStyleClass().removeAll(
        "vcs-next-step-info",
        "vcs-next-step-success",
        "vcs-next-step-warning",
        "vcs-next-step-danger");
    nextStepLabel.getStyleClass().add(toneClass == null || toneClass.isBlank() ? "vcs-next-step-info" : toneClass);
  }

  private void updateBranchChoices(List<String> branches, String selectedBranch) {
    updatingBranchSelection = true;
    try {
      cbBranch.setItems(FXCollections.observableArrayList(branches == null ? List.of() : branches));
      cbBranch.setValue(selectedBranch == null || selectedBranch.isBlank() ? null : selectedBranch);
      if (cbBranch.getEditor() != null) {
        cbBranch.getEditor().setText(selectedBranch == null ? "" : selectedBranch);
      }
    } finally {
      updatingBranchSelection = false;
    }
  }

  private void setSetupVisible(boolean visible) {
    setupBox.setVisible(visible);
    setupBox.setManaged(visible);
  }

  private boolean hasConflicts(GitVcsService.RepositoryStatus status) {
    if (status == null || status.entries() == null) return false;
    for (GitVcsService.StatusEntry entry : status.entries()) {
      String index = entry.indexStatus();
      String workTree = entry.workTreeStatus();
      if ("U".equals(index) || "U".equals(workTree)) return true;
      if ("D".equals(index) && "D".equals(workTree)) return true;
      if ("A".equals(index) && "A".equals(workTree)) return true;
    }
    return false;
  }

  private void updateSyncChipStyle() {
    incomingLabel.getStyleClass().removeAll("vcs-sync-neutral", "vcs-sync-success", "vcs-sync-warning", "vcs-sync-danger");
    if (currentHasConflicts) {
      incomingLabel.getStyleClass().add("vcs-sync-danger");
    } else if (!currentHasRemote || currentBehind > 0) {
      incomingLabel.getStyleClass().add("vcs-sync-warning");
    } else {
      incomingLabel.getStyleClass().add("vcs-sync-success");
    }
  }

  private void updateActionEmphasis() {
    clearActionState(btnCommit);
    clearActionState(btnPull);
    clearActionState(btnPush);
    clearActionState(btnFetch);
    if (currentHasConflicts) {
      btnCommit.getStyleClass().add("vcs-action-state-danger");
      btnPull.getStyleClass().add("vcs-action-state-danger");
      btnPush.getStyleClass().add("vcs-action-state-danger");
    } else if (currentBehind > 0) {
      btnPull.getStyleClass().add("vcs-action-state-attention");
    } else if (currentChangeCount > 0) {
      btnCommit.getStyleClass().add("vcs-action-state-attention");
    } else if (currentAhead > 0 || (repositoryInitialized && currentHasRemote && !currentHasUpstream)) {
      btnPush.getStyleClass().add("vcs-action-state-attention");
    } else if (repositoryInitialized && currentHasRemote) {
      btnFetch.getStyleClass().add("vcs-action-state-ready");
    }
  }

  private void clearActionState(Button button) {
    button.getStyleClass().removeAll(
        "vcs-action-state-attention",
        "vcs-action-state-ready",
        "vcs-action-state-danger");
  }

  private String plural(int count) {
    return count == 1 ? "" : "s";
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
      loadStatus(true);
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
        lastRemoteCheckMs = System.currentTimeMillis();
        lastRemoteCheckDisplay = "Online check: " + LocalTime.now().format(CHECK_TIME_FORMAT);
      } catch (Exception ex) {
        lastRemoteCheckMs = System.currentTimeMillis();
        lastRemoteCheckDisplay = "Online check failed: " + safeMessage(ex);
        appendLog(ex.getMessage());
      }
      loadStatus(false);
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
      loadStatus(false);
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
      loadStatus(true);
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
      loadStatus(true);
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
      loadStatus(false);
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
      loadStatus(false);
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
      loadStatus(false);
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
      loadStatus(false);
    });
  }

  private void runDiscardSelected() {
    GitVcsService.StatusEntry entry = listChanges.getSelectionModel().getSelectedItem();
    if (entry == null) return;
    if (!EditorDialogs.confirm(
        getScene() == null ? null : getScene().getWindow(),
        "Discard File Change",
        "Discard changes in " + entry.path() + "? This cannot be undone.",
        "Discard",
        true)) {
      appendLog("Discard cancelled.");
      return;
    }
    runAsync("Discard", () -> {
      try {
        vcs.discardFile(projectRoot, entry.path());
        appendLog("Discarded: " + entry.path());
      } catch (Exception ex) {
        appendLog("Discard failed: " + ex.getMessage());
      }
      loadStatus(false);
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
      loadStatus(false);
    });
  }

  private void runCreateBranch() {
    String name = cbBranch.getEditor() != null ? cbBranch.getEditor().getText() : null;
    if (name == null || name.isBlank()) name = cbBranch.getValue();
    if (name == null || name.isBlank()) {
      appendLog("Enter a branch name in the branch selector to create a new branch.");
      return;
    }
    if (name.equals(currentBranch)) {
      appendLog("You are already on branch: " + name);
      return;
    }
    String branchToCreate = name.trim();
    runAsync("Create branch", () -> {
      try {
        appendCommandResult(vcs.createBranch(projectRoot, branchToCreate));
      } catch (Exception ex) {
        appendLog("Create branch failed: " + ex.getMessage());
      }
      loadStatus(false);
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
      loadStatus(true);
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
      loadStatus(true);
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
    boolean hasSelection = listChanges.getSelectionModel().getSelectedItem() != null;
    boolean hasChanges = currentChangeCount > 0;
    boolean canSync = repoReady && currentHasRemote && !currentHasConflicts;

    btnRefresh.setDisable(busy);
    btnInitialize.setDisable(busy || !hasProject || !gitAvailable || repositoryInitialized);
    btnFetch.setDisable(busy || !repoReady || !currentHasRemote);
    btnPull.setDisable(busy || !canSync);
    btnPush.setDisable(busy || !canSync || currentBehind > 0 || (currentAhead == 0 && currentHasUpstream));
    btnCommit.setDisable(busy || !repoReady || !hasChanges || currentHasConflicts);
    btnStash.setDisable(busy || !repoReady || !hasChanges);
    btnStashPop.setDisable(busy || !repoReady);
    btnStageSelected.setDisable(busy || !repoReady || !hasSelection);
    btnUnstageSelected.setDisable(busy || !repoReady || !hasSelection);
    btnDiscardSelected.setDisable(busy || !repoReady || !hasSelection);
    btnDiffSelected.setDisable(busy || !repoReady || !hasSelection);
    cbBranch.setDisable(busy || !repoReady);
    btnNewBranch.setDisable(busy || !repoReady);
    txtCommitMessage.setDisable(busy || !repoReady);
    listChanges.setDisable(!repoReady);
    chkInitCommit.setDisable(busy || !hasProject || repositoryInitialized);
    btnConfigureRemote.setDisable(busy || !repoReady);
    updateActionEmphasis();
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

  private String safeMessage(Throwable ex) {
    if (ex == null) return "Unknown error";
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.trim();
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
        getStyleClass().removeAll(
            "vcs-status-untracked",
            "vcs-status-modified",
            "vcs-status-added",
            "vcs-status-deleted",
            "vcs-status-renamed",
            "vcs-status-conflict");
        return;
      }

      setText(friendlyStatus(item) + " - " + item.path());
      getStyleClass().removeAll(
          "vcs-status-untracked",
          "vcs-status-modified",
          "vcs-status-added",
          "vcs-status-deleted",
          "vcs-status-renamed",
          "vcs-status-conflict");

      String code = item.code().toUpperCase(Locale.ROOT);
      if (isConflict(item)) {
        getStyleClass().add("vcs-status-conflict");
      } else if (item.isUntracked()) {
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

    private static String friendlyStatus(GitVcsService.StatusEntry item) {
      if (isConflict(item)) return "Conflict";
      if (item.isUntracked()) return "New";
      String code = item.code().toUpperCase(Locale.ROOT);
      if (code.contains("A")) return "Added";
      if (code.contains("D")) return "Deleted";
      if (code.contains("R")) return "Renamed";
      if (code.contains("C")) return "Copied";
      if (code.contains("M")) return "Changed";
      return "Updated";
    }

    private static boolean isConflict(GitVcsService.StatusEntry item) {
      String index = item.indexStatus();
      String workTree = item.workTreeStatus();
      return "U".equals(index)
          || "U".equals(workTree)
          || ("D".equals(index) && "D".equals(workTree))
          || ("A".equals(index) && "A".equals(workTree));
    }
  }
}

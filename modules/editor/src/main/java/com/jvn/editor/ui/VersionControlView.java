package com.jvn.editor.ui;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Team-focused Git control panel for JVN projects.
 */
public class VersionControlView extends BorderPane {
  private static final long REMOTE_CHECK_INTERVAL_MS = 120_000L;
  private static final DateTimeFormatter CHECK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final String ICON_REFRESH = "#8ecaff";
  private static final String ICON_ONLINE = "#69d7ff";
  private static final String ICON_PULL = "#f2c86b";
  private static final String ICON_PUSH = "#79df93";
  private static final String ICON_COMMIT = "#8bb8ff";
  private static final String ICON_SHELF = "#c29cff";
  private static final String ICON_RESTORE = "#ffcf7a";
  private static final String ICON_STAGE = "#80e08d";
  private static final String ICON_UNSTAGE = "#f0b673";
  private static final String ICON_DISCARD = "#ff7f9f";
  private static final String ICON_DIFF = "#8ecaff";
  private static final String ICON_BRANCH = "#b7a7ff";
  private static final double GUIDE_POPUP_WIDTH = 320.0;

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
  private final Popup guidePopup = new Popup();
  private final VBox guidePopupRoot = new VBox(0);
  private final Label guideArrowLabel = new Label("\u25b2");
  private final VBox guideCard = new VBox(4);
  private final Label guideTitleLabel = new Label();
  private final Label guideBodyLabel = new Label();
  private final Button guideCloseButton = new Button("\u00d7");
  private final BorderPane contentPane = new BorderPane();
  private final StackPane contentStack = new StackPane();
  private final VBox initializingOverlay = new VBox(8);
  private final ProgressIndicator initializingSpinner = new ProgressIndicator();
  private final Label initializingTitleLabel = new Label("Initializing version control");
  private final Label initializingBodyLabel = new Label("Reading project status and preparing Git controls.");
  private final Label initTitleLabel = new Label("\u26a0 Repository Not Initialized");
  private final Label initHintLabel = new Label("Repository is not initialized for this project.");
  private final VBox initBox = new VBox(6);

  private final VBox setupBox = new VBox(8);

  private final CheckBox chkInitCommit = new CheckBox("Create initial commit");

  private final Button btnRefresh = actionButton(
      "Refresh",
      CssIcon.redo(ICON_REFRESH),
      "Refresh the project status and check whether the online repository has new work.",
      "vcs-action-button-neutral");
  private final Button btnInitialize = new Button("Initialize");
  private final Button btnFetch = actionButton(
      "Check Online",
      CssIcon.download(ICON_ONLINE),
      "Contact the remote repository and check for work you do not have yet.",
      "vcs-action-button-accent");
  private final Button btnPull = actionButton(
      "Get Updates",
      CssIcon.arrowDown(ICON_PULL),
      "Download incoming commits and replay your local work on top.",
      "vcs-action-button-warning");
  private final Button btnPush = actionButton(
      "Send Online",
      CssIcon.arrowUp(ICON_PUSH),
      "Upload your saved snapshots to the remote repository.",
      "vcs-action-button-success");
  private final Button btnCommit = actionButton(
      "Save Snapshot",
      CssIcon.save(ICON_COMMIT),
      "Save all current project changes into a local version snapshot.",
      "vcs-action-button-success");
  private final Button btnStash = actionButton(
      "Shelve",
      CssIcon.folder(ICON_SHELF),
      "Temporarily put current changes aside without saving a snapshot.",
      "vcs-action-button-neutral");
  private final Button btnStashPop = actionButton(
      "Restore Shelf",
      CssIcon.popOut(ICON_RESTORE),
      "Bring back the latest shelved changes.",
      "vcs-action-button-neutral");
  private final Button btnStageSelected = iconButton(CssIcon.plusBold(ICON_STAGE), "Mark the selected file(s) for the next Git commit.");
  private final Button btnUnstageSelected = iconButton(CssIcon.minus(ICON_UNSTAGE), "Remove the selected file(s) from the staged Git area.");
  private final Button btnDiscardSelected = iconButton(CssIcon.delete(ICON_DISCARD), "Permanently discard the selected file change(s).");
  private final Button btnDiffSelected = iconButton(CssIcon.search(ICON_DIFF), "Show selected file diff(s) in the activity log.");
  private final ComboBox<String> cbBranch = new ComboBox<>();
  private final Button btnNewBranch = actionButton(
      "New Branch",
      CssIcon.branchPlus(ICON_BRANCH),
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
  private boolean statusLoaded;
  private int currentAhead;
  private int currentBehind;
  private int currentChangeCount;
  private long lastRemoteCheckMs = -1L;
  private String lastRemoteCheckDisplay = "Online check: not checked yet";
  private String currentBranch = "";
  private Consumer<String> onOpenRelativePath;
  private Timeline autoRefreshTimer;
  private boolean disposed;
  private String lastRemoteFailure = "";
  private String lastGuideKey = "";
  private String dismissedGuideKey = "";
  private String busyActionName = "";
  private final List<Node> currentGuideTargets = new ArrayList<>();

  public VersionControlView() {
    getStyleClass().addAll("version-control-root", "sidebar-tool-root");
    titleLabel.getStyleClass().addAll("vcs-title", "sidebar-tool-title");
    repoLabel.getStyleClass().addAll("vcs-muted", "sidebar-tool-subtitle");
    toolLabel.getStyleClass().add("vcs-muted");
    incomingLabel.getStyleClass().addAll("vcs-sync-chip", "vcs-sync-neutral");
    lastRemoteCheckLabel.getStyleClass().add("vcs-muted");
    nextStepLabel.getStyleClass().addAll("vcs-next-step", "vcs-next-step-info");
    nextStepLabel.setWrapText(true);
    configureGuidePopup();
    configureInitializingOverlay();
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
    txtCommitMessage.textProperty().addListener((obs, oldValue, newValue) -> updateControlsForState());
    txtCommitMessage.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ENTER && !txtCommitMessage.getText().isBlank()) runCommit();
    });

    txtLog.setEditable(false);
    txtLog.setWrapText(true);
    txtLog.setPrefRowCount(6);

    listChanges.setPlaceholder(new Label("No changed files"));
    listChanges.setCellFactory(lv -> new StatusCell());
    listChanges.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
    listChanges.getSelectionModel().getSelectedItems().addListener(
        (javafx.collections.ListChangeListener<GitVcsService.StatusEntry>) change -> updateControlsForState());

    btnRefresh.setOnAction(e -> refreshStatus(true));
    btnInitialize.setOnAction(e -> initializeRepository());
    btnInitialize.getStyleClass().addAll("vcs-action-button", "vcs-action-button-success");
    btnInitialize.setGraphic(CssIcon.plusBold(ICON_STAGE));
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

    contentPane.setTop(top);
    contentPane.setCenter(center);
    contentStack.getChildren().addAll(contentPane, initializingOverlay);
    StackPane.setAlignment(initializingOverlay, Pos.CENTER);
    setCenter(contentStack);

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

  private void configureGuidePopup() {
    guidePopup.setAutoHide(false);
    guidePopup.setHideOnEscape(true);
    guideArrowLabel.getStyleClass().add("vcs-guide-arrow");
    guideCard.getStyleClass().add("vcs-guide-card");
    guideTitleLabel.getStyleClass().add("vcs-guide-title");
    guideBodyLabel.getStyleClass().add("vcs-guide-body");
    guideBodyLabel.setWrapText(true);
    guideBodyLabel.setMaxWidth(GUIDE_POPUP_WIDTH - 30.0);
    guideCloseButton.getStyleClass().add("vcs-guide-close");
    guideCloseButton.setTooltip(new Tooltip("Dismiss this guidance."));
    guideCloseButton.setOnAction(e -> {
      dismissedGuideKey = lastGuideKey;
      guidePopup.hide();
    });
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox guideHeader = new HBox(8, guideTitleLabel, spacer, guideCloseButton);
    guideHeader.setAlignment(Pos.CENTER_LEFT);
    guideCard.getChildren().addAll(guideHeader, guideBodyLabel);
    guidePopupRoot.getStyleClass().add("vcs-guide-popup");
    guidePopupRoot.setAlignment(Pos.CENTER);
    guidePopupRoot.getChildren().addAll(guideArrowLabel, guideCard);
    guidePopup.getContent().setAll(guidePopupRoot);
  }

  private void configureInitializingOverlay() {
    initializingOverlay.getStyleClass().add("vcs-initializing-overlay");
    initializingOverlay.setAlignment(Pos.CENTER);
    initializingOverlay.setVisible(false);
    initializingOverlay.setManaged(false);
    initializingOverlay.setPickOnBounds(true);
    initializingSpinner.getStyleClass().add("vcs-initializing-spinner");
    initializingSpinner.setMaxSize(36, 36);
    initializingTitleLabel.getStyleClass().add("vcs-initializing-title");
    initializingBodyLabel.getStyleClass().add("vcs-initializing-body");
    initializingBodyLabel.setWrapText(true);
    initializingBodyLabel.setMaxWidth(360);
    initializingOverlay.getChildren().addAll(initializingSpinner, initializingTitleLabel, initializingBodyLabel);
  }

  public void setOnOpenRelativePath(Consumer<String> onOpenRelativePath) {
    this.onOpenRelativePath = onOpenRelativePath;
  }

  public void setProjectRoot(File projectRoot) {
    if (disposed) return;
    this.projectRoot = projectRoot;
    statusLoaded = false;
    dismissedGuideKey = "";
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
        lastRemoteFailure = "";
      }
      if (hasRemote && shouldCheckRemote(forceRemoteCheck)) {
        try {
          GitVcsService.CommandResult fetch = vcs.fetch(projectRoot);
          lastRemoteCheckMs = System.currentTimeMillis();
          remoteCheckText = "Online check: " + LocalTime.now().format(CHECK_TIME_FORMAT);
          if (!fetch.success()) {
            remoteCheckText += " (failed)";
          }
          lastRemoteFailure = fetch.success() ? "" : safe(fetch.output());
          lastRemoteCheckDisplay = remoteCheckText;
        } catch (Exception ex) {
          lastRemoteCheckMs = System.currentTimeMillis();
          lastRemoteFailure = safeMessage(ex);
          remoteCheckText = "Online check failed: " + lastRemoteFailure;
          lastRemoteCheckDisplay = remoteCheckText;
          appendLog("Online check failed: " + lastRemoteFailure);
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
        markStatusLoaded();
        setNextStep("Could not read version-control status. See Activity for details.", "vcs-next-step-danger");
        updateControlsForState();
      });
    }
  }

  private void applyNoProjectState() {
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    lastRemoteFailure = "";
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
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    lastRemoteFailure = "";
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
    markStatusLoaded();
    repositoryInitialized = false;
    currentHasRemote = false;
    currentHasUpstream = false;
    currentHasConflicts = false;
    currentAhead = 0;
    currentBehind = 0;
    currentChangeCount = 0;
    currentBranch = "";
    lastRemoteFailure = "";
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
    markStatusLoaded();
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
    } else if (hasRemote && !lastRemoteFailure.isBlank()) {
      setNextStep("Online check failed. Fix authentication or network access, then Check Online again.", "vcs-next-step-danger");
    } else if (currentBehind > 0 && currentChangeCount > 0) {
      setNextStep("Save Snapshot or Shelve your local edits, then Get Updates.", "vcs-next-step-warning");
    } else if (currentBehind > 0) {
      setNextStep("Get Updates first. The online repository has work this copy does not have yet.", "vcs-next-step-warning");
    } else if (currentChangeCount > 0) {
      setNextStep("Describe the change and click Save Snapshot.", "vcs-next-step-info");
    } else if (!hasRemote) {
      setNextStep("Recommended: connect a remote repository so the project can be backed up online.", "vcs-next-step-warning");
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

  private void updateGuidance() {
    clearGuideTargets();
    GuidanceStep step = computeGuidanceStep();
    if (step == null || step.targets().isEmpty()) {
      lastGuideKey = "";
      guidePopup.hide();
      return;
    }

    for (Node target : step.targets()) {
      if (target != null && !target.getStyleClass().contains("vcs-guide-target")) {
        target.getStyleClass().add("vcs-guide-target");
        currentGuideTargets.add(target);
      }
    }

    if (getScene() == null || getScene().getWindow() == null || busy) {
      return;
    }

    String previousGuideKey = lastGuideKey;
    lastGuideKey = step.key();
    if (step.key().equals(dismissedGuideKey)) {
      guidePopup.hide();
      return;
    }

    if (!guidePopup.isShowing() || !step.key().equals(previousGuideKey)) {
      Platform.runLater(() -> showGuidePopup(step));
    }
  }

  private void clearGuideTargets() {
    for (Node target : currentGuideTargets) {
      if (target != null) target.getStyleClass().remove("vcs-guide-target");
    }
    currentGuideTargets.clear();
  }

  private GuidanceStep computeGuidanceStep() {
    if (projectRoot == null) {
      return new GuidanceStep(
          "no-project",
          List.of(titleLabel),
          "Open a project",
          "Select a JVN project first. Version control guidance starts once a project is loaded.",
          "info");
    }
    if (!gitAvailable) {
      return new GuidanceStep(
          "git-missing",
          List.of(toolLabel),
          "Git is missing",
          "Install Git and restart the editor so snapshots, pull, and push can run.",
          "danger");
    }
    if (!repositoryInitialized) {
      return new GuidanceStep(
          "init-repository",
          List.of(btnInitialize, initBox),
          "Start here",
          "Initialize version control. This creates the local repository used for snapshots and sync.",
          "warning");
    }
    if (currentHasConflicts) {
      return new GuidanceStep(
          "conflicts",
          List.of(listChanges),
          "Resolve conflicts first",
          "Files marked Conflict must be fixed manually before Save Snapshot, Get Updates, or Send Online can continue.",
          "danger");
    }
    if (currentHasRemote && !lastRemoteFailure.isBlank()) {
      return new GuidanceStep(
          "remote-failed:" + failureBucket(lastRemoteFailure),
          List.of(btnFetch, remoteLabel),
          "Online check failed",
          remoteFailureGuidance(lastRemoteFailure),
          "danger");
    }
    if (currentBehind > 0 && currentChangeCount > 0) {
      return new GuidanceStep(
          "behind-with-edits",
          List.of(txtCommitMessage, btnCommit, btnStash),
          "Protect local edits",
          "Save Snapshot or Shelve your local changes before getting the online updates.",
          "warning");
    }
    if (currentBehind > 0) {
      return new GuidanceStep(
          "behind",
          List.of(btnPull),
          "Get updates",
          "The online repository has newer work. Pull it before pushing your own snapshots.",
          "warning");
    }
    if (currentChangeCount > 0) {
      boolean messageReady = txtCommitMessage.getText() != null && !txtCommitMessage.getText().isBlank();
      return new GuidanceStep(
          messageReady ? "commit-ready" : "commit-message",
          messageReady ? List.of(btnCommit, txtCommitMessage) : List.of(txtCommitMessage, btnCommit),
          messageReady ? "Save snapshot" : "Describe the change",
          messageReady
              ? "Click Save Snapshot to record these file changes locally."
              : "Write a short title for what changed, then click Save Snapshot.",
          "info");
    }
    if (!currentHasRemote) {
      return new GuidanceStep(
          "add-remote",
          List.of(btnConfigureRemote, setupBox),
          "Connect online backup",
          "Add or create a remote repository so this project can be shared and restored.",
          "warning");
    }
    if (currentAhead > 0 || !currentHasUpstream) {
      return new GuidanceStep(
          "push",
          List.of(btnPush),
          "Send online",
          "Upload your saved snapshot to the remote repository.",
          "success");
    }
    return new GuidanceStep(
        "clean",
        List.of(btnFetch),
        "All clear",
        "No local edits or waiting uploads. Check Online whenever you want to verify remote changes.",
        "success");
  }

  private void showGuidePopup(GuidanceStep step) {
    if (step == null || step.targets().isEmpty() || getScene() == null) return;
    Node anchor = firstVisibleTarget(step.targets());
    if (anchor == null) return;
    var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
    Window window = getScene().getWindow();
    if (bounds == null || window == null || !window.isShowing()) return;

    guideCard.getStyleClass().removeAll(
        "vcs-guide-card-info",
        "vcs-guide-card-success",
        "vcs-guide-card-warning",
        "vcs-guide-card-danger");
    guideCard.getStyleClass().add("vcs-guide-card-" + step.tone());
    guideTitleLabel.setText(step.title());
    guideBodyLabel.setText(step.body());

    double x = bounds.getMinX() + (bounds.getWidth() / 2.0) - (GUIDE_POPUP_WIDTH / 2.0);
    double y = bounds.getMaxY() + 8.0;
    double minX = window.getX() + 10.0;
    double maxX = window.getX() + Math.max(10.0, window.getWidth() - GUIDE_POPUP_WIDTH - 10.0);
    x = Math.max(minX, Math.min(x, maxX));

    guidePopup.hide();
    guidePopup.show(window, x, y);
    if (guidePopupRoot.getScene() != null && getScene() != null) {
      guidePopupRoot.getScene().getStylesheets().setAll(getScene().getStylesheets());
    }
  }

  private Node firstVisibleTarget(List<Node> targets) {
    if (targets == null) return null;
    for (Node target : targets) {
      if (target != null && target.isVisible() && target.getScene() != null) return target;
    }
    return null;
  }

  private String failureBucket(String failure) {
    String lower = failure == null ? "" : failure.toLowerCase(Locale.ROOT);
    if (lower.contains("username") || lower.contains("authentication") || lower.contains("auth")) return "auth";
    if (lower.contains("certificate") || lower.contains("ssl") || lower.contains("tls")) return "certificate";
    if (lower.contains("could not resolve") || lower.contains("timed out") || lower.contains("network")) return "network";
    if (lower.contains("remote origin already exists")) return "remote-exists";
    return "general";
  }

  private String remoteFailureGuidance(String failure) {
    String bucket = failureBucket(failure);
    return switch (bucket) {
      case "auth" -> "Git could not authenticate. Run `git fetch origin` in a terminal, sign in if prompted, then return and Check Online.";
      case "certificate" -> "The remote certificate was rejected. Check VPN/proxy/certificate settings, then run Check Online again.";
      case "network" -> "The remote could not be reached. Check VPN/network access and the remote URL, then retry.";
      default -> "Open the Activity log for details, fix the remote problem, then use Check Online again.";
    };
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
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Stage", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.stageFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Staged " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Stage failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runUnstageSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Unstage", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.unstageFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Unstaged " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Unstage failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runDiscardSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    String target = entries.size() == 1
        ? entries.get(0).path()
        : entries.size() + " selected files";
    if (!EditorDialogs.confirm(
        getScene() == null ? null : getScene().getWindow(),
        entries.size() == 1 ? "Discard File Change" : "Discard Selected File Changes",
        "Discard changes in " + target + "? This cannot be undone.",
        "Discard",
        true)) {
      appendLog("Discard cancelled.");
      return;
    }
    runAsync("Discard", () -> {
      int changed = 0;
      try {
        for (GitVcsService.StatusEntry entry : entries) {
          vcs.discardFile(projectRoot, entry.path());
          changed++;
        }
        appendLog("Discarded " + changed + " file" + plural(changed) + ".");
      } catch (Exception ex) {
        appendLog("Discard failed: " + ex.getMessage());
      }
      loadStatus(false);
    });
  }

  private void runDiffSelected() {
    List<GitVcsService.StatusEntry> entries = selectedChangeEntries();
    if (entries.isEmpty()) return;
    runAsync("Diff", () -> {
      try {
        StringBuilder output = new StringBuilder();
        for (GitVcsService.StatusEntry entry : entries) {
          String diff = vcs.diffFile(projectRoot, entry.path());
          if (diff.isBlank()) {
            output.append("No diff available for: ").append(entry.path()).append("\n\n");
          } else {
            output.append("--- diff ").append(entry.path()).append(" ---\n").append(diff).append("\n\n");
          }
        }
        appendLog(output.toString().strip());
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

  private List<GitVcsService.StatusEntry> selectedChangeEntries() {
    return List.copyOf(listChanges.getSelectionModel().getSelectedItems());
  }

  private void updateToolAvailabilityLabel(boolean git) {
    toolLabel.setText("Git: " + (git ? "ok" : "missing"));
  }

  private void runAsync(String actionName, Runnable action) {
    if (busy || disposed) return;
    setBusy(true, actionName);
    try {
      worker.submit(() -> {
        try {
          action.run();
        } catch (Exception ex) {
          appendLog(actionName + " failed: " + ex.getMessage());
        } finally {
          Platform.runLater(() -> setBusy(false, null));
        }
      });
    } catch (RejectedExecutionException ex) {
      setBusy(false, null);
    }
  }

  private void setBusy(boolean busy) {
    setBusy(busy, null);
  }

  private void setBusy(boolean busy, String actionName) {
    this.busy = busy;
    this.busyActionName = busy && actionName != null ? actionName : "";
    updateInitializingOverlay();
    updateControlsForState();
  }

  private void markStatusLoaded() {
    statusLoaded = true;
    updateInitializingOverlay();
  }

  private void updateInitializingOverlay() {
    boolean initializingRepository = busyActionName.toLowerCase(Locale.ROOT).contains("initialize");
    boolean show = busy && (!statusLoaded || initializingRepository);
    if (show) {
      if (initializingRepository) {
        initializingTitleLabel.setText("Initializing repository");
        initializingBodyLabel.setText("Creating Git tracking and preparing the first project snapshot.");
      } else {
        initializingTitleLabel.setText("Initializing version control");
        initializingBodyLabel.setText("Reading repository status, branches, changed files, and online state.");
      }
    }
    initializingOverlay.setVisible(show);
    initializingOverlay.setManaged(show);
  }

  private void updateControlsForState() {
    boolean hasProject = projectRoot != null;
    boolean repoReady = hasProject && gitAvailable && repositoryInitialized;
    boolean hasSelection = !listChanges.getSelectionModel().getSelectedItems().isEmpty();
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
    updateGuidance();
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
    guideHideTimer.stop();
    guidePopup.hide();
    clearGuideTargets();
    if (autoRefreshTimer != null) {
      autoRefreshTimer.stop();
      autoRefreshTimer = null;
    }
    worker.shutdownNow();
  }

  private record GuidanceStep(String key, List<Node> targets, String title, String body, String tone) {}

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

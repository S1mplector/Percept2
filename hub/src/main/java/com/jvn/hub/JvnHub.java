package com.jvn.hub;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Standalone engine hub. a tiny Swing app that lets the user launch the editor,
 * launcher, or runtime, run common Gradle tasks, and pull-rebase the repository
 * without touching the {@code jvnw} wrapper CLI.
 *
 * <p>Visually mirrors {@code com.jvn.editor.ui.StartupSplashOverlay}: black background,
 * blue accent, compact header + simple activity panel.</p>
 *
 * <p>Shells out to {@code ./gradlew} and {@code git}. The hub remains responsive while
 * a task runs; only one task at a time is allowed and the buttons disable for the
 * duration.</p>
 */
public final class JvnHub {

  // --- Splash-inspired color palette -----------------------------------------
  private static final Color BG             = Color.BLACK;
  private static final Color PANEL_BG       = Color.BLACK;
  private static final Color HOVER_BG       = Color.decode("#0b1422");
  private static final Color PRESSED_BG     = Color.decode("#141e30");
  private static final Color BORDER_NEUTRAL = Color.decode("#1f2a3d");
  private static final Color TEXT_PRIMARY   = Color.decode("#e6ebf5");
  private static final Color TEXT_MUTED     = Color.decode("#9caac0");
  private static final Color TEXT_SOFT      = Color.decode("#b7c3d9");
  private static final Color ACCENT_BLUE    = Color.decode("#6ea8ff");
  private static final Color ACCENT_GREEN   = Color.decode("#7ed39a");
  private static final Color ACCENT_ERROR   = Color.decode("#f38ba8");
  private static final Color LOG_TEXT       = Color.decode("#cfd8e6");
  private static final Color SCROLL_THUMB   = Color.decode("#1a2333");
  private static final Color SCROLL_THUMB_HOVER = Color.decode("#2a3a55");

  /** Resolved at class-init time from a Gradle-generated resource. */
  private static final String VERSION = readVersion();

  private final Path projectRoot;
  private final JFrame frame = new JFrame("JVN Engine Hub");
  private final JLabel statusLabel = new JLabel("Idle");
  private final JLabel versionLabel = new JLabel();
  private final ActivitySpinner activitySpinner = new ActivitySpinner();
  private final JLabel activityTitle = new JLabel("Ready");
  private final JLabel activityDetail = new JLabel("Choose an action to get started.");
  private final javax.swing.Timer spinnerTimer = new javax.swing.Timer(70, e -> activitySpinner.tick());
  private final List<JButton> actionButtons = new ArrayList<>();
  private final AtomicReference<Process> runningProcess = new AtomicReference<>();
  private final AtomicBoolean updateCheckRunning = new AtomicBoolean(false);

  /** Currently-loaded announcements; refreshed on startup and after Update Engine. */
  private final List<Announcement> announcements = new ArrayList<>();
  /** IDs (date+title) of announcements the user has already opened in the dialog. */
  private final Set<String> readIds = new HashSet<>();
  /** Bell button in the header; redrawn so the small badge reflects announcement count. */
  private AnnouncementsButton announcementsButton;
  /** Update button with a right-aligned incoming-commit badge. */
  private UpdateEngineButton updateEngineButton;

  private JvnHub(Path projectRoot) {
    this.projectRoot = projectRoot;
    // Load persisted read-state first so the badge counts only unread entries
    // on the very first paint.
    readIds.addAll(loadReadIds());
    announcements.addAll(loadAnnouncements());
    buildUi();
    checkIncomingUpdates(true);
  }

  /** Entry point. Can be invoked directly or via the {@code :hub:run} Gradle task. */
  public static void main(String[] args) {
    Path root = resolveProjectRoot(args);
    // Keep the cross-platform (Metal) L&F — Aqua on macOS tints custom backgrounds
    // with system chrome we cannot override per-component. Cross-platform L&F honors
    // setBackground/setForeground verbatim, which is what the dark theme needs.
    applyDarkDefaults();
    SwingUtilities.invokeLater(() -> {
      JvnHub hub = new JvnHub(root);
      hub.frame.setVisible(true);
    });
  }

  /** Seed UIManager so ancillary components (tooltips, split panes, dialogs) match. */
  private static void applyDarkDefaults() {
    UIManager.put("control", BG);
    UIManager.put("info", BG);
    UIManager.put("nimbusBase", BG);
    UIManager.put("nimbusBlueGrey", BORDER_NEUTRAL);
    // Root chrome that can otherwise bleed through as the cross-platform L&F's
    // default light grey behind the content pane and at the window edges.
    UIManager.put("Frame.background", BG);
    UIManager.put("Window.background", BG);
    UIManager.put("RootPane.background", BG);
    UIManager.put("Dialog.background", BG);
    UIManager.put("Panel.background", BG);
    UIManager.put("OptionPane.background", BG);
    UIManager.put("ToolTip.background", BG);
    UIManager.put("ToolTip.foreground", TEXT_SOFT);
    UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER_NEUTRAL));
    UIManager.put("ScrollPane.background", BG);
    UIManager.put("TextArea.background", BG);
    UIManager.put("TextArea.foreground", LOG_TEXT);
    UIManager.put("TextArea.caretForeground", LOG_TEXT);
    UIManager.put("ScrollBar.background", BG);
    UIManager.put("ScrollBar.track", BG);
    UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
  }

  // --- UI assembly -----------------------------------------------------------

  private void buildUi() {
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) { confirmAndExit(); }
    });

    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBackground(BG);
    root.setBorder(new EmptyBorder(16, 16, 16, 16));

    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildCenter(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    frame.setContentPane(root);
    // Force every paintable surface to pure black so the hub's body matches
    // the splash exactly — defeats the cross-platform L&F's default light-grey
    // root pane / frame background that would otherwise tint the window edges.
    frame.setBackground(BG);
    frame.getRootPane().setBackground(BG);
    frame.getRootPane().setOpaque(true);
    frame.getContentPane().setBackground(BG);

    frame.setResizable(false);
    frame.pack();
    frame.setMinimumSize(new Dimension(640, 460));
    frame.setPreferredSize(new Dimension(640, 460));
    frame.setSize(640, 460);
    frame.setLocationRelativeTo(null);

    // Render the vector logo into a raster for the OS window icon.
    frame.setIconImage(JvnLogoIcon.renderToImage(128, 128));
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(BG);

    // --- Left: vector logo + text stack -------------------------------------
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    left.setBackground(BG);

    JLabel logoLabel = new JLabel(new JvnLogoIcon(96, 56));
    left.add(logoLabel);

    JLabel title = new JLabel("Java Vector Nexus");
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

    JLabel subtitle = new JLabel("Engine Hub");
    subtitle.setForeground(TEXT_MUTED);
    subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));

    versionLabel.setText("v" + readDiskVersion());
    versionLabel.setForeground(ACCENT_BLUE);
    versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 10f));

    JPanel titleBox = new JPanel();
    titleBox.setBackground(BG);
    titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleBox.add(title);
    titleBox.add(Box.createVerticalStrut(2));
    titleBox.add(subtitle);
    titleBox.add(Box.createVerticalStrut(2));
    titleBox.add(versionLabel);

    left.add(titleBox);
    header.add(left, BorderLayout.WEST);

    // --- Right: announcements bell -----------------------------------------
    announcementsButton = new AnnouncementsButton();
    announcementsButton.refreshBadge(unreadCount());
    announcementsButton.addActionListener(e -> showAnnouncementsDialog());

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    right.setBackground(BG);
    right.add(announcementsButton);
    header.add(right, BorderLayout.EAST);

    return header;
  }

  // --- Announcements ---------------------------------------------------------

  /**
   * Reads the announcements file (committed at {@code .jvn/announcements.md} so it
   * arrives via {@code git pull --rebase}). The format is markdown-flavoured:
   * each entry begins with {@code ## YYYY-MM-DD — Title} followed by a body that
   * runs until the next {@code ## } header or end of file.
   */
  private List<Announcement> loadAnnouncements() {
    Path file = projectRoot.resolve(".jvn/announcements.md");
    if (!Files.isRegularFile(file)) return Collections.emptyList();
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      appendLog("[hub] failed to read announcements: " + e.getMessage());
      return Collections.emptyList();
    }
    List<Announcement> out = new ArrayList<>();
    String date = null;
    String title = null;
    StringBuilder body = new StringBuilder();
    for (String raw : lines) {
      if (raw.startsWith("## ")) {
        if (date != null) {
          out.add(new Announcement(date, title, body.toString().strip()));
          body.setLength(0);
        }
        // Header form: "## 2026-04-27 — Title"  or  "## 2026-04-27 - Title"
        String header = raw.substring(3).strip();
        int sep = indexOfFirst(header, " — ", " - ", " – ");
        if (sep > 0) {
          date = header.substring(0, sep).strip();
          title = header.substring(sep).replaceFirst("^[\\s\u2014\u2013-]+", "").strip();
        } else {
          date = header;
          title = "";
        }
      } else if (date != null) {
        body.append(raw).append('\n');
      }
      // Lines before the first header are intentionally ignored (file preamble).
    }
    if (date != null) {
      out.add(new Announcement(date, title, body.toString().strip()));
    }
    return out;
  }

  private static int indexOfFirst(String s, String... needles) {
    int best = -1;
    for (String n : needles) {
      int idx = s.indexOf(n);
      if (idx >= 0 && (best < 0 || idx < best)) best = idx;
    }
    return best;
  }

  private void showAnnouncementsDialog() {
    JDialog dialog = new JDialog(frame, "Announcements", true);
    dialog.setUndecorated(false);

    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBackground(BG);
    root.setBorder(new EmptyBorder(16, 16, 16, 16));

    JLabel header = new JLabel("Engine Announcements");
    header.setForeground(TEXT_PRIMARY);
    header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));

    JLabel sub = new JLabel(announcements.isEmpty()
        ? "No announcements yet. Click \"Update Engine\" to fetch the latest."
        : announcements.size() + " total \u00B7 latest first");
    sub.setForeground(TEXT_MUTED);
    sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11f));

    JPanel headerBox = new JPanel();
    headerBox.setBackground(BG);
    headerBox.setLayout(new BoxLayout(headerBox, BoxLayout.Y_AXIS));
    header.setAlignmentX(Component.LEFT_ALIGNMENT);
    sub.setAlignmentX(Component.LEFT_ALIGNMENT);
    headerBox.add(header);
    headerBox.add(Box.createVerticalStrut(2));
    headerBox.add(sub);
    root.add(headerBox, BorderLayout.NORTH);

    // List body.
    JPanel list = new JPanel();
    list.setBackground(BG);
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    list.setBorder(BorderFactory.createEmptyBorder());

    if (announcements.isEmpty()) {
      JLabel empty = new JLabel("—");
      empty.setForeground(TEXT_MUTED);
      list.add(empty);
    } else {
      for (int i = 0; i < announcements.size(); i++) {
        list.add(buildAnnouncementCard(announcements.get(i)));
        if (i < announcements.size() - 1) list.add(Box.createVerticalStrut(10));
      }
    }

    JScrollPane scroll = new JScrollPane(list);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setOpaque(true);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(new Dimension(540, 320));
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    root.add(scroll, BorderLayout.CENTER);

    FlatButton close = new FlatButton("Close",
        VectorIcon.of(VectorIcon.Kind.CLOSE, 14, TEXT_PRIMARY), null);
    close.addActionListener(e -> dialog.dispose());
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    footer.setBackground(BG);
    footer.add(close);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(frame);
    // Opening the dialog counts as reading every entry currently visible in it.
    // The badge clears before the dialog shows so the user never sees a stale count.
    markAllAnnouncementsRead();
    dialog.setVisible(true);
  }

  // --- Read-state persistence ------------------------------------------------

  private static String idOf(Announcement a) {
    return a.date + "|" + a.title;
  }

  private int unreadCount() {
    int n = 0;
    for (Announcement a : announcements) {
      if (!readIds.contains(idOf(a))) n++;
    }
    return n;
  }

  private void markAllAnnouncementsRead() {
    if (announcements.isEmpty()) return;
    boolean changed = false;
    for (Announcement a : announcements) {
      changed |= readIds.add(idOf(a));
    }
    if (changed) saveReadIds();
    if (announcementsButton != null) announcementsButton.refreshBadge(0);
  }

  /** Per-user, machine-local state file. Lives outside the repo by design. */
  private static Path readStateFile() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "hub-read.properties");
  }

  private static Set<String> loadReadIds() {
    Path file = readStateFile();
    if (!Files.isRegularFile(file)) return new HashSet<>();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
    } catch (IOException ignored) {
      return new HashSet<>();
    }
    Set<String> out = new HashSet<>();
    for (String key : props.stringPropertyNames()) {
      if ("true".equalsIgnoreCase(props.getProperty(key))) out.add(key);
    }
    return out;
  }

  private void saveReadIds() {
    Path file = readStateFile();
    try {
      Files.createDirectories(file.getParent());
    } catch (IOException e) {
      appendLog("[hub] could not create state dir: " + e.getMessage());
      return;
    }
    Properties props = new Properties();
    for (String id : readIds) props.setProperty(id, "true");
    try (var out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      props.store(out, "JVN Engine Hub \u2014 announcement read-state. Auto-generated.");
    } catch (IOException e) {
      appendLog("[hub] could not save read-state: " + e.getMessage());
    }
  }

  private JPanel buildAnnouncementCard(Announcement a) {
    JPanel card = new JPanel(new BorderLayout(0, 4));
    card.setBackground(BG);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        new EmptyBorder(10, 12, 10, 12)));

    JLabel dateLbl = new JLabel(a.date);
    dateLbl.setForeground(ACCENT_BLUE);
    dateLbl.setFont(dateLbl.getFont().deriveFont(Font.BOLD, 10f));

    JLabel titleLbl = new JLabel(a.title.isEmpty() ? "Update" : a.title);
    titleLbl.setForeground(TEXT_PRIMARY);
    titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));

    JPanel head = new JPanel();
    head.setBackground(BG);
    head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
    dateLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    head.add(dateLbl);
    head.add(Box.createVerticalStrut(1));
    head.add(titleLbl);
    card.add(head, BorderLayout.NORTH);

    JTextArea bodyArea = new JTextArea(a.body);
    bodyArea.setEditable(false);
    bodyArea.setFocusable(false);
    bodyArea.setLineWrap(true);
    bodyArea.setWrapStyleWord(true);
    bodyArea.setOpaque(true);
    bodyArea.setBackground(BG);
    bodyArea.setForeground(TEXT_SOFT);
    bodyArea.setFont(bodyArea.getFont().deriveFont(Font.PLAIN, 12f));
    bodyArea.setBorder(new EmptyBorder(4, 0, 0, 0));
    card.add(bodyArea, BorderLayout.CENTER);

    return card;
  }

  /** Small immutable record describing one announcement entry. */
  private record Announcement(String date, String title, String body) {}

  private JPanel buildCenter() {
    // 5 actions laid out as a 3-row / 2-col grid; the last cell stays empty.
    JPanel buttons = new JPanel(new GridLayout(3, 2, 10, 10));
    buttons.setBackground(BG);

    buttons.add(makeAction("Run Editor", "Launch the full JVN editor.",
        VectorIcon.Kind.EDIT, false, () -> runGradle(":editor:run")));

    buttons.add(makeAction("Run Launcher", "Launch the standalone JVN launcher.",
        VectorIcon.Kind.ROCKET, false, () -> runGradle(":editor:runLauncher")));

    buttons.add(makeAction("Build All", "Compile every module.",
        VectorIcon.Kind.HAMMER, false, () -> runGradle("build")));

    buttons.add(makeAction("Run Tests", "Execute the full test suite.",
        VectorIcon.Kind.CHECK, false, () -> runGradle("test")));

    buttons.add(makeAction("Build Shortcuts", "Install Start Menu / Applications shortcuts for this OS.",
        VectorIcon.Kind.SHORTCUT, false, this::installShortcuts));

    updateEngineButton = new UpdateEngineButton("Update Engine",
        VectorIcon.of(VectorIcon.Kind.REFRESH, 16, ACCENT_BLUE));
    updateEngineButton.setToolTipText("git pull --rebase");
    updateEngineButton.addActionListener(e -> updateEngine());
    actionButtons.add(updateEngineButton);
    buttons.add(updateEngineButton);

    JPanel center = new JPanel(new BorderLayout(0, 8));
    center.setBackground(BG);
    center.add(buttons, BorderLayout.NORTH);
    center.add(buildActivityPanel(), BorderLayout.SOUTH);
    return center;
  }

  private JPanel buildActivityPanel() {
    activityTitle.setForeground(TEXT_PRIMARY);
    activityTitle.setFont(activityTitle.getFont().deriveFont(Font.BOLD, 12f));
    activityDetail.setForeground(TEXT_MUTED);
    activityDetail.setFont(activityDetail.getFont().deriveFont(Font.PLAIN, 10f));

    JPanel text = new JPanel(new BorderLayout(10, 0));
    text.setBackground(PANEL_BG);
    text.add(activityTitle, BorderLayout.WEST);
    text.add(activityDetail, BorderLayout.CENTER);

    JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setBackground(PANEL_BG);
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        new EmptyBorder(8, 10, 8, 12)));
    panel.add(activitySpinner, BorderLayout.WEST);
    panel.add(text, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(0, 42));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    return panel;
  }

  private static void styleScrollBar(JScrollBar bar) {
    if (bar == null) return;
    bar.setUI(new DarkScrollBarUI());
    bar.setOpaque(true);
    bar.setBackground(BG);
    bar.setBorder(BorderFactory.createEmptyBorder());
    bar.setPreferredSize(new Dimension(10, 10));
    bar.setUnitIncrement(16);
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new BorderLayout());
    footer.setBackground(BG);

    statusLabel.setForeground(TEXT_SOFT);
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));

    JLabel rootLabel = new JLabel("Project: " + projectRoot.toString());
    rootLabel.setForeground(Color.decode("#75829a"));
    rootLabel.setFont(rootLabel.getFont().deriveFont(Font.PLAIN, 10f));

    FlatButton cancel = new FlatButton("Cancel",
        VectorIcon.of(VectorIcon.Kind.STOP, 14, ACCENT_ERROR), ACCENT_ERROR);
    cancel.addActionListener(e -> cancelRunning());

    FlatButton quit = new FlatButton("Quit",
        VectorIcon.of(VectorIcon.Kind.CLOSE, 14, TEXT_PRIMARY), null);
    quit.addActionListener(e -> confirmAndExit());

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.setBackground(BG);
    right.add(cancel);
    right.add(quit);

    JPanel left = new JPanel();
    left.setBackground(BG);
    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    rootLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    left.add(statusLabel);
    left.add(rootLabel);

    footer.add(left, BorderLayout.WEST);
    footer.add(right, BorderLayout.EAST);
    return footer;
  }

  private JButton makeAction(String label, String tooltip, VectorIcon.Kind iconKind,
                             boolean accent, Runnable action) {
    Color foreground = accent ? ACCENT_BLUE : TEXT_PRIMARY;
    Icon icon = iconKind != null ? VectorIcon.of(iconKind, 16, foreground) : null;
    FlatButton button = new FlatButton(label, icon, accent ? ACCENT_BLUE : null);
    button.setToolTipText(tooltip);
    button.addActionListener(e -> action.run());
    actionButtons.add(button);
    return button;
  }

  // --- Task execution --------------------------------------------------------

  private void runGradle(String task) {
    if (!acquire(task)) return;
    List<String> cmd = new ArrayList<>();
    cmd.add(gradleCommand());
    cmd.add("--console=plain");
    cmd.add(task);
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, task);
  }

  private void updateEngine() {
    if (!acquire("git pull --rebase")) return;
    if (updateEngineButton != null) updateEngineButton.setChecking(true);
    List<String> cmd = List.of("git", "pull", "--rebase");
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, "Update Engine");
  }

  private void installShortcuts() {
    String os = System.getProperty("os.name", "").toLowerCase();
    List<String> cmd = new ArrayList<>();
    Path script;
    String label = "Build Shortcuts";

    if (os.contains("win")) {
      script = projectRoot.resolve("install-windows-launcher.ps1");
      String powershell = commandExists("pwsh") ? "pwsh" : "powershell";
      cmd.add(powershell);
      cmd.add("-NoProfile");
      cmd.add("-ExecutionPolicy");
      cmd.add("Bypass");
      cmd.add("-File");
      cmd.add(script.toAbsolutePath().toString());
    } else if (os.contains("mac") || os.contains("darwin")) {
      script = projectRoot.resolve("install-macos-launcher.sh");
      cmd.add("bash");
      cmd.add(script.toAbsolutePath().toString());
    } else {
      script = projectRoot.resolve("install-linux-launcher.sh");
      cmd.add("bash");
      cmd.add(script.toAbsolutePath().toString());
    }

    if (!Files.isRegularFile(script)) {
      appendLog("[hub] shortcut installer not found: " + script.toAbsolutePath());
      setStatus("Failed: " + label, ACCENT_ERROR);
      return;
    }

    if (!acquire(label)) return;
    appendLog("$ " + quoteCommandForLog(cmd));
    startProcess(cmd, label);
  }

  private boolean acquire(String label) {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return false;
    }
    setButtonsEnabled(false);
    setStatus("Running: " + label, ACCENT_BLUE);
    setActivity("Working on " + label, "This can take a moment.", true, ACCENT_BLUE);
    return true;
  }

  private void release(String label, int exitCode) {
    runningProcess.set(null);
    setButtonsEnabled(true);
    Color tone = exitCode == 0 ? ACCENT_GREEN : ACCENT_ERROR;
    String prefix = exitCode == 0 ? "Done" : "Failed (exit " + exitCode + ")";
    setStatus(prefix + ": " + label, tone);
    setActivity(prefix + ": " + label,
        exitCode == 0 ? "Ready for the next action." : "Check the terminal or generated launcher log for details.",
        false,
        tone);
  }

  private void startProcess(List<String> command, String label) {
    new SwingWorker<Integer, String>() {
      private String lastOutput = "";

      @Override protected Integer doInBackground() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true);
        // Gradle daemons can interact badly when invoked from within another Gradle
        // task JVM. --no-daemon keeps child gradle invocations self-contained.
        if (command.get(0).endsWith("gradlew") || command.get(0).endsWith("gradlew.bat")) {
          List<String> augmented = new ArrayList<>(command);
          augmented.add(1, "--no-daemon");
          pb.command(augmented);
        }
        Process process;
        try {
          process = pb.start();
        } catch (IOException e) {
          publish("[hub] failed to start process: " + e.getMessage());
          return -1;
        }
        runningProcess.set(process);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
              lastOutput = compactMessage(line);
            }
          }
        } catch (IOException e) {
          lastOutput = "Stream error: " + e.getMessage();
        }
        return process.waitFor();
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          appendLog(chunks.get(chunks.size() - 1));
        }
      }

      @Override protected void done() {
        int exit;
        try {
          exit = get();
        } catch (Exception e) {
          exit = -1;
          lastOutput = "Task raised: " + e.getMessage();
        }
        release(label, exit);
        if (!lastOutput.isBlank()) {
          setActivityDetail(lastOutput);
        }
        // Update Engine touched the working tree — re-read on-disk state so
        // the version label and announcements list reflect the new HEAD.
        if (exit == 0 && "Update Engine".equals(label)) {
          refreshFromDisk();
          checkIncomingUpdates(false);
        } else if ("Update Engine".equals(label) && updateEngineButton != null) {
          updateEngineButton.setChecking(false);
        }
      }
    }.execute();
  }

  private void cancelRunning() {
    Process p = runningProcess.get();
    if (p == null) {
      appendLog("[hub] no task is running.");
      return;
    }
    appendLog("[hub] cancelling current task...");
    setActivity("Cancelling task", "Stopping the running process.", true, ACCENT_ERROR);
    p.descendants().forEach(ProcessHandle::destroy);
    p.destroy();
  }

  private void confirmAndExit() {
    Process p = runningProcess.get();
    if (p != null) {
      appendLog("[hub] cancelling running task before exit...");
      setActivity("Cancelling task", "Closing the hub after the process stops.", true, ACCENT_ERROR);
      p.descendants().forEach(ProcessHandle::destroy);
      p.destroy();
    }
    if (spinnerTimer.isRunning()) spinnerTimer.stop();
    frame.dispose();
    // Give child processes a moment to die.
    new Thread(() -> {
      try { Thread.sleep(150); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
      System.exit(0);
    }, "jvn-hub-shutdown").start();
  }

  // --- Helpers ---------------------------------------------------------------

  private void setButtonsEnabled(boolean enabled) {
    SwingUtilities.invokeLater(() -> actionButtons.forEach(b -> b.setEnabled(enabled)));
  }

  private void setStatus(String text, Color color) {
    SwingUtilities.invokeLater(() -> {
      statusLabel.setText(text);
      statusLabel.setForeground(color != null ? color : TEXT_SOFT);
    });
  }

  private void appendLog(String line) {
    if (line == null) return;
    setActivityDetail(compactMessage(line));
  }

  private void setActivity(String title, String detail, boolean spinning, Color tone) {
    SwingUtilities.invokeLater(() -> {
      activityTitle.setText(compactMessage(title));
      activityTitle.setForeground(tone != null ? tone : TEXT_PRIMARY);
      activityDetail.setText(compactMessage(detail));
      activitySpinner.setActive(spinning);
      if (spinning && !spinnerTimer.isRunning()) {
        spinnerTimer.start();
      } else if (!spinning && spinnerTimer.isRunning()) {
        spinnerTimer.stop();
      }
    });
  }

  private void setActivityDetail(String detail) {
    SwingUtilities.invokeLater(() -> activityDetail.setText(compactMessage(detail)));
  }

  private void checkIncomingUpdates(boolean fetchFirst) {
    if (updateEngineButton == null) return;
    if (!Files.isDirectory(projectRoot.resolve(".git"))) {
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!commandExists("git")) {
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!updateCheckRunning.compareAndSet(false, true)) return;

    updateEngineButton.setChecking(true);
    new SwingWorker<Integer, Void>() {
      @Override protected Integer doInBackground() {
        if (fetchFirst) {
          runGit(List.of("git", "fetch", "--quiet", "--prune", "--no-tags"), 45);
        }
        CommandResult result = runGit(List.of("git", "rev-list", "--count", "HEAD..@{upstream}"), 10);
        if (result.exitCode != 0) return -1;
        try {
          return Math.max(0, Integer.parseInt(result.output.strip()));
        } catch (NumberFormatException e) {
          return -1;
        }
      }

      @Override protected void done() {
        updateCheckRunning.set(false);
        int count = -1;
        try {
          count = get();
        } catch (Exception ignored) {
          // Keep the badge hidden when Git cannot report an upstream count.
        }
        updateEngineButton.setIncomingCount(count);
      }
    }.execute();
  }

  private CommandResult runGit(List<String> command, long timeoutSeconds) {
    Process process;
    try {
      process = new ProcessBuilder(command)
          .directory(projectRoot.toFile())
          .redirectErrorStream(true)
          .start();
    } catch (IOException e) {
      return new CommandResult(-1, e.getMessage());
    }

    StringBuilder output = new StringBuilder();
    Thread reader = new Thread(() -> {
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (output.length() < 4096) output.append(line).append('\n');
        }
      } catch (IOException ignored) {
        // Best-effort capture only.
      }
    }, "jvn-hub-git-output");
    reader.setDaemon(true);
    reader.start();

    try {
      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new CommandResult(-1, "timed out");
      }
      reader.join(1000);
      return new CommandResult(process.exitValue(), output.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return new CommandResult(-1, "interrupted");
    }
  }

  private record CommandResult(int exitCode, String output) {}

  private static String compactMessage(String text) {
    if (text == null) return "";
    String compact = text.replaceAll("\\s+", " ").trim();
    if (compact.startsWith("[hub] ")) compact = compact.substring(6);
    if (compact.length() > 96) {
      compact = compact.substring(0, 93) + "...";
    }
    return compact;
  }

  private String gradleCommand() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String name = os.contains("win") ? "gradlew.bat" : "gradlew";
    Path wrapper = projectRoot.resolve(name);
    return wrapper.toAbsolutePath().toString();
  }

  private boolean commandExists(String command) {
    if (command == null || command.isBlank()) return false;
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) return false;
    String os = System.getProperty("os.name", "").toLowerCase();
    String[] dirs = path.split(java.io.File.pathSeparator);
    if (os.contains("win")) {
      String pathext = System.getenv("PATHEXT");
      String[] exts = pathext == null || pathext.isBlank()
          ? new String[] { ".exe", ".cmd", ".bat", "" }
          : pathext.toLowerCase().split(";");
      for (String dir : dirs) {
        for (String ext : exts) {
          Path candidate = Paths.get(dir, command + ext);
          if (Files.isRegularFile(candidate) || Files.isExecutable(candidate)) return true;
        }
      }
      return false;
    }
    for (String dir : dirs) {
      Path candidate = Paths.get(dir, command);
      if (Files.isExecutable(candidate)) return true;
    }
    return false;
  }

  private static String quoteCommandForLog(List<String> command) {
    if (command == null || command.isEmpty()) return "";
    List<String> quoted = new ArrayList<>();
    for (String part : command) {
      if (part == null) continue;
      boolean needsQuotes = part.isBlank()
          || part.chars().anyMatch(Character::isWhitespace)
          || part.contains("\"");
      quoted.add(needsQuotes ? "\"" + part.replace("\"", "\\\"") + "\"" : part);
    }
    return String.join(" ", quoted);
  }

  private static String readVersion() {
    try (InputStream in = JvnHub.class.getResourceAsStream("/com/jvn/hub/version.properties")) {
      if (in != null) {
        Properties props = new Properties();
        props.load(in);
        String v = props.getProperty("version");
        if (v != null && !v.isBlank()) return v.trim();
      }
    } catch (IOException ignored) {
      // fall through
    }
    return "dev";
  }

  /**
   * Live version lookup: read {@code gradle.properties} from disk so an Update
   * Engine pull surfaces a bumped {@code jvnVersion} without rebuilding. Falls
   * back to the build-time classpath resource if the file is unreadable.
   */
  private String readDiskVersion() {
    Path props = projectRoot.resolve("gradle.properties");
    if (Files.isRegularFile(props)) {
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(props)) {
        p.load(in);
        String v = p.getProperty("jvnVersion");
        if (v != null && !v.isBlank()) return v.trim();
      } catch (IOException ignored) {
        // fall through
      }
    }
    return VERSION;
  }

  /**
   * Re-reads the version + announcements file and updates the corresponding
   * Swing components. Safe to call from any thread; UI updates are dispatched
   * to the EDT.
   */
  private void refreshFromDisk() {
    String newVersion = readDiskVersion();
    List<Announcement> fresh = loadAnnouncements();
    SwingUtilities.invokeLater(() -> {
      versionLabel.setText("v" + newVersion);
      announcements.clear();
      announcements.addAll(fresh);
      int unread = unreadCount();
      if (announcementsButton != null) announcementsButton.refreshBadge(unread);
      appendLog("[hub] refresh: " + fresh.size()
          + " announcement" + (fresh.size() == 1 ? "" : "s")
          + " (" + unread + " unread). Version: " + newVersion + ".");
      frame.repaint();
    });
  }

  private static Path resolveProjectRoot(String[] args) {
    // 1. Explicit override from args.
    for (int i = 0; i < args.length - 1; i++) {
      if ("--project-root".equals(args[i])) return Paths.get(args[i + 1]).toAbsolutePath();
    }
    // 2. System property set by the :hub:run Gradle task.
    String prop = System.getProperty("jvn.projectRoot");
    if (prop != null && !prop.isBlank()) return Paths.get(prop).toAbsolutePath();
    // 3. Walk up from CWD looking for gradlew + settings.gradle.kts.
    Path cwd = Paths.get(".").toAbsolutePath().normalize();
    Path probe = cwd;
    for (int i = 0; i < 8 && probe != null; i++) {
      if (looksLikeProjectRoot(probe)) return probe;
      probe = probe.getParent();
    }
    return cwd;
  }

  private static boolean looksLikeProjectRoot(Path dir) {
    return Files.isRegularFile(dir.resolve("gradlew"))
        || Files.isRegularFile(dir.resolve("gradlew.bat"))
        || Files.isRegularFile(dir.resolve("settings.gradle.kts"))
        || Files.isDirectory(dir.resolve(".git"));
  }

  // ===========================================================================
  //  Nested UI primitives: flat dark button, vector icons, scrollbar UI.
  // ===========================================================================

  /** Compact indeterminate spinner used in place of terminal-style output. */
  private static final class ActivitySpinner extends JComponent {
    private int frame = 0;
    private boolean active = false;

    ActivitySpinner() {
      setOpaque(false);
      setPreferredSize(new Dimension(24, 24));
      setMinimumSize(new Dimension(24, 24));
      setMaximumSize(new Dimension(24, 24));
    }

    void setActive(boolean active) {
      this.active = active;
      if (!active) frame = 0;
      repaint();
    }

    void tick() {
      if (!active) return;
      frame = (frame + 1) % 12;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      float cx = w / 2f;
      float cy = h / 2f;
      float radius = Math.min(w, h) * 0.34f;

      g2.setColor(active ? Color.decode("#07101f") : BG);
      g2.fillOval(Math.round(cx - radius - 4), Math.round(cy - radius - 4),
          Math.round((radius + 4) * 2), Math.round((radius + 4) * 2));

      for (int i = 0; i < 12; i++) {
        int age = active ? Math.floorMod(i - frame, 12) : i;
        float alpha = active ? (0.22f + (11 - age) * 0.065f) : 0.16f;
        double angle = (Math.PI * 2.0 * i / 12.0) - Math.PI / 2.0;
        float x = cx + (float) Math.cos(angle) * radius;
        float y = cy + (float) Math.sin(angle) * radius;
        int dot = active && age == 0 ? 4 : 3;
        g2.setColor(withAlpha(active ? ACCENT_BLUE : TEXT_MUTED, Math.min(1.0f, alpha)));
        g2.fillOval(Math.round(x - dot / 2f), Math.round(y - dot / 2f), dot, dot);
      }

      if (!active) {
        g2.setColor(ACCENT_GREEN);
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(cx - 6, cy);
        check.lineTo(cx - 1, cy + 5);
        check.lineTo(cx + 7, cy - 6);
        g2.draw(check);
      }
      g2.dispose();
    }

    private static Color withAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255f));
    }
  }

  /**
   * Custom-painted button that stays fully black (no L&F chrome) and paints its own
   * 1px border with optional accent color. Text + icon render via {@code super.paintComponent}.
   */
  private static class FlatButton extends JButton {
    private final Color accent;

    FlatButton(String text, Icon icon, Color accentOrNull) {
      super(text);
      this.accent = accentOrNull;
      if (icon != null) {
        setIcon(icon);
        setIconTextGap(10);
      }
      setHorizontalAlignment(SwingConstants.CENTER);
      setHorizontalTextPosition(SwingConstants.RIGHT);
      setForeground(accentOrNull != null ? accentOrNull : TEXT_PRIMARY);
      setFont(getFont().deriveFont(Font.PLAIN, 12f));
      setBorder(new EmptyBorder(10, 18, 10, 18));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arc = 8;
      int w = getWidth();
      int h = getHeight();

      Color fill;
      if (!isEnabled()) {
        fill = BG;
      } else if (getModel().isPressed()) {
        fill = PRESSED_BG;
      } else if (getModel().isRollover()) {
        fill = HOVER_BG;
      } else {
        fill = PANEL_BG;
      }
      g2.setColor(fill);
      g2.fillRoundRect(0, 0, w, h, arc, arc);

      Color borderColor = accent != null ? accent : BORDER_NEUTRAL;
      if (!isEnabled()) borderColor = BORDER_NEUTRAL;
      g2.setColor(borderColor);
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      g2.dispose();
      super.paintComponent(g);
    }
  }

  /** Update button that paints incoming commit count as a right-aligned badge. */
  private static final class UpdateEngineButton extends FlatButton {
    private int incomingCount = -1;
    private boolean checking = false;

    UpdateEngineButton(String text, Icon icon) {
      super(text, icon, ACCENT_BLUE);
      setBorder(new EmptyBorder(10, 18, 10, 52));
    }

    void setChecking(boolean checking) {
      SwingUtilities.invokeLater(() -> {
        this.checking = checking;
        if (checking && incomingCount < 0) {
          setToolTipText("Checking for incoming engine updates...");
        } else if (!checking) {
          refreshTooltip();
        }
        repaint();
      });
    }

    void setIncomingCount(int count) {
      SwingUtilities.invokeLater(() -> {
        this.checking = false;
        this.incomingCount = count;
        refreshTooltip();
        repaint();
      });
    }

    private void refreshTooltip() {
      if (incomingCount > 0) {
        setToolTipText(incomingCount + " incoming commit" + (incomingCount == 1 ? "" : "s")
            + " available. Click to pull --rebase.");
      } else if (incomingCount == 0) {
        setToolTipText("Engine is up to date.");
      } else {
        setToolTipText("Update Engine — incoming commit count unavailable.");
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (incomingCount <= 0) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      String text = incomingCount > 99 ? "99+" : Integer.toString(incomingCount);
      Font badgeFont = getFont().deriveFont(Font.BOLD, 11f);
      g2.setFont(badgeFont);
      FontMetrics fm = g2.getFontMetrics();
      int textW = fm.stringWidth(text);
      int badgeH = 22;
      int badgeW = Math.max(26, textW + 14);
      int badgeX = getWidth() - badgeW - 14;
      int badgeY = (getHeight() - badgeH) / 2;

      Color fill = isEnabled() ? ACCENT_GREEN : BORDER_NEUTRAL;
      g2.setColor(fill);
      g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, badgeH, badgeH);
      g2.setColor(BG);
      int textX = badgeX + (badgeW - textW) / 2;
      int textY = badgeY + badgeH - (badgeH - fm.getAscent() + fm.getDescent()) / 2 - 1;
      g2.drawString(text, textX, textY);
      g2.dispose();
    }
  }

  /**
   * Borderless bell button with a small numeric badge in the top-right corner.
   * The badge auto-hides when the announcement count is zero. Painted entirely
   * by Swing — no rasters required.
   */
  private static final class AnnouncementsButton extends JButton {
    private int count = 0;

    AnnouncementsButton() {
      setIcon(VectorIcon.of(VectorIcon.Kind.BELL, 22, TEXT_PRIMARY));
      setToolTipText("Announcements — small updates pulled from the engine");
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(new EmptyBorder(8, 8, 8, 8));
      setPreferredSize(new Dimension(40, 40));
    }

    void refreshBadge(int newCount) {
      this.count = Math.max(0, newCount);
      setToolTipText(count == 0
          ? "No announcements yet"
          : count + " announcement" + (count == 1 ? "" : "s") + " — click to read");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      // Subtle hover halo so the bell signals interactivity.
      if (getModel().isRollover() || getModel().isPressed()) {
        Graphics2D h = (Graphics2D) g.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        h.setColor(getModel().isPressed() ? PRESSED_BG : HOVER_BG);
        h.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        h.dispose();
      }
      super.paintComponent(g);

      if (count <= 0) return;

      // Badge: small filled circle + numeric label, anchored top-right.
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      String text = count > 9 ? "9+" : Integer.toString(count);
      Font badgeFont = getFont().deriveFont(Font.BOLD, 9f);
      g2.setFont(badgeFont);
      FontMetrics fm = g2.getFontMetrics();
      int textW = fm.stringWidth(text);
      int diameter = Math.max(14, textW + 8);
      int badgeX = getWidth() - diameter - 4;
      int badgeY = 4;

      g2.setColor(ACCENT_ERROR);
      g2.fillRoundRect(badgeX, badgeY, diameter, 14, 14, 14);
      g2.setColor(Color.WHITE);
      int textX = badgeX + (diameter - textW) / 2;
      int textY = badgeY + 14 - (14 - fm.getAscent() + fm.getDescent()) / 2 - 1;
      g2.drawString(text, textX, textY);
      g2.dispose();
    }
  }

  /**
   * Resolution-independent vector icon painted via Java2D. Color and size are both
   * configurable so the same {@link Kind} can be reused across contexts.
   */
  private static final class VectorIcon implements Icon {
    enum Kind { PLAY, EDIT, ROCKET, HAMMER, CHECK, REFRESH, STOP, CLOSE, BELL, SHORTCUT }

    private final Kind kind;
    private final int size;
    private final Color color;

    private VectorIcon(Kind kind, int size, Color color) {
      this.kind = kind;
      this.size = size;
      this.color = color;
    }

    static VectorIcon of(Kind kind, int size, Color color) {
      return new VectorIcon(kind, size, color);
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g2.translate(x, y);
      g2.setColor(color);
      float s = size;
      // Strokes are sized as a fraction of the icon so all icons read consistently
      // at any size and never look hairline at large sizes.
      float strokeMain = Math.max(1.4f, s * 0.10f);
      float strokeBold = Math.max(1.6f, s * 0.13f);
      g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

      switch (kind) {
        case PLAY -> {
          // Equilateral-ish triangle, fill + stroke gives slightly rounded corners.
          Path2D tri = new Path2D.Float();
          tri.moveTo(s * 0.30f, s * 0.20f);
          tri.lineTo(s * 0.80f, s * 0.50f);
          tri.lineTo(s * 0.30f, s * 0.80f);
          tri.closePath();
          g2.setStroke(new BasicStroke(s * 0.14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(tri);
          g2.fill(tri);
        }
        case EDIT -> {
          // Pencil drawn vertically into a rotated frame, then tilted -45°
          // (eraser at top-right, graphite tip at bottom-left).
          AffineTransform saved = g2.getTransform();
          g2.translate(s * 0.50f, s * 0.50f);
          g2.rotate(Math.PI / 4);

          float w = s * 0.22f;          // pencil width
          float halfBody = s * 0.24f;   // half the wood body length
          float eraserH = s * 0.08f;
          float ferruleH = s * 0.04f;
          float tipH = s * 0.12f;
          float corner = s * 0.04f;

          // Eraser — small rounded rectangle above the wood body.
          g2.fill(new RoundRectangle2D.Float(
              -w / 2f, -halfBody - eraserH - ferruleH,
              w, eraserH, corner, corner));
          // Ferrule (metal band) — same width, no rounding.
          g2.fillRect(
              (int) Math.round(-w / 2f), (int) Math.round(-halfBody - ferruleH),
              (int) Math.round(w), (int) Math.round(ferruleH));
          // Wood body — long rectangle.
          g2.fillRect(
              (int) Math.round(-w / 2f), (int) Math.round(-halfBody),
              (int) Math.round(w), (int) Math.round(halfBody * 2));
          // Graphite tip — triangle below the body.
          Path2D tip = new Path2D.Float();
          tip.moveTo(-w / 2f, halfBody);
          tip.lineTo(0f, halfBody + tipH);
          tip.lineTo(w / 2f, halfBody);
          tip.closePath();
          g2.fill(tip);

          g2.setTransform(saved);
        }
        case ROCKET -> {
          // Pointed-nose capsule with two angled side fins and a small tail flame.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.50f, s * 0.08f);
          body.curveTo(s * 0.66f, s * 0.18f, s * 0.66f, s * 0.32f, s * 0.66f, s * 0.42f);
          body.lineTo(s * 0.66f, s * 0.74f);
          body.lineTo(s * 0.34f, s * 0.74f);
          body.lineTo(s * 0.34f, s * 0.42f);
          body.curveTo(s * 0.34f, s * 0.32f, s * 0.34f, s * 0.18f, s * 0.50f, s * 0.08f);
          body.closePath();
          g2.fill(body);

          // Side fins — sweep down and out from the lower body.
          Path2D finL = new Path2D.Float();
          finL.moveTo(s * 0.34f, s * 0.56f);
          finL.lineTo(s * 0.16f, s * 0.84f);
          finL.lineTo(s * 0.34f, s * 0.78f);
          finL.closePath();
          g2.fill(finL);

          Path2D finR = new Path2D.Float();
          finR.moveTo(s * 0.66f, s * 0.56f);
          finR.lineTo(s * 0.84f, s * 0.84f);
          finR.lineTo(s * 0.66f, s * 0.78f);
          finR.closePath();
          g2.fill(finR);

          // Tail flame — small downward triangle exiting the bottom of the body.
          Path2D flame = new Path2D.Float();
          flame.moveTo(s * 0.42f, s * 0.78f);
          flame.lineTo(s * 0.50f, s * 0.92f);
          flame.lineTo(s * 0.58f, s * 0.78f);
          flame.closePath();
          g2.fill(flame);
        }
        case HAMMER -> {
          // Classic T-shape: wide rounded head sitting on a vertical handle.
          float headW = s * 0.72f;
          float headH = s * 0.26f;
          float headX = (s - headW) / 2f;
          float headY = s * 0.18f;
          float handleW = s * 0.18f;
          float handleX = (s - handleW) / 2f;
          float handleY = headY + headH - s * 0.02f;
          float handleH = s - handleY - s * 0.10f;
          float corner = s * 0.06f;

          g2.fill(new RoundRectangle2D.Float(headX, headY, headW, headH, corner, corner));
          g2.fill(new RoundRectangle2D.Float(handleX, handleY, handleW, handleH, corner, corner));
        }
        case CHECK -> {
          // Bold tick with rounded joins; balanced inside the icon box.
          Path2D check = new Path2D.Float();
          check.moveTo(s * 0.18f, s * 0.52f);
          check.lineTo(s * 0.42f, s * 0.76f);
          check.lineTo(s * 0.84f, s * 0.28f);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(check);
        }
        case REFRESH -> {
          // Open circle with a tangent arrowhead at the start, pointing into the rotation direction.
          float r = s * 0.34f;
          float cx = s * 0.50f;
          float cy = s * 0.50f;
          // Arc spans 290° starting at 50° (top-right gap) — leaves room for the arrow.
          Shape arc = new Arc2D.Float(cx - r, cy - r, r * 2f, r * 2f, 50f, 290f, Arc2D.OPEN);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(arc);

          // Arrowhead at the arc's start point (50°), pointing tangent to the circle.
          double theta = Math.toRadians(50);
          float ax = (float) (cx + r * Math.cos(theta));
          float ay = (float) (cy - r * Math.sin(theta));
          // Tangent direction for clockwise traversal at this point: (sin θ, cos θ) in screen coords.
          float tx = (float) Math.sin(theta);
          float ty = (float) Math.cos(theta);
          // Perpendicular for arrow width.
          float px = -ty;
          float py = tx;
          float headLen = s * 0.22f;
          float headWid = s * 0.11f;
          Path2D head = new Path2D.Float();
          head.moveTo(ax + tx * headLen * 0.5f, ay + ty * headLen * 0.5f);
          head.lineTo(ax - tx * headLen * 0.5f + px * headWid,
                      ay - ty * headLen * 0.5f + py * headWid);
          head.lineTo(ax - tx * headLen * 0.5f - px * headWid,
                      ay - ty * headLen * 0.5f - py * headWid);
          head.closePath();
          g2.fill(head);
        }
        case STOP -> {
          // Slightly-rounded filled square; corner radius scales with icon size.
          float pad = s * 0.22f;
          float r = s * 0.10f;
          g2.fill(new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s - pad * 2f, r, r));
        }
        case CLOSE -> {
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine((int) Math.round(s * 0.26f), (int) Math.round(s * 0.26f),
                      (int) Math.round(s * 0.74f), (int) Math.round(s * 0.74f));
          g2.drawLine((int) Math.round(s * 0.74f), (int) Math.round(s * 0.26f),
                      (int) Math.round(s * 0.26f), (int) Math.round(s * 0.74f));
        }
        case SHORTCUT -> {
          // Desktop/app shortcut: small window tile with a launch arrow.
          float pad = s * 0.16f;
          float corner = s * 0.08f;
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new RoundRectangle2D.Float(pad, pad, s * 0.58f, s * 0.58f, corner, corner));
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D arrow = new Path2D.Float();
          arrow.moveTo(s * 0.42f, s * 0.68f);
          arrow.lineTo(s * 0.82f, s * 0.28f);
          g2.draw(arrow);
          Path2D head = new Path2D.Float();
          head.moveTo(s * 0.82f, s * 0.28f);
          head.lineTo(s * 0.80f, s * 0.52f);
          head.lineTo(s * 0.58f, s * 0.30f);
          head.closePath();
          g2.fill(head);
        }
        case BELL -> {
          // Top stem (the loop where a real bell would hang).
          float stemW = s * 0.12f;
          float stemH = s * 0.08f;
          g2.fill(new RoundRectangle2D.Float(
              (s - stemW) / 2f, s * 0.06f, stemW, stemH, s * 0.04f, s * 0.04f));

          // Dome that flares into a wide flange — closes flat across the bottom.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.50f, s * 0.16f);
          body.curveTo(s * 0.78f, s * 0.20f, s * 0.78f, s * 0.50f, s * 0.84f, s * 0.70f);
          body.lineTo(s * 0.16f, s * 0.70f);
          body.curveTo(s * 0.22f, s * 0.50f, s * 0.22f, s * 0.20f, s * 0.50f, s * 0.16f);
          body.closePath();
          g2.fill(body);

          // Clapper — small ball just below the flange.
          float clapper = s * 0.16f;
          g2.fill(new Ellipse2D.Float((s - clapper) / 2f, s * 0.74f, clapper, clapper));
        }
      }
      g2.dispose();
    }
  }

  /**
   * Vector logomark for the JVN engine — fully painted, no PNG dependency.
   * Renders "JVN" in bold sans-serif with a vertical orange gradient (light
   * peach at the top fading into deep orange at the baseline) — an
   * understated nod to the engine's original fiery wordmark, on a black field.
   * {@link #renderToImage(int, int)} exposes the same artwork as a raster so
   * the OS window/dock icon stays in sync.
   */
  private static final class JvnLogoIcon implements Icon {
    private static final Color ORANGE_TOP    = Color.decode("#ffe2a8");
    private static final Color ORANGE_BOTTOM = Color.decode("#ff5a1f");

    private final int width;
    private final int height;

    JvnLogoIcon(int width, int height) {
      this.width = width;
      this.height = height;
    }

    /** Render the logo into a fresh ARGB raster suitable for {@link JFrame#setIconImage}. */
    static BufferedImage renderToImage(int w, int h) {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      try {
        new JvnLogoIcon(w, h).paintIcon(null, g, 0, 0);
      } finally {
        g.dispose();
      }
      return img;
    }

    @Override public int getIconWidth()  { return width; }
    @Override public int getIconHeight() { return height; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g2.translate(x, y);

      // "JVN" wordmark, vertically centered, with a top-to-bottom orange gradient.
      int jvnSize = Math.max(14, Math.round(height * 0.78f));
      Font jvnFont = new Font(Font.SANS_SERIF, Font.BOLD, jvnSize);
      g2.setFont(jvnFont);
      FontMetrics fm = g2.getFontMetrics();
      String jvn = "JVN";
      int jvnW = fm.stringWidth(jvn);
      int jvnX = (width - jvnW) / 2;
      int textTop = (height - (fm.getAscent() + fm.getDescent())) / 2;
      int jvnY = textTop + fm.getAscent();

      GradientPaint gradient = new GradientPaint(
          0, jvnY - fm.getAscent(), ORANGE_TOP,
          0, jvnY + fm.getDescent(), ORANGE_BOTTOM);
      g2.setPaint(gradient);
      g2.drawString(jvn, jvnX, jvnY);

      g2.dispose();
    }
  }

  /**
   * Flat dark scrollbar UI: no arrow buttons, rounded thumb, BG-matching track.
   * Lightens the thumb on hover/drag for subtle feedback.
   */
  private static final class DarkScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
      this.thumbColor = SCROLL_THUMB;
      this.trackColor = BG;
      this.thumbDarkShadowColor = BG;
      this.thumbHighlightColor = SCROLL_THUMB;
      this.thumbLightShadowColor = SCROLL_THUMB;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
    @Override
    protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

    private static JButton zeroButton() {
      JButton b = new JButton();
      Dimension zero = new Dimension(0, 0);
      b.setPreferredSize(zero);
      b.setMinimumSize(zero);
      b.setMaximumSize(zero);
      b.setVisible(false);
      return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
      g.setColor(BG);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
      if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color fill = (isThumbRollover() || isDragging) ? SCROLL_THUMB_HOVER : SCROLL_THUMB;
      int inset = 2;
      int arc = Math.min(bounds.width, bounds.height) - inset * 2;
      if (arc < 4) arc = 4;
      g2.setColor(fill);
      g2.fillRoundRect(bounds.x + inset, bounds.y + inset,
          bounds.width - inset * 2, bounds.height - inset * 2, arc, arc);
      g2.dispose();
    }
  }
}

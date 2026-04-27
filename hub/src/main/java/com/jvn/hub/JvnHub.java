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
import java.awt.Stroke;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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
 * blue accent, compact header + log panel.</p>
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
  private final JTextArea logArea = new JTextArea();
  private final List<JButton> actionButtons = new ArrayList<>();
  private final AtomicReference<Process> runningProcess = new AtomicReference<>();

  /** Currently-loaded announcements; refreshed on startup and after Update Engine. */
  private final List<Announcement> announcements = new ArrayList<>();
  /** IDs (date+title) of announcements the user has already opened in the dialog. */
  private final Set<String> readIds = new HashSet<>();
  /** Bell button in the header; redrawn so the small badge reflects announcement count. */
  private AnnouncementsButton announcementsButton;

  private JvnHub(Path projectRoot) {
    this.projectRoot = projectRoot;
    // Load persisted read-state first so the badge counts only unread entries
    // on the very first paint.
    readIds.addAll(loadReadIds());
    announcements.addAll(loadAnnouncements());
    buildUi();
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

    buttons.add(makeAction("Update Engine", "git pull --rebase",
        VectorIcon.Kind.REFRESH, true, this::updateEngine));

    // Scroll log fills remaining space; wrap buttons in a container that doesn't stretch.
    JPanel center = new JPanel(new BorderLayout(0, 10));
    center.setBackground(BG);
    center.add(buttons, BorderLayout.NORTH);
    center.add(buildLogPanel(), BorderLayout.CENTER);
    return center;
  }

  private JPanel buildLogPanel() {
    logArea.setEditable(false);
    logArea.setFocusable(false);
    logArea.setLineWrap(true);
    logArea.setWrapStyleWord(true);
    logArea.setOpaque(true);
    logArea.setBackground(BG);
    logArea.setForeground(LOG_TEXT);
    logArea.setCaretColor(LOG_TEXT);
    logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    logArea.setMargin(new Insets(8, 8, 8, 8));
    logArea.setBorder(BorderFactory.createEmptyBorder());

    JScrollPane scroll = new JScrollPane(logArea);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setOpaque(true);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(new Dimension(0, 180));

    // Dark-themed, flat scrollbars matching the rest of the chrome.
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    scroll.getVerticalScrollBar().setUnitIncrement(16);

    JPanel wrap = new JPanel(new BorderLayout());
    wrap.setBackground(BG);
    wrap.add(scroll, BorderLayout.CENTER);
    return wrap;
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
    List<String> cmd = List.of("git", "pull", "--rebase");
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, "Update Engine");
  }

  private boolean acquire(String label) {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return false;
    }
    setButtonsEnabled(false);
    setStatus("Running: " + label, ACCENT_BLUE);
    return true;
  }

  private void release(String label, int exitCode) {
    runningProcess.set(null);
    setButtonsEnabled(true);
    Color tone = exitCode == 0 ? ACCENT_GREEN : ACCENT_ERROR;
    String prefix = exitCode == 0 ? "Done" : "Failed (exit " + exitCode + ")";
    setStatus(prefix + ": " + label, tone);
  }

  private void startProcess(List<String> command, String label) {
    new SwingWorker<Integer, String>() {
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
          while ((line = reader.readLine()) != null) publish(line);
        } catch (IOException e) {
          publish("[hub] stream error: " + e.getMessage());
        }
        return process.waitFor();
      }

      @Override protected void process(List<String> chunks) {
        for (String line : chunks) appendLog(line);
      }

      @Override protected void done() {
        int exit;
        try {
          exit = get();
        } catch (Exception e) {
          exit = -1;
          appendLog("[hub] task raised: " + e.getMessage());
        }
        release(label, exit);
        // Update Engine touched the working tree — re-read on-disk state so
        // the version label and announcements list reflect the new HEAD.
        if (exit == 0 && "Update Engine".equals(label)) {
          refreshFromDisk();
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
    p.descendants().forEach(ProcessHandle::destroy);
    p.destroy();
  }

  private void confirmAndExit() {
    Process p = runningProcess.get();
    if (p != null) {
      appendLog("[hub] cancelling running task before exit...");
      p.descendants().forEach(ProcessHandle::destroy);
      p.destroy();
    }
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
    SwingUtilities.invokeLater(() -> {
      logArea.append(line);
      logArea.append("\n");
      logArea.setCaretPosition(logArea.getDocument().getLength());
    });
  }

  private String gradleCommand() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String name = os.contains("win") ? "gradlew.bat" : "gradlew";
    Path wrapper = projectRoot.resolve(name);
    return wrapper.toAbsolutePath().toString();
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

  /**
   * Custom-painted button that stays fully black (no L&F chrome) and paints its own
   * 1px border with optional accent color. Text + icon render via {@code super.paintComponent}.
   */
  private static final class FlatButton extends JButton {
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
    enum Kind { PLAY, EDIT, ROCKET, HAMMER, CHECK, REFRESH, STOP, CLOSE, BELL }

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
      Stroke thick = new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
      g2.setStroke(thick);
      float s = size;

      switch (kind) {
        case PLAY -> {
          Path2D tri = new Path2D.Float();
          tri.moveTo(s * 0.25f, s * 0.15f);
          tri.lineTo(s * 0.85f, s * 0.50f);
          tri.lineTo(s * 0.25f, s * 0.85f);
          tri.closePath();
          g2.fill(tri);
        }
        case EDIT -> {
          // Pencil along the main diagonal.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.15f, s * 0.85f);
          body.lineTo(s * 0.55f, s * 0.45f);
          body.lineTo(s * 0.85f, s * 0.15f);
          body.lineTo(s * 0.95f, s * 0.25f);
          body.lineTo(s * 0.45f, s * 0.75f);
          body.closePath();
          g2.draw(body);
          // Tip fill.
          Path2D tip = new Path2D.Float();
          tip.moveTo(s * 0.10f, s * 0.90f);
          tip.lineTo(s * 0.25f, s * 0.75f);
          tip.lineTo(s * 0.35f, s * 0.85f);
          tip.closePath();
          g2.fill(tip);
        }
        case ROCKET -> {
          // Simple rocket silhouette.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.50f, s * 0.10f);
          body.curveTo(s * 0.80f, s * 0.30f, s * 0.80f, s * 0.55f, s * 0.65f, s * 0.80f);
          body.lineTo(s * 0.35f, s * 0.80f);
          body.curveTo(s * 0.20f, s * 0.55f, s * 0.20f, s * 0.30f, s * 0.50f, s * 0.10f);
          body.closePath();
          g2.draw(body);
          g2.draw(new Ellipse2D.Float(s * 0.42f, s * 0.35f, s * 0.16f, s * 0.16f));
          // Fins.
          Path2D finL = new Path2D.Float();
          finL.moveTo(s * 0.35f, s * 0.65f);
          finL.lineTo(s * 0.15f, s * 0.90f);
          finL.lineTo(s * 0.35f, s * 0.85f);
          finL.closePath();
          g2.fill(finL);
          Path2D finR = new Path2D.Float();
          finR.moveTo(s * 0.65f, s * 0.65f);
          finR.lineTo(s * 0.85f, s * 0.90f);
          finR.lineTo(s * 0.65f, s * 0.85f);
          finR.closePath();
          g2.fill(finR);
        }
        case HAMMER -> {
          // Head.
          Path2D head = new Path2D.Float();
          head.moveTo(s * 0.10f, s * 0.20f);
          head.lineTo(s * 0.65f, s * 0.20f);
          head.lineTo(s * 0.65f, s * 0.45f);
          head.lineTo(s * 0.10f, s * 0.45f);
          head.closePath();
          g2.fill(head);
          // Handle.
          g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine((int) (s * 0.55f), (int) (s * 0.45f), (int) (s * 0.90f), (int) (s * 0.90f));
        }
        case CHECK -> {
          Path2D check = new Path2D.Float();
          check.moveTo(s * 0.15f, s * 0.55f);
          check.lineTo(s * 0.40f, s * 0.80f);
          check.lineTo(s * 0.85f, s * 0.25f);
          g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(check);
        }
        case REFRESH -> {
          // Circular arrow.
          float r = s * 0.35f;
          float cx = s * 0.50f;
          float cy = s * 0.50f;
          Shape arc = new java.awt.geom.Arc2D.Float(
              cx - r, cy - r, r * 2, r * 2, 45f, 270f, java.awt.geom.Arc2D.OPEN);
          g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(arc);
          // Arrow head at the start of the arc (top-right quadrant).
          double theta = Math.toRadians(45);
          float ax = (float) (cx + r * Math.cos(theta));
          float ay = (float) (cy - r * Math.sin(theta));
          Path2D head = new Path2D.Float();
          head.moveTo(ax, ay);
          head.lineTo(ax - s * 0.14f, ay - s * 0.04f);
          head.lineTo(ax - s * 0.06f, ay + s * 0.14f);
          head.closePath();
          g2.fill(head);
        }
        case STOP -> {
          float pad = s * 0.2f;
          g2.fillRoundRect((int) pad, (int) pad,
              (int) (s - pad * 2), (int) (s - pad * 2), 2, 2);
        }
        case CLOSE -> {
          g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine((int) (s * 0.2f), (int) (s * 0.2f),
              (int) (s * 0.8f), (int) (s * 0.8f));
          g2.drawLine((int) (s * 0.8f), (int) (s * 0.2f),
              (int) (s * 0.2f), (int) (s * 0.8f));
        }
        case BELL -> {
          // Stem at top.
          g2.fillRect((int) (s * 0.46f), (int) (s * 0.08f),
              (int) (s * 0.10f), (int) (s * 0.08f));
          // Bell body: dome flaring out to a wide flange.
          Path2D body = new Path2D.Float();
          body.moveTo(s * 0.50f, s * 0.16f);
          body.curveTo(s * 0.82f, s * 0.20f, s * 0.82f, s * 0.55f, s * 0.85f, s * 0.72f);
          body.lineTo(s * 0.15f, s * 0.72f);
          body.curveTo(s * 0.18f, s * 0.55f, s * 0.18f, s * 0.20f, s * 0.50f, s * 0.16f);
          body.closePath();
          g2.fill(body);
          // Clapper.
          g2.fill(new Ellipse2D.Float(s * 0.42f, s * 0.74f, s * 0.16f, s * 0.16f));
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

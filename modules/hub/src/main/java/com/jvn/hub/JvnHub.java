package com.jvn.hub;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JWindow;
import javax.swing.JProgressBar;
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
 * <p>Visually mirrors the launcher/editor palette: dark charcoal neutral-gray
 * background, light text, compact header, and simple activity panel.</p>
 *
 * <p>Shells out to {@code ./gradlew} and {@code git}. The hub remains responsive while
 * a task runs; only one task at a time is allowed and the buttons disable for the
 * duration.</p>
 */
public final class JvnHub {

  private static final String PACKAGED_GRADLE_CACHE_MARKER_FILE = ".jvn-packaged-gradle-cache.properties";
  private static final String PUBLIC_DOCUMENTATION_URL = "https://javavectornexus.com";
  private static final String SOURCE_REPOSITORY_URL = "https://github.com/S1mplector/Java-Vector-Nexus";
  private static final String ENGINE_UPDATE_REMOTE = "origin";
  private static final String ENGINE_UPDATE_BRANCH = "stable";
  private static final String ENGINE_UPDATE_REMOTE_REF = ENGINE_UPDATE_REMOTE + "/" + ENGINE_UPDATE_BRANCH;
  private static final String ENGINE_UPDATE_FETCH_REFSPEC =
      "refs/heads/" + ENGINE_UPDATE_BRANCH + ":refs/remotes/" + ENGINE_UPDATE_REMOTE_REF;

  // --- Editor dark neutral-gray palette -------------------------------------
  // Mirrors the dark editor/launcher CSS: neutral graphite surfaces, no blue
  // cast, and very subtle gradients instead of flat pure black.
  private static final Color BG             = Color.decode("#101010");
  private static final Color BG_TOP         = Color.decode("#151515");
  private static final Color BG_BOTTOM      = Color.decode("#101010");
  private static final Color PANEL_BG       = Color.decode("#1c1c1c");
  private static final Color PANEL_BG_TOP   = Color.decode("#262626");
  private static final Color PANEL_BG_BOTTOM = Color.decode("#1c1c1c");
  private static final Color HOVER_BG       = Color.decode("#303030");
  private static final Color PRESSED_BG     = Color.decode("#181818");
  private static final Color BORDER_NEUTRAL = Color.decode("#3a3a3a");
  private static final Color TEXT_PRIMARY   = Color.decode("#f0f0f0");
  private static final Color TEXT_MUTED     = Color.decode("#9a9a9a");
  private static final Color TEXT_SOFT      = Color.decode("#c5c5c5");
  /** High-contrast neutral for emphasis (version tag, dates, running-task state). */
  private static final Color ACCENT_NEUTRAL = Color.decode("#c2c2c2");
  private static final Color ACCENT_GREEN   = Color.decode("#7ed39a");
  private static final Color ACCENT_DEV     = Color.decode("#8cc5ff");
  private static final Color ACCENT_SAFE    = Color.decode("#ffd166");
  private static final Color ACCENT_ERROR   = Color.decode("#f38ba8");
  private static final Color ACCENT_MAINTENANCE = Color.decode("#ff9933");
  private static final Color LOG_TEXT       = Color.decode("#cfcfcf");
  private static final Color SCROLL_THUMB   = Color.decode("#2a2a2a");
  private static final Color SCROLL_THUMB_HOVER = Color.decode("#3a3a3a");
  private static final String DEFAULT_LAUNCHER_MAINTENANCE_MESSAGE =
      "JVN Launcher is temporarily under maintenance. Use Run Editor for daily work.";
  private static final int PROCESS_OUTPUT_PREFIX_LIMIT = 8192;
  private static final int PROCESS_OUTPUT_TAIL_LINES = 40;

  /** Resolved at class-init time from a Gradle-generated resource. */
  private static final String VERSION = readVersion();
  private static final int BASE_HUB_WIDTH = 640;
  private static final int BASE_HUB_HEIGHT = 540;
  private static final double MIN_UI_SCALE = 0.75;
  private static final double MAX_UI_SCALE = 1.85;
  private static final HubDisplayProfile DISPLAY_PROFILE = HubDisplayProfile.detect();
  private static double activeUiScale = initialUiScale();

  private static int ui(int value) {
    if (value == 0) return 0;
    return Math.max(1, (int) Math.round(value * activeUiScale));
  }

  private static float uiFont(float value) {
    return (float) (value * activeUiScale);
  }

  private static float uiStroke(float value) {
    return (float) Math.max(1.0, value * activeUiScale);
  }

  private static Dimension uiDimension(int width, int height) {
    return new Dimension(ui(width), ui(height));
  }

  private static EmptyBorder uiPadding(int top, int left, int bottom, int right) {
    return new EmptyBorder(ui(top), ui(left), ui(bottom), ui(right));
  }

  private static VectorIcon uiIcon(VectorIcon.Kind kind, int size, Color color) {
    return VectorIcon.of(kind, ui(size), color);
  }

  private final Path projectRoot;
  private final JFrame frame = new JFrame("JVN Engine Hub");
  private final JLabel statusLabel = new JLabel("Idle");
  private final JLabel footerBranchLabel = new JLabel("No branch");
  private final JLabel footerRootLabel = new JLabel();
  private final JLabel footerModeLabel = new JLabel("Standard");
  private final JLabel versionLabel = new JLabel();
  private final ActivitySpinner activitySpinner = new ActivitySpinner();
  private final JLabel activityTitle = new JLabel("Ready");
  private final JLabel activityDetail = new JLabel("Choose an action to get started.");
  private final StepListPanel activitySteps = new StepListPanel();
  private final ActivityProgressPanel activityPanel = new ActivityProgressPanel(new BorderLayout(ui(10), ui(8)));
  private final javax.swing.Timer spinnerTimer = new javax.swing.Timer(70, e -> activitySpinner.tick());
  private final javax.swing.Timer autoStepTimer = new javax.swing.Timer(1800, e -> autoAdvanceDuringSilence());
  private final List<AbstractButton> actionButtons = new ArrayList<>();
  private final AtomicReference<Process> runningProcess = new AtomicReference<>();
  private final AtomicBoolean updateCheckRunning = new AtomicBoolean(false);
  private int lastKnownIncoming = -1;
  private int activeStepIndex = -1;
  private String activeStepLabel = "";

  /** Launcher maintenance state; refreshed on startup and after Update Engine. */
  private LauncherMaintenanceState launcherMaintenanceState = LauncherMaintenanceState.available();
  /** Developer Mode exposes engineering-focused actions and launch flags. */
  private boolean developerModeEnabled = false;
  private DeveloperModeToggleButton developerModeButton;
  /** Safe Mode launch toggle; applies to editor-side processes launched from the hub. */
  private boolean safeModeEnabled = false;
  private SafeModeToggleButton safeModeButton;
  private JPanel actionGrid;
  private JButton runEditorButton;
  private JButton runLauncherButton;
  private JButton buildAllButton;
  private JButton runTestsButton;
  private JButton gradleOptionsButton;
  private JButton buildShortcutsButton;
  private boolean gradleStacktraceEnabled = true;
  private boolean gradleInfoLoggingEnabled = false;
  private boolean gradleDebugLoggingEnabled = false;
  private boolean gradleOfflineEnabled = false;
  private boolean gradleRefreshDependenciesEnabled = false;
  private boolean gradleNoBuildCacheEnabled = false;
  private boolean gradleNoDaemonEnabled = false;
  private String gradleExtraArgs = "";
  /** Header shortcut for a lightweight local environment report. */
  private HeaderIconButton diagnosticsButton;
  /** Header shortcut for version, source, install, and update details. */
  private HeaderIconButton aboutButton;
  /** Header shortcut that opens the public documentation website. */
  private HeaderIconButton documentationButton;
  /** Update button with a right-aligned incoming-commit badge. */
  private UpdateEngineButton updateEngineButton;
  private HubShellPanel shellPanel;
  private boolean frameConfigured;
  private boolean shutdownInProgress;
  private ResizeOverlay resizeOverlay;
  private javax.swing.Timer resizeOverlayTimer;

  private JvnHub(Path projectRoot) {
    this.projectRoot = projectRoot;
    launcherMaintenanceState = loadLauncherMaintenanceState();
    buildUi();
    checkIncomingUpdates(true);
  }

  /** Entry point. Can be invoked directly or via the {@code :hub:run} Gradle task. */
  public static void main(String[] args) {
    Path root = resolveProjectRoot(args);
    // Keep the cross-platform (Metal) L&F — Aqua on macOS tints custom backgrounds
    // with system chrome we cannot override per-component. Cross-platform L&F honors
    // setBackground/setForeground verbatim, which is what the custom theme needs.
    applyHubDefaults();
    SwingUtilities.invokeLater(() -> {
      HubLifecycleSplash splash = HubLifecycleSplash.startup();
      splash.showCentered();
      javax.swing.Timer launchDelay = new javax.swing.Timer(120, event -> {
        ((javax.swing.Timer) event.getSource()).stop();
        JvnHub hub = new JvnHub(root);
        hub.frame.setVisible(true);
        splash.closeAfter(320, null);
      });
      launchDelay.setRepeats(false);
      launchDelay.start();
    });
  }

  /** Seed UIManager so ancillary components (tooltips, split panes, dialogs) match. */
  private static void applyHubDefaults() {
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
    // macOS's native delegates paint a light menu strip regardless of component
    // background values. Basic delegates respect the Hub palette on every host.
    UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
    UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
    UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
    UIManager.put("CheckBoxMenuItemUI", "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
    UIManager.put("RadioButtonMenuItemUI", "javax.swing.plaf.basic.BasicRadioButtonMenuItemUI");
    UIManager.put("PopupMenuUI", "javax.swing.plaf.basic.BasicPopupMenuUI");
    UIManager.put("PopupMenuSeparatorUI", "javax.swing.plaf.basic.BasicPopupMenuSeparatorUI");
    UIManager.put("MenuBar.background", BG_TOP);
    UIManager.put("MenuBar.foreground", TEXT_PRIMARY);
    UIManager.put("Menu.background", BG_TOP);
    UIManager.put("Menu.foreground", TEXT_PRIMARY);
    UIManager.put("Menu.selectionBackground", HOVER_BG);
    UIManager.put("Menu.selectionForeground", TEXT_PRIMARY);
    UIManager.put("MenuItem.background", PANEL_BG);
    UIManager.put("MenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("MenuItem.selectionBackground", HOVER_BG);
    UIManager.put("MenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("CheckBoxMenuItem.background", PANEL_BG);
    UIManager.put("CheckBoxMenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("CheckBoxMenuItem.selectionBackground", HOVER_BG);
    UIManager.put("CheckBoxMenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("RadioButtonMenuItem.background", PANEL_BG);
    UIManager.put("RadioButtonMenuItem.foreground", TEXT_PRIMARY);
    UIManager.put("RadioButtonMenuItem.selectionBackground", HOVER_BG);
    UIManager.put("RadioButtonMenuItem.selectionForeground", TEXT_PRIMARY);
    UIManager.put("PopupMenu.background", PANEL_BG);
    UIManager.put("PopupMenu.foreground", TEXT_PRIMARY);
    UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER_NEUTRAL));
    UIManager.put("Separator.background", PANEL_BG);
    UIManager.put("Separator.foreground", BORDER_NEUTRAL);
    UIManager.put("PopupMenuSeparator.background", PANEL_BG);
    UIManager.put("PopupMenuSeparator.foreground", BORDER_NEUTRAL);
    UIManager.put("ScrollPane.background", BG);
    UIManager.put("TextArea.background", BG);
    UIManager.put("TextArea.foreground", LOG_TEXT);
    UIManager.put("TextArea.caretForeground", LOG_TEXT);
    UIManager.put("ScrollBar.background", BG);
    UIManager.put("ScrollBar.track", BG);
    UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
    scaleDefaultUiFonts();
  }

  private static void scaleDefaultUiFonts() {
    if (Math.abs(activeUiScale - 1.0) < 0.01) return;
    for (String key : List.of(
        "Button.font",
        "CheckBox.font",
        "Label.font",
        "Menu.font",
        "MenuItem.font",
        "OptionPane.buttonFont",
        "OptionPane.messageFont",
        "PopupMenu.font",
        "TextArea.font",
        "TextField.font",
        "ToggleButton.font",
        "ToolTip.font")) {
      Object value = UIManager.get(key);
      if (value instanceof Font font) {
        UIManager.put(key, font.deriveFont(uiFont(font.getSize2D())));
      }
    }
  }

  private record HubDisplayProfile(
      double uiScale,
      int screenWidth,
      int screenHeight,
      int dpi,
      double deviceScale,
      boolean override) {

    static HubDisplayProfile detect() {
      double overrideScale = parseScaleOverride(System.getProperty("jvn.hub.uiScale"));
      if (!Double.isFinite(overrideScale)) overrideScale = parseScaleOverride(System.getenv("JVN_HUB_UI_SCALE"));
      if (Double.isFinite(overrideScale)) {
        return new HubDisplayProfile(clampScale(overrideScale), 0, 0, 0, 1.0, true);
      }

      int screenWidth = 0;
      int screenHeight = 0;
      int dpi = 96;
      double deviceScale = 1.0;
      try {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = environment.getDefaultScreenDevice();
        GraphicsConfiguration configuration = device.getDefaultConfiguration();
        Rectangle bounds = configuration.getBounds();
        screenWidth = Math.max(0, bounds.width);
        screenHeight = Math.max(0, bounds.height);
        AffineTransform transform = configuration.getDefaultTransform();
        deviceScale = Math.max(Math.abs(transform.getScaleX()), Math.abs(transform.getScaleY()));
      } catch (Exception ignored) {
        // Headless or unusual desktop environments fall back to a normal 1x hub.
      }
      try {
        dpi = Toolkit.getDefaultToolkit().getScreenResolution();
      } catch (Exception ignored) {
        // Some containerized desktops do not expose DPI to AWT.
      }

      double scale = automaticScaleForDisplay(screenWidth, screenHeight, dpi);
      return new HubDisplayProfile(scale, screenWidth, screenHeight, dpi, deviceScale, false);
    }

    private static double parseScaleOverride(String raw) {
      if (raw == null || raw.isBlank()) return Double.NaN;
      try {
        double parsed = Double.parseDouble(raw.trim());
        return Double.isFinite(parsed) && parsed > 0.0 ? parsed : Double.NaN;
      } catch (NumberFormatException ignored) {
        return Double.NaN;
      }
    }

    private static double clampScale(double value) {
      return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, value));
    }
  }

  static double automaticScaleForDisplay(int screenWidth, int screenHeight, int dpi) {
      int longSide = Math.max(screenWidth, screenHeight);
      int shortSide = screenWidth > 0 && screenHeight > 0 ? Math.min(screenWidth, screenHeight) : 0;
      double resolutionScale = 1.0;
      if (longSide >= 3800 || shortSide >= 2100) {
        resolutionScale = 1.45;
      } else if (longSide >= 3200 || shortSide >= 1800) {
        resolutionScale = 1.32;
      } else if (longSide >= 2800 || shortSide >= 1600) {
        resolutionScale = 1.20;
      }

      double dpiScale = 1.0;
      if (dpi >= 216) {
        dpiScale = 1.50;
      } else if (dpi >= 168) {
        dpiScale = 1.35;
      } else if (dpi >= 132) {
        dpiScale = 1.20;
      }

      // Swing already renders in logical coordinates on HiDPI displays. Applying the
      // graphics transform again makes Retina windows roughly 1.5x too large.
      double scale = Math.max(resolutionScale, dpiScale);
      if (screenWidth > 0 && screenHeight > 0) {
        double widthCap = (screenWidth * 0.82) / BASE_HUB_WIDTH;
        double heightCap = (screenHeight * 0.82) / BASE_HUB_HEIGHT;
        double screenCap = Math.max(1.0, Math.min(widthCap, heightCap));
        scale = Math.min(scale, screenCap);
      }
      return HubDisplayProfile.clampScale(scale);
  }

  static Dimension hubSizeForScale(double scale) {
    double bounded = HubDisplayProfile.clampScale(scale);
    return new Dimension(
        (int) Math.round(BASE_HUB_WIDTH * bounded),
        (int) Math.round(BASE_HUB_HEIGHT * bounded));
  }

  private static String displayScaleSummary() {
    return String.format(Locale.ROOT, "Hub UI scale %.2fx", activeUiScale);
  }

  private static String displayScaleDetails() {
    if (DISPLAY_PROFILE.override()) {
      return "Manual override from jvn.hub.uiScale or JVN_HUB_UI_SCALE.";
    }
    String screen = DISPLAY_PROFILE.screenWidth() > 0 && DISPLAY_PROFILE.screenHeight() > 0
        ? DISPLAY_PROFILE.screenWidth() + "x" + DISPLAY_PROFILE.screenHeight()
        : "unknown";
    String dpi = DISPLAY_PROFILE.dpi() > 0 ? Integer.toString(DISPLAY_PROFILE.dpi()) : "unknown";
    return "screen=" + screen
        + "; dpi=" + dpi
        + "; deviceScale=" + String.format(Locale.ROOT, "%.2f", DISPLAY_PROFILE.deviceScale());
  }

  private static double initialUiScale() {
    if (DISPLAY_PROFILE.override()) return DISPLAY_PROFILE.uiScale();
    Path file = uiStateFile();
    if (!Files.isRegularFile(file)) return DISPLAY_PROFILE.uiScale();
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      String value = properties.getProperty("ui.scale", "auto").trim();
      if (value.equalsIgnoreCase("auto")) return DISPLAY_PROFILE.uiScale();
      return HubDisplayProfile.clampScale(Double.parseDouble(value));
    } catch (IOException | NumberFormatException ignored) {
      return DISPLAY_PROFILE.uiScale();
    }
  }

  private static Path uiStateFile() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "hub-ui.properties");
  }

  private static boolean automaticUiScaleSelected() {
    if (DISPLAY_PROFILE.override()) return false;
    Path file = uiStateFile();
    if (!Files.isRegularFile(file)) return true;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
      return properties.getProperty("ui.scale", "auto").trim().equalsIgnoreCase("auto");
    } catch (IOException ignored) {
      return true;
    }
  }

  private void saveUiScale(String value) {
    Path file = uiStateFile();
    Properties properties = new Properties();
    properties.setProperty("ui.scale", value);
    try {
      Files.createDirectories(file.getParent());
      try (var output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        properties.store(output, "JVN Engine Hub UI preferences. Auto-generated.");
      }
    } catch (IOException e) {
      appendLog("[hub] could not save UI scale: " + e.getMessage());
    }
  }

  // --- UI assembly -----------------------------------------------------------

  private void buildUi() {
    if (!frameConfigured) {
      frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
        @Override public void windowClosing(WindowEvent e) { confirmAndExit(); }
      });
      frameConfigured = true;
    }

    JPanel root = new GradientPanel(new BorderLayout(0, ui(12)), BG_TOP, BG_BOTTOM);
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));

    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildCenter(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    frame.setContentPane(root);
    frame.setJMenuBar(buildMenuBar());
    // Keep frame/root chrome aligned with the custom charcoal-neutral surface.
    frame.setBackground(BG);
    frame.getRootPane().setBackground(BG);
    frame.getRootPane().setOpaque(true);
    frame.getContentPane().setBackground(BG);
    installResizeOverlay();

    Dimension hubSize = uiDimension(BASE_HUB_WIDTH, BASE_HUB_HEIGHT);
    frame.setResizable(true);
    frame.setMinimumSize(hubSize);
    frame.setPreferredSize(hubSize);
    frame.pack();
    frame.setSize(hubSize);
    frame.setLocationRelativeTo(null);

    installApplicationIcon(frame);
  }

  private void installResizeOverlay() {
    resizeOverlay = new ResizeOverlay(frame);
    resizeOverlayTimer = new javax.swing.Timer(520, event -> resizeOverlay.setVisible(false));
    resizeOverlayTimer.setRepeats(false);
    frame.setGlassPane(resizeOverlay);
    frame.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        if (!frame.isShowing() || shutdownInProgress) return;
        resizeOverlay.updateDimensions();
        resizeOverlay.setVisible(true);
        resizeOverlayTimer.restart();
      }
    });
  }

  static String formatWindowPixels(int width, int height) {
    return Math.max(0, width) + " × " + Math.max(0, height) + " px";
  }

  private JMenuBar buildMenuBar() {
    JMenuBar bar = new JMenuBar() {
      @Override
      protected void paintComponent(Graphics graphics) {
        graphics.setColor(BG_TOP);
        graphics.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    bar.setOpaque(true);
    bar.setBackground(BG_TOP);
    bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_NEUTRAL));

    JMenu file = hubMenu("File");
    file.add(hubMenuItem("Show Engine Folder", this::revealEngineRoot));
    file.add(hubMenuItem("Show Hub Data Folder", () -> revealHubFolder("data", Paths.get(System.getProperty("user.home", "."), ".jvn"))));
    file.add(hubMenuItem("Show Hub Logs", () -> revealHubFolder("logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    file.add(hubMenuItem("Copy Engine Path", this::copyEngineRootPath));
    file.addSeparator();
    file.add(hubMenuItem("Quit Engine Hub", this::confirmAndExit));

    JMenu engine = hubMenu("Engine");
    engine.add(hubMenuItem("Run Editor", () -> clickIfAvailable(runEditorButton)));
    engine.addSeparator();
    engine.add(hubMenuItem("Refresh Hub State", this::refreshFromDisk));

    JMenu build = hubMenu("Build");
    build.add(hubMenuItem("Compile All Modules", () -> guardedRun(
        "Compile All Modules", () -> runGradle("compileAll", "Compile All Modules"))));
    build.add(hubMenuItem("Build All Modules", () -> clickIfAvailable(buildAllButton)));
    build.add(hubMenuItem("Quick Verification", () -> guardedRun(
        "Quick Verification", () -> runGradle("quickCheck", "Quick Verification"))));
    build.add(hubMenuItem("Run Test Suite", () -> clickIfAvailable(runTestsButton)));
    build.addSeparator();
    build.add(hubMenuItem("Gradle Options", this::showGradleOptionsDialog));
    build.add(hubMenuItem("Install Platform Shortcuts", () -> clickIfAvailable(buildShortcutsButton)));

    JMenu sourceControl = hubMenu("Source Control");
    sourceControl.setForeground(ACCENT_MAINTENANCE);
    sourceControl.add(hubMenuItem("Check for Engine Updates", () -> checkIncomingUpdates(true)));
    sourceControl.add(hubMenuItem("Update from Stable", () -> clickIfAvailable(updateEngineButton)));
    sourceControl.addSeparator();
    sourceControl.add(hubMenuItem("Open GitHub Repository", this::openSourceRepository));
    sourceControl.add(hubMenuItem("Copy Current Branch", this::copyCurrentBranch));

    JMenu tools = hubMenu("Tools");
    tools.add(hubMenuItem("Run Diagnostics", this::showDiagnosticsReport));
    tools.add(hubMenuItem("Show Hub Data Folder", () -> revealHubFolder(
        "data", Paths.get(System.getProperty("user.home", "."), ".jvn"))));
    tools.add(hubMenuItem("Show Hub Logs", () -> revealHubFolder(
        "logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    tools.addSeparator();
    tools.add(hubMenuItem("Cancel Running Task", this::cancelRunning));

    JMenu view = hubMenu("View");
    view.add(buildUiScaleMenu());
    view.addSeparator();
    JCheckBoxMenuItem safeMode = hubCheckMenuItem("Safe Mode", safeModeEnabled);
    safeMode.addActionListener(e -> setSafeModeEnabled(safeMode.isSelected()));
    view.add(safeMode);
    JCheckBoxMenuItem developerMode = hubCheckMenuItem("Developer Mode", developerModeEnabled);
    developerMode.addActionListener(e -> setDeveloperModeEnabled(developerMode.isSelected()));
    view.add(developerMode);

    JMenu help = hubMenu("Help");
    help.add(hubMenuItem("Documentation", this::openDocumentationWebsite));
    help.addSeparator();
    help.add(hubMenuItem("About Engine Hub", this::showAboutReport));

    bar.add(file);
    bar.add(engine);
    bar.add(build);
    bar.add(sourceControl);
    bar.add(tools);
    if (developerModeEnabled) bar.add(buildDeveloperModeMenu());
    if (safeModeEnabled) bar.add(buildSafeModeMenu());
    bar.add(view);
    bar.add(help);
    return bar;
  }

  private JMenu buildDeveloperModeMenu() {
    JMenu developer = hubMenu("Developer");
    developer.setForeground(ACCENT_DEV);
    developer.add(hubMenuItem("Compile All Modules", () -> guardedRun(
        "Compile All Modules", () -> runGradle("compileAll", "Compile All Modules"))));
    developer.add(hubMenuItem("Quick Verification", () -> guardedRun(
        "Quick Verification", () -> runGradle("quickCheck", "Quick Verification"))));
    developer.add(hubMenuItem("Run Full Test Suite", () -> clickIfAvailable(runTestsButton)));
    developer.addSeparator();
    developer.add(hubMenuItem("Configure Gradle", this::showGradleOptionsDialog));
    developer.add(hubMenuItem("Inspect Engine Diagnostics", this::showDiagnosticsReport));
    return developer;
  }

  private JMenu buildSafeModeMenu() {
    JMenu safe = hubMenu("Safe Mode");
    safe.setForeground(ACCENT_SAFE);
    safe.add(hubMenuItem("Run Editor with Guardrails", () -> clickIfAvailable(runEditorButton)));
    safe.add(hubMenuItem("Update Stable with Recovery", () -> clickIfAvailable(updateEngineButton)));
    safe.addSeparator();
    safe.add(hubMenuItem("Recheck Workspace Health", this::showDiagnosticsReport));
    safe.add(hubMenuItem("Open Recovery Logs", () -> revealHubFolder(
        "logs", Paths.get(System.getProperty("user.home", "."), ".jvn", "logs"))));
    safe.add(hubMenuItem("Cancel Running Task", this::cancelRunning));
    return safe;
  }

  private JMenu buildUiScaleMenu() {
    JMenu scale = hubMenu("UI Scale");
    scale.setBackground(PANEL_BG);
    ButtonGroup choices = new ButtonGroup();
    boolean automatic = automaticUiScaleSelected();
    addScaleChoice(scale, choices, "Auto (Fit Display)", Double.NaN, automatic);
    addScaleChoice(scale, choices, "Compact (75%)", 0.75, !automatic && nearScale(0.75));
    addScaleChoice(scale, choices, "Small (85%)", 0.85, !automatic && nearScale(0.85));
    addScaleChoice(scale, choices, "Default (100%)", 1.0, !automatic && nearScale(1.0));
    addScaleChoice(scale, choices, "Large (125%)", 1.25, !automatic && nearScale(1.25));
    return scale;
  }

  private void addScaleChoice(JMenu menu, ButtonGroup choices, String label, double value, boolean selected) {
    JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, selected);
    styleMenuItem(item);
    item.addActionListener(e -> applyUiScale(value));
    choices.add(item);
    menu.add(item);
  }

  private boolean nearScale(double value) {
    return Math.abs(activeUiScale - value) < 0.01;
  }

  private void applyUiScale(double requestedScale) {
    if (runningProcess.get() != null) {
      appendLog("[hub] UI scale cannot change while an action is running.");
      return;
    }
    boolean automatic = !Double.isFinite(requestedScale);
    double newScale = automatic ? DISPLAY_PROFILE.uiScale() : HubDisplayProfile.clampScale(requestedScale);
    saveUiScale(automatic ? "auto" : String.format(Locale.ROOT, "%.2f", newScale));
    if (Math.abs(activeUiScale - newScale) < 0.01) {
      frame.setJMenuBar(buildMenuBar());
      return;
    }

    activeUiScale = newScale;
    actionButtons.clear();
    activityPanel.removeAll();
    buildUi();
    setDeveloperModeEnabled(developerModeEnabled);
    setSafeModeEnabled(safeModeEnabled);
    frame.revalidate();
    frame.repaint();
    appendLog("[hub] UI scale changed to " + String.format(Locale.ROOT, "%.0f%%", newScale * 100.0) + ".");
  }

  private static JMenu hubMenu(String text) {
    JMenu menu = new JMenu(text);
    menu.setOpaque(true);
    menu.setBackground(BG_TOP);
    menu.setForeground(TEXT_PRIMARY);
    menu.setFont(menu.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
    menu.getPopupMenu().setOpaque(true);
    menu.getPopupMenu().setBackground(PANEL_BG);
    menu.getPopupMenu().setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    return menu;
  }

  private static JMenuItem hubMenuItem(String text, Runnable action) {
    JMenuItem item = new JMenuItem(text);
    styleMenuItem(item);
    item.addActionListener(e -> action.run());
    return item;
  }

  private static JCheckBoxMenuItem hubCheckMenuItem(String text, boolean selected) {
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, selected);
    styleMenuItem(item);
    return item;
  }

  private static void styleMenuItem(JMenuItem item) {
    item.setOpaque(true);
    item.setBackground(PANEL_BG);
    item.setForeground(TEXT_PRIMARY);
    item.setFont(item.getFont().deriveFont(Font.PLAIN, uiFont(12f)));
  }

  private static void installApplicationIcon(JFrame frame) {
    if (frame == null) return;
    BufferedImage icon512 = JvnLogoIcon.renderToImage(512, 512);
    frame.setIconImages(List.of(
        JvnLogoIcon.renderToImage(16, 16),
        JvnLogoIcon.renderToImage(32, 32),
        JvnLogoIcon.renderToImage(64, 64),
        JvnLogoIcon.renderToImage(128, 128),
        icon512));
    try {
      if (java.awt.Taskbar.isTaskbarSupported()) {
        java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
        if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
          taskbar.setIconImage(icon512);
        }
      }
    } catch (UnsupportedOperationException | SecurityException ignored) {
      // Some desktop environments expose the Taskbar API but reject icon changes.
    }
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setOpaque(false);

    // --- Left: vector logo + text stack -------------------------------------
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(12), 0));
    left.setOpaque(false);

    JLabel logoLabel = new JLabel(new JvnLogoIcon(ui(124), ui(66)));
    left.add(logoLabel);

    JLabel title = new JLabel("Engine Hub");
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(16f)));

    versionLabel.setText(formatVersionLabel(readDiskVersion()));
    versionLabel.setForeground(ACCENT_NEUTRAL);
    versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, uiFont(10f)));

    JPanel titleBox = new JPanel();
    titleBox.setOpaque(false);
    titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleBox.add(title);
    titleBox.add(Box.createVerticalStrut(ui(2)));
    titleBox.add(versionLabel);

    left.add(titleBox);
    header.add(left, BorderLayout.WEST);

    // --- Right: mode toggles and utility shortcuts ---------------------------
    developerModeButton = new DeveloperModeToggleButton();
    developerModeButton.addActionListener(e -> setDeveloperModeEnabled(developerModeButton.isSelected()));
    actionButtons.add(developerModeButton);

    safeModeButton = new SafeModeToggleButton();
    safeModeButton.addActionListener(e -> setSafeModeEnabled(safeModeButton.isSelected()));
    actionButtons.add(safeModeButton);

    diagnosticsButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.DIAGNOSTICS, 24),
        "Diagnostics — run a lightweight health check");
    diagnosticsButton.addActionListener(e -> showDiagnosticsReport());
    actionButtons.add(diagnosticsButton);

    aboutButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.ABOUT, 24),
        "About — version and install details");
    aboutButton.addActionListener(e -> showAboutReport());
    actionButtons.add(aboutButton);

    documentationButton = new HeaderIconButton(
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.DOCUMENTATION, 24),
        "Documentation — open the documentation website");
    documentationButton.addActionListener(e -> openDocumentationWebsite());
    actionButtons.add(documentationButton);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(6), 0));
    right.setOpaque(false);
    right.add(developerModeButton);
    right.add(safeModeButton);
    right.add(diagnosticsButton);
    right.add(aboutButton);
    right.add(documentationButton);
    header.add(right, BorderLayout.EAST);

    return header;
  }

  private LauncherMaintenanceState loadLauncherMaintenanceState() {
    Path file = projectRoot.resolve(".jvn/maintenance.properties");
    if (!Files.isRegularFile(file)) return LauncherMaintenanceState.available();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
    } catch (IOException e) {
      appendLog("[hub] failed to read maintenance state: " + e.getMessage());
      return LauncherMaintenanceState.available();
    }
    boolean launcherUnderMaintenance = parseBooleanProperty(
        props,
        "launcher.maintenance",
        "launcher.underMaintenance",
        "launcher.disabled");
    String message = firstNonBlank(
        props.getProperty("launcher.message"),
        props.getProperty("launcher.maintenanceMessage"),
        DEFAULT_LAUNCHER_MAINTENANCE_MESSAGE);
    return new LauncherMaintenanceState(launcherUnderMaintenance, message);
  }

  private static boolean parseBooleanProperty(Properties props, String... keys) {
    if (props == null || keys == null) return false;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String value = props.getProperty(key);
      if (value == null || value.isBlank()) continue;
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("true")
          || normalized.equals("1")
          || normalized.equals("yes")
          || normalized.equals("on");
    }
    return false;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private record LauncherMaintenanceState(boolean underMaintenance, String message) {
    static LauncherMaintenanceState available() {
      return new LauncherMaintenanceState(false, "");
    }

    String resolvedMessage() {
      return message == null || message.isBlank() ? DEFAULT_LAUNCHER_MAINTENANCE_MESSAGE : message;
    }
  }

  private JPanel buildCenter() {
    actionGrid = new JPanel(new GridLayout(3, 2, ui(10), ui(10)));
    actionGrid.setOpaque(false);

    runEditorButton = makeAction("Run Editor", "Launch the full JVN editor.",
        VectorIcon.Kind.EDIT, null, () -> guardedRun("Run Editor", () -> runFastApp("editor", "Run Editor")));
    runEditorButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.EDITOR));

    // Launcher access is intentionally withheld while that workflow is under maintenance.
    runLauncherButton = makeLauncherAction();

    buildAllButton = makeAction("Build All", "Compile every module.",
        VectorIcon.Kind.HAMMER, null, () -> guardedRun("Build All", () -> runGradle("build", "Build All")));
    buildAllButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.BUILD));

    runTestsButton = makeAction("Run Tests", "Developer Mode: execute the full test suite.",
        VectorIcon.Kind.CHECK, ACCENT_DEV, () -> runGradle("test", "Run Tests"));
    runTestsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.TESTS));

    gradleOptionsButton = makeAction("Gradle Options", "Developer Mode: configure Gradle flags for hub actions.",
        VectorIcon.Kind.SLIDERS, ACCENT_DEV, this::showGradleOptionsDialog);
    gradleOptionsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.OPTIONS));

    buildShortcutsButton = makeAction("Build Shortcuts", "Install Start Menu / Applications shortcuts for this OS.",
        VectorIcon.Kind.SHORTCUT, null, () -> guardedRun("Build Shortcuts", this::installShortcuts));
    buildShortcutsButton.setIcon(WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.SHORTCUT));

    updateEngineButton = new UpdateEngineButton("Update Engine",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.UPDATE));
    updateEngineButton.setToolTipText("git pull --rebase " + ENGINE_UPDATE_REMOTE + " " + ENGINE_UPDATE_BRANCH);
    updateEngineButton.addActionListener(e -> updateEngine());
    actionButtons.add(updateEngineButton);
    rebuildActionGrid();

    shellPanel = new HubShellPanel();

    JPanel workspace = new JPanel(new BorderLayout(0, ui(10)));
    workspace.setOpaque(false);
    workspace.add(new HubPerformancePanel(), BorderLayout.NORTH);
    workspace.add(shellPanel, BorderLayout.CENTER);

    JPanel center = new JPanel(new BorderLayout(0, ui(8)));
    center.setOpaque(false);
    center.add(actionGrid, BorderLayout.NORTH);
    center.add(buildActivityPanel(), BorderLayout.SOUTH);
    center.add(workspace, BorderLayout.CENTER);
    return center;
  }

  private void rebuildActionGrid() {
    if (actionGrid == null) return;
    actionGrid.removeAll();
    actionGrid.setLayout(new GridLayout(developerModeEnabled ? 3 : 2, 2, ui(10), ui(10)));
    actionGrid.add(runEditorButton);
    actionGrid.add(buildAllButton);
    if (developerModeEnabled) {
      actionGrid.add(runTestsButton);
      actionGrid.add(gradleOptionsButton);
    }
    actionGrid.add(buildShortcutsButton);
    actionGrid.add(updateEngineButton);
    actionGrid.revalidate();
    actionGrid.repaint();
  }

  private JPanel buildActivityPanel() {
    activityTitle.setForeground(TEXT_PRIMARY);
    activityTitle.setFont(activityTitle.getFont().deriveFont(Font.BOLD, uiFont(12f)));
    activityDetail.setForeground(TEXT_MUTED);
    activityDetail.setFont(activityDetail.getFont().deriveFont(Font.PLAIN, uiFont(10f)));

    JPanel text = new JPanel(new BorderLayout(ui(10), 0));
    text.setOpaque(false);
    text.add(activityTitle, BorderLayout.WEST);
    text.add(activityDetail, BorderLayout.CENTER);

    JPanel header = new JPanel(new BorderLayout(ui(10), 0));
    header.setOpaque(false);
    header.add(activitySpinner, BorderLayout.WEST);
    header.add(text, BorderLayout.CENTER);

    activitySteps.setSteps(List.of(
        "Choose an action",
        "The hub will show every background stage here."));

    activityPanel.setProgress(0.0);
    activityPanel.setTone(ACCENT_NEUTRAL, false);
    activityPanel.setBackground(PANEL_BG);
    activityPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(8, 10, 8, 12)));
    activityPanel.add(header, BorderLayout.NORTH);
    activityPanel.add(activitySteps, BorderLayout.CENTER);
    activityPanel.setPreferredSize(new Dimension(0, ui(118)));
    activityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ui(118)));
    return activityPanel;
  }

  private static void styleScrollBar(JScrollBar bar) {
    if (bar == null) return;
    bar.setUI(new NeutralScrollBarUI());
    bar.setOpaque(true);
    bar.setBackground(BG);
    bar.setBorder(BorderFactory.createEmptyBorder());
    bar.setPreferredSize(uiDimension(10, 10));
    bar.setUnitIncrement(ui(16));
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new GridBagLayout());
    footer.setBackground(Color.decode("#191919"));
    footer.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Color.decode("#303030")),
        uiPadding(3, 8, 3, 8)));

    statusLabel.setForeground(TEXT_SOFT);
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerBranchLabel.setText(resolveBranch(projectRoot));
    footerBranchLabel.setForeground(TEXT_SOFT);
    footerBranchLabel.setFont(footerBranchLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerRootLabel.setText(projectRoot.getFileName() == null ? projectRoot.toString() : projectRoot.getFileName().toString());
    footerRootLabel.setToolTipText(projectRoot.toString());
    footerRootLabel.setForeground(TEXT_SOFT);
    footerRootLabel.setFont(footerRootLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    footerModeLabel.setForeground(TEXT_SOFT);
    footerModeLabel.setFont(footerModeLabel.getFont().deriveFont(Font.BOLD, uiFont(11f)));

    JLabel javaLabel = footerLabel("Java " + javaFeatureVersion(), TEXT_SOFT);
    JLabel version = footerLabel(VERSION, TEXT_SOFT);

    FlatButton cancel = new FlatButton("Cancel",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.CANCEL, 18), ACCENT_ERROR);
    cancel.addActionListener(e -> cancelRunning());

    FlatButton quit = new FlatButton("Quit",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.QUIT, 18), null);
    quit.addActionListener(e -> confirmAndExit());

    FlatButton more = new FlatButton("More",
        WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.MORE, 18), null);
    more.addActionListener(e -> showFooterMenu(more));

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(6), 0));
    right.setOpaque(false);
    right.add(more);
    right.add(cancel);
    right.add(quit);
    right.setMinimumSize(right.getPreferredSize());

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(8), 0));
    left.setOpaque(false);
    left.add(footerItem("Hub", statusLabel));
    left.add(footerDivider());
    left.add(footerItem("Branch", footerBranchLabel));
    left.add(footerDivider());
    left.add(footerItem("Root", footerRootLabel));
    left.add(footerDivider());
    left.add(footerItem("Mode", footerModeLabel));
    left.add(footerDivider());
    left.add(footerItem("Java", javaLabel));
    left.add(footerDivider());
    left.add(footerItem("Version", version));
    left.setMinimumSize(new Dimension(0, left.getPreferredSize().height));

    GridBagConstraints leftConstraints = new GridBagConstraints();
    leftConstraints.gridx = 0;
    leftConstraints.gridy = 0;
    leftConstraints.weightx = 1.0;
    leftConstraints.fill = GridBagConstraints.HORIZONTAL;
    leftConstraints.anchor = GridBagConstraints.WEST;
    footer.add(left, leftConstraints);

    GridBagConstraints rightConstraints = new GridBagConstraints();
    rightConstraints.gridx = 1;
    rightConstraints.gridy = 0;
    rightConstraints.weightx = 0.0;
    rightConstraints.fill = GridBagConstraints.NONE;
    rightConstraints.anchor = GridBagConstraints.EAST;
    footer.add(right, rightConstraints);
    installFooterPopup(footer);
    installFooterPopup(left);
    installFooterPopup(right);
    return footer;
  }

  private void showFooterMenu(Component invoker) {
    JPopupMenu menu = buildFooterMenu();
    menu.show(invoker, 0, -menu.getPreferredSize().height);
  }

  private void installFooterPopup(JComponent component) {
    component.setComponentPopupMenu(buildFooterMenu());
  }

  private JPopupMenu buildFooterMenu() {
    JPopupMenu menu = new JPopupMenu();
    menu.setBackground(PANEL_BG);
    menu.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    menu.add(popupItem("Run Editor", () -> clickIfAvailable(runEditorButton)));
    menu.add(popupItem("Build All", () -> clickIfAvailable(buildAllButton)));
    menu.addSeparator();
    menu.add(popupItem("Update Engine", this::updateEngine));
    menu.add(popupItem("Diagnostics", this::showDiagnosticsReport));
    menu.add(popupItem("Documentation Website", this::openDocumentationWebsite));
    menu.addSeparator();
    menu.add(popupItem("Reveal Engine Root", this::revealEngineRoot));
    menu.add(popupItem("Copy Engine Root Path", this::copyEngineRootPath));
    menu.addSeparator();
    menu.add(popupItem("Cancel Running Task", this::cancelRunning));
    menu.add(popupItem("Quit Hub", this::confirmAndExit));
    return menu;
  }

  private JMenuItem popupItem(String label, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.setBackground(PANEL_BG);
    item.setForeground(TEXT_SOFT);
    item.setFont(item.getFont().deriveFont(Font.BOLD, uiFont(12f)));
    item.setEnabled(action != null);
    if (action != null) item.addActionListener(e -> action.run());
    return item;
  }

  private void clickIfAvailable(AbstractButton button) {
    if (button == null || !button.isEnabled()) return;
    button.doClick();
  }

  private void revealEngineRoot() {
    try {
      java.awt.Desktop.getDesktop().open(projectRoot.toFile());
      setStatus("Opened engine root", ACCENT_NEUTRAL);
    } catch (Exception e) {
      setStatus("Could not open engine root", ACCENT_ERROR);
      setActivity("Reveal failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false, ACCENT_ERROR);
    }
  }

  private void revealHubFolder(String label, Path folder) {
    try {
      Files.createDirectories(folder);
      java.awt.Desktop.getDesktop().open(folder.toFile());
      setStatus("Opened Hub " + label, ACCENT_NEUTRAL);
    } catch (Exception e) {
      setStatus("Could not open Hub " + label, ACCENT_ERROR);
      setActivity("Open folder failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), false, ACCENT_ERROR);
    }
  }

  private void copyEngineRootPath() {
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(projectRoot.toString()), null);
    setStatus("Copied engine root path", ACCENT_NEUTRAL);
  }

  private void copyCurrentBranch() {
    String branch = resolveBranch(projectRoot);
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(branch), null);
    setStatus("Copied branch " + branch, ACCENT_NEUTRAL);
  }

  private static JPanel footerItem(String caption, JLabel value) {
    JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, ui(4), 0));
    item.setOpaque(false);
    JLabel cap = footerLabel(caption + ":", TEXT_MUTED);
    item.add(cap);
    item.add(value);
    return item;
  }

  private static JLabel footerLabel(String text, Color color) {
    JLabel label = new JLabel(text == null || text.isBlank() ? "--" : text);
    label.setForeground(color != null ? color : TEXT_SOFT);
    label.setFont(label.getFont().deriveFont(Font.BOLD, uiFont(11f)));
    return label;
  }

  private static JComponent footerDivider() {
    JPanel divider = new JPanel();
    divider.setBackground(Color.decode("#353535"));
    divider.setPreferredSize(uiDimension(1, 14));
    divider.setMaximumSize(uiDimension(1, 14));
    return divider;
  }

  private void refreshFooterMode() {
    String mode;
    Color color;
    if (developerModeEnabled && safeModeEnabled) {
      mode = "Dev + Safe";
      color = ACCENT_SAFE;
    } else if (developerModeEnabled) {
      mode = "Developer";
      color = ACCENT_DEV;
    } else if (safeModeEnabled) {
      mode = "Safe";
      color = ACCENT_SAFE;
    } else {
      mode = "Standard";
      color = TEXT_SOFT;
    }
    footerModeLabel.setText(mode);
    footerModeLabel.setForeground(color);
  }

  private static String javaFeatureVersion() {
    String version = System.getProperty("java.version", "");
    if (version.isBlank()) return "--";
    if (version.startsWith("1.")) {
      int next = version.indexOf('.', 2);
      return next > 0 ? version.substring(2, next) : version.substring(2);
    }
    int dot = version.indexOf('.');
    int dash = version.indexOf('-');
    int end = dot > 0 ? dot : (dash > 0 ? dash : version.length());
    return version.substring(0, end);
  }

  private static String resolveBranch(Path projectRoot) {
    Path dir = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
    for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
      Optional<Path> gitDirOpt = resolveGitDir(dir.resolve(".git"));
      if (gitDirOpt.isEmpty()) continue;
      Path gitDir = gitDirOpt.get();
      Path head = gitDir.resolve("HEAD");
      if (!Files.isRegularFile(head)) continue;
      try {
        String value = Files.readString(head).trim();
        if (value.startsWith("ref:")) {
          String ref = value.substring(4).trim();
          int slash = ref.lastIndexOf('/');
          return slash >= 0 ? ref.substring(slash + 1) : ref;
        }
        return value.length() > 7 ? value.substring(0, 7).toLowerCase(Locale.ROOT) : value;
      } catch (IOException ignored) {
        return "No branch";
      }
    }
    return "No branch";
  }

  private static Optional<Path> resolveGitDir(Path gitPath) {
    try {
      if (gitPath == null) return Optional.empty();
      if (Files.isDirectory(gitPath)) return Optional.of(gitPath);
      if (Files.isRegularFile(gitPath)) {
        String text = Files.readString(gitPath).trim();
        if (text.startsWith("gitdir:")) {
          Path target = Paths.get(text.substring("gitdir:".length()).trim());
          Path parent = gitPath.getParent();
          if (parent == null) return Optional.empty();
          return Optional.of(target.isAbsolute()
              ? target.normalize()
              : parent.resolve(target).normalize());
        }
      }
    } catch (IOException ignored) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private void setSafeModeEnabled(boolean enabled) {
    safeModeEnabled = enabled;
    if (safeModeButton != null) safeModeButton.setSafeModeEnabled(enabled);
    refreshFooterMode();
    String title = enabled ? "Safe Mode enabled" : "Safe Mode disabled";
    String detail = enabled
        ? "Editor-side launches use safe-mode flags. Update Engine uses guarded Git recovery."
        : "Editor-side launches use the standard engine startup path. Update Engine uses the normal Git update path.";
    setStatus(title, enabled ? ACCENT_SAFE : TEXT_SOFT);
    setActivity(title, detail, false, enabled ? ACCENT_SAFE : TEXT_MUTED);
    refreshModeMenus();
  }

  private void setDeveloperModeEnabled(boolean enabled) {
    developerModeEnabled = enabled;
    if (developerModeButton != null) developerModeButton.setDeveloperModeEnabled(enabled);
    rebuildActionGrid();
    refreshFooterMode();
    String title = enabled ? "Developer Mode enabled" : "Developer Mode disabled";
    String detail = enabled
        ? "Run Tests is visible and editor-side launches receive developer-mode flags."
        : "Developer-only actions are hidden from the main hub controls.";
    setStatus(title, enabled ? ACCENT_DEV : TEXT_SOFT);
    setActivity(title, detail, false, enabled ? ACCENT_DEV : TEXT_MUTED);
    if (shellPanel != null) {
      shellPanel.setVisible(enabled);
      frame.setPreferredSize(null);
      frame.pack();
    }
    refreshModeMenus();
  }

  private void refreshModeMenus() {
    if (!frameConfigured) return;
    frame.setJMenuBar(buildMenuBar());
    frame.revalidate();
    frame.repaint();
  }

  private void showGradleOptionsDialog() {
    JDialog dialog = new JDialog(frame, "Developer Gradle Options", true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(14)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));
    root.add(dialogHeader("Developer Gradle Options", "Applied to hub Gradle actions while Developer Mode is enabled."), BorderLayout.NORTH);

    JCheckBox stacktrace = optionCheckBox("Stacktrace", "Add --stacktrace to failures.", gradleStacktraceEnabled);
    JCheckBox info = optionCheckBox("Info logging", "Add --info for more Gradle output.", gradleInfoLoggingEnabled);
    JCheckBox debug = optionCheckBox("Debug logging", "Add --debug for very verbose Gradle output.", gradleDebugLoggingEnabled);
    JCheckBox offline = optionCheckBox("Offline mode", "Add --offline and avoid network dependency resolution.", gradleOfflineEnabled);
    JCheckBox refresh = optionCheckBox("Refresh dependencies", "Add --refresh-dependencies.", gradleRefreshDependenciesEnabled);
    JCheckBox noBuildCache = optionCheckBox("No build cache", "Add --no-build-cache.", gradleNoBuildCacheEnabled);
    JCheckBox noDaemon = optionCheckBox("No daemon", "Add --no-daemon. Enable only when invoking via ./gradlew :hub:run to avoid nested-daemon conflicts. Leave off for normal hub launches to allow daemon reuse.", gradleNoDaemonEnabled);

    JTextField extraArgs = gradleTextField(gradleExtraArgs);
    extraArgs.setToolTipText("Extra Gradle arguments, for example: --scan -PmyFlag=true");

    JPanel options = new JPanel(new GridBagLayout());
    options.setBackground(PANEL_BG);
    options.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(12, 14, 12, 14)));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(0, 0, ui(6), 0);
    int row = 0;
    for (JCheckBox box : List.of(stacktrace, info, debug, offline, refresh, noBuildCache, noDaemon)) {
      gbc.gridy = row++;
      options.add(box, gbc);
    }
    JLabel extraLabel = new JLabel("Extra arguments");
    extraLabel.setForeground(TEXT_MUTED);
    extraLabel.setFont(extraLabel.getFont().deriveFont(Font.BOLD, uiFont(10f)));
    gbc.gridy = row++;
    gbc.insets = new java.awt.Insets(ui(6), 0, ui(5), 0);
    options.add(extraLabel, gbc);
    gbc.gridy = row;
    gbc.insets = new java.awt.Insets(0, 0, 0, 0);
    options.add(extraArgs, gbc);
    root.add(options, BorderLayout.CENTER);

    FlatButton reset = new FlatButton("Reset", null, null);
    reset.addActionListener(e -> {
      gradleStacktraceEnabled = true;
      gradleInfoLoggingEnabled = false;
      gradleDebugLoggingEnabled = false;
      gradleOfflineEnabled = false;
      gradleRefreshDependenciesEnabled = false;
      gradleNoBuildCacheEnabled = false;
      gradleNoDaemonEnabled = false;
      gradleExtraArgs = "";
      dialog.dispose();
      setStatus("Gradle options reset", ACCENT_DEV);
      setActivity("Gradle options reset", describeGradleOptions(), false, ACCENT_DEV);
    });

    FlatButton cancel = new FlatButton("Cancel", null, null);
    cancel.addActionListener(e -> dialog.dispose());

    FlatButton apply = new FlatButton("Apply", uiIcon(VectorIcon.Kind.CHECK, 14, ACCENT_DEV), ACCENT_DEV);
    apply.addActionListener(e -> {
      gradleStacktraceEnabled = stacktrace.isSelected();
      gradleInfoLoggingEnabled = info.isSelected();
      gradleDebugLoggingEnabled = debug.isSelected();
      gradleOfflineEnabled = offline.isSelected();
      gradleRefreshDependenciesEnabled = refresh.isSelected() && !offline.isSelected();
      gradleNoBuildCacheEnabled = noBuildCache.isSelected();
      gradleNoDaemonEnabled = noDaemon.isSelected();
      gradleExtraArgs = extraArgs.getText() == null ? "" : extraArgs.getText().trim();
      dialog.dispose();
      setStatus("Gradle options updated", ACCENT_DEV);
      setActivity("Gradle options updated", describeGradleOptions(), false, ACCENT_DEV);
    });

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(8), 0));
    footer.setOpaque(false);
    footer.add(reset);
    footer.add(cancel);
    footer.add(apply);
    root.add(footer, BorderLayout.SOUTH);

    dialog.setContentPane(root);
    Dimension minimum = uiDimension(620, 430);
    dialog.setMinimumSize(minimum);
    dialog.pack();
    if (dialog.getWidth() < minimum.width || dialog.getHeight() < minimum.height) {
      dialog.setSize(new Dimension(
          Math.max(dialog.getWidth(), minimum.width),
          Math.max(dialog.getHeight(), minimum.height)));
    }
    dialog.setLocationRelativeTo(frame);
    dialog.getRootPane().setDefaultButton(apply);
    dialog.setVisible(true);
  }

  private JCheckBox optionCheckBox(String label, String tooltip, boolean selected) {
    JCheckBox box = new JCheckBox(label, selected);
    box.setToolTipText(tooltip);
    box.setOpaque(false);
    box.setForeground(TEXT_SOFT);
    box.setFocusPainted(false);
    box.setIcon(new HubCheckIcon(false));
    box.setSelectedIcon(new HubCheckIcon(true));
    box.setDisabledIcon(new HubCheckIcon(false));
    box.setDisabledSelectedIcon(new HubCheckIcon(true));
    box.setFont(box.getFont().deriveFont(Font.PLAIN, uiFont(12.5f)));
    box.setBorder(uiPadding(4, 2, 4, 2));
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    return box;
  }

  private JTextField gradleTextField(String value) {
    JTextField field = new JTextField(value == null ? "" : value);
    field.setForeground(TEXT_PRIMARY);
    field.setBackground(BG);
    field.setCaretColor(TEXT_PRIMARY);
    field.setSelectionColor(new Color(60, 110, 160));
    field.setSelectedTextColor(TEXT_PRIMARY);
    field.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_NEUTRAL),
        uiPadding(8, 10, 8, 10)));
    Dimension size = uiDimension(560, 38);
    field.setPreferredSize(size);
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
    return field;
  }

  private void showDiagnosticsReport() {
    setStatus("Running health check", ACCENT_NEUTRAL);
    setActivity("Running diagnostics", "Checking local engine setup.", true, ACCENT_NEUTRAL);
    setButtonsEnabled(false);

    new SwingWorker<List<HealthCheck>, Void>() {
      @Override protected List<HealthCheck> doInBackground() {
        return runHealthChecks();
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        List<HealthCheck> checks;
        try {
          checks = get();
        } catch (Exception e) {
          checks = List.of(new HealthCheck(
              CheckStatus.FAIL,
              "Diagnostics",
              "Health check failed to run.",
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        long failures = checks.stream().filter(c -> c.status() == CheckStatus.FAIL).count();
        long warnings = checks.stream().filter(c -> c.status() == CheckStatus.WARN).count();
        Color tone = failures > 0 ? ACCENT_ERROR : warnings > 0 ? ACCENT_NEUTRAL : ACCENT_GREEN;
        String title = failures > 0
            ? "Health check found " + failures + " issue" + (failures == 1 ? "" : "s")
            : warnings > 0
                ? "Health check found " + warnings + " warning" + (warnings == 1 ? "" : "s")
                : "Health check passed";
        setStatus(title, tone);
        setActivity(title, "Diagnostics report is ready.", false, tone);
        showReportDialog("Diagnostics / Health Check", checks, healthSummary(checks));
      }
    }.execute();
  }

  private List<HealthCheck> runHealthChecks() {
    List<HealthCheck> checks = new ArrayList<>();

    int requiredJava = readRequiredJavaVersion();
    int runtimeJava = parseJavaMajor(System.getProperty("java.version", ""));
    boolean javaOk = requiredJava <= 0 || runtimeJava <= 0 || runtimeJava >= requiredJava;
    checks.add(new HealthCheck(
        javaOk ? CheckStatus.PASS : CheckStatus.FAIL,
        "Java runtime",
        "Running Java " + firstNonBlank(System.getProperty("java.version"), "unknown")
            + (requiredJava > 0 ? "; project requests Java " + requiredJava + "." : "."),
        "java.home=" + firstNonBlank(System.getProperty("java.home"), "unknown")));

    Path wrapper = Path.of(gradleCommand());
    boolean wrapperExists = Files.isRegularFile(wrapper);
    boolean wrapperRunnable = wrapperExists && (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
        || Files.isExecutable(wrapper));
    checks.add(new HealthCheck(
        wrapperRunnable ? CheckStatus.PASS : CheckStatus.FAIL,
        "Gradle wrapper",
        wrapperRunnable ? "Wrapper is present and runnable." : "Gradle wrapper is missing or not executable.",
        wrapper.toString()));

    Path gitDir = projectRoot.resolve(".git");
    CommandResult status = commandExists("git") && Files.isDirectory(gitDir)
        ? runGit(List.of("git", "-c", "core.quotePath=false", "status", "--porcelain=v1", "--untracked-files=all"), 8)
        : new CommandResult(-1, commandExists("git") ? ".git directory not found" : "git command not found");
    if (status.exitCode == 0) {
      List<GitStatusEntry> entries = parseGitStatus(status.output);
      checks.add(new HealthCheck(
          entries.isEmpty() ? CheckStatus.PASS : CheckStatus.WARN,
          "Repository status",
          entries.isEmpty()
              ? "Working tree is clean."
              : entries.size() + " local change" + (entries.size() == 1 ? "" : "s") + " detected.",
          entries.isEmpty() ? "git status --porcelain returned no changes." : summarizeEntries(entries)));
    } else {
      checks.add(new HealthCheck(
          CheckStatus.WARN,
          "Repository status",
          "Git status is unavailable.",
          status.output.strip()));
    }

    String javafxVersion = readGradleProperty("jvnJavaFxVersion");
    boolean hasJavaFxConfig = javafxVersion != null && !javafxVersion.isBlank()
        && Files.isRegularFile(projectRoot.resolve("modules/editor/build.gradle.kts"));
    checks.add(new HealthCheck(
        hasJavaFxConfig ? CheckStatus.PASS : CheckStatus.WARN,
        "JavaFX configuration",
        hasJavaFxConfig
            ? "Editor JavaFX runtime is configured for version " + javafxVersion + "."
            : "JavaFX configuration could not be confirmed.",
        "Validated gradle.properties and modules/editor/build.gradle.kts."));

    Path stateDir = projectRoot.resolve(".jvn");
    checks.add(checkWritableStateDirectory(stateDir));

    int incoming = readIncomingCommitCount();
    checks.add(new HealthCheck(
        incoming < 0 ? CheckStatus.WARN : CheckStatus.PASS,
        "Update status",
        incoming > 0
            ? incoming + " incoming commit" + (incoming == 1 ? "" : "s") + " available."
            : incoming == 0
                ? "Engine appears up to date with " + ENGINE_UPDATE_REMOTE_REF + "."
                : "Incoming update count is unavailable.",
        incoming < 0
            ? "Git unavailable, " + ENGINE_UPDATE_REMOTE_REF + " unavailable, or rev-list failed."
            : "Compared HEAD.." + ENGINE_UPDATE_REMOTE_REF + "."));

    return checks;
  }

  private HealthCheck checkWritableStateDirectory(Path stateDir) {
    try {
      Files.createDirectories(stateDir);
      Path probe = Files.createTempFile(stateDir, "health-", ".tmp");
      Files.writeString(probe, "ok", StandardCharsets.UTF_8);
      Files.deleteIfExists(probe);
      return new HealthCheck(
          CheckStatus.PASS,
          "Writable .jvn state",
          "Hub state directory is writable.",
          stateDir.toAbsolutePath().toString());
    } catch (IOException e) {
      return new HealthCheck(
          CheckStatus.FAIL,
          "Writable .jvn state",
          "Hub state directory is not writable.",
          stateDir.toAbsolutePath() + "\n" + e.getMessage());
    }
  }

  private String healthSummary(List<HealthCheck> checks) {
    long failures = checks.stream().filter(c -> c.status() == CheckStatus.FAIL).count();
    long warnings = checks.stream().filter(c -> c.status() == CheckStatus.WARN).count();
    return failures > 0
        ? failures + " issue" + (failures == 1 ? "" : "s") + " need attention"
        : warnings > 0
            ? warnings + " warning" + (warnings == 1 ? "" : "s") + " to review"
            : "All lightweight checks passed";
  }

  private void showReportDialog(String title, List<HealthCheck> checks, String summary) {
    JDialog dialog = new JDialog(frame, title, true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel root = new JPanel(new BorderLayout(0, ui(12)));
    root.setBackground(BG);
    root.setBorder(uiPadding(16, 16, 16, 16));

    root.add(dialogHeader(title, summary), BorderLayout.NORTH);

    JPanel rows = new JPanel();
    rows.setBackground(BG);
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    for (int i = 0; i < checks.size(); i++) {
      rows.add(healthCheckCard(checks.get(i)));
      if (i < checks.size() - 1) rows.add(Box.createVerticalStrut(ui(8)));
    }

    JScrollPane scroll = new JScrollPane(rows);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(uiDimension(620, 390));
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    root.add(scroll, BorderLayout.CENTER);
    root.add(dialogFooter(dialog), BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);
  }

  private JPanel healthCheckCard(HealthCheck check) {
    JPanel card = new JPanel(new BorderLayout(ui(10), 0));
    card.setBackground(PANEL_BG);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(check.status().color()),
        uiPadding(10, 12, 10, 12)));

    JLabel icon = new JLabel(uiIcon(check.status().icon(), 18, check.status().color()));
    card.add(icon, BorderLayout.WEST);

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    JLabel title = new JLabel(check.title());
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(13f)));
    JTextArea summary = dialogText(check.summary(), TEXT_SOFT, 11f, Font.PLAIN);
    JTextArea details = dialogText(check.details(), TEXT_MUTED, 10f, Font.PLAIN);
    details.setBorder(uiPadding(4, 0, 0, 0));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    summary.setAlignmentX(Component.LEFT_ALIGNMENT);
    details.setAlignmentX(Component.LEFT_ALIGNMENT);
    text.add(title);
    text.add(Box.createVerticalStrut(ui(2)));
    text.add(summary);
    if (check.details() != null && !check.details().isBlank()) {
      text.add(details);
    }
    card.add(text, BorderLayout.CENTER);
    return card;
  }

  private void showAboutReport() {
    String version = displayVersionLabel(readDiskVersion());
    String sourceMode = isRunningFromSource() ? "Running from source" : "Packaged build";
    String commit = readGitValue(List.of("git", "rev-parse", "--short", "HEAD"), "unknown");
    String branch = readGitValue(List.of("git", "rev-parse", "--abbrev-ref", "HEAD"), "unknown");
    int incoming = readIncomingCommitCount();
    String updateStatus = incoming > 0
        ? incoming + " incoming commit" + (incoming == 1 ? "" : "s") + " available"
        : incoming == 0
            ? "Up to date"
            : "Unavailable";

    List<HealthCheck> report = List.of(
        new HealthCheck(CheckStatus.INFO, "Version", version, sourceMode),
        new HealthCheck(CheckStatus.INFO, "Source / build mode", sourceMode,
            isRunningFromSource()
                ? "Launched from compiled classes in the engine checkout."
                : "Launched from a packaged runtime artifact."),
        new HealthCheck(CheckStatus.INFO, "Git revision",
            commit + ("unknown".equals(branch) ? "" : " on " + branch),
            "Current engine checkout revision."),
        new HealthCheck(CheckStatus.INFO, "Install path",
            projectRoot.toAbsolutePath().toString(),
            "The hub uses this folder as its engine workspace."),
        new HealthCheck(CheckStatus.INFO,
            "Developer Mode",
            developerModeEnabled ? "Enabled" : "Disabled",
            developerModeEnabled
                ? "Run Tests is visible and launch commands receive developer-mode flags."
                : "Developer-focused actions are hidden from the main action grid."),
        new HealthCheck(CheckStatus.INFO,
            "Gradle options",
            describeGradleOptions(),
            developerModeEnabled
                ? "These options are applied before the Gradle task name."
                : "Enable Developer Mode to apply configurable Gradle options."),
        new HealthCheck(safeModeEnabled ? CheckStatus.WARN : CheckStatus.INFO,
            "Safe Mode",
            safeModeEnabled ? "Enabled" : "Disabled",
            safeModeEnabled
                ? "Editor-side launches receive safe-mode flags while preserving the normal Gradle cache."
                : "Editor-side launches use the standard startup path."),
        new HealthCheck(incoming > 0 ? CheckStatus.WARN : incoming == 0 ? CheckStatus.PASS : CheckStatus.INFO,
            "Update status", updateStatus, "Compared HEAD.." + ENGINE_UPDATE_REMOTE_REF + "."),
        new HealthCheck(CheckStatus.INFO, "Java runtime",
            firstNonBlank(System.getProperty("java.version"), "unknown"),
            "java.home=" + firstNonBlank(System.getProperty("java.home"), "unknown")),
        new HealthCheck(CheckStatus.INFO, "Operating system",
            firstNonBlank(System.getProperty("os.name"), "unknown"),
            firstNonBlank(System.getProperty("os.arch"), "unknown")),
        new HealthCheck(CheckStatus.INFO, "Display scale",
            displayScaleSummary(),
            displayScaleDetails())
    );
    setStatus("Engine info ready", ACCENT_NEUTRAL);
    setActivity("Engine info", version + " · " + sourceMode, false, ACCENT_NEUTRAL);
    showReportDialog("About / Version Info", report, version + " · " + sourceMode);
  }

  private JPanel dialogHeader(String titleText, String subtitleText) {
    JLabel title = new JLabel(titleText);
    title.setForeground(TEXT_PRIMARY);
    title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(16f)));
    JLabel subtitle = new JLabel(subtitleText == null ? "" : subtitleText);
    subtitle.setForeground(TEXT_MUTED);
    subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, uiFont(11f)));

    JPanel box = new JPanel();
    box.setOpaque(false);
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(title);
    box.add(Box.createVerticalStrut(ui(2)));
    box.add(subtitle);
    return box;
  }

  private JPanel dialogFooter(JDialog dialog) {
    FlatButton close = new FlatButton("Close",
        uiIcon(VectorIcon.Kind.CLOSE, 14, TEXT_PRIMARY), null);
    close.addActionListener(e -> dialog.dispose());
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    footer.setOpaque(false);
    footer.add(close);
    return footer;
  }

  private JButton makeAction(String label, String tooltip, VectorIcon.Kind iconKind,
                             boolean accent, Runnable action) {
    return makeAction(label, tooltip, iconKind, accent ? ACCENT_NEUTRAL : null, action);
  }

  private JButton makeAction(String label, String tooltip, VectorIcon.Kind iconKind,
                             Color accentOrNull, Runnable action) {
    Color foreground = accentOrNull != null ? accentOrNull : TEXT_PRIMARY;
    Icon icon = iconKind != null ? uiIcon(iconKind, 16, foreground) : null;
    FlatButton button = new FlatButton(label, icon, accentOrNull);
    button.setToolTipText(tooltip);
    button.addActionListener(e -> action.run());
    actionButtons.add(button);
    return button;
  }

  private JButton makeLauncherAction() {
    LauncherButton button = new LauncherButton("Run Launcher");
    button.setMaintenanceState(launcherMaintenanceState);
    button.addActionListener(e -> runLauncherAction());
    actionButtons.add(button);
    return button;
  }

  private void runLauncherAction() {
    if (launcherMaintenanceState.underMaintenance()) {
      showLauncherMaintenanceNotice();
      return;
    }
    guardedRun("Run Launcher", () -> runFastApp("launcher", "Run Launcher"));
  }

  private void showLauncherMaintenanceNotice() {
    LauncherMaintenanceState state = launcherMaintenanceState;
    setStatus("Launcher under maintenance", ACCENT_MAINTENANCE);
    setActivity("Launcher under maintenance", state.resolvedMessage(), false, ACCENT_MAINTENANCE);
  }

  // --- Task execution --------------------------------------------------------

  /**
   * Runs {@code action} immediately, or first warns the user that the engine is behind
   * origin/stable when the last update check reported one or more incoming commits.
   * Choosing "Update Now" triggers {@link #updateEngine()}; "Run Anyway" proceeds with
   * the original action; closing the dialog cancels entirely.
   */
  private void guardedRun(String label, Runnable action) {
    if (lastKnownIncoming <= 0) {
      action.run();
      return;
    }
    String commitWord = lastKnownIncoming == 1 ? "commit" : "commits";
    int choice = showUpdateDialog(
        "Engine Update Available",
        "Your engine is " + lastKnownIncoming + " " + commitWord + " behind " + ENGINE_UPDATE_REMOTE_REF + ".",
        "Running '" + label + "' without updating may cause unexpected behaviour "
            + "or use outdated project files.\n\n"
            + "Click 'Update Now' to pull the latest changes first, "
            + "or 'Run Anyway' to continue with the current version.",
        new String[]{"Update Now", "Run Anyway"},
        UpdateDialogTone.WARNING);
    if (choice == 0) {
      updateEngine();
    } else {
      action.run();
    }
  }

  private void runGradle(String task, String label) {
    if (!acquire(label)) return;
    List<String> cmd = new ArrayList<>();
    cmd.add(gradleCommand());
    cmd.add("--console=plain");
    if (developerModeEnabled) {
      cmd.add("-Djvn.hub.developerMode=true");
      cmd.add("-Djvn.editor.developerMode=true");
      cmd.add("-Djvn.launcher.developerMode=true");
      cmd.add("-Djvn.help.developerMode=true");
      cmd.addAll(developerGradleOptions());
    }
    if (safeModeEnabled) {
      cmd.add("-Djvn.hub.safeMode=true");
      cmd.add("-Djvn.editor.safeMode=true");
      cmd.add("-Djvn.launcher.safeMode=true");
      cmd.add("-Djvn.help.safeMode=true");
    }
    if (shouldPreferConfigurationCache(task)) {
      cmd.add("--configuration-cache");
    }
    if (shouldLimitLaunchWorkers(task)) {
      cmd.add("--max-workers=" + balancedLaunchWorkerCount());
    }
    cmd.add(task);
    completeCurrentStep("Gradle command assembled.");
    advanceStep(modeLaunchDetail());
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, label);
  }

  private void runFastApp(String app, String label) {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      runGradle(app.equals("runtime") ? ":runtime:run"
          : app.equals("launcher") ? ":editor:runLauncher" : ":editor:run", label);
      return;
    }
    if (!acquire(label)) return;
    List<String> cmd = new ArrayList<>();
    cmd.add(projectRoot.resolve("scripts").resolve("launch-app.sh").toString());
    cmd.add(app);
    if (developerModeEnabled) cmd.add("--developer-mode");
    if (safeModeEnabled) cmd.add("--safe-mode");
    completeCurrentStep("Cache-first launch command assembled.");
    advanceStep(modeLaunchDetail());
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, label);
  }

  private String modeLaunchDetail() {
    if (developerModeEnabled && safeModeEnabled) return "Starting background process in Developer + Safe Mode.";
    if (developerModeEnabled) return "Starting background process in Developer Mode.";
    if (safeModeEnabled) return "Starting background process in Safe Mode.";
    return "Starting background process.";
  }

  private boolean shouldPreferConfigurationCache(String task) {
    if (safeModeEnabled) return false;
    if (hasConfigurationCacheFlag()) return false;
    return switch (task) {
      case ":editor:run", ":editor:runLauncher", ":runtime:run",
          "build", "test", "check", "ci", "compileAll", "quickCheck" -> true;
      default -> false;
    };
  }

  private boolean hasConfigurationCacheFlag() {
    for (String arg : splitExtraGradleArgs(gradleExtraArgs)) {
      if (arg.equals("--configuration-cache")
          || arg.startsWith("--configuration-cache=")
          || arg.equals("--no-configuration-cache")
          || arg.equals("-Dorg.gradle.configuration-cache")
          || arg.startsWith("-Dorg.gradle.configuration-cache=")) {
        return true;
      }
    }
    return false;
  }

  private boolean shouldLimitLaunchWorkers(String task) {
    if (hasMaxWorkersFlag()) return false;
    return switch (task) {
      case ":editor:run", ":editor:runLauncher", ":runtime:run" -> true;
      default -> false;
    };
  }

  private boolean hasMaxWorkersFlag() {
    for (String arg : splitExtraGradleArgs(gradleExtraArgs)) {
      if (arg.equals("--max-workers") || arg.startsWith("--max-workers=")) {
        return true;
      }
    }
    return false;
  }

  private int balancedLaunchWorkerCount() {
    int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
    return Math.max(2, processors <= 4 ? 2 : processors - 2);
  }

  private List<String> developerGradleOptions() {
    List<String> options = new ArrayList<>();
    if (gradleStacktraceEnabled) options.add("--stacktrace");
    if (gradleDebugLoggingEnabled) {
      options.add("--debug");
    } else if (gradleInfoLoggingEnabled) {
      options.add("--info");
    }
    if (gradleOfflineEnabled) {
      options.add("--offline");
    } else if (gradleRefreshDependenciesEnabled) {
      options.add("--refresh-dependencies");
    }
    if (gradleNoBuildCacheEnabled) options.add("--no-build-cache");
    if (gradleNoDaemonEnabled) options.add("--no-daemon");
    options.addAll(splitExtraGradleArgs(gradleExtraArgs));
    return options;
  }

  private String describeGradleOptions() {
    if (!developerModeEnabled) return "Developer Mode is off.";
    List<String> options = developerGradleOptions();
    return options.isEmpty() ? "No additional Gradle flags." : String.join(" ", options);
  }

  private void openDocumentationWebsite() {
    openWebsite("Documentation website", PUBLIC_DOCUMENTATION_URL);
  }

  private void openSourceRepository() {
    openWebsite("Source repository", SOURCE_REPOSITORY_URL);
  }

  private void openWebsite(String label, String url) {
    try {
      if (!java.awt.Desktop.isDesktopSupported()
          || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
        throw new IOException("Desktop browser integration is not available.");
      }
      java.awt.Desktop.getDesktop().browse(URI.create(url));
      setActivity(label, url, false, ACCENT_NEUTRAL);
    } catch (Exception ex) {
      String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      appendLog("[hub] could not open " + label.toLowerCase(Locale.ROOT) + ": " + detail);
      setActivity("Open website failed", detail, false, ACCENT_ERROR);
    }
  }

  private void updateEngine() {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return;
    }
    startSteps("Update Engine");
    setActivity("Inspecting update readiness", "Checking Git worktree before pulling.", true, ACCENT_NEUTRAL);
    UpdatePreflight preflight = inspectUpdatePreflight();
    completeCurrentStep("Git worktree inspected.");
    if (preflight.statusUnavailable()) {
      if (!confirmUpdateWithUnknownStatus(preflight.statusError())) {
        finishSteps(false, "Update cancelled before pull.");
        setActivity("Update cancelled", "No engine files were changed.", false, TEXT_MUTED);
        return;
      }
    } else if (preflight.hasInterruptedGitOperation() || preflight.hasChanges()) {
      if (safeModeEnabled && preflight.hasInterruptedGitOperation()) {
        finishSteps(false, "Safe Mode recovery started.");
        recoverSafeModeUpdateFailure("Interrupted Git operation found before update.\n\n" + preflight.summary());
        return;
      }
      if (safeModeEnabled && preflight.hasChanges() && !preflight.onlyBuildOutput()) {
        completeCurrentStep("Local changes detected; Safe Mode will use Git autostash.");
        appendLog("[hub] Safe Mode: local changes detected; using git pull --rebase --autostash "
            + ENGINE_UPDATE_REMOTE + " " + ENGINE_UPDATE_BRANCH + ".");
        startUpdateEngine();
        return;
      }
      UpdatePreflightAction action = chooseUpdatePreflightAction(preflight);
      if (action == UpdatePreflightAction.CANCEL) {
        finishSteps(false, "Update cancelled before pull.");
        setActivity("Update cancelled", "No engine files were changed.", false, TEXT_MUTED);
        return;
      }
      cleanBeforeUpdate(preflight);
      return;
    }
    startUpdateEngine();
  }

  private void startUpdateEngine() {
    if (!acquire("Update Engine")) return;
    if (updateEngineButton != null) updateEngineButton.setChecking(true);
    List<String> cmd = safeModeEnabled
        ? List.of("git", "pull", "--rebase", "--autostash", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_BRANCH)
        : List.of("git", "pull", "--rebase", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_BRANCH);
    completeCurrentStep("Git command assembled.");
    advanceStep("Fetching from " + ENGINE_UPDATE_REMOTE_REF + ".");
    if (safeModeEnabled) {
      appendLog("[hub] Safe Mode: Update Engine will autostash tracked local changes and auto-recover failed rebase state.");
    }
    appendLog("$ " + String.join(" ", cmd));
    startProcess(cmd, "Update Engine");
  }

  private UpdatePreflight inspectUpdatePreflight() {
    CommandResult status = runGit(
        List.of("git", "-c", "core.quotePath=false", "status", "--porcelain=v1", "--untracked-files=all"),
        10);
    if (status.exitCode != 0) {
      return UpdatePreflight.unavailable(status.output.strip().isBlank()
          ? "git status failed with exit " + status.exitCode
          : status.output.strip());
    }
    return UpdatePreflight.from(parseGitStatus(status.output), inspectInterruptedGitOperations());
  }

  private boolean confirmUpdateWithUnknownStatus(String details) {
    String[] options = {"Update Anyway", "Cancel"};
    int choice = showUpdateDialog(
        "Update Engine",
        "The hub could not check whether local engine files have changed.",
        "Updating may still work, but Git may stop if local files would be overwritten.\n\n" + details,
        options,
        UpdateDialogTone.WARNING);
    return choice == 0;
  }

  private UpdatePreflightAction chooseUpdatePreflightAction(UpdatePreflight preflight) {
    if (preflight.hasInterruptedGitOperation()) {
      String[] options = {"Abort Git Operation and Update", "Cancel"};
      int choice = showUpdateDialog(
          "Interrupted Git Update Found",
          "Update Engine found an unfinished Git operation in the engine checkout.",
          "This usually happens after a previous update failed during rebase or conflict resolution. "
              + "The Hub can abort that unfinished Git operation, clean the checkout, and retry the update.\n\n"
              + preflight.summary(),
          options,
          UpdateDialogTone.WARNING);
      return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
    }

    if (preflight.onlyBuildOutput()) {
      String[] options = {"Clear Build Output and Update", "Cancel"};
      int choice = showUpdateDialog(
          "Clear Build Output?",
          "Update Engine found generated build output in the engine checkout.",
          "Build output can be safely recreated. Clear these files before updating?\n\n"
              + preflight.summary(),
          options,
          UpdateDialogTone.QUESTION);
      return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
    }

    String[] options = {"Clean Changes and Update", "Cancel"};
    int choice = showUpdateDialog(
        "Local Engine Changes Found",
        "Update Engine found local changes that do not look like build output.",
        "Cleaning will permanently discard these local engine changes and delete untracked files.\n"
            + "Choose Cancel if you want to keep or commit them first.\n\n"
            + preflight.summary(),
        options,
        UpdateDialogTone.DANGER);
    return choice == 0 ? UpdatePreflightAction.CLEAN_AND_UPDATE : UpdatePreflightAction.CANCEL;
  }

  private int showUpdateDialog(
      String title,
      String message,
      String details,
      String[] options,
      UpdateDialogTone tone
  ) {
    boolean hasSecondaryOption = options != null
        && options.length > 1
        && options[1] != null
        && !options[1].isBlank();
    AtomicReference<Integer> result = new AtomicReference<>(hasSecondaryOption ? 1 : 0);

    JDialog dialog = new JDialog(frame, title, true);
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.getRootPane().setBorder(BorderFactory.createLineBorder(tone.borderColor(), 1));

    JPanel card = new JPanel(new BorderLayout(0, ui(16)));
    card.setBackground(PANEL_BG);
    card.setBorder(uiPadding(18, 20, 18, 20));

    JPanel header = new JPanel(new BorderLayout(ui(14), 0));
    header.setOpaque(false);
    header.add(new JLabel(new UpdateDialogIcon(tone, ui(34))), BorderLayout.WEST);

    JPanel titleStack = new JPanel();
    titleStack.setOpaque(false);
    titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

    JLabel titleLabel = new JLabel(title == null || title.isBlank() ? "Update Engine" : title.trim());
    titleLabel.setForeground(TEXT_PRIMARY);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, uiFont(17f)));
    titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

    JTextArea messageArea = dialogText(message, TEXT_SOFT, 12f, Font.BOLD);
    messageArea.setAlignmentX(Component.LEFT_ALIGNMENT);
    messageArea.setBorder(uiPadding(4, 0, 0, 0));

    titleStack.add(titleLabel);
    titleStack.add(messageArea);
    header.add(titleStack, BorderLayout.CENTER);

    JButton close = iconOnlyButton(uiIcon(VectorIcon.Kind.CLOSE, 12, TEXT_MUTED));
    close.addActionListener(e -> {
      result.set(hasSecondaryOption ? 1 : 0);
      dialog.dispose();
    });
    header.add(close, BorderLayout.EAST);
    card.add(header, BorderLayout.NORTH);

    JTextArea detailArea = dialogText(details, LOG_TEXT, 11f, Font.PLAIN);
    detailArea.setBorder(uiPadding(10, 12, 10, 12));
    detailArea.setCaretPosition(0);

    JScrollPane scroll = new JScrollPane(detailArea);
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
    scroll.setBackground(BG);
    scroll.getViewport().setBackground(BG);
    scroll.setPreferredSize(uiDimension(540, 190));
    styleScrollBar(scroll.getVerticalScrollBar());
    styleScrollBar(scroll.getHorizontalScrollBar());
    card.add(scroll, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(10), 0));
    actions.setOpaque(false);

    String cancelLabel = hasSecondaryOption ? options[1] : "";
    String primaryLabel = options != null && options.length > 0 && options[0] != null ? options[0] : "Update";

    if (hasSecondaryOption) {
      FlatButton cancel = new FlatButton(cancelLabel, null, null);
      cancel.addActionListener(e -> {
        result.set(1);
        dialog.dispose();
      });
      actions.add(cancel);
    }

    FlatButton primary = new FlatButton(primaryLabel, null, tone.primaryColor());
    primary.addActionListener(e -> {
      result.set(0);
      dialog.dispose();
    });

    actions.add(primary);
    card.add(actions, BorderLayout.SOUTH);

    dialog.setContentPane(card);
    dialog.pack();
    dialog.setMinimumSize(uiDimension(620, 330));
    dialog.setLocationRelativeTo(frame);
    dialog.getRootPane().setDefaultButton(primary);
    dialog.getRootPane().registerKeyboardAction(
        e -> {
          result.set(hasSecondaryOption ? 1 : 0);
          dialog.dispose();
        },
        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.setVisible(true);

    return result.get();
  }

  private static JTextArea dialogText(String text, Color color, float size, int style) {
    JTextArea area = new JTextArea(text == null ? "" : text);
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setForeground(color);
    area.setFont(area.getFont().deriveFont(style, uiFont(size)));
    return area;
  }

  private static JButton iconOnlyButton(Icon icon) {
    JButton button = new JButton(icon);
    button.setBorder(uiPadding(6, 6, 6, 6));
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setRolloverEnabled(true);
    button.setToolTipText("Close");
    return button;
  }

  private void cleanBeforeUpdate(UpdatePreflight preflight) {
    setButtonsEnabled(false);
    setStatus(preflight.hasInterruptedGitOperation() ? "Recovering Git checkout" : "Cleaning before update", ACCENT_NEUTRAL);
    startSteps("Clean Before Update");
    setActivity(
        preflight.hasInterruptedGitOperation()
            ? "Recovering Git checkout"
            : preflight.onlyBuildOutput() ? "Clearing build output" : "Cleaning local engine changes",
        "Preparing the engine checkout for update.",
        true,
        ACCENT_NEUTRAL);

    new SwingWorker<Boolean, String>() {
      private String failure = "";

      @Override protected Boolean doInBackground() {
        publish("[hub] cleaning local files before Update Engine...");
        CommandResult result = preflight.onlyBuildOutput() && !preflight.hasInterruptedGitOperation()
            ? cleanBuildOutputChanges(preflight)
            : cleanAllLocalChanges();
        if (result.exitCode != 0) {
          failure = result.output.strip().isBlank()
              ? "clean command failed with exit " + result.exitCode
              : compactMessage(result.output);
          return false;
        }
        return true;
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          updateStepsFromOutput("Clean Before Update", chunks.get(chunks.size() - 1));
          appendLog(chunks.get(chunks.size() - 1));
        }
      }

      @Override protected void done() {
        setButtonsEnabled(true);
        boolean ok = false;
        try {
          ok = get();
        } catch (Exception e) {
          failure = e.getMessage();
        }
        if (!ok) {
          setStatus("Clean failed", ACCENT_ERROR);
          finishSteps(false, failure == null || failure.isBlank() ? "Clean failed." : failure);
          setActivity("Clean failed", failure == null || failure.isBlank() ? "Git could not clean the checkout." : failure,
              false, ACCENT_ERROR);
          return;
        }
        finishSteps(true, "Checkout cleaned.");
        setActivity("Clean complete", "Starting engine update.", true, ACCENT_NEUTRAL);
        startUpdateEngine();
      }
    }.execute();
  }

  private CommandResult cleanBuildOutputChanges(UpdatePreflight preflight) {
    List<String> tracked = preflight.buildOutputChanges().stream()
        .filter(GitStatusEntry::trackedChange)
        .map(GitStatusEntry::path)
        .distinct()
        .toList();
    if (!tracked.isEmpty()) {
      List<String> restore = new ArrayList<>(List.of("git", "restore", "--staged", "--worktree", "--"));
      restore.addAll(tracked);
      CommandResult result = runGit(restore, 60);
      if (result.exitCode != 0) return result;
    }

    List<String> untracked = preflight.buildOutputChanges().stream()
        .filter(GitStatusEntry::untracked)
        .map(GitStatusEntry::path)
        .distinct()
        .toList();
    if (!untracked.isEmpty()) {
      List<String> clean = new ArrayList<>(List.of("git", "clean", "-fd", "--"));
      clean.addAll(untracked);
      CommandResult result = runGit(clean, 60);
      if (result.exitCode != 0) return result;
    }

    return new CommandResult(0, "cleaned build output");
  }

  private CommandResult cleanAllLocalChanges() {
    CommandResult abort = abortInterruptedGitOperations();
    if (abort.exitCode != 0) return abort;
    CommandResult reset = runGit(List.of("git", "reset", "--hard", "HEAD"), 60);
    if (reset.exitCode != 0) return reset;
    CommandResult clean = runGit(List.of("git", "clean", "-fd"), 60);
    if (clean.exitCode != 0) return clean;
    return new CommandResult(0, abort.output + reset.output + clean.output);
  }

  private CommandResult abortInterruptedGitOperations() {
    StringBuilder output = new StringBuilder();
    if (gitPathExists("rebase-merge") || gitPathExists("rebase-apply")) {
      CommandResult result = runGit(List.of("git", "rebase", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git rebase --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("MERGE_HEAD")) {
      CommandResult result = runGit(List.of("git", "merge", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git merge --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("CHERRY_PICK_HEAD")) {
      CommandResult result = runGit(List.of("git", "cherry-pick", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git cherry-pick --abort failed:\n" + result.output);
      }
    }
    if (gitPathExists("REVERT_HEAD")) {
      CommandResult result = runGit(List.of("git", "revert", "--abort"), 30);
      output.append(result.output);
      if (result.exitCode != 0) {
        return new CommandResult(result.exitCode, "git revert --abort failed:\n" + result.output);
      }
    }
    return new CommandResult(0, output.toString());
  }

  private void installShortcuts() {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return;
    }
    startSteps("Build Shortcuts");
    setActivity("Preparing shortcuts", "Detecting operating system.", true, ACCENT_NEUTRAL);
    String os = System.getProperty("os.name", "").toLowerCase();
    List<String> cmd = new ArrayList<>();
    Path script;
    String label = "Build Shortcuts";

    if (os.contains("win")) {
      script = projectRoot.resolve("install-windows-launcher.ps1");
      cmd.add(windowsPowerShellCommand());
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
    completeCurrentStep("Operating system detected.");
    advanceStep("Looking for shortcut installer.");

    if (!Files.isRegularFile(script)) {
      appendLog("[hub] shortcut installer not found: " + script.toAbsolutePath());
      setStatus("Failed: " + label, ACCENT_ERROR);
      finishSteps(false, "Shortcut installer not found.");
      setActivity("Failed: " + label, "Shortcut installer not found.", false, ACCENT_ERROR);
      return;
    }

    if (!acquire(label)) return;
    completeCurrentStep("Installer command assembled.");
    advanceStep("Starting shortcut installer.");
    appendLog("$ " + quoteCommandForLog(cmd));
    startProcess(cmd, label);
  }

  private boolean acquire(String label) {
    if (runningProcess.get() != null) {
      appendLog("[hub] a task is already running; wait for it to finish or cancel it.");
      return false;
    }
    setButtonsEnabled(false);
    setStatus("Running: " + label + activeModeSuffix(), activeModeColor());
    startSteps(label);
    startAutoStepTicker(label);
    setActivity("Working on " + label, activeModeActivityDetail(), true, activeModeColor());
    return true;
  }

  private String activeModeSuffix() {
    if (developerModeEnabled && safeModeEnabled) return " (Developer + Safe Mode)";
    if (developerModeEnabled) return " (Developer Mode)";
    if (safeModeEnabled) return " (Safe Mode)";
    return "";
  }

  private Color activeModeColor() {
    if (safeModeEnabled) return ACCENT_SAFE;
    if (developerModeEnabled) return ACCENT_DEV;
    return ACCENT_NEUTRAL;
  }

  private String activeModeActivityDetail() {
    if (developerModeEnabled && safeModeEnabled) {
      return "Developer diagnostics are enabled while launch state is isolated.";
    }
    if (developerModeEnabled) return "Developer Mode is exposing engineering-focused launch behavior.";
    if (safeModeEnabled) return "Safe Mode flags are enabled for this process.";
    return "This can take a moment.";
  }

  private void release(String label, int exitCode) {
    runningProcess.set(null);
    setButtonsEnabled(true);
    Color tone = exitCode == 0 ? ACCENT_GREEN : ACCENT_ERROR;
    String prefix = exitCode == 0 ? "Done" : "Failed (exit " + exitCode + ")";
    setStatus(prefix + ": " + label, tone);
    finishSteps(exitCode == 0,
        exitCode == 0 ? "All stages completed." : "Stopped before every stage completed.");
    setActivity(prefix + ": " + label,
        exitCode == 0 ? "Ready for the next action." : "Check the terminal or generated launcher log for details.",
        false,
        tone);
  }

  private void startProcess(List<String> command, String label) {
    new SwingWorker<Integer, String>() {
      private String lastOutput = "";
      private final StringBuilder fullOutput = new StringBuilder();
      private final List<String> recentOutput = new ArrayList<>();

      @Override protected Integer doInBackground() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(projectRoot.toFile())
            .redirectErrorStream(true);
        if (isGradleWrapperCommand(command)) {
          Path packagedGradleHome = projectRoot.resolve(".jvn-gradle-user-home");
          if (isPackagedGradleHome(packagedGradleHome)) {
            pb.environment().put("GRADLE_USER_HOME", packagedGradleHome.toAbsolutePath().toString());
            publish("[hub] using packaged Gradle user home.");
          } else if (Files.isDirectory(packagedGradleHome)) {
            publish("[hub] using default Gradle user home; packaged cache marker was not found.");
          }
        }
        Process process;
        try {
          publish("[hub] preparing process environment...");
          process = pb.start();
        } catch (IOException e) {
          publish("[hub] failed to start process: " + e.getMessage());
          return -1;
        }
        runningProcess.set(process);
        publish("[hub] process started; reading live output...");
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
              if (fullOutput.length() < PROCESS_OUTPUT_PREFIX_LIMIT) fullOutput.append(line).append('\n');
              rememberProcessOutput(recentOutput, line);
              lastOutput = compactMessage(line);
              publish(line);
            }
          }
        } catch (IOException e) {
          lastOutput = "Stream error: " + e.getMessage();
          publish("[hub] " + lastOutput);
        }
        return process.waitFor();
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          String line = chunks.get(chunks.size() - 1);
          updateStepsFromOutput(label, line);
          appendLog(line);
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
        if (exit == 0) {
          if (!lastOutput.isBlank()) {
            setActivityDetail(lastOutput);
          }
        } else {
          String failureSummary = summarizeProcessFailure(fullOutput.toString(), recentOutput, lastOutput);
          if (!failureSummary.isBlank()) {
            appendLog("[hub] failure summary: " + failureSummary);
            setActivityDetail(failureSummary);
          } else if (!lastOutput.isBlank()) {
            setActivityDetail(lastOutput);
          }
        }
        // Update Engine touched the working tree — re-read on-disk state so
        // version and maintenance state reflect the new HEAD.
        if (exit == 0 && "Update Engine".equals(label)) {
          refreshFromDisk();
          checkIncomingUpdates(false);
        } else if ("Update Engine".equals(label) && updateEngineButton != null) {
          updateEngineButton.setChecking(false);
          handleUpdateEngineFailure(exit, fullOutput.toString().isBlank() ? lastOutput : fullOutput.toString());
        }
      }
    }.execute();
  }

  private void handleUpdateEngineFailure(int exitCode, String output) {
    if (safeModeEnabled) {
      recoverSafeModeUpdateFailure(output);
      return;
    }

    setActivity(
        "Update Engine failed",
        friendlyUpdateFailureSummary(output, UpdatePreflight.empty())
            + " Enable Safe Mode to recover the checkout automatically.",
        false,
        ACCENT_ERROR);

    new SwingWorker<UpdatePreflight, Void>() {
      @Override protected UpdatePreflight doInBackground() {
        return inspectUpdatePreflight();
      }

      @Override protected void done() {
        UpdatePreflight preflight = UpdatePreflight.empty();
        try {
          preflight = get();
        } catch (Exception ignored) {
          // reason: best-effort post-failure diagnosis; the raw failure message is still shown
        }
        if (preflight != null && preflight.hasInterruptedGitOperation()) {
          int choice = showUpdateDialog(
              "Update Engine Needs Recovery",
              "Git was left in an unfinished update state.",
              "Safe Mode can abort the unfinished Git operation, re-check the checkout, and let the user retry without staying stuck in a failure loop.\n\n"
                  + friendlyUpdateFailureSummary(output, preflight)
                  + "\n\n"
                  + preflight.summary(),
              new String[]{"Recover in Safe Mode", "Close"},
              UpdateDialogTone.WARNING);
          if (choice == 0) {
            setSafeModeEnabled(true);
            recoverSafeModeUpdateFailure(output);
          }
        } else {
          showUpdateFailureDialog("Update Engine Failed", output, preflight);
        }
      }
    }.execute();
  }

  private void recoverSafeModeUpdateFailure(String output) {
    if (runningProcess.get() != null) {
      appendLog("[hub] Safe Mode recovery is waiting for the running process to finish.");
      return;
    }
    setStatus("Safe Mode recovering update", ACCENT_SAFE);
    startSteps("Safe Mode Update Recovery");
    setActivity(
        "Recovering failed update",
        "Safe Mode is aborting unfinished Git operations and checking whether the checkout is stable.",
        true,
        ACCENT_SAFE);
    advanceStep("Aborting interrupted Git operation.");

    new SwingWorker<UpdateRecoveryResult, String>() {
      @Override protected UpdateRecoveryResult doInBackground() {
        CommandResult abort = abortInterruptedGitOperations();
        publish(abort.exitCode == 0
            ? "[hub] Safe Mode recovery: interrupted Git operations aborted."
            : "[hub] Safe Mode recovery: abort failed.");
        if (abort.exitCode != 0) {
          return new UpdateRecoveryResult(false, abort, UpdatePreflight.empty(), output);
        }
        UpdatePreflight preflight = inspectUpdatePreflight();
        return new UpdateRecoveryResult(true, abort, preflight, output);
      }

      @Override protected void process(List<String> chunks) {
        if (!chunks.isEmpty()) {
          appendLog(chunks.get(chunks.size() - 1));
        }
      }

      @Override protected void done() {
        UpdateRecoveryResult result;
        try {
          result = get();
        } catch (Exception ex) {
          result = new UpdateRecoveryResult(
              false,
              new CommandResult(-1, exceptionMessage(ex)),
              UpdatePreflight.empty(),
              output);
        }

        if (result.ok()) {
          advanceToStep(2, "Checkout state re-checked.");
          finishSteps(true, "Safe Mode returned Git to a stable checkout.");
          setStatus("Safe Mode recovered update", ACCENT_SAFE);
          setActivity(
              "Safe Mode recovered update",
              recoverySummary(result),
              false,
              ACCENT_SAFE);
          showSafeModeRecoveryDialog(result);
        } else {
          finishSteps(false, "Safe Mode could not recover the checkout automatically.");
          setStatus("Safe Mode recovery failed", ACCENT_ERROR);
          setActivity(
              "Safe Mode recovery failed",
              compactMessage(result.abortResult() != null ? result.abortResult().output : ""),
              false,
              ACCENT_ERROR);
          showManualRecoveryDialog(result);
        }
      }
    }.execute();
  }

  private void showSafeModeRecoveryDialog(UpdateRecoveryResult result) {
    UpdatePreflight preflight = result != null ? result.preflight() : UpdatePreflight.empty();
    String details = recoverySummary(result);
    if (preflight != null && preflight.statusUnavailable()) {
      details += "\n\nGit status check:\n" + preflight.statusError();
    } else if (preflight != null && (preflight.hasChanges() || preflight.hasInterruptedGitOperation())) {
      details += "\n\nCurrent checkout state:\n" + preflight.summary();
    }
    details += "\n\nOriginal failure:\n" + compactForDialog(result != null ? result.sourceOutput() : "");

    int choice = showUpdateDialog(
        "Safe Mode Recovery Complete",
        "The checkout is no longer stuck in an interrupted Git operation.",
        details,
        new String[]{"Retry Update", "Close"},
        UpdateDialogTone.QUESTION);
    if (choice == 0) {
      updateEngine();
    }
  }

  private void showUpdateFailureDialog(String title, String output, UpdatePreflight preflight) {
    String details = friendlyUpdateFailureSummary(output, preflight)
        + "\n\n"
        + (preflight != null && (preflight.hasChanges() || preflight.hasInterruptedGitOperation())
            ? "Current checkout state:\n" + preflight.summary() + "\n\n"
            : "")
        + "Git output:\n"
        + compactForDialog(output);
    showUpdateDialog(
        title,
        "Update Engine could not complete.",
        details,
        new String[]{"Close"},
        UpdateDialogTone.WARNING);
  }

  private void showManualRecoveryDialog(UpdateRecoveryResult result) {
    String output = result != null && result.abortResult() != null ? result.abortResult().output : "";
    String details = "Safe Mode could not abort the interrupted Git operation automatically.\n\n"
        + "Open a terminal in the engine folder and run:\n\n"
        + "git status\n"
        + "git rebase --abort\n"
        + "git merge --abort\n"
        + "git cherry-pick --abort\n"
        + "git revert --abort\n\n"
        + "Then retry Update Engine. If those abort commands report that no operation is in progress, send the git status output.\n\n"
        + "Git output:\n"
        + compactForDialog(output);
    showUpdateDialog(
        "Manual Git Recovery Needed",
        "The Hub could not safely recover the engine checkout.",
        details,
        new String[]{"Close"},
        UpdateDialogTone.DANGER);
  }

  private String recoverySummary(UpdateRecoveryResult result) {
    UpdatePreflight preflight = result != null ? result.preflight() : UpdatePreflight.empty();
    if (preflight.isEmpty()) {
      return "Safe Mode aborted any unfinished Git operation. Retry Update Engine when ready.";
    }
    if (preflight.statusUnavailable()) {
      return "Safe Mode aborted the unfinished Git operation, but Git status could not be checked.";
    }
    if (preflight.hasInterruptedGitOperation()) {
      return "Git still reports an interrupted operation after recovery.";
    }
    if (preflight.hasChanges()) {
      return "Git is no longer mid-update. Local changes are still present; Safe Mode will use autostash when retrying.";
    }
    return "Git is no longer mid-update and the checkout is clean. Retry Update Engine when ready.";
  }

  private String friendlyUpdateFailureSummary(String output, UpdatePreflight preflight) {
    if (preflight != null && preflight.hasInterruptedGitOperation()) {
      return "Git stopped during an update and left an unfinished operation in the checkout.";
    }
    String text = output == null ? "" : output.toLowerCase(Locale.ROOT);
    if (text.contains("conflict") || text.contains("resolve all conflicts")) {
      return "Git hit a content conflict while applying the engine update.";
    }
    if (text.contains("would be overwritten")) {
      return "Local files would be overwritten by the engine update.";
    }
    if (text.contains("no tracking information") || text.contains("no upstream")) {
      return "This checkout does not have an upstream branch configured.";
    }
    if (text.contains("could not resolve host") || text.contains("failed to connect")
        || text.contains("network is unreachable") || text.contains("timed out")) {
      return "Git could not reach the remote repository. This is likely a network problem.";
    }
    if (text.contains("permission denied") || text.contains("authentication failed")
        || text.contains("could not read from remote repository")) {
      return "Git could not authenticate with the remote repository.";
    }
    if (text.contains("index.lock") || text.contains("another git process")) {
      return "Another Git process or a stale Git lock is blocking the update.";
    }
    if (text.contains("not a git repository")) {
      return "The Hub is not running from a valid Git checkout.";
    }
    return "Git returned a failure while updating the engine checkout.";
  }

  private static String compactForDialog(String text) {
    if (text == null || text.isBlank()) return "(no Git output captured)";
    String trimmed = text.strip();
    int limit = 1800;
    if (trimmed.length() <= limit) return trimmed;
    return trimmed.substring(0, limit) + "\n... output truncated ...";
  }

  private static String exceptionMessage(Throwable ex) {
    if (ex == null) return "Unknown failure";
    String message = ex.getMessage();
    return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
  }

  private static boolean isGradleWrapperCommand(List<String> command) {
    if (command.isEmpty()) return false;
    Path executable = Path.of(command.get(0)).getFileName();
    if (executable == null) return false;
    String name = executable.toString();
    return name.equals("gradlew") || name.equals("gradlew.bat");
  }

  private static boolean isPackagedGradleHome(Path gradleHome) {
    return Files.isDirectory(gradleHome)
        && Files.isRegularFile(gradleHome.resolve(PACKAGED_GRADLE_CACHE_MARKER_FILE));
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
    if (shutdownInProgress) return;
    shutdownInProgress = true;
    HubLifecycleSplash splash = HubLifecycleSplash.shutdown(
        "Verifying tasks and engine state...");
    splash.showCentered();
    Process p = runningProcess.get();
    if (p != null) {
      appendLog("[hub] cancelling running task before exit...");
      setActivity("Cancelling task", "Closing the hub after the process stops.", true, ACCENT_ERROR);
      p.descendants().forEach(ProcessHandle::destroy);
      p.destroy();
    }
    if (spinnerTimer.isRunning()) spinnerTimer.stop();
    frame.setVisible(false);
    new Thread(() -> {
      if (p != null) {
        try {
          p.waitFor(650, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
      }
      SwingUtilities.invokeLater(() -> splash.completeShutdown(
          () -> {
            frame.dispose();
            System.exit(0);
          }));
    }, "jvn-hub-shutdown").start();
  }

  // --- Helpers ---------------------------------------------------------------

  private void setButtonsEnabled(boolean enabled) {
    SwingUtilities.invokeLater(() -> actionButtons.forEach(b -> b.setEnabled(enabled)));
  }

  private void setStatus(String text, Color color) {
    SwingUtilities.invokeLater(() -> {
      statusLabel.setText(text);
      statusLabel.setToolTipText(text);
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
      activityPanel.setTone(tone != null ? tone : ACCENT_NEUTRAL, spinning || runningProcess.get() != null);
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

  private void startSteps(String label) {
    List<String> steps = stepsForAction(label);
    SwingUtilities.invokeLater(() -> {
      activeStepLabel = label == null ? "" : label.trim();
      activeStepIndex = steps.isEmpty() ? -1 : 0;
      activitySteps.setSteps(steps);
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      updateActivityProgressFill();
    });
  }

  private void completeCurrentStep(String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void advanceStep(String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activeStepIndex >= 0) activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
      int next = Math.max(0, activeStepIndex + 1);
      if (next < activitySteps.stepCount()) {
        activeStepIndex = next;
        activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      }
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void advanceToStep(int targetIndex, String detail) {
    SwingUtilities.invokeLater(() -> {
      if (activitySteps.stepCount() == 0) return;
      int target = Math.max(0, Math.min(targetIndex, activitySteps.stepCount() - 1));
      if (activeStepIndex < 0) activeStepIndex = 0;
      if (target > activeStepIndex) {
        for (int i = activeStepIndex; i < target; i++) {
          activitySteps.setStatus(i, StepStatus.DONE);
        }
        activeStepIndex = target;
        activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
      }
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      updateActivityProgressFill();
    });
  }

  private void finishSteps(boolean success, String detail) {
    SwingUtilities.invokeLater(() -> {
      stopAutoStepTicker();
      int count = activitySteps.stepCount();
      if (count > 0) {
        for (int i = 0; i < count; i++) {
          StepStatus status = success ? StepStatus.DONE : (i == activeStepIndex ? StepStatus.FAILED : activitySteps.statusAt(i));
          if (!success && i > activeStepIndex && activitySteps.statusAt(i) == StepStatus.PENDING) {
            status = StepStatus.PENDING;
          }
          activitySteps.setStatus(i, status);
        }
      }
      activeStepIndex = -1;
      activeStepLabel = "";
      if (detail != null && !detail.isBlank()) activityDetail.setText(compactMessage(detail));
      activityPanel.setProgress(success ? 1.0 : Math.max(activityPanel.progress(), activitySteps.progressFraction()));
      activityPanel.setTone(success ? ACCENT_GREEN : ACCENT_ERROR, false);
    });
  }

  private void updateActivityProgressFill() {
    activityPanel.setProgress(activitySteps.progressFraction());
  }

  private List<String> stepsForAction(String label) {
    String key = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "run editor" -> List.of(
          "Read launch request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Apply safe-mode launch flags",
          "Start Gradle process",
          "Attach live output stream",
          "Configure project modules",
          "Check Java toolchain",
          "Resolve project dependencies",
          "Compile shared engine classes",
          "Build editor classpath",
          "Process editor resources",
          "Assemble runtime classpath",
          "Start JavaFX toolkit",
          "Load editor preferences",
          "Initialize workspace services",
          "Create editor window",
          "Wait for editor handoff",
          "Monitor editor process");
      case "run launcher" -> List.of(
          "Read launch request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Apply safe-mode launch flags",
          "Start Gradle process",
          "Attach live output stream",
          "Configure project modules",
          "Check Java toolchain",
          "Resolve project dependencies",
          "Compile shared engine classes",
          "Build launcher classpath",
          "Process launcher resources",
          "Assemble runtime classpath",
          "Start JavaFX toolkit",
          "Load launcher preferences",
          "Scan project workspace",
          "Create launcher window",
          "Wait for launcher handoff",
          "Monitor launcher process");
      case "build all" -> List.of(
          "Read build request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Start Gradle process",
          "Configure project modules",
          "Compile source sets",
          "Assemble module outputs",
          "Finalize build result");
      case "run tests" -> List.of(
          "Read test request",
          "Resolve engine workspace",
          "Locate Gradle wrapper",
          "Start Gradle process",
          "Configure project modules",
          "Compile test classes",
          "Run test suites",
          "Collect test reports");
      case "build shortcuts" -> List.of(
          "Detect operating system",
          "Find shortcut installer",
          "Prepare installer command",
          "Start installer process",
          "Create application entries",
          "Verify shortcut install");
      case "update engine" -> List.of(
          "Inspect local Git state",
          "Prepare update command",
          "Fetch upstream changes",
          "Rebase local checkout",
          "Refresh version metadata",
          "Reload engine state");
      case "clean before update" -> List.of(
          "Inspect local files",
          "Select safe clean command",
          "Restore tracked build output",
          "Remove generated files",
          "Confirm clean checkout");
      case "safe mode update recovery" -> List.of(
          "Inspect failed update",
          "Abort interrupted Git operation",
          "Re-check checkout state",
          "Present recovery options");
      default -> List.of(
          "Read action request",
          "Resolve engine workspace",
          "Prepare command",
          "Start background process",
          "Read process output",
          "Finalize result");
    };
  }

  private void updateStepsFromOutput(String label, String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return;
    String line = rawLine.trim();
    String lower = line.toLowerCase(Locale.ROOT);
    String key = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);

    if (lower.contains("preparing process environment")) {
      advanceToStep(stepIndexFor(key, "prepare_environment"), "Preparing command environment.");
      return;
    }
    if (lower.contains("process started")) {
      advanceToStep(stepIndexFor(key, "process_started"), "Background process started.");
      return;
    }
    if (lower.contains("starting a gradle daemon") || lower.contains("daemon")) {
      advanceToStep(stepIndexFor(key, "process_started"), "Starting Gradle daemon.");
      return;
    }
    if (lower.contains("configure project") || lower.contains("calculating task graph")) {
      advanceToStep(stepIndexFor(key, "configure"), "Configuring Gradle project modules.");
      return;
    }
    if (lower.startsWith("> task")) {
      handleGradleTaskStep(key, lower, line);
      return;
    }
    if (lower.contains("build successful") || lower.contains("build failed")) {
      advanceToStep(99, line);
      return;
    }
    if (key.equals("update engine")) {
      if (lower.contains("fetch") || lower.contains("from ")) {
        advanceToStep(2, "Fetching upstream changes.");
      } else if (lower.contains("rebas") || lower.contains("updating") || lower.contains("fast-forward")) {
        advanceToStep(3, "Applying engine update.");
      }
    } else if (key.equals("build shortcuts")) {
      if (lower.contains("install") || lower.contains("shortcut") || lower.contains("application")) {
        advanceToStep(4, "Creating OS shortcuts.");
      }
    } else if (key.equals("clean before update")) {
      if (lower.contains("restore")) {
        advanceToStep(2, "Restoring tracked build output.");
      } else if (lower.contains("clean") || lower.contains("remov")) {
        advanceToStep(3, "Removing generated files.");
      }
    }
  }

  private void handleGradleTaskStep(String key, String lowerTaskLine, String originalLine) {
    if (lowerTaskLine.contains("compiletest") || lowerTaskLine.contains(":testclasses")) {
      advanceToStep(stepIndexFor(key, "test_compile"), originalLine);
      return;
    }
    if (lowerTaskLine.matches(".*:test(\\s|$).*")) {
      advanceToStep(stepIndexFor(key, "test_run"), originalLine);
      return;
    }
    if (isEditorUiLaunch(key) && lowerTaskLine.contains(":editor:compile")) {
      advanceToStep(stepIndexFor(key, "app_compile"), originalLine);
      return;
    }
    if (isEditorUiLaunch(key) && lowerTaskLine.contains("processresources")) {
      advanceToStep(stepIndexFor(key, "resources"), originalLine);
      return;
    }
    if (lowerTaskLine.contains("compile")) {
      advanceToStep(stepIndexFor(key, "compile"), originalLine);
      return;
    }
    if (lowerTaskLine.contains(":classes") || lowerTaskLine.contains(":jar")) {
      advanceToStep(stepIndexFor(key, "classpath"), originalLine);
      return;
    }
    if (lowerTaskLine.contains(":editor:runlauncher")) {
      advanceToStep(stepIndexFor(key, "app_start"), "Starting standalone launcher.");
      return;
    }
    if (lowerTaskLine.contains(":editor:run")) {
      advanceToStep(stepIndexFor(key, "app_start"), originalLine);
      return;
    }
    if (lowerTaskLine.contains("assemble") || lowerTaskLine.contains("build")) {
      advanceToStep(key.equals("build all") ? 6 : 5, originalLine);
    }
  }

  private int stepIndexFor(String key, String milestone) {
    boolean launch = isEditorUiLaunch(key);
    if (launch) {
      return switch (milestone) {
        case "prepare_environment" -> 3;
        case "process_started" -> 4;
        case "configure" -> 6;
        case "compile" -> 9;
        case "app_compile" -> 10;
        case "resources" -> 11;
        case "classpath" -> 12;
        case "app_start" -> 13;
        default -> activeStepIndex;
      };
    }
    return switch (milestone) {
      case "prepare_environment" -> 2;
      case "process_started" -> 3;
      case "configure" -> 4;
      case "compile", "test_compile" -> 5;
      case "test_run" -> 6;
      case "classpath", "resources", "app_compile", "app_start" -> 5;
      default -> activeStepIndex;
    };
  }

  private static boolean isEditorUiLaunch(String key) {
    return "run editor".equals(key) || "run launcher".equals(key) || "help center".equals(key);
  }

  private void startAutoStepTicker(String label) {
    SwingUtilities.invokeLater(() -> {
      activeStepLabel = label == null ? "" : label.trim();
      autoStepTimer.setInitialDelay(1200);
      if (!autoStepTimer.isRunning()) autoStepTimer.start();
    });
  }

  private void stopAutoStepTicker() {
    if (autoStepTimer.isRunning()) autoStepTimer.stop();
  }

  private void autoAdvanceDuringSilence() {
    if (activeStepIndex < 0 || activitySteps.stepCount() == 0) {
      stopAutoStepTicker();
      return;
    }
    String key = activeStepLabel == null ? "" : activeStepLabel.trim().toLowerCase(Locale.ROOT);
    int limit = autoAdvanceLimit(key);
    if (activeStepIndex >= limit) {
      stopAutoStepTicker();
      return;
    }
    int next = activeStepIndex + 1;
    activitySteps.setStatus(activeStepIndex, StepStatus.DONE);
    activeStepIndex = next;
    activitySteps.setStatus(activeStepIndex, StepStatus.RUNNING);
    activityDetail.setText(compactMessage(activitySteps.stepAt(activeStepIndex)));
    updateActivityProgressFill();
  }

  private int autoAdvanceLimit(String key) {
    int last = Math.max(0, activitySteps.stepCount() - 1);
    if ("run editor".equals(key) || "run launcher".equals(key)) {
      return Math.max(0, last - 1);
    }
    if ("build all".equals(key) || "run tests".equals(key)) {
      return Math.max(0, last - 1);
    }
    if ("build shortcuts".equals(key) || "update engine".equals(key)) {
      return Math.max(0, last - 1);
    }
    return Math.max(0, last - 1);
  }

  private void checkIncomingUpdates(boolean fetchFirst) {
    if (updateEngineButton == null) return;
    if (!Files.isDirectory(projectRoot.resolve(".git"))) {
      lastKnownIncoming = -1;
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!commandExists("git")) {
      lastKnownIncoming = -1;
      updateEngineButton.setIncomingCount(-1);
      return;
    }
    if (!updateCheckRunning.compareAndSet(false, true)) return;

    updateEngineButton.setChecking(true);
    new SwingWorker<Integer, Void>() {
      @Override protected Integer doInBackground() {
        if (fetchFirst) {
          runGit(List.of("git", "fetch", "--quiet", "--prune", "--no-tags", ENGINE_UPDATE_REMOTE, ENGINE_UPDATE_FETCH_REFSPEC), 45);
        }
        CommandResult result = runGit(List.of("git", "rev-list", "--count", "HEAD.." + ENGINE_UPDATE_REMOTE_REF), 10);
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
            // reason: non-critical operation; exception swallowed to prevent crash propagation
          // Keep the badge hidden when Git cannot report an upstream count.
        }
        lastKnownIncoming = count;
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
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
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

  private List<String> inspectInterruptedGitOperations() {
    List<String> operations = new ArrayList<>();
    if (gitPathExists("rebase-merge") || gitPathExists("rebase-apply")) {
      operations.add("rebase in progress");
    }
    if (gitPathExists("MERGE_HEAD")) {
      operations.add("merge in progress");
    }
    if (gitPathExists("CHERRY_PICK_HEAD")) {
      operations.add("cherry-pick in progress");
    }
    if (gitPathExists("REVERT_HEAD")) {
      operations.add("revert in progress");
    }
    return List.copyOf(operations);
  }

  private boolean gitPathExists(String gitPath) {
    if (gitPath == null || gitPath.isBlank()) return false;
    CommandResult result = runGit(List.of("git", "rev-parse", "--git-path", gitPath), 5);
    if (result.exitCode != 0 || result.output.isBlank()) return false;
    String firstLine = result.output.split("\\R", 2)[0].trim();
    if (firstLine.isBlank()) return false;
    Path path = Paths.get(firstLine);
    if (!path.isAbsolute()) path = projectRoot.resolve(path);
    return Files.exists(path);
  }

  private static List<GitStatusEntry> parseGitStatus(String output) {
    if (output == null || output.isBlank()) return List.of();
    List<GitStatusEntry> entries = new ArrayList<>();
    for (String line : output.split("\\R")) {
      if (line.length() < 4) continue;
      String code = line.substring(0, 2);
      String path = line.substring(3).trim();
      int renameArrow = path.indexOf(" -> ");
      if (renameArrow >= 0) {
        path = path.substring(renameArrow + 4).trim();
      }
      if (!path.isBlank()) entries.add(new GitStatusEntry(code, path));
    }
    return entries;
  }

  private static boolean isBuildOutputPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return false;
    String path = rawPath.replace('\\', '/');
    return path.equals("build")
        || path.startsWith("build/")
        || path.contains("/build/")
        || path.endsWith("/build")
        || path.equals("bin")
        || path.startsWith("bin/")
        || path.contains("/bin/")
        || path.endsWith("/bin")
        || path.equals("out")
        || path.startsWith("out/")
        || path.contains("/out/")
        || path.endsWith("/out")
        || path.equals(".gradle")
        || path.startsWith(".gradle/")
        || path.contains("/.gradle/")
        || path.equals(".jvn-gradle-user-home")
        || path.startsWith(".jvn-gradle-user-home/");
  }

  private static String summarizeEntries(List<GitStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return "No changed files.";
    StringBuilder out = new StringBuilder();
    int limit = Math.min(entries.size(), 12);
    for (int i = 0; i < limit; i++) {
      GitStatusEntry entry = entries.get(i);
      out.append(entry.code().trim().isBlank() ? "changed" : entry.code().trim())
          .append("  ")
          .append(entry.path())
          .append('\n');
    }
    if (entries.size() > limit) {
      out.append("... and ").append(entries.size() - limit).append(" more.\n");
    }
    return out.toString().trim();
  }

  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private record CommandResult(int exitCode, String output) {}

  private record UpdateRecoveryResult(
      boolean ok,
      CommandResult abortResult,
      UpdatePreflight preflight,
      String sourceOutput) {}

  private enum UpdatePreflightAction {
    CLEAN_AND_UPDATE,
    CANCEL
  }

  private record GitStatusEntry(String code, String path) {
    boolean untracked() {
      return "??".equals(code);
    }

    boolean trackedChange() {
      return !untracked() && !"!!".equals(code);
    }
  }

  private record UpdatePreflight(
      List<GitStatusEntry> allChanges,
      List<GitStatusEntry> buildOutputChanges,
      List<GitStatusEntry> otherChanges,
      List<String> interruptedGitOperations,
      String statusError) {

    static UpdatePreflight unavailable(String error) {
      return new UpdatePreflight(List.of(), List.of(), List.of(), List.of(), error == null ? "" : error);
    }

    static UpdatePreflight empty() {
      return new UpdatePreflight(List.of(), List.of(), List.of(), List.of(), "");
    }

    static UpdatePreflight from(List<GitStatusEntry> entries) {
      return from(entries, List.of());
    }

    static UpdatePreflight from(List<GitStatusEntry> entries, List<String> interruptedGitOperations) {
      List<GitStatusEntry> build = new ArrayList<>();
      List<GitStatusEntry> other = new ArrayList<>();
      for (GitStatusEntry entry : entries) {
        if (isBuildOutputPath(entry.path())) {
          build.add(entry);
        } else {
          other.add(entry);
        }
      }
      return new UpdatePreflight(
          List.copyOf(entries),
          List.copyOf(build),
          List.copyOf(other),
          interruptedGitOperations == null ? List.of() : List.copyOf(interruptedGitOperations),
          "");
    }

    boolean statusUnavailable() {
      return statusError != null && !statusError.isBlank();
    }

    boolean isEmpty() {
      return !statusUnavailable() && !hasChanges() && !hasInterruptedGitOperation();
    }

    boolean hasChanges() {
      return !allChanges.isEmpty();
    }

    boolean hasInterruptedGitOperation() {
      return interruptedGitOperations != null && !interruptedGitOperations.isEmpty();
    }

    boolean onlyBuildOutput() {
      return hasChanges() && !buildOutputChanges.isEmpty() && otherChanges.isEmpty();
    }

    String summary() {
      StringBuilder out = new StringBuilder();
      if (hasInterruptedGitOperation()) {
        out.append("Interrupted Git state:\n");
        for (String operation : interruptedGitOperations) {
          out.append("- ").append(operation).append('\n');
        }
      }
      if (onlyBuildOutput()) {
        if (out.length() > 0) out.append('\n');
        out.append("Build output:\n").append(summarizeEntries(buildOutputChanges));
        return out.toString();
      }
      if (!otherChanges.isEmpty()) {
        if (out.length() > 0) out.append('\n');
        out.append("Other local changes:\n").append(summarizeEntries(otherChanges));
      }
      if (!buildOutputChanges.isEmpty()) {
        if (out.length() > 0) out.append("\n\n");
        out.append("Build output:\n").append(summarizeEntries(buildOutputChanges));
      }
      return out.length() == 0 ? "No changed files." : out.toString();
    }
  }

  private static String compactMessage(String text) {
    if (text == null) return "";
    String compact = text.replaceAll("\\s+", " ").trim();
    if (compact.startsWith("[hub] ")) compact = compact.substring(6);
    if (compact.length() > 96) {
      compact = compact.substring(0, 93) + "...";
    }
    return compact;
  }

  private static void rememberProcessOutput(List<String> recentOutput, String line) {
    if (recentOutput == null || line == null || line.isBlank()) return;
    while (recentOutput.size() >= PROCESS_OUTPUT_TAIL_LINES) {
      recentOutput.remove(0);
    }
    recentOutput.add(line.trim());
  }

  private static String summarizeProcessFailure(String outputPrefix, List<String> recentOutput, String fallback) {
    String combined = ((outputPrefix == null ? "" : outputPrefix) + "\n"
        + (recentOutput == null ? "" : String.join("\n", recentOutput))).strip();
    String friendly = friendlyProcessFailure(combined);
    if (!friendly.isBlank()) return friendly;

    List<String> candidates = new ArrayList<>();
    if (outputPrefix != null && !outputPrefix.isBlank()) {
      candidates.addAll(List.of(outputPrefix.split("\\R")));
    }
    if (recentOutput != null) candidates.addAll(recentOutput);

    for (int i = candidates.size() - 1; i >= 0; i--) {
      String line = candidates.get(i);
      if (isActionableFailureLine(line)) return compactMessage(line);
    }
    if (fallback != null && !fallback.isBlank() && !isProcessNoiseLine(fallback)) {
      return compactMessage(fallback);
    }
    return "";
  }

  private static String friendlyProcessFailure(String output) {
    if (output == null || output.isBlank()) return "";
    String lower = output.toLowerCase(Locale.ROOT);
    if (lower.contains("javafx runtime components are missing")) {
      return "JavaFX runtime components are missing from the launch classpath.";
    }
    if (lower.contains("unable to open display")) {
      return "JavaFX could not open a desktop display for this session.";
    }
    if (lower.contains("incompatible architecture") && lower.contains("javafx")) {
      return "JavaFX native libraries do not match this CPU architecture.";
    }
    if (lower.contains("glass gtk") || lower.contains("glassgtk") || lower.contains("libgtk")
        || (lower.contains("gtk") && lower.contains("javafx"))) {
      return "JavaFX GTK native support is missing or cannot load on this Linux desktop.";
    }
    if (lower.contains("x11") && (lower.contains("javafx") || lower.contains("glass"))) {
      return "JavaFX could not connect to the Linux X11 desktop session.";
    }
    if (lower.contains("graphics device initialization failed") || lower.contains("quantumrenderer")) {
      return "JavaFX graphics pipeline failed to initialize.";
    }
    if (lower.contains("no toolkit found")) {
      return "JavaFX toolkit native runtime was not available.";
    }
    return "";
  }

  private static boolean isActionableFailureLine(String line) {
    if (line == null || line.isBlank() || isProcessNoiseLine(line)) return false;
    String lower = line.toLowerCase(Locale.ROOT);
    return lower.contains("exception")
        || lower.contains("caused by:")
        || lower.contains("error")
        || lower.contains("failed")
        || lower.contains("could not")
        || lower.contains("unable to")
        || lower.contains("unsupportedclassversion")
        || lower.contains("java.lang.")
        || lower.contains("com.sun.glass")
        || lower.contains("graphics device initialization");
  }

  private static boolean isProcessNoiseLine(String line) {
    if (line == null) return true;
    String lower = line.trim().toLowerCase(Locale.ROOT);
    return lower.isBlank()
        || lower.startsWith("> task ")
        || lower.startsWith("reusing configuration cache")
        || lower.startsWith("configuration cache entry")
        || lower.startsWith("calculating task graph")
        || lower.startsWith("build failed")
        || lower.startsWith("build successful")
        || lower.startsWith("deprecated gradle features were used");
  }

  private String gradleCommand() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String name = os.contains("win") ? "gradlew.bat" : "gradlew";
    Path wrapper = projectRoot.resolve(name);
    return wrapper.toAbsolutePath().toString();
  }

  private String windowsPowerShellCommand() {
    if (commandExists("pwsh")) return "pwsh";
    for (String envName : List.of("SystemRoot", "WINDIR")) {
      String root = System.getenv(envName);
      if (root == null || root.isBlank()) continue;
      Path candidate = Paths.get(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
      if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().toString();
    }
    return "powershell.exe";
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

  private static List<String> splitExtraGradleArgs(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (escaping) {
        current.append(ch);
        escaping = false;
        continue;
      }
      if (ch == '\\' && !inSingle) {
        escaping = true;
        continue;
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }
    if (escaping) current.append('\\');
    if (current.length() > 0) out.add(current.toString());
    return out;
  }

  private int readRequiredJavaVersion() {
    String raw = readGradleProperty("javaVersion");
    if (raw == null || raw.isBlank()) return -1;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private String readGradleProperty(String key) {
    if (key == null || key.isBlank()) return null;
    Path props = projectRoot.resolve("gradle.properties");
    if (!Files.isRegularFile(props)) return null;
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(props)) {
      p.load(in);
      return p.getProperty(key);
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
      return null;
    }
  }

  private int readIncomingCommitCount() {
    if (!commandExists("git") || !Files.isDirectory(projectRoot.resolve(".git"))) return -1;
    CommandResult result = runGit(List.of("git", "rev-list", "--count", "HEAD.." + ENGINE_UPDATE_REMOTE_REF), 8);
    if (result.exitCode != 0) return -1;
    try {
      return Math.max(0, Integer.parseInt(result.output.strip()));
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private String readGitValue(List<String> command, String fallback) {
    if (!commandExists("git") || !Files.isDirectory(projectRoot.resolve(".git"))) return fallback;
    CommandResult result = runGit(command, 5);
    if (result.exitCode != 0) return fallback;
    String value = result.output.strip();
    return value.isBlank() ? fallback : value;
  }

  private static int parseJavaMajor(String version) {
    if (version == null || version.isBlank()) return -1;
    String v = version.trim();
    try {
      if (v.startsWith("1.")) {
        int dot = v.indexOf('.', 2);
        return Integer.parseInt(dot > 0 ? v.substring(2, dot) : v.substring(2));
      }
      int end = 0;
      while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
      return end == 0 ? -1 : Integer.parseInt(v.substring(0, end));
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return -1;
    }
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private enum CheckStatus {
    PASS(ACCENT_GREEN, VectorIcon.Kind.CHECK),
    WARN(ACCENT_NEUTRAL, VectorIcon.Kind.INFO),
    FAIL(ACCENT_ERROR, VectorIcon.Kind.CLOSE),
    INFO(TEXT_MUTED, VectorIcon.Kind.INFO);

    private final Color color;
    private final VectorIcon.Kind icon;

    CheckStatus(Color color, VectorIcon.Kind icon) {
      this.color = color;
      this.icon = icon;
    }

    Color color() { return color; }
    VectorIcon.Kind icon() { return icon; }
  }

  private record HealthCheck(CheckStatus status, String title, String summary, String details) {}

  private static String readVersion() {
    try (InputStream in = JvnHub.class.getResourceAsStream("/com/jvn/hub/version.properties")) {
      if (in != null) {
        Properties props = new Properties();
        props.load(in);
        String v = props.getProperty("version");
        if (v != null && !v.isBlank()) return v.trim();
      }
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
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
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        // fall through
      }
    }
    return VERSION;
  }

  static String formatVersionLabel(String rawVersion) {
    String version = displayVersionLabel(rawVersion);
    if (!isRunningFromSource()) return version;
    return "<html>" + version
        + "<br><span style='color:#ff9933;font-size:9px;font-weight:normal'>Running from source</span></html>";
  }

  private static String displayVersionLabel(String rawVersion) {
    String raw = rawVersion == null ? "" : rawVersion.trim();
    if (raw.isBlank() || raw.equalsIgnoreCase("dev") || raw.equalsIgnoreCase("vdev")) {
      return "v0.4.2";
    }

    String version = raw.startsWith("v") || raw.startsWith("V") ? raw.substring(1) : raw;
    String lower = version.toLowerCase(Locale.ROOT);
    String maturity = null;
    if (lower.contains("alpha") || lower.contains("snapshot") || lower.contains("dev")) {
      maturity = "Alpha";
    } else if (lower.contains("beta")) {
      maturity = "Beta";
    } else if (lower.contains("rc")) {
      maturity = "RC";
    }

    int suffix = version.indexOf('-');
    if (suffix >= 0) version = version.substring(0, suffix);
    int plus = version.indexOf('+');
    if (plus >= 0) version = version.substring(0, plus);
    version = version.trim();
    if (version.isBlank()) version = "0.4.2";
    return "v" + version + (maturity == null ? "" : " " + maturity);
  }

  private static boolean isRunningFromSource() {
    String override = System.getProperty("jvn.runningFromSource");
    if (override != null && !override.isBlank()) {
      String normalized = override.trim().toLowerCase(Locale.ROOT);
      return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }
    try {
      CodeSource source = JvnHub.class.getProtectionDomain().getCodeSource();
      if (source == null || source.getLocation() == null) return false;
      Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
      if (Files.isDirectory(location)) return true;
      String path = location.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
      return path.contains("/build/classes/") || path.contains("/out/production/");
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return false;
    }
  }

  /**
   * Re-reads version and launcher-maintenance state from disk. Safe to call
   * from any thread; UI updates are dispatched to the EDT.
   */
  private void refreshFromDisk() {
    String newVersion = readDiskVersion();
    LauncherMaintenanceState freshLauncherMaintenance = loadLauncherMaintenanceState();
    SwingUtilities.invokeLater(() -> {
      versionLabel.setText(formatVersionLabel(newVersion));
      boolean maintenanceChanged = launcherMaintenanceState.underMaintenance()
          != freshLauncherMaintenance.underMaintenance();
      launcherMaintenanceState = freshLauncherMaintenance;
      if (runLauncherButton instanceof LauncherButton launcherButton) {
        launcherButton.setMaintenanceState(freshLauncherMaintenance);
      }
      appendLog("[hub] refresh: version " + newVersion
          + ". Launcher maintenance: "
          + (freshLauncherMaintenance.underMaintenance() ? "on" : "off")
          + (maintenanceChanged ? " (changed)" : "") + ".");
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
  //  Nested UI primitives: flat neutral button, vector icons, scrollbar UI.
  // ===========================================================================

  /** Lightweight Swing panel for the hub's charcoal launcher-style surface. */
  private static final class GradientPanel extends JPanel {
    private final Color top;
    private final Color bottom;

    GradientPanel(LayoutManager layout, Color top, Color bottom) {
      super(layout);
      this.top = top;
      this.bottom = bottom;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, getHeight()),
          new float[] {0f, 1f},
          new Color[] {top, bottom}));
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.dispose();
      super.paintComponent(g);
    }
  }

  private static Color lighten(Color color, float amount) {
    float t = Math.max(0f, Math.min(1f, amount));
    int r = Math.round(color.getRed() + (255 - color.getRed()) * t);
    int g = Math.round(color.getGreen() + (255 - color.getGreen()) * t);
    int b = Math.round(color.getBlue() + (255 - color.getBlue()) * t);
    return new Color(r, g, b, color.getAlpha());
  }

  private static Color alpha(Color color, float alpha) {
    return new Color(
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
  }

  private enum StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
  }

  /** Paints launch progress behind the activity header and auto-scrolling step list. */
  private static final class ActivityProgressPanel extends JPanel {
    private double progress = 0.0;
    private Color tone = ACCENT_NEUTRAL;
    private boolean active = false;

    ActivityProgressPanel(LayoutManager layout) {
      super(layout);
      setOpaque(true);
    }

    void setProgress(double value) {
      double next = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
      if (Math.abs(progress - next) < 0.001) return;
      progress = next;
      repaint();
    }

    double progress() {
      return progress;
    }

    void setTone(Color color, boolean active) {
      tone = color == null ? ACCENT_NEUTRAL : color;
      this.active = active;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (progress <= 0.001 || getWidth() <= 0 || getHeight() <= 0) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Shape oldClip = g2.getClip();
      RoundRectangle2D clip = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ui(14), ui(14));
      g2.setClip(clip);

      int fillWidth = Math.max(1, (int) Math.round(getWidth() * progress));
      float alpha = active ? 0.22f : 0.16f;
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          Math.max(1f, fillWidth),
          0f,
          new float[] {0f, 1f},
          new Color[] {withAlpha(tone, alpha), withAlpha(tone, Math.max(0.08f, alpha * 0.58f))}));
      g2.fillRect(0, 0, fillWidth, getHeight());

      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, getHeight()),
          new float[] {0f, 0.55f, 1f},
          new Color[] {
              withAlpha(Color.WHITE, active ? 0.045f : 0.025f),
              withAlpha(Color.WHITE, 0.0f),
              withAlpha(Color.BLACK, 0.10f)
          }));
      g2.fillRect(0, 0, fillWidth, getHeight());

      g2.setClip(oldClip);
      g2.dispose();
    }

    private static Color withAlpha(Color color, float alpha) {
      if (color == null) color = ACCENT_NEUTRAL;
      return new Color(
          color.getRed(),
          color.getGreen(),
          color.getBlue(),
          Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /** Checklist-style progress surface for long hub actions. */
  private static final class StepListPanel extends JComponent {
    private static final int ROW_HEIGHT = ui(22);
    private static final int TOP_BLUR_BAND = ui(46);

    private final List<String> steps = new ArrayList<>();
    private final List<StepStatus> statuses = new ArrayList<>();
    private final javax.swing.Timer scrollTimer;
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;

    StepListPanel() {
      setOpaque(false);
      setPreferredSize(uiDimension(0, 62));
      setMinimumSize(uiDimension(0, 62));
      scrollTimer = new javax.swing.Timer(16, e -> animateScroll());
    }

    void setSteps(List<String> newSteps) {
      steps.clear();
      statuses.clear();
      scrollOffset = 0.0;
      targetScrollOffset = 0.0;
      if (scrollTimer.isRunning()) scrollTimer.stop();
      if (newSteps != null) {
        for (String step : newSteps) {
          if (step == null || step.isBlank()) continue;
          steps.add(step.trim());
          statuses.add(StepStatus.PENDING);
        }
      }
      repaint();
    }

    int stepCount() {
      return steps.size();
    }

    StepStatus statusAt(int index) {
      if (index < 0 || index >= statuses.size()) return StepStatus.PENDING;
      return statuses.get(index);
    }

    String stepAt(int index) {
      if (index < 0 || index >= steps.size()) return "";
      return steps.get(index);
    }

    void setStatus(int index, StepStatus status) {
      if (index < 0 || index >= statuses.size()) return;
      statuses.set(index, status == null ? StepStatus.PENDING : status);
      retargetScroll();
      repaint();
    }

    double progressFraction() {
      if (statuses.isEmpty()) return 0.0;
      double units = 0.0;
      for (StepStatus status : statuses) {
        if (status == StepStatus.DONE || status == StepStatus.FAILED) {
          units += 1.0;
        } else if (status == StepStatus.RUNNING) {
          units += 0.55;
        }
      }
      return Math.max(0.0, Math.min(1.0, units / statuses.size()));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      Shape oldClip = g2.getClip();
      g2.clipRect(0, 0, getWidth(), getHeight());

      Font stepFont = getFont().deriveFont(Font.PLAIN, uiFont(10f));
      g2.setFont(stepFont);
      FontMetrics fm = g2.getFontMetrics();

      BufferedImage blurredDoneLayer = null;
      Graphics2D blurG = null;
      if (getWidth() > 0 && getHeight() > 0) {
        blurredDoneLayer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        blurG = blurredDoneLayer.createGraphics();
        blurG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        blurG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        blurG.setFont(stepFont);
        blurG.clipRect(0, 0, getWidth(), Math.min(getHeight(), TOP_BLUR_BAND));
      }

      for (int i = 0; i < steps.size(); i++) {
        StepStatus status = statuses.get(i);
        int yTop = (int) Math.round(i * ROW_HEIGHT - scrollOffset);
        int yCenter = yTop + ROW_HEIGHT / 2;
        if (yTop > getHeight() + ROW_HEIGHT || yTop + ROW_HEIGHT < -ROW_HEIGHT) continue;

        boolean blurCompleted = status == StepStatus.DONE && yTop < TOP_BLUR_BAND;
        if (blurCompleted && blurG != null) {
          drawStepRow(blurG, i, yCenter, fm, 0.68f, true);
        } else {
          drawStepRow(g2, i, yCenter, fm, 1.0f, false);
        }
      }

      if (blurG != null) {
        blurG.dispose();
        BufferedImage softened = soften(blurredDoneLayer);
        g2.drawImage(softened, 0, 0, null);
        paintTopVeil(g2);
      }
      g2.setClip(oldClip);
      g2.dispose();
    }

    private void drawStepRow(Graphics2D g2, int index, int y, FontMetrics fm, float alpha, boolean softened) {
      StepStatus status = statuses.get(index);
      Color color = switch (status) {
        case DONE -> ACCENT_GREEN;
        case RUNNING -> ACCENT_NEUTRAL;
        case FAILED -> ACCENT_ERROR;
        case PENDING -> TEXT_MUTED;
      };

      int cx = ui(8);
      int r = ui(4);
      g2.setColor(withAlpha(status == StepStatus.PENDING ? BORDER_NEUTRAL : color, alpha));
      g2.setStroke(new BasicStroke(uiStroke(1.2f)));
      g2.drawOval(cx - r, y - r, r * 2, r * 2);
      if (index < steps.size() - 1) {
        g2.setColor(withAlpha(BORDER_NEUTRAL, softened ? 0.24f : 0.75f));
        g2.drawLine(cx, y + r + ui(2), cx, y + ROW_HEIGHT - r - ui(2));
      }

      if (status == StepStatus.DONE) {
        g2.setColor(withAlpha(ACCENT_GREEN, alpha));
        g2.setStroke(new BasicStroke(uiStroke(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(cx - ui(5), y);
        check.lineTo(cx - ui(1), y + ui(4));
        check.lineTo(cx + ui(6), y - ui(5));
        g2.draw(check);
      } else if (status == StepStatus.RUNNING) {
        g2.setColor(withAlpha(ACCENT_NEUTRAL, alpha));
        g2.fillOval(cx - ui(2), y - ui(2), ui(4), ui(4));
      } else if (status == StepStatus.FAILED) {
        g2.setColor(withAlpha(ACCENT_ERROR, alpha));
        g2.setStroke(new BasicStroke(uiStroke(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - ui(4), y - ui(4), cx + ui(4), y + ui(4));
        g2.drawLine(cx + ui(4), y - ui(4), cx - ui(4), y + ui(4));
      }

      g2.setColor(withAlpha(status == StepStatus.PENDING ? TEXT_MUTED : TEXT_SOFT, softened ? 0.54f : alpha));
      String text = steps.get(index);
      int textX = ui(22);
      int maxW = Math.max(ui(20), getWidth() - textX - ui(4));
      while (fm.stringWidth(text) > maxW && text.length() > 4) {
        text = text.substring(0, text.length() - 4) + "...";
      }
      g2.drawString(text, textX, y + (fm.getAscent() - fm.getDescent()) / 2 - ui(1));
    }

    private void paintTopVeil(Graphics2D g2) {
      int band = Math.min(getHeight(), TOP_BLUR_BAND);
      if (band <= 0) return;
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          band,
          new float[] {0f, 1f},
          new Color[] {withAlpha(PANEL_BG, 0.38f), withAlpha(PANEL_BG, 0.02f)}));
      g2.fillRect(0, 0, getWidth(), band);
    }

    private void retargetScroll() {
      int focus = focusedStepIndex();
      int visibleRows = Math.max(1, getHeight() / ROW_HEIGHT);
      double desired = Math.max(0, (focus - Math.max(1, visibleRows - 2)) * ROW_HEIGHT);
      double max = Math.max(0, steps.size() * ROW_HEIGHT - getHeight());
      targetScrollOffset = Math.max(0, Math.min(max, desired));
      if (!scrollTimer.isRunning()) scrollTimer.start();
    }

    private int focusedStepIndex() {
      for (int i = 0; i < statuses.size(); i++) {
        if (statuses.get(i) == StepStatus.RUNNING || statuses.get(i) == StepStatus.FAILED) return i;
      }
      for (int i = 0; i < statuses.size(); i++) {
        if (statuses.get(i) == StepStatus.PENDING) return i;
      }
      return Math.max(0, statuses.size() - 1);
    }

    private void animateScroll() {
      double delta = targetScrollOffset - scrollOffset;
      if (Math.abs(delta) < 0.35) {
        scrollOffset = targetScrollOffset;
        scrollTimer.stop();
      } else {
        scrollOffset += delta * 0.18;
      }
      repaint();
    }

    private static BufferedImage soften(BufferedImage source) {
      if (source == null) return null;
      float ninth = 1f / 9f;
      float[] kernel = {
          ninth, ninth, ninth,
          ninth, ninth, ninth,
          ninth, ninth, ninth
      };
      return new java.awt.image.ConvolveOp(
          new java.awt.image.Kernel(3, 3, kernel),
          java.awt.image.ConvolveOp.EDGE_NO_OP,
          null).filter(source, null);
    }

    private static Color withAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /** Compact indeterminate spinner used in place of terminal-style output. */
  private static final class ActivitySpinner extends JComponent {
    private int frame = 0;
    private boolean active = false;

    ActivitySpinner() {
      setOpaque(false);
      Dimension size = uiDimension(24, 24);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
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
      float shellPad = Math.max(3f, Math.min(w, h) * 0.16f);

      g2.setColor(active ? Color.decode("#1f1f1f") : PANEL_BG);
      g2.fillOval(Math.round(cx - radius - shellPad), Math.round(cy - radius - shellPad),
          Math.round((radius + shellPad) * 2), Math.round((radius + shellPad) * 2));

      for (int i = 0; i < 12; i++) {
        int age = active ? Math.floorMod(i - frame, 12) : i;
        float alpha = active ? (0.22f + (11 - age) * 0.065f) : 0.16f;
        double angle = (Math.PI * 2.0 * i / 12.0) - Math.PI / 2.0;
        float x = cx + (float) Math.cos(angle) * radius;
        float y = cy + (float) Math.sin(angle) * radius;
        int dot = Math.max(active && age == 0 ? ui(4) : ui(3), Math.round(Math.min(w, h) * (active && age == 0 ? 0.16f : 0.12f)));
        g2.setColor(withAlpha(active ? ACCENT_NEUTRAL : TEXT_MUTED, Math.min(1.0f, alpha)));
        g2.fillOval(Math.round(x - dot / 2f), Math.round(y - dot / 2f), dot, dot);
      }

      if (!active) {
        g2.setColor(ACCENT_GREEN);
        g2.setStroke(new BasicStroke(uiStroke(1.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D check = new Path2D.Float();
        check.moveTo(cx - Math.min(w, h) * 0.25f, cy);
        check.lineTo(cx - Math.min(w, h) * 0.04f, cy + Math.min(w, h) * 0.21f);
        check.lineTo(cx + Math.min(w, h) * 0.29f, cy - Math.min(w, h) * 0.25f);
        g2.draw(check);
      }
      g2.dispose();
    }

    private static Color withAlpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255f));
    }
  }

  private enum UpdateDialogTone {
    QUESTION(ACCENT_NEUTRAL, BORDER_NEUTRAL),
    WARNING(Color.decode("#d7b56d"), Color.decode("#6d5830")),
    DANGER(ACCENT_ERROR, Color.decode("#6d3440"));

    private final Color primary;
    private final Color border;

    UpdateDialogTone(Color primary, Color border) {
      this.primary = primary;
      this.border = border;
    }

    Color primaryColor() {
      return primary;
    }

    Color borderColor() {
      return border;
    }
  }

  private static final class UpdateDialogIcon implements Icon {
    private final UpdateDialogTone tone;
    private final int size;

    private UpdateDialogIcon(UpdateDialogTone tone, int size) {
      this.tone = tone == null ? UpdateDialogTone.WARNING : tone;
      this.size = Math.max(24, size);
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.translate(x, y);

      float s = size;
      Color accent = tone.primaryColor();
      g2.setPaint(new LinearGradientPaint(
          0f, 0f, 0f, s,
          new float[] {0f, 1f},
          new Color[] {alpha(accent, 0.22f), alpha(accent, 0.08f)}));
      g2.fillOval(1, 1, size - 2, size - 2);

      g2.setColor(accent);
      g2.setStroke(new BasicStroke(uiStroke(1.3f)));
      g2.drawOval(1, 1, size - 2, size - 2);

      g2.setStroke(new BasicStroke(Math.max(2f, s * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (tone == UpdateDialogTone.QUESTION) {
        g2.draw(new Arc2D.Float(s * 0.24f, s * 0.24f, s * 0.52f, s * 0.52f, 35f, 285f, Arc2D.OPEN));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(s * 0.28f, s * 0.50f);
        arrow.lineTo(s * 0.62f, s * 0.50f);
        arrow.lineTo(s * 0.50f, s * 0.38f);
        g2.draw(arrow);
      } else {
        g2.drawLine(Math.round(s * 0.50f), Math.round(s * 0.24f), Math.round(s * 0.50f), Math.round(s * 0.58f));
        g2.fillOval(Math.round(s * 0.45f), Math.round(s * 0.70f), Math.round(s * 0.10f), Math.round(s * 0.10f));
      }
      g2.dispose();
    }

    private static Color alpha(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f));
    }
  }

  /**
   * Custom-painted button that stays free of L&F chrome and paints its own
   * 1px border with optional accent color. Text + icon render via {@code super.paintComponent}.
   */
  private static class FlatButton extends JButton {
    private final Color accent;

    FlatButton(String text, Icon icon, Color accentOrNull) {
      super(text);
      this.accent = accentOrNull;
      if (icon != null) {
        setIcon(icon);
        setIconTextGap(ui(10));
      }
      setHorizontalAlignment(SwingConstants.CENTER);
      setHorizontalTextPosition(SwingConstants.RIGHT);
      setForeground(accentOrNull != null ? accentOrNull : TEXT_PRIMARY);
      setFont(getFont().deriveFont(Font.PLAIN, uiFont(12f)));
      setBorder(uiPadding(10, 18, 10, 18));
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
      int arc = ui(8);
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
      Color topFill = fill.equals(PANEL_BG) ? PANEL_BG_TOP : lighten(fill, 0.035f);
      g2.setPaint(new LinearGradientPaint(
          0f,
          0f,
          0f,
          Math.max(1f, h),
          new float[] {0f, 1f},
          new Color[] {topFill, fill}));
      g2.fillRoundRect(0, 0, w, h, arc, arc);

      Color borderColor = accent != null ? accent : BORDER_NEUTRAL;
      if (!isEnabled()) borderColor = BORDER_NEUTRAL;
      g2.setColor(borderColor);
      g2.setStroke(new BasicStroke(uiStroke(1f)));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      g2.dispose();
      super.paintComponent(g);
    }
  }

  /** Launcher action button that can switch its maintenance overlay on refresh. */
  private static final class LauncherButton extends FlatButton {
    private static final int STRIPE_WIDTH = ui(28);
    private static final int STRIPE_PERIOD = STRIPE_WIDTH * 2;
    private final javax.swing.Timer stripeTimer;
    private int stripeOffset = 0;
    private boolean underMaintenance = false;

    LauncherButton(String text) {
      super(text, WindowsSevenActionIcon.of(WindowsSevenActionIcon.Kind.LAUNCHER), BORDER_NEUTRAL);
      stripeTimer = new javax.swing.Timer(70, e -> {
        stripeOffset = (stripeOffset + 2) % STRIPE_PERIOD;
        repaint();
      });
      stripeTimer.setCoalesce(true);
    }

    void setMaintenanceState(LauncherMaintenanceState state) {
      LauncherMaintenanceState safeState = state == null ? LauncherMaintenanceState.available() : state;
      underMaintenance = safeState.underMaintenance();
      setForeground(underMaintenance ? ACCENT_MAINTENANCE : TEXT_PRIMARY);
      setIcon(WindowsSevenActionIcon.of(
          underMaintenance ? WindowsSevenActionIcon.Kind.LAUNCHER_MAINTENANCE
              : WindowsSevenActionIcon.Kind.LAUNCHER));
      setToolTipText(underMaintenance
          ? safeState.resolvedMessage()
          : "Launch the standalone JVN launcher.");
      if (underMaintenance && isDisplayable()) {
        stripeTimer.start();
      } else {
        stripeTimer.stop();
      }
      repaint();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      if (underMaintenance) stripeTimer.start();
    }

    @Override
    public void removeNotify() {
      stripeTimer.stop();
      super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (!underMaintenance) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int arc = ui(8);
      Shape oldClip = g2.getClip();
      g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));

      g2.setColor(alpha(Color.decode("#120c00"), 0.34f));
      g2.fillRoundRect(0, 0, w, h, arc, arc);

      g2.setColor(alpha(ACCENT_MAINTENANCE, 0.18f));
      for (int x = -h - STRIPE_PERIOD + stripeOffset; x < w + STRIPE_PERIOD; x += STRIPE_PERIOD) {
        g2.fillPolygon(
            new int[] {x, x + STRIPE_WIDTH, x + STRIPE_WIDTH + h, x + h},
            new int[] {0, 0, h, h},
            4);
      }
      g2.setClip(oldClip);

      String badge = "MAINTENANCE";
      Font badgeFont = getFont().deriveFont(Font.BOLD, uiFont(9f));
      g2.setFont(badgeFont);
      FontMetrics fm = g2.getFontMetrics();
      int badgeW = fm.stringWidth(badge) + ui(12);
      int badgeH = ui(17);
      int badgeX = Math.max(ui(6), w - badgeW - ui(7));
      int badgeY = ui(6);
      g2.setColor(alpha(Color.decode("#1b1202"), 0.94f));
      g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, ui(7), ui(7));
      g2.setColor(alpha(ACCENT_MAINTENANCE, 0.72f));
      g2.drawRoundRect(badgeX, badgeY, badgeW, badgeH, ui(7), ui(7));
      g2.setColor(ACCENT_MAINTENANCE);
      g2.drawString(badge, badgeX + ui(6), badgeY + ui(12));
      g2.dispose();
    }
  }

  /** Update button that paints incoming commit count as a right-aligned badge. */
  private static final class UpdateEngineButton extends FlatButton {
    private int incomingCount = -1;
    private boolean checking = false;

    UpdateEngineButton(String text, Icon icon) {
      super(text, icon, ACCENT_NEUTRAL);
      setBorder(uiPadding(10, 18, 10, 52));
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
            + " available from " + ENGINE_UPDATE_REMOTE_REF + ". Click to pull --rebase.");
      } else if (incomingCount == 0) {
        setToolTipText("Engine is up to date with " + ENGINE_UPDATE_REMOTE_REF + ".");
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
      Font badgeFont = getFont().deriveFont(Font.BOLD, uiFont(11f));
      g2.setFont(badgeFont);
      FontMetrics fm = g2.getFontMetrics();
      int textW = fm.stringWidth(text);
      int badgeH = ui(22);
      int badgeW = Math.max(ui(26), textW + ui(14));
      int badgeX = getWidth() - badgeW - ui(14);
      int badgeY = (getHeight() - badgeH) / 2;

      Color fill = isEnabled() ? ACCENT_GREEN : BORDER_NEUTRAL;
      g2.setColor(fill);
      g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, badgeH, badgeH);
      g2.setColor(BG);
      int textX = badgeX + (badgeW - textW) / 2;
      int textY = badgeY + badgeH - (badgeH - fm.getAscent() + fm.getDescent()) / 2 - ui(1);
      g2.drawString(text, textX, textY);
      g2.dispose();
    }
  }

  /** Toggleable Developer Mode header control; blue when active, muted when inactive. */
  private static final class DeveloperModeToggleButton extends JToggleButton {
    DeveloperModeToggleButton() {
      setDeveloperModeEnabled(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    void setDeveloperModeEnabled(boolean enabled) {
      setSelected(enabled);
      setIcon(WindowsSevenActionIcon.of(
          enabled ? WindowsSevenActionIcon.Kind.DEVELOPER_ACTIVE : WindowsSevenActionIcon.Kind.DEVELOPER, 24));
      setToolTipText(enabled
          ? "Developer Mode ON — tests and engineering launch flags are enabled"
          : "Developer Mode OFF — click to show developer actions");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D h = (Graphics2D) g.create();
      h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (getModel().isRollover() || getModel().isPressed()) {
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
      }
      if (isSelected()) {
        h.setColor(alpha(ACCENT_DEV, getModel().isPressed() ? 0.26f : getModel().isRollover() ? 0.20f : 0.14f));
        h.fillRoundRect(0, 0, getWidth(), getHeight(), ui(10), ui(10));
        h.setColor(alpha(ACCENT_DEV, 0.70f));
        h.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ui(10), ui(10));
      }
      h.dispose();
      super.paintComponent(g);
    }
  }

  /** Toggleable Safe Mode header control; amber when active, muted when inactive. */
  private static final class SafeModeToggleButton extends JToggleButton {
    SafeModeToggleButton() {
      setSafeModeEnabled(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    void setSafeModeEnabled(boolean enabled) {
      setSelected(enabled);
      setIcon(WindowsSevenActionIcon.of(
          enabled ? WindowsSevenActionIcon.Kind.SAFE_ACTIVE : WindowsSevenActionIcon.Kind.SAFE, 24));
      setToolTipText(enabled
          ? "Safe Mode ON — launches use safe-mode flags; Update Engine uses guarded Git recovery"
          : "Safe Mode OFF — click to launch with safe-mode flags; Update Engine uses normal Git update");
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D h = (Graphics2D) g.create();
      h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (getModel().isRollover() || getModel().isPressed()) {
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
      }
      if (isSelected()) {
        h.setColor(alpha(ACCENT_SAFE, getModel().isPressed() ? 0.26f : getModel().isRollover() ? 0.20f : 0.14f));
        h.fillRoundRect(0, 0, getWidth(), getHeight(), ui(10), ui(10));
        h.setColor(alpha(ACCENT_SAFE, 0.70f));
        h.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ui(10), ui(10));
      }
      h.dispose();
      super.paintComponent(g);
    }
  }

  /** Borderless header utility button with the shared Hub hover treatment. */
  private static class HeaderIconButton extends JButton {
    HeaderIconButton(Icon icon, String tooltip) {
      setIcon(icon);
      setToolTipText(tooltip);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setBorder(uiPadding(8, 8, 8, 8));
      setPreferredSize(uiDimension(40, 40));
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (getModel().isRollover() || getModel().isPressed()) {
        Graphics2D h = (Graphics2D) g.create();
        h.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintHeaderHoverBackdrop(h, this, getModel().isPressed());
        h.dispose();
      }
      super.paintComponent(g);
    }
  }

  private static void paintHeaderHoverBackdrop(Graphics2D g, JComponent component, boolean pressed) {
    float radius = ui(17);
    float centerX = component.getWidth() / 2.0f;
    float centerY = component.getHeight() / 2.0f + ui(1);
    float strength = pressed ? 0.34f : 0.24f;
    RadialGradientPaint shadow = new RadialGradientPaint(
        new Point2D.Float(centerX, centerY),
        radius,
        new float[]{0.0f, 0.48f, 0.76f, 1.0f},
        new Color[]{
            alpha(Color.WHITE, strength),
            alpha(Color.WHITE, strength * 0.72f),
            alpha(Color.WHITE, strength * 0.24f),
            alpha(Color.WHITE, 0.0f)
        });
    g.setPaint(shadow);
    g.fillOval(
        Math.round(centerX - radius),
        Math.round(centerY - radius),
        Math.round(radius * 2.0f),
        Math.round(radius * 2.0f));
  }

  /** Temporary terminal-style size readout painted over the Hub during live resize. */
  private static final class ResizeOverlay extends JComponent {
    private final JFrame owner;
    private String windowSize = "";
    private String contentSize = "";

    ResizeOverlay(JFrame owner) {
      this.owner = owner;
      setOpaque(false);
      setVisible(false);
    }

    void updateDimensions() {
      windowSize = formatWindowPixels(owner.getWidth(), owner.getHeight());
      Component content = owner.getContentPane();
      contentSize = "CONTENT  " + formatWindowPixels(content.getWidth(), content.getHeight());
      repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      int width = getWidth();
      int height = getHeight();
      if (width <= 0 || height <= 0) return;
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(30, 14, 2, 112));
        g.fillRect(0, 0, width, height);
        paintCloud(g, width * 0.18f, height * 0.35f, Math.max(width, height) * 0.46f, 86);
        paintCloud(g, width * 0.52f, height * 0.62f, Math.max(width, height) * 0.55f, 72);
        paintCloud(g, width * 0.84f, height * 0.28f, Math.max(width, height) * 0.40f, 64);
        paintCloud(g, width * 0.70f, height * 0.88f, Math.max(width, height) * 0.34f, 52);

        Font dimensionFont = getFont().deriveFont(Font.BOLD, uiFont(34f));
        g.setFont(dimensionFont);
        FontMetrics dimensionMetrics = g.getFontMetrics();
        int dimensionX = (width - dimensionMetrics.stringWidth(windowSize)) / 2;
        int baseline = height / 2 + dimensionMetrics.getAscent() / 3;
        g.setColor(new Color(0, 0, 0, 145));
        g.drawString(windowSize, dimensionX + ui(2), baseline + ui(3));
        g.setColor(Color.decode("#fff4e8"));
        g.drawString(windowSize, dimensionX, baseline);

        Font detailFont = getFont().deriveFont(Font.BOLD, uiFont(10f));
        g.setFont(detailFont);
        FontMetrics detailMetrics = g.getFontMetrics();
        int detailX = (width - detailMetrics.stringWidth(contentSize)) / 2;
        int detailBaseline = baseline + ui(24);
        g.setColor(new Color(255, 210, 168, 225));
        g.drawString(contentSize, detailX, detailBaseline);
      } finally {
        g.dispose();
      }
    }

    private static void paintCloud(Graphics2D g, float centerX, float centerY, float radius, int alpha) {
      float safeRadius = Math.max(1f, radius);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(centerX, centerY),
          safeRadius,
          new float[] {0f, 0.45f, 1f},
          new Color[] {
              new Color(255, 145, 38, alpha),
              new Color(225, 91, 12, alpha / 2),
              new Color(116, 36, 0, 0)
          }));
      g.fill(new Ellipse2D.Float(
          centerX - safeRadius,
          centerY - safeRadius,
          safeRadius * 2f,
          safeRadius * 2f));
    }
  }

  /** Glass-and-chrome action glyphs inspired by the Windows 7 desktop era. */
  private static final class WindowsSevenActionIcon implements Icon {
    enum Kind {
      EDITOR, LAUNCHER, LAUNCHER_MAINTENANCE, BUILD, SHORTCUT, UPDATE, TESTS, OPTIONS,
      MORE, CANCEL, QUIT,
      DEVELOPER, DEVELOPER_ACTIVE, SAFE, SAFE_ACTIVE,
      DIAGNOSTICS, ABOUT, DOCUMENTATION
    }

    private final Kind kind;
    private final int size;

    private WindowsSevenActionIcon(Kind kind, int size) {
      this.kind = kind;
      this.size = size;
    }

    static WindowsSevenActionIcon of(Kind kind) {
      return new WindowsSevenActionIcon(kind, ui(26));
    }

    static WindowsSevenActionIcon of(Kind kind, int logicalSize) {
      return new WindowsSevenActionIcon(kind, ui(logicalSize));
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (WindowsSevenHubIconPainter.paint(g, kind.name())) return;
        switch (kind) {
          case EDITOR -> paintEditor(g);
          case LAUNCHER -> paintLauncher(g, false);
          case LAUNCHER_MAINTENANCE -> paintLauncher(g, true);
          case BUILD -> paintBuild(g);
          case SHORTCUT -> paintShortcut(g);
          case UPDATE -> paintUpdate(g);
          case TESTS -> paintTests(g);
          case OPTIONS -> paintOptions(g);
          case MORE -> paintMore(g);
          case CANCEL -> paintCancel(g);
          case QUIT -> paintQuit(g);
          case DEVELOPER -> paintDeveloper(g, false);
          case DEVELOPER_ACTIVE -> paintDeveloper(g, true);
          case SAFE -> paintSafe(g, false);
          case SAFE_ACTIVE -> paintSafe(g, true);
          case DIAGNOSTICS -> paintDiagnostics(g);
          case ABOUT -> paintAbout(g);
          case DOCUMENTATION -> paintDocumentation(g);
        }
      } finally {
        g.dispose();
      }
    }

    private static void paintEditor(Graphics2D g) {
      shadow(g, 3, 3, 18, 15, 3);
      g.setPaint(new java.awt.GradientPaint(0, 3, Color.decode("#65c9ff"), 0, 18, Color.decode("#1265b5")));
      g.fillRoundRect(3, 2, 18, 15, 3, 3);
      g.setColor(Color.decode("#b9eaff"));
      g.drawRoundRect(3, 2, 17, 14, 3, 3);
      gloss(g, 4, 3, 16, 6, 3);
      g.setColor(Color.decode("#d8e9f4"));
      g.fillRoundRect(9, 17, 6, 2, 1, 1);
      g.fillRoundRect(6, 19, 12, 2, 2, 2);
      g.rotate(-0.72, 17, 14);
      g.setPaint(new java.awt.GradientPaint(14, 8, Color.decode("#ffe77a"), 20, 18, Color.decode("#f28b19")));
      g.fillRoundRect(16, 7, 4, 13, 2, 2);
      g.setColor(Color.decode("#7a491c"));
      g.drawRoundRect(16, 7, 3, 12, 2, 2);
      g.setColor(Color.decode("#f8d8b4"));
      g.fillPolygon(new int[] {16, 20, 18}, new int[] {20, 20, 23}, 3);
    }

    private static void paintLauncher(Graphics2D g, boolean maintenance) {
      Color top = maintenance ? Color.decode("#ffd36e") : Color.decode("#74ddff");
      Color bottom = maintenance ? Color.decode("#b76513") : Color.decode("#1769b5");
      glassOrb(g, top, bottom);

      Path2D rocket = new Path2D.Float();
      rocket.moveTo(13, 5);
      rocket.curveTo(17, 6, 19, 9, 19, 12);
      rocket.lineTo(14, 17);
      rocket.lineTo(10, 17);
      rocket.lineTo(6, 21);
      rocket.lineTo(7, 15);
      rocket.lineTo(12, 10);
      rocket.curveTo(12, 8, 12, 6, 13, 5);
      rocket.closePath();
      g.setPaint(new LinearGradientPaint(
          7f, 5f, 18f, 19f,
          new float[] {0f, 0.45f, 1f},
          new Color[] {Color.WHITE, Color.decode("#dceaf3"), Color.decode("#8297a8")}));
      g.fill(rocket);
      g.setColor(maintenance ? Color.decode("#7b3b0c") : Color.decode("#174b74"));
      g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(rocket);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(15f, 10f), 3.2f,
          new float[] {0f, 0.62f, 1f},
          new Color[] {Color.WHITE, Color.decode("#7fdcff"), Color.decode("#1765a2")}));
      g.fillOval(13, 8, 4, 4);
      g.setColor(new Color(255, 255, 255, 190));
      g.drawOval(13, 8, 4, 4);
      g.setPaint(new LinearGradientPaint(
          7f, 17f, 7f, 23f,
          new float[] {0f, 0.5f, 1f},
          new Color[] {Color.decode("#fff4a0"), Color.decode("#ff9b28"), new Color(214, 48, 18, 40)}));
      g.fillPolygon(new int[] {8, 11, 7}, new int[] {17, 18, 23}, 3);
    }

    private static void paintBuild(Graphics2D g) {
      shadow(g, 2, 10, 15, 11, 3);
      for (int row = 0; row < 2; row++) {
        for (int col = 0; col < 2; col++) {
          int bx = 2 + col * 7;
          int by = 9 + row * 6;
          g.setPaint(new java.awt.GradientPaint(bx, by, Color.decode("#6fd7ff"), bx, by + 6, Color.decode("#1670be")));
          g.fillRoundRect(bx, by, 6, 5, 2, 2);
          g.setColor(Color.decode("#bcecff"));
          g.drawRoundRect(bx, by, 5, 4, 2, 2);
        }
      }
      g.rotate(-0.68, 16, 10);
      g.setPaint(new java.awt.GradientPaint(14, 2, Color.decode("#fff0a0"), 19, 8, Color.decode("#d78919")));
      g.fillRoundRect(13, 2, 9, 6, 2, 2);
      g.setColor(Color.decode("#76501f"));
      g.drawRoundRect(13, 2, 8, 5, 2, 2);
      g.setPaint(new java.awt.GradientPaint(16, 7, Color.decode("#a96b37"), 18, 21, Color.decode("#5b2e18")));
      g.fillRoundRect(16, 7, 3, 14, 2, 2);
    }

    private static void paintShortcut(Graphics2D g) {
      shadow(g, 4, 2, 16, 18, 4);
      g.setPaint(new java.awt.GradientPaint(0, 2, Color.decode("#76e1ff"), 0, 21, Color.decode("#1575c7")));
      g.fillRoundRect(4, 2, 16, 18, 4, 4);
      g.setColor(Color.decode("#c7f4ff"));
      g.drawRoundRect(4, 2, 15, 17, 4, 4);
      gloss(g, 5, 3, 14, 7, 3);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(8, 15, 16, 7);
      g.drawLine(11, 7, 16, 7);
      g.drawLine(16, 7, 16, 12);
      g.setColor(Color.decode("#75d04a"));
      g.fillRoundRect(1, 14, 10, 8, 4, 4);
      g.setColor(Color.decode("#eaffdf"));
      g.drawLine(3, 18, 8, 18);
      g.drawLine(6, 16, 8, 18);
      g.drawLine(6, 20, 8, 18);
    }

    private static void paintUpdate(Graphics2D g) {
      shadow(g, 3, 3, 18, 18, 9);
      g.setPaint(new java.awt.GradientPaint(4, 3, Color.decode("#79dcff"), 19, 21, Color.decode("#1976bd")));
      g.fillOval(3, 3, 18, 18);
      g.setColor(Color.decode("#b9f2ff"));
      g.drawOval(3, 3, 17, 17);
      g.setColor(new Color(255, 255, 255, 100));
      g.fillOval(6, 4, 10, 5);
      g.setColor(Color.decode("#79d34f"));
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawArc(5, 5, 14, 14, 30, 145);
      g.fillPolygon(new int[] {5, 4, 9}, new int[] {8, 14, 11}, 3);
      g.setColor(Color.decode("#f2fbff"));
      g.drawArc(5, 5, 14, 14, 210, 140);
      g.fillPolygon(new int[] {19, 20, 15}, new int[] {16, 10, 13}, 3);
    }

    private static void paintTests(Graphics2D g) {
      glassTile(g, 3, 2, 18, 20, Color.decode("#80e77b"), Color.decode("#258b35"));
      g.setColor(Color.decode("#f5fff2"));
      g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(7, 8, 9, 10);
      g.drawLine(9, 10, 13, 6);
      g.drawLine(7, 15, 9, 17);
      g.drawLine(9, 17, 13, 13);
      g.setColor(Color.decode("#d8f6ff"));
      g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(15, 8, 19, 8);
      g.drawLine(15, 15, 19, 15);
    }

    private static void paintOptions(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#c09aff"), Color.decode("#6740a8"));
      g.setColor(Color.decode("#f8f2ff"));
      g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int[] ys = {8, 12, 16};
      int[] knobs = {9, 16, 12};
      for (int i = 0; i < ys.length; i++) {
        g.drawLine(6, ys[i], 18, ys[i]);
        g.setColor(Color.decode("#ffd66d"));
        g.fillOval(knobs[i] - 2, ys[i] - 2, 4, 4);
        g.setColor(Color.decode("#fff5c7"));
        g.drawOval(knobs[i] - 2, ys[i] - 2, 3, 3);
        g.setColor(Color.decode("#f8f2ff"));
      }
    }

    private static void paintMore(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#77d9ff"), Color.decode("#266da9"));
      g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      for (int i = 0; i < 3; i++) {
        int y = 8 + i * 4;
        g.setColor(Color.decode("#eaf9ff"));
        g.drawLine(7, y, 18, y);
        g.setPaint(new java.awt.GradientPaint(4, y - 2, Color.WHITE, 7, y + 2, Color.decode("#9daab4")));
        g.fillOval(4, y - 2, 4, 4);
      }
    }

    private static void paintCancel(Graphics2D g) {
      glassOrb(g, Color.decode("#ff8b8b"), Color.decode("#b51f31"));
      g.setPaint(new java.awt.GradientPaint(8, 7, Color.WHITE, 16, 17, Color.decode("#e4e9ed")));
      g.fillRoundRect(7, 7, 10, 10, 2, 2);
      g.setColor(Color.decode("#8d1727"));
      g.drawRoundRect(7, 7, 9, 9, 2, 2);
    }

    private static void paintQuit(Graphics2D g) {
      glassOrb(g, Color.decode("#ff876f"), Color.decode("#a71920"));
      g.setColor(Color.decode("#fff9f4"));
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawArc(6, 5, 12, 14, 42, 276);
      g.drawLine(12, 3, 12, 11);
    }

    private static void paintDeveloper(Graphics2D g, boolean active) {
      glassOrb(g,
          active ? Color.decode("#66d8ff") : Color.decode("#87a8c4"),
          active ? Color.decode("#1764bc") : Color.decode("#405467"));
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(9, 7, 5, 12);
      g.drawLine(5, 12, 9, 17);
      g.drawLine(15, 7, 19, 12);
      g.drawLine(19, 12, 15, 17);
      g.setColor(Color.decode("#ffe879"));
      g.drawLine(14, 6, 10, 18);
    }

    private static void paintSafe(Graphics2D g, boolean active) {
      shadow(g, 3, 2, 18, 20, 7);
      Path2D shield = new Path2D.Float();
      shield.moveTo(12, 2);
      shield.curveTo(16, 5, 19, 5, 21, 6);
      shield.lineTo(20, 13);
      shield.curveTo(19, 18, 15, 21, 12, 22);
      shield.curveTo(9, 21, 5, 18, 4, 13);
      shield.lineTo(3, 6);
      shield.curveTo(7, 5, 9, 4, 12, 2);
      shield.closePath();
      g.setPaint(new java.awt.GradientPaint(4, 3,
          active ? Color.decode("#a8ef72") : Color.decode("#b1bec6"), 19, 22,
          active ? Color.decode("#318638") : Color.decode("#53626c")));
      g.fill(shield);
      g.setColor(active ? Color.decode("#eaffd8") : Color.decode("#edf3f6"));
      g.draw(shield);
      g.setColor(new Color(255, 255, 255, 105));
      g.fillOval(7, 5, 10, 5);
      g.setColor(active ? Color.decode("#fff078") : Color.WHITE);
      g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(8, 12, 11, 15);
      g.drawLine(11, 15, 17, 9);
    }

    private static void paintDiagnostics(Graphics2D g) {
      glassTile(g, 2, 3, 20, 18, Color.decode("#74dcff"), Color.decode("#176da8"));
      g.setColor(Color.decode("#102a3a"));
      g.fillRoundRect(5, 7, 14, 10, 2, 2);
      g.setColor(Color.decode("#83f18b"));
      g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D pulse = new Path2D.Float();
      pulse.moveTo(6, 13);
      pulse.lineTo(9, 13);
      pulse.lineTo(11, 9);
      pulse.lineTo(14, 16);
      pulse.lineTo(16, 12);
      pulse.lineTo(19, 12);
      g.draw(pulse);
    }

    private static void paintAbout(Graphics2D g) {
      glassOrb(g, Color.decode("#83d8ff"), Color.decode("#235cae"));
      g.setColor(Color.WHITE);
      g.fillOval(11, 6, 3, 3);
      g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.drawLine(12, 11, 12, 17);
    }

    private static void paintDocumentation(Graphics2D g) {
      shadow(g, 3, 3, 18, 18, 3);
      g.setPaint(new java.awt.GradientPaint(3, 3, Color.decode("#72cfff"), 20, 21, Color.decode("#225aa8")));
      g.fillRoundRect(3, 3, 18, 18, 3, 3);
      g.setColor(Color.decode("#d9f3ff"));
      g.drawRoundRect(3, 3, 17, 17, 3, 3);
      g.setColor(Color.WHITE);
      g.fillRoundRect(6, 6, 5, 12, 1, 1);
      g.fillRoundRect(13, 6, 5, 12, 1, 1);
      g.setColor(Color.decode("#6d92b8"));
      g.drawLine(12, 7, 12, 18);
      g.drawLine(7, 9, 10, 9);
      g.drawLine(14, 9, 17, 9);
    }

    private static void glassTile(Graphics2D g, int x, int y, int width, int height,
                                  Color top, Color bottom) {
      shadow(g, x, y, width, height, 4);
      g.setPaint(new LinearGradientPaint(
          x, y, x, y + height,
          new float[] {0f, 0.14f, 0.55f, 1f},
          new Color[] {
              brighter(top, 0.26f),
              top,
              blend(top, bottom, 0.58f),
              darker(bottom, 0.18f)
          }));
      g.fillRoundRect(x, y, width, height, 4, 4);
      g.setColor(new Color(255, 255, 255, 185));
      g.drawRoundRect(x, y, width - 1, height - 1, 4, 4);
      g.setColor(new Color(0, 18, 34, 105));
      g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 3, 3);
      gloss(g, x + 1, y + 1, width - 2, Math.max(4, height / 2), 3);
      g.setColor(new Color(255, 255, 255, 72));
      g.drawLine(x + 3, y + height - 2, x + width - 4, y + height - 2);
    }

    private static void glassOrb(Graphics2D g, Color top, Color bottom) {
      shadow(g, 3, 3, 18, 18, 18);
      g.setPaint(new RadialGradientPaint(
          new Point2D.Float(8.5f, 6.5f), 18f,
          new float[] {0f, 0.30f, 0.72f, 1f},
          new Color[] {
              brighter(top, 0.38f),
              top,
              blend(top, bottom, 0.64f),
              darker(bottom, 0.25f)
          }));
      g.fillOval(3, 3, 18, 18);
      g.setColor(new Color(255, 255, 255, 205));
      g.drawOval(3, 3, 17, 17);
      g.setColor(new Color(0, 18, 34, 100));
      g.drawOval(4, 4, 15, 15);
      g.setPaint(new LinearGradientPaint(
          0f, 4f, 0f, 11f,
          new float[] {0f, 1f},
          new Color[] {new Color(255, 255, 255, 175), new Color(255, 255, 255, 12)}));
      g.fillOval(6, 4, 12, 7);
      g.setColor(new Color(255, 255, 255, 70));
      g.drawArc(6, 7, 12, 11, 205, 128);
    }

    private static Color brighter(Color color, float amount) {
      float safe = Math.max(0f, Math.min(1f, amount));
      return new Color(
          Math.round(color.getRed() + (255 - color.getRed()) * safe),
          Math.round(color.getGreen() + (255 - color.getGreen()) * safe),
          Math.round(color.getBlue() + (255 - color.getBlue()) * safe));
    }

    private static Color darker(Color color, float amount) {
      float safe = Math.max(0f, Math.min(1f, amount));
      return new Color(
          Math.round(color.getRed() * (1f - safe)),
          Math.round(color.getGreen() * (1f - safe)),
          Math.round(color.getBlue() * (1f - safe)),
          color.getAlpha());
    }

    private static Color blend(Color first, Color second, float secondWeight) {
      float weight = Math.max(0f, Math.min(1f, secondWeight));
      float firstWeight = 1f - weight;
      return new Color(
          Math.round(first.getRed() * firstWeight + second.getRed() * weight),
          Math.round(first.getGreen() * firstWeight + second.getGreen() * weight),
          Math.round(first.getBlue() * firstWeight + second.getBlue() * weight));
    }

    private static void shadow(Graphics2D g, int x, int y, int width, int height, int arc) {
      g.setColor(new Color(0, 0, 0, 24));
      g.fillRoundRect(x - 1, y + 1, width + 2, height + 3, arc + 2, arc + 2);
      g.setColor(new Color(0, 0, 0, 48));
      g.fillRoundRect(x, y + 2, width, height + 1, arc + 1, arc + 1);
      g.setColor(new Color(0, 0, 0, 82));
      g.fillRoundRect(x + 1, y + 2, width - 2, height, arc, arc);
    }

    private static void gloss(Graphics2D g, int x, int y, int width, int height, int arc) {
      g.setPaint(new LinearGradientPaint(
          0f, y, 0f, y + height,
          new float[] {0f, 0.48f, 1f},
          new Color[] {
              new Color(255, 255, 255, 180),
              new Color(255, 255, 255, 76),
              new Color(255, 255, 255, 5)
          }));
      g.fillRoundRect(x, y, width, height, arc, arc);
    }
  }

  /**
   * Resolution-independent vector icon painted via Java2D. Color and size are both
   * configurable so the same {@link Kind} can be reused across contexts.
   */
  private static final class VectorIcon implements Icon {
    enum Kind { PLAY, EDIT, ROCKET, HAMMER, CHECK, REFRESH, STOP, CLOSE, SHORTCUT, DOCUMENTATION, HEALTH, INFO, SHIELD, DEVELOPER, SLIDERS }

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

          // Arrowhead at the arc's start point (50 degrees), pointing tangent to the circle.
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
          // Holy fuck! This is the last of the bunch. 
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
        case DOCUMENTATION -> {
          // Document sheet with a folded corner and two guide lines.
          float pad = s * 0.16f;
          float fold = s * 0.22f;
          Path2D sheet = new Path2D.Float();
          sheet.moveTo(pad, pad);
          sheet.lineTo(s - pad - fold, pad);
          sheet.lineTo(s - pad, pad + fold);
          sheet.lineTo(s - pad, s - pad);
          sheet.lineTo(pad, s - pad);
          sheet.closePath();
          g2.fill(sheet);

          g2.setColor(BG);
          Path2D cut = new Path2D.Float();
          cut.moveTo(s - pad - fold, pad);
          cut.lineTo(s - pad - fold, pad + fold);
          cut.lineTo(s - pad, pad + fold);
          cut.closePath();
          g2.fill(cut);

          g2.setColor(color);
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D foldLine = new Path2D.Float();
          foldLine.moveTo(s - pad - fold, pad);
          foldLine.lineTo(s - pad - fold, pad + fold);
          foldLine.lineTo(s - pad, pad + fold);
          g2.draw(foldLine);

          g2.setColor(BG);
          g2.setStroke(new BasicStroke(Math.max(1.2f, s * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine(Math.round(pad + s * 0.12f), Math.round(s * 0.50f),
                      Math.round(s - pad - s * 0.12f), Math.round(s * 0.50f));
          g2.drawLine(Math.round(pad + s * 0.12f), Math.round(s * 0.66f),
                      Math.round(s - pad - s * 0.22f), Math.round(s * 0.66f));
        }
        case HEALTH -> {
          // Pulse line inside a rounded monitor frame.
          float pad = s * 0.12f;
          float corner = s * 0.10f;
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s - pad * 2f, corner, corner));
          Path2D pulse = new Path2D.Float();
          pulse.moveTo(s * 0.22f, s * 0.56f);
          pulse.lineTo(s * 0.36f, s * 0.56f);
          pulse.lineTo(s * 0.45f, s * 0.34f);
          pulse.lineTo(s * 0.56f, s * 0.70f);
          pulse.lineTo(s * 0.66f, s * 0.48f);
          pulse.lineTo(s * 0.80f, s * 0.48f);
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(pulse);
        }
        case INFO -> {
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.draw(new Ellipse2D.Float(s * 0.16f, s * 0.16f, s * 0.68f, s * 0.68f));
          g2.fill(new Ellipse2D.Float(s * 0.46f, s * 0.28f, s * 0.08f, s * 0.08f));
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2.drawLine(Math.round(s * 0.50f), Math.round(s * 0.46f),
                      Math.round(s * 0.50f), Math.round(s * 0.70f));
        }
        case SHIELD -> {
          Path2D shield = new Path2D.Float();
          shield.moveTo(s * 0.50f, s * 0.10f);
          shield.curveTo(s * 0.66f, s * 0.20f, s * 0.78f, s * 0.22f, s * 0.84f, s * 0.24f);
          shield.lineTo(s * 0.80f, s * 0.54f);
          shield.curveTo(s * 0.76f, s * 0.74f, s * 0.62f, s * 0.86f, s * 0.50f, s * 0.92f);
          shield.curveTo(s * 0.38f, s * 0.86f, s * 0.24f, s * 0.74f, s * 0.20f, s * 0.54f);
          shield.lineTo(s * 0.16f, s * 0.24f);
          shield.curveTo(s * 0.28f, s * 0.22f, s * 0.40f, s * 0.20f, s * 0.50f, s * 0.10f);
          shield.closePath();
          g2.fill(shield);
          g2.setColor(BG);
          g2.setStroke(new BasicStroke(Math.max(1.4f, s * 0.10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D check = new Path2D.Float();
          check.moveTo(s * 0.34f, s * 0.52f);
          check.lineTo(s * 0.46f, s * 0.64f);
          check.lineTo(s * 0.68f, s * 0.40f);
          g2.draw(check);
        }
        case DEVELOPER -> {
          g2.setStroke(new BasicStroke(strokeBold, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D left = new Path2D.Float();
          left.moveTo(s * 0.38f, s * 0.30f);
          left.lineTo(s * 0.18f, s * 0.50f);
          left.lineTo(s * 0.38f, s * 0.70f);
          g2.draw(left);

          Path2D right = new Path2D.Float();
          right.moveTo(s * 0.62f, s * 0.30f);
          right.lineTo(s * 0.82f, s * 0.50f);
          right.lineTo(s * 0.62f, s * 0.70f);
          g2.draw(right);

          g2.drawLine(Math.round(s * 0.54f), Math.round(s * 0.22f),
                      Math.round(s * 0.46f), Math.round(s * 0.78f));
        }
        case SLIDERS -> {
          g2.setStroke(new BasicStroke(strokeMain, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          float[] ys = {s * 0.28f, s * 0.50f, s * 0.72f};
          float[] knobs = {s * 0.66f, s * 0.36f, s * 0.58f};
          for (int i = 0; i < ys.length; i++) {
            g2.drawLine(Math.round(s * 0.18f), Math.round(ys[i]), Math.round(s * 0.82f), Math.round(ys[i]));
            g2.fill(new Ellipse2D.Float(knobs[i] - s * 0.07f, ys[i] - s * 0.07f, s * 0.14f, s * 0.14f));
          }
        }
      }
      g2.dispose();
    }
  }

  /**
   * Vector logomark for the JVN engine, fully painted with no PNG dependency.
   * Renders "JVN" in bold sans-serif with the same white-to-gray treatment used
   * by the editor and launcher wordmark.
   * {@link #renderToImage(int, int)} exposes the same artwork as a raster so
   * the OS window/dock icon stays in sync.
   */
  private static final class JvnLogoIcon implements Icon {
    private static final Color WORDMARK_TOP = Color.decode("#ffffff");
    private static final Color WORDMARK_HIGH = Color.decode("#cfd8df");
    private static final Color WORDMARK_DARK = Color.decode("#7c8791");
    private static final Color WORDMARK_FLASH = Color.decode("#f9fbfc");
    private static final Color WORDMARK_MID = Color.decode("#9da8b2");
    private static final Color WORDMARK_LOW = Color.decode("#eef3f7");
    private static final Color WORDMARK_BOTTOM = Color.decode("#59636d");

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

      int jvnSize = Math.max(18, Math.round(height * 0.72f));
      Font jvnFont = new Font(Font.SANS_SERIF, Font.BOLD, jvnSize);
      g2.setFont(jvnFont);
      String jvn = "JVN";
      GlyphVector glyphs = jvnFont.createGlyphVector(g2.getFontRenderContext(), jvn);
      Rectangle bounds = glyphs.getPixelBounds(g2.getFontRenderContext(), 0, 0);
      double tx = (width - bounds.getWidth()) / 2.0 - bounds.getX();
      double ty = (height - bounds.getHeight()) / 2.0 - bounds.getY() - Math.max(1.0, height * 0.03);
      Shape wordmark = AffineTransform.getTranslateInstance(tx, ty).createTransformedShape(glyphs.getOutline());
      Rectangle shapeBounds = wordmark.getBounds();

      paintBrushSwipe(g2, width, height);

      g2.setColor(new Color(0, 0, 0, 95));
      g2.translate(0, Math.max(1, height * 0.05));
      g2.fill(wordmark);
      g2.translate(0, -Math.max(1, height * 0.05));

      LinearGradientPaint gradient = new LinearGradientPaint(
          0f, (float) shapeBounds.getMinY(),
          0f, (float) shapeBounds.getMaxY(),
          new float[] {0f, 0.14f, 0.27f, 0.42f, 0.58f, 0.76f, 1f},
          new Color[] {
              WORDMARK_TOP,
              WORDMARK_HIGH,
              WORDMARK_DARK,
              WORDMARK_FLASH,
              WORDMARK_MID,
              WORDMARK_LOW,
              WORDMARK_BOTTOM
          });
      g2.setPaint(gradient);
      g2.fill(wordmark);

      g2.dispose();
    }

    private static void paintBrushSwipe(Graphics2D g2, int width, int height) {
      Path2D main = new Path2D.Double();
      main.moveTo(width * 0.08, height * 0.76);
      main.curveTo(
          width * 0.24, height * 0.58,
          width * 0.47, height * 0.66,
          width * 0.88, height * 0.44);
      g2.setPaint(new LinearGradientPaint(
          0f, 0f,
          (float) width, 0f,
          new float[] {0f, 0.18f, 0.56f, 0.84f, 1f},
          new Color[] {
              new Color(255, 122, 26, 15),
              new Color(255, 143, 36, 118),
              new Color(242, 106, 33, 148),
              new Color(255, 179, 71, 78),
              new Color(255, 122, 26, 10)
          }));
      g2.setStroke(new BasicStroke(
          Math.max(7f, height * 0.22f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(main);

      Path2D highlight = new Path2D.Double();
      highlight.moveTo(width * 0.17, height * 0.84);
      highlight.curveTo(
          width * 0.36, height * 0.70,
          width * 0.56, height * 0.74,
          width * 0.80, height * 0.58);
      g2.setColor(new Color(255, 179, 71, 55));
      g2.setStroke(new BasicStroke(
          Math.max(1.8f, height * 0.045f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(highlight);

      Path2D texture = new Path2D.Double();
      texture.moveTo(width * 0.12, height * 0.64);
      texture.curveTo(
          width * 0.33, height * 0.50,
          width * 0.57, height * 0.55,
          width * 0.92, height * 0.32);
      g2.setColor(new Color(243, 107, 33, 40));
      g2.setStroke(new BasicStroke(
          Math.max(2.4f, height * 0.07f),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      g2.draw(texture);
    }
  }

  /**
   * Flat neutral scrollbar UI: no arrow buttons, rounded thumb, BG-matching track.
   * Lightens the thumb on hover/drag for subtle feedback.
   */
  private static final class NeutralScrollBarUI extends BasicScrollBarUI {
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

  private static final class HubCheckIcon implements Icon {
    private final boolean selected;

    HubCheckIcon(boolean selected) {
      this.selected = selected;
    }

    @Override
    public int getIconWidth() {
      return ui(15);
    }

    @Override
    public int getIconHeight() {
      return ui(15);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      ButtonModel model = c instanceof AbstractButton button ? button.getModel() : null;
      boolean enabled = c == null || c.isEnabled();
      boolean rollover = model != null && model.isRollover();
      boolean pressed = model != null && model.isPressed();
      int size = Math.min(getIconWidth(), getIconHeight());
      Color border = enabled ? (rollover ? ACCENT_DEV : BORDER_NEUTRAL) : Color.decode("#2b2b2b");
      Color fill = selected
          ? (enabled ? Color.decode("#1e3450") : Color.decode("#242424"))
          : (enabled ? BG : Color.decode("#181818"));
      if (pressed && enabled) fill = Color.decode("#16283d");

      g2.setColor(fill);
      g2.fillRoundRect(x, y, size, size, ui(4), ui(4));
      g2.setColor(border);
      g2.drawRoundRect(x, y, size - 1, size - 1, ui(4), ui(4));

      if (selected) {
        g2.setStroke(new BasicStroke(Math.max(1.6f, ui(2)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(enabled ? ACCENT_DEV : TEXT_MUTED);
        Path2D mark = new Path2D.Double();
        mark.moveTo(x + size * 0.26, y + size * 0.54);
        mark.lineTo(x + size * 0.44, y + size * 0.72);
        mark.lineTo(x + size * 0.76, y + size * 0.30);
        g2.draw(mark);
      }
      g2.dispose();
    }
  }

  private static final class HubLifecycleSplash extends JWindow {
    private final JLabel status;
    private final JLabel thanks;
    private final JProgressBar progress;

    private HubLifecycleSplash(JPanel content, int width, int height,
                               JLabel status, JLabel thanks, JProgressBar progress) {
      this.status = status;
      this.thanks = thanks;
      this.progress = progress;
      setBackground(new Color(0, 0, 0, 0));
      setContentPane(content);
      setAlwaysOnTop(true);
      setSize(ui(width), ui(height));
    }

    static HubLifecycleSplash startup() {
      JPanel pane = new JPanel();
      pane.setOpaque(false);
      pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
      pane.setBorder(uiPadding(18, 18, 18, 18));

      JLabel logo = centered(new JLabel(new JvnLogoIcon(ui(230), ui(118))));
      JLabel version = centered(new JLabel(displayVersionLabel(VERSION)));
      version.setForeground(Color.decode("#f4f6f8"));
      version.setFont(version.getFont().deriveFont(Font.BOLD, uiFont(18f)));
      JLabel source = centered(new JLabel(isRunningFromSource() ? "Running from source" : ""));
      source.setForeground(Color.decode("#d5dae0"));
      source.setFont(source.getFont().deriveFont(Font.BOLD, uiFont(12f)));
      source.setVisible(!source.getText().isBlank());

      pane.add(Box.createVerticalGlue());
      pane.add(logo);
      pane.add(version);
      pane.add(Box.createVerticalStrut(ui(4)));
      pane.add(source);
      pane.add(Box.createVerticalGlue());
      return new HubLifecycleSplash(pane, 520, 300, null, null, null);
    }

    static HubLifecycleSplash shutdown(String detail) {
      JPanel pane = new JPanel(new GridBagLayout());
      pane.setOpaque(false);
      pane.setBorder(uiPadding(24, 34, 24, 34));
      JPanel stack = new JPanel();
      stack.setOpaque(false);
      stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

      JLabel logo = centered(new JLabel(new JvnLogoIcon(ui(150), ui(76))));
      JLabel title = centered(new JLabel("Closing JVN"));
      title.setForeground(Color.decode("#f4f4f4"));
      title.setFont(title.getFont().deriveFont(Font.BOLD, uiFont(20f)));
      JLabel status = centered(new JLabel(detail));
      status.setForeground(Color.decode("#b8b8b8"));
      status.setFont(status.getFont().deriveFont(Font.BOLD, uiFont(12f)));
      JProgressBar progress = new JProgressBar();
      progress.setAlignmentX(Component.CENTER_ALIGNMENT);
      progress.setIndeterminate(true);
      progress.setBorderPainted(false);
      progress.setBackground(Color.decode("#111111"));
      progress.setForeground(ACCENT_DEV);
      progress.setMaximumSize(new Dimension(ui(300), ui(5)));
      progress.setPreferredSize(new Dimension(ui(300), ui(5)));
      JLabel thanks = centered(new JLabel("Thank you for choosing JVN."));
      thanks.setForeground(Color.decode("#ff9933"));
      thanks.setFont(thanks.getFont().deriveFont(Font.BOLD, uiFont(14f)));
      thanks.setVisible(false);

      stack.add(logo);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(title);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(status);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(progress);
      stack.add(Box.createVerticalStrut(ui(9)));
      stack.add(thanks);
      pane.add(stack);
      return new HubLifecycleSplash(pane, 390, 245, status, thanks, progress);
    }

    private static JLabel centered(JLabel label) {
      label.setAlignmentX(Component.CENTER_ALIGNMENT);
      label.setHorizontalAlignment(SwingConstants.CENTER);
      return label;
    }

    void showCentered() {
      setLocationRelativeTo(null);
      setVisible(true);
      toFront();
    }

    void completeShutdown(Runnable afterClose) {
      status.setText("Everything is safely closed.");
      progress.setIndeterminate(false);
      progress.setValue(100);
      thanks.setVisible(true);
      revalidate();
      repaint();
      closeAfter(850, afterClose);
    }

    void closeAfter(int visibleMillis, Runnable afterClose) {
      javax.swing.Timer closeDelay = new javax.swing.Timer(visibleMillis, event -> {
        ((javax.swing.Timer) event.getSource()).stop();
        dispose();
        if (afterClose != null) afterClose.run();
      });
      closeDelay.setRepeats(false);
      closeDelay.start();
    }
  }

  private final class HubPerformancePanel extends JPanel {
    private final JLabel cpuValue = metricValue();
    private final JLabel heapValue = metricValue();
    private final JLabel threadsValue = metricValue();
    private final JLabel activityValue = metricValue();
    private final JLabel engineValue = new JLabel();
    private final PerformanceGraph graph = new PerformanceGraph();
    private final String revision = readGitValue(List.of("git", "rev-parse", "--short", "HEAD"), "unknown");
    private final javax.swing.Timer refreshTimer = new javax.swing.Timer(1000, event -> refreshMetrics());

    HubPerformancePanel() {
      super(new BorderLayout(0, ui(3)));
      setBackground(PANEL_BG);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(BORDER_NEUTRAL),
          uiPadding(9, 12, 10, 12)));

      engineValue.setForeground(TEXT_MUTED);
      engineValue.setFont(engineValue.getFont().deriveFont(Font.PLAIN, uiFont(9f)));
      engineValue.setHorizontalAlignment(SwingConstants.RIGHT);

      JPanel header = new JPanel(new BorderLayout());
      header.setOpaque(false);
      header.add(engineValue, BorderLayout.CENTER);
      add(header, BorderLayout.NORTH);

      add(graph, BorderLayout.CENTER);

      JPanel metrics = new JPanel(new GridLayout(1, 4, ui(8), 0));
      metrics.setOpaque(false);
      metrics.add(metric("CPU", cpuValue));
      metrics.add(metric("JVM heap", heapValue));
      metrics.add(metric("Threads", threadsValue));
      metrics.add(metric("Hub task", activityValue));
      metrics.setMinimumSize(new Dimension(0, ui(24)));
      metrics.setPreferredSize(new Dimension(0, ui(24)));
      JPanel metricsSafeArea = new JPanel(new BorderLayout());
      metricsSafeArea.setOpaque(false);
      metricsSafeArea.setBorder(BorderFactory.createEmptyBorder(0, 0, ui(3), 0));
      metricsSafeArea.add(metrics, BorderLayout.CENTER);
      add(metricsSafeArea, BorderLayout.SOUTH);
      setPreferredSize(new Dimension(0, ui(128)));
      refreshMetrics();
    }

    @Override
    public void addNotify() {
      super.addNotify();
      refreshMetrics();
      refreshTimer.start();
    }

    @Override
    public void removeNotify() {
      refreshTimer.stop();
      super.removeNotify();
    }

    private void refreshMetrics() {
      Runtime runtime = Runtime.getRuntime();
      long heapUsed = runtime.totalMemory() - runtime.freeMemory();
      long heapMax = runtime.maxMemory();
      heapValue.setText(formatBytes(heapUsed) + " / " + formatBytes(heapMax));
      threadsValue.setText(Integer.toString(ManagementFactory.getThreadMXBean().getThreadCount()));
      boolean active = runningProcess.get() != null;
      activityValue.setText(active ? statusLabel.getText() : "Idle");
      activityValue.setForeground(active ? ACCENT_GREEN : TEXT_SOFT);

      java.lang.management.OperatingSystemMXBean genericBean = ManagementFactory.getOperatingSystemMXBean();
      double cpuLoad = 0.0;
      if (genericBean instanceof com.sun.management.OperatingSystemMXBean systemBean) {
        cpuLoad = systemBean.getProcessCpuLoad();
        cpuValue.setText(cpuLoad >= 0.0 ? String.format(Locale.ROOT, "%.0f%%", cpuLoad * 100.0) : "--");
      } else {
        cpuValue.setText("--");
      }

      long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
      String updates = lastKnownIncoming > 0
          ? lastKnownIncoming + " incoming"
          : lastKnownIncoming == 0 ? "up to date" : "updates unknown";
      engineValue.setText(resolveBranch(projectRoot) + " @ " + revision + "  ·  " + updates
          + "  ·  uptime " + formatUptime(uptimeSeconds));
      double heapRatio = heapMax > 0L ? Math.min(1.0, (double) heapUsed / heapMax) : 0.0;
      graph.pushSample(Math.max(0.0, cpuLoad), heapRatio, active ? 1.0 : 0.0);
    }

    private static JPanel metric(String name, JLabel value) {
      JPanel chip = new JPanel(new BorderLayout(ui(6), 0));
      chip.setBackground(PANEL_BG_TOP);
      chip.setBorder(uiPadding(2, 7, 2, 7));
      JLabel label = new JLabel(name);
      label.setForeground(TEXT_MUTED);
      label.setFont(label.getFont().deriveFont(Font.BOLD, uiFont(8f)));
      chip.add(label, BorderLayout.WEST);
      value.setHorizontalAlignment(SwingConstants.RIGHT);
      chip.add(value, BorderLayout.EAST);
      return chip;
    }

    private static JLabel metricValue() {
      JLabel value = new JLabel("--");
      value.setForeground(TEXT_SOFT);
      value.setFont(value.getFont().deriveFont(Font.BOLD, uiFont(9f)));
      return value;
    }

    private static String formatBytes(long bytes) {
      if (bytes < 0L) return "--";
      double gibibytes = bytes / (1024.0 * 1024.0 * 1024.0);
      if (gibibytes >= 1.0) return String.format(Locale.ROOT, "%.1f GB", gibibytes);
      return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatUptime(long seconds) {
      long hours = seconds / 3600L;
      long minutes = (seconds % 3600L) / 60L;
      long remainder = seconds % 60L;
      return hours > 0L
          ? String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
          : String.format(Locale.ROOT, "%dm %02ds", minutes, remainder);
    }

    private static final class PerformanceGraph extends JComponent {
      private static final int SAMPLE_COUNT = 120;
      private final double[] cpu = new double[SAMPLE_COUNT];
      private final double[] heap = new double[SAMPLE_COUNT];
      private final double[] activity = new double[SAMPLE_COUNT];
      private int index;
      private boolean filled;

      PerformanceGraph() {
        setOpaque(true);
        setBackground(Color.decode("#121212"));
        setPreferredSize(new Dimension(0, ui(48)));
      }

      void pushSample(double cpuRatio, double heapRatio, double activityRatio) {
        int slot = index % SAMPLE_COUNT;
        cpu[slot] = clampRatio(cpuRatio);
        heap[slot] = clampRatio(heapRatio);
        activity[slot] = clampRatio(activityRatio);
        index++;
        if (index >= SAMPLE_COUNT) filled = true;
        repaint();
      }

      @Override
      protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          int width = getWidth();
          int height = getHeight();
          g2.setColor(Color.decode("#242424"));
          for (int row = 1; row < 4; row++) {
            int y = height * row / 4;
            g2.drawLine(0, y, width, y);
          }
          for (int column = 1; column < 6; column++) {
            int x = width * column / 6;
            g2.drawLine(x, 0, x, height);
          }

          int samples = filled ? SAMPLE_COUNT : Math.min(index, SAMPLE_COUNT);
          if (samples <= 1) return;
          double scaleX = width / (double) (SAMPLE_COUNT - 1);
          Path2D heapArea = metricPath(heap, samples, scaleX, height, true);
          g2.setColor(new Color(168, 85, 247, 56));
          g2.fill(heapArea);
          drawMetric(g2, heap, samples, scaleX, height, new Color(192, 132, 252), uiStroke(1.5f));
          drawMetric(g2, cpu, samples, scaleX, height, Color.decode("#f27333"), uiStroke(1.8f));
          drawMetric(g2, activity, samples, scaleX, height, Color.decode("#f4f4f4"), uiStroke(1.5f));
        } finally {
          g2.dispose();
        }
      }

      private Path2D metricPath(double[] values, int samples, double scaleX, int height, boolean close) {
        Path2D path = new Path2D.Double();
        if (close) path.moveTo(0, height);
        for (int i = 0; i < samples; i++) {
          int slot = (index - samples + i + SAMPLE_COUNT) % SAMPLE_COUNT;
          double x = i * scaleX;
          double y = height * (1.0 - values[slot]);
          if (i == 0 && !close) path.moveTo(x, y); else path.lineTo(x, y);
        }
        if (close) {
          path.lineTo((samples - 1) * scaleX, height);
          path.closePath();
        }
        return path;
      }

      private void drawMetric(
          Graphics2D graphics, double[] values, int samples, double scaleX, int height, Color color, float width) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(metricPath(values, samples, scaleX, height, false));
      }

      private static double clampRatio(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
      }
    }
  }

  private class HubShellPanel extends JPanel {
    private final javax.swing.JTextPane textPane = new javax.swing.JTextPane();
    private final JTextField inputField = new JTextField();
    private final StringBuilder sessionText = new StringBuilder();
    private File currentWorkingDir;

    HubShellPanel() {
      super(new BorderLayout(0, ui(8)));
      setOpaque(false);
      setVisible(false);

      currentWorkingDir = projectRoot != null ? projectRoot.toFile() : new File(System.getProperty("user.dir"));

      textPane.setEditable(false);
      textPane.setBackground(PANEL_BG);
      textPane.setForeground(LOG_TEXT);
      textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, (int) uiFont(12f)));
      textPane.setBorder(uiPadding(4, 4, 4, 4));

      JScrollPane scroll = new JScrollPane(textPane);
      scroll.setBorder(BorderFactory.createLineBorder(BORDER_NEUTRAL));
      scroll.setBackground(BG);
      scroll.getViewport().setBackground(PANEL_BG);
      styleScrollBar(scroll.getVerticalScrollBar());
      styleScrollBar(scroll.getHorizontalScrollBar());
      scroll.setPreferredSize(uiDimension(0, 160));

      inputField.setBackground(PANEL_BG);
      inputField.setForeground(TEXT_PRIMARY);
      inputField.setCaretColor(TEXT_PRIMARY);
      inputField.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(BORDER_NEUTRAL),
          uiPadding(4, 6, 4, 6)));
      inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, (int) uiFont(12f)));

      inputField.addActionListener(e -> {
        String cmd = inputField.getText();
        if (cmd != null && !cmd.isBlank()) {
          inputField.setText("");
          executeCommand(cmd.trim());
        }
      });

      FlatButton btnNew = new FlatButton("New Session", null, null);
      btnNew.addActionListener(e -> {
        textPane.setText("");
        sessionText.setLength(0);
        appendOutput("Session reset.\n", LOG_TEXT);
      });

      FlatButton btnCopy = new FlatButton("Copy Session", null, null);
      btnCopy.addActionListener(e -> {
        StringSelection selection = new StringSelection(sessionText.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        appendOutput("Session text copied to clipboard.\n", LOG_TEXT);
      });

      JPanel header = new JPanel(new BorderLayout());
      header.setOpaque(false);
      JLabel lbl = new JLabel("Developer Shell");
      lbl.setForeground(TEXT_PRIMARY);
      lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, uiFont(11f)));
      header.add(lbl, BorderLayout.WEST);

      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, ui(4), 0));
      buttons.setOpaque(false);
      buttons.add(btnNew);
      buttons.add(btnCopy);
      header.add(buttons, BorderLayout.EAST);

      add(header, BorderLayout.NORTH);
      add(scroll, BorderLayout.CENTER);
      add(inputField, BorderLayout.SOUTH);

      appendOutput("Working directory initialized to: " + currentWorkingDir.getAbsolutePath() + "\n", ACCENT_NEUTRAL);
    }

    private void appendOutput(String text, Color color) {
      SwingUtilities.invokeLater(() -> {
        javax.swing.text.StyledDocument doc = textPane.getStyledDocument();
        javax.swing.text.Style style = textPane.addStyle("ColorStyle", null);
        javax.swing.text.StyleConstants.setForeground(style, color);
        try {
          doc.insertString(doc.getLength(), text, style);
          sessionText.append(text);
          textPane.setCaretPosition(doc.getLength());
        } catch (Exception ignored) {}
      });
    }

    private void executeCommand(String commandLine) {
      String dirPrefix = currentWorkingDir != null ? currentWorkingDir.getAbsolutePath() + "> " : "> ";
      appendOutput(dirPrefix + commandLine + "\n", ACCENT_GREEN);

      if (commandLine.startsWith("cd ")) {
        handleCdCommand(commandLine);
        return;
      }

      boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
      List<String> command = new ArrayList<>();
      if (isWin) {
        command.add("cmd.exe");
        command.add("/c");
        command.add(commandLine);
      } else {
        command.add("bash");
        command.add("-c");
        command.add(commandLine);
      }

      ProcessBuilder pb = new ProcessBuilder(command);
      if (currentWorkingDir != null && currentWorkingDir.isDirectory()) {
        pb.directory(currentWorkingDir);
      }
      pb.redirectErrorStream(true);

      Thread execThread = new Thread(() -> {
        try {
          Process process = pb.start();
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
              appendOutput(line + "\n", LOG_TEXT);
            }
          }
          process.waitFor();
        } catch (Exception e) {
          appendOutput("Execution error: " + e.getMessage() + "\n", ACCENT_ERROR);
        }
      });
      execThread.setDaemon(true);
      execThread.start();
    }

    private void handleCdCommand(String commandLine) {
      String target = commandLine.substring(3).trim();
      if (target.isBlank()) return;

      File newDir = new File(target);
      if (!newDir.isAbsolute() && currentWorkingDir != null) {
        newDir = new File(currentWorkingDir, target);
      }

      try {
        newDir = newDir.getCanonicalFile();
        if (newDir.exists() && newDir.isDirectory()) {
          currentWorkingDir = newDir;
          appendOutput("Changed directory to " + currentWorkingDir.getAbsolutePath() + "\n", ACCENT_NEUTRAL);
        } else {
          appendOutput("cd: no such file or directory: " + target + "\n", ACCENT_ERROR);
        }
      } catch (Exception e) {
        appendOutput("cd error: " + e.getMessage() + "\n", ACCENT_ERROR);
      }
    }
  }
}

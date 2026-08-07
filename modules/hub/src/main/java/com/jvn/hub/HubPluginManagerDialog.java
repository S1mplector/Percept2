package com.jvn.hub;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

/** Engine Hub manager for preflighting and installing user or project plugin jars. */
final class HubPluginManagerDialog extends JDialog {
  private static final Color BACKGROUND = Color.decode("#171717");
  private static final Color PANEL = Color.decode("#232323");
  private static final Color TEXT = Color.decode("#e6e6e6");
  private static final Color MUTED = Color.decode("#a7a7a7");
  private final Path userPlugins = Path.of(System.getProperty("user.home", "."), ".jvn", "plugins");
  private final Path projectPlugins;
  private final Runnable onPluginsChanged;
  private final HubPluginBundleVerifier verifier = new HubPluginBundleVerifier();
  private final DefaultListModel<PluginRow> rows = new DefaultListModel<>();
  private final JList<PluginRow> list = new JList<>(rows);
  private final JLabel status = new JLabel("Scanning installed plugins…");

  HubPluginManagerDialog(Window owner, Path projectRoot, Runnable onPluginsChanged) {
    super(owner, "JVN Plugins", ModalityType.MODELESS);
    this.projectPlugins = projectRoot == null ? null : projectRoot.resolve("plugins");
    this.onPluginsChanged = onPluginsChanged == null ? () -> {} : onPluginsChanged;
    buildUi();
    refresh();
  }

  private void buildUi() {
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setMinimumSize(new Dimension(620, 420));
    setSize(760, 520);
    setLocationRelativeTo(getOwner());

    JPanel content = new JPanel(new BorderLayout(10, 10));
    content.setBackground(BACKGROUND);
    content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

    JLabel intro = new JLabel("Installed plugins are verified without executing their code. Load a verified JAR into the user or current-project plugin folder.");
    intro.setForeground(TEXT);
    content.add(intro, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setBackground(PANEL);
    list.setForeground(TEXT);
    list.setCellRenderer((source, value, index, selected, focused) -> {
      JLabel label = new JLabel(value == null ? "" : value.displayText());
      label.setOpaque(true);
      label.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
      label.setBackground(selected ? Color.decode("#3a3a3a") : PANEL);
      label.setForeground(value != null && !value.verification().isValid() ? Color.decode("#f38ba8") : TEXT);
      return label;
    });
    JScrollPane scroll = new JScrollPane(list);
    scroll.getViewport().setBackground(PANEL);
    scroll.setBorder(BorderFactory.createLineBorder(Color.decode("#454545")));
    content.add(scroll, BorderLayout.CENTER);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setBackground(BACKGROUND);
    JButton loadUser = button("Load for User", () -> loadInto(userPlugins));
    JButton loadProject = button("Load for Project", () -> loadInto(projectPlugins));
    loadProject.setEnabled(projectPlugins != null);
    actions.add(loadUser);
    actions.add(loadProject);
    actions.add(button("Verify Installed", this::refresh));
    actions.add(button("Remove Selected", this::removeSelected));
    actions.add(button("Open User Folder", () -> openFolder(userPlugins)));
    if (projectPlugins != null) actions.add(button("Open Project Folder", () -> openFolder(projectPlugins)));

    JPanel bottom = new JPanel(new BorderLayout(8, 8));
    bottom.setBackground(BACKGROUND);
    status.setForeground(MUTED);
    status.setHorizontalAlignment(SwingConstants.LEFT);
    bottom.add(actions, BorderLayout.NORTH);
    bottom.add(status, BorderLayout.SOUTH);
    content.add(bottom, BorderLayout.SOUTH);
    setContentPane(content);
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(event -> action.run());
    return button;
  }

  private void refresh() {
    rows.clear();
    scan(userPlugins, "User");
    if (projectPlugins != null) scan(projectPlugins, "Project");
    int invalid = 0;
    for (int i = 0; i < rows.size(); i++) if (!rows.get(i).verification().isValid()) invalid++;
    status.setText(rows.isEmpty()
        ? "No plugins installed. Load a verified plugin JAR to begin."
        : rows.size() + " plugin" + (rows.size() == 1 ? "" : "s") + " scanned · " + invalid + " issue" + (invalid == 1 ? "" : "s"));
  }

  private void scan(Path directory, String scope) {
    if (directory == null || !Files.isDirectory(directory)) return;
    try (Stream<Path> files = Files.list(directory)) {
      List<Path> jars = files.filter(path -> Files.isRegularFile(path)
              && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
          .toList();
      for (Path jar : jars) rows.addElement(new PluginRow(scope, jar, verifier.verify(jar)));
    } catch (IOException error) {
      status.setText("Could not scan " + directory + ": " + error.getMessage());
    }
  }

  private void loadInto(Path destination) {
    if (destination == null) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select JVN plugin JAR");
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path source = chooser.getSelectedFile().toPath();
    Verification verification = verifier.verify(source);
    if (!verification.isValid()) {
      JOptionPane.showMessageDialog(this, "Plugin verification failed:\n" + verification.error(), "JVN Plugins", JOptionPane.ERROR_MESSAGE);
      return;
    }
    try {
      Files.createDirectories(destination);
      Path target = destination.resolve(source.getFileName().toString());
      if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
          "Replace the existing plugin?\n" + target.getFileName(), "Replace plugin", JOptionPane.YES_NO_OPTION)
          != JOptionPane.YES_OPTION) return;
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      onPluginsChanged.run();
      refresh();
      status.setText("Loaded " + verification.descriptor().name() + ". Reload the editor project to activate it.");
    } catch (IOException error) {
      JOptionPane.showMessageDialog(this, "Could not load plugin:\n" + error.getMessage(), "JVN Plugins", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void removeSelected() {
    PluginRow row = list.getSelectedValue();
    if (row == null) return;
    if (JOptionPane.showConfirmDialog(this, "Remove plugin JAR?\n" + row.source().getFileName(),
        "Remove plugin", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
    try {
      Files.deleteIfExists(row.source());
      onPluginsChanged.run();
      refresh();
      status.setText("Removed " + row.source().getFileName() + ". Reload the editor project to unload it.");
    } catch (IOException error) {
      JOptionPane.showMessageDialog(this, "Could not remove plugin:\n" + error.getMessage(), "JVN Plugins", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void openFolder(Path folder) {
    try {
      Files.createDirectories(folder);
      if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(folder.toFile());
    } catch (IOException error) {
      JOptionPane.showMessageDialog(this, "Could not open plugin folder:\n" + error.getMessage(), "JVN Plugins", JOptionPane.ERROR_MESSAGE);
    }
  }

  private record PluginRow(String scope, Path source, Verification verification) {
    String displayText() {
      Descriptor descriptor = verification.descriptor();
      String name = descriptor == null ? source.getFileName().toString() : descriptor.name() + " " + descriptor.version();
      return scope + " · " + name + " — " + (verification.isValid() ? "Verified" : verification.error());
    }
  }

  /** Hub-side preflight deliberately avoids loading or executing third-party classes. */
  private static final class HubPluginBundleVerifier {
    private static final String MANIFEST_PATH = "jvn-plugin.json";
    private static final Pattern STRING_FIELD = Pattern.compile(
        "\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");

    Verification verify(Path source) {
      Path jar = source == null ? null : source.toAbsolutePath().normalize();
      if (jar == null || !Files.isRegularFile(jar)) return Verification.failed(jar, "Plugin bundle is not a regular file");
      if (!jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
        return Verification.failed(jar, "Plugin bundle must be a .jar file");
      }
      try (JarFile archive = new JarFile(jar.toFile())) {
        var entry = archive.getJarEntry(MANIFEST_PATH);
        if (entry == null) return Verification.failed(jar, "Missing " + MANIFEST_PATH);
        String json;
        try (InputStream input = archive.getInputStream(entry)) {
          json = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        Matcher matcher = STRING_FIELD.matcher(json);
        while (matcher.find()) fields.put(matcher.group(1), matcher.group(2));
        Descriptor descriptor = new Descriptor(fields.get("id"), fields.get("name"), fields.get("version"),
            fields.get("jvnApi"), fields.get("entrypoint"));
        if (descriptor.hasMissingRequiredField()) return Verification.failed(jar, descriptor, "Manifest is missing a required plugin field");
        if (!acceptsApi(descriptor.jvnApi())) {
          return Verification.failed(jar, descriptor, "Requires JVN Plugin API " + descriptor.jvnApi() + "; this engine provides 1.1.0");
        }
        if (archive.getJarEntry(descriptor.entrypoint().replace('.', '/') + ".class") == null) {
          return Verification.failed(jar, descriptor, "Entrypoint class is not present in the JAR");
        }
        return Verification.valid(jar, descriptor);
      } catch (IOException error) {
        return Verification.failed(jar, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
      }
    }

    private static boolean acceptsApi(String range) {
      if (range == null || range.isBlank()) return false;
      String normalized = range.trim();
      if ("1.x".equals(normalized)) return true;
      if (normalized.contains(">=2") || normalized.contains("<1")) return false;
      return normalized.contains("1.1") || normalized.contains("1.0") || normalized.startsWith(">=1");
    }
  }

  private record Descriptor(String id, String name, String version, String jvnApi, String entrypoint) {
    boolean hasMissingRequiredField() {
      return isBlank(id) || isBlank(name) || isBlank(version) || isBlank(jvnApi) || isBlank(entrypoint);
    }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
  }

  private record Verification(Path source, Descriptor descriptor, String error) {
    boolean isValid() { return error == null || error.isBlank(); }
    private static Verification valid(Path source, Descriptor descriptor) { return new Verification(source, descriptor, ""); }
    private static Verification failed(Path source, String error) { return new Verification(source, null, error); }
    private static Verification failed(Path source, Descriptor descriptor, String error) { return new Verification(source, descriptor, error); }
  }
}

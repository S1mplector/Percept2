package com.jvn.editor;

import com.jvn.core.generalhelp.HelpArticle;
import com.jvn.core.generalhelp.JaneAssistant;
import com.jvn.core.generalhelp.JaneChatResponse;
import com.jvn.core.generalhelp.JaneTrainingCorpus;
import com.jvn.core.generalhelp.TagiGeneralHelpSystem;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Terminal chatbot for Jane, JVN's local assistant. */
public final class JaneConsoleApp {
  private static final Pattern HEADING_LINE = Pattern.compile("^#\\s+(.+)$");

  private JaneConsoleApp() {}

  public static void main(String[] args) throws Exception {
    Path root = resolveWorkspaceRoot(args);
    System.setProperty("jvn.jane.workspaceRoot", root.toString());
    TagiGeneralHelpSystem tagi = new TagiGeneralHelpSystem();
    tagi.setArticles(JaneTrainingCorpus.train(indexDocs(root)));
    JaneAssistant jane = new JaneAssistant(tagi);

    System.out.println("Jane is ready. Indexed " + tagi.articles().size() + " training articles from " + root);
    System.out.println("Ask a question, or type /exit.");

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      while (true) {
        System.out.print("\nYou> ");
        String line = reader.readLine();
        if (line == null) break;
        String query = line.trim();
        if (query.equalsIgnoreCase("/exit") || query.equalsIgnoreCase("/quit")) break;
        if (query.equalsIgnoreCase("/clear")) {
          jane.clearHistory();
          System.out.println("Jane> Cleared chat history.");
          continue;
        }
        if (query.isBlank()) continue;
        JaneChatResponse response = jane.ask(query);
        System.out.println("\nJane> " + response.answer());
      }
    }
  }

  private static Path resolveWorkspaceRoot(String[] args) {
    if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
      return Path.of(args[0]).toAbsolutePath().normalize();
    }
    String property = System.getProperty("jvn.jane.workspaceRoot");
    if (property != null && !property.isBlank()) {
      return Path.of(property).toAbsolutePath().normalize();
    }
    return Path.of("").toAbsolutePath().normalize();
  }

  private static List<HelpArticle> indexDocs(Path root) {
    List<HelpArticle> docs = new ArrayList<>();
    if (root == null || !Files.isDirectory(root)) return docs;
    try (Stream<Path> stream = Files.walk(root)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
          .filter(path -> !shouldSkip(root, path))
          .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
          .forEach(path -> addDoc(root, path, docs));
    } catch (Exception ignored) {
      // Jane keeps the built-in expert corpus even if workspace indexing fails.
    }
    return docs;
  }

  private static void addDoc(Path root, Path path, List<HelpArticle> docs) {
    try {
      String body = Files.readString(path);
      String relative = root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
      docs.add(new HelpArticle(
          path.toAbsolutePath().normalize().toString(),
          title(body, path),
          summary(body),
          relative,
          body,
          Map.of("source", "Workspace")));
    } catch (Exception ignored) {
      // Skip unreadable docs; the rest of the corpus remains useful.
    }
  }

  private static boolean shouldSkip(Path root, Path path) {
    Path relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
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
      for (String line : body.split("\\R")) {
        Matcher matcher = HEADING_LINE.matcher(line.trim());
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
      if (paragraph.length() > 220) break;
    }
    String value = paragraph.toString().replaceAll("\\s+", " ").trim();
    if (value.isBlank()) return "No summary available.";
    return value.length() <= 220 ? value : value.substring(0, 217).trim() + "...";
  }
}

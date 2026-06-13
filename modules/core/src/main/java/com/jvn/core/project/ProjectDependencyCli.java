package com.jvn.core.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CLI wrapper for the project dependency validator.
 */
public final class ProjectDependencyCli {
  private ProjectDependencyCli() {
  }

  public static void main(String[] args) {
    int exit = run(args);
    if (exit != 0) System.exit(exit);
  }

  static int run(String[] args) {
    Options options = Options.parse(args == null ? new String[0] : args);
    if (options.help) {
      printHelp();
      return 0;
    }
    if (!options.errors.isEmpty()) {
      options.errors.forEach(error -> System.err.println("error: " + error));
      printHelp();
      return 2;
    }

    ProjectDependencyValidator.Report report = ProjectDependencyValidator.inspect(options.project);
    printReport(report, options.showInfo);

    boolean failed = report.errorCount() > 0
        || (options.failOnWarning && report.warningCount() > 0);
    return failed ? 1 : 0;
  }

  private static void printReport(ProjectDependencyValidator.Report report, boolean showInfo) {
    System.out.println("JVN dependency validation");
    System.out.println("  project : " + report.projectRoot());
    System.out.println("  findings: " + report.errorCount() + " error(s), "
        + report.warningCount() + " warning(s), " + report.infoCount() + " info");

    List<ProjectDependencyValidator.Finding> findings = new ArrayList<>(report.findings());
    findings.sort(Comparator
        .comparing((ProjectDependencyValidator.Finding f) -> f.severity().ordinal())
        .thenComparing(ProjectDependencyValidator.Finding::category)
        .thenComparing(ProjectDependencyValidator.Finding::location)
        .thenComparing(ProjectDependencyValidator.Finding::message));

    for (ProjectDependencyValidator.Finding finding : findings) {
      if (!showInfo && finding.severity() == ProjectDependencyValidator.Severity.INFO) continue;
      String target = finding.target() == null ? "" : " -> " + finding.target();
      System.out.println("  [" + finding.severity() + "] "
          + finding.category() + " " + finding.location()
          + " - " + finding.message() + target);
    }
  }

  private static void printHelp() {
    System.out.println("""
        Usage: ProjectDependencyCli [options]

        Options:
          --project <dir>      Project directory to scan. Defaults to the current directory.
          --fail-on-warning    Return a failing exit code when warnings are present.
          --show-info          Print informational cleanup findings such as unused assets.
          --help               Show this help text.
        """);
  }

  private static final class Options {
    private Path project = Path.of(".");
    private boolean failOnWarning;
    private boolean showInfo;
    private boolean help;
    private final List<String> errors = new ArrayList<>();

    private static Options parse(String[] args) {
      Options options = new Options();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
          case "--project", "-p" -> {
            if (i + 1 >= args.length) {
              options.errors.add(arg + " requires a directory");
            } else {
              options.project = Path.of(args[++i]);
            }
          }
          case "--fail-on-warning" -> options.failOnWarning = true;
          case "--show-info" -> options.showInfo = true;
          case "--help", "-h" -> options.help = true;
          default -> options.errors.add("unknown option: " + arg);
        }
      }
      return options;
    }
  }
}

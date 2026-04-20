package com.jvn.editor.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.scripting.jes.JesParseException;
import com.jvn.scripting.jes.JesParser;
import com.jvn.scripting.jes.JesToken;
import com.jvn.scripting.jes.JesTokenizer;

/**
 * Shared JES static analysis used by editor linting and future diagnostics panes.
 */
public final class JesScriptAnalyzer {
  public enum Mode {
    JES_DOCUMENT,
    TIMELINE_BLOCK
  }

  private static final Pattern SCENE_NAME_PATTERN =
      Pattern.compile("\\bscene\\s+\"([^\"]+)\"");
  private static final Pattern ENTITY_NAME_PATTERN =
      Pattern.compile("\\bentity\\s+\"([^\"]+)\"");
  private static final Pattern TIMELINE_LABEL_PATTERN =
      Pattern.compile("\\blabel\\s+\"([^\"]+)\"");

  private JesScriptAnalyzer() {
  }

  public static Analysis analyze(String text) {
    return analyze(text, null, null, Mode.JES_DOCUMENT);
  }

  public static Analysis analyze(String text, Mode mode) {
    return analyze(text, null, null, mode);
  }

  public static Analysis analyze(String text, File projectRoot, File sourceFile, Mode mode) {
    String source = text == null ? "" : text;
    Mode effectiveMode = mode == null ? Mode.JES_DOCUMENT : mode;
    String sourceName = sourceFile == null ? "" : sourceFile.getPath();
    List<LanguageDiagnostic> diagnostics = new ArrayList<>();

    if (effectiveMode == Mode.TIMELINE_BLOCK) {
      diagnostics.add(LanguageDiagnostic.info(
          "jes",
          sourceName,
          "jes_timeline_block",
          "Timeline block",
          0,
          0,
          0,
          0));
    } else {
      try {
        List<JesToken> tokens = new JesTokenizer(source).tokenize();
        new JesParser(tokens).parseProgram();
      } catch (JesParseException ex) {
        diagnostics.add(toDiagnostic(source, sourceName, ex));
      } catch (Exception ex) {
        diagnostics.add(LanguageDiagnostic.error(
            "jes",
            sourceName,
            "jes_analysis_error",
            "Error: " + ex.getMessage(),
            0,
            Math.max(0, source.length()),
            0,
            0));
      }
    }

    List<Symbol> symbols = collectSymbols(source);
    return new Analysis(source, diagnostics, symbols);
  }

  private static LanguageDiagnostic toDiagnostic(String source, String sourceName, JesParseException ex) {
    int line = Math.max(0, ex.getLine() - 1);
    int column = Math.max(0, ex.getCol() - 1);
    int start = offsetForLineColumn(source, line, column);
    int end = Math.min(Math.max(start + 1, start), lineEndOffset(source, line));
    return LanguageDiagnostic.error(
        "jes",
        sourceName,
        "jes_parse_error",
        ex.getMessage(),
        start,
        end,
        line,
        column);
  }

  private static List<Symbol> collectSymbols(String source) {
    if (source == null || source.isEmpty()) return List.of();
    List<Symbol> symbols = new ArrayList<>();
    collectSymbols(source, SCENE_NAME_PATTERN, SymbolKind.SCENE, symbols);
    collectSymbols(source, ENTITY_NAME_PATTERN, SymbolKind.ENTITY, symbols);
    collectSymbols(source, TIMELINE_LABEL_PATTERN, SymbolKind.TIMELINE_LABEL, symbols);
    symbols.sort((a, b) -> {
      int cmp = Integer.compare(a.startOffset(), b.startOffset());
      if (cmp != 0) return cmp;
      cmp = a.kind().name().compareTo(b.kind().name());
      if (cmp != 0) return cmp;
      return a.name().compareToIgnoreCase(b.name());
    });
    return symbols;
  }

  private static void collectSymbols(String source,
                                     Pattern pattern,
                                     SymbolKind kind,
                                     List<Symbol> out) {
    Matcher matcher = pattern.matcher(source);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (name == null || name.isBlank()) continue;
      int start = matcher.start(1);
      int line = lineForOffset(source, start);
      int column = columnForOffset(source, start);
      out.add(new Symbol(kind, name, start, matcher.end(1), line, column));
    }
  }

  private static int offsetForLineColumn(String source, int targetLine, int targetColumn) {
    if (source == null || source.isEmpty()) return 0;
    int line = 0;
    int lineStart = 0;
    for (int i = 0; i < source.length() && line < targetLine; i++) {
      if (source.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    }
    return Math.min(source.length(), lineStart + Math.max(0, targetColumn));
  }

  private static int lineEndOffset(String source, int targetLine) {
    if (source == null || source.isEmpty()) return 0;
    int start = offsetForLineColumn(source, targetLine, 0);
    int next = source.indexOf('\n', start);
    return next < 0 ? source.length() : next;
  }

  private static int lineForOffset(String source, int offset) {
    if (source == null || source.isEmpty()) return 0;
    int clamped = Math.max(0, Math.min(offset, source.length()));
    int line = 0;
    for (int i = 0; i < clamped; i++) {
      if (source.charAt(i) == '\n') line++;
    }
    return line;
  }

  private static int columnForOffset(String source, int offset) {
    if (source == null || source.isEmpty()) return 0;
    int clamped = Math.max(0, Math.min(offset, source.length()));
    int lineStart = source.lastIndexOf('\n', Math.max(0, clamped - 1));
    return clamped - (lineStart < 0 ? 0 : lineStart + 1);
  }

  public record Analysis(String source,
                         List<LanguageDiagnostic> diagnostics,
                         List<Symbol> symbols) {
    public Analysis {
      source = source == null ? "" : source;
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
      symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }

    public List<LanguageDiagnostic> errors() {
      return diagnostics.stream().filter(LanguageDiagnostic::isError).toList();
    }

    public List<String> entityNames() {
      return namesFor(SymbolKind.ENTITY);
    }

    public List<String> timelineLabelNames() {
      return namesFor(SymbolKind.TIMELINE_LABEL);
    }

    private List<String> namesFor(SymbolKind kind) {
      Set<String> names = new LinkedHashSet<>();
      for (Symbol symbol : symbols) {
        if (symbol.kind() == kind && symbol.name() != null && !symbol.name().isBlank()) {
          names.add(symbol.name());
        }
      }
      List<String> sorted = new ArrayList<>(names);
      sorted.sort(String::compareToIgnoreCase);
      return Collections.unmodifiableList(sorted);
    }
  }

  public enum SymbolKind {
    SCENE,
    ENTITY,
    TIMELINE_LABEL
  }

  public record Symbol(SymbolKind kind,
                       String name,
                       int startOffset,
                       int endOffset,
                       int line,
                       int column) {
    public Symbol {
      kind = kind == null ? SymbolKind.ENTITY : kind;
      name = name == null ? "" : name.trim();
      startOffset = Math.max(0, startOffset);
      endOffset = Math.max(startOffset, endOffset);
      line = Math.max(0, line);
      column = Math.max(0, column);
    }

    public boolean matchesPrefix(String prefix) {
      String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
      return name.toLowerCase(Locale.ROOT).startsWith(p);
    }
  }
}

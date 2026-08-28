package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AppliedDeclaration;
import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Imports reviewed assets and writes idempotent VNS declarations and entry includes. */
@SuppressWarnings("NullAway")
final class AssetDeclarationWriter {
  private final AssetDeclarationCatalog declarations;

  AssetDeclarationWriter(AssetDeclarationCatalog declarations) {
    this.declarations = declarations;
  }

  Path importAsset(Path root, Path source, AssetKind kind, String owner) throws IOException {
    Path input = source == null ? null : source.toAbsolutePath().normalize();
    if (input == null || !Files.isRegularFile(input)) {
      throw new IOException("Dropped asset does not exist");
    }
    if (input.startsWith(root)) return input;
    Path directory = root.resolve("assets")
        .resolve(AssetPathHeuristics.recommendedDirectory(kind, owner)).normalize();
    if (!directory.startsWith(root)) throw new IOException("Unsafe asset destination");
    Files.createDirectories(directory);
    Path target = uniqueTarget(directory, input.getFileName().toString());
    Files.copy(input, target);
    return target;
  }

  AppliedDeclaration apply(Path root, AssetSuggestion suggestion) throws IOException {
    String declaration = declarationFor(suggestion);
    if (declaration.isBlank()) return new AppliedDeclaration(null, "", false, false);
    AssetDeclarationCatalog.Index existing = declarations.scan(root);
    AssetDeclarationCatalog.Declaration found = existing.byPath().get(suggestion.relativePath());
    if (found != null) {
      return new AppliedDeclaration(found.sourceFile(), found.sourceLineText(), false, false);
    }

    Path target = root.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH).normalize();
    if (!target.startsWith(root)) throw new IOException("Unsafe declaration target");
    Files.createDirectories(target.getParent());
    String current = Files.isRegularFile(target)
        ? Files.readString(target, StandardCharsets.UTF_8)
        : "# Auto-generated asset declarations. Review changes in the Auto-label dashboard.\n";
    StringBuilder addition = new StringBuilder();
    boolean characterAdded = false;
    String declarationOwner = declarationOwner(suggestion);
    if (suggestion.kind().usesCharacterDeclaration()
        && !declarationOwner.isBlank()
        && !existing.characterIds().contains(declarationOwner)
        && !declarations.containsCharacter(current, declarationOwner)) {
      addition.append("\n@character ").append(declarationOwner).append(" \"\"\n");
      characterAdded = true;
    }
    addition.append(declaration).append('\n');
    writeAtomically(target, ensureTrailingNewline(current) + addition);
    boolean includeAdded = ensureEntryIncludesAutoDeclarations(root);
    return new AppliedDeclaration(target, declaration, characterAdded, includeAdded);
  }

  BatchWriteResult applyAll(Path root, List<AssetSuggestion> suggestions) throws IOException {
    if (suggestions == null || suggestions.isEmpty()) return new BatchWriteResult(null, 0, 0, false);
    AssetDeclarationCatalog.Index existing = declarations.scan(root);
    Path target = root.resolve(AssetAutoLabelService.AUTO_DECLARATIONS_PATH).normalize();
    if (!target.startsWith(root)) throw new IOException("Unsafe declaration target");
    String current = Files.isRegularFile(target)
        ? Files.readString(target, StandardCharsets.UTF_8)
        : "# Auto-generated asset declarations. Review changes in the Auto-label dashboard.\n";
    StringBuilder addition = new StringBuilder();
    Set<String> characterIds = new LinkedHashSet<>(existing.characterIds());
    Set<String> handledPaths = new LinkedHashSet<>();
    int generated = 0;
    int charactersAdded = 0;
    for (AssetSuggestion suggestion : suggestions) {
      if (suggestion == null || !handledPaths.add(suggestion.relativePath())
          || existing.byPath().containsKey(suggestion.relativePath())) continue;
      String declaration = declarationFor(suggestion);
      if (declaration.isBlank()) continue;
      String owner = declarationOwner(suggestion);
      if (suggestion.kind().usesCharacterDeclaration() && !owner.isBlank()
          && characterIds.add(owner) && !declarations.containsCharacter(current, owner)) {
        addition.append("\n@character ").append(owner).append(" \"\"\n");
        charactersAdded++;
      }
      addition.append(declaration).append('\n');
      generated++;
    }
    if (generated == 0) return new BatchWriteResult(target, 0, 0, false);
    Files.createDirectories(target.getParent());
    writeAtomically(target, ensureTrailingNewline(current) + addition);
    return new BatchWriteResult(
        target, generated, charactersAdded, ensureEntryIncludesAutoDeclarations(root));
  }

  String declarationFor(AssetSuggestion suggestion) {
    if (suggestion == null || suggestion.label().isBlank()) return "";
    String path = AssetBrowserView.vnsTokenForPath(suggestion.relativePath());
    return switch (suggestion.kind()) {
      case BACKGROUND -> "@background " + suggestion.label() + " " + path;
      case CHARACTER_SPRITE -> {
        String owner = suggestion.owner().isBlank() ? suggestion.label() : suggestion.owner();
        yield "@charimg " + owner + " " + suggestion.label() + " " + path;
      }
      case CHARACTER_LAYER, PROP, PANEL, UI, EFFECT -> {
        String owner = suggestion.owner().isBlank()
            ? AssetPathHeuristics.inferEntityId(suggestion.relativePath(), suggestion.kind())
            : suggestion.owner();
        yield "@charlayer " + owner + " " + suggestion.label() + " " + path;
      }
      case AUDIO, VIDEO, FONT, DATA, UNKNOWN -> "";
    };
  }

  private String declarationOwner(AssetSuggestion suggestion) {
    if (!suggestion.owner().isBlank()) return suggestion.owner();
    if (suggestion.kind() == AssetKind.CHARACTER_SPRITE) return suggestion.label();
    return AssetPathHeuristics.inferEntityId(suggestion.relativePath(), suggestion.kind());
  }

  private boolean ensureEntryIncludesAutoDeclarations(Path root) throws IOException {
    Path manifest = root.resolve("jvn.project");
    if (!Files.isRegularFile(manifest)) return false;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(manifest)) {
      properties.load(input);
    }
    String configured = properties.getProperty("entryVns", "scripts/main.vns").trim();
    if (configured.isBlank()) configured = "scripts/main.vns";
    Path entry = root.resolve(configured).normalize();
    if (!entry.startsWith(root) || !Files.isRegularFile(entry)) return false;
    String include = "@include /definitions/auto_labels.vns";
    List<String> lines = new ArrayList<>(Files.readAllLines(entry, StandardCharsets.UTF_8));
    if (lines.stream().map(String::strip).anyMatch(include::equalsIgnoreCase)) return false;
    int insertion = 0;
    while (insertion < lines.size()) {
      String trimmed = lines.get(insertion).strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("@scenario")
          || trimmed.startsWith("@include")) {
        insertion++;
      } else {
        break;
      }
    }
    lines.add(insertion, include);
    writeAtomically(entry, String.join("\n", lines) + "\n");
    return true;
  }

  private Path uniqueTarget(Path directory, String fileName) {
    String safeName = fileName == null || fileName.isBlank() ? "asset" : fileName;
    Path target = directory.resolve(safeName);
    if (!Files.exists(target)) return target;
    int dot = safeName.lastIndexOf('.');
    String base = dot > 0 ? safeName.substring(0, dot) : safeName;
    String extension = dot > 0 ? safeName.substring(dot) : "";
    for (int i = 2; i < 100_000; i++) {
      target = directory.resolve(base + "_" + i + extension);
      if (!Files.exists(target)) return target;
    }
    return directory.resolve(base + "_" + System.nanoTime() + extension);
  }

  private String ensureTrailingNewline(String value) {
    if (value == null || value.isEmpty()) return "";
    return value.endsWith("\n") ? value : value + "\n";
  }

  private void writeAtomically(Path target, String content) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    Files.writeString(temporary, content, StandardCharsets.UTF_8);
    moveAtomically(temporary, target);
  }

  static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  record BatchWriteResult(
      Path declarationFile, int declarationsGenerated, int charactersAdded,
      boolean entryIncludeAdded) {}
}

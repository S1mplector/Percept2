package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import com.jvn.editor.ui.AssetAutoLabelService.AssetSuggestion;
import com.jvn.editor.ui.AssetAutoLabelService.LabelStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/** Infers reviewable labels from VNS-declared siblings and the project's asset taxonomy. */
@SuppressWarnings("NullAway")
final class AssetLabelInference {
  List<Path> collectAssets(Path root) throws IOException {
    Set<Path> files = new LinkedHashSet<>();
    collectAssetsUnder(root.resolve("assets"), files);
    collectAssetsUnder(root.resolve("game/images"), files);
    return files.stream().sorted(Comparator.comparing(Path::toString)).toList();
  }

  AssetSuggestion fromDeclaration(
      Path file, String relativePath, AssetDeclarationCatalog.Declaration declaration,
      boolean isNew, boolean conflict, int aliasCount) {
    String source = declaration.sourceFile().getFileName() + ":" + declaration.line();
    String explanation = conflict
        ? "Conflicting VNS label points to multiple files; first found in " + source
        : aliasCount > 1
            ? "Declared with " + aliasCount + " VNS aliases; first found in " + source
            : "Declared in " + source;
    return new AssetSuggestion(
        file, relativePath, declaration.kind(), declaration.owner(), declaration.label(),
        conflict ? LabelStatus.CONFLICT : LabelStatus.DECLARED, 1.0,
        explanation,
        declaration.sourceFile(), declaration.line(), isNew);
  }

  AssetSuggestion fromRegistry(
      Path file, String relativePath, AssetLabelRegistry.Entry entry, boolean isNew) {
    return new AssetSuggestion(
        file, relativePath, entry.kind(), entry.owner(), entry.label(), entry.status(),
        entry.confidence(), entry.reason().isBlank() ? "Saved editor decision" : entry.reason(),
        null, -1, isNew);
  }

  AssetSuggestion fromMissingDeclaration(
      Path root, AssetDeclarationCatalog.Declaration declaration, boolean conflict,
      int aliasCount) {
    Path expected = root.resolve(declaration.relativePath()).normalize();
    String source = declaration.sourceFile().getFileName() + ":" + declaration.line();
    String aliasText = aliasCount > 1 ? " (one of " + aliasCount + " aliases)" : "";
    return new AssetSuggestion(
        expected, declaration.relativePath(), declaration.kind(), declaration.owner(),
        declaration.label(), conflict ? LabelStatus.CONFLICT : LabelStatus.MISSING, 1.0,
        "VNS declaration points to a missing asset" + aliasText + "; declared in " + source,
        declaration.sourceFile(), declaration.line(), false);
  }

  AssetSuggestion infer(
      Path file, String relativePath, AssetDeclarationCatalog.Index catalog,
      Set<String> usedScopedLabels, boolean isNew) {
    AssetKind pathKind = AssetPathHeuristics.kindFromPath(Path.of(relativePath));
    String directory = AssetPathHeuristics.parentPath(relativePath);
    List<AssetDeclarationCatalog.Declaration> siblings =
        catalog.byDirectory().getOrDefault(directory, List.of());
    AssetKind kind = siblings.isEmpty()
        ? pathKind : majority(siblings, AssetDeclarationCatalog.Declaration::kind, pathKind);
    String owner = ownerForDirectory(directory, catalog.ownerByDirectory());
    if (owner.isBlank()) owner = AssetPathHeuristics.inferOwner(relativePath, kind);
    String label = AssetPathHeuristics.inferLabel(relativePath, kind, owner);
    label = AssetPathHeuristics.uniqueLabel(owner, label, usedScopedLabels);
    usedScopedLabels.add(AssetPathHeuristics.scopeKey(owner, label));

    double confidence = 0.52;
    List<String> reasons = new ArrayList<>();
    if (!siblings.isEmpty()) {
      confidence += 0.32;
      reasons.add(siblings.size() + " declared sibling" + (siblings.size() == 1 ? "" : "s")
          + " in the same directory");
    }
    if (!owner.isBlank()) {
      confidence += ownerForDirectory(directory, catalog.ownerByDirectory()).isBlank() ? 0.05 : 0.10;
      reasons.add("owner " + owner + " inferred from the asset tree");
    }
    if (pathKind != AssetKind.UNKNOWN) {
      confidence += 0.05;
      reasons.add("type inferred from path and extension");
    }
    if (reasons.isEmpty()) reasons.add("filename-only suggestion; review recommended");
    return new AssetSuggestion(
        file, relativePath, kind, owner, label, LabelStatus.SUGGESTED,
        Math.min(0.99, confidence), String.join("; ", reasons), null, -1, isNew);
  }

  private void collectAssetsUnder(Path directory, Set<Path> output) throws IOException {
    if (!Files.isDirectory(directory)) return;
    try (Stream<Path> stream = Files.walk(directory, 16)) {
      stream.filter(Files::isRegularFile)
          .filter(AssetPathHeuristics::isSupportedAsset)
          .map(path -> path.toAbsolutePath().normalize())
          .forEach(output::add);
    }
  }

  private String ownerForDirectory(String directory, Map<String, String> knownOwners) {
    String candidate = directory;
    while (!candidate.isBlank()) {
      String owner = knownOwners.getOrDefault(candidate, "");
      if (!owner.isBlank()) return owner;
      candidate = AssetPathHeuristics.parentPath(candidate);
    }
    return "";
  }

  private static <T> T majority(
      List<AssetDeclarationCatalog.Declaration> declarations,
      Function<AssetDeclarationCatalog.Declaration, T> mapper, T fallback) {
    Map<T, Integer> counts = new LinkedHashMap<>();
    for (AssetDeclarationCatalog.Declaration declaration : declarations) {
      counts.merge(mapper.apply(declaration), 1, Integer::sum);
    }
    return counts.entrySet().stream().max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey).orElse(fallback);
  }
}
